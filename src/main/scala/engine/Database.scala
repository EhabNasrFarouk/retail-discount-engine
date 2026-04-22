package engine

import com.zaxxer.hikari.{HikariConfig, HikariDataSource}
import java.sql.{Connection, Timestamp}
import scala.util.Try

object Database {

  private val hikariConfig = new HikariConfig()
  hikariConfig.setJdbcUrl(Config.dbUrl)
  hikariConfig.setUsername(Config.dbUser)
  hikariConfig.setPassword(Config.dbPassword)
  hikariConfig.setMaximumPoolSize(Config.parallelism + 2)
  hikariConfig.setConnectionTimeout(30000)
  hikariConfig.addDataSourceProperty("rewriteBatchedStatements", "true")
  hikariConfig.addDataSourceProperty("useServerPrepStmts", "false")

  private val dataSource = new HikariDataSource(hikariConfig)

  def openConnection(): Try[Connection] = Try(dataSource.getConnection())

  private val insertSql =
    """INSERT INTO processed_transactions
      |  (timestamp, product_name, expiry_date, quantity, unit_price,
      |   channel, payment_method, discount, final_price)
      |VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)""".stripMargin

  def insertBatch(conn: Connection, batch: List[ProcessedTransaction]): Int =
    Try {
      val ps = conn.prepareStatement(insertSql)
      batch.foreach { pt =>
        val tx = pt.transaction
        ps.setTimestamp(1, Timestamp.valueOf(tx.timestamp))
        ps.setString   (2, tx.productName)
        ps.setDate     (3, java.sql.Date.valueOf(tx.expiryDate))
        ps.setInt      (4, tx.quantity)
        ps.setDouble   (5, tx.unitPrice)
        ps.setString   (6, tx.channel)
        ps.setString   (7, tx.paymentMethod)
        ps.setDouble   (8, pt.discount)
        ps.setDouble   (9, pt.finalPrice)
        ps.addBatch()
      }
      ps.executeBatch()
      ps.close()
      batch.size
    }.recover { case e =>
      Logger.error(s"Batch insert failed: ${e.getMessage}")
      0
    }.getOrElse(0)
}