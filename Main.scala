// using scala 3.1.0
// using lib org.jsoup:jsoup:1.14.3
import org.jsoup.Jsoup
import scala.collection.JavaConverters._

val mavenCentral = "https://repo.maven.apache.org/maven2/"

def help() = {
  val msg = """|Usage: exists [org] [name] [version]
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
  def fetch(url: String, finder: Finder.ActiveFinder): Finder.StoppedFinder = {
    val needle :: leftover = finder.toFind

    //println(s"about to look for $url")
    // TODO account for 404
    val doc = Jsoup.connect(url).get()

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
