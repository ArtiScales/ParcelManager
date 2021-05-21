package fr.ign.artiscales.pm.decomposition;

import fr.ign.artiscales.pm.parcelFunction.MarkParcelAttributeFromPosition;
import fr.ign.artiscales.pm.parcelFunction.ParcelState;
import fr.ign.artiscales.tools.geoToolsFunctions.Attribute;
import fr.ign.artiscales.tools.geoToolsFunctions.vectors.Geom;
import fr.ign.artiscales.tools.geoToolsFunctions.vectors.MinimalBoundingRectangle;
import fr.ign.artiscales.tools.geoToolsFunctions.vectors.Tree;
import fr.ign.artiscales.tools.geoToolsFunctions.vectors.collec.CollecMgmt;
import fr.ign.artiscales.tools.geoToolsFunctions.vectors.collec.CollecTransform;
import fr.ign.artiscales.tools.geoToolsFunctions.vectors.geom.Lines;
import fr.ign.artiscales.tools.geoToolsFunctions.vectors.geom.Polygons;
import fr.ign.artiscales.tools.geometryGeneration.CityGeneration;
import fr.ign.artiscales.tools.parameter.ProfileUrbanFabric;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.geotools.data.DataStore;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.feature.DefaultFeatureCollection;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Polygon;
import org.opengis.feature.simple.SimpleFeature;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Oriented Bounding Box algorithm implementation with methods to generate road network
 */
public class OBBBlockDecomposition {

//	public static void main(String[] args) throws Exception {
//		ShapefileDataStore sds = new ShapefileDataStore((new File("src/main/resources/smallTest/parcel.gpkg")).toURI().toURL());
//		SimpleFeatureCollection toSplit = sds.getFeatureSource().getFeatures();
//		ShapefileDataStore sdsRoad = new ShapefileDataStore((new File("src/main/resources/smallTest/road.gpkg")).toURI().toURL());
//		SimpleFeatureCollection roads = sdsRoad.getFeatureSource().getFeatures();
//		List<LineString> lines = Collec.fromPolygonSFCtoListRingLines(CityGeneration.createUrbanIslet(toSplit));
//		Collec.exportSFC(splitParcels(toSplit, roads, 800, 15, 0, 0, lines, 10, 6, 20, false, 3), new File("/tmp/normal"));
//		Collec.exportSFC(splitParcels(toSplit, roads, 800, 15, 0.5, 0, lines, 10, 6, 20, true, 3), new File("/tmp/forced"));
//		sds.dispose();
//		sdsRoad.dispose();
//	}

    /**
     * Split the parcels into sub parcels. The parcel that are going to be cut must have a field matching the {@link MarkParcelAttributeFromPosition#getMarkFieldName()} field or
     * "SPLIT" by default with the value of 1.
     * <p>
     * Overload to split a single parcel.
     *
     * @param toSplit                         {@link SimpleFeatureCollection} of parcels
     * @param maximalArea                     Area of the parcel under which the parcel won't be anymore cut
     * @param maximalWidth                    Width of the parcel under which the parcel won't be anymore cut
     * @param harmonyCoeff                    intensity of the forcing of a parcel to be connected with a road
     * @param extBlock                        outside of the parcels (representing road or public space)
     * @param streetWidth                     With of the street composing the street network
     * @param decompositionLevelWithoutStreet Number of last iteration row for which no street network is generated
     * @param forceStreetAccess               Is the polygon should be turned in order to assure the connection with the road ? Also regarding the <i>harmony coeff</i>. Most of cases, it's yes
     * @return a collection of subdivised parcels
     */
    public static SimpleFeatureCollection splitParcels(SimpleFeature toSplit, double maximalArea, double maximalWidth, double harmonyCoeff,
                                                       double noise, List<LineString> extBlock, double streetWidth, boolean forceStreetAccess, int decompositionLevelWithoutStreet) {
        return splitParcel(toSplit, null, maximalArea, maximalWidth, harmonyCoeff, noise, extBlock, streetWidth, 999, streetWidth,
                forceStreetAccess, decompositionLevelWithoutStreet);
    }

