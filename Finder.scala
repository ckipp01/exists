sealed trait Finder

object Finder {
  case class ActiveFinder(found: List[String], toFind: List[String])
      extends Finder {
    def update(justFound: String, leftover: List[String]) =
      this.copy(found = found :+ justFound, toFind = leftover)
    def updateAndStop(justFound: String): StoppedFinder =
      StoppedFinder(found :+ justFound, Right(Right("It exists!")))
    def stop(msg: String): StoppedFinder = StoppedFinder(found, Left(msg))
    def stop(possibles: List[String]): StoppedFinder =
      StoppedFinder(found, Right(Left(possibles)))
  }

  object ActiveFinder {
    def apply(toFind: List[String]): ActiveFinder =
      ActiveFinder(List.empty, toFind)
  }

  case class StoppedFinder(
      found: List[String],
      ouput: Either[String, Either[List[String], String]]
  ) extends Finder {
    def show(): Unit = {
      println(s"found: ${found.mkString(" ")}")

      ouput match {
        case Left(msg) => println(s"something went wrong: $msg")
        case Right(Left(possibles)) =>
          println("did you mean any of these:")
          possibles.foreach(println)
        case Right(Right(msg)) => println(msg)
      }

    }
  }

  object StoppedFinder {
    def fromBadFormat(msg: String): StoppedFinder =
      StoppedFinder(List.empty, Left(msg))
  }

  private def splitOrg(org: String): List[String] = org.split('.').toList

  def fromArgs(args: Seq[String]): Finder = {

    args match {
      case Seq(org)       => ActiveFinder(splitOrg(org))
      case Seq(org, name) => ActiveFinder(splitOrg(org) :+ name)
      case Seq(org, name, version) =>
        ActiveFinder(splitOrg(org) ++ List(name, version))
      case _ =>
        StoppedFinder.fromBadFormat(
          """|Looks like you passed in the wrong shape.
             |
             |Try something like this:
             |
             |exists org.scalameta metals_2.12 0
             |""".stripMargin
        )
    }

  }
}
