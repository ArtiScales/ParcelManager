package fr.ign.artiscales.pm.usecase;

import fr.ign.artiscales.pm.fields.GeneralFields;
import fr.ign.artiscales.pm.fields.french.FrenchZoningSchemas;
import fr.ign.artiscales.pm.parcelFunction.MarkParcelAttributeFromPosition;
import fr.ign.artiscales.pm.parcelFunction.ParcelAttribute;
import fr.ign.artiscales.pm.scenario.PMScenario;
import fr.ign.artiscales.pm.workflow.Densification;
import fr.ign.artiscales.tools.geoToolsFunctions.vectors.Geom;
import fr.ign.artiscales.tools.geoToolsFunctions.vectors.collec.CollecMgmt;
import fr.ign.artiscales.tools.geoToolsFunctions.vectors.collec.CollecTransform;
import fr.ign.artiscales.tools.geometryGeneration.CityGeneration;
import fr.ign.artiscales.tools.io.csv.CsvExport;
import fr.ign.artiscales.tools.io.csv.CsvOp;
import fr.ign.artiscales.tools.parameter.ProfileUrbanFabric;
import org.geotools.data.DataStore;
import org.geotools.data.DataUtilities;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.locationtech.jts.geom.Geometry;
import org.opengis.feature.simple.SimpleFeature;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

/**
 * This class provides a workflow in order to help densification studies. They can be asked in French Schémas de Cohérence Territoriale (SCoT). It isolate empty parcels within
 * urban zones (called <i>vacant lot</i> and simulates their densification. If they are too big, it simulates the creation of a whole neighborhood. The output Geopackages is called
 * <i>parcelDentCreusesDensified</i>
 * <p>
 * It also simulates the parcels that can be created with the flag parcels on already built parcels. The geopackage containing those parcels is called
 * <i>parcelPossiblyDensified</i>
 */
public class DensificationStudy extends UseCase {

    public static void main(String[] args) throws IOException {
        File rootFile = new File("src/main/resources/DensificationStudy/");
        File outFolder = new File(rootFile, "out");
        runDensificationStudy(rootFile, outFolder);
    }

    public static void runDensificationStudy(File rootFile, File outFolder) throws IOException {
        PMScenario pmScen = new PMScenario(new File(rootFile, "scenario.json"));
        pmScen.executeStep();
        for (int i = 1; i <= 4; i++)
            CsvOp.calculateColumnsBasicStat(new File(outFolder, "densificationStudyResult.csv"), i, true);
        // make a (nice) map out of it
//        DataStore ds = Geopackages.getDataStore(new File(rootFile, "parcel.gpkg"));
//        JoinCSVToGeoFile.joinCSVToGeoFile(
//                MergeByAttribute.mergeByAttribute(Objects.requireNonNull(GeneralFields.addCommunityCode(ds.getFeatureSource(ds.getTypeNames()[0]).getFeatures())),
//                        GeneralFields.getZoneCommunityCode()),
//                GeneralFields.getZoneCommunityCode(), new File(outFolder, "densificationStudyResult.csv"), GeneralFields.getZoneCommunityCode(),
//                new File(outFolder, "CityStat"), null, Arrays.asList(StatisticOperation.SUM));
//        ds.dispose();
    }

