// using scala 3.1.0
// using options -deprecation -feature -explain
// using lib org.jsoup:jsoup:1.14.3

import scala.annotation.tailrec

@main def run(args: String*) =
  args match {
    case Seq() | Seq("help") | Seq("--h") | Seq("-h") => help()
    case args =>
      parseOptions(args) match {
        case finder: Finder.ActiveFinder  => finder.find().show()
        case finder: Finder.StoppedFinder => finder.show()
      }
  }

def help() = {
  val msg = """|Usage: exists [options] [org[:name[:version]]]
               |
               |When you just want to know what exists.
               |Either I'll find it or complete it.
               |
               |Options:
               |
               | -h, --h           shows what you're looking at
               | -r, --repository  specify a repository
               |
               |""".stripMargin

  println(msg)
}

def parseOptions(args: Seq[String]) = {
  @tailrec
  def parse(finder: Finder.ActiveFinder, rest: List[String]): Finder = {
    rest match {
      case "-r" :: repo :: rest => parse(finder.withRepository(repo), rest)
      case "--repository" :: repo :: rest =>
        parse(finder.withRepository(repo), rest)
      case dep :: Nil =>
        DependencySegment
          .fromString(dep)
          .fold(finder.stop, finder.withDeps(_))
      case opts => finder.stop("Unrecognized options")
    }
  }

  parse(Finder.ActiveFinder.empty(), args.toList)
}
