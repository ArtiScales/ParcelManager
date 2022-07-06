package fr.ign.artiscales.pm.workflow;

import fr.ign.artiscales.pm.division.DivisionType;
import fr.ign.artiscales.pm.division.FlagDivision;
import fr.ign.artiscales.pm.division.OBBDivision;
import fr.ign.artiscales.pm.division.OBBThenSS;
import fr.ign.artiscales.pm.division.StraightSkeletonDivision;
import fr.ign.artiscales.pm.fields.GeneralFields;
import fr.ign.artiscales.pm.parcelFunction.MarkParcelAttributeFromPosition;
import fr.ign.artiscales.pm.parcelFunction.ParcelCollection;
import fr.ign.artiscales.pm.parcelFunction.ParcelSchema;
import fr.ign.artiscales.tools.FeaturePolygonizer;
import fr.ign.artiscales.tools.geoToolsFunctions.Attribute;
import fr.ign.artiscales.tools.geoToolsFunctions.Schemas;
import fr.ign.artiscales.tools.geoToolsFunctions.vectors.Geom;
import fr.ign.artiscales.tools.geoToolsFunctions.vectors.collec.CollecMgmt;
import fr.ign.artiscales.tools.geoToolsFunctions.vectors.collec.CollecTransform;
import fr.ign.artiscales.tools.geoToolsFunctions.vectors.collec.OpOnCollec;
import fr.ign.artiscales.tools.geoToolsFunctions.vectors.geom.Polygons;
import fr.ign.artiscales.tools.geometryGeneration.CityGeneration;
import fr.ign.artiscales.tools.parameter.ProfileUrbanFabric;
import org.apache.commons.math3.random.MersenneTwister;
import org.geotools.data.DataStore;
import org.geotools.data.DataUtilities;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.feature.DefaultFeatureCollection;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.PrecisionModel;
import org.locationtech.jts.precision.GeometryPrecisionReducer;
import org.opengis.feature.simple.SimpleFeature;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * This workflow operates on a zone rather than on parcels. Zones can either be taken from a zoning plan or from a ready-to-use zone collection (which can be made out of parcels).
 * Creation and integration of zone could be made with the {@link #createZoneToCut(String, SimpleFeatureCollection, SimpleFeatureCollection)} method. The parcel which are across the zone are cut
 * and the parts that aren't contained into the zone are kept with their attributes. The chosen parcel division process (OBB by default) is then applied on the zone.
 *
 * @author Maxime Colomb
 */
public class ZoneDivision extends Workflow {

    public ZoneDivision() {
    }

//    public static void main(String[] args) throws Exception {
//        File rootFolder = new File("src/main/resources/TestScenario/");
//        DataStore parcelDS = CollecMgmt.getDataStore(new File(rootFolder, "InputData/parcel.gpkg"));
//        DataStore zoningDS = CollecMgmt.getDataStore(new File(rootFolder, "InputData/zoning.gpkg"));
//        DataStore roadDS = CollecMgmt.getDataStore(new File(rootFolder, "InputData/road.gpkg"));
//        File outFolder = new File("/tmp");
//        setDEBUG(false);
//        Workflow.PROCESS = DivisionType.OBBThenSS;
//        SimpleFeatureCollection zone = createZoneToCut("AU", "AU1", zoningDS.getFeatureSource(zoningDS.getTypeNames()[0]).getFeatures(), parcelDS.getFeatureSource(parcelDS.getTypeNames()[0]).getFeatures());
//        CollecMgmt.exportSFC(zone, new File(outFolder, "zone"));
//        SimpleFeatureCollection z = new ZoneDivision().zoneDivision(zone, parcelDS.getFeatureSource(parcelDS.getTypeNames()[0]).getFeatures(), roadDS.getFeatureSource(roadDS.getTypeNames()[0]).getFeatures(), outFolder,
//                ProfileUrbanFabric.convertJSONtoProfile(new File("src/main/resources/TestScenario/profileUrbanFabric/mediumHouse.json")),
////                ProfileUrbanFabric.convertJSONtoProfile(new File("src/main/resources/TestScenario/profileUrbanFabric/smallHouse.json")),
//                true);
//        CollecMgmt.exportSFC(z, new File(outFolder, "result"));
//        zoningDS.dispose();
//    }

    /**
     * Create a zone to cut from a zoning plan by selecting features from a Geopackage regarding a fixed value. Name of the field is by default set to <i>TYPEZONE</i> and must be changed if needed
     * with the {@link fr.ign.artiscales.pm.fields.GeneralFields#setZoneGenericNameField(String)} method. Name of a Generic Zone is provided and can be null. If null, inputSFC is
     * usually directly a ready-to-use zone and all given zone are marked. Also takes a bounding {@link SimpleFeatureCollection} to bound the output.
     *
     * @param genericZone Name of the generic zone to be cut
     * @param inputSFC    Geopackage of zones to extract the wanted zone from (usually a zoning plan)
     * @param boundingSFC {@link SimpleFeatureCollection} to bound the process on a wanted location
     * @return An extraction of the zoning collection
     */
    public static SimpleFeatureCollection createZoneToCut(String genericZone, SimpleFeatureCollection inputSFC, SimpleFeatureCollection boundingSFC) {
        return createZoneToCut(genericZone, null, inputSFC, boundingSFC);
    }

    /**
     * Create a zone to cut from a zoning plan by selecting features from a Geopackage regarding a fixed value. Name of the field is by default set to <i>TYPEZONE</i> and must be changed if needed
     * with the {@link fr.ign.artiscales.pm.fields.GeneralFields#setZoneGenericNameField(String)} method. Name of a <i>generic zone</i> and a <i>precise Zone</i> can be provided
     * and can be null. If null, inputSFC is usually directly a ready-to-use zone and all given zone are marked. Also takes a bounding {@link SimpleFeatureCollection} to bound the
     * output.
     *
     * @param genericZone Name of the generic zone to be cut
     * @param preciseZone Name of the precise zone to be cut. Can be null
     * @param inputSFC    Geopackage of zones to extract the wanted zone from (usually a zoning plan)
     * @param boundingSFC {@link SimpleFeatureCollection} to bound the process on a wanted location
     * @return An extraction of the zoning collection
     */
    public static SimpleFeatureCollection createZoneToCut(String genericZone, String preciseZone, SimpleFeatureCollection inputSFC, SimpleFeatureCollection boundingSFC) {
        // get the wanted zones from the zoning file
        SimpleFeatureCollection finalZone;
        if (genericZone != null && !genericZone.equals(""))
            if (preciseZone == null || preciseZone.equals(""))
                finalZone = MarkParcelAttributeFromPosition.getOnlyMarkedParcels(MarkParcelAttributeFromPosition.markParcelWithAttribute(
                        CollecTransform.selectIntersection(inputSFC, Geom.safeUnion(boundingSFC)), GeneralFields.getZoneGenericNameField(), genericZone));
            else
                finalZone = MarkParcelAttributeFromPosition.getOnlyMarkedParcels(MarkParcelAttributeFromPosition.markParcelWithAttribute(
                        MarkParcelAttributeFromPosition.markParcelWithAttribute(
                                CollecTransform.selectIntersection(inputSFC, Geom.safeUnion(boundingSFC)), GeneralFields.getZoneGenericNameField(), genericZone),
                        GeneralFields.getZonePreciseNameField(), preciseZone));
        else
            finalZone = MarkParcelAttributeFromPosition
                    .getOnlyMarkedParcels(MarkParcelAttributeFromPosition.markAllParcel(CollecTransform.selectIntersection(inputSFC, Geom.safeUnion(boundingSFC))));
        if (finalZone != null && finalZone.isEmpty()) System.out.println("createZoneToCut(): zone is empty");
        return finalZone;
    }

    public SimpleFeatureCollection zoneDivision(File zoneFile, SimpleFeatureCollection parcelSFC, File roadFile, ProfileUrbanFabric profile, boolean keepExistingRoad, File outFolder) throws IOException {
        DataStore dsZone = CollecMgmt.getDataStore(zoneFile);
        SimpleFeatureCollection zone = dsZone.getFeatureSource(dsZone.getTypeNames()[0]).getFeatures();
        SimpleFeatureCollection result;
        if (roadFile == null) {
            result = zoneDivision(zone, parcelSFC, outFolder, profile, keepExistingRoad);
        } else {
            DataStore dsRoad = CollecMgmt.getDataStore(roadFile);
            result = zoneDivision(zone, parcelSFC, dsRoad.getFeatureSource(dsRoad.getTypeNames()[0]).getFeatures(), outFolder, profile, keepExistingRoad);
            dsRoad.dispose();
        }
        dsZone.dispose();
        return result;
    }

    /**
     * Method to use from fresh Geopackages. Mainly used by OpenMole tasks.
     *
     * @param zoneFile   Geopackage representing the zones to be cut
     * @param parcelFile Geopackage of the entire parcel plan of the area
     * @param profile    Urban fabric profile of the wanted parcel plan
     * @param outFolder  folder where everything is stored
     * @return the collection containing the cut parcel plan
     * @throws IOException from marking parcel
     */
    public SimpleFeatureCollection zoneDivision(File zoneFile, File parcelFile, File outFolder, ProfileUrbanFabric profile, File roadFile, File buildingFile) throws IOException {
        DataStore dsZone = CollecMgmt.getDataStore(zoneFile);
        DataStore dsParcel = CollecMgmt.getDataStore(parcelFile);
        SimpleFeatureCollection zone = DataUtilities.collection(dsZone.getFeatureSource(dsZone.getTypeNames()[0]).getFeatures());
        SimpleFeatureCollection parcel = DataUtilities.collection(dsParcel.getFeatureSource(dsParcel.getTypeNames()[0]).getFeatures());
        dsZone.dispose();
        dsParcel.dispose();
        if (roadFile != null)
            if (buildingFile != null)
                return zoneDivision(zone, parcel, outFolder, profile, roadFile, buildingFile, false);
            else {
                DataStore dsRoad = CollecMgmt.getDataStore(roadFile);
                SimpleFeatureCollection result = zoneDivision(zone, parcel, dsRoad.getFeatureSource(dsRoad.getTypeNames()[0]).getFeatures(), outFolder, profile, false);
                dsRoad.dispose();
                return result;
            }
        else
            return zoneDivision(zone, parcel, outFolder, profile, false);
    }

    /**
     * Merge and re-cut a specific zone. Cut first the surrounding parcels to keep them unsplit, then split the zone parcel and remerge them all into the original parcel file A bit
     * complicated algorithm to deal with non-existing pieces of parcels (as road).
     *
     * @param initialZone       Zone which will be used to cut parcels. Will cut parcels that intersects them and keep their infos. Will then optionally fill the empty spaces in between the zones and feed
     *                          it to the OBB algorithm.
     * @param keepExistingRoads If true, existing raod (lack of parcel in the parcel plan) will be kept. If not, the whole zone is simulated regardless of its content.
     * @param parcels           {@link SimpleFeatureCollection} of the unmarked parcels.
     * @param outFolder         folder to write {@link Workflow#isDEBUG()} and {@link Workflow#isSAVEINTERMEDIATERESULT()} geofile if concerned
     * @param profile           {@link ProfileUrbanFabric} contains the parameters of the wanted urban scene
     * @return The input parcel {@link SimpleFeatureCollection} with the marked parcels replaced by the simulated parcels.
     * @throws IOException from marking parcel
     */
    public SimpleFeatureCollection zoneDivision(SimpleFeatureCollection initialZone, SimpleFeatureCollection parcels, File outFolder, ProfileUrbanFabric profile, boolean keepExistingRoads) throws IOException {
        return zoneDivision(initialZone, parcels, null, outFolder, profile, keepExistingRoads);
    }

    /**
     * Merge and re-cut a specific zone. Cut first the surrounding parcels to keep them unsplit, then split the zone parcel and remerge them all into the original parcel file A bit
     * complicated algorithm to deal with non-existing pieces of parcels (as road).
     *
     * @param initialZone       Zone which will be used to cut parcels. Will cut parcels that intersects them and keep their infos. Will then optionally fill the empty spaces in between the zones and feed
     *                          it to the OBB algorithm.
     * @param keepExistingRoads If true, existing raod (lack of parcel in the parcel plan) will be kept. If not, the whole zone is simulated regardless of its content.
     * @param parcels           {@link SimpleFeatureCollection} of the unmarked parcels.
     * @param outFolder         folder to write {@link Workflow#isDEBUG()} and {@link Workflow#isSAVEINTERMEDIATERESULT()} geofile if concerned
     * @param profile           {@link ProfileUrbanFabric} contains the parameters of the wanted urban scene
     * @return The input parcel {@link SimpleFeatureCollection} with the marked parcels replaced by the simulated parcels.
     * All parcels have the same schema with a .
     * @throws IOException from marking parcel
     */
    public SimpleFeatureCollection zoneDivision(SimpleFeatureCollection initialZone, SimpleFeatureCollection parcels, File outFolder, ProfileUrbanFabric profile, File roadFile, File buildingFile, boolean keepExistingRoads) throws IOException {
        DataStore dsRoad = CollecMgmt.getDataStore(roadFile);
        DataStore dsBuilidng = CollecMgmt.getDataStore(buildingFile);
        SimpleFeatureCollection result = zoneDivision(initialZone, parcels, dsRoad.getFeatureSource(dsRoad.getTypeNames()[0]).getFeatures(), dsBuilidng.getFeatureSource(dsBuilidng.getTypeNames()[0]).getFeatures(), outFolder, profile, keepExistingRoads, CollecTransform.fromPolygonSFCtoListRingLines(parcels), CityGeneration.createBufferBorder(parcels));
        dsRoad.dispose();
        dsBuilidng.dispose();
        return result;
    }

    /**
     * Merge and recut a specific zone. Cut first the surrounding parcels to keep them unsplit, then split the zone parcel and remerge them all into the original parcel file A bit
     * complicated algorithm to deal with non-existing pieces of parcels (as road).
     *
     * @param initialZone       Zone which will be used to cut parcels. Will cut parcels that intersects them and keep their infos. Will then optionally fill the empty spaces in between the zones and feed
     *                          it to the OBB algorithm.
     * @param parcels           {@link SimpleFeatureCollection} of the unmarked parcels.
     * @param roads             Road features can be used in OBB process (optional)
     * @param keepExistingRoads If true, existing raod (lack of parcel in the parcel plan) will be kept. If not, the whole zone is simulated regardless of its content.
     * @param outFolder         folder to write {@link Workflow#isDEBUG()} and {@link Workflow#isSAVEINTERMEDIATERESULT()} geofile if concerned
     * @param profile           {@link ProfileUrbanFabric} contains the parameters of the wanted urban scene
     * @return The input parcel {@link SimpleFeatureCollection} with the marked parcels replaced by the simulated parcels.
     * @throws IOException from marking parcel
     */
    public SimpleFeatureCollection zoneDivision(SimpleFeatureCollection initialZone, SimpleFeatureCollection parcels, SimpleFeatureCollection roads, File outFolder, ProfileUrbanFabric profile, boolean keepExistingRoads) throws IOException {
        return zoneDivision(initialZone, parcels, roads, null, outFolder, profile, keepExistingRoads, null, null);
    }

    /**
     * Merge and recut a specific zone. Cut first the surrounding parcels to keep them unsplit, then split the zone parcel and remerge them all into the original parcel file A bit
     * complicated algorithm to deal with non-existing pieces of parcels (as road).
     *
     * @param initialZone       Zone which will be used to cut parcels. Will cut parcels that intersects them and keep their infos. Will then optionally fill the empty spaces in between the zones and feed
     *                          it to the OBB algorithm.
     * @param parcels           {@link SimpleFeatureCollection} of the unmarked parcels.
     * @param roads             Road features can be used in OBB process (optional)
     * @param keepExistingRoads If true, existing raod (lack of parcel in the parcel plan) will be kept. If not, the whole zone is simulated regardless of its content.
     * @param outFolder         folder to write {@link Workflow#isDEBUG()} and {@link Workflow#isSAVEINTERMEDIATERESULT()} geofile if concerned
     * @param profile           {@link ProfileUrbanFabric} contains the parameters of the wanted urban scene
     * @param buildings         for densification only (can be null)
     * @param exclusionZone     for densification only (can be null)
     * @param extLines          for densification only (can be null)
     * @return The input parcel {@link SimpleFeatureCollection} with the marked parcels replaced by the simulated parcels.
     * All parcels have the same schema as input parcels.
     * @throws IOException from marking parcel
     */
    public SimpleFeatureCollection zoneDivision(SimpleFeatureCollection initialZone, SimpleFeatureCollection parcels, SimpleFeatureCollection roads, SimpleFeatureCollection buildings, File outFolder, ProfileUrbanFabric profile, boolean keepExistingRoads, List<LineString> extLines, Geometry exclusionZone) throws IOException {
        File tmpFolder = new File(outFolder, "tmp");
        if (isDEBUG())
            tmpFolder.mkdirs();
        // parcel geometry name for all
        String geomName = parcels.getSchema().getGeometryDescriptor().getLocalName();
        checkFields(parcels.getSchema());
        final Geometry geomZone = Geom.safeUnion(initialZone);
        //setting final schema. If no split field at first, we don't add it in the final collection.
        final SimpleFeatureBuilder finalParcelBuilder = Schemas.isSchemaContainsAttribute(parcels.getSchema(), MarkParcelAttributeFromPosition.getMarkFieldName()) ?
                new SimpleFeatureBuilder(parcels.getSchema()) : ParcelSchema.getSFBWithoutSplit(parcels.getSchema());
        // sort in two different collections, the ones that matters and the ones that will be saved for future purposes
        DefaultFeatureCollection parcelsInZone = new DefaultFeatureCollection();
        // parcels to save for after and convert them to the minimal attribute
        DefaultFeatureCollection savedParcels = new DefaultFeatureCollection();
        Arrays.stream(parcels.toArray(new SimpleFeature[0])).forEach(parcel -> {
            if (((Geometry) parcel.getDefaultGeometry()).intersects(geomZone))
                parcelsInZone.add(parcel);
            else
                savedParcels.add(parcel);
        });
        if (isDEBUG())
            CollecMgmt.exportSFC(parcelsInZone, new File(tmpFolder, "parcelsInZone"));
        //We prepare the zones
        int numZone = 0;
        DefaultFeatureCollection goOdZone = new DefaultFeatureCollection();
        if (keepExistingRoads) {// select zone that covers parcel rather than the actual zone.
            Geometry unionParcel = Geom.safeUnion(parcels);
            SimpleFeatureBuilder sfBuilder = ParcelSchema.getSFBMinParcelSplit();
            try (SimpleFeatureIterator zoneIt = initialZone.features()) {
                while (zoneIt.hasNext()) {
                    numZone++;
                    SimpleFeature zone = zoneIt.next();
                    // avoid most of tricky geometry problems
                    Geometry intersection = Geom.safeIntersection(Arrays.asList(((Geometry) zone.getDefaultGeometry()), unionParcel));
                    if (!intersection.isEmpty()) {
                        List<Polygon> geomsZone = Polygons.getPolygons(intersection);
                        for (Geometry geomPartZone : geomsZone) {
                            Geometry geom = GeometryPrecisionReducer.reduce(geomPartZone, new PrecisionModel(100));
                            // avoid silvers (plants the code)
                            if (geom.getArea() > 10) {
                                sfBuilder.set(geomName, geom);
                                sfBuilder.set(ParcelSchema.getParcelSectionField(), makeNewSection(String.valueOf(numZone)));
                                sfBuilder.set(MarkParcelAttributeFromPosition.getMarkFieldName(), 1);
//                                sfBuilder.set(ParcelSchema.getMinParcelCommunityField(), zone.getAttribute(ParcelSchema.getMinParcelCommunityField()));
                                goOdZone.add(sfBuilder.buildFeature(Attribute.makeUniqueId()));
                            }
                        }
                    }
                }
            } catch (Exception problem) {
                problem.printStackTrace();
            }
            // zone verification
            if (goOdZone.isEmpty() || OpOnCollec.area(goOdZone) < profile.getMinimalArea()) {
                System.out.println("ZoneDivision: no zones to cut or zone is too small to be taken into consideration");
                return parcels;
            }
        } else { // we mark and add all zones
            SimpleFeatureBuilder sfBuilder = ParcelSchema.getSFBMinParcelSplit();
            try (SimpleFeatureIterator zoneIt = initialZone.features()) {
                while (zoneIt.hasNext()) {
                    sfBuilder.set(geomName, zoneIt.next().getDefaultGeometry());
                    sfBuilder.set(ParcelSchema.getParcelSectionField(), makeNewSection(String.valueOf(numZone++)));
                    sfBuilder.set(MarkParcelAttributeFromPosition.getMarkFieldName(), 1);
                    goOdZone.add(sfBuilder.buildFeature(Attribute.makeUniqueId()));
                }
            } catch (Exception problem) {
                problem.printStackTrace();
            }
        }
        // parts of parcel outside the zone must not be cut by the algorithm and keep their attributes
        List<Geometry> geomList = Arrays.stream(parcelsInZone.toArray(new SimpleFeature[0]))
                .map(x -> (Geometry) x.getDefaultGeometry()).collect(Collectors.toList());
        geomList.addAll(Arrays.stream(goOdZone.toArray(new SimpleFeature[0])).map(x -> (Geometry) x.getDefaultGeometry()).collect(Collectors.toList()));
        List<Polygon> polygons = FeaturePolygonizer.getPolygons(geomList);
        Geometry geomSelectedZone = Geom.safeUnion(goOdZone);
        if (isDEBUG()) {
            Geom.exportGeom(geomSelectedZone, new File(tmpFolder, "geomSelectedZone"));
            Geom.exportGeom(polygons, new File(tmpFolder, "polygons"));
            System.out.println("geomz and polygonz exported");
        }
        // Big loop on each generated geometry to save the parts that are not contained in the zones.
        // We add them to the savedParcels collection.
        for (Geometry poly : polygons) {
            // if the polygons are not included on the zone, we check to which parcel do they belong
            if (!geomSelectedZone.buffer(0.01).contains(poly)) {
                try (SimpleFeatureIterator parcelIt = parcelsInZone.features()) {
                    while (parcelIt.hasNext()) {
                        SimpleFeature feat = parcelIt.next();
                        // if that original parcel contains that piece of parcel, we copy the previous parcels information
                        if (((Geometry) feat.getDefaultGeometry()).buffer(0.01).contains(poly)) {
                            Schemas.setFieldsToSFB(finalParcelBuilder, feat);
                            finalParcelBuilder.set(geomName, poly);
                            savedParcels.add(finalParcelBuilder.buildFeature(Attribute.makeUniqueId()));
                        }
                    }
                } catch (Exception problem) {
                    problem.printStackTrace();
                }
            }
        }
        if (isDEBUG())
            CollecMgmt.exportSFC(savedParcels, new File(tmpFolder, "parcelsSaved"));
        // Parcel subdivision
        SimpleFeatureCollection splitParcels = new DefaultFeatureCollection();
        SimpleFeatureCollection blockCollection = CityGeneration.createUrbanBlock(parcels);
        try (SimpleFeatureIterator it = goOdZone.features()) {
            while (it.hasNext()) {
                SimpleFeature zone = it.next();
                switch (PROCESS) {
                    case OBB:
                        ((DefaultFeatureCollection) splitParcels)
                                .addAll(OBBDivision.splitParcel(zone, roads, profile.getMaximalArea(), profile.getMinimalWidthContactRoad(), profile.getHarmonyCoeff(), profile.getIrregularityCoeff(),
                                        CollecTransform.fromPolygonSFCtoListRingLines(CollecTransform.selectIntersection(blockCollection, (Geometry) zone.getDefaultGeometry())),
                                        profile.getLaneWidth(), profile.getStreetLane(), profile.getStreetWidth(), true, profile.getBlockShape()));
                        break;
                    case SS:
                    case SSoffset:
                        StraightSkeletonDivision.FOLDER_OUT_DEBUG = tmpFolder;
                        ((DefaultFeatureCollection) splitParcels)
                                .addAll(StraightSkeletonDivision.runTopologicalStraightSkeletonParcelDecomposition(zone, roads,
                                        "NOM_VOIE_G", "IMPORTANCE", PROCESS.equals(DivisionType.SSoffset) ? profile.getMaxDepth() : 0, profile.getMaxDistanceForNearestRoad(), profile.getMinimalArea(), profile.getMinimalWidthContactRoad(), profile.getMaxWidth(),
                                        profile.getIrregularityCoeff() == 0 ? 0.1 : profile.getIrregularityCoeff(), new MersenneTwister(42), profile.getLaneWidth(), ParcelSchema.getParcelID(zone)));
                        break;
                    case OBBThenSS:
                        ((DefaultFeatureCollection) splitParcels)
                                .addAll(OBBThenSS.applyOBBThenSS(zone,
                                        roads == null || roads.isEmpty() ? null : CollecTransform.selectIntersection(roads, ((Geometry) zone.getDefaultGeometry()).buffer(30))
                                        , profile, CollecTransform.fromPolygonSFCtoListRingLines(CollecTransform.selectIntersection(blockCollection, (Geometry) zone.getDefaultGeometry()))));
                        break;
                    case FlagDivision:
                        ((DefaultFeatureCollection) splitParcels)
                                .addAll(FlagDivision.doFlagDivision(zone, roads, buildings, profile.getHarmonyCoeff(), profile.getIrregularityCoeff(),
                                        profile.getMaximalArea(), profile.getMinimalWidthContactRoad(), profile.getLenDriveway(), extLines, exclusionZone));
                        break;
                    case MS:
                        System.out.println("not implemented yet");
                        break;
                }
            }
        } catch (Exception problem) {
            problem.printStackTrace();
        }
        if (isDEBUG()) {
            CollecMgmt.exportSFC(splitParcels, new File(tmpFolder, "freshSplitedParcels"));
            System.out.println("fresh cuted parcels exported");
        }
        // merge the small parcels to bigger ones
        splitParcels = ParcelCollection.mergeTooSmallParcels(splitParcels, profile.getMinimalArea(), PROCESS.equals(DivisionType.SS));
        DefaultFeatureCollection result = new DefaultFeatureCollection();
        int num = 0;
        // set attribute for the simulated parcels
        try (SimpleFeatureIterator itParcel = splitParcels.features()) {
            while (itParcel.hasNext()) {
                SimpleFeature parcel = itParcel.next();
                Geometry parcelGeom = (Geometry) parcel.getDefaultGeometry();
                Schemas.setFieldsToSFB(finalParcelBuilder, CollecTransform.selectWhichIntersectMost(parcels, parcelGeom));
                finalParcelBuilder.set(geomName, parcelGeom);
                // get the section name of the corresponding zone
                finalParcelBuilder.set(ParcelSchema.getParcelSectionField(), Arrays.stream(goOdZone.toArray(new SimpleFeature[0])).filter(z -> ((Geometry) z.getDefaultGeometry()).buffer(2).contains(parcelGeom)).map(z -> (String) z.getAttribute(ParcelSchema.getParcelSectionField())).findFirst().orElse(""));
                finalParcelBuilder.set(ParcelSchema.getParcelCommunityField(), Arrays.stream(parcels.toArray(new SimpleFeature[0])).filter(g -> ((Geometry) g.getDefaultGeometry()).intersects((Geometry) parcel.getDefaultGeometry())).findFirst().get().getAttribute(ParcelSchema.getParcelCommunityField()));
                finalParcelBuilder.set(ParcelSchema.getParcelNumberField(), String.valueOf(num++));
                result.add(finalParcelBuilder.buildFeature(Attribute.makeUniqueId()));
            }
        } catch (Exception problem) {
            problem.printStackTrace();
        }
        if (isDEBUG())
            CollecMgmt.exportSFC(result, new File(tmpFolder, "parcelZoneDivisionOnly"), false);
        if (isSAVEINTERMEDIATERESULT()) {
            CollecMgmt.exportSFC(result, new File(outFolder, "parcelZoneDivisionOnly"), OVERWRITEGEOPACKAGE);
            OVERWRITEGEOPACKAGE = false;
        }
        // add the saved parcels
        try (SimpleFeatureIterator itSavedParcels = savedParcels.features()) {
            while (itSavedParcels.hasNext())
                result.add(itSavedParcels.next());
        } catch (Exception problem) {
            problem.printStackTrace();
        }
        return result;
    }

    /**
     * Create a new section name following a precise rule.
     *
     * @param numZone number of the nex zone
     * @return the section's name
     */
    public String makeNewSection(String numZone) {
        return "New" + numZone + "Section";
    }

    /**
     * Check if the input {@link SimpleFeature} has a section field that has been simulated with this present workflow.
     *
     * @param feat {@link SimpleFeature} to test.
     * @return true if the section field is marked with the {@link #makeNewSection(String)} method.
     */
    public boolean isNewSection(SimpleFeature feat) {
        String section = (String) feat.getAttribute(ParcelSchema.getParcelSectionField());
        return section.startsWith("New") && section.endsWith("Section");
    }
}