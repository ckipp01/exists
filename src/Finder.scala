package io.kipp.exists

import java.net.URI

import Repository.Entry

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

    /** Update the current finder with the segment that was just found and the
      * leftover segments.
      */
    def update(
        justFound: DependencySegment,
        leftover: List[DependencySegment]
    ): ActiveFinder = this.copy(found = found :+ justFound, toFind = leftover)

    /** Update the current finder with the segment that was just found and then
      * transition to a stopped finder.
      */
    def updateAndStop(justFound: DependencySegment): StoppedFinder =
      StoppedFinder(found :+ justFound, Right(Right("It exists!")), metadata)

    /** Stop the finder with the given error message */
    def stop(msg: String): StoppedFinder =
      StoppedFinder(found, Left(msg), metadata)

    /** Stop the finder since the needle wasn't found, but you have a list of
      * possibles.
      */
    def stop(possibles: List[Entry]): StoppedFinder =
      StoppedFinder(found, Right(Left(possibles.map(_.value))), metadata)

    def find() = repository.findWith(this)

    /** Update the finder with a new repository give the string name. */
    def withRepository(name: String) =
      this.copy(repository = Repository.fromString(name))

    /** Update the finder with the desired dependency segments. */
    def withDeps(deps: List[DependencySegment]) =
      assert(
        toFind.isEmpty,
        "This should only ever be called once and it looks like it already has been"
      )
      this.copy(toFind = deps)

    /** Update the finder by updating the fetcher to have creds */
    def withCreds(creds: Creds) =
      this.copy(fetcher = fetcher.copy(creds = Some(creds)))

    /** Only called when you know the url exists, so this url will be fetched
      * and parsed and then the finder updated with the info.
      */
    def withMetadata(uri: URI) =
      Metadata.parse(fetcher.getDoc(uri.toString)) match
        case Left(err) =>
          println(s"Something went wrong fetching metadata: $err")
          this
        case Right(metadata) => this.copy(metadata = Some(metadata))

    /** Update the finder with a missing metadata if none is found where it's
      * supposed to be.
      */
    def withMissingMetadata() =
      this.copy(metadata = Some(Metadata.Missing))

  end ActiveFinder

  object ActiveFinder:
    def apply(
        toFind: List[DependencySegment]
    ): ActiveFinder =
      ActiveFinder(
        List.empty,
        toFind,
        new Fetcher(None),
        Repository.CentralRepository,
        None
      )

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
      output: Either[
        String,
        Either[List[String], String]
      ], // TODO maybe Right[Left] should be an entry instead of a string
      metadata: Option[Metadata]
  ) extends Finder:

    /** Prints out all the info we've gathered up until this became a stopped finder.
      */
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
        case Some(Metadata.Found(version, Some(latest))) =>
          println(s"Latest version according to metadata: $version")
          println(s"Last updated according to metadata: $latest")
        case Some(Metadata.Found(version, _)) =>
          println(
            s"Latest version according to metadata: $version"
          )
        case Some(Metadata.Missing) =>
          println(
            "No mavemen-metadata.xml file was found for this artifact"
          )

      output match
        case Left(msg)              => println(s"Something went wrong: $msg")
        case Right(Left(possibles)) =>
          // TODO this could be a huge list. So for sanity we just do 10. Make
          // this configurable later
          val toTake = Math.min(10, possibles.size)
          println(
            s"Exact match not found, so here are the $toTake newest possiblities:"
          )
          possibles
            .take(toTake)
            .foreach(possible => println(s" ${possible.stripSuffix("/")}"))
        case Right(Right(msg)) => println(msg)
    end show
  end StoppedFinder
end Finder
