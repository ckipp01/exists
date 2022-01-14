import org.jsoup.nodes.Document
import java.time.LocalDateTime
import scala.util.Try
import java.time.format.DateTimeFormatter

enum Metadata:
  case Found(latestVersion: String, lasdUpdatedDate: Option[LocalDateTime])
      extends Metadata
  case Missing extends Metadata
end Metadata

object Metadata:

  private val mavenMetadataLastUpdatedFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("uuuuMMddHHmmss")

  def parse(document: Either[String, Document]): Either[String, Metadata] =
    document match
      case Right(doc) =>
        val latest = doc.select("latest").text

        val lastUpdated =
          Option(
            LocalDateTime.parse(
              doc.select("lastUpdated").text().trim,
              mavenMetadataLastUpdatedFormatter
            )
          )

        Right(Metadata.Found(latest, lastUpdated))
      case Left(err) => Left(err)
  end parse

end Metadata
