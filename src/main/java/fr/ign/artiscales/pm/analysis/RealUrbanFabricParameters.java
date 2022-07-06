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
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.geotools.data.DataStore;
import org.geotools.data.DataUtilities;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.feature.DefaultFeatureCollection;
import org.locationtech.jts.geom.Geometry;
import org.opengis.feature.simple.SimpleFeature;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.stream.Collectors;

/**
 * This class generates values for the description of a parcel layout. It is aimed to help the setting of {@link fr.ign.artiscales.tools.parameter.ProfileUrbanFabric} parameters.
 * It can work on different scales, from the block or the zonePreciseNameField of a zoning plan to a whole community.
 * A zone scale must be defined
 *
 * @author Maxime Colomb
 */
public class RealUrbanFabricParameters {

    private final HashMap<String, SimpleFeatureCollection> parcelPerZone = new HashMap<>();
    private String scaleZone;
    // TODO program the possibility of mixing those scales - List<String> scaleZone = Arrays.asList("community");
    private File parcelFile, buildingFile, zoningFile, roadFile, outFolder;

    /**
     * Constructor for a complete analysis.
     *
     * @param scaleZone    scale of the analysis
     * @param parcelFile   geoFile for parcel plan
     * @param buildingFile geoFile for buildings
     * @param zoningFile   geoFile for zoning plan
     * @param roadFile     geoFile for roads
     * @param outFolder    folder where outputs are wrote
     */
    public RealUrbanFabricParameters(String scaleZone, File parcelFile, File buildingFile, File zoningFile, File roadFile, File outFolder) {
        this.setScaleZone(scaleZone);
        this.parcelFile = parcelFile;
        if (!parcelFile.exists())
            System.out.println(parcelFile + " doesn't exist");
        this.buildingFile = buildingFile;
        if (!buildingFile.exists())
            System.out.println(buildingFile + " doesn't exist");
        this.zoningFile = zoningFile;
        if (!zoningFile.exists())
            System.out.println(zoningFile + " doesn't exist");
        this.roadFile = roadFile;
        if (!roadFile.exists())
            System.out.println(roadFile + " doesn't exist");
        this.outFolder = outFolder;
        outFolder.mkdirs();
    }

    /**
     * Constructor for a basic file organization.
     *
     * @param scaleZone scale of the analysis
     * @param root      root folder must contain geo files properly named
     */
    public RealUrbanFabricParameters(String scaleZone, File root) {
        setScaleZone(scaleZone);
        setFiles(root);
    }

    /**
     * Constructor for a short usage (only input parcel's area).
     *
     * @param parcelSFC    set of parcels
     * @param buildingFile geoFile containing
     */
    public RealUrbanFabricParameters(SimpleFeatureCollection parcelSFC, File buildingFile) {
        parcelPerZone.put("parcel", parcelSFC);
        this.buildingFile = buildingFile;
    }

//    /**
//     * Proceed to the analysis of parameters in every defined zones of the geographic files.
//     */
//    public static void main(String[] args) throws IOException {
//        RealUrbanFabricParameters rufp = new RealUrbanFabricParameters("genericZone", new File("src/main/resources/TestScenario/"));
//        rufp.generateEveryAnalysisOfScene();
//        RealUrbanFabricParameters rufp = new RealUrbanFabricParameters(new File("src/main/resources/ParcelComparison/"));
//        rufp.setParcelFile(new File("src/main/resources/ParcelComparison/parcel2003.gpkg"));
//        rufp.scaleZone = "community";
//        rufp.makeSplitParcelBetweenZone();
//        for (String zone : rufp.parcelPerZone.keySet()) {
//            System.out.println("for " + zone);
//            // Parcel's area
//            DescriptiveStatistics stat = rufp.getAreaBuilt(rufp.parcelPerZone.get(zone), zone);
//            System.out.println("10: " + stat.getPercentile(10));
//            System.out.println("20: " + stat.getPercentile(20));
//            System.out.println("80: " + stat.getPercentile(80));
//            System.out.println("90: " + stat.getPercentile(90));
//        }
//    }

    private static String getZoneEnglishName(String scaleZone, String zone) {
        switch (scaleZone) {
            case "genericZone":
                return GeneralFields.getGenericZoneEnglishName(zone) + " zone";
            case "preciseZone":
                return zone + " zone";
            case "block":
                return zone + " block";
            case "community":
                return zone + " community";
        }
        throw new NullArgumentException();
    }

    /**
     * Get the scale of the analyzed zone
     *
     * @return the scale
     */
    public String getScaleZone() {
        return scaleZone;
    }

    /**
     * Set the scale of the analyzed zone.
     *
     * @param scaleZone Can either be: <ul>
     *                  <li>community</li>
     *                  <li>genericZone</li>
     *                  <li>preciseZone</li>
     *                  <li>block</li>
     *                  </ul>
     */
    public void setScaleZone(String scaleZone) {
        if (!scaleZone.equals("community") && !scaleZone.equals("genericZone") && !scaleZone.equals("preciseZone") && !scaleZone.equals("block")) {
            System.out.println("setScaleZone: invalid value. Set community as default value");
            this.scaleZone = "community";
        } else
            this.scaleZone = scaleZone;
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
     * Set automaticlally the file names with their basic names from a root folder. Possible to change them with dedicaded setters
     *
     * @param mainFolder root folder containing the geographic layers.
     */
    public void setFiles(File mainFolder) {
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
        outFolder = new File(mainFolder, "/ParametersOfScene");
        outFolder.mkdirs();
    }

    /**
     * Generate every indicators about the urban scene (area of parcel built and area of all parcels, road statistics)
     *
     * @throws IOException reading files
     */
    public void generateEveryAnalysisOfScene() throws IOException {
        makeSplitParcelBetweenZone();
        DataStore dsRoad = CollecMgmt.getDataStore(roadFile);
        for (String zone : parcelPerZone.keySet()) {
            System.out.println("for " + zone);
            // Parcel's area
            makeAreaBuiltAndTotal(parcelPerZone.get(zone), zone);
            // Road Information
            roadInformations(parcelPerZone.get(zone), DataUtilities.collection(dsRoad.getFeatureSource(dsRoad.getTypeNames()[0]).getFeatures()), scaleZone, zone);
        }
        dsRoad.dispose();
        System.out.println("##### " + scaleZone + " done");
    }

    /**
     * Fulfill the collection of zones by splitting the parcel plan relatively to the given scale type.
     * Scale and the collection of zones per scale type's units are stored statically.
     *
     * @throws IOException reading files
     */
    public void makeSplitParcelBetweenZone() throws IOException {
        DataStore dsParcel = CollecMgmt.getDataStore(parcelFile);
        SimpleFeatureCollection parcel = dsParcel.getFeatureSource(dsParcel.getTypeNames()[0]).getFeatures();
        switch (scaleZone) {
            case "community":
                for (String cityCodes : ParcelAttribute.getCityCodesOfParcels(parcel))
                    parcelPerZone.put(cityCodes, ParcelGetter.getParcelByZip(parcel, cityCodes));
                break;
            case "genericZone":
            case "preciseZone":
                DataStore dsZone = CollecMgmt.getDataStore(zoningFile);
                SimpleFeatureCollection zonings = DataUtilities
                        .collection(CollecTransform.selectIntersection(dsZone.getFeatureSource(dsZone.getTypeNames()[0]).getFeatures(), parcel));
                if (scaleZone.equals("genericZone"))
                    for (String genericZone : GeneralFields.getGenericZoningTypes(zonings))
                        parcelPerZone.put(genericZone, MarkParcelAttributeFromPosition.getOnlyMarkedParcels(
                                MarkParcelAttributeFromPosition.markParcelIntersectGenericZoningType(parcel, genericZone, zoningFile)));
                else //precise zone
                    for (String preciseZone : GeneralFields.getPreciseZoningTypes(zonings))
                        parcelPerZone.put(preciseZone, MarkParcelAttributeFromPosition.getOnlyMarkedParcels(
                                MarkParcelAttributeFromPosition.markParcelIntersectPreciseZoningType(parcel, "", preciseZone, zoningFile)));
                dsZone.dispose();
                break;
            case "block":
                SimpleFeatureCollection block = CityGeneration.createUrbanBlock(parcel);
                int i = 0;
                try (SimpleFeatureIterator it = block.features()) {
                    while (it.hasNext())
                        parcelPerZone.put(String.valueOf(i++), MarkParcelAttributeFromPosition.getOnlyMarkedParcels(MarkParcelAttributeFromPosition
                                .markParcelIntersectPolygonIntersection(parcel, Collections.singletonList((Geometry) it.next().getDefaultGeometry()))));
                } catch (Exception e) {
                    e.printStackTrace();
                }
                break;
        }
        dsParcel.dispose();
    }

    /**
     * Generate the road information in the give zones.
     *
     * @param zoneCollection parcels from the zones to analyse
     * @param road           road feature collection
     * @param scaleZone      name of the scale to analyse
     * @param zoneName       name of the zone to analyse
     * @throws IOException reading files
     */
    public void roadInformations(SimpleFeatureCollection zoneCollection, SimpleFeatureCollection road, String scaleZone, String zoneName) throws IOException {
        if (parcelPerZone.isEmpty())
            this.makeSplitParcelBetweenZone();
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
        ;
//        double buffer = switch (scaleZone) {
//            case "genericZone" -> 20;
//            case "preciseZone", "block" -> 10;
//            default -> 42;
//        };
        Geometry zoneGeom = Geom.safeUnion(zoneCollection).buffer(buffer).buffer(-buffer);
        SimpleFeatureCollection roadsSelected = CollecTransform.selectIntersection(road, zoneGeom);
        if (roadsSelected.size() > 1)
            MakeStatisticGraphs.roadGraph(roadsSelected, "Characteristics of the roads from the " + getZoneEnglishName(scaleZone, zoneName),
                    "Type of road", "Total lenght of road", outFolder);
        DataStore parcelDS = CollecMgmt.getDataStore(parcelFile);
        RoadRatioParcels.roadRatioZone(zoneCollection, CollecTransform.selectIntersection(parcelDS.getFeatureSource(parcelDS.getTypeNames()[0]).getFeatures(), Geom.safeUnion(zoneCollection).buffer(buffer)), zoneName, outFolder, true, roadFile);
        parcelDS.dispose();
    }

    /**
     * Get area built for a short usage. A single zone has to be defined.
     *
     * @return the distribution of area.
     * @throws IOException if wrong parcelPerZone Map is defined, or (potentially) reading geo files and writing csv
     */
    public DescriptiveStatistics getAreaBuilt() throws IOException {
        if (parcelPerZone.size() > 1)
            throw new IOException("RealUrbanFabricParameters.getAreaBuilt() : wrong usage of this method, there must be a single zone defined");
        for (String zone : parcelPerZone.keySet())
            return getAreaBuilt(parcelPerZone.get(zone), zone);
        throw new IOException("RealUrbanFabricParameters.getAreaBuilt() : wrong usage of this method, there must be a single zone defined");
    }

    /**
     * Get the distribution of built parcel's area of one zone.
     *
     * @param collection parcel collection
     * @param zoneName   name of the zone to get
     * @return the distribution
     * @throws IOException (potentially) reading geo files and writing csv
     */
    public DescriptiveStatistics getAreaBuilt(SimpleFeatureCollection collection, String zoneName) throws IOException {
        if (parcelPerZone.isEmpty())
            this.makeSplitParcelBetweenZone();
        if (collection.isEmpty())
            return null;
        DataStore buildingDS = CollecMgmt.getDataStore(buildingFile);
        DefaultFeatureCollection df = new DefaultFeatureCollection();
        Arrays.stream(collection.toArray(new SimpleFeature[0])).forEach(sf -> df.add((SimpleFeature) DataUtilities.duplicate(sf)));
        SimpleFeatureCollection sfc = MarkParcelAttributeFromPosition.getOnlyMarkedParcels(MarkParcelAttributeFromPosition.markBuiltParcel(MarkParcelAttributeFromPosition.resetMarkingField(df),
                CollecTransform.selectIntersection(buildingDS.getFeatureSource(buildingDS.getTypeNames()[0]).getFeatures(), Geom.safeUnion(df))));
        buildingDS.dispose();
        DescriptiveStatistics ds = new DescriptiveStatistics();
        if (sfc != null && sfc.size() > 2) {
            Arrays.stream(sfc.toArray(new SimpleFeature[0])).forEach(sf -> ds.addValue(((Geometry) sf.getDefaultGeometry()).getArea()));
            if (outFolder != null) {
                Graph vals = MakeStatisticGraphs.sortValuesAreaAndCategorize(
                        Arrays.stream(sfc.toArray(new SimpleFeature[0])).collect(Collectors.toList()), scaleZone + zoneName, true);
                vals.setNameDistrib(vals.getNameDistrib() + "-built");
                vals.toCSV(outFolder);
                MakeStatisticGraphs.makeGraphHisto(vals, outFolder, "built parcels of the " + getZoneEnglishName(scaleZone, zoneName),
                        "parcel area", "number of parcels", 15);
            }
        }
        return ds;
    }

    /**
     * Get the distribution of every parcel's area of one zone.
     *
     * @param collection parcel collection
     * @param zoneName   name of the zone to get
     * @return the distribution
     * @throws IOException (potentially) reading geofiles and writting csv
     */
    public DescriptiveStatistics getAreaTotal(SimpleFeatureCollection collection, String zoneName) throws IOException {
        if (parcelPerZone.isEmpty())
            this.makeSplitParcelBetweenZone();
        if (collection.isEmpty())
            return null;
        DescriptiveStatistics ds = new DescriptiveStatistics();
        SimpleFeatureCollection sfc = MarkParcelAttributeFromPosition.markAllParcel(collection);
        if (sfc.size() > 2) {
            Graph vals = MakeStatisticGraphs.sortValuesAreaAndCategorize(
                    Arrays.stream(sfc.toArray(new SimpleFeature[0])).collect(Collectors.toList()), scaleZone + zoneName, true);
            vals.toCSV(outFolder);
            MakeStatisticGraphs.makeGraphHisto(vals, outFolder, "total parcels of the " + getZoneEnglishName(scaleZone, zoneName),
                    "parcel area", "number of parcels", 15);
            Arrays.stream(sfc.toArray(new SimpleFeature[0])).forEach(sf -> ds.addValue(((Geometry) sf.getDefaultGeometry()).getArea()));
        }
        return ds;
    }

    /**
     * Calculate the area bound of every parcels then only the build parcels.
     *
     * @param collection parcel collection
     * @param zoneName   name of the zone to analyze
     * @throws IOException reading geofiles and writting csv
     */
    public void makeAreaBuiltAndTotal(SimpleFeatureCollection collection, String zoneName) throws IOException {
        if (parcelPerZone.isEmpty())
            this.makeSplitParcelBetweenZone();
        if (collection.isEmpty())
            return;
        getAreaTotal(collection, zoneName);
        getAreaBuilt(collection, zoneName);
    }
}
