package fr.ign.artiscales.fields.french;

import java.util.ArrayList;
import java.util.List;

import org.opengis.feature.simple.SimpleFeature;

import fr.ign.artiscales.fields.GeneralFields;

public class FrenchZoningSchemas {

	/**
	 * Decide the possibility of urbanization of an urban zoning feature according to the French urban planning habits. The <i>zoneNameField<i> field must be <b>U</b> or alike and the
	 * <i>zonePreciseNameField<i> field must be a variation of the <b>UA</b>, <b>UB</b>, <b>UC</b>, <b>UD</b> zones. It also allows the types <b>U</b>, <b>C</b> and <b>ZC</b> that could provide
	 * from Cartes Communales.
	 * 
	 * <i>ZoneGenericNameField<i> and <i>zonePreciseNameField<i> are <i>TYPEZONE</i> and <i>LIBELLE</i> can be changed with the setters from the {@link fr.ign.artiscales.fields.GeneralFields} class.
	 * 
	 * @param zone
	 *            Simple Feature of the input zone
	 * @return true if the urban zone is urbanizable, wrong otherwise.
	 */
	public static boolean isUrbanZoneUsuallyAdmitResidentialConstruction(SimpleFeature zone) {
		if (zone == null) 
			return false;
		String libelle = ((String) zone.getAttribute(GeneralFields.getZonePreciseNameField())).toLowerCase();
		if (normalizeNameFrenchBigZone((String) zone.getAttribute(GeneralFields.getZoneGenericNameField())).equals("U") && libelle != null
				&& (libelle.equals("u") || libelle.startsWith("ua") || libelle.startsWith("ub") || libelle.startsWith("uc")
						|| libelle.startsWith("ud") || libelle.startsWith("c")))
			return true;
		return false;
	}

	/**
	 * Decide the possibility of urbanization of an urban zoning feature according to the French urban planning habits. The <i>ZoneGenericNameField<i> field must be <b>U</b> or alike and the
	 * <i>zonePreciseNameField<i> field must be a variation of the <b>UA</b>, <b>UB</b>, <b>UC</b>, <b>UD</b> zones. It also allows the types <b>U</b>, <b>C</b> and <b>ZC</b> that could provide
	 * from Cartes Communales.
	 * 
	 * <i>ZoneGenericNameField<i> and <i>zonePreciseNameField<i> are <i>TYPEZONE</i> and <i>LIBELLE</i> can be changed with the setters from the {@link fr.ign.artiscales.fields.GeneralFields} class.
	 * 
	 * @param zone
	 *            Simple Feature of the input zone
	 * @return true if the urban zone is urbanizable, wrong otherwise.
	 */
	public static boolean isZoneUsuallyAdmitResidentialConstruction(SimpleFeature zone) {
		String libelle = ((String) zone.getAttribute(GeneralFields.getZonePreciseNameField())).toLowerCase();
		if (isUrbanZoneUsuallyAdmitResidentialConstruction(zone) && normalizeNameFrenchBigZone((String) zone.getAttribute(GeneralFields.getZoneGenericNameField())).equals("AU") 
				&& (libelle.startsWith("1") || libelle.startsWith("au1")))
			return true;
		return false;
	}

	/**
	 * Translate some big zone labels coming from different urban documents to a normalized one. If no match has been found, we return the input value.
	 * 
	 * @param nameZone
	 * @return The normalized name.
	 */
	public static String normalizeNameFrenchBigZone(String nameZone) {
		switch (nameZone) {
		case "":
			return "";
		case "U":
		case "ZC":
		case "C":
			return "U";
		case "AU":
		case "TBU":
			return "AU";
		case "N":
		case "A":
		case "NC":
		case "ZNC":
			return "NC";
		}
		return nameZone;
	}

	/**
	 * Gives the usual names given to the three types of zones in a french context. If null or unknown input, return every known answers.
	 * 
	 * @param zone
	 *            normalized type of zone
	 * @return a list of the big zones that the normalize type of zone can have
	 */
	public static List<String> getUsualNames(String zone) {
		List<String> listZones = new ArrayList<>();
		switch (zone) {
		case "U":
			listZones.add("U");
			listZones.add("ZC");
			listZones.add("C");
			break;
		case "AU":
			listZones.add("AU");
			listZones.add("TBC");
			break;
		case "NC":
			listZones.add("A");
			listZones.add("N");
			listZones.add("NC");
			listZones.add("ZNC");
			break;
		default:
			listZones.add("U");
			listZones.add("ZC");
			listZones.add("C");
			listZones.add("AU");
			listZones.add("TBC");
			listZones.add("A");
			listZones.add("N");
			listZones.add("NC");
			listZones.add("ZNC");
		}
		return listZones;
	}	
}
