import java.io.{Closeable, File}

import scala.concurrent.duration._

trait Example extends App {
  def process(inF: File, outF: File): Unit

  def using[A, C <: Closeable](c: C)(f: C => A): A =
    try f(c) finally c.close()

  def timed[A](f: => A): (Duration, A) = {
    val start = System.currentTimeMillis
    val result = f
    val end = System.currentTimeMillis
    (end - start).milliseconds -> result
  }

  val (time, _) = timed {
    process(
      new File("mockaroo.csv"),
      new File("emails.txt")
    )
  }
  println(s"Completed in $time")
  println(s"Executed ${Geocoder.requests.get} geocoder requests")
  println(s"Maximum ${Geocoder.maxRequests.get} concurrent requests")
}
