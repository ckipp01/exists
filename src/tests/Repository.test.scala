package io.kipp.exists

import java.net.URI
import java.time.LocalDateTime

import io.kipp.exists.Repository.CentralRepository
import io.kipp.exists.Repository.Entry
import io.kipp.exists.Repository.SonatypeSnapshots

class RepositoryTests extends munit.FunSuite:

  test("entry-order") {
    val now = LocalDateTime.now
    val entryNow = Entry("c", URI("c"), Some(now))
    val entryYesterday = Entry("a", URI("a"), Some(now.minusDays(1)))
    val entryThreeDaysAgo = Entry("b", URI("b"), Some(now.minusDays(3)))

    val entries = List(
      entryYesterday,
      entryThreeDaysAgo,
      entryNow
    )

    assertEquals(
      entries.sorted,
      List(entryThreeDaysAgo, entryYesterday, entryNow)
    )
  }

  test("central") {
    assertEquals(
      Repository.fromString("central"),
      Right(Repository.CentralRepository)
    )
  }

  test("sonatype-snapshots") {
    assert(
      List(
        Repository.fromString("sonatype:snapshot"),
        Repository.fromString("sonatype:snapshots")
      ).forall(_ == Right(Repository.SonatypeSnapshots))
    )
  }

  test("nexus") {
    assert(
      List(
        Repository.fromString("sonatype:foobar"),
        Repository.fromString("nexus:foobar")
      ).forall(_ == Right(Repository.SontatypeNexus(URI("https://foobar/"))))
    )
  }

  test("unrecognized") {
    assertEquals(
      Repository.fromString("blah:foobar"),
      Left("Unrecognized repository.")
    )
  }

end RepositoryTests
