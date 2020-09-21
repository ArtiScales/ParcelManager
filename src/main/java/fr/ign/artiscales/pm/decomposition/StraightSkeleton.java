package fr.ign.artiscales.pm.decomposition;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.vecmath.Point3d;

import org.apache.commons.lang3.ArrayUtils;
import org.geotools.geometry.jts.WKTReader2;
import org.locationtech.jts.algorithm.Orientation;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.io.ParseException;
import org.twak.camp.Corner;
import org.twak.camp.Edge;
import org.twak.camp.Machine;
import org.twak.camp.OffsetSkeleton;
import org.twak.camp.Output;
import org.twak.camp.Output.Face;
import org.twak.camp.Skeleton;
import org.twak.utils.collections.Loop;
import org.twak.utils.collections.LoopL;
import org.twak.utils.collections.Loopable;

import fr.ign.artiscales.tools.graph.recursiveGraph.HalfEdge;
import fr.ign.artiscales.tools.graph.recursiveGraph.Node;
import fr.ign.artiscales.tools.graph.recursiveGraph.TopologicalGraph;

/**
 * Weighted straight skeleton computation. Uses the campskeleton library. The result is converted to a topological graph using half-edges.
 * 
 * @author MBrasebin
 * @author Julien Perret
 * 
 */
public class StraightSkeleton {
  private Polygon inputPolygon;

  public Polygon getInputPolygon() {
    return inputPolygon;
  }

  /**
   * Compute a straight skeleton from the input polygon.
   * 
   * @param p
   *          input polygon
   */
  public StraightSkeleton(Polygon p) {
    this(p, null, 0);
  }

  /**
   * Straight skelton calculation with cap parameters that defines a perpendicular distance from the block contours
   * 
   * @param p
   * @param cap
   */
  public StraightSkeleton(Polygon p, double cap) {
    this(p, null, cap);
  }

  public StraightSkeleton(Polygon p, double[] angles) {
    this(p, angles, 0);
  }

  /**
   * Calcul du squelette droit, le résultat est obtenu par le getCarteTopo() Une pondération est appliquée
   * 
   * @param p
   * @param angles
   *          : la pondération appliquée pour le calcul de squelette droit. Le nombre d'élément du tableaux doit être au moins égal au nombre de côté (intérieurs inclus du
   *          polygone)
   */
  public StraightSkeleton(Polygon p, double[] angles, double cap) {
    this.inputPolygon = p;// (Polygon) TopologyPreservingSimplifier.simplify(inputPolygon, 0.1);
    System.out.println("StraightSkeleton with " + cap + "\n" + p);
    Skeleton s = buildSkeleton(this.inputPolygon, angles);
    if (cap != 0) {
      s.capAt(cap);
    }
    s.skeleton();
    try {
      this.graph = convertOutPut(s.output);
    } catch (Exception e) {
      s = buildSkeleton(this.inputPolygon, angles);
      s.skeleton();
      this.graph = convertOutPut(s.output);
    }
  }

  public static Skeleton buildSkeleton(Polygon p, double[] angles) {
    return new Skeleton(buildEdgeLoops(p, angles), true);
  }

  private static int setMachine(Edge e, double[] angles, int countAngle, Machine machine) {
    if (angles == null) {
      e.machine = machine;
    } else {
      e.machine = new Machine(angles[countAngle]);
    }
    return countAngle + 1;
  }

  public static LoopL<Edge> buildEdgeLoops(Polygon p, double[] angles) {
    int countAngle = 0;
    Machine directionMachine = new Machine();
    LoopL<Edge> input = new LoopL<Edge>();
    Loop<Edge> loop = new Loop<Edge>();
    for (Edge e : convertLineString(p.getExteriorRing(), false)) {
      loop.append(e);
      countAngle = setMachine(e, angles, countAngle, directionMachine);
    }
    input.add(loop);
    for (int i = 0; i < p.getNumInteriorRing(); i++) {
      Loop<Edge> loopIn = new Loop<Edge>();
      input.add(loopIn);
      for (Edge e : convertLineString(p.getInteriorRingN(i), true)) {
        loopIn.append(e);
        countAngle = setMachine(e, angles, countAngle, directionMachine);
      }
    }
    return input;
  }

