package fr.ign.artiscales.decomposition.graph;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class TopologicalGraph {
  List<Node> nodes = new ArrayList<>();
  List<Edge> edges = new ArrayList<>();
  List<Face> faces = new ArrayList<>();

  public List<Node> getNodes() {
    return nodes;
  }

  public void setNodes(List<Node> nodes) {
    this.nodes = nodes;
  }

  public List<Edge> getEdges() {
    return edges;
  }

  public void setEdges(List<Edge> edges) {
    this.edges = edges;
  }

  public List<Face> getFaces() {
    return faces;
  }

  public void setFaces(List<Face> faces) {
    this.faces = faces;
  }

  public List<Edge> edgesOf(Node node) {
    return edgesOf(node, this.edges);
  }

  public List<Edge> edgesOf(Node node, List<Edge> edges) {
    return edges.stream().filter(e -> (e.getOrigin() == node || e.getTarget() == node)).collect(Collectors.toList());
  }

  public Edge next(Node node, Edge edge, List<Edge> edges) {
    return edgesOf(node, edges).stream().filter(e -> e != edge).findAny().orElse(null);
  }

  public Node getCommonNode(Edge e1, Edge e2) {
    if (e1.getOrigin() == e2.getOrigin() || e1.getOrigin() == e2.getTarget())
      return e1.getOrigin();
    return e1.getTarget();
  }
}
