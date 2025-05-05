ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "2.12.18"

lazy val root = (project in file("."))
  .settings(
    name := "recommendationSystem"
  )

libraryDependencies ++= Seq(
  // Spark Libraries
  "org.apache.spark" %% "spark-core" % "3.3.2",
  "org.apache.spark" %% "spark-sql" % "3.3.2",
  "org.apache.spark" %% "spark-mllib" % "3.3.2",

  // Akka HTTP for REST API
  "com.typesafe.akka" %% "akka-http" % "10.2.9",
  "com.typesafe.akka" %% "akka-stream" % "2.6.20",

  // Spray JSON for JSON serialization
  "io.spray" %% "spray-json" % "1.3.6",
  "com.typesafe" % "config" % "1.4.3"
)

// Needed for Spark with Java 17+
Compile / run / fork := true
Compile / run / javaOptions ++= Seq(
  "--add-exports", "java.base/sun.nio.ch=ALL-UNNAMED"
)
