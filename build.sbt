name := "neo4j-reproduce-797"
scalaVersion := "2.13.4"
organization := "neotypes"

val neo4jDriverVersion = "4.2.0"
val logbackVersion = "1.2.3"

libraryDependencies ++= Seq(
  "org.neo4j.driver" % "neo4j-java-driver" % neo4jDriverVersion,
  "ch.qos.logback" % "logback-classic" % logbackVersion
)
