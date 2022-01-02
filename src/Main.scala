// using scala 3.1.0
// using options -deprecation -feature -explain
// using lib org.jsoup:jsoup:1.14.3

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

@main def run(args: String*) =
  args match {
    case Seq() | Seq("help") | Seq("--h") | Seq("-h") => help()
    case args =>
      Finder.fromArgs(args) match {
        case finder: Finder.ActiveFinder =>
          CentralRepository.findWith(finder).show()
        //SonatypeSnapshots.findWith(finder).show()
        case finder: Finder.StoppedFinder => finder.show()
      }
  }
