ThisBuild / version      := "1.0.0"
ThisBuild / scalaVersion := "2.13.12"
ThisBuild / organization := "engine"

lazy val root = (project in file("."))
  .settings(
    name := "rule-engine",
    libraryDependencies ++= Seq(
      "mysql"                  %  "mysql-connector-java"       % "8.0.33",
      "com.opencsv"            %  "opencsv"                    % "5.9",
      "org.scala-lang.modules" %% "scala-parallel-collections" % "1.0.4",
      "com.zaxxer"             %  "HikariCP"                   % "5.1.0"
    )
  )