package fr.ign.artiscales.pm.usecase;

import fr.ign.artiscales.pm.analysis.SingleParcelStat;
import fr.ign.artiscales.pm.parcelFunction.MarkParcelAttributeFromPosition;
import fr.ign.artiscales.pm.parcelFunction.ParcelCollection;
import fr.ign.artiscales.pm.scenario.PMScenario;
import fr.ign.artiscales.pm.scenario.PMStep;
import fr.ign.artiscales.tools.geoToolsFunctions.vectors.collec.CollecMgmt;
import fr.ign.artiscales.tools.geometryGeneration.CityGeneration;
import org.geotools.data.DataStore;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;


/**
 * This process compares the evolution of a parcel plan at two different versions (file1 and file2) with the simulation on the zone.
 * The simulation must be defined with a scenario (see package {@link fr.ign.artiscales.pm.scenario}).
 * mergeGpkgFiles
 *
 * @author Maxime Colomb
 */
public class CompareSimulatedParcelsWithEvolution extends UseCase {

    public static void main(String[] args) throws IOException {
        Instant start = Instant.now();
        // definition of the geopackages representing two set of parcel
        File rootFolder = new File("src/main/resources/ParcelShapeComparison/");
        File outFolder = new File(rootFolder, "OutputResults");
        outFolder.mkdirs();
        setDEBUG(false);
        compareSimulatedParcelsWithEvolutionWorkflow(rootFolder, outFolder);
        System.out.println(Duration.between(start, Instant.now()));
    }

    public static void compareSimulatedParcelsWithEvolutionWorkflow(File rootFolder, File outFolder) throws IOException {
        File parcelRefFile = new File(rootFolder, "InputData/parcel2003.gpkg");
        File parcelCompFile = new File(rootFolder, "InputData/parcel2018.gpkg");
        File roadFile = new File(rootFolder, "InputData/road2003.gpkg");

        // definition of a parameter file
        File scenarioFile = new File(rootFolder, "scenario.json");
        compareSimulatedParcelsWithEvolutionWorkflow(rootFolder, parcelRefFile, parcelCompFile, roadFile, scenarioFile, outFolder);
    }

    public static void compareSimulatedParcelsWithEvolutionWorkflow(File rootFolder, File parcelRefFile, File parcelCompFile, File roadFile,
                                                                    File scenarioFile, File outFolder) throws IOException {
        // Mark and export the parcels that have changed between the two set of time
        ParcelCollection.sortDifferentParcel(parcelRefFile, parcelCompFile, outFolder);
        // create blocks for parcel densification in case they haven't been generated before
        CityGeneration.createUrbanBlock(parcelRefFile, rootFolder);
        PMScenario.setSaveIntermediateResult(true);
        PMStep.setGENERATEATTRIBUTES(false);
        PMStep.setAllowIsolatedParcel(true);
        PMScenario pm = new PMScenario(scenarioFile);
        pm.executeStep();
        System.out.println("++++++++++ Done with PMscenario ++++++++++");
        System.out.println();

        List<File> lF = new ArrayList<>();

        //get the intermediate files resulting of the PM steps and merge them together
        for (File f : Objects.requireNonNull(outFolder.listFiles()))
            if ((f.getName().contains(("Only")) && f.getName().endsWith(".gpkg")))
                lF.add(f);
        File simulatedFile = new File(outFolder, "simulatedParcel.gpkg");
        CollecMgmt.mergeFiles(lF, simulatedFile);
        // stat for the real parcels
        File realParcelFile = new File(outFolder, "realParcel.gpkg");
        SingleParcelStat.writeStatSingleParcel(realParcelFile, roadFile, new File(outFolder, "RealParcelStats.csv"), true);
        // stat for the simulated parcels
        SingleParcelStat.writeStatSingleParcel(simulatedFile, roadFile, realParcelFile, new File(outFolder, "SimulatedParcelStats.csv"), true);
        // for every workflows
        System.out.println("++++++++++ Analysis by zones ++++++++++");
        System.out.println("steps" + pm.getStepList());

        PMStep.setParcel(parcelRefFile);
        PMStep.setPOLYGONINTERSECTION(null);
        // we proceed with an analysis made for each steps
        PMStep.flushCachePlacesSimulates();
        MarkParcelAttributeFromPosition.setPostMark(true);

        // specially analyze the parcels that have been reshaped by the densification algorihtm
        DataStore dsSimu = CollecMgmt.getDataStore(simulatedFile);
        DataStore dsReal = CollecMgmt.getDataStore(realParcelFile);
        DataStore dsRoad = CollecMgmt.getDataStore(roadFile);

        SingleParcelStat.writeStatSingleParcel(
                MarkParcelAttributeFromPosition.markWorkflowSimulatedParcel(dsSimu.getFeatureSource(dsSimu.getTypeNames()[0]).getFeatures(), "densification")
                , dsRoad.getFeatureSource(dsRoad.getTypeNames()[0]).getFeatures(), dsReal.getFeatureSource(dsReal.getTypeNames()[0]).getFeatures(), new File(outFolder, "DensifiedParcelStats.csv"));

        dsReal.dispose();
        dsSimu.dispose();
        dsRoad.dispose();
    }
}
