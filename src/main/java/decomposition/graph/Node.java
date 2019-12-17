package decomposition.graph;

import org.locationtech.jts.geom.Coordinate;

public class Node {
  private Coordinate coordinate;
  public Coordinate getCoordinate() {
    return coordinate;
  }
  public Node(Coordinate c) {
    this.coordinate = c;
  }
}
