package decomposition;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.math3.random.MersenneTwister;
import org.apache.commons.math3.random.RandomGenerator;
import org.geotools.data.shapefile.ShapefileDataStore;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.feature.SchemaException;
import org.locationtech.jts.algorithm.Orientation;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.util.LineStringExtracter;
import org.locationtech.jts.operation.overlay.snap.GeometrySnapper;
import org.locationtech.jts.operation.polygonize.Polygonizer;
import org.opengis.feature.simple.SimpleFeature;

import decomposition.analysis.FindObjectInDirection;
import graph.Face;
import graph.HalfEdge;
import graph.Node;
import graph.TopologicalGraph;

/**
 * Re-implementation of block decomposition into parcels from :
 * 
 * Vanegas, C. A., Kelly, T., Weber, B., Halatsch, J., Aliaga, D. G., Müller, P., May 2012. Procedural generation of parcels in urban modeling. Comp. Graph. Forum 31 (2pt3).
 * 
 * Decomposition method by using straight skeleton
 * 
 * @author Mickael Brasebin
 *
 */
public class TopologicalStraightSkeletonParcelDecomposition {

  //////////////////////////////////////////////////////
  // Input data parameters
  // Must be a double attribute
  public final static String NAME_ATT_IMPORTANCE = "length";
  // public final static String NAME_ATT_ROAD = "NOM_VOIE_G";
  public final static String NAME_ATT_ROAD = "n_sq_vo";

  //////////////////////////////////////////////////////

  // Indicate if a boundary of partial skeleton is at the border of input
  // block
  public final static String ATT_IS_INSIDE = "INSIDE";
  public final static int ARC_VALUE_INSIDE = 1;
  public final static int ARC_VALUE_OUTSIDE = 2;

  public final static String ATT_ROAD = "NOM_VOIE";

  public static String ATT_FACE_ID_STRIP = "ID_STRIP";

  // Indicate the importance of the neighbour road
  public static String ATT_IMPORTANCE = "IMPORTANCE";

  public static boolean DEBUG = true;
  public static String FOLDER_OUT_DEBUG = "/tmp/";

  private static boolean isReflex(Node node, HalfEdge previous, HalfEdge next) {
    return isReflex(node.getCoordinate(), previous.getGeometry(), next.getGeometry());
  }

  private static Coordinate getNextCoordinate(Coordinate current, LineString line) {
    return (line.getCoordinateN(0).equals2D(current)) ? line.getCoordinateN(1) : line.getCoordinateN(line.getNumPoints() - 2);
  }

  private static boolean isReflex(Coordinate current, LineString previous, LineString next) {
    Coordinate previousCoordinate = getNextCoordinate(current, previous);
    Coordinate nextCoordinate = getNextCoordinate(current, next);
    return Orientation.index(previousCoordinate, current, nextCoordinate) == Orientation.CLOCKWISE;
  }

  public static final int NONE = 0;
  public static final int PREVIOUS = 1;
  public static final int NEXT = 2;

  private static int classify(Node node, Pair<HalfEdge, Optional<Pair<String, Double>>> previous, Pair<HalfEdge, Optional<Pair<String, Double>>> next) {
    if (isReflex(node, previous.getLeft(), next.getLeft()))
      return NONE;
    if (previous.getRight().map(a -> a.getRight()).orElse(0.0) > next.getRight().map(a -> a.getRight()).orElse(0.0)) {
      return PREVIOUS;
    }
    return NEXT;
  }

  private static Pair<String, Double> attributes(SimpleFeature s) {
    String name = s.getAttribute(NAME_ATT_ROAD).toString();
    String impo = s.getAttribute(NAME_ATT_IMPORTANCE).toString();
    return new ImmutablePair<>(name, Double.parseDouble(impo.replaceAll(",", ".")));
  }

