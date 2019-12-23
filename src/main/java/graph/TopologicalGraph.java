package graph;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class TopologicalGraph {
  List<Node> nodes = new ArrayList<>();
  List<HalfEdge> edges = new ArrayList<>();
  List<Face> faces = new ArrayList<>();

  public List<Node> getNodes() {
    return nodes;
  }

  public void setNodes(List<Node> nodes) {
    this.nodes = nodes;
  }

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

  public List<HalfEdge> edgesOf(Node node) {
    return edgesOf(node, this.edges);
  }

  public List<HalfEdge> edgesOf(Node node, List<HalfEdge> edges) {
    return edges.stream().filter(e -> (e.getOrigin() == node || e.getTarget() == node)).collect(Collectors.toList());
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
}
