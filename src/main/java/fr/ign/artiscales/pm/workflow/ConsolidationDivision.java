package fr.ign.artiscales.pm.workflow;

import fr.ign.artiscales.pm.division.DivisionType;
import fr.ign.artiscales.pm.division.FlagDivision;
import fr.ign.artiscales.pm.division.OBBDivision;
import fr.ign.artiscales.pm.division.OBBThenSS;
import fr.ign.artiscales.pm.division.StraightSkeletonDivision;
import fr.ign.artiscales.pm.parcelFunction.MarkParcelAttributeFromPosition;
import fr.ign.artiscales.pm.parcelFunction.ParcelCollection;
import fr.ign.artiscales.pm.parcelFunction.ParcelSchema;
import fr.ign.artiscales.tools.geoToolsFunctions.Attribute;
import fr.ign.artiscales.tools.geoToolsFunctions.Schemas;
import fr.ign.artiscales.tools.geoToolsFunctions.vectors.Geom;
import fr.ign.artiscales.tools.geoToolsFunctions.vectors.collec.CollecMgmt;
import fr.ign.artiscales.tools.geoToolsFunctions.vectors.collec.CollecTransform;
import fr.ign.artiscales.tools.geometryGeneration.CityGeneration;
import fr.ign.artiscales.tools.parameter.ProfileUrbanFabric;
import org.apache.commons.math3.random.MersenneTwister;
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
import java.util.Arrays;
import java.util.List;

/**
 * Simulation following this workflow merge together the contiguous marked parcels to create zones. The chosen parcel division process (OBB by default) is then applied on each created
 * zone.
 *
 * @author Maxime Colomb
 */
public class ConsolidationDivision extends Workflow {
    public ConsolidationDivision() {
    }

//    public static void main(String[] args) throws Exception {
//        File rootFolder = new File("src/main/resources/TestScenario/");
//        DataStore parcelDS = CollecMgmt.getDataStore(new File(rootFolder, "InputData/parcel.gpkg"));
//        File roadFile = new File(rootFolder, "InputData/road.gpkg");
//        File outFolder = new File("/tmp");
//        setDEBUG(true);
//        PROCESS = DivisionType.OBB;
//        setSAVEINTERMEDIATERESULT(true);
//        SimpleFeatureCollection z = new ConsolidationDivision().consolidationDivision(MarkParcelAttributeFromPosition.markRandomParcels(parcelDS.getFeatureSource(parcelDS.getTypeNames()[0]).getFeatures(), 25, false), roadFile, outFolder, ProfileUrbanFabric.convertJSONtoProfile(new File("src/main/resources/TestScenario/profileUrbanFabric/smallHouse.json")));
//        CollecMgmt.exportSFC(z, new File("/tmp/conso"));
//    }

    /**
     * do parcel consolidation to parcels that touches. Set a new section value, number 1 and the most intersecting city code
     *
     * @param parcels       every parcel of the simulation in order to copy some attributes
     * @param parcelToMerge parcels to merge
     * @return collection of merged parcels
     */
    public static DefaultFeatureCollection consolidation(SimpleFeatureCollection parcels, SimpleFeatureCollection parcelToMerge) {
        DefaultFeatureCollection mergedParcels = new DefaultFeatureCollection();
        SimpleFeatureBuilder sfBuilder = ParcelSchema.getSFBMinParcelSplit();
        Geometry multiGeom = Geom.safeUnion(parcelToMerge);
        for (int i = 0; i < multiGeom.getNumGeometries(); i++) {
            sfBuilder.add(multiGeom.getGeometryN(i));
            sfBuilder.set(ParcelSchema.getParcelSectionField(), String.valueOf(i));
            sfBuilder.set(ParcelSchema.getParcelCommunityField(),
                    CollecTransform.getIntersectingFieldFromSFC(multiGeom.getGeometryN(i), parcels, ParcelSchema.getParcelCommunityField()));
            sfBuilder.set(ParcelSchema.getParcelNumberField(), "0");
            sfBuilder.set(MarkParcelAttributeFromPosition.getMarkFieldName(), 1);
            mergedParcels.add(sfBuilder.buildFeature(Attribute.makeUniqueId()));
        }
        return mergedParcels;
    }

