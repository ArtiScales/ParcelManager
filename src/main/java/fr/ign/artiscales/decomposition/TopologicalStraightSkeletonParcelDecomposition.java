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
  public static String FOLDER_OUT_DEBUG = "/tmp/skeleton_100";

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

  private static Pair<String, Double> getAttributes(SimpleFeature s) {
    String name = s.getAttribute(NAME_ATT_ROAD).toString();
    String impo = s.getAttribute(NAME_ATT_IMPORTANCE).toString();
    return new ImmutablePair<>(name, Double.parseDouble(impo.replaceAll(",", ".")));
  }

  private static List<HalfEdge> getOrderedExteriorEdges(StraightSkeleton straightSkeleton) {
    // TODO would having to order the exterior edges justify creating the infinite face?
    // FIXME here is a hack to get the exterior edges. This is ugly
    List<HalfEdge> edges = straightSkeleton.getExteriorEdges().stream().filter(e -> !straightSkeleton.getInputPolygon().buffer(-0.1).contains(e.getGeometry()))
        .collect(Collectors.toList());
    // edges.forEach(e -> System.out.println(e.getGeometry()));
    straightSkeleton.getGraph().getEdges().forEach(e -> e.setAttribute("EXTERIOR", "false"));
    edges.forEach(e -> e.setAttribute("EXTERIOR", "true"));
    straightSkeleton.getGraph().getEdges().iterator().next().getAttributes().forEach(a -> System.out.println("attribute " + a));
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

  private static TopologicalGraph getAlphaStrips(TopologicalGraph graph, List<HalfEdge> orderedEdges, Map<HalfEdge, Optional<Pair<String, Double>>> attributes) {
    TopologicalGraph alphaStripGraph = new TopologicalGraph();
    HalfEdge firstHE = orderedEdges.get(0);
    Optional<Pair<String, Double>> firstAttributes = attributes.get(firstHE);
    HalfEdge currentStripHE = new HalfEdge(firstHE.getOrigin(), null, null);
    currentStripHE.getChildren().add(firstHE);
    alphaStripGraph.getEdges().add(currentStripHE);
    for (int i = 0; i < orderedEdges.size() - 1; i++) {
      HalfEdge e1 = orderedEdges.get(i), e2 = orderedEdges.get((i + 1) % attributes.size());
      Optional<Pair<String, Double>> a1 = attributes.get(e1), a2 = attributes.get(e2);
      if (a1.map(p -> p.getLeft()).orElse("").equals(a2.map(p -> p.getLeft()).orElse(""))) {
        currentStripHE.getChildren().add(e2);
      } else {
        Node node = graph.getCommonNode(e1, e2);// FIXME
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
      Node node = graph.getCommonNode(lastHE, firstHE);// FIXME
      currentStripHE.setTarget(node);
      alphaStripGraph.addNode(node);
    }
    System.out.println("Strips = " + alphaStripGraph.getEdges().size());
    for (HalfEdge strip : alphaStripGraph.getEdges()) {
      List<HalfEdge> edges = strip.getChildren();
      LineString l = Util.union(edges.stream().map(e -> e.getGeometry()).collect(Collectors.toList()));
      System.out.println(l);
      strip.setLine(l);
    }
    for (HalfEdge strip : alphaStripGraph.getEdges()) {
      Face stripFace = new Face();
      List<Face> stripFaces = strip.getChildren().stream().map(e -> e.getFace()).collect(Collectors.toList());
      stripFace.getChildren().addAll(stripFaces);
      stripFaces.forEach(f -> f.setParent(stripFace));
      List<Polygon> polygons = stripFaces.stream().map(f -> f.getGeometry()).collect(Collectors.toList());
      stripFace.setPolygon(Util.polygonUnionWithoutHoles(polygons));
      strip.setFace(stripFace);
      alphaStripGraph.getFaces().add(stripFace);
      System.out.println(stripFace.getGeometry());
    }
    return alphaStripGraph;
  }

  public static List<Polygon> decompose(Polygon p, SimpleFeatureCollection roads, double offsetDistance, double maxDistanceForNearestRoad, double minimalArea, double minWidth,
      double maxWidth, double omega, RandomGenerator rng) throws SchemaException {
    // Partial skeleton
    Polygon pol = (Polygon) GeometryPrecisionReducer.reduce(p, new PrecisionModel(100));
    StraightSkeleton straightSkeleton = new StraightSkeleton(pol, offsetDistance);
    TopologicalGraph graph = straightSkeleton.getGraph();
    graph.getFaces().forEach(f -> System.out.println(f.getGeometry()));
    List<HalfEdge> orderedEdges = getOrderedExteriorEdges(straightSkeleton);
    export(graph, new File(FOLDER_OUT_DEBUG, "init"));
    // get the road attributes
    Map<HalfEdge, Optional<Pair<String, Double>>> attributes = new HashMap<>();
    orderedEdges.stream().forEach(e -> attributes.put(e, FindObjectInDirection.find(e.getGeometry(), pol, roads, maxDistanceForNearestRoad).map(s -> getAttributes(s))));
    TopologicalGraph alphaStrips = getAlphaStrips(graph, orderedEdges, attributes);
    export(alphaStrips, new File(FOLDER_OUT_DEBUG, "alpha"));
    TopologicalGraph betaStrips = getBetaStrips(graph, orderedEdges, alphaStrips, attributes);
    export(betaStrips, new File(FOLDER_OUT_DEBUG, "beta"));
    return createParcels(straightSkeleton, betaStrips, (Polygon) pol.buffer(-0.1), minWidth, maxWidth, omega, rng);
  }

  private static LineString getPerpendicular(Coordinate c1, Coordinate c2, GeometryFactory factory, boolean left) {
    double dx = c2.x - c1.x, dy = c2.y - c1.y;
    double x = left ? -1 : 1, y = left ? 1 : -1;
    double length = Math.sqrt(dx * dx + dy * dy);
    return factory.createLineString(new Coordinate[] { c1, new Coordinate(c1.x + x * dy / length, c1.y + y * dx / length) });
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

  private static Polygon createParcel(StraightSkeleton straightSkeleton, TopologicalGraph betaStrips, LineString line) {
    List<HalfEdge> edges = straightSkeleton.getIncludedEdges();
    Coordinate c1 = line.getCoordinateN(0);
    Coordinate c2 = line.getCoordinateN(1);
    Coordinate c3 = line.getCoordinateN(line.getNumPoints() - 2);
    Coordinate c4 = line.getCoordinateN(line.getNumPoints() - 1);
    Node n1 = straightSkeleton.getGraph().getNode(c1);
    if (n1 != null) {

    }
    Node n2 = straightSkeleton.getGraph().getNode(c4);
    LineString l1 = getPerpendicular(c1, c2, line.getFactory(), true);
    System.out.println(l1);
    LineString l2 = getPerpendicular(c4, c3, line.getFactory(), false);
    System.out.println(l2);
    return null;
  }

  private static List<Double> sampleWidths(double length, RealDistribution nd, double minWidth, double maxWidth) {
    List<Double> widths = new ArrayList<>();
    double sum = 0;
    while (sum < length) {
      double sample = nd.sample();
      if (sum + sample > length) {
        double remaining = length - sum;
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

  private static Pair<HalfEdge, Coordinate> getIntersection(StraightSkeleton straightSkeleton, Coordinate o, Coordinate d) {
    Point p = straightSkeleton.getInputPolygon().getFactory().createPoint(o);
    List<Face> faces = straightSkeleton.getGraph().getFaces().stream().filter(f -> f.getGeometry().intersects(p)).collect(Collectors.toList());
    if (faces.size() != 1) {
      // System.out.println("found " + faces.size() + " faces intersecting " + p);
      faces = straightSkeleton.getGraph().getFaces().stream().filter(f -> f.getGeometry().intersects(p.buffer(0.01))).collect(Collectors.toList());
      if (faces.size() != 1) {
        System.out.println("found " + faces.size() + " faces intersecting " + p + " again");
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

  private static Coordinate getCoordinate(List<Coordinate> stripCoordinates, Coordinate coord, double d) {
    List<Coordinate> coords = stripCoordinates.stream().filter(c -> c.distance(coord) < 0.01).collect(Collectors.toList());
    if (coords.isEmpty())
      return null;
    return coords.get(0);// FIXME check if there is more than one?
  }

  private static double getAngle(Coordinate c1, Coordinate c2, Coordinate d) {
    return Angle.angleBetween(c2, c1, new Coordinate(c1.x + d.x, c1.y + d.y));
  }

  private static List<Coordinate> getPath(StraightSkeleton straightSkeleton, Polygon strip, Node node, Coordinate direction) {
    // Coordinate stripCoordinate = getCoordinate(stripCoordinates, node.getCoordinate(), 0.01);
    // if (stripCoordinate != null)
    // return Arrays.asList(stripCoordinate);// we reached the border of the strip
    if (strip.getExteriorRing().distance(strip.getFactory().createPoint(node.getCoordinate())) < 0.01) {
      // we are 'on' the border of the strip
      Coordinate projection = Util.project(node.getCoordinate(), strip.getExteriorRing());
      return Arrays.asList(projection);
    }
    // find the best edge to follow
    List<HalfEdge> edges = straightSkeleton.getGraph().outgoingEdgesOf(node, straightSkeleton.getGraph().getEdges());
    edges.sort((HalfEdge h1, HalfEdge h2) -> Double.compare(getAngle(h1.getOrigin().getCoordinate(), h1.getTarget().getCoordinate(), direction),
        getAngle(h2.getOrigin().getCoordinate(), h2.getTarget().getCoordinate(), direction)));
    HalfEdge edge = edges.get(0);
    List<Coordinate> path = new ArrayList<>();
    path.add(node.getCoordinate());
    Coordinate d = getUnitVector(node.getCoordinate(), edge.getTarget().getCoordinate());
    path.addAll(getPath(straightSkeleton, strip, edge.getTarget(), d));
    return path;
  }

  private static LineString getCutLine(StraightSkeleton straightSkeleton, Polygon pol, Polygon strip, Coordinate coordinate, LineString support) {
    Node node = straightSkeleton.getGraph().getNode(coordinate, 0.01);
    if (node == null) {
      // no node here, compute perpendicular line
      Coordinate c1 = support.getCoordinateN(1);
      Coordinate c2 = support.getCoordinateN(0);
      Coordinate d = getPerpendicularVector(c1, c2, false);
      Pair<HalfEdge, Coordinate> intersection = getIntersection(straightSkeleton, c2, d);
      Coordinate intersectionCoord = intersection.getRight();
      if (strip.getExteriorRing().distance(strip.getFactory().createPoint(intersectionCoord)) < 0.01) {
        // we are 'on' the border of the strip
        Coordinate projection = Util.project(intersectionCoord, strip.getExteriorRing());
        return pol.getFactory().createLineString(new Coordinate[] { c2, projection });
      }
      // Coordinate stripCoordinate = getCoordinate(stripCoordinates, intersection.getRight(), 0.01);
      // if (stripCoordinate != null) {
      // return pol.getFactory().createLineString(new Coordinate[] { c2, stripCoordinate });
      // }
      List<Coordinate> coordinateList = new ArrayList<>();
      coordinateList.add(c2);
      coordinateList.add(intersection.getRight());
      Node currentNode = straightSkeleton.getGraph().getNode(intersectionCoord, 0.01);
      if (currentNode == null) {
        double angle = getAngle(intersection.getLeft().getOrigin().getCoordinate(), intersection.getLeft().getTarget().getCoordinate(), d);
        currentNode = (angle < Math.PI / 2) ? intersection.getLeft().getTarget() : intersection.getLeft().getOrigin();
        coordinateList.add(currentNode.getCoordinate());
        d = getUnitVector(intersection.getRight(), currentNode.getCoordinate());
      }
      coordinateList.addAll(getPath(straightSkeleton, strip, currentNode, d));
      return pol.getFactory().createLineString(coordinateList.toArray(new Coordinate[coordinateList.size()]));
      // System.out.println(pol.getFactory().createPoint(intersection.getRight()));
      // System.out.println(pol.getFactory().createLineString(new Coordinate[] { c2, intersection.getRight() }));
      // System.out.println(pol.getFactory().createLineString(new Coordinate[] {intersection.getRight(), intersection.getLeft().getTarget().getCoordinate()}));
    }
    List<HalfEdge> edges = straightSkeleton.getGraph().getEdges().stream().filter(h -> h.getOrigin() == node && h.getTwin() != null).collect(Collectors.toList());
    if (edges.size() != 1) {
      System.out.println("found " + edges.size() + " edges starting at " + node.getGeometry());
    } else {
      HalfEdge edge = edges.get(0);
      List<Coordinate> coordinateList = new ArrayList<>();
      Coordinate c1 = edge.getOrigin().getCoordinate();
      Coordinate c2 = edge.getTarget().getCoordinate();
      coordinateList.add(c1);
      coordinateList.add(c2);
      Coordinate d = getUnitVector(c1, c2);
      coordinateList.addAll(getPath(straightSkeleton, strip, node, d));
      return pol.getFactory().createLineString(coordinateList.toArray(new Coordinate[coordinateList.size()]));
      // System.out.println(edges.get(0).getGeometry());
    }
    return null;
  }

  private static List<Polygon> slice(StraightSkeleton straightSkeleton, Polygon pol, double minWidth, double maxWidth, RealDistribution nd, RandomGenerator rng, Face strip) {
    List<Polygon> result = new ArrayList<>();
    List<HalfEdge> extEdges = strip.getEdges().stream().filter(e -> e.getTwin() == null && !pol.contains(e.getGeometry())).collect(Collectors.toList());
    // List<Coordinate> stripCoordinates = Arrays.asList(strip.getGeometry().getCoordinates());
    // System.out.println("Face\n" + face.getGeometry());
    LineMerger lsm = new LineMerger();
    extEdges.stream().forEach(e -> lsm.add(e.getGeometry()));
    Collection<?> merged = lsm.getMergedLineStrings();
    if (merged.size() == 1) {
      // FIXME check the order of that linestring: should be CCW
      LineString phi = (LineString) merged.iterator().next();
      LengthIndexedLine lil = new LengthIndexedLine(phi);
      double length = phi.getLength();
      List<Double> widths = sampleWidths(length, nd, minWidth, maxWidth);
      if (widths.size() == 1) {
        result.add(strip.getGeometry());
      } else {
        double previousL = widths.get(0);
        // split the strip now
        Polygon remainder = (Polygon) strip.getGeometry().copy();
        // we remove the last width
        for (double w : widths.subList(1, widths.size())) {
          double current = previousL + w;
          LineString l = (LineString) lil.extractLine(previousL, current);
          // get perpendicular line
          Coordinate coordinate = lil.extractPoint(previousL);
          LineString cutLine = getCutLine(straightSkeleton, pol, remainder, coordinate, l);
          Polygon r = (Polygon) GeometryPrecisionReducer.reduce(remainder, new PrecisionModel(100));
          LineString cl = (LineString) GeometryPrecisionReducer.reduce(cutLine, new PrecisionModel(100));
          Geometry[] snapped = GeometrySnapper.snap(r, cl, 0.01);
          Pair<Polygon, Polygon> split = splitPolygon((Polygon) snapped[0], (LineString) snapped[1], false);
          result.add(split.getLeft());
          remainder = split.getRight();
          previousL = current;
        }
        result.add(remainder);
      }
    } else {
      System.out.println("MORE THAN ONE SEGMENT FOR BETA STRIP (" + merged.size() + ")");
    }
    return result;
  }

  private static List<Polygon> createParcels(StraightSkeleton straightSkeleton, TopologicalGraph betaStrips, Polygon pol, double minWidth, double maxWidth, double omega,
      RandomGenerator rng) {
    NormalDistribution nd = new NormalDistribution(rng, (minWidth + maxWidth) / 2, Math.sqrt(3 * omega));
    List<Polygon> result = new ArrayList<>();
    betaStrips.getFaces().forEach(f->result.addAll(slice(straightSkeleton, pol, minWidth, maxWidth, nd, rng, f)));
    return result;
  }

  private static TopologicalGraph getBetaStrips(TopologicalGraph graph, List<HalfEdge> orderedEdges, TopologicalGraph alphaStrips,
      Map<HalfEdge, Optional<Pair<String, Double>>> attributes) {
    System.out.println("Supporting vertices");
    // classify supporting vertices
    for (Node n : alphaStrips.getNodes()) {
      System.out.println(n.getGeometry());
      HalfEdge previousEdge = graph.incomingEdgesOf(n, orderedEdges).get(0);
      HalfEdge nextEdge = graph.outgoingEdgesOf(n, orderedEdges).get(0);
      Optional<Pair<String, Double>> previousAttributes = attributes.get(previousEdge);
      Optional<Pair<String, Double>> nextAttributes = attributes.get(nextEdge);
      int supportingVertexClass = classify(n, previousEdge, previousAttributes, nextEdge, nextAttributes);
      Face prevFace = previousEdge.getFace(), nextFace = nextEdge.getFace();
      Face prevAlphaStrip = prevFace.getParent(), nextAlphaStrip = nextFace.getParent();
      // FIXME use parent edge children
      // get all edges connecting the current and previous strips (they form the diagonal edge)
      List<HalfEdge> edgeList = graph.getEdges().stream().filter(e -> e.getTwin() != null)
          .filter(e -> (e.getFace().getParent() == prevAlphaStrip && e.getTwin().getFace().getParent() == nextAlphaStrip)).collect(Collectors.toList());
      edgeList.forEach(e -> System.out.println(e.getGeometry()));
      // create the complete ordered list of edges forming the diagonal edge
      List<HalfEdge> diagonalEdgeList = new ArrayList<>();
      // get the next outgoing edge from the current node belonging to the edge list
      HalfEdge currEdge = graph.next(n, null, edgeList);
      System.out.println("currEdge\n" + currEdge.getGeometry());
      Node currNode = n;
      while (currEdge != null) {
        currNode = currEdge.getTarget();
        diagonalEdgeList.add(currEdge);
        currEdge = graph.next(currNode, currEdge, edgeList);
        // currEdge = currEdge.getNext();
      }
      System.out.println(currNode.getGeometry());
      if (supportingVertexClass != NONE) {
        final Face splitAlphaStrip = (supportingVertexClass == PREVIOUS) ? nextAlphaStrip : prevAlphaStrip;
        // FIXME always getFace()?
//        List<Face> facesToRemove = diagonalEdgeList.stream().map(e -> (e.getFace().getParent() == splitAlphaStrip) ? e.getFace() : e.getTwin().getFace())
//            .collect(Collectors.toList());
//        System.out.println("faces to remove");
//        facesToRemove.forEach(f->System.out.println(f.getGeometry()));
        // FIXME check: only one edge for alpha strips?
//        Coordinate projection = Util.project(currNode.getCoordinate(), splitAlphaStrip.getEdges().get(0).getGeometry());
//        Polygon toRemove = facesToRemove.get(facesToRemove.size() - 1).getGeometry();
//        Pair<Polygon, Polygon> split = splitPolygon(toRemove, currNode.getCoordinate(), projection);
        // toRemove.getFactory().createLineString(new Coordinate[] {currNode.getCoordinate(), projection }));
        // TODO check that the first edge is always the supporting edge
        Coordinate projection = Util.project(currNode.getCoordinate(), splitAlphaStrip.getEdges().get(0).getGeometry());
        Polygon splitAlphaStripSnapped = snap(splitAlphaStrip.getGeometry(),projection, 0.01);
        Polygon r = (Polygon) GeometryPrecisionReducer.reduce(splitAlphaStripSnapped, new PrecisionModel(100));
        LineString cl = (LineString) GeometryPrecisionReducer.reduce(r.getFactory().createLineString(new Coordinate[] {projection, currNode.getCoordinate()}), new PrecisionModel(100));
        Geometry[] snapped = GeometrySnapper.snap(r, cl, 0.01);
        Pair<Polygon, Polygon> split = splitPolygon((Polygon) snapped[0], (LineString) snapped[1], false);
//        Pair<Polygon, Polygon> split = splitPolygon(splitAlphaStrip.getGeometry(), currNode.getCoordinate(), projection);
        Face gainingBetaSplit = (supportingVertexClass == PREVIOUS) ? prevAlphaStrip : nextAlphaStrip;
        Face loosingBetaSplit = (supportingVertexClass == PREVIOUS) ? nextAlphaStrip : prevAlphaStrip;
        Polygon gainingBetaSplitPolygon = gainingBetaSplit.getGeometry();
        Polygon loosingBetaSplitPolygon = loosingBetaSplit.getGeometry();
        System.out.println((supportingVertexClass == PREVIOUS)?"PREVIOUS":"NEXT");
        System.out.println("Gaining before:\n" + gainingBetaSplit.getGeometry());
        System.out.println("Loosing before:\n" + loosingBetaSplit.getGeometry());
        System.out.println("LEFT:\n"+split.getLeft());
        System.out.println("RIGHT:\n"+split.getRight());
        Polygon exchangedPolygonPart = (supportingVertexClass == PREVIOUS) ? split.getLeft() : split.getRight();
        Polygon remainingPolygonPart = (supportingVertexClass == PREVIOUS) ? split.getRight() : split.getLeft();
        System.out.println("Exchanged:\n" + exchangedPolygonPart);
        System.out.println("Remaining:\n" + remainingPolygonPart);
//        List<Polygon> absorbedPolygons = facesToRemove.subList(0, facesToRemove.size() - 1).stream().map(f -> f.getGeometry()).collect(Collectors.toList());
        List<Polygon> newAbsorbing = new ArrayList<>();
        newAbsorbing.add(gainingBetaSplitPolygon);
        newAbsorbing.add(exchangedPolygonPart);
//        absorbedPolygons.add(toRemove);
        System.out.println("absorbing ");
        newAbsorbing.forEach(f->System.out.println(f));
        gainingBetaSplit.setPolygon(Util.polygonUnion(newAbsorbing));
        System.out.println("ABSORBING:\n" + gainingBetaSplit.getGeometry());
//        loosingBetaSplit.setPolygon(Util.polygonUnion(Arrays.asList(Util.polygonDifference(Arrays.asList(loosingBetaSplitPolygon), absorbedPolygons), remainingPolygonPart)));
        loosingBetaSplit.setPolygon(Util.polygonDifference(Arrays.asList(splitAlphaStripSnapped), Arrays.asList(exchangedPolygonPart)));
        System.out.println("ABSORBED:\n" + loosingBetaSplit.getGeometry());
      }
      System.out.println("DONE");
    }
    return new TopologicalGraph(alphaStrips.getFaces().stream().map(f -> f.getGeometry()).collect(Collectors.toList()));
  }

  @SuppressWarnings("rawtypes")
  public static List<Geometry> polygonize(Geometry geometry, boolean force) {
    LineMerger merger = new LineMerger();
    merger.add(geometry);
    // List lines = LineStringExtracter.getLines(geometry);
    Polygonizer polygonizer = new Polygonizer(force);
    System.out.println("merged=");
    for (Object o : merger.getMergedLineStrings()) {
      System.out.println(o);
    }
    polygonizer.add(merger.getMergedLineStrings());
    Collection polys = polygonizer.getPolygons();
    if (!polygonizer.getCutEdges().isEmpty()) {
      System.out.println("cut edges");
      polygonizer.getCutEdges().forEach(e -> System.out.println(e));
    }
    if (!polygonizer.getDangles().isEmpty()) {
      System.out.println("Dangles");
      polygonizer.getDangles().forEach(e -> System.out.println(e));
    }
    if (!polygonizer.getInvalidRingLines().isEmpty()) {
      System.out.println("InvalidRingLines");
      polygonizer.getInvalidRingLines().forEach(e -> System.out.println(e));
    }
    return Arrays.asList(GeometryFactory.toPolygonArray(polys));
  }

  private static LinearRing snap(LinearRing l, Coordinate c, double tolerance) {
    List<Coordinate> coordinates = new ArrayList<>(l.getNumPoints());
    for (int i = 0; i < l.getNumPoints() - 1; i++) {
      Coordinate c1 = l.getCoordinateN(i), c2 = l.getCoordinateN(i + 1);
      coordinates.add(c1);
      LineSegment segment = new LineSegment(c1, c2);
      double distance = segment.distance(c);
      if (distance < tolerance && c1.distance(c) > distance && c2.distance(c) > distance) {
        System.out.println("INSERTING " + l.getFactory().createPoint(c));
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

  private static Polygon snap(Polygon poly, Polygon snapGeom, double tolerance) {
    GeometrySnapper snapper = new GeometrySnapper(poly);
    return (Polygon) snapper.snapTo(snapGeom, tolerance);
  }

  public static Pair<Polygon, Polygon> splitPolygon(Polygon poly, Coordinate origin, Coordinate projection) {// LineString line) {
    LineString line = poly.getFactory().createLineString(new Coordinate[] { projection, origin });
    Polygon snapped = snap(poly, projection, 0.01);
    return splitPolygon(snapped, line, false);
  }

  public static Pair<Polygon, Polygon> splitPolygon(Polygon poly, LineString line, boolean force) {
    // Polygon snapped = snap(poly, line.getCoordinateN(line.getNumPoints() - 1), 0.01);
    System.out.println("SNAPPED:\n" + poly);
    System.out.println("LINE:\n"+line);
    System.out.println("BOUNDARY:\n"+poly.getBoundary());
    Geometry nodedLinework = poly.getBoundary().union(line);
    List<Geometry> polys = polygonize(nodedLinework, force);
    // Only keep polygons which are inside the input
    List<Polygon> output = polys.stream().map(g -> (Polygon) g).filter(g -> poly.contains(g.getInteriorPoint())).collect(Collectors.toList());
    if (output.size() != 2) {
      System.out.println("OUTPUT WITH " + output.size());
      System.out.println("SPLIT WITH");
      System.out.println(line);
      System.out.println("NODED");
      System.out.println(nodedLinework);
      System.out.println("POLYGONIZED (" + polys.size() + ")");
      System.out.println(polys);
      return null;
    }
    // try to order them from left to right
    // get the coordinates (remove the last, duplicated, point)
    List<Coordinate> coords = Arrays.asList(output.get(0).getExteriorRing().getCoordinates()).subList(0, output.get(0).getExteriorRing().getNumPoints() - 1);
    int index0 = coords.indexOf(line.getCoordinateN(0));
    int index1 = coords.indexOf(line.getCoordinateN(1));
    int indexDiff = (index1 - index0 + coords.size()) % coords.size();
    System.out.println("SPLIT WITH (" + coords.size() + ")");
    System.out.println(line);
    System.out.println(poly.getFactory().createPoint(line.getCoordinateN(0)));
    System.out.println(poly.getFactory().createPoint(line.getCoordinateN(1)));
    System.out.println("index " + index0);
    System.out.println("index " + index1);
    System.out.println("index diff = " + indexDiff);
    if (indexDiff == 1) {
      // exterior ring is CW so this should be on the right of the line, right?
      System.out.println("LEFT=\n" + output.get(1));
      System.out.println("RIGHT=\n" + output.get(0));
      return new ImmutablePair<>(output.get(1), output.get(0));
    }
    System.out.println("LEFT=\n" + output.get(0));
    System.out.println("RIGHT=\n" + output.get(1));
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
    // String shapeFileOut = folderOut + "outflag.shp";
    (new File(folderOut)).mkdirs();
    // Reading collection
    ShapefileDataStore parcelDS = new ShapefileDataStore(new File(inputParcelShapeFile).toURI().toURL());
    SimpleFeatureCollection parcels = parcelDS.getFeatureSource().getFeatures();
    ShapefileDataStore roadDS = new ShapefileDataStore(new File(inputRoadShapeFile).toURI().toURL());
    SimpleFeatureCollection roads = roadDS.getFeatureSource().getFeatures();
    SimpleFeatureIterator iterator = parcels.features();
    SimpleFeature feature = iterator.next();
    List<Polygon> polygons = Util.getPolygons((Geometry) feature.getDefaultGeometry());
    double maxDepth = 10, maxDistanceForNearestRoad = 100, minimalArea = 20, minWidth = 2, maxWidth = 5, omega = 0.1;
    iterator.close();
    List<Polygon> outputParcels = decompose(polygons.get(0), roads, maxDepth, maxDistanceForNearestRoad, minimalArea, minWidth, maxWidth, omega, new MersenneTwister(42));
    System.out.println("OUTPUT PARCELS");
    outputParcels.forEach(p -> System.out.println(p));
    roadDS.dispose();
    parcelDS.dispose();
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
