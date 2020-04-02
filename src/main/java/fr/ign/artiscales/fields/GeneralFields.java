package fr.ign.artiscales.fields;

import java.io.IOException;
import java.util.Arrays;

import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.feature.DefaultFeatureCollection;
import org.opengis.feature.simple.SimpleFeature;

import fr.ign.artiscales.parcelFunction.ParcelSchema;

public class GeneralFields {

	static String zoneGenericNameField = "TYPEZONE";
	static String zonePreciseNameField = "LIBELLE";

	/**
	 * This method returns the parcels of a given collection that have been simulated.
	 * It selects the parcels if the length of the filed value for the <i>SECTION</i> information is upper than 2 (French Parcel have a two letters section, and Parcel Manager creates longer section names)
	 * Other methods can be set to determine if a parcel has been simulated.
	 * @param sfc
	 *            Parcel collection to sort
	 * @return The parcel {@link SimpleFeatureCollection} with only the simulated parcels
	 * @throws IOException
	 */
	public static SimpleFeatureCollection getParcelLikeFrenchWithSimulatedFileds(SimpleFeatureCollection sfc) throws IOException  {
		DefaultFeatureCollection result = new DefaultFeatureCollection();
		Arrays.stream(sfc.toArray(new SimpleFeature[0])).forEach(parcel -> {
			if (isParcelLikeFrenchHasSimulatedFileds(parcel)) {
					result.add(parcel);
			}
		});
		return result.collection();
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
}
