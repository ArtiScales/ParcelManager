package fr.ign.artiscales.pm.scenario;

import fr.ign.artiscales.pm.analysis.RealUrbanFabricParameters;
import fr.ign.artiscales.pm.division.StraightSkeletonDivision;
import fr.ign.artiscales.pm.fields.GeneralFields;
import fr.ign.artiscales.pm.parcelFunction.MarkParcelAttributeFromPosition;
import fr.ign.artiscales.pm.parcelFunction.ParcelAttribute;
import fr.ign.artiscales.pm.parcelFunction.ParcelGetter;
import fr.ign.artiscales.pm.parcelFunction.ParcelSchema;
import fr.ign.artiscales.pm.parcelFunction.ParcelState;
import fr.ign.artiscales.pm.usecase.DensificationStudy;
import fr.ign.artiscales.pm.workflow.ConsolidationDivision;
import fr.ign.artiscales.pm.workflow.Densification;
import fr.ign.artiscales.pm.workflow.Workflow;
import fr.ign.artiscales.pm.workflow.ZoneDivision;
import fr.ign.artiscales.tools.geoToolsFunctions.vectors.collec.CollecMgmt;
import fr.ign.artiscales.tools.geometryGeneration.CityGeneration;
import fr.ign.artiscales.tools.parameter.ProfileUrbanFabric;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.geotools.data.DataStore;
import org.geotools.data.DataUtilities;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.feature.DefaultFeatureCollection;
import org.locationtech.jts.geom.Geometry;
import org.opengis.feature.simple.SimpleFeature;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Object representing each step of a Parcel Manager scenario. This object is automatically set by the PMScenario object.
 *
 * @author Maxime Colomb
 * @see <a href="https://github.com/ArtiScales/ParcelManager/blob/master/src/main/resources/doc/scenarioCreation.md">scenarioCreation.md</a>
 */
public class PMStep {
    private static final List<String> cachePlacesSimulates = new ArrayList<>();
    /**
     * Geographic files
     */
    private static File PARCELFILE, ZONINGFILE, BUILDINGFILE, ROADFILE, PREDICATEFILE, POLYGONINTERSECTION, ZONE, OUTFOLDER, PROFILEFOLDER;
    /**
     * If true, save a geopackage containing only the simulated parcels in the temporary folder for every workflow simulated.
     */
    private static boolean SAVEINTERMEDIATERESULT = false;
    /**
     * If true, will save all the intermediate results in the temporary folder
     */
    private static boolean DEBUG = false;
    private static boolean allowIsolatedParcel = false;

    final private String workflow, parcelProcess, communityNumber, communityType, urbanFabricType, genericZone, preciseZone;
    private final boolean peripheralRoad;
    List<String> communityNumbers = new ArrayList<>();
    /**
     * If true, will look at the community built parcel's area to adapt the maximal and minimal area (set with the 1st and the 9th decile of the built area's distribution). False by default.
     */
    private boolean adaptAreaOfUrbanFabric;
    /**
     * If true, will keep road on ZoneDivision processes. True by default
     */
    private boolean keepExistingRoad;
    /**
     * The last generated parcel plan file. Could be useful for programs to get it directly
     */
    private File lastOutput;

    public PMStep(String workflow, String parcelProcess, String genericZone, String preciseZone, String communityNumber, String communityType, String urbanFabricType, boolean peripheralRoad, boolean keepExistingRoad, boolean adaptUrbanFabric) {
        this.workflow = workflow;
        this.parcelProcess = parcelProcess;
        this.genericZone = genericZone;
        this.preciseZone = preciseZone;
        this.communityNumber = communityNumber;
        this.communityType = communityType;
        this.urbanFabricType = urbanFabricType;
        this.adaptAreaOfUrbanFabric = adaptUrbanFabric;
        this.keepExistingRoad = keepExistingRoad;
        this.peripheralRoad = peripheralRoad;
        setSaveIntermediateResult(SAVEINTERMEDIATERESULT);
        setDEBUG(DEBUG);
    }

    public static File getOUTFOLDER() {
        return OUTFOLDER;
    }

