package analysis;

import java.io.File;
import java.util.Arrays;
import java.util.stream.Collectors;

import org.geotools.data.shapefile.ShapefileDataStore;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.opengis.feature.simple.SimpleFeature;

import fr.ign.cogit.parcelFunction.MarkParcelAttributeFromPosition;
import fr.ign.cogit.parcelFunction.ParcelGetter;
import goal.Densification;

public class DensificationRatio {
/**
 * This class allows to simulate the densification on a certain ratio of non-built parcels in a urban context.
 * Densification ratios can be specifically set in French Schémas de Cohérence Territorials (SCoT)
 */
	
	public static void main(String[] args) throws Exception {
		double ratio = 0.5;
		File rootFolder = new File("/home/ubuntu/PMtest/SeineEtMarne/");
		File parcelFile = new File(rootFolder, "parcel.shp");
		File zoningFile = new File(rootFolder, "zoning.shp");
		File buildingFile = new File(rootFolder, "building.shp");
		File roadFile = new File(rootFolder, "parcel.shp");
		File isletFile = new File(rootFolder, "silet.shp");
		File outFolder = new File(rootFolder, "/out/");
		File tmpFolder = new File(rootFolder, "/out/");
		File profileFile = new File(rootFolder, "/profileFolder/smallHouse.json");

		ShapefileDataStore sdsIslet = new ShapefileDataStore(parcelFile.toURI().toURL());
		SimpleFeatureCollection islet = sdsIslet.getFeatureSource().getFeatures();

		ShapefileDataStore sdsParcel = new ShapefileDataStore(parcelFile.toURI().toURL());
		SimpleFeatureCollection parcels = sdsParcel.getFeatureSource().getFeatures();

		// get total unbuilt parcels from the urbanized zones
		Long nbVacantParcelU = Arrays
				.stream(MarkParcelAttributeFromPosition
						.markBuiltParcel(ParcelGetter.getParcelByZoningType("U", parcels, zoningFile), buildingFile)
						.toArray(new SimpleFeature[0]))
				.filter(feat -> feat.getAttribute(MarkParcelAttributeFromPosition.getMarkFieldName()).equals(1))
				.collect(Collectors.counting());
		System.out.println(nbVacantParcelU + " vacant parcel in U ");
		SimpleFeatureCollection parcelsToSimulate = MarkParcelAttributeFromPosition.markRandomParcels(parcels, "U",
				zoningFile, 100.0, (int) Math.round(ratio * Double.valueOf(nbVacantParcelU)));
		Densification.densification(parcelsToSimulate, islet, tmpFolder, buildingFile,roadFile, profileFile, false);
	}

	
	public static void simulateRandomParcels() {
		
	}
	
	public static void simulateBestEvaluatedParcels() {
		
	}
}
