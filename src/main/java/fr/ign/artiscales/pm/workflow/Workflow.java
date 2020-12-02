package fr.ign.artiscales.pm.workflow;

import org.opengis.feature.simple.SimpleFeature;

public abstract class Workflow {
	/**
	 * The process used to divide the parcels
	 */
	public static String PROCESS = "OBB";
	/**
	 * If true, will save a Geopackage containing only the simulated parcels in the temporary folder.
	 */
	public static boolean SAVEINTERMEDIATERESULT = false;
	/**
	 * If true, overwrite the output saved Geopackages. If false, append the simulated parcels to a potential already existing Geopackage.
	 */
	public static boolean OVERWRITEGEOPACKAGE = true;
	/**
	 * If true, will save all the intermediate results in the temporary folder
	 */
	public static boolean DEBUG = false;
	
	public abstract String makeNewSection(String section) ;
	
	public abstract boolean isNewSection(SimpleFeature feat);
}