    /**
     * Split the parcels into sub parcels. The parcel that are going to be cut must have a field matching the {@link MarkParcelAttributeFromPosition#getMarkFieldName()} field or
     * "SPLIT" by default with the value of 1.
     * <p>
     * Overload to split a single parcel.
     *
     * @param featToSplit                     parcel. Must have been marked in order to be simulated
     * @param maximalArea                     Area of the parcel under which the parcel won't be anymore cut
     * @param maximalWidth                    Width of the parcel under which the parcel won't be anymore cut
     * @param harmonyCoeff                    intensity of the forcing of a parcel to be connected with a road
     * @param extBlock                        outside of the parcels (representing road or public space)
     * @param smallStreetWidth                With of the street composing the street network
     * @param decompositionLevelWithoutStreet Number of last iteration row for which no street network is generated
     * @param forceStreetAccess               Is the polygon should be turned in order to assure the connection with the road ? Also regarding the <i>harmony coeff</i>. Most of cases, it's yes
     * @param largeStreetLevel                Level of decomposition in which large streets are generated
     * @param largeStreetWidth                Width of the large streets
     * @param noise                           irregularity into parcel shape
     * @param roads                           Road layer (can be null)
     * @return a collection of subdivised parcels
     */
    public static SimpleFeatureCollection splitParcel(SimpleFeature featToSplit, SimpleFeatureCollection roads, double maximalArea, double maximalWidth,
                                                      double harmonyCoeff, double noise, List<LineString> extBlock, double smallStreetWidth, int largeStreetLevel, double largeStreetWidth,
                                                      boolean forceStreetAccess, int decompositionLevelWithoutStreet) {
        DefaultFeatureCollection result = new DefaultFeatureCollection();
        SimpleFeatureBuilder builder = new SimpleFeatureBuilder(featToSplit.getFeatureType());
        Object mark = featToSplit.getAttribute(MarkParcelAttributeFromPosition.getMarkFieldName());
        // // if the parcel is not to be split, we add it on the final result and continue to iterate through the parcels.
        if (mark == null || (int) mark != 1) {
            result.add(featToSplit);
        } else {
            Polygon polygon = Polygons.getPolygon((Geometry) featToSplit.getDefaultGeometry());
            DescriptiveStatistics dS = new DescriptiveStatistics();
            OBBBlockDecomposition.decompose(polygon, extBlock,
                    (roads != null && !roads.isEmpty()) ? CollecTransform.selectIntersection(roads, (Geometry) featToSplit.getDefaultGeometry()) : null,
                    maximalArea, maximalWidth, noise, harmonyCoeff, smallStreetWidth, largeStreetLevel, largeStreetWidth, forceStreetAccess, 0,
                    decompositionLevelWithoutStreet).stream().forEach(c -> dS.addValue(c.getValue()));
            int decompositionLevelWithRoad = (int) dS.getPercentile(50) - decompositionLevelWithoutStreet;
            int decompositionLevelWithLargeRoad = (int) dS.getPercentile(50) - largeStreetLevel;
            OBBBlockDecomposition.decompose(polygon, extBlock,
                    (roads != null && !roads.isEmpty()) ? CollecTransform.selectIntersection(roads, (Geometry) featToSplit.getDefaultGeometry()) : null,
                    maximalArea, maximalWidth, noise, harmonyCoeff, smallStreetWidth, decompositionLevelWithLargeRoad, largeStreetWidth,
                    forceStreetAccess, decompositionLevelWithRoad, decompositionLevelWithoutStreet).childrenStream().forEach(p -> {
                SimpleFeature newFeature = builder.buildFeature(Attribute.makeUniqueId());
                newFeature.setDefaultGeometry(p.getKey());
                result.add(newFeature);
            });
        }
        return result;
    }

    /**
     * Split the parcels into sub parcels. The parcel that are going to be cut must have a field matching the {@link MarkParcelAttributeFromPosition#getMarkFieldName()} field or
     * "SPLIT" by default with the value of 1.
     *
     * @param toSplit           {@link SimpleFeatureCollection} of parcels
     * @param roadFile          road file layer (can be null)
     * @param profile           chosen {@link ProfileUrbanFabric}
     * @param forceStreetAccess Is the polygon should be turned in order to assure the connection with the road ? Also regarding the <i>harmony coeff</i>. Most of cases, it's yes
     * @return a collection of subdivised parcels
     * @throws IOException reading geo files
     */
    public static SimpleFeatureCollection splitParcels(SimpleFeatureCollection toSplit, File roadFile, ProfileUrbanFabric profile, boolean forceStreetAccess) throws IOException {
        SimpleFeatureCollection result;
        if (roadFile == null) {
            result = splitParcels(toSplit, null,
                    profile.getMaximalArea(), profile.getMinimalWidthContactRoad(), profile.getHarmonyCoeff(), profile.getNoise(), CollecTransform.fromPolygonSFCtoListRingLines(CityGeneration.createUrbanBlock(toSplit)),
                    profile.getStreetWidth(), profile.getLargeStreetLevel(), profile.getLargeStreetWidth(), forceStreetAccess,
                    profile.getDecompositionLevelWithoutStreet());
        } else {
            DataStore roadDS = CollecMgmt.getDataStore(roadFile);
            result = splitParcels(toSplit, roadDS.getFeatureSource(roadDS.getTypeNames()[0]).getFeatures(),
                    profile.getMaximalArea(), profile.getMinimalWidthContactRoad(), profile.getHarmonyCoeff(), profile.getNoise(), CollecTransform.fromPolygonSFCtoListRingLines(CityGeneration.createUrbanBlock(toSplit)),
                    profile.getStreetWidth(), profile.getLargeStreetLevel(), profile.getLargeStreetWidth(), forceStreetAccess,
                    profile.getDecompositionLevelWithoutStreet());
            roadDS.dispose();
        }
        return result;
    }

