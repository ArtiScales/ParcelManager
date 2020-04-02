package fr.ign.artiscales.analysis;

import java.io.File;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

import org.geotools.data.shapefile.ShapefileDataStore;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.locationtech.jts.geom.Geometry;

import fr.ign.artiscales.parcelFunction.ParcelCollection;
import fr.ign.cogit.geoToolsFunctions.Csv;
import fr.ign.cogit.geoToolsFunctions.vectors.Collec;
import fr.ign.cogit.geoToolsFunctions.vectors.Shp;
import fr.ign.cogit.geometryGeneration.CityGeneration;
import fr.ign.artiscales.scenario.PMScenario;
import fr.ign.artiscales.scenario.PMStep;


/**
 * This process compares the evolution of a parcel plan at two different versions (file1 and file2) with the simulation on the zone.
 * The simulation must be defined with a scenario (see package {@link fr.ign.artiscales.scenario}).

 * @author Maxime Colomb
 *
 */
public class CompareSimulatedParcelsWithEvolution {

	public static void main(String[] args) throws Exception {
		Instant start = Instant.now();
		
		//definition of the shapefiles representing two set of parcel
		File rootFolder = new File("/home/ubuntu/PMtest/SeineEtMarne/");
		File outFolder = new File("/home/ubuntu/PMtest/SeineEtMarne/out");
		File tmpFolder = new File("/home/ubuntu/PMtest/SeineEtMarne/out/");
		outFolder.mkdirs();
		File file1 = new File(rootFolder, "PARCELLE03.SHP");
		File file2 = new File(rootFolder, "PARCELLE12In.shp");
		
		//definition of a parameter file 
		File scenarioFile = new File(rootFolder, "jsonEx.json");
		
		// Mark and export the parcels that have changed between the two set of time
//		ParcelCollection.markDiffParcel(file1, file2, rootFolder, tmpFolder);
//
//		// create ilots for parcel densification in case they haven't been generated before
//		CityGeneration.createUrbanIslet(file1, rootFolder);
		
		PMScenario.setSaveIntermediateResult(true);
		PMStep.setGENERATEATTRIBUTES(false);
		PMScenario pm = new PMScenario(scenarioFile, outFolder);
		pm.executeStep();
		
		List<File> lF = new	ArrayList<File>();

		//get the intermediate files resulting of the PM steps and merge them together
		for (File f : outFolder.listFiles()) {
			if ((f.getName().contains(("Only")) && f.getName().contains(".shp"))) {
				lF.add(f);
			}
		}
		File simulatedFile = new File(outFolder, "simulatedParcels.shp");
		Shp.mergeVectFiles(lF, simulatedFile);
	
		PMStep.setParcel(file1);
		PMStep.setPOLYGONINTERSECTION(null);
		System.out.println("++++++++++analysis by zones++++++++++");
		//we proceed with an analysis made for each steps
		for (PMStep step : pm.getStepList()) {
			File zoneOutFolder = new File(outFolder,step.getZoneStudied());
			zoneOutFolder.mkdirs();
			Geometry geom = step.getBoundsOfZone();						
			
			// make statistic graphs
			List<AreaGraph> lAG = new ArrayList<AreaGraph>();
			Hashtable<String, Object[]> csvData = new Hashtable<String, Object[]>();
			
			//simulated parcels crop
			ShapefileDataStore sdsSimulatedParcel = new ShapefileDataStore(simulatedFile.toURI().toURL());
			SimpleFeatureCollection sfcSimulatedParcel = Collec.snapDatas(sdsSimulatedParcel.getFeatureSource().getFeatures(),geom);
			Collec.exportSFC(sfcSimulatedParcel, new File(zoneOutFolder,"SimulatedParcel"));
			AreaGraph areaSimulatedParcels = MakeStatisticGraphs.sortValuesAndCategorize(sfcSimulatedParcel,"Area of Simulated Parcels");
			MakeStatisticGraphs.makeGraphHisto(areaSimulatedParcels,zoneOutFolder , "Distribution on zone:"+step.getZoneStudied(), "Surface of simulated parcels",
					"Nombre ", 10);
			lAG.add(areaSimulatedParcels);
			csvData.put("SimulatedParcels", areaSimulatedParcels.getSortedDistribution().toArray());
			sdsSimulatedParcel.dispose();

			//evolved parcel crop
			ShapefileDataStore sdsEvolvedParcel = new ShapefileDataStore(new File(rootFolder, "evolvedParcel.shp").toURI().toURL());
			SimpleFeatureCollection sfcEvolvedParcel = Collec.snapDatas(sdsEvolvedParcel.getFeatureSource().getFeatures(), geom);
			Collec.exportSFC(sfcEvolvedParcel, new File(zoneOutFolder,"EvolvedParcel"));
			AreaGraph areaEvolvedParcels = MakeStatisticGraphs.sortValuesAndCategorize(sfcEvolvedParcel,"Area of Evolved Parcels");
			MakeStatisticGraphs.makeGraphHisto(areaEvolvedParcels,zoneOutFolder , "Distribution on zone:"+step.getZoneStudied(), "Surface of evolved",
					"Nombre 2", 10);
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
