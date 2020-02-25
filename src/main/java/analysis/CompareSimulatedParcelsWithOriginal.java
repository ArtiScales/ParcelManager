package analysis;

import java.io.File;
import java.time.Duration;
import java.time.Instant;

import fr.ign.cogit.geometryGeneration.CityGeneration;
import fr.ign.cogit.parcelFunction.ParcelCollection;
import scenario.PMScenario;

public class CompareSimulatedParcelsWithOriginal {
	/**
	 * 
	 * 
	 * @param args
	 * @throws Exception
	 */
	public static void main(String[] args) throws Exception {
		Instant start = Instant.now();
		File rootFolder = new File("/home/ubuntu/Documents/PMtest/");
		 ParcelCollection.markDiffParcel(new File(rootFolder, "brie98.shp"),new File(rootFolder, "brie12.shp"), rootFolder);
		 CityGeneration.CreateIlots(new File(rootFolder, "brie98.shp"), rootFolder);

		PMScenario pm = new PMScenario(new File(rootFolder, "jsonEx.json"), new File("/tmp/"));
		pm.executeStep();

		Instant end = Instant.now();
		System.out.println(Duration.between(start, end)); // prints PT1M3.553S
		// markDiffParcel(new File("/tmp/a.shp"),new File("/tmp/b.shp"), new File("/tmp/"));
	}
}
