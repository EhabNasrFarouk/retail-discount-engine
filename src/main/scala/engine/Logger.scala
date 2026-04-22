package engine

import java.io.{FileWriter, PrintWriter}
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object Logger {

  private val logFile  = "rules_engine.log"
  private val writer   = new PrintWriter(new FileWriter(logFile, /*append=*/true), /*autoFlush=*/true)
  private val fmt      = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

  def log(level: String, message: String): Unit = writer.synchronized {
    val ts = LocalDateTime.now().format(fmt)
    writer.println(s"$ts $level $message")
  }

  def info (msg: String): Unit = log("INFO ", msg)
  def warn (msg: String): Unit = log("WARN ", msg)
  def error(msg: String): Unit = log("ERROR", msg)
}
