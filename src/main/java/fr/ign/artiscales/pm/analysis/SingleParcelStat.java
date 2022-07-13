package fr.ign.artiscales.pm.analysis;

import com.opencsv.CSVWriter;
import fr.ign.artiscales.pm.fields.GeneralFields;
import fr.ign.artiscales.pm.parcelFunction.MarkParcelAttributeFromPosition;
import fr.ign.artiscales.pm.parcelFunction.ParcelSchema;
import fr.ign.artiscales.pm.parcelFunction.ParcelState;
import fr.ign.artiscales.tools.geoToolsFunctions.vectors.collec.CollecMgmt;
import fr.ign.artiscales.tools.geoToolsFunctions.vectors.collec.CollecTransform;
import fr.ign.artiscales.tools.geoToolsFunctions.vectors.geom.Lines;
import fr.ign.artiscales.tools.geoToolsFunctions.vectors.geom.Polygons;
import fr.ign.artiscales.tools.geometryGeneration.CityGeneration;
import org.geotools.data.DataStore;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.locationtech.jts.algorithm.MinimumBoundingCircle;
import org.locationtech.jts.algorithm.construct.MaximumInscribedCircle;
import org.locationtech.jts.algorithm.distance.DiscreteHausdorffDistance;
import org.locationtech.jts.algorithm.match.HausdorffSimilarityMeasure;
import org.locationtech.jts.geom.Geometry;
import org.opengis.feature.simple.SimpleFeature;

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
        if (!CollecMgmt.isCollecContainsAttribute(parcels, ParcelSchema.getParcelCommunityField()))
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
                    // if we set a parcel plan to compare, we calculate the Hausdorff distances for the parcels that intersects the most parts.
                    String HausDist = "NA";
                    String DisHausDst = "NA";
                    String CodeAppar = "NA";
                    // Setting of Hausdorf distance
                    if (parcelToCompare != null) {
                        SimpleFeature parcelCompare = CollecTransform.getIntersectingSimpleFeatureFromSFC(parcelGeom, parcelToCompare);
                        if (parcelCompare != null) {
                            Geometry parcelCompareGeom = (Geometry) parcelCompare.getDefaultGeometry();
                            HausDist = String.valueOf( new HausdorffSimilarityMeasure().measure(parcelGeom, parcelCompareGeom));
                            DisHausDst = String.valueOf(new DiscreteHausdorffDistance(parcelGeom, parcelCompareGeom).distance());
                            CodeAppar = makeParcelCode(parcelCompare);
                        }
                    }
                    String[] line = {
                            parcel.getAttribute(ParcelSchema.getParcelCommunityField()) + "-"
                                    + parcel.getAttribute(ParcelSchema.getParcelSectionField()) + "-"
                                    + parcel.getAttribute(ParcelSchema.getParcelNumberField()),
                            String.valueOf(parcelGeom.getArea()), String.valueOf(parcelGeom.getLength()), String.valueOf(widthRoadContact != 0),
                            String.valueOf(widthRoadContact),
                            String.valueOf(ParcelState.countParcelNeighborhood(parcelGeom, CollecTransform.selectIntersection(parcels, parcelGeom.buffer(2)))),
                            HausDist, DisHausDst, CodeAppar, String.valueOf(aspectRatio(parcelGeom))};
                    csv.writeNext(line);
                });
        csv.close();
    }

    public static double aspectRatio(Geometry geom) {
        MinimumBoundingCircle mbc = new MinimumBoundingCircle(geom);
        MaximumInscribedCircle mic = new MaximumInscribedCircle(geom, 1);
        return mic.getRadiusLine().getLength() * 2 / mbc.getDiameter().getLength();
    }

//    /**
//     * Generation of maps containing Hausdorf distance between a parcel plan and its matched compared parcel
//     *
//     * @param parcelIn        Reference parcel plan
//     * @param parcelToCompare parcel to build Hausdorf distance.
//     * @return A simple
//     */
//    public static SimpleFeatureCollection makeHausdorfDistanceMaps(SimpleFeatureCollection parcelIn, SimpleFeatureCollection parcelToCompare) {
//        if (!CollecMgmt.isCollecContainsAttribute(parcelIn, "CODE"))
//            GeneralFields.addParcelCode(parcelIn);
//        if (!CollecMgmt.isCollecContainsAttribute(parcelToCompare, "CODE"))
//            GeneralFields.addParcelCode(parcelToCompare);
//        SimpleFeatureType schema = parcelIn.getSchema();
//        DefaultFeatureCollection result = new DefaultFeatureCollection();
//        SimpleFeatureTypeBuilder sfTypeBuilder = new SimpleFeatureTypeBuilder();
//        sfTypeBuilder.setName("minParcel");
//        sfTypeBuilder.setCRS(schema.getCoordinateReferenceSystem());
//        sfTypeBuilder.add(schema.getGeometryDescriptor().getLocalName(), Polygon.class);
//        sfTypeBuilder.setDefaultGeometry(schema.getGeometryDescriptor().getLocalName());
//        sfTypeBuilder.add("DisHausDst", Double.class);
//        sfTypeBuilder.add("HausDist", Double.class);
//        sfTypeBuilder.add("CODE", String.class);
//        sfTypeBuilder.add("CodeAppar", String.class);
//        SimpleFeatureBuilder builder = new SimpleFeatureBuilder(sfTypeBuilder.buildFeatureType());
//        HausdorffSimilarityMeasure hausDis = new HausdorffSimilarityMeasure();
//        try (SimpleFeatureIterator parcelIt = parcelIn.features()) {
//            while (parcelIt.hasNext()) {
//                SimpleFeature parcel = parcelIt.next();
//                Geometry parcelGeom = (Geometry) parcel.getDefaultGeometry();
//                SimpleFeature parcelCompare = CollecTransform.getIntersectingSimpleFeatureFromSFC(parcelGeom, parcelToCompare);
//                if (parcelCompare != null) {
//                    Geometry parcelCompareGeom = (Geometry) parcelCompare.getDefaultGeometry();
//                    DiscreteHausdorffDistance dhd = new DiscreteHausdorffDistance(parcelGeom, parcelCompareGeom);
//                    builder.set("DisHausDst", dhd.distance());
//                    builder.set("HausDist", hausDis.measure(parcelGeom, parcelCompareGeom));
//                    builder.set("CodeAppar", makeParcelCode(parcelCompare));
//                }
//                builder.set("CODE", parcel.getAttribute("CODE"));
//                builder.set(schema.getGeometryDescriptor().getLocalName(), parcelGeom);
//                result.add(builder.buildFeature(Attribute.makeUniqueId()));
//            }
//        } catch (Exception problem) {
//            problem.printStackTrace();
//        }
//        return result;
//    }
}
