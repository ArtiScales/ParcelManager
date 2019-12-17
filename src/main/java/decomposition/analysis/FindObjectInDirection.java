package decomposition.analysis;

import org.geotools.data.DataUtilities;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.feature.DefaultFeatureCollection;
import org.geotools.feature.SchemaException;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.geometry.jts.WKTReader2;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.MultiLineString;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.math.Vector2D;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;

import decomposition.Util;

/**
 * 
 * @author mickael brasebin
 *
 */
public class FindObjectInDirection {

	private final static double smallVectorSize = 0.01;

	public static void main(String[] args) throws ParseException, SchemaException {
		String polWKT = "POLYGON ((926132.266 6688555.264 0.0, 926132.26 6688555.27 0.0, 926132.27 6688555.28 0.0, 926134.07 6688557.65 0.0, 926134.08 6688557.65 0.0, 926134.09 6688557.66 0.0, 926134.1 6688557.65 0.0, 926134.11 6688557.65 0.0, 926134.16 6688557.61 0.0, 926134.18 6688557.6 0.0, 926161.83 6688535.07 0.0, 926161.91 6688535.01 0.0, 926162.0 6688534.97 0.0, 926162.1 6688534.96 0.0, 926162.2 6688534.96 0.0, 926162.3 6688534.98 0.0, 926162.39 6688535.02 0.0, 926162.47 6688535.07 0.0, 926162.54 6688535.14 0.0, 926202.64 6688585.96 0.0, 926202.66 6688585.97 0.0, 926202.68 6688585.96 0.0, 926212.03 6688578.5 0.0, 926230.4 6688564.17 0.0, 926230.42 6688564.16 0.0, 926213.98 6688541.91 0.0, 926196.959 6688518.829 0.0, 926189.46 6688508.67 0.0, 926189.45 6688508.68 0.0, 926174.611 6688520.768 0.0, 926160.411 6688532.339 0.0, 926160.39 6688532.36 0.0, 926160.378 6688532.366 0.0, 926132.27 6688555.27 0.0, 926132.266 6688555.264 0.0))";
		double maximumDistance = 50;
		Polygon polygon = (Polygon) new WKTReader2().read(polWKT);
		Coordinate[] dpl = polygon.getCoordinates();
		int nbPoints = dpl.length;
		DefaultFeatureCollection fT = new DefaultFeatureCollection();
		SimpleFeatureType PARCELTYPE = DataUtilities.createType("location","geom:Polygon");
		SimpleFeature parcelle = SimpleFeatureBuilder.build( PARCELTYPE, new Object[]{polygon}, null);
		fT.add(parcelle);
    SimpleFeatureType EDGETYPE = DataUtilities.createType("location","geom:LineString");
		for (int i = 1; i < nbPoints - 1; i++) {
			Coordinate dpPred = dpl[i];
			Coordinate dpActu = dpl[i+1];
			LineString lS = polygon.getFactory().createLineString(new Coordinate[] {dpPred,dpActu});
			SimpleFeature f = FindObjectInDirection.find(SimpleFeatureBuilder.build( EDGETYPE, new Object[]{lS}, null), parcelle, fT, maximumDistance);
			System.out.println((f != null)?f.getDefaultGeometry():"NULL");
		}
	}

	/**
	 * Find an object in the collection collectionToSelect in a direction
	 * perpendicular to bound (a LineString object) in a direction opposite to
	 * parcel
	 * 
	 * @param bound
	 * @param parcel
	 * @param collectionToSelect
	 * @param maximumDistance
	 * @return
	 */
	public static SimpleFeature find(SimpleFeature linestringFeature, SimpleFeature oppositeDirectionFeature,
	    SimpleFeatureCollection collectionToSelect, double maximumDistance) {
		return find((LineString) linestringFeature.getDefaultGeometry(), (Geometry) oppositeDirectionFeature.getDefaultGeometry(), collectionToSelect,
				maximumDistance);
	}

	public static SimpleFeature find(LineString linestring, Geometry oppositeDirection,
	    SimpleFeatureCollection collectionToSelect, double maximumDistance) {
		if (collectionToSelect.isEmpty()) {
			return null;
		}
		LineString ls = generateLineofSight(linestring, oppositeDirection, maximumDistance);
		if (ls == null) {
			return null;
		}
		double distance = Double.POSITIVE_INFINITY;
		SimpleFeature bestcandidateParcel = null;
		SimpleFeatureIterator iterator = Util.select(collectionToSelect, ls).features();
		while (iterator.hasNext()) {
		  SimpleFeature boundaryTemp = iterator.next();
			double distTemp = ((Geometry)boundaryTemp.getDefaultGeometry()).distance(oppositeDirection);
			if (oppositeDirection.buffer(0.5).contains((Geometry)boundaryTemp.getDefaultGeometry())) {
				continue;
			}
			if (distTemp < distance) {
				distance = distTemp;
				bestcandidateParcel = boundaryTemp;
			}
		}
		return bestcandidateParcel;
	}

	private static LineString generateLineofSight(LineString geom, Geometry oppositeDirection,
			double maximumDistance) {
		Coordinate dp1 = geom.getCoordinateN(0);
		Coordinate dp2 = geom.getCoordinateN(1);

		Vector2D vLine = new Vector2D(dp1, dp2);

		Vector2D vectOrth = vLine.rotateByQuarterCircle(1).normalize().multiply(smallVectorSize);
		Vector2D vectOrthNeg = vectOrth.negate().normalize().multiply(smallVectorSize);

		Coordinate lineCenter = geom.getCentroid().getCoordinate();

		Coordinate dpDep = vectOrth.translate(lineCenter);
		Coordinate dpDepNeg = vectOrthNeg.translate(lineCenter);

		GeometryFactory factory = geom.getFactory();
		boolean isInPolygonDep = oppositeDirection.contains(factory.createPoint(dpDep));
		boolean isInPolygonDepNeg = oppositeDirection.contains(factory.createPoint(dpDepNeg));

		if (isInPolygonDep && isInPolygonDepNeg) {
			MultiLineString iOC = Util.getMultiLineString(oppositeDirection);
			double distDep = iOC.distance(factory.createPoint(dpDep));
			double distDepNeg = iOC.distance(factory.createPoint(dpDepNeg));
			if (distDep < distDepNeg) {
				isInPolygonDepNeg = false;
			} else {
				isInPolygonDep = false;
			}
			System.out.println(
					FindObjectInDirection.class + " TRANSLATION IS IN PARCEL IN BOTH DIRECTION " + oppositeDirection);
		}

		if ((!isInPolygonDep) && (!isInPolygonDepNeg)) {
		  MultiLineString iOC = Util.getMultiLineString(oppositeDirection);
			double distDep = iOC.distance(factory.createPoint(dpDep));
			double distDepNeg = iOC.distance(factory.createPoint(dpDepNeg));
			if (distDep > distDepNeg) {
				isInPolygonDep = true;
			} else {
				isInPolygonDepNeg = true;
			}
			System.out.println(
					FindObjectInDirection.class + " TRANSLATION IS IN PARCEL IN NO DIRECTION " + oppositeDirection);
		}
		Vector2D rightVector = null;
		if (isInPolygonDep) {
			rightVector = vectOrthNeg.normalize().multiply(maximumDistance);
		}
		if (!isInPolygonDep) {
			rightVector = vectOrth.normalize().multiply(maximumDistance);
		}
		if (rightVector == null) {
			return null;
		}
		Coordinate dpLine = rightVector.translate(lineCenter);
		return factory.createLineString(new Coordinate[] {dpLine,lineCenter});
	}
}
