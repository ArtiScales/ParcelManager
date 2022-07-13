package fr.ign.artiscales.pm.scenario;

import fr.ign.artiscales.pm.analysis.RealUrbanFabricParameters;
import fr.ign.artiscales.pm.division.DivisionType;
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
import fr.ign.artiscales.pm.workflow.WorkflowType;
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
import java.util.Collections;
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

    private static boolean allowIsolatedParcel = false;
    private static boolean peripheralRoad;
    final private WorkflowType workflow;
    final private DivisionType parcelProcess;
    final private String communityNumber, communityType, urbanFabricType, genericZone, preciseZone, selection;
    List<String> communityNumbers = new ArrayList<>();
    private ProfileUrbanFabric profile;
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

    public PMStep(String workflow, String parcelProcess, String genericZone, String preciseZone, String communityNumber, String communityType, String urbanFabricType, String selection, boolean peripheralRoad, boolean keepExistingRoad, boolean adaptUrbanFabric) {
        this.workflow = WorkflowType.valueOf(workflow);
        if (this.workflow.equals(WorkflowType.densification) || this.workflow.equals(WorkflowType.densificationStudy) || this.workflow.equals(WorkflowType.densificationOrNeighborhood)) //needless to say but for compilation
            this.parcelProcess = DivisionType.FlagDivision;
        else
            this.parcelProcess = DivisionType.valueOf(parcelProcess);
        this.genericZone = genericZone;
        this.preciseZone = preciseZone;
        this.communityNumber = communityNumber;
        this.communityType = communityType;
        this.urbanFabricType = urbanFabricType;
        this.selection = selection;
        setAdaptAreaOfUrbanFabric(adaptUrbanFabric);
        setKeepExistingRoad(keepExistingRoad);
        setPeripheralRoad(peripheralRoad);
    }

    public static File getOUTFOLDER() {
        return OUTFOLDER;
    }

    public static void setOUTFOLDER(File OUTFOLDER) {
        PMStep.OUTFOLDER = OUTFOLDER;
    }

    /**
     * Set the path of the different files for a PMStep to be executed. The method is used by PMScenario in a static way because it has no reasons to change within a PM simulation,
     * except for the parcel file that must be updated after each PMStep to make the new PMStep simulation on an already simulated parcel plan.
     *
     * @param parcelFile          geofiles containing parcel plan for the whole scenario
     * @param zoningFile          geofiles containing zoning plan for the whole scenario
     * @param buildingFile        geofiles containing buildings for the whole scenario
     * @param roadFile            geofiles containing roads for the whole scenario
     * @param predicateFile       particular urban rules which could be applied on specific processes. Can be null.
     * @param polygonIntersection polygons used for parcel selection. Can be null.
     * @param zone                specific zones to simulate the reshaping as a whole. Can ben null
     * @param outFolder           folder where every result are written.
     * @param profileFolder       folder containing parameter rules
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
     * Question if the flag division process allows the creation of porch or flag parcels isolated from the kown road network.
     *
     * @return Is the simulation of new parcels isolated from the road allowed ?
     */
    public static boolean isAllowIsolatedParcel() {
        return allowIsolatedParcel;
    }

    /**
     * Set if the flag division process allows the creation of porch or flag parcels isolated from the kown road network.
     *
     * @param allowIsolatedParcel Can we simulate new parcels isolated from the road ?
     */

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

    /**
     * Are we generating peripheral road around zones to be reshaped in the {@link StraightSkeletonDivision} process ?
     *
     * @return true if we do the generation
     */
    public boolean isPeripheralRoad() {
        return peripheralRoad;
    }

    /**
     * @param newPeripheralRoad Do we need to generate peripheral road around zones to be reshaped in the {@link StraightSkeletonDivision} process ?
     */
    public void setPeripheralRoad(boolean newPeripheralRoad) {
        peripheralRoad = newPeripheralRoad;
    }

    public boolean isAdaptAreaOfUrbanFabric() {
        return adaptAreaOfUrbanFabric;
    }

    public void setAdaptAreaOfUrbanFabric(boolean newAdaptAreaOfUrbanFabric) {
        adaptAreaOfUrbanFabric = newAdaptAreaOfUrbanFabric;
    }

    /**
     * @return the name of the urbanFabricType used by this current step.
     */
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
        // get the wanted building profile
        profile = ProfileUrbanFabric.convertJSONtoProfile(new File(PROFILEFOLDER + "/" + urbanFabricType + ".json"));
        StraightSkeletonDivision.setGeneratePeripheralRoad(isPeripheralRoad());
        //convert the parcel to a common type
        DataStore dSParcel = CollecMgmt.getDataStore(PARCELFILE);
        SimpleFeatureCollection parcel = DataUtilities.collection(dSParcel.getFeatureSource(dSParcel.getTypeNames()[0]).getFeatures());
        dSParcel.dispose();

        // mark (select) the parcels
        SimpleFeatureCollection parcelMarked;
        //if we work with zones, we put them as parcel input
        if (workflow.equals(WorkflowType.zoneDivision))
            parcelMarked = getSimulationParcels(getZone(parcel));
        else
            parcelMarked = getSimulationParcels(parcel);
        if (PMScenario.isDEBUG()) {
            System.out.println("parcels marked with " + MarkParcelAttributeFromPosition.countMarkedParcels(parcelMarked) + " marks");
            File tmpFolder = new File(OUTFOLDER, "tmp");
            tmpFolder.mkdirs();
            CollecMgmt.exportSFC(parcelMarked, new File(tmpFolder, "parcelMarked" + this.workflow + "-" + this.parcelProcess.toString() + this.preciseZone));
        }
        DefaultFeatureCollection parcelCut = new DefaultFeatureCollection();
        // in case of lot of cities to simulate, we separate the execution of PM simulations for each community
        for (String communityNumber : communityNumbers) {
            System.out.println("for community " + communityNumber);
            SimpleFeatureCollection parcelMarkedComm = ParcelGetter.getParcelByCommunityCode(parcelMarked, communityNumber);
            if (parcelMarkedComm.size() == 0) {
                System.out.println("No parcels for community " + communityNumber);
                continue;
            }
            //if we adapt parcel's area to the community
            if (isAdaptAreaOfUrbanFabric()) {
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
            if (PREDICATEFILE != null && PREDICATEFILE.exists())
                setAllowIsolatedParcel(ParcelState.isArt3AllowsIsolatedParcel(parcel.features().next(), PREDICATEFILE));
            // we choose one of the different workflows
            switch (workflow) {
                case zoneDivision:
                    ZoneDivision.PROCESS = parcelProcess;
                    parcelCut.addAll((new ZoneDivision()).zoneDivision(parcelMarkedComm,
                            ParcelGetter.getParcelByCommunityCode(parcel, communityNumber), OUTFOLDER, profile, ROADFILE, BUILDINGFILE, isKeepExistingRoad()));
                    break;
                case densification:
                    parcelCut.addAll((new Densification()).densification(parcelMarkedComm,
                            CityGeneration.createUrbanBlock(parcelMarkedComm), OUTFOLDER, BUILDINGFILE, ROADFILE, profile.getHarmonyCoeff(),
                            profile.getIrregularityCoeff(), profile.getMaximalArea(), profile.getMinimalArea(), profile.getMinimalWidthContactRoad(),
                            profile.getDrivewayWidth(), isAllowIsolatedParcel(), CityGeneration.createBufferBorder(parcelMarkedComm)));
                    break;
                case consolidationDivision:
                    ConsolidationDivision.PROCESS = parcelProcess;
                    //there was a problem here when adding different collections - hack to use my custom method
                    SimpleFeatureCollection cut = (new ConsolidationDivision()).consolidationDivision(parcelMarkedComm, ROADFILE, OUTFOLDER, profile);
                    List<SimpleFeatureCollection> lCol = parcelCut.isEmpty() ? Collections.singletonList(cut) : Arrays.asList(parcelCut, cut);
                    parcelCut = (DefaultFeatureCollection) CollecMgmt.mergeSFC(lCol, true, null);
//                    parcelCut.addAll((new ConsolidationDivision()).consolidationDivision(parcelMarkedComm, ROADFILE, OUTFOLDER, profile));
                    break;
                case densificationStudy:
                    DensificationStudy.runDensificationStudy(parcelMarkedComm, BUILDINGFILE, ROADFILE, ZONINGFILE, OUTFOLDER, isAllowIsolatedParcel(), profile);
                    break;
                default:
                    System.out.println(workflow + ": unrecognized workflow");
            }
        }
        // we add the parcels from the communities that haven't been simulated
        for (String communityCode : ParcelAttribute.getCityCodesOfParcels(parcel))
            if (!communityNumbers.contains(communityCode))
                parcelCut.addAll(ParcelGetter.getParcelByCommunityCode(parcel, communityCode));

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
     * Select and mark the parcels that must be simulated within a collection of parcels.
     * It first selects the parcel of the zone studied, whether by a city code or by a zone type. The fields can be set with the setters of the {@link fr.ign.artiscales.pm.parcelFunction.ParcelGetter} class.
     * Then it marks the interesting parcels that either cross a given polygon collection, intersects a zoning type, or any other variable defined in the {@link #selection} field.
     * It returns every parcels selected. Split field name in "SPLIT" by default and can be changed with the method {@link fr.ign.artiscales.pm.parcelFunction.MarkParcelAttributeFromPosition#setMarkFieldName(String)}.
     * If none of this information are set, the algorithm selects all parcels and mark nothing.
     *
     * @param parcelIn total input parcel
     * @return The parcel collection with a mark for the interesting parcels to simulate.
     * @throws IOException reading a lot of files
     */
    public SimpleFeatureCollection getSimulationParcels(SimpleFeatureCollection parcelIn) throws IOException {
        if (workflow.equals(WorkflowType.zoneDivision))
            ParcelSchema.setParcelCommunityField(GeneralFields.getZoneCommunityCode());
        // select the parcels from the interesting communities
        SimpleFeatureCollection parcel = new DefaultFeatureCollection();
        if (communityNumber != null && !communityNumber.equals("")) { // if a community information has been set
            if (communityNumber.contains(",")) { // if a list of community has been set, the numbers must be separated with
                for (String z : communityNumber.split(",")) { // we select parcels from every zipcode
                    communityNumbers.add(z);
                    ((DefaultFeatureCollection) parcel).addAll(ParcelGetter.getParcelByCommunityCode(parcelIn, z));
                }
            } else { // if a single community number is set
                communityNumbers.add(communityNumber);
                parcel = ParcelGetter.getParcelByCommunityCode(parcelIn, communityNumber);
            }
        } else if (communityType != null && !communityType.equals("")) {        // if a type of community has been set
            communityNumbers.addAll(ParcelAttribute.getCityCodesOfParcels(parcelIn));
            parcel = ParcelGetter.getParcelByTypo(communityType, parcelIn, ZONINGFILE);
        } else { // if the input parcel is just what needs to be simulated, we put them all
            communityNumbers.addAll(ParcelAttribute.getCityCodesOfParcels(parcelIn));
            parcel = parcelIn;
        }
        if (PMScenario.isDEBUG())
            CollecMgmt.exportSFC(parcel, new File(OUTFOLDER, "selectedParcels"));
        // parcels have been selected - now is time to mark them
        // parcel marking with a special rule
        if (selection != null && selection != "" && !workflow.equals(WorkflowType.zoneDivision))
            switch (selection.split(",")[0]) {
                case "parcelSmallerRatio":
                    parcel = MarkParcelAttributeFromPosition.markParcelsInf(parcel, profile.getMaximalArea() * Double.parseDouble(selection.split(",")[1]));
                    break;
                case "parcelSmaller":
                    parcel = MarkParcelAttributeFromPosition.markParcelsInf(parcel, Double.parseDouble(selection.split(",")[1]));
                    break;
                case "parcelBiggerRatio":
                    parcel = MarkParcelAttributeFromPosition.markParcelsSup(parcel, profile.getMaximalArea() * Double.parseDouble(selection.split(",")[1]));
                    break;
                case "parcelBigger":
                    parcel = MarkParcelAttributeFromPosition.markParcelsSup(parcel, Double.parseDouble(selection.split(",")[1]));
                    break;
                default:
                    System.out.println("getSimulationParcels() : selection type not implemented (yet)");
            }
        // parcel marking with input polygons (disabled if we use a specific zone)
        if (POLYGONINTERSECTION != null && POLYGONINTERSECTION.exists() && !workflow.equals(WorkflowType.zoneDivision))
            parcel = MarkParcelAttributeFromPosition.markParcelIntersectPolygonIntersection(parcel, POLYGONINTERSECTION);
        SimpleFeatureCollection result = new DefaultFeatureCollection();
        // parcel marking with a zoning plan (possible to be hacked for any attribute feature selection by setting the field name to the genericZoning scenario parameter)
        if (ZONINGFILE != null && ZONINGFILE.exists() && genericZone != null && !genericZone.equals(""))
            for (String communityNumber : communityNumbers) { // we proceed for each city
                SimpleFeatureCollection parcelCity = ParcelGetter.getParcelByCommunityCode(parcel, communityNumber);
                boolean alreadySimuled = false;
                String place = communityNumber + "-" + genericZone;
                for (String cachePlaceSimulates : cachePlacesSimulates) // check the cache to see if zone have already been simulated
                    if (cachePlaceSimulates.startsWith(place)) {
                        System.out.println("Warning: " + place + " already simulated");
                        alreadySimuled = true;
                        break;
                    }
                if (!alreadySimuled) // if that zone has never been simulated, we proceed as usual
                    if (preciseZone == null || preciseZone.equals("")) { // if a generic zone is set and no precise zone set
                        ((DefaultFeatureCollection) result).addAll(MarkParcelAttributeFromPosition.markParcelIntersectGenericZoningType(parcelCity, genericZone, ZONINGFILE));
                        cachePlacesSimulates.add(place);
                    } else { // if a generic zone and a precise zone are set
                        ((DefaultFeatureCollection) result).addAll(MarkParcelAttributeFromPosition.markParcelIntersectPreciseZoningType(parcelCity, genericZone, preciseZone, ZONINGFILE));
                        cachePlacesSimulates.add(place + "-" + preciseZone);
                    }
                else { // the zone has already been simulated, we isolate a small part defined by the preciseZone field
                    if (preciseZone == null || preciseZone.equals("")) { // if a precise zone hasn't been specified
                        // if previous zones have had a precise zone calculated, we list them
                        List<String> preciseZones = new ArrayList<>();
                        for (String pl : cachePlacesSimulates)
                            if (pl.startsWith(place)) {
                                String[] p = pl.split("-");
                                if (p.length == 3)
                                    preciseZones.add(p[2]);
                            }
                        if (!preciseZones.isEmpty()) { // if we found specific precise zones that has been simulated, we exclude them from the marking session
                            ((DefaultFeatureCollection) result).addAll(MarkParcelAttributeFromPosition.markParcelIntersectZoningWithoutPreciseZonings(parcelCity, genericZone, preciseZones, ZONINGFILE));
                            if (PMScenario.isDEBUG())
                                System.out.println("sparedPreciseZones: " + preciseZones);
                        } else { // if no precise zones have been found - this shouldn't happen - but we select zones with generic zoning
                            ((DefaultFeatureCollection) result).addAll(MarkParcelAttributeFromPosition.markParcelIntersectGenericZoningType(parcelCity, genericZone, ZONINGFILE));
                            if (PMScenario.isDEBUG())
                                System.out.println("no precise zones have been found and generic zone " + genericZone + " has already been simulated - this shouldn't happen");
                        }
                        cachePlacesSimulates.add(place);
                    } else { // a precise zone has been specified : we mark them parcels
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
        if (workflow.equals(WorkflowType.zoneDivision)) {
            if (MarkParcelAttributeFromPosition.isNoParcelMarked(parcel))
                result = parcel;
            else
                result = MarkParcelAttributeFromPosition.getOnlyMarkedParcels(parcel);
        }
        // special case where zoneDivision will return other than parcel
        if (workflow.equals(WorkflowType.zoneDivision))
            ParcelSchema.setParcelCommunityField(ParcelSchema.getParcelCommunityField());
        return result;
    }

    /**
     * Generate the bound of the parcels that are simulated by the current PMStep. Uses the marked parcels by the {@link #getSimulationParcels(SimpleFeatureCollection)} method.
     *
     * @return A list of the geometries of the simulated parcels
     * @throws IOException reading geo files
     */
    public List<Geometry> getBoundsOfZone() throws IOException {
        DataStore ds = CollecMgmt.getDataStore(PARCELFILE);
        List<Geometry> lG = new ArrayList<>();
        if (workflow.equals(WorkflowType.zoneDivision)) {
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
     * @param parcel input parcel plan
     * @return the zone to be divided
     * @throws IOException reading zoning geo file
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

    /**
     * It is possible for {@link ZoneDivision} workflow to either work on the integrality of the concerned zone or to keep the parts where parcel doesn't exist and left it as a public space (road in most cases).
     *
     * @return Are we keeping already existing road on ZoneDivision processes?
     */
    public boolean isKeepExistingRoad() {
        return keepExistingRoad;
    }

    /**
     * It is possible for {@link ZoneDivision} workflow to either work on the integrality of the concerned zone or to keep the parts where parcel doesn't exist and left it as a public space (road in most cases).
     *
     * @param keepExistingRoad Do we need to keep already existing road on ZoneDivision processes?
     */
    public void setKeepExistingRoad(boolean keepExistingRoad) {
        this.keepExistingRoad = keepExistingRoad;
    }

    /**
     * creates a name for a geo file to be written
     *
     * @return every parameter is parsed into the returned string
     */
    public File makeFileName() {
        return new File(OUTFOLDER, "parcelCuted-" + workflow + "-" + urbanFabricType + "-" + genericZone + "_" + preciseZone + CollecMgmt.getDefaultGISFileType());
    }
}
