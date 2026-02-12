name := "pa-opsramp"
version := "1.0.0"
scalaVersion := "2.12.18"

// Common settings for all modules
lazy val commonSettings = Seq(
  organization := "com.opsramp",
  scalaVersion := "2.12.18",
  scalacOptions ++= Seq("-deprecation", "-feature", "-unchecked")
)

val sparkVersion = "3.5.0"
val deltaVersion = "3.0.0"

// Common Spark dependencies - use compile for local dev, provided for assembly
lazy val sparkDependencies = Seq(
  "org.apache.spark" %% "spark-core" % sparkVersion,
  "org.apache.spark" %% "spark-sql" % sparkVersion
)

// Common utility dependencies
lazy val commonDependencies = Seq(
  "io.delta" %% "delta-spark" % deltaVersion,
  "org.apache.hadoop" % "hadoop-aws" % "3.3.4",
  "com.amazonaws" % "aws-java-sdk-bundle" % "1.12.262"
)

// Root project - aggregates all modules
lazy val root = (project in file("."))
  .settings(commonSettings)
  .settings(
    name := "pa-opsramp",
    // Don't publish root project
    publish / skip := true
  )
  .aggregate(s3ToDeltaJob)

// S3 to Delta Lake Job Module
lazy val s3ToDeltaJob = (project in file("jobs/s3-to-delta"))
  .settings(commonSettings)
  .settings(
    name := "s3-to-delta-job",
    libraryDependencies ++= sparkDependencies ++ commonDependencies,

    // For local runs, include Spark dependencies
    Compile / run / fork := true,
    run / javaOptions ++= Seq(
      "-Xmx4G",
      "--add-opens=java.base/java.lang=ALL-UNNAMED",
      "--add-opens=java.base/java.lang.invoke=ALL-UNNAMED",
      "--add-opens=java.base/java.lang.reflect=ALL-UNNAMED",
      "--add-opens=java.base/java.io=ALL-UNNAMED",
      "--add-opens=java.base/java.net=ALL-UNNAMED",
      "--add-opens=java.base/java.nio=ALL-UNNAMED",
      "--add-opens=java.base/java.util=ALL-UNNAMED",
      "--add-opens=java.base/java.util.concurrent=ALL-UNNAMED",
      "--add-opens=java.base/java.util.concurrent.atomic=ALL-UNNAMED",
      "--add-opens=java.base/sun.nio.ch=ALL-UNNAMED",
      "--add-opens=java.base/sun.nio.cs=ALL-UNNAMED",
      "--add-opens=java.base/sun.security.action=ALL-UNNAMED",
      "--add-opens=java.base/sun.util.calendar=ALL-UNNAMED"
    ),

    // Assembly settings for creating fat jar
    assembly / assemblyMergeStrategy := {
      case PathList("META-INF", xs @ _*) => MergeStrategy.discard
      case "reference.conf" => MergeStrategy.concat
      case x => MergeStrategy.first
    },

    assembly / assemblyJarName := "s3-to-delta-assembly.jar"
  )

// You can add more job modules here in the future:
// lazy val anotherJob = (project in file("jobs/another-job"))
//   .settings(commonSettings)
//   .settings(...)
