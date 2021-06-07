package fr.ign.artiscales.pm.decomposition;

import fr.ign.artiscales.pm.parcelFunction.MarkParcelAttributeFromPosition;
import fr.ign.artiscales.pm.parcelFunction.ParcelState;
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
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.MultiLineString;
import org.locationtech.jts.geom.Polygon;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.filter.FilterFactory2;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
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
//TODO rename to FlagDivision
public class FlagParcelDecomposition {
    /*	 public static void main(String[] args) throws Exception {
         /////////////////////////
         //////// try the generateFlagSplitedParcels method
            /////////////////////////
            long start = System.currentTimeMillis();
            File rootFolder = new File("src/main/resources/TestScenario/");

            // Input 1/ the input parcels to split
            File parcelFile = new File(rootFolder, "parcel.gpkg");
            // Input 2 : the buildings that mustn't intersects the allowed roads (facultatif)
             File inputBuildingFile = new File(rootFolder, "building.gpkg");
             // Input 4 (facultative) : a road Geopacakge (it can be used to check road access
             // if this is better than characerizing road as an absence of parcel)
             File inputRoad = new File(rootFolder, "road.gpkg");
    //		 File zoningFile = new File(rootFolder, "zoning.gpkg");
             ProfileUrbanFabric profile = ProfileUrbanFabric.convertJSONtoProfile(new File(rootFolder, "profileUrbanFabric/detachedHouse.json"));
             DefaultFeatureCollection result = new DefaultFeatureCollection();
             DataStore ds = CollecMgmt.getDataStore(parcelFile);
             generateFlagSplitedParcels(ds.getFeatureSource(ds.getTypeNames()[0]).getFeatures(), inputBuildingFile, inputRoad, profile);
             generateFlagSplitedParcels(ds.getFeatureSource(ds.getTypeNames()[0]).getFeatures(), inputBuildingFile, null, profile);
             generateFlagSplitedParcels(ds.getFeatureSource(ds.getTypeNames()[0]).getFeatures(), null, null, profile);
             ds.dispose();
             CollecMgmt.exportSFC(result, new File(rootFolder, "out.gpkg"));
             System.out.println("time : " + (System.currentTimeMillis() - start));
         }*/
    /**
     * We remove <i>silver</i> parts that may have a too small area inferior to 25
     */
    public static double TOO_SMALL_PARCEL_AREA = 25;
    final private double maximalArea, maximalWidth, drivewayWidth;
    private final Polygon polygonInit;
    private SimpleFeatureCollection buildings;
    private SimpleFeatureCollection roads;
    /**
     * This line represents the exterior of an urban island (it serves to determine if a parcel has road access)
     */
    private List<LineString> ext = null;
    private Geometry exclusionZone = null;

    /**
     * Flag decomposition algorithm method without buildings, roads and exclusion geometry
     *
     * @param p             the initial polygon to decompose
     * @param buildings     the buildings that will constraint the possibility of adding a road
     * @param maximalArea   the maximalArea for a parcel
     * @param maximalWidth  the maximal width
     * @param drivewayWidth the width of the created driveway
     */
    public FlagParcelDecomposition(Polygon p, SimpleFeatureCollection buildings, double maximalArea, double maximalWidth, double drivewayWidth) {
        super();
        this.maximalArea = maximalArea;
        this.maximalWidth = maximalWidth;
        this.polygonInit = p;
        this.buildings = buildings;
        this.drivewayWidth = drivewayWidth;
    }

    /**
     * Constructor of a FlagParcelDecomposition method without buildings and roads
     *
     * @param parcelGeom     the initial polygon to decompose
     * @param maximalArea    the maximalArea for a parcel
     * @param maximalWidth   the maximal width
     * @param drivewayWidth  the width of driveways
     * @param islandExterior the exterior of this island to assess road access
     * @param exclusionZone  a zone to find roads from empty parcels area
     */
    public FlagParcelDecomposition(Polygon parcelGeom, Double maximalArea, Double maximalWidth, Double drivewayWidth, List<LineString> islandExterior, Geometry exclusionZone) {
        this.maximalArea = maximalArea;
        this.maximalWidth = maximalWidth;
        this.polygonInit = parcelGeom;
        this.drivewayWidth = drivewayWidth;
        this.setExt(islandExterior);
        this.exclusionZone = exclusionZone;
    }

