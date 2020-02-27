package test;

import java.io.File;

import org.geotools.data.shapefile.ShapefileDataStore;
import org.geotools.data.simple.SimpleFeatureCollection;

import analysis.StatParcelStreetRatio;
import fields.ArtiScalesParcelFields;
import fr.ign.cogit.geoToolsFunctions.vectors.Collec;
import fr.ign.cogit.parcelFunction.MarkParcelAttributeFromPosition;
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
		File rootFolder = new File("/home/ubuntu/workspace/ParcelManager/src/main/resources/testData/");

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

		double maximalArea = 400.0;
		double minimalArea = 100.0;
		double maximalWidth = 7.0;
		double lenRoad = 3.0;
		int decompositionLevelWithoutRoad = 3;
		/////////////////////////
		// zoneTotRecomp
		/////////////////////////
		System.out.println("/////////////////////////");
		System.out.println("zoneTotRecomp");
		System.out.println("/////////////////////////");
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
		Collec.exportSFC(parcelCuted, new File(tmpFolder,"parcelTotZoneTmp.shp"));
		SimpleFeatureCollection finaux = ArtiScalesParcelFields.fixParcelAttributes(parcelCuted, tmpFolder, buildingFile,
				communityFile, polygonIntersection, zoningFile, true);
		Collec.exportSFC(finaux, new File(tmpFolder,"parcelTotZone.shp"));
		Collec.exportSFC(zone, new File(tmpFolder,"zone.shp"));
		System.out.println(StatParcelStreetRatio.streetRatioParcelZone(zone, finaux, new File(tmpFolder, "stat")));

		shpDSZone.dispose();
		
		/////////////////////////
		//////// try the consolidRecomp method
		/////////////////////////
		System.out.println("/////////////////////////");
		System.out.println("consolidRecomp");
		System.out.println("/////////////////////////");
//		ParcelConsolidRecomp.DEBUG = true;
		SimpleFeatureCollection testmp = MarkParcelAttributeFromPosition.markParcelIntersectPolygonIntersection(finaux, polygonIntersection);
		SimpleFeatureCollection test = MarkParcelAttributeFromPosition.markParcelIntersectZoningType(testmp, "NC", zoningFile);
		SimpleFeatureCollection cuted = ParcelConsolidRecomp.parcelConsolidRecomp(test, tmpFolder, maximalArea,
				minimalArea, maximalWidth, lenRoad, decompositionLevelWithoutRoad);
		SimpleFeatureCollection finaux2 = ArtiScalesParcelFields.fixParcelAttributes(cuted, tmpFolder, buildingFile,
				communityFile, polygonIntersection, zoningFile, false);
		Collec.exportSFC(finaux2, new File(tmpFolder,"ParcelConsolidRecomp.shp"));
		System.out.println(StatParcelStreetRatio.streetRatioParcels(test, finaux2,tmpFolder));

		/////////////////////////
		//////// try the parcelDensification method
		/////////////////////////
		System.out.println("/////////////////////////");
		System.out.println("parcelDensification");
		System.out.println("/////////////////////////");
		ShapefileDataStore shpDSIlot = new ShapefileDataStore(ilotFile.toURI().toURL());
		SimpleFeatureCollection ilot = shpDSIlot.getFeatureSource().getFeatures();

		SimpleFeatureCollection parcMarked = MarkParcelAttributeFromPosition.markParcelIntersectPolygonIntersection(finaux2, polygonIntersection);
		SimpleFeatureCollection toDensify = MarkParcelAttributeFromPosition.markParcelIntersectZoningType(parcMarked, "U",
				zoningFile);
		SimpleFeatureCollection salut = ParcelDensification.parcelDensification(toDensify, ilot, tmpFolder,
				buildingFile, maximalArea, minimalArea, maximalWidth, lenRoad,
				// ParcelState.isArt3AllowsIsolatedParcel(ParcelAttribute.getCityCodeFromParcels(toDensify).get(0),
				// predicateFile));
				ParcelState.isArt3AllowsIsolatedParcel(parcMarked.features().next(), predicateFile));
		SimpleFeatureCollection finaux3 = ArtiScalesParcelFields.fixParcelAttributes(salut, tmpFolder, buildingFile,
				communityFile, polygonIntersection, zoningFile, false);
		Collec.exportSFC(finaux3, new File(tmpFolder,"parcelDensification.shp"));
		shpDSIlot.dispose();
		shpDSParcel.dispose();
	}
}
