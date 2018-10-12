// See LICENSE for license details.
import complete.DefaultParsers._
import scala.sys.process._

def scalacOptionsVersion(scalaVersion: String): Seq[String] = {
  Seq() ++ {
    // If we're building with Scala > 2.11, enable the compile option
    //  switch to support our anonymous Bundle definitions:
    //  https://github.com/scala/bug/issues/10047
    CrossVersion.partialVersion(scalaVersion) match {
      case Some((2, scalaMajor: Long)) if scalaMajor < 12 => Seq()
      case _ => Seq("-Xsource:2.11")
    }
  }
}

def javacOptionsVersion(scalaVersion: String): Seq[String] = {
  Seq() ++ {
    // Scala 2.12 requires Java 8. We continue to generate
    //  Java 7 compatible code for Scala 2.11
    //  for compatibility with old clients.
    CrossVersion.partialVersion(scalaVersion) match {
      case Some((2, scalaMajor: Long)) if scalaMajor < 12 =>
        Seq("-source", "1.7", "-target", "1.7")
      case _ =>
        Seq("-source", "1.8", "-target", "1.8")
    }
  }
}

val defaultVersions = Map("chisel3" -> "latest.release")

lazy val commonSettings = Seq (
  organization := "edu.berkeley.cs",
  version := "2.0",
//  git.remoteRepo := "git@github.com:ucb-bar/riscv-sodor.git",
  autoAPIMappings := true,
  scalaVersion := "2.11.12",
  crossScalaVersions := Seq("2.11.12", "2.12.6"),
  scalacOptions := Seq("-deprecation", "-feature") ++ scalacOptionsVersion(scalaVersion.value),
  javacOptions ++= javacOptionsVersion(scalaVersion.value),
  libraryDependencies ++= Seq(
    "com.github.scopt" %% "scopt" % "3.7.0"
  ),
  libraryDependencies ++= (Seq("chisel3").map {
    dep: String => "edu.berkeley.cs" %% dep % sys.props.getOrElse(dep + "Version", defaultVersions(dep))
  })
)

lazy val common = (project in file("src/common")).
  settings(commonSettings: _*)

lazy val rv32_1stage = (project in file("src/rv32_1stage")).
  settings(commonSettings: _*).
  settings(chipSettings: _*).
  dependsOn(common)

lazy val rv32_2stage = (project in file("src/rv32_2stage")).
  settings(commonSettings: _*).
  settings(chipSettings: _*).
  dependsOn(common)

lazy val rv32_3stage = (project in file("src/rv32_3stage")).
  settings(commonSettings: _*).
  settings(chipSettings: _*).
  dependsOn(common)

lazy val rv32_5stage = (project in file("src/rv32_5stage")).
  settings(commonSettings: _*).
  settings(chipSettings: _*).
  dependsOn(common)


lazy val rv32_ucode  = (project in file("src/rv32_ucode")).
  settings(commonSettings: _*).
  settings(chipSettings: _*).
  dependsOn(common)


lazy val elaborateTask = InputKey[Unit]("elaborate", "convert chisel components into backend source code")
lazy val makeTask = InputKey[Unit]("make", "trigger backend-specific makefile command")

def runChisel(args: Seq[String], cp: Classpath, pr: ResolvedProject) = {
   val numArgs = 1
   require(args.length >= numArgs, "syntax: elaborate <component> [chisel args]")
   val projectName = pr.id
//   val packageName = projectName //TODO: valid convention?
   val packageName = "Sodor" //TODO: celio change
   val componentName = args(0)
   val classLoader = new java.net.URLClassLoader(cp.map(_.data.toURI.toURL).toArray, cp.getClass.getClassLoader)
   val chiselMainClass = classLoader.loadClass("Chisel.chiselMain$")
   val chiselMainObject = chiselMainClass.getDeclaredFields.head.get(null)
   val chiselMain = chiselMainClass.getMethod("run", classOf[Array[String]], classOf[Function0[_]])
   val chiselArgs = args.drop(numArgs)
   val component = classLoader.loadClass(packageName+"."+componentName)
   val generator = () => component.newInstance()
   chiselMain.invoke(chiselMainObject, Array(chiselArgs.toArray, generator):_*)
}

val chipSettings = Seq(
  elaborateTask := {
    val args: Seq[String] = spaceDelimited("<arg>").parsed
    runChisel(args, (fullClasspath in Runtime).value, thisProject.value)
  },
  makeTask := {
    val args: Seq[String] = spaceDelimited("<arg>").parsed
    require(args.length >= 2, "syntax: <dir> <target>")
    runChisel(args.drop(2), (fullClasspath in Runtime).value, thisProject.value)
    val makeDir = args(0)
    val target = args(1)
    val jobs = java.lang.Runtime.getRuntime.availableProcessors
    val make = "make -C" + makeDir + " -j" + jobs + " " + target
    make!
  }
)

