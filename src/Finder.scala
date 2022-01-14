sealed trait Finder

/** The Finder is meant to server as a mini state-machine of sorts where it
  * fetches what it needs to ensure the org artifact versions is avaible and/or
  * get the possiblities that exists. Once it has those it transitions from an
  * Active Finder to a Stopped Finder
  */
object Finder:

  /** Finder that is in the process of finding a dependency or that availble
    * values for a segment. Once `toFind` becomes emtpy, it will transition to
    * a Stopped finder.
    *
    * @param found the dependency segments that have been found and we know exist
    * @param toFind the dependency segments we have yet to find
    * @param fetcher all fetching of the indexes and metadatafiles happen in here
    * @param repository the type of repository we are looking in
    */
  case class ActiveFinder(
      found: List[DependencySegment],
      toFind: List[DependencySegment],
      fetcher: Fetcher,
      repository: Repository,
      metadata: Option[Metadata]
  ) extends Finder:

    def update(
        justFound: DependencySegment,
        leftover: List[DependencySegment]
    ): ActiveFinder = this.copy(found = found :+ justFound, toFind = leftover)

    def updateAndStop(justFound: DependencySegment): StoppedFinder =
      StoppedFinder(found :+ justFound, Right(Right("It exists!")), metadata)

    def stop(msg: String): StoppedFinder =
      StoppedFinder(found, Left(msg), metadata)

    def stop(possibles: List[String]): StoppedFinder =
      StoppedFinder(found, Right(Left(possibles)), metadata)

    def updateRepository(Repository: Repository) =
      this.copy(repository = repository)

    def find() = repository.findWith(this)

    def withRepository(name: String) =
      this.copy(repository = Repository.fromString(name))

    def withDeps(deps: List[DependencySegment]) =
      assert(
        toFind.isEmpty,
        "This should only ever be called once and it looks like it already has been"
      )
      this.copy(toFind = deps)

    def withMetadata(baseUrl: String) =
      Metadata.parse(fetcher.getDoc(s"${baseUrl}maven-metadata.xml")) match
        case Left(err) =>
          println(s"Something went wrong fetching metadata: $err")
          this
        case Right(metadata) => this.copy(metadata = Some(metadata))

  end ActiveFinder

  object ActiveFinder:
    def apply(
        toFind: List[DependencySegment]
    ): ActiveFinder =
      ActiveFinder(List.empty, toFind, new Fetcher, CentralRepository, None)

    def empty() = apply(List.empty)

  /** Once a Finder has either found everything it needs to or runs into an
    * issue it becomes a StoppedFinder
    *
    * @param found the dependency segments that were found prior to stopping
    * @param output left meaning an issue message about why it was stopped,
    *               or a Left[Left] indicating that an exact artifact was
    *               not found, but these are the possibles, or a Left[Right]
    *               indicating that the exact thing the user was looking for
    *               was found.
    */
  case class StoppedFinder(
      found: List[DependencySegment],
      ouput: Either[String, Either[List[String], String]],
      metadata: Option[Metadata]
  ) extends Finder:
    def show(): Unit =
      if found.nonEmpty then
        val start = "Found up until: "
        val prettyOutput = found.foldLeft(start) { (a, b) =>
          (a, b) match
            case (`start`, DependencySegment.Org(value)) => start + value
            case (partialOrg, DependencySegment.Org(value)) =>
              s"${partialOrg}.${value}"
            case partial => s"${partial._1}:${partial._2.value}"

        }
        println(prettyOutput)

      metadata match
        case None => ()
        case Some(Metadata(version, Some(latest))) =>
          println(s"Latest version according to metadata: $version")
          println(s"Last updated according to metadata: $latest")
        case Some(metadata) =>
          println(
            s"Latest version according to metadata: ${metadata.latestVersion}"
          )

      ouput match
        case Left(msg) => println(s"Something went wrong: $msg")
        case Right(Left(possibles)) =>
          val totalToShow = Math.min(possibles.size, 5)
          println(
            s"Exact match not found, so here are the $totalToShow closest versions with the newest dates:"
          )
          // TODO for now we are just taking 5 so we don't get a giant list
          possibles
            .take(totalToShow)
            .foreach(possible => println(s" ${possible.dropRight(1)}"))
        case Right(Right(msg)) => println(msg)
    end show
  end StoppedFinder

  object StoppedFinder:
    def fromBadFormat(msg: String): StoppedFinder =
      StoppedFinder(List.empty, Left(msg), None)

  def fromString(dep: String): Finder =
    DependencySegment.fromString(dep) match
      case Right(segments) => ActiveFinder(segments)
      case Left(invalid)   => StoppedFinder.fromBadFormat(invalid)
end Finder
