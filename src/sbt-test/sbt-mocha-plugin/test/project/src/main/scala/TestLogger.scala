import org.apache.logging.log4j.core._
import org.apache.logging.log4j.core.appender.AbstractAppender
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.core.layout.PatternLayout
import org.apache.logging.log4j.message.ObjectMessage
import sbt.internal.util.{ObjectEvent, StringEvent}
import sbt.{Level => SbtLevel}

object TestLogger extends AbstractAppender("TestLogger", null, PatternLayout.createDefaultLayout(), true) {

  override def append(event: LogEvent): Unit = {
    val level = event.getLevel match {
      case Level.ERROR => SbtLevel.Error
      case Level.WARN => SbtLevel.Warn
      case Level.INFO => SbtLevel.Info
    }
    val msg = event.getMessage match {
      case o: ObjectMessage =>
        o.getParameter match {
          case e: ObjectEvent[_] => e.message.toString
          case s: StringEvent => s.message
          case _ => event.getMessage.getFormattedMessage
        }
      case _ => event.getMessage.getFormattedMessage
    }
    messages = (level, stripAnsiCodes(msg)) :: messages

    if (event.getMessage.getThrowable != null) {
      exceptions = event.getMessage.getThrowable :: exceptions
    }
  }

  override def error(msg: String): Unit = messages = (SbtLevel.Error, msg) :: messages
  override def error(msg: String, t: Throwable): Unit = {
    exceptions = t :: exceptions
    error(msg)
  }
  override def error(msg: String, event: LogEvent, t: Throwable): Unit = {
    exceptions = t :: exceptions
    append(event)
  }

  def stripAnsiCodes(s: String): String = {
    s.replaceAll("\u001b[^m]*m", "")
  }

  var messages = List.empty[(SbtLevel.Value, String)]
  var exceptions = List.empty[Throwable]
}
