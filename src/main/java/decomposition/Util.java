package decomposition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.factory.CommonFactoryFinder;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryCollection;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.MultiLineString;
import org.locationtech.jts.geom.Polygon;
import org.opengis.filter.FilterFactory2;

public class Util {

  public static SimpleFeatureCollection select(SimpleFeatureCollection collection, Geometry geom) {
    FilterFactory2 ff = CommonFactoryFinder.getFilterFactory2();
    return collection.subCollection(ff.intersects(ff.property(collection.getSchema().getGeometryDescriptor().getLocalName()), ff.literal(geom)));
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

}