  private static List<HalfEdge> getOrderedExteriorEdges(StraightSkeleton straightSkeleton) {
    // TODO would having to order the exterior edges justify creating the infinite face?
    List<HalfEdge> edges = straightSkeleton.getExteriorEdges().stream().filter(e -> !straightSkeleton.getInputPolygon().contains(e.getGeometry())).collect(Collectors.toList());
    edges.forEach(e -> System.out.println(e.getGeometry()));
    List<HalfEdge> orderedEdges = new ArrayList<>();
    HalfEdge first = edges.get(0);
    orderedEdges.add(first);
    Node currentNode = first.getTarget();
    HalfEdge current = straightSkeleton.getGraph().next(currentNode, first, edges);
    orderedEdges.add(current);
    currentNode = current.getTarget();
    while (current != first) {
      current = straightSkeleton.getGraph().next(currentNode, current, edges);
      currentNode = current.getTarget();
      if (current != first)
        orderedEdges.add(current);
    }
    return orderedEdges;
  }

  private static TopologicalGraph getAlphaStrips(TopologicalGraph graph, List<HalfEdge> orderedEdges, List<Pair<HalfEdge, Optional<Pair<String, Double>>>> attributes) {
    List<Strip> strips = new ArrayList<>();
    Pair<HalfEdge, Optional<Pair<String, Double>>> firstPair = attributes.get(0);
    Strip currentStrip = new Strip(firstPair.getKey());
    strips.add(currentStrip);
    Map<Node, Pair<HalfEdge, HalfEdge>> supportingVertices = new HashMap<>();
    for (int i = 0; i < attributes.size() - 1; i++) {
      Pair<HalfEdge, Optional<Pair<String, Double>>> p1 = attributes.get(i);
      Pair<HalfEdge, Optional<Pair<String, Double>>> p2 = attributes.get((i + 1) % attributes.size());
      if (p1.getRight().map(p -> p.getLeft()).orElse("").equals(p2.getRight().map(p -> p.getLeft()).orElse(""))) {
        currentStrip.getEdges().add(p2.getKey());
      } else {
        currentStrip = new Strip(p2.getKey());
        strips.add(currentStrip);
        supportingVertices.put(graph.getCommonNode(p1.getLeft(), p2.getLeft()), new ImmutablePair<>(p1.getLeft(), p2.getLeft()));
      }
    }
    Pair<HalfEdge, Optional<Pair<String, Double>>> lastPair = attributes.get(attributes.size() - 1);
    if (lastPair.getRight().map(p -> p.getLeft()).orElse("").equals(firstPair.getRight().map(p -> p.getLeft()).orElse(""))) {
      // we merge the first and last strips
      Strip firstStrip = strips.get(0);
      Strip lastStrip = strips.get(strips.size() - 1);
      firstStrip.getEdges().addAll(lastStrip.getEdges());
      strips.remove(lastStrip);
    } else {
      supportingVertices.put(graph.getCommonNode(lastPair.getLeft(), firstPair.getLeft()), new ImmutablePair<>(lastPair.getLeft(), firstPair.getLeft()));
    }
    System.out.println("Strips = " + strips.size());
    // List<List<HalfEdge>> alphaStrips = new ArrayList<>();
    // Map<HalfEdge, Integer> alphaStripEdgeMap = new HashMap<>();
    // for (Integer index : stripIds) {
    // // find all edges with the corresponding index
    // List<HalfEdge> stripEdges = map.entrySet().stream().filter(p -> p.getValue().equals(index)).map(p -> p.getKey()).map(p -> p.getLeft()).collect(Collectors.toList());
    // alphaStrips.add(stripEdges);
    // stripEdges.forEach(e -> alphaStripEdgeMap.put(e, index));
    // }
    // Map<Integer, LineString> psiMap = new HashMap<>();
    for (Strip strip : strips) {
      List<HalfEdge> edges = strip.getEdges();
      LineString l = Util.union(edges.stream().map(e -> e.getGeometry()).collect(Collectors.toList()));
      System.out.println(l);
      strip.setLine(l);
      // psiMap.put(currentStripId, l);
    }
    // Map<Face, Integer> alphaStripFaceMap = new HashMap<>();
    // Map<Integer, Polygon> alphaStripPolygonMap = new HashMap<>();
    for (Strip strip : strips) {
      List<Face> stripFaces = strip.getEdges().stream().map(e -> e.getFace()).collect(Collectors.toList());
      // stripFaces.forEach(p -> alphaStripFaceMap.put(p, currentStripId));
      List<Polygon> polygons = stripFaces.stream().map(f -> f.getGeometry()).collect(Collectors.toList());
      // alphaStripPolygonMap.put(currentStripId, Util.polygonUnionWithoutHoles(polygons));
      strip.setPolygon(Util.polygonUnionWithoutHoles(polygons));
      System.out.println(strip.getPolygon());
    }
    TopologicalGraph alphaStripGraph = new TopologicalGraph();
    return alphaStripGraph;
  }

