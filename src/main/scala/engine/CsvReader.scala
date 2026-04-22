package engine

import java.time.{LocalDate, LocalDateTime}
import java.time.format.DateTimeFormatter
import scala.io.Source
import scala.util.Try

object CsvReader {

  private val tsFormatter   = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
  private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

  def parseLine(line: String): Option[Transaction] =
    Try {
      val cols = line.split(",", -1).map(c => c.trim.replace("\"", ""))
      Transaction(
        timestamp     = LocalDateTime.parse(cols(0), tsFormatter),
        productName   = cols(1),
        expiryDate    = LocalDate.parse(cols(2), dateFormatter),
        quantity      = cols(3).toInt,
        unitPrice     = cols(4).toDouble,
        channel       = cols(5),
        paymentMethod = cols(6)
      )
    }.toOption

  def readCsv(filePath: String): Iterator[Transaction] = {
    Logger.info(s"Opening CSV: $filePath")
    val src = Source.fromFile(filePath)(scala.io.Codec.UTF8)
    src
      .getLines()
      .drop(1)
      .flatMap { line =>
        val parsed = parseLine(line)
        if (parsed.isEmpty) Logger.warn(s"Skipped unparseable line: $line")
        parsed
      }
  }
}
