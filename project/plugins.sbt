
resolvers ++= Seq(
  //"Typesafe Repository" at "https://repo.typesafe.com/typesafe/releases",
  Resolver.url("sbts3 resolver", url("https://dl.bintray.com/emersonloureiro/sbt-plugins"))(Resolver.ivyStylePatterns)
)
resolvers ++= Seq(
  Resolver.bintrayRepo("lonelyplanet", "maven")
)

credentials += Credentials(Path.userHome / ".sbt" / ".credentials")

addSbtPlugin("com.typesafe.sbt" % "sbt-git" % "1.0.0")

addSbtPlugin("org.scalastyle" %% "scalastyle-sbt-plugin" % "1.0.0")

addSbtPlugin("com.timushev.sbt" % "sbt-updates" % "0.3.4")

addSbtPlugin("com.codacy" % "sbt-codacy-coverage" % "3.0.3")

addSbtPlugin("com.github.gseitz" % "sbt-release" % "1.0.13")

addSbtPlugin("org.scoverage" % "sbt-scoverage" % "1.6.1")

addSbtPlugin("cf.janga" % "sbts3" % "0.10.3")

addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "0.15.0")

addSbtPlugin("com.lightbend.akka.grpc" % "sbt-akka-grpc" % "0.7.3")
addSbtPlugin("com.lightbend.sbt" % "sbt-javaagent" % "0.1.4") // ALPN agent
addSbtPlugin("com.thesamet" % "sbt-protoc" % "1.0.4")