    /**
     * Split the parcels into sub parcels. The parcel that are going to be cut must have a field matching the {@link MarkParcelAttributeFromPosition#getMarkFieldName()} field or
     * "SPLIT" by default with the value of 1.
     *
     * @param toSplit                         {@link SimpleFeatureCollection} of parcels. Must have been marked in order to be simulated
     * @param roads                           Road layer (can be null)
     * @param maximalArea                     Area of the parcel under which the parcel won't be anymore cut
     * @param maximalWidth                    Width of the parcel under which the parcel won't be anymore cut
     * @param harmonyCoeff                    intensity of the forcing of a parcel to be connected with a road
     * @param noise                           irregularity into parcel shape
     * @param extBlock                        outside of the parcels (representing road or public space)
     * @param smallStreetWidth                Width of the small streets
     * @param largeStreetLevel                Level of decomposition in which large streets are generated
     * @param largeStreetWidth                Width of the large streets
     * @param forceStreetAccess               Is the polygon should be turned in order to assure the connection with the road ? Also regarding the <i>harmony coeff</i>. Most of cases, it's yes
     * @param decompositionLevelWithoutStreet Number of last iteration row for which no street network is generated
     * @return a collection of subdivised parcels
     */
    public static SimpleFeatureCollection splitParcels(SimpleFeatureCollection toSplit, SimpleFeatureCollection roads, double maximalArea, double maximalWidth, double harmonyCoeff, double noise, List<LineString> extBlock,
                                                       double smallStreetWidth, int largeStreetLevel, double largeStreetWidth, boolean forceStreetAccess, int decompositionLevelWithoutStreet) {
        DefaultFeatureCollection result = new DefaultFeatureCollection();
        try (SimpleFeatureIterator featIt = toSplit.features()) {
            while (featIt.hasNext()) {
                result.addAll(splitParcel(featIt.next(), roads, maximalArea, maximalWidth, harmonyCoeff, noise, extBlock,
                        smallStreetWidth, largeStreetLevel, largeStreetWidth, forceStreetAccess, decompositionLevelWithoutStreet));
            }
        }
        return result;
    }

    /**
     * End condition : either the area is below a threshold or width to road (which is ultimately allowed to be 0)
     *
     * @param area           Area of the current parcel
     * @param frontSideWidth width of contact between road and parcel
     * @param maximalArea    Area threshold
     * @param maximalWidth   threshold of width of contact between road and parcel
     * @return true if the algorithm must stop
     */
    static boolean endCondition(double area, double frontSideWidth, double maximalArea, double maximalWidth) {
        return (area <= maximalArea) || ((frontSideWidth <= maximalWidth) && (frontSideWidth != 0.0));
    }

