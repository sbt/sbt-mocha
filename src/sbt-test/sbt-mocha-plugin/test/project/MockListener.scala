import sbt._

object MockListener extends TestReportListener {

  @volatile var gotAnEvent = false

  def testEvent(event: TestEvent) = gotAnEvent = true

  def startGroup(name: String) = ()

  def endGroup(name: String, t: Throwable) = ()

  def endGroup(name: String, result: TestResult) = ()
}
