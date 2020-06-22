package fr.ign.artiscales.graph;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.LineString;

public class HalfEdge extends GraphElement<LineString,HalfEdge> {
  Node origin = null;
  Node target = null;
  Face face = null;
  HalfEdge twin = null;
  HalfEdge next = null;
  public HalfEdge getTwin() {
    return twin;
  }
  public void setTwin(HalfEdge twin) {
    this.twin = twin;
    if (twin.getTwin() != this) twin.setTwin(this);
  }
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
  public Face getFace() {
    return face;
  }
  public void setFace(Face face) {
    this.face = face;
    if (!face.getEdges().contains(this)) face.getEdges().add(this);
  }
  public HalfEdge(Node o, Node t) {
    this(o,t,o.getGeometry().getFactory().createLineString(new Coordinate[] {o.getCoordinate(), t.getCoordinate()}));
  }
  public HalfEdge(Node o, Node t, LineString l) {
    this.origin = o;
    this.target = t;
    this.line = l;
  }
  public HalfEdge getNext() {
    return next;
  }
  public void setNext(HalfEdge next) {
    this.next = next;
  }
}