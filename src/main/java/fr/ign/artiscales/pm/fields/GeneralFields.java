package fr.ign.artiscales.pm.fields;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import fr.ign.artiscales.tools.geoToolsFunctions.vectors.collec.CollecMgmt;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.feature.DefaultFeatureCollection;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.opengis.feature.simple.SimpleFeature;

import fr.ign.artiscales.pm.fields.french.FrenchParcelFields;
import fr.ign.artiscales.pm.fields.french.FrenchParcelSchemas;
import fr.ign.artiscales.pm.fields.french.FrenchZoningSchemas;
import fr.ign.artiscales.pm.parcelFunction.MarkParcelAttributeFromPosition;
import fr.ign.artiscales.pm.parcelFunction.ParcelAttribute;
import fr.ign.artiscales.pm.parcelFunction.ParcelSchema;
import fr.ign.artiscales.tools.geoToolsFunctions.Attribute;
import fr.ign.artiscales.tools.geoToolsFunctions.Schemas;

public class GeneralFields {

	/**
	 * Represent the general permission of the zones. Its default name is '<b>TYPEZONE</b>' and can be of three different types:
	 * <ul>
	 * <li><b>U</b>: Already urbanized land,</li>
	 * <li><b>AU</b>: Not urbanized land but open to new developments,</li>
	 * <li><b>N</b>: Not urbanized land and not open to new developments.</li>
	 * </ul>
	 */
	static String zoneGenericNameField = "TYPEZONE";
	/**
	 * Precise special rules on a zone. Its default name is '<b>LIBELLE</b>'
	 */
	static String zonePreciseNameField = "LIBELLE";
	/**
	 * Type of parcels. Mostly defines their attributes the call of the schemas for re-assignation. Its default value is '<b>french</b>'.
	 */
	static String parcelFieldType = "french";
	static String zoneCommunityCode = "DEPCOM";

	/**
	 * This method returns the parcels of a given collection that have been simulated. The type of fields must be precise and that can change the specific rule. In the case of
	 * French Parcels, it selects the parcels if the length of the filed value for the <i>SECTION</i> information is upper than 2 (French Parcel have a two letters section, and
	 * Parcel Manager creates longer section names) Other methods can be set to determine if a parcel has been simulated.
	 * 
	 * @param sfc
	 *            Parcel {@link SimpleFeatureCollection} to sort
	 * @return The parcel {@link SimpleFeatureCollection} with only the simulated parcels
	 * @throws IOException
	 */
	public static SimpleFeatureCollection getParcelWithSimulatedFileds(SimpleFeatureCollection sfc) throws IOException {
		DefaultFeatureCollection result = new DefaultFeatureCollection();
		Arrays.stream(sfc.toArray(new SimpleFeature[0])).forEach(parcel -> {
			if (parcelFieldType.equals("french"))
				if (isParcelLikeFrenchHasSimulatedFileds(parcel))
					result.add(parcel);
		});
		return result.collection();
	}

	/**
	 * Get a list of all the {@link #zonePreciseNameField} values of the given collection. The city code field name is <i>LIBELLE<i> by default and can be changed with the method
	 * {@link #setZonePreciseNameField(String)}.
	 * 
	 * @param zonings
	 *            Input {@link SimpleFeatureCollection} of zoning plan
	 * @return The list of every <i>city code numbers</i> from the input parcels.
	 */
	public static List<String> getPreciseZoningTypes(SimpleFeatureCollection zonings) {
		List<String> result = new ArrayList<>();
		Arrays.stream(zonings.toArray(new SimpleFeature[0])).forEach(feat -> {
			String code = ((String) feat.getAttribute(zonePreciseNameField));
			if (code != null && !code.isEmpty() && !result.contains(code))
				result.add(code);
		});
		return result;
	}

	/**
	 * Get a list of all the {@link #zoneGenericNameField} values of the given collection. The city code field name is <i>TYPEZONE<i> by default and can be changed with the method
	 * {@link #setZoneGenericNameField(String)}.
	 * 
	 * @param zonings
	 *            Input {@link SimpleFeatureCollection} of zoning plan
	 * @return The list of every <i>city code numbers</i> from the input parcels.
	 */
	public static List<String> getGenericZoningTypes(SimpleFeatureCollection zonings) {
		List<String> result = new ArrayList<>();
		Arrays.stream(zonings.toArray(new SimpleFeature[0])).forEach(feat -> {
			String code = ((String) feat.getAttribute(zoneGenericNameField));
			if (code != null && !code.isEmpty() && !result.contains(code))
				result.add(code);
		});
		return result;
	}

	/**
	 * Add a "CODE" field for every parcels of a {@link SimpleFeatureCollection}. Only french solution implemented yet.
	 * 
	 * @param parcels
	 *            {@link SimpleFeatureCollection} of parcels.
	 * @return The collection with the "CODE" field added.
	 */
	public static SimpleFeatureCollection addParcelCode(SimpleFeatureCollection parcels) {
		switch (parcelFieldType) {
		case "french":
			return FrenchParcelFields.addFrenchParcelCode(parcels);
		}
		System.out.println("No parcel field type defined for GeneralFields.addParcelCode. Return null");
		return null;
	}

	/**
	 * Add the {@link #zoneCommunityCode} field for every parcels of a {@link SimpleFeatureCollection}. Only french solution implemented yet.
	 * 
	 * @param parcels
	 *            {@link SimpleFeatureCollection} of parcels.
	 * @return The collection with the "CODE" field added.
	 */
	public static SimpleFeatureCollection addCommunityCode(SimpleFeatureCollection parcels) {
		switch (parcelFieldType) {
		case "french":
			return FrenchParcelFields.addCommunityCode(parcels);
		}
		System.out.println("No parcel field type defined for GeneralFields.addCommunityCode. Return null");
		return null;
	}

	/**
	 * Trivial method to get the genericZone list of a type
	 * 
	 * @param genericZone
	 *            the given generic zone
	 * @return the given list( as final)
	 */
	public static List<String> getGenericZoneUsualNames(String genericZone) {
		switch (parcelFieldType) {
		case "french":
			return FrenchZoningSchemas.getUsualNames(genericZone);
		}
		System.out.println("No parcel field type defined for GeneralFields.getGenericZoneUsualNames. Return null");
		return null;
	}

	/**
	 * Trivial method to get the genericZone list of a type
	 * 
	 * @param genericZone
	 *            the given generic zone
	 * @return the given list( as final)
	 */
	public static String getGenericZoneEnglishName(String genericZone) {
		switch (parcelFieldType) {
		case "french":
			return FrenchZoningSchemas.getTranslatedName(genericZone);
		}
		System.out.println("No parcel field type defined for GeneralFields.getGenericZoneUsualNames. Return null");
		return null;
	}

	/**
	 * This method allows to determine if a parcel has been simulated regarding to different parcel types of nomenclature. For now, only the French verification but other methods
	 * can be set to determine if a parcel has been simulated.
	 * 
	 * @param feature
	 *            {@link SimpleFeature} input parcel
	 * @return True if the parcel section looks like it has been simulated.
	 */
	public static boolean isParcelHasSimulatedFields(SimpleFeature feature) {
		switch (parcelFieldType) {
		case "french":
			return isParcelLikeFrenchHasSimulatedFileds(feature);
		case "every":
			return true;
		default:
			System.out.println(
					"isParcelHasSimulatedFields: unknown method because of an unknown parcel nomenclature (" + parcelFieldType + "). Returned false");
			return false;
		}
	}

	/**
	 * This method allows to determine if a parcel has been simulated. It looks if the length of filed value for the <i>SECTION</i> information is upper than 2 (French Parcel have
	 * a two letters section, and Parcel Manager creates longer section names). Other methods can be set to determine if a parcel has been simulated.
	 * 
	 * @param feature
	 *            {@link SimpleFeature} input parcel
	 * @return True if the parcel section looks like it has been simulated.
	 */
	public static boolean isParcelLikeFrenchHasSimulatedFileds(SimpleFeature feature) {
		return (feature.getAttribute(ParcelSchema.getMinParcelSectionField())) != null
				&& ((String) feature.getAttribute(ParcelSchema.getMinParcelSectionField())).length() > 3;
	}

	public static SimpleFeatureBuilder getSFBZoning() {
		switch (parcelFieldType) {
		case "french":
			return FrenchParcelSchemas.getSFBFrenchZoning();
		default:
			System.out.println("getSFBZoning: unknown method because of an unknown parcel nomenclature (" + parcelFieldType
					+ "). Returned an empty SimpleFeatureBuilder");
			return Schemas.getBasicSchema("zoning");
		}
	}

	public static String getZoneGenericNameField() {
		return zoneGenericNameField;
	}

	public static void setZoneGenericNameField(String zoneNameField) {
		GeneralFields.zoneGenericNameField = zoneNameField;
	}

	public static String getZonePreciseNameField() {
		return zonePreciseNameField;
	}

	public static void setZonePreciseNameField(String zonePreciseNameField) {
		GeneralFields.zonePreciseNameField = zonePreciseNameField;
	}

	public static String getParcelFieldType() {
		return parcelFieldType;
	}

	public static void setParcelFieldType(String fieldType) {
		GeneralFields.parcelFieldType = fieldType;
	}

	public static String getZoneCommunityCode() {
		return zoneCommunityCode;
	}

	public static void setZoneCommunityCode(String zoneCommunityCode) {
		GeneralFields.zoneCommunityCode = zoneCommunityCode;
	}

	/**
	 * Change a {@link SimpleFeatureCollection} of any kind to follow the minimum schema for Parcel Manager (see method {@link ParcelSchema#getSFBMinParcel()} with the help of an
	 * extra {@link SimpleFeatureCollection}.
	 * 
	 * @param sfc
	 *            input {@link SimpleFeatureCollection} to transform.
	 * @param sfcWithInfo
	 *            {@link SimpleFeatureCollection} containing the interesting attribute informations.
	 * @return A {@link SimpleFeatureCollection} with attributes following the minimal schema
	 * @throws IOException
	 */
	public static SimpleFeatureCollection transformSFCToMinParcel(SimpleFeatureCollection sfc, SimpleFeatureCollection sfcWithInfo)
			throws IOException {
		DefaultFeatureCollection result = new DefaultFeatureCollection();
		SimpleFeatureBuilder builder = Schemas.getSFBSchemaWithMultiPolygon(ParcelSchema.getSFBMinParcel().getFeatureType());
		try (SimpleFeatureIterator it = sfc.features()) {
			while (it.hasNext()) {
				SimpleFeature feat = it.next();
				builder.set(builder.getFeatureType().getGeometryDescriptor().getLocalName(), feat.getDefaultGeometry());
				builder.set(ParcelSchema.getMinParcelCommunityField(), ParcelAttribute.getCommunityCodeFromSFC(sfcWithInfo, feat));
				builder.set(ParcelSchema.getMinParcelSectionField(), ParcelAttribute.getSectionCodeFromSFC(sfcWithInfo, feat));
				builder.set(ParcelSchema.getMinParcelNumberField(), ParcelAttribute.getNumberCodeFromSFC(sfcWithInfo, feat));
				result.add(builder.buildFeature(Attribute.makeUniqueId()));
			}
		} catch (Exception problem) {
			problem.printStackTrace();
		}
		return result.collection();
	}

	/**
	 * Change a {@link SimpleFeatureCollection} of any kind to follow the minimum schema for Parcel Manager (see method {@link ParcelSchema#getSFBMinParcel()}.
	 * 
	 * @param parcels
	 *            input {@link SimpleFeatureCollection}
	 * @return A {@link SimpleFeatureCollection} with attributes following the minimal schema
	 */
	public static SimpleFeatureCollection transformSFCToMinParcel(SimpleFeatureCollection parcels) {
		return transformSFCToMinParcel(parcels, false);
	}

	/**
	 * Change a {@link SimpleFeatureCollection} of any kind to follow the minimum schema for Parcel Manager (see method {@link ParcelSchema#getSFBMinParcel()}. Could be the version
	 * with the mark field or not.
	 * 
	 * @param parcels
	 *            input {@link SimpleFeatureCollection}
	 * @param hasMark
	 *            If the {@link MarkParcelAttributeFromPosition#getMarkFieldName()} should be kept or not.
	 * @return A {@link SimpleFeatureCollection} with attributes following the minimal schema
	 */
	public static SimpleFeatureCollection transformSFCToMinParcel(SimpleFeatureCollection parcels, boolean hasMark) {
		DefaultFeatureCollection result = new DefaultFeatureCollection();
		boolean split = CollecMgmt.isCollecContainsAttribute(parcels, MarkParcelAttributeFromPosition.getMarkFieldName()) && hasMark;
		Arrays.stream(parcels.toArray(new SimpleFeature[0])).forEach(feat -> {
			SimpleFeatureBuilder sfb;
			if (split)
				sfb = ParcelSchema.setSFBMinParcelWithFeat(feat, ParcelSchema.getSFBMinParcelSplit().getFeatureType());
			else
				sfb = ParcelSchema.setSFBMinParcelWithFeat(feat, ParcelSchema.getSFBMinParcel().getFeatureType());
			result.add(sfb.buildFeature(Attribute.makeUniqueId()));
		});
		return result;
	}
}
