name := "neo4j-reproduce-797"
scalaVersion := "2.13.4"
organization := "neotypes"

val neo4jDriverVersion = "4.2.0"
val fs2Version = "2.4.6"
val logbackVersion = "1.2.3"

libraryDependencies ++= Seq(
  "org.neo4j.driver" % "neo4j-java-driver" % neo4jDriverVersion,
  "co.fs2" %% "fs2-core" % fs2Version,
  "co.fs2" %% "fs2-reactive-streams" % fs2Version,
  "ch.qos.logback" % "logback-classic" % logbackVersion
)

Compile / run / fork := true
Compile / run / javaOptions += "-XX:ActiveProcessorCount=1"
