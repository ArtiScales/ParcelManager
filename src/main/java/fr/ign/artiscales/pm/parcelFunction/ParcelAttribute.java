package fr.ign.artiscales.pm.parcelFunction;

import fr.ign.artiscales.pm.fields.GeneralFields;
import fr.ign.artiscales.pm.fields.french.FrenchParcelFields;
import fr.ign.artiscales.tools.geoToolsFunctions.vectors.collec.CollecMgmt;
import fr.ign.artiscales.tools.geoToolsFunctions.vectors.collec.CollecTransform;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.locationtech.jts.geom.Geometry;
import org.opengis.feature.simple.SimpleFeature;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

/**
 * Methods to deal with parcel's specific attributes
 */
public class ParcelAttribute {

    /**
     * field name for the description of the type of community
     */
    private static String communityTypeFieldName = "armature";

//	public static void main(String[] args) throws Exception {
//	File parcelFile = new File("/home/ubuntu/workspace/ParcelManager/src/main/resources/testData/parcelle.gpkg");
//	ShapefileDataStore sds = new ShapefileDataStore(parcelFile.toURI().toURL());
//	SimpleFeatureCollection sfc = sds.getFeatureSource().getFeatures();
//	long start = System.currentTimeMillis();
//	Collec.getFieldFromSFC((Geometry) sfc.features().next().getDefaultGeometry(),sfc, "CODE_DEP");
////	getAttributeFromSFC(sfc, sfc.features().next(),"CODE_DEP");
//	System.out.println("time : " + String.valueOf(System.currentTimeMillis() - start));
//	}

    /**
     * Get the Community Code number from a {@link SimpleFeature} (that is most of the time, a parcel or building)
     *
     * @param sFCWithCommunityCode Collection of cities. The default field name is <i>DEPCOM</i> an can be changed with the function {@link #setCommunityTypeFieldName(String)}
     * @param feat                 Collection of parcels to get city codes from.
     * @return the most represented city code from the SimpleFeatureCollection
     */
    public static String getCommunityCodeFromSFC(SimpleFeatureCollection sFCWithCommunityCode, SimpleFeature feat) {
        if (!CollecMgmt.isCollecContainsAttribute(sFCWithCommunityCode, ParcelSchema.getMinParcelCommunityField())) {
            switch (GeneralFields.getParcelFieldType()) {
                case ("french"):
                    return FrenchParcelFields.makeDEPCOMCode(CollecTransform.getIntersectingSimpleFeatureFromSFC((Geometry) feat.getDefaultGeometry(), sFCWithCommunityCode));
                default:
                    return "";
            }
        } else {
            return CollecTransform.getIntersectingFieldFromSFC((Geometry) feat.getDefaultGeometry(), sFCWithCommunityCode, ParcelSchema.getMinParcelCommunityField());
        }
    }

    /**
     * get the Section code from a {@link SimpleFeature} (that is most of the time, a parcel or building)
     *
     * @param sFCWithCommunityCode Collection of cities. The default field name is <i>SECTION</i> an can be changed with the function {@link ParcelSchema#setMinParcelSectionField(String)}
     * @param feat                 Collection of parcels to get city codes from.
     * @return the most represented city code from the SimpleFeatureCollection
     */
    public static String getSectionCodeFromSFC(SimpleFeatureCollection sFCWithCommunityCode, SimpleFeature feat) {
        return CollecTransform.getIntersectingFieldFromSFC((Geometry) feat.getDefaultGeometry(), sFCWithCommunityCode, ParcelSchema.getMinParcelSectionField());
    }

    /**
     * get the Number from a {@link SimpleFeature} (that is most of the time, a parcel or building)
     *
     * @param sFCWithCommunityCode Collection of cities. The default field name is <i>NUMERO</i> an can be changed with the function {@link ParcelSchema#setMinParcelNumberField(String)}
     * @param feat                 Collection of parcels to get city codes from.
     * @return the most represented city code from the SimpleFeatureCollection
     */
    public static String getNumberCodeFromSFC(SimpleFeatureCollection sFCWithCommunityCode, SimpleFeature feat) {
        return CollecTransform.getIntersectingFieldFromSFC((Geometry) feat.getDefaultGeometry(), sFCWithCommunityCode, ParcelSchema.getMinParcelNumberField());
    }

    /**
     * Get the type of community from a Simplefeature (that is most of the time, a parcel or building).
     *
     * @param sfc  Collection of cities. The default field name is <i>armature</i> an can be changed with the
     *             {@link fr.ign.artiscales.pm.parcelFunction.ParcelSchema#setMinParcelCommunityField(String)} method
     * @param feat Feature to get city codes from.
     * @return the type of community in which is the feature.
     */
    public static String getCommunityTypeFromSFC(SimpleFeatureCollection sfc, SimpleFeature feat) {
        return CollecTransform.getIntersectingFieldFromSFC((Geometry) feat.getDefaultGeometry(), sfc, communityTypeFieldName);
    }

    /**
     * Get a list of all the <i>city code numbers</i> of the given collection. The city code field name is <i>DEPCOM</i> by default and can be changed with the method
     * {@link fr.ign.artiscales.pm.parcelFunction.ParcelSchema#setMinParcelCommunityField(String)}. If no code is found, we try to generate it (only for the French Parcels)
     *
     * @param parcels Input {@link SimpleFeatureCollection} of parcels
     * @return The list of every <i>city code numbers</i> from the input parcels.
     */
    public static List<String> getCityCodesOfParcels(SimpleFeatureCollection parcels) {
        List<String> result = new ArrayList<>();
        Arrays.stream(parcels.toArray(new SimpleFeature[0])).forEach(feat -> {
            String code = ((String) feat.getAttribute(ParcelSchema.getMinParcelCommunityField()));
            if (code != null && !code.isEmpty()) {
                if (!result.contains(code)) {
                    result.add(code);
                }
            } else {
                try {
                    String c = FrenchParcelFields.makeDEPCOMCode(feat);
                    if (c != null && !result.contains(c)) {
                        result.add(c);
                    }
                } catch (Exception ignored) {
                }
            }
        });
        return result;
    }

    /**
     * Get the most represented <i>city code numbers</i> of the given collection. The city code field name is <i>DEPCOM</i> by default and can be changed with the method
     * {@link fr.ign.artiscales.pm.parcelFunction.ParcelSchema#setMinParcelCommunityField(String)}. If no code is found, we try to generate it (only for the French Parcels).
     *
     * @param parcels Input {@link SimpleFeatureCollection} of parcels
     * @return the most represented <i>city code numbers</i>
     */
    public static String getCityCodeOfParcels(SimpleFeatureCollection parcels) {
        HashMap<String, Integer> result = new HashMap<>();
        try (SimpleFeatureIterator it = parcels.features()) {
            while (it.hasNext()) {
                SimpleFeature feat = it.next();
                String code = ((String) feat.getAttribute(ParcelSchema.getMinParcelCommunityField()));
                if (code != null && !code.isEmpty()) {
                    result.put(code, result.getOrDefault(code, 1));
                } else {
                    try {
                        String c = FrenchParcelFields.makeDEPCOMCode(feat);
                        if (c != null && !result.containsKey(c)) {
                            result.put(code, result.getOrDefault(code, 1));
                        }
                    } catch (Exception ignored) {
                    }
                }
            }
        } catch (Exception problem) {
            problem.printStackTrace();
        }
        List<Entry<String, Integer>> sorted = result.entrySet().stream().sorted(Map.Entry.comparingByValue()).collect(Collectors.toList());
        return sorted.get(sorted.size() - 1).getKey();
    }

    public static String getCommunityTypeFieldName() {
        return communityTypeFieldName;
    }

    public static void setCommunityTypeFieldName(String armatureCodeName) {
        ParcelAttribute.communityTypeFieldName = armatureCodeName;
    }
}
