sealed trait Finder

object Finder {
  case class ActiveFinder(
      found: List[DependencySegment],
      toFind: List[DependencySegment],
      fetcher: Fetcher,
      repository: Repository
  ) extends Finder {

    def update(
        justFound: DependencySegment,
        leftover: List[DependencySegment]
    ): ActiveFinder = this.copy(found = found :+ justFound, toFind = leftover)

    def updateAndStop(justFound: DependencySegment): StoppedFinder =
      StoppedFinder(found :+ justFound, Right(Right("It exists!")))

    def stop(msg: String): StoppedFinder = StoppedFinder(found, Left(msg))

    def stop(possibles: List[String]): StoppedFinder =
      StoppedFinder(found, Right(Left(possibles)))

    def updateRepository(Repository: Repository) =
      this.copy(repository = repository)

    def find() = repository.findWith(this)

    def withRepository(name: String) = {
      this.copy(repository = Repository.fromString(name))
    }

    def withDeps(deps: List[DependencySegment]) = {
      assert(
        toFind.isEmpty,
        "This should only ever be called once and it looks like it already has been"
      )
      this.copy(toFind = deps)
    }

  }

  object ActiveFinder {
    def apply(
        toFind: List[DependencySegment]
    ): ActiveFinder =
      ActiveFinder(List.empty, toFind, new Fetcher, CentralRepository)

    def empty() = apply(List.empty)
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

  def fromString(dep: String): Finder = {
    DependencySegment.fromString(dep) match {
      case Right(segments) => ActiveFinder(segments)
      case Left(invalid)   => StoppedFinder.fromBadFormat(invalid)
    }
  }
}