    /**
     * Flag decomposition algorithm without road and exclusion geometry
     *
     * @param p              the initial polygon to decompose
     * @param buildings      the buildings that will constraint the possibility of adding a road
     * @param maximalArea    the maximalArea for a parcel
     * @param maximalWidth   the maximal width
     * @param drivewayWidth  the width of driveways
     * @param islandExterior the exterior of this island to assess road access
     * @param exclusionZone  a zone to find roads from empty parcels area
     */
    public FlagParcelDecomposition(Polygon p, SimpleFeatureCollection buildings, double maximalArea, double maximalWidth, double drivewayWidth, List<LineString> islandExterior, Geometry exclusionZone) {
        super();
        this.maximalArea = maximalArea;
        this.maximalWidth = maximalWidth;
        this.polygonInit = p;
        this.buildings = buildings;
        this.drivewayWidth = drivewayWidth;
        this.setExt(islandExterior);
        this.exclusionZone = exclusionZone;
    }

    /**
     * Flag decomposition algorithm without exclusion geometry
     *
     * @param p              the initial polygon to decompose
     * @param buildings      the buildings that will constraint the possibility of adding a road
     * @param roads          road segment could be used for detecting road contact (can be null)
     * @param maximalArea    the maximalArea for a parcel
     * @param maximalWidth   the maximal width
     * @param drivewayWidth  the width of driveways
     * @param islandExterior the exterior of this island to assess road access
     */
    public FlagParcelDecomposition(Polygon p, SimpleFeatureCollection buildings, SimpleFeatureCollection roads, double maximalArea, double maximalWidth, double drivewayWidth, List<LineString> islandExterior) {
        super();
        this.maximalArea = maximalArea;
        this.maximalWidth = maximalWidth;
        this.polygonInit = p;
        this.buildings = buildings;
        this.roads = roads;
        this.drivewayWidth = drivewayWidth;
        this.setExt(islandExterior);
    }


    /**
     * Flag decomposition object. Have to be created before any flag division
     *
     * @param p              the initial polygon to decompose
     * @param buildings      the buildings that will constraint the possibility of adding a road
     * @param roads          road segment could be used for detecting road contact (can be null)
     * @param maximalArea    the maximalArea for a parcel
     * @param maximalWidth   the maximal width
     * @param drivewayWidth  the width of driveways
     * @param islandExterior the exterior of this island to assess road access
     * @param exclusionZone  a zone to find roads from empty parcels area
     */
    public FlagParcelDecomposition(Polygon p, SimpleFeatureCollection buildings, SimpleFeatureCollection roads, double maximalArea, double maximalWidth, double drivewayWidth, List<LineString> islandExterior, Geometry exclusionZone) {
        super();
        this.maximalArea = maximalArea;
        this.maximalWidth = maximalWidth;
        this.polygonInit = p;
        this.buildings = buildings;
        this.roads = roads;
        this.drivewayWidth = drivewayWidth;
        this.setExt(islandExterior);
        this.exclusionZone = exclusionZone;
    }

    /**
     * Main way to access to the flag parcel split algorithm.
     *
     * @param parcelSFC    parcels to cut
     * @param buildingFile building that could stop the creation of a driveway
     * @param roadFile     complementary roads (as line and not as parcel void)
     * @param profile      type of {@link ProfileUrbanFabric} to simulate
     * @return collection of cut parcels
     * @throws IOException reading buildingFile
     */
    public static SimpleFeatureCollection generateFlagSplitedParcels(SimpleFeatureCollection parcelSFC, File buildingFile, File roadFile, ProfileUrbanFabric profile) throws IOException {
        FilterFactory2 ff = CommonFactoryFinder.getFilterFactory2();
        SimpleFeatureCollection block = CityGeneration.createUrbanBlock(parcelSFC, true);
        DefaultFeatureCollection result = new DefaultFeatureCollection();

        // import related collections (if they exists)
        boolean hasBuilding = false;
        boolean hasRoad = false;
        DataStore buildingDS = null;
        if (buildingFile != null) {
            hasBuilding = true;
            buildingDS = CollecMgmt.getDataStore(buildingFile);
        }
        DataStore roadDS = null;
        if (roadFile != null) {
            hasRoad = true;
            roadDS = CollecMgmt.getDataStore(roadFile);
        }

        try (SimpleFeatureIterator it = parcelSFC.features()) {
            while (it.hasNext()) {
                SimpleFeature feat = it.next();
                if (feat.getAttribute(MarkParcelAttributeFromPosition.getMarkFieldName()) != null
                        && (int) feat.getAttribute(MarkParcelAttributeFromPosition.getMarkFieldName()) == 1) {
                    if (hasBuilding && hasRoad)
                        result.addAll(generateFlagSplitedParcels(feat, CollecTransform.fromPolygonSFCtoListRingLines(block.subCollection(ff.bbox(
                                ff.property(feat.getFeatureType().getGeometryDescriptor().getLocalName()), feat.getBounds()))), profile.getHarmonyCoeff(), profile.getNoise(),
                                DataUtilities.collection(CollecTransform.selectIntersection(buildingDS.getFeatureSource(buildingDS.getTypeNames()[0]).getFeatures(), ((Geometry) feat.getDefaultGeometry()).buffer(10))),
                                DataUtilities.collection(CollecTransform.selectIntersection(roadDS.getFeatureSource(roadDS.getTypeNames()[0]).getFeatures(), ((Geometry) feat.getDefaultGeometry()).buffer(10))),
                                profile.getMaximalArea(), profile.getMinimalWidthContactRoad(), profile.getLenDriveway(), null));
                    else if (hasBuilding)
                        result.addAll(generateFlagSplitedParcels(feat, CollecTransform.fromPolygonSFCtoListRingLines(block.subCollection(ff.bbox(
                                ff.property(feat.getFeatureType().getGeometryDescriptor().getLocalName()), feat.getBounds()))), profile.getHarmonyCoeff(), profile.getNoise(),
                                DataUtilities.collection(CollecTransform.selectIntersection(buildingDS.getFeatureSource(buildingDS.getTypeNames()[0]).getFeatures(), ((Geometry) feat.getDefaultGeometry()).buffer(10))),
                                profile.getMaximalArea(), profile.getMinimalWidthContactRoad(), profile.getLenDriveway(), null));
                    else
                        result.addAll(generateFlagSplitedParcels(feat, CollecTransform.fromPolygonSFCtoListRingLines(block.subCollection(ff.bbox(
                                ff.property(feat.getFeatureType().getGeometryDescriptor().getLocalName()), feat.getBounds()))), profile.getHarmonyCoeff(),
                                profile.getNoise(), profile.getMaximalArea(), profile.getMinimalWidthContactRoad(), profile.getLenDriveway(), null));
                } else
                    result.add(feat);
            }
        } catch (Exception problem) {
            problem.printStackTrace();
        }
        if (hasBuilding)
            buildingDS.dispose();
        if (hasRoad)
            roadDS.dispose();
        return result;
    }

    /**
     * Flag parcel split with no buildings and no road.
     *
     * @param feat                    parcel to cut
     * @param extLines                block contour
     * @param harmonyCoeff            possibility for parcel to be elongated or not
     * @param noise                   randomness in the parcel creation process
     * @param maximalAreaSplitParcel  area under which a parcel won't be cut anymore
     * @param maximalWidthSplitParcel with between road and parcel under which parcel won't be cut anymore
     * @param lenDriveway             length of the driveway to simulate
     * @param exclusionZone           Zone to consider as not existing
     * @return collection of cut parcels
     */
    public static SimpleFeatureCollection generateFlagSplitedParcels(SimpleFeature feat, List<LineString> extLines, double harmonyCoeff, double noise,
                                                                     Double maximalAreaSplitParcel, Double maximalWidthSplitParcel, Double lenDriveway, Geometry exclusionZone) {
        return Geom.geomsToCollec((new FlagParcelDecomposition(Polygons.getPolygon((Geometry) feat.getDefaultGeometry()),
                maximalAreaSplitParcel, maximalWidthSplitParcel, lenDriveway, extLines, exclusionZone)).decompParcel(harmonyCoeff, noise), Schemas.getBasicSchemaMultiPolygon("geom"));
    }

    /**
     * Flag parcel split version with buildings and without road.
     *
     * @param feat                    parcel to cut
     * @param extLines                block contour
     * @param harmonyCoeff            possibility for parcel to be elongated or not
     * @param noise                   randomness in the parcel creation process
     * @param buildingSFC             building that could stop the creation of a driveway
     * @param maximalAreaSplitParcel  area under which a parcel won't be cut anymore
     * @param maximalWidthSplitParcel with between road and parcel under which parcel won't be cut anymore
     * @param lenDriveway             length of the driveway to simulate
     * @param exclusionZone           Zone to consider as not existing
     * @return collection of cut parcels
     */
    public static SimpleFeatureCollection generateFlagSplitedParcels(SimpleFeature feat, List<LineString> extLines, double harmonyCoeff, double noise,
                                                                     SimpleFeatureCollection buildingSFC, Double maximalAreaSplitParcel, Double maximalWidthSplitParcel, Double lenDriveway, Geometry exclusionZone) {
        return Geom.geomsToCollec((new FlagParcelDecomposition(Polygons.getPolygon((Geometry) feat.getDefaultGeometry()), buildingSFC,
                maximalAreaSplitParcel, maximalWidthSplitParcel, lenDriveway, extLines, exclusionZone)).decompParcel(harmonyCoeff, noise), Schemas.getBasicSchemaMultiPolygon("geom"));
    }

    /**
     * Flag parcel split version with buildings and road.
     *
     * @param feat                    parcel to cut
     * @param extLines                block contour
     * @param harmonyCoeff            possibility for parcel to be elongated or not
     * @param noise                   randomness in the parcel creation process
     * @param buildingSFC             building that could stop the creation of a driveway
     * @param roadSFC                 complementary roads (as line and not as parcel void)
     * @param maximalAreaSplitParcel  area under which a parcel won't be cut anymore
     * @param maximalWidthSplitParcel with between road and parcel under which parcel won't be cut anymore
     * @param lenDriveway             length of the driveway to simulate
     * @param exclusionZone           Zone to consider as not existing
     * @return collection of cut parcels
     */
    public static SimpleFeatureCollection generateFlagSplitedParcels(SimpleFeature feat, List<LineString> extLines, double harmonyCoeff, double noise, SimpleFeatureCollection buildingSFC,
                                                                     SimpleFeatureCollection roadSFC, Double maximalAreaSplitParcel, Double maximalWidthSplitParcel, Double lenDriveway, Geometry exclusionZone) {
        return Geom.geomsToCollec((new FlagParcelDecomposition(Polygons.getPolygon((Geometry) feat.getDefaultGeometry()), buildingSFC, roadSFC,
                maximalAreaSplitParcel, maximalWidthSplitParcel, lenDriveway, extLines, exclusionZone)).decompParcel(harmonyCoeff, noise), Schemas.getBasicSchemaMultiPolygon("geom"));
    }

    static boolean isRoadPolygonIntersectsLine(SimpleFeatureCollection roads, LineString ls) {
        return roads != null && Geom.unionGeom(ParcelState.getRoadPolygon(roads)).contains(ls);
    }

    /**
     * The decomposition method
     *
     * @return List of parcels
     */
    List<Polygon> decompParcel(double harmonyCoeff, double noise) {
        return decompParcel(this.polygonInit, harmonyCoeff, noise);
    }

    /**
     * The core algorithm
     *
     * @param p            input polygon
     * @param harmonyCoeff OBB algorithm parameter to check if the bounding box could be rotated accordingely to the ratio between the box's length and width
     * @param noise        irregularity into parcel shape
     * @return the flag cut parcel if possible. The input parcel otherwise
     */
    List<Polygon> decompParcel(Polygon p, double harmonyCoeff, double noise) {
        // End test condition
        if (this.endCondition(p.getArea(), this.frontSideWidth(p)))
            return Collections.singletonList(p);
        // Determination of splitting polygon (it is a splitting line in the article)
        List<Polygon> splittingPolygon = OBBBlockDecomposition.computeSplittingPolygon(p, this.getExt(), true, harmonyCoeff, noise, 0.0, 0, 0.0, 0, 0);
        // Split into polygon
        List<Polygon> splitPolygon = OBBBlockDecomposition.split(p, splittingPolygon);
        // If a parcel has no road access, there is a probability to make a flag split
        List<Polygon> result = new ArrayList<>();
        if (splitPolygon.stream().anyMatch(x -> !hasRoadAccess(x))) {
            Pair<List<Polygon>, List<Polygon>> polGeneratedParcel = generateFlagParcel(splitPolygon);
            // We check if both parcels have road access, if false we abort the decomposition (that may be useless here...)
            for (Polygon pol1 : polGeneratedParcel.getLeft())
                for (Polygon pol2 : polGeneratedParcel.getRight())
                    if (!hasRoadAccess(pol2) || !hasRoadAccess(pol1))
                        return Collections.singletonList(p);
            splitPolygon = polGeneratedParcel.getLeft();
            result.addAll(polGeneratedParcel.getRight());
        }
        // All split polygons are split and results added to the output
        for (Polygon pol : splitPolygon)
            result.addAll(decompParcel(pol, harmonyCoeff, noise));
        return result;
    }

