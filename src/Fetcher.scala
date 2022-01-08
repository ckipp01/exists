import org.jsoup.Jsoup
import org.jsoup.HttpStatusException
import org.jsoup.nodes.Document

/** The Fetcher is fully in charge of actually fetching the pages and metadata. Any
  *  actual fetching of anything should be done in here and only in here.
  */
class Fetcher:
  private val session = Jsoup.newSession

  /** The actual fetching of the page content. We don't do any parsing or
    *  anything here. We literally just grab it, ensure we didn't get an error
    *  doing it and then return the document.
    *
    *  @param url The url that we want to fetch from
    *  @return Either an error message or the Document
    */
  def getDoc(url: String): Either[String, Document] =
    try Right(session.newRequest.url(url).get())
    catch
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
end Fetcher
