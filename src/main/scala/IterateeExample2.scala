import java.io._

import play.api.libs.iteratee.Enumeratee._
import play.api.libs.iteratee._
import play.extras.iteratees.Encoding._

import scala.concurrent.{Future, Await}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

object IterateeExample2 extends Example {
  // http://stackoverflow.com/questions/10346592/how-to-write-an-enumeratee-to-chunk-an-enumerator-along-different-boundaries
  lazy val upToNewLine =
    Traversable.splitOnceAt[String, Char](_ != '\n') &>>
    Iteratee.consume()

  override def process(inF: File, outF: File): Unit = {
    using(new PrintWriter(new FileWriter(outF), true)) { out =>
      val f = Enumerator.fromFile(inF)
        .through(decode())
        .map(new String(_))
        .through(filter(_.nonEmpty))
        .through(Enumeratee.grouped(upToNewLine))
        .through(filter(_.nonEmpty))
        .through(drop(1))
        .through(filterNot(_.contains('"')))
        .through(map(MockData.parse))
        .through(Concurrent.buffer(8))
        .through(map(datum => Geocoder(datum.ipAddress).map(c => Enumerator(datum -> c))))
        .map(Enumerator.flatten)
        .run(Iteratee.fold(Enumerator.empty[(MockData, Country)])(_ interleave _))
        .flatMap { merged =>
           merged.through(collect { case (datum, Country(c)) if c == "United States" => datum.email})
             .through(take(20))
             .run(Iteratee.foreach(out.println))
        }

      Await.result(f, 1.minute)
    }
  }
}
