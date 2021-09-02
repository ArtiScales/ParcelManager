package fr.ign.artiscales.pm.division;

import fr.ign.artiscales.pm.parcelFunction.MarkParcelAttributeFromPosition;
import fr.ign.artiscales.tools.FeaturePolygonizer;
import fr.ign.artiscales.tools.geoToolsFunctions.Attribute;
import fr.ign.artiscales.tools.geoToolsFunctions.Schemas;
import fr.ign.artiscales.tools.geoToolsFunctions.vectors.Geom;
import fr.ign.artiscales.tools.geoToolsFunctions.vectors.collec.CollecMgmt;
import fr.ign.artiscales.tools.geoToolsFunctions.vectors.geom.Lines;
import fr.ign.artiscales.tools.geoToolsFunctions.vectors.geom.Points;
import fr.ign.artiscales.tools.geoToolsFunctions.vectors.geom.Polygons;
import fr.ign.artiscales.tools.graph.analysis.FindObjectInDirection;
import fr.ign.artiscales.tools.graph.recursiveGraph.Face;
import fr.ign.artiscales.tools.graph.recursiveGraph.HalfEdge;
import fr.ign.artiscales.tools.graph.recursiveGraph.Node;
import fr.ign.artiscales.tools.graph.recursiveGraph.TopologicalGraph;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.ImmutableTriple;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.apache.commons.math3.distribution.NormalDistribution;
import org.apache.commons.math3.distribution.RealDistribution;
import org.apache.commons.math3.random.MersenneTwister;
import org.apache.commons.math3.random.RandomGenerator;
import org.geotools.data.DataStore;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.feature.DefaultFeatureCollection;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.geometry.jts.GeometryBuilder;
import org.locationtech.jts.algorithm.Angle;
import org.locationtech.jts.algorithm.Orientation;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineSegment;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.MultiLineString;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.PrecisionModel;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKTReader;
import org.locationtech.jts.linearref.LengthIndexedLine;
import org.locationtech.jts.operation.linemerge.LineMerger;
import org.locationtech.jts.operation.overlay.snap.GeometrySnapper;
import org.locationtech.jts.operation.polygonize.Polygonizer;
import org.locationtech.jts.precision.GeometryPrecisionReducer;
import org.locationtech.jts.simplify.TopologyPreservingSimplifier;
import org.opengis.feature.simple.SimpleFeature;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Re-implementation of block decomposition into parcels from :
 * <p>
 * Vanegas, C. A., Kelly, T., Weber, B., Halatsch, J., Aliaga, D. G., Müller, P., May 2012. Procedural generation of parcels in urban modeling. Comp. Graph. Forum 31 (2pt3).
 * <p>
 * Decomposition method by using straight skeleton
 *
 * @author Mickael Brasebin
 * @author Julien Perret
 * @author Maxime Colomb
 */
public class StraightSkeletonDivision {

    //////////////////////////////////////////////////////
    // Input data parameters
    // Must be a double attribute
    // public final static String NAME_ATT_IMPORTANCE = "length";
    // // public final static String NAME_ATT_ROAD = "NOM_VOIE_G";
    // // public final static String NAME_ATT_ROAD = "n_sq_vo";
    // public final static String NAME_ATT_ROAD = "Id";
    // Indicate the importance of the neighbour road
    private final String NAME_ATT_LEVELOFATTRACTION;
    private final String NAME_ATT_ROADNAME;

    //////////////////////////////////////////////////////

    // Indicate if a boundary of partial skeleton is at the border of input
    // block
    // public final static String ATT_IS_INSIDE = "INSIDE";
    // public final static int ARC_VALUE_INSIDE = 1;
    // public final static int ARC_VALUE_OUTSIDE = 2;

    // public final static String ATT_ROAD = "NOM_VOIE";

    // public static String ATT_FACE_ID_STRIP = "ID_STRIP";

    // public static String ATT_IMPORTANCE = "IMPORTANCE";

    private static boolean SAVEINTERMEDIATERESULT = true;
    private static boolean DEBUG = false;
    private static boolean generatePeripheralRoad;
    public static File FOLDER_OUT_DEBUG = new File("/tmp/skeleton");

    private final Polygon initialPolygon;
    private final Map<Face, List<HalfEdge>> frontages;
    private SimpleFeatureCollection roads;
    private final double tolerance;
    private final GeometryPrecisionReducer precisionReducer;
    private final StraightSkeleton straightSkeleton;
    private List<HalfEdge> orderedExteriorEdges;
    private TopologicalGraph alphaStrips;
    private TopologicalGraph betaStrips;
    private Map<Face, LineString> psiMap;
    private Map<HalfEdge, Optional<Pair<String, Double>>> roadAttributes;
    private final Geometry snapInitialSSFaces;
    private final GeometryFactory factory;
    private Set<Face> facesWithMultipleFrontages;

    public static void main(String[] args) throws IOException, EdgeException, StraightSkeletonException, ParseException {
        File rootFolder = new File("src/main/resources/TestScenario/");
        File roadFile = new File(rootFolder, "road.gpkg");
        DEBUG = true;
        DataStore dsRoad = CollecMgmt.getDataStore(roadFile);
        WKTReader wktR = new WKTReader();
        Polygon p = (Polygon) wktR.read("Polygon ((935387.92000000004190952 6687121.2900000000372529, 935410.35999999998603016 6687079.05999999959021807, 935200.15000000002328306 6686960.15000000037252903, 935181.2099999999627471 6686972.41999999992549419, 935178.56999999994877726 6687002.87999999988824129, 935178.16000000003259629 6687005.49000000022351742, 935170.55000000004656613 6687021.15000000037252903, 935158.06999999994877726 6687029.84999999962747097, 935155.30000000004656613 6687034.24000000022351742, 935133.47999999998137355 6687075.08999999985098839, 935130.40000000002328306 6687072.50999999977648258, 935117.11999999999534339 6687098.29999999981373549, 935112.98999999999068677 6687105.79999999981373549, 935107.31000000005587935 6687117.05999999959021807, 935092.93000000005122274 6687138.03000000026077032, 935154.40000000002328306 6687153.92999999970197678, 935196.10999999998603016 6687163.87999999988824129, 935192.52000000001862645 6687174.84999999962747097, 935322.56000000005587935 6687244.82000000029802322, 935347.78000000002793968 6687196.87999999988824129, 935369.39000000001396984 6687156, 935387.92000000004190952 6687121.2900000000372529))");
        double maxDepth = 20, maxDistanceForNearestRoad = 15, minWidth = 20, maxWidth = 50, omega = 0.1, widthRoad = 12;
        SAVEINTERMEDIATERESULT = true;
        String NAME_ATT_IMPORTANCE = "IMPORTANCE";
        String NAME_ATT_ROAD = "NOM_VOIE_G";
        StraightSkeletonDivision decomposition = new StraightSkeletonDivision(
                p, dsRoad.getFeatureSource(dsRoad.getTypeNames()[0]).getFeatures(), NAME_ATT_ROAD, NAME_ATT_IMPORTANCE, maxDepth, maxDistanceForNearestRoad, true, widthRoad, "main");
        export(decomposition.straightSkeleton.getGraph(), new File(FOLDER_OUT_DEBUG, "after_fix"));
        List<Polygon> globalOutputParcels = new ArrayList<>(decomposition.createParcels(minWidth, maxWidth, omega, new MersenneTwister(42)));
        TopologicalGraph output = new TopologicalGraph(globalOutputParcels, 0.02);
        DefaultFeatureCollection result = new DefaultFeatureCollection();
        SimpleFeatureBuilder builder = Schemas.getBasicSchema("parcelSplitSS");
        for (Face face : output.getFaces()) {
            builder.set(CollecMgmt.getDefaultGeomName(), face.getGeometry());
            result.add(builder.buildFeature(Attribute.makeUniqueId()));
        }
        CollecMgmt.exportSFC(result, new File(FOLDER_OUT_DEBUG, "result.gpkg"));

    }

    /**
     * Create and compute Straight Skeleton algorithm. Version without peripheral road
     *
     * @param p
     * @param roads
     * @param roadNameAttribute
     * @param roadImportanceAttribute
     * @param maxDepth
     * @param maxDistanceForNearestRoad
     * @throws StraightSkeletonException
     * @throws EdgeException
     */
    public StraightSkeletonDivision(Polygon p, SimpleFeatureCollection roads, String roadNameAttribute, String roadImportanceAttribute, double maxDepth,
                                    double maxDistanceForNearestRoad, String name) throws StraightSkeletonException, EdgeException {
        this(p, roads, roadNameAttribute, roadImportanceAttribute, maxDepth, maxDistanceForNearestRoad, 2, false, 0, name);
    }

    public StraightSkeletonDivision(Polygon p, SimpleFeatureCollection roads, String roadNameAttribute, String roadImportanceAttribute, double maxDepth,
                                    double maxDistanceForNearestRoad, boolean generatePeripheralRoad, double widthRoad, String name) throws StraightSkeletonException, EdgeException {
        this(p, roads, roadNameAttribute, roadImportanceAttribute, maxDepth, maxDistanceForNearestRoad, 2, generatePeripheralRoad, widthRoad, name);
    }