    /**
     * Computed the splitting polygons composed by two boxes determined from the oriented bounding boxes split from a line at its middle.
     *
     * @param pol                        The input polygon
     * @param ext                        outside of the parcels (representing road or public space)
     * @param shortDirectionSplit        It is split by the short edges or by the long edge.
     * @param harmonyCoeff               intensity of the forcing of a parcel to be connected with a road
     * @param noise                      Irregularity into parcel shape
     * @param smallRoadWidth             Width of the small streets
     * @param largeRoadLevel             Level of decomposition in which large streets are generated
     * @param largeRoadWidth             Width of the large streets
     * @param decompositionLevelWithRoad
     * @param decompositionLevel
     * @return A list of split polygons
     */
    static List<Polygon> computeSplittingPolygon(Polygon pol, List<LineString> ext, boolean shortDirectionSplit, double harmonyCoeff, double noise, double smallRoadWidth, int largeRoadLevel, double largeRoadWidth,
                                                 int decompositionLevelWithRoad, int decompositionLevel) {
//	  public static List<Polygon> computeSplittingPolygon(Polygon pol, List<LineString> ext, boolean shortDirectionSplit, double noise, double smallRoadWidth, int largeRoadLevel, double largeRoadWidth,
//		      int decompositionLevelWithRoad, int decompositionLevel) {
        if (pol.getArea() < 1.0)
            return Collections.emptyList();
        // Determination of the bounding box
        Polygon oBB = MinimalBoundingRectangle.getRectangle(pol);
        Coordinate[] coordinates = oBB.getCoordinates();
        double dist1 = coordinates[0].distance(coordinates[1]);
        double dist2 = coordinates[1].distance(coordinates[2]);

        boolean keepCoordinateOrder = dist1 > dist2;
        if (!shortDirectionSplit && dist1 > dist2 && dist2 / dist1 > harmonyCoeff)
            keepCoordinateOrder = !keepCoordinateOrder;
        else if (!shortDirectionSplit && dist1 < dist2 && dist1 / dist2 > harmonyCoeff)
            keepCoordinateOrder = !keepCoordinateOrder;

        Coordinate p0 = keepCoordinateOrder ? coordinates[0] : coordinates[1];
        Coordinate p1 = keepCoordinateOrder ? coordinates[1] : coordinates[2];
        Coordinate p2 = keepCoordinateOrder ? coordinates[2] : coordinates[3];
        Coordinate p3 = keepCoordinateOrder ? coordinates[3] : coordinates[0];
        double width = Math.min(dist1, dist2);
        // The noise value is determined by noise parameters and parcel width (to avoid lines that go out of parcel)
        double noiseTemp = Math.min(width / 3, noise);
        // X and Y move of the centroid
        double alpha = 0.5 + (0.5 - Math.random()) * noiseTemp;
        if (decompositionLevel < decompositionLevelWithRoad) {
            // Creation of road
            double roadWidth = smallRoadWidth;
            if (decompositionLevel < largeRoadLevel) {
                roadWidth = largeRoadWidth;
            }
            double roadAlpha = roadWidth / (p0.distance(p1) * 2);
            Coordinate p4 = new Coordinate(p0.x + (alpha - roadAlpha) * (p1.x - p0.x), p0.y + (alpha - roadAlpha) * (p1.y - p0.y));
            Coordinate p5 = new Coordinate(p3.x + (alpha - roadAlpha) * (p2.x - p3.x), p3.y + (alpha - roadAlpha) * (p2.y - p3.y));
            Coordinate p6 = new Coordinate(p0.x + (alpha + roadAlpha) * (p1.x - p0.x), p0.y + (alpha + roadAlpha) * (p1.y - p0.y));
            Coordinate p7 = new Coordinate(p3.x + (alpha + roadAlpha) * (p2.x - p3.x), p3.y + (alpha + roadAlpha) * (p2.y - p3.y));
            try {
                ext.add(pol.getFactory().createLineString(new Coordinate[]{p4, p5, p7, p6, p4}));
            } catch (NullPointerException np) {
                ext = new ArrayList<>();
                ext.add(pol.getFactory().createLineString(new Coordinate[]{p4, p5, p7, p6, p4}));
            }
            return Arrays.asList(pol.getFactory().createPolygon(new Coordinate[]{p0, p4, p5, p3, p0}), pol.getFactory().createPolygon(new Coordinate[]{p6, p1, p2, p7, p6}));
        }
        Coordinate p4 = new Coordinate(p0.x + alpha * (p1.x - p0.x), p0.y + alpha * (p1.y - p0.y));
        Coordinate p5 = new Coordinate(p3.x + alpha * (p2.x - p3.x), p3.y + alpha * (p2.y - p3.y));
        return Arrays.asList(pol.getFactory().createPolygon(new Coordinate[]{p0, p4, p5, p3, p0}), pol.getFactory().createPolygon(new Coordinate[]{p4, p1, p2, p5, p4}));
    }

    /**
     * Split the input polygon by another.
     *
     * @param poly1
     * @param poly2
     * @return The splitting of Polygon1 with Polygon2
     */
    static List<Polygon> split(Polygon poly1, Polygon poly2) {
        Geometry intersection = Geom.scaledGeometryReductionIntersection(Arrays.asList(poly1, poly2));
        if (intersection instanceof Polygon)
            return Collections.singletonList((Polygon) intersection);
        List<Polygon> res = new ArrayList<>(intersection.getNumGeometries());
        for (int i = 0; i < intersection.getNumGeometries(); i++) {
            Geometry geom = intersection.getGeometryN(i);
            if (geom instanceof Polygon)
                res.add((Polygon) geom);
        }
        return res;
    }

