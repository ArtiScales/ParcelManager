package fr.ign.artiscales.analysis;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

import org.geotools.data.shapefile.ShapefileDataStore;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.locationtech.jts.geom.Geometry;
import org.opengis.feature.simple.SimpleFeature;

import fr.ign.artiscales.fields.french.FrenchZoningSchemas;
import fr.ign.artiscales.goal.Densification;
import fr.ign.artiscales.parcelFunction.MarkParcelAttributeFromPosition;
import fr.ign.artiscales.parcelFunction.ParcelAttribute;
import fr.ign.artiscales.scenario.PMScenario;
import fr.ign.artiscales.scenario.PMStep;
import fr.ign.cogit.geoToolsFunctions.Csv;
import fr.ign.cogit.geoToolsFunctions.vectors.Collec;
import fr.ign.cogit.geoToolsFunctions.vectors.Geom;
import fr.ign.cogit.geometryGeneration.CityGeneration;
import fr.ign.cogit.parameter.ProfileUrbanFabric;

/**
 * This class provides a workflow in order to help densification studies. They can be asked in French Schémas de Cohérence Territoriale (SCoT). It isolate empty parcels within urban zones (called
 * <i>vacant lot</i> and simulates their densification. If they are too big, it simulates the creation of a whole neighborhood. The output shapefile is called
 * <i>parcelDentCreusesDensified.shp</i>
 * 
 * It also simulates the parcels that can be created with the flag parcels on already built parcels. The shapefile containing those parcels is called
 * <i>parcelPossiblyDensified.shp</i>
 *
 */
public class DensificationStudy {

	public static void main(String[] args) throws Exception {
		PMStep.setGENERATEATTRIBUTES(false);
		PMScenario pmScen = new PMScenario(new File("src/main/resources/DensificationStudy/scenario.json"), new File("/tmp"));
		pmScen.executeStep();
		
////		run a densification study on a single community
//		File rootFolder = new File("/home/ubuntu/PMtest/Densification/");
//		File parcelFile = new File(rootFolder, "torcy.shp");
//		// File parcelFile = new File(rootFolder, "marked.shp");
//		File zoningFile = new File(rootFolder, "zoning.shp");
//		File buildingFile = new File(rootFolder, "building.shp");
//		File roadFile = new File(rootFolder, "road.shp");
//		File isletFile = new File(rootFolder, "islet.shp");
//		File outFolder = new File(rootFolder, "/out/");
//		File tmpFolder = new File("/tmp/");
//		ProfileUrbanFabric profile = ProfileUrbanFabric.convertJSONtoProfile(new File(rootFolder, "/profileBuildingType/smallHouse.json"));
//
//		ShapefileDataStore sdsParcel = new ShapefileDataStore(parcelFile.toURI().toURL());
//		SimpleFeatureCollection parcels = sdsParcel.getFeatureSource().getFeatures();
//
//		boolean isParcelWithoutStreetAllowed = false;
//		runDensificationStudy(parcels, isletFile, buildingFile, roadFile, zoningFile, tmpFolder, outFolder,
//				isParcelWithoutStreetAllowed, profile);
//		sdsParcel.dispose();		
		}

