package io.kipp.exists

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

import org.jsoup.nodes.Document

import scala.util.Try

enum Metadata:
  case Found(latestVersion: String, lasdUpdatedDate: Option[LocalDateTime])
      extends Metadata
  case Missing extends Metadata
end Metadata

object Metadata:

  val preface = "maven-metadata"
  val filename = "maven-metadata.xml"

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
