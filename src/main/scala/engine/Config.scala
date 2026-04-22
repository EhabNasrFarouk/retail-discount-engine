package engine

import scala.io.{BufferedSource, Source}
import scala.util.Try

object Config {

  private def loadFile(path: String): Map[String, String] =
    Try {
      val src: BufferedSource = Source.fromFile(path)
      val pairs = src.getLines()
        .map(_.trim)
        .filterNot(l => l.isEmpty || l.startsWith("#"))
        .flatMap { line =>
          line.split("=", 2) match {
            case Array(k, v) => Some(k.trim -> v.trim.replace("\"", ""))
            case _           => None
          }
        }
        .toMap
      src.close()
      pairs
    }.getOrElse(Map.empty)

  private val settings: Map[String, String] =
    loadFile("resources/application.conf")

  def get(key: String, default: String = ""): String =
    settings.getOrElse(key, default)

  val dbUrl        : String = get("db.url",       "jdbc:mysql://localhost:3306/rule_engine")
  val dbUser       : String = get("db.user",      "root")
  val dbPassword   : String = get("db.password",  "root")
  val batchSize    : Int    = get("batch.size",    "5000").toInt
  val csvPath      : String = get("csv.path",      "TRX10M.csv")
  val parallelism  : Int    = get("parallelism",   Runtime.getRuntime.availableProcessors().toString).toInt
}
