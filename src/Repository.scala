import org.jsoup.nodes.Document
import org.jsoup.Jsoup

import scala.collection.JavaConverters.*
import scala.annotation.tailrec

/** An ADT of the type of repositories that exist and exists knows how to
  *  traverse through.
  */
sealed trait Repository:
  /** The name of the repository */
  def name: String

  /** The url of the repository */
  def url: String

  @tailrec
  final def fetch(
      url: String,
      finder: Finder.ActiveFinder
  ): Finder.StoppedFinder =
    val needle :: leftover = finder.toFind

    finder.fetcher.getDoc(url) match
      case Left(msg) => finder.stop(msg)
      case Right(doc) =>
        val allLinks = doc
          .select("a")
          .asScala
          .toList

        (
          allLinks.collect {
            case elem if elem.text.startsWith(needle.value) =>
              elem.text
          },
          needle
        ) match
          case (possibles, _)
              if possibles.contains(
                s"${needle.value}/"
              ) && leftover.nonEmpty =>
            fetch(s"$url${needle.value}/", finder.update(needle, leftover))

          case (possibles, DependencySegment.Version(_))
              if possibles.contains(s"${needle.value}/") && allLinks
                .find(
                  _.text == "maven-metadata.xml"
                )
                .nonEmpty =>
            finder
              .withMetadata(s"${url}maven-metadata.xml")
              .updateAndStop(needle)

          case (possibles, DependencySegment.Version(_))
              if possibles.contains(s"${needle.value}/") =>
            println("missing a maven metadata file!!!!")
            finder.updateAndStop(needle)

          case (possibles, _) if possibles.contains(s"${needle.value}/") =>
            finder.updateAndStop(needle)

          case (possibles, DependencySegment.Version(_))
              if possibles.isEmpty && allLinks
                .find(_.text == "maven-metadata.xml")
                .nonEmpty =>
            finder
              .withMetadata(s"${url}maven-metadata.xml")
              .stop(
                s"Can't find ${needle.value} or anything that starts with it"
              )

          case (possibles, DependencySegment.Version(_)) if possibles.isEmpty =>
            println("missing a maven metadata file!!!!")
            finder
              .withMetadata(s"${url}maven-metadata.xml")
              .stop(
                s"Can't find ${needle.value} or anything that starts with it"
              )

          case (possibles, _) if possibles.isEmpty =>
            finder.stop(
              s"Can't find ${needle.value} or anything that starts with it"
            )

          case (possibles, DependencySegment.Version(_))
              if allLinks
                .find(_.text == "maven-metadata.xml")
                .nonEmpty =>
            finder
              .withMetadata(s"${url}maven-metadata.xml")
              .stop(
                possibles.filterNot(possible =>
                  possible.startsWith(
                    "maven-metadata"
                  ) || possible == "Parent Directory" || possible == "../"
                )
              )

          case (possibles, DependencySegment.Version(_)) =>
            println("missing a maven metadata file!!!!")
            finder
              .stop(
                possibles.filterNot(possible =>
                  possible.startsWith(
                    "maven-metadata"
                  ) || possible == "Parent Directory" || possible == "../"
                )
              )

          case (possibles, _) =>
            finder.stop(
              possibles.filterNot(possible =>
                possible.startsWith(
                  "maven-metadata"
                ) || possible == "Parent Directory" || possible == "../"
              )
            )
        end match
    end match
  end fetch

  def findWith(finder: Finder.ActiveFinder) = fetch(url, finder)
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
