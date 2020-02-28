package analysis;

import java.io.File;
import java.time.Duration;
import java.time.Instant;

import fr.ign.cogit.geometryGeneration.CityGeneration;
import fr.ign.cogit.parcelFunction.ParcelCollection;
import scenario.PMScenario;

public class CompareSimulatedParcelsWithOriginal {
	/**
	 * This process compares the evolution of a parcel plan at two different versions (file1 and file2) with the simulation on the zone
	 * The simulation must be defined with a scenario (see package {@link ../scenario})
	 * 
	 * @param args
	 * @throws Exception
	 */
	public static void main(String[] args) throws Exception {
		Instant start = Instant.now();
		
		//definition of the shapefiles representing two set of parcel
		File rootFolder = new File("/media/mcolomb/2a3b1227-9bf5-461e-bcae-035a8845f72f/Documents/boulot/theseIGN/PM/PMtest/");
		File tmpFile = new File("/tmp/");
		File file1 = new File(rootFolder, "brie98.shp");
		File file2 = new File(rootFolder, "brie12.shp");
		
		//definition of a parameter file 
		File scenarioFile = new File(rootFolder, "jsonEx.json");
		
		// Mark and export the parcels that have changed between the two set of time
		ParcelCollection.markDiffParcel(file1, file2 , rootFolder);

		// create ilots for parcel densification in case they haven't been generated before
		CityGeneration.createUrbanIslet(file1, rootFolder);

		PMScenario pm = new PMScenario(scenarioFile, tmpFile);
		pm.executeStep();

		Instant end = Instant.now();
		System.out.println(Duration.between(start, end)); // prints PT1M3.553S
//		 markDiffParcel(new File("/tmp/a.shp"),new File("/tmp/b.shp"), new File("/tmp/"));
		
		//TODO calculate macro indocators (nombre de parcelle, distribution comparée
	}
}
