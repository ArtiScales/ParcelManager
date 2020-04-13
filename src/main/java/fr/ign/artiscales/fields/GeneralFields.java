package fr.ign.artiscales.fields;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.feature.DefaultFeatureCollection;
import org.opengis.feature.simple.SimpleFeature;

import fr.ign.artiscales.fields.french.FrenchZoningSchemas;
import fr.ign.artiscales.parcelFunction.ParcelSchema;

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
	 * @param genericZone the given generic zone
	 * @return the given list( as final)
	 */
	public static List<String> getGenericZoneUsualNames(String genericZone) {
		List<String> genericZoneUsualNames = new ArrayList<String>();
		switch (GeneralFields.getParcelFieldType()) {
		case "french":
			genericZoneUsualNames = FrenchZoningSchemas.getUsualNames(genericZone);
			break;
		}
		return genericZoneUsualNames;
	}

	
	/**
	 * This method allows to determine if a parcel has been simulated. It looks if the length of filed value for the <i>SECTION</i> information is upper than 2 (French Parcel have
	 * a two letters section, and Parcel Manager creates longer section names). Other methods can be set to determine if a parcel has been simulated.
	 * 
	 * @param feature
	 *            : {@link SimpleFeature} parcel
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
}