  public static SimpleFeatureCollection decompose(Polygon pol, SimpleFeatureCollection roads, double offsetDistance, double maxDistanceForNearestRoad, double minimalArea,
      double minWidth, double maxWidth, double noiseParameter, RandomGenerator rng) throws SchemaException {
    // Partial skeleton
    StraightSkeleton straightSkeleton = new StraightSkeleton(pol, offsetDistance);
    straightSkeleton.getGraph().getFaces().forEach(f -> System.out.println(f.getGeometry()));
    List<HalfEdge> orderedEdges = getOrderedExteriorEdges(straightSkeleton);
    // get the road attributes
    List<Pair<HalfEdge, Optional<Pair<String, Double>>>> attributes = orderedEdges.stream()
        // .map(e -> new ImmutablePair<>(e,Closest.find(e.getGeometry().getCentroid(), roads, maxDistanceForNearestRoad)))
        .map(e -> new ImmutablePair<>(e, FindObjectInDirection.find(e.getGeometry(), pol, roads, maxDistanceForNearestRoad)))
        .map(so -> new ImmutablePair<>(so.left, so.right.map(s -> attributes(s)))).collect(Collectors.toList());
    Map<Pair<HalfEdge, Optional<Pair<String, Double>>>, Integer> map = new HashMap<>();
    Pair<HalfEdge, Optional<Pair<String, Double>>> firstPair = attributes.get(0);
    int stripId = 0;
    map.put(firstPair, stripId++);
    Map<Node, Pair<HalfEdge, HalfEdge>> supportingVertices = new HashMap<>();
    for (int i = 0; i < attributes.size() - 1; i++) {
      Pair<HalfEdge, Optional<Pair<String, Double>>> p1 = attributes.get(i);
      Pair<HalfEdge, Optional<Pair<String, Double>>> p2 = attributes.get((i + 1) % attributes.size());
      if (p1.getRight().map(p -> p.getLeft()).orElse("").equals(p2.getRight().map(p -> p.getLeft()).orElse(""))) {
        map.put(p2, map.get(p1));
      } else {
        map.put(p2, stripId++);
        supportingVertices.put(straightSkeleton.getGraph().getCommonNode(p1.getLeft(), p2.getLeft()), new ImmutablePair<>(p1.getLeft(), p2.getLeft()));
      }
    }
    Pair<HalfEdge, Optional<Pair<String, Double>>> lastPair = attributes.get(attributes.size() - 1);
    if (lastPair.getRight().map(p -> p.getLeft()).orElse("").equals(firstPair.getRight().map(p -> p.getLeft()).orElse(""))) {
      // we merge the first and last strips
      Integer firstStrip = map.get(firstPair);
      Integer lastStrip = map.get(lastPair);
      map.keySet().stream().filter(p -> map.get(p).equals(lastStrip)).forEach(p -> map.put(p, firstStrip));
    } else {
      supportingVertices.put(straightSkeleton.getGraph().getCommonNode(lastPair.getLeft(), firstPair.getLeft()), new ImmutablePair<>(lastPair.getLeft(), firstPair.getLeft()));
    }
    Set<Integer> stripIds = new HashSet<>(map.values());
    System.out.println("Strips = " + stripIds.size());
    List<List<HalfEdge>> alphaStrips = new ArrayList<>();
    Map<HalfEdge, Integer> alphaStripEdgeMap = new HashMap<>();
    for (Integer index : stripIds) {
      // find all edges with the corresponding index
      List<HalfEdge> stripEdges = map.entrySet().stream().filter(p -> p.getValue().equals(index)).map(p -> p.getKey()).map(p -> p.getLeft()).collect(Collectors.toList());
      alphaStrips.add(stripEdges);
      stripEdges.forEach(e -> alphaStripEdgeMap.put(e, index));
    }
    Map<Integer, LineString> psiMap = new HashMap<>();
    for (List<HalfEdge> strip : alphaStrips) {
      Integer currentStripId = alphaStripEdgeMap.get(strip.get(0));
      LineString l = Util.union(strip.stream().map(e -> e.getGeometry()).collect(Collectors.toList()));
      System.out.println(l);
      psiMap.put(currentStripId, l);
    }
    Map<Face, Integer> alphaStripFaceMap = new HashMap<>();
    Map<Integer, Polygon> alphaStripPolygonMap = new HashMap<>();
    for (List<HalfEdge> strip : alphaStrips) {
      Integer currentStripId = alphaStripEdgeMap.get(strip.get(0));
      List<Face> stripFaces = strip.stream().map(e -> e.getFace()).collect(Collectors.toList());
      stripFaces.forEach(p -> alphaStripFaceMap.put(p, currentStripId));
      List<Polygon> polygons = stripFaces.stream().map(f -> f.getGeometry()).collect(Collectors.toList());
      alphaStripPolygonMap.put(currentStripId, Util.polygonUnionWithoutHoles(polygons));
      System.out.println(Util.polygonUnionWithoutHoles(polygons));
    }
    System.out.println("Supporting vertices");
    Map<Integer, Polygon> betaStripPolygonMap = new HashMap<>(alphaStripPolygonMap);
    // classify supporting vertices
    for (Node n : supportingVertices.keySet()) {
      System.out.println(n.getGeometry());
      Pair<HalfEdge, HalfEdge> pair = supportingVertices.get(n);
      Pair<HalfEdge, Optional<Pair<String, Double>>> previous = attributes.stream().filter(p -> p.getLeft() == pair.getLeft()).findFirst().get();
      Pair<HalfEdge, Optional<Pair<String, Double>>> next = attributes.stream().filter(p -> p.getLeft() == pair.getRight()).findFirst().get();
      int supportingVertexClass = classify(n, previous, next);
      // System.out.println(supportingVertexClass);
      HalfEdge previousEdge = previous.getLeft();
      HalfEdge nextEdge = next.getLeft();
      Face prevFace = previousEdge.getFace();
      Face nextFace = nextEdge.getFace();
      Integer prevFaceId = alphaStripFaceMap.get(prevFace);
      Integer nextFaceId = alphaStripFaceMap.get(nextFace);
      List<HalfEdge> edgeList = straightSkeleton.getInteriorEdges().stream()
          .filter(e -> (alphaStripFaceMap.get(e.getFace()) == prevFaceId && alphaStripFaceMap.get(e.getTwin().getFace()) == nextFaceId)
          // ||
          // (alphaStripFaceMap.get(e.getFace()) == nextFaceId && alphaStripFaceMap.get(e.getTwin().getFace()) == prevFaceId)
          ).collect(Collectors.toList());
      edgeList.forEach(e -> System.out.println(e.getGeometry()));
      List<HalfEdge> diagonalEdgeList = new ArrayList<>();
      HalfEdge currEdge = straightSkeleton.getGraph().next(n, null, edgeList);
      System.out.println("currEdge\n" + currEdge.getGeometry());
      Node currNode = n;
      while (currEdge != null) {
        // currNode = (currEdge.getOrigin() == currNode) ? currEdge.getTarget() : currEdge.getOrigin();
        currNode = currEdge.getTarget();
        diagonalEdgeList.add(currEdge);
        currEdge = straightSkeleton.getGraph().next(currNode, currEdge, edgeList);
        // currEdge = currEdge.getNext();
      }
      System.out.println(currNode.getGeometry());
      if (supportingVertexClass != NONE) {
        final Integer removedId = (supportingVertexClass == PREVIOUS) ? nextFaceId : prevFaceId;
        List<Face> facesToRemove = diagonalEdgeList.stream().map(e -> (alphaStripFaceMap.get(e.getFace()) == removedId) ? e.getFace() : e.getTwin().getFace())
            .collect(Collectors.toList());
        Coordinate projection = Util.project(currNode.getCoordinate(), psiMap.get(removedId));
        // System.out.println(psiMap.get(removedId));
        // System.out.println(currNode.getGeometry().getFactory().createPoint(projection));
        // System.out.println(facesToRemove.get(facesToRemove.size() - 1).getGeometry());
        Polygon toRemove = facesToRemove.get(facesToRemove.size() - 1).getGeometry();
        Pair<Polygon, Polygon> split = splitPolygon(toRemove, toRemove.getFactory().createLineString(new Coordinate[] { currNode.getCoordinate(), projection }));
        // System.out.println(split.getLeft());
        // System.out.println(split.getRight());
        Integer absorbingBetaSplitId = (supportingVertexClass == PREVIOUS) ? prevFaceId : nextFaceId;
        Integer absorbedBetaSplitId = (supportingVertexClass == PREVIOUS) ? nextFaceId : prevFaceId;
        Polygon absorbingBetaSplit = betaStripPolygonMap.get(absorbingBetaSplitId);
        Polygon absorbedBetaSplit = betaStripPolygonMap.get(absorbedBetaSplitId);
        int leftShared = sharedPoints(split.getLeft(), absorbingBetaSplit);
        // int rightShared = sharedPoints(split.getRight(), absorbingBetaSplit);
        // System.out.println("SHARED " + leftShared + " - " + rightShared);
        System.out.println(absorbingBetaSplit);
        Polygon absorbedPolygonPart = (leftShared == 2) ? split.getLeft() : split.getRight();
        // System.out.println("ABSORBED:\n" + absorbedPolygonPart);
        List<Polygon> absorbedPolygons = facesToRemove.subList(0, facesToRemove.size() - 1).stream().map(f -> f.getGeometry()).collect(Collectors.toList());
        absorbedPolygons.add(absorbedPolygonPart);
        List<Polygon> newAbsorbing = new ArrayList<>(absorbedPolygons);
        newAbsorbing.add(absorbingBetaSplit);
        betaStripPolygonMap.put(absorbingBetaSplitId, Util.polygonUnion(newAbsorbing));
        betaStripPolygonMap.put(absorbedBetaSplitId, Util.polygonDifference(Arrays.asList(absorbedBetaSplit), absorbedPolygons));
      }
      System.out.println("DONE");
    }
    System.out.println("betaStripPolygonMap=");
    betaStripPolygonMap.values().forEach(p -> System.out.println(p));

    // System.out.println("FACES");
    // for (Face f : cs.getGraph().getFaces()) {
    // System.out.println(f.getGeometry());
    // }
    // System.out.println("EDGES");
    // for (Edge e : cs.getGraph().getEdges()) {
    // System.out.println(e.getGeometry());
    // }
    // System.out.println("NODES");
    // for (Node n : cs.getGraph().getNodes()) {
    // System.out.println(n.getGeometry());
    // }
    return null;
  }

