package fr.ign.artiscales.parcelFunction;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;

import org.geotools.data.shapefile.ShapefileDataStore;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.factory.CommonFactoryFinder;
import org.geotools.util.factory.GeoTools;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.PrecisionModel;
import org.locationtech.jts.precision.GeometryPrecisionReducer;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.filter.Filter;
import org.opengis.filter.FilterFactory2;

import com.opencsv.CSVReader;

import fr.ign.artiscales.fields.FrenchZoningFields;
import fr.ign.artiscales.fields.GeneralFields;
import fr.ign.cogit.geoToolsFunctions.vectors.Collec;
import fr.ign.cogit.geoToolsFunctions.vectors.Geom;

public class ParcelState {

//	public static void main(String[] args) throws Exception {
//		File geoFile = new File("/home/ubuntu/boulot/these/result2903/dataGeo/");
//		File batiFile = new File(geoFile, "building.shp");
//		File parcelFile = new File("/tmp/parcelTested.shp");
//		ShapefileDataStore sds = new ShapefileDataStore(parcelFile.toURI().toURL());
//		SimpleFeatureIterator sfc = sds.getFeatureSource().getFeatures().features();
//
//		try {
//			while (sfc.hasNext()) {
//				SimpleFeature sf = sfc.next();
//				System.out.println("sf " + sf.getAttribute("NUMERO"));
//				long startTime2 = System.currentTimeMillis();
//
//				isAlreadyBuilt(batiFile, sf);
//				long endTime2 = System.nanoTime();
//				System.out.println("duration for isAlreadyBuilt : " + (endTime2 - startTime2) * 1000);
//			}
//
//		} catch (Exception problem) {
//			problem.printStackTrace();
//		} finally {
//			sfc.close();
//		}
//		sds.dispose();
//	}

	/**
	 * return false if the parcel mandatory needs a contact with the road to be urbanized. return true otherwise TODO haven't done it for the zones because I only found communities
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
		return isArt3AllowsIsolatedParcel(
				((String) feat.getAttribute("CODE_DEP")) + ((String) feat.getAttribute("CODE_COM")), predicateFile);
	}

	/**
	 * return false if the parcel mandatory needs a contact with the road to be urbanized. return true otherwise TODO haven't done it for the zones because I only found communities
	 * that set the same rule regardless of the zone, but that could be done
	 * 
	 * @param insee
	 *            The community number of the concerned city
	 * @param predicateFile
	 *            The table containing urban rules. If null or not set, will return <b>false</b>
	 * @return false by default
	 * @throws IOException
	 */
	public static boolean isArt3AllowsIsolatedParcel(String insee, File predicateFile) throws IOException {
		if(!predicateFile.exists()) {
			return false;
		}
		int nInsee = 0;
		int nArt3 = 0;
		// get rule file
		CSVReader rule = new CSVReader(new FileReader(predicateFile));

		// seek for attribute numbers
		String[] firstLine = rule.readNext();
		for (int i = 0; i < firstLine.length; i++) {
			String s = firstLine[i];
			if (s.equals("insee")) {
				nInsee = i;
			} else if (s.equals("art_3")) {
				nArt3 = i;
			}
		}

		for (String[] line : rule.readAll()) {
			if (insee.equals(line[nInsee])) {
				if (line[nArt3].equals("1")) {
					rule.close();
					return false;
				} else {
					rule.close();
					return true;
				}
			}
		}
		rule.close();
		return false;
	}

	/**
	 * This algorithm looks if a parcel is overlapped by a building and returns true
	 * if they are.
	 * 
	 * @param batiSFC
	 * @param feature
	 * @return True if a building is really intersecting the parcel
	 * @throws IOException
	 */
	public static boolean isAlreadyBuilt(SimpleFeatureCollection batiSFC, SimpleFeature feature) throws IOException {
		return isAlreadyBuilt(batiSFC, feature, 0.0);
	}

