import com.typesafe.sbt.SbtGit.git
import Versions._

ThisBuild / organization  := "com.brane"
ThisBuild / version := "0.1.0-SNAPSHOT"

// For cross builds see https://www.scala-sbt.org/1.x/docs/Cross-Build.html
//crossScalaVersions in ThisBuild := Seq("2.12.12", "2.11.12","2.13.5")
ThisBuild / crossScalaVersions := Seq("2.13.5")
//crossScalaVersions in ThisBuild := Seq("2.13.0")

// The default scala version to use for this project (w/o use of `+` in sbt tasks)
// Adjust as appropriate for your project.
ThisBuild / scalaVersion := crossScalaVersions.value.head

ThisBuild / scalacOptions  in Compile ++= Seq(
  "-deprecation",
  "-feature",
  "-unchecked",
  "-Xlog-reflective-calls",
  "-Xlint")
ThisBuild / javacOptions in Compile ++= Seq("-Xlint:unchecked", "-Xlint:deprecation")
ThisBuild / testOptions in Test += Tests.Argument("-oDF")

Global / cancelable := true // ctrl-c

enablePlugins(GitVersioning)
enablePlugins(S3Plugin)

// Project versioning with git plugin
ThisBuild / git.uncommittedSignifier := Some("UNCOMMITTED")
// Generates an sbt warning that can be ignored (likely an sbt bug).
ThisBuild / git.formattedShaVersion := {
  val base = git.baseVersion.?.value
  val suffix =
    makeUncommittedSignifierSuffix(git.gitUncommittedChanges.value, git.uncommittedSignifier.value)
  git.gitHeadCommit.value map { sha =>
    formatShaVersion(base, sha, suffix)
  }
}
def makeUncommittedSignifierSuffix(hasUncommittedChanges: Boolean, uncommittedSignifier: Option[String]): String =
  git.flaggedOptional(hasUncommittedChanges, uncommittedSignifier).map("-" + _).getOrElse("-SNAPSHOT")

def formatShaVersion(baseVersion: Option[String], sha:String, suffix: String): String =
  baseVersion.map(_ +"-").getOrElse("") + sha.take(8) + suffix
// End Project versioning with git plugin

// Add S3 credentials into the file `~/.s3credentials` (See https://github.com/sbt/sbt-s3 for details) then enable this
// to use S3 during builds.
// credentials += Credentials(Path.userHome / ".s3credentials")

ThisBuild / publishTo := {
  val base = sys.env.getOrElse("JFROG_BASE_URL", "")
  if (isSnapshot.value)
    Some("snapshots" at base + "eng-mvn-snapshot-non-unique-local/")
  else
    Some("releases"  at base + "eng-mvn-release-local/")
}

lazy val `monitor` = project.in(file(".")).aggregate(producer, processor, client)

lazy val producer = project
  .in(file("producer"))
  .settings(PB.targets in Compile := Seq(scalapb.gen() -> (sourceManaged in Compile).value))
  .settings(libraryDependencies ++= Seq(
    "com.typesafe.akka" %% "akka-stream-kafka" % AlpakkaKafkaVersion,
    "com.typesafe.akka" %% "akka-stream" % AkkaVersion,
    "ch.qos.logback" % "logback-classic" % "1.2.3",
    "org.scalatest" %% "scalatest" % "3.0.8" % Test))

lazy val kafka = project
  .in(file("kafka"))
  .settings(
    libraryDependencies ++= Seq(
      "ch.qos.logback" % "logback-classic" % LogbackVersion,
      "org.slf4j" % "log4j-over-slf4j" % "1.7.26",
      "io.github.embeddedkafka" %% "embedded-kafka" % EmbeddedKafkaVersion),
    cancelable := false)

lazy val client = project
  .in(file("client"))
  .enablePlugins(AkkaGrpcPlugin, JavaAgent)
  .settings(
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-stream" % AkkaVersion,
      "com.typesafe.akka" %% "akka-discovery" % AkkaVersion))


lazy val prometheus = project .in(file("prometheus-cube"))
  .settings(
    libraryDependencies ++= Seq(
      "org.squbs" %% "squbs-unicomplex" % squbsV,
      "io.prometheus" % "simpleclient" % PrometheusClientV,
      "io.prometheus" % "simpleclient_common" % PrometheusClientV,
      "io.prometheus" % "simpleclient_hotspot" % PrometheusClientV,
      "com.typesafe.akka" %% "akka-http" % AkkaHttpVersion
    )
  )

lazy val processor = project
  .in(file("processor"))
  .dependsOn(prometheus)
  .enablePlugins(AkkaGrpcPlugin, JavaAgent)
  .settings(javaAgents += "org.mortbay.jetty.alpn" % "jetty-alpn-agent" % "2.0.9" % "runtime;test")
  .settings(libraryDependencies ++= Seq(
    "com.typesafe.akka" %% "akka-stream-kafka" % AlpakkaKafkaVersion,
    "com.typesafe.akka" %% "akka-stream-kafka-cluster-sharding" % AlpakkaKafkaVersion,
    "com.typesafe.akka" %% "akka-stream" % AkkaVersion,
    "com.typesafe.akka" %% "akka-discovery" % AkkaVersion,
    "com.typesafe.akka" %% "akka-cluster-sharding-typed" % AkkaVersion,
    "com.typesafe.akka" %% "akka-stream-typed" % AkkaVersion,
    "com.typesafe.akka" %% "akka-serialization-jackson" % AkkaVersion,
    "com.typesafe.akka" %% "akka-http2-support" % AkkaHttpVersion,
    "com.typesafe.akka" %% "akka-http-spray-json" % AkkaHttpVersion,
    "com.lightbend.akka.management" %% "akka-management" % AkkaManagementVersion,
    "com.lightbend.akka.management" %% "akka-management-cluster-http" % AkkaManagementVersion,
    "ch.qos.logback" % "logback-classic" % LogbackVersion,
    "com.typesafe.akka" %% "akka-actor-testkit-typed" % AkkaVersion % Test,
    "org.scalatest" %% "scalatest" % "3.0.8" % Test))