    public static void setOUTFOLDER(File OUTFOLDER) {
        PMStep.OUTFOLDER = OUTFOLDER;
    }

    /**
     * Set the path of the different files for a PMStep to be executed. The method is used by PMScenario in a static way because it has no reasons to change within a PM simulation,
     * except for the parcel file that must be updated after each PMStep to make the new PMStep simulation on an already simulated parcel plan
     */
    public static void setFiles(File parcelFile, File zoningFile, File buildingFile, File roadFile, File predicateFile, File polygonIntersection, File zone, File outFolder, File profileFolder) {
        PARCELFILE = parcelFile;
        ZONINGFILE = zoningFile;
        BUILDINGFILE = buildingFile;
        ROADFILE = roadFile;
        POLYGONINTERSECTION = polygonIntersection;
        ZONE = zone;
        PREDICATEFILE = predicateFile;
        OUTFOLDER = outFolder;
        PROFILEFOLDER = profileFolder;
    }

    /**
     * Put a parcel plan different from the on originally set
     *
     * @param parcelFile new geo file containing parcel plan
     */
    public static void setParcel(File parcelFile) {
        PARCELFILE = parcelFile;
    }

    /**
     * If true, save a geopackage containing only the simulated parcels in the temporary folder for every workflow simulated.
     *
     * @return SAVEINTERMEDIATERESULT
     */
    public static boolean isSaveIntermediateResult() {
        return SAVEINTERMEDIATERESULT;
    }

    /**
     * If true, save a geopackage containing only the simulated parcels in the temporary folder for every workflow simulated.
     *
     * @param sAVEINTERMEDIATERESULT true if we save, false otherwise
     */
    public static void setSaveIntermediateResult(boolean sAVEINTERMEDIATERESULT) {
        Workflow.setSAVEINTERMEDIATERESULT(sAVEINTERMEDIATERESULT);
        SAVEINTERMEDIATERESULT = sAVEINTERMEDIATERESULT;
    }

    /**
     * Get the intersection polygon geographic file.
     *
     * @return POLYGONINTERSECTION
     */
    public static File getPOLYGONINTERSECTION() {
        return POLYGONINTERSECTION;
    }

    /**
     * Set the intersection polygon geographic file.
     *
     * @param pOLYGONINTERSECTION new geo file containing intersection
     */
    public static void setPOLYGONINTERSECTION(File pOLYGONINTERSECTION) {
        POLYGONINTERSECTION = pOLYGONINTERSECTION;
    }

    /**
     * If true, will save all the intermediate results in the temporary folder
     *
     * @return DEBUG
     */
    public static boolean isDEBUG() {
        return DEBUG;
    }

    /**
     * If true, will save all the intermediate results in the temporary folder
     *
     * @param dEBUG true for debug mode
     */
    public static void setDEBUG(boolean dEBUG) {
        Workflow.setDEBUG(dEBUG);
        DEBUG = dEBUG;
    }

    public static boolean isAllowIsolatedParcel() {
        return allowIsolatedParcel;
    }

    public static void setAllowIsolatedParcel(boolean allowIsolatedParcel) {
        PMStep.allowIsolatedParcel = allowIsolatedParcel;
    }

    /**
     * Empty the cache of zones that have already been simulated
     */
    public static void flushCachePlacesSimulates() {
        cachePlacesSimulates.clear();
    }

    public static File getPROFILEFOLDER() {
        return PROFILEFOLDER;
    }

    public boolean isPeripheralRoad() {
        return peripheralRoad;
    }

    public boolean isAdaptAreaOfUrbanFabric() {
        return adaptAreaOfUrbanFabric;
    }

    public void setAdaptAreaOfUrbanFabric(boolean newAdaptAreaOfUrbanFabric) {
        adaptAreaOfUrbanFabric = newAdaptAreaOfUrbanFabric;
    }

    public String getUrbanFabricType() {
        return urbanFabricType;
    }

    /**
     * Execute the current PM Step.
     *
     * @return The geo file containing the whole parcels of the given collection, where the simulated parcel have replaced the former parcels.
     * @throws IOException tons of reading and writing
     */
    public File execute() throws IOException {
        OUTFOLDER.mkdirs();
        StraightSkeletonDivision.setGeneratePeripheralRoad(peripheralRoad);
        //convert the parcel to a common type
        DataStore dSParcel = CollecMgmt.getDataStore(PARCELFILE);
        SimpleFeatureCollection parcel = DataUtilities.collection(dSParcel.getFeatureSource(dSParcel.getTypeNames()[0]).getFeatures());
        dSParcel.dispose();

        // mark (select) the parcels
        SimpleFeatureCollection parcelMarked;
        //if we work with zones, we put them as parcel input
        if (workflow.equals("zoneDivision"))
            parcelMarked = getSimulationParcels(getZone(parcel));
        else
            parcelMarked = getSimulationParcels(parcel);
        if (DEBUG) {
            System.out.println("parcels marked with " + MarkParcelAttributeFromPosition.countMarkedParcels(parcelMarked) + " marks");
            File tmpFolder = new File(OUTFOLDER, "tmp");
            tmpFolder.mkdirs();
            CollecMgmt.exportSFC(parcelMarked, new File(tmpFolder, "parcelMarked" + this.workflow + "-" + this.parcelProcess + this.preciseZone));
        }
        SimpleFeatureCollection parcelCut = new DefaultFeatureCollection();
        // get the wanted building profile
        ProfileUrbanFabric profile = ProfileUrbanFabric.convertJSONtoProfile(new File(PROFILEFOLDER + "/" + urbanFabricType + ".json"));
        // in case of lot of cities to simulate, we separate the execution of PM simulations for each community
        for (String communityNumber : communityNumbers) {
            System.out.println("for community " + communityNumber);
            SimpleFeatureCollection parcelMarkedComm = ParcelGetter.getParcelByCommunityCode(parcelMarked, communityNumber);
            if (parcelMarkedComm.size() == 0) {
                System.out.println("No parcels for community " + communityNumber);
                continue;
            }
            //if we adapt parcel's area to the community
            if (adaptAreaOfUrbanFabric) {
                RealUrbanFabricParameters rufp = new RealUrbanFabricParameters(parcelMarkedComm, BUILDINGFILE);
                DescriptiveStatistics stat = rufp.getAreaBuilt();
                double max = stat.getPercentile(75);
                double min = max / 2 < stat.getPercentile(10) ? max / 2.5 : stat.getPercentile(10);
                System.out.println("new parcel MaximalArea: " + max);
                System.out.println("new parcel MinimalArea: " + min);
                profile.setMaximalArea(max);
                profile.setMinimalArea(min);
            }
            // If a predicate file has been set
            if (PREDICATEFILE != null && PREDICATEFILE.exists()) {
                allowIsolatedParcel = ParcelState.isArt3AllowsIsolatedParcel(parcel.features().next(), PREDICATEFILE);
                System.out.println("allowIsolatedParcel finally " + allowIsolatedParcel);
            }
            // we choose one of the different workflows
            switch (workflow) {
                case "zoneDivision":
                    ZoneDivision.PROCESS = parcelProcess;
                    ((DefaultFeatureCollection) parcelCut).addAll((new ZoneDivision()).zoneDivision(parcelMarkedComm,
                            ParcelGetter.getParcelByCommunityCode(parcel, communityNumber), OUTFOLDER, profile, ROADFILE, BUILDINGFILE, keepExistingRoad));
                    break;
                case "densification":
                    ((DefaultFeatureCollection) parcelCut).addAll((new Densification()).densification(parcelMarkedComm,
                            CityGeneration.createUrbanBlock(parcelMarkedComm), OUTFOLDER, BUILDINGFILE, ROADFILE, profile.getHarmonyCoeff(),
                            profile.getNoise(), profile.getMaximalArea(), profile.getMinimalArea(), profile.getMinimalWidthContactRoad(),
                            profile.getLenDriveway(), allowIsolatedParcel, CityGeneration.createBufferBorder(parcelMarkedComm)));
                    break;
                case "densificationOrNeighborhood":
                    ((DefaultFeatureCollection) parcelCut).addAll(
                            (new Densification()).densificationOrNeighborhood(parcelMarkedComm, CityGeneration.createUrbanBlock(parcelMarkedComm), OUTFOLDER,
                                    BUILDINGFILE, ROADFILE, profile, allowIsolatedParcel, CityGeneration.createBufferBorder(parcelMarkedComm), 5));
                    break;
                case "consolidationDivision":
                    ConsolidationDivision.PROCESS = parcelProcess;
                    ((DefaultFeatureCollection) parcelCut).addAll((new ConsolidationDivision()).consolidationDivision(parcelMarkedComm, ROADFILE, OUTFOLDER, profile));
                    break;
                case "densificationStudy":
                    DensificationStudy.runDensificationStudy(parcelMarkedComm, BUILDINGFILE, ROADFILE, ZONINGFILE, OUTFOLDER, allowIsolatedParcel, profile);
                    break;
                default:
                    System.out.println(workflow + ": unrecognized workflow");
            }
        }
        // we add the parcels from the communities that haven't been simulated
        for (String communityCode : ParcelAttribute.getCityCodesOfParcels(parcel)) {
            if (communityNumbers.contains(communityCode))
                continue;
            ((DefaultFeatureCollection) parcelCut)
                    .addAll(ParcelGetter.getParcelByCommunityCode(parcel, communityCode));
        }
        lastOutput = makeFileName();

        CollecMgmt.exportSFC(parcelCut, lastOutput);
        //if the step produces no output, we return the input parcels
        if (!lastOutput.exists()) {
            System.out.println("PMstep " + this + " returns nothing");
            return PARCELFILE;
        }
        return lastOutput;
    }

