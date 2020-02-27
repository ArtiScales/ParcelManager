package decomposition;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
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
import org.locationtech.jts.geom.LineSegment;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.util.LineStringExtracter;
import org.locationtech.jts.linearref.LengthIndexedLine;
import org.locationtech.jts.operation.linemerge.LineMerger;
import org.locationtech.jts.operation.overlay.snap.GeometrySnapper;
import org.locationtech.jts.operation.polygonize.Polygonizer;
import org.opengis.feature.simple.SimpleFeature;

import com.google.common.collect.Lists;

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

  public static SimpleFeatureCollection decompose(Polygon pol, SimpleFeatureCollection roads, double offsetDistance, double maxDistanceForNearestRoad, double minimalArea,
      double minWidth, double maxWidth, double noiseParameter, RandomGenerator rng) throws SchemaException {
    // Partial skeleton
    StraightSkeleton straightSkeleton = new StraightSkeleton(pol, offsetDistance);
    TopologicalGraph graph = straightSkeleton.getGraph();
    export(graph, new File("/tmp/init"));
    graph.getFaces().forEach(f -> System.out.println(f.getGeometry()));
    List<HalfEdge> orderedEdges = getOrderedExteriorEdges(straightSkeleton);
    // get the road attributes
    Map<HalfEdge, Optional<Pair<String, Double>>> attributes = new HashMap<>();
    orderedEdges.stream().forEach(e -> attributes.put(e, FindObjectInDirection.find(e.getGeometry(), pol, roads, maxDistanceForNearestRoad).map(s -> getAttributes(s))));
    TopologicalGraph alphaStrips = getAlphaStrips(graph, orderedEdges, attributes);
    export(alphaStrips, new File("/tmp/alpha"));
    TopologicalGraph betaStrips = getBetaStrips(graph, orderedEdges, alphaStrips, attributes);
    export(betaStrips, new File("/tmp/beta"));
    List<Polygon> parcels = createParcels(straightSkeleton, betaStrips, (Polygon) pol.buffer(-0.1), minWidth, maxWidth, noiseParameter, rng);
    return null;
  }

  private static LineString getPerpendicular(Coordinate c1, Coordinate c2, GeometryFactory factory, boolean left) {
    double dx = c2.x - c1.x, dy = c2.y - c1.y;
    double x = left ? -1 : 1, y = left ? 1 : -1;
    double length = Math.sqrt(dx * dx + dy * dy);
    return factory.createLineString(new Coordinate[] { c1, new Coordinate(c1.x + x * dy / length, c1.y + y * dx / length) });
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

  private static List<Polygon> createParcels(StraightSkeleton straightSkeleton, TopologicalGraph betaStrips, Polygon pol, double minWidth, double maxWidth,
      double splitIrregularity, RandomGenerator rng) {
    double mean = (minWidth + maxWidth) / 2;
    double sd = Math.sqrt(3 * splitIrregularity);
    NormalDistribution nd = new NormalDistribution(rng, mean, sd);
    List<Polygon> result = new ArrayList<>();
    for (Face face : betaStrips.getFaces()) {
      List<HalfEdge> extEdges = face.getEdges().stream().filter(e -> e.getTwin() == null && !pol.contains(e.getGeometry())).collect(Collectors.toList());
      System.out.println("Face\n" + face.getGeometry());
      LineMerger lsm = new LineMerger();
      extEdges.stream().forEach(e -> lsm.add(e.getGeometry()));
      Collection<?> merged = lsm.getMergedLineStrings();
      if (merged.size() == 1) {
        // FIXME check the order of that linestring: should be CCW
        LineString phi = (LineString) merged.iterator().next();
        LengthIndexedLine lil = new LengthIndexedLine(phi);
        double length = phi.getLength();
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
        if (widths.size() == 1) {
          result.add(face.getGeometry());
        } else {
//          Node n = straightSkeleton.getGraph().getNode(c)
//          extEdges.stream().filter(predicate)
          List<Polygon> polygons = straightSkeleton.getGraph().getFaces().stream().
            map(f -> f.getGeometry()).
            filter(f -> f.intersects(face.getGeometry())).
            map(f->snap(f,face.getGeometry(),0.01)).
            map(f->f.intersection(face.getGeometry())).
            filter(f->f instanceof Polygon).
            map(f->(Polygon) f).
            collect(Collectors.toList());
          List<LineString> lines = new ArrayList<>();
          List<LineString> perpLines = new ArrayList<>();
          List<Polygon> prevPolygons = new ArrayList<>();
          double previousL = 0;
          for (double w : widths) {
            double current = previousL + w;
            LineString l = (LineString) lil.extractLine(previousL, current);
            lines.add(l);
            List<Polygon> currentPol = polygons.stream().filter(p->p.intersects(l)).collect(Collectors.toList());
            Coordinate c1 = l.getCoordinateN(l.getNumPoints() - 2);
            Coordinate c2 = l.getCoordinateN(l.getNumPoints() - 1);
            perpLines.add(getPerpendicular(c1, c2, l.getFactory(), true));
            // System.out.println("WIDTH = " + w + "\n" + l);
            // System.out.println(createParcel(straightSkeleton, betaStrips, l));
            previousL = current;
          }

        }
      } else {
        System.out.println("MORE THAN ONE SEGMENT FOR BETA STRIP (" + merged.size() + ")");
      }
    }
    return null;
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
      Face prevFace = previousEdge.getFace();
      Face nextFace = nextEdge.getFace();
      Face prevAlphaStrip = prevFace.getParent();
      Face nextAlphaStrip = nextFace.getParent();
      // FIXME use parent edge children
      List<HalfEdge> edgeList = graph.getEdges().stream().filter(e -> e.getTwin() != null)
          .filter(e -> (e.getFace().getParent() == prevAlphaStrip && e.getTwin().getFace().getParent() == nextAlphaStrip)).collect(Collectors.toList());
      edgeList.forEach(e -> System.out.println(e.getGeometry()));
      List<HalfEdge> diagonalEdgeList = new ArrayList<>();
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
        final Face removedAlphaStrip = (supportingVertexClass == PREVIOUS) ? nextAlphaStrip : prevAlphaStrip;
        // FIXME always getFace()?
        List<Face> facesToRemove = diagonalEdgeList.stream().map(e -> (e.getFace().getParent() == removedAlphaStrip) ? e.getFace() : e.getTwin().getFace())
            .collect(Collectors.toList());
        // FIXME only one edge for alpha strips?
        Coordinate projection = Util.project(currNode.getCoordinate(), removedAlphaStrip.getEdges().get(0).getGeometry());
        Polygon toRemove = facesToRemove.get(facesToRemove.size() - 1).getGeometry();
        Pair<Polygon, Polygon> split = splitPolygon(toRemove, currNode.getCoordinate(), projection);// toRemove.getFactory().createLineString(new Coordinate[] {
                                                                                                    // currNode.getCoordinate(), projection }));
        Face absorbingBetaSplit = (supportingVertexClass == PREVIOUS) ? prevAlphaStrip : nextAlphaStrip;
        Face absorbedBetaSplit = (supportingVertexClass == PREVIOUS) ? nextAlphaStrip : prevAlphaStrip;
        Polygon absorbingBetaSplitPolygon = absorbingBetaSplit.getGeometry();
        Polygon absorbedBetaSplitPolygon = absorbedBetaSplit.getGeometry();
        int leftShared = sharedPoints(split.getLeft(), absorbingBetaSplitPolygon);
        // int rightShared = sharedPoints(split.getRight(), absorbingBetaSplit);
        // System.out.println("SHARED " + leftShared + " - " + rightShared);
        System.out.println(absorbingBetaSplitPolygon);
        Polygon absorbedPolygonPart = (leftShared == 2) ? split.getLeft() : split.getRight();
        Polygon keptPolygonPart = (leftShared == 2) ? split.getRight() : split.getLeft();
        System.out.println("ABSORBEDPART:\n" + absorbedPolygonPart);
        List<Polygon> absorbedPolygons = facesToRemove.subList(0, facesToRemove.size() - 1).stream().map(f -> f.getGeometry()).collect(Collectors.toList());
        List<Polygon> newAbsorbing = new ArrayList<>(absorbedPolygons);
        newAbsorbing.add(absorbingBetaSplitPolygon);
        newAbsorbing.add(absorbedPolygonPart);
        absorbedPolygons.add(toRemove);
        absorbingBetaSplit.setPolygon(Util.polygonUnion(newAbsorbing));
        absorbedBetaSplit.setPolygon(Util.polygonUnion(Arrays.asList(Util.polygonDifference(Arrays.asList(absorbedBetaSplitPolygon), absorbedPolygons), keptPolygonPart)));
        System.out.println("ABSORBING:\n" + absorbingBetaSplit.getGeometry());
        System.out.println("ABSORBED:\n" + absorbedBetaSplit.getGeometry());
      }
      System.out.println("DONE");
    }
    return new TopologicalGraph(alphaStrips.getFaces().stream().map(f -> f.getGeometry()).collect(Collectors.toList()));
  }

  @SuppressWarnings("rawtypes")
  public static List<Geometry> polygonize(Geometry geometry) {
    List lines = LineStringExtracter.getLines(geometry);
    Polygonizer polygonizer = new Polygonizer();
    polygonizer.add(lines);
    Collection polys = polygonizer.getPolygons();
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
    LineString line = poly.getFactory().createLineString(new Coordinate[] { origin, projection });
    Polygon snapped = snap(poly, projection, 0.01);
    System.out.println("SNAPPED =\n" + snapped);
    Geometry nodedLinework = snapped.getBoundary().union(line);
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
    } else {
      System.out.println("P1=\n" + output.get(0));
      System.out.println("P2=\n" + output.get(1));
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
