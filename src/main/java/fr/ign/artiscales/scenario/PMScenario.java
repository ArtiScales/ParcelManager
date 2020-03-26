package fr.ign.artiscales.scenario;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

public class PMScenario {

	private File zoningFile, buildingFile, roadFile, polygonIntersection, predicateFile, parcelFile, ilotFile, profileFolder, tmpFolder, outFolder;

	private List<PMStep> stepList = new ArrayList<PMStep>();

	boolean fileSet = false;
	
	static boolean SAVEINTERMEDIATERESULT = false; 

//	public static void main(String[] args) throws Exception {
//		PMScenario pm = new PMScenario(
//				new File("/home/thema/Documents/MC/workspace/ParcelManager/src/main/resources/testData/jsonEx.json"),
//				new File("/tmp/"));
//		pm.executeStep();
//	}

//	public PMScenario(File jSON) {
//		return new PMScenario(jSON, new File("/tmp"));
//	}

	public PMScenario(File jSON, File tmpfolder) throws Exception {
		tmpFolder = tmpfolder;
		PMStep.setSaveIntermediateResult(SAVEINTERMEDIATERESULT);
		JsonFactory factory = new JsonFactory();
		JsonParser parser = factory.createParser(jSON);
		JsonToken token = parser.nextToken();
		
		while (!parser.isClosed()) {
			token = parser.nextToken();
//			shortcut if every data is in the same folder
			if (token == JsonToken.FIELD_NAME && "rootfile".equals(parser.getCurrentName()) && !fileSet) {
				fileSet = true;
				token = parser.nextToken();
				if (token == JsonToken.VALUE_STRING) {
					String rootFolder = parser.getText();
					zoningFile = new File(rootFolder, "zoning.shp");
					buildingFile = new File(rootFolder, "building.shp");
					roadFile = new File(rootFolder, "road.shp");
					polygonIntersection = new File(rootFolder, "polygonIntersection.shp");
					predicateFile = new File(rootFolder, "predicate.csv");
					parcelFile = new File(rootFolder, "parcelle.shp");
					ilotFile = new File(rootFolder, "ilot.shp");
					profileFolder = new File(rootFolder, "ilot.shp");
				}
			}

			if (token == JsonToken.FIELD_NAME && "steps".equals(parser.getCurrentName())) {
				token = parser.nextToken();
				String goal = "";
				String parcelProcess = "";
				String zone = "";
				String communityNumber = "";
				String communityType = "";
				String buildingType = "";

				while (token != JsonToken.END_ARRAY) {
					token = parser.nextToken();

					// must i recreate a json object ? or can I map this object directly into a new java object? Maybe, but tired of searching
//					System.out.println(token + " - " + parser.getCurrentName());

					if (token == JsonToken.FIELD_NAME && parser.getCurrentName().equals("goal")) {
						token = parser.nextToken();
						if (token == JsonToken.VALUE_STRING) {
							goal = parser.getText();
						}
					}
					if (token == JsonToken.FIELD_NAME && parser.getCurrentName().equals("parcelProcess")) {
						token = parser.nextToken();
						if (token == JsonToken.VALUE_STRING) {
							parcelProcess = parser.getText();
						}
					}
					if (token == JsonToken.FIELD_NAME && parser.getCurrentName().equals("zone")) {
						token = parser.nextToken();
						if (token == JsonToken.VALUE_STRING) {
							zone = parser.getText();
						}
					}
					if (token == JsonToken.FIELD_NAME && parser.getCurrentName().equals("communityNumber")) {
						token = parser.nextToken();
						if (token == JsonToken.VALUE_STRING) {
							communityNumber = parser.getText();
						}
					}
					if (token == JsonToken.FIELD_NAME && parser.getCurrentName().equals("communityType")) {
						token = parser.nextToken();
						if (token == JsonToken.VALUE_STRING) {
							communityType = parser.getText();
						}
					}
					if (token == JsonToken.FIELD_NAME && parser.getCurrentName().equals("buildingType")) {
						token = parser.nextToken();
						if (token == JsonToken.VALUE_STRING) {
							buildingType = parser.getText();
						}
					}
					if (token == JsonToken.END_OBJECT) {
						List<PMStep> list = getStepList();
						PMStep step = new PMStep(goal, parcelProcess, zone, communityNumber, communityType,
								buildingType);
						list.add(step);
						setStepList(list);
					}
				}
			}

			if (token == JsonToken.FIELD_NAME && "zoningFile".equals(parser.getCurrentName())) {
				token = parser.nextToken();
				if (token == JsonToken.VALUE_STRING) {
					zoningFile = new File(parser.getText());
					fileSet = true;
				}
			}
			if (token == JsonToken.FIELD_NAME && "roadFile".equals(parser.getCurrentName())) {
				token = parser.nextToken();
				if (token == JsonToken.VALUE_STRING) {
					roadFile = new File(parser.getText());
					fileSet = true;
				}
			}
			if (token == JsonToken.FIELD_NAME && "buildingFile".equals(parser.getCurrentName())) {
				token = parser.nextToken();
				if (token == JsonToken.VALUE_STRING) {
					buildingFile = new File(parser.getText());
					fileSet = true;
				}
			}
			if (token == JsonToken.FIELD_NAME && "polygonIntersection".equals(parser.getCurrentName())) {
				token = parser.nextToken();
				if (token == JsonToken.VALUE_STRING) {
					polygonIntersection = new File(parser.getText());
					fileSet = true;
				}
			}
			if (token == JsonToken.FIELD_NAME && "predicateFile".equals(parser.getCurrentName())) {
				token = parser.nextToken();
				if (token == JsonToken.VALUE_STRING) {
					predicateFile = new File(parser.getText());
					fileSet = true;
				}
			}
			if (token == JsonToken.FIELD_NAME && "parcelFile".equals(parser.getCurrentName())) {
				token = parser.nextToken();
				if (token == JsonToken.VALUE_STRING) {
					parcelFile = new File(parser.getText());
					fileSet = true;
				}
			}
			if (token == JsonToken.FIELD_NAME && "ilotFile".equals(parser.getCurrentName())) {
				token = parser.nextToken();
				if (token == JsonToken.VALUE_STRING) {
					ilotFile = new File(parser.getText());
					fileSet = true;
				}
			}
			if (token == JsonToken.FIELD_NAME && "profileFolder".equals(parser.getCurrentName())) {
				token = parser.nextToken();
				if (token == JsonToken.VALUE_STRING) {
					profileFolder = new File(parser.getText());
					fileSet = true;
				}
			}
			if (token == JsonToken.FIELD_NAME && "outFolder".equals(parser.getCurrentName())) {
				token = parser.nextToken();
				if (token == JsonToken.VALUE_STRING) {
					outFolder = new File(parser.getText());
					fileSet = true;
				}
			}
		}
		parser.close();
		PMStep.setFiles(parcelFile, ilotFile, zoningFile, tmpFolder, buildingFile, roadFile, predicateFile, polygonIntersection, outFolder,
				profileFolder);
	}

	public void executeStep() throws Exception {
		for (PMStep pmstep : getStepList()) {
			System.out.println("try " + pmstep);
			PMStep.setParcel(pmstep.execute());
		}
	}

	public List<PMStep> getStepList() {
		return stepList;
	}

	public void setStepList(List<PMStep> stepList) {
		this.stepList = stepList;
	}

	@Override
	public String toString() {
		return "PMScenario [zoningFile=" + zoningFile + ", buildingFile=" + buildingFile + ", roadFile=" + roadFile + ", polygonIntersection="
				+ polygonIntersection + ", predicateFile=" + predicateFile + ", parcelFile=" + parcelFile + ", ilotFile=" + ilotFile + ", tmpFolder="
				+ tmpFolder + ", outFolder=" + outFolder + ", stepList=" + stepList + ", fileSet=" + fileSet + ", profileFolder=" + profileFolder
				+ "]";
	}

	public static boolean isSaveIntermediateResult() {
		return SAVEINTERMEDIATERESULT;
	}

	public static void setSaveIntermediateResult(boolean saveIntermediateResult) {
		SAVEINTERMEDIATERESULT = saveIntermediateResult;
	}

}
