package fr.ign.artiscales.workflow;

import java.io.File;

import fr.ign.artiscales.scenario.PMScenario;
import fr.ign.artiscales.scenario.PMStep;

public class SmallTest {
	public static void main(String[] args) throws Exception {
		long start = System.currentTimeMillis();
		PMScenario.setSaveIntermediateResult(true);
		PMStep.setDEBUG(true);
		PMScenario pm = new PMScenario(new File("src/main/resources/smallTest/scenario.json"), new File("/tmp/"));
		pm.executeStep();
		System.out.println(System.currentTimeMillis() - start);
	}
}
