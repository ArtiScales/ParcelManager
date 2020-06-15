package fr.ign.artiscales.decomposition;

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
import org.apache.commons.math3.distribution.NormalDistribution;
import org.apache.commons.math3.distribution.RealDistribution;
import org.apache.commons.math3.random.MersenneTwister;
import org.apache.commons.math3.random.RandomGenerator;
import org.geotools.data.shapefile.ShapefileDataStore;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.feature.SchemaException;
import org.locationtech.jts.algorithm.Angle;
import org.locationtech.jts.algorithm.Orientation;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineSegment;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.PrecisionModel;
import org.locationtech.jts.linearref.LengthIndexedLine;
import org.locationtech.jts.operation.linemerge.LineMerger;
import org.locationtech.jts.operation.overlay.snap.GeometrySnapper;
import org.locationtech.jts.operation.polygonize.Polygonizer;
import org.locationtech.jts.precision.GeometryPrecisionReducer;
import org.locationtech.jts.simplify.TopologyPreservingSimplifier;
import org.opengis.feature.simple.SimpleFeature;

import fr.ign.artiscales.decomposition.analysis.FindObjectInDirection;
import fr.ign.artiscales.graph.Face;
import fr.ign.artiscales.graph.HalfEdge;
import fr.ign.artiscales.graph.Node;
import fr.ign.artiscales.graph.TopologicalGraph;

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
  // public final static String NAME_ATT_IMPORTANCE = "length";
  // // public final static String NAME_ATT_ROAD = "NOM_VOIE_G";
  // // public final static String NAME_ATT_ROAD = "n_sq_vo";
  // public final static String NAME_ATT_ROAD = "Id";
  private final String NAME_ATT_IMPORTANCE;
  private final String NAME_ATT_ROAD;

  //////////////////////////////////////////////////////

  // Indicate if a boundary of partial skeleton is at the border of input
  // block
  // public final static String ATT_IS_INSIDE = "INSIDE";
  // public final static int ARC_VALUE_INSIDE = 1;
  // public final static int ARC_VALUE_OUTSIDE = 2;

  // public final static String ATT_ROAD = "NOM_VOIE";

  // public static String ATT_FACE_ID_STRIP = "ID_STRIP";

  // Indicate the importance of the neighbour road
  // public static String ATT_IMPORTANCE = "IMPORTANCE";

  public static boolean DEBUG = true;
  public static String FOLDER_OUT_DEBUG = "/tmp/skeleton";

  private Polygon initialPolygon;
  // private SimpleFeatureCollection roads;
  // private double offsetDistance;
  // private double maxDistanceForNearestRoad;
  // private double minimalArea;
  private PrecisionModel precisionModel;
  private double tolerance;
  private GeometryPrecisionReducer precisionReducer;
  private StraightSkeleton straightSkeleton;
  private List<HalfEdge> orderedEdges;
  private TopologicalGraph alphaStrips;
  private TopologicalGraph betaStrips;
  private Map<HalfEdge, Optional<Pair<String, Double>>> attributes;
  private Geometry snapGeom;

  public TopologicalStraightSkeletonParcelDecomposition(Polygon p, SimpleFeatureCollection roads, String roadNameAttribute, String roadImportanceAttribute, double offsetDistance,
      double maxDistanceForNearestRoad, double minimalArea) throws StraightSkeletonException {
    this(p, roads, roadNameAttribute, roadImportanceAttribute, offsetDistance, maxDistanceForNearestRoad, minimalArea, 2);
  }

  public TopologicalStraightSkeletonParcelDecomposition(Polygon p, SimpleFeatureCollection roads, String roadNameAttribute, String roadImportanceAttribute, double offsetDistance,
      double maxDistanceForNearestRoad, double minimalArea, int numberOfDigits) throws StraightSkeletonException {
    this.precisionModel = new PrecisionModel(Math.pow(10, numberOfDigits));
    this.tolerance = 2.0 / Math.pow(10, numberOfDigits);
    p = (Polygon) TopologyPreservingSimplifier.simplify(p, 10 * tolerance);
    this.precisionReducer = new GeometryPrecisionReducer(precisionModel);
    this.initialPolygon = (Polygon) precisionReducer.reduce(p);
    this.NAME_ATT_ROAD = roadNameAttribute;
    this.NAME_ATT_IMPORTANCE = roadImportanceAttribute;
    // this.roads = roads;
    // this.offsetDistance = offsetDistance;
    // this.maxDistanceForNearestRoad = maxDistanceForNearestRoad;
    // this.minimalArea = minimalArea;
    // Partial skeleton
    try {
      this.straightSkeleton = new StraightSkeleton(this.initialPolygon, offsetDistance);
    } catch (Exception e) {
      e.printStackTrace();
      throw new StraightSkeletonException();
    }
    TopologicalGraph graph = straightSkeleton.getGraph();
    // graph.getFaces().forEach(f -> log(f.getGeometry()));
    export(graph, new File(FOLDER_OUT_DEBUG, "init_before_exterior"));
    this.orderedEdges = getOrderedExteriorEdges(straightSkeleton.getGraph());
    export(graph, new File(FOLDER_OUT_DEBUG, "init"));
    snapGeom = initialPolygon.getFactory().createGeometryCollection(graph.getFaces().stream().map(f -> f.getGeometry()).collect(Collectors.toList()).toArray(new Geometry[] {}));
    // get the road attributes
    this.attributes = new HashMap<>();
    orderedEdges.stream()
        .forEach(e -> attributes.put(e, FindObjectInDirection.find(e.getGeometry(), this.initialPolygon, roads, maxDistanceForNearestRoad).map(s -> getAttributes(s))));
    this.alphaStrips = mergeOnLogicalStreets();
    export(alphaStrips, new File(FOLDER_OUT_DEBUG, "alpha"));
    this.betaStrips = fixDiagonalEdges(alphaStrips, attributes);
    export(betaStrips, new File(FOLDER_OUT_DEBUG, "beta"));
  }

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

  private static int classify(Node node, HalfEdge previous, Optional<Pair<String, Double>> previousAttributes, HalfEdge next, Optional<Pair<String, Double>> nextAttributes) {
    if (isReflex(node, previous, next))
      return NONE;
    if (previousAttributes.map(a -> a.getRight()).orElse(0.0) > nextAttributes.map(a -> a.getRight()).orElse(0.0)) {
      return PREVIOUS;
    }
    return NEXT;
  }

  private Pair<String, Double> getAttributes(SimpleFeature s) {
    String name = s.getAttribute(NAME_ATT_ROAD).toString();
    String impo = s.getAttribute(NAME_ATT_IMPORTANCE).toString();
    return new ImmutablePair<>(name, Double.parseDouble(impo.replaceAll(",", ".")));
  }

  private static List<HalfEdge> getOrderedEdgesFromCycle(List<HalfEdge> edges) {
    List<HalfEdge> orderedEdges = new ArrayList<>();
    HalfEdge first = edges.get(0);
    orderedEdges.add(first);
    Node currentNode = first.getTarget();
    HalfEdge current = TopologicalGraph.next(currentNode, first, edges);
    orderedEdges.add(current);
    currentNode = current.getTarget();
    while (current != first) {
      current = TopologicalGraph.next(currentNode, current, edges);
      currentNode = current.getTarget();
      if (current != first)
        orderedEdges.add(current);
    }
    return orderedEdges;
  }

  private static void log(Object text) {
    if (DEBUG) {
      System.out.println(text);
    }
  }

  private static List<HalfEdge> getOrderedEdges(List<HalfEdge> edges) throws EdgeException {
    log("getOrderedEdges");
    edges.forEach(e -> log(e.getGeometry()));
    List<HalfEdge> orderedEdges = new ArrayList<>();
    boolean forward = true;
    HalfEdge current = edges.remove(0);
    log("current\n" + current.getGeometry());
    orderedEdges.add(current);
    Node currentNode = current.getTarget();
    while (!edges.isEmpty()) {
      currentNode = forward ? current.getTarget() : orderedEdges.get(0).getOrigin();
      current = forward ? TopologicalGraph.next(currentNode, current, edges) : TopologicalGraph.previous(currentNode, orderedEdges.get(0), edges);
      if (current == null) {
        if (forward) { // try backwards
          forward = false;
        } else { // we already tried forwards and backwards
          throw new EdgeException();
        }
      } else {
        edges.remove(current);
        if (forward) {
          orderedEdges.add(current);
        } else {
          orderedEdges.add(0, current);
        }
      }
    }
    return orderedEdges;
  }

  private List<HalfEdge> getOrderedExteriorEdges(TopologicalGraph graph) {
    // TODO would having to order the exterior edges justify creating the infinite face?
    // FIXME here is a hack to get the exterior edges. This is ugly
    List<HalfEdge> edges = graph.getEdges().stream().filter(p -> p.getTwin() == null).filter(e -> !this.initialPolygon.buffer(-0.1).contains(e.getGeometry()))
        .collect(Collectors.toList());
    // edges.forEach(e -> log(e.getGeometry()));
    graph.getEdges().forEach(e -> e.setAttribute("EXTERIOR", "false"));
    edges.forEach(e -> e.setAttribute("EXTERIOR", "true"));
    // straightSkeleton.getGraph().getEdges().iterator().next().getAttributes().forEach(a -> log("attribute " + a));
    return getOrderedEdgesFromCycle(edges);
  }

  /**
   * Create alpha strips by merging faces.
   * 
   * @return a topological graph whose faces are the alpha strips
   */
  private TopologicalGraph mergeOnLogicalStreets() {
    // log("getAlphaStrips:edges");
    // orderedEdges.forEach(e->log(e.getGeometry()));
    TopologicalGraph alphaStripGraph = new TopologicalGraph();
    HalfEdge firstHE = orderedEdges.get(0);
//    log("getAlphaStrips:firstHE:\n" + firstHE.getGeometry());
    Optional<Pair<String, Double>> firstAttributes = attributes.get(firstHE);
    HalfEdge currentStripHE = new HalfEdge(firstHE.getOrigin(), null, null);
    currentStripHE.getChildren().add(firstHE);
    alphaStripGraph.getEdges().add(currentStripHE);
    for (int i = 0; i < orderedEdges.size() - 1; i++) {
      HalfEdge e1 = orderedEdges.get(i), e2 = orderedEdges.get((i + 1) % attributes.size());
      straightSkeleton.getGraph();
      int numberOfOutgoing = TopologicalGraph.outgoingEdgesOf(e1.getTarget(), straightSkeleton.getGraph().getEdges()).size();
      Optional<Pair<String, Double>> a1 = attributes.get(e1), a2 = attributes.get(e2);
      if (a1.map(p -> p.getLeft()).orElse("").equals(a2.map(p -> p.getLeft()).orElse("")) || numberOfOutgoing == 1) {
        currentStripHE.getChildren().add(e2);
      } else {
        Node node = straightSkeleton.getGraph().getCommonNode(e1, e2);// FIXME
        currentStripHE.setTarget(node);
        currentStripHE = new HalfEdge(e2.getOrigin(), null, null);
        currentStripHE.getChildren().add(e2);
        alphaStripGraph.getEdges().add(currentStripHE);
        alphaStripGraph.addNode(node);
      }
    }
    HalfEdge lastHE = orderedEdges.get(orderedEdges.size() - 1);
    Optional<Pair<String, Double>> lastAttributes = attributes.get(lastHE);
    if (lastAttributes.map(p -> p.getLeft()).orElse("").equals(firstAttributes.map(p -> p.getLeft()).orElse(""))) {
      // we merge the first and last strips
      HalfEdge firstStrip = alphaStripGraph.getEdges().get(0);
      HalfEdge lastStrip = alphaStripGraph.getEdges().get(alphaStripGraph.getEdges().size() - 1);
      firstStrip.getChildren().addAll(lastStrip.getChildren());
      alphaStripGraph.getEdges().remove(lastStrip);
      firstStrip.setOrigin(lastStrip.getOrigin());
    } else {
      Node node = straightSkeleton.getGraph().getCommonNode(lastHE, firstHE);// FIXME
      currentStripHE.setTarget(node);
      alphaStripGraph.addNode(node);
    }
    log("Strips = " + alphaStripGraph.getEdges().size());
    for (HalfEdge strip : alphaStripGraph.getEdges()) {
      List<HalfEdge> edges = strip.getChildren();
      LineString l = Util.union(edges.stream().map(e -> e.getGeometry()).collect(Collectors.toList()));
      strip.setLine(l);
      log(l);
    }
    for (HalfEdge strip : alphaStripGraph.getEdges()) {
      Face stripFace = new Face();
      List<Face> stripFaces = strip.getChildren().stream().map(e -> e.getFace()).collect(Collectors.toList());
      stripFace.getChildren().addAll(stripFaces);
      stripFaces.forEach(f -> f.setParent(stripFace));
      List<Polygon> polygons = stripFaces.stream().map(f -> f.getGeometry()).collect(Collectors.toList());
      stripFace.setPolygon(Util.polygonUnionWithoutHoles(polygons, precisionReducer));
      strip.setFace(stripFace);
      alphaStripGraph.getFaces().add(stripFace);
    }
    return alphaStripGraph;
  }

  private static Coordinate getUnitVector(Coordinate c1, Coordinate c2) {
    double dx = c2.x - c1.x, dy = c2.y - c1.y;
    double length = Math.sqrt(dx * dx + dy * dy);
    return new Coordinate(dx / length, dy / length);
  }

  private static Coordinate getPerpendicularVector(Coordinate c1, Coordinate c2, boolean left) {
    double dx = c2.x - c1.x, dy = c2.y - c1.y;
    double x = left ? -1 : 1, y = left ? 1 : -1;
    double length = Math.sqrt(dx * dx + dy * dy);
    return new Coordinate(x * dy / length, y * dx / length);
  }

  private static List<Double> sampleWidths(double length, RealDistribution nd, double minWidth, double maxWidth) {
    if (length < minWidth)
      return Arrays.asList(length);
    List<Double> widths = new ArrayList<>();
    double sum = 0;
    while (sum < length) {
      // sample a width
      double sample = nd.sample();
      // if we sampled enough (total widths greater than the targer)
      if (sum + sample > length) {
        double remaining = length - sum;
        // remaining is what remains to be drawn
        if (remaining > minWidth) {
          sample = remaining;
        } else {
          double previous = widths.get(widths.size() - 1);
          widths.remove(widths.size() - 1);
          sum -= previous;
          if (previous + remaining < maxWidth) {
            sample = previous + remaining;
          } else {
            sample = (previous + remaining) / 2;
            widths.add(sample);
            sum += sample;
          }
        }
      }
      widths.add(sample);
      sum += sample;
    }
    return widths;
  }

  private Pair<HalfEdge, Coordinate> getIntersection(Coordinate o, Coordinate d) {
    Point p = straightSkeleton.getInputPolygon().getFactory().createPoint(o);
    List<Face> faces = straightSkeleton.getGraph().getFaces().stream().filter(f -> f.getGeometry().intersects(p)).collect(Collectors.toList());
    if (faces.size() != 1) {
      // log("found " + faces.size() + " faces intersecting " + p);
      faces = straightSkeleton.getGraph().getFaces().stream().filter(f -> f.getGeometry().intersects(p.buffer(tolerance))).collect(Collectors.toList());
      if (faces.size() != 1) {
        log("found " + faces.size() + " faces intersecting " + p + " again");
        return null;
      }
      o = Util.project(o, faces.get(0).getGeometry().getExteriorRing());
    }
    Face face = faces.get(0);
    List<HalfEdge> edges = face.getEdges().stream().filter(h -> h.getAttribute("EXTERIOR").toString().equals("false")).collect(Collectors.toList());
    final Coordinate origin = o;
    List<Pair<HalfEdge, Coordinate>> intersections = edges.stream().filter(h -> Util.getRayLineSegmentIntersects(origin, d, h.getGeometry()))
        .map(h -> new ImmutablePair<HalfEdge, Coordinate>(h, Util.getRayLineSegmentIntersection(origin, d, h.getGeometry()))).collect(Collectors.toList());
    intersections.sort((Pair<HalfEdge, Coordinate> p1, Pair<HalfEdge, Coordinate> p2) -> Double.compare(p1.getRight().distance(origin), p2.getRight().distance(origin)));
    return intersections.get(0);
  }

  private static double getAngle(Coordinate c1, Coordinate c2, Coordinate d) {
    return Angle.angleBetween(c2, c1, new Coordinate(c1.x + d.x, c1.y + d.y));
  }

  private List<Coordinate> getPath(StraightSkeleton straightSkeleton, Polygon strip, Node node, Coordinate direction) {
    // Coordinate stripCoordinate = getCoordinate(stripCoordinates, node.getCoordinate(), 0.01);
    // if (stripCoordinate != null)
    // return Arrays.asList(stripCoordinate);// we reached the border of the strip
    if (strip.getExteriorRing().distance(strip.getFactory().createPoint(node.getCoordinate())) <= tolerance) {
      // we are 'on' the border of the strip
      Coordinate projection = Util.project(node.getCoordinate(), strip.getExteriorRing());
      log("PROJECT\n" + node.getCoordinate() + "\nTO" + projection);
      return Arrays.asList(projection);
    }
    // find the best edge to follow
    List<HalfEdge> edges = TopologicalGraph.outgoingEdgesOf(node, straightSkeleton.getGraph().getEdges());
    edges.sort((HalfEdge h1, HalfEdge h2) -> Double.compare(getAngle(h1.getOrigin().getCoordinate(), h1.getTarget().getCoordinate(), direction),
        getAngle(h2.getOrigin().getCoordinate(), h2.getTarget().getCoordinate(), direction)));
    HalfEdge edge = edges.get(0);
    List<Coordinate> path = new ArrayList<>();
    path.add(node.getCoordinate());
    Coordinate d = getUnitVector(node.getCoordinate(), edge.getTarget().getCoordinate());
    path.addAll(getPath(straightSkeleton, strip, edge.getTarget(), d));
    return path;
  }

  private LineString getCutLine(Polygon strip, Coordinate coordinate, LineString support) {
    log("getCutLine FROM\n" + strip.getFactory().createPoint(coordinate) + "\nWITH EXT STRIP\n" + strip.getExteriorRing());
    Node node = straightSkeleton.getGraph().getNode(coordinate, tolerance);
    GeometryFactory factory = strip.getFactory();
    if (node == null) {
      // log("NO NODE");
      // no node here, compute perpendicular line
      Coordinate c1 = support.getCoordinateN(1);
      Coordinate c2 = support.getCoordinateN(0);
      Coordinate d = getPerpendicularVector(c1, c2, false);
      Pair<HalfEdge, Coordinate> intersection = getIntersection(c2, d);
      Coordinate intersectionCoord = intersection.getRight();
      // Polygon snapped = snap(strip, intersectionCoord, tolerance);
      log("INTERSECTION\n" + factory.createPoint(intersectionCoord) + "\n" + strip + "\n" + intersection.getLeft().getGeometry());
      if (strip.getExteriorRing().distance(factory.createPoint(intersectionCoord)) <= tolerance) {
        // we are 'on' the border of the strip
        Coordinate projection = Util.project(intersectionCoord, strip.getExteriorRing());// use snapped?
        log("PROJECTION\n" + factory.createLineString(new Coordinate[] { c2, projection }));
        // return factory.createLineString(new Coordinate[] { c2, intersectionCoord });
        return factory.createLineString(new Coordinate[] { c2, projection });
      }
      List<Coordinate> coordinateList = new ArrayList<>();
      coordinateList.add(c2);
      coordinateList.add(intersectionCoord);
      Node currentNode = straightSkeleton.getGraph().getNode(intersectionCoord, tolerance);
      if (currentNode == null) {
        double angle = getAngle(intersection.getLeft().getOrigin().getCoordinate(), intersection.getLeft().getTarget().getCoordinate(), d);
        currentNode = (angle < Math.PI / 2) ? intersection.getLeft().getTarget() : intersection.getLeft().getOrigin();
        // do not add the coord. It will eventually be added by getPath
        // coordinateList.add(currentNode.getCoordinate());
        d = getUnitVector(intersectionCoord, currentNode.getCoordinate());
        // log("NO NODE. CONTINUE TO\n"+currentNode.getGeometry());
      } else {
        // log("FOUND NODE\n"+currentNode.getGeometry());
      }
      coordinateList.addAll(getPath(straightSkeleton, strip, currentNode, d));
      return factory.createLineString(coordinateList.toArray(new Coordinate[coordinateList.size()]));
    }
    List<HalfEdge> edges = straightSkeleton.getGraph().getEdges().stream().filter(h -> h.getOrigin() == node && h.getTwin() != null).collect(Collectors.toList());
    if (edges.size() != 1) {
      log("found " + edges.size() + " edges starting at " + node.getGeometry());
    } else {
      HalfEdge edge = edges.get(0);
      List<Coordinate> coordinateList = new ArrayList<>();
      Coordinate c1 = edge.getOrigin().getCoordinate();
      Coordinate c2 = edge.getTarget().getCoordinate();
      coordinateList.add(c1);
      // coordinateList.add(c2);
      Coordinate d = getUnitVector(c1, c2);
      coordinateList.addAll(getPath(straightSkeleton, strip, edge.getTarget(), d));
      return factory.createLineString(coordinateList.toArray(new Coordinate[coordinateList.size()]));
      // log(edges.get(0).getGeometry());
    }
    return null;
  }

  private List<Polygon> slice(double minWidth, double maxWidth, RealDistribution nd, RandomGenerator rng, Face strip) throws EdgeException {
    List<Polygon> result = new ArrayList<>();
    if (strip.getGeometry().isEmpty()) {
      log("EMPTY STRIP");
      return result;
    }
    log("potentials");
    strip.getEdges().stream().filter(e -> e.getTwin() == null).forEach(f -> log(f.getGeometry()));
    List<HalfEdge> extEdges = getOrderedEdges(
        strip.getEdges().stream().filter(e -> e.getTwin() == null && !initialPolygon.buffer(-0.1).contains(e.getGeometry())).collect(Collectors.toList()));
    // LineMerger lsm = new LineMerger();
    // extEdges.stream().forEach(e -> lsm.add(e.getGeometry()));
    // Collection<?> merged = lsm.getMergedLineStrings();
    // TODO check the order of that linestring: should be CCW
    // LineString phi = (LineString) merged.iterator().next();
    List<Coordinate> coordinates = extEdges.stream().map(e -> e.getTarget().getCoordinate()).collect(Collectors.toList());
    coordinates.add(0, extEdges.get(0).getOrigin().getCoordinate());
    LineString phi = strip.getGeometry().getFactory().createLineString(coordinates.toArray(new Coordinate[coordinates.size()]));
    LengthIndexedLine lil = new LengthIndexedLine(phi);
    log("phi\n" + phi);
    double length = phi.getLength();
    List<Double> widths = sampleWidths(length, nd, minWidth, maxWidth);
    if (widths.size() == 1) {
      result.add(strip.getGeometry());
    } else {
      double current = widths.get(0);
      // split the strip now
      Polygon remainder = (Polygon) strip.getGeometry().copy();
      // we remove the last width
      for (double w : widths.subList(1, widths.size())) {
        double next = current + w;
        LineString l = (LineString) lil.extractLine(current, next);
        // get perpendicular line
        Coordinate coordinate = lil.extractPoint(current);
        log("REMAINDER\n" + snapped(remainder));
        LineString cutLine = getCutLine(remainder, coordinate, l);
        // Polygon r = (Polygon) precisionReducer.reduce(remainder);
        // LineString cl = (LineString) precisionReducer.reduce(cutLine);
        // Geometry[] snapped = GeometrySnapper.snap(r, cl, tolerance);
        // Pair<Polygon, Polygon> split = splitPolygon((Polygon) snapped[0], (LineString) snapped[1], false);
        // GeometrySnapper snapper = new GeometrySnapper(remainder);
        // Polygon snapped = (Polygon) snapper.snapTo(cutLine, tolerance);
        log("cutLine\n" + cutLine);
        Geometry[] snapped = GeometrySnapper.snap(remainder, cutLine, tolerance);
        log("GeometrySnapper\n" + snapped[0]);
        log("GeometrySnapper\n" + snapped[1]);
        Pair<Polygon, Polygon> split = splitPolygon((Polygon) snapped[0], (LineString) snapped[1]);
        result.add(split.getLeft());
        remainder = split.getRight();
        current = next;
      }
      result.add(remainder);
    }
    return result;
  }

  private List<Polygon> createParcels(double minWidth, double maxWidth, double omega, RandomGenerator rng) throws EdgeException {
    NormalDistribution nd = new NormalDistribution(rng, (minWidth + maxWidth) / 2, Math.sqrt(3 * omega));
    List<Polygon> result = new ArrayList<>();
    for (Face face : betaStrips.getFaces()) {
      result.addAll(slice(minWidth, maxWidth, nd, rng, face));
    }
    return result;
  }

  /**
   * The α-strips computed from the skeleton faces suffer from diagonal edges at the intersection of logical streets [...]. To correct these edges, we modify LS (B) [...] to
   * transfer a near-triangular region from the strip on one side of an offending edge to the strip on the other side. We refer to these corrected strips as β-strips.
   * 
   * TODO support multiple supporting vertex classification schemes
   * 
   * @param alphaStrips
   *          α-strips
   * @param attributes
   *          attributes of the edges (used to classify supporting vertices)
   * @return β-strips
   */
  private TopologicalGraph fixDiagonalEdges(TopologicalGraph alphaStrips, Map<HalfEdge, Optional<Pair<String, Double>>> attributes) {
    // classify supporting vertices
    for (Node n : alphaStrips.getNodes()) {
      HalfEdge previousEdge = TopologicalGraph.incomingEdgesOf(n, orderedEdges).get(0);
      HalfEdge nextEdge = TopologicalGraph.outgoingEdgesOf(n, orderedEdges).get(0);
      Optional<Pair<String, Double>> previousAttributes = attributes.get(previousEdge);
      Optional<Pair<String, Double>> nextAttributes = attributes.get(nextEdge);
      int supportingVertexClass = classify(n, previousEdge, previousAttributes, nextEdge, nextAttributes);
      Face prevFace = previousEdge.getFace(), nextFace = nextEdge.getFace();
      Face prevAlphaStrip = prevFace.getParent(), nextAlphaStrip = nextFace.getParent();
      // FIXME use parent edge children
      // get all edges connecting the current and previous strips (they form the diagonal edge)
      List<HalfEdge> edgeList = straightSkeleton.getGraph().getEdges().stream().filter(e -> e.getTwin() != null)
          .filter(e -> (e.getFace().getParent() == prevAlphaStrip && e.getTwin().getFace().getParent() == nextAlphaStrip)).collect(Collectors.toList());
      // create the complete ordered list of edges forming the diagonal edge
      List<HalfEdge> diagonalEdgeList = new ArrayList<>();
      // get the next outgoing edge from the current node belonging to the edge list
      HalfEdge currEdge = TopologicalGraph.next(n, null, edgeList);
      Node currNode = n;
      while (currEdge != null) {
        currNode = currEdge.getTarget();
        diagonalEdgeList.add(currEdge);
        currEdge = TopologicalGraph.next(currNode, currEdge, edgeList);
      }
      if (supportingVertexClass != NONE) {
        final Face splitAlphaStrip = (supportingVertexClass == PREVIOUS) ? nextAlphaStrip : prevAlphaStrip;
        // TODO check that the first edge is always the supporting edge
        Coordinate projection = Util.project(currNode.getCoordinate(), splitAlphaStrip.getEdges().get(0).getGeometry());
        // log("SPLIT WITH\n"+currNode.getGeometry()+"\n"+currNode.getGeometry().getFactory().createPoint(projection));
        Polygon splitAlphaStripSnapped = snap(splitAlphaStrip.getGeometry(), projection, tolerance);
        Polygon r = (Polygon) precisionReducer.reduce(splitAlphaStripSnapped);
        LineString cl = (LineString) precisionReducer.reduce(r.getFactory().createLineString(new Coordinate[] { projection, currNode.getCoordinate() }));
        Geometry[] snapped = GeometrySnapper.snap(r, cl, tolerance);
        if (cl.isEmpty()) {
          log("IGNORING EMPTY PROJECTION LINE \n" + splitAlphaStrip.getGeometry() + "\n" + r.getFactory().createPoint(projection) + "\n"
              + r.getFactory().createPoint(currNode.getCoordinate()));
          continue;
        }
        if (cl.getCoordinateN(0).distance(n.getCoordinate()) <= tolerance) {
          // projection, once snapped, is too close to the supporting vertex
          log("IGNORING\n" + splitAlphaStrip.getGeometry() + "\n" + cl.getPointN(0));
          continue;
        }
        Pair<Polygon, Polygon> split = splitPolygon((Polygon) snapped[0], (LineString) snapped[1]);
        if (split == null) {
          log("SKIPPING\n" + splitAlphaStrip.getGeometry() + "\n" + cl.getPointN(0) + "\n");
          continue;
        }
        // Pair<Polygon, Polygon> split = splitPolygon(splitAlphaStrip.getGeometry(), currNode.getCoordinate(), projection);
        Face gainingBetaSplit = (supportingVertexClass == PREVIOUS) ? prevAlphaStrip : nextAlphaStrip;
        Face loosingBetaSplit = (supportingVertexClass == PREVIOUS) ? nextAlphaStrip : prevAlphaStrip;
        Polygon gainingBetaSplitPolygon = gainingBetaSplit.getGeometry();
        // Polygon loosingBetaSplitPolygon = loosingBetaSplit.getGeometry();
        // log((supportingVertexClass == PREVIOUS)?"PREVIOUS":"NEXT");
        // log("Gaining before:\n" + gainingBetaSplit.getGeometry());
        // log("Loosing before:\n" + loosingBetaSplit.getGeometry());
        // log("LEFT:\n"+split.getLeft());
        // log("RIGHT:\n"+split.getRight());
        Polygon exchangedPolygonPart = (supportingVertexClass == PREVIOUS) ? split.getLeft() : split.getRight();
        // Polygon remainingPolygonPart = (supportingVertexClass == PREVIOUS) ? split.getRight() : split.getLeft();
        // log("Exchanged:\n" + exchangedPolygonPart);
        // log("Remaining:\n" + remainingPolygonPart);
        // List<Polygon> absorbedPolygons = facesToRemove.subList(0, facesToRemove.size() - 1).stream().map(f -> f.getGeometry()).collect(Collectors.toList());
        List<Polygon> newAbsorbing = new ArrayList<>();
        newAbsorbing.add(gainingBetaSplitPolygon);
        newAbsorbing.add(exchangedPolygonPart);
        // absorbedPolygons.add(toRemove);
        // log("absorbing ");
        // newAbsorbing.forEach(f->log(f));
        gainingBetaSplit.setPolygon(Util.polygonUnion(newAbsorbing, precisionReducer));
        // log("ABSORBING:\n" + gainingBetaSplit.getGeometry());
        // loosingBetaSplit.setPolygon(Util.polygonUnion(Arrays.asList(Util.polygonDifference(Arrays.asList(loosingBetaSplitPolygon), absorbedPolygons), remainingPolygonPart)));
        loosingBetaSplit.setPolygon(Util.polygonDifference(Arrays.asList(splitAlphaStripSnapped), Arrays.asList(exchangedPolygonPart)));
        // log("ABSORBED:\n" + loosingBetaSplit.getGeometry());
      }
      // log("DONE");
    }
    export(new TopologicalGraph(alphaStrips.getFaces().stream().map(f -> f.getGeometry()).collect(Collectors.toList())), new File(FOLDER_OUT_DEBUG, "beta_before_snap"));
    TopologicalGraph result = new TopologicalGraph(alphaStrips.getFaces().stream().map(f -> snapped(f.getGeometry())).collect(Collectors.toList()));
    // cleanup strips without exterior edge
    orderedEdges = getOrderedExteriorEdges(result);
    List<Face> toRemove = new ArrayList<>();
    for (Face face : result.getFaces()) {
      long extEdges = face.getEdges().stream().filter(e -> orderedEdges.contains(e)).count();
      if (extEdges == 0) {
        log("REMOVING FACE " + face.getGeometry());
        Optional<Face> f = face.getEdges().stream().filter(e -> e.getTwin() != null).map(e -> new ImmutablePair<Double, Face>(e.getGeometry().getLength(), e.getTwin().getFace()))
            .sorted((a, b) -> Double.compare(b.getLeft(), a.getLeft())).map(p -> p.getRight()).findFirst();
        if (f.isPresent()) {
          Face gainingFace = f.get();
          log("MERGE WITH FACE " + gainingFace.getGeometry());
          gainingFace.setPolygon(Util.polygonUnion(Arrays.asList(face.getGeometry(), gainingFace.getGeometry()), precisionReducer));
          log("RESULT IS " + gainingFace.getGeometry());
          toRemove.add(face);
        }
      }
    }
    result.getFaces().removeAll(toRemove);
    result = new TopologicalGraph(result.getFaces().stream().map(f -> snapped(f.getGeometry())).collect(Collectors.toList()));
    // cleanup strips without exterior edge
    orderedEdges = getOrderedExteriorEdges(result);
    return result;
  }

  private Polygon snapped(Polygon polygon) {
    GeometrySnapper snapper = new GeometrySnapper(polygon);
    return (Polygon) snapper.snapTo(snapGeom, tolerance);
  }

  @SuppressWarnings("rawtypes")
  public static List<Geometry> polygonize(Geometry geometry) {
    LineMerger merger = new LineMerger();
    merger.add(geometry);
    // List lines = LineStringExtracter.getLines(geometry);
    Polygonizer polygonizer = new Polygonizer();
    // log("merged=");
    // for (Object o : merger.getMergedLineStrings()) {
    // log(o);
    // }
    polygonizer.add(merger.getMergedLineStrings());
    Collection polys = polygonizer.getPolygons();
    // if (!polygonizer.getCutEdges().isEmpty()) {
    // log("cut edges");
    // polygonizer.getCutEdges().forEach(e -> log(e));
    // }
    // if (!polygonizer.getDangles().isEmpty()) {
    // log("Dangles");
    // polygonizer.getDangles().forEach(e -> log(e));
    // }
    // if (!polygonizer.getInvalidRingLines().isEmpty()) {
    // log("InvalidRingLines");
    // polygonizer.getInvalidRingLines().forEach(e -> log(e));
    // }
    return Arrays.asList(GeometryFactory.toPolygonArray(polys));
  }

  private static LinearRing snap(LinearRing l, Coordinate c, double tolerance) {
    List<Coordinate> coordinates = new ArrayList<>(l.getNumPoints());
    for (int i = 0; i < l.getNumPoints() - 1; i++) {
      Coordinate c1 = l.getCoordinateN(i), c2 = l.getCoordinateN(i + 1);
      coordinates.add(c1);
      LineSegment segment = new LineSegment(c1, c2);
      double distance = segment.distance(c);
      if (distance <= tolerance && c1.distance(c) > distance && c2.distance(c) > distance) {
        // log("INSERTING " + l.getFactory().createPoint(c));
        coordinates.add(c);
      }
    }
    coordinates.add(l.getCoordinateN(0));// has to be a loop / ring
    return l.getFactory().createLinearRing(coordinates.toArray(new Coordinate[coordinates.size()]));
  }

  private static Polygon snap(Polygon poly, Coordinate c, double tolerance) {
    LinearRing shell = snap((LinearRing) poly.getExteriorRing(), c, tolerance);
    LinearRing[] holes = new LinearRing[poly.getNumInteriorRing()];
    for (int i = 0; i < holes.length; i++) {
      holes[i] = snap((LinearRing) poly.getInteriorRingN(i), c, tolerance);
    }
    return poly.getFactory().createPolygon(shell, holes);
  }

  // private static Polygon snap(Polygon poly, Polygon snapGeom, double tolerance) {
  // GeometrySnapper snapper = new GeometrySnapper(poly);
  // return (Polygon) snapper.snapTo(snapGeom, tolerance);
  // }

  public Pair<Polygon, Polygon> splitPolygon(Polygon poly, Coordinate origin, Coordinate projection) {// LineString line) {
    LineString line = poly.getFactory().createLineString(new Coordinate[] { projection, origin });
    Polygon snapped = snap(poly, projection, tolerance);
    return splitPolygon(snapped, line);
  }

  private int index(List<Coordinate> coords, Coordinate c) {
    if (coords.contains(c))
      return coords.indexOf(c);
    for (int index = 0; index < coords.size(); index++) {
      // we apparently have to make the tolerance larger due to polygonizer
      if (coords.get(index).distance(c) <= tolerance)
        return index;
    }
    return -1;
  }

  /**
   * Get the part of the line between the first and the second point on the border of the polygon (should be snapped before).
   * 
   * @param poly
   * @param line
   * @return
   */
  private LineString part(Polygon poly, LineString line) {
    LineString exterior = poly.getExteriorRing();
    List<Coordinate> coords = new ArrayList<>();
    Coordinate[] inputCoords = line.getCoordinates();
    for (Coordinate c : inputCoords) {
      if (exterior.intersects(line.getFactory().createPoint(c))) {
        coords.add(c);
        if (coords.size() > 1)
          break;
      } else {
        if (!coords.isEmpty()) {
          coords.add(c);
        }
      }
    }
    if (coords.size() < 2) {
      return line;
    }
    return line.getFactory().createLineString(coords.toArray(new Coordinate[coords.size()]));
  }

  public Pair<Polygon, Polygon> splitPolygon(Polygon poly, LineString line) {
    // Polygon snapped = snap(poly, line.getCoordinateN(line.getNumPoints() - 1), 0.01);
    // log("SNAPPED:\n" + poly);
    // log("LINE:\n"+line);
    // log("BOUNDARY:\n"+poly.getBoundary());
    LineString reducedLine = part(poly, line);
    Geometry nodedLinework = poly.getBoundary().union(reducedLine);
    List<Geometry> polys = polygonize(nodedLinework);
    // Only keep polygons which are inside the input
    // List<Polygon> output = polys.stream().map(g -> (Polygon) precisionReducer.reduce(g)).filter(g -> poly.contains(g.getInteriorPoint())).collect(Collectors.toList());
    List<Polygon> output = polys.stream().map(g -> (Polygon) g).filter(g -> poly.contains(g.getInteriorPoint())).collect(Collectors.toList());
    if (output.size() != 2) {
      log("OUTPUT WITH " + output.size());
      log("SPLIT\n" + poly + "\nWITH\n" + line + "\nPART\n" + reducedLine);
      log("NODED\n" + nodedLinework);
      log("POLYGONIZED (" + polys.size() + ")");
      log(polys);
      return null;
    }
    // try to order them from left to right
    // get the coordinates (remove the last, duplicated, point)
    List<Coordinate> coords = Arrays.asList(output.get(0).getExteriorRing().getCoordinates()).subList(0, output.get(0).getExteriorRing().getNumPoints() - 1);
    int index0 = index(coords, reducedLine.getCoordinateN(0));
    int index1 = index(coords, reducedLine.getCoordinateN(1));
    int indexDiff = (index1 - index0 + coords.size()) % coords.size();
    log("SPLIT WITH (" + coords.size() + ")");
    log(reducedLine);
    log(poly.getFactory().createPoint(reducedLine.getCoordinateN(0)));
    log(poly.getFactory().createPoint(reducedLine.getCoordinateN(1)));
    log(output.get(0).getExteriorRing());
    log("index " + index0);
    log("index " + index1);
    log("index diff = " + indexDiff);
    if (indexDiff == 1) {
      // exterior ring is CW so this should be on the right of the line, right?
      log("LEFT=\n" + output.get(1));
      log("RIGHT=\n" + output.get(0));
      return new ImmutablePair<>(output.get(1), output.get(0));
    }
    log("LEFT=\n" + output.get(0));
    log("RIGHT=\n" + output.get(1));
    return new ImmutablePair<>(output.get(0), output.get(1));
  }

  public static int sharedPoints(Geometry a, Geometry b) {
    Geometry[] snapped = GeometrySnapper.snap(a, b, 0.1);
    Set<Coordinate> ca = new HashSet<>(Arrays.asList(snapped[0].getCoordinates()));
    Set<Coordinate> cb = new HashSet<>(Arrays.asList(snapped[1].getCoordinates()));
    return (int) ca.stream().filter(c -> cb.contains(c)).count();
  }

  public static void main(String[] args) throws IOException, SchemaException {
    // File rootFolder = new File("src/main/resources/GeneralTest/");
    // File roadFile = new File(rootFolder, "road.shp");
    // File parcelFile = new File(rootFolder, "parcel.shp");
    // String inputParcelShapeFile = "/home/julien/data/PLU_PARIS/ilots_13.shp";
    // String inputRoadShapeFile = "/home/julien/data/PLU_PARIS/voie/voie_l93.shp";

    File roadFile = new File("/home/julien/data/PLU_PARIS/voie/voie_l93.shp");
    File parcelFile = new File("/home/julien/data/PLU_PARIS/ilots_13.shp");
    // String NAME_ATT_IMPORTANCE = "Type";
    // String NAME_ATT_ROAD = "Id";
    String NAME_ATT_ROAD = "l_voie";
    String NAME_ATT_IMPORTANCE = "length";
    String folderOut = "data/";
    double maxDepth = 20, maxDistanceForNearestRoad = 1000, minimalArea = 20, minWidth = 2, maxWidth = 5, omega = 0.1;
    RandomGenerator rng = new MersenneTwister(42);
    // The output file that will contain all the decompositions
    // String shapeFileOut = folderOut + "outflag.shp";
    (new File(folderOut)).mkdirs();
    // Reading collection
    ShapefileDataStore parcelDS = new ShapefileDataStore(parcelFile.toURI().toURL());
    SimpleFeatureCollection parcels = parcelDS.getFeatureSource().getFeatures();
    ShapefileDataStore roadDS = new ShapefileDataStore(roadFile.toURI().toURL());
    SimpleFeatureCollection roads = roadDS.getFeatureSource().getFeatures();
    SimpleFeatureIterator iterator = parcels.features();
    SimpleFeature feature = null;
    int count = 0;
    List<Polygon> globalOutputParcels = new ArrayList<>();
    log("wE SHOULD BE HANDLING " + parcels.size() + " POLYGONS");
    while (iterator.hasNext()) {
      feature = iterator.next();
      // if (feature.getAttribute("NUMERO").equals("0024") && feature.getAttribute("SECTION").equals("ZA")) break;
      List<Polygon> polygons = Util.getPolygons((Geometry) feature.getDefaultGeometry());
      for (Polygon polygon : polygons) {
        try {
          FOLDER_OUT_DEBUG = "/tmp/skeleton/polygon_" + count;
          log("start with polygon " + count);
          TopologicalStraightSkeletonParcelDecomposition decomposition = new TopologicalStraightSkeletonParcelDecomposition(polygon, roads, NAME_ATT_ROAD, NAME_ATT_IMPORTANCE,
              maxDepth, maxDistanceForNearestRoad, minimalArea);
          List<Polygon> outputParcels = decomposition.createParcels(minWidth, maxWidth, omega, rng);
          globalOutputParcels.addAll(outputParcels);
          // log("OUTPUT/");
          // outputParcels.forEach(p -> log(p));
          // log("/OUTPUT");
          count++;
        } catch (Exception e) {
          // TODO Auto-generated catch block
          // log("OUTPUT/");
          // globalOutputParcels.forEach(p -> log(p));
          // log("/OUTPUT");
          e.printStackTrace();
          break;
        }
      }
    }
    iterator.close();
    roadDS.dispose();
    parcelDS.dispose();
    log("OUTPUT/");
    globalOutputParcels.forEach(p -> log(p));
    log("/OUTPUT");
  }

  private static void export(TopologicalGraph graph, File directory) {
    for (int id = 0; id < graph.getFaces().size(); id++)
      graph.getFaces().get(id).setAttribute("ID", id);
    List<Node> nodes = new ArrayList<>(graph.getNodes());
    for (int id = 0; id < graph.getNodes().size(); id++)
      nodes.get(id).setAttribute("ID", id);
    for (int id = 0; id < graph.getEdges().size(); id++) {
      HalfEdge edge = graph.getEdges().get(id);
      edge.setAttribute("ID", id);
      edge.setAttribute("ORIGIN", edge.getOrigin().getAttribute("ID"));
      edge.setAttribute("TARGET", edge.getTarget().getAttribute("ID"));
      edge.setAttribute("FACE", edge.getFace().getAttribute("ID"));
    }
    for (int id = 0; id < graph.getEdges().size(); id++) {
      HalfEdge edge = graph.getEdges().get(id);
      edge.setAttribute("TWIN", (edge.getTwin() != null) ? edge.getTwin().getAttribute("ID") : null);
      edge.setAttribute("NEXT", (edge.getNext() != null) ? edge.getNext().getAttribute("ID") : null);
    }
    if (!directory.isDirectory())
      directory.mkdirs();
    TopologicalGraph.export(graph.getFaces(), new File(directory, "faces.shp"), "Polygon");
    TopologicalGraph.export(graph.getEdges(), new File(directory, "edges.shp"), "LineString");
    TopologicalGraph.export(graph.getNodes(), new File(directory, "nodes.shp"), "Point");
  }
}
class EdgeException extends Exception {
  /**
   * 
   */
  private static final long serialVersionUID = -8778686157065646491L;
}
class StraightSkeletonException extends Exception {

  /**
   * 
   */
  private static final long serialVersionUID = -6217346421144071706L;

}