package fr.ign.artiscales.pm.usecase;

import fr.ign.artiscales.pm.analysis.RoadRatioParcels;
import fr.ign.artiscales.pm.analysis.SingleParcelStat;
import fr.ign.artiscales.pm.division.DivisionType;
import fr.ign.artiscales.pm.division.StraightSkeletonDivision;
import fr.ign.artiscales.pm.parcelFunction.MarkParcelAttributeFromPosition;
import fr.ign.artiscales.pm.parcelFunction.ParcelGetter;
import fr.ign.artiscales.pm.workflow.ConsolidationDivision;
import fr.ign.artiscales.pm.workflow.Densification;
import fr.ign.artiscales.pm.workflow.Workflow;
import fr.ign.artiscales.pm.workflow.ZoneDivision;
import fr.ign.artiscales.tools.geoToolsFunctions.vectors.collec.CollecMgmt;
import fr.ign.artiscales.tools.geometryGeneration.CityGeneration;
import fr.ign.artiscales.tools.parameter.ProfileUrbanFabric;
import org.geotools.data.DataStore;
import org.geotools.data.DataUtilities;
import org.geotools.data.simple.SimpleFeatureCollection;

import java.io.File;

/**
 * Tests principal workflows and analysis of Parcel Manager. Scenario-less to clearly show every step of a Parcel Manager experiment.
 *
 * @author Maxime Colomb
 */
public class TestScenario extends UseCase {

    // /////////////////////////
    // //////// try the parcelGenMotif method
    /////////////////////////
    public static void main(String[] args) throws Exception {
        // org.geotools.util.logging.Logging.getLogger("org.hsqldb.persist.Logger").setLevel(Level.OFF);
        // org.geotools.util.logging.Logging.getLogger("org.geotools.jdbc.JDBCDataStore").setLevel(Level.OFF);
        long start = System.currentTimeMillis();
        File rootFolder = new File("src/main/resources/TestScenario/");
        File outFolder = new File(rootFolder, "OutputResults");
        setDEBUG(false);
        setSAVEINTERMEDIATERESULT(false);
        doTestScenario(outFolder, new File(rootFolder, "InputData"));
        System.out.println("time: " + ((System.currentTimeMillis() - start) / 1000) + " sec");
    }