    /**
     * Densification study. Can be used as a workflows in scenarios.
     *
     * @param parcels                      input parcels
     * @param buildingFile                 building geofile of the studied zone
     * @param roadFile                     road geofile of the studied zone
     * @param zoningFile                   zoning geofile of the studied zone
     * @param outFolder                    folder where output are exported
     * @param isParcelWithoutStreetAllowed Is it possible to create flag or porch parcel without contact with the road?
     * @param profile                      profile of the wanted urban fabric
     * @throws IOException many reasons
     */
    public static void runDensificationStudy(SimpleFeatureCollection parcels, File buildingFile, File roadFile, File zoningFile,
                                             File outFolder, boolean isParcelWithoutStreetAllowed, ProfileUrbanFabric profile) throws IOException {
        outFolder.mkdir();
        parcels = DataUtilities.collection(parcels);
        SimpleFeatureCollection block = CityGeneration.createUrbanBlock(parcels, true);
        Geometry buffer = CityGeneration.createBufferBorder(parcels);
        Geometry maskZone = Geom.unionSFC(block);
        // building crop and export
        DataStore buildingDS = CollecMgmt.getDataStore(buildingFile);
        SimpleFeatureCollection building = DataUtilities.collection(CollecTransform.selectIntersection(buildingDS.getFeatureSource(buildingDS.getTypeNames()[0]).getFeatures(), maskZone));
        File buildingFileExported = CollecMgmt.exportSFC(building, new File(outFolder, "building"));
        buildingDS.dispose();
        // road crop and export
        DataStore roadDS = CollecMgmt.getDataStore(roadFile);
        SimpleFeatureCollection road = DataUtilities.collection(CollecTransform.selectIntersection(roadDS.getFeatureSource(roadDS.getTypeNames()[0]).getFeatures(), maskZone));
        File roadFileExported = CollecMgmt.exportSFC(road, new File(outFolder, "road"));
        roadDS.dispose();
        // zoning crop and export
        DataStore zoningDS = CollecMgmt.getDataStore(zoningFile);
        SimpleFeatureCollection zoning = DataUtilities.collection(CollecTransform.selectIntersection(zoningDS.getFeatureSource(zoningDS.getTypeNames()[0]).getFeatures(), maskZone));
        zoningDS.dispose();

        String splitField = MarkParcelAttributeFromPosition.getMarkFieldName();

        // get total unbuilt parcels from the urbanized zones
        SimpleFeatureCollection parcelsVacantLot = MarkParcelAttributeFromPosition.
                markParcelIntersectFrenchConstructibleZoningType(MarkParcelAttributeFromPosition.markUnBuiltParcel(parcels, building), zoning);
        if (isDEBUG()) {
            CollecMgmt.exportSFC(parcelsVacantLot, new File(outFolder, "/parcelsVacantLot"));
            System.out.println("export total unbuilt parcels from the urbanized zones");
        }

        SimpleFeatureCollection parcelsVacantLotCreated = (new Densification()).densificationOrNeighborhood(parcelsVacantLot, block, outFolder, buildingFileExported,
                roadFileExported, profile, isParcelWithoutStreetAllowed, buffer, 5);
        if (isDEBUG()) {
            CollecMgmt.exportSFC(parcelsVacantLotCreated, new File(outFolder, "/parcelsVacantLotCreated"));
            System.out.println("parcel vacant lot created");
        }
        // simulate the densification of built parcels in the given zone
        SimpleFeatureCollection parcelsDensifZone = MarkParcelAttributeFromPosition
                .markParcelIntersectFrenchConstructibleZoningType(MarkParcelAttributeFromPosition.markBuiltParcel(parcels, building), zoning);
        if (isDEBUG()) {
            CollecMgmt.exportSFC(parcelsDensifZone, new File(outFolder, "/parcelsDensifZone"));
            System.out.println("parcel densif zone created");
        }
        SimpleFeatureCollection parcelsDensifCreated = (new Densification()).densification(parcelsDensifZone, block, outFolder, buildingFileExported,
                roadFileExported, profile, isParcelWithoutStreetAllowed, buffer);
        if (isDEBUG())
            CollecMgmt.exportSFC(parcelsDensifCreated, new File(outFolder, "/parcelsDensifCreated"));

        // change split name to show if they can be built and start postprocessing
        String firstMarkFieldName = MarkParcelAttributeFromPosition.getMarkFieldName();
        MarkParcelAttributeFromPosition.setMarkFieldName("BUILDABLE");
        MarkParcelAttributeFromPosition.setPostMark(true);
        // Mark the simulated parcels that doesn't contains buildings (and therefore can be build)
        parcelsVacantLotCreated = MarkParcelAttributeFromPosition
                .markUnBuiltParcel(MarkParcelAttributeFromPosition.markSimulatedParcel(parcelsVacantLotCreated), building);

        parcelsDensifCreated = MarkParcelAttributeFromPosition
                .markUnBuiltParcel(MarkParcelAttributeFromPosition.markSimulatedParcel(parcelsDensifCreated), building);

        // If the parcels have to be connected to the road, we mark them
        if (!isParcelWithoutStreetAllowed) {
            parcelsVacantLotCreated = MarkParcelAttributeFromPosition.markParcelsConnectedToRoad(parcelsVacantLotCreated, CityGeneration.createUrbanBlock(parcelsVacantLotCreated), road, buffer);
            parcelsDensifCreated = MarkParcelAttributeFromPosition.markParcelsConnectedToRoad(parcelsDensifCreated, CityGeneration.createUrbanBlock(parcelsDensifCreated, true), road, buffer);
        }
        // exporting output geopackages and countings
        List<SimpleFeature> vacantParcelU = Arrays.stream(parcelsDensifCreated.toArray(new SimpleFeature[0]))
                .filter(feat -> feat.getAttribute(MarkParcelAttributeFromPosition.getMarkFieldName()).equals(1)).collect(Collectors.toList());
        CollecMgmt.exportSFC(parcelsVacantLot, new File(outFolder, "parcelVacantLot"), false);
        CollecMgmt.exportSFC(parcelsVacantLotCreated, new File(outFolder, "parcelVacantLotDensified"), false);
        CollecMgmt.exportSFC(vacantParcelU, new File(outFolder, "parcelPossiblyDensified"), false);

        long nbVacantLot = Arrays.stream(parcelsVacantLot.toArray(new SimpleFeature[0])).filter(feat -> feat.getAttribute(splitField).equals(1))
                .count();
        long nbVacantLotParcels = Arrays.stream(parcelsVacantLotCreated.toArray(new SimpleFeature[0]))
                .filter(feat -> feat.getAttribute(MarkParcelAttributeFromPosition.getMarkFieldName()).equals(1)).count();
        System.out.println("number of vacant lots " + nbVacantLot);
        System.out.println("possible to have " + nbVacantLotParcels + " buildable parcels out of it");
        System.out.println();
        System.out.println("possible to have " + vacantParcelU.size() + " parcels with densification process");

        long nbParcelsInUrbanizableZones = Arrays.stream(parcels.toArray(new SimpleFeature[0]))
                .filter(feat -> FrenchZoningSchemas
                        .isUrbanZoneUsuallyAdmitResidentialConstruction(CollecTransform.getIntersectingSimpleFeatureFromSFC((Geometry) feat.getDefaultGeometry(), zoning)))
                .count();

        // saving the stats in a .csv file
        String[] firstline = {GeneralFields.getZoneCommunityCode(), "parcels in urbanizable zones", "number of vacant lots", "parcels simulated in vacant lots",
                "parcels simulated by densification"};
        Object[] line = {nbParcelsInUrbanizableZones, nbVacantLot, nbVacantLotParcels, vacantParcelU.size()};
        HashMap<String, Object[]> l = new HashMap<>();
        l.put(ParcelAttribute.getCityCodeOfParcels(parcelsDensifCreated), line);
        CsvExport.generateCsvFile(l, outFolder, "densificationStudyResult", firstline, true);
        CsvExport.needFLine = false;
        // redo normal mark name
        MarkParcelAttributeFromPosition.setMarkFieldName(firstMarkFieldName);
    }
}
