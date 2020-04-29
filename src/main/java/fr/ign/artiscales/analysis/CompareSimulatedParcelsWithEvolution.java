package fr.ign.artiscales.analysis;

import java.io.File;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.geotools.data.shapefile.ShapefileDataStore;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.locationtech.jts.geom.Geometry;

import fr.ign.artiscales.parcelFunction.ParcelCollection;
import fr.ign.artiscales.scenario.PMScenario;
import fr.ign.artiscales.scenario.PMStep;
import fr.ign.cogit.geoToolsFunctions.Csv;
import fr.ign.cogit.geoToolsFunctions.vectors.Collec;
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

		// definition of a parameter file
		File scenarioFile = new File(rootFolder, "scenario.json");
		
		// Mark and export the parcels that have changed between the two set of time
		ParcelCollection.markDiffParcel(fileParcelPast, fileParcelNow, outFolder);

		// create ilots for parcel densification in case they haven't been generated before
		CityGeneration.createUrbanIslet(fileParcelPast, rootFolder);
		
		PMScenario.setSaveIntermediateResult(true);
//		PMStep.setDEBUG(true);
		PMStep.setGENERATEATTRIBUTES(false);
		PMScenario pm = new PMScenario(scenarioFile, outFolder);
		pm.executeStep();
		System.out.println("++++++++++ Done with PMscenario ++++++++++");
		System.out.println();
		
		List<File> lF = new	ArrayList<File>();

		//get the intermediate files resulting of the PM steps and merge them together
		for (File f : outFolder.listFiles()) {
			if ((f.getName().contains(("Only")) && f.getName().contains(".shp"))) {
				lF.add(f);
			}
		}
		File simulatedFile = new File(outFolder, "simulatedParcels.shp");
		Shp.mergeVectFiles(lF, simulatedFile);
	
		PMStep.setParcel(fileParcelPast);
		PMStep.setPOLYGONINTERSECTION(null);
		System.out.println("++++++++++ Analysis by zones ++++++++++");
		System.out.println("steps"+ pm.getStepList());
		//we proceed with an analysis made for each steps
		PMStep.cachePlacesSimulates.clear(); 
		for (PMStep step : pm.getStepList()) {
			System.out.println("for step " + step);
			File zoneOutFolder = new File(outFolder,step.getZoneStudied());
			zoneOutFolder.mkdirs();
			Geometry geom = step.getBoundsOfZone();						
			
			// make statistic graphs
			List<AreaGraph> lAG = new ArrayList<AreaGraph>();
			HashMap<String, Object[]> csvData = new HashMap<String, Object[]>();
			
			//simulated parcels crop
			ShapefileDataStore sdsSimulatedParcel = new ShapefileDataStore(simulatedFile.toURI().toURL());
			SimpleFeatureCollection sfcSimulatedParcel = Collec.snapDatas(sdsSimulatedParcel.getFeatureSource().getFeatures(), geom);
			Collec.exportSFC(sfcSimulatedParcel, new File(zoneOutFolder, "SimulatedParcel"));
			AreaGraph areaSimulatedParcels = MakeStatisticGraphs.sortValuesAndCategorize(sfcSimulatedParcel, "Area of Simulated Parcels");
			MakeStatisticGraphs.makeGraphHisto(areaSimulatedParcels,zoneOutFolder , "Distribution on zone:"+step.getZoneStudied(), "Surface of simulated parcels",
					"Nombre ", 10);
			lAG.add(areaSimulatedParcels);
			csvData.put("SimulatedParcels", areaSimulatedParcels.getSortedDistribution().toArray());
			sdsSimulatedParcel.dispose();

			//evolved parcel crop
			ShapefileDataStore sdsEvolvedParcel = new ShapefileDataStore(new File(outFolder, "evolvedParcel.shp").toURI().toURL());
			SimpleFeatureCollection sfcEvolvedParcel = Collec.snapDatas(sdsEvolvedParcel.getFeatureSource().getFeatures(), geom);
			Collec.exportSFC(sfcEvolvedParcel, new File(zoneOutFolder, "EvolvedParcel"));
			AreaGraph areaEvolvedParcels = MakeStatisticGraphs.sortValuesAndCategorize(sfcEvolvedParcel, "Area of Evolved Parcels");
			MakeStatisticGraphs.makeGraphHisto(areaEvolvedParcels, zoneOutFolder, "Distribution on zone:" + step.getZoneStudied(),
					"Surface of evolved", "Nombre 2", 10);
			lAG.add(areaEvolvedParcels);
			csvData.put("EvolvedParcels", areaEvolvedParcels.getSortedDistribution().toArray());
			sdsEvolvedParcel.dispose();

			Csv.generateCsvFileCol(csvData, zoneOutFolder, "area");
			
			MakeStatisticGraphs.makeGraphHisto(lAG,zoneOutFolder , "Distribution de la surface des parcelles subdivisées :"+step.getZoneStudied(), "Surface d'une parcelle (m2)",
					"Nombre de parcelles", 10);
		}

		Instant end = Instant.now();
		System.out.println(Duration.between(start, end));
		
		//TODO calculate macro indocators (nombre de parcelle, distribution comparée
	}
}
