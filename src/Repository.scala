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
import java.net.URI
import Repository.Entry

/** An ADT of the type of repositories that exist and exists knows how to
  *  traverse through.
  *
  *  TODO this could just be an enum but I didn't start it this way and now I
  *  don't feel like switching it
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
          Repository.Entry(value, URI(uri), None)
      }
  end gatherPossibles

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
      case Left(msg)  => finder.stop(msg)
      case Right(doc) =>
        // TODO sort by option?
        // val all = gatherPossibles(doc).sortBy(entry => entry.lastUpdate).reverse
        val all = gatherPossibles(doc)

        val metadata: Option[Entry] = all
          .collectFirst {
            case entry if entry.value.trim == "maven-metadata.xml" => entry
          }

        (
          all.collect {
            case entry if entry.value.startsWith(needle.value) =>
              entry
          },
          needle
        ) match
          case (possibles, _)
              if possibles
                .map(_.value)
                .contains(
                  s"${needle.value}/"
                ) || possibles
                .map(_.value)
                .contains(needle.value) && leftover.nonEmpty =>
            val target = possibles
              .find(possible =>
                possible.value == needle.value || possible.value == needle.value + "/"
              )
              .map(_.uri)
              .getOrElse(
                URI(url.toString + needle.value + "/")
              ) // This should alway be here since we check above

            fetch(url.resolve(target), finder.update(needle, leftover))

          case (possibles, DependencySegment.Version(_))
              if possibles
                .map(_.value)
                .contains(
                  s"${needle.value}/"
                ) || possibles.map(_.value).contains(needle.value) =>
            if metadata.nonEmpty then
              val target =
                metadata.fold(s"${url}maven-metadata.xml")(x => x.uri.toString)
              finder.withMetadata(target).updateAndStop(needle)
            else finder.withMissingMetadata().updateAndStop(needle)

          case (possibles, _)
              if possibles
                .map(_.value)
                .contains(
                  s"${needle.value}/"
                ) || possibles.map(_.value).contains(needle.value) =>
            finder.updateAndStop(needle)

          case (possibles, DependencySegment.Version(_)) if possibles.isEmpty =>
            val message =
              s"Can't find ${needle.value} or anything that starts with it"
            if metadata.nonEmpty then
              val target =
                metadata.fold(s"${url}maven-metadata.xml")(x => x.uri.toString)
              finder
                .withMetadata(target)
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
            if metadata.nonEmpty then
              val target =
                metadata.fold(s"${url}maven-metadata.xml")(x => x.uri.toString)
              finder
                .withMetadata(target)
                .stop(
                  filterPossibles(possibles).map(_.value)
                ) //  TODO send in entries instead
            else
              finder
                .withMissingMetadata()
                .stop(filterPossibles(possibles).map(_.value))

          case (possibles, _) =>
            finder.stop(filterPossibles(possibles).map(_.value))

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

  private def filterPossibles(possibles: List[Repository.Entry]) =
    possibles.filterNot(entry =>
      entry.value.startsWith(
        "maven-metadata"
      ) || entry.value == "Parent Directory" || entry.value == "../"
    )

end Repository

object Repository:

  case class Entry(value: String, uri: URI, lastUpdate: Option[LocalDateTime])

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

  case object CentralRepository extends Repository:
    val name = "central"
    val url = URI("https://repo1.maven.org/maven2/")

  case object SonatypeReleases extends Repository:
    val name = "sonatype:releases"
    // https://oss.sonatype.org/content/repositories/releases/ will just redirect
    // to the above so we just make them point to exactly the same thing and
    // avoid the redirect
    val url = CentralRepository.url

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
            Some(Repository.Entry(t, URI(u), date))
          case (_, t, u) if t.nonEmpty =>
            Some(Repository.Entry(t, URI(u), None))
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

  // https://nexus-acdc.tools.msi.audi.com/service/rest/repository/browse/acdc-releases/

  end SontatypeNexus

  object SontatypeNexus:
    val CustomNexus = "nexus:(.*)".r

  end SontatypeNexus
end Repository