    /**
     * Method that merges the contiguous marked parcels into zones and then split those zones with a given parcel division algorithm (by default, the Oriented Bounding Box).
     *
     * @param parcels   The parcels to be merged and cut. Must be marked with the SPLIT filed (see markParcelIntersectMUPOutput for example, with the method concerning MUP-City's output)
     * @param outFolder The folder where will be saved intermediate results and temporary files for debug
     * @param profile   {@link ProfileUrbanFabric} contains the parameters of the wanted urban scene
     * @return the set of parcel with decomposition
     * @throws IOException Writing files in debug modes
     */
    public SimpleFeatureCollection consolidationDivision(SimpleFeatureCollection parcels, File roadFile, File outFolder, ProfileUrbanFabric profile) throws IOException {
        return consolidationDivision(parcels, roadFile, null, null, null, outFolder, profile);
    }

    /**
     * Method that merges the contiguous marked parcels into zones and then split those zones with a given parcel division algorithm (by default, the Oriented Bounding Box).
     * Overload with FlagDivision data
     *
     * @param parcels      The parcels to be merged and cut. Must be marked with the SPLIT filed (see markParcelIntersectMUPOutput for example, with the method concerning MUP-City's output)
     * @param outFolder    The folder where will be saved intermediate results and temporary files for debug
     * @param profile      {@link ProfileUrbanFabric} contains the parameters of the wanted urban scene
     * @param buildingFile Building geo file. Mandatory for ParcelFlag process, can be null otherwise.
     * @param roadFile     Road geo file. Can be null.
     * @return the set of parcel with decomposition
     * @throws IOException Writing files in debug modes
     */
    public SimpleFeatureCollection consolidationDivision(SimpleFeatureCollection parcels, File roadFile, File buildingFile, List<LineString> extLines, Geometry exclusionZone, File outFolder, ProfileUrbanFabric profile) throws IOException {
        if (!CollecMgmt.isCollecContainsAttribute(parcels, MarkParcelAttributeFromPosition.getMarkFieldName())) {
            if (isDEBUG())
                System.out.println("consolidationDivision: no marking (" + MarkParcelAttributeFromPosition.getMarkFieldName() + ") field/");
            return parcels;
        }
        if (MarkParcelAttributeFromPosition.isNoParcelMarked(parcels)) {
            if (isDEBUG())
                System.out.println("consolidationDivision: no parcel marked");
            return ParcelCollection.getParcelWithoutSplitField(parcels);
        }
        checkFields(parcels.getSchema());

        File tmpFolder = new File(outFolder, "tmp");
        if (isDEBUG())
            tmpFolder.mkdirs();
        DefaultFeatureCollection parcelSaved = new DefaultFeatureCollection(parcels);
        if (isDEBUG()) {
            CollecMgmt.exportSFC(parcels, new File(tmpFolder, "step0"));
            System.out.println("done step 0");
        }
        ////////////////
        // first step : round of selection of the intersected parcels
        ////////////////
        DefaultFeatureCollection parcelToMerge = new DefaultFeatureCollection();
        Arrays.stream(parcels.toArray(new SimpleFeature[0])).forEach(parcel -> {
            if (parcel.getAttribute(MarkParcelAttributeFromPosition.getMarkFieldName()) != null
                    && (String.valueOf(parcel.getAttribute(MarkParcelAttributeFromPosition.getMarkFieldName()))).equals("1")) {
                parcelToMerge.add(parcel);
                parcelSaved.remove(parcel);
            }
        });
        if (isDEBUG()) {
            CollecMgmt.exportSFC(parcelToMerge.collection(), new File(tmpFolder, "step1"));
            System.out.println("done step 1");
        }
        ////////////////
        // second step : merge of the parcel that touches themselves by block
        ////////////////
        DefaultFeatureCollection mergedParcels = consolidation(parcels, parcelToMerge);
        if (isDEBUG()) {
            CollecMgmt.exportSFC(mergedParcels.collection(), new File(tmpFolder, "step2"));
            System.out.println("done step 2");
        }
        ////////////////
        // third step : cut the parcels
        ////////////////
        SimpleFeatureCollection roads;
        if (roadFile != null && roadFile.exists()) {
            DataStore dsRoad = CollecMgmt.getDataStore(roadFile);
            roads = DataUtilities.collection(CollecTransform.selectIntersection(dsRoad.getFeatureSource(dsRoad.getTypeNames()[0]).getFeatures(), Geom.safeUnion(mergedParcels).buffer(30)));
            if (isDEBUG())
                CollecMgmt.exportSFC(roads, new File(tmpFolder, "roads"));
            dsRoad.dispose();
        } else
            roads = null;

        //setting final schema. If no split field at first, we don't add it in the final collection.
        SimpleFeatureBuilder sfBuilderFinalParcel = ParcelSchema.getSFBWithoutSplit(parcels.getSchema());

        SimpleFeatureCollection blockCollection = CityGeneration.createUrbanBlock(parcels);
        FilterFactory2 ff = CommonFactoryFinder.getFilterFactory2();
        SimpleFeatureCollection result = new DefaultFeatureCollection();
        try (SimpleFeatureIterator itInitialParcel = mergedParcels.features()) {
            while (itInitialParcel.hasNext()) {
                SimpleFeature feat = itInitialParcel.next();
                int i = 0;
                if (((Geometry) feat.getDefaultGeometry()).getArea() > profile.getMaximalArea()) { // Parcel big enough, we cut it
                    try {
                        SimpleFeatureCollection freshCutParcel = new DefaultFeatureCollection();
                        switch (PROCESS) {
                            case OBB:
                                freshCutParcel = OBBDivision.splitParcel(feat,
                                        roads == null || roads.isEmpty() ? null : CollecTransform.selectIntersection(roads, ((Geometry) feat.getDefaultGeometry()).buffer(profile.getMaxDistanceForNearestRoad())),
                                        profile.getMaximalArea(), profile.getMinimalWidthContactRoad(), profile.getHarmonyCoeff(), profile.getIrregularityCoeff(),
                                        CollecTransform.fromPolygonSFCtoListRingLines(blockCollection.subCollection(ff.bbox(ff.property(feat.getFeatureType().getGeometryDescriptor().getLocalName()), feat.getBounds()))),
                                        profile.getLaneWidth(), profile.getStreetLane(), profile.getStreetWidth(), true, profile.getBlockShape());
                                break;
                            case SS:
                            case SSoffset:
                                StraightSkeletonDivision.FOLDER_OUT_DEBUG = tmpFolder;
                                freshCutParcel = StraightSkeletonDivision.runTopologicalStraightSkeletonParcelDecomposition(feat, roads, "NOM_VOIE_G", "IMPORTANCE", PROCESS.equals(DivisionType.SSoffset) ? profile.getMaxDepth() : 0,
                                        profile.getMaxDistanceForNearestRoad(), profile.getMinimalArea(), profile.getMinimalWidthContactRoad(), profile.getMaxWidth(),
                                        (profile.getIrregularityCoeff() == 0) ? 0.1 : profile.getIrregularityCoeff(), new MersenneTwister(1), profile.getLaneWidth(), ParcelSchema.getParcelID(feat));
                                break;
                            case OBBThenSS:
                                freshCutParcel = OBBThenSS.applyOBBThenSS(feat,
                                        roads == null || roads.isEmpty() ? null : CollecTransform.selectIntersection(roads, ((Geometry) feat.getDefaultGeometry()).buffer(profile.getMaxDistanceForNearestRoad())),
                                        profile, CollecTransform.fromPolygonSFCtoListRingLines(blockCollection.subCollection(ff.bbox(ff.property(feat.getFeatureType().getGeometryDescriptor().getLocalName()), feat.getBounds()))));
                                break;
                            case FlagDivision:
                                if (buildingFile != null && buildingFile.exists()) {
                                    System.out.println("ConsolidationDivision Critic error : cannot use FlagDivision process without building set");
                                    DataStore dsBuilding = CollecMgmt.getDataStore(buildingFile);
                                    freshCutParcel = FlagDivision.doFlagDivision(feat, roads, dsBuilding.getFeatureSource(dsBuilding.getTypeNames()[0]).getFeatures(), profile.getHarmonyCoeff(), profile.getIrregularityCoeff(),
                                            profile.getMaximalArea(), profile.getMinimalWidthContactRoad(), profile.getLenDriveway(), extLines, exclusionZone);
                                    dsBuilding.dispose();
                                }
                                break;
                        }
                        if (!freshCutParcel.isEmpty() && freshCutParcel.size() > 0) {
                            try (SimpleFeatureIterator it = freshCutParcel.features()) {
                                // every single parcel goes into new collection
                                while (it.hasNext()) {
                                    SimpleFeature freshCut = it.next();
                                    Schemas.setFieldsToSFB(sfBuilderFinalParcel, CollecTransform.selectWhichIntersectMost(parcels, (Geometry) freshCut.getDefaultGeometry()));
                                    sfBuilderFinalParcel.set(CollecMgmt.getDefaultGeomName(), freshCut.getDefaultGeometry());
                                    sfBuilderFinalParcel.set(ParcelSchema.getParcelSectionField(), makeNewSection((String) feat.getAttribute(ParcelSchema.getParcelSectionField())));
                                    sfBuilderFinalParcel.set(ParcelSchema.getParcelNumberField(), String.valueOf(i++));
                                    sfBuilderFinalParcel.set(ParcelSchema.getParcelCommunityField(), feat.getAttribute(ParcelSchema.getParcelCommunityField()));
                                    ((DefaultFeatureCollection) result).add(sfBuilderFinalParcel.buildFeature(Attribute.makeUniqueId()));
                                }
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                } else {// parcel not big enough, we directly put it in the collection
                    Schemas.setFieldsToSFB(sfBuilderFinalParcel, CollecTransform.selectWhichIntersectMost(parcels, (Geometry) feat.getDefaultGeometry()));
                    sfBuilderFinalParcel.set(CollecMgmt.getDefaultGeomName(), feat.getDefaultGeometry());
                    sfBuilderFinalParcel.set(ParcelSchema.getParcelSectionField(), makeNewSection((String) feat.getAttribute(ParcelSchema.getParcelSectionField())));
                    sfBuilderFinalParcel.set(ParcelSchema.getParcelNumberField(), String.valueOf(i));
                    sfBuilderFinalParcel.set(ParcelSchema.getParcelCommunityField(), feat.getAttribute(ParcelSchema.getParcelCommunityField()));
                    ((DefaultFeatureCollection) result).add(sfBuilderFinalParcel.buildFeature(Attribute.makeUniqueId()));
                }
            }
        }
        // merge small parcels
        result = ParcelCollection.mergeTooSmallParcels(result, (int) profile.getMinimalArea(), PROCESS.equals(DivisionType.SS));

        if (result.isEmpty())
            return ParcelCollection.getParcelWithoutSplitField(parcels);
        if (isSAVEINTERMEDIATERESULT()) {
            CollecMgmt.exportSFC(result, new File(outFolder, "parcelConsolidationOnly"), OVERWRITEGEOPACKAGE);
            OVERWRITEGEOPACKAGE = false;
        }
        sfBuilderFinalParcel = ParcelSchema.getSFBWithoutSplit(result.getSchema());
        try (SimpleFeatureIterator it = parcelSaved.features()) {
            while (it.hasNext()) {
                Schemas.setFieldsToSFB(sfBuilderFinalParcel, it.next());
                ((DefaultFeatureCollection) result).add(sfBuilderFinalParcel.buildFeature(Attribute.makeUniqueId()));
            }
        }
        if (isDEBUG()) {
            CollecMgmt.exportSFC(result, new File(tmpFolder, "step3"));
            System.out.println("done step 3");
        }
        return result;
    }

    /**
     * Create a new section name following a precise rule.
     *
     * @param section former name of the next zone
     * @return the section's name
     */
    public String makeNewSection(String section) {
        return "newSection" + section + "ConsolidationDivision";
    }

    /**
     * Check if the input {@link SimpleFeature} has a section field that has been simulated with this present workflow.
     *
     * @param feat {@link SimpleFeature} to test.
     * @return true if the section field is marked with the {@link #makeNewSection(String)} method.
     */
    public boolean isNewSection(SimpleFeature feat) {
        String section = (String) feat.getAttribute(ParcelSchema.getParcelSectionField());
        return section.startsWith("newSection") && section.endsWith("ConsolidationDivision");
    }
}
