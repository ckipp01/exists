// using scala 3.1.0
// using lib org.jsoup:jsoup:1.14.3
import org.jsoup.Jsoup
import scala.collection.JavaConverters._
import scala.util.Try
import org.jsoup.HttpStatusException
import org.jsoup.nodes.Document

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

def find(finder: Finder.ActiveFinder): Unit = {
  val session = Jsoup.newSession

  def fetch(url: String, finder: Finder.ActiveFinder): Finder.StoppedFinder = {
    val needle :: leftover = finder.toFind

    // TODO do we need to sanitize or worry about anything here?
    // TODO move this out of here
    // TODO we should use a new session for this later to re-use the auth
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

        val possibles: List[String] = allLinks.collect {
          case elem if elem.attr("title").startsWith(needle) =>
            elem.attr("title")
        }

        if (possibles.contains(s"$needle/") && leftover.nonEmpty) {
          fetch(s"$url$needle/", finder.update(needle, leftover))
        } else if (possibles.contains(s"$needle/")) {
          // We can _maybe_ assume the metadata file is here as well since there is none left
          finder.updateAndStop(needle)
        } else if (possibles.isEmpty) {
          finder.stop(s"Can't find $needle or anything like it")
        } else {
          finder.stop(possibles) // TODO do we want o track the needle?
        }
    }
  }

  val result = fetch(mavenCentral, finder)
  result.show()
}

def exists(finder: Finder): Unit = {
  finder match {
    case Finder.StoppedFinder(found, Left(msg)) => println(msg)
    case Finder.StoppedFinder(found, Right(Left(possibles))) =>
      possibles.foreach(println)
    case Finder.StoppedFinder(found, Right(Right(msg))) =>
      println(msg) // TODO maybe use found
    case active @ Finder.ActiveFinder(_, _) => find(active)
  }

}

@main def run(args: String*) = {

  args match {
    case Seq() | Seq("help") | Seq("--h") | Seq("-h") => help()
    case args => exists(Finder.fromArgs(args))
  }

}
