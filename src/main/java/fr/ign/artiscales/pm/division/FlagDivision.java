package fr.ign.artiscales.pm.division;

import fr.ign.artiscales.pm.parcelFunction.MarkParcelAttributeFromPosition;
import fr.ign.artiscales.pm.parcelFunction.ParcelSchema;
import fr.ign.artiscales.pm.parcelFunction.ParcelState;
import fr.ign.artiscales.tools.geoToolsFunctions.Attribute;
import fr.ign.artiscales.tools.geoToolsFunctions.Schemas;
import fr.ign.artiscales.tools.geoToolsFunctions.vectors.Geom;
import fr.ign.artiscales.tools.geoToolsFunctions.vectors.collec.CollecMgmt;
import fr.ign.artiscales.tools.geoToolsFunctions.vectors.collec.CollecTransform;
import fr.ign.artiscales.tools.geoToolsFunctions.vectors.geom.Lines;
import fr.ign.artiscales.tools.geoToolsFunctions.vectors.geom.Polygons;
import fr.ign.artiscales.tools.geometryGeneration.CityGeneration;
import fr.ign.artiscales.tools.parameter.ProfileUrbanFabric;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.geotools.data.DataStore;
import org.geotools.data.DataUtilities;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.factory.CommonFactoryFinder;
import org.geotools.feature.DefaultFeatureCollection;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.MultiLineString;
import org.locationtech.jts.geom.Polygon;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.filter.FilterFactory2;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Re-implementation of block decomposition into parcels with flag shape. The algorithm is an adaptation from :
 * <p>
 * Vanegas, C. A., Kelly, T., Weber, B., Halatsch, J., Aliaga, D. G., Müller, P., May 2012. Procedural generation of parcels in urban modeling. Comp. Graph. Forum 31 (2pt3).
 * <p>
 * As input a polygon that represents the zone to decompose. For each step the decomposition is processed according to the OBBBlockDecomposition algorithm If one of the parcels do
 * not have access to the road, a L parcel is created. A road is added on the other parcel according to 1/ the shortest path to the public road 2/ if this shortest path does not
 * intersect an existing building. The width of the road is parametrable in the attributes : roadWidth
 * <p>
 * It is a recursive method, the decomposition is stop when a stop criteria is reached either the area or roadwidthaccess is below a given threshold
 *
 * @author Mickael Brasebin
 */
public class FlagDivision extends Division {

    /**
     * We remove <i>silver</i> parts that may have a too small area inferior to 25
     */
    public static double TOO_SMALL_PARCEL_AREA = 25;

//    public static void main(String[] args) throws Exception {
//        long start = System.currentTimeMillis();
//        File rootFolder = new File("src/main/resources/TestScenario/");
//        setDEBUG(false);
//        // Input 1/ the input parcels to split
//        File parcelFile = new File("/tmp/dad.gpkg");
//        // Input 2 : the buildings that mustn't intersects the allowed roads (facultatif)
//        File buildingFile = new File(rootFolder, "InputData/building.gpkg");
//        // Input 4 (facultative) : a road GeoPackage (it can be used to check road access if this is better than characterizing road as an absence of parcel)
//        File roadFile = new File(rootFolder, "InputData/road.gpkg");
//        ProfileUrbanFabric profile = ProfileUrbanFabric.convertJSONtoProfile(new File(rootFolder, "profileUrbanFabric/mediumHouse.json"));
//        DefaultFeatureCollection result = new DefaultFeatureCollection();
//        DataStore ds = CollecMgmt.getDataStore(parcelFile);
//        SimpleFeatureCollection parcel = MarkParcelAttributeFromPosition.markUnBuiltParcel(ds.getFeatureSource(ds.getTypeNames()[0]).getFeatures(), buildingFile);
//        CollecMgmt.exportSFC(parcel, new File("/tmp/parcelMarked"));
//        result.addAll(doFlagDivision(parcel, buildingFile, roadFile, profile, CityGeneration.createUrbanBlock(parcel), null));
//
//        ds.dispose();
//        CollecMgmt.exportSFC(result, new File("/tmp/out.gpkg"));
//        System.out.println("time : " + (System.currentTimeMillis() - start));
//    }

