package fr.ign.artiscales.decomposition;

import java.io.File;
import java.io.IOException;

import org.geotools.data.shapefile.ShapefileDataStore;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.geometry.jts.WKTReader2;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.io.ParseException;

import fr.ign.artiscales.decomposition.graph.Edge;
import fr.ign.artiscales.decomposition.graph.Face;
import fr.ign.artiscales.decomposition.graph.Node;
import fr.ign.artiscales.decomposition.graph.TopologicalGraph;

public class Test {

  public static void main(String[] args) throws ParseException, IOException {
    WKTReader2 reader = new WKTReader2();
    // String string = "Polygon ((931791.39347010478377342 6702751.51166855916380882, 931667.39000000001396984 6702822.55999999959021807, 931573.58005860995035619
    // 6702875.74996676854789257, 931573.58007097674999386 6702875.75020194333046675, 931628.17000000004190952 6703031.07000000029802322, 931630.99042630556505173
    // 6703040.42891911510378122, 931861.22085814399179071 6702965.19387879781424999, 931791.39347010478377342 6702751.51166855916380882))";
    String string = "Polygon ((917916 6458449.79999999981373549 216, 917918.19999999995343387 6458435.29999999981373549 216.59999999999999432, 917896 6458434.29999999981373549 221.30000000000001137, 917900.80000000004656613 6458391.5 221.5, 917863.30000000004656613 6458385.90000000037252903 232.19999999999998863, 917864 6458379.70000000018626451 231.69999999999998863, 917851 6458378.70000000018626451 231.69999999999998863, 917799.40000000002328306 6458372.29999999981373549 226.5, 917800.69999999995343387 6458354 216.5, 917783.69999999995343387 6458346.79999999981373549 220.19999999999998863, 917780.30000000004656613 6458337.29999999981373549 219.5, 917777.80000000004656613 6458339.5 219.5, 917771 6458336.40000000037252903 220, 917759.19999999995343387 6458353.90000000037252903 219.80000000000001137, 917736 6458362.59999999962747097 219.69999999999998863, 917733.40000000002328306 6458360.79999999981373549 217.5, 917738.09999999997671694 6458355.90000000037252903 217.5, 917733.69999999995343387 6458352.79999999981373549 217.5, 917728.80000000004656613 6458357.79999999981373549 217.40000000000000568, 917732.19999999995343387 6458362 218.80000000000001137, 917731.40000000002328306 6458362.79999999981373549 218.90000000000000568, 917729.59999999997671694 6458373.90000000037252903 218.90000000000000568, 917752.5 6458387.29999999981373549 220.5, 917757.90000000002328306 6458380.90000000037252903 221.09999999999999432, 917766.80000000004656613 6458380.40000000037252903 220.5, 917754.5 6458393.70000000018626451 219.09999999999999432, 917761 6458403.29999999981373549 219.09999999999999432, 917766.09999999997671694 6458398.20000000018626451 219.09999999999999432, 917770.69999999995343387 6458406.29999999981373549 219, 917776.30000000004656613 6458400.40000000037252903 219.09999999999999432, 917781.59999999997671694 6458408.79999999981373549 219.09999999999999432, 917796.90000000002328306 6458390.5 220.5, 917789.09999999997671694 6458448.09999999962747097 225.69999999999998863, 917787.80000000004656613 6458455.79999999981373549 225.59999999999999432, 917748.30000000004656613 6458451 224, 917748.5 6458447.09999999962747097 224, 917743.5 6458445.79999999981373549 224, 917742.59999999997671694 6458457.59999999962747097 224, 917724.09999999997671694 6458456.09999999962747097 224, 917725.90000000002328306 6458445.59999999962747097 223.59999999999999432, 917712.19999999995343387 6458443.09999999962747097 224.59999999999999432, 917709.59999999997671694 6458466.20000000018626451 225, 917706.30000000004656613 6458491 223.09999999999999432, 917699.90000000002328306 6458490.40000000037252903 217.09999999999999432, 917698.5 6458499.40000000037252903 217.09999999999999432, 917704.90000000002328306 6458501 222.30000000000001137, 917702.90000000002328306 6458515.90000000037252903 221.09999999999999432, 917716.19999999995343387 6458518.40000000037252903 221.09999999999999432, 917717.59999999997671694 6458511.09999999962747097 221.09999999999999432, 917728.19999999995343387 6458512.20000000018626451 223.40000000000000568, 917741.90000000002328306 6458536.20000000018626451 223.5, 917760.80000000004656613 6458523.40000000037252903 228.69999999999998863, 917764 6458502 223.59999999999999432, 917764.30000000004656613 6458488 228.5, 917757.59999999997671694 6458473.79999999981373549 223.5, 917744.80000000004656613 6458481.59999999962747097 223.5, 917747 6458465.59999999962747097 224.09999999999999432, 917791.30000000004656613 6458472.79999999981373549 228, 917791.90000000002328306 6458463.79999999981373549 228, 917849.69999999995343387 6458470.20000000018626451 228, 917843.90000000002328306 6458517.70000000018626451 227.19999999999998863, 917878.09999999997671694 6458522.40000000037252903 227.19999999999998863, 917878.09999999997671694 6458507.40000000037252903 227.19999999999998863, 917861.5 6458505.40000000037252903 227.90000000000000568, 917865.30000000004656613 6458466.90000000037252903 227.90000000000000568, 917859.69999999995343387 6458465 227.90000000000000568, 917861.30000000004656613 6458456.79999999981373549 227.90000000000000568, 917853.09999999997671694 6458455.5 227.90000000000000568, 917854.69999999995343387 6458444.29999999981373549 222.40000000000000568, 917894.09999999997671694 6458448.79999999981373549 221.90000000000000568, 917916 6458449.79999999981373549 216),(917793.80000000004656613 6458448.79999999981373549 226, 917802 6458387.29999999981373549 227.40000000000000568, 917848.80000000004656613 6458392.29999999981373549 230.80000000000001137, 917846.69999999995343387 6458409.20000000018626451 230.80000000000001137, 917846.19999999995343387 6458427 228.69999999999998863, 917830.09999999997671694 6458425.29999999981373549 221.30000000000001137, 917829.59999999997671694 6458432.29999999981373549 221.30000000000001137, 917824.30000000004656613 6458430.70000000018626451 221.30000000000001137, 917824.40000000002328306 6458437.79999999981373549 221.90000000000000568, 917831.80000000004656613 6458438.70000000018626451 221.90000000000000568, 917831.30000000004656613 6458441.40000000037252903 221.90000000000000568, 917851.69999999995343387 6458443.90000000037252903 221.30000000000001137, 917849.5 6458455.29999999981373549 227.90000000000000568, 917793.80000000004656613 6458448.79999999981373549 226),(917859.5 6458430 229.59999999999999432, 917861.19999999995343387 6458411.09999999962747097 232.59999999999999432, 917863.09999999997671694 6458400.79999999981373549 232.59999999999999432, 917885.19999999995343387 6458403.29999999981373549 222.09999999999999432, 917881.30000000004656613 6458433.5 221.80000000000001137, 917859.5 6458430 229.59999999999999432),(917734.5 6458493.40000000037252903 223, 917720 6458491.59999999962747097 221.5, 917722.09999999997671694 6458468 224.69999999999998863, 917723.5 6458460.5 224.80000000000001137, 917741.59999999997671694 6458462.5 224.80000000000001137, 917741.59999999997671694 6458464.79999999981373549 224.90000000000000568, 917738.40000000002328306 6458485.40000000037252903 223, 917732.90000000002328306 6458488.29999999981373549 223, 917734.5 6458493.40000000037252903 223),(917783.09999999997671694 6458369.5 229, 917782.80000000004656613 6458363.20000000018626451 228.09999999999999432, 917791.59999999997671694 6458365.20000000018626451 217.30000000000001137, 917791.09999999997671694 6458360.20000000018626451 217.30000000000001137, 917793.30000000004656613 6458354.90000000037252903 217.30000000000001137, 917795.5 6458355.70000000018626451 217.19999999999998863, 917793.19999999995343387 6458371.09999999962747097 226.5, 917783.09999999997671694 6458369.5 229))";
    Polygon polygon = (Polygon) reader.read(string);
    // MinimumDiameter minimumDiameter = new MinimumDiameter(polygon);
    // Polygon oBB = (Polygon) minimumDiameter.getMinimumRectangle();
    // System.out.println(oBB);
    // System.out.println(MinimalBoundingRectangle.getRectangle(polygon));
    CampSkeleton cs = new CampSkeleton(polygon);
    TopologicalGraph graph = cs.getGraph();
    System.out.println("EDGES");
    for (Edge e : graph.getEdges()) {
      System.out.println(e.getGeometry());
    }
    System.out.println("FACES");
    for (Face e : graph.getFaces()) {
      if (!e.getGeometry().isEmpty())
        System.out.println(e.getGeometry());
    }
    System.out.println("NODES");
    for (Node e : graph.getNodes()) {
      System.out.println(e.getGeometry());
    }
    //
    System.out.println(CampSkeleton.shrink(polygon, 2));
    
    String inputRoadShapeFile = "/home/julien/data/PLU_PARIS/voie/voie_l93.shp";
    ShapefileDataStore roadDS = new ShapefileDataStore(new File(inputRoadShapeFile).toURI().toURL());
    SimpleFeatureCollection roads = roadDS.getFeatureSource().getFeatures();
    LineString line = (LineString) reader.read("LineString (653608.67376999428961426 6859509.79754020832479, 653622.56625000014901161 6859524.18500000052154064)");
    SimpleFeatureCollection selection = Util.select(roads, line);
    System.out.println("selection = " + selection.size());
    roadDS.dispose();
    Coordinate o = new Coordinate(652810.3017603179, 6857966.40922954);
    Coordinate d = new Coordinate(0.6571914520563683,-0.7537236863360752);
    LineString lineString = (LineString) reader.read("LINESTRING (652792.1862488963 6857976.0127653275, 652803.37 6857975.05)");
    Coordinate inter = Util.getRayLineSegmentIntersection(o, d, lineString);
    System.out.println("inter\n"+lineString.getFactory().createPoint(inter));
  }
}