  private static List<Edge> convertLineString(LineString ring, boolean reverse) {
    Coordinate[] coordinates = ring.getCoordinates();
    if (!Orientation.isCCW(coordinates) ^ reverse)
      ArrayUtils.reverse(coordinates);
    return fromDPLToEdges(coordinates);
  }

  private static Node getNode(Coordinate p, Map<Coordinate, Node> map, TopologicalGraph graph, GeometryFactory factory) {
    if (map.containsKey(p)) {
      return map.get(p);
    }
    Coordinate snapped = snap(p, graph.getEdges().stream().map(f -> f.getGeometry()).collect(Collectors.toList()), factory);
    if (map.containsKey(snapped)) {
      return map.get(snapped);
    }
    Node node = new Node(snapped);
    graph.addNode(node);
    map.put(p, node);
    return node;
  }

  /**
   * Converts the output of the algorithm to a topological graph.
   * 
   * @param out
   * @return
   */
  private static TopologicalGraph convertOutPut(Output out) {
    GeometryFactory factory = new GeometryFactory();
    TopologicalGraph graph = new TopologicalGraph();
    Map<Coordinate, Node> nodeMap = new HashMap<>();
    // For each face
    for (Face face : new HashSet<>(out.faces.values())) {
      // create a corresponding face (if it has points - do not export the infinite face)
      if (!face.points.isEmpty()) {
        fr.ign.artiscales.tools.graph.recursiveGraph.Face topoFace = new fr.ign.artiscales.tools.graph.recursiveGraph.Face();
        LinearRing exterior = null;
        List<LinearRing> interiors = new ArrayList<>();
        for (Loop<Point3d> ptLoop : face.points) {
          List<Coordinate> coord = new ArrayList<>();
          HalfEdge prev = null;
          HalfEdge first = null;
          for (Loopable<Point3d> loopable : ptLoop.loopableIterator()) {
            Coordinate p1 = pointToCoordinate(loopable.get());
            Coordinate p2 = pointToCoordinate(loopable.getNext().get());
            if (p1 != null && p2 != null) {
              // create or get the nodes
              Node n1 = getNode(p1, nodeMap, graph, factory), n2 = getNode(p2, nodeMap, graph, factory);
              coord.add(n1.getCoordinate());
              // Create a new halfedge
              HalfEdge a = new HalfEdge(n1, n2, factory.createLineString(new Coordinate[] { n1.getCoordinate(), n2.getCoordinate() }));
              // Add it to the graph
              graph.getEdges().add(a);
              graph.getEdges().forEach(e -> {
                if (e.getOrigin() == n2 && e.getTarget() == n1) {
                  e.setTwin(a);
                }
              });
              a.setFace(topoFace);
              if (prev != null)
                prev.setNext(a);
              else
                first = a;
              prev = a;
            }
          }
          coord.add(coord.get(0));
          prev.setNext(first);
          LinearRing ring = factory.createLinearRing(coord.toArray(new Coordinate[coord.size()]));
          if (exterior == null)
            exterior = ring;
          else
            interiors.add(ring);
        }
        // for (Loop<SharedEdge> loop : face.edges) {// can it actually have more than a loop?
        // // for each edge
        // List<Coordinate> coord = new ArrayList<>();
        // HalfEdge prev = null;
        // HalfEdge first = null;
        // for (SharedEdge se : loop) {
        // Coordinate p1 = pointToCoordinate(se.getStart(face)), p2 = pointToCoordinate(se.getEnd(face));
        // if (p1 != null && p2 != null) {
        // // create or get the nodes
        // Node n1 = getNode(p1, nodeMap, graph, factory), n2 = getNode(p2, nodeMap, graph, factory);
        // coord.add(n2.getCoordinate());
        // // Create a new halfedge (inverse the order to have the half edges in CCW order)
        // HalfEdge a = new HalfEdge(n2, n1, factory.createLineString(new Coordinate[] { n2.getCoordinate(), n1.getCoordinate() }));
        // // Add it to the graph
        // graph.getEdges().add(a);
        // graph.getEdges().forEach(e -> {
        // if (e.getOrigin() == n1 && e.getTarget() == n2) {
        // e.setTwin(a);
        // }
        // });
        // a.setFace(topoFace);
        // if (prev != null)
        // prev.setNext(a);
        // else
        // first = a;
        // prev = a;
        // }
        // }
        // coord.add(coord.get(0));
        // prev.setNext(first);
        // LinearRing ring = factory.createLinearRing(coord.toArray(new Coordinate[coord.size()]));
        // if (exterior == null)
        // exterior = ring;
        // else
        // interiors.add(ring);
        // }
        topoFace.setPolygon(factory.createPolygon(exterior, interiors.toArray(new LinearRing[interiors.size()])));
        graph.getFaces().add(topoFace);
      }
    }
    return graph;
  }

