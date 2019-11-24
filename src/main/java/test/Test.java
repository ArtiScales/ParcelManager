package test;

import java.io.File;

import org.geotools.data.shapefile.ShapefileDataStore;
import org.geotools.data.simple.SimpleFeatureCollection;

import algorithm.ParcelConsolidRecomp;
import fr.ign.cogit.GTFunctions.Vectors;
import fr.ign.cogit.parcelFunction.ParcelGetter;

public class Test {

	// /////////////////////////
	// //////// try the parcelGenMotif method
	/////////////////////////
	public static void main(String[] args) throws Exception {

		File tmpFolder = new File("/tmp/");
		File rootFolder = new File("/home/mcolomb/informatique/ArtiScales/");
		File zoningFile = new File(rootFolder, "dataRegulation/zoning.shp");
		File buildingFile = new File(rootFolder, "dataGeo/building.shp");
		File cityFile = new File(rootFolder, "dataGeo/communities.shp");
		ShapefileDataStore shpDSZone = new ShapefileDataStore(new File(rootFolder, "dataGeo/parcel.shp").toURI().toURL());
		SimpleFeatureCollection parcels = shpDSZone.getFeatureSource().getFeatures();
		SimpleFeatureCollection parcel = ParcelGetter.getParcelByZip(parcels, "25418");

		File mupOutput = new File(rootFolder, "/MupCityDepot/DDense/base/DDense--N7_Ba_Yag_ahpS_seed_42-evalAnal-20.0.shp");

		//zoneTotRecomp
		
		
//		//consolidRecomp
//		SimpleFeatureCollection testmp = ParcelConsolidRecomp.markParcelIntersectMUPOutput(parcel, mupOutput);
//		SimpleFeatureCollection test = ParcelConsolidRecomp.markParcelIntersectZoningType(testmp, "NC", zoningFile);
//		SimpleFeatureCollection cuted = ParcelConsolidRecomp.parcelConsolidRecomp(test, tmpFolder, 400.0, 100.0, 7.0, 3.0, 4);
//		SimpleFeatureCollection finaux = ParcelConsolidRecomp.fixParcelAttributes(cuted, tmpFolder, buildingFile, cityFile, mupOutput);

		Vectors.exportSFC(finaux, new File("/tmp/parcelDensification.shp"));
		shpDSZone.dispose();

	}
}
