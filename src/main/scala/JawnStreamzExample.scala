import scalaz.stream.Process
import scalaz.stream.io.channel
import scalaz.stream.nio.file
import scala.concurrent.duration._

import jawnstreamz._
import jawn.ast._
import jawn.ast.JParser.facade

object JawnStreamzExample extends App {
  implicit val scheduler = scalaz.stream.DefaultScheduler
  Process.awakeEvery(1.second) // every 1 second
    .map(_ => 64) // ask for 64 bytes
    .through(file.chunkR("mockaroo.json")) // from mockaroo.json
    .unwrapJsonArray[JValue] // emit one JSON array element at a time
    .collect { case jObj: JObject => jObj.get("email") } // get the e-mail
    .map(_.toString) // convert to string
    .to(scalaz.stream.io.stdOutLines) // write to stdout
    .run // convert to a task
    .run // and execute said task
}
