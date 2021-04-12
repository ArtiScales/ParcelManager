package fr.ign.artiscales.pm.usecase;

import fr.ign.artiscales.pm.scenario.PMScenario;
import fr.ign.artiscales.pm.scenario.PMStep;

import java.io.File;

public class FigureIterationStep {
    public static void main(String[] args) throws Exception {
        long start = System.currentTimeMillis();
        PMStep.setGENERATEATTRIBUTES(false);
        PMScenario.setReuseSimulatedParcels(false);
        PMScenario pm = new PMScenario(new File("src/main/resources/FigureIterationStep/scenario.json"));
        pm.executeStep();
        PMScenario pmSS = new PMScenario(new File("src/main/resources/FigureIterationStep/scenarioSS.json"));
        pmSS.executeStep();
        System.out.println(System.currentTimeMillis() - start);
    }
}
