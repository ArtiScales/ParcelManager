package fr.ign.artiscales.pm.workflow;

import java.io.File;

import fr.ign.artiscales.pm.scenario.PMScenario;
import fr.ign.artiscales.pm.scenario.PMStep;

public class FigureIterationStep {
	public static void main(String[] args) throws Exception {
		long start = System.currentTimeMillis();
		PMStep.setGENERATEATTRIBUTES(false);
		PMScenario.setReuseSimulatedParcels(false);
		PMScenario pm = new PMScenario(new File("src/main/resources/FigureIterationStep/scenario.json"), new File("/tmp/"));
		pm.executeStep();
		System.out.println(System.currentTimeMillis() - start);
	}
}
