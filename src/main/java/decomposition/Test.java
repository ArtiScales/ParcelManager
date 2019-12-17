package decomposition;

import org.geotools.geometry.jts.WKTReader2;
import org.locationtech.jts.algorithm.MinimumDiameter;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.io.ParseException;

public class Test {

  public static void main(String[] args) throws ParseException {
    WKTReader2 reader = new WKTReader2();
    String string = "Polygon ((931791.39347010478377342 6702751.51166855916380882, 931667.39000000001396984 6702822.55999999959021807, 931573.58005860995035619 6702875.74996676854789257, 931573.58007097674999386 6702875.75020194333046675, 931628.17000000004190952 6703031.07000000029802322, 931630.99042630556505173 6703040.42891911510378122, 931861.22085814399179071 6702965.19387879781424999, 931791.39347010478377342 6702751.51166855916380882))";
    Polygon polygon = (Polygon) reader.read(string);
    MinimumDiameter minimumDiameter = new MinimumDiameter(polygon);
    Polygon oBB = (Polygon) minimumDiameter.getMinimumRectangle();
    System.out.println(oBB);
    System.out.println(MinimalBoundingRectangle.getRectangle(polygon));
  }

}
