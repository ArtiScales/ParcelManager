package fr.ign.artiscales.pm.usecase;

import fr.ign.artiscales.pm.analysis.SingleParcelStat;
import fr.ign.artiscales.pm.parcelFunction.MarkParcelAttributeFromPosition;
import fr.ign.artiscales.pm.parcelFunction.ParcelCollection;
import fr.ign.artiscales.pm.scenario.PMScenario;
import fr.ign.artiscales.pm.scenario.PMStep;
import fr.ign.artiscales.tools.geoToolsFunctions.vectors.Geom;
import fr.ign.artiscales.tools.geoToolsFunctions.vectors.collec.CollecMgmt;
import fr.ign.artiscales.tools.geoToolsFunctions.vectors.collec.CollecTransform;
import fr.ign.artiscales.tools.geometryGeneration.CityGeneration;
import org.geotools.data.DataStore;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.factory.CommonFactoryFinder;
import org.geotools.util.factory.GeoTools;
import org.locationtech.jts.geom.Geometry;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.filter.FilterFactory2;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;


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
        File rootFolder = new File("src/main/resources/ParcelComparison/");
        File outFolder = new File(rootFolder, "out");
        outFolder.mkdirs();
        setDEBUG(false);
        compareSimulatedParcelsWithEvolutionWorkflow(rootFolder, outFolder);
        System.out.println(Duration.between(start, Instant.now()));
    }

    public static void compareSimulatedParcelsWithEvolutionWorkflow(File rootFolder, File outFolder) throws IOException {
        File parcelRefFile = new File(rootFolder, "parcel2003.gpkg");
        File parcelCompFile = new File(rootFolder, "parcel2018.gpkg");
        File roadFile = new File(rootFolder, "road.gpkg");

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
        PMStep.setDEBUG(true);
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
        SingleParcelStat.writeStatSingleParcel(simulatedFile,  roadFile, realParcelFile,new File(outFolder, "SimulatedParcelStats.csv"), true);
        // for every workflows
        System.out.println("++++++++++ Analysis by zones ++++++++++");
        System.out.println("steps" + pm.getStepList());

        PMStep.setParcel(parcelRefFile);
        PMStep.setPOLYGONINTERSECTION(null);
        // we proceed with an analysis made for each steps
        PMStep.flushCachePlacesSimulates();
        MarkParcelAttributeFromPosition.setPostMark(true);
        FilterFactory2 ff = CommonFactoryFinder.getFilterFactory2(GeoTools.getDefaultHints());
        DataStore dsRoad = CollecMgmt.getDataStore(roadFile);
        for (PMStep step : pm.getStepList()) {
            System.out.println("analysis for step " + step);
            File zoneOutFolder = new File(outFolder, step.getZoneStudied());
            zoneOutFolder.mkdirs();
            List<Geometry> geoms = step.getBoundsOfZone();
            Geometry geomUnion = Geom.unionPrecisionReduce(geoms, 100).buffer(-1);
            // simulated parcels crop
            DataStore sdsSimulatedParcel = CollecMgmt.getDataStore(simulatedFile);
            SimpleFeatureCollection sfcSimulatedParcel = sdsSimulatedParcel.getFeatureSource(sdsSimulatedParcel.getTypeNames()[0]).getFeatures()
                    .subCollection(ff.intersects(ff.property(sdsSimulatedParcel.getFeatureSource(sdsSimulatedParcel.getTypeNames()[0]).getFeatures()
                            .getSchema().getGeometryDescriptor().getLocalName()), ff.literal(geomUnion)));
            CollecMgmt.exportSFC(sfcSimulatedParcel, new File(zoneOutFolder, "SimulatedParcel.gpkg"));

            // real parcel crop
            DataStore dsRealParcel = CollecMgmt.getDataStore(realParcelFile);
            SimpleFeatureCollection sfcRealParcel = CollecTransform.selectIntersection(dsRealParcel.getFeatureSource(dsRealParcel.getTypeNames()[0]).getFeatures(), geomUnion);
            CollecMgmt.exportSFC(sfcRealParcel, new File(zoneOutFolder, "RealParcel.gpkg"));

            DataStore sdsGoalParcel = CollecMgmt.getDataStore(step.getLastOutput() != null ? step.getLastOutput() : step.makeFileName());
            SingleParcelStat.writeStatSingleParcel(
                    MarkParcelAttributeFromPosition.markSimulatedParcel(MarkParcelAttributeFromPosition.markParcelIntersectPolygonIntersection(
                            sdsGoalParcel.getFeatureSource(sdsGoalParcel.getTypeNames()[0]).getFeatures(), geoms.stream().map(g -> g.buffer(-2)).collect(Collectors.toList()))),
                    CollecTransform.selectIntersection(dsRoad.getFeatureSource(dsRoad.getTypeNames()[0]).getFeatures(), geomUnion),sfcRealParcel,
                    new File(zoneOutFolder, "SimulatedParcelStats.csv"));
            sdsSimulatedParcel.dispose();
            sdsGoalParcel.dispose();

            //real parcel crop
            System.out.println("real parcels");
            DataStore dsRealAndAllParcels = CollecMgmt.getDataStore(parcelCompFile);
            SingleParcelStat.writeStatSingleParcel(
                    MarkParcelAttributeFromPosition.markParcelIntersectPolygonIntersection(dsRealAndAllParcels.getFeatureSource(dsRealAndAllParcels.getTypeNames()[0]).getFeatures(),
                            Arrays.stream(sfcRealParcel.toArray(new SimpleFeature[0])).map(g -> ((Geometry) g.getDefaultGeometry()).buffer(-1)).collect(Collectors.toList())),
                    CollecTransform.selectIntersection(dsRoad.getFeatureSource(dsRoad.getTypeNames()[0]).getFeatures(), geomUnion), new File(zoneOutFolder, "RealParcelStats.csv"));
            dsRealParcel.dispose();
            dsRealAndAllParcels.dispose();
        }
        dsRoad.dispose();
    }
}
