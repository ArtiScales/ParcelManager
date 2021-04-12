package fr.ign.artiscales.pm.workflow;

import fr.ign.artiscales.pm.decomposition.OBBBlockDecomposition;
import fr.ign.artiscales.pm.decomposition.TopologicalStraightSkeletonParcelDecomposition;
import org.opengis.feature.simple.SimpleFeature;

public abstract class Workflow {
    /**
     * The process used to divide the parcels
     */
    public static String PROCESS = "OBB";
    /**
     * If true, will save a Geopackage containing only the simulated parcels in the temporary folder.
     */
    private static boolean SAVEINTERMEDIATERESULT = false;

    public static boolean isSAVEINTERMEDIATERESULT() {
        return SAVEINTERMEDIATERESULT;
    }

    public static void setSAVEINTERMEDIATERESULT(boolean SAVEINTERMEDIATERESULT) {
        TopologicalStraightSkeletonParcelDecomposition.setSAVEINTERMEDIATERESULT(SAVEINTERMEDIATERESULT);
        Workflow.SAVEINTERMEDIATERESULT = SAVEINTERMEDIATERESULT;
    }

    public static boolean isDEBUG() {
        return DEBUG;
    }

    public static void setDEBUG(boolean DEBUG) {
        TopologicalStraightSkeletonParcelDecomposition.setDEBUG(DEBUG);
        Workflow.DEBUG = DEBUG;
    }

    /**
     * If true, overwrite the output saved Geopackages. If false, append the simulated parcels to a potential already existing Geopackage.
     */
    public static boolean OVERWRITEGEOPACKAGE = true;
    /**
     * If true, will save all the intermediate results in the temporary folder
     */
    private static boolean DEBUG = false;

    public abstract String makeNewSection(String section);

    public abstract boolean isNewSection(SimpleFeature feat);
}