  private static Coordinate snap(Coordinate toSnap, List<LineString> existingGeometries, GeometryFactory factory) {
    Geometry noded = factory.createGeometryCollection(existingGeometries.stream().map(p -> p.getBoundary()).toArray(Geometry[]::new));
    for (Coordinate c : noded.getCoordinates()) {
      if (c.distance(toSnap) < 0.01)
        return c;
    }
    return toSnap;
  }

  /**
   * Convertit une liste de sommets formant un cycle en arrêtes.
   * 
   * @param dpl
   */
  public static List<Edge> fromDPLToEdges(Coordinate[] dpl) {
    int nbPoints = dpl.length;
    List<Edge> lEOut = new ArrayList<Edge>();
    List<Corner> lC = new ArrayList<Corner>();
    for (int i = 0; i < nbPoints - 1; i++) {
      lC.add(fromPositionToCorner(dpl[i]));
    }
    for (int i = 0; i < nbPoints - 2; i++) {
      lEOut.add(new Edge(lC.get(i), lC.get(i + 1)));
    }
    lEOut.add(new Edge(lC.get(nbPoints - 2), lC.get(0)));
    return lEOut;
  }

  /**
   * Convertit un positon en corner.
   * 
   * @param dp
   * @return
   */
  private static Corner fromPositionToCorner(Coordinate dp) {
    // if (dp.getDimension() == 2) {
    // return new Corner(dp.getX(), dp.getY(), 0);
    // }
    return new Corner(dp.getX(), dp.getY(), 0);
  }

  private static Coordinate pointToCoordinate(Point3d c) {
    // return new Coordinate(precModel.makePrecise(c.x), precModel.makePrecise(c.y), precModel.makePrecise(c.z));
    return new Coordinate(c.x, c.y);
  }

  private static Coordinate cornerToCoordinate(Corner c) {
    // return new Coordinate(precModel.makePrecise(c.x), precModel.makePrecise(c.y), precModel.makePrecise(c.z));
    return new Coordinate(c.x, c.y);
  }

  // private static PrecisionModel precModel = new PrecisionModel(100);

  private TopologicalGraph graph = null;

  /**
   * 
   * @return perment d'obtenir la carte topo générée
   */
  public TopologicalGraph getGraph() {
    return this.graph;
  }

  /**
   * 
   * @return extrait les arcs extérieurs du polygone
   */
  public List<HalfEdge> getExteriorEdges() {
    return this.graph.getEdges().stream().filter(p -> p.getTwin() == null).collect(Collectors.toList());
  }

