package scenario;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

import org.geotools.data.DataUtilities;
import org.geotools.data.shapefile.ShapefileDataStore;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.feature.DefaultFeatureCollection;
import org.locationtech.jts.geom.Geometry;
import org.opengis.feature.simple.SimpleFeature;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import fields.FrenchParcelFields;
import fr.ign.cogit.geoToolsFunctions.vectors.Collec;
import fr.ign.cogit.geoToolsFunctions.vectors.Geom;
import fr.ign.cogit.parameter.ProfileUrbanFabric;
import fr.ign.cogit.parcelFunction.MarkParcelAttributeFromPosition;
import fr.ign.cogit.parcelFunction.ParcelGetter;
import fr.ign.cogit.parcelFunction.ParcelState;
import goal.ParcelConsolidRecomp;
import goal.ParcelDensification;
import goal.ParcelTotRecomp;

/**
 * Object representing each step of a Parcel Manager scenario. This object is automaticaly set by the PMScenario object Please read the technical documentation on
 * {@link https://github.com/ArtiScales/ParcelManager/blob/master/src/main/resources/doc/scenarioCreation.md}
 * 
 * @author mcolomb
 *
 */

public class PMStep {
	public PMStep(String goal, String parcelProcess, String zone, String communityNumber, String communityType, String urbanFabricType) {
		this.goal = goal;
		this.parcelProcess = parcelProcess;
		this.zone = zone;
		this.communityNumber = communityNumber;
		this.communityType = communityType;
		this.urbanFabricType = urbanFabricType;
		ParcelTotRecomp.SAVEINTERMEDIATERESULT = SAVEINTERMEDIATERESULT;
		ParcelDensification.SAVEINTERMEDIATERESULT = SAVEINTERMEDIATERESULT;
		ParcelConsolidRecomp.SAVEINTERMEDIATERESULT = SAVEINTERMEDIATERESULT;
	}

	/**
	 * Set the path of the different files for a PMStep to be executed. The method is used by PMScenario in a static way because it has no reasons to change within a PM simulation,
	 * except for the parcel file that must be updated after each PMStep to make the new PMStep simulation on an already simulated parcel plan
	 * 
	 * @param parcelFile
	 * @param ilotFile
	 * @param zoningFile
	 * @param tmpFolder
	 * @param buildingFile
	 * @param predicateFile
	 * @param communityFile
	 * @param polygonIntersection
	 * @param outFolder
	 * @param profileFolder
	 */
	public static void setFiles(File parcelFile, File ilotFile, File zoningFile, File tmpFolder, File buildingFile, File predicateFile,
			File communityFile, File polygonIntersection, File outFolder, File profileFolder) {
		PARCELFILE = parcelFile;
		ILOTFILE = ilotFile;
		ZONINGFILE = zoningFile;
		TMPFOLDER = tmpFolder;
		tmpFolder.mkdirs();
		BUILDINGFILE = buildingFile;
		POLYGONINTERSECTION = polygonIntersection;
		PREDICATEFILE = predicateFile;
		COMMUNITYFILE = communityFile;
		OUTFOLDER = outFolder;
		PROFILEFOLDER = profileFolder;
		 if(!COMMUNITYFILE.exists()) {
			 GENERATEATTRIBUTES = false;
		 }
	}

	String goal;
	String parcelProcess;
	String zone;
	String communityNumber;
	String communityType;
	String urbanFabricType;

	static File PARCELFILE;
	static File ILOTFILE;
	static File ZONINGFILE;
	static File TMPFOLDER;
	static File BUILDINGFILE;
	static File PREDICATEFILE;
	static File COMMUNITYFILE;
	static File POLYGONINTERSECTION;
	static File OUTFOLDER;
	static File PROFILEFOLDER;
	static boolean GENERATEATTRIBUTES = true;
	static boolean SAVEINTERMEDIATERESULT = false; 

	public PMStep() {
	}