    /**
     * Mark the parcels that must be simulated within a collection of parcels.
     * <p>
     * It first select the parcel of the zone studied, whether by a city code or by a zone type. The fields can be set with the setters of the
     * {@link fr.ign.artiscales.pm.parcelFunction.ParcelGetter} class.
     * <p>
     * Then it marks the interesting parcels that either cross a given polygon collection or intersects a zoning type. It return even the parcels that won't be simulated. Split
     * field name in "SPLIT" by default and can be changed with the method {@link fr.ign.artiscales.pm.parcelFunction.MarkParcelAttributeFromPosition#setMarkFieldName(String)}.
     * <p>
     * If none of this informations are set, the algorithm selects all the parcels.
     *
     * @return The parcel collection with a mark for the interesting parcels to simulate.
     * @throws IOException reading a lot of files
     */
    public SimpleFeatureCollection getSimulationParcels(SimpleFeatureCollection parcelIn) throws IOException {
        // special case where zoneDivision will return other than parcel
        String iniZoneCommunityName = ParcelSchema.getParcelCommunityField();
        if (workflow.equals("zoneDivision"))
            ParcelSchema.setParcelCommunityField(GeneralFields.getZoneCommunityCode());
        // select the parcels from the interesting communities
        SimpleFeatureCollection parcel = new DefaultFeatureCollection();
        // if a community information has been set
        if (communityNumber != null && !communityNumber.equals("")) {
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
                parcel = ParcelGetter.getParcelByCommunityCode(parcelIn, communityNumber);
            }
        }
        // if multiple communities are present in the parcel collection
        else if (ParcelAttribute.getCityCodesOfParcels(parcelIn).size() > 1) {
            communityNumbers.addAll(ParcelAttribute.getCityCodesOfParcels(parcelIn));
            ((DefaultFeatureCollection) parcel).addAll(parcelIn);
        }
        // if a type of community has been set
        else if (communityType != null && !communityType.equals("")) {
            communityNumbers.addAll(ParcelAttribute.getCityCodesOfParcels(parcelIn));
            parcel = ParcelGetter.getParcelByTypo(communityType, parcelIn, ZONINGFILE);
        }
        // if the input parcel is just what needs to be simulated
        else {
            communityNumbers.addAll(ParcelAttribute.getCityCodesOfParcels(parcelIn));
            parcel = parcelIn;
        }
        if (DEBUG)
            CollecMgmt.exportSFC(parcel, new File(OUTFOLDER, "selectedParcels"));
        // parcels have been selected - now is time to mark them
        // parcel marking with input polygons (disabled if we use a specific zone)
        if (POLYGONINTERSECTION != null && POLYGONINTERSECTION.exists() && !workflow.equals("zoneDivision"))
            parcel = MarkParcelAttributeFromPosition.markParcelIntersectPolygonIntersection(parcel, POLYGONINTERSECTION);
        SimpleFeatureCollection result = new DefaultFeatureCollection();
        // parcel marking with a zoning plan (possible to be hacked for any attribute feature selection by setting the field name to the genericZoning scenario parameter)
        if (ZONINGFILE != null && ZONINGFILE.exists() && genericZone != null && !genericZone.equals(""))
            // genericZone = FrenchZoningSchemas.normalizeNameFrenchBigZone(genericZone);
            // we proceed for each cities
            for (String communityNumber : communityNumbers) {
                SimpleFeatureCollection parcelCity = ParcelGetter.getParcelByCommunityCode(parcel, communityNumber);
                boolean alreadySimuled = false;
                String place = communityNumber + "-" + genericZone;
                // check the cache to see if zone have already been simulated
                for (String cachePlaceSimulates : cachePlacesSimulates)
                    if (cachePlaceSimulates.startsWith(place)) {
                        System.out.println("Warning: " + place + " already simulated");
                        alreadySimuled = true;
                        break;
                    }
                // if that zone has never been simulated, we proceed as usual
                if (!alreadySimuled)
                    // if a generic zone is set
                    //If no precise zone set
                    if (preciseZone == null || preciseZone.equals("")) {
                        ((DefaultFeatureCollection) result).addAll(MarkParcelAttributeFromPosition.markParcelIntersectGenericZoningType(parcelCity, genericZone, ZONINGFILE));
                        cachePlacesSimulates.add(place);
                    }
                    // if a specific zone is also set
                    else {
                        ((DefaultFeatureCollection) result).addAll(MarkParcelAttributeFromPosition.markParcelIntersectPreciseZoningType(parcelCity, genericZone, preciseZone, ZONINGFILE));
                        cachePlacesSimulates.add(place + "-" + preciseZone);
                    }
                    // the zone has already been simulated : must be a small part defined by the preciseZone field
                else {
                    // if a precise zone hasn't been specified
                    if (preciseZone == null || preciseZone.equals("")) {
                        // if previous zones have had a precise zone calculated, we list them
                        List<String> preciseZones = new ArrayList<>();
                        for (String pl : cachePlacesSimulates) {
                            if (pl.startsWith(place)) {
                                String[] p = pl.split("-");
                                if (p.length == 3 && pl.startsWith(place))
                                    preciseZones.add(p[2]);
                            }
                        }
                        // if we found specific precise zones, we exclude them from the marking session
                        if (!preciseZones.isEmpty()) {
                            ((DefaultFeatureCollection) result).addAll(MarkParcelAttributeFromPosition
                                    .markParcelIntersectZoningWithoutPreciseZonings(parcelCity, genericZone, preciseZones, ZONINGFILE));
                            System.out.println("sparedPreciseZones: " + preciseZones);
                        }
                        // if no precise zones have been found - this shouldn't happen - but we select zones with generic zoning
                        else {
                            System.out.println("no precise zones have been found - this shouldn't happend");
                            ((DefaultFeatureCollection) result).addAll(MarkParcelAttributeFromPosition.markParcelIntersectGenericZoningType(parcelCity, genericZone, ZONINGFILE));
                        }
                        cachePlacesSimulates.add(place);
                    }
                    // a precise zone has been specified : we mark them parcels
                    else {
                        ((DefaultFeatureCollection) result).addAll(MarkParcelAttributeFromPosition.markParcelIntersectPreciseZoningType(parcelCity, genericZone, preciseZone, ZONINGFILE));
                        cachePlacesSimulates.add(place + "-" + preciseZone);
                    }
                }
                if (parcelCity.isEmpty() || MarkParcelAttributeFromPosition.isNoParcelMarked(parcelCity)) {
                    cachePlacesSimulates.remove(place);
                    cachePlacesSimulates.remove(place + "-" + preciseZone);
                    //TODO find a proper way to remove the whole community?
                }
            }
        else
            result = parcel;
        // if the result is only zones, we return only the marked ones
        if (workflow.equals("zoneDivision")) {
            if (MarkParcelAttributeFromPosition.isNoParcelMarked(parcel))
                result = parcel;
            else
                result = MarkParcelAttributeFromPosition.getOnlyMarkedParcels(parcel);
        }

