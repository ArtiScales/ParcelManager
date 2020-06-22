package fr.ign.artiscales.graph;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.geotools.data.DataUtilities;
import org.geotools.data.FeatureWriter;
import org.geotools.data.FileDataStore;
import org.geotools.data.Transaction;
import org.geotools.data.shapefile.ShapefileDataStoreFactory;
import org.geotools.feature.SchemaException;
import org.locationtech.jts.algorithm.Orientation;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Polygon;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;

public class TopologicalGraph {
  Map<Coordinate, Node> nodes = new HashMap<>();
  List<HalfEdge> edges = new ArrayList<>();
  List<Face> faces = new ArrayList<>();
  private static boolean DEBUG = false;

  public Collection<Node> getNodes() {
    return nodes.values();
  }

  // public void setNodes(List<Node> nodes) {
  // this.nodes = nodes;
  // }

  public List<HalfEdge> getEdges() {
    return edges;
  }

  public void setEdges(List<HalfEdge> edges) {
    this.edges = edges;
  }

  public List<Face> getFaces() {
    return faces;
  }

  public void setFaces(List<Face> faces) {
    this.faces = faces;
  }

  public TopologicalGraph() {
  }

  /**
   * Does not handle holes.
   */
  public TopologicalGraph(Collection<Polygon> polygons, double tolerance) {
    for (Polygon polygon : polygons) {
      Face f = new Face(polygon);
      this.faces.add(f);
      // make sure the coordinates are CCW
      boolean ccw = Orientation.isCCW(polygon.getExteriorRing().getCoordinateSequence());
      Coordinate[] coords = (ccw ? polygon.getExteriorRing() : polygon.getExteriorRing().reverse()).getCoordinates();
      HalfEdge first = null;
      HalfEdge previous = null;
      for (int index = 0; index < coords.length - 1; index++) {
        Coordinate c1 = coords[index];
        Coordinate c2 = coords[index + 1];
        Node n1 = getOrCreateNode(c1, tolerance);
        Node n2 = getOrCreateNode(c2, tolerance);
        HalfEdge e = new HalfEdge(n1, n2, polygon.getFactory().createLineString(new Coordinate[] { c1, c2 }));
        Optional<HalfEdge> twin = getHalfEdge(n2, n1);
        if (twin.isPresent())
          e.setTwin(twin.get());
        e.setFace(f);
        this.edges.add(e);
        if (first == null)
          first = e;
        if (previous != null)
          previous.setNext(e);
        previous = e;
      }
      previous.setNext(first);
    }
  }

  private Optional<HalfEdge> getHalfEdge(Node or, Node ta) {
    return this.edges.stream().filter(e -> (e.origin == or) && (e.target == ta)).findAny();
  }

  public Node getOrCreateNode(Coordinate c) {
    return getOrCreateNode(c, 0);
  }

  public Node getOrCreateNode(Coordinate c, double tolerance) {
    if (tolerance == 0) {
      if (this.nodes.containsKey(c)) return this.nodes.get(c);
      Node node = new Node(c);
      this.nodes.put(c, node);
      return node;
    }
    Node node = getNode(c, tolerance);
    if (node == null) {
      node = new Node(c);
      this.nodes.put(c, node);
    }
    return node;
  }

  public void addNode(Node n) {
    this.nodes.put(n.getCoordinate(), n);
  }

  public List<HalfEdge> edgesOf(Node node) {
    return edgesOf(node, this.edges);
  }

  public List<HalfEdge> edgesOf(Node node, List<HalfEdge> edges) {
    return edges.stream().filter(e -> (e.getOrigin() == node || e.getTarget() == node)).collect(Collectors.toList());
  }

  public static List<HalfEdge> incomingEdgesOf(Node node, List<HalfEdge> edges) {
    return edges.stream().filter(e -> e.getTarget() == node).collect(Collectors.toList());
  }

  public static List<HalfEdge> outgoingEdgesOf(Node node, List<HalfEdge> edges) {
    return edges.stream().filter(e -> e.getOrigin() == node).collect(Collectors.toList());
  }

  public static HalfEdge next(Node node, HalfEdge edge, List<HalfEdge> edges) {
    return outgoingEdgesOf(node, edges).stream().filter(e -> e != edge).findAny().orElse(null);
  }

  public static HalfEdge previous(Node node, HalfEdge edge, List<HalfEdge> edges) {
    return incomingEdgesOf(node, edges).stream().filter(e -> e != edge).findAny().orElse(null);
  }

  public Node getCommonNode(HalfEdge e1, HalfEdge e2) {
    if (e1.getOrigin() == e2.getOrigin() || e1.getOrigin() == e2.getTarget())
      return e1.getOrigin();
    return e1.getTarget();
  }

  private static void log(Object o) {
    if (DEBUG) {
      System.out.println(o);
    }
  }

  public static <G extends Geometry, E extends GraphElement<G, E>> void export(Collection<E> feats, File file, String geomType) {
    log(Calendar.getInstance().getTime() + " save " + feats.size() + " features to " + file);
    file.getParentFile().mkdirs();
    if (feats.isEmpty())
      return;
    try {
      String specs = "geom:" + geomType + ":srid=2154";// FIXME should not force lambert93
      List<String> attributes = feats.iterator().next().getAttributes();
      for (String attribute : attributes) {
        specs += "," + attribute + ":String";
      }
      log(specs);
      ShapefileDataStoreFactory factory = new ShapefileDataStoreFactory();
      FileDataStore dataStore = factory.createDataStore(file.toURI().toURL());
      String featureTypeName = "Object";
      SimpleFeatureType featureType = DataUtilities.createType(featureTypeName, specs);
      dataStore.createSchema(featureType);
      String typeName = dataStore.getTypeNames()[0];
      FeatureWriter<SimpleFeatureType, SimpleFeature> writer = dataStore.getFeatureWriterAppend(typeName, Transaction.AUTO_COMMIT);
      System.setProperty("org.geotools.referencing.forceXY", "true");
      log(Calendar.getInstance().getTime() + " write shapefile");
      for (E element : feats) {
        SimpleFeature feature = writer.next();
        Object[] att = new Object[attributes.size() + 1];
        att[0] = element.getGeometry();
        for (int i = 0; i < attributes.size(); i++) {
          att[i + 1] = element.getAttribute(attributes.get(i));
        }
        // log("WRITING " + element.getGeometry());
        feature.setAttributes(att);
        writer.write();
      }
      log(Calendar.getInstance().getTime() + " done");
      writer.close();
      dataStore.dispose();
    } catch (MalformedURLException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    } catch (SchemaException e) {
      e.printStackTrace();
    }
  }

  public Node getNode(Coordinate c) {
    return this.nodes.get(c);
  }

  public Node getNode(Coordinate c, double tolerance) {
    List<Coordinate> candidates = this.nodes.keySet().stream().filter(coord -> coord.distance(c) <= tolerance).collect(Collectors.toList());
    if (candidates.isEmpty())
      return null;
    candidates.sort((c1, c2) -> Double.compare(c1.distance(c), c2.distance(c)));
    return this.nodes.get(candidates.get(0));
  }
}
