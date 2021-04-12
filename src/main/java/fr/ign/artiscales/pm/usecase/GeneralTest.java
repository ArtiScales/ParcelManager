package fr.ign.artiscales.pm.usecase;

import fr.ign.artiscales.pm.analysis.MakeStatisticGraphs;
import fr.ign.artiscales.pm.analysis.RoadRatioParcels;
import fr.ign.artiscales.pm.analysis.SingleParcelStat;
import fr.ign.artiscales.pm.fields.french.FrenchParcelFields;
import fr.ign.artiscales.pm.parcelFunction.MarkParcelAttributeFromPosition;
import fr.ign.artiscales.pm.parcelFunction.ParcelGetter;
import fr.ign.artiscales.pm.workflow.ConsolidationDivision;
import fr.ign.artiscales.pm.workflow.Densification;
import fr.ign.artiscales.pm.workflow.Workflow;
import fr.ign.artiscales.pm.workflow.ZoneDivision;
import fr.ign.artiscales.tools.geoToolsFunctions.vectors.Geopackages;
import fr.ign.artiscales.tools.geoToolsFunctions.vectors.collec.CollecMgmt;
import fr.ign.artiscales.tools.geometryGeneration.CityGeneration;
import fr.ign.artiscales.tools.parameter.ProfileUrbanFabric;
import org.geotools.data.DataStore;
import org.geotools.data.DataUtilities;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.opengis.feature.simple.SimpleFeature;

import java.io.File;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * Class that tests principal workflows and analysis of Parcel Manager.
 *
 * @author Maxime Colomb
 */
public class GeneralTest extends UseCase {

    // /////////////////////////
    // //////// try the parcelGenMotif method
    /////////////////////////
    public static void main(String[] args) throws Exception {
        // org.geotools.util.logging.Logging.getLogger("org.hsqldb.persist.Logger").setLevel(Level.OFF);
        org.geotools.util.logging.Logging.getLogger("org.geotools.jdbc.JDBCDataStore").setLevel(Level.OFF);
        long start = System.currentTimeMillis();
        File rootFolder = new File("src/main/resources/GeneralTest/");
        File outFolder = new File(rootFolder, "out");
        setDEBUG(false);
        Workflow.setSAVEINTERMEDIATERESULT(false);
        GeneralTest(outFolder, rootFolder);
        System.out.println(System.currentTimeMillis() - start);
    }