  // private static Pair<Polygon, Polygon> splitPolygon(Polygon toRemove, LineString createLineString) {
  //
  // return null;
  // }

  @SuppressWarnings("rawtypes")
  public static List<Geometry> polygonize(Geometry geometry) {
    List lines = LineStringExtracter.getLines(geometry);
    Polygonizer polygonizer = new Polygonizer();
    polygonizer.add(lines);
    Collection polys = polygonizer.getPolygons();
    return Arrays.asList(GeometryFactory.toPolygonArray(polys));
  }

  public static Pair<Polygon, Polygon> splitPolygon(Polygon poly, LineString line) {
    Geometry[] snapped = GeometrySnapper.snap(poly, line, 0.001);
    Geometry nodedLinework = snapped[0].getBoundary().union(snapped[1]);
    List<Geometry> polys = polygonize(nodedLinework);
    // Only keep polygons which are inside the input
    List<Polygon> output = polys.stream().map(g -> (Polygon) g).filter(g -> poly.contains(g.getInteriorPoint())).collect(Collectors.toList());
    if (output.size() != 2) {
      System.out.println("OUTPUT WITH " + output.size());
      System.out.println("SPLIT WITH");
      System.out.println(line);
      System.out.println("NODED");
      System.out.println(nodedLinework);
      System.out.println("POLYGONIZED");
      System.out.println(polys);
    }
    return new ImmutablePair<>(output.get(0), output.get(1));
  }