	/**
	 * Execute the current PM Step.
	 * @return The ShapeFile containing the whole parcels of the given collection, where the simulated parcel have replaced the former parcels. 
	 * @throws Exception
	 */
	public File execute() throws Exception {
		//mark (select) the parcels 
		SimpleFeatureCollection parcelMarked = getSimulationParcels();
		
		ShapefileDataStore shpDSParcel = new ShapefileDataStore(PARCELFILE.toURI().toURL());
		SimpleFeatureCollection parcel = DataUtilities.collection(shpDSParcel.getFeatureSource().getFeatures());
		shpDSParcel.dispose();
		
		ShapefileDataStore shpDSIlot = new ShapefileDataStore(ILOTFILE.toURI().toURL());
		SimpleFeatureCollection ilot = shpDSIlot.getFeatureSource().getFeatures();
		SimpleFeatureCollection parcelCut = new DefaultFeatureCollection();
		
		//get the wanted building profile
		ProfileUrbanFabric.setProfileFolder(PROFILEFOLDER.toString());
		ObjectMapper mapper = new ObjectMapper();
		mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
		InputStream fileInputStream = new FileInputStream(ProfileUrbanFabric.getProfileFolder() + "/" + urbanFabricType + ".json");
		ProfileUrbanFabric profile = mapper.readValue(fileInputStream, ProfileUrbanFabric.class);
		
		//base is the goal : we choose one of the three goals
		switch (goal) {
		case "totalZone":
			ParcelTotRecomp.PROCESS = parcelProcess;
			ShapefileDataStore shpDSZone = new ShapefileDataStore(ZONINGFILE.toURI().toURL());
			SimpleFeatureCollection featuresZones = shpDSZone.getFeatureSource().getFeatures();
			SimpleFeatureCollection zoneCollection = ParcelTotRecomp.createZoneToCut(zone, featuresZones, parcel);
			parcelCut = ParcelTotRecomp.parcelTotRecomp(zoneCollection, parcel, TMPFOLDER, ZONINGFILE, profile.getMaximalArea(),
					profile.getMinimalArea(), profile.getMaximalWidth(), profile.getStreetWidth(), profile.getLargeStreetLevel(), profile.getLargeStreetWidth(),  profile.getDecompositionLevelWithoutStreet());
			shpDSZone.dispose();
			break;
		case "dens":
			parcelCut = ParcelDensification.parcelDensification(parcelMarked, ilot, TMPFOLDER, BUILDINGFILE, profile.getMaximalArea(),
					profile.getMinimalArea(), profile.getMaximalWidth(), profile.getLenDriveway(),
					ParcelState.isArt3AllowsIsolatedParcel(parcel.features().next(), PREDICATEFILE));
			break;
		case "consolid":
			ParcelConsolidRecomp.PROCESS = parcelProcess;
			parcelCut = ParcelConsolidRecomp.parcelConsolidRecomp(parcelMarked, TMPFOLDER, profile.getMaximalArea(), profile.getMinimalArea(),
					profile.getMaximalWidth(), profile.getStreetWidth(), profile.getLargeStreetLevel(), profile.getLargeStreetWidth(), profile.getDecompositionLevelWithoutStreet());
			break;
			default:
				System.out.println(goal+": unrekognized goal (must be either \"totalZone\", \"dens\" or \"consolid\"");
		}
		File output = new File(OUTFOLDER, "parcelCuted-" + goal + ".shp");
		if (GENERATEATTRIBUTES) {
			parcelCut = FrenchParcelFields.fixParcelAttributes(parcelCut, TMPFOLDER, COMMUNITYFILE);
		}
		Collec.exportSFC(parcelCut, output);
		shpDSIlot.dispose();
		
		//if the step produces no output, we return the input parcels
		if(!output.exists()) {
			System.out.println("PMstep "+this.toString() +" returns nothing");
			return PARCELFILE;
		}
		return output;
	}
	
