import java.io._

import scala.concurrent.{Future, Await}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.io.Source

object IteratorExample extends Example {
  override def process(inF: File, outF: File): Unit = {
    using(new FileInputStream("mockaroo.csv")) { in =>
      using(new PrintWriter(new FileOutputStream("emails.txt"), true)) { out =>
        Source.fromInputStream(in).getLines
          .drop(1)
          .filterNot(_.contains('"'))
          .map(MockData.parse)
          .map(datum => datum -> Await.result(Geocoder(datum.ipAddress), 5.seconds))
          .collect { case (datum, Country(c)) if c == "United States" => datum.email }
          .take(20)
          .foreach(out.println)
      }
    }
  }
}