  public static int sharedPoints(Geometry a, Geometry b) {
    Geometry[] snapped = GeometrySnapper.snap(a, b, 0.1);
    Set<Coordinate> ca = new HashSet<>(Arrays.asList(snapped[0].getCoordinates()));
    Set<Coordinate> cb = new HashSet<>(Arrays.asList(snapped[1].getCoordinates()));
    return (int) ca.stream().filter(c -> cb.contains(c)).count();
  }

  public static void main(String[] args) throws IOException, SchemaException {
    String inputParcelShapeFile = "/home/julien/data/PLU_PARIS/ilots_13.shp";
    String inputRoadShapeFile = "/home/julien/data/PLU_PARIS/voie/voie_l93.shp";
    String folderOut = "data/";
    // The output file that will contain all the decompositions
    String shapeFileOut = folderOut + "outflag.shp";
    (new File(folderOut)).mkdirs();
    // Reading collection
    ShapefileDataStore parcelDS = new ShapefileDataStore(new File(inputParcelShapeFile).toURI().toURL());
    SimpleFeatureCollection parcels = parcelDS.getFeatureSource().getFeatures();
    ShapefileDataStore roadDS = new ShapefileDataStore(new File(inputRoadShapeFile).toURI().toURL());
    SimpleFeatureCollection roads = roadDS.getFeatureSource().getFeatures();
    SimpleFeatureIterator iterator = parcels.features();
    SimpleFeature feature = iterator.next();
    List<Polygon> polygons = Util.getPolygons((Geometry) feature.getDefaultGeometry());
    double maxDepth = 10, maxDistanceForNearestRoad = 100, minimalArea = 20, minWidth = 5, maxWidth = 40, noiseParameter = 0.1;
    iterator.close();
    decompose(polygons.get(0), roads, maxDepth, maxDistanceForNearestRoad, minimalArea, minWidth, maxWidth, noiseParameter, new MersenneTwister(42));
    roadDS.dispose();
    parcelDS.dispose();
  }
}

class Strip {
  List<HalfEdge> edges = new ArrayList<>();
  LineString line = null;
  Polygon polygon = null;

  public Polygon getPolygon() {
    return polygon;
  }

  public void setPolygon(Polygon polygon) {
    this.polygon = polygon;
  }

  public LineString getLine() {
    return line;
  }

  public void setLine(LineString line) {
    this.line = line;
  }

  public List<HalfEdge> getEdges() {
    return edges;
  }

  public Strip(HalfEdge edge) {
    this.edges.add(edge);
  }
}