    public static void GeneralTest(File outRootFolder, File rootFolder) throws Exception {
        File roadFile = new File(rootFolder, "road.gpkg");
        File zoningFile = new File(rootFolder, "zoning.gpkg");
        File buildingFile = new File(rootFolder, "building.gpkg");
        File parcelFile = new File(rootFolder, "parcel.gpkg");
        File profileFolder = new File(rootFolder, "profileUrbanFabric");
        boolean allowIsolatedParcel = false;
        DataStore gpkgDSParcel = Geopackages.getDataStore(parcelFile);
        SimpleFeatureCollection parcel = DataUtilities
                .collection(ParcelGetter.getFrenchParcelByZip(gpkgDSParcel.getFeatureSource(gpkgDSParcel.getTypeNames()[0]).getFeatures(), "25267"));
        gpkgDSParcel.dispose();
        ProfileUrbanFabric profileDetached = ProfileUrbanFabric.convertJSONtoProfile(new File(profileFolder, "detachedHouse.json"));
        ProfileUrbanFabric profileSmallHouse = ProfileUrbanFabric.convertJSONtoProfile(new File(profileFolder, "smallHouse.json"));
        ProfileUrbanFabric profileLargeCollective = ProfileUrbanFabric.convertJSONtoProfile(new File(profileFolder, "largeCollective.json"));
        Workflow.PROCESS = "SS";
        for (int i = 0; i <= 2; i++) {
//        for (int i = 2; i <= 2; i++) {
            // multiple process calculation
            String ext = "offset";
            if (i == 1) {
                Workflow.PROCESS = "OBB";
                ext = "OBB";
            } else if (i == 2) {
                Workflow.PROCESS = "SS";
                profileDetached.setMaxDepth(0);
                profileSmallHouse.setMaxDepth(0);
                profileLargeCollective.setMaxDepth(0);
                ext = "SS";
            }
            System.out.println("PROCESS: "+ext);
            File outFolder = new File(outRootFolder, "/out/" + ext);
            outFolder.mkdirs();
            File statFolder = new File(outFolder, "stat");
            statFolder.mkdirs();

            /////////////////////////
            // zoneTotRecomp
            /////////////////////////
            System.out.println("*-*-*-*-*-*-*-*-*-*");
            System.out.println("zoneTotRecomp");
            System.out.println("*-*-*-*-*-*-*-*-*-*");
            DataStore gpkgDSZoning = Geopackages.getDataStore(zoningFile);
            SimpleFeatureCollection zoning = DataUtilities.collection((gpkgDSZoning.getFeatureSource(gpkgDSZoning.getTypeNames()[0]).getFeatures()));
            gpkgDSZoning.dispose();
            SimpleFeatureCollection zone = ZoneDivision.createZoneToCut("AU", "AU1", zoning, zoningFile, parcel);
            // If no zones, we won't bother
            if (zone.isEmpty()) {
                System.out.println("parcelGenZone : no zones to be cut");
                System.exit(1);
            }
            DataStore rDS = Geopackages.getDataStore(roadFile);
            SimpleFeatureCollection parcelCuted = (new ZoneDivision()).zoneDivision(zone, parcel, rDS.getFeatureSource(rDS.getTypeNames()[0]).getFeatures(), outFolder, profileLargeCollective);
            rDS.dispose();
            SimpleFeatureCollection finaux = FrenchParcelFields.setOriginalFrenchParcelAttributes(parcelCuted, parcel);
            CollecMgmt.exportSFC(finaux, new File(outFolder, "parcelTotZone.gpkg"));
            CollecMgmt.exportSFC(zone, new File(outFolder, "zone.gpkg"));
            RoadRatioParcels.roadRatioZone(zone, finaux, profileLargeCollective.getNameBuildingType(), statFolder, roadFile);
            MakeStatisticGraphs.makeAreaGraph(Arrays.stream(finaux.toArray(new SimpleFeature[0])).filter(sf -> (new ZoneDivision()).isNewSection(sf))
                    .collect(Collectors.toList()), statFolder, "Zone division - large collective building simulation");

            /////////////////////////
            //////// try the consolidRecomp method
            /////////////////////////
            System.out.println("*-*-*-*-*-*-*-*-*-*");
            System.out.println("consolidRecomp");
            System.out.println("*-*-*-*-*-*-*-*-*-*");
            SimpleFeatureCollection markedZone = MarkParcelAttributeFromPosition.markParcelIntersectPreciseZoningType(finaux, "AU", "AUb", zoningFile);
            SimpleFeatureCollection cutedNormalZone = (new ConsolidationDivision()).consolidationDivision(markedZone, roadFile, outFolder, profileDetached);
            SimpleFeatureCollection finalNormalZone = FrenchParcelFields.setOriginalFrenchParcelAttributes(cutedNormalZone, parcel);
            CollecMgmt.exportSFC(finalNormalZone, new File(outFolder, "ParcelConsolidRecomp.gpkg"));
            RoadRatioParcels.roadRatioParcels(markedZone, finalNormalZone, profileDetached.getNameBuildingType(), statFolder, roadFile);
            MakeStatisticGraphs.makeAreaGraph(Arrays.stream(finalNormalZone.toArray(new SimpleFeature[0]))
                            .filter(sf -> (new ConsolidationDivision()).isNewSection(sf)).collect(Collectors.toList()), statFolder,
                    "Consolidation-division - detached houses simulation");

            /////////////////////////
            //////// try the parcelDensification method
            /////////////////////////
            System.out.println("*-*-*-*-*-*-*-*-*-*");
            System.out.println("parcelDensification");
            System.out.println("*-*-*-*-*-*-*-*-*-*");
            SimpleFeatureCollection parcelDensified = (new Densification()).densification(
                    MarkParcelAttributeFromPosition.markParcelIntersectPreciseZoningType(finalNormalZone, "U", "UB", zoningFile),
                    CityGeneration.createUrbanBlock(finalNormalZone), outFolder, buildingFile, roadFile, profileSmallHouse.getHarmonyCoeff(),
                    profileSmallHouse.getNoise(), profileSmallHouse.getMaximalArea(), profileSmallHouse.getMinimalArea(),
                    profileSmallHouse.getMinimalWidthContactRoad(), profileSmallHouse.getLenDriveway(), allowIsolatedParcel);
            SimpleFeatureCollection finaux3 = FrenchParcelFields.setOriginalFrenchParcelAttributes(parcelDensified, parcel);
            CollecMgmt.exportSFC(finaux3, new File(outFolder, "parcelDensification.gpkg"));
            MakeStatisticGraphs.makeAreaGraph(Arrays.stream(parcelDensified.toArray(new SimpleFeature[0]))
                            .filter(sf -> (new Densification()).isNewSection(sf)).collect(Collectors.toList()), statFolder,
                    "Densification - small houses simulation");
            SingleParcelStat.writeStatSingleParcel(MarkParcelAttributeFromPosition.markSimulatedParcel(finaux3), roadFile,
                    new File(statFolder, "statParcel.csv"));
        }
    }
}
