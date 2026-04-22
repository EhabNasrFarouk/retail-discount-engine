package engine

import java.util.concurrent.{Executors, Semaphore}
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._
import scala.util.chaining._
object Main extends App {

  val csvPath   = Config.csvPath
  val batchSz   = Config.batchSize
  val parallelism = Config.parallelism

  Logger.info(s"Rule engine started — file=$csvPath  batchSize=$batchSz  parallelism=$parallelism")

  val pool = Executors.newFixedThreadPool(parallelism)
  implicit val ec: ExecutionContext = ExecutionContext.fromExecutor(pool)

  val semaphore = new Semaphore(parallelism)

  val totalInserted: Int =
    CsvReader
      .readCsv(csvPath)
      .grouped(batchSz)
      .map { batch =>
        semaphore.acquire()
        Future {
          try     ChunkProcessor.runChunk(batch.toList)
          finally semaphore.release()
        }
      }
      .foldLeft(Future.successful(0)) { (accF, batchF) =>
        for {
          acc    <- accF
          result <- batchF
        } yield acc + result
      }
      .pipe(f => Await.result(f, Duration.Inf))

  pool.shutdown()
  Logger.info(s"Rule engine finished — total rows inserted: $totalInserted")
  println(s"Done. $totalInserted transactions processed and stored.")
}
