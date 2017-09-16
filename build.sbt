name := "ChargeBeeDataSync"

version := "1.0"

scalaVersion := "2.12.3"

resolvers += "Artima Maven Repository" at "http://repo.artima.com/releases"

libraryDependencies += "com.chargebee" % "chargebee-java" % "2.3.8"
libraryDependencies += "org.mongodb.scala" %% "mongo-scala-driver" % "2.1.0"
libraryDependencies += "com.github.scopt" %% "scopt" % "3.7.0"
libraryDependencies += "org.scalactic" %% "scalactic" % "3.0.1"
libraryDependencies += "org.scalatest" %% "scalatest" % "3.0.1" % "test"
libraryDependencies += "me.tongfei" % "progressbar" % "0.5.5"

enablePlugins(JavaAppPackaging)

mappings in Universal ++= {
  val scriptsDir = file("scripts/")
  scriptsDir.listFiles.toSeq.map { f =>
    f -> ("bin/" + f.getName)
  }
}