	/**
	 * Densification study. Can be used as a goal in scenarios
	 * @param parcels
	 * @param buildingFile
	 * @param roadFile
	 * @param zoningFile
	 * @param tmpFolder
	 * @param outFolder
	 * @param isParcelWithoutStreetAllowed
	 * @param profile
	 * @throws Exception
	 */
	public static void runDensificationStudy(SimpleFeatureCollection parcels, File buildingFile, File roadFile, File zoningFile,
			File tmpFolder, File outFolder, boolean isParcelWithoutStreetAllowed, ProfileUrbanFabric profile) throws Exception {
		outFolder.mkdir();
		SimpleFeatureCollection islet = CityGeneration.createUrbanIslet(parcels);

		String splitField = MarkParcelAttributeFromPosition.getMarkFieldName();
		// get total unbuilt parcels from the urbanized zones
		SimpleFeatureCollection parcelsVacantLot = MarkParcelAttributeFromPosition
				.markParcelIntersectFrenchConstructibleZoningType(MarkParcelAttributeFromPosition.markUnBuiltParcel(parcels, buildingFile),
						zoningFile);
//		Collec.exportSFC(parcelsVacantLot, new File("/tmp/parcelsVacantLot"));
		SimpleFeatureCollection parcelsVacantLotCreated = Densification.densificationOrNeighborhood(parcelsVacantLot, islet, tmpFolder, buildingFile,
				roadFile, profile, isParcelWithoutStreetAllowed, Geom.createBufferBorder(parcels));
//		Collec.exportSFC(parcelsVacantLotCreated, new File("/tmp/parcelsVacantLotCreated"));

		// simulate the densification of built parcels in the given zone
		SimpleFeatureCollection parcelsDensifZone = MarkParcelAttributeFromPosition
				.markParcelIntersectFrenchConstructibleZoningType(MarkParcelAttributeFromPosition.markBuiltParcel(parcels, buildingFile), zoningFile);
//		Collec.exportSFC(parcelsDensifZone, new File("/tmp/parcelsDensifZone"));

		SimpleFeatureCollection parcelsDensifCreated = Densification.densificationOrNeighborhood(parcelsDensifZone, islet, tmpFolder, buildingFile,
				roadFile, profile, isParcelWithoutStreetAllowed, Geom.createBufferBorder(parcels));
//		Collec.exportSFC(parcelsDensifCreated, new File("/tmp/parcelsDensifCreated"));

		// change split name to show if they can be built
		MarkParcelAttributeFromPosition.setMarkFieldName("BUILDABLE");
		// Mark the simulated parcels that doesn't contains buildings (and therefore can be build)
		parcelsVacantLotCreated = MarkParcelAttributeFromPosition.markUnBuiltParcel(MarkParcelAttributeFromPosition.markSimulatedParcel(parcelsVacantLotCreated),
				buildingFile);

		parcelsDensifCreated = MarkParcelAttributeFromPosition
				.markUnBuiltParcel(MarkParcelAttributeFromPosition.markSimulatedParcel(parcelsDensifCreated), buildingFile);
		// If the parcels have to be connected to the road, we mark them
		if (!isParcelWithoutStreetAllowed) {
			parcelsVacantLotCreated = MarkParcelAttributeFromPosition.markParcelsConnectedToRoad(parcelsVacantLotCreated, islet, roadFile);
			parcelsDensifCreated = MarkParcelAttributeFromPosition.markParcelsConnectedToRoad(parcelsDensifCreated, islet, roadFile);
		}
		// exporting output shapefiles and countings
		List<SimpleFeature> vacantParcelU = Arrays.stream(parcelsDensifCreated.toArray(new SimpleFeature[0]))
				.filter(feat -> feat.getAttribute(MarkParcelAttributeFromPosition.getMarkFieldName()).equals(1)).collect(Collectors.toList());
		Collec.exportSFC(parcelsVacantLot, new File(outFolder, "parcelVacantLot.shp"), false);
		Collec.exportSFC(parcelsVacantLotCreated, new File(outFolder, "parcelVacantLotDensified.shp"), false);
		Collec.exportSFC(vacantParcelU, new File(outFolder, "parcelPossiblyDensified.shp"), false);
		
		long nbVacantLot = Arrays.stream(parcelsVacantLot.toArray(new SimpleFeature[0])).filter(feat -> feat.getAttribute(splitField).equals(1)).count();
		long nbVacantLotParcels = Arrays.stream(parcelsVacantLotCreated.toArray(new SimpleFeature[0]))
				.filter(feat -> feat.getAttribute(MarkParcelAttributeFromPosition.getMarkFieldName()).equals(1)).count();
		System.out.println("number of vacant lots "+ nbVacantLot);
		System.out.println("possible to have "+ nbVacantLotParcels + " buildable parcels out of it");
		System.out.println();
		System.out.println("possible to have " + vacantParcelU.size() + " densifiable parcels");
		
		ShapefileDataStore sds = new ShapefileDataStore(zoningFile.toURI().toURL());
		SimpleFeatureCollection zoning = sds.getFeatureSource().getFeatures();
		long nbParcelsInUrbanizableZones = Arrays.stream(parcels.toArray(new SimpleFeature[0]))
				.filter(feat -> FrenchZoningSchemas
						.isUrbanZoneUsuallyAdmitResidentialConstruction(Collec.getSimpleFeatureFromSFC((Geometry) feat.getDefaultGeometry(), zoning)))
				.count();
		sds.dispose();
		
		// saving the stats in a .csv file
		String[] firstline = { "parcels in urbanizable zones", "DEPCOM", "number of vacant lots", "parcels simulated in vacant lots",
				"parcels simulated by densification" };
		Object[] line = { nbParcelsInUrbanizableZones, nbVacantLot, nbVacantLotParcels, vacantParcelU.size() };
		HashMap<String, Object[]> l = new HashMap<String,Object[]>();
		l.put(ParcelAttribute.getCityCodeOfParcels(parcelsDensifCreated), line);
		Csv.generateCsvFile(l, outFolder, "densificationStudyResult", firstline, true);
		Csv.needFLine = false;
		
		//redo normal mark name 
		MarkParcelAttributeFromPosition.setMarkFieldName("SPLIT");
	}
}
