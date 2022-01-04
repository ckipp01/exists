// using scala 3.1.0
// using options -deprecation -feature -explain
// using lib org.jsoup:jsoup:1.14.3

@main def run(args: String*) =
  args match {
    case Seq() | Seq("help") | Seq("--h") | Seq("-h") => help()
    case args =>
      Finder.fromString(args.head) match {
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
               |help, -h, --h      shows what you're looking at
               |
               |""".stripMargin

  println(msg)
}
