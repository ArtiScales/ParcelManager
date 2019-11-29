package test;

import java.io.File;

import org.apache.commons.logging.impl.Log4JLogger;
import org.geotools.data.shapefile.ShapefileDataStore;
import org.geotools.data.simple.SimpleFeatureCollection;

import algorithm.ParcelConsolidRecomp;
import algorithm.ParcelDensification;
import algorithm.ParcelTotRecomp;
import fields.ArtiScalesFields;
import fields.AttributeFromPosition;
import fr.ign.cogit.GTFunctions.Vectors;
import fr.ign.cogit.parcelFunction.ParcelAttribute;
import fr.ign.cogit.parcelFunction.ParcelGetter;
import fr.ign.cogit.parcelFunction.ParcelState;

public class Test {

	// /////////////////////////
	// //////// try the parcelGenMotif method
	/////////////////////////
	public static void main(String[] args) throws Exception {

		File tmpFolder = new File("/tmp/");
		File rootFolder = new File("/home/mcolomb/informatique/ArtiScales/");
		File zoningFile = new File(rootFolder, "dataRegulation/zoning.shp");
		File buildingFile = new File(rootFolder, "dataGeo/building.shp");
		File communityFile = new File(rootFolder, "dataGeo/communities.shp");
		File predicateFile = new File(rootFolder, "dataRegulation/predicate.csv");

		ShapefileDataStore shpDSParcel = new ShapefileDataStore(new File(rootFolder, "dataGeo/parcel.shp").toURI().toURL());
		SimpleFeatureCollection parcels = shpDSParcel.getFeatureSource().getFeatures();
		SimpleFeatureCollection parcel = ParcelGetter.getParcelByZip(parcels, "25575");

		// ShapefileDataStore shpDSParcel = new ShapefileDataStore(new File("/tmp/parcel.shp").toURI().toURL());
		// SimpleFeatureCollection parcel = shpDSParcel.getFeatureSource().getFeatures();

		// File mupOutput = new File(rootFolder, "/MupCityDepot/DDense/variante0/DDense-yager-evalAnal.shp");
		File mupOutput = new File(rootFolder, "MupCityDepot/DDense/base/DDense--N7_Ba_Yag_ahpS_seed_42-evalAnal-20.0.shp");

		double maximalArea = 400.0;
		double minimalArea = 100.0;
		double maximalWidth = 7.0;
		double lenRoad = 3.0;
		int decompositionLevelWithoutRoad = 4;

		 // zoneTotRecomp
		 ShapefileDataStore shpDSZone = new ShapefileDataStore(zoningFile.toURI().toURL());
		 SimpleFeatureCollection featuresZones = shpDSZone.getFeatureSource().getFeatures();
		 SimpleFeatureCollection zone = ParcelTotRecomp.createZoneToCut("AU", featuresZones, parcel);
		 // If no zones, we won't bother
		 if (zone.isEmpty()) {
		 System.out.println("parcelGenZone : no zones to be cut");
		 System.exit(1);
		 }
		 SimpleFeatureCollection parcelCuted = ParcelTotRecomp.parcelTotRecomp(zone, parcel, tmpFolder, zoningFile, maximalArea, minimalArea,
		 maximalWidth, lenRoad, decompositionLevelWithoutRoad);
		 Vectors.exportSFC(parcelCuted, new File("/tmp/parcelTotZoneTmp.shp"));
//		 SimpleFeatureCollection finaux = ArtiScalesFields.fixParcelAttributes(parcelCuted, tmpFolder, buildingFile, communityFile, mupOutput,
//		 zoningFile, true);
//		 Vectors.exportSFC(finaux, new File("/tmp/parcelTotZone.shp"));
		 shpDSZone.dispose();

//		// consolidRecomp
//		SimpleFeatureCollection testmp = AttributeFromPosition.markParcelIntersectMUPOutput(parcel, mupOutput);
//		SimpleFeatureCollection test = AttributeFromPosition.markParcelIntersectZoningType(testmp, "NC", zoningFile);
//		SimpleFeatureCollection cuted = ParcelConsolidRecomp.parcelConsolidRecomp(test, tmpFolder, maximalArea, minimalArea, maximalWidth, lenRoad,
//				decompositionLevelWithoutRoad);
//		Vectors.exportSFC(cuted, new File("/tmp/ParcelConsolidRecompTemp.shp"));
//		SimpleFeatureCollection finaux2 = ArtiScalesFields.fixParcelAttributes(cuted, tmpFolder, buildingFile, communityFile, mupOutput, zoningFile,
//				true);
//		Vectors.exportSFC(finaux2, new File("/tmp/ParcelConsolidRecomp.shp"));

//		/////////////////////////
//		//////// try the parcelDensification method
//		/////////////////////////
//
//		ShapefileDataStore shpDSIlot = new ShapefileDataStore(new File(rootFolder, "dataGeo/ilot.shp").toURI().toURL());
//		SimpleFeatureCollection ilot = shpDSIlot.getFeatureSource().getFeatures();
//
//		SimpleFeatureCollection parcMUPMarked = AttributeFromPosition.markParcelIntersectMUPOutput(finaux2, mupOutput);
//		SimpleFeatureCollection toDensify = AttributeFromPosition.markParcelIntersectZoningType(parcMUPMarked, "U", zoningFile);
//		System.out.println("cut");
//		SimpleFeatureCollection salut = ParcelDensification.parcelDensification(toDensify, ilot, tmpFolder, buildingFile, 800.0, 200.0, 15.0, 5.0,
//				// ParcelState.isArt3AllowsIsolatedParcel(ParcelAttribute.getCityCodeFromParcels(toDensify).get(0), predicateFile));
//				ParcelState.isArt3AllowsIsolatedParcel(parcMUPMarked.features().next(), predicateFile));
//		SimpleFeatureCollection finaux3 = ArtiScalesFields.fixParcelAttributes(salut, tmpFolder, buildingFile, communityFile, mupOutput, zoningFile,
//				true);
//		Vectors.exportSFC(salut, new File("/tmp/parcelDensification.shp"));
//		shpDSIlot.dispose();

		shpDSParcel.dispose();

	}
}
