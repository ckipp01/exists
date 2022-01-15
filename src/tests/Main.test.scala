// using lib org.scalameta::munit::1.0.0-M1

package io.kipp.exists

import io.kipp.exists.Finder.StoppedFinder
import io.kipp.exists.Finder.ActiveFinder

class MainTests extends munit.FunSuite:

  lazy val baseArgs = Seq("org.scalameta:metals_2.12:")
  lazy val baseFinder =
    Finder.ActiveFinder
      .empty()
      .withDeps(
        List(
          DependencySegment.Org("org"),
          DependencySegment.Org("scalameta"),
          DependencySegment.Artifact("metals_2.12"),
          DependencySegment.Version("")
        )
      )
  lazy val baseCreds = Creds("username", "password")

  test("creds") {
    val result = parseCreds("username:password")
    assertEquals(
      result,
      Right(baseCreds)
    )
  }

  test("bad-creds") {
    val result = parseCreds("idn")
    assertEquals(
      result,
      Left("""Malformed creds. They should be like "username:password"""")
    )
  }

  test("base") {
    val result: Finder = parseOptions(baseArgs)
    assertEquals(
      result,
      baseFinder
    )
  }

  test("snapshots") {
    val result: Finder =
      parseOptions(Seq("-r", "sonatype:snapshots") ++ baseArgs)
    assertEquals(
      result,
      baseFinder.withRepository("sonatype:snapshots")
    )
  }

  test("repo-creds") {
    val result: Finder =
      parseOptions(
        Seq(
          "-r",
          "sonatype:myCustomerNexusAddres",
          "-c",
          "username:password"
        ) ++ baseArgs
      )
    assertEquals(
      result,
      baseFinder.withRepository("sonatype:nexus").withCreds(baseCreds)
    )
  }

  test("invalid") {
    val result: Finder =
      parseOptions(
        Seq(
          "--wrong",
          "stuff"
        ) ++ baseArgs
      )
    assertEquals(
      result,
      ActiveFinder.empty().stop("Unrecognized options")
    )
  }
end MainTests
