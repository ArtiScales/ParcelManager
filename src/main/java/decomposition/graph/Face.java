package decomposition.graph;

import java.util.ArrayList;
import java.util.List;

import org.locationtech.jts.geom.Polygon;

public class Face extends GraphElement<Polygon> {
  List<Edge> edges = new ArrayList<>();
  Polygon polygon = null;
  public Face() {
  }
  public List<Edge> getEdges() {
    return edges;
  }
  public void setEdges(List<Edge> edges) {
    this.edges = edges;
  }
  public Polygon getGeometry() {
    return polygon;
  }
  public void setPolygon(Polygon polygon) {
    this.polygon = polygon;
  }
}
