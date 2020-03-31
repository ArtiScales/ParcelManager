package fr.ign.artiscales.fields;

import org.opengis.feature.simple.SimpleFeature;

public class FrenchZoningFields {

	public static boolean isZoneUsuallyAdmitResidentialConstruction(SimpleFeature feat) {
		try {
			String libelle = ((String) feat.getAttribute("LIBELLE")).toLowerCase();
			if (feat.getAttribute("TYPEZONE").equals("U") && (libelle.startsWith("ua") || libelle.startsWith("ub") || libelle.startsWith("uc"))) {
				return true;
			}
		} catch (Exception e) {
			System.out.println(e);
		}
		return false;
	}

}
