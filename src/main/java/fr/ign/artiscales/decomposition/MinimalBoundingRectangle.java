package fr.ign.artiscales.decomposition;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.util.AffineTransformation;

/**
 * @author Julien Gaffuri
 * 
 */
public class MinimalBoundingRectangle {
	
//	public static void main(String[] args) throws Exception {
//		WKTReader r = new WKTReader();
//		System.out.println(getRectangle(r.read(
//				"Polygon ((680560.02000000001862645 6786954.03000000026077032, 680543.42000000004190952 6786931.12000000011175871, 680540.26000000000931323 6786926.70000000018626451, 680534.46999999997206032 6786918.61000000033527613, 680532.14000000001396984 6786915.70000000018626451, 680518.06999999994877726 6786929.5400000000372529, 680514.47999999998137355 6786933.29999999981373549, 680491.66000000003259629 6786961.13999999966472387, 680535.81999999994877726 6787004.41000000014901161, 680571.18999999994412065 6786969.41999999992549419, 680560.02000000001862645 6786954.03000000026077032))")));
//	}
	
  /**
   * @param geom
   * @return The smallest surrounding rectangle of a geometry
   */
  public static Polygon getRectangle(Geometry geom) {
    Geometry hull_ = geom.convexHull();
    // Convex hull is not a polygon; the SSR is not defined: return null
    if (!(hull_ instanceof Polygon)) {
      return null;
    }
    Polygon convHull = (Polygon) hull_;
    // center coordinates (for rotation)
    Coordinate rotationCenter = geom.getCentroid().getCoordinate();
    // get convex hull coordinates
    Coordinate[] coord = convHull.getExteriorRing().getCoordinates();
    // go through the segments
    double minArea = Double.MAX_VALUE, minAngle = 0.0;
    Polygon ssr = null;
    for (int i = 0; i < coord.length - 1; i++) {
      // compute the rectangular hull of the rotated convex hull
      // compute the angle value
      double angle = Math.atan2(coord[i + 1].y - coord[i].y, coord[i + 1].x - coord[i].x);
      AffineTransformation trans = AffineTransformation.rotationInstance(-1.0 * angle, rotationCenter.x, rotationCenter.y);
      Polygon rect = (Polygon) trans.transform(convHull).getEnvelope();
      // compute the rectangle area
      double area = rect.getArea();
      // check if it is minimum
      if (area < minArea) {
        minArea = area;
        ssr = rect;
        minAngle = angle;
      }
    }
    AffineTransformation trans = AffineTransformation.rotationInstance(minAngle, rotationCenter.x, rotationCenter.y);
    return (Polygon) trans.transform(ssr);
  }

  /**
   * @param geom
   * @return The smallest surrounding rectangle scaled to preserve its area
   */
  public static Polygon getRectanglePreservedArea(Geometry geom) {
    return MinimalBoundingRectangle.getRectangleGoalArea(geom, geom.getArea());
  }

  /**
   * @param geom
   * @param goalArea
   * @return The smallest surrounding rectangle of a geometry scaled to a given goal area
   */
  public static Polygon getRectangleGoalArea(Geometry geom, double goalArea) {
    Polygon ssr = MinimalBoundingRectangle.getRectangle(geom);
    double scale = Math.sqrt(goalArea / ssr.getArea());
    Point centroid = ssr.getCentroid();
    AffineTransformation transform = AffineTransformation.scaleInstance(scale, scale, centroid.getX(), centroid.getY());
    return (Polygon) transform.transform(ssr);
  }
}
