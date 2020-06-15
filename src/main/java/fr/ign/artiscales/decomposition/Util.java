package fr.ign.artiscales.decomposition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.factory.CommonFactoryFinder;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryCollection;
import org.locationtech.jts.geom.LineSegment;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.MultiLineString;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.linearref.LengthIndexedLine;
import org.locationtech.jts.math.Vector2D;
import org.locationtech.jts.operation.linemerge.LineMerger;
import org.locationtech.jts.operation.union.CascadedPolygonUnion;
import org.locationtech.jts.precision.GeometryPrecisionReducer;
import org.opengis.filter.FilterFactory2;

import fr.ign.cogit.FeaturePolygonizer;
import si.uom.SI;

public class Util {

  public static SimpleFeatureCollection select(SimpleFeatureCollection collection, Geometry geom) {
    FilterFactory2 ff = CommonFactoryFinder.getFilterFactory2();
    return collection.subCollection(ff.intersects(ff.property(collection.getSchema().getGeometryDescriptor().getLocalName()), ff.literal(geom)));
  }

  public static SimpleFeatureCollection select(SimpleFeatureCollection collection, Geometry geom, double distance) {
    FilterFactory2 ff = CommonFactoryFinder.getFilterFactory2();
    return collection.subCollection(ff.dwithin(ff.property(collection.getSchema().getGeometryDescriptor().getLocalName()), ff.literal(geom), distance, SI.METRE.toString()));
  }

  public static List<Polygon> getPolygons(Geometry geom) {
    if (geom instanceof Polygon) {
      return Collections.singletonList((Polygon) geom);
    }
    if (geom instanceof GeometryCollection) {
      List<Polygon> result = new ArrayList<>();
      for (int i = 0; i < geom.getNumGeometries(); i++) {
        Geometry g = geom.getGeometryN(i);
        result.addAll(getPolygons(g));
      }
      return result;
    }
    return Collections.emptyList();
  }

  public static List<LineString> getLineStrings(Geometry geom) {
    if (geom instanceof LineString) {
      return Collections.singletonList((LineString) geom);
    }
    if (geom instanceof GeometryCollection) {
      List<LineString> result = new ArrayList<>();
      for (int i = 0; i < geom.getNumGeometries(); i++) {
        Geometry g = geom.getGeometryN(i);
        result.addAll(getLineStrings(g));
      }
      return result;
    }
    return Collections.emptyList();
  }

  public static MultiLineString getMultiLineString(Geometry geom) {
    List<LineString> list = getLineStrings(geom);
    return geom.getFactory().createMultiLineString(list.toArray(new LineString[list.size()]));
  }

  public static LineString union(List<LineString> list) {
    if (list.isEmpty())
      return null;
    LineMerger merger = new LineMerger();
    list.forEach(l -> merger.add(l));
    return (LineString) merger.getMergedLineStrings().iterator().next();// FIXME we assume a lot here
  }

  public static Polygon polygonUnion(List<Polygon> list, GeometryPrecisionReducer reducer) {
    if (list.isEmpty())
      return null;
    List<Geometry> reducedList = list.stream().filter(g->g!=null).map(g -> reducer.reduce(g)).collect(Collectors.toList());
    return (Polygon) new CascadedPolygonUnion(reducedList).union();
  }

  public static Polygon polygonUnionWithoutHoles(List<Polygon> list, GeometryPrecisionReducer reducer) {
    Polygon union = polygonUnion(list, reducer);
    return union.getFactory().createPolygon(union.getExteriorRing().getCoordinates());
  }

  public static Polygon polygonDifference(List<Polygon> a, List<Polygon> b) {
    Geometry difference = FeaturePolygonizer.getDifference(a, b);
    List<Polygon> p = Util.getPolygons(difference);
    if (p.size() != 1) {
      System.out.println(p.size() + " polygons");
      p.forEach(pp -> System.out.println(pp));
      return null;
    }
    return p.get(0);
  }

