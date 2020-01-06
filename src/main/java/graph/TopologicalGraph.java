package graph;

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
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Polygon;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;

public class TopologicalGraph {
  Map<Coordinate,Node> nodes = new HashMap<>();
  List<HalfEdge> edges = new ArrayList<>();
  List<Face> faces = new ArrayList<>();

  public Collection<Node> getNodes() {
    return nodes.values();
  }

//  public void setNodes(List<Node> nodes) {
//    this.nodes = nodes;
//  }

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
  public TopologicalGraph(Collection<Polygon> polygons) {
    for (Polygon polygon: polygons) {
      Face f = new Face(polygon);
      this.faces.add(f);
      // we reverse so that the coordinates are CCW
      Coordinate[] coords = polygon.getExteriorRing().reverse().getCoordinates();
      for (int index = 0; index < coords.length - 1; index++) {
        Coordinate c1 = coords[index];
        Coordinate c2 = coords[index + 1];
        Node n1 = getOrCreateNode(c1);
        Node n2 = getOrCreateNode(c2);
        HalfEdge e = new HalfEdge(n1,n2,polygon.getFactory().createLineString(new Coordinate[] {c1,c2}));
        Optional<HalfEdge> twin = getHalfEdge(n2,n1);
        if (twin.isPresent()) e.setTwin(twin.get());
        e.setFace(f);
        this.edges.add(e);
      }
    }
  }
  
  private Optional<HalfEdge> getHalfEdge(Node or, Node ta) {
    return this.edges.stream().filter(e->(e.origin == or) && (e.target == ta)).findAny();
  }

  private Node getOrCreateNode(Coordinate c) {
    if (this.nodes.containsKey(c)) return this.nodes.get(c);
    Node newNode = new Node(c);
    this.nodes.put(c, newNode);
    return newNode;
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

  public List<HalfEdge> incomingEdgesOf(Node node, List<HalfEdge> edges) {
    return edges.stream().filter(e -> e.getTarget() == node).collect(Collectors.toList());
  }

  public List<HalfEdge> outgoingEdgesOf(Node node, List<HalfEdge> edges) {
    return edges.stream().filter(e -> e.getOrigin() == node).collect(Collectors.toList());
  }

  public HalfEdge next(Node node, HalfEdge edge, List<HalfEdge> edges) {
    return outgoingEdgesOf(node, edges).stream().filter(e -> e != edge).findAny().orElse(null);
  }

  public Node getCommonNode(HalfEdge e1, HalfEdge e2) {
    if (e1.getOrigin() == e2.getOrigin() || e1.getOrigin() == e2.getTarget())
      return e1.getOrigin();
    return e1.getTarget();
  }
  
  public static <G extends Geometry, E extends GraphElement<G,E>> void export(Collection<E> feats, File file, String geomType) {
    System.out.println(Calendar.getInstance().getTime() + " save " + feats.size() + " features to " + file);
    if (feats.isEmpty())
      return;
    try {
      String specs = "geom:" + geomType + ":srid=2154";//FIXME should not force lambert93
      List<String> attributes = feats.iterator().next().getAttributes();
      for (String attribute : attributes) {
        specs += "," + attribute + ":String";
      }
      ShapefileDataStoreFactory factory = new ShapefileDataStoreFactory();
      FileDataStore dataStore = factory.createDataStore(file.toURI().toURL());
      String featureTypeName = "Object";
      SimpleFeatureType featureType = DataUtilities.createType(featureTypeName, specs);
      dataStore.createSchema(featureType);
      String typeName = dataStore.getTypeNames()[0];
      FeatureWriter<SimpleFeatureType, SimpleFeature> writer = dataStore.getFeatureWriterAppend(typeName, Transaction.AUTO_COMMIT);
      System.setProperty("org.geotools.referencing.forceXY", "true");
      System.out.println(Calendar.getInstance().getTime() + " write shapefile");
      for (E element : feats) {
        SimpleFeature feature = writer.next();
        Object[] att = new Object[attributes.size() + 1];
        att[0] = element.getGeometry();
        for (int i = 0; i < attributes.size(); i++) {
          att[i + 1] = element.getAttribute(attributes.get(i));
        }
        // System.out.println("WRITING " + element.getGeometry());
        feature.setAttributes(att);
        writer.write();
      }
      System.out.println(Calendar.getInstance().getTime() + " done");
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
}
