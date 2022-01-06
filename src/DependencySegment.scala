sealed trait DependencySegment {
  def value: String
}

object DependencySegment {
  private def splitOrg(org: String): List[String] = org.split('.').toList

  type InvalidFormat = String

  def fromString(s: String): Either[InvalidFormat, List[DependencySegment]] = {
    val emptyEnding = s.endsWith(":")

    s.split(":").toSeq match {
      case Seq(org) if emptyEnding =>
        Right(splitOrg(org).map(Org.apply) :+ Artifact(""))
      case Seq(org) => Right(splitOrg(org).map(Org.apply))
      case Seq(org, name) if emptyEnding =>
        Right(
          splitOrg(org).map(Org.apply) ::: List(Artifact(name), Version(""))
        )
      case Seq(org, name) =>
        Right(splitOrg(org).map(Org.apply) :+ Artifact(name))
      case Seq(org, name, version) =>
        Right(
          splitOrg(org).map(Org.apply) ::: List(
            Artifact(name),
            Version(version)
          )
        )
      case _ =>
        Left(
          """|Invalid format for dep.
             |
             |Try something like this:
             |
             |org.scalameta:metals_2.12:0
             |""".stripMargin
        )

    }
  }
}

//  TODO we should find a way that instead of a value, we can group the parts
//  because right now org.scalameta results in two seperate Orgs
case class Org(value: String) extends DependencySegment
case class Artifact(value: String) extends DependencySegment
case class Version(value: String) extends DependencySegment
