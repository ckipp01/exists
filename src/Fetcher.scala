import org.jsoup.Jsoup
import org.jsoup.HttpStatusException
import org.jsoup.nodes.Document

class Fetcher {

  val session = Jsoup.newSession

  def getDoc(url: String): Either[String, Document] = {
    try {
      Right(session.newRequest.url(url).get())
    } catch {
      case e: HttpStatusException if e.getStatusCode == 404 =>
        Left(
          s"Stopped because this url can't be found: $url"
        )
      case e: HttpStatusException if e.getStatusCode == 401 =>
        Left(
          s"Stopped because I don't have authorization for: $url"
        )
      case _ =>
        Left(s"Stopped for some unknown reason on: $url")
    }
  }
}
