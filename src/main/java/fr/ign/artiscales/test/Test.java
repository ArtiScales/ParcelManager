package fr.ign.artiscales.test;

import java.io.File;

import org.geotools.data.shapefile.ShapefileDataStore;
import org.geotools.data.simple.SimpleFeatureCollection;

import fr.ign.artiscales.analysis.StatParcelStreetRatio;
import fr.ign.artiscales.fields.FrenchParcelFields;
import fr.ign.cogit.geoToolsFunctions.vectors.Collec;
import fr.ign.artiscales.parcelFunction.MarkParcelAttributeFromPosition;
import fr.ign.artiscales.parcelFunction.ParcelGetter;
import fr.ign.artiscales.parcelFunction.ParcelState;
import fr.ign.artiscales.goal.ConsolidationDivision;
import fr.ign.artiscales.goal.Densification;
import fr.ign.artiscales.goal.ZoneDivision;

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
		File communityFile = new File(rootFolder, "communities.shp");
		File polygonIntersection = new File(rootFolder, "polygonIntersection.shp");
		File predicateFile = new File(rootFolder, "predicate.csv");
		File parcelFile = new File(rootFolder, "parcelle.shp");
		File ilotFile = new File(rootFolder, "ilot.shp");
		ShapefileDataStore shpDSParcel = new ShapefileDataStore(parcelFile.toURI().toURL());
		SimpleFeatureCollection parcel = ParcelGetter.getFrenchParcelByZip(shpDSParcel.getFeatureSource().getFeatures(),
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
		SimpleFeatureCollection zone = ZoneDivision.createZoneToCut("AU", featuresZones, parcel);
		// If no zones, we won't bother
		if (zone.isEmpty()) {
			System.out.println("parcelGenZone : no zones to be cut");
			System.exit(1);
		}
		ZoneDivision.SAVEINTERMEDIATERESULT = true;
		SimpleFeatureCollection parcelCuted = ZoneDivision.zoneDivision(zone, parcel, tmpFolder, zoningFile,
				maximalArea, minimalArea, maximalWidth, lenRoad, decompositionLevelWithoutRoad);
		Collec.exportSFC(parcelCuted, new File(tmpFolder,"parcelTotZoneTmp.shp"));
		SimpleFeatureCollection finaux = FrenchParcelFields.fixParcelAttributes(parcelCuted, communityFile);
		Collec.exportSFC(finaux, new File(tmpFolder,"parcelTotZone.shp"));
		Collec.exportSFC(zone, new File(tmpFolder,"zone.shp"));
//		System.out.println(StatParcelStreetRatio.streetRatioParcelZone(zone, finaux, new File(tmpFolder, "stat")));

		shpDSZone.dispose();
		
		/////////////////////////
		//////// try the consolidRecomp method
		/////////////////////////
		System.out.println("/////////////////////////");
		System.out.println("consolidRecomp");
		System.out.println("/////////////////////////");
		ConsolidationDivision.DEBUG = true;
		ConsolidationDivision.SAVEINTERMEDIATERESULT = true;
		SimpleFeatureCollection testmp = MarkParcelAttributeFromPosition.markParcelIntersectPolygonIntersection(finaux, polygonIntersection);
		SimpleFeatureCollection test = MarkParcelAttributeFromPosition.markParcelIntersectZoningType(testmp, "NC", zoningFile);
		SimpleFeatureCollection cuted = ConsolidationDivision.consolidationDivision(test, tmpFolder, maximalArea,
				minimalArea, maximalWidth, lenRoad, decompositionLevelWithoutRoad);
//		SimpleFeatureCollection finaux2 = ArtiScalesParcelFields.fixParcelAttributes(cuted, tmpFolder, buildingFile,
//				communityFile, polygonIntersection, zoningFile, false);
		SimpleFeatureCollection finaux2 = FrenchParcelFields.fixParcelAttributes(cuted, communityFile);
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
//		SimpleFeatureCollection parcMarked = MarkParcelAttributeFromPosition.markParcelIntersectPolygonIntersection(parcel, polygonIntersection);

		SimpleFeatureCollection toDensify = MarkParcelAttributeFromPosition.markParcelIntersectZoningType(parcMarked, "U",
				zoningFile);
		SimpleFeatureCollection salut = Densification.densification(toDensify, ilot, tmpFolder, buildingFile,new File("/tmp/road.shp"), maximalArea, minimalArea,
				maximalWidth, lenRoad, ParcelState.isArt3AllowsIsolatedParcel(parcMarked.features().next(), predicateFile));

//		SimpleFeatureCollection finaux3 = ArtiScalesParcelFields.fixParcelAttributes(salut, tmpFolder, buildingFile, communityFile,
//				polygonIntersection, zoningFile, false);
		SimpleFeatureCollection finaux3 = FrenchParcelFields.fixParcelAttributes(salut, communityFile);
		Collec.exportSFC(finaux3, new File(tmpFolder,"parcelDensification.shp"));
		shpDSIlot.dispose();
		shpDSParcel.dispose();
	}
}
