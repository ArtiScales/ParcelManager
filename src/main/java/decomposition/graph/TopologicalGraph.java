package decomposition.graph;

import java.util.ArrayList;
import java.util.List;

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
}
