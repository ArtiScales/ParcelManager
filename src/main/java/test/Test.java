package test;

import java.io.File;

import org.geotools.data.shapefile.ShapefileDataStore;
import org.geotools.data.simple.SimpleFeatureCollection;

import analysis.StatParcelStreetRatio;
import fields.ArtiScalesFields;
import fields.AttributeFromPosition;
import fr.ign.cogit.GTFunctions.Vectors;
import fr.ign.cogit.parcelFunction.ParcelGetter;
import fr.ign.cogit.parcelFunction.ParcelState;
import goal.ParcelConsolidRecomp;
import goal.ParcelDensification;
import goal.ParcelTotRecomp;

public class Test {

	// /////////////////////////
	// //////// try the parcelGenMotif method
	/////////////////////////
	public static void main(String[] args) throws Exception {

		File tmpFolder = new File("/tmp/");

		// File rootFolder = new File("/home/mcolomb/informatique/ArtiScales/");
		// File zoningFile = new File(rootFolder, "dataRegulation/zoning.shp");
		// File buildingFile = new File(rootFolder, "dataGeo/building.shp");
		// File communityFile = new File(rootFolder, "dataGeo/communities.shp");
		// File predicateFile = new File(rootFolder, "dataRegulation/predicate.csv");
		// File parcelFile = new File(rootFolder, "dataGeo/parcel.shp");
		// File polygonIntersection = new File(rootFolder,
		// "MupCityDepot/DDense/base/DDense--N7_Ba_Yag_ahpS_seed_42-evalAnal-20.0.shp");

		// File rootFolder = new
		// File(Test.class.getClassLoader().getResource("testData").getFile());
		File rootFolder = new File("/home/thema/Documents/MC/workspace/ParcelManager/src/main/resources/testData/");

		File zoningFile = new File(rootFolder, "zoning.shp");
		File buildingFile = new File(rootFolder, "building.shp");
		File polygonIntersection = new File(rootFolder, "polygonIntersection.shp");
		File communityFile = new File(rootFolder, "communities.shp");
		File predicateFile = new File(rootFolder, "predicate.csv");
		File parcelFile = new File(rootFolder, "parcelle.shp");
		File ilotFile = new File(rootFolder, "ilot.shp");
		ShapefileDataStore shpDSParcel = new ShapefileDataStore(parcelFile.toURI().toURL());
		SimpleFeatureCollection parcel = ParcelGetter.getParcelByZip(shpDSParcel.getFeatureSource().getFeatures(),
				"25267");

		// ShapefileDataStore shpDSParcel = new ShapefileDataStore(new
		// File("/tmp/parcel.shp").toURI().toURL());
		// SimpleFeatureCollection parcel =
		// shpDSParcel.getFeatureSource().getFeatures();

		double maximalArea = 400.0;
		double minimalArea = 100.0;
		double maximalWidth = 7.0;
		double lenRoad = 3.0;
		int decompositionLevelWithoutRoad = 3;
		/////////////////////////
		// zoneTotRecomp
		/////////////////////////

		ShapefileDataStore shpDSZone = new ShapefileDataStore(zoningFile.toURI().toURL());
		SimpleFeatureCollection featuresZones = shpDSZone.getFeatureSource().getFeatures();
		SimpleFeatureCollection zone = ParcelTotRecomp.createZoneToCut("AU", featuresZones, parcel);
		// If no zones, we won't bother
		if (zone.isEmpty()) {
			System.out.println("parcelGenZone : no zones to be cut");
			System.exit(1);
		}

		SimpleFeatureCollection parcelCuted = ParcelTotRecomp.parcelTotRecomp(zone, parcel, tmpFolder, zoningFile,
				maximalArea, minimalArea, maximalWidth, lenRoad, decompositionLevelWithoutRoad);
		Vectors.exportSFC(parcelCuted, new File("/tmp/parcelTotZoneTmp.shp"));
		SimpleFeatureCollection finaux = ArtiScalesFields.fixParcelAttributes(parcelCuted, tmpFolder, buildingFile,
				communityFile, polygonIntersection, zoningFile, true);
		Vectors.exportSFC(finaux, new File("/tmp/parcelTotZone.shp"));
		Vectors.exportSFC(zone, new File("/tmp/zone.shp"));
		System.out.println(StatParcelStreetRatio.streetRatioParcelZone(zone, finaux, new File(tmpFolder, "stat")));

		shpDSZone.dispose();
		
		/////////////////////////
		//////// try the consolidRecomp method
		/////////////////////////

		SimpleFeatureCollection testmp = AttributeFromPosition.markParcelIntersectPolygonIntersection(finaux, polygonIntersection);
		SimpleFeatureCollection test = AttributeFromPosition.markParcelIntersectZoningType(testmp, "NC", zoningFile);
		SimpleFeatureCollection cuted = ParcelConsolidRecomp.parcelConsolidRecomp(test, tmpFolder, maximalArea,
				minimalArea, maximalWidth, lenRoad, decompositionLevelWithoutRoad);
		SimpleFeatureCollection finaux2 = ArtiScalesFields.fixParcelAttributes(cuted, tmpFolder, buildingFile,
				communityFile, polygonIntersection, zoningFile, false);
		Vectors.exportSFC(finaux2, new File("/tmp/ParcelConsolidRecomp.shp"));

		System.out.println(StatParcelStreetRatio.streetRatioParcels(test, finaux2));

		/////////////////////////
		//////// try the parcelDensification method
		/////////////////////////

		ShapefileDataStore shpDSIlot = new ShapefileDataStore(ilotFile.toURI().toURL());
		SimpleFeatureCollection ilot = shpDSIlot.getFeatureSource().getFeatures();

		SimpleFeatureCollection parcMarked = AttributeFromPosition.markParcelIntersectPolygonIntersection(finaux2, polygonIntersection);
		SimpleFeatureCollection toDensify = AttributeFromPosition.markParcelIntersectZoningType(parcMarked, "U",
				zoningFile);
		SimpleFeatureCollection salut = ParcelDensification.parcelDensification(toDensify, ilot, tmpFolder,
				buildingFile, maximalArea, minimalArea, maximalWidth, lenRoad,
				// ParcelState.isArt3AllowsIsolatedParcel(ParcelAttribute.getCityCodeFromParcels(toDensify).get(0),
				// predicateFile));
				ParcelState.isArt3AllowsIsolatedParcel(parcMarked.features().next(), predicateFile));
		SimpleFeatureCollection finaux3 = ArtiScalesFields.fixParcelAttributes(salut, tmpFolder, buildingFile,
				communityFile, polygonIntersection, zoningFile, false);
		Vectors.exportSFC(finaux3, new File("/tmp/parcelDensification.shp"));
		shpDSIlot.dispose();

		shpDSParcel.dispose();

	}
}
