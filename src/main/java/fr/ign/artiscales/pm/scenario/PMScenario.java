package fr.ign.artiscales.pm.scenario;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import fr.ign.artiscales.pm.decomposition.TopologicalStraightSkeletonParcelDecomposition;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Object representing a Parcel Manager scenario. Will set files and launch a list of predefined {@link fr.ign.artiscales.pm.scenario.PMStep}.
 *
 * @author Maxime Colomb
 * @see <a href="https://github.com/ArtiScales/ParcelManager/blob/master/src/main/resources/doc/scenarioCreation.md">scenarioCreation.md</a>
 */
public class PMScenario {
    /**
     * If true, the parcels simulated for each steps will be the input of the next step. If false, the simulation will operate on the input parcel for each steps
     */
    private static boolean REUSESIMULATEDPARCELS = true;
    /**
     * If true, save a geopackage containing only the simulated parcels in the temporary folder for every workflow simulated.
     */
    private static boolean SAVEINTERMEDIATERESULT = false;
    private File zoningFile, buildingFile, roadFile, polygonIntersection, zone, predicateFile, parcelFile, profileFolder, outFolder;
    private List<PMStep> stepList = new ArrayList<>();
    private boolean fileSet = false;
    boolean keepExistingRoad, adaptAreaOfUrbanFabric, generatePeripheralRoad;

//	public static void main(String[] args) throws Exception {
//		PMScenario pm = new PMScenario(
//				new File("src/main/resources/testData/jsonEx.json"),
//				new File("/tmp/"));
//		pm.executeStep();
//	}

