import java.io._

import play.api.libs.iteratee.{Iteratee, Enumeratee, Enumerator, Traversable}
import play.api.libs.iteratee.Enumeratee._
import play.extras.iteratees.Encoding._

import scala.concurrent.{Future, Await}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

object IterateeExample extends Example {
  // http://stackoverflow.com/questions/10346592/how-to-write-an-enumeratee-to-chunk-an-enumerator-along-different-boundaries
  lazy val upToNewLine =
    Traversable.splitOnceAt[String, Char](_ != '\n') &>>
    Iteratee.consume()

  override def process(inF: File, outF: File): Unit = {
    using(new PrintWriter(new FileWriter(outF), true)) { out =>
      val f = Enumerator.fromFile(inF)
        .through(decode())
        .map(new String(_))
        .through(Enumeratee.grouped(upToNewLine))
        .through(filter(_.nonEmpty))
        .through(drop(1))
        .through(filterNot[String](_.contains('"')))
        .through(map(MockData.parse))
        .through(mapM(datum => Geocoder(datum.ipAddress).map(datum -> _)))
        .through(collect { case (datum, Country(c)) if c == "United States" => datum.email})
        .through(take(20))
        .run(Iteratee.foreach(out.println))

      Await.result(f, 1.minute)
    }
  }
}
