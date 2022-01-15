import java.net.URI
import java.time.LocalDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

import Finder.StoppedFinder
import Repository.Entry
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

import scala.annotation.tailrec
import scala.collection.JavaConverters.*
import scala.util.Try

/** An ADT of the type of repositories that exist and exists knows how to
  *  traverse through.
  */
sealed trait Repository:
  /** The name of the repository */
  def name: String

  /** The base url of the repository */
  def url: URI

  /** the layout of the index pages are all a bit different. the logic here is
    *  what actually traverses the document and gathers all the valid options
    *  for that segment.
    *
    *  @param doc The document that holds all the goodies aka links
    */
  def gatherPossibles(doc: Document): List[Repository.Entry]

  import Repository.filterPossibles
  import Repository.findNeedleUri

  /** Where the actual parsing of the index pages happen.
    * @param url the url that will be fetched
    * @param finder the finder that is being used to fetch
    */
  @tailrec
  final def fetch(
      url: URI,
      finder: Finder.ActiveFinder
  ): Finder.StoppedFinder =
    val needle :: leftover = finder.toFind

    finder.fetcher.getDoc(url.toString) match
      case Left(msg) => finder.stop(msg)
      case Right(doc) =>
        val all = gatherPossibles(doc).sorted.reverse

        val metadata: Option[Entry] = all
          .collectFirst {
            case entry if entry.value == Metadata.filename => entry
          }

        (
          all.collect {
            case entry if entry.value.startsWith(needle.value) =>
              entry
          },
          needle
        ) match
          case (possibles, _)
              if findNeedleUri(
                possibles,
                needle
              ).nonEmpty && leftover.nonEmpty =>
            val nextUrl = findNeedleUri(possibles, needle).getOrElse(
              URI(url.toString + needle.value + "/")
            )
            fetch(url.resolve(nextUrl), finder.update(needle, leftover))

          case (possibles, DependencySegment.Version(_))
              if findNeedleUri(possibles, needle).nonEmpty =>
            metadata match
              case Some(data) =>
                finder.withMetadata(url.resolve(data.uri)).updateAndStop(needle)
              case None => finder.withMissingMetadata().updateAndStop(needle)

          case (possibles, _) if findNeedleUri(possibles, needle).nonEmpty =>
            finder.updateAndStop(needle)

          case (possibles, DependencySegment.Version(_)) if possibles.isEmpty =>
            val message =
              s"Can't find ${needle.value} or anything that starts with it"
            metadata match
              case Some(data) =>
                finder.withMetadata(url.resolve(data.uri)).stop(message)
              case None => finder.withMissingMetadata().stop(message)

          case (possibles, _) if possibles.isEmpty =>
            finder.stop(
              s"Can't find ${needle.value} or anything that starts with it"
            )

          case (possibles, DependencySegment.Version(_)) =>
            metadata match
              case Some(data) =>
                finder
                  .withMetadata(url.resolve(data.uri))
                  .stop(filterPossibles(possibles))
              case None =>
                finder.withMissingMetadata().stop(filterPossibles(possibles))

          case (possibles, _) => finder.stop(filterPossibles(possibles))

        end match
    end match
  end fetch

  /** Entry point to go ahead and start the fetching process with the current
    *  active finder. Differen repositories have different structures. For
    *  example some like sonatype:snapshots don't allow for the root level
    *  index, sonatype nexus doesn't allow access to indexes in the same url
    *  structure but instead pushes you through the html listing via
    *  /service/rest.a So this method starts the process by ensuring the url
    *  that is passed to fetch is the correct one we can start with.
    */
  def findWith(finder: Finder.ActiveFinder): StoppedFinder =
    fetch(url, finder)

end Repository