    public static void doTestScenario(File outRootFolder, File rootFolder) throws Exception {
        File roadFile = new File(rootFolder, "road.gpkg");
        File zoningFile = new File(rootFolder, "zoning.gpkg");
        File buildingFile = new File(rootFolder, "building.gpkg");
        File parcelFile = new File(rootFolder, "parcel.gpkg");
        File profileFolder = new File(rootFolder.getParent(), "profileUrbanFabric");
        boolean allowIsolatedParcel = false;
        DataStore DSParcel = CollecMgmt.getDataStore(parcelFile);
        SimpleFeatureCollection parcel = DataUtilities.collection(ParcelGetter.getParcelByZip(DSParcel.getFeatureSource(DSParcel.getTypeNames()[0]).getFeatures(), "25267,25395"));
        DSParcel.dispose();
        ProfileUrbanFabric profileMediumHouse = ProfileUrbanFabric.convertJSONtoProfile(new File(profileFolder, "mediumHouse.json"));
        ProfileUrbanFabric profileSmallHouse = ProfileUrbanFabric.convertJSONtoProfile(new File(profileFolder, "smallHouse.json"));
        ProfileUrbanFabric profileMediumCollective = ProfileUrbanFabric.convertJSONtoProfile(new File(profileFolder, "mediumCollective.json"));
        StraightSkeletonDivision.setGeneratePeripheralRoad(true);
//        for (int i = 4; i <= 4; i++) {
        for (int i = 0; i <= 4; i++) {
//          multiple process calculation
            String ext = "offset";
            Workflow.PROCESS = DivisionType.SSoffset;
            if (i == 1) {
                Workflow.PROCESS = DivisionType.SS;
                ext = "StraightSkeletonPeripheralRoad";
            } else if (i == 2) {
                Workflow.PROCESS = DivisionType.SS;
                ext = "StraightSkeleton";
                StraightSkeletonDivision.setGeneratePeripheralRoad(false);
            } else if (i == 3) {
                Workflow.PROCESS = DivisionType.OBB;
                ext = "OBB";
            } else if (i == 4) {
                Workflow.PROCESS = DivisionType.OBBThenSS;
                ext = "OBBThenSS";
            }
            System.out.println("PROCESS: " + ext);
            File outFolder = new File(outRootFolder, ext);
            File statFolder = new File(outFolder, "stat");
            statFolder.mkdirs();

            /////////////////////////
            // zoneTotRecomp
            /////////////////////////
            System.out.println("*-*-*-*-*-*-*-*-*-*");
            System.out.println("zoneTotRecomp");
            System.out.println("*-*-*-*-*-*-*-*-*-*");
            DataStore gpkgDSZoning = CollecMgmt.getDataStore(zoningFile);
            SimpleFeatureCollection zoning = DataUtilities.collection((gpkgDSZoning.getFeatureSource(gpkgDSZoning.getTypeNames()[0]).getFeatures()));
            gpkgDSZoning.dispose();
            SimpleFeatureCollection zone = ZoneDivision.createZoneToCut("AU", "AU1", zoning, parcel);
            // If no zones, we won't bother
            if (zone.isEmpty()) {
                System.out.println("parcelGenZone : no zones to be cut");
                System.exit(1);
            }
            DataStore rDS = CollecMgmt.getDataStore(roadFile);
            SimpleFeatureCollection parcelCuted = (new ZoneDivision()).zoneDivision(zone, parcel, rDS.getFeatureSource(rDS.getTypeNames()[0]).getFeatures(), outFolder, profileMediumCollective, true);
            rDS.dispose();
            CollecMgmt.exportSFC(parcelCuted, new File(outFolder, "parcelZoneDivision.gpkg"));
            CollecMgmt.exportSFC(zone, new File(outFolder, "zone.gpkg"));
            RoadRatioParcels.roadRatioZone(zone, parcelCuted, profileMediumCollective.getNameBuildingType().replace(" ", "_"), statFolder, roadFile);
//            List<SimpleFeature> parcelSimulatedZone = Arrays.stream(finaux.toArray(new SimpleFeature[0])).filter(sf -> (new ZoneDivision()).isNewSection(sf)).collect(Collectors.toList());
//            MakeStatisticGraphs.makeAreaGraph(parcelSimulatedZone, statFolder, "Zone division - medium-sized blocks of flats");
//            MakeStatisticGraphs.makeWidthContactRoadGraph(parcelSimulatedZone, CityGeneration.createUrbanBlock(finaux, true), roadFile, new File(statFolder, "contact"), "Zone division - medium-sized blocks of flats");

            /////////////////////////
            //////// try the consolidRecomp method
            /////////////////////////
            System.out.println("*-*-*-*-*-*-*-*-*-*");
            System.out.println("consolidRecomp");
            System.out.println("*-*-*-*-*-*-*-*-*-*");
            SimpleFeatureCollection markedZone = MarkParcelAttributeFromPosition.markParcelIntersectPreciseZoningType(parcelCuted, "AU", "AU2", zoningFile);
//            SimpleFeatureCollection markedZone = MarkParcelAttributeFromPosition.markParcelIntersectPreciseZoningType(parcelCuted, "AU", "AU2", zoningFile);
            SimpleFeatureCollection cutedNormalZone = (new ConsolidationDivision()).consolidationDivision(markedZone, roadFile, outFolder, profileMediumHouse);
            CollecMgmt.exportSFC(cutedNormalZone, new File(outFolder, "ParcelConsolidation.gpkg"));
            RoadRatioParcels.roadRatioParcels(markedZone, cutedNormalZone, profileMediumHouse.getNameBuildingType().replace(" ", "_"), statFolder, roadFile);
//            List<SimpleFeature> parcelSimulatedConsolid = Arrays.stream(finalNormalZone.toArray(new SimpleFeature[0]))
//                    .filter(sf -> (new ConsolidationDivision()).isNewSection(sf)).collect(Collectors.toList());
//            MakeStatisticGraphs.makeAreaGraph(parcelSimulatedConsolid, statFolder, "Zone consolidation - medium-sized houses");
//            MakeStatisticGraphs.makeWidthContactRoadGraph(parcelSimulatedConsolid, CityGeneration.createUrbanBlock(finalNormalZone, true), roadFile, new File(statFolder, "contact"), "Zone consolidation - medium-sized houses");

            /////////////////////////
            //////// try the parcelDensification method
            /////////////////////////
            System.out.println("*-*-*-*-*-*-*-*-*-*");
            System.out.println("parcelDensification");
            System.out.println("*-*-*-*-*-*-*-*-*-*");
            SimpleFeatureCollection parcelDensified = (new Densification()).densification(
                    MarkParcelAttributeFromPosition.markParcelIntersectPreciseZoningType(cutedNormalZone, "U", "UB", zoningFile),
                    CityGeneration.createUrbanBlock(cutedNormalZone, true), outFolder, buildingFile, roadFile, profileSmallHouse.getHarmonyCoeff(),
                    profileSmallHouse.getIrregularityCoeff(), profileSmallHouse.getMaximalArea(), profileSmallHouse.getMinimalArea(),
                    profileSmallHouse.getLenDriveway(), profileSmallHouse.getLenDriveway(), allowIsolatedParcel);
            CollecMgmt.exportSFC(parcelDensified, new File(outFolder, "parcelDensification.gpkg"));
//            List<SimpleFeature> densifiedParcels = Arrays.stream(parcelDensified.toArray(new SimpleFeature[0])).filter(sf -> (new Densification()).isNewSection(sf)).collect(Collectors.toList());
//            MakeStatisticGraphs.makeAreaGraph(densifiedParcels, statFolder, "Densification - small-sized houses simulation");
//            MakeStatisticGraphs.makeWidthContactRoadGraph(densifiedParcels, CityGeneration.createUrbanBlock(finalNormalZone, true), roadFile, new File(statFolder, "contact"), "Densification - small-sized houses");

            SingleParcelStat.writeStatSingleParcel(MarkParcelAttributeFromPosition.markSimulatedParcel(parcelDensified), roadFile, new File(statFolder, "statParcel.csv"));
            CollecMgmt.exportSFC(MarkParcelAttributeFromPosition.markParcelsNotConnectedToRoad(parcelDensified, CityGeneration.createUrbanBlock(parcelDensified), roadFile, null), new File(outFolder,"/parcelNotConnectedToRoad"));
        }
    }
}