	/**
	 * Mark the parcels that must be simulated within a collection of parcels.
	 * 
	 * It first select the parcel of the zone studied, whether by a city code or by a zone type. The fields can be set with the setters of the
	 * {@link fr.ign.cogit.parcelFunction.ParcelGetter} class.
	 * 
	 * Then it marks the interesting parcels that either cross a given polygon collection or intersects a zoning type. It return even the parcels that won't be simulated. Split
	 * field name in "SPLIT" by default and can be changed with the method {@link fr.ign.cogit.parcelFunction.MarkParcelAttributeFromPosition#setMarkFieldName(String)}.
	 * 
	 * If none of this informations are set, the algorithm selects all the parcels.
	 * 
	 * @return The parcel collection with a mark for the interesting parcels to simulate.
	 * @throws Exception
	 */
	public SimpleFeatureCollection getSimulationParcels() throws Exception {

		//select the interesting parcels
		ShapefileDataStore shpDSParcel = new ShapefileDataStore(PARCELFILE.toURI().toURL());
		SimpleFeatureCollection parcel = new DefaultFeatureCollection();
		if (communityNumber != null && communityNumber != "") {
			parcel = DataUtilities.collection(ParcelGetter.getParcelByZip(shpDSParcel.getFeatureSource().getFeatures(), communityNumber));
		} else if (communityType != null && communityType != "") {
			parcel = DataUtilities.collection(ParcelGetter.getParcelByTypo(communityType, parcel, ZONINGFILE));
		} else {
			parcel = DataUtilities.collection(shpDSParcel.getFeatureSource().getFeatures());
		}

		//mark the parcels
		SimpleFeatureCollection parcelMarked = new DefaultFeatureCollection();
		// parcel marking step
		if (POLYGONINTERSECTION != null && POLYGONINTERSECTION.exists()) {
			parcelMarked = MarkParcelAttributeFromPosition.markParcelIntersectPolygonIntersection(parcel, POLYGONINTERSECTION);
		}
//		Collec.exportSFC(parcelMarked, new File(TMPFOLDER, "parcelMarked.shp"));
		if (ZONINGFILE != null && ZONINGFILE.exists() && zone != null && zone != "") {
			if (parcelMarked.size() > 0) {
				parcelMarked = MarkParcelAttributeFromPosition.markParcelIntersectZoningType(parcelMarked, zone, ZONINGFILE);
			} else {
				parcelMarked = MarkParcelAttributeFromPosition.markParcelIntersectZoningType(parcel, zone, ZONINGFILE);
			}
		}
//		Collec.exportSFC(parcelMarked, new File(TMPFOLDER, "parcelMarked2.shp"));
		DefaultFeatureCollection result = DataUtilities.collection(parcelMarked);
		shpDSParcel.dispose();
		return result;
	}
	
	/**
	 * Generate the bound of the parcels that are simulated by the current PMStep. Uses the marked parcels by the {@link #getSimulationParcels()} method. 
	 * @return A geometry of the simulated parcels 
	 * @throws IOException
	 * @throws Exception
	 */
	public Geometry getBoundsOfZone() throws IOException, Exception {
		DefaultFeatureCollection zone = new DefaultFeatureCollection();
		Arrays.stream(getSimulationParcels().toArray(new SimpleFeature[0])).forEach(parcel -> {
			if (parcel.getAttribute(MarkParcelAttributeFromPosition.getMarkFieldName()).equals(1)) {
				zone.add(parcel);
			}
		});
		return Geom.unionSFC(zone);
	}

	public static void setParcel(File parcelFile) {
		PARCELFILE = parcelFile;
	}

	public String getZoneStudied() {
		return goal + "With" + parcelProcess + "On" + zone + "Of" + communityNumber;
	}
	
	@Override
	public String toString() {
		return "PMStep [goal=" + goal + ", parcelProcess=" + parcelProcess + ", zone=" + zone + ", communityNumber=" + communityNumber
				+ ", communityType=" + communityType + ", urbanFabricType=" + urbanFabricType + "]";
	}

	public static boolean isSaveIntermediateResult() {
		return SAVEINTERMEDIATERESULT;
	}

	public static void setSaveIntermediateResult(boolean sAVEINTERMEDIATERESULT) {
		SAVEINTERMEDIATERESULT = sAVEINTERMEDIATERESULT;
	}

	public static File getPOLYGONINTERSECTION() {
		return POLYGONINTERSECTION;
	}

	public static void setPOLYGONINTERSECTION(File pOLYGONINTERSECTION) {
		POLYGONINTERSECTION = pOLYGONINTERSECTION;
	}
}