        if (workflow.equals("zoneDivision"))
            ParcelSchema.setParcelCommunityField(iniZoneCommunityName);
        return result;
    }

    /**
     * Generate the bound of the parcels that are simulated by the current PMStep. Uses the marked parcels by the {@link #getSimulationParcels(SimpleFeatureCollection)} method.
     *
     * @return A list of the geometries of the simulated parcels
     * @throws IOException
     */
    public List<Geometry> getBoundsOfZone() throws IOException {
        DataStore ds = CollecMgmt.getDataStore(PARCELFILE);
        List<Geometry> lG = new ArrayList<>();
        if (workflow.equals("zoneDivision")) {
            Arrays.stream(getZone(ds.getFeatureSource(ds.getTypeNames()[0]).getFeatures()).toArray(new SimpleFeature[0])).forEach(parcel -> lG.add((Geometry) parcel.getDefaultGeometry()));
        } else {
            Arrays.stream(getSimulationParcels(ds.getFeatureSource(ds.getTypeNames()[0]).getFeatures()).toArray(new SimpleFeature[0]))
                    .forEach(parcel -> {
                        if (parcel.getAttribute(MarkParcelAttributeFromPosition.getMarkFieldName()).equals(1))
                            lG.add((Geometry) parcel.getDefaultGeometry());
                    });
        }
        ds.dispose();
        return lG;
    }

    /**
     * Get the zones to simulate for the <i>Zone Division</i> workflow. If a specific zone input is set at the {@link #ZONE} location, it will automatically get and return it.
     *
     * @param parcel
     * @return
     * @throws IOException
     */
    private SimpleFeatureCollection getZone(SimpleFeatureCollection parcel) throws IOException {
        SimpleFeatureCollection zoneIn;
        // If a specific zone is an input, we take them directly. We also have to set attributes from pre-existing parcel field.
        if (ZONE != null && ZONE.exists()) {
            DataStore dsZone = CollecMgmt.getDataStore(ZONE);
            zoneIn = GeneralFields.transformSFCToMinParcel(dsZone.getFeatureSource(dsZone.getTypeNames()[0]).getFeatures(), parcel);
            dsZone.dispose();
        }
        // If no zone have been set, it means we have to use the zoning plan.
        else {
            DataStore dsZoning = CollecMgmt.getDataStore(ZONINGFILE);
            zoneIn = ZoneDivision.createZoneToCut(genericZone, preciseZone, dsZoning.getFeatureSource(dsZoning.getTypeNames()[0]).getFeatures(), parcel);
            dsZoning.dispose();
        }
        return zoneIn;
    }

    /**
     * Get a string describing the studied zone
     *
     * @return the description
     */
    public String getZoneStudied() {
        return workflow + "With" + parcelProcess + "On" + genericZone + "_" + preciseZone + "Of" + communityNumber;
    }

    @Override
    public String toString() {
        return "PMStep [workflow=" + workflow + ", parcelProcess=" + parcelProcess + ", communityNumber=" + communityNumber + ", communityType="
                + communityType + ", urbanFabricType=" + urbanFabricType + ", genericZone=" + genericZone + ", preciseZone=" + preciseZone + "]";
    }

    /**
     * The last generated parcel plan file. Could be useful for programs to get it directly
     *
     * @return lastOutput
     */
    public File getLastOutput() {
        return lastOutput;
    }

    public void setKeepExistingRoad(boolean keepExistingRoad) {
        this.keepExistingRoad = keepExistingRoad;
    }

    public File makeFileName() {
        return new File(OUTFOLDER, "parcelCuted-" + workflow + "-" + urbanFabricType + "-" + genericZone + "_" + preciseZone + CollecMgmt.getDefaultGISFileType());
    }
}
