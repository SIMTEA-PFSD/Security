lazy val root = (project in file("."))
  .enablePlugins(PlayScala, JavaAppPackaging, DockerPlugin)
  .settings(
    name         := "security-service",
    organization := "edu.pfsd.simtea",
    version      := "1.0-SNAPSHOT",
    scalaVersion := "2.13.18",

    // ─── Scala Compiler Options ─────────────────────────
    scalacOptions ++= Seq(
      "-deprecation",
      "-feature",
      "-unchecked",
      "-Xlint:_",
      "-Ywarn-dead-code"
    ),

    // ─── Dependencias ───────────────────────────────────
    libraryDependencies ++= Seq(
      guice,

      // Kafka
      "org.apache.kafka"  %  "kafka-clients"        % "3.6.0",

      // Circe (JSON)
      "io.circe"          %% "circe-core"           % "0.14.6",
      "io.circe"          %% "circe-generic"        % "0.14.6",
      "io.circe"          %% "circe-parser"         % "0.14.6",

      // Cats Effect + http4s
      "org.typelevel"     %% "cats-effect"          % "3.5.2",
      "org.http4s"        %% "http4s-ember-server"  % "0.23.25",
      "org.http4s"        %% "http4s-dsl"           % "0.23.25",
      "org.http4s"        %% "http4s-circe"         % "0.23.25",

      // Doobie + PostgreSQL
      "org.tpolecat"      %% "doobie-core"          % "1.0.0-RC4",
      "org.tpolecat"      %% "doobie-hikari"        % "1.0.0-RC4",
      "org.tpolecat"      %% "doobie-postgres"      % "1.0.0-RC4",
      "org.postgresql"    %  "postgresql"           % "42.7.1",

      // Config y logs
      "com.typesafe"      %  "config"               % "1.4.3",
      //"org.slf4j"         %  "slf4j-simple"         % "2.0.9",

      // Test
      "org.scalatest"     %% "scalatest"            % "3.2.17" % Test,
      "org.scalatestplus.play" %% "scalatestplus-play" % "7.0.2" % Test

    ),

    Compile / mainClass := Some("security.Main"),

    // ─── Docker ──────────────────────────────────────────
    Docker / packageName := "security-service",
    Docker / version     := version.value,
    dockerBaseImage      := "eclipse-temurin:17-jre",
    dockerExposedPorts   := Seq(8082),
    dockerUpdateLatest   := true
  )
  javacOptions ++= Seq("--release", "17")