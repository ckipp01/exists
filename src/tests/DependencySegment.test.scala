package io.kipp.exists

class DependencySegmentTests extends munit.FunSuite:

  test("invalid") {
    assertEquals(
      DependencySegment.fromString("wrong-format:stuff:idn:one more"),
      Left(
        """|Invalid format for dep.
             |
             |Try something like this:
             |
             |org.scalameta:metals_2.12:0
             |""".stripMargin
      )
    )
  }

  test("org-only") {
    assertEquals(
      DependencySegment.fromString("org"),
      Right(List(DependencySegment.Org("org")))
    )
  }

  test("artifact") {
    assertEquals(
      DependencySegment.fromString("org.scalameta:metals_2.12"),
      Right(
        List(
          DependencySegment.Org("org"),
          DependencySegment.Org("scalameta"),
          DependencySegment.Artifact("metals_2.12")
        )
      )
    )
  }

  test("empty-version") {
    assertEquals(
      DependencySegment.fromString("org.scalameta:metals_2.12:"),
      Right(
        List(
          DependencySegment.Org("org"),
          DependencySegment.Org("scalameta"),
          DependencySegment.Artifact("metals_2.12"),
          DependencySegment.Version("")
        )
      )
    )
  }

  test("version") {
    assertEquals(
      DependencySegment.fromString("org.scalameta:metals_2.12:0"),
      Right(
        List(
          DependencySegment.Org("org"),
          DependencySegment.Org("scalameta"),
          DependencySegment.Artifact("metals_2.12"),
          DependencySegment.Version("0")
        )
      )
    )
  }

end DependencySegmentTests
