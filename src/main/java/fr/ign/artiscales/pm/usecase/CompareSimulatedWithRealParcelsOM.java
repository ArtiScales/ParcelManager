package fr.ign.artiscales.pm.usecase;

import com.opencsv.CSVReader;
import fr.ign.artiscales.pm.fields.GeneralFields;
import fr.ign.artiscales.pm.parcelFunction.MarkParcelAttributeFromPosition;
import fr.ign.artiscales.pm.parcelFunction.ParcelCollection;
import fr.ign.artiscales.pm.parcelFunction.ParcelSchema;
import fr.ign.artiscales.pm.scenario.PMScenario;
import fr.ign.artiscales.pm.scenario.PMStep;
import fr.ign.artiscales.pm.workflow.ConsolidationDivision;
import fr.ign.artiscales.pm.workflow.Workflow;
import fr.ign.artiscales.pm.workflow.ZoneDivision;
import fr.ign.artiscales.tools.geoToolsFunctions.vectors.collec.CollecMgmt;
import fr.ign.artiscales.tools.geoToolsFunctions.vectors.collec.CollecTransform;
import fr.ign.artiscales.tools.geometryGeneration.CityGeneration;
import fr.ign.artiscales.tools.io.csv.Csv;
import fr.ign.artiscales.tools.io.csv.CsvOp;
import fr.ign.artiscales.tools.parameter.ProfileUrbanFabric;
import org.geotools.data.DataStore;
import org.geotools.data.DataUtilities;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.feature.DefaultFeatureCollection;
import org.opengis.feature.simple.SimpleFeature;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;


/**
 * This method must be run in order to prepare the data for an OpenMole exploration
 *
 * @author Maxime Colomb
 */
public class CompareSimulatedWithRealParcelsOM {

    public static void main(String[] args) throws Exception {
        File root = new File("/home/mc/.openmole/mc-Latitude-5410/webui/projects/donnee/");
//        SimpleFeatureCollection parcelSimuled = (new ZoneDivision()).zoneDivision(new File(root, "zone.gpkg"), new File(root, "parcel2003.gpkg"),
//                new File("/tmp/"), ProfileUrbanFabric.convertJSONtoProfile(new File("src/main/resources/TestScenario/profileUrbanFabric/mediumHouse.json")), new File(root, "road2003.gpkg"), new File(root, "building2003.gpkg"));
//        double hausdorfDistance = SingleParcelStat.hausdorffDistance(parcelSimuled, new File(root, "realParcel.gpkg"));
//        System.out.println(hausdorfDistance);
        Csv.sep = ',';
        simulateZoneDivisionFromCSV(new File("/home/mc/workspace/parcelmanager/openmole/exResult.csv"), new File(root, "zone.gpkg"), new File(root, "road2003.gpkg"), new File(root, "building2003.gpkg"), new File(root, "parcel2003.gpkg"), new File("/tmp/calibration"), "OBB");
    }

    public static void run() throws Exception {
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
        CityGeneration.createUrbanBlock(fileParcelPast, rootFolder);

        PMScenario.setSaveIntermediateResult(true);
        PMStep.setDEBUG(true);
        PMScenario pm = new PMScenario(scenarioFile);
        pm.executeStep();
        System.out.println("++++++++++ Done with PMscenario ++++++++++");
        System.out.println();

        List<File> lF = new ArrayList<>();

        //get the intermediate files resulting of the PM steps and merge them together
        for (File f : Objects.requireNonNull(outFolder.listFiles()))
            if ((f.getName().contains(("Only")) && f.getName().contains(".gpkg")))
                lF.add(f);
        File simulatedFile = new File(outFolder, "simulatedParcels.gpkg");
        CollecMgmt.mergeFiles(lF, simulatedFile);
        PMStep.setParcel(fileParcelPast);
        PMStep.setPOLYGONINTERSECTION(null);
    }

    public static void setProcess(int processNb) {
        switch (processNb) {
            case 0:
                Workflow.PROCESS = "SSoffset";
                break;
            case 1:
                Workflow.PROCESS = "SS";
                break;
            case 2:
                Workflow.PROCESS = "SSThenOBB";
                break;
            case 3:
                Workflow.PROCESS = "OBB";
                break;
            case 4:
                Workflow.PROCESS = "FlagDivision";
                break;
            default:
                throw new IllegalArgumentException("setProcess : not supposed to have upper values");
        }
    }