    /**
     * Main way to access to the flag parcel split algorithm for a collection. Parcels must be marked in order to be simulated.
     *
     * @param inputCollection feature to decompose
     * @param buildingFile    building that could stop the creation of a driveway
     * @param roadFile        complementary roads (as line and not as parcel void)
     * @param profile         Description of the urban fabric profile planed to be simulated on this zone.
     * @return collection of cut parcels
     * @throws IOException reading buildingFile
     */
    public static SimpleFeatureCollection doFlagDivision(SimpleFeatureCollection inputCollection, File buildingFile, File roadFile, ProfileUrbanFabric profile) throws IOException {
        return doFlagDivision(inputCollection, buildingFile, roadFile, profile, CityGeneration.createUrbanBlock(inputCollection), null);
    }

    /**
     * Main way to access to the flag parcel split algorithm for a collection. Parcels must be marked in order to be simulated.
     *
     * @param inputCollection feature to decompose
     * @param buildingFile    building that could stop the creation of a driveway
     * @param roadFile        complementary roads (as line and not as parcel void)
     * @param profile         Description of the urban fabric profile planed to be simulated on this zone.
     * @param block           SimpleFeatureCollection containing the morphological block. Can be generated with the {@link fr.ign.artiscales.tools.geometryGeneration.CityGeneration#createUrbanBlock(SimpleFeatureCollection)} method.
     * @param exclusionZone   Exclude a zone that won't be considered as a potential road connection. Useful to represent border of the parcel plan. Can be null.
     * @return collection of cut parcels
     * @throws IOException reading buildingFile
     */
    public static SimpleFeatureCollection doFlagDivision(SimpleFeatureCollection inputCollection, File buildingFile, File roadFile, ProfileUrbanFabric profile, SimpleFeatureCollection block, Geometry exclusionZone) throws IOException {
        return doFlagDivision(inputCollection, buildingFile, roadFile, profile.getHarmonyCoeff(), profile.getIrregularityCoeff(), profile.getMaximalArea(), profile.getMinimalWidthContactRoad(), profile.getDrivewayWidth(), block, exclusionZone);
    }

    /**
     * Main way to access to the flag parcel split algorithm for a collection. Parcels must be marked in order to be simulated.
     *
     * @param inputCollection         feature to decompose
     * @param buildingFile            building that could stop the creation of a driveway
     * @param roadFile                complementary roads (as line and not as parcel void)
     * @param harmony                 OBB algorithm parameter to allow the rotation of the bounding box if the ratio between the box's length and width is higher to this coefficient. Must be between 0 and 1.
     * @param irregularityCoeff       irregularity into parcel division
     * @param maximalArea             threshold of parcel area above which the OBB algorithm stops to decompose parcels*
     * @param exclusionZone           Exclude a zone that won't be considered as a potential road connection. Useful to represent border of the parcel plan. Can be null.
     * @param block                   SimpleFeatureCollection containing the morphological block. Can be generated with the {@link fr.ign.artiscales.tools.geometryGeneration.CityGeneration#createUrbanBlock(SimpleFeatureCollection)} method.
     * @param drivewayWidth           Width of the driveway to connect a parcel through another parcel to the road
     * @param minimalWidthContactRoad Width of the contact between parcel and road under which the parcel won't be cut anymore
     * @return collection of cut parcels
     * @throws IOException reading buildingFile
     */
    public static SimpleFeatureCollection doFlagDivision(SimpleFeatureCollection inputCollection, File buildingFile, File roadFile, double harmony, double irregularityCoeff, double maximalArea, double minimalWidthContactRoad, double drivewayWidth, SimpleFeatureCollection block, Geometry exclusionZone) throws IOException {
        if (!CollecMgmt.isCollecContainsAttribute(inputCollection, MarkParcelAttributeFromPosition.getMarkFieldName()) || MarkParcelAttributeFromPosition.isNoParcelMarked(inputCollection)) {
            if (isDEBUG())
                System.out.println("doFlagDivision: no parcel marked");
            return inputCollection;
        }
        FilterFactory2 ff = CommonFactoryFinder.getFilterFactory2();
        DefaultFeatureCollection result = new DefaultFeatureCollection();
        // import related collections (if they exists)
        DataStore buildingDS = null;
        if (buildingFile != null)
            buildingDS = CollecMgmt.getDataStore(buildingFile);
        DataStore roadDS = null;
        if (roadFile != null)
            roadDS = CollecMgmt.getDataStore(roadFile);
        try (SimpleFeatureIterator it = inputCollection.features()) {
            while (it.hasNext()) {
                SimpleFeature feat = it.next();
                result.addAll(doFlagDivision(feat,
                        roadFile != null ? DataUtilities.collection(roadDS.getFeatureSource(roadDS.getTypeNames()[0]).getFeatures()) : null,
                        buildingFile != null ? DataUtilities.collection(buildingDS.getFeatureSource(buildingDS.getTypeNames()[0]).getFeatures()) : null,
                        harmony, irregularityCoeff, maximalArea, minimalWidthContactRoad, drivewayWidth,
                        CollecTransform.fromPolygonSFCtoListRingLines(block.subCollection(ff.bbox(ff.property(inputCollection.getSchema().getGeometryDescriptor().getLocalName()), inputCollection.getBounds()))), exclusionZone));
            }
        }
        if (buildingFile != null)
            buildingDS.dispose();
        if (roadFile != null)
            roadDS.dispose();
        return result;
    }


