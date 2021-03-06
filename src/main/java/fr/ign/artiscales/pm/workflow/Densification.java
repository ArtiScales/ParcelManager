package fr.ign.artiscales.pm.workflow;

import fr.ign.artiscales.pm.division.FlagDivision;
import fr.ign.artiscales.pm.division.OBBDivision;
import fr.ign.artiscales.pm.fields.GeneralFields;
import fr.ign.artiscales.pm.parcelFunction.MarkParcelAttributeFromPosition;
import fr.ign.artiscales.pm.parcelFunction.ParcelSchema;
import fr.ign.artiscales.pm.parcelFunction.ParcelState;
import fr.ign.artiscales.tools.geoToolsFunctions.Attribute;
import fr.ign.artiscales.tools.geoToolsFunctions.Schemas;
import fr.ign.artiscales.tools.geoToolsFunctions.vectors.Geom;
import fr.ign.artiscales.tools.geoToolsFunctions.vectors.collec.CollecMgmt;
import fr.ign.artiscales.tools.geoToolsFunctions.vectors.collec.CollecTransform;
import fr.ign.artiscales.tools.parameter.ProfileUrbanFabric;
import org.geotools.data.DataStore;
import org.geotools.data.DataUtilities;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.factory.CommonFactoryFinder;
import org.geotools.feature.DefaultFeatureCollection;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.filter.FilterFactory2;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Simulation following that workflow divides parcels to ensure that they could be densified. The
 * {@link FlagDivision#generateFlagSplitedParcels(SimpleFeature, List, double, double, SimpleFeatureCollection, SimpleFeatureCollection, Double, Double, Double, Geometry)} method is applied on the selected
 * parcels. If the creation of a flag parcel is impossible and the local rules allows parcel to be disconnected from the road network, the
 * {@link OBBDivision#splitParcels(SimpleFeature, double, double, double, double, List, double, boolean, int)} is applied. Other behavior can be set relatively to the
 * parcel's sizes.
 *
 * @author Maxime Colomb
 */
public class Densification extends Workflow {

    static double uncountedBuildingArea = 20;

//	 public static void main(String[] args) throws Exception {
//	 File parcelFile = new File("/tmp/ex/parcel.gpkg");
//	 File buildingFile = new File("/tmp/ex/building.gpkg");
//	 File roadFile = new File("/tmp/ex/road.gpkg");
//	 File outFolder = new File("/tmp/ex");
//	 outFolder.mkdirs();
//	 DataStore pDS = Geopackages.getDataStore(parcelFile);
//	 SimpleFeatureCollection parcels = pDS.getFeatureSource(pDS.getTypeNames()[0]).getFeatures();
//	 Collec.exportSFC((new Densification()).densificationOrNeighborhood(parcels, CityGeneration.createUrbanBlock(parcels), outFolder, buildingFile, roadFile,
//	 ProfileUrbanFabric.convertJSONtoProfile(new File("src/main/resources/TestScenario/profileUrbanFabric/smallHouse.json")), false, null,4),
//	 new File(outFolder, "result"));
//	 }

    public Densification() {
    }

    /**
     * Apply the densification workflow on a set of marked parcels. TODO improvements: if a densification is impossible (mainly for building constructed on the both cut parcel
     * reason), reiterate the flag cut division with noise. The cut may work better !
     *
     * @param parcelCollection        {@link SimpleFeatureCollection} of marked parcels.
     * @param blockCollection         {@link SimpleFeatureCollection} containing the morphological block. Can be generated with the
     *                                {@link fr.ign.artiscales.tools.geometryGeneration.CityGeneration#createUrbanBlock(SimpleFeatureCollection)} method.
     * @param outFolder               Folder to store result files
     * @param buildingFile            Geopackage representing the buildings
     * @param roadFile                Geopackage representing the roads. If road not needed, use the overloaded method.
     * @param maximalAreaSplitParcel  threshold of parcel area above which the OBB algorithm stops to decompose parcels
     * @param minimalAreaSplitParcel  threshold under which the parcels is not kept. If parcel simulated is under this workflow will keep the unsimulated parcel.
     * @param maximalWidthSplitParcel threshold of parcel connection to road under which the OBB algorithm stops to decompose parcels
     * @param lenDriveway             lenght of the driveway to connect a parcel through another parcel to the road
     * @param allowIsolatedParcel     true if the simulated parcels have the right to be isolated from the road, false otherwise.
     * @param exclusionZone           Exclude a zone that won't be considered as a potential road connection. Useful to represent border of the parcel plan. Can be null.
     * @return The input parcel {@link SimpleFeatureCollection} with the marked parcels replaced by the simulated parcels. All parcels have the
     * {@link fr.ign.artiscales.pm.parcelFunction.ParcelSchema#getSFBMinParcel()} schema.
     * @throws IOException Reading and writing geo files
     */
    public SimpleFeatureCollection densification(SimpleFeatureCollection parcelCollection, SimpleFeatureCollection blockCollection, File outFolder,
                                                 File buildingFile, File roadFile, double harmonyCoeff, double noise, double maximalAreaSplitParcel, double minimalAreaSplitParcel,
                                                 double maximalWidthSplitParcel, double lenDriveway, boolean allowIsolatedParcel, Geometry exclusionZone) throws IOException {
        // if parcels doesn't contains the markParcelAttribute field or have no marked parcels
        if (MarkParcelAttributeFromPosition.isNoParcelMarked(parcelCollection)) {
            System.out.println("Densification : unmarked parcels");
            return GeneralFields.transformSFCToMinParcel(parcelCollection);
        }
        // preparation of optional datas
        boolean hasBuilding = false;
        boolean hasRoad = false;
        DataStore buildingDS = null;
        if (buildingFile != null && buildingFile.exists()) {
            hasBuilding = true;
            buildingDS = CollecMgmt.getDataStore(buildingFile);
        }
        DataStore roadDS = null;
        if (roadFile != null && roadFile.exists()) {
            hasRoad = true;
            roadDS = CollecMgmt.getDataStore(roadFile);
        }
        // preparation of the builder and empty collections
        final String geomName = parcelCollection.getSchema().getGeometryDescriptor().getLocalName();
        FilterFactory2 ff = CommonFactoryFinder.getFilterFactory2();
        DefaultFeatureCollection onlyCutedParcels = new DefaultFeatureCollection();
        DefaultFeatureCollection resultParcels = new DefaultFeatureCollection();
        SimpleFeatureBuilder sFBMinParcel = ParcelSchema.getSFBMinParcel();
        try (SimpleFeatureIterator iterator = parcelCollection.features()) {
            while (iterator.hasNext()) {
                SimpleFeature initialParcel = iterator.next();
                // if the parcel is selected for the simulation and bigger than the limit size
                if (initialParcel.getAttribute(MarkParcelAttributeFromPosition.getMarkFieldName()) != null
                        && initialParcel.getAttribute(MarkParcelAttributeFromPosition.getMarkFieldName()).equals(1)
                        && ((Geometry) initialParcel.getDefaultGeometry()).getArea() > maximalAreaSplitParcel) {
                    // we get the needed block lines
                    List<LineString> lines = CollecTransform.fromPolygonSFCtoListRingLines(blockCollection
                            .subCollection(ff.bbox(ff.property(initialParcel.getFeatureType().getGeometryDescriptor().getLocalName()), initialParcel.getBounds())));
                    // we flag cut the parcel (differently regarding whether they have optional data or not)
                    SimpleFeatureCollection unsortedFlagParcel;
                    if (hasBuilding && hasRoad)
                        unsortedFlagParcel = FlagDivision.generateFlagSplitedParcels(initialParcel, lines, harmonyCoeff, noise,
                                CollecTransform.selectIntersection(buildingDS.getFeatureSource(buildingDS.getTypeNames()[0]).getFeatures(), ((Geometry) initialParcel.getDefaultGeometry()).buffer(10)),
                                CollecTransform.selectIntersection(roadDS.getFeatureSource(roadDS.getTypeNames()[0]).getFeatures(), ((Geometry) initialParcel.getDefaultGeometry()).buffer(10)),
                                maximalAreaSplitParcel, maximalWidthSplitParcel, lenDriveway, exclusionZone);
                    else if (hasBuilding)
                        unsortedFlagParcel = FlagDivision.generateFlagSplitedParcels(initialParcel, lines, harmonyCoeff, noise,
                                CollecTransform.selectIntersection(buildingDS.getFeatureSource(buildingDS.getTypeNames()[0]).getFeatures(), ((Geometry) initialParcel.getDefaultGeometry()).buffer(10)),
                                maximalAreaSplitParcel, maximalWidthSplitParcel, lenDriveway, exclusionZone);
                    else
                        unsortedFlagParcel = FlagDivision.generateFlagSplitedParcels(initialParcel, lines, harmonyCoeff, noise,
                                maximalAreaSplitParcel, maximalWidthSplitParcel, lenDriveway, exclusionZone);
                    // we check if the cut parcels are meeting the expectations
                    boolean add = true;
                    // If it returned a collection of 1, it was impossible to flag split the parcel. If allowed, we cut the parcel with regular OBB
                    if (unsortedFlagParcel.size() == 1) {
                        add = false;
                        if (allowIsolatedParcel) {
                            unsortedFlagParcel = OBBDivision.splitParcels(initialParcel, maximalAreaSplitParcel, maximalWidthSplitParcel, 0.5, noise,
                                    lines, 0, true, 99);
                            add = true;
                        }
                    }
                    // If the flag cut parcel size is too small, we won't add anything
                    try (SimpleFeatureIterator parcelIt = unsortedFlagParcel.features()) {
                        while (parcelIt.hasNext())
                            if (((Geometry) parcelIt.next().getDefaultGeometry()).getArea() < minimalAreaSplitParcel) {
                                add = false;
                                break;
                            }
                    } catch (Exception problem) {
                        System.out.println("problem" + problem + "for " + initialParcel + " feature densification");
                        problem.printStackTrace();
                    }
                    // We check existing buildings are constructed across two cut parcels. If true, me merge those parcels together
                    if (add) {
                        DefaultFeatureCollection toMerge = new DefaultFeatureCollection();
                        try (SimpleFeatureIterator parcelIt = unsortedFlagParcel.features()) {
                            while (parcelIt.hasNext()) {
                                SimpleFeature parcel = parcelIt.next();
                                // if at least one parcel is unbuilt, then the decomposition is not in vain
                                if (ParcelState.isAlreadyBuilt(buildingFile, parcel, -1, uncountedBuildingArea))
                                    toMerge.add(parcel);
                            }
                        } catch (Exception problem) {
                            problem.printStackTrace();
                        }
                        // if all parcels are marked, buildings are present on every cut parts. We then cancel densification
                        if (toMerge.size() == unsortedFlagParcel.size())
                            add = false;
                        else if (toMerge.size() > 1) { // merge the parcel that are built upon a building (we assume that it must be the same building)
                            // tmp save the collection of output parcels
                            DefaultFeatureCollection tmpUnsortedFlagParcel = new DefaultFeatureCollection();
                            tmpUnsortedFlagParcel.addAll(unsortedFlagParcel);
                            unsortedFlagParcel = new DefaultFeatureCollection();
                            // we add the merged parcels
                            SimpleFeatureBuilder builder = Schemas.getSFBSchemaWithMultiPolygon(toMerge.getSchema());
                            builder.set(toMerge.getSchema().getGeometryDescriptor().getLocalName(), Geom.unionSFC(toMerge).buffer(0.1).buffer(-0.1));
                            ((DefaultFeatureCollection) unsortedFlagParcel).add(builder.buildFeature(Attribute.makeUniqueId()));
                            // we check if the flag cut parcels have been merged or if they need to be put on the new collection
                            try (SimpleFeatureIterator parcelIt = tmpUnsortedFlagParcel.features()) {
                                while (parcelIt.hasNext()) {
                                    SimpleFeature parcel = parcelIt.next();
                                    boolean complete = true;
                                    // we now iterate on the parcel that we have merged
                                    try (SimpleFeatureIterator parcelIt2 = toMerge.features()) {
                                        while (parcelIt2.hasNext())
                                            // if the initial parcel has been merged, we don't add it to the new collection
                                            if (parcel.getDefaultGeometry().equals(parcelIt2.next().getDefaultGeometry())) {
                                                complete = false;
                                                break;
                                            }
                                    } catch (Exception problem) {
                                        problem.printStackTrace();
                                    }
                                    // if at least one parcel is unbuilt, then the decomposition is not in vain
                                    if (complete)
                                        ((DefaultFeatureCollection) unsortedFlagParcel).add(parcel);
                                }
                            } catch (Exception problem) {
                                problem.printStackTrace();
                            }
                        }
                    }
                    if (add) { // if we are okay to add parts : we construct the new parcels
                        int i = 1;
                        try (SimpleFeatureIterator parcelCutedIt = unsortedFlagParcel.features()) {
                            while (parcelCutedIt.hasNext()) {
                                SimpleFeature parcelCuted = parcelCutedIt.next();
                                Geometry pGeom = (Geometry) parcelCuted.getDefaultGeometry();
                                for (int ii = 0; ii < pGeom.getNumGeometries(); ii++) {
                                    sFBMinParcel.set(geomName, pGeom.getGeometryN(ii));
                                    sFBMinParcel.set(ParcelSchema.getMinParcelSectionField(), makeNewSection(initialParcel.getAttribute(ParcelSchema.getMinParcelSectionField()) + "-" + i++));
                                    sFBMinParcel.set(ParcelSchema.getMinParcelNumberField(), initialParcel.getAttribute(ParcelSchema.getMinParcelNumberField()));
                                    sFBMinParcel.set(ParcelSchema.getMinParcelCommunityField(), initialParcel.getAttribute(ParcelSchema.getMinParcelCommunityField()));
                                    SimpleFeature cutedParcel = sFBMinParcel.buildFeature(Attribute.makeUniqueId());
                                    resultParcels.add(cutedParcel);
                                    if (isSAVEINTERMEDIATERESULT())
                                        onlyCutedParcels.add(cutedParcel);
                                }
                            }
                        } catch (Exception problem) {
                            problem.printStackTrace();
                        }
                    } else {
                        sFBMinParcel = ParcelSchema.setSFBMinParcelWithFeat(initialParcel, sFBMinParcel.getFeatureType());
                        resultParcels.add(sFBMinParcel.buildFeature(Attribute.makeUniqueId()));
                    }
                } else { // if no simulation needed, we add the normal parcel
                    sFBMinParcel = ParcelSchema.setSFBMinParcelWithFeat(initialParcel, sFBMinParcel.getFeatureType());
                    resultParcels.add(sFBMinParcel.buildFeature(Attribute.makeUniqueId()));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (hasRoad)
            roadDS.dispose();
        if (hasBuilding)
            buildingDS.dispose();
        if (isSAVEINTERMEDIATERESULT()) {
            CollecMgmt.exportSFC(onlyCutedParcels, new File(outFolder, "parcelDensificationOnly"), OVERWRITEGEOPACKAGE);
            OVERWRITEGEOPACKAGE = false;
        }
        return resultParcels.collection();
    }

    /**
     * Apply the densification workflow on a set of marked parcels.
     * <p>
     * overload of the {@link #densification(SimpleFeatureCollection, SimpleFeatureCollection, File, File, File, double, double, double, double, double, double, boolean, Geometry)}
     * method if we choose to not use a geometry of exclusion
     *
     * @param parcelCollection        SimpleFeatureCollection of marked parcels.
     * @param blockCollection         SimpleFeatureCollection containing the morphological block. Can be generated with the
     *                                {@link fr.ign.artiscales.tools.geometryGeneration.CityGeneration#createUrbanBlock(SimpleFeatureCollection)} method.
     * @param outFolder               folder to store created files
     * @param buildingFile            Geopackage representing the buildings
     * @param roadFile                Geopackage representing the roads
     * @param maximalAreaSplitParcel  threshold of parcel area above which the OBB algorithm stops to decompose parcels
     * @param minimalAreaSplitParcel  threshold under which the parcels is not kept. If parcel simulated is under this workflow will keep the unsimulated parcel.
     * @param maximalWidthSplitParcel threshold of parcel connection to road under which the OBB algorithm stops to decompose parcels
     * @param lenDriveway             length of the driveway to connect a parcel through another parcel to the road
     * @param allowIsolatedParcel     true if the simulated parcels have the right to be isolated from the road, false otherwise.
     * @return The input parcel {@link SimpleFeatureCollection} with the marked parcels replaced by the simulated parcels. All parcels have the
     * {@link fr.ign.artiscales.pm.parcelFunction.ParcelSchema#getSFBMinParcel()} schema. * @throws Exception
     */
    public SimpleFeatureCollection densification(SimpleFeatureCollection parcelCollection, SimpleFeatureCollection blockCollection, File outFolder,
                                                 File buildingFile, File roadFile, double harmonyCoeff, double noise, double maximalAreaSplitParcel, double minimalAreaSplitParcel,
                                                 double maximalWidthSplitParcel, double lenDriveway, boolean allowIsolatedParcel) throws Exception {
        return densification(parcelCollection, blockCollection, outFolder, buildingFile, roadFile, harmonyCoeff, noise, maximalAreaSplitParcel,
                minimalAreaSplitParcel, maximalWidthSplitParcel, lenDriveway, allowIsolatedParcel, null);
    }

    /**
     * Apply the densification workflow on a set of marked parcels.
     * <p>
     * overload of the {@link #densification(SimpleFeatureCollection, SimpleFeatureCollection, File, File, File, double, double, double, double, double, double, boolean, Geometry)}
     * method if we choose to not use a road Geopackage
     *
     * @param parcelCollection        SimpleFeatureCollection of marked parcels.
     * @param blockCollection         SimpleFeatureCollection containing the morphological block. Can be generated with the
     *                                {@link fr.ign.artiscales.tools.geometryGeneration.CityGeneration#createUrbanBlock(SimpleFeatureCollection)} method.
     * @param outFolder               folder to store created files
     * @param buildingFile            Geopackage representing the buildings
     * @param maximalAreaSplitParcel  threshold of parcel area above which the OBB algorithm stops to decompose parcels
     * @param minimalAreaSplitParcel  threshold under which the parcels is not kept. If parcel simulated is under this workflow will keep the unsimulated parcel.
     * @param maximalWidthSplitParcel threshold of parcel connection to road under which the OBB algorithm stops to decompose parcels
     * @param lenDriveway             lenght of the driveway to connect a parcel through another parcel to the road
     * @param allowIsolatedParcel     true if the simulated parcels have the right to be isolated from the road, false otherwise.
     * @return The input parcel {@link SimpleFeatureCollection} with the marked parcels replaced by the simulated parcels. All parcels have the
     * {@link fr.ign.artiscales.pm.parcelFunction.ParcelSchema#getSFBMinParcel()} schema. * @throws Exception
     */
    public SimpleFeatureCollection densification(SimpleFeatureCollection parcelCollection, SimpleFeatureCollection blockCollection, File outFolder,
                                                 File buildingFile, double harmonyCoeff, double noise, double maximalAreaSplitParcel, double minimalAreaSplitParcel,
                                                 double maximalWidthSplitParcel, double lenDriveway, boolean allowIsolatedParcel) throws Exception {
        return densification(parcelCollection, blockCollection, outFolder, buildingFile, null, harmonyCoeff, noise, maximalAreaSplitParcel,
                minimalAreaSplitParcel, maximalWidthSplitParcel, lenDriveway, allowIsolatedParcel);
    }

    /**
     * Apply the densification workflow on a set of marked parcels. Overload
     * {@link #densification(SimpleFeatureCollection, SimpleFeatureCollection, File, File, File, ProfileUrbanFabric, boolean, Geometry)} method with a profile building type input
     * (which automatically report its parameters to the fields)
     *
     * @param parcelCollection    SimpleFeatureCollection of marked parcels.
     * @param blockCollection     SimpleFeatureCollection containing the morphological block. Can be generated with the
     *                            {@link fr.ign.artiscales.tools.geometryGeneration.CityGeneration#createUrbanBlock(SimpleFeatureCollection)} method.
     * @param outFolder           folder to store result files.
     * @param buildingFile        Geopackage representing the buildings.
     * @param roadFile            Geopackage representing the roads (optional).
     * @param profile             Description of the urban fabric profile planed to be simulated on this zone.
     * @param allowIsolatedParcel true if the simulated parcels have the right to be isolated from the road, false otherwise.
     * @return The input parcel {@link SimpleFeatureCollection} with the marked parcels replaced by the simulated parcels. All parcels have the
     * {@link fr.ign.artiscales.pm.parcelFunction.ParcelSchema#getSFBMinParcel()} schema.
     * @throws IOException
     */
    public SimpleFeatureCollection densification(SimpleFeatureCollection parcelCollection, SimpleFeatureCollection blockCollection, File outFolder,
                                                 File buildingFile, File roadFile, ProfileUrbanFabric profile, boolean allowIsolatedParcel) throws IOException {
        return densification(parcelCollection, blockCollection, outFolder, buildingFile, roadFile, profile, allowIsolatedParcel, null);
    }

    /**
     * Apply the densification workflow on a set of marked parcels. Overload
     * {@link #densification(SimpleFeatureCollection, SimpleFeatureCollection, File, File, File, double, double, double, double, double, double, boolean, Geometry)} method with a
     * profile building type input (which automatically report its parameters to the fields)
     *
     * @param parcelCollection    SimpleFeatureCollection of marked parcels.
     * @param blockCollection     SimpleFeatureCollection containing the morphological block. Can be generated with the
     *                            {@link fr.ign.artiscales.tools.geometryGeneration.CityGeneration#createUrbanBlock(SimpleFeatureCollection)} method.
     * @param outFolder           folder to store result files.
     * @param buildingFile        Geopackage representing the buildings.
     * @param roadFile            Geopackage representing the roads (optional).
     * @param profile             Description of the urban fabric profile planed to be simulated on this zone.
     * @param allowIsolatedParcel true if the simulated parcels have the right to be isolated from the road, false otherwise.
     * @param exclusionZone       Exclude a zone that won't be considered as a potential road connection. Useful to represent border of the parcel plan. Can be null.
     * @return The input parcel {@link SimpleFeatureCollection} with the marked parcels replaced by the simulated parcels. All parcels have the
     * {@link fr.ign.artiscales.pm.parcelFunction.ParcelSchema#getSFBMinParcel()} schema. * @throws Exception
     * @throws IOException
     */
    public SimpleFeatureCollection densification(SimpleFeatureCollection parcelCollection, SimpleFeatureCollection blockCollection, File outFolder,
                                                 File buildingFile, File roadFile, ProfileUrbanFabric profile, boolean allowIsolatedParcel, Geometry exclusionZone) throws IOException {
        return densification(parcelCollection, blockCollection, outFolder, buildingFile, roadFile, profile.getHarmonyCoeff(), profile.getNoise(),
                profile.getMaximalArea(), profile.getMinimalArea(), profile.getMinimalWidthContactRoad(), profile.getLenDriveway(),
                allowIsolatedParcel, exclusionZone);
    }

    /**
     * Apply a hybrid densification process on the coming parcel collection. The parcels that size are inferior to 4x the maximal area of parcel type to create are runned with the
     * densication workflow. The parcels that size are superior to 4x the maximal area are considered as able to build neighborhood. They are divided with the
     * {@link fr.ign.artiscales.pm.workflow.ConsolidationDivision#consolidationDivision(SimpleFeatureCollection, File, File, ProfileUrbanFabric)} method.
     *
     * @param parcelCollection          SimpleFeatureCollection of marked parcels.
     * @param blockCollection           SimpleFeatureCollection containing the morphological block. Can be generated with the
     *                                  {@link fr.ign.artiscales.tools.geometryGeneration.CityGeneration#createUrbanBlock(SimpleFeatureCollection)} method.
     * @param outFolder                 folder to store result files.
     * @param buildingFile              Geopackage representing the buildings.
     * @param roadFile                  Geopackage representing the roads (optional).
     * @param profile                   ProfileUrbanFabric of the simulated urban scene.
     * @param allowIsolatedParcel       true if the simulated parcels have the right to be isolated from the road, false otherwise.
     * @param exclusionZone             Exclude a zone that won't be considered as a potential road connection. Useful to represent border of the parcel plan. Can be null.
     * @param factorOflargeZoneCreation If the area of the parcel to be simulated is superior to the maximal size of parcels multiplied by this factor, the simulation will be done with the
     *                                  {@link fr.ign.artiscales.pm.workflow.ConsolidationDivision#consolidationDivision(SimpleFeatureCollection, File, File, ProfileUrbanFabric)} method.
     * @return The input parcel {@link SimpleFeatureCollection} with the marked parcels replaced by the simulated parcels. All parcels have the
     * {@link fr.ign.artiscales.pm.parcelFunction.ParcelSchema#getSFBMinParcel()} schema.
     * @throws IOException
     */
    public SimpleFeatureCollection densificationOrNeighborhood(SimpleFeatureCollection parcelCollection, SimpleFeatureCollection blockCollection,
                                                               File outFolder, File buildingFile, File roadFile, ProfileUrbanFabric profile, boolean allowIsolatedParcel, Geometry exclusionZone,
                                                               int factorOflargeZoneCreation) throws IOException {
        // TODO stupid hack but I can't figure out how those SimpleFeatuceCollection's attributes are changed if not wrote in hard
//        SimpleFeatureCollection parcelCollectionMerged = MarkParcelAttributeFromPosition.unionTouchingMarkedGeometries(parcelCollection);
        File tmp = new File(outFolder, "tmp");
        tmp.mkdirs();
//        File tmpDens = CollecMgmt.exportSFC(parcelCollectionMerged, new File(tmp, "Dens"));
        File tmpDens = CollecMgmt.exportSFC(parcelCollection, new File(tmp, "Dens"));
        // We flagcut the parcels which size is inferior to 4x the max parcel size
        SimpleFeatureCollection parcelDensified = densification(
//              MarkParcelAttributeFromPosition.markParcelsInf(parcelCollectionMerged, profile.getMaximalArea() * factorOflargeZoneCreation),
                MarkParcelAttributeFromPosition.markParcelsInf(parcelCollection, profile.getMaximalArea() * factorOflargeZoneCreation),
                blockCollection, outFolder, buildingFile, roadFile, profile.getHarmonyCoeff(), profile.getNoise(), profile.getMaximalArea(),
                profile.getMinimalArea(), profile.getMinimalWidthContactRoad(), profile.getLenDriveway(), allowIsolatedParcel, exclusionZone);
        if (isDEBUG())
            CollecMgmt.exportSFC(parcelDensified, new File(outFolder, "densificationOrNeighborhood-Dens"));
        // if parcels are too big, we try to create neighborhoods inside them with the consolidation algorithm
        // We first re-mark the parcels that were marked.
        DataStore ds = CollecMgmt.getDataStore(tmpDens);
        SimpleFeatureCollection supParcels = MarkParcelAttributeFromPosition.markParcelsSup(
                MarkParcelAttributeFromPosition.markAlreadyMarkedParcels(parcelDensified, DataUtilities.collection(ds.getFeatureSource(ds.getTypeNames()[0]).getFeatures())),
                profile.getMaximalArea() * factorOflargeZoneCreation);
        if (isDEBUG())
            CollecMgmt.exportSFC(parcelDensified, new File(outFolder, "densificationOrNeighborhood-ReMarked"));

        if (!MarkParcelAttributeFromPosition.isNoParcelMarked(supParcels)) {
            profile.setStreetWidth(profile.getLaneWidth());
            parcelDensified = (new ConsolidationDivision()).consolidationDivision(supParcels, roadFile, outFolder, profile);
            if (isDEBUG())
                CollecMgmt.exportSFC(parcelDensified, new File(outFolder, "densificationOrNeighborhood-Neigh"));
        }

        ds.dispose();
        tmp.delete();
        return parcelDensified;
    }

    /**
     * Create a new section name following a precise rule.
     *
     * @param section name of the former section
     * @return the new section's name
     */
    public String makeNewSection(String section) {
        return section + "-Densifyed";
    }

    /**
     * Check if the input {@link SimpleFeature} has a section field that has been simulated with this present workflow.
     *
     * @param feat {@link SimpleFeature} to test.
     * @return true if the section field is marked with the {@link #makeNewSection(String)} method.
     */
    public boolean isNewSection(SimpleFeature feat) {
        return ((String) feat.getAttribute(ParcelSchema.getMinParcelSectionField())).endsWith("-Densifyed");
    }
}
