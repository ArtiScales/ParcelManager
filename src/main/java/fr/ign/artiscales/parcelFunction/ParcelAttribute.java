package fr.ign.artiscales.parcelFunction;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.locationtech.jts.geom.Geometry;
import org.opengis.feature.simple.SimpleFeature;

import fr.ign.artiscales.fields.french.FrenchParcelFields;
import fr.ign.cogit.geoToolsFunctions.vectors.Collec;

public class ParcelAttribute {
	
	/**
	 * field name for the description of the type of community
	 */
	private static String communityTypeFieldName = "armature";

//	public static void main(String[] args) throws Exception {
//	File parcelFile = new File("/home/ubuntu/workspace/ParcelManager/src/main/resources/testData/parcelle.shp");
//	ShapefileDataStore sds = new ShapefileDataStore(parcelFile.toURI().toURL());
//	SimpleFeatureCollection sfc = sds.getFeatureSource().getFeatures();
//	long start = System.currentTimeMillis();
//	Collec.getFieldFromSFC((Geometry) sfc.features().next().getDefaultGeometry(),sfc, "CODE_DEP");
////	getAttributeFromSFC(sfc, sfc.features().next(),"CODE_DEP");
//	System.out.println("time : " + String.valueOf(System.currentTimeMillis() - start));
//	}
	 
	/**
	 * get the Community Code number from a Simplefeature (that is most of the time, a parcel or building)
	 * 
	 * @param sFCWithCommunityCode
	 *            Collection of cities. The default field name is <i>DEPCOM</i> an can be changed with the function {@link #setCommunityTypeFieldName(String)}
	 * @param feat
	 *            Collection of parcels to get city codes from.
	 * @return the most represented city code from the SimpleFeatureCollection 
	 */
	public static String getCommunityCodeFromSFC(SimpleFeatureCollection sFCWithCommunityCode, SimpleFeature feat) {
		return Collec.getFieldFromSFC((Geometry) feat.getDefaultGeometry(), sFCWithCommunityCode, ParcelSchema.getMinParcelCommunityField());
	}

	/**
	 * Get the type of community from a Simplefeature (that is most of the time, a parcel or building).
	 * 
	 * @param sfc
	 *            Collection of cities. The default field name is <i>armature</i> an can be changed with the
	 *            {@link fr.ign.artiscales.parcelFunction.ParcelSchema#setMinParcelCommunityField(String)} method
	 * @param feat
	 *            Feature to get city codes from.
	 * @return the type of community in which is the feature.
	 */
	public static String getCommunityTypeFromSFC(SimpleFeatureCollection sfc, SimpleFeature feat) {
		return Collec.getFieldFromSFC((Geometry) feat.getDefaultGeometry(), sfc, communityTypeFieldName);
	}

	/**
	 * Get a list of all the <i>city code numbers</i> of the given collection. The city code field name is <i>DEPCOM<i> by default and can be changed with the method
	 * {@link fr.ign.artiscales.parcelFunction.ParcelSchema#setMinParcelCommunityField(String)}. If no code is found, we try to generate it (only for the French Parcels)
	 * 
	 * @param parcels
	 *            Input {@link SimpleFeatureCollection} of parcels
	 * @return The list of every <i>city code numbers</i> from the input parcels.
	 */
	public static List<String> getCityCodesOfParcels(SimpleFeatureCollection parcels) {
		List<String> result = new ArrayList<String>();
		Arrays.stream(parcels.toArray(new SimpleFeature[0])).forEach(feat -> {
			String code = ((String) feat.getAttribute(ParcelSchema.getMinParcelCommunityField()));
			if (code != null && !code.isEmpty()) {
				if (!result.contains(code)) {
					result.add(code);
				}
			} else {
				try {
					String c = FrenchParcelFields.makeINSEECode(feat);
					if (c != null && !result.contains(c)) {
						result.add(c);
					}
				} catch (Exception e) {
				}
			}
		});
		return result;
	}
	
	/**
	 * Get the most represented <i>city code numbers</i> of the given collection. The city code field name is <i>DEPCOM<i> by default and can be changed with the method
	 * {@link fr.ign.artiscales.parcelFunction.ParcelSchema#setMinParcelCommunityField(String)}. If no code is found, we try to generate it (only for the French Parcels).
	 * 
	 * @param parcels
	 *            Input {@link SimpleFeatureCollection} of parcels
	 * @return the most represented <i>city code numbers</i>
	 */
	public static String getCityCodeOfParcels(SimpleFeatureCollection parcels) {
		HashMap<String, Integer> result = new HashMap<String, Integer>(); 
		try (SimpleFeatureIterator it = parcels.features()) {
			while (it.hasNext()) {
				SimpleFeature feat = it.next();
				String code = ((String) feat.getAttribute(ParcelSchema.getMinParcelCommunityField()));
				if (code != null && !code.isEmpty()) {
					result.put(code, result.getOrDefault(code, 1));
				} else {
					try {
						String c = FrenchParcelFields.makeINSEECode(feat);
						if (c != null && !result.containsKey(c)) {
							result.put(code, result.getOrDefault(code, 1));
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

	public static String getCommunityTypeFieldName() {
		return communityTypeFieldName;
	}

	public static void setCommunityTypeFieldName(String armatureCodeName) {
		ParcelAttribute.communityTypeFieldName = armatureCodeName;
	}
}