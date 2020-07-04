package fr.ign.artiscales.workflow;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import org.geotools.feature.SchemaException;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.NoSuchAuthorityCodeException;

import com.opencsv.CSVReader;

import fr.ign.artiscales.goal.ZoneDivision;
import fr.ign.artiscales.parcelFunction.ParcelCollection;
import fr.ign.artiscales.scenario.PMScenario;
import fr.ign.artiscales.scenario.PMStep;
import fr.ign.cogit.geoToolsFunctions.vectors.Shp;
import fr.ign.cogit.geometryGeneration.CityGeneration;
import fr.ign.cogit.parameter.ProfileUrbanFabric;


/**
 * This method must be run in order to prepare the data for an OpenMole exploration 

 * @author Maxime Colomb
 *
 */
public class CompareSimulatedParcelsWithEvolutionOM {

	public static void main(String[] args) throws Exception {
		simulateUrbanFabricOfCSV(new File("/tmp/outOM/optimized.csv"));	
	}
	public static void run() throws Exception{
		// definition of the shapefiles representing two set of parcel
		File rootFolder = new File("src/main/resources/ParcelComparisonOM/");
		File outFolder = new File(rootFolder, "out");
		outFolder.mkdirs();
		File fileParcelPast = new File(rootFolder, "parcel2003.shp");
		File fileParcelNow = new File(rootFolder, "parcel2018.shp");

		// definition of a parameter file
		File scenarioFile = new File(rootFolder, "scenario.json");
//	}
//	
//	public static void  compareSimulatedParcelsWithEvolution() { 
		// Mark and export the parcels that have changed between the two set of time
		ParcelCollection.sortDifferentParcel(fileParcelPast, fileParcelNow, outFolder);

		// create ilots for parcel densification in case they haven't been generated before
		CityGeneration.createUrbanIslet(fileParcelPast, rootFolder);
		
		PMScenario.setSaveIntermediateResult(true);
		PMStep.setDEBUG(true);
		PMStep.setGENERATEATTRIBUTES(false);
		PMScenario pm = new PMScenario(scenarioFile, outFolder);
		pm.executeStep();
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
		}
	
	public static void simulateUrbanFabricOfCSV(File csvIn)
			throws IOException, NoSuchAuthorityCodeException, FactoryException, SchemaException {
		CSVReader r = new CSVReader(new FileReader(csvIn));
		File zoneFile = new File(
				"/home/thema/.openmole/thema-HP-ZBook-14/webui/projects/compare/donnee/parcel2003.shp");
		File parcelFile = new File("/home/thema/.openmole/thema-HP-ZBook-14/webui/projects/compare/donnee/zone.shp");
		File tmpFolder = new File("/tmp/");
		File outFolder = new File("/tmp/outOM/");
		ZoneDivision.DEBUG = false;
		String[] firstLine = r.readNext();
		for (String[] line : r.readAll()) {
			ProfileUrbanFabric profile = new ProfileUrbanFabric(firstLine, line);
			File zd = ZoneDivision.zoneDivision(zoneFile, parcelFile, profile, tmpFolder, outFolder);
			System.out.println("fait");
			Files.copy(zd.toPath(), new File(outFolder, "result" + line[0]).toPath());
		}
		r.close();
	}
}