    /**
     * Simulate the Zone Division workflow from parameters contained in a CSV file (which could be an output of OpenMole)
     */
    public static void simulateZoneDivisionFromCSV(File csvIn, File zoneFile, File roadFile, File buildingFile, File parcelFile, File outFolder, String process) throws IOException {
        CSVReader r = new CSVReader(new FileReader(csvIn));
        outFolder.mkdir();
        String[] firstLine = r.readNext();
        List<Integer> listId = Arrays.asList(0, 12);
        int i = 0;
        for (String[] line : r.readAll()) {
            Workflow.PROCESS =process;
            CollecMgmt.exportSFC((new ZoneDivision()).zoneDivision(zoneFile, parcelFile, outFolder, new ProfileUrbanFabric(firstLine, line), roadFile, buildingFile),
                    new File(outFolder, i++ + CsvOp.makeLine(listId, line)));

        }
        r.close();
    }

    /**
     * Method to create different geopackages of each zoning type and community of an input Geopackage.
     *
     * @param toSortFile Geopackage file to sort (zones or parcels)
     * @param zoningFile the zoning plan in a geopackage format (field names are set in the {@link GeneralFields} class)
     * @param outFolder  the folder which will contain the exported geopackages
     * @throws IOException Reading and writing files
     */
    public static void sortUniqueZone(File toSortFile, File zoningFile, File outFolder) throws IOException {
        DataStore dsToSort = CollecMgmt.getDataStore(toSortFile);
        DataStore dsZoning = CollecMgmt.getDataStore(zoningFile);
        SimpleFeatureCollection zoning = DataUtilities.collection(dsZoning.getFeatureSource(dsZoning.getTypeNames()[0]).getFeatures());
        SimpleFeatureCollection toSort = DataUtilities.collection(dsToSort.getFeatureSource(dsToSort.getTypeNames()[0]).getFeatures());
        String[] vals = {ParcelSchema.getParcelCommunityField(), GeneralFields.getZonePreciseNameField()};
        for (String uniquePreciseName : CollecMgmt.getEachUniqueFieldFromSFC(zoning, vals)) {
            SimpleFeatureCollection eachZoning = CollecTransform.getSFCfromSFCIntersection(toSort, CollecTransform.getSFCPart(zoning, vals, uniquePreciseName.split("-")));
            if (eachZoning == null || eachZoning.isEmpty())
                continue;
            CollecMgmt.exportSFC(eachZoning, new File(outFolder, uniquePreciseName));
        }
        dsToSort.dispose();
        dsZoning.dispose();
    }

//    public static void doConsolidationDivision(SimpleFeatureCollection parcels, File roadFile, File buildingFile, List<LineString> extLines, Geometry exclusionZone, File outFolder, ProfileUrbanFabric profile, int processType) throws IOException {
//        setProcess(processType);
//        (new ConsolidationDivision()).consolidationDivision(parcels, roadFile, buildingFile,extLines,  exclusionZone, outFolder,  profile);
//
//}

    /**
     * Method to create different geopackages of each zoning type and community of an input Geopackage.
     *
     * @param toSortFile Geopackage file to sort (zones or parcels)
     * @param outFolder  the folder which will contain the exported geopackages
     * @throws IOException Reading and writing files
     */
    public static void sortConsolidatedZone(File toSortFile, File outFolder) throws IOException {
        DataStore dsToSort = CollecMgmt.getDataStore(toSortFile);
        SimpleFeatureCollection toSort = DataUtilities.collection(dsToSort.getFeatureSource(dsToSort.getTypeNames()[0]).getFeatures());
        DefaultFeatureCollection parcelToMerge = new DefaultFeatureCollection();
        Arrays.stream(toSort.toArray(new SimpleFeature[0])).forEach(parcel -> {
            if (parcel.getAttribute(MarkParcelAttributeFromPosition.getMarkFieldName()) != null
                    && (String.valueOf(parcel.getAttribute(MarkParcelAttributeFromPosition.getMarkFieldName()))).equals("1")) {
                parcelToMerge.add(parcel);
            }
        });
        DefaultFeatureCollection consolidatedZone = ConsolidationDivision.consolidation(toSort, parcelToMerge, outFolder);
        int i = 0;
        try (SimpleFeatureIterator it = consolidatedZone.features()) {
            while (it.hasNext()) {
                SimpleFeature eachZone = it.next();
                if (eachZone != null)
                    CollecMgmt.exportSFC(Arrays.asList(eachZone), new File(outFolder, "z " + i++));
            }
        }
        dsToSort.dispose();
    }
}