    /**
     * Generate flag parcels: check if parcels have access to road and if not, try to generate a road throught other parcels
     *
     * @param splittedPolygon list of polygon split with the OBB method
     * @return The output is a pair of two elements:<ul>
     * <li> the left one contains parcel with an initial road access and may continue to be decomposed</li>
     * <li> the right one contains parcel with added road access</li>
     * </ul>
     */
    Pair<List<Polygon>, List<Polygon>> generateFlagParcel(List<Polygon> splittedPolygon) {
        List<Polygon> right = new ArrayList<>();

        // We get the two geometries with and without road access
        List<Polygon> lPolygonWithRoadAccess = splittedPolygon.stream().filter(this::hasRoadAccess).collect(Collectors.toList());
        List<Polygon> lPolygonWithNoRoadAccess = splittedPolygon.stream().filter(x -> !hasRoadAccess(x)).collect(Collectors.toList());

        bouclepoly:
        for (Polygon currentPoly : lPolygonWithNoRoadAccess) {
            List<Pair<MultiLineString, Polygon>> listMap = generateCandidateForCreatingRoad(currentPoly, lPolygonWithRoadAccess);
            // We order the proposition according to the length (we will try at first to build the road on the shortest side
            listMap.sort(Comparator.comparingDouble(o -> o.getKey().getLength()));
            loopSide:
            for (Pair<MultiLineString, Polygon> side : listMap) {
                // The geometry road
                Geometry road = side.getKey().buffer(this.drivewayWidth);
                Polygon polygon = side.getValue();
                // The road intersects a building on the property, we do not keep it
                if (this.buildings != null && !this.buildings.isEmpty() && !CollecTransform.selectIntersection(CollecTransform.selectIntersection(this.buildings, this.polygonInit.buffer(-0.5)), road).isEmpty())
                    continue;
                try {
                    // The first geometry is the polygon with road access and a remove of the geometry
                    Geometry geomPol1 = Geom.safeDifference(polygon, road);
                    Geometry geomPol2 = Geom.safeIntersection(Geom.safeUnion(currentPoly, road), Geom.safeUnion(currentPoly, polygon));
                    // It might be a multi polygon so we remove the small area <
                    List<Polygon> lPolygonsOut1 = Polygons.getPolygons(geomPol1);
                    lPolygonsOut1 = lPolygonsOut1.stream().filter(x -> x.getArea() > TOO_SMALL_PARCEL_AREA).collect(Collectors.toList());

                    List<Polygon> lPolygonsOut2 = Polygons.getPolygons(geomPol2);
                    lPolygonsOut2 = lPolygonsOut2.stream().filter(x -> x.getArea() > TOO_SMALL_PARCEL_AREA).collect(Collectors.toList());

                    // We check if there is a road acces for all, if not we abort
                    for (Polygon pol : lPolygonsOut1)
                        if (!hasRoadAccess(pol))
                            continue loopSide;
                    for (Polygon pol : lPolygonsOut2)
                        if (!hasRoadAccess(pol))
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

    private List<MultiLineString> regroupLineStrings(List<LineString> lineStrings) {
        List<MultiLineString> curvesOutput = new ArrayList<>();
        while (!lineStrings.isEmpty()) {
            LineString currentLineString = lineStrings.remove(0);
            List<LineString> currentMultiCurve = new ArrayList<>();
            currentMultiCurve.add(currentLineString);
            Geometry buffer = currentLineString.buffer(0.1);
            for (int i = 0; i < lineStrings.size(); i++) {
                if (buffer.intersects(lineStrings.get(i))) {
                    // Adding line in MultiCurve
                    currentMultiCurve.add(lineStrings.remove(i));
                    i = -1;
                    // Updating the buffer
                    buffer = getListLSAsMultiLS(currentMultiCurve).buffer(0.1);
                }
            }
            curvesOutput.add(getListLSAsMultiLS(currentMultiCurve));
        }
        return curvesOutput;
    }

    /**
     * Generate a list of candidate for creating roads. The pair is composed of a linestring that may be used to generate the road and the parcel on which it may be built
     *
     * @param currentPoly
     * @param lPolygonWithRoadAcces
     * @return
     */
    private List<Pair<MultiLineString, Polygon>> generateCandidateForCreatingRoad(Polygon currentPoly, List<Polygon> lPolygonWithRoadAcces) {
        // A buffer to get the sides of the polygon with no road access
        Geometry buffer = currentPoly.buffer(0.1);
        // A map to know to which polygon belongs a potential road
        List<Pair<MultiLineString, Polygon>> listMap = new ArrayList<>();
        for (Polygon polyWithRoadAcces : lPolygonWithRoadAcces) {
            if (!polyWithRoadAcces.intersects(buffer))
                continue;
            // We list the segments of the polygon with road access and keep the ones that does not intersect the buffer of new no-road-access polygon and the
            // Then regroup the lines according to their connectivity. We finnaly add elements to list the correspondance between pears
            this.regroupLineStrings(Lines.getSegments(polyWithRoadAcces.getExteriorRing()).stream().filter(
                    x -> (!buffer.contains(x)) && !getListLSAsMultiLS(this.getExt()).buffer(0.1).contains(x) && !isRoadPolygonIntersectsLine(roads, x))
                    .collect(Collectors.toList())).stream().forEach(x -> listMap.add(new ImmutablePair<>(x, polyWithRoadAcces)));
        }
        return listMap;
    }

    /**
     * End condition : either the area or the contact width to road is below a threshold (0 value is not allowed for contact width to road, as opposite to straight OBB).
     * Goes to the {@link OBBBlockDecomposition} class.
     *
     * @param area           Area of the current parcel
     * @param frontSideWidth width of contact between road and parcel
     * @return true if the algorithm must stop
     */
    private boolean endCondition(double area, double frontSideWidth) {
        if (frontSideWidth == 0.0)
            return true;
        return OBBBlockDecomposition.endCondition(area, frontSideWidth, maximalArea, maximalWidth);
    }

    /**
     * Determine the width of the parcel on road
     *
     * @param p input {@link Polygon}
     * @return the length of contact between parcel and road
     */
    private double frontSideWidth(Polygon p) {
        return ParcelState.getParcelFrontSideWidth(p, roads, ext);
    }

    /**
     * Indicate if the given polygon is near the {@link org.locationtech.jts.geom.Polygon#getExteriorRing() shell} of a given Polygon object. This object is the islandExterior
     * argument out of {@link #FlagParcelDecomposition(Polygon, SimpleFeatureCollection, double, double, double, List, Geometry)} the FlagParcelDecomposition constructor or if not
     * set, the bounds of the {@link #polygonInit initial polygon}.
     * <p>
     * If no roads have been found and a road Geopacakge has been set, we look if a road Geopacakge has been set and if the given road is nearby
     *
     * @param poly parcel's polygon
     */
    private boolean hasRoadAccess(Polygon poly) {
        return ParcelState.isParcelHasRoadAccess(poly, roads, poly.getFactory().createMultiLineString(ext.toArray(new LineString[ext.size()])), exclusionZone);
    }

    private MultiLineString getListLSAsMultiLS(List<LineString> list) {
        return Lines.getListLineStringAsMultiLS(list, this.polygonInit.getFactory());
    }

    private List<LineString> getExt() {
        if (ext == null) {
            generateExt();
        }
        return ext;
    }

    private void setExt(List<LineString> ext) {
        this.ext = ext;
    }

    /**
     * We generate an exterior with the studied polygon itself if no exterior block has been set
     */
    private void generateExt() {
        // FIXME this code doesn't change a thing? If it would (with the use of setExt()), the road could be generated to the exterior of the polygon, possibly leading to nowhere?
        this.polygonInit.getFactory().createMultiLineString(new LineString[]{this.polygonInit.getExteriorRing()});
    }
}
