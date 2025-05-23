ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / scalaVersion := "2.13.12" // Make sure this matches project scalaVersion

lazy val root = (project in file("."))
  .settings(
    name := "OPALExample",
    version := "0.1",
    scalaVersion := "2.13.12",
    libraryDependencies ++= Seq(
      "de.opal-project" %% "framework" % "5.0.0",
      "de.opal-project" %% "bytecode-representation" % "5.0.0",
      "de.opal-project" %% "common" % "5.0.0",
      "de.opal-project" %% "bytecode-assembler" % "5.0.0", // Essential for assembling bytecode
      "de.opal-project" %% "bytecode-creator" % "5.0.0",
      "com.typesafe" % "config" % "1.4.3"
    ),
    resolvers += Resolver.sonatypeRepo("snapshots") // Add this if using SNAPSHOT versions of OPAL
  )