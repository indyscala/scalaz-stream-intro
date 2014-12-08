import java.io._

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

object NaiveExample extends Example {
  def process(inF: File, outF: File): Unit = {
    val in = new BufferedReader(new FileReader(inF))
    try {
      val out = new PrintWriter(new FileWriter(outF), true)
      try {
        var line: String = in.readLine() // Skip the header
        var i = 20
        while ({ line = in.readLine(); line != null && !line.contains('"') && i > 0 }) {
          val mockData = MockData.parse(line)
          val country = Await.result(Geocoder(mockData.ipAddress), 5.seconds)
          if (country == Country("United States")) {
            out.println(mockData.email)
            i = i - 1
          }
        }
      } finally out.close()
    } finally in.close()
  }
}
