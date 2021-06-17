name := "neo4j-reproduce-797"
scalaVersion := "2.13.6"
organization := "neotypes"

val fs2Version = "2.5.6"
val neo4jDriverVersion = "4.3-SNAPSHOT"
val logbackVersion = "1.2.3"

resolvers += Resolver.mavenLocal

libraryDependencies ++= Seq(
  "org.neo4j.driver" % "neo4j-java-driver" % neo4jDriverVersion,
  "co.fs2" %% "fs2-core" % fs2Version,
  "co.fs2" %% "fs2-reactive-streams" % fs2Version,
  "ch.qos.logback" % "logback-classic" % logbackVersion
)

Compile / run / fork := true
Compile / run / javaOptions += "-XX:ActiveProcessorCount=1"
