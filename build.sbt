val scala212 = "2.12.14"

val scalaTestArtifact    = "org.scalatest"  %% "scalatest"        % "3.2.+" % Test
val awsArtifact          = "com.amazonaws"  %  "aws-java-sdk-s3"  % "1.11.+"
val logbackArtifact      = "ch.qos.logback" % "logback-classic"   % "1.2.+"

lazy val publishSettings = Seq(
  publishMavenStyle := true,
  pomIncludeRepository := { _ => false },
  publishTo := sonatypePublishToBundle.value,
  licenses := Seq("Apache-2.0" -> url("http://opensource.org/licenses/Apache-2.0")),
  homepage := Some(url("https://github.com/salesforce/awesolog")),
  scmInfo := Some(
    ScmInfo(
      url("https://github.com/salesforce/awesolog"),
      "scm:git:git@github.com:salesforce/awesolog.git"
    )
  ),
  credentials += Credentials(
    "Sonatype Nexus Repository Manager",
    "oss.sonatype.org",
    sys.env.getOrElse("SONATYPE_USERNAME",""),
    sys.env.getOrElse("SONATYPE_PASSWORD","")
  ),
  developers := List(
    Developer(
      id = "damianxu88",
      name = "Damian Xu",
      email = "damian.xu@salesforce.com",
      url = url("http://github.com/damianxu88")
    )
  ),
  useGpgPinentry := true
)

lazy val commonSettings = Seq(
  scalacOptions ++= Seq("-deprecation", "-feature", "-Xlint", "-Xfatal-warnings"),
  scalaVersion := scala212,
  libraryDependencies += scalaTestArtifact,
  organization := "com.salesforce.mce",
  headerLicense := Some(HeaderLicense.Custom(
  """|Copyright (c) 2021, salesforce.com, inc.
     |All rights reserved.
     |SPDX-License-Identifier: BSD-3-Clause
     |For full license text, see the LICENSE file in the repo root or https://opensource.org/licenses/BSD-3-Clause
     |""".stripMargin
  ))
)

lazy val javaOnlySettings = Seq(
  autoScalaLibrary := false,
  crossPaths := false
)

lazy val root = (project in file(".")).
  settings(commonSettings: _*).
  settings(javaOnlySettings: _*).
  settings(publishSettings: _*).
  settings(
    name := "awesolog",
    libraryDependencies ++= Seq(
      awsArtifact,
      logbackArtifact
    )
  )
