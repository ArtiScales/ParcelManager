package fr.ign.artiscales.pm.workflow;

import fr.ign.artiscales.pm.division.Division;
import fr.ign.artiscales.pm.division.DivisionType;
import fr.ign.artiscales.pm.division.StraightSkeletonDivision;
import fr.ign.artiscales.pm.parcelFunction.ParcelSchema;
import fr.ign.artiscales.tools.geoToolsFunctions.Schemas;
import org.apache.commons.math3.random.MersenneTwister;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;

/**
 * Class to implement in order to construct a new Workflow
 */
public abstract class Workflow {
    /**
     * The process used to divide the parcels
     */
    public static DivisionType PROCESS = DivisionType.OBB;
    /**
     * If true, overwrite the output saved Geopackages. If false, append the simulated parcels to a potential already existing Geopackage.
     */
    public static boolean OVERWRITEGEOPACKAGE = true;
    public static MersenneTwister random = new MersenneTwister();
    /**
     * If true, will save a Geopackage containing only the simulated parcels in the temporary folder.
     */
    private static boolean SAVEINTERMEDIATERESULT = false;
    /**
     * If true, will save all the intermediate results in the temporary folder
     */
    private static boolean DEBUG = false;

    public static boolean isSAVEINTERMEDIATERESULT() {
        return SAVEINTERMEDIATERESULT;
    }

    public static void setSAVEINTERMEDIATERESULT(boolean SAVEINTERMEDIATERESULT) {
        StraightSkeletonDivision.setSAVEINTERMEDIATERESULT(SAVEINTERMEDIATERESULT);
        Workflow.SAVEINTERMEDIATERESULT = SAVEINTERMEDIATERESULT;
    }

    public static boolean isDEBUG() {
        return DEBUG;
    }

    public static void setDEBUG(boolean DEBUG) {
        Division.setDEBUG(DEBUG);
        Workflow.DEBUG = DEBUG;
    }

    /**
     * Check if every fields required for a workflow task are present. If not, it will write something in the console.
     *
     * @param parcelInput schema of the input collection.
     */
    public static void checkFields(SimpleFeatureType parcelInput) {
        if (!Schemas.isSchemaContainsAttribute(parcelInput, ParcelSchema.getParcelCommunityField()))
            System.out.println("Parcel collection doesn't contain the needed Community Field. Set a " + ParcelSchema.getParcelCommunityField() + " field or change the default name");
        if (!Schemas.isSchemaContainsAttribute(parcelInput, ParcelSchema.getParcelNumberField()))
            System.out.println("Parcel collection doesn't contain the needed Number Field.  Set a " + ParcelSchema.getParcelNumberField() + " field or change the default name");
        if (!Schemas.isSchemaContainsAttribute(parcelInput, ParcelSchema.getParcelSectionField()))
            System.out.println("Parcel collection doesn't contain the needed Section Field.  Set a " + ParcelSchema.getParcelSectionField() + " field or change the default name");
    }

    public static void setSeed(long seed) {
        random = new MersenneTwister(seed);
        Division.setSeed(seed);
    }

    public abstract String makeNewSection(String section);

    public abstract boolean isNewSection(SimpleFeature feat);


}