package fr.ign.artiscales.pm.analysis;

import com.opencsv.CSVWriter;
import fr.ign.artiscales.pm.fields.GeneralFields;
import fr.ign.artiscales.pm.parcelFunction.MarkParcelAttributeFromPosition;
import fr.ign.artiscales.pm.parcelFunction.ParcelSchema;
import fr.ign.artiscales.pm.parcelFunction.ParcelState;
import fr.ign.artiscales.tools.geoToolsFunctions.Attribute;
import fr.ign.artiscales.tools.geoToolsFunctions.vectors.collec.CollecMgmt;
import fr.ign.artiscales.tools.geoToolsFunctions.vectors.collec.CollecTransform;
import fr.ign.artiscales.tools.geoToolsFunctions.vectors.collec.OpOnCollec;
import fr.ign.artiscales.tools.geoToolsFunctions.vectors.geom.Lines;
import fr.ign.artiscales.tools.geoToolsFunctions.vectors.geom.Polygons;
import fr.ign.artiscales.tools.geometryGeneration.CityGeneration;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.geotools.data.DataStore;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.feature.DefaultFeatureCollection;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.locationtech.jts.algorithm.MinimumBoundingCircle;
import org.locationtech.jts.algorithm.construct.MaximumInscribedCircle;
import org.locationtech.jts.algorithm.distance.DiscreteHausdorffDistance;
import org.locationtech.jts.algorithm.match.HausdorffSimilarityMeasure;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Polygon;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;

import static fr.ign.artiscales.pm.fields.GeneralFields.makeParcelCode;

/**
 * This class calculates basic statistics for every marked parcels. If no mark parcels are found, stats are calucated for every parcels.
 *
 * @author Maxime Colomb
 */
public class SingleParcelStat {

//	 public static void main(String[] args) throws IOException {
//	 long strat = System.currentTimeMillis();
//	 File root = new File("src/main/resources/ParcelComparison/");
//	 DataStore dsParcelEv = CollecMgmt.getDataStore(new File(root,"/out/evolvedParcel.gpkg"));
//	 DataStore dsParcelSimu = CollecMgmt.getDataStore(new File(root, "/out/simulatedParcel.gpkg"));
//	 SimpleFeatureCollection parcelEv = FrenchParcelFields.addCommunityCode(
//	         MarkParcelAttributeFromPosition.markAllParcel(dsParcelEv.getFeatureSource(dsParcelEv.getTypeNames()[0]).getFeatures()));
//	 SimpleFeatureCollection parcelSimu = MarkParcelAttributeFromPosition.markAllParcel(dsParcelSimu.getFeatureSource(dsParcelSimu.getTypeNames()[0]).getFeatures());
//	 DataStore dsRoad = CollecMgmt.getDataStore(new File(root, "/road.gpkg"));
//	 SimpleFeatureCollection road = dsRoad.getFeatureSource(dsRoad.getTypeNames()[0]).getFeatures();
//
//	 writeStatSingleParcel(parcelEv, road, new File(root,"out2/ev.csv"));
//	 writeStatSingleParcel(parcelSimu, road, parcelEv, new File(root,"out2/sim.csv"));
//
//	 // Collec.exportSFC(makeHausdorfDistanceMaps(parcelEv, parcelSimu), new File("/tmp/haus"));
//	 dsParcelEv.dispose();
//	 dsParcelSimu.dispose();
//	 dsRoad.dispose();
//	 System.out.println("time : " + (System.currentTimeMillis() - strat));
//	 }

    /**
     * Write every regular statistics for a parcel plan
     *
     * @param parcelFile    geofile conaining the plan
     * @param roadFile      Road files
     * @param parcelStatCsv output statistic .csv file
     * @param markAll       do we mark every parcels ? Only marked parcels will have their stat made.
     * @throws IOException reading and writing file
     */
    public static void writeStatSingleParcel(File parcelFile, File roadFile, File parcelStatCsv, boolean markAll) throws IOException {
        writeStatSingleParcel(parcelFile, roadFile, null, parcelStatCsv, markAll);
    }

