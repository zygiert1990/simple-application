val tapirVersion = "1.11.13"

lazy val rootProject = (project in file("."))
  .settings(
    Seq(
      name := "simple-application",
      version := "0.1.0-SNAPSHOT",
      organization := "com.zygiert",
      scalaVersion := "3.7.0-RC1-bin-20250207-d60a914-NIGHTLY",
      libraryDependencies ++= Seq(
        "com.softwaremill.sttp.tapir" %% "tapir-netty-server" % tapirVersion,
        "ch.qos.logback" % "logback-classic" % "1.5.16",
        "com.softwaremill.sttp.tapir" %% "tapir-sttp-stub-server" % tapirVersion % Test,
        "org.scalatest" %% "scalatest" % "3.2.19" % Test
      )
    )
  )
  .settings(fatJarSettings)

lazy val fatJarSettings = Seq(
  assembly / assemblyJarName := "simple-application.jar",
  assembly / assemblyMergeStrategy := {
    // SwaggerUI: https://tapir.softwaremill.com/en/latest/docs/openapi.html#using-swaggerui-with-sbt-assembly
    case PathList("META-INF", "maven", "org.webjars", "swagger-ui", "pom.properties") => MergeStrategy.singleOrError
    case PathList("META-INF", "resources", "webjars", "swagger-ui", _*) => MergeStrategy.singleOrError
    // other
    case PathList(ps@_*) if ps.last endsWith "io.netty.versions.properties" => MergeStrategy.first
    case PathList(ps@_*) if ps.last endsWith "pom.properties" => MergeStrategy.discard
    case PathList(ps@_*) if ps.last endsWith "module-info.class" => MergeStrategy.discard
    case PathList(ps@_*) if ps.last endsWith "okio.kotlin_module" => MergeStrategy.discard
    case x =>
      val oldStrategy = (assembly / assemblyMergeStrategy).value
      oldStrategy(x)
  }
)
