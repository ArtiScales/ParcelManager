package fr.ign.artiscales.pm.analysis;

import fr.ign.artiscales.pm.fields.GeneralFields;
import fr.ign.artiscales.pm.parcelFunction.MarkParcelAttributeFromPosition;
import fr.ign.artiscales.pm.parcelFunction.ParcelAttribute;
import fr.ign.artiscales.pm.parcelFunction.ParcelGetter;
import fr.ign.artiscales.tools.geoToolsFunctions.vectors.Geom;
import fr.ign.artiscales.tools.geoToolsFunctions.vectors.collec.CollecMgmt;
import fr.ign.artiscales.tools.geoToolsFunctions.vectors.collec.CollecTransform;
import fr.ign.artiscales.tools.geometryGeneration.CityGeneration;
import org.apache.commons.math3.exception.NullArgumentException;
import org.geotools.data.DataStore;
import org.geotools.data.DataUtilities;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.locationtech.jts.geom.Geometry;
import org.opengis.feature.simple.SimpleFeature;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.stream.Collectors;

/**
 * This class generates values for the description of an urban fabric. It is aimed to help the setting of {@link fr.ign.artiscales.tools.parameter.ProfileUrbanFabric} parameters.
 * It can work on different scales, from the block or the zonePreciseNameField of a zoning plan to a whole community.
 *
 * @author Maxime Colomb
 */
public class GetParametersOfScene {

    // static String scaleZone = "community";
    // TODO program the possibility of mixing those scales - List<String> scaleZone = Arrays.asList("community");
    static File parcelFile, buildingFile, zoningFile, roadFile, outFolder;

    /**
     * Proceed to the analysis of parameters in every defined zones of the geographic files.
     */
    public static void main(String[] args) throws IOException {
        outFolder = new File("/tmp/ParametersOfScene");
        setFiles(new File("src/main/resources/GeneralTest/"));
        String scaleZone = "genericZone";
        generateAnalysisOfScene(scaleZone);
        // scaleZone = "preciseZone";
        // generateAnalysisOfScene(scaleZone);
        // scaleZone = "community";
        // generateAnalysisOfScene(scaleZone);
        // scaleZone = "block";
        // generateAnalysisOfScene(scaleZone);
    }

    /**
     * Set automaticlally the file names with their basic names from a root folder. Possible to change them with dedicaded setters
     *
     * @param mainFolder root folder containing the geographic layers.
     */
    public static void setFiles(File mainFolder) {
        parcelFile = new File(mainFolder, "parcel" + CollecMgmt.getDefaultGISFileType());
        if (!parcelFile.exists())
            System.out.println(parcelFile + " doesn't exist");
        buildingFile = new File(mainFolder, "building" + CollecMgmt.getDefaultGISFileType());
        if (!buildingFile.exists())
            System.out.println(buildingFile + " doesn't exist");
        zoningFile = new File(mainFolder, "zoning" + CollecMgmt.getDefaultGISFileType());
        if (!zoningFile.exists())
            System.out.println(zoningFile + " doesn't exist");
        roadFile = new File(mainFolder, "road" + CollecMgmt.getDefaultGISFileType());
        if (!roadFile.exists())
            System.out.println(roadFile + " doesn't exist");
        outFolder.mkdirs();
    }
    /// **
    // * Allow the
    // * @param scalesZone
    // * @throws IOException
    // */
    // public static void generateAnalysisOfScene(List<String> scalesZone) throws IOException {
    // for (String scaleZone : scalesZone)
    // generateAnalysisOfScene(scaleZone);
    // //TODO finish that
    // }

    /**
     * Generate every indications about the urban scene.
     *
     * @param scaleZone The scale of the studied zone. Can either be:
     *                  <ul>
     *                  <li>community</li>
     *                  <li>genericZone</li>
     *                  <li>preciseZone</li>
     *                  <li>block</li>
     *                  </ul>
     * @throws IOException
     */
    public static void generateAnalysisOfScene(String scaleZone) throws IOException {
        DataStore ds = CollecMgmt.getDataStore(parcelFile);
        // sdsRoad.setCharset(Charset.forName("UTF-8"));
        SimpleFeatureCollection parcels = ds.getFeatureSource(ds.getTypeNames()[0]).getFeatures();
        // collection of every input with its set of parcels
        HashMap<String, SimpleFeatureCollection> listSFC = new HashMap<>();
        DataStore sdsZone = CollecMgmt.getDataStore(zoningFile);
        SimpleFeatureCollection zonings = DataUtilities
                .collection(CollecTransform.selectIntersection(sdsZone.getFeatureSource(sdsZone.getTypeNames()[0]).getFeatures(), parcels));
        sdsZone.dispose();
        // get the concerned features
        switch (scaleZone) {
            case "community":
                for (String cityCodes : ParcelAttribute.getCityCodesOfParcels(parcels))
                    listSFC.put(cityCodes, ParcelGetter.getParcelByZip(parcels, cityCodes));
                break;
            case "genericZone":
                for (String genericZone : GeneralFields.getGenericZoningTypes(zonings))
                    listSFC.put(genericZone, MarkParcelAttributeFromPosition.getOnlyMarkedParcels(
                            MarkParcelAttributeFromPosition.markParcelIntersectGenericZoningType(parcels, genericZone, zoningFile)));
                break;
            case "preciseZone":
                for (String preciseZone : GeneralFields.getPreciseZoningTypes(zonings))
                    listSFC.put(preciseZone, MarkParcelAttributeFromPosition.getOnlyMarkedParcels(
                            MarkParcelAttributeFromPosition.markParcelIntersectPreciseZoningType(parcels, "", preciseZone, zoningFile)));
                break;
            case "block":
                SimpleFeatureCollection block = CityGeneration.createUrbanBlock(parcels);
                int i = 0;
                try (SimpleFeatureIterator it = block.features()) {
                    while (it.hasNext())
                        listSFC.put(String.valueOf(i++), MarkParcelAttributeFromPosition.getOnlyMarkedParcels(MarkParcelAttributeFromPosition
                                .markParcelIntersectPolygonIntersection(parcels, Collections.singletonList((Geometry) it.next().getDefaultGeometry()))));
                } catch (Exception e) {
                    e.printStackTrace();
                }
                break;
        }
        DataStore dsRoad = CollecMgmt.getDataStore(roadFile);
        for (String zone : listSFC.keySet()) {
            // Parcel's area
            areaBuiltAndTotal(listSFC.get(zone), scaleZone, zone);
            // Road Information
            roadInformations(listSFC.get(zone), DataUtilities.collection(dsRoad.getFeatureSource(dsRoad.getTypeNames()[0]).getFeatures()), scaleZone,
                    zone);
        }
        dsRoad.dispose();
        ds.dispose();
        System.out.println("##### " + scaleZone + " done");
    }

