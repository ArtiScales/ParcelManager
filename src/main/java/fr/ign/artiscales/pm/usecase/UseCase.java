package fr.ign.artiscales.pm.usecase;

import fr.ign.artiscales.pm.scenario.PMStep;
import fr.ign.artiscales.pm.workflow.Workflow;

public abstract class UseCase {
    /**
     * If true, will save all the intermediate results in the temporary folder
     */
    private static boolean DEBUG = false;

    public static boolean isDEBUG() {
        return DEBUG;
    }

    public static void setDEBUG(boolean DEBUG) {
        PMStep.setDEBUG(DEBUG);
        UseCase.DEBUG = DEBUG;
    }
}
