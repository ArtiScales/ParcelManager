package fr.ign.artiscales.graph;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.geotools.feature.DefaultFeatureCollection;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.geotools.referencing.CRS;
import org.locationtech.jts.algorithm.Orientation;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Polygon;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.referencing.FactoryException;

import fr.ign.artiscales.decomposition.graph.GraphElement;
import fr.ign.cogit.geoToolsFunctions.Attribute;
import fr.ign.cogit.geoToolsFunctions.vectors.Collec;

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
      if (this.nodes.containsKey(c))
        return this.nodes.get(c);
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

  public void addNodes(Collection<Node> nodes) {
    for (Node n : nodes) {
      this.nodes.put(n.getCoordinate(), n);
    }
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
  
  public static <G extends Geometry, E extends GraphElement<G>> void export(List<E> feats, File fileOut,
			Class<? extends Geometry> geomType) {
		System.out.println("save " + feats.size() + " to " + fileOut);
		if (feats.isEmpty())
			return;
		SimpleFeatureTypeBuilder sfTypeBuilder = new SimpleFeatureTypeBuilder();
		try {
			sfTypeBuilder.setCRS(CRS.decode("EPSG:" + feats.get(0).getGeometry().getSRID()));
		} catch (FactoryException e) {
			e.printStackTrace();
		}
		sfTypeBuilder.setName(fileOut.getName());
		sfTypeBuilder.add(Collec.getDefaultGeomName(), geomType);
		sfTypeBuilder.setDefaultGeometry(Collec.getDefaultGeomName());
		SimpleFeatureType featureType = sfTypeBuilder.buildFeatureType();
		List<String> attributes = feats.get(0).getAttributes();
		for (String attribute : attributes)
			sfTypeBuilder.add(attribute, String.class);
		SimpleFeatureBuilder builder = new SimpleFeatureBuilder(featureType);
		if (DEBUG)
			System.out.println(Calendar.getInstance().getTime() + " write geopackage");
		DefaultFeatureCollection dfc = new DefaultFeatureCollection();
		for (E element : feats) {
			builder.set(Collec.getDefaultGeomName(), element.getGeometry());
			for (int i = 0; i < attributes.size(); i++)
				builder.set(i, element.getAttribute(attributes.get(i)));
			dfc.add(builder.buildFeature(Attribute.makeUniqueId()));
		}
		try {
			Collec.exportSFC(dfc, fileOut);
		} catch (IOException e) {
			e.printStackTrace();
		}
		if (DEBUG)
			System.out.println(Calendar.getInstance().getTime() + " done");
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

  public List<HalfEdge> getPath(Node start, Node end) {
    List<HalfEdge> result = new ArrayList<>();
    HalfEdge currentEdge = TopologicalGraph.outgoingEdgesOf(start, getEdges()).get(0);
    result.add(currentEdge);
    Node currentNode = currentEdge.getTarget();
    while (currentNode != end) {
      currentEdge = TopologicalGraph.outgoingEdgesOf(currentNode, getEdges()).get(0);
      result.add(currentEdge);
      currentNode = currentEdge.getTarget();
    }
    return result;
  }

  public Pair<List<HalfEdge>, Double> getShortestPath(HalfEdge start, HalfEdge end) {
    List<HalfEdge> pathForward = getPath(start.getTarget(), end.getOrigin());
    List<HalfEdge> pathBackward = getPath(end.getTarget(), start.getOrigin());
    double lengthForward = pathForward.stream().flatMapToDouble(e -> e.getChildren().stream().mapToDouble(c->c.getGeometry().getLength())).sum();
    double lengthBackward = pathBackward.stream().flatMapToDouble(e -> e.getChildren().stream().mapToDouble(c->c.getGeometry().getLength())).sum();
    return (lengthForward < lengthBackward) ? new ImmutablePair<>(pathForward, lengthForward) : new ImmutablePair<>(pathBackward, -lengthBackward);
  }
}