    /**
     * Write every regular statistics for a parcel plan
     *
     * @param parcelFile      geofile conaining the plan
     * @param parcelToCompare Optional parcel plan to compare shapes
     * @param roadFile        Road files
     * @param parcelStatCsv   output statistic .csv file
     * @param markAll         do we mark every parcels ? Only marked parcels will have their stat made.
     * @throws IOException reading and writing file
     */
    public static void writeStatSingleParcel(File parcelFile, File roadFile, File parcelToCompare, File parcelStatCsv, boolean markAll) throws IOException {
        DataStore dsRoad = CollecMgmt.getDataStore(roadFile);
        DataStore dsParcel = CollecMgmt.getDataStore(parcelFile);
        SimpleFeatureCollection parcels;
        if (markAll)
            parcels = MarkParcelAttributeFromPosition.markAllParcel(dsParcel.getFeatureSource(dsParcel.getTypeNames()[0]).getFeatures());
        else
            parcels = dsParcel.getFeatureSource(dsParcel.getTypeNames()[0]).getFeatures();
        if (!CollecMgmt.isCollecContainsAttribute(parcels, ParcelSchema.getMinParcelCommunityField()))
            parcels = GeneralFields.addCommunityCode(parcels);
        if (parcelToCompare == null)
            writeStatSingleParcel(parcels, dsRoad.getFeatureSource(dsRoad.getTypeNames()[0]).getFeatures(), parcelStatCsv);
        else {
            DataStore dsParcel2 = CollecMgmt.getDataStore(parcelToCompare);
            writeStatSingleParcel(parcels, dsRoad.getFeatureSource(dsRoad.getTypeNames()[0]).getFeatures(),
                    dsParcel2.getFeatureSource(dsParcel2.getTypeNames()[0]).getFeatures(), parcelStatCsv);
            dsParcel2.dispose();
        }
        dsRoad.dispose();
        dsParcel.dispose();
    }

    /**
     * Calculate statistics (area, perimeter, widthContactWithRoad, numberOfNeighborhood and AspectRatio) for every single parcels.
     *
     * @param parcels       collection of parcels
     * @param roadFile      road feature collection to calculate contact with road. Could be optional (but it's not yet)
     * @param parcelStatCsv output tab file to write
     * @throws IOException writing stats
     */
    public static void writeStatSingleParcel(SimpleFeatureCollection parcels, File roadFile, File parcelStatCsv) throws IOException {
        DataStore sds = CollecMgmt.getDataStore(roadFile);
        writeStatSingleParcel(parcels, sds.getFeatureSource(sds.getTypeNames()[0]).getFeatures(), parcelStatCsv);
        sds.dispose();
    }

    /**
     * Calculate statistics (area, perimeter, widthContactWithRoad, numberOfNeighborhood and AspectRatio) for every single parcels.
     *
     * @param parcels       collection of parcels
     * @param roads         road feature collection to calculate contact with road. Could be optional (but it's not yet)
     * @param parcelStatCsv output tab file to write
     * @throws IOException writing stats
     */
    public static void writeStatSingleParcel(SimpleFeatureCollection parcels, SimpleFeatureCollection roads, File parcelStatCsv) throws IOException {
        writeStatSingleParcel(parcels, roads, null, parcelStatCsv);
    }

