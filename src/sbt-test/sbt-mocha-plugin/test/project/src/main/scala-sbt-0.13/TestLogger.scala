import sbt._

object TestLogger extends AbstractLogger {

  // ANSI code stripping is broken, see https://github.com/sbt/sbt/issues/1143
  // so we return true and strip them ourselves
  override def ansiCodesSupported = true
  def stripAnsiCodes(s: String): String = {
    s.replaceAll("\033[^m]*m", "")
  }

  var messages = List.empty[(Level.Value, String)]
  var exceptions = List.empty[Throwable]

  def trace(t: => Throwable) = exceptions = t :: exceptions

  def success(msg: => String) = ()

  def log(level: Level.Value, msg: => String) = {
    messages = (level, stripAnsiCodes(msg)) :: messages
  }

  def getLevel = Level.Debug
  def setLevel(newLevel: Level.Value) = Unit
  def setTrace(flag: Int) = Unit
  def getTrace = 1
  def successEnabled = true
  def setSuccessEnabled(flag: Boolean) = Unit
  def control(event: ControlEvent.Value, message: => String) = Unit
  def logAll(events: Seq[LogEvent]) = events.foreach(log)
}
