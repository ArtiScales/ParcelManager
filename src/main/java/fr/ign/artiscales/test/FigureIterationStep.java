package fr.ign.artiscales.test;

import java.io.File;

import fr.ign.artiscales.fields.GeneralFields;
import fr.ign.artiscales.scenario.PMScenario;

public class FigureIterationStep {
	public static void main(String[] args) throws Exception {
		GeneralFields.setParcelFieldType("");
		PMScenario.setReuseSimulatedParcels(false);
		PMScenario pm = new PMScenario(new File("src/main/resources/FigureIterationStep/scenario.json"), new File("/tmp/"));
		pm.executeStep();
	}
}
