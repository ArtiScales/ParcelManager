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
      // compute the rectangular hull of the rotated convew hull
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
