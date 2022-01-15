import org.jsoup.Jsoup
import org.jsoup.HttpStatusException
import org.jsoup.nodes.Document
import java.util.Base64
import java.nio.charset.StandardCharsets

/** The Fetcher is fully in charge of actually fetching the pages and metadata. Any
  *  actual fetching of anything should be done in here and only in here.
  */
case class Fetcher(creds: Option[Creds]):

  private def base64login(creds: Creds) = Base64.getEncoder.encodeToString(
    (creds.username + ":" + creds.password).getBytes(StandardCharsets.UTF_8)
  )

  private val session = creds match
    case Some(auth) =>
      Jsoup.newSession.header("Authorization", "Basic " + base64login(auth))
    case None => Jsoup.newSession

  // .header("Authorization", "Basic " + base64login)

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
      case x =>
        Left(s"Stopped because: ${x.getMessage}")
  end getDoc
end Fetcher

case class Creds(username: String, password: String)
