package decomposition.analysis;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.locationtech.jts.geom.Geometry;
import org.opengis.feature.simple.SimpleFeature;

import decomposition.Util;

public class Closest {
  public static Optional<SimpleFeature> find(Geometry geom, SimpleFeatureCollection collectionToSelect, double maximumDistance) {
    if (collectionToSelect.isEmpty()) {
      return Optional.empty();
    }
    SimpleFeatureIterator iterator = Util.select(collectionToSelect, geom, maximumDistance).features();
    List<SimpleFeature> list = new ArrayList<>();
    while (iterator.hasNext()) {
      SimpleFeature candidate = iterator.next();
      list.add(candidate);
    }
    iterator.close();
    return list.stream().map(sf->new ImmutablePair<>(sf,((Geometry)sf.getDefaultGeometry()).distance(geom))).min((a,b)->a.right.compareTo(b.right)).map(x->x.left);
  }
}
