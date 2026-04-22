# Retail Discount Engine

-  A high-performance Scala rules engine that processes millions of retail transactions in parallel, applies configurable discount logic, and persists results into MySQL using a high-throughput connection pool.

---

## Table of Contents

- [Overview](#overview)
- [Architecture](#architecture)
- [How It Works](#how-it-works)
- [Discount Rules](#discount-rules)
- [Project Structure](#project-structure)
- [Prerequisites](#prerequisites)
- [Configuration](#configuration)
- [Database Setup](#database-setup)
- [How to Run](#how-to-run)
- [Sample Dataset](#sample-dataset)
- [Performance](#performance)
- [Technologies Used](#technologies-used)
- [Contact](#contact)

---

## Overview

This engine reads retail transaction data from a CSV file, evaluates each transaction against a set of configurable discount rules, calculates the final discounted price, and persists all results into a MySQL database.

It is designed for **high-throughput processing** — capable of handling **millions of rows** by splitting data into configurable batches and processing them concurrently using Scala's parallel collections and a fixed thread pool backed by a semaphore for backpressure control.

### Key Capabilities

| Capability | Detail |
|------------|--------|
| **Parallel processing** | Two-level parallelism: batch-level Futures + row-level `txs.par` |
| **Lazy CSV streaming** | Streams rows without loading the entire file into memory |
| **6 discount rules** | Expiry, Product type, Special day, Bulk qty, Channel, Payment method |
| **Smart discount logic** | Top-2 discounts averaged — never just picks one |
| **Bulk DB insert** | HikariCP pool + JDBC batch statements for maximum throughput |
| **Structured logging** | Every operation logged with INFO / WARN / ERROR levels |
| **Fully configurable** | Batch size, parallelism, DB credentials — all in one config file |

---

## Architecture

```
+------------------------------------------------------------------+
|                         INPUT LAYER                              |
|                                                                  |
|   TRX1000.csv  -->  CsvReader  -->  Iterator[Transaction]        |
|   application.conf  -->  Config                                  |
+------------------------------------------------------------------+
                              |
                              v  .grouped(batchSize)
+------------------------------------------------------------------+
|                      ORCHESTRATION LAYER                         |
|                                                                  |
|   Main  --  FixedThreadPool(parallelism)  --  Semaphore          |
|              |                                                   |
|              +-- Future { ChunkProcessor.runChunk(batch1) }      |
|              +-- Future { ChunkProcessor.runChunk(batch2) }      |
|              +-- Future { ChunkProcessor.runChunk(batchN) }      |
+------------------------------------------------------------------+
                              |
                              v  per batch (parallel)
+------------------------------------------------------------------+
|                      PROCESSING LAYER                            |
|                                                                  |
|   ChunkProcessor                                                 |
|      +-- txs.par.map(DiscountCalculator.process)                 |
|              |                                                   |
|              +-- DiscountCalculator                              |
|                    +-- Apply all 6 rules                         |
|                    +-- Flatten, sort descending, take top 2      |
|                    +-- Average the top 2                         |
|                    +-- finalPrice = qty x price x (1 - disc%)   |
+------------------------------------------------------------------+
                              |
                              v
+------------------------------------------------------------------+
|                      PERSISTENCE LAYER                           |
|                                                                  |
|   Database                                                       |
|      +-- HikariCP connection pool (poolSize = parallelism + 2)   |
|      +-- rewriteBatchedStatements=true  (MySQL optimization)     |
|      +-- JDBC executeBatch() per chunk                           |
|                                                                  |
|   MySQL --> processed_transactions table                         |
+------------------------------------------------------------------+
                              |
                              v
+------------------------------------------------------------------+
|                        LOGGING LAYER                             |
|   Logger  -->  rules_engine.log  (INFO / WARN / ERROR)          |
+------------------------------------------------------------------+
```

---

## How It Works

### Step 1 — Load configuration
`Config` reads `resources/application.conf` and exposes all settings (DB URL, batch size, parallelism, CSV path) as typed values.

### Step 2 — Stream the CSV
`CsvReader` opens the file using `scala.io.Source`, skips the header row, and parses each line into a `Transaction` case class. Invalid or malformed lines are skipped with a `WARN` log — the engine never crashes on bad data.

### Step 3 — Batch and orchestrate
`Main` groups the lazy iterator into batches of `batchSize` rows. Each batch is submitted as a `Future` on a fixed thread pool. A `Semaphore` with `parallelism` permits prevents too many batches from running simultaneously, keeping memory usage predictable.

### Step 4 — Parallel discount calculation
`ChunkProcessor` receives a batch and calls `txs.par.map(DiscountCalculator.process)`. The `.par` converts the list to a parallel collection, distributing discount calculation across all available CPU cores simultaneously.

### Step 5 — Apply discount rules
`DiscountCalculator` runs all 6 rules against each transaction, collects the qualifying discounts, sorts them descending, takes the top 2, and averages them. The final price is computed as:

```
finalPrice = quantity x unitPrice x (1.0 - averageDiscount / 100.0)
```

### Step 6 — Bulk insert to MySQL
`Database` acquires a connection from the HikariCP pool, prepares a batch insert statement, adds all processed rows via `addBatch()`, and executes with `executeBatch()`. The MySQL flag `rewriteBatchedStatements=true` rewrites multiple inserts into a single multi-row INSERT for maximum throughput.

### Step 7 — Accumulate and finish
`Main` folds all batch Futures into a running total of inserted rows, waits for all of them to complete, shuts down the thread pool, and logs the final count.

---

## Discount Rules

Each transaction is evaluated against **all 6 rules simultaneously**. The top 2 qualifying discounts are selected and averaged to produce the final discount percentage.

### Rule Details

**1. Expiry Rule**
```scala
if (daysLeft > 0 && daysLeft < 30) => discount = (30 - daysLeft)%
```
Products close to expiry get higher discounts. A product expiring tomorrow gets 29% off; one expiring in 20 days gets 10% off.

**2. Cheese / Wine Rule**
```scala
if productName.contains("cheese") => 10%
if productName.contains("wine")   => 5%
```
Category-based discount applied by matching the product name.

**3. Special Day Rule**
```scala
if (month == 3 && day == 23) => 50%
```
A flat 50% discount is applied to all transactions on March 23rd.

**4. Bulk Quantity Rule**
```scala
qty 6-9   => 5%
qty 10-14 => 7%
qty 15+   => 10%
```
Volume purchasing is rewarded with tiered discounts.

**5. App Channel Rule**
```scala
if channel == "App" => discount = ceil(quantity / 5.0) * 5
```
App purchases receive a discount equal to the nearest multiple of 5 of their quantity percentage.

**6. Visa Payment Rule**
```scala
if paymentMethod == "Visa" => 5%
```
A flat 5% cashback-style discount for Visa card payments.

### Discount Combination Table

| Rules Triggered | Result |
|----------------|--------|
| None | 0% — full price |
| 1 rule | That rule's discount |
| 2 rules | Average of both |
| 3+ rules | Average of the top 2 highest |

---

## Project Structure

```
retail-discount-engine/
|
+-- src/
|   +-- main/
|       +-- scala/
|           +-- engine/
|               +-- Main.scala               # Entry point — orchestrates the full pipeline
|               +-- Config.scala             # Reads and exposes application.conf settings
|               +-- CsvReader.scala          # Lazy CSV parser -> Iterator[Transaction]
|               +-- ChunkProcessor.scala     # Parallel batch processor (txs.par + DB insert)
|               +-- DiscountRules.scala      # All 6 discount rule functions (pure functions)
|               +-- DiscountCalculator.scala # Applies rules, averages top 2, computes price
|               +-- Database.scala           # HikariCP pool setup + JDBC batch insert
|               +-- Logger.scala             # Structured log writer (INFO/WARN/ERROR)
|               +-- TransactionModels.scala  # Case classes: Transaction, ProcessedTransaction
|
+-- resources/
|   +-- application.conf                     # All runtime configuration
|
+-- TRX1000.csv                              # Sample dataset (1000 transactions for testing)
+-- build.sbt                                # SBT build definition and dependencies
+-- .gitignore                               # Excludes target/, logs, IDE files, large CSVs
+-- README.md                                # This file
```

---

## Prerequisites

Make sure the following are installed before running the project:

| Tool | Version | Purpose |
|------|---------|---------|
| **Java JDK** | 11 or higher | Runtime for Scala |
| **Scala** | 2.13.x | Core language |
| **MySQL** | 8.x | Result storage |

---

## Configuration

All runtime settings live in `resources/application.conf`:

```hocon
# Database connection
db.url      = jdbc:mysql://localhost:3306/rule_engine
db.user     = root
db.password = root

# Path to the CSV input file
csv.path    = TRX1000.csv

# Number of rows per batch
batch.size  = 5000

# Number of parallel threads (defaults to CPU core count if not set)
# parallelism = 8
```

> **Tip:** For large datasets (millions of rows), increase `batch.size` to `10000` and set `parallelism` to match your CPU core count for best performance.

---

## Database Setup

Run the following SQL in your MySQL client before starting the engine:

```sql
CREATE DATABASE IF NOT EXISTS rule_engine;
USE rule_engine;

CREATE TABLE IF NOT EXISTS processed_transactions (
    id              BIGINT        AUTO_INCREMENT PRIMARY KEY,
    timestamp       DATETIME      NOT NULL,
    product_name    VARCHAR(255)  NOT NULL,
    expiry_date     DATE          NOT NULL,
    quantity        INT           NOT NULL,
    unit_price      DOUBLE        NOT NULL,
    channel         VARCHAR(100)  NOT NULL,
    payment_method  VARCHAR(100)  NOT NULL,
    discount        DOUBLE        NOT NULL,
    final_price     DOUBLE        NOT NULL,
    created_at      TIMESTAMP     DEFAULT CURRENT_TIMESTAMP
);
```

---

## How to Run

**1. Clone the repository**
```bash
git clone https://github.com/EhabNasrFarouk/retail-discount-engine.git
cd retail-discount-engine
```

**2. Set up the database**

Run the SQL from the [Database Setup](#database-setup) section above.

**3. Configure the application**

Edit `resources/application.conf` with your MySQL credentials and CSV path.

**4. Run with SBT**
```bash
sbt run
```

**5. Check results**

| Output | Location |
|--------|----------|
| Terminal progress | Printed live during execution |
| Detailed logs | `rules_engine.log` |
| Processed data | MySQL `processed_transactions` table |

**Example terminal output:**
```
[INFO] Rule engine started — file=TRX1000.csv  batchSize=5000  parallelism=8
[INFO] Opening CSV: TRX1000.csv
[INFO] Inserted 1000 rows from chunk of 1000
[INFO] Rule engine finished — total rows inserted: 1000
Done. 1000 transactions processed and stored.
```

**Query your results in MySQL:**
```sql
SELECT product_name, quantity, unit_price, discount, final_price
FROM processed_transactions
ORDER BY discount DESC
LIMIT 10;
```

---

## Sample Dataset

A sample file `TRX1000.csv` with **1000 transactions** is included for quick testing.

**CSV format:**
```
timestamp,product_name,expiry_date,quantity,unit_price,channel,payment_method
2023-03-23T10:00:00Z,Cheese Block,2023-04-01,12,15.99,App,Visa
```

**Column reference:**

| Column | Format | Description |
|--------|--------|-------------|
| `timestamp` | `yyyy-MM-dd'T'HH:mm:ss'Z'` | Date and time of transaction |
| `product_name` | String | Name of the purchased product |
| `expiry_date` | `yyyy-MM-dd` | Product expiry date |
| `quantity` | Int | Number of units purchased |
| `unit_price` | Double | Price per single unit |
| `channel` | String | Sales channel: `App`, `Store`, `Web` |
| `payment_method` | String | Payment method: `Visa`, `Cash`, `Mastercard`, etc. |

> For full-scale testing, place your large dataset at the path defined in `csv.path` inside `application.conf`. The engine is tested on 10M+ row files.

---

## Performance

The engine is designed to maximize throughput on large datasets:

- **Lazy streaming** — the CSV is never fully loaded into memory; rows are consumed as an iterator
- **Semaphore backpressure** — prevents unbounded Future creation and memory spikes
- **HikariCP pool** — reuses DB connections across batches instead of opening a new connection per batch
- **`rewriteBatchedStatements=true`** — MySQL driver merges batch inserts into a single multi-row INSERT, dramatically reducing round-trips
- **`txs.par`** — Scala parallel collections distribute discount computation across all CPU cores with zero manual thread management

---

## Technologies Used

| Technology | Version | Purpose |
|------------|---------|---------|
| **Scala** | 2.13.12 | Core language |
| **Scala Parallel Collections** | 1.0.4 | Intra-batch parallelism (`txs.par`) |
| **Java ExecutorService** | JDK 11+ | Fixed thread pool for batch-level concurrency |
| **HikariCP** | 5.1.0 | High-performance JDBC connection pooling |
| **MySQL Connector/J** | 8.0.33 | MySQL JDBC driver |
| **scala.io.Source** | Built-in | Lazy CSV file reading |
| **Java Time API** | JDK 11+ | Date/time parsing and manipulation |

![Scala](https://img.shields.io/badge/Scala-2.13-DC322F?style=for-the-badge&logo=scala&logoColor=white)
![MySQL](https://img.shields.io/badge/MySQL-8.x-4479A1?style=for-the-badge&logo=mysql&logoColor=white)
![SBT](https://img.shields.io/badge/SBT-1.x-FF6600?style=for-the-badge)
![HikariCP](https://img.shields.io/badge/HikariCP-Connection_Pool-009688?style=for-the-badge)
---

## Contact


Feel free to reach out if you have questions, suggestions, or want to collaborate:

[![Gmail](https://img.shields.io/badge/Gmail-D14836?style=flat&logo=gmail&logoColor=white)](mailto:ehabnasr406@gmail.com)
[![LinkedIn](https://img.shields.io/badge/LinkedIn-0077B5?style=flat&logo=linkedin&logoColor=white)](https://www.linkedin.com/in/ehab-nasr-farouk)