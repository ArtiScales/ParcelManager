package fr.ign.artiscales.pm.usecase;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

import org.geotools.data.DataStore;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.locationtech.jts.geom.Geometry;
import org.opengis.feature.simple.SimpleFeature;

import fr.ign.artiscales.pm.fields.GeneralFields;
import fr.ign.artiscales.pm.fields.french.FrenchZoningSchemas;
import fr.ign.artiscales.pm.parcelFunction.MarkParcelAttributeFromPosition;
import fr.ign.artiscales.pm.parcelFunction.ParcelAttribute;
import fr.ign.artiscales.pm.scenario.PMScenario;
import fr.ign.artiscales.pm.scenario.PMStep;
import fr.ign.artiscales.pm.workflow.Densification;
import fr.ign.artiscales.tools.carto.JoinCSVToGeoFile;
import fr.ign.artiscales.tools.carto.MergeByAttribute;
import fr.ign.artiscales.tools.geoToolsFunctions.Csv;
import fr.ign.artiscales.tools.geoToolsFunctions.vectors.Collec;
import fr.ign.artiscales.tools.geoToolsFunctions.vectors.Geom;
import fr.ign.artiscales.tools.geoToolsFunctions.vectors.Geopackages;
import fr.ign.artiscales.tools.geometryGeneration.CityGeneration;
import fr.ign.artiscales.tools.parameter.ProfileUrbanFabric;

/**
 * This class provides a workflow in order to help densification studies. They can be asked in French Schémas de Cohérence Territoriale (SCoT). It isolate empty parcels within
 * urban zones (called <i>vacant lot</i> and simulates their densification. If they are too big, it simulates the creation of a whole neighborhood. The output Geopackages is called
 * <i>parcelDentCreusesDensified</i>
 * 
 * It also simulates the parcels that can be created with the flag parcels on already built parcels. The geopackage containing those parcels is called
 * <i>parcelPossiblyDensified</i>
 *
 */
public class DensificationStudy {
	public static void main(String[] args) throws Exception {
		File rootFile = new File("src/main/resources/DensificationStudy/");
		File outFolder = new File(rootFile, "out");
		PMStep.setGENERATEATTRIBUTES(false);
		PMScenario pmScen = new PMScenario(new File(rootFile, "scenario.json"), new File("/tmp"));
		pmScen.executeStep();
		for (int i = 1; i <= 4; i++)
			Csv.calculateColumnsBasicStat(new File(outFolder, "densificationStudyResult.csv"), i, true);
		// make a (nice) map out of it
		DataStore ds = Geopackages.getDataStore(new File(rootFile, "parcel.gpkg"));
		JoinCSVToGeoFile.joinCSVToGeoFile(
				MergeByAttribute.mergeByAttribute(GeneralFields.addCommunityCode(ds.getFeatureSource(ds.getTypeNames()[0]).getFeatures()),
						GeneralFields.getZoneCommunityCode()),
				GeneralFields.getZoneCommunityCode(), new File(outFolder, "densificationStudyResult.csv"), GeneralFields.getZoneCommunityCode(),
				new File(outFolder, "CityStat"), null);
		ds.dispose();
	}

