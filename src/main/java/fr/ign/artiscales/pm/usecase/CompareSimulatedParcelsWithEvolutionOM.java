package fr.ign.artiscales.pm.usecase;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import org.geotools.data.DataStore;
import org.geotools.data.DataUtilities;
import org.geotools.data.simple.SimpleFeatureCollection;

import com.opencsv.CSVReader;

import fr.ign.artiscales.pm.fields.GeneralFields;
import fr.ign.artiscales.pm.parcelFunction.ParcelCollection;
import fr.ign.artiscales.pm.parcelFunction.ParcelSchema;
import fr.ign.artiscales.pm.scenario.PMScenario;
import fr.ign.artiscales.pm.scenario.PMStep;
import fr.ign.artiscales.pm.workflow.ZoneDivision;
import fr.ign.artiscales.tools.geoToolsFunctions.Csv;
import fr.ign.artiscales.tools.geoToolsFunctions.vectors.Collec;
import fr.ign.artiscales.tools.geoToolsFunctions.vectors.Geopackages;
import fr.ign.artiscales.tools.geoToolsFunctions.vectors.Shp;
import fr.ign.artiscales.tools.geometryGeneration.CityGeneration;
import fr.ign.artiscales.tools.parameter.ProfileUrbanFabric;


/**
 * This method must be run in order to prepare the data for an OpenMole exploration 

 * @author Maxime Colomb
 *
 */
public class CompareSimulatedParcelsWithEvolutionOM {

	public static void main(String[] args) throws Exception {
		simulateZoneDivisionFromCSV(new File("/tmp/outOM/pop.csv"),
				new File("/home/thema/.openmole/thema-HP-ZBook-14/webui/projects/compare/donnee/parcel2003.gpkg"),
				new File("/home/thema/.openmole/thema-HP-ZBook-14/webui/projects/compare/donnee/zone.gpkg"),
				new File("/tmp/outOM/"));
		sortUniqueZoning(new File("/home/thema/.openmole/thema-HP-ZBook-14/webui/projects/compare/donnee/zone.gpkg"), new File("/home/thema/Documents/MC/workspace/ParcelManager/src/main/resources/ParcelComparison/zoning.gpkg"), new File("/tmp/out"));
	}
	public static void run() throws Exception{
		// definition of the geopackages representing two set of parcel
		File rootFolder = new File("src/main/resources/ParcelComparisonOM/");
		File outFolder = new File(rootFolder, "out");
		outFolder.mkdirs();
		File fileParcelPast = new File(rootFolder, "parcel2003.gpkg");
		File fileParcelNow = new File(rootFolder, "parcel2018.gpkg");

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
			if ((f.getName().contains(("Only")) && f.getName().contains(".gpkg")))
				lF.add(f);
		File simulatedFile = new File(outFolder, "simulatedParcels.gpkg");
		Shp.mergeVectFiles(lF, simulatedFile);
		PMStep.setParcel(fileParcelPast);
		PMStep.setPOLYGONINTERSECTION(null);
		}
	/**
	 * Simulate the Zone Division workflow from parameters contained in a CSV file (which could be an output of OpenMole)
	 * @param csvIn
	 * @throws IOException
	 */
	public static void simulateZoneDivisionFromCSV(File csvIn, File zoneFile, File parcelFile, File outFolder) throws IOException {
		CSVReader r = new CSVReader(new FileReader(csvIn));
		outFolder.mkdir();
		ZoneDivision.DEBUG = false;
		String[] firstLine = r.readNext();
		List<Integer> listId = new ArrayList<Integer>();
		for (int i = 0 ; i < firstLine.length ; i++) 
			if (firstLine[i].startsWith("Out-"))
				listId.add(i);
		int i = 0 ;
		for (String[] line : r.readAll()) 
			Files.copy((new ZoneDivision()).zoneDivision(zoneFile, parcelFile, new ProfileUrbanFabric(firstLine, line), outFolder).toPath(), new File(outFolder, i++ + Csv.makeLine(listId, line)).toPath());
		r.close();
	}
	
	/**
	 * Method to create different geopackages of each zoning type and community of an input Geopackage. 
	 * 
	 * @param toSortFile Geopackage file to sort (zones or parcels)
	 * @param zoningFile the zoning plan in a geopackage format (field names are set in the {@link GeneralFields} class)
	 * @param outFolder the folder which will contain the exported geopackages
	 * @return A folder (the same as the outFolder parameter) containing the exported Geopackages
	 * @throws IOException
	 */
	public static File sortUniqueZoning(File toSortFile, File zoningFile, File outFolder) throws IOException {
		DataStore dsToSort = Geopackages.getDataStore(toSortFile);
		DataStore dsZoning = Geopackages.getDataStore(zoningFile);
		SimpleFeatureCollection zoning = DataUtilities.collection(dsZoning.getFeatureSource(dsZoning.getTypeNames()[0]).getFeatures());
		SimpleFeatureCollection toSort = DataUtilities.collection(dsToSort.getFeatureSource(dsToSort.getTypeNames()[0]).getFeatures());
		String[] vals = {ParcelSchema.getMinParcelCommunityField(), GeneralFields.getZonePreciseNameField()}; 
		for (String uniquePreciseName : Collec.getEachUniqueFieldFromSFC(zoning, vals)) {
			SimpleFeatureCollection eachZoning = Collec.getSFCfromSFCIntersection(toSort, Collec.getSFCPart(zoning, vals, uniquePreciseName.split("-")));
			if (eachZoning == null || eachZoning.isEmpty()) 
				continue;
			Collec.exportSFC(eachZoning, new File(outFolder, uniquePreciseName));
		}
		dsToSort.dispose();
		dsZoning.dispose();
		return outFolder;
	}

}