    /**
     * Constructor decomposing initial polygon until beta-stripes
     */
    public StraightSkeletonDivision(Polygon p, SimpleFeatureCollection roads, String roadNameAttribute, String roadImportanceAttribute, double maxDepth,
                                    double maxDistanceForNearestRoad, int numberOfDigits, boolean generatePeripheralRoad, double widthRoad, String name) throws StraightSkeletonException, EdgeException {
        this.tolerance = 2.0 / Math.pow(10, numberOfDigits);
        p = (Polygon) TopologyPreservingSimplifier.simplify(p, 10 * tolerance);
        this.precisionReducer = new GeometryPrecisionReducer(new PrecisionModel(Math.pow(10, numberOfDigits)));
        this.roads = roads;
        this.factory = p.getFactory();
        this.NAME_ATT_ROADNAME = roadNameAttribute;
        this.NAME_ATT_LEVELOFATTRACTION = roadImportanceAttribute;
        FOLDER_OUT_DEBUG = new File(FOLDER_OUT_DEBUG, "skeleton/" + name + "_" + (generatePeripheralRoad ? "peripheralRoad" : "noPeripheralRoad") + "_" + (maxDepth != 0 ? "offset" : "noOffset"));
        FOLDER_OUT_DEBUG.mkdirs();
        if (generatePeripheralRoad)
            p = this.generatePeripheralRoad(p, widthRoad);
        if (isSAVEINTERMEDIATERESULT() || isDEBUG()) {
            try {
                CollecMgmt.exportSFC(this.roads, new File(FOLDER_OUT_DEBUG, "roads.gpkg"));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        this.initialPolygon = (Polygon) precisionReducer.reduce(p);

        // Create and export skeleton
        try {
            this.straightSkeleton = new StraightSkeleton(this.initialPolygon, maxDepth);
        } catch (Exception e) {
            e.printStackTrace();
            throw new StraightSkeletonException();
        }
        TopologicalGraph graph = this.straightSkeleton.getGraph();
        export(graph, new File(FOLDER_OUT_DEBUG, "init"));

        // order edges in list and mark if they are exterior
        this.orderedExteriorEdges = getOrderedExteriorEdges(straightSkeleton.getGraph());

        //store faces of initial Straight Skeleton into an independant variable
        snapInitialSSFaces = factory.createGeometryCollection(
                graph.getFaces().stream().map(Face::getGeometry).collect(Collectors.toList()).toArray(new Geometry[]{}));

        //characterize frontage
        this.frontages = frontageDefinition(maxDistanceForNearestRoad);
        if (this.frontages == null)
            return;

        // get alpha strips
        this.alphaStrips = mergeSSStripToAlphaStrip();
        export(alphaStrips, new File(FOLDER_OUT_DEBUG, "alpha"));

        //get beta stripes
        this.betaStrips = fixDiagonalEdges(alphaStrips, roadAttributes);
        export(betaStrips, new File(FOLDER_OUT_DEBUG, "beta"));
    }

    private Optional<Pair<String, Double>> getRoadAttributes(LineString l, double maxDistanceForNearestRoad) {
        return FindObjectInDirection.find(l, this.initialPolygon, this.roads, maxDistanceForNearestRoad, this.NAME_ATT_LEVELOFATTRACTION).map(this::getRoadAttributes);
    }

    private Map<Face, List<HalfEdge>> frontageDefinition(double maxDistanceForNearestRoad) {
        log("");
        log("");
        log("FRONTAGE DEFINITION");
        log("");
        log("");// get the road attributes (name and importance)
        log("INPUT ZONE : " + this.initialPolygon);
        this.roadAttributes = new HashMap<>();
        for (HalfEdge e : orderedExteriorEdges) {
            Optional<Pair<String, Double>> a = getRoadAttributes(e.getGeometry(), maxDistanceForNearestRoad);
//			if (a.isPresent())
            roadAttributes.put(e, a);
        }
        if (roadAttributes.entrySet().stream().noneMatch(x -> x.getValue().isPresent())) {
            System.out.println("WARNING : no road found - alpha and beta strips ignored");
            alphaStrips = betaStrips = null;
            return null;
        }
        // build frontages for faces (consecutive edges belonging to the face)
        Map<Face, List<List<HalfEdge>>> frontages = new HashMap<>();
        List<HalfEdge> frontage = new ArrayList<>();
        Face currFace = null;
        facesWithMultipleFrontages = new HashSet<>();
        for (HalfEdge e : orderedExteriorEdges) {
            if (!roadAttributes.get(e).isPresent())
                continue;
            if (currFace == null) { // case for is the first edge
                currFace = e.getFace();
                frontage.add(e);
            } else {
                if (e.getFace() == currFace) { // if we are sill on the same face
                    frontage.add(e);
                } else { // we changed face. Save the current frontage
                    List<List<HalfEdge>> l = frontages.getOrDefault(currFace, new ArrayList<>());
                    if (!l.isEmpty())
                        facesWithMultipleFrontages.add(currFace);
                    l.add(frontage);
                    frontages.put(currFace, l);
                    // we create a new frontage
                    frontage = new ArrayList<>();
                    currFace = e.getFace();
                    frontage.add(e);
                }
            }
        }
        if (orderedExteriorEdges.get(0).getFace() == orderedExteriorEdges.get(orderedExteriorEdges.size() - 1).getFace()) {
            frontages.get(orderedExteriorEdges.get(0).getFace()).stream().filter(l -> l.contains(orderedExteriorEdges.get(0))).findFirst().get().addAll(frontage);
        } else {
            List<List<HalfEdge>> l = frontages.getOrDefault(currFace, new ArrayList<>());
            if (!l.isEmpty()) {
                facesWithMultipleFrontages.add(currFace);
            }
            l.add(frontage);
            frontages.put(currFace, l);
        }
        log(facesWithMultipleFrontages.size() + " FACES with multiple frontages");
        facesWithMultipleFrontages.forEach(f -> log(f.getGeometry()));
        Map<Face, List<HalfEdge>> primary = new HashMap<>();
        // determine the primary frontage for each face
        for (Entry<Face, List<List<HalfEdge>>> entry : frontages.entrySet()) {
            log("Face with " + entry.getValue().size() + " frontages\n" + entry.getKey().getGeometry());
            if (entry.getValue().size() > 1) { // there are multiple frontages for this face, determine the primary one
                List<HalfEdge> primaryFrontage = entry.getValue().stream()
                        .map(f -> new ImmutablePair<>(f, f.stream().map(e -> e.getGeometry().getLength()).reduce((a, b) -> a + b).get()))
                        .max((a, b) -> Double.compare(a.getRight(), b.getRight())).get().getLeft();
                primary.put(entry.getKey(), primaryFrontage);
                primaryFrontage.stream().map(HalfEdge::getGeometry).forEach(StraightSkeletonDivision::log);
            } else { // only one frontage. Easy
                primary.put(entry.getKey(), entry.getValue().get(0));
                entry.getValue().get(0).stream().map(HalfEdge::getGeometry).forEach(StraightSkeletonDivision::log);
            }
        }
        for (int i = 0; i < orderedExteriorEdges.size(); i++) {
            HalfEdge e = orderedExteriorEdges.get(i);
            // if e is a primary edge
            if (primary.containsKey(e.getFace()) && primary.get(e.getFace()).contains(e)) {
                if (i > 0) {
                    List<HalfEdge> list = new ArrayList<>(orderedExteriorEdges.subList(0, i));
                    // more the edges before to the end of the list
                    orderedExteriorEdges.removeAll(list);
                    orderedExteriorEdges.addAll(list);
                }
                break;
            }
        }
        return primary;
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

    private static int classify(Node node, HalfEdge previous, Optional<Pair<String, Double>> previousAttributes, HalfEdge next,
                                Optional<Pair<String, Double>> nextAttributes) {
        if (isReflex(node, previous, next))
            return NONE;
        double previousValue = previousAttributes.map(Pair::getRight).orElse(0.0);
        double nextValue = nextAttributes.map(Pair::getRight).orElse(0.0);
        if (previousValue > nextValue)
            return PREVIOUS;
        if (previousValue == nextValue && previousValue == 0.0)
            return NONE;
        return NEXT;
    }

    private Pair<String, Double> getRoadAttributes(SimpleFeature s) {
        String name = (String) s.getAttribute(NAME_ATT_ROADNAME);
        String impo = (String) s.getAttribute(NAME_ATT_LEVELOFATTRACTION);
        return new ImmutablePair<>(name == null ? "unknown" : name, Double.parseDouble(impo.replaceAll(",", ".")));
    }

    /**
     * Order
     *
     * @param edges
     * @return
     */
    private static List<HalfEdge> getOrderedEdgesFromCycle(List<HalfEdge> edges) {
        List<HalfEdge> orderedEdges = new ArrayList<>();
        if (edges.isEmpty()) {
            return orderedEdges;
        }
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
        if (DEBUG)
            System.out.println(text);
    }

    private static List<HalfEdge> getOrderedEdges(List<HalfEdge> edges) throws EdgeException {
        log("getOrderedEdges");
        edges.forEach(e -> log(e.getGeometry()));
        List<HalfEdge> orderedEdges = new ArrayList<>();
        if (edges.isEmpty())
            return orderedEdges;
        boolean forward = true;
        HalfEdge current = edges.remove(0);
        log("current\n" + current.getGeometry());
        orderedEdges.add(current);
        Node currentNode = current.getTarget();
        while (!edges.isEmpty()) {
            currentNode = forward ? current.getTarget() : orderedEdges.get(0).getOrigin();
            current = forward ? TopologicalGraph.next(currentNode, current, edges)
                    : TopologicalGraph.previous(currentNode, orderedEdges.get(0), edges);
            if (current == null) {
                if (forward) { // try backwards
                    forward = false;
                } else { // we already tried forwards and backwards
                    throw new EdgeException();
                }
            } else {
                log("current\n" + current.getGeometry());
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

    /**
     * Mark the edges whether they are in the exterior of the polygon or not. Also order the nodes in the list
     *
     * @param graph straight skeleton graph
     * @return the graph classed and with attribute EXTERIOR
     */
    private List<HalfEdge> getOrderedExteriorEdges(TopologicalGraph graph) {
        // TODO would having to order the exterior edges justify creating the infinite face?
        // FIXME here is a hack to get the exterior edges. This is ugly
        List<HalfEdge> edges = graph.getEdges().stream().filter(p -> p.getTwin() == null)
                .filter(e -> !this.initialPolygon.buffer(-0.1).contains(e.getGeometry())).collect(Collectors.toList());
        log("getOrderedExteriorEdges");
        log("pre-order");
        edges.forEach(e -> log(e.getGeometry()));
        graph.getEdges().forEach(e -> e.setAttribute("EXTERIOR", "false"));
        edges.forEach(e -> e.setAttribute("EXTERIOR", "true"));
        // straightSkeleton.getGraph().getEdges().iterator().next().getAttributes().forEach(a -> log("attribute " + a));
        List<HalfEdge> og = getOrderedEdgesFromCycle(edges);
        log("ordered");
        og.forEach(e -> log(e.getGeometry()));
        return og;
    }

    /**
     * Do we merge street segments (Half Edges) if they share the same name or if they haven't no name ?
     *
     * @param e1                      first segment
     * @param e2                      second segment
     * @param mergeStreetsWithoutName if false, won't consider when street segments have no name.
     */
    private boolean mergeOnStreetName(HalfEdge e1, HalfEdge e2, boolean mergeStreetsWithoutName) {
        Optional<Pair<String, Double>> a1 = roadAttributes.get(e1), a2 = roadAttributes.get(e2);
        log("MERGE?\n" + e1.getGeometry() + "\n" + e2.getGeometry());
        log(a1.isPresent() + "-" + a2.isPresent());
        log(a1.isPresent() ? a1.get().getLeft() : "-");
        log(a2.isPresent() ? a2.get().getLeft() : "-");
        // to also merge streets without a name. Otherwise, we do not allow their merging
        return mergeStreetsWithoutName ? a1.map(Pair::getLeft).orElse("").equals(a2.map(Pair::getLeft).orElse(""))
                : a1.isPresent() && a2.isPresent() && a1.map(Pair::getLeft).get().equals(a2.map(Pair::getLeft).get());
    }

    /**
     * Create alpha strips by merging faces facing common frontages.
     *
     * @return a topological graph whose faces are the alpha strips
     */
    private TopologicalGraph mergeSSStripToAlphaStrip() {
        log("");
        log("");
        log("CREATE ALPHA STRIPES");
        log("");
        log("");

        List<HalfEdge> primaryStrips = new ArrayList<>();
        List<HalfEdge> secondaryStrips = new ArrayList<>();
        HalfEdge currentStripHE = new HalfEdge(orderedExteriorEdges.get(0).getOrigin(), null, null);
        currentStripHE.getChildren().add(orderedExteriorEdges.get(0));
        primaryStrips.add(currentStripHE);
        List<Node> nodes = new ArrayList<>();
        for (int i = 0; i < orderedExteriorEdges.size() - 1; i++) {
            HalfEdge e1 = orderedExteriorEdges.get(i), e2 = orderedExteriorEdges.get((i + 1) % orderedExteriorEdges.size());
            Face f1 = e1.getFace(), f2 = e2.getFace();
            boolean p1 = frontages.containsKey(f1) && frontages.get(f1).contains(e1);
            boolean p2 = frontages.containsKey(f2) && frontages.get(f2).contains(e2);
            if (p1 && p2) {
                if (f1 == f2 || mergeOnStreetName(e1, e2, false)) { //TODO I set false here because it makes the FIGURE wrong, but the problem is more about why there is roads where there shouldn't be ? !
//                if (f1 == f2 || mergeOnStreetName(e1, e2, true)) { //TODO I set false here because it makes the FIGURE wrong, but the problem is more about why there is roads where there shouldn't be ? !
                    currentStripHE.getChildren().add(e2);
                } else {
                    Node node = straightSkeleton.getGraph().getCommonNode(e1, e2);// FIXME
                    currentStripHE.setTarget(node); //FIXME aren't we supposed to do something with that?
                    currentStripHE = new HalfEdge(e2.getOrigin(), null, null);
                    currentStripHE.getChildren().add(e2);
                    primaryStrips.add(currentStripHE);
                    nodes.add(node);
                }
            } else {
                if (p1 || p2) {
                    // finish the previous strip
                    Node node = straightSkeleton.getGraph().getCommonNode(e1, e2);// FIXME
                    currentStripHE.setTarget(node);
                    // create a new secondary strip
                    currentStripHE = new HalfEdge(e2.getOrigin(), null, null);
                    currentStripHE.getChildren().add(e2);
                    if (!p2)
//                        secondaryStrips.add(currentStripHE);
                        secondaryStrips.add(currentStripHE);
                    else
                        primaryStrips.add(currentStripHE);
                    nodes.add(node);
                } else {
                    //
                    currentStripHE.getChildren().add(e2);
                }
            }
        }
        HalfEdge e1 = orderedExteriorEdges.get(orderedExteriorEdges.size() - 1);
        HalfEdge e2 = orderedExteriorEdges.get(0);
        Face f1 = e1.getFace(), f2 = e2.getFace();
        boolean p1 = frontages.containsKey(f1) && frontages.get(f1).contains(e1), p2 = frontages.containsKey(f2) && frontages.get(f2).contains(e2);
        // Optional<Pair<String, Double>> lastAttributes = attributes.get(lastHE);
        if (p1 && p2) {
            log("P1 & P2");
            if (mergeOnStreetName(e1, e2, false)) {
                log("MERGE!!!");
                // we merge the first and last strips
                HalfEdge firstStrip = primaryStrips.get(0);
                HalfEdge lastStrip = primaryStrips.get(primaryStrips.size() - 1);
                firstStrip.getChildren().addAll(lastStrip.getChildren());
                primaryStrips.remove(lastStrip);
                firstStrip.setOrigin(lastStrip.getOrigin());
            } else {
                log("NO MERGE");
                Node node = straightSkeleton.getGraph().getCommonNode(e1, e2);// FIXME
                currentStripHE.setTarget(node);
                nodes.add(node);
            }
        } else {
            log("!(P1 & P2)");
            // finish the previous strip
            Node node = straightSkeleton.getGraph().getCommonNode(e1, e2);// FIXME
            currentStripHE.setTarget(node);
        }
        TopologicalGraph tempGraph = new TopologicalGraph();
        tempGraph.addNodes(nodes);
        tempGraph.getEdges().addAll(primaryStrips);
        tempGraph.getEdges().addAll(secondaryStrips);
        // In case of multiple frontage, remove secondary strips (they would create multilinestring supporting edges)
        for (Face face : this.facesWithMultipleFrontages) {
            // get its primary strip
            Optional<HalfEdge> opPrimaryStrip = primaryStrips.stream()
                    .filter(s -> s.getChildren().stream().map(HalfEdge::getFace).anyMatch(f -> f == face)).findFirst();
            if (opPrimaryStrip.isPresent()) {
                HalfEdge primaryStrip = opPrimaryStrip.get();
                log("primary\n" + primaryStrip.getGeometry());
                // gather its secondary strips
                List<HalfEdge> secondary = secondaryStrips.stream()
                        .filter(s -> s.getChildren().stream().map(HalfEdge::getFace).anyMatch(f -> f == face)).collect(Collectors.toList());
                // get the first halfedge of the face
                List<Triple<HalfEdge, List<HalfEdge>, Double>> triples = secondary.stream().map(e -> {
                    log("secondary\n" + e.getGeometry());
                    Pair<List<HalfEdge>, Double> path = tempGraph.getShortestPath(primaryStrip, e);
                    return new ImmutableTriple<>(e, path.getLeft(), path.getRight());
                }).collect(Collectors.toList());
                triples.add(new ImmutableTriple<>(primaryStrip, new ArrayList<>(), 0.0));
                triples.sort(Comparator.comparingDouble(Triple::getRight));
                Node origin = triples.get(0).getLeft().getOrigin();
                Node target = triples.get(triples.size() - 1).getLeft().getTarget();
                HalfEdge strip = new HalfEdge(origin, target, null);
                Triple<HalfEdge, List<HalfEdge>, Double> previous = triples.remove(0);
                strip.getChildren().addAll(previous.getLeft().getChildren());
                while (!triples.isEmpty()) {
                    Triple<HalfEdge, List<HalfEdge>, Double> current = triples.remove(0);
                    Pair<List<HalfEdge>, Double> path = tempGraph.getShortestPath(previous.getLeft(), current.getLeft());
                    path.getLeft().forEach(p -> strip.getChildren().addAll(p.getChildren()));
                    strip.getChildren().addAll(current.getLeft().getChildren());
                    previous = current;
                }
                // cleanup strips
                List<HalfEdge> primaryToRemove = primaryStrips.stream()
                        .filter(s -> s.getChildren().stream().anyMatch(h -> strip.getChildren().contains(h))).collect(Collectors.toList());
                primaryStrips.removeAll(primaryToRemove);
                List<HalfEdge> secondaryToRemove = secondaryStrips.stream()
                        .filter(s -> s.getChildren().stream().anyMatch(h -> strip.getChildren().contains(h))).collect(Collectors.toList());
                secondaryStrips.removeAll(secondaryToRemove);
                primaryStrips.add(strip);
            } else {
                log("could not find a primary strip for face\n" + face);
            }
        }
        TopologicalGraph alphaStripGraph = new TopologicalGraph();
        log("Primary Strips = " + primaryStrips.size());
        for (HalfEdge strip : primaryStrips) {
            List<HalfEdge> edges = strip.getChildren();
            LineString l = Lines.union(edges.stream().map(HalfEdge::getGeometry).collect(Collectors.toList()));
            strip.setLine(l);
            alphaStripGraph.getEdges().add(strip);
            alphaStripGraph.addNode(strip.getOrigin());
            alphaStripGraph.addNode(strip.getTarget());
            // Node start = alphaStripGraph.getOrCreateNode(strip.getOrigin().getCoordinate());
            // Node end = alphaStripGraph.getOrCreateNode(strip.getTarget().getCoordinate());
            // strip.setOrigin(start);
            // strip.setTarget(end);
            log(l);
        }
        log("Secondary Strips = " + secondaryStrips.size());
        for (HalfEdge strip : secondaryStrips) {
            List<HalfEdge> edges = strip.getChildren();
            LineString l = Lines.union(edges.stream().map(HalfEdge::getGeometry).collect(Collectors.toList()));
            strip.setLine(l);
            // randomly added
            Face stripFace = new Face();
            List<Face> stripFaces = strip.getChildren().stream().map(HalfEdge::getFace).collect(Collectors.toList());
            stripFace.getChildren().addAll(stripFaces);
            stripFaces.forEach(f -> f.setParent(stripFace));
            List<Polygon> polygons = stripFaces.stream().map(Face::getGeometry).collect(Collectors.toList());
            stripFace.setPolygon(Polygons.polygonUnionWithoutHoles(polygons, precisionReducer));
            strip.setFace(stripFace);
            alphaStripGraph.getFaces().add(stripFace);
            log(l);
        }
        for (HalfEdge strip : alphaStripGraph.getEdges()) {
            Face stripFace = new Face();
            List<Face> stripFaces = strip.getChildren().stream().map(HalfEdge::getFace).collect(Collectors.toList());
            stripFace.getChildren().addAll(stripFaces);
            stripFaces.forEach(f -> f.setParent(stripFace));
            List<Polygon> polygons = stripFaces.stream().map(Face::getGeometry).collect(Collectors.toList());
            stripFace.setPolygon(Polygons.polygonUnionWithoutHoles(polygons, precisionReducer));
            strip.setFace(stripFace);
            alphaStripGraph.getFaces().add(stripFace);
        }
        for (HalfEdge strip : secondaryStrips) {
            log("Secondary\n" + strip.getGeometry() + "\n" + strip.getChildren().size());
            strip.getChildren().forEach(e -> {
                log(e.getGeometry());
                log(e.getFace().getGeometry());
            });
            Set<Face> stripFaces = strip.getChildren().stream().map(e -> e.getFace().getParent()).collect(Collectors.toSet());
            if (stripFaces.size() != 1) {
                log(stripFaces.size() + " FACES!?");
            } else {
                Face f = stripFaces.iterator().next();
                log(f.getGeometry());
                strip.setFace(f);
            }
        }
        alphaStripGraph.getEdges().addAll(secondaryStrips);
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
            return Collections.singletonList(length);
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

    private Optional<ImmutablePair<HalfEdge, Coordinate>> getIntersection(Coordinate o, Coordinate d, TopologicalGraph graph, List<HalfEdge> edges) {
        Point p = factory.createPoint(o);
        log("getIntersection\n" + p + "\n" + d.getX() + "," + d.getY() + "\n" + factory.createPoint(new Coordinate(o.x + d.x, o.y + d.y)));
        List<Face> faces = graph.getFaces().stream().filter(f -> f.getGeometry().intersects(p)).collect(Collectors.toList());
        if (faces.size() != 1) {
            // log("found " + faces.size() + " faces intersecting " + p);
            faces = graph.getFaces().stream().filter(f -> f.getGeometry().intersects(p.buffer(tolerance))).collect(Collectors.toList());
            if (faces.size() != 1) {
                log("found " + faces.size() + " faces intersecting " + p + " again");
                faces.forEach(f -> log(f.getGeometry()));
                // return Optional.empty();
            } else {
                o = Points.project(o, faces.get(0).getGeometry().getExteriorRing());
            }
        }
        // Face face = faces.get(0);
        // List<HalfEdge> edges = face.getEdges().stream().filter(h -> h.getAttribute("EXTERIOR").toString().equals("false")).collect(Collectors.toList());
        // List<HalfEdge> edges = graph.getEdges().stream().filter(h -> h.getAttribute("EXTERIOR").toString().equals("false")).collect(Collectors.toList());
        // List<HalfEdge> edges = graph.getEdges();
        edges.forEach(e -> log(e.getGeometry()));
        final Coordinate origin = o;
        Optional<ImmutablePair<HalfEdge, Coordinate>> intersections = edges.stream()
                .filter(h -> Lines.getRayLineSegmentIntersects(origin, d, h.getGeometry()))
                .map(h -> new ImmutablePair<>(h, Lines.getRayLineSegmentIntersection(origin, d, h.getGeometry())))
                // .filter(pair->pair.getRight().distance(origin) > tolerance)
                .sorted((Pair<HalfEdge, Coordinate> p1, Pair<HalfEdge, Coordinate> p2) -> Double.compare(p1.getRight().distance(origin),
                        p2.getRight().distance(origin)))
                .findFirst();
        return intersections;
    }

    private Optional<Coordinate> getIntersection(Coordinate o, Coordinate d, Polygon polygon) {
        Point p = factory.createPoint(o);
        log("getIntersection\n" + p + "\n" + d.getX() + "," + d.getY() + "\n" + factory.createPoint(new Coordinate(o.x + d.x, o.y + d.y)));
        final Coordinate origin = o;
        LineString ring = polygon.getExteriorRing();
        List<LineString> segments = new ArrayList<>();
        for (int index = 0; index < ring.getNumPoints() - 1; index++) {
            Coordinate c1 = ring.getCoordinateN(index);
            Coordinate c2 = ring.getCoordinateN(index + 1);
            segments.add(factory.createLineString(new Coordinate[]{c1, c2}));
        }
        Optional<Coordinate> intersection = segments.stream().filter(h -> Lines.getRayLineSegmentIntersects(origin, d, h))
                .map(h -> Lines.getRayLineSegmentIntersection(origin, d, h))
                .sorted((p1, p2) -> Double.compare(p1.distance(origin), p2.distance(origin))).findFirst();
        return intersection;
    }

    private static double getAngle(Coordinate c1, Coordinate c2, Coordinate d) {
        return Angle.angleBetween(c2, c1, new Coordinate(c1.x + d.x, c1.y + d.y));
    }

    private List<Coordinate> getPath(List<HalfEdge> graphEdges, Polygon strip, Node node, Coordinate direction) {
        // Coordinate stripCoordinate = getCoordinate(stripCoordinates, node.getCoordinate(), 0.01);
        // if (stripCoordinate != null)
        // return Arrays.asList(stripCoordinate);// we reached the border of the strip
        if (strip.getExteriorRing().distance(factory.createPoint(node.getCoordinate())) <= tolerance) {
            // we are 'on' the border of the strip
            Coordinate projection = Points.project(node.getCoordinate(), strip.getExteriorRing());
            log("PROJECT\n" + node.getCoordinate() + "\nTO" + projection);
            return Collections.singletonList(projection);
        }
        // find the best edge to follow
        List<HalfEdge> edges = TopologicalGraph.outgoingEdgesOf(node, graphEdges);
        if (edges.isEmpty()) {
            log("NO EDGE FROM\n" + node.getGeometry());
            Optional<Coordinate> intersection = getIntersection(node.getCoordinate(), new Coordinate(-direction.x, -direction.y), strip);
            if (intersection.isPresent()) {
                log("FOUND INTERSECTION AT\n" + factory.createPoint(intersection.get()));
                return Collections.singletonList(intersection.get());
            }
            return Collections.emptyList();
        }
        edges.sort((HalfEdge h1, HalfEdge h2) -> Double.compare(getAngle(h1.getOrigin().getCoordinate(), h1.getTarget().getCoordinate(), direction),
                getAngle(h2.getOrigin().getCoordinate(), h2.getTarget().getCoordinate(), direction)));
        HalfEdge edge = edges.get(0);
        graphEdges.remove(edge);
        // we don't want to go back
        if (edge.getTwin() != null)
            graphEdges.remove(edge.getTwin());
        List<Coordinate> path = new ArrayList<>();
        path.add(node.getCoordinate());
        Coordinate d = getUnitVector(node.getCoordinate(), edge.getTarget().getCoordinate());
        path.addAll(getPath(graphEdges, strip, edge.getTarget(), d));
        return path;
    }

    private LineString getCutLine(Polygon strip, Coordinate coordinate, LineString support) {
        log("getCutLine FROM\n" + factory.createPoint(coordinate) + "\nWITH EXT STRIP\n" + strip.getExteriorRing() + "\nWITH SUPPORT\n" + support);
        Node node = straightSkeleton.getGraph().getNode(coordinate, tolerance);
        // Node node = betaStrips.getNode(coordinate, tolerance);
        List<HalfEdge> edges = (node == null) ? new ArrayList<>()
                : straightSkeleton.getGraph().getEdges().stream().filter(h -> h.getOrigin() == node && h.getTwin() != null)
                .collect(Collectors.toList());
        if (node == null || edges.isEmpty()) {
            // log("NO NODE");
            // no node here, compute perpendicular line
            Coordinate c1 = support.getCoordinateN(1);
            Coordinate c2 = support.getCoordinateN(0);
            Coordinate d = getPerpendicularVector(c1, c2, false);
            edges = straightSkeleton.getGraph().getEdges().stream().filter(h -> h.getAttribute("EXTERIOR").toString().equals("false"))
                    .collect(Collectors.toList());
            Optional<ImmutablePair<HalfEdge, Coordinate>> optionalIntersection = getIntersection(c2, d, straightSkeleton.getGraph(), edges);
            if (!optionalIntersection.isPresent()) {
                // try again with exterior edges (but not the one the point is on)
                edges = straightSkeleton.getGraph().getEdges().stream().filter(
                        h -> h.getAttribute("EXTERIOR").toString().equals("true") && h.getGeometry().distance(factory.createPoint(c2)) > tolerance)
                        .collect(Collectors.toList());
                optionalIntersection = getIntersection(c2, d, straightSkeleton.getGraph(), edges);
                if (!optionalIntersection.isPresent()) {
                    return null;
                }
                return factory.createLineString(new Coordinate[]{c2, optionalIntersection.get().getRight()});
            }
            Pair<HalfEdge, Coordinate> intersection = optionalIntersection.get();
            Coordinate intersectionCoord = intersection.getRight();
            // Polygon snapped = snap(strip, intersectionCoord, tolerance);
            log("INTERSECTION\n" + factory.createPoint(intersectionCoord) + "\n" + strip + "\n" + intersection.getLeft().getGeometry());
            if (strip.getExteriorRing().distance(factory.createPoint(intersectionCoord)) <= tolerance) {
                // we are 'on' the border of the strip
                Coordinate projection = Points.project(intersectionCoord, strip.getExteriorRing());// use snapped?
                log("PROJECTION\n" + factory.createLineString(new Coordinate[]{c2, projection}));
                // return factory.createLineString(new Coordinate[] { c2, intersectionCoord });
                return factory.createLineString(new Coordinate[]{c2, projection});
            }
            List<Coordinate> coordinateList = new ArrayList<>();
            coordinateList.add(c2);
            coordinateList.add(intersectionCoord);
            Node currentNode = straightSkeleton.getGraph().getNode(intersectionCoord, tolerance);
            // Node currentNode = betaStrips.getNode(intersectionCoord, tolerance);
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
            // coordinateList.addAll(getPath(betaStrips, strip, currentNode, d));
            coordinateList.addAll(getPath(new ArrayList<>(straightSkeleton.getGraph().getEdges()), strip, currentNode, d));
            return factory.createLineString(coordinateList.toArray(new Coordinate[coordinateList.size()]));
        }
        // List<HalfEdge> edges = betaStrips.getEdges().stream().filter(h -> h.getOrigin() == node && h.getTwin() != null).collect(Collectors.toList());
        // List<HalfEdge> edges = straightSkeleton.getGraph().getEdges().stream().filter(h -> h.getOrigin() == node && h.getTwin() != null).collect(Collectors.toList());
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
            // coordinateList.addAll(getPath(betaStrips, strip, edge.getTarget(), d));
            coordinateList.addAll(getPath(new ArrayList<>(straightSkeleton.getGraph().getEdges()), strip, edge.getTarget(), d));
            return factory.createLineString(coordinateList.toArray(new Coordinate[coordinateList.size()]));
            // log(edges.get(0).getGeometry());
        }
        return null;
    }

    private List<Polygon> slice(double minWidth, double maxWidth, RealDistribution nd, Face strip) {
        List<Polygon> result = new ArrayList<>();
        if (strip.getGeometry().isEmpty()) {
            log("EMPTY STRIP");
            return result;
        }
        LineString psi = psiMap.get(strip);
        LengthIndexedLine lil = new LengthIndexedLine(psi);
        log("psi\n" + psi);
        if (psi == null) {
            log("face not found for strip " + strip.getGeometry());
            return Collections.singletonList(strip.getGeometry());
        }
        double length = psi.getLength();
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
                log("REMAINDER\n" + remainder);
                log(snapped(remainder));
                LineString cutLine = getCutLine(remainder, coordinate, l);
                log("cutLine\n" + cutLine);
                Geometry[] snapped = GeometrySnapper.snap(remainder, cutLine, tolerance);
                log("GeometrySnapper\n" + snapped[0]);
                log("GeometrySnapper\n" + snapped[1]);
                Pair<Polygon, Polygon> split = splitPolygon((Polygon) snapped[0], (LineString) snapped[1]);
                result.add(split.getLeft());
                remainder = split.getRight();
                if (remainder == null)
                    break;
                current = next;
            }
            result.add(remainder);
        }
        return result;
    }

    private List<Polygon> createParcels(double minWidth, double maxWidth, double omega, RandomGenerator rng) {
        NormalDistribution nd = new NormalDistribution(rng, (minWidth + maxWidth) / 2, Math.sqrt(3 * omega));
        List<Polygon> result = new ArrayList<>();
        for (Face face : betaStrips.getFaces())
            result.addAll(slice(minWidth, maxWidth, nd, face));
        return result;
    }

    /**
     * The α-strips computed from the skeleton faces suffer from diagonal edges at the intersection of logical streets [...]. To correct these edges, we modify LS (B) [...] to
     * transfer a near-triangular region from the strip on one side of an offending edge to the strip on the other side. We refer to these corrected strips as β-strips.
     * <p>
     * TODO support multiple supporting vertex classification schemes
     *
     * @param alphaStrips α-strips
     * @param attributes  attributes of the edges (used to classify supporting vertices)
     * @return β-strips
     * @throws EdgeException
     */
    private TopologicalGraph fixDiagonalEdges(TopologicalGraph alphaStrips, Map<HalfEdge, Optional<Pair<String, Double>>> attributes) throws EdgeException {
        // name the faces and create supporting edges
        log("");
        log("");
        log("CREATE BETA STRIPES");
        log("");
        log("");
        List<LineString> psiList = new ArrayList<>();
        for (int id = 0; id < alphaStrips.getFaces().size(); id++) {
            Face strip = alphaStrips.getFaces().get(id);
            strip.setAttribute("ID", id);
            // create the supporting edge psi
            List<HalfEdge> extEdges = getOrderedEdges(strip.getEdges().stream()
                    .filter(e -> e.getTwin() == null && !initialPolygon.buffer(-0.1).contains(e.getGeometry())).collect(Collectors.toList()));
            List<Coordinate> coordinates = extEdges.stream()
                    .flatMap(e -> Arrays.asList(e.getGeometry().getCoordinates()).subList(1, e.getGeometry().getNumPoints()).stream())
                    .collect(Collectors.toList());
            coordinates.add(0, extEdges.get(0).getOrigin().getCoordinate());
            LineString psi = factory.createLineString(coordinates.toArray(new Coordinate[coordinates.size()]));
            psiList.add(psi);
        }
        log("PSIlist:");
        psiList.stream().forEach(p -> log(p));
        // classify supporting vertices
        for (Node n : alphaStrips.getNodes()) {
            log("node\n" + n.getGeometry());
            HalfEdge previousEdge = TopologicalGraph.incomingEdgesOf(n, orderedExteriorEdges).get(0);
            HalfEdge nextEdge = TopologicalGraph.outgoingEdgesOf(n, orderedExteriorEdges).get(0);
            int supportingVertexClass = classify(n, previousEdge, attributes.get(previousEdge), nextEdge, attributes.get(nextEdge)); //decide whether the edge must be turned to the previous direction or to the next direction
            Face prevFace = previousEdge.getFace(), nextFace = nextEdge.getFace();
            Face prevAlphaStrip = prevFace.getParent(), nextAlphaStrip = nextFace.getParent();
            // FIXME use parent edge children?
            // get all edges connecting the current and previous strips (they form the diagonal edge)
            List<HalfEdge> edgeList = straightSkeleton.getGraph().getEdges().stream().filter(e -> e.getTwin() != null)
                    .filter(e -> (e.getFace().getParent() == prevAlphaStrip && e.getTwin().getFace().getParent() == nextAlphaStrip))
                    .collect(Collectors.toList());
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
                log("SPLIT WITH " + supportingVertexClass + "\n" + currNode.getGeometry() + "\n" + splitAlphaStrip.getGeometry());
                Coordinate projection = Points.project(currNode.getCoordinate(), splitAlphaStrip.getEdges().get(0).getGeometry());
                log("SPLIT WITH\n" + currNode.getGeometry().getFactory().createPoint(projection));
                Polygon splitAlphaStripSnapped = snap(splitAlphaStrip.getGeometry(), projection, tolerance);
                Polygon r = (Polygon) precisionReducer.reduce(splitAlphaStripSnapped);
                LineString cl = (LineString) precisionReducer
                        .reduce(factory.createLineString(new Coordinate[]{projection, currNode.getCoordinate()}));
                Geometry[] snapped = GeometrySnapper.snap(r, cl, tolerance);
                if (cl.isEmpty()) {
                    log("IGNORING EMPTY PROJECTION LINE \n" + splitAlphaStrip.getGeometry() + "\n" + factory.createPoint(projection) + "\n"
                            + factory.createPoint(currNode.getCoordinate()));
                    continue;
                }
                if (cl.getCoordinateN(0).distance(n.getCoordinate()) <= tolerance) {
                    // projection, once snapped, is too close to the supporting vertex
                    log("IGNORING\n" + splitAlphaStrip.getGeometry() + "\n" + cl.getPointN(0));
                    continue;
                }
                log("SPLIT POLYGON\n" + snapped[0] + "\nWITH LINE\n" + snapped[1]);
                Pair<Polygon, Polygon> split = splitPolygon((Polygon) snapped[0], (LineString) snapped[1]);
                if (split == null) {
                    log("SKIPPING\n" + splitAlphaStrip.getGeometry() + "\n" + cl.getPointN(0) + "\n");
                    continue;
                }
                // Pair<Polygon, Polygon> split = splitPolygon(splitAlphaStrip.getGeometry(), currNode.getCoordinate(), projection);
                Face gainingBetaSplit = (supportingVertexClass == PREVIOUS) ? prevAlphaStrip : nextAlphaStrip;
                Face loosingBetaSplit = (supportingVertexClass == PREVIOUS) ? nextAlphaStrip : prevAlphaStrip;
                Polygon gainingBetaSplitPolygon = gainingBetaSplit.getGeometry();
                Polygon exchangedPolygonPart = (supportingVertexClass == PREVIOUS) ? split.getLeft() : split.getRight();
                Polygon remainingPolygonPart = (supportingVertexClass == PREVIOUS) ? split.getRight() : split.getLeft();
                List<Polygon> newAbsorbing = new ArrayList<>();
                newAbsorbing.add(gainingBetaSplitPolygon);
                newAbsorbing.add(exchangedPolygonPart);
                gainingBetaSplit.setPolygon(Polygons.polygonUnion(newAbsorbing, precisionReducer));
                log("UNION\n" + gainingBetaSplit.getGeometry());
                // loosingBetaSplit.setPolygon(Util.polygonDifference(Arrays.asList(splitAlphaStripSnapped), Arrays.asList(exchangedPolygonPart)));
                try {
                    log("DIFFERENCE\n" + Polygons.polygonDifference(Collections.singletonList(splitAlphaStripSnapped), Collections.singletonList(exchangedPolygonPart)));
                } catch (NullPointerException npe) {
//				npe.printStackTrace();
                }
                log("REMAINING\n" + remainingPolygonPart);
                if (remainingPolygonPart != null) {
                    loosingBetaSplit.setPolygon((Polygon) precisionReducer.reduce(remainingPolygonPart));
                    log("REDUCED\n" + loosingBetaSplit.getGeometry());
                }
                // clean up the edges
                log("Removing " + diagonalEdgeList.size() + " diagonal edges and their twins");
                diagonalEdgeList.forEach(e -> log(e.getGeometry()));
                straightSkeleton.getGraph().getEdges()
                        .removeAll(diagonalEdgeList.stream().flatMap(e -> Stream.of(e, e.getTwin())).collect(Collectors.toList()));
                // get all the nodes on the diagonal except the last one
                List<Node> nodes = diagonalEdgeList.stream().map(HalfEdge::getOrigin).distinct().collect(Collectors.toList());
                // except the first one too
                nodes.remove(n);
                // remove skeleton arcs for the removed region
                List<HalfEdge> internalEdges = removeInternalEdges(nodes, loosingBetaSplit);
                log("Removing " + internalEdges.size() + " internal edges");
                straightSkeleton.getGraph().getEdges().removeAll(internalEdges);
                // recompute the skeleton arcs to remain perpendicular to the local edges ψ for the dangling edges
                List<Node> nodesToClean = nodes.stream().filter(node -> !recomputeEdges(node, gainingBetaSplit, cl)).collect(Collectors.toList());
                internalEdges = removeInternalEdges(nodesToClean, gainingBetaSplit);
                log("Removing " + internalEdges.size() + " internal edges");
                straightSkeleton.getGraph().getEdges().removeAll(internalEdges);
                // add the new edge
                HalfEdge e = new HalfEdge(straightSkeleton.getGraph().getOrCreateNode(cl.getCoordinateN(0)),
                        straightSkeleton.getGraph().getOrCreateNode(currNode.getCoordinate()));
                e.setAttribute("EXTERIOR", "false");
                straightSkeleton.getGraph().getEdges().add(e);
                if (remainingPolygonPart == null || loosingBetaSplit.getGeometry() == null || loosingBetaSplit.getGeometry().isEmpty()) {
                    log("REMOVING FACE (NULL OR EMPTY)");
                    alphaStrips.getFaces().remove(loosingBetaSplit);
                }
            }
        }
        export(new TopologicalGraph(alphaStrips.getFaces().stream().map(Face::getGeometry).collect(Collectors.toList()), tolerance),
                new File(FOLDER_OUT_DEBUG, "beta_before_snap"));
        // snap everything back to the original graph
        TopologicalGraph result = new TopologicalGraph(
                alphaStrips.getFaces().stream().map(f -> snapped(f.getGeometry())).collect(Collectors.toList()), tolerance);
        export(result, new File(FOLDER_OUT_DEBUG, "beta_after_snap"));
        // cleanup strips without exterior edge
        orderedExteriorEdges = getOrderedExteriorEdges(result);
        List<Face> toRemove = new ArrayList<>();
        for (Face face : result.getFaces()) {
            long extEdges = face.getEdges().stream().filter(e -> orderedExteriorEdges.contains(e)).count();
            if (extEdges == 0) {
                // the face has no access to the road
                log("REMOVING FACE " + face.getGeometry());
                Optional<Face> f = face.getEdges().stream().filter(e -> e.getTwin() != null)
                        .map(e -> new ImmutablePair<>(e.getGeometry().getLength(), e.getTwin().getFace()))
                        .sorted((a, b) -> Double.compare(b.getLeft(), a.getLeft())).map(ImmutablePair::getRight).findFirst();
                if (f.isPresent()) {
                    Face gainingFace = f.get();
                    face.getEdges().forEach(e -> e.setFace(gainingFace));
                    log("MERGE WITH FACE " + gainingFace.getGeometry());
                    gainingFace.setPolygon(Polygons.polygonUnion(Arrays.asList(face.getGeometry(), gainingFace.getGeometry()), precisionReducer));
                    log("RESULT IS " + gainingFace.getGeometry());
                    toRemove.add(face);
                }
            }
        }
        result.getFaces().removeAll(toRemove);
        // modify the snapping geometry to add the new points where necessary
        // snapGeom = factory.createGeometryCollection(result.getFaces().stream().map(f -> f.getGeometry()).collect(Collectors.toList()).toArray(new Geometry[] {}));
        result = new TopologicalGraph(result.getFaces().stream().map(f -> snapped(f.getGeometry())).collect(Collectors.toList()), tolerance);
        export(result, new File(FOLDER_OUT_DEBUG, "beta_after_removal"));
        // cleanup strips without exterior edge
        orderedExteriorEdges = getOrderedExteriorEdges(result);
        // add the initial supporting edges reduced to fit into the final beta strips
        psiMap = new HashMap<>();
        for (Face face : result.getFaces()) {
            log("face : " + face.getGeometry());
            psiList.stream().map(p -> reduced(p, face)).filter(Optional::isPresent)
                    .filter(o -> getRoadAttributes(o.get(), 10.0).isPresent())
                    .forEach(l -> log(l.get()));
            List<ImmutablePair<LineString, Double>> tmp = psiList.stream().map(p -> reduced(p, face)).filter(Optional::isPresent)
                    .filter(o -> getRoadAttributes(o.get(), 10.0).isPresent())
                    .map(o -> new ImmutablePair<>(o.get(), o.get().getLength())).sorted((a, b) -> Double.compare(b.getRight(), a.getRight())).collect(Collectors.toList());
            tmp.forEach(t -> log(t.getRight() + " => " + t.getLeft()));
            Optional<LineString> optionalPsi = tmp.stream()
                    .findFirst().map(ImmutablePair::getLeft);
            log("/face");
            if (optionalPsi.isPresent()) {
                psiMap.put(face, optionalPsi.get());
//            LineString l = Lines.union(psiList.stream().map(p -> reduced(p, face)).filter(Optional::isPresent).map(o -> o.get()).collect(Collectors.toList()));
//            if (l != null) {
//                psiMap.put(face,l);
            } else
                log("No supporting edge found for face\n" + face.getGeometry());
        }
        return result;
    }

    private Stream<HalfEdge> getSupportingEdges(Node node, Face split, List<HalfEdge> internalEdges) {
        List<HalfEdge> edges = internalEdges.stream().filter(e -> e.getOrigin() == node).collect(Collectors.toList());
        log("getSupportingEdges " + edges.size());
        edges.forEach(e -> log(e.getGeometry()));
        if (edges.isEmpty()) {
            // no more internal edge, return the supporting edge(s?)
            // return TopologicalGraph.outgoingEdgesOf(node, this.straightSkeleton.getGraph().getEdges()).stream().filter(e -> e.getTwin() == null);
            return TopologicalGraph.incomingEdgesOf(node, this.straightSkeleton.getGraph().getEdges()).stream().filter(e -> e.getTwin() == null);
        }
        internalEdges.removeAll(edges.stream().flatMap(e -> Stream.of(e, e.getTwin())).collect(Collectors.toList()));
        return edges.stream().flatMap(e -> getSupportingEdges(e.getTarget(), split, internalEdges));
    }

    private boolean recomputeEdges(Node node, Face split, LineString cutline) {
        log("recomputeEdges\n" + node.getGeometry());
        // get the supporting edge
        // get the internal edges of the face
        List<HalfEdge> internalEdges = this.straightSkeleton.getGraph().getEdges().stream()
                .filter(e -> e.getFace() != null && e.getFace().getParent() == split && e.getTwin() != null && e.getTwin().getFace() != null
                        && e.getTwin().getFace().getParent() == split)
                .collect(Collectors.toList());
        internalEdges.forEach(e -> log(e.getGeometry()));
        List<HalfEdge> support;
        try {
            support = getOrderedEdges(getSupportingEdges(node, split, internalEdges).collect(Collectors.toList()));
        } catch (EdgeException e1) {
            // TODO Auto-generated catch block
            e1.printStackTrace();
            return false;
        }
        if (support.isEmpty()) {
            log("NO SUPPORT EDGE FOR " + node.getGeometry());
            return false;
        }
        log("SUPPORT");
        support.forEach(e -> log(e.getGeometry()));
        // we assume we found at least one supporting edge
        Coordinate c0 = support.get(0).getOrigin().getCoordinate();
        Coordinate c1 = support.get(support.size() - 1).getTarget().getCoordinate();
        Coordinate direction = getPerpendicularVector(c0, c1, true);
        log("direction\n" + factory.createPoint(new Coordinate(node.getCoordinate().x + direction.x, node.getCoordinate().y + direction.y)));
        // project the node in the direction perpendicular to the supporting edge
        // create the edge and the target node

        // we should project a line to the border and modify the graph accordingly
        Coordinate intersection = Lines.getRayLineSegmentIntersection(node.getCoordinate(), direction, cutline);
        if (intersection != null) {
            Node targetNode = this.straightSkeleton.getGraph().getOrCreateNode(intersection);
            HalfEdge e = new HalfEdge(node, targetNode);
            e.setAttribute("EXTERIOR", "false");
            this.straightSkeleton.getGraph().getEdges().add(e);
            return true;
        } else {
            log("NO INTERSECTION WITH\n" + cutline);
            return false;
        }
    }

    private List<HalfEdge> removeInternalEdges(List<Node> nodes, Face split) {
        // get the internal edges of the face
        List<HalfEdge> internalEdges = this.straightSkeleton.getGraph().getEdges().stream()
                .filter(e -> e.getFace() != null && e.getFace().getParent() == split && e.getTwin() != null && e.getTwin().getFace() != null
                        && e.getTwin().getFace().getParent() == split)
                .collect(Collectors.toList());
        return nodes.stream().flatMap(n -> removeInternalEdges(n, split, internalEdges)).flatMap(e -> Stream.of(e, e.getTwin()))
                .collect(Collectors.toList());
    }

    private Stream<HalfEdge> removeInternalEdges(Node node, Face split, List<HalfEdge> internalEdges) {
        List<HalfEdge> edges = internalEdges.stream().filter(e -> e.getOrigin() == node).collect(Collectors.toList());
        if (edges.isEmpty())
            return Stream.empty();
        internalEdges.removeAll(edges);
        return edges.stream().flatMap(e -> Stream.concat(Stream.of(e), removeInternalEdges(e.getTarget(), split, internalEdges)));
    }

    private Optional<LineString> reduced(LineString psi, Polygon polygon) {
        GeometrySnapper snapper = new GeometrySnapper(psi);
        psi = (LineString) snapper.snapTo(polygon, tolerance);
        List<Coordinate> coords = new ArrayList<>();
        for (int index = 0; index < psi.getNumPoints(); index++) {
            Point p = psi.getPointN(index);
            if (p.intersects(polygon)) {
                coords.add(p.getCoordinate());
            } else {
                if (!coords.isEmpty())
                    break;
            }
        }
        // log(coords.size());
        if (coords.size() > 1) {
            return Optional.of(factory.createLineString(coords.toArray(new Coordinate[coords.size()])));
        }
        return Optional.empty();
    }

    private Optional<LineString> reduced(LineString psi, Face face) {
        return reduced(psi, face.getGeometry());
    }

    private Polygon snapped(Polygon polygon, double tolerance) {
        GeometrySnapper snapper = new GeometrySnapper(polygon);
        return (Polygon) snapper.snapTo(snapInitialSSFaces, tolerance);
    }

    private Polygon snapped(Polygon polygon) {
        return snapped(polygon, tolerance);
    }

    @SuppressWarnings("rawtypes")
    public static List<Geometry> polygonize(Geometry geometry) {
        LineMerger merger = new LineMerger();
        merger.add(geometry);
        Polygonizer polygonizer = new Polygonizer();
        polygonizer.add(merger.getMergedLineStrings());
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
            if (distance <= tolerance && c1.distance(c) > distance && c2.distance(c) > distance) {
                // log("INSERTING " + l.getFactory().createPoint(c));
                coordinates.add(c);
            }
        }
        coordinates.add(l.getCoordinateN(0));// has to be a loop / ring
        return l.getFactory().createLinearRing(coordinates.toArray(new Coordinate[coordinates.size()]));
    }

    private static Polygon snap(Polygon poly, Coordinate c, double tolerance) {
        LinearRing shell = snap(poly.getExteriorRing(), c, tolerance);
        LinearRing[] holes = new LinearRing[poly.getNumInteriorRing()];
        for (int i = 0; i < holes.length; i++) {
            holes[i] = snap(poly.getInteriorRingN(i), c, tolerance);
        }
        return poly.getFactory().createPolygon(shell, holes);
    }

    public Pair<Polygon, Polygon> splitPolygon(Polygon poly, Coordinate origin, Coordinate projection) {// LineString line) {
        LineString line = poly.getFactory().createLineString(new Coordinate[]{projection, origin});
        Polygon snapped = snap(poly, projection, tolerance);
        return splitPolygon(snapped, line);
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
            if (exterior.intersects(factory.createPoint(c))) {
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
        return factory.createLineString(coords.toArray(new Coordinate[coords.size()]));
    }

    public Pair<Polygon, Polygon> splitPolygon(Polygon poly, LineString line) {
        Polygon snap = (Polygon) GeometrySnapper.snapToSelf(poly, tolerance, true);
        if (!snap.isEmpty()) {
            poly = snap;
        }
        LineString reducedLine = part(poly, line);
        GeometrySnapper snapper = new GeometrySnapper(reducedLine);
        reducedLine = (LineString) snapper.snapTo(poly, tolerance);
        log("snapped\n" + poly + "\n" + reducedLine);
        Geometry nodedLinework = poly.getBoundary().union(reducedLine);
        List<Geometry> polys = polygonize(nodedLinework);
        // Only keep polygons which are inside the input
        // List<Polygon> output = polys.stream().map(g -> (Polygon) precisionReducer.reduce(g)).filter(g -> poly.contains(g.getInteriorPoint())).collect(Collectors.toList());
        final Polygon p = poly;
        List<Polygon> output = polys.stream().map(g -> (Polygon) g).filter(g -> p.contains(g.getInteriorPoint())).collect(Collectors.toList());
        if (output.size() != 2) {
            log("OUTPUT WITH " + output.size() + " ( " + polys.size() + " )");
            log("SPLIT\n" + poly + "\nWITH\n" + line + "\nPART\n" + reducedLine);
            log("NODED\n" + nodedLinework);
            log("POLYGONIZED (" + polys.size() + ")");
            log(polys);
        }
        if (output.size() == 1) {
            int position0 = position(output.get(0), reducedLine);
            if (position0 == LEFT) {
                return new ImmutablePair<>(output.get(0), null);
            } else {
                return new ImmutablePair<>(null, output.get(0));
            }
        }
        // try to order them from left to right
        int position0 = position(output.get(0), reducedLine);
        int position1 = position(output.get(1), reducedLine);
        if (position0 == LEFT && position1 == RIGHT) {
            return new ImmutablePair<>(output.get(0), output.get(1));
        }
        if (position0 == RIGHT && position1 == LEFT) {
            return new ImmutablePair<>(output.get(1), output.get(0));
        }
        log("SPLIT WITH\n" + reducedLine);
        log(factory.createPoint(reducedLine.getCoordinateN(0)));
        log(factory.createPoint(reducedLine.getCoordinateN(1)));
        log(output.get(0).getExteriorRing());
        log("position " + position0);
        log("position " + position1);
        return null;
    }

    private static final int RIGHT = 1;
    private static final int LEFT = -1;
    private static final int NEITHER = 0;

    private int position(Polygon polygon, LineString line) {
        GeometrySnapper snapper = new GeometrySnapper(line);
        line = part(polygon, (LineString) snapper.snapTo(polygon, tolerance));
        LengthIndexedLine lil = new LengthIndexedLine(line);
        Coordinate c0 = lil.extractPoint(0);
        // get the index of the first coordinate of the line along the polygon
        int firstIndex = -1;
        for (int index = 0; index < polygon.getExteriorRing().getNumPoints(); index++) {
            if (c0.equals2D(polygon.getExteriorRing().getCoordinateN(index))) {
                firstIndex = index;
                break;
            }
        }
        if (firstIndex >= 0) {
            Coordinate c1 = polygon.getExteriorRing().getCoordinateN(firstIndex);
            double a = lil.indexOf(c1);
            Coordinate c2 = polygon.getExteriorRing().getCoordinateN(firstIndex + 1);
            int size = polygon.getExteriorRing().getNumPoints() - 1; // we ignore the last coord
            if (line.isCoordinate(c2)) {
                double b = lil.indexOf(c2);
                log("POLYGON CW (" + size + ")\n" + polygon + "\n" + line);
                log("LENGTH INDEX: " + a);
                log("LENGTH INDEX: " + b);
                log("INDEX: " + firstIndex);
                log("INDEX: " + (firstIndex + 1));
                log("P\n" + polygon.getExteriorRing().getPointN(firstIndex));
                log("P\n" + polygon.getExteriorRing().getPointN(firstIndex + 1));
                log((b > a) ? "RIGHT" : "LEFT");
                return (b > a) ? RIGHT : LEFT;
            } else {
                c2 = polygon.getExteriorRing().getCoordinateN((firstIndex + size - 1) % size);
                double b = lil.indexOf(c2);
                log("POLYGON CCW (" + size + ")\n" + polygon + "\n" + line);
                log("LENGTH INDEX: " + a);
                log("LENGTH INDEX: " + b);
                log("INDEX: " + firstIndex);
                log("INDEX: " + ((firstIndex + size - 1) % size));
                log("P\n" + polygon.getExteriorRing().getPointN(firstIndex));
                log("P\n" + polygon.getExteriorRing().getPointN((firstIndex + size - 1) % size));
                log((b > a) ? "LEFT" : "RIGHT");
                return (b > a) ? LEFT : RIGHT;
            }
        }
        return NEITHER;
    }

    public static int sharedPoints(Geometry a, Geometry b) {
        Geometry[] snapped = GeometrySnapper.snap(a, b, 0.1);
        Set<Coordinate> ca = new HashSet<>(Arrays.asList(snapped[0].getCoordinates()));
        Set<Coordinate> cb = new HashSet<>(Arrays.asList(snapped[1].getCoordinates()));
        return (int) ca.stream().filter(cb::contains).count();
    }

    public static SimpleFeatureCollection runTopologicalStraightSkeletonParcelDecomposition(SimpleFeatureCollection sfcParcelIn, File roadFile, String NAME_ATT_ROAD, String NAME_ATT_IMPORTANCE, double maxDepth, double maxDistanceForNearestRoad, double minimalArea, double minWidth, double maxWidth, double omega, RandomGenerator rng, double streetWidth, String name) throws IOException {
        DataStore dsRoad = CollecMgmt.getDataStore(roadFile);
        SimpleFeatureCollection p = runTopologicalStraightSkeletonParcelDecomposition(sfcParcelIn, dsRoad.getFeatureSource(dsRoad.getTypeNames()[0]).getFeatures(), NAME_ATT_ROAD, NAME_ATT_IMPORTANCE, maxDepth, maxDistanceForNearestRoad,
                minimalArea, minWidth, maxWidth, omega, rng, streetWidth, name);
        dsRoad.dispose();
        return p;
    }

    /**
     * Run Straight Skeleton on marked parcels
     */
    public static SimpleFeatureCollection runTopologicalStraightSkeletonParcelDecomposition(SimpleFeatureCollection sfcParcelIn, SimpleFeatureCollection roads, String NAME_ATT_ROAD, String NAME_ATT_IMPORTANCE, double maxDepth, double maxDistanceForNearestRoad, double minimalArea, double minWidth, double maxWidth, double omega, RandomGenerator rng, double streetWidth, String name) {
        DefaultFeatureCollection result = new DefaultFeatureCollection();
        try (SimpleFeatureIterator parcelIt = sfcParcelIn.features()) {
            while (parcelIt.hasNext()) {
                SimpleFeature sf = parcelIt.next();
                if (sf.getAttribute(MarkParcelAttributeFromPosition.getMarkFieldName()).equals(1))
                    result.addAll(runTopologicalStraightSkeletonParcelDecomposition(sf, roads, NAME_ATT_ROAD, NAME_ATT_IMPORTANCE, maxDepth,
                            maxDistanceForNearestRoad, minimalArea, minWidth, maxWidth, omega, rng, streetWidth, name));
            }
        } catch (Exception problem) {
            problem.printStackTrace();
        }
        return result;
    }

    /**
     * Creates a road around the parcel. reduce the parcel polygon and add the newly created road to the road collection
     *
     * @param p         initial polygon shape
     * @param roadWidth width of the road to create
     * @return the new shape of the polygon
     */
    public Polygon generatePeripheralRoad(Polygon p, double roadWidth) {
        Polygon newGeom = Polygons.getPolygon(p.buffer(-roadWidth));
        if (newGeom == null || newGeom.isEmpty())
            return p;
        DefaultFeatureCollection newRoad = new DefaultFeatureCollection();
        SimpleFeatureBuilder roadSFB = new SimpleFeatureBuilder(roads.getSchema());
        newRoad.addAll(roads);
        List<Polygon> lp = FeaturePolygonizer.getPolygons(Arrays.asList(newGeom, Polygons.getPolygon(p.buffer(-roadWidth / 2))));
        for (Polygon pp : lp) {
            if (newGeom.buffer(0.5).contains(pp)) //skip if the parcel is the interior one
                continue;
            int nb = 0;
            for (MultiLineString ls : dividePeripheralRoadInParts(pp.getExteriorRing())) {
                roadSFB.set(roads.getSchema().getGeometryDescriptor().getLocalName(), ls);
                roadSFB.set(this.NAME_ATT_ROADNAME, "autogenerated" + nb++);
//			roadSFB.set(this.NAME_ATT_IMPORTANCE, ( new Random()).nextInt(5));
                roadSFB.set(this.NAME_ATT_LEVELOFATTRACTION, 4);
                SimpleFeature sf = roadSFB.buildFeature(Attribute.makeUniqueId());
                newRoad.add(sf);
            }
        }
        this.roads = newRoad;
        return newGeom;
    }

    /**
     * Divide a peripheral road into multiple parts. Will create a new segment if the road angle is too important (less than 2/3 pi) and have a certain length.
     *
     * @param lr peripheral road
     * @return list of road segments
     */
    public static List<MultiLineString> dividePeripheralRoadInParts(LinearRing lr) {
        List<MultiLineString> ls = new ArrayList<>();
        double len = lr.getLength() / 2;
        Coordinate[] coordinates = lr.getCoordinates();
        List<LineString> oneRoad = new ArrayList<>();
        double lenCrossed = 0;
        for (int c = 0; c < coordinates.length - 1; c++) {
            LineString segment = new GeometryBuilder(lr.getFactory()).lineString(coordinates[c].x, coordinates[c].y, coordinates[c + 1].x, coordinates[c + 1].y);
            lenCrossed += segment.getLength();
            if (oneRoad.size() > 0) {
                // get last (must be long enough to represent a real road segment, not a buffer angle part)
                LineString last = null;
                for (int l = oneRoad.size() - 1; l >= 0; l--) {
                    if (oneRoad.get(l).getLength() > 2 && segment.getLength() > 2) { // if the length of a segment is superior to two meters, we consider it's a real segment
                        last = oneRoad.get(l);
                        break;
                    }
                }
                if (last != null && Angle.angleBetween(coordinates[c + 1], coordinates[c], last.getCoordinates()[0]) < 2 * Math.PI / 3) {
                    ls.add(oneRoad.size() == 1 ? Lines.getMultiLineString(oneRoad.get(0)) : (MultiLineString) lr.getFactory().buildGeometry(oneRoad).union());
                    oneRoad = new ArrayList<>();
                    lenCrossed = 0;
                }
            }
            oneRoad.add(segment);
            if (lenCrossed > len) { // we start a new road
                ls.add(oneRoad.size() == 1 ? Lines.getMultiLineString(oneRoad.get(0)) : (MultiLineString) lr.getFactory().buildGeometry(oneRoad).union());
                oneRoad = new ArrayList<>();
                lenCrossed -= len;
            }
        }
        // last segment should have not been put inside collection so we do it now
        if (oneRoad.size() != 0)
            ls.add(oneRoad.size() == 1 ? Lines.getMultiLineString(oneRoad.get(0)) : (MultiLineString) lr.getFactory().buildGeometry(oneRoad).union());

        if (SAVEINTERMEDIATERESULT) { //save all generated peripheral roads
            DefaultFeatureCollection tmp = new DefaultFeatureCollection();
            try {
                tmp.addAll(Geom.geomsToCollec(ls, Schemas.getBasicMLSSchema("PeripheralRoad")));
                FOLDER_OUT_DEBUG.mkdirs();
                if (!tmp.isEmpty())
                    CollecMgmt.exportSFC(tmp, new File(FOLDER_OUT_DEBUG, "peripheralRoads"), false);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return ls;
    }

    /**
     * Class to run an automatic Straight Skeleton application on a set of parcels
     */

    public static SimpleFeatureCollection runTopologicalStraightSkeletonParcelDecomposition(SimpleFeature feat, SimpleFeatureCollection roads,
                                                                                            String roadNameAttribute, String roadImportanceAttribute, double maxDepth, double maxDistanceForNearestRoad,
                                                                                            double minimalArea, double minWidth, double maxWidth, double omega, RandomGenerator rng, double widthRoad, String name) {
        int count = 0;
        List<Polygon> globalOutputParcels = new ArrayList<>();
        List<Polygon> polygons = Polygons.getPolygons((Geometry) feat.getDefaultGeometry());
        for (Polygon polygon : polygons) {
            try {
                if (polygon.getArea() < minimalArea) // if small parcel, we ignore
                    continue;
                log("start with polygon " + count);
                StraightSkeletonDivision decomposition = new StraightSkeletonDivision(polygon, roads,
                        roadNameAttribute, roadImportanceAttribute, maxDepth, generatePeripheralRoad ? maxDistanceForNearestRoad + widthRoad : maxDistanceForNearestRoad, generatePeripheralRoad, widthRoad, name);
                export(decomposition.straightSkeleton.getGraph(), new File(FOLDER_OUT_DEBUG, "after_fix"));
                if (decomposition.betaStrips != null)
                    globalOutputParcels.addAll(decomposition.createParcels(minWidth, maxWidth, omega, rng));
                else
                    globalOutputParcels.add(decomposition.initialPolygon);
                log("end with polygon " + count++);
            } catch (Exception e) {
                log("error with polygon " + count);
                count += 1;
                e.printStackTrace();
            }
        }
        TopologicalGraph output = new TopologicalGraph(globalOutputParcels, 0.02);
        DefaultFeatureCollection result = new DefaultFeatureCollection();
        SimpleFeatureBuilder builder = Schemas.getBasicSchema("parcelSplitSS");
        for (Face face : output.getFaces()) {
            builder.set(CollecMgmt.getDefaultGeomName(), face.getGeometry());
            result.add(builder.buildFeature(Attribute.makeUniqueId()));
        }
        return result;
    }

    private static void export(TopologicalGraph graph, File directory) {
        if (!isSAVEINTERMEDIATERESULT() && !isDEBUG())
            return;
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
            if (edge.getFace() != null)
                edge.setAttribute("FACE", edge.getFace().getAttribute("ID"));
            else
                edge.setAttribute("FACE", "");
        }
        for (int id = 0; id < graph.getEdges().size(); id++) {
            HalfEdge edge = graph.getEdges().get(id);
            edge.setAttribute("TWIN", (edge.getTwin() != null) ? edge.getTwin().getAttribute("ID") : null);
            edge.setAttribute("NEXT", (edge.getNext() != null) ? edge.getNext().getAttribute("ID") : null);
        }
        if (!directory.isDirectory())
            directory.mkdirs();
        TopologicalGraph.setSRID(2154);
        TopologicalGraph.export(graph.getFaces(), new File(directory, "faces.gpkg"), Polygon.class);
        TopologicalGraph.export(graph.getEdges(), new File(directory, "edges.gpkg"), LineString.class);
        TopologicalGraph.export(graph.getNodes(), new File(directory, "nodes.gpkg"), Point.class);
    }

    public static boolean isSAVEINTERMEDIATERESULT() {
        return SAVEINTERMEDIATERESULT;
    }

    public static void setSAVEINTERMEDIATERESULT(boolean SAVEINTERMEDIATERES) {
        SAVEINTERMEDIATERESULT = SAVEINTERMEDIATERES;
    }

    public static boolean isGeneratePeripheralRoad() {
        return generatePeripheralRoad;
    }

    public static void setGeneratePeripheralRoad(boolean generatePeripheralRoad) {
        StraightSkeletonDivision.generatePeripheralRoad = generatePeripheralRoad;
    }

    public static boolean isDEBUG() {
        return DEBUG;
    }

    public static void setDEBUG(boolean DEBUG) {
        StraightSkeletonDivision.DEBUG = DEBUG;
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