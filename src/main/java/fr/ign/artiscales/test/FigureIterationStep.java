package fr.ign.artiscales.test;

import java.io.File;

import fr.ign.artiscales.scenario.PMScenario;
import fr.ign.artiscales.scenario.PMStep;

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