object Repository:

  case object CentralRepository extends Repository:
    val name = "central"
    val url = URI("https://repo1.maven.org/maven2/")

    def gatherPossibles(doc: Document): List[Repository.Entry] =
      val dateRegex = """([0-9]{4}-[0-9]{2}-[0-9]{2})"""
      val timeRegex = """([0-9]{2}:[0-9]{2})"""
      val DateTimeAndIgnoreOtherJunk = (dateRegex + " " + timeRegex + ".*").r
      doc
        .select("a")
        .asScala
        .toList
        .map { elem =>
          (
            elem.text,
            elem.attr("href"),
            Try(elem.nextSibling.toString.trim).getOrElse("")
          )
        }
        .map {
          case (value, uri, DateTimeAndIgnoreOtherJunk(date, time)) =>
            Repository.Entry(
              value,
              URI(uri),
              Try(
                LocalDateTime.parse(
                  s"$date $time",
                  DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm")
                )
              ).toOption
            )
          case (value, uri, _) =>
            Repository.Entry(value.trim, URI(uri), None)
        }
    end gatherPossibles
  end CentralRepository

  case object SonatypeReleases extends Repository:
    val name = "sonatype:releases"
    // https://oss.sonatype.org/content/repositories/releases/ will just redirect
    // to the above so we just make them point to exactly the same thing and
    // avoid the redirect
    val url = CentralRepository.url
    def gatherPossibles(doc: Document): List[Repository.Entry] =
      CentralRepository.gatherPossibles(doc)

  case object SonatypeSnapshots extends Repository:
    val name = "sonatype:snapshots"
    val url = URI("https://oss.sonatype.org/content/repositories/snapshots/")

    private val zdtFormatter: DateTimeFormatter =
      DateTimeFormatter.ofPattern("EEE MMM dd HH:mm:ss zzz uuuu")

    override def gatherPossibles(doc: Document): List[Repository.Entry] =
      doc.select("tr").asScala.toList.flatMap { tr =>
        val lastModified =
          tr.select("td:nth-child(2)").text()
        val firstSlot = tr.select("td:nth-child(1) a")
        val text = firstSlot.text()
        val uri = firstSlot.attr("href")

        (lastModified, text, uri) match
          case (lm, t, u) if lm.nonEmpty && t.nonEmpty =>
            val date =
              Try(ZonedDateTime.parse(lm, zdtFormatter)).toOption
                .map(_.toLocalDateTime)
            Some(Repository.Entry(t.trim, URI(u), date))
          case (_, t, u) if t.nonEmpty =>
            Some(Repository.Entry(t.trim, URI(u), None))
          case _ => None
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
                URI(s"$url${segment.value}/"),
                finder.update(segment, finder.toFind.tail)
              )
        case None =>
          finder.stop(
            "Idn, this should be impossible, there should be no way to get here without passing something in."
          )
    end findWith
  end SonatypeSnapshots

  case class SontatypeNexus(url: URI) extends Repository:

    val name = "nexus"

    private val zdtFormatter: DateTimeFormatter =
      DateTimeFormatter.ofPattern("EEE MMM dd HH:mm:ss zzz uuuu")

    override def gatherPossibles(doc: Document): List[Repository.Entry] =
      SonatypeSnapshots.gatherPossibles(doc)

    override def findWith(finder: Finder.ActiveFinder): Finder.StoppedFinder =
      finder.fetcher.getDoc(url.toString) match
        case Left(msg) => finder.stop(msg)
        case Right(doc) =>
          doc
            .select(".nexus-body a")
            .asScala
            .toList
            .collectFirst {
              case link if link.text.trim == "HTML index" =>
                val relUri = URI(link.attr("href"))
                url.resolve(relUri)
            }
            .fold(finder.stop("Unable to find an HTML index"))(uri =>
              fetch(uri, finder)
            )

    end findWith
  end SontatypeNexus

  object SontatypeNexus:
    val CustomNexus = "nexus:(.*)".r

  end SontatypeNexus

  case class Entry(value: String, uri: URI, lastUpdate: Option[LocalDateTime])
      extends Ordered[Entry]:

    override def compare(that: Entry) =
      (lastUpdate, that.lastUpdate) match
        case (None, None) => 0
        case (Some(thisLastUpdated), Some(thatLastUpdated))
            if thisLastUpdated.isAfter(thatLastUpdated) =>
          1
        case (Some(thisLastUpdated), Some(thatLastUpdated))
            if thisLastUpdated.isEqual(thatLastUpdated) =>
          0
        case (Some(thisLastUpdated), Some(thatLastUpdated)) => -1
        case (None, _)                                      => -1
        case _                                              => 1
  end Entry

  /** Parse a string to a repository type. If we don't know it just blow up for
    * now.
    */
  def fromString(name: String) =
    name.toLowerCase match
      case "central" | "sonatype:releases"            => CentralRepository
      case "sonatype:snapshot" | "sonatype:snapshots" => SonatypeSnapshots
      case SontatypeNexus.CustomNexus(url) =>
        if url.endsWith("/") then SontatypeNexus(URI(url))
        else SontatypeNexus(URI(url + "/"))
      case _ => CentralRepository

  private def filterPossibles(possibles: List[Entry]): List[Entry] =
    possibles.filterNot(entry =>
      entry.value.startsWith(
        "maven-metadata"
      ) || entry.value == "Parent Directory" || entry.value == "../"
    )

  private def findNeedleUri(
      possibles: List[Entry],
      needle: DependencySegment
  ) =
    possibles
      .collectFirst {
        case entry
            if entry.value == s"${needle.value}/" || entry.value == needle.value =>
          entry.uri
      }

end Repository
