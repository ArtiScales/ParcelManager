package test;

import java.io.File;

import org.geotools.data.shapefile.ShapefileDataStore;
import org.geotools.data.simple.SimpleFeatureCollection;

import algorithm.ParcelConsolidRecomp;
import algorithm.ParcelTotRecomp;
import fields.ArtiScalesFields;
import fr.ign.cogit.GTFunctions.Vectors;
import fr.ign.cogit.parcelFunction.ParcelGetter;

public class Test {

	// /////////////////////////
	// //////// try the parcelGenMotif method
	/////////////////////////
	public static void main(String[] args) throws Exception {

		File tmpFolder = new File("/tmp/");
		File rootFolder = new File("/home/ubuntu/boulot/these/ArtiScales/ArtiScales/");
		File zoningFile = new File(rootFolder, "dataRegulation/zoning.shp");
		File buildingFile = new File(rootFolder, "dataGeo/building.shp");
		File communityFile = new File(rootFolder, "dataGeo/communities.shp");
		ShapefileDataStore shpDSParcel = new ShapefileDataStore(new File(rootFolder, "dataGeo/parcel.shp").toURI().toURL());
		SimpleFeatureCollection parcels = shpDSParcel.getFeatureSource().getFeatures();
		SimpleFeatureCollection parcel = ParcelGetter.getParcelByZip(parcels, "25381");

		ShapefileDataStore shpDSZone = new ShapefileDataStore(zoningFile.toURI().toURL());
		SimpleFeatureCollection featuresZones = shpDSZone.getFeatureSource().getFeatures();

		File mupOutput = new File(rootFolder, "/MupCityDepot/DDense/variante0/DDense-yager-evalAnal.shp");

		double maximalArea = 400.0;
		double minimalArea = 100.0;
		double maximalWidth = 7.0;
		double lenRoad = 3.0;
		int decompositionLevelWithoutRoad = 4;

		// zoneTotRecomp
		SimpleFeatureCollection zone = ParcelTotRecomp.createZoneToCut("AU", featuresZones, parcel);
		// If no zones, we won't bother
		if (zone.isEmpty()) {
			System.out.println("parcelGenZone : no zones to be cut");
			System.exit(1);
		}
		SimpleFeatureCollection parcelCuted = ParcelTotRecomp.parcelTotRecomp(zone, parcel, tmpFolder, zoningFile, maximalArea, minimalArea,
				maximalWidth, lenRoad, decompositionLevelWithoutRoad);
		Vectors.exportSFC(parcelCuted, new File("/tmp/parcelTotZoneTmp.shp"));
		SimpleFeatureCollection finaux = ArtiScalesFields.fixParcelAttributes(parcelCuted, tmpFolder, buildingFile, communityFile, mupOutput,
				zoningFile, true);

		 //consolidRecomp
		 SimpleFeatureCollection testmp = ParcelConsolidRecomp.markParcelIntersectMUPOutput(parcel, mupOutput);
		 SimpleFeatureCollection test = ParcelConsolidRecomp.markParcelIntersectZoningType(testmp, "NC", zoningFile);
		 SimpleFeatureCollection cuted = ParcelConsolidRecomp.parcelConsolidRecomp(test, tmpFolder,maximalArea, minimalArea, maximalWidth, lenRoad,
		 decompositionLevelWithoutRoad);
		 SimpleFeatureCollection finaux2 = ArtiScalesFields.fixParcelAttributes(cuted, tmpFolder, buildingFile, communityFile, mupOutput,
					zoningFile, true);

		Vectors.exportSFC(finaux, new File("/tmp/parcelTotZone.shp"));
		Vectors.exportSFC(finaux2, new File("/tmp/ParcelConsolidRecomp.shp"));

		shpDSZone.dispose();
		shpDSParcel.dispose();

	}
}
