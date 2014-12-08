import java.net.{Inet4Address, InetAddress}
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.{TimeUnit, Executors}

import scala.concurrent.{Promise, ExecutionContext, Future}
import scala.util.{Failure, Success, Random}
import scalaz.{-\/, \/-}
import scalaz.concurrent.Task

// It's slow and a bit dated, but it'll work for a demo.
object Geocoder {
  val scheduler = Executors.newScheduledThreadPool(8)
  val requests = new AtomicInteger(0)
  val currentRequests = new AtomicInteger(0)
  val maxRequests = new AtomicInteger(0)

  def apply(address: InetAddress)(implicit ec: ExecutionContext): Future[Country] = {
    requests.incrementAndGet()

    val current = currentRequests.incrementAndGet()
    def updateMax: Unit = {
      val currentMax = maxRequests.get
      if (current > currentMax)
        if (!maxRequests.compareAndSet(currentMax, current))
          updateMax
    }
    updateMax

    address match {
      case ipv4: Inet4Address =>
        val p = Promise[Country]()
        scheduler.schedule(new Runnable {
          def run = {
            p.success(Country(((ipv4.getAddress()(3) + 128) % 10) match {
              case 0 | 1 => "United States"
              case 2 => "Ottoman Empire"
              case 3 | 4 | 5 => "Yugoslavia"
              case 6 => "Holy Roman Empire"
              case 7 => "Dahomey"
              case 8 => "East Germany"
              case 9 => "Narnia"
            }))
            currentRequests.decrementAndGet()
          }
        }, math.max((Random.nextGaussian * 10.0 + 100.0).toInt, 0), TimeUnit.MILLISECONDS)
        p.future
      case ipv6 => Future.successful(Country("Canada"))
    }
  }

  // https://github.com/scalaz/scalaz/pull/853/files
  def fromStdFuture[A](fa: => Future[A])(implicit ec: ExecutionContext): Task[A] =
    Task.async(register =>
      fa.onComplete({
        case Success(a) => register(\/-(a))
        case Failure(t) => register(-\/(t))
      })(ec)
    )

  def task(address: InetAddress)(implicit ec: ExecutionContext): Task[Country] =
    fromStdFuture(apply(address))(ec)
}

case class Country(name: String)