    /**
     * Calculate statistics (area, perimeter, widthContactWithRoad, numberOfNeighborhood, Hausdorf Distances and AspectRatio) for every single parcels.
     *
     * @param parcels         collection of parcels
     * @param roads           road feature collection to calculate contact with road. Could be optional (but it's not yet)
     * @param parcelToCompare parcel plan before their simulation.
     * @param parcelStatCsv   output tab file to write
     * @throws IOException writing stats
     */
    public static void writeStatSingleParcel(SimpleFeatureCollection parcels, SimpleFeatureCollection roads, SimpleFeatureCollection parcelToCompare, File parcelStatCsv) throws IOException {
        // look if there's mark field. If not, every parcels are marked
        if (!CollecMgmt.isCollecContainsAttribute(parcels, MarkParcelAttributeFromPosition.getMarkFieldName())) {
            System.out.println("+++ writeStatSingleParcel: unmarked parcels. Try to mark them with the MarkParcelAttributeFromPosition.markAllParcel() method. Return null ");
            return;
        }
        CSVWriter csv = new CSVWriter(new FileWriter(parcelStatCsv, false));
        String[] firstLine = {"code", "area", "perimeter", "contactWithRoad", "widthContactWithRoad", "numberOfNeighborhood", "HausDist",
                "DisHausDst", "CodeAppar", "AspectRatio"};
        csv.writeNext(firstLine);
        SimpleFeatureCollection block = CityGeneration.createUrbanBlock(parcels, true);
        Arrays.stream(parcels.toArray(new SimpleFeature[0]))
                .filter(p -> (int) p.getAttribute(MarkParcelAttributeFromPosition.getMarkFieldName()) == 1).forEach(parcel -> {
                    // if parcel is marked to be analyzed
                    Geometry parcelGeom = (Geometry) parcel.getDefaultGeometry();
                    double widthRoadContact = ParcelState.getParcelFrontSideWidth(Polygons.getPolygon(parcelGeom),
                            CollecTransform.selectIntersection(roads, parcelGeom.buffer(7)), Lines.fromMultiToLineString(
                                    CollecTransform.fromPolygonSFCtoRingMultiLines(CollecTransform.selectIntersection(block, parcelGeom.buffer(7)))));
                    boolean contactWithRoad = widthRoadContact != 0;
                    // if we set a parcel plan to compare, we calculate the Hausdorff distances for the parcels that intersects the most parts.
                    String HausDist = "NA";
                    String DisHausDst = "NA";
                    String CodeAppar = "NA";
                    // Setting of Hausdorf distance
                    if (parcelToCompare != null) {
                        HausdorffSimilarityMeasure hausDis = new HausdorffSimilarityMeasure();
                        SimpleFeature parcelCompare = CollecTransform.getIntersectingSimpleFeatureFromSFC(parcelGeom, parcelToCompare);
                        if (parcelCompare != null) {
                            Geometry parcelCompareGeom = (Geometry) parcelCompare.getDefaultGeometry();
                            DiscreteHausdorffDistance dhd = new DiscreteHausdorffDistance(parcelGeom, parcelCompareGeom);
                            HausDist = String.valueOf(hausDis.measure(parcelGeom, parcelCompareGeom));
                            DisHausDst = String.valueOf(dhd.distance());
                            CodeAppar = makeParcelCode(parcelCompare);
                        }
                    }
                    // Calculating Aspect Ratio
                    MinimumBoundingCircle mbc = new MinimumBoundingCircle(parcelGeom);
                    MaximumInscribedCircle mic = new MaximumInscribedCircle(parcelGeom, 1);
                    String[] line = {
                            parcel.getAttribute(ParcelSchema.getMinParcelCommunityField()) + "-"
                                    + parcel.getAttribute(ParcelSchema.getMinParcelSectionField()) + "-"
                                    + parcel.getAttribute(ParcelSchema.getMinParcelNumberField()),
                            String.valueOf(parcelGeom.getArea()), String.valueOf(parcelGeom.getLength()), String.valueOf(contactWithRoad),
                            String.valueOf(widthRoadContact),
                            String.valueOf(ParcelState.countParcelNeighborhood(parcelGeom, CollecTransform.selectIntersection(parcels, parcelGeom.buffer(2)))),
                            HausDist, DisHausDst, CodeAppar,
                            String.valueOf(mic.getRadiusLine().getLength() * 2 / mbc.getDiameter().getLength())};
                    csv.writeNext(line);
                });
        csv.close();
    }

