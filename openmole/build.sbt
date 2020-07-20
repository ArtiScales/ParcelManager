name := "ParcelManagerPlugin"

version := "1.0"

scalaVersion := "2.13.2"

val parcelManagerVersion = "0.2-SNAPSHOT"

enablePlugins(SbtOsgi)

//(unmanagedResourceDirectories in Compile) := (unmanagedResourceDirectories in Compile).value.filter(_.getName.startsWith("resources"))

OsgiKeys.exportPackage := Seq("fr.ign.artiscales.*")

OsgiKeys.importPackage := Seq("*;resolution:=optional")

OsgiKeys.privatePackage := Seq("!scala.*,!java.*,*")

OsgiKeys.requireCapability := """osgi.ee; osgi.ee="JavaSE";version:List="1.8,1.9"""""

resolvers += Resolver.mavenLocal

libraryDependencies += "fr.ign.cogit" % "ParcelManager" % parcelManagerVersion  excludeAll(ExclusionRule(organization = "org.geotools"))

val geotoolsGridVersion = "21.0"
val geotoolsVersion = "23.0"

libraryDependencies ++= Seq (
  "org.geotools" % "gt-grid" % geotoolsGridVersion,
  "org.geotools" % "gt-coverage" % geotoolsVersion,
  "org.geotools" % "gt-geotiff" % geotoolsGridVersion,
  "org.geotools" % "gt-image" % geotoolsVersion,
  "org.geotools" % "gt-epsg-hsql" % geotoolsVersion,
  "org.geotools" % "gt-geopkg" % geotoolsVersion,
  "org.geotools" % "gt-opengis" % geotoolsVersion,
  "org.geotools" % "gt-main" % geotoolsVersion,
  "javax.media" % "jai_core" % "1.1.3" from "http://download.osgeo.org/webdav/geotools/javax/media/jai_core/1.1.3/jai_core-1.1.3.jar",
  "javax.media" % "jai_codec" % "1.1.3",
  "javax.media" % "jai_imageio" % "1.1"
)

OsgiKeys.embeddedJars := (Keys.externalDependencyClasspath in Compile).value map (_.data) filter (f=> (f.getName startsWith "gt-"))