package fr.ign.artiscales.workflow;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import fr.ign.artiscales.parcelFunction.ParcelCollection;
import fr.ign.artiscales.scenario.PMScenario;
import fr.ign.artiscales.scenario.PMStep;
import fr.ign.cogit.geoToolsFunctions.vectors.Shp;
import fr.ign.cogit.geometryGeneration.CityGeneration;


/**
 * This method must be run in order to prepare the data for an OpenMole exploration 

 * @author Maxime Colomb
 *
 */
public class CompareSimulatedParcelsWithEvolutionOM {

	public static void main(String[] args) throws Exception {
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
}