    // public static double aspectRatio(Geometry geom) {
    // MinimumBoundingCircle mbc = new MinimumBoundingCircle(geom);
    //// MaximumInscribedCircle mic = new MaximumInscribedCircle(geom, 1);
    //// return mic.getRadiusLine().getLength() / mbc.getDiameter().getLength();
    // return mbc.getDiameter().getLength();
    // }

    /**
     * Calculation Hausdorff Similarity average for a set of parcels. Candidate must have been reduced before methode call
     *
     * @param parcelInFile        The reference geo file
     * @param parcelToCompareFile The geo file to compare
     * @return Hausdorff Similarity average
     * @throws IOException reading files
     */
    public static double hausdorffSimilarityAverage(File parcelInFile, File parcelToCompareFile) throws IOException {
        DataStore sdsParcelIn = CollecMgmt.getDataStore(parcelInFile);
        DataStore sdsParcelToCompareFile = CollecMgmt.getDataStore(parcelToCompareFile);
        double result = hausdorffSimilarityAverage(sdsParcelIn.getFeatureSource(sdsParcelIn.getTypeNames()[0]).getFeatures(),
                sdsParcelToCompareFile.getFeatureSource(sdsParcelToCompareFile.getTypeNames()[0]).getFeatures());
        sdsParcelIn.dispose();
        sdsParcelToCompareFile.dispose();
        return result;
    }

    /**
     * Calculate the difference of number of parcels between two geo files.
     *
     * @param parcelInFile        The reference geo file
     * @param parcelToCompareFile The geo file to compare
     * @return the difference of average (absolute value)
     * @throws IOException reading files
     */
    public static int diffNumberOfParcel(File parcelInFile, File parcelToCompareFile) throws IOException {
        DataStore dsParcelIn = CollecMgmt.getDataStore(parcelInFile);
        DataStore dsParcelToCompareFile = CollecMgmt.getDataStore(parcelToCompareFile);
        int result = dsParcelIn.getFeatureSource(dsParcelIn.getTypeNames()[0]).getFeatures().size()
                - dsParcelToCompareFile.getFeatureSource(dsParcelToCompareFile.getTypeNames()[0]).getFeatures().size();
        dsParcelIn.dispose();
        dsParcelToCompareFile.dispose();
        return Math.abs(result);
    }

    /**
     * Calculate the difference between the average area of two Geopackages. Find a better indicator to compare distribution.
     *
     * @param parcelInFile        The reference Geopackage
     * @param parcelToCompareFile The Geopackage to compare
     * @return the difference of average (absolute value)
     * @throws IOException reading files
     */
    public static double diffAreaAverage(File parcelInFile, File parcelToCompareFile) throws IOException {
        DataStore dsParcelIn = CollecMgmt.getDataStore(parcelInFile);
        DataStore dsParcelToCompareFile = CollecMgmt.getDataStore(parcelToCompareFile);
        double result = OpOnCollec.area(dsParcelIn.getFeatureSource(dsParcelIn.getTypeNames()[0]).getFeatures())
                - OpOnCollec.area(dsParcelToCompareFile.getFeatureSource(dsParcelToCompareFile.getTypeNames()[0]).getFeatures());
        dsParcelIn.dispose();
        dsParcelToCompareFile.dispose();
        return Math.abs(result);
    }

