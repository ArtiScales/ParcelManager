package fr.ign.artiscales.pm.usecase;

import fr.ign.artiscales.pm.scenario.PMScenario;
import fr.ign.artiscales.pm.scenario.PMStep;

import java.io.File;

public class SmallTest {
    public static void main(String[] args) throws Exception {
        long start = System.currentTimeMillis();
        PMScenario.setSaveIntermediateResult(true);
        PMStep.setDEBUG(true);
        PMScenario pm = new PMScenario(new File("src/main/resources/smallTest/scenario.json"));
        pm.executeStep();
        System.out.println(System.currentTimeMillis() - start);
    }
}
