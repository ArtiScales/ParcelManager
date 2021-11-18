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
//        File parcelFile = new File(rootFolder, "InputData/parcel.gpkg");
//        // Input 2 : the buildings that mustn't intersects the allowed roads (facultatif)
//        File inputBuildingFile = new File(rootFolder, "InputData/building.gpkg");
//        // Input 4 (facultative) : a road GeoPackage (it can be used to check road access if this is better than characterizing road as an absence of parcel)
//        File inputRoad = new File(rootFolder, "InputData/road.gpkg");
//        ProfileUrbanFabric profile = ProfileUrbanFabric.convertJSONtoProfile(new File(rootFolder, "profileUrbanFabric/mediumHouse.json"));
//        DefaultFeatureCollection result = new DefaultFeatureCollection();
//        DataStore ds = CollecMgmt.getDataStore(parcelFile);
//        SimpleFeatureCollection parcel = MarkParcelAttributeFromPosition.markRandomParcels(ds.getFeatureSource(ds.getTypeNames()[0]).getFeatures(), 15, false);
//        CollecMgmt.exportSFC(parcel, new File("/tmp/parcelMarked"));
//        result.addAll(doFlagDivision(parcel, inputBuildingFile, inputRoad, profile, CityGeneration.createUrbanBlock(parcel), null));
//
//        ds.dispose();
//        CollecMgmt.exportSFC(result, new File(rootFolder, "out.gpkg"));
//        System.out.println("time : " + (System.currentTimeMillis() - start));
//    }


    public static SimpleFeatureCollection doFlagDivision(SimpleFeatureCollection inputCollection, File buildingFile, File roadFile, ProfileUrbanFabric profile) throws IOException {
        return doFlagDivision(inputCollection, buildingFile, roadFile, profile, CityGeneration.createUrbanBlock(inputCollection), null);
    }

    public static SimpleFeatureCollection doFlagDivision(SimpleFeatureCollection inputCollection, File buildingFile, File roadFile, ProfileUrbanFabric profile, SimpleFeatureCollection block, Geometry exclusionZone) throws IOException {
        return doFlagDivision(inputCollection, buildingFile, roadFile, profile.getHarmonyCoeff(), profile.getNoise(), profile.getMaximalArea(), profile.getMinimalWidthContactRoad(), profile.getLenDriveway(), block, exclusionZone);
    }

    /**
     * Main way to access to the flag parcel split algorithm for a collection. Parcels must be marked in order to be simulated.
     *
     * @param inputCollection feature to decompose
     * @param buildingFile    building that could stop the creation of a driveway
     * @param roadFile        complementary roads (as line and not as parcel void)
     * @return collection of cut parcels
     * @throws IOException reading buildingFile
     */
    public static SimpleFeatureCollection doFlagDivision(SimpleFeatureCollection inputCollection, File buildingFile, File roadFile, double harmony, double noise, double maximalArea, double minimalWidthContact, double lenDriveway, SimpleFeatureCollection block, Geometry exclusionZone) throws IOException {
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
                        roadFile != null ? DataUtilities.collection(CollecTransform.selectIntersection(roadDS.getFeatureSource(roadDS.getTypeNames()[0]).getFeatures(), ((Geometry) feat.getDefaultGeometry()).buffer(10))) : null,
                        buildingFile != null ? DataUtilities.collection(CollecTransform.selectIntersection(buildingDS.getFeatureSource(buildingDS.getTypeNames()[0]).getFeatures(), ((Geometry) feat.getDefaultGeometry()).buffer(10))) : null,
                        harmony, noise, maximalArea, minimalWidthContact, lenDriveway, CollecTransform.fromPolygonSFCtoListRingLines(block.subCollection(ff.bbox(
                                ff.property(inputCollection.getSchema().getGeometryDescriptor().getLocalName()), inputCollection.getBounds()))), exclusionZone));
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
     * Add a fied <i>SIMULATED</i> and
     *
     * @param sf           input feature
     * @param harmonyCoeff OBB algorithm parameter to check if the bounding box could be rotated accordingly to the ratio between the box's length and width
     * @param noise        irregularity into parcel shape
     * @return the flag cut parcel if possible, the input parcel otherwise. Schema of the returned parcel is the same as input + a simulated field.
     */
    public static SimpleFeatureCollection doFlagDivision(SimpleFeature sf, SimpleFeatureCollection road, SimpleFeatureCollection building, double harmonyCoeff, double noise,
                                                         double maximalArea, double minimalWidthContactRoad, double drivewayWidth, List<LineString> extLines, Geometry exclusionZone) {
        if (isDEBUG())
            System.out.println("flagSplit parcel " + sf);
        DefaultFeatureCollection result = new DefaultFeatureCollection();
        Polygon p = Polygons.getPolygon((Geometry) sf.getDefaultGeometry());
        SimpleFeatureBuilder builder = ParcelSchema.addField(sf.getFeatureType(), "SIMULATED");
        building = CollecTransform.selectIntersection(building, ((Geometry) sf.getDefaultGeometry()).buffer(20));
        SimpleFeatureCollection r = CollecTransform.selectIntersection(road, ((Geometry) sf.getDefaultGeometry()).buffer(20));
        // End test condition
        if ((sf.getAttribute(MarkParcelAttributeFromPosition.getMarkFieldName()) != null && !sf.getAttribute(MarkParcelAttributeFromPosition.getMarkFieldName()).equals(1))
                || endCondition(((Geometry) sf.getDefaultGeometry()).getArea(), frontSideWidth(p, r, extLines), maximalArea, minimalWidthContactRoad)) {
            Schemas.setFieldsToSFB(builder, sf);
            builder.set("SIMULATED", 0);
            result.add(builder.buildFeature(Attribute.makeUniqueId()));
            return result;
        }
        // Determination of splitting polygon (it is a splitting line in the article)
        List<Polygon> splittingPolygon = OBBDivision.computeSplittingPolygon(p, extLines, true, harmonyCoeff, noise, 0.0, 0, 0.0, 0, 0);
        // Split into polygon
        List<Polygon> splitPolygon = OBBDivision.split(p, splittingPolygon);
        // If a parcel has no road access, there is a probability to make a flag split
        if (splitPolygon.stream().anyMatch(x -> !hasRoadAccess(x, r, extLines, exclusionZone))) {
            Pair<List<Polygon>, List<Polygon>> polGeneratedParcel = flagParcel(splitPolygon, r, building, extLines, exclusionZone, drivewayWidth);
            // We check if both parcels have road access, if false we abort the decomposition (that may be useless here...)
            for (Polygon pol1 : polGeneratedParcel.getLeft())
                for (Polygon pol2 : polGeneratedParcel.getRight())
                    if (!hasRoadAccess(pol2, r, extLines, exclusionZone) || !hasRoadAccess(pol1, r, extLines, exclusionZone)) {
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
            result.addAll(doFlagDivision(builder.buildFeature(Attribute.makeUniqueId()), r, building, harmonyCoeff, noise, maximalArea, minimalWidthContactRoad, drivewayWidth, extLines, exclusionZone));
        }
        return result;
    }

    /**
     * Generate flag parcels: check if parcels have access to road and if not, try to generate a road throughout other parcels.
     *
     * @param splitPolygon
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
     *
     * @param currentPoly           Polygon to link to the road
     * @param lPolygonWithRoadAcces other polygons surrounding the #currentPoly
     * @return A list of pairs:<ul>
     * <li> the left part contains </li>
     * <li> the right part contains </li>
     * </ul>
     */
    private static List<Pair<MultiLineString, Polygon>> generateCandidateForCreatingRoad(Polygon currentPoly, List<Polygon> lPolygonWithRoadAcces, List<LineString> ext, SimpleFeatureCollection roads) {
        // A buffer to get the sides of the polygon with no road access
        Geometry buffer = currentPoly.buffer(0.1);
        // A map to know to which polygon belongs a potential road
        List<Pair<MultiLineString, Polygon>> listMap = new ArrayList<>();
        for (Polygon polyWithRoadAcces : lPolygonWithRoadAcces) {
            if (!polyWithRoadAcces.intersects(buffer))
                continue;
            // We list the segments of the polygon with road access and keep the ones that does not intersect the buffer of new no-road-access polygon and the
            // Then regroup the lines according to their connectivity. We finally add elements to list the correspondance between pears
            Lines.regroupLineStrings(Lines.getSegments(polyWithRoadAcces.getExteriorRing()).stream().filter(
                            x -> (!buffer.contains(x)) && !Lines.getListLineStringAsMultiLS(ext, currentPoly.getFactory()).buffer(0.1).contains(x) && !isRoadPolygonIntersectsLine(roads, x))
                    .collect(Collectors.toList()), currentPoly.getFactory()).stream().forEach(x -> listMap.add(new ImmutablePair<>(x, polyWithRoadAcces)));

        }
        return listMap;
    }

    /**
     * End condition : either the area or the contact width to road is below a threshold (0 value is not allowed for contact width to road, as opposite to straight OBB).
     * Goes to the {@link OBBDivision} class.
     *
     * @param area           Area of the current parcel
     * @param frontSideWidth minimal width of contact between road and parcel
     * @return true if the algorithm must stop
     */
    private static boolean endCondition(double area, double frontSideWidth, double maximalArea, double minimalWidth) {
        if (frontSideWidth == 0.0)
            return true;
        return OBBDivision.endCondition(area, frontSideWidth, maximalArea, minimalWidth);
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

    /**
     * Indicate if the given polygon is near the {@link org.locationtech.jts.geom.Polygon#getExteriorRing()} shell of a given Polygon object. This object is the ext parameter,
     * or if not set, the bounds of the input polygon.
     * If no roads have been found and a road Geopacakge has been set, we look if a road Geopacakge has been set and if the given road is nearby
     *
     * @param poly parcel's polygon
     */
    private static boolean hasRoadAccess(Polygon poly, SimpleFeatureCollection roads, List<LineString> ext, Geometry exclusionZone) {
        return ParcelState.isParcelHasRoadAccess(poly, roads, poly.getFactory().createMultiLineString(ext.toArray(new LineString[ext.size()])), exclusionZone);
    }

    static boolean isRoadPolygonIntersectsLine(SimpleFeatureCollection roads, LineString ls) {
        return roads != null && Geom.unionGeom(ParcelState.getRoadPolygon(roads)).contains(ls);
    }

}
