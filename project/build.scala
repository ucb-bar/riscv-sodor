import sbt._
import Keys._

object BuildSettings extends Build {
  val buildOrganization = "berkeley"
  val buildVersion = "2.0"
  val buildScalaVersion = "2.11.12"
  val chiselVersion = System.getProperty("chiselVersion", "latest.release")
  val defaultVersions = Map(
    "chisel3" -> "3.0.1",//3.0-SNAPSHOT",
    "chisel-iotesters" -> "1.1.1"//"1.1-SNAPSHOT"
    )

  val buildSettings = Defaults.defaultSettings ++ Seq (
    organization := buildOrganization,
    version      := buildVersion,
    scalaVersion := buildScalaVersion,
    traceLevel   := 15,
    resolvers ++= Seq(
      "Sonatype Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots",
      "Sonatype Releases" at "https://oss.sonatype.org/content/repositories/releases"
    ),
    libraryDependencies ++= Seq("org.scala-lang" % "scala-reflect" % scalaVersion.value),
    addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full),
    libraryDependencies ++= (Seq("chisel3","chisel-iotesters").map {
  dep: String => "edu.berkeley.cs" %% dep % sys.props.getOrElse(dep + "Version", defaultVersions(dep)) })
  )

  lazy val common = Project("common", file("common"), settings = buildSettings
    ++Seq(scalaSource in Compile := baseDirectory.value / "../src/common"))
  lazy val rv32_1stage = Project("rv32_1stage", file("rv32_1stage"), settings = buildSettings ++ chipSettings
    ++Seq(scalaSource in Compile := baseDirectory.value / "../src/rv32_1stage")) dependsOn(common)
  lazy val rv32_2stage = Project("rv32_2stage", file("rv32_2stage"), settings = buildSettings ++ chipSettings
    ++Seq(scalaSource in Compile := baseDirectory.value / "../src/rv32_2stage")) dependsOn(common)
  lazy val rv32_3stage = Project("rv32_3stage", file("rv32_3stage"), settings = buildSettings ++ chipSettings
    ++Seq(scalaSource in Compile := baseDirectory.value / "../src/rv32_3stage")) dependsOn(common)
  lazy val rv32_5stage = Project("rv32_5stage", file("rv32_5stage"), settings = buildSettings ++ chipSettings
    ++Seq(scalaSource in Compile := baseDirectory.value / "../src/rv32_5stage")) dependsOn(common)
  lazy val rv32_ucode  = Project("rv32_ucode", file("rv32_ucode"), settings = buildSettings ++ chipSettings
    ++Seq(scalaSource in Compile := baseDirectory.value / "../src/rv32_ucode")) dependsOn(common)
  lazy val fpgatop  = Project("fpgatop", file("fpgatop"), settings = buildSettings ++ chipSettings
    ++Seq(scalaSource in Compile := baseDirectory.value / "../src/fpgatop")
    ++Seq(resourceDirectory in Compile := baseDirectory.value / "../vsrc")) dependsOn(common,rv32_3stage)
  lazy val zynqsimtop  = Project("zynqsimtop", file("zynqsimtop"), settings = buildSettings ++ chipSettings
    ++Seq(scalaSource in Compile := baseDirectory.value / "../src/zynqsimtop")) dependsOn(fpgatop)

  val elaborateTask = InputKey[Unit]("elaborate", "convert chisel components into backend source code")
  val makeTask = InputKey[Unit]("make", "trigger backend-specific makefile command")

  def runChisel(args: Seq[String], cp: Classpath, pr: ResolvedProject) = {
     val numArgs = 1
     require(args.length >= numArgs, "syntax: elaborate <component> [chisel args]")
     val projectName = pr.id
//     val packageName = projectName //TODO: valid convention?
     val packageName = "Sodor" //TODO: celio change
     val componentName = args(0)
     val classLoader = new java.net.URLClassLoader(cp.map(_.data.toURL).toArray, cp.getClass.getClassLoader)
     val chiselMainClass = classLoader.loadClass("Chisel.chiselMain$")
     val chiselMainObject = chiselMainClass.getDeclaredFields.head.get(null)
     val chiselMain = chiselMainClass.getMethod("run", classOf[Array[String]], classOf[Function0[_]])
     val chiselArgs = args.drop(numArgs)
     val component = classLoader.loadClass(packageName+"."+componentName)
     val generator = () => component.newInstance()
     chiselMain.invoke(chiselMainObject, Array(chiselArgs.toArray, generator):_*)
  }

  val chipSettings = Seq(
    elaborateTask <<= inputTask { (argTask: TaskKey[Seq[String]]) =>
      (argTask, fullClasspath in Runtime, thisProject) map {
        runChisel
      }
    },
    makeTask <<= inputTask { (argTask: TaskKey[Seq[String]]) =>
      (argTask, fullClasspath in Runtime, thisProject) map {
        (args: Seq[String], cp: Classpath, pr: ResolvedProject) => {
          require(args.length >= 2, "syntax: <dir> <target>")
          runChisel(args.drop(2), cp, pr)
          val makeDir = args(0)
          val target = args(1)
          val jobs = java.lang.Runtime.getRuntime.availableProcessors
          val make = "make -C" + makeDir + " -j" + jobs + " " + target
          make!
        }
      }
    }
  )
}