	/**
	 * Densification study. Can be used as a workflows in scenarios.
	 * 
	 * @param parcels
	 * @param buildingFile
	 * @param roadFile
	 * @param zoningFile
	 * @param outFolder
	 * @param isParcelWithoutStreetAllowed
	 * @param profile
	 * @throws Exception
	 */
	public static void runDensificationStudy(SimpleFeatureCollection parcels, File buildingFile, File roadFile, File zoningFile,
			File outFolder, boolean isParcelWithoutStreetAllowed, ProfileUrbanFabric profile) throws Exception {
		outFolder.mkdir();
		SimpleFeatureCollection islet = CityGeneration.createUrbanIslet(parcels);
		Geometry buffer = Geom.createBufferBorder(parcels);
		String splitField = MarkParcelAttributeFromPosition.getMarkFieldName();
		// get total unbuilt parcels from the urbanized zones
		SimpleFeatureCollection parcelsVacantLot = MarkParcelAttributeFromPosition.markParcelIntersectFrenchConstructibleZoningType(
				MarkParcelAttributeFromPosition.markUnBuiltParcel(parcels, buildingFile), zoningFile);
		// Collec.exportSFC(parcelsVacantLot, new File("/tmp/parcelsVacantLot"));
		SimpleFeatureCollection parcelsVacantLotCreated = (new Densification()).densificationOrNeighborhood(parcelsVacantLot, islet, outFolder, buildingFile,
				roadFile, profile, isParcelWithoutStreetAllowed, buffer, 5);
		// Collec.exportSFC(parcelsVacantLotCreated, new File("/tmp/parcelsVacantLotCreated"));

		// simulate the densification of built parcels in the given zone
		SimpleFeatureCollection parcelsDensifZone = MarkParcelAttributeFromPosition
				.markParcelIntersectFrenchConstructibleZoningType(MarkParcelAttributeFromPosition.markBuiltParcel(parcels, buildingFile), zoningFile);
		// Collec.exportSFC(parcelsDensifZone, new File("/tmp/parcelsDensifZone"));

		SimpleFeatureCollection parcelsDensifCreated = (new Densification()).densification(parcelsDensifZone, islet, outFolder, buildingFile,
				roadFile, profile, isParcelWithoutStreetAllowed, buffer);
		// Collec.exportSFC(parcelsDensifCreated, new File("/tmp/parcelsDensifCreated"));

		// change split name to show if they can be built and start postprocessing
		String firstMarkFieldName = MarkParcelAttributeFromPosition.getMarkFieldName();
		MarkParcelAttributeFromPosition.setMarkFieldName("BUILDABLE");
		MarkParcelAttributeFromPosition.setPostMark(true);
		// Mark the simulated parcels that doesn't contains buildings (and therefore can be build)
		parcelsVacantLotCreated = MarkParcelAttributeFromPosition
				.markUnBuiltParcel(MarkParcelAttributeFromPosition.markSimulatedParcel(parcelsVacantLotCreated), buildingFile);

		parcelsDensifCreated = MarkParcelAttributeFromPosition
				.markUnBuiltParcel(MarkParcelAttributeFromPosition.markSimulatedParcel(parcelsDensifCreated), buildingFile);
		// If the parcels have to be connected to the road, we mark them
		if (!isParcelWithoutStreetAllowed) {
			parcelsVacantLotCreated = MarkParcelAttributeFromPosition.markParcelsConnectedToRoad(parcelsVacantLotCreated, CityGeneration.createUrbanIslet(parcelsVacantLotCreated), roadFile, buffer);
			parcelsDensifCreated = MarkParcelAttributeFromPosition.markParcelsConnectedToRoad(parcelsDensifCreated, islet, roadFile, buffer);
		}
		// exporting output geopackages and countings
		List<SimpleFeature> vacantParcelU = Arrays.stream(parcelsDensifCreated.toArray(new SimpleFeature[0]))
				.filter(feat -> feat.getAttribute(MarkParcelAttributeFromPosition.getMarkFieldName()).equals(1)).collect(Collectors.toList());
		Collec.exportSFC(parcelsVacantLot, new File(outFolder, "parcelVacantLot"), false);
		Collec.exportSFC(parcelsVacantLotCreated, new File(outFolder, "parcelVacantLotDensified"), false);
		Collec.exportSFC(vacantParcelU, new File(outFolder, "parcelPossiblyDensified"), false);

		long nbVacantLot = Arrays.stream(parcelsVacantLot.toArray(new SimpleFeature[0])).filter(feat -> feat.getAttribute(splitField).equals(1))
				.count();
		long nbVacantLotParcels = Arrays.stream(parcelsVacantLotCreated.toArray(new SimpleFeature[0]))
				.filter(feat -> feat.getAttribute(MarkParcelAttributeFromPosition.getMarkFieldName()).equals(1)).count();
		System.out.println("number of vacant lots " + nbVacantLot);
		System.out.println("possible to have " + nbVacantLotParcels + " buildable parcels out of it");
		System.out.println();
		System.out.println("possible to have " + vacantParcelU.size() + " parcels with densification process");

		DataStore sds = Geopackages.getDataStore(zoningFile);
		SimpleFeatureCollection zoning = sds.getFeatureSource(sds.getTypeNames()[0]).getFeatures();
		long nbParcelsInUrbanizableZones = Arrays.stream(parcels.toArray(new SimpleFeature[0]))
				.filter(feat -> FrenchZoningSchemas
						.isUrbanZoneUsuallyAdmitResidentialConstruction(Collec.getIntersectingSimpleFeatureFromSFC((Geometry) feat.getDefaultGeometry(), zoning)))
				.count();
		sds.dispose();

		// saving the stats in a .csv file
		String[] firstline = { GeneralFields.getZoneCommunityCode(), "parcels in urbanizable zones", "number of vacant lots", "parcels simulated in vacant lots",
				"parcels simulated by densification" };
		Object[] line = { nbParcelsInUrbanizableZones, nbVacantLot, nbVacantLotParcels, vacantParcelU.size() };
		HashMap<String, Object[]> l = new HashMap<String, Object[]>();
		l.put(ParcelAttribute.getCityCodeOfParcels(parcelsDensifCreated), line);
		Csv.generateCsvFile(l, outFolder, "densificationStudyResult", firstline, true);
		Csv.needFLine = false;
		// redo normal mark name
		MarkParcelAttributeFromPosition.setMarkFieldName(firstMarkFieldName);
	}
}
