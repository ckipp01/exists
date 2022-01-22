package io.kipp

import java.net.URI

import io.kipp.exists.*
import io.kipp.exists.Repository.SonatypeSnapshots

class FinderTests extends munit.FunSuite:

  val baseFinder = Finder.PreFinder.empty()

  test("stop") {
    assertEquals(
      baseFinder.stop("stop"),
      Finder.StoppedFinder(
        found = List.empty,
        output = Left("stop"),
        metadata = None
      )
    )
  }

  test("update") {
    assertEquals(
      baseFinder
        .activate()
        .update(
          justFound = DependencySegment.Org("org"),
          leftover = List(DependencySegment.Org("scalameta"))
        ),
      Finder.ActiveFinder(
        found = List(DependencySegment.Org("org")),
        toFind = List(DependencySegment.Org("scalameta")),
        Fetcher(None),
        Repository.CentralRepository,
        None
      )
    )
  }

  test("update-and-stop") {
    assertEquals(
      baseFinder
        .activate()
        .updateAndStop(
          justFound = DependencySegment.Org("org")
        ),
      Finder.StoppedFinder(
        found = List(DependencySegment.Org("org")),
        output = Right(Right("It exists!")),
        metadata = None
      )
    )
  }

  test("stop-with-possiblities") {
    assertEquals(
      baseFinder
        .activate()
        .stop(
          List(
            Repository
              .Entry(value = "test", uri = URI("test"), lastUpdate = None)
          )
        ),
      Finder.StoppedFinder(
        found = List.empty,
        output = Right(Left(List("test"))),
        metadata = None
      )
    )
  }

  test("with-repo") {
    assertEquals(
      baseFinder.withRepository(SonatypeSnapshots),
      Finder.PreFinder(
        toFind = List.empty,
        Fetcher(None),
        Repository.SonatypeSnapshots
      )
    )
  }

  test("with-deps") {
    assertEquals(
      baseFinder.withDeps(List(DependencySegment.Org("org"))),
      Finder.PreFinder(
        toFind = List(DependencySegment.Org("org")),
        Fetcher(None),
        Repository.CentralRepository
      )
    )
  }

  test("with-creds") {
    assertEquals(
      baseFinder.withCreds(Creds("username", "password")),
      Finder.PreFinder(
        toFind = List.empty,
        Fetcher(Some(Creds("username", "password"))),
        Repository.CentralRepository
      )
    )
  }

  test("missing-metadata") {
    assertEquals(
      baseFinder.activate().withMissingMetadata(),
      Finder.ActiveFinder(
        found = List.empty,
        toFind = List.empty,
        Fetcher(None),
        Repository.CentralRepository,
        Some(Metadata.Missing)
      )
    )
  }

// TODO we don't test withMetadata yet becuase we need to mock the fetcher call

end FinderTests
