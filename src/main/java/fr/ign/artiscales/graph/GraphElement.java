package fr.ign.artiscales.graph;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.locationtech.jts.geom.Geometry;

public abstract class GraphElement<G extends Geometry, T extends GraphElement<G,T>> {
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
  T parent = null;
  public T getParent() {
    return parent;
  }
  public void setParent(T parent) {
    this.parent = parent;
  }
  List<T> children = new ArrayList<>();
  public List<T> getChildren() {
    return children;
  }
  public void setChildren(List<T> children) {
    this.children = children;
  }
  public abstract G getGeometry();
}
