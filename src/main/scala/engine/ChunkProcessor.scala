package engine

import scala.collection.parallel.CollectionConverters._

object ChunkProcessor {

  def runChunk(txs: List[Transaction]): Int = {
    val processed: List[ProcessedTransaction] =
      txs.par
         .map(DiscountCalculator.process)
         .toList

    Database.openConnection() match {
      case scala.util.Success(conn) =>
        val inserted = Database.insertBatch(conn, processed)
        conn.close()
        Logger.info(s"Inserted $inserted rows from chunk of ${txs.size}")
        inserted
      case scala.util.Failure(e) =>
        Logger.error(s"Could not open DB connection: ${e.getMessage}")
        0
    }
  }
}
