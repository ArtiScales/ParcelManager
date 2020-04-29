package fr.ign.artiscales.scenario;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.geotools.data.DataUtilities;
import org.geotools.data.shapefile.ShapefileDataStore;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.feature.DefaultFeatureCollection;
import org.locationtech.jts.geom.Geometry;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.NoSuchAuthorityCodeException;

import fr.ign.artiscales.analysis.DensificationStudy;
import fr.ign.artiscales.fields.GeneralFields;
import fr.ign.artiscales.fields.french.FrenchParcelFields;
import fr.ign.artiscales.fields.french.FrenchZoningSchemas;
import fr.ign.artiscales.goal.ConsolidationDivision;
import fr.ign.artiscales.goal.Densification;
import fr.ign.artiscales.goal.ZoneDivision;
import fr.ign.artiscales.parcelFunction.MarkParcelAttributeFromPosition;
import fr.ign.artiscales.parcelFunction.ParcelAttribute;
import fr.ign.artiscales.parcelFunction.ParcelGetter;
import fr.ign.artiscales.parcelFunction.ParcelSchema;
import fr.ign.artiscales.parcelFunction.ParcelState;
import fr.ign.cogit.geoToolsFunctions.vectors.Collec;
import fr.ign.cogit.geoToolsFunctions.vectors.Geom;
import fr.ign.cogit.geometryGeneration.CityGeneration;
import fr.ign.cogit.parameter.ProfileUrbanFabric;

/**
 * Object representing each step of a Parcel Manager scenario. This object is automatically set by the PMScenario object.
 * 
 * @see <a href="https://github.com/ArtiScales/ParcelManager/blob/master/src/main/resources/doc/scenarioCreation.md">scenarioCreation.md</a> 
 * 
 * @author Maxime Colomb
 *
 */
public class PMStep {

	public PMStep(String goal, String parcelProcess, String genericZone, String preciseZone, String communityNumber, String communityType, String urbanFabricType) {
		this.goal = goal;
		this.parcelProcess = parcelProcess;
		this.genericZone = genericZone;
		this.preciseZone = preciseZone;
		this.communityNumber = communityNumber;
		this.communityType = communityType;
		this.urbanFabricType = urbanFabricType;
		ZoneDivision.SAVEINTERMEDIATERESULT = SAVEINTERMEDIATERESULT;
		Densification.SAVEINTERMEDIATERESULT = SAVEINTERMEDIATERESULT;
		ConsolidationDivision.SAVEINTERMEDIATERESULT = SAVEINTERMEDIATERESULT;
		ZoneDivision.DEBUG = DEBUG;
		ConsolidationDivision.DEBUG = DEBUG;				
	}

	/**
	 * Set the path of the different files for a PMStep to be executed. The method is used by PMScenario in a static way because it has no reasons to change within a PM simulation,
	 * except for the parcel file that must be updated after each PMStep to make the new PMStep simulation on an already simulated parcel plan
	 * 
	 */
	public static void setFiles(File parcelFile, File zoningFile, File tmpFolder, File buildingFile, File roadFile, File predicateFile,
			File polygonIntersection, File outFolder, File profileFolder) {
		PARCELFILE = parcelFile;
		ZONINGFILE = zoningFile;
		TMPFOLDER = tmpFolder;
		tmpFolder.mkdirs();
		BUILDINGFILE = buildingFile;
		ROADFILE = roadFile;
		POLYGONINTERSECTION = polygonIntersection;
		PREDICATEFILE = predicateFile;
		OUTFOLDER = outFolder;
		PROFILEFOLDER = profileFolder;
	}

	private String goal, parcelProcess, communityNumber, communityType , urbanFabricType , genericZone, preciseZone;
	List<String> communityNumbers = new ArrayList<String>(); 
	
	private static File PARCELFILE, ZONINGFILE, TMPFOLDER, BUILDINGFILE, ROADFILE, PREDICATEFILE, 
	POLYGONINTERSECTION, OUTFOLDER, PROFILEFOLDER;
	public static List<String> cachePlacesSimulates = new ArrayList<String>();

