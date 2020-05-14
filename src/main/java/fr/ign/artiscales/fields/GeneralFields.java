package fr.ign.artiscales.fields;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.feature.DefaultFeatureCollection;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.NoSuchAuthorityCodeException;

import fr.ign.artiscales.fields.french.FrenchZoningSchemas;
import fr.ign.artiscales.parcelFunction.MarkParcelAttributeFromPosition;
import fr.ign.artiscales.parcelFunction.ParcelSchema;
import fr.ign.cogit.geoToolsFunctions.Attribute;
import fr.ign.cogit.geoToolsFunctions.vectors.Collec;

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
	 * Type of parcels. Mostly defines their attributes the call of the schemas for re-assignation. Its default value is 'french'.
	 */
	static String parcelFieldType = "french";
	static String zoneCommunityCode = "DEPCOM";
	/**
	 * This method returns the parcels of a given collection that have been simulated. The type of fields must be precise and that can change the specific rule. In the case of
	 * French Parcels, it selects the parcels if the length of the filed value for the <i>SECTION</i> information is upper than 2 (French Parcel have a two letters section, and
	 * Parcel Manager creates longer section names) Other methods can be set to determine if a parcel has been simulated.
	 * 
	 * @param sfc
	 *            Parcel collection to sort
	 * @return The parcel {@link SimpleFeatureCollection} with only the simulated parcels
	 * @throws IOException
	 */
	public static SimpleFeatureCollection getParcelWithSimulatedFileds(SimpleFeatureCollection sfc) throws IOException  {
		DefaultFeatureCollection result = new DefaultFeatureCollection();
		Arrays.stream(sfc.toArray(new SimpleFeature[0])).forEach(parcel -> {
			if (parcelFieldType.equals("french")) {
				if (isParcelLikeFrenchHasSimulatedFileds(parcel)) {
					result.add(parcel);
				}
			}
		});
		return result.collection();
	}
	
	/**
	 * Trivial method to get the genericZone list of a type
	 * 
	 * @param genericZone
	 *            the given generic zone
	 * @return the given list( as final)
	 */
	public static List<String> getGenericZoneUsualNames(String genericZone) {
		List<String> genericZoneUsualNames = new ArrayList<String>();
		switch (parcelFieldType) {
		case "french":
			genericZoneUsualNames = FrenchZoningSchemas.getUsualNames(genericZone);
			break;
		}
		return genericZoneUsualNames;
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
		if (((String) feature.getAttribute(ParcelSchema.getMinParcelSectionField())) != null
				&& ((String) feature.getAttribute(ParcelSchema.getMinParcelSectionField())).length() > 3) {
			return true;
		}
		return false;
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
			boolean split = Collec.isCollecContainsAttribute(parcels, MarkParcelAttributeFromPosition.getMarkFieldName()) && hasMark ;
		Arrays.stream(parcels.toArray(new SimpleFeature[0])).forEach(feat -> {
			try {
				SimpleFeatureBuilder sfb ;
				if (split)
					sfb = ParcelSchema.setSFBMinParcelWithFeat(feat, ParcelSchema.getSFBMinParcelSplit().getFeatureType());
				else 
					sfb = ParcelSchema.setSFBMinParcelWithFeat(feat, ParcelSchema.getSFBMinParcel().getFeatureType());
				result.add(sfb.buildFeature(Attribute.makeUniqueId()));
			} catch (NoSuchAuthorityCodeException e) {
				e.printStackTrace();
			} catch (FactoryException e) {
				e.printStackTrace();
			}
		});
		return result;
	}
}
