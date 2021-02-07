package fr.ign.artiscales.pm.parcelFunction;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import fr.ign.artiscales.tools.geoToolsFunctions.vectors.collec.CollecMgmt;
import fr.ign.artiscales.tools.geoToolsFunctions.vectors.collec.CollecTransform;
import org.geotools.data.DataStore;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.factory.CommonFactoryFinder;
import org.geotools.util.factory.GeoTools;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.MultiLineString;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.PrecisionModel;
import org.locationtech.jts.precision.GeometryPrecisionReducer;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.filter.FilterFactory2;

import com.opencsv.CSVReader;

import fr.ign.artiscales.pm.fields.GeneralFields;
import fr.ign.artiscales.tools.geoToolsFunctions.Attribute;
import fr.ign.artiscales.tools.geoToolsFunctions.vectors.Geom;
import fr.ign.artiscales.tools.geoToolsFunctions.vectors.Geopackages;
import fr.ign.artiscales.tools.geoToolsFunctions.vectors.geom.Lines;

public class ParcelState {

	// public static void main(String[] args) throws Exception {
	// File geoFile = new File("/home/ubuntu/boulot/these/result2903/dataGeo/");
	// File batiFile = new File(geoFile, "building");
	// File parcelFile = new File("/tmp/parcelTested");
	// ShapefileDataStore sds = new ShapefileDataStore(parcelFile.toURI().toURL());
	// SimpleFeatureIterator sfc = sds.getFeatureSource().getFeatures().features();
	// try {
	// while (sfc.hasNext()) {
	// SimpleFeature sf = sfc.next();
	// System.out.println("sf " + sf.getAttribute("NUMERO"));
	// long startTime2 = System.currentTimeMillis();
	//
	// isAlreadyBuilt(batiFile, sf);
	// long endTime2 = System.nanoTime();
	// System.out.println("duration for isAlreadyBuilt : " + (endTime2 - startTime2) * 1000);
	// }
	// } catch (Exception problem) {
	// problem.printStackTrace();
	// } finally {
	// sfc.close();
	// }
	// sds.dispose();
	// }
	private static String widthFieldAttribute = "LARGEUR";
	private static double defaultWidthRoad = 7.5;

