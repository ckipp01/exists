import org.jsoup.nodes.Document
import org.jsoup.Jsoup

import scala.collection.JavaConverters.*
import scala.annotation.tailrec
import Finder.StoppedFinder
import org.jsoup.nodes.Element
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import scala.util.Try
import java.time.ZonedDateTime

/** An ADT of the type of repositories that exist and exists knows how to
  *  traverse through.
  */
sealed trait Repository:
  /** The name of the repository */
  def name: String

  /** The url of the repository */
  def url: String

  def gatherPossibles(doc: Document): List[(String, LocalDateTime)] =
    val dateRegex = """([0-9]{4}-[0-9]{2}-[0-9]{2})"""
    val timeRegex = """([0-9]{2}:[0-9]{2})"""
    val DateTimeAndIgnoreOtherJunk = (dateRegex + " " + timeRegex + ".*").r
    doc
      .select("a")
      .asScala
      .toList
      .map { elem =>
        (elem.text, Try(elem.nextSibling.toString.trim).getOrElse(""))
      }
      .map {
        case (version, DateTimeAndIgnoreOtherJunk(date, time)) =>
          (
            version,
            // TODO this can throw
            LocalDateTime.parse(
              s"$date $time",
              DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm")
            )
          )
        case (version, _) =>
          (version, LocalDateTime.now)
      }
  end gatherPossibles

  /** Where the actual parsing of the index pages happen.
    * @param url the url that will be fetched
    * @param finder the finder that is being used to fetch
    */
  @tailrec
  final def fetch(
      url: String,
      finder: Finder.ActiveFinder
  ): Finder.StoppedFinder =
    val needle :: leftover = finder.toFind

    finder.fetcher.getDoc(url) match
      case Left(msg) => finder.stop(msg)
      case Right(doc) =>
        val all = gatherPossibles(doc).sortBy(_._2).reverse
        val metadataExists = containsMetadata(all.map(_._1))

        (
          all.collect {
            case (version, date) if version.startsWith(needle.value) =>
              version
          },
          needle
        ) match
          case (possibles, _)
              if possibles.contains(
                s"${needle.value}/"
              ) && leftover.nonEmpty =>
            fetch(s"$url${needle.value}/", finder.update(needle, leftover))

          case (possibles, DependencySegment.Version(_))
              if possibles.contains(s"${needle.value}/") =>
            if metadataExists then
              finder.withMetadata(url).updateAndStop(needle)
            else finder.withMissingMetadata().updateAndStop(needle)

          case (possibles, _) if possibles.contains(s"${needle.value}/") =>
            finder.updateAndStop(needle)

          case (possibles, DependencySegment.Version(_)) if possibles.isEmpty =>
            val message =
              s"Can't find ${needle.value} or anything that starts with it"
            if metadataExists then
              finder
                .withMetadata(url)
                .stop(message)
            else
              finder
                .withMissingMetadata()
                .stop(message)

          case (possibles, _) if possibles.isEmpty =>
            finder.stop(
              s"Can't find ${needle.value} or anything that starts with it"
            )

          case (possibles, DependencySegment.Version(_)) =>
            if metadataExists then
              finder
                .withMetadata(url)
                .stop(filterPossibles(possibles))
            else finder.withMissingMetadata().stop(filterPossibles(possibles))

          case (possibles, _) =>
            finder.stop(filterPossibles(possibles))

        end match
    end match
  end fetch

  def findWith(finder: Finder.ActiveFinder): StoppedFinder = fetch(url, finder)

  private def filterPossibles(possibles: List[String]) =
    possibles.filterNot(possible =>
      possible.startsWith(
        "maven-metadata"
      ) || possible == "Parent Directory" || possible == "../"
    )

  private def containsMetadata(links: List[String]): Boolean =
    links.contains("maven-metadata.xml")

end Repository

object Repository:
  def fromString(name: String) =
    name.toLowerCase match
      case "central" | "sonatype:releases"            => CentralRepository
      case "sonatype:snapshot" | "sonatype:snapshots" => SonatypeSnapshots
      case _                                          => CentralRepository

case object CentralRepository extends Repository:
  val name = "central"
  val url = "https://repo1.maven.org/maven2/"
  def startUrl(segments: List[DependencySegment]) = url

case object SonatypeReleases extends Repository:
  val name = "sonatype:releases"
  // https://oss.sonatype.org/content/repositories/releases/ will just redirect
  // to the above so we just make them point to exactly the same thing and
  // avoid the redirect
  val url = CentralRepository.url
  def startUrl(segments: List[DependencySegment]) = url

case object SonatypeSnapshots extends Repository:
  val name = "sonatype:snapshots"
  val url = "https://oss.sonatype.org/content/repositories/snapshots/"
  def startUrl(segments: List[DependencySegment]) =
    assert(segments.nonEmpty)
    s"$url${segments.head.value}/"

  private val zdtFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEE MMM dd HH:mm:ss zzz uuuu")

  override def gatherPossibles(doc: Document): List[(String, LocalDateTime)] =
    doc.select("tr").asScala.toList.flatMap { tr =>
      val lastModified =
        tr.select("td:nth-child(2)").text()
      val version =
        tr.select("td:nth-child(1)").text()
      if lastModified.nonEmpty then
        val date: ZonedDateTime =
          ZonedDateTime.parse(lastModified, zdtFormatter)
        List((version, date.toLocalDateTime))
      else List.empty
    }

  end gatherPossibles

  override def findWith(finder: Finder.ActiveFinder): Finder.StoppedFinder =
    val firstSegment = finder.toFind.headOption

    firstSegment match
      case Some(segment) =>
        finder.fetcher.getDoc(s"$url${segment.value}/") match
          case Left(msg) => finder.stop(msg)
          case Right(_) =>
            fetch(
              s"$url${segment.value}/",
              finder.update(segment, finder.toFind.tail)
            )
      case None =>
        finder.stop(
          "Idn, this should be impossible, there should be no way to get here without passing something in."
        )
  end findWith
end SonatypeSnapshots
