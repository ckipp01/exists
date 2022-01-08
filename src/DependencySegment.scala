/** The parts of our dependency we'll be searching for. "" is an valid value if you
  *  want to get all option at that level.
  *  @value The value of that segment
  */
enum DependencySegment(val value: String):
  case Org(override val value: String) extends DependencySegment(value)
  case Artifact(override val value: String) extends DependencySegment(value)
  case Version(override val value: String) extends DependencySegment(value)
end DependencySegment

object DependencySegment:
  // TODO An improvement in the future would be to not make org.scalemata into
  // two unrelated segments, but have them be better "joined" but "seperate"
  // since they do need to be seperate since they are at differnet levels when
  // we look at the index page, but the are both the Org.
  private def splitOrg(org: String): List[String] = org.split('.').toList

  type InvalidFormat = String

  def fromString(s: String): Either[InvalidFormat, List[DependencySegment]] =
    val emptyEnding = s.endsWith(":")

    s.split(":").toSeq match
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
    end match
  end fromString

end DependencySegment