    /**
     * Calculation Hausdorff Similarity average for a set of parcels. Candidate must have been reduced before methode call
     *
     * @param parcelIn        reference parcel set
     * @param parcelToCompare parcels to compare shapes
     * @return Mean of Hausdorff distances
     */
    public static double hausdorffSimilarityAverage(SimpleFeatureCollection parcelIn, SimpleFeatureCollection parcelToCompare) {
        HausdorffSimilarityMeasure hausDis = new HausdorffSimilarityMeasure();
        DescriptiveStatistics stat = new DescriptiveStatistics();
        try (SimpleFeatureIterator parcelIt = parcelIn.features()) {
            while (parcelIt.hasNext()) {
                SimpleFeature parcel = parcelIt.next();
                Geometry parcelGeom = (Geometry) parcel.getDefaultGeometry();
                SimpleFeature parcelCompare = CollecTransform.getIntersectingSimpleFeatureFromSFC(parcelGeom, parcelToCompare);
                if (parcelCompare != null)
                    stat.addValue(hausDis.measure(parcelGeom, (Geometry) parcelCompare.getDefaultGeometry()));
            }
        } catch (Exception problem) {
            problem.printStackTrace();
        }
        return stat.getMean();
    }

    /**
     * Generation of maps containing Hausdorf distance between a parcel plan and its matched compared parcel
     *
     * @param parcelIn        Reference parcel plan
     * @param parcelToCompare parcel to build Hausdorf distance.
     * @return A simple
     */
    public static SimpleFeatureCollection makeHausdorfDistanceMaps(SimpleFeatureCollection parcelIn, SimpleFeatureCollection parcelToCompare) {
        if (!CollecMgmt.isCollecContainsAttribute(parcelIn, "CODE"))
            GeneralFields.addParcelCode(parcelIn);
        if (!CollecMgmt.isCollecContainsAttribute(parcelToCompare, "CODE"))
            GeneralFields.addParcelCode(parcelToCompare);
        SimpleFeatureType schema = parcelIn.getSchema();
        DefaultFeatureCollection result = new DefaultFeatureCollection();
        SimpleFeatureTypeBuilder sfTypeBuilder = new SimpleFeatureTypeBuilder();
        sfTypeBuilder.setName("minParcel");
        sfTypeBuilder.setCRS(schema.getCoordinateReferenceSystem());
        sfTypeBuilder.add(schema.getGeometryDescriptor().getLocalName(), Polygon.class);
        sfTypeBuilder.setDefaultGeometry(schema.getGeometryDescriptor().getLocalName());
        sfTypeBuilder.add("DisHausDst", Double.class);
        sfTypeBuilder.add("HausDist", Double.class);
        sfTypeBuilder.add("CODE", String.class);
        sfTypeBuilder.add("CodeAppar", String.class);
        SimpleFeatureBuilder builder = new SimpleFeatureBuilder(sfTypeBuilder.buildFeatureType());
        HausdorffSimilarityMeasure hausDis = new HausdorffSimilarityMeasure();
        try (SimpleFeatureIterator parcelIt = parcelIn.features()) {
            while (parcelIt.hasNext()) {
                SimpleFeature parcel = parcelIt.next();
                Geometry parcelGeom = (Geometry) parcel.getDefaultGeometry();
                SimpleFeature parcelCompare = CollecTransform.getIntersectingSimpleFeatureFromSFC(parcelGeom, parcelToCompare);
                if (parcelCompare != null) {
                    Geometry parcelCompareGeom = (Geometry) parcelCompare.getDefaultGeometry();
                    DiscreteHausdorffDistance dhd = new DiscreteHausdorffDistance(parcelGeom, parcelCompareGeom);
                    builder.set("DisHausDst", dhd.distance());
                    builder.set("HausDist", hausDis.measure(parcelGeom, parcelCompareGeom));
                    builder.set("CodeAppar", makeParcelCode(parcelCompare));
                }
                builder.set("CODE", parcel.getAttribute("CODE"));
                builder.set(schema.getGeometryDescriptor().getLocalName(), parcelGeom);
                result.add(builder.buildFeature(Attribute.makeUniqueId()));
            }
        } catch (Exception problem) {
            problem.printStackTrace();
        }
        return result;
    }
}
