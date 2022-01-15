// using scala 3.1.0
// using options -deprecation -feature -explain
// using lib org.jsoup:jsoup:1.14.3

import scala.annotation.tailrec

@main def run(args: String*): Unit =
  args match
    case Seq() | Seq("help") | Seq("--h") | Seq("-h") | Seq("--help") => help()
    case args =>
      parseOptions(args) match
        case finder: Finder.ActiveFinder  => finder.find().show()
        case finder: Finder.StoppedFinder => finder.show()

def help() =
  val msg = """|Usage: exists [options] [org[:name[:version]]]
               |
               |When you just want to know what exists.
               |Either I'll find it or complete it.
               |
               |Options:
               |
               | -h, --help        shows what you're looking at
               | -c, --credentials credentials for the passed in repository
               | -r, --repository  specify a repository
               |
               |""".stripMargin

  println(msg)
end help

/** We don't use a library for cli parsing since we do so little, so we just
  *  recurse over the args and strip out what we need to build up our finder
  *
  * @param args Args passed in from the user
  */
def parseOptions(args: Seq[String]): Finder =
  @tailrec
  def parse(finder: Finder.ActiveFinder, rest: List[String]): Finder =
    rest match
      case "-r" :: repo :: rest => parse(finder.withRepository(repo), rest)
      case "--repository" :: repo :: rest =>
        parse(finder.withRepository(repo), rest)
      case "-c" :: credentials :: rest =>
        parseCreds(credentials) match
          case Left(msg)    => finder.stop(msg)
          case Right(creds) => parse(finder.withCreds(creds), rest)
      case "--credentials" :: credentials :: rest =>
        parseCreds(credentials) match
          case Left(msg)    => finder.stop(msg)
          case Right(creds) => parse(finder.withCreds(creds), rest)
      case dep :: Nil =>
        DependencySegment
          .fromString(dep)
          .fold(finder.stop, finder.withDeps)
      case opts => finder.stop("Unrecognized options")

  parse(Finder.ActiveFinder.empty(), args.toList)
end parseOptions

def parseCreds(credentials: String): Either[String, Creds] =
  credentials.split(":").toList match
    case user :: pass :: Nil =>
      Right(Creds(user, pass))
    case _ =>
      Left("""Malformed creds. They should be like "username:password"""")