	/**
	 * This algorithm looks if a parcel is overlapped by a building and returns true if they are.
	 * overload of the {@link #isAlreadyBuilt(SimpleFeatureCollection, SimpleFeature, double)} to select only a selection of buildings
	 * 
	 * @param buildingFile
	 * @param parcel
	 * @param emprise
	 * @return True if a building is really intersecting the parcel
	 * @throws Exception
	 */
	public static boolean isAlreadyBuilt(File buildingFile, SimpleFeature parcel, Geometry emprise) throws Exception {
		ShapefileDataStore batiSDS = new ShapefileDataStore(buildingFile.toURI().toURL());
		boolean result = isAlreadyBuilt(Collec.snapDatas(batiSDS.getFeatureSource().getFeatures(), emprise), parcel, 0.0);
		batiSDS.dispose();
		return result;
	}

	/**
	 * This algorithm looks if a parcel is overlapped by a building+a buffer (in most of the cases, buffer is negative to delete small parts of buildings that can slightly overlap
	 * a parcel) and returns true if they are.
	 * 
	 * @param batiSFC
	 * @param parcel
	 * @param bufferBati
	 * @return True if a building is really intersecting the parcel
	 * @throws IOException
	 */
	public static boolean isAlreadyBuilt(SimpleFeatureCollection batiSFC, SimpleFeature parcel, double bufferBati)
			throws IOException {
		boolean isContent = false;
		Geometry geom = ((Geometry) parcel.getDefaultGeometry());
		try (SimpleFeatureIterator iterator = batiSFC.features()) {
			while (iterator.hasNext()) {
				if (geom.intersects(((Geometry) iterator.next().getDefaultGeometry()).buffer(bufferBati))) {
					isContent = true;
					break;
				}
			}
		} catch (Exception problem) {
			problem.printStackTrace();
		}
		return isContent;
	}

//	public static Double getEvalInParcel(IFeature parcel, File outMup)
//			throws NoSuchAuthorityCodeException, ParseException, FactoryException, IOException, Exception {
//		if (outMup == null) {
//			return 0.0;
//		}
//		return getEvalInParcel(GeOxygeneGeoToolsTypes.convert2SimpleFeature(parcel, CRS.decode("EPSG:2154")), outMup);
//	}

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
		ShapefileDataStore cellsSDS = new ShapefileDataStore(outMup.toURI().toURL());
		Double result = getEvalInParcel(parcel, cellsSDS.getFeatureSource().getFeatures());
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
		Filter inter = ff.intersects(ff.property(mupSFC.getSchema().getGeometryDescriptor().getLocalName()), ff.literal(parcel.getDefaultGeometry()));
		SimpleFeatureCollection onlyCells = mupSFC.subCollection(inter);
		Double bestEval = 0.0;
		// put the best cell evaluation into the parcel
		if (onlyCells.size() > 0) {
			try (SimpleFeatureIterator onlyCellIt = onlyCells.features()) {
				while (onlyCellIt.hasNext()) {
					bestEval = Math.max(bestEval, (Double) onlyCellIt.next().getAttribute("eval"));
				}
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
		Filter inter = ff.intersects(ff.property(mupSFC.getSchema().getGeometryDescriptor().getLocalName()),
				ff.literal(((Geometry) parcel.getDefaultGeometry()).buffer(100.0)));
		SimpleFeatureCollection onlyCells = mupSFC.subCollection(inter);
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
						if (geometryUp.intersects((Geometry) cell.getDefaultGeometry())) {
							return ((Double) cell.getAttribute("eval"));
						}
					}
				} catch (Exception problem) {
					problem.printStackTrace();
				} 
				distBuffer = distBuffer + 5;
			}
		}
		return bestEval;
	}

	public static boolean isParcelInCell(SimpleFeature parcelIn, SimpleFeatureCollection cellsCollection)
			throws Exception {
		Geometry geom = (Geometry) parcelIn.getDefaultGeometry();
		cellsCollection = Collec.snapDatas(cellsCollection, geom);
		boolean result = false;
		// import of the cells of MUP-City outputs
		try (SimpleFeatureIterator cellsCollectionIt = cellsCollection.features()) {
			while (cellsCollectionIt.hasNext()) {
				if (((Geometry) cellsCollectionIt.next().getDefaultGeometry()).intersects(geom)) {
					result = true;
					break;
				}
			}
		} catch (Exception problem) {
			problem.printStackTrace();
		}
		return result;
	}

	/**
	 * Return a single Zone Generic Name that a parcels intersect. If the parcel intersects multiple, we select the one that covers the most area
	 * 
	 * @param parcelIn
	 * @param zoningFile
	 * @return Zone Generic Name that a parcels intersect
	 * @throws Exception
	 */
	public static String parcelInBigZone(File zoningFile, SimpleFeature parcelIn) throws Exception {
		List<String> bigZones = parcelInBigZone(parcelIn, zoningFile);
		if (bigZones.isEmpty())
			return "null";
		return bigZones.get(0);
	}

	/**
	 * return the Zone Generic Name that a parcels intersect result is sorted by the largest intersected zone to the lowest
	 * 
	 * @param parcelIn
	 * @param zoningFile
	 * @return A list of the multiple parcel intersected, sorted by area of occupation
	 * @throws Exception
	 */
	public static List<String> parcelInBigZone(SimpleFeature parcelIn, File zoningFile) throws Exception {
		List<String> result = new LinkedList<String>();
		ShapefileDataStore shpDSZone = new ShapefileDataStore(zoningFile.toURI().toURL());
		// if there's two zones, we need to sort them by making collection. zis iz évy
		// calculation, but it could worth it
		boolean twoZones = false;
		HashMap<String, Double> repart = new HashMap<String, Double>();
		try (SimpleFeatureIterator featuresZones = Collec
				.snapDatas(shpDSZone.getFeatureSource().getFeatures(), (Geometry) parcelIn.getDefaultGeometry()).features()) {
			zoneLoop: while (featuresZones.hasNext()) {
				SimpleFeature feat = featuresZones.next();
				PrecisionModel precMod = new PrecisionModel(100);
				Geometry featGeometry = GeometryPrecisionReducer.reduce((Geometry) feat.getDefaultGeometry(), precMod);
				Geometry parcelInGeometry = GeometryPrecisionReducer.reduce((Geometry) parcelIn.getDefaultGeometry(),
						precMod);
				if (featGeometry.buffer(0.5).contains(parcelInGeometry)) {
					twoZones = false;
					String zoneName = FrenchZoningFields.normalizeNameFrenchBigZone((String) feat.getAttribute(GeneralFields.getZoneGenericNameField()));
					switch (zoneName) {
					case "U":
						result.add("U");
						result.remove("AU");
						result.remove("NC");
						break zoneLoop;
					case "AU":
						result.add("AU");
						result.remove("U");
						result.remove("NC");
						break zoneLoop;
					case "NC":
						result.add("NC");
						result.remove("AU");
						result.remove("U");
						break zoneLoop;
					default:
						result.remove("AU");
						result.remove("U");
						result.remove("NC");
						result.add(zoneName);
					}
				}
				// maybe the parcel is in between two zones (less optimized) intersection
				else if ((featGeometry).intersects(parcelInGeometry)) {
					twoZones = true;
					double area = Geom
							.scaledGeometryReductionIntersection(Arrays.asList(featGeometry, parcelInGeometry))
							.getArea();
					String zoneName = FrenchZoningFields.normalizeNameFrenchBigZone((String) feat.getAttribute(GeneralFields.getZoneGenericNameField()));
					switch (zoneName) {
					case "U":
						repart.put("U",repart.getOrDefault("U", 0.0) + area);
						break;
					case "AU":
						repart.put("AU", repart.getOrDefault("AU", 0.0) + area);
						break;
					case "NC":
						repart.put("NC", repart.getOrDefault("NC", 0.0) + area);
						break;
					default:
						repart.put(zoneName, repart.getOrDefault((String) feat.getAttribute(GeneralFields.getZoneGenericNameField()), 0.0) + area);
					}
				}
			}
		} catch (Exception problem) {
			problem.printStackTrace();
		}
		shpDSZone.dispose();

		//in case of multi zones, we sort the entries relatively to the highest area
		if (twoZones == true) {
			List<Entry<String, Double>> entryList = new ArrayList<Entry<String, Double>>(repart.entrySet());
			Collections.sort(entryList, new Comparator<Entry<String, Double>>() {
				@Override
				public int compare(Entry<String, Double> obj1, Entry<String, Double> obj2) {
					return obj2.getValue().compareTo(obj1.getValue());
				}
			});
			for (Entry<String, Double> s : entryList) {
				result.add(s.getKey());
			}
		}
		return result;
	}

	/**
	 * return a single typology that a parcels intersect if the parcel intersects
	 * multiple, we select the one that covers the most area
	 * 
	 * @param parcelIn
	 * @param communityFile
	 * @return the number of most intersected community type
	 * @throws Exception
	 */
	public static String parcelInTypo(File communityFile, SimpleFeature parcelIn) throws Exception {
		return parcelInTypo(parcelIn, communityFile).get(0);
	}

	/**
	 * return the typologies that a parcels intersect
	 * 
	 * @param parcelIn
	 * @param communityFile
	 * @return the multiple parcel intersected, sorted by area of occupation
	 * @throws Exception
	 */
	public static List<String> parcelInTypo(SimpleFeature parcelIn, File communityFile) throws Exception {
		List<String> result = new ArrayList<String>();
		ShapefileDataStore shpDSZone = new ShapefileDataStore(communityFile.toURI().toURL());
		// objects for crossed zones
		boolean twoZones = false;
		HashMap<String, Double> repart = new HashMap<String, Double>();

		try (SimpleFeatureIterator featuresZones = Collec
				.snapDatas(shpDSZone.getFeatureSource().getFeatures(), (Geometry) parcelIn.getDefaultGeometry()).features()) {
			zone: while (featuresZones.hasNext()) {
				SimpleFeature feat = featuresZones.next();
				Geometry parcelInGeometry = (Geometry) parcelIn.getDefaultGeometry();
				Geometry featGeometry = (Geometry) feat.getDefaultGeometry();
				// TODO if same typo in two different typo, won't fall into that trap =>
				// create a big zone shapefile instead?
				if (featGeometry.buffer(1).contains(parcelInGeometry)) {
					switch ((String) feat.getAttribute("typo")) {
					case "rural":
						result.add("rural");
						result.remove("periUrbain");
						result.remove("banlieue");
						result.remove("centre");
						break zone;
					case "periUrbain":
						result.add("periUrbain");
						result.remove("rural");
						result.remove("banlieue");
						result.remove("centre");
						break zone;
					case "banlieue":
						result.add("banlieue");
						result.remove("rural");
						result.remove("periUrbain");
						result.remove("centre");
						break zone;
					case "centre":
						result.add("centre");
						result.remove("rural");
						result.remove("periUrbain");
						result.remove("banlieue");
						break zone;
					}
				}
				// maybe the parcel is in between two cities
				else if (featGeometry.intersects(parcelInGeometry)) {
					twoZones = true;
					double area = Geom
							.scaledGeometryReductionIntersection(Arrays.asList(featGeometry, parcelInGeometry))
							.getArea();
					switch ((String) feat.getAttribute("typo")) {
					case "rural":
						if (repart.containsKey("rural")) {
							repart.put("rural", repart.get("rural") + area);
						} else {
							repart.put("rural", area);
						}
						break;
					case "centre":
						if (repart.containsKey("centre")) {
							repart.put("centre", repart.get("centre") + area);
						} else {
							repart.put("centre", area);
						}
						break;
					case "banlieue":
						if (repart.containsKey("banlieue")) {
							repart.put("banlieue", repart.get("banlieue") + area);
						} else {
							repart.put("banlieue", area);
						}
						break;
					case "periUrbain":
						if (repart.containsKey("periUrbain")) {
							repart.put("periUrbain", repart.get("periUrbain") + area);
						} else {
							repart.put("periUrbain", area);
						}
						break;
					}
				}
			}
		} catch (Exception problem) {
			problem.printStackTrace();
		} 

		if (twoZones == true) {
			List<Entry<String, Double>> entryList = new ArrayList<Entry<String, Double>>(repart.entrySet());
			Collections.sort(entryList, new Comparator<Entry<String, Double>>() {
				@Override
				public int compare(Entry<String, Double> obj1, Entry<String, Double> obj2) {
					return obj2.getValue().compareTo(obj1.getValue());
				}
			});

			for (Entry<String, Double> s : entryList) {
				result.add(s.getKey());
			}
		}

		shpDSZone.dispose();

		if (result.isEmpty()) {
			result.add("null");
		}
		return result;
	}
}
