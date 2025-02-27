val AkkaVersion = "2.6.20"
val AlpakkaKafkaVersion = "3.0.1"
val AkkaManagementVersion = "1.1.4"
val AkkaHttpVersion = "10.2.10"
val EmbeddedKafkaVersion = "2.4.1.1"
val LogbackVersion = "1.2.11"

ThisBuild / scalaVersion := "2.13.8"
ThisBuild / organization := "com.lightbend.akka.samples"
ThisBuild / Compile/ scalacOptions ++= Seq(
  "-deprecation",
  "-feature",
  "-unchecked",
  "-Xlog-reflective-calls",
  "-Xlint")
ThisBuild / Compile / javacOptions ++= Seq("-Xlint:unchecked", "-Xlint:deprecation")
ThisBuild / Test / testOptions += Tests.Argument("-oDF")
ThisBuild / licenses := Seq(("CC0", url("http://creativecommons.org/publicdomain/zero/1.0")))
ThisBuild / resolvers ++= Seq(
  "Akka Snapshots" at "https://repo.akka.io/snapshots",
  Resolver.bintrayRepo("akka", "snapshots")
)

Global / cancelable := true // ctrl-c

lazy val `akka-sample-kafka-to-sharding` = project.in(file(".")).aggregate(producer, processor, client)

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

lazy val processor = project
  .in(file("processor"))
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
      "com.lightbend.akka.management" %% "akka-management" % AkkaManagementVersion,
      "com.lightbend.akka.management" %% "akka-management-cluster-http" % AkkaManagementVersion,
      "com.typesafe.akka" %% "akka-http-spray-json" % AkkaHttpVersion,
      "ch.qos.logback" % "logback-classic" % LogbackVersion,
      "com.typesafe.akka" %% "akka-actor-testkit-typed" % AkkaVersion % Test,
      "org.scalatest" %% "scalatest" % "3.0.8" % Test))

lazy val producer = project
  .in(file("producer"))
  .settings(Compile / PB.targets := Seq(scalapb.gen() -> (Compile / sourceManaged).value))
  .settings(libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-stream-kafka" % AlpakkaKafkaVersion,
      "com.typesafe.akka" %% "akka-stream" % AkkaVersion,
      "ch.qos.logback" % "logback-classic" % "1.2.11",
      "org.scalatest" %% "scalatest" % "3.0.8" % Test))