	public static int countParcelNeighborhood(Geometry parcelGeom, SimpleFeatureCollection parcels) {
		int result = 0;
		try (SimpleFeatureIterator parcelIt = parcels.features()) {
			while (parcelIt.hasNext())
				if (GeometryPrecisionReducer.reduce((Geometry) parcelIt.next().getDefaultGeometry(), new PrecisionModel(10)).buffer(1)
						.intersects(GeometryPrecisionReducer.reduce(parcelGeom, new PrecisionModel(10))))
					result++;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return result;
	}

	/**
	 * Get a list of the surrounding buffered road segments.
	 * 
	 * The buffer length is calculated with an attribute field. The default name of the field is <i>LARGEUR</i> and can be set with the {@link #setWidthFieldAttribute(String)}
	 * method. If no field is found, a default value of 7.5 meters is used (this default value can be set with the {@link #setDefaultWidthRoad(double)} method).
	 * 
	 * @param roads
	 *            collection of road
	 * @return The list of the surrounding buffered road segments.
	 */
	public static List<Geometry> getRoadPolygon(SimpleFeatureCollection roads) {
		// List<Geometry> roadGeom = Arrays.stream(Collec.snapDatas(roads, poly.buffer(5)).toArray(new SimpleFeature[0]))
		// .map(g -> ((Geometry) g.getDefaultGeometry()).buffer((double) g.getAttribute("LARGEUR"))).collect(Collectors.toList());
		List<Geometry> roadGeom = new ArrayList<>();
		try (SimpleFeatureIterator roadSnapIt = roads.features()) {
			while (roadSnapIt.hasNext()) {
				SimpleFeature feat = roadSnapIt.next();
				roadGeom.add(((Geometry) feat.getDefaultGeometry())
						.buffer((CollecMgmt.isCollecContainsAttribute(roads, widthFieldAttribute) ? (double) feat.getAttribute(widthFieldAttribute) + 2.5
								: defaultWidthRoad)));
			}
		} catch (Exception problem) {
			problem.printStackTrace();
		}
		return roadGeom;
	}

	/**
	 * Determine the width of the parcel on road.
	 * 
	 * @param p
	 *            input {@link Polygon}
	 * @param roads
	 *            Road Geopackage (can be null)
	 * @param ext
	 *            lines
	 * @return width of the parcel on road
	 */
	public static double getParcelFrontSideWidth(Polygon p, SimpleFeatureCollection roads, List<LineString> ext) {
		try {
			double len = p.buffer(0.2).intersection(p.getFactory().createMultiLineString(ext.toArray(new LineString[ext.size()]))).getLength();
			if (len > 0)
				return len;
			if (roads != null) {
				len = p.buffer(0.2)
						.intersection(Lines.getListLineStringAsMultiLS(
								Arrays.stream(roads.toArray(new SimpleFeature[0])).filter(r -> ((Geometry) r.getDefaultGeometry()).intersects(p))
										.flatMap(r -> Lines.getLineStrings((Geometry) r.getDefaultGeometry()).stream()).collect(Collectors.toList()),
								new GeometryFactory()))
						.getLength();
				if (len > 0)
					return len;
				else
					return 0;
			} else
				return 0;
		} catch (Exception e) {
			try {
				double len = p.buffer(0.4).intersection(p.getFactory().createMultiLineString(ext.toArray(new LineString[ext.size()]))).getLength();
				if (len > 0)
					return len;
				if (roads != null) {
					len = p.buffer(0.4).intersection(Lines.getListLineStringAsMultiLS(
							Arrays.stream(roads.toArray(new SimpleFeature[0])).filter(r -> ((Geometry) r.getDefaultGeometry()).intersects(p))
									.flatMap(r -> Lines.getLineStrings((Geometry) r.getDefaultGeometry()).stream()).collect(Collectors.toList()),
							new GeometryFactory())).getLength();
					if (len > 0)
						return len;
					else
						return 0;
				} else
					return 0;
			} catch (Exception ee) {
				return 0;
			}
		}
	}

	/**
	 * Indicate if the given polygon has a proximity to the road, which can be represented by multiple ways. This could be a road Geopackage or a {@link Geometry} representing the
	 * exterior of a parcel plan.
	 * 
	 * @param poly
	 *            input polygon
	 * @param roads
	 *            input road Geopacakge (can be null)
	 * @param ext
	 *            External polygon
	 * @return true is the polygon has a road access
	 */
	public static boolean isParcelHasRoadAccess(Polygon poly, SimpleFeatureCollection roads, MultiLineString ext) {
		return isParcelHasRoadAccess(poly, roads, ext, null);
	}

	/**
	 * Indicate if the given polygon has a proximity to the road, which can be represented by multiple ways. This could be a road Geopacakge or a {@link Geometry} representing the
	 * exterior of a parcel plan. Some empty {@link Geometry} can represent an exclusion zone which won't be taken as a road space when empty of parcels
	 * 
	 * @param poly
	 *            input polygon
	 * @param roads
	 *            input road Geopacakge (can be null)
	 * @param ext
	 *            External polygon
	 * @param disabledBuffer
	 *            A {@link Geometry} that cannot be considered as absence of road -can be null)
	 * @return true is the polygon has a road access
	 */
	public static boolean isParcelHasRoadAccess(Polygon poly, SimpleFeatureCollection roads, MultiLineString ext, Geometry disabledBuffer) {
		if (poly.intersects(ext.buffer(0.5)))
			return disabledBuffer == null || !poly.intersects(disabledBuffer.buffer(0.5));
		return roads != null && !roads.isEmpty() && poly.intersects(Geom.unionGeom(getRoadPolygon(roads)));
	}

	/**
	 * Return false if the parcel mandatory needs a contact with the road to be urbanized. return true otherwise TODO haven't done it for the zones because I only found communities
	 * that set the same rule regardless of the zone, but that could be done
	 * 
	 * @param feat
	 *            The parcel (which has to be French)
	 * @param predicateFile
	 *            The table containing urban rules. If null or not set, will return <b>false</b>
	 * @return false by default
	 * @throws IOException
	 */
	public static boolean isArt3AllowsIsolatedParcel(SimpleFeature feat, File predicateFile) throws IOException {
		return isArt3AllowsIsolatedParcel(( feat.getAttribute("CODE_DEP")) + ((String) feat.getAttribute("CODE_COM")), predicateFile);
	}

	/**
	 * Return false if the parcel mandatory needs a contact with the road to be urbanized. return true otherwise TODO haven't done it for the zones because I only found communities
	 * that set the same rule regardless of the zone, but that could be done.
	 * 
	 * @param insee
	 *            The community number of the concerned city
	 * @param predicateFile
	 *            The table containing urban rules. If null or not set, will return <b>false</b>
	 * @return false by default
	 * @throws IOException
	 */
	public static boolean isArt3AllowsIsolatedParcel(String insee, File predicateFile) throws IOException {
		if (!predicateFile.exists())
			return false;
		// get rule file
		CSVReader rule = new CSVReader(new FileReader(predicateFile));
		// seek for attribute numbers
		String[] firstLine = rule.readNext();
		int nInsee = Attribute.getIndice(firstLine, "insee");
		int nArt3 = Attribute.getIndice(firstLine, "art_3");
		for (String[] line : rule.readAll())
			if (insee.equals(line[nInsee]))
				if (line[nArt3].equals("1")) {
					rule.close();
					return false;
				} else {
					rule.close();
					return true;
				}
		rule.close();
		return false;
	}

	/**
	 * This algorithm looks if a parcel is overlapped by a building and returns true if they are.
	 * 
	 * @param batiSFC
	 * @param feature
	 * @return True if a building is really intersecting the parcel
	 */
	public static boolean isAlreadyBuilt(SimpleFeatureCollection batiSFC, SimpleFeature feature) {
		return isAlreadyBuilt(batiSFC, feature, 0.0, 0.0);
	}

	public static boolean isAlreadyBuilt(File buildingFile, SimpleFeature parcel, Geometry emprise) throws IOException {
		return isAlreadyBuilt(buildingFile, parcel, emprise, 0);
	}

	/**
	 * This algorithm looks if a parcel is overlapped by a building and returns true if they are. overload of the
	 * {@link #isAlreadyBuilt(SimpleFeatureCollection, SimpleFeature, double, double)} to select only a selection of buildings
	 * 
	 * @param buildingFile
	 * @param parcel
	 * @param emprise
	 * @return True if a building is really intersecting the parcel
	 * @throws IOException
	 */
	public static boolean isAlreadyBuilt(File buildingFile, SimpleFeature parcel, Geometry emprise, double uncountedBuildingArea) throws IOException {
		DataStore batiDS = Geopackages.getDataStore(buildingFile);
		boolean result = isAlreadyBuilt(CollecTransform.selectIntersection(batiDS.getFeatureSource(batiDS.getTypeNames()[0]).getFeatures(), emprise), parcel,
				0.0, uncountedBuildingArea);
		batiDS.dispose();
		return result;
	}

	public static boolean isAlreadyBuilt(File buildingFile, SimpleFeature parcel, double bufferBati, double uncountedBuildingArea)
			throws IOException {
		DataStore batiDS = Geopackages.getDataStore(buildingFile);
		boolean result = isAlreadyBuilt(
				CollecTransform.selectIntersection(batiDS.getFeatureSource(batiDS.getTypeNames()[0]).getFeatures(),
						((Geometry) parcel.getDefaultGeometry()).buffer(10)),
				(Geometry) parcel.getDefaultGeometry(), bufferBati, uncountedBuildingArea);
		batiDS.dispose();
		return result;
	}

	/**
	 * This algorithm looks if a parcel is overlapped by a building+a buffer (in most of the cases, buffer is negative to delete small parts of buildings that can slightly overlap
	 * a parcel) and returns true if they are.
	 * 
	 * @param buildingSFC
	 * @param parcel
	 * @param bufferBati
	 * @return True if a building is really intersecting the parcel
	 */
	public static boolean isAlreadyBuilt(SimpleFeatureCollection buildingSFC, SimpleFeature parcel, double bufferBati, double uncountedBuildingArea) {
		return isAlreadyBuilt(buildingSFC, (Geometry) parcel.getDefaultGeometry(), bufferBati, uncountedBuildingArea);
	}

	public static boolean isAlreadyBuilt(SimpleFeatureCollection buildingSFC, Geometry parcelGeom, double bufferBati, double uncountedBuildingArea) {
		try (SimpleFeatureIterator iterator = buildingSFC.features()) {
			while (iterator.hasNext()) {
				Geometry buildingGeom = (Geometry) iterator.next().getDefaultGeometry();
				if (buildingGeom.getArea() >= uncountedBuildingArea && parcelGeom.intersects(buildingGeom.buffer(bufferBati)))
					return true;
			}
		} catch (Exception problem) {
			problem.printStackTrace();
		}
		return false;
	}

	/**
	 * Get the evaluation of a cell generated by MUP-City and contained in a input parcel
	 * 
	 * @param parcel
	 *            Input {@link SimpleFeature} parcel
	 * @param outMup
	 *            Shapefile to the vectorized MUP-City output
	 * @return The best evaluation of the intersected MUP-City's cells
	 * @throws IOException
	 */
	public static Double getEvalInParcel(SimpleFeature parcel, File outMup) throws IOException {
		DataStore cellsSDS = Geopackages.getDataStore(outMup);
		Double result = getEvalInParcel(parcel, cellsSDS.getFeatureSource(cellsSDS.getTypeNames()[0]).getFeatures());
		cellsSDS.dispose();
		return result;
	}

	/**
	 * Get the evaluation of a cell generated by MUP-City and contained in a input parcel
	 * 
	 * @param parcel
	 *            Input {@link SimpleFeature} parcel
	 * @param mupSFC
	 *            {@link SimpleFeatureCollection} of MUP-City's outputs
	 * @return The best evaluation of the intersected MUP-City's cells
	 */
	public static Double getEvalInParcel(SimpleFeature parcel, SimpleFeatureCollection mupSFC) {
		FilterFactory2 ff = CommonFactoryFinder.getFilterFactory2(GeoTools.getDefaultHints());
		SimpleFeatureCollection onlyCells = mupSFC.subCollection(
				ff.intersects(ff.property(mupSFC.getSchema().getGeometryDescriptor().getLocalName()), ff.literal(parcel.getDefaultGeometry())));
		double bestEval = 0.0;
		// put the best cell evaluation into the parcel
		if (onlyCells.size() > 0) {
			try (SimpleFeatureIterator onlyCellIt = onlyCells.features()) {
				while (onlyCellIt.hasNext())
					bestEval = Math.max(bestEval, (Double) onlyCellIt.next().getAttribute("eval"));
			} catch (Exception problem) {
				problem.printStackTrace();
			}
		}
		return bestEval;
	}

	/**
	 * Get the evaluation of a cell generated by MUP-City and close to the input parcel
	 * 
	 * @param parcel
	 *            Input {@link SimpleFeature} parcel
	 * @param mupSFC
	 *            {@link SimpleFeatureCollection} of MUP-City's outputs
	 * @return The best evaluation of the MUP-City's cells near the parcel every 5 meters. Return 0 if the cells are 100 meters far from the parcels.
	 */
	public static Double getCloseEvalInParcel(SimpleFeature parcel, SimpleFeatureCollection mupSFC) {
		FilterFactory2 ff = CommonFactoryFinder.getFilterFactory2(GeoTools.getDefaultHints());
		SimpleFeatureCollection onlyCells = mupSFC.subCollection(ff.intersects(ff.property(mupSFC.getSchema().getGeometryDescriptor().getLocalName()),
				ff.literal(((Geometry) parcel.getDefaultGeometry()).buffer(100.0))));
		Double bestEval = 0.0;
		// put the best cell evaluation into the parcel
		if (onlyCells.size() > 0) {
			double distBuffer = 0.0;
			// we randomly decide that the cell cannot be further than 100 meters
			while (distBuffer < 100) {
				Geometry geometryUp = ((Geometry) parcel.getDefaultGeometry()).buffer(distBuffer);
				try (SimpleFeatureIterator onlyCellIt = onlyCells.features()) {
					while (onlyCellIt.hasNext()) {
						SimpleFeature cell = onlyCellIt.next();
						if (geometryUp.intersects((Geometry) cell.getDefaultGeometry()))
							return ((Double) cell.getAttribute("eval"));
					}
				} catch (Exception problem) {
					problem.printStackTrace();
				}
				distBuffer = distBuffer + 5;
			}
		}
		return bestEval;
	}

	/**
	 * Return a single Zone Generic Name that a parcels intersect. If the parcel intersects multiple, we select the one that covers the most area
	 * 
	 * @param parcelIn
	 * @param zoningFile
	 * @return Zone Generic Name that a parcels intersect
	 * @throws IOException
	 */
	public static String parcelInGenericZone(File zoningFile, SimpleFeature parcelIn) throws IOException {
		DataStore ds = Geopackages.getDataStore(zoningFile);
		String preciseZone = CollecTransform.getIntersectingFieldFromSFC((Geometry) parcelIn.getDefaultGeometry(),
				ds.getFeatureSource(ds.getTypeNames()[0]).getFeatures(), GeneralFields.getZoneGenericNameField());
		ds.dispose();
		return preciseZone;
	}

	/**
	 * return a single typology that a parcels intersect if the parcel intersects multiple, we select the one that covers the most area
	 * 
	 * @param parcelIn
	 * @param communityFile
	 * @param typoAttribute
	 *            the field name of the typo
	 * @return the number of most intersected community type
	 * @throws IOException 
	 */
	public static String parcelInTypo(File communityFile, SimpleFeature parcelIn, String typoAttribute) throws IOException {
		DataStore ds = Geopackages.getDataStore(communityFile);
		String typo = CollecTransform.getIntersectingFieldFromSFC((Geometry) parcelIn.getDefaultGeometry(),
				ds.getFeatureSource(ds.getTypeNames()[0]).getFeatures(), typoAttribute);
		ds.dispose();
		return typo;
	}

	public static String getWidthFieldAttribute() {
		return widthFieldAttribute;
	}

	public static void setWidthFieldAttribute(String widthFieldAttribute) {
		ParcelState.widthFieldAttribute = widthFieldAttribute;
	}

	public static double getDefaultWidthRoad() {
		return defaultWidthRoad;
	}

	public static void setDefaultWidthRoad(double defaultWidthRoad) {
		ParcelState.defaultWidthRoad = defaultWidthRoad;
	}
}
