sealed trait Finder

object Finder {
  case class ActiveFinder(
      found: List[DependencySegment],
      toFind: List[DependencySegment]
  ) extends Finder {

    def addEmptySegment = {
      toFind.last match {
        case org: Org =>
          this.copy(toFind = toFind :+ Artifact(""))
        case artifact: Artifact =>
          this.copy(toFind = toFind :+ Version(""))
        case _ => this
      }
    }

    def update(
        justFound: DependencySegment,
        leftover: List[DependencySegment]
    ) = this.copy(found = found :+ justFound, toFind = leftover)

    def updateAndStop(justFound: DependencySegment): StoppedFinder =
      StoppedFinder(found :+ justFound, Right(Right("It exists!")))

    def stop(msg: String): StoppedFinder = StoppedFinder(found, Left(msg))

    def stop(possibles: List[String]): StoppedFinder =
      StoppedFinder(found, Right(Left(possibles)))
  }

  object ActiveFinder {
    def apply(toFind: List[DependencySegment]): ActiveFinder =
      ActiveFinder(List.empty, toFind)
  }

  case class StoppedFinder(
      found: List[DependencySegment],
      ouput: Either[String, Either[List[String], String]]
  ) extends Finder {
    def show(): Unit = {
      if found.nonEmpty then println(s"found: ${found.mkString(" ")}")

      ouput match {
        case Left(msg) => println(s"Something went wrong: $msg")
        case Right(Left(possibles)) =>
          println("Did you mean any of these:")
          possibles.foreach(possible => println(possible.dropRight(1)))
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

    val dep = args.headOption
    val emptyEnding = dep.map(_.endsWith(":")).getOrElse(false)

    dep.map(_.split(":").toSeq) match {
      case Some(Seq(org)) if emptyEnding =>
        ActiveFinder(
          splitOrg(org).map(Org.apply)
        ).addEmptySegment

      case Some(Seq(org)) =>
        ActiveFinder(splitOrg(org).map(Org.apply))

      case Some(Seq(org, name)) if emptyEnding =>
        ActiveFinder(
          splitOrg(org).map(Org.apply) :+ Artifact(name)
        ).addEmptySegment

      case Some(Seq(org, name)) =>
        ActiveFinder(
          splitOrg(org).map(Org.apply) :+ Artifact(name)
        )

      case Some(Seq(org, name, version)) =>
        ActiveFinder(
          splitOrg(org).map(Org.apply) ++ List(
            Artifact(name),
            Version(version)
          )
        )

      case _ =>
        StoppedFinder.fromBadFormat(
          """|Looks like you passed in the wrong shape.
             |
             |Try something like this:
             |
             |exists org.scalameta:metals_2.12:0
             |""".stripMargin
        )
    }
  }
}
