organization := "com.lightbend.akka.samples"
name := "akka-sample-persistence-scala"

scalaVersion := "2.13.8"
def akkaVersion = "2.6.20"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-persistence-typed" % akkaVersion,
  "com.typesafe.akka" %% "akka-serialization-jackson" % akkaVersion,
  "ch.qos.logback" % "logback-classic" % "1.2.11",
  "com.typesafe.akka" %% "akka-actor-testkit-typed" % akkaVersion % Test,
  "org.scalatest" %% "scalatest" % "3.1.0" % Test
)

Compile / scalacOptions ++= Seq("-deprecation", "-feature", "-unchecked", "-Xlog-reflective-calls", "-Xlint")

// show full stack traces and test case durations
Test / testOptions += Tests.Argument("-oDF")
Test / logBuffered := false

licenses := Seq(("CC0", url("http://creativecommons.org/publicdomain/zero/1.0")))
