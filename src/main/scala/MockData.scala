import java.net.InetAddress

case class MockData(
  id: Int,
  firstName: String,
  lastName: String,
  email: String,
  ipAddress: InetAddress
)

object MockData {
  def parse(s: String) = {
    s.split(",") match {
      case Array(id, firstName, lastName, email, ipAddress) =>
        MockData(id.toInt, firstName, lastName, email, InetAddress.getByName(ipAddress))
      case bummer =>
        sys.error(s"Invalid MockData: ${s}")
    }
  }
}