    /**
     * Split the input polygons by a list of polygons.
     *
     * @param poly
     * @param polygons
     * @return A list of the split input polygons
     */
    static List<Polygon> split(Polygon poly, List<Polygon> polygons) {
        return polygons.stream().flatMap(p -> split(poly, p).stream()).collect(Collectors.toList());
    }

    /**
     * Decompose method. Overload to use no specific street {@link SimpleFeatureCollection}.
     */
    static Tree<Pair<Polygon, Integer>> decompose(Polygon polygon, List<LineString> ext, double maximalArea, double maximalWidth, double noise,
                                                  double epsilon, double streetWidth, boolean forceStreetAccess, int decompositionLevelWithStreet, int decompositionLevel) {
        return decompose(polygon, ext, null, maximalArea, maximalWidth, noise, epsilon, streetWidth, 999, streetWidth, forceStreetAccess,
                decompositionLevelWithStreet, decompositionLevel);
    }

    /**
     * Main method for OBB decomposition
     *
     * @param polygon                      {@link Polygon} of the parcel to be cut
     * @param ext                          outside of the parcels (representing road or public space)
     * @param roads                        Road layer (can be null)
     * @param maximalArea                  Area of the parcel under which the parcel won't be anymore cut
     * @param maximalWidth                 Width of the parcel under which the parcel won't be anymore cut
     * @param noise                        irregularity into parcel shape
     * @param harmonyCoeff                 intensity of the forcing of a parcel to be connected with a road
     * @param smallStreetWidth             Width of the small streets
     * @param largeStreetLevel             Level of decomposition in which large streets are generated
     * @param largeStreetWidth             Width of the large streets
     * @param forceStreetAccess            Is the polygon should be turned in order to assure the connection with the road ? Also regarding the <i>harmony coeff</i>. Most of cases, it's yes
     * @param decompositionLevelWithStreet
     * @param decompositionLevel
     * @return A tree with the polygon decomposition
     */
    static Tree<Pair<Polygon, Integer>> decompose(Polygon polygon, List<LineString> ext, SimpleFeatureCollection roads, double maximalArea, double maximalWidth, double noise,
                                                  double harmonyCoeff, double smallStreetWidth, int largeStreetLevel, double largeStreetWidth, boolean forceStreetAccess, int decompositionLevelWithStreet,
                                                  int decompositionLevel) {
        double area = polygon.getArea();
        double frontSideWidth = ParcelState.getParcelFrontSideWidth(polygon, roads, ext);
        if (endCondition(area, frontSideWidth, maximalArea, maximalWidth))
            return new Tree<>(new ImmutablePair<>(polygon, decompositionLevel));
        // Determination of splitting polygon (it is a splitting line in the article)
        List<Polygon> splittingPolygon = computeSplittingPolygon(polygon, ext, true, harmonyCoeff, noise, smallStreetWidth, largeStreetLevel, largeStreetWidth, decompositionLevelWithStreet, decompositionLevel);
        // Split into polygon
        List<Polygon> splitPolygons = split(polygon, splittingPolygon);
        // If a parcel has no road access, there is a probability to make a perpendicular split
        // Probability to make a perpendicular split if no road access or ratio between larger and smaller size of OBB higher than Epsilon
        if ((forceStreetAccess && ((!ParcelState.isParcelHasRoadAccess(splitPolygons.get(0), null, Lines.getListLineStringAsMultiLS(ext, new GeometryFactory()))
                || !ParcelState.isParcelHasRoadAccess(splitPolygons.get(1), null, Lines.getListLineStringAsMultiLS(ext, new GeometryFactory())))))) {
            // Same steps but with different splitting geometries
            splittingPolygon = computeSplittingPolygon(polygon, ext, false, harmonyCoeff, noise, smallStreetWidth, largeStreetLevel, largeStreetWidth, decompositionLevelWithStreet, decompositionLevel);
            splitPolygons = split(polygon, splittingPolygon);
        }
        // All split polygons are split and results added to the output
        return new Tree<>(new ImmutablePair<>(polygon, decompositionLevel), splitPolygons.stream().map(pol -> decompose(pol, ext, roads, maximalArea, maximalWidth, noise, harmonyCoeff, smallStreetWidth, largeStreetLevel, largeStreetWidth, forceStreetAccess, decompositionLevelWithStreet, decompositionLevel + 1)).collect(Collectors.toList()));
    }
}
