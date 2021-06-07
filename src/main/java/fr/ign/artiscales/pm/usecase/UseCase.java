package fr.ign.artiscales.pm.usecase;

import fr.ign.artiscales.pm.scenario.PMStep;

/**
 * Super class for complete studies of Parcel Manager
 */
public abstract class UseCase {
    /**
     * If true, will save all the intermediate results in the temporary folder
     */
    private static boolean DEBUG = false;
    private static boolean SAVEINTERMEDIATERESULT = false;

    public static boolean isSAVEINTERMEDIATERESULT() {
        return SAVEINTERMEDIATERESULT;
    }

    public static void setSAVEINTERMEDIATERESULT(boolean SAVEINTERMEDIATERESULT) {
        UseCase.SAVEINTERMEDIATERESULT = SAVEINTERMEDIATERESULT;
        PMStep.setSaveIntermediateResult(SAVEINTERMEDIATERESULT);
    }

    public static boolean isDEBUG() {
        return DEBUG;
    }

    public static void setDEBUG(boolean DEBUG) {
        PMStep.setDEBUG(DEBUG);
        UseCase.DEBUG = DEBUG;
    }
}