    /**
     * Flag split a single parcel. Main way to access to the flag parcel split algorithm.
     * Add a field <i>SIMULATED</i> with the 1 value if simulation has been done, 0 otherwise.
     *
     * @param sf                      input parcel to flag split
     * @param building                buildings that could stop the creation of a driveway
     * @param harmony                 OBB algorithm parameter to allow the rotation of the bounding box if the ratio between the box's length and width is higher to this coefficient. Must be between 0 and 1.
     * @param irregularityCoeff       irregularity into parcel division
     * @param maximalArea             threshold of parcel area above which the OBB algorithm stops to decompose parcels
     * @param road                    complementary roads (as line and not as parcel void)
     * @param exclusionZone           Exclude a zone that won't be considered as a potential road connection. Useful to represent border of the parcel plan. Can be null.
     * @param extLines                Lines representing the morphological block.
     * @param drivewayWidth           lenght of the driveway to connect a parcel through another parcel to the road
     * @param minimalWidthContactRoad Width of the contact between parcel and road under which the parcel won't be cut anymore
     * @return the flag cut parcel if possible, the input parcel otherwise. Schema of the returned parcel is the same as input + a simulated field.
     */
    public static SimpleFeatureCollection doFlagDivision(SimpleFeature sf, SimpleFeatureCollection road, SimpleFeatureCollection building, double harmony, double irregularityCoeff,
                                                         double maximalArea, double minimalWidthContactRoad, double drivewayWidth, List<LineString> extLines, Geometry exclusionZone) {
        DefaultFeatureCollection result = new DefaultFeatureCollection();
        Polygon p = Polygons.getPolygon((Geometry) sf.getDefaultGeometry());
        // shrink collections
        building = CollecTransform.selectIntersection(building, p, 30);
        road = CollecTransform.selectIntersection(road, p, 30);
        extLines = (List<LineString>) CollecTransform.selectIntersection(extLines, p, 30);
        // test end condition
        SimpleFeatureBuilder builder = ParcelSchema.addSimulatedField(sf.getFeatureType());
        if ((sf.getAttribute(MarkParcelAttributeFromPosition.getMarkFieldName()) != null && !sf.getAttribute(MarkParcelAttributeFromPosition.getMarkFieldName()).equals(1))
                || endCondition(p.getArea(), frontSideWidth(p, road, extLines), maximalArea, minimalWidthContactRoad)) {
            Schemas.setFieldsToSFB(builder, sf);
            builder.set("SIMULATED", 0);
            result.add(builder.buildFeature(Attribute.makeUniqueId()));
            return result;
        }
        if (isDEBUG())
            System.out.println("flagSplit parcel " + sf);
        // Determination of splitting polygon (it is a splitting line in the article)
        // Split into polygon
        List<Polygon> splitPolygon = OBBDivision.split(p, OBBDivision.computeSplittingPolygon(p, extLines, true, harmony, irregularityCoeff, 0.0, 0, 0.0, 0, 0));
        // If a parcel has no road access, there is a probability to make a flag split
        if (hasRoadAccess(splitPolygon, road, extLines, exclusionZone)) {
            Pair<List<Polygon>, List<Polygon>> polGeneratedParcel = flagParcel(splitPolygon, road, building, extLines, exclusionZone, drivewayWidth);
            // We check if both parcels have road access, if false we abort the decomposition (that may be useless here...)
            for (Polygon pol1 : polGeneratedParcel.getLeft())
                for (Polygon pol2 : polGeneratedParcel.getRight())
                    if (!hasRoadAccess(pol2, road, extLines, exclusionZone) || !hasRoadAccess(pol1, road, extLines, exclusionZone)) {
                        Schemas.setFieldsToSFB(builder, sf);
                        builder.set("SIMULATED", 0);
                        result.add(builder.buildFeature(Attribute.makeUniqueId()));
                        return result;
                    }
            splitPolygon = polGeneratedParcel.getLeft(); //we'll continue to split this part
            for (Polygon pol : polGeneratedParcel.getRight()) { // we put this part into the result collection
                Schemas.setFieldsToSFB(builder, sf);
                builder.set(sf.getFeatureType().getGeometryDescriptor().getLocalName(), pol);
                builder.set("SIMULATED", 1);
                result.add(builder.buildFeature(Attribute.makeUniqueId()));
            }
        }
        // All split polygons are split and results added to the output
        for (Polygon pol : splitPolygon) {
            Schemas.setFieldsToSFB(builder, sf);
            builder.set(sf.getFeatureType().getGeometryDescriptor().getLocalName(), pol);
            result.addAll(doFlagDivision(builder.buildFeature(Attribute.makeUniqueId()), road, building, harmony, irregularityCoeff, maximalArea, minimalWidthContactRoad, drivewayWidth, extLines, exclusionZone));
        }
        return result;
    }

    /**
     * Generate flag parcels: check if parcels have access to road and if not, try to generate a road throughout other parcels.
     *
     * @param splitPolygon  Polygon to split
     * @param road          input road
     * @param building      input building
     * @param ext           outside block
     * @param exclusionZone void to not be counted as a road
     * @param drivewayWidth width of the simulated driveway
     * @return The output is a pair:<ul>
     * <li> the left part contains parcel with an initial road access and may continue to be decomposed</li>
     * <li> the right part contains parcel with added road access</li>
     * </ul>
     */
    private static Pair<List<Polygon>, List<Polygon>> flagParcel(List<Polygon> splitPolygon, SimpleFeatureCollection road, SimpleFeatureCollection building, List<LineString> ext, Geometry exclusionZone, double drivewayWidth) {
        List<Polygon> right = new ArrayList<>();

        // We get the two geometries with and without road access
        List<Polygon> lPolygonWithRoadAccess = splitPolygon.stream().filter(x -> hasRoadAccess(x, road, ext, exclusionZone)).collect(Collectors.toList());
        List<Polygon> lPolygonWithNoRoadAccess = splitPolygon.stream().filter(x -> !hasRoadAccess(x, road, ext, exclusionZone)).collect(Collectors.toList());

        bouclepoly:
        for (Polygon currentPoly : lPolygonWithNoRoadAccess) {
            List<Pair<MultiLineString, Polygon>> listMap = generateCandidateForCreatingRoad(currentPoly, lPolygonWithRoadAccess, ext, road);
            // We order the proposition according to the length (we will try at first to build the road on the shortest side
            listMap.sort(Comparator.comparingDouble(o -> o.getKey().getLength()));
            loopSide:
            for (Pair<MultiLineString, Polygon> side : listMap) {
                // The geometry road
                Geometry roadGeom = side.getKey().buffer(drivewayWidth);
                Polygon polygon = side.getValue();
                // The road intersects a building on the property, we do not keep it
                if (building != null && !building.isEmpty() && !CollecTransform.selectIntersection(building, roadGeom).isEmpty())
                    continue;
                try {
                    // The first geometry is the polygon with road access and a remove of the geometry
                    Geometry geomPol1 = Geom.safeDifference(polygon, roadGeom);
                    Geometry geomPol2 = Geom.safeIntersection(Geom.safeUnion(currentPoly, roadGeom), Geom.safeUnion(currentPoly, polygon));
                    // It might be a multi polygon so we remove the small area <
                    List<Polygon> lPolygonsOut1 = Polygons.getPolygons(geomPol1);
                    lPolygonsOut1 = lPolygonsOut1.stream().filter(x -> x.getArea() > TOO_SMALL_PARCEL_AREA).collect(Collectors.toList());

                    List<Polygon> lPolygonsOut2 = Polygons.getPolygons(geomPol2);
                    lPolygonsOut2 = lPolygonsOut2.stream().filter(x -> x.getArea() > TOO_SMALL_PARCEL_AREA).collect(Collectors.toList());

                    // We check if there is a road access for all, if not we abort
                    for (Polygon pol : lPolygonsOut1)
                        if (!hasRoadAccess(pol, road, ext, exclusionZone))
                            continue loopSide;
                    for (Polygon pol : lPolygonsOut2)
                        if (!hasRoadAccess(pol, road, ext, exclusionZone))
                            continue loopSide;

                    // We directly add the result from polygon 2 to the results
                    right.addAll(lPolygonsOut2);

                    // We update the geometry of the first polygon
                    lPolygonWithRoadAccess.remove(side.getValue());
                    lPolygonWithRoadAccess.addAll(lPolygonsOut1);

                    // We go to the next polygon
                    continue bouclepoly;
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            // We have added nothing if we are here, we kept the initial polygon
            right.add(currentPoly);
        }
        // We add the polygon with road access
        return new ImmutablePair<>(new ArrayList<>(lPolygonWithRoadAccess), right);
    }

    /**
     * Generate a list of candidate for creating roads. The pair is composed of a linestring that may be used to generate the road and the parcel on which it may be built.
     * todo replace buffers and contains with dwithin ?!
     *
     * @param currentPoly           Polygon to link to the road
     * @param lPolygonWithRoadAcces other polygons surrounding the #currentPoly
     * @return A list of pairs:<ul>
     * <li> the left part contains the planned road</li>
     * <li> the right part contains the rest of the polygon</li>
     * </ul>
     */
    private static List<Pair<MultiLineString, Polygon>> generateCandidateForCreatingRoad(Polygon currentPoly, List<Polygon> lPolygonWithRoadAcces, List<LineString> ext, SimpleFeatureCollection roads) {
        // A buffer to get the sides of the polygon with no road access
        Geometry curretPolyBuffered = currentPoly.buffer(0.1);
        Geometry extBuffered = Lines.getListLineStringAsMultiLS(ext, currentPoly.getFactory()).buffer(0.1);
        // A map to know to which polygon belongs a potential road
        List<Pair<MultiLineString, Polygon>> listMap = new ArrayList<>();
        for (Polygon polyWithRoadAcces : lPolygonWithRoadAcces) {
            if (!polyWithRoadAcces.intersects(curretPolyBuffered)) // work with the concerned polygon
                continue;
            // We list the segments of the polygon with road access and keep the ones that does not intersect the buffer of new no-road-access polygon and the
            // Then regroup the lines according to their connectivity. We finally add elements to list the correspondence between pears
            Lines.regroupLineStrings(Lines.getSegments(polyWithRoadAcces.getExteriorRing()).stream()
                            .filter(x -> !curretPolyBuffered.contains(x) && !extBuffered.contains(x) && !isRoadPolygonIntersectsLine(roads, x))
                            .collect(Collectors.toList()),
                    currentPoly.getFactory()).forEach(x -> listMap.add(new ImmutablePair<>(x, polyWithRoadAcces)));
        }
        return listMap;
    }

    /**
     * End condition : either the area or the contact width to road is below a threshold (0 value is not allowed for contact width to road, as opposite to straight OBB).
     * Goes to the {@link OBBDivision} class.
     *
     * @param area                    Area of the current parcel
     * @param frontSideWidth          minimal width of contact between road and parcel
     * @param maximalArea             threshold of parcel area above which the OBB algorithm stops to decompose parcels
     * @param minimalWidthContactRoad Width of the parcel under which the parcel won't be anymore cut
     * @return true if the algorithm must stop
     */
    private static boolean endCondition(double area, double frontSideWidth, double maximalArea, double minimalWidthContactRoad) {
        if (frontSideWidth == 0.0)
            return true;
        return OBBDivision.endCondition(area, frontSideWidth, maximalArea, minimalWidthContactRoad);
    }

    /**
     * Determine the width of the parcel on road
     *
     * @param p input {@link Polygon}
     * @return the length of contact between parcel and road
     */
    private static double frontSideWidth(Polygon p, SimpleFeatureCollection roads, List<LineString> ext) {
        return ParcelState.getParcelFrontSideWidth(p, roads, ext);
    }

    private static boolean hasRoadAccess(List<Polygon> splitPolygon, SimpleFeatureCollection road, List<LineString> extLines, Geometry exclusionZone) {
        return splitPolygon.stream().anyMatch(x -> !hasRoadAccess(x, road, extLines, exclusionZone));
    }

    /**
     * Indicate if the given polygon is near the {@link org.locationtech.jts.geom.Polygon#getExteriorRing()} shell of a given Polygon object. This object is the ext parameter,
     * or if not set, the bounds of the input polygon.
     * If no roads have been found and a road Geopacakge has been set, we look if a road Geopacakge has been set and if the given road is nearby
     *
     * @param poly parcel's polygon
     */
    private static boolean hasRoadAccess(Polygon poly, SimpleFeatureCollection roads, List<LineString> ext, Geometry exclusionZone) {
        return ParcelState.isParcelHasRoadAccess(poly, roads, poly.getFactory().createMultiLineString(ext.toArray(new LineString[0])), exclusionZone);
    }

    static boolean isRoadPolygonIntersectsLine(SimpleFeatureCollection roads, LineString ls) {
        return roads != null && Geom.unionGeom(ParcelState.getRoadPolygon(roads)).contains(ls);
    }

}
