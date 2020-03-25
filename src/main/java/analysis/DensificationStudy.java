package analysis;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.geotools.data.shapefile.ShapefileDataStore;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.opengis.feature.simple.SimpleFeature;

import fr.ign.cogit.geoToolsFunctions.vectors.Collec;
import fr.ign.cogit.parcelFunction.MarkParcelAttributeFromPosition;
import goal.Densification;

public class DensificationStudy {
	
	/**
	 * This class allows to help densification studies that can be ask in French Schémas de Cohérence Territoriale (SCoT). It isolate empty parcels within urban zones (called
	 * <i>dent creuses</i> and simulates their densification. If they are too big, it simulates the creation of a whole neighborhood. Th output shapefile is called
	 * <i>parcelDentCreusesDensified.shp</i>
	 * 
	 * It also simulates the parcels that can be created with the flag parcels on already built parcels. The shapefile containing those parcels is called
	 * <i>parcelPossiblyDensified.shp</i>
	 *
	 */
	public static void main(String[] args) throws Exception {
		File rootFolder = new File("/home/ubuntu/PMtest/Densification/");
		File parcelFile = new File(rootFolder, "torcy.shp");
		File zoningFile = new File(rootFolder, "zoning.shp");
		File buildingFile = new File(rootFolder, "building.shp");
		File roadFile = new File(rootFolder, "road.shp");
		File isletFile = new File(rootFolder, "islet.shp");
		File outFolder = new File(rootFolder, "/out/");
		File tmpFolder = new File("/tmp/");
		File profileFile = new File(rootFolder, "/profileBuildingType/smallHouse.json");

		String zone = "U";
		
		ShapefileDataStore sdsIslet = new ShapefileDataStore(isletFile.toURI().toURL());
		SimpleFeatureCollection islet = sdsIslet.getFeatureSource().getFeatures();

		ShapefileDataStore sdsParcel = new ShapefileDataStore(parcelFile.toURI().toURL());
		SimpleFeatureCollection parcels = sdsParcel.getFeatureSource().getFeatures();

		// get total unbuilt parcels from the urbanized zones
//		Long nbVacantParcelU = Arrays.stream(MarkParcelAttributeFromPosition.markBuiltParcel(ParcelGetter.getParcelByZoningType("U", parcels, zoningFile), buildingFile).toArray(new SimpleFeature[0])).filter(feat -> feat.getAttribute(MarkParcelAttributeFromPosition.getMarkFieldName()).equals(0)).collect(Collectors.counting());

		SimpleFeatureCollection parcelsDentCreuseZone = MarkParcelAttributeFromPosition
				.markParcelIntersectZoningType(MarkParcelAttributeFromPosition.markUnBuiltParcel(parcels, buildingFile), zone, zoningFile);
		SimpleFeatureCollection parcelsCreated = Densification.densificationOrNeighborhood(parcelsDentCreuseZone, islet, tmpFolder, buildingFile,
				roadFile, profileFile, false);

		Collec.exportSFC(parcelsCreated, new File(outFolder, "parcelDentCreusesDensified.shp"));
			
		SimpleFeatureCollection parcelsDensifZone = MarkParcelAttributeFromPosition
				.markParcelIntersectZoningType(MarkParcelAttributeFromPosition.markBuiltParcel(parcels, buildingFile), zone, zoningFile);
		SimpleFeatureCollection parcelsDensifCreated = MarkParcelAttributeFromPosition
				.markParcelIntersectZoningType(MarkParcelAttributeFromPosition.markUnBuiltParcel(Densification.densification(parcelsDensifZone, islet, tmpFolder, buildingFile,
				roadFile, profileFile, false), buildingFile), zone, zoningFile);
		List<SimpleFeature> vacantParcelU = Arrays.stream(parcelsDensifCreated.toArray(new SimpleFeature[0])).filter(feat -> feat.getAttribute(MarkParcelAttributeFromPosition.getMarkFieldName()).equals(1)).collect(Collectors.toList());
		Collec.exportSFC(vacantParcelU, new File(outFolder, "parcelPossiblyDensified.shp"));
		System.out.println("number of dent creuses " + parcelsDentCreuseZone.size());
		System.out.println("possible to have " + parcelsCreated.size() + " buildable parcels out of it");
		System.out.println();
		System.out.println("possible to have " + vacantParcelU.size() + " densifiable parcels");
		}
}
