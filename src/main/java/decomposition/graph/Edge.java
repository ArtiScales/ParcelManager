package decomposition.graph;

import org.locationtech.jts.geom.LineString;

public class Edge extends GraphElement<LineString> {
  Node origin = null;
  Node target = null;
  Face right = null;
  Face left = null;
  LineString line = null;
  public LineString getGeometry() {
    return line;
  }
  public void setLine(LineString line) {
    this.line = line;
  }
  public Node getOrigin() {
    return origin;
  }
  public void setOrigin(Node origin) {
    this.origin = origin;
  }
  public Node getTarget() {
    return target;
  }
  public void setTarget(Node target) {
    this.target = target;
  }
  public Face getRight() {
    return right;
  }
  public void setRight(Face right) {
    this.right = right;
    if (!right.getEdges().contains(this)) right.getEdges().add(this);
  }
  public Face getLeft() {
    return left;
  }
  public void setLeft(Face left) {
    this.left = left;
    if (!left.getEdges().contains(this)) left.getEdges().add(this);
  }
  public Edge(Node o, Node t, LineString l) {
    this.origin = o;
    this.target = t;
    this.line = l;
  }
}
