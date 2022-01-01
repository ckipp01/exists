// using scala 3.1.0
// using lib org.jsoup:jsoup:1.14.3
// TODO figure out what Scala 3 scalac options we want for better warnings

import org.jsoup.Jsoup
import scala.collection.JavaConverters._
import scala.util.Try
import org.jsoup.HttpStatusException
import org.jsoup.nodes.Document
import scala.annotation.tailrec

val mavenCentral = "https://repo.maven.apache.org/maven2/"

def help() = {
  val msg = """|Usage: exists [org[:name[:version]]]
               |
               |When you just want to know what exists.
               |Either I'll find it or complete it.
               |
               |Commands:
               |
               |help, -h, --h      shows what you're looking at
               |""".stripMargin

  println(msg)
}

def find(finder: Finder.ActiveFinder): Finder.StoppedFinder = {
  val session = Jsoup.newSession

  @tailrec
  def fetch(url: String, finder: Finder.ActiveFinder): Finder.StoppedFinder = {
    val needle :: leftover = finder.toFind

    // TODO do we need to sanitize or worry about anything here?
    val possibleDoc: Either[Finder.StoppedFinder, Document] =
      try {
        Right(session.newRequest.url(url).get())
      } catch {
        case e: HttpStatusException if e.getStatusCode == 404 =>
          Left(finder.stop(s"Stopped because this url can't be found: $url"))
        case e: HttpStatusException if e.getStatusCode == 401 =>
          Left(
            finder.stop(s"Stopped because I don't have authorization for: $url")
          )
        case _ => Left(finder.stop(s"Stopped for some unknown reason on: $url"))
      }

    possibleDoc match {
      case Left(stopped) => stopped
      case Right(doc) =>
        val allLinks = doc
          .select("a")
          .asScala
          .toList

        allLinks.collect {
          case elem if elem.attr("title").startsWith(needle.value) =>
            elem.attr("title")
        } match {
          case possibles
              if possibles.contains(s"${needle.value}/") && leftover.nonEmpty =>
            fetch(s"$url${needle.value}/", finder.update(needle, leftover))
          case possibles if possibles.contains(s"${needle.value}/") =>
            finder.updateAndStop(needle)
          case possibles if possibles.isEmpty =>
            finder.stop(
              s"Can't find ${needle.value} or anything that starts with it"
            )
          case possibles =>
            finder.stop(possibles.filterNot(_.startsWith("maven-metadata")))
        }
    }
  }

  fetch(mavenCentral, finder)
}

def findAndShow(finder: Finder): Unit = {
  finder match {
    case active: Finder.ActiveFinder   => find(active).show()
    case stopped: Finder.StoppedFinder => stopped.show()
  }
}

@main def run(args: String*) =
  args match {
    case Seq() | Seq("help") | Seq("--h") | Seq("-h") => help()
    case args => findAndShow(Finder.fromArgs(args))
  }
