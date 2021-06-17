name := "neo4j-reproduce-797"
scalaVersion := "2.13.6"
organization := "neotypes"

val akkaStreamVersion = "2.6.15"
val neo4jDriverVersion = "4.3-SNAPSHOT"
val logbackVersion = "1.2.3"

resolvers += Resolver.mavenLocal

libraryDependencies ++= Seq(
  "org.neo4j.driver" % "neo4j-java-driver" % neo4jDriverVersion,
  "com.typesafe.akka" %% "akka-stream" % akkaStreamVersion,
  "ch.qos.logback" % "logback-classic" % logbackVersion
)
