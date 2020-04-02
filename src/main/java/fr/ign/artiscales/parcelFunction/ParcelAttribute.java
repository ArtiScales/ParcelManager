package fr.ign.artiscales.parcelFunction;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.PrecisionModel;
import org.locationtech.jts.precision.GeometryPrecisionReducer;
import org.opengis.feature.simple.SimpleFeature;

import fr.ign.cogit.geoToolsFunctions.Attribute;

public class ParcelAttribute {
	
	/**
	 * field name for the description of the type of community
	 */
	private static String communityTypeFieldName = "armature";

//	public static void main(String[] args) throws Exception {
//	File parcelFile = new File("/tmp/tmp.shp");
//	ShapefileDataStore sds = new ShapefileDataStore(parcelFile.toURI().toURL());
//	SimpleFeatureCollection sfc = sds.getFeatureSource().getFeatures();
//	
//	System.out.println(getCityCodeFromParcels(sfc));
//	}
	 
	/**
	 * get the Community Code number from a Simplefeature (that is most of the time, a parcel or building)
	 * 
	 * @param community
	 *            Collection of cities. The default field name is <i>DEPCOM</i> an can be changed with the function {@link #setArmatureCodeName(String)}
	 * @param parcel
	 *            Collection of parcels to get city codes from.
	 * @return the most represented city code from the SimpleFeatureCollection 
	 */
	public static String getCommunityCodeFromSFC(SimpleFeatureCollection community, SimpleFeature parcel) {
		return getAttributeFromSFC(community, parcel, ParcelSchema.getMinParcelCommunityField());
	}

	/**
	 * get the type of community from a Simplefeature (that is most of the time, a parcel or building)
	 * 
	 * @param community
	 *            Collection of cities. The default field name is <i>armature</i> an can be changed with the
	 *            {@link fr.ign.artiscales.parcelFunction.ParcelSchema#setMinParcelCommunityField(String)} method
	 * @param feat
	 *            Feature to get city codes from.
	 * @return the type of community in which is the feature.
	 */
	public static String getCommunityTypeFromSFC(SimpleFeatureCollection community, SimpleFeature feat) {
		return getAttributeFromSFC(community, feat, communityTypeFieldName);
	}

	/**
	 * get the value of a feature's field from a SimpleFeatureCollection that intersects a given Simplefeature (that is most of the time, a parcel or building) If the given feature
	 * is overlapping multiple SimpleFeatureCollection's features, we calculate which has the more area of intersection
	 * 
	 * @param collec
	 *            Input collection
	 * @param givenFeature
	 *            The given feature to look for
	 * @param fieldName
	 *            The name of the field in which to look for the attribute
	 * @return the value of the feature's field
	 */
	public static String getAttributeFromSFC(SimpleFeatureCollection collec, SimpleFeature givenFeature, String fieldName) {
		SimpleFeature overlappingFeature = null;
		Geometry givenFeatureGeom = GeometryPrecisionReducer.reduce((Geometry) givenFeature.getDefaultGeometry(), new PrecisionModel(10)); 
		boolean multipleOverlap = false;
		SortedMap<Double, SimpleFeature> index = new TreeMap<>();
		try (SimpleFeatureIterator collecIt = collec.features()){
			while (collecIt.hasNext()) {
				SimpleFeature theFeature = collecIt.next();
				Geometry theFeatureGeom =  GeometryPrecisionReducer.reduce((Geometry) theFeature.getDefaultGeometry(), new PrecisionModel(10));
				if (theFeatureGeom.contains(givenFeatureGeom)) {
					overlappingFeature = theFeature;
					break;
				}
				// if the parcel is in between two cities, we put the cities in a sorted collection 
				else if (theFeatureGeom.intersects(givenFeatureGeom)) {
					multipleOverlap = true;
					index.put(theFeatureGeom.getArea(), theFeature);
				}
			}
		} catch (Exception problem) {
			problem.printStackTrace();
		} 
		if (multipleOverlap) {
			overlappingFeature = index.get(index.lastKey());
		}
		return (String) overlappingFeature.getAttribute(fieldName);
	}

	/**
	 * get  a list of all the  <i>city code numbers</i> of the given collection. The city code field name is <i>DEPCOM<i> by default and can be changed with the method
	 * {@link fr.ign.artiscales.parcelFunction.ParcelSchema#setMinParcelCommunityField(String)} If no code is found, we try to generate it (only for the French Parcels)
	 * 
	 * @param parcels
	 *            Input {@link SimpleFeatureCollection} of parcels
	 * @return The list of every <i>city code numbers</i> from the input parcels.
	 */
	public static List<String> getCityCodesFromParcels(SimpleFeatureCollection parcels) {
		List<String> result = new ArrayList<String>();
		Arrays.stream(parcels.toArray(new SimpleFeature[0])).forEach(feat -> {
			String code = ((String) feat.getAttribute(ParcelSchema.getMinParcelCommunityField()));
			if (code != null && !code.isEmpty()) {
				if (!result.contains(code)) {
					result.add(code);
				}
			} else {
				try {
					String c = Attribute.makeINSEECode(feat);
					if (c != null && !result.contains(c)) {
						result.add(Attribute.makeINSEECode(feat));
					}
				} catch (Exception e) {
				}
			}
		});
		return result;
	}
	
	/**
	 * get the most represented <i>city code numbers</i> of the given collection. The city code field name is <i>DEPCOM<i> by default and can be changed with the method
	 * {@link fr.ign.artiscales.parcelFunction.ParcelSchema#setMinParcelCommunityField(String)} If no code is found, we try to generate it (only for the French Parcels)
	 * 
	 * @param parcels
	 *            Input {@link SimpleFeatureCollection} of parcels
	 * @return the most represented <i>city code numbers</i>
	 */
	public static String getCityCodeFromParcels(SimpleFeatureCollection parcels) {
		HashMap<String, Integer> result = new HashMap<String, Integer>(); 
		try (SimpleFeatureIterator it = parcels.features()) {
			while (it.hasNext()) {
				SimpleFeature feat = it.next();
				String code = ((String) feat.getAttribute(ParcelSchema.getMinParcelCommunityField()));
				if (code != null && !code.isEmpty()) {
					result.put(code, result.getOrDefault(code, 0));
				} else {
					try {
						String c = Attribute.makeINSEECode(feat);
						if (c != null && !result.containsKey(c)) {
							result.put(code, result.getOrDefault(code, 0));
						}
					} catch (Exception e) {
					}
				}
			}
		} catch (Exception problem) {
			problem.printStackTrace();
		}
		List<Entry<String, Integer>> sorted = result.entrySet().stream().sorted(Map.Entry.comparingByValue()).collect(Collectors.toList());
		return sorted.get(sorted.size()-1).getKey();
	}

	public static String getArmatureCodeName() {
		return communityTypeFieldName;
	}

	public static void setArmatureCodeName(String armatureCodeName) {
		ParcelAttribute.communityTypeFieldName = armatureCodeName;
	}
}
