package io.kipp

import java.net.URI

import io.kipp.exists.*
import io.kipp.exists.Repository.SonatypeSnapshots

class FinderTests extends munit.FunSuite:

  val baseFinder = Finder.ActiveFinder.empty()

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
      baseFinder.update(
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
      baseFinder.updateAndStop(
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
      baseFinder.stop(
        List(
          Repository.Entry(value = "test", uri = URI("test"), lastUpdate = None)
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
      Finder.ActiveFinder(
        found = List.empty,
        toFind = List.empty,
        Fetcher(None),
        Repository.SonatypeSnapshots,
        None
      )
    )
  }

  test("with-deps") {
    assertEquals(
      baseFinder.withDeps(List(DependencySegment.Org("org"))),
      Finder.ActiveFinder(
        found = List.empty,
        toFind = List(DependencySegment.Org("org")),
        Fetcher(None),
        Repository.CentralRepository,
        None
      )
    )
  }

  test("with-creds") {
    assertEquals(
      baseFinder.withCreds(Creds("username", "password")),
      Finder.ActiveFinder(
        found = List.empty,
        toFind = List.empty,
        Fetcher(Some(Creds("username", "password"))),
        Repository.CentralRepository,
        None
      )
    )
  }

  test("missing-metadata") {
    assertEquals(
      baseFinder.withMissingMetadata(),
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