  public static Coordinate project(Coordinate p, LineString l) {
    List<Pair<Coordinate, Double>> list = new ArrayList<>();
    for (int i = 0; i < l.getNumPoints() - 1; i++) {
      LineSegment segment = new LineSegment(l.getCoordinateN(i), l.getCoordinateN(i + 1));
      Coordinate proj = segment.closestPoint(p);
      list.add(new ImmutablePair<>(proj, proj.distance(p)));
    }
    return list.stream().min((a, b) -> a.getRight().compareTo(b.getRight())).get().getLeft();
  }

  public static Pair<LineString, LineString> splitLine(LineString line, double s) {
    LengthIndexedLine lil = new LengthIndexedLine(line);
    return new ImmutablePair<LineString, LineString>((LineString) lil.extractLine(0, s), (LineString) lil.extractLine(s, line.getLength()));
  }

  public static Pair<LineString, LineString> splitLine(LineString line, Coordinate c) {
    LengthIndexedLine lil = new LengthIndexedLine(line);
    return splitLine(line, lil.indexOf(c));
  }

  static boolean getRayLineSegmentIntersects(Coordinate o, Coordinate d, Coordinate a, Coordinate b) {
    Vector2D ortho = Vector2D.create(-d.y, d.x);
    Vector2D aToO = Vector2D.create(a, o);
    Vector2D aToB = Vector2D.create(a, b);
    double denom = aToB.dot(ortho);
    if (denom < 0) {
      ortho = Vector2D.create(d.y, -d.x);
      denom = aToB.dot(ortho);
    }
//    System.out.println("denom = " + denom);
    // Here would be a good time to see if denom is zero in which case the line segment and the ray are parallel.
    if (denom == 0) return false; // TODO : add tolerance?
    // The length of this cross product can also be written as abs( aToB.x * aToO.y - aToO.x * aToB.y ).
    double length = Math.abs(aToB.getX() * aToO.getY() - aToO.getX() * aToB.getY());
    double t1 = length / denom;
    double t2 = aToO.dot(ortho) / denom;
//    System.out.println("t1 = " + t1 + " t2 = " + t2);
    return t2 >= 0 && t2 <= 1 && t1 >= 0;
  }
  static Coordinate getRayLineSegmentIntersection(Coordinate o, Coordinate d, Coordinate a, Coordinate b) {
    Vector2D ortho = Vector2D.create(-d.y, d.x);
    Vector2D aToO = Vector2D.create(a, o);
    Vector2D aToB = Vector2D.create(a, b);
    double denom = aToB.dot(ortho);
    if (denom < 0) {
      ortho = Vector2D.create(d.y, -d.x);
      denom = aToB.dot(ortho);
    }
    // Here would be a good time to see if denom is zero in which case the line segment and the ray are parallel.
    if (denom == 0) return null; // TODO : add tolerance?
    // The length of this cross product can also be written as abs( aToB.x * aToO.y - aToO.x * aToB.y ).
    double length = Math.abs(aToB.getX() * aToO.getY() - aToO.getX() * aToB.getY());
    double t1 = length / denom;
    double t2 = aToO.dot(ortho) / denom;
    if (t2 >= 0 && t2 <= 1 && t1 >= 0)
      return new Coordinate(a.getX() + t2 * aToB.getX(), a.getY() + t2 * aToB.getY());
    return null;
  }

  static boolean getRayLineSegmentIntersects(Coordinate o, Coordinate d, LineString line) {
    Coordinate a = line.getCoordinateN(0);
    Coordinate b = line.getCoordinateN(line.getNumPoints() - 1);
    return getRayLineSegmentIntersects(o, d, a, b);
  }
  static Coordinate getRayLineSegmentIntersection(Coordinate o, Coordinate d, LineString line) {
    Coordinate a = line.getCoordinateN(0);
    Coordinate b = line.getCoordinateN(line.getNumPoints() - 1);
    return getRayLineSegmentIntersection(o, d, a, b);
  }
}
