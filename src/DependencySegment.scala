sealed trait DependencySegment {
  def value: String
}

//  TODO we should find a way that instead of a value, we can group the parts
//  because right now org.scalameta results in two seperate Orgs
case class Org(value: String) extends DependencySegment
case class Artifact(value: String) extends DependencySegment
case class Version(value: String) extends DependencySegment
