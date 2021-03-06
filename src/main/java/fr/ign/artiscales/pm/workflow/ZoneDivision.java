package fr.ign.artiscales.pm.workflow;

import fr.ign.artiscales.pm.division.OBBDivision;
import fr.ign.artiscales.pm.division.StraightSkeletonDivision;
import fr.ign.artiscales.pm.parcelFunction.MarkParcelAttributeFromPosition;
import fr.ign.artiscales.pm.parcelFunction.ParcelAttribute;
import fr.ign.artiscales.pm.parcelFunction.ParcelCollection;
import fr.ign.artiscales.pm.parcelFunction.ParcelSchema;
import fr.ign.artiscales.tools.FeaturePolygonizer;
import fr.ign.artiscales.tools.geoToolsFunctions.Attribute;
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
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.PrecisionModel;
import org.locationtech.jts.precision.GeometryPrecisionReducer;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.AttributeDescriptor;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * This workflow operates on a zone rather than on parcels. Zones can either be taken from a zoning plan or from a ready-to-use zone collection (which can be made out of parcels).
 * Creation and integration of zone could be made with the {@link #createZoneToCut(String, SimpleFeatureCollection, File, SimpleFeatureCollection)} method. The parcel which are across the zone are cut
 * and the parts that aren't contained into the zone are kept with their attributes. The chosen parcel division process (OBB by default) is then applied on the zone.
 *
 * @author Maxime Colomb
 */
public class ZoneDivision extends Workflow {

    public ZoneDivision() {
    }

//	public static void main(String[] args) throws Exception {
//		File outFile = new File("/tmp/");
//		outFile.mkdirs();
//		File simuledFile = (new ZoneDivision()).zoneDivision(
//				new File("parcelmanagergui/tmp/zone.gpkg"),
//				new File(parcelmanagergui/tmp/parcels.gpkg"),
//				ProfileUrbanFabric.convertJSONtoProfile(new File(
//						"src/main/resources/ParcelComparison/profileUrbanFabric/mediumCollective.json")), false, outFile);
//	}

    /**
     * Create a zone to cut from a zoning plan by selecting features from a Geopackage regarding a fixed value. Name of the field is by default set to <i>TYPEZONE</i> and must be changed if needed
     * with the {@link fr.ign.artiscales.pm.fields.GeneralFields#setZoneGenericNameField(String)} method. Name of a Generic Zone is provided and can be null. If null, inputSFC is
     * usually directly a ready-to-use zone and all given zone are marked. Also takes a bounding {@link SimpleFeatureCollection} to bound the output.
     *
     * @param genericZone Name of the generic zone to be cut
     * @param inputSFC    Geopackage of zones to extract the wanted zone from (usually a zoning plan)
     * @param zoningFile  The File containing the zoning plan (can be null if no zoning plan is planned to be used)
     * @param boundingSFC {@link SimpleFeatureCollection} to bound the process on a wanted location
     * @return An extraction of the zoning collection
     * @throws IOException from marking parcel
     */
    public static SimpleFeatureCollection createZoneToCut(String genericZone, SimpleFeatureCollection inputSFC, File zoningFile, SimpleFeatureCollection boundingSFC) throws IOException {
        return createZoneToCut(genericZone, null, inputSFC, zoningFile, boundingSFC);
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
     * @param zoningFile  The File containing the zoning plan (can be null if no zoning plan is planned to be used)
     * @param boundingSFC {@link SimpleFeatureCollection} to bound the process on a wanted location
     * @return An extraction of the zoning collection
     * @throws IOException from marking parcel
     */
    public static SimpleFeatureCollection createZoneToCut(String genericZone, String preciseZone, SimpleFeatureCollection inputSFC, File zoningFile, SimpleFeatureCollection boundingSFC) throws IOException {
        // get the wanted zones from the zoning file
        SimpleFeatureCollection finalZone;
        if (genericZone != null && !genericZone.equals(""))
            if (preciseZone == null || preciseZone.equals(""))
                finalZone = MarkParcelAttributeFromPosition.getOnlyMarkedParcels(MarkParcelAttributeFromPosition
                        .markParcelIntersectGenericZoningType(CollecTransform.selectIntersection(inputSFC, Geom.unionSFC(boundingSFC)), genericZone, zoningFile));
            else
                finalZone = MarkParcelAttributeFromPosition.getOnlyMarkedParcels(MarkParcelAttributeFromPosition.markParcelIntersectPreciseZoningType(
                        CollecTransform.selectIntersection(inputSFC, Geom.unionSFC(boundingSFC)), genericZone, preciseZone, zoningFile));
        else
            finalZone = MarkParcelAttributeFromPosition
                    .getOnlyMarkedParcels(MarkParcelAttributeFromPosition.markAllParcel(CollecTransform.selectIntersection(inputSFC, Geom.unionSFC(boundingSFC))));
        if (finalZone != null && finalZone.isEmpty()) System.out.println("createZoneToCut(): zone is empty");
        return finalZone;
    }

    public SimpleFeatureCollection zoneDivision(File zoneFile, SimpleFeatureCollection parcelSFC, File roadFile, ProfileUrbanFabric profile, boolean keepExistingRoad, File outFolder) throws IOException {
        DataStore sdsZone = CollecMgmt.getDataStore(zoneFile);
        SimpleFeatureCollection zone = sdsZone.getFeatureSource(sdsZone.getTypeNames()[0]).getFeatures();
        SimpleFeatureCollection result;
        if (roadFile == null) {
            result = zoneDivision(zone, parcelSFC, outFolder, profile, keepExistingRoad);
        } else {
            DataStore sdsRoad = CollecMgmt.getDataStore(zoneFile);
            result = zoneDivision(zone, parcelSFC, sdsRoad.getFeatureSource(sdsRoad.getTypeNames()[0]).getFeatures(), outFolder, profile, keepExistingRoad);
            sdsRoad.dispose();
        }
        sdsZone.dispose();
        return result;
    }

    /**
     * Method to use from fresh Geopackages. Also used by OpenMole tasks.
     *
     * @param zoneFile          Geopackage representing the zones to be cut
     * @param parcelFile        Geopackage of the entire parcel plan of the area
     * @param profile           Urban fabric profile of the wanted parcel plan
     * @param outFolder         folder where everything is stored
     * @param keepExistingRoads If true, existing raod (lack of parcel in the parcel plan) will be kept. If not, the whole zone is simulated regardless of its content.
     * @return Geopackage containing only the cuted parcel plan
     * @throws IOException from marking parcel
     */
    public File zoneDivision(File zoneFile, File parcelFile, ProfileUrbanFabric profile, boolean keepExistingRoads, File outFolder) throws IOException {
        DataStore dsZone = CollecMgmt.getDataStore(zoneFile);
        DataStore dsParcel = CollecMgmt.getDataStore(parcelFile);
        SimpleFeatureCollection zone = DataUtilities.collection(dsZone.getFeatureSource(dsZone.getTypeNames()[0]).getFeatures());
        SimpleFeatureCollection parcel = DataUtilities.collection(dsParcel.getFeatureSource(dsParcel.getTypeNames()[0]).getFeatures());
        dsZone.dispose();
        dsParcel.dispose();
        setSAVEINTERMEDIATERESULT(true);
        OVERWRITEGEOPACKAGE = true;
        zoneDivision(zone, parcel, outFolder, profile, keepExistingRoads);
        return new File(outFolder, "parcelZoneDivisionOnly" + CollecMgmt.getDefaultGISFileType());
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
     * @return The input parcel {@link SimpleFeatureCollection} with the marked parcels replaced by the simulated parcels. All parcels have the
     * {@link fr.ign.artiscales.pm.parcelFunction.ParcelSchema#getSFBMinParcel()} schema.
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
     * @return The input parcel {@link SimpleFeatureCollection} with the marked parcels replaced by the simulated parcels. All parcels have the
     * {@link fr.ign.artiscales.pm.parcelFunction.ParcelSchema#getSFBMinParcel()} schema.
     * @throws IOException from marking parcel
     */
    public SimpleFeatureCollection zoneDivision(SimpleFeatureCollection initialZone, SimpleFeatureCollection parcels, File outFolder,  ProfileUrbanFabric profile, File roadFile, boolean keepExistingRoads) throws IOException {
        DataStore dsRoad = CollecMgmt.getDataStore(roadFile);
        SimpleFeatureCollection result = zoneDivision(initialZone, parcels, dsRoad.getFeatureSource(dsRoad.getTypeNames()[0]).getFeatures(), outFolder, profile, keepExistingRoads);
        dsRoad.dispose();
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
     * @return The input parcel {@link SimpleFeatureCollection} with the marked parcels replaced by the simulated parcels. All parcels have the
     * {@link fr.ign.artiscales.pm.parcelFunction.ParcelSchema#getSFBMinParcel()} schema.
     * @throws IOException from marking parcel
     */
    public SimpleFeatureCollection zoneDivision(SimpleFeatureCollection initialZone, SimpleFeatureCollection parcels, SimpleFeatureCollection roads, File outFolder, ProfileUrbanFabric profile, boolean keepExistingRoads) throws IOException {
        File tmpFolder = new File(outFolder, "tmp");
        if (isDEBUG())
            tmpFolder.mkdirs();
        // parcel geometry name for all
        String geomName = parcels.getSchema().getGeometryDescriptor().getLocalName();
        final Geometry geomZone = Geom.unionSFC(initialZone);
        final SimpleFeatureBuilder finalParcelBuilder = ParcelSchema.getSFBMinParcel();
        // sort in two different collections, the ones that matters and the ones that will be saved for future purposes
        DefaultFeatureCollection parcelsInZone = new DefaultFeatureCollection();
        // parcels to save for after and convert them to the minimal attribute
        DefaultFeatureCollection savedParcels = new DefaultFeatureCollection();
        Arrays.stream(parcels.toArray(new SimpleFeature[0])).forEach(parcel -> {
            if (((Geometry) parcel.getDefaultGeometry()).intersects(geomZone))
                parcelsInZone.add(ParcelSchema.setSFBMinParcelWithFeat(parcel, finalParcelBuilder.getFeatureType()).buildFeature(Attribute.makeUniqueId()));
            else
                savedParcels.add(ParcelSchema.setSFBMinParcelWithFeat(parcel, finalParcelBuilder.getFeatureType()).buildFeature(Attribute.makeUniqueId()));
        });
        SimpleFeatureBuilder originalSFB = new SimpleFeatureBuilder(parcelsInZone.getSchema());
        if (isDEBUG()) {
            CollecMgmt.exportSFC(parcelsInZone, new File(tmpFolder, "parcelsInZone"));
            CollecMgmt.exportSFC(savedParcels, new File(tmpFolder, "parcelsSaved"));
            System.out.println("parcels in zone exported");
        }
        int numZone = 0;
        DefaultFeatureCollection goOdZone = new DefaultFeatureCollection();
        if (keepExistingRoads) {// complete the void left by the existing roads from the zones. Also assess a section number
            Geometry unionParcel = Geom.unionSFC(parcels);
            SimpleFeatureBuilder sfBuilder = ParcelSchema.getSFBMinParcelSplit();
            try (SimpleFeatureIterator zoneIt = initialZone.features()) {
                while (zoneIt.hasNext()) {
                    numZone++;
                    SimpleFeature zone = zoneIt.next();
                    // avoid most of tricky geometry problems
                    Geometry intersection = Geom.scaledGeometryReductionIntersection(Arrays.asList(((Geometry) zone.getDefaultGeometry()), unionParcel));
                    if (!intersection.isEmpty()) {
                        List<Polygon> geomsZone = Polygons.getPolygons(intersection);
                        for (Geometry geomPartZone : geomsZone) {
                            Geometry geom = GeometryPrecisionReducer.reduce(geomPartZone, new PrecisionModel(100));
                            // avoid silvers (plants the code)
                            if (geom.getArea() > 10) {
                                sfBuilder.set(geomName, geom);
                                sfBuilder.set(ParcelSchema.getMinParcelSectionField(), makeNewSection(String.valueOf(numZone)));
                                sfBuilder.set(MarkParcelAttributeFromPosition.getMarkFieldName(), 1);
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
        } else { // get the whole zone
            goOdZone.addAll(MarkParcelAttributeFromPosition.markAllParcel(initialZone));
        }
        // parts of parcel outside the zone must not be cut by the algorithm and keep their attributes
        List<Geometry> geomList = Arrays.stream(parcelsInZone.toArray(new SimpleFeature[0]))
                .map(x -> (Geometry) x.getDefaultGeometry()).collect(Collectors.toList());
        geomList.addAll(Arrays.stream(goOdZone.toArray(new SimpleFeature[0])).map(x -> (Geometry) x.getDefaultGeometry()).collect(Collectors.toList()));
        List<Polygon> polygons = FeaturePolygonizer.getPolygons(geomList);
        Geometry geomSelectedZone = Geom.unionSFC(goOdZone);
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
                        // if that original parcel contains that piece of parcel, we copy the previous parcels informations
                        if (((Geometry) feat.getDefaultGeometry()).buffer(0.01).contains(poly)) {
                            originalSFB.set(geomName, poly);
                            for (AttributeDescriptor attr : feat.getFeatureType().getAttributeDescriptors()) {
                                if (attr.getLocalName().equals(geomName))
                                    continue;
                                originalSFB.set(attr.getName(), feat.getAttribute(attr.getName()));
                            }
                            savedParcels.add(originalSFB.buildFeature(Attribute.makeUniqueId()));
                        }
                    }
                } catch (Exception problem) {
                    problem.printStackTrace();
                }
            }
        }
        // Parcel subdivision
        SimpleFeatureCollection splitedParcels = new DefaultFeatureCollection();
        SimpleFeatureCollection blockCollection = CityGeneration.createUrbanBlock(parcels);
        try (SimpleFeatureIterator it = goOdZone.features()) {
            while (it.hasNext()) {
                SimpleFeature zone = it.next();
                DefaultFeatureCollection tmpZoneToCut = new DefaultFeatureCollection();
                tmpZoneToCut.add(zone);
                switch (PROCESS) {
                    case "OBB":
                        ((DefaultFeatureCollection) splitedParcels)
                                .addAll(OBBDivision.splitParcels(tmpZoneToCut, null, profile.getMaximalArea(), profile.getMinimalWidthContactRoad(), profile.getHarmonyCoeff(), profile.getNoise(),
                                        CollecTransform.fromPolygonSFCtoListRingLines(
                                                CollecTransform.selectIntersection(blockCollection, (Geometry) zone.getDefaultGeometry())),
                                        profile.getLaneWidth(), profile.getStreetLane(), profile.getStreetWidth(), true, profile.getBlockShape()));
                        break;
                    case "SS":
                        StraightSkeletonDivision.FOLDER_OUT_DEBUG = tmpFolder;
                        ((DefaultFeatureCollection) splitedParcels)
                                .addAll(StraightSkeletonDivision.runTopologicalStraightSkeletonParcelDecomposition(zone, roads,
                                        "NOM_VOIE_G", "IMPORTANCE", profile.getMaxDepth(), profile.getMaxDistanceForNearestRoad(), profile.getMinimalArea(), profile.getMinimalWidthContactRoad(), profile.getMaxWidth(),
                                        profile.getNoise() == 0 ? 0.1 : profile.getNoise(), new MersenneTwister(42), profile.getLaneWidth()));
                        break;
                    case "MS":
                        System.out.println("not implemented yet");
                        break;
                }
            }
        } catch (Exception problem) {
            problem.printStackTrace();
        }
        if (isDEBUG()) {
            CollecMgmt.exportSFC(splitedParcels, new File(tmpFolder, "freshSplitedParcels"));
            System.out.println("fresh cuted parcels exported");
        }
        // merge the small parcels to bigger ones
        splitedParcels = ParcelCollection.mergeTooSmallParcels(splitedParcels, profile.getMinimalArea(), PROCESS.equals("SS"));
        DefaultFeatureCollection result = new DefaultFeatureCollection();
        int num = 0;
        // fix attribute for the simulated parcels
        try (SimpleFeatureIterator itParcel = splitedParcels.features()) {
            while (itParcel.hasNext()) {
                SimpleFeature parcel = itParcel.next();
                Geometry parcelGeom = (Geometry) parcel.getDefaultGeometry();
                finalParcelBuilder.set(geomName, parcelGeom);
                // get the section name
                String section = "";
                try (SimpleFeatureIterator goOdZoneIt = goOdZone.features()) {
                    while (goOdZoneIt.hasNext()) {
                        SimpleFeature zone = goOdZoneIt.next();
                        if (((Geometry) zone.getDefaultGeometry()).buffer(2).contains(parcelGeom)) {
                            section = (String) zone.getAttribute(ParcelSchema.getMinParcelSectionField());
                            break;
                        }
                    }
                } catch (Exception problem) {
                    problem.printStackTrace();
                }
                finalParcelBuilder.set(ParcelSchema.getMinParcelSectionField(), section);
                finalParcelBuilder.set(ParcelSchema.getMinParcelCommunityField(), ParcelAttribute.getCommunityCodeFromSFC(parcelsInZone, parcel));
                finalParcelBuilder.set(ParcelSchema.getMinParcelNumberField(), String.valueOf(num++));
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
        SimpleFeatureType schemaParcel = finalParcelBuilder.getFeatureType();
        try (SimpleFeatureIterator itSavedParcels = savedParcels.features()) {
            while (itSavedParcels.hasNext()) {
                SimpleFeatureBuilder parcelBuilder = ParcelSchema.setSFBMinParcelWithFeat(itSavedParcels.next(), schemaParcel);
                result.add(parcelBuilder.buildFeature(Attribute.makeUniqueId()));
            }
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
        String section = (String) feat.getAttribute(ParcelSchema.getMinParcelSectionField());
        return section.startsWith("New") && section.endsWith("Section");
    }
}