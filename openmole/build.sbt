name := "ParcelManagerPlugin"

version := "1.0"

scalaVersion := "2.13.2"

val parcelManagerVersion = "0.2-SNAPSHOT"

enablePlugins(SbtOsgi)

OsgiKeys.exportPackage := Seq("fr.ign.artiscales.*")

OsgiKeys.importPackage := Seq("*;resolution:=optional")

OsgiKeys.privatePackage := Seq("!scala.*,!java.*,*")

OsgiKeys.requireCapability := """osgi.ee; osgi.ee="JavaSE";version:List="1.8,1.9"""""

resolvers += Resolver.mavenLocal

libraryDependencies += "fr.ign.cogit" % "ParcelManager" % parcelManagerVersion  excludeAll(ExclusionRule(organization = "org.geotools"))

val geotoolsGridVersion = "21.0"

libraryDependencies ++= Seq (
  "org.geotools" % "gt-grid" % geotoolsGridVersion,
  "org.geotools" % "gt-coverage" % geotoolsGridVersion,
  "org.geotools" % "gt-geotiff" % geotoolsGridVersion,
  "org.geotools" % "gt-image" % geotoolsGridVersion,
  "org.geotools" % "gt-epsg-hsql" % geotoolsGridVersion,
  "org.geotools" % "gt-referencing" % geotoolsGridVersion,
  "org.geotools" % "gt-shapefile" % geotoolsGridVersion,
  "org.geotools" % "gt-graph" % geotoolsGridVersion,
  "org.geotools" % "gt-metadata" % geotoolsGridVersion,
  "org.geotools" % "gt-opengis" % geotoolsGridVersion,
  "org.geotools" % "gt-main" % geotoolsGridVersion,
  "javax.media" % "jai_core" % "1.1.3" from "http://download.osgeo.org/webdav/geotools/javax/media/jai_core/1.1.3/jai_core-1.1.3.jar",
  "javax.media" % "jai_codec" % "1.1.3",
  "javax.media" % "jai_imageio" % "1.1"
)

OsgiKeys.embeddedJars := (Keys.externalDependencyClasspath in Compile).value map (_.data) filter (f=> (f.getName startsWith "gt-"))