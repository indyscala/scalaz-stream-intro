import java.io._

import scodec.bits.ByteVector

import scala.concurrent.ExecutionContext.Implicits.global
import scalaz.stream.Process._
import scalaz.stream.nio.file

object ScalazStreamExample extends Example {
  override def process(inF: File, outF: File): Unit = {
    file.linesR(inF.getName)
      .drop(1)
      .filter(!_.contains('"'))
      .map(MockData.parse)
      .gatherMap(8)(datum => Geocoder.task(datum.ipAddress).map(datum -> _))
      .collect { case (datum, Country(c)) if c == "United States" => datum.email }
      .take(20)
      .to(file.chunkW(outF.getName).contramap(s => ByteVector(s.getBytes)))
      .run
      .run
  }
}