  /**
   * 
   * @return extrait les arcs générés lors du calcul du squelette droit
   */
  public List<HalfEdge> getInteriorEdges() {
    return this.graph.getEdges().stream().filter(p -> p.getTwin() != null).collect(Collectors.toList());
  }

  /**
   * 
   * @return extrait les arcs générés ne touchant pas la frontière du polygone
   */
  public List<HalfEdge> getIncludedEdges() {
    Geometry geom = inputPolygon.buffer(-0.5);
    return this.graph.getEdges().stream().filter(p -> geom.contains(p.getGeometry())).collect(Collectors.toList());
  }

  private static LinearRing convertCornerLoop(Loop<Corner> lC, GeometryFactory factory) {
    List<Coordinate> dpl = new ArrayList<>(lC.count());
    for (Corner c : lC) {
      dpl.add(cornerToCoordinate(c));
    }
    dpl.add(dpl.get(0));// close the ring
    return factory.createLinearRing(dpl.toArray(new Coordinate[dpl.size()]));
  }

  public static Polygon convertCornerLoops(LoopL<Corner> points, GeometryFactory factory) {
    LinearRing exterior = null;
    List<LinearRing> interior = new ArrayList<>();
    for (Loop<Corner> lP : points) {
      LinearRing ring = convertCornerLoop(lP, factory);
      if (exterior == null) {
        exterior = ring;
      } else {
        interior.add(ring);
      }
    }
    return factory.createPolygon(exterior, interior.toArray(new LinearRing[interior.size()]));
  }

  public static Polygon shrink(Polygon p, double value) {
    return convertCornerLoops(OffsetSkeleton.shrink(buildEdgeLoops(p, null), value), p.getFactory());
  }

  public static void main(String[] args) throws ParseException {
    WKTReader2 reader = new WKTReader2();
    Polygon polygon = (Polygon) reader.read(
        "MultiPolygon (((936393.43999999994412065 6687559.91000000014901161, 936384.64000000001396984 6687567.15000000037252903, 936376.08999999996740371 6687576.51999999955296516, 936365 6687577.61000000033527613, 936336.14000000001396984 6687595.57000000029802322, 936350 6687599.04999999981373549, 936367.23999999999068677 6687605.88999999966472387, 936385.61999999999534339 6687618.24000000022351742, 936391.43999999994412065 6687627.63999999966472387, 936408.58999999996740371 6687642.67999999970197678, 936459.57999999995809048 6687680.36000000033527613, 936491.71999999997206032 6687701.34999999962747097, 936512.2099999999627471 6687715.16999999992549419, 936540.68000000005122274 6687731.37999999988824129, 936556.5400000000372529 6687714.76999999955296516, 936561 6687708.87999999988824129, 936571.47999999998137355 6687703.59999999962747097, 936588.09999999997671694 6687691.2900000000372529, 936590.64000000001396984 6687688.23000000044703484, 936588.39000000001396984 6687686.51999999955296516, 936559.59999999997671694 6687665.44000000040978193, 936538.06000000005587935 6687649.38999999966472387, 936502.18999999994412065 6687621.92999999970197678, 936480.41000000003259629 6687605.45000000018626451, 936464.77000000001862645 6687593.66999999992549419, 936452.51000000000931323 6687584.46999999973922968, 936434.97999999998137355 6687571.2099999999627471, 936427.41000000003259629 6687565.40000000037252903, 936408.43000000005122274 6687550.58000000007450581, 936393.43999999994412065 6687559.91000000014901161)))");
    StraightSkeleton cs = new StraightSkeleton(polygon);
    TopologicalGraph graph = cs.getGraph();
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
//    TopologicalGraph.export(graph.getFaces(), new File("/tmp/faces.gpkg"), Polygon.class);
//    TopologicalGraph.export(graph.getEdges(), new File("/tmp/edges.gpkg"), LineString.class);
  }
}
