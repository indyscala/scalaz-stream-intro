scalaVersion := "2.11.4"

resolvers ++= Seq(
  "Typesafe Releases" at "http://repo.typesafe.com/typesafe/releases/",
  "Scalaz Bintray Repo" at "http://dl.bintray.com/scalaz/releases",
  "rossabaker bintray repo" at "http://dl.bintray.com/rossabaker/maven"
)

libraryDependencies ++= Seq(
  "com.typesafe.play" %% "play-iteratees" % "2.4.0-M2",
  "com.typesafe.play.extras" %% "iteratees-extras" % "1.3.0",
  "org.scalaz.stream" %% "scalaz-stream" % "0.6a",
  "com.rossabaker" %% "jawn-streamz" % "0.3.0",
  "org.spire-math" %% "jawn-ast" % "0.7.1"
)

