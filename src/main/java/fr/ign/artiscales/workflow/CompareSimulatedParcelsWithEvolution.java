package fr.ign.artiscales.workflow;

import java.io.File;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.geotools.data.collection.SpatialIndexFeatureCollection;
import org.geotools.data.shapefile.ShapefileDataStore;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.factory.CommonFactoryFinder;
import org.geotools.util.factory.GeoTools;
import org.locationtech.jts.geom.Geometry;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.filter.FilterFactory2;

import fr.ign.artiscales.analysis.SingleParcelStat;
import fr.ign.artiscales.parcelFunction.MarkParcelAttributeFromPosition;
import fr.ign.artiscales.parcelFunction.ParcelCollection;
import fr.ign.artiscales.scenario.PMScenario;
import fr.ign.artiscales.scenario.PMStep;
import fr.ign.cogit.geoToolsFunctions.vectors.Collec;
import fr.ign.cogit.geoToolsFunctions.vectors.Geom;
import fr.ign.cogit.geoToolsFunctions.vectors.Shp;
import fr.ign.cogit.geometryGeneration.CityGeneration;


/**
 * This process compares the evolution of a parcel plan at two different versions (file1 and file2) with the simulation on the zone.
 * The simulation must be defined with a scenario (see package {@link fr.ign.artiscales.scenario}).

 * @author Maxime Colomb
 *
 */
public class CompareSimulatedParcelsWithEvolution {

	public static void main(String[] args) throws Exception {
		Instant start = Instant.now();
		// definition of the shapefiles representing two set of parcel
		File rootFolder = new File("src/main/resources/ParcelComparison/");
		File outFolder = new File(rootFolder, "out");
		outFolder.mkdirs();
		File fileParcelPast = new File(rootFolder, "parcel2003.shp");
		File fileParcelNow = new File(rootFolder, "parcel2018.shp");
		File roadFile = new File(rootFolder, "road.shp");

		// definition of a parameter file
		File scenarioFile = new File(rootFolder, "scenario.json");
		
		// Mark and export the parcels that have changed between the two set of time
		ParcelCollection.sortDifferentParcel(fileParcelPast, fileParcelNow, outFolder);

		// create ilots for parcel densification in case they haven't been generated before
		CityGeneration.createUrbanIslet(fileParcelPast, rootFolder);
		
		PMScenario.setSaveIntermediateResult(true);
//		PMStep.setDEBUG(true);
		PMStep.setGENERATEATTRIBUTES(false);
		PMScenario pm = new PMScenario(scenarioFile, outFolder);
//		pm.executeStep();
		System.out.println("++++++++++ Done with PMscenario ++++++++++");
		System.out.println();
		
		List<File> lF = new	ArrayList<File>();

		//get the intermediate files resulting of the PM steps and merge them together
		for (File f : outFolder.listFiles())
			if ((f.getName().contains(("Only")) && f.getName().contains(".shp")))
				lF.add(f);
		File simulatedFile = new File(outFolder, "simulatedParcels.shp");
		Shp.mergeVectFiles(lF, simulatedFile);
	
		PMStep.setParcel(fileParcelPast);
		PMStep.setPOLYGONINTERSECTION(null);
		System.out.println("++++++++++ Analysis by zones ++++++++++");
		System.out.println("steps"+ pm.getStepList());
		//we proceed with an analysis made for each steps
		PMStep.cachePlacesSimulates.clear(); 
		MarkParcelAttributeFromPosition.setPostMark(true);
		FilterFactory2 ff = CommonFactoryFinder.getFilterFactory2(GeoTools.getDefaultHints());
		ShapefileDataStore sdsRoad = new ShapefileDataStore(roadFile.toURI().toURL());
		for (PMStep step : pm.getStepList()) {
			System.out.println("analysis for step " + step);
			File zoneOutFolder = new File(outFolder, step.getZoneStudied());
			zoneOutFolder.mkdirs();
			List<Geometry> geoms = step.getBoundsOfZone();
			Geometry geomUnion = Geom.unionPrecisionReduce(geoms, 100).buffer(1);

			// simulated parcels crop
			ShapefileDataStore sdsSimulatedParcel = new ShapefileDataStore(simulatedFile.toURI().toURL());
			SimpleFeatureCollection sfcSimulatedParcel = sdsSimulatedParcel.getFeatureSource().getFeatures().subCollection(
					ff.within(ff.property(sdsSimulatedParcel.getSchema().getGeometryDescriptor().getLocalName()), ff.literal(geomUnion)));
			Collec.exportSFC(sfcSimulatedParcel, new File(zoneOutFolder, "SimulatedParcel"));
			
			ShapefileDataStore sdsEvolvedParcel = new ShapefileDataStore(new File(outFolder, "evolvedParcel.shp").toURI().toURL());
			SimpleFeatureCollection sfcEvolvedParcel = Collec.snapDatas(sdsEvolvedParcel.getFeatureSource().getFeatures(), geomUnion);
			Collec.exportSFC(sfcEvolvedParcel, new File(zoneOutFolder, "EvolvedParcel"));
			
//			ShapefileDataStore sdsFinalParcel = new ShapefileDataStore(step.getLastOutput().toURI().toURL());
			ShapefileDataStore sdsFinalParcel = new ShapefileDataStore(new File("/home/ubuntu/workspace/ParcelManager/src/main/resources/ParcelComparison/out/parcelCuted-consolidationDivision-smallHouse-NC_.shp").toURI().toURL());
			SingleParcelStat.writeStatSingleParcel(
					MarkParcelAttributeFromPosition.markSimulatedParcel(MarkParcelAttributeFromPosition.markParcelIntersectPolygonIntersection(
							sdsFinalParcel.getFeatureSource().getFeatures(), geoms.stream().map(g -> g.buffer(-2)).collect(Collectors.toList()))),
					sfcEvolvedParcel, Collec.snapDatas(new SpatialIndexFeatureCollection(sdsRoad.getFeatureSource().getFeatures()), geomUnion),
					new File(zoneOutFolder, "SimulatedParcelStats.csv"));
			sdsSimulatedParcel.dispose();
			sdsFinalParcel.dispose();

			//evolved parcel crop
			System.out.println("evolved parcels");
			ShapefileDataStore sdsEvolvedAndAllParcels = new ShapefileDataStore(fileParcelNow.toURI().toURL());
			SingleParcelStat.writeStatSingleParcel(
					MarkParcelAttributeFromPosition.markParcelIntersectPolygonIntersection(sdsEvolvedAndAllParcels.getFeatureSource().getFeatures(),
							Arrays.stream(sfcEvolvedParcel.toArray(new SimpleFeature[0])).map(g -> ((Geometry) g.getDefaultGeometry()).buffer(-1)).collect(Collectors.toList())),
					Collec.snapDatas(new SpatialIndexFeatureCollection(sdsRoad.getFeatureSource().getFeatures()), geomUnion), new File(zoneOutFolder, "EvolvedParcelStats.csv"));
			sdsEvolvedParcel.dispose();
		}
		sdsRoad.dispose();
		Instant end = Instant.now();
		System.out.println(Duration.between(start, end));
		}
}