	/**
	 * If true, run method to re-assign fields value
	 */
	private static boolean GENERATEATTRIBUTES = true;
	/**
	 * If true, save a shapefile containing only the simulated parcels in the temporary folder for every goal simulated.
	 */
	private static boolean SAVEINTERMEDIATERESULT = false; 
	/**
	 * If true, will save all the intermediate results in the temporary folder
	 */
	private static boolean DEBUG = false;
	/**
	 * Execute the current PM Step.
	 * @return The ShapeFile containing the whole parcels of the given collection, where the simulated parcel have replaced the former parcels. 
	 * @throws Exception
	 */
	public File execute() throws Exception {
		//convert the parcel to a common type
		ShapefileDataStore shpDSParcel = new ShapefileDataStore(PARCELFILE.toURI().toURL());
		SimpleFeatureCollection parcel = DataUtilities.collection(shpDSParcel.getFeatureSource().getFeatures());
		switch (GeneralFields.getParcelFieldType()) {
		case "french":
			parcel = FrenchParcelFields.frenchParcelToMinParcel(parcel);
			break;
		}
		ShapefileDataStore shpDSZone = new ShapefileDataStore(ZONINGFILE.toURI().toURL());
		// mark (select) the parcels
		SimpleFeatureCollection parcelMarked;
		//if we work with zones, we put them as parcel input
		if (goal.equals("zoneDivision")) {
			parcelMarked = getSimulationParcels(ZoneDivision.createZoneToCut(genericZone, shpDSZone.getFeatureSource().getFeatures(), parcel));
		} else {
			parcelMarked = getSimulationParcels(parcel);
		}
		shpDSZone.dispose();
		if (DEBUG)
			Collec.exportSFC(parcelMarked, new File(TMPFOLDER, "parcelMarked" + this.getZoneStudied()));
		SimpleFeatureCollection parcelCut = new DefaultFeatureCollection();
		// get the wanted building profile
		ProfileUrbanFabric profile = ProfileUrbanFabric.convertJSONtoProfile(new File(PROFILEFOLDER + "/" + urbanFabricType + ".json"));
		// in case of lot of cities to simulate, we separate the execution of PM simulations for each community
		for (String communityNumber : communityNumbers) {
			System.out.println("for community "+communityNumber);
			SimpleFeatureCollection parcelMarkedComm = ParcelGetter.getParcelByCommunityCode(parcelMarked, communityNumber);
			// we choose one of the different goals
			switch (goal) {
			case "zoneDivision":
				ZoneDivision.PROCESS = parcelProcess;
				parcelCut = ZoneDivision.zoneDivision(parcelMarkedComm, parcel, TMPFOLDER, ZONINGFILE,
						profile.getMaximalArea(), profile.getMinimalArea(), profile.getMaximalWidth(), profile.getStreetWidth(),
						profile.getLargeStreetLevel(), profile.getLargeStreetWidth(), profile.getDecompositionLevelWithoutStreet());
				parcel = parcelCut;
				break;
			case "densification":
				((DefaultFeatureCollection) parcelCut).addAll(Densification.densification(parcelMarkedComm, CityGeneration.createUrbanIslet(parcelMarkedComm), TMPFOLDER, BUILDINGFILE, ROADFILE, profile.getMaximalArea(),
						profile.getMinimalArea(), profile.getMaximalWidth(), profile.getLenDriveway(),
						ParcelState.isArt3AllowsIsolatedParcel(parcel.features().next(), PREDICATEFILE), Geom.createBufferBorder(parcelMarkedComm)));
				break;
			case "densificationOrNeighborhood":
				((DefaultFeatureCollection) parcelCut).addAll(Densification.densificationOrNeighborhood(parcelMarkedComm, CityGeneration.createUrbanIslet(parcelMarkedComm), TMPFOLDER, BUILDINGFILE, ROADFILE, profile,
						ParcelState.isArt3AllowsIsolatedParcel(parcel.features().next(), PREDICATEFILE), Geom.createBufferBorder(parcelMarkedComm)));
				break;
			case "consolidationDivision":
				ConsolidationDivision.PROCESS = parcelProcess;
				((DefaultFeatureCollection) parcelCut).addAll(ConsolidationDivision.consolidationDivision(parcelMarkedComm, TMPFOLDER, profile.getMaximalArea(), profile.getMinimalArea(),
						profile.getMaximalWidth(), profile.getStreetWidth(), profile.getLargeStreetLevel(), profile.getLargeStreetWidth(),
						profile.getDecompositionLevelWithoutStreet()));
				break;
			case "densificationStudy":
				DensificationStudy.runDensificationStudy(parcelMarkedComm, BUILDINGFILE, ROADFILE, ZONINGFILE, TMPFOLDER, OUTFOLDER,
						ParcelState.isArt3AllowsIsolatedParcel(parcel.features().next(), PREDICATEFILE), profile);
				break;
			default:
				System.out.println(goal + ": unrekognized goal");
			}
		}
		// we add the parcels from the communities that haven't been simulated 
		for(String communityCode : ParcelAttribute.getCityCodesOfParcels(parcel)) {
			if (communityNumbers.contains(communityCode))
				continue;
			((DefaultFeatureCollection) parcelCut).addAll( ParcelGetter.getParcelByCommunityCode(parcel, communityCode));
		}
		File output = new File(OUTFOLDER, "parcelCuted-" + goal + "-"+ urbanFabricType + "-" + genericZone +"_" + preciseZone + ".shp");
		//Attribute generation (optional)
		if (GENERATEATTRIBUTES) {
			switch (GeneralFields.getParcelFieldType()) {
			case "french":
				System.out.println("we set attribute as a french parcel");
				parcelCut = FrenchParcelFields.setOriginalFrenchParcelAttributes(parcelCut, shpDSParcel.getFeatureSource().getFeatures());
				break;
			}
		}
		Collec.exportSFC(parcelCut, output);
		shpDSParcel.dispose();
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
	 * {@link fr.ign.artiscales.parcelFunction.ParcelGetter} class.
	 * 
	 * Then it marks the interesting parcels that either cross a given polygon collection or intersects a zoning type. It return even the parcels that won't be simulated. Split
	 * field name in "SPLIT" by default and can be changed with the method {@link fr.ign.artiscales.parcelFunction.MarkParcelAttributeFromPosition#setMarkFieldName(String)}.
	 * 
	 * If none of this informations are set, the algorithm selects all the parcels.
	 * 
	 * @return The parcel collection with a mark for the interesting parcels to simulate.
	 * @throws IOException 
	 * @throws FactoryException 
	 * @throws NoSuchAuthorityCodeException 
	 */
	public SimpleFeatureCollection getSimulationParcels(SimpleFeatureCollection parcelIn) throws IOException, NoSuchAuthorityCodeException, FactoryException  {
		
		// special case where zoneDivision will return other than parcel
		if (goal.equals("zoneDivision")) {
			ParcelSchema.setMinParcelCommunityField(GeneralFields.getZoneCommunityCode());
		}
		
		//select the parcels from the interesting communities
		SimpleFeatureCollection parcel = new DefaultFeatureCollection();
		// if a community information has been set 
		if (communityNumber != null && communityNumber != "") {
			// if a list of community has been set, the numbers must be separated with 
			if (communityNumber.contains(",")) {
				// we select parcels from every zipcodes
				for (String z : communityNumber.split(",")) {
					communityNumbers.add(z);
					((DefaultFeatureCollection) parcel).addAll(ParcelGetter.getParcelByCommunityCode(parcelIn, z));
				}
			} 
			// if a single community number is set
			else {
				communityNumbers.add(communityNumber);
				parcel = DataUtilities.collection(ParcelGetter.getParcelByCommunityCode(parcelIn, communityNumber));
			}
		} 
		// if multiple communities are present in the parcel collection
		else if(ParcelAttribute.getCityCodesOfParcels(parcelIn).size() > 1) {
			for (String cityCode : ParcelAttribute.getCityCodesOfParcels(parcelIn)) {
				communityNumbers.add(cityCode);
			}
			((DefaultFeatureCollection) parcel).addAll(parcelIn);
		} 
		// if a type of community has been set  
		else if (communityType != null && communityType != "") {
			communityNumbers.addAll(ParcelAttribute.getCityCodesOfParcels(parcelIn));
			parcel = DataUtilities.collection(ParcelGetter.getParcelByTypo(communityType, parcelIn, ZONINGFILE));
		} 
		// if the input parcel is just what needs to be simulated
		else {
			communityNumbers.addAll(ParcelAttribute.getCityCodesOfParcels(parcelIn));
			parcel = DataUtilities.collection(parcelIn);
		}
		
		if (DEBUG)
			Collec.exportSFC(parcel, new File(OUTFOLDER, "selectedParcels.shp"));		
		
		// parcel marking with input polygons 
		if (POLYGONINTERSECTION != null && POLYGONINTERSECTION.exists()) {
			parcel = MarkParcelAttributeFromPosition.markParcelIntersectPolygonIntersection(parcel, POLYGONINTERSECTION);
		}
		SimpleFeatureCollection result = new DefaultFeatureCollection(); 
		// parcel marking with a zoning plan (possible to be hacked for any attribute feature selection by setting the field name to the genericZoning scenario parameter) 
		if (ZONINGFILE != null && ZONINGFILE.exists() && genericZone != null && !genericZone.equals("")) {
			genericZone = FrenchZoningSchemas.normalizeNameFrenchBigZone(genericZone);
			// we proceed for each cities (in )
			for (String communityNumber : communityNumbers) {
				SimpleFeatureCollection parcelCity = ParcelGetter.getParcelByCommunityCode(parcel, communityNumber);
				boolean alreadySimuled = false;
				String place = communityNumber + "-" + genericZone;
				for (String cachePlaceSimulates : cachePlacesSimulates) {
					if (cachePlaceSimulates.startsWith(place)) {
						System.out.println("Warning: " + place + " already simulated");
						alreadySimuled = true;
						break;
					}
				}
				// if that zone has never been simulated, we proceed as usual
				if (!alreadySimuled) {
					// if a generic zone is set but no specific zone are
					if (genericZone != null && genericZone != "" && (preciseZone == null || preciseZone == "")) {
						((DefaultFeatureCollection)result).addAll(MarkParcelAttributeFromPosition.markParcelIntersectGenericZoningType(parcelCity, genericZone, ZONINGFILE));
						cachePlacesSimulates.add(place);
					}
					// if a specific zone is also set
					else if (genericZone != null && genericZone != "" && preciseZone != null && preciseZone != "") {
						((DefaultFeatureCollection)result).addAll(MarkParcelAttributeFromPosition.markParcelIntersectPreciseZoningType(parcelCity, genericZone, preciseZone, ZONINGFILE));
						cachePlacesSimulates.add(place + "-" + preciseZone);
					}
				}
				// the zone has already been simulated : must be a small part defined by the preciseZone field
				else {
					// if a precise zone hasn't been specified
					if (genericZone != null && genericZone != "" && (preciseZone == null || preciseZone == "")) {
						// if previous zones have had a precise zone calculated, we list them
						List<String> preciseZones = new ArrayList<String>();
						for (String pl : cachePlacesSimulates) {
							if (pl.startsWith(place)) {
								String[] p = pl.split("-");
								if (p.length == 3 && pl.startsWith(place)) {
									preciseZones.add(p[2]);
								}
							}
						}
						// if we found specific precise zones, we exclude them from the marking session
						if (!preciseZones.isEmpty()) {
							((DefaultFeatureCollection) result).addAll(MarkParcelAttributeFromPosition
									.markParcelIntersectZoningWithoutPreciseZonings(parcelCity, genericZone, preciseZones, ZONINGFILE));
							System.out.println("sparedPreciseZones: " + preciseZones);
						} 
						// if no precise zones have been found - this shouldn't happend - but we select zones with generic zoning
						else {
							System.out.println("no precise zones have been found - this shouldn't happend");
							((DefaultFeatureCollection)result).addAll(MarkParcelAttributeFromPosition.markParcelIntersectGenericZoningType(parcelCity, genericZone, ZONINGFILE));
						}
						cachePlacesSimulates.add(place);
					} 
					//a precise zone has been specified : we mark them parcels  
					else if (genericZone != null && genericZone != "" && preciseZone != null && preciseZone != "") {
						((DefaultFeatureCollection)result).addAll(MarkParcelAttributeFromPosition.markParcelIntersectPreciseZoningType(parcelCity, genericZone, preciseZone, ZONINGFILE));
						cachePlacesSimulates.add(place + "-" + preciseZone);
					} else {
						System.out.println(
								"getSimulationParcels: zone has already been simulated but not on a small part defined by the preciseZone field");
					}
				}
				 if (parcelCity.isEmpty() || MarkParcelAttributeFromPosition.isNoParcelMarked(parcelCity)) {
					cachePlacesSimulates.remove(place);
					cachePlacesSimulates.remove(place + "-" + preciseZone);
					//TODO find a proper way to remove the whole community?
				 }
			}
		} else {
			result=parcel;
		}
		
		//if the result is only zones, we return only the interesting parcels 
		if (goal.equals("zoneDivision")) {
			result = new DefaultFeatureCollection();
			try (SimpleFeatureIterator it = parcel.features()) {
				while (it.hasNext()) {
					SimpleFeature feat = it.next();
					if (feat.getAttribute(MarkParcelAttributeFromPosition.getMarkFieldName()).equals(1)) {
						((DefaultFeatureCollection) result).add(feat);
					}
				}
			} catch (Exception problem) {
				problem.printStackTrace();
			}
		}
		return result;
	}
	
	/**
	 * Generate the bound of the parcels that are simulated by the current PMStep. Uses the marked parcels by the {@link #getSimulationParcels(SimpleFeatureCollection)} method.
	 * Flush the cache.
	 * 
	 * @return A geometry of the simulated parcels
	 * @throws IOException
	 * @throws FactoryException
	 * @throws NoSuchAuthorityCodeException
	 */
	public Geometry getBoundsOfZone() throws IOException, NoSuchAuthorityCodeException, FactoryException {
		DefaultFeatureCollection zone = new DefaultFeatureCollection();
		ShapefileDataStore sds = new ShapefileDataStore(PARCELFILE.toURI().toURL());
		SimpleFeatureCollection sfc = DataUtilities.collection(sds.getFeatureSource().getFeatures());
		sds.dispose();
		SimpleFeatureCollection zones = getSimulationParcels(sfc);
		Arrays.stream(zones.toArray(new SimpleFeature[0])).forEach(parcel -> {
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
		return goal + "With" + parcelProcess + "On" + genericZone +"_" + preciseZone + "Of" + communityNumber;
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

	public static boolean isGENERATEATTRIBUTES() {
		return GENERATEATTRIBUTES;
	}

	public static void setGENERATEATTRIBUTES(boolean gENERATEATTRIBUTES) {
		GENERATEATTRIBUTES = gENERATEATTRIBUTES;
	}

	public static boolean isDEBUG() {
		return DEBUG;
	}

	public static void setDEBUG(boolean dEBUG) {
		DEBUG = dEBUG;
	}

	@Override
	public String toString() {
		return "PMStep [goal=" + goal + ", parcelProcess=" + parcelProcess + ", communityNumber=" + communityNumber + ", communityType="
				+ communityType + ", urbanFabricType=" + urbanFabricType + ", genericZone=" + genericZone + ", preciseZone=" + preciseZone + "]";
	}
}
