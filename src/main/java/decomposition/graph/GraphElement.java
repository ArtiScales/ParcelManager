package decomposition.graph;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.locationtech.jts.geom.Geometry;

public abstract class GraphElement<G extends Geometry> {
  Map<String, Object> attributes = new HashMap<>();
  public Object getAttribute(String name) {
    return this.attributes.get(name);
  }
  public void setAttribute(String name, Object value) {
    this.attributes.put(name, value);
  }
  public List<String> getAttributes() {
    return new ArrayList<>(attributes.keySet());
  }
  public abstract G getGeometry();
}
