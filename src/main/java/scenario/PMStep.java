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
	 * generate which parcels must be simulated 
	 * @return
	 * @throws Exception 
	 */
	public SimpleFeatureCollection getSimulationParcels() throws Exception {
		ShapefileDataStore shpDSParcel = new ShapefileDataStore(PARCELFILE.toURI().toURL());
		SimpleFeatureCollection parcel = new DefaultFeatureCollection();
		if (communityNumber != null && communityNumber != "") {
			parcel = DataUtilities.collection(ParcelGetter.getParcelByZip(shpDSParcel.getFeatureSource().getFeatures(), communityNumber));
		} else if (communityType != null && communityType != "") {
			parcel = DataUtilities.collection(ParcelGetter.getParcelByTypo(communityType, parcel, ZONINGFILE));
		} else {
			parcel = DataUtilities.collection(shpDSParcel.getFeatureSource().getFeatures());
		}

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