    /**
     * Generate the road information in the give zones.
     *
     * @param zoneCollection
     * @param road
     * @param scaleZone
     * @param zoneName
     * @throws IOException
     */
    public static void roadInformations(SimpleFeatureCollection zoneCollection, SimpleFeatureCollection road, String scaleZone, String zoneName)
            throws IOException {
        // Road informations is harder to produce. We are based on the road Geopackage and on the ratio of road/area calculation to produce estimations
        // we create a buffer around the zone to get corresponding road segments. The buffer length depends on the type of scale
        double buffer = 42;
        switch (scaleZone) {
            case "genericZone":
                buffer = 20;
                break;
            case "preciseZone":
            case "block":
                buffer = 10;
                break;
        }
        Geometry zoneGeom = Geom.unionSFC(zoneCollection).buffer(buffer).buffer(-buffer);
        SimpleFeatureCollection roadsSelected = CollecTransform.selectIntersection(road, zoneGeom);
        if (roadsSelected.size() > 1)
            MakeStatisticGraphs.roadGraph(roadsSelected, "Characteristics of the " + getZoneEnglishName(scaleZone, zoneName) + " roads ",
                    "Type of road", "Total lenght of road", outFolder);

        DataStore parcelDS = CollecMgmt.getDataStore(parcelFile);
        RoadRatioParcels.roadRatioZone(zoneCollection, parcelDS.getFeatureSource(parcelDS.getTypeNames()[0]).getFeatures(), zoneName, outFolder, roadFile);
        parcelDS.dispose();
    }

    /**
     * Calculate the area bound of every parcels then only the build parcels.
     *
     * @param collection
     * @param scaleZone
     * @param zone
     * @throws IOException
     */
    public static void areaBuiltAndTotal(SimpleFeatureCollection collection, String scaleZone, String zone) throws IOException {
        if (collection.isEmpty())
            return;
        HashMap<String, SimpleFeatureCollection> lSFC = new HashMap<>();
        lSFC.put("total parcels", MarkParcelAttributeFromPosition.getOnlyMarkedParcels(collection));
        lSFC.put("built parcels",
                MarkParcelAttributeFromPosition.getOnlyMarkedParcels(MarkParcelAttributeFromPosition.markBuiltParcel(collection, buildingFile)));
        for (String nameSfc : lSFC.keySet()) {
            SimpleFeatureCollection sfc = lSFC.get(nameSfc);
            if (sfc != null && sfc.size() > 2) {
                Graph vals = MakeStatisticGraphs.sortValuesAreaAndCategorize(
                        Arrays.stream(sfc.toArray(new SimpleFeature[0])).collect(Collectors.toList()), scaleZone + zone, true);
                vals.toCSV(outFolder);
                MakeStatisticGraphs.makeGraphHisto(vals, outFolder, "area of the" + nameSfc + "of the " + getZoneEnglishName(scaleZone, zone),
                        "parcel area", "number of parcels", 15);
            }
        }
    }

    private static String getZoneEnglishName(String scaleZone, String zone) {
        switch (scaleZone) {
            case "genericZone":
                return GeneralFields.getGenericZoneEnglishName(zone);
            case "preciseZone":
                return "zone-" + zone;
            case "block":
                return "block-" + zone;
            case "community":
                return "community-" + zone;
        }
        throw new NullArgumentException();
    }

    public static File getParcelFile() {
        return parcelFile;
    }

    public static void setParcelFile(File parcelFile) {
        GetParametersOfScene.parcelFile = parcelFile;
    }

    public static File getBuildingFile() {
        return buildingFile;
    }

    public static void setBuildingFile(File buildingFile) {
        GetParametersOfScene.buildingFile = buildingFile;
    }

    public static File getZoningFile() {
        return zoningFile;
    }

    public static void setZoningFile(File zoningFile) {
        GetParametersOfScene.zoningFile = zoningFile;
    }

    public static File getRoadFile() {
        return roadFile;
    }

    public static void setRoadFile(File roadFile) {
        GetParametersOfScene.roadFile = roadFile;
    }

    public static File getOutFolder() {
        return outFolder;
    }

    public static void setOutFolder(File outFolder) {
        GetParametersOfScene.outFolder = outFolder;
    }
}