    public PMScenario(File jSON) throws IOException {
        PMStep.setSaveIntermediateResult(SAVEINTERMEDIATERESULT);
        JsonFactory factory = new JsonFactory();
        JsonParser parser = factory.createParser(jSON);
        JsonToken token = parser.nextToken();
        while (!parser.isClosed()) {
            token = parser.nextToken();
//			shortcut if every data is in the same folder
            if (token == JsonToken.FIELD_NAME && "rootfile".equals(parser.getCurrentName()) && !fileSet) {
                fileSet = true;
                token = parser.nextToken();
                if (token == JsonToken.VALUE_STRING) {
                    String rootFolder = parser.getText();
                    zoningFile = new File(rootFolder, "zoning.gpkg");
                    buildingFile = new File(rootFolder, "building.gpkg");
                    roadFile = new File(rootFolder, "road.gpkg");
                    polygonIntersection = new File(rootFolder, "polygonIntersection.gpkg");
                    zone = new File(rootFolder, "zone.gpkg");
                    predicateFile = new File(rootFolder, "predicate.csv");
                    parcelFile = new File(rootFolder, "parcel.gpkg");
                    profileFolder = new File(rootFolder, "profileUrbanFabric");
                }
            }
            //if the line is an array, it describes a PMStep
            if (token == JsonToken.FIELD_NAME && "steps".equals(parser.getCurrentName())) {
                token = parser.nextToken();
                String workflow = "";
                String parcelProcess = "";
                String genericZone = "";
                String preciseZone = "";
                String communityNumber = "";
                String communityType = "";
                String urbanFabric = "";
                while (token != JsonToken.END_ARRAY) {
                    token = parser.nextToken();
                    if (token == JsonToken.FIELD_NAME && parser.getCurrentName().equals("workflow")) {
                        token = parser.nextToken();
                        if (token == JsonToken.VALUE_STRING)
                            workflow = parser.getText();
                    }
                    if (token == JsonToken.FIELD_NAME && parser.getCurrentName().equals("parcelProcess")) {
                        token = parser.nextToken();
                        if (token == JsonToken.VALUE_STRING)
                            parcelProcess = parser.getText();
                    }
                    if (token == JsonToken.FIELD_NAME && parser.getCurrentName().equals("genericZone")) {
                        token = parser.nextToken();
                        if (token == JsonToken.VALUE_STRING)
                            genericZone = parser.getText();
                    }
                    if (token == JsonToken.FIELD_NAME && parser.getCurrentName().equals("preciseZone")) {
                        token = parser.nextToken();
                        if (token == JsonToken.VALUE_STRING)
                            preciseZone = parser.getText();
                    }
                    if (token == JsonToken.FIELD_NAME && parser.getCurrentName().equals("communityNumber")) {
                        token = parser.nextToken();
                        if (token == JsonToken.VALUE_STRING)
                            communityNumber = parser.getText();
                    }
                    if (token == JsonToken.FIELD_NAME && parser.getCurrentName().equals("communityType")) {
                        token = parser.nextToken();
                        if (token == JsonToken.VALUE_STRING)
                            communityType = parser.getText();
                    }
                    if (token == JsonToken.FIELD_NAME && parser.getCurrentName().equals("urbanFabricType")) {
                        token = parser.nextToken();
                        if (token == JsonToken.VALUE_STRING)
                            urbanFabric = parser.getText();
                    }
                    //specific options concerning workflows can be parsed here
                    if (token == JsonToken.FIELD_NAME && "optional".equals(parser.getCurrentName())) {
                        token = parser.nextToken();
                        if (token == JsonToken.VALUE_STRING) {
                            switch (parser.getText()) {
                                case "keepExistingRoad:true":
                                    keepExistingRoad = true;
                                    break;
                                case "keepExistingRoad:false":
                                    keepExistingRoad = false;
                                    break;
                                case "adaptAreaOfUrbanFabric:true":
                                case "adaptAreaOfUrbanFabric":
                                    adaptAreaOfUrbanFabric = true;
                                    break;
                                case "peripheralRoad:true":
                                    generatePeripheralRoad =true;
                                    break;
                                case "peripheralRoad:false":
                                    generatePeripheralRoad = false;
                                    break;
                            }
                        }
                    }
                    if (token == JsonToken.END_OBJECT) {
                        List<PMStep> list = getStepList();
                        PMStep step = new PMStep(workflow, parcelProcess, genericZone, preciseZone, communityNumber, communityType, urbanFabric, generatePeripheralRoad,  keepExistingRoad,  adaptAreaOfUrbanFabric);
                        list.add(step);
                        setStepList(list);
                        workflow = parcelProcess = genericZone = preciseZone = communityNumber = communityType = urbanFabric = "";
                    }
                }
            }

            if (token == JsonToken.FIELD_NAME && "zoningFile".equals(parser.getCurrentName())) {
                token = parser.nextToken();
                if (token == JsonToken.VALUE_STRING) {
                    zoningFile = new File(parser.getText());
                    fileSet = true;
                }
            }
            if (token == JsonToken.FIELD_NAME && "roadFile".equals(parser.getCurrentName())) {
                token = parser.nextToken();
                if (token == JsonToken.VALUE_STRING) {
                    roadFile = new File(parser.getText());
                    fileSet = true;
                }
            }
            if (token == JsonToken.FIELD_NAME && "buildingFile".equals(parser.getCurrentName())) {
                token = parser.nextToken();
                if (token == JsonToken.VALUE_STRING) {
                    buildingFile = new File(parser.getText());
                    fileSet = true;
                }
            }
            if (token == JsonToken.FIELD_NAME && "polygonIntersectionFile".equals(parser.getCurrentName())) {
                token = parser.nextToken();
                if (token == JsonToken.VALUE_STRING) {
                    polygonIntersection = new File(parser.getText());
                    fileSet = true;
                }
            }
            if (token == JsonToken.FIELD_NAME && "zoneFile".equals(parser.getCurrentName())) {
                token = parser.nextToken();
                if (token == JsonToken.VALUE_STRING) {
                    zone = new File(parser.getText());
                    fileSet = true;
                }
            }
            if (token == JsonToken.FIELD_NAME && "predicateFile".equals(parser.getCurrentName())) {
                token = parser.nextToken();
                if (token == JsonToken.VALUE_STRING) {
                    predicateFile = new File(parser.getText());
                    fileSet = true;
                }
            }
            if (token == JsonToken.FIELD_NAME && "parcelFile".equals(parser.getCurrentName())) {
                token = parser.nextToken();
                if (token == JsonToken.VALUE_STRING) {
                    parcelFile = new File(parser.getText());
                    fileSet = true;
                }
            }
            if (token == JsonToken.FIELD_NAME && "profileFolder".equals(parser.getCurrentName())) {
                token = parser.nextToken();
                if (token == JsonToken.VALUE_STRING) {
                    profileFolder = new File(parser.getText());
                    fileSet = true;
                }
            }
            if (token == JsonToken.FIELD_NAME && "outFolder".equals(parser.getCurrentName())) {
                token = parser.nextToken();
                if (token == JsonToken.VALUE_STRING) {
                    outFolder = new File(parser.getText());
                    fileSet = true;
                }
            }
        }
        parser.close();
        PMStep.setFiles(parcelFile, zoningFile, buildingFile, roadFile, predicateFile, polygonIntersection, zone, outFolder,
                profileFolder);
    }

    public static boolean isSaveIntermediateResult() {
        return SAVEINTERMEDIATERESULT;
    }

    public static void setSaveIntermediateResult(boolean saveIntermediateResult) {
        SAVEINTERMEDIATERESULT = saveIntermediateResult;
    }

    public static boolean isReuseSimulatedParcels() {
        return REUSESIMULATEDPARCELS;
    }

    public static void setReuseSimulatedParcels(boolean reuseSimulatedParcel) {
        REUSESIMULATEDPARCELS = reuseSimulatedParcel;
    }

    /**
     * Run every step that are present in the stepList
     *
     * @throws IOException
     */
    public void executeStep() throws IOException {
        for (PMStep pmstep : getStepList()) {
            System.out.println("try " + pmstep);
            if (REUSESIMULATEDPARCELS)
                PMStep.setParcel(pmstep.execute());
            else
                pmstep.execute();
        }
    }

    public List<PMStep> getStepList() {
        return stepList;
    }

    public void setStepList(List<PMStep> stepList) {
        this.stepList = stepList;
    }

    @Override
    public String toString() {
        return "PMScenario [zoningFile=" + zoningFile + ", buildingFile=" + buildingFile + ", roadFile=" + roadFile + ", polygonIntersection="
                + polygonIntersection + ", zone=" + zone + ", predicateFile=" + predicateFile + ", parcelFile=" + parcelFile + ", outFolder=" + outFolder + ", stepList=" + stepList + ", fileSet=" + fileSet + ", profileFolder=" + profileFolder + "]";
    }

}
