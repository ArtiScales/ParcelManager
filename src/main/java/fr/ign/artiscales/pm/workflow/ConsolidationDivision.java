package fr.ign.artiscales.pm.workflow;

import fr.ign.artiscales.pm.decomposition.OBBBlockDecomposition;
import fr.ign.artiscales.pm.decomposition.TopologicalStraightSkeletonParcelDecomposition;
import fr.ign.artiscales.pm.parcelFunction.MarkParcelAttributeFromPosition;
import fr.ign.artiscales.pm.parcelFunction.ParcelCollection;
import fr.ign.artiscales.pm.parcelFunction.ParcelSchema;
import fr.ign.artiscales.tools.geoToolsFunctions.Attribute;
import fr.ign.artiscales.tools.geoToolsFunctions.vectors.Geom;
import fr.ign.artiscales.tools.geoToolsFunctions.vectors.Geopackages;
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
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.filter.FilterFactory2;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

/**
 * Simulation following this workflow merge together the contiguous marked parcels to create zones. The chosen parcel division process (OBB by default) is then applied on each created
 * zone.
 *
 * @author Maxime Colomb
 */
public class ConsolidationDivision extends Workflow {

    public ConsolidationDivision() {
    }

    /**
     * Method that merges the contiguous marked parcels into zones and then split those zones with a given parcel division algorithm (by default, the Oriented Bounding Box).
     * Without PolygonItersection used to operate a final selection
     *
     * @param parcels   The parcels to be merged and cut. Must be marked with the SPLIT filed (see markParcelIntersectMUPOutput for example, with the method concerning MUP-City's output)
     * @param outFolder The folder where will be saved intermediate results and temporary files for debug
     * @param profile   {@link ProfileUrbanFabric} contains the parameters of the wanted urban scene
     * @return the set of parcel with decomposition
     * @throws IOException
     */
    public SimpleFeatureCollection consolidationDivision(SimpleFeatureCollection parcels, File roadFile, File outFolder, ProfileUrbanFabric profile) throws IOException {
        return consolidationDivision(parcels, roadFile, outFolder, profile, null);
    }

    /**
     * Method that merges the contiguous marked parcels into zones and then split those zones with a given parcel division algorithm (by default, the Oriented Bounding Box).
     *
     * @param parcels             The parcels to be merged and cut. Must be marked with the SPLIT filed (see markParcelIntersectMUPOutput for example, with the method concerning MUP-City's output)
     * @param roadFile            Geopackages of the road segments. Can be null.
     * @param outFolder           The folder where will be saved intermediate results and temporary files for debug
     * @param profile             {@link ProfileUrbanFabric} contains the parameters of the wanted urban scene
     * @param polygonIntersection Optional polygon layer that was used to process to the selection of parcels with their intersection. Used to keep only the intersecting simulated parcels. CAn be null
     * @return the set of parcel with decomposition
     * @throws IOException
     */
    public SimpleFeatureCollection consolidationDivision(SimpleFeatureCollection parcels, File roadFile, File outFolder, ProfileUrbanFabric profile,
                                                         File polygonIntersection) throws IOException {
        File tmpFolder = new File(outFolder, "tmp");
        if (DEBUG)
            tmpFolder.mkdirs();
        DefaultFeatureCollection parcelSaved = new DefaultFeatureCollection();
        parcelSaved.addAll(parcels);
        DefaultFeatureCollection parcelToMerge = new DefaultFeatureCollection();
        if (DEBUG) {
            CollecMgmt.exportSFC(parcels, new File(tmpFolder, "step0"));
            System.out.println("done step 0");
        }
        ////////////////
        // first step : round of selection of the intersected parcels
        ////////////////
        Arrays.stream(parcels.toArray(new SimpleFeature[0])).forEach(parcel -> {
            if (parcel.getAttribute(MarkParcelAttributeFromPosition.getMarkFieldName()) != null
                    && (String.valueOf(parcel.getAttribute(MarkParcelAttributeFromPosition.getMarkFieldName()))).equals("1")) {
                parcelToMerge.add(parcel);
                parcelSaved.remove(parcel);
            }
        });
        if (DEBUG) {
            CollecMgmt.exportSFC(parcelToMerge.collection(), new File(tmpFolder, "step1"));
            System.out.println("done step 1");
        }
        ////////////////
        // second step : merge of the parcel that touches themselves by block
        ////////////////
        DefaultFeatureCollection mergedParcels = new DefaultFeatureCollection();
        SimpleFeatureBuilder sfBuilder = ParcelSchema.getSFBMinParcelSplit();
        Geometry multiGeom = Geom.unionSFC(parcelToMerge);
        for (int i = 0; i < multiGeom.getNumGeometries(); i++) {
            sfBuilder.add(multiGeom.getGeometryN(i));
            sfBuilder.set(ParcelSchema.getMinParcelSectionField(), Integer.toString(i));
            sfBuilder.set(ParcelSchema.getMinParcelCommunityField(),
                    CollecTransform.getIntersectingFieldFromSFC(multiGeom.getGeometryN(i), parcels, ParcelSchema.getMinParcelCommunityField()));
            mergedParcels.add(sfBuilder.buildFeature(Attribute.makeUniqueId()));
        }
        if (DEBUG) {
            CollecMgmt.exportSFC(mergedParcels.collection(), new File(tmpFolder, "step2"));
            System.out.println("done step 2");
        }
        ////////////////
        // third step : cuting of the parcels
        ////////////////
        SimpleFeatureCollection roads;
        if (roadFile != null && roadFile.exists()) {
            DataStore sdsRoad = Geopackages.getDataStore(roadFile);
            roads = DataUtilities.collection(CollecTransform.selectIntersection(sdsRoad.getFeatureSource(sdsRoad.getTypeNames()[0]).getFeatures(), mergedParcels));
            sdsRoad.dispose();
        } else
            roads = null;
        SimpleFeatureBuilder sfBuilderFinalParcel = ParcelSchema.getSFBMinParcel();
        DefaultFeatureCollection cutParcels = new DefaultFeatureCollection();
        SimpleFeatureCollection blockCollection = CityGeneration.createUrbanBlock(parcels);
        FilterFactory2 ff = CommonFactoryFinder.getFilterFactory2();
        Arrays.stream(mergedParcels.toArray(new SimpleFeature[0])).forEach(feat -> {
            if (((Geometry) feat.getDefaultGeometry()).getArea() > profile.getMaximalArea()) {
                // Parcel big enough, we cut it
                feat.setAttribute(MarkParcelAttributeFromPosition.getMarkFieldName(), 1);
                try {
                    SimpleFeatureCollection freshCutParcel = new DefaultFeatureCollection();
                    switch (PROCESS) {
                        case "OBB":
                            freshCutParcel = OBBBlockDecomposition.splitParcel(feat,
                                    (roads != null && !roads.isEmpty()) ? CollecTransform.selectIntersection(roads, (Geometry) feat.getDefaultGeometry()) : null,
                                    profile.getMaximalArea(), profile.getMinimalWidthContactRoad(), profile.getHarmonyCoeff(), profile.getNoise(),
                                    CollecTransform.fromPolygonSFCtoListRingLines(blockCollection.subCollection(
                                            ff.bbox(ff.property(feat.getFeatureType().getGeometryDescriptor().getLocalName()), feat.getBounds()))),
                                    profile.getStreetWidth(), profile.getLargeStreetLevel(), profile.getLargeStreetWidth(), true,
                                    profile.getDecompositionLevelWithoutStreet());
                            break;
                        case "SS":
                            freshCutParcel = TopologicalStraightSkeletonParcelDecomposition.runTopologicalStraightSkeletonParcelDecomposition(feat, roads, "NOM_VOIE_G", "IMPORTANCE", outFolder, profile.getMaxDepth(),
                                    profile.getMaxDistanceForNearestRoad(), profile.getMinimalArea(), profile.getMinWidth(), profile.getMaxWidth(),
                                    (profile.getNoise() == 0) ? 0.1 : profile.getNoise(), new MersenneTwister(42), true, profile.getStreetWidth());
                            break;
                    }
                    if (freshCutParcel != null && !freshCutParcel.isEmpty() && freshCutParcel.size() > 0) {
                        SimpleFeatureIterator it = freshCutParcel.features();
                        // every single parcel goes into new collection
                        int i = 0;
                        while (it.hasNext()) {
                            SimpleFeature freshCut = it.next();
                            // that takes time but it's the best way I've found to set a correct section number (to look at the step 2 polygons)
                            String sec = "Default";
                            try (SimpleFeatureIterator ilotIt = mergedParcels.features()) {
                                while (ilotIt.hasNext()) {
                                    SimpleFeature ilot = ilotIt.next();
                                    if (((Geometry) ilot.getDefaultGeometry()).intersects((Geometry) freshCut.getDefaultGeometry())) {
                                        sec = (String) ilot.getAttribute(ParcelSchema.getMinParcelSectionField());
                                        break;
                                    }
                                }
                            } catch (Exception problem) {
                                problem.printStackTrace();
                            }
                            sfBuilderFinalParcel.set(CollecMgmt.getDefaultGeomName(), freshCut.getDefaultGeometry());
                            sfBuilderFinalParcel.set(ParcelSchema.getMinParcelSectionField(), makeNewSection(sec));
                            sfBuilderFinalParcel.set(ParcelSchema.getMinParcelNumberField(), String.valueOf(i++));
                            sfBuilderFinalParcel.set(ParcelSchema.getMinParcelCommunityField(),
                                    feat.getAttribute(ParcelSchema.getMinParcelCommunityField()));
                            cutParcels.add(sfBuilderFinalParcel.buildFeature(Attribute.makeUniqueId()));
                        }
                        it.close();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else {
                // parcel not big enough, we directly put it in the collection
                cutParcels.add(sfBuilderFinalParcel.buildFeature(Attribute.makeUniqueId(), new Object[]{feat.getDefaultGeometry()}));
            }
        });
        DefaultFeatureCollection result = new DefaultFeatureCollection();
        if (DEBUG) {
            CollecMgmt.exportSFC(cutParcels, new File(tmpFolder, "step3"));
            System.out.println("done step 3");
        }
        // merge small parcels
        SimpleFeatureCollection cutBigParcels = ParcelCollection.mergeTooSmallParcels(cutParcels, (int) profile.getMinimalArea());
        SimpleFeatureType schema = ParcelSchema.getSFBMinParcel().getFeatureType();
        Arrays.stream(cutBigParcels.toArray(new SimpleFeature[0])).forEach(feat -> {
            SimpleFeatureBuilder SFBParcel = ParcelSchema.setSFBMinParcelWithFeat(feat, schema);
            result.add(SFBParcel.buildFeature(Attribute.makeUniqueId()));
        });
        if (SAVEINTERMEDIATERESULT) {
            CollecMgmt.exportSFC(result, new File(outFolder, "parcelConsolidationOnly"), OVERWRITEGEOPACKAGE);
            OVERWRITEGEOPACKAGE = false;
        }
        // add initial non cut parcel to final parcels
        Arrays.stream(parcelSaved.toArray(new SimpleFeature[0])).forEach(feat -> {
            SimpleFeatureBuilder SFBParcel = ParcelSchema.setSFBMinParcelWithFeat(feat, schema);
            result.add(SFBParcel.buildFeature(Attribute.makeUniqueId()));
        });
        if (DEBUG) {
            CollecMgmt.exportSFC(result, new File(tmpFolder, "step4"));
            System.out.println("done step 4");
        }
        // //If the selection of parcel was based on a polygon intersection file, we keep only the intersection parcels
        // //TODO avec une emprise plus large que juste les parcelles : regarder du côté de morpholim pour un calculer un buffer suffisant ?)
        // if (polygonIntersection != null && polygonIntersection.exists()) {
        // ShapefileDataStore sdsInter = new ShapefileDataStore(polygonIntersection.toURI().toURL());
        // SimpleFeatureCollection sfc = sdsInter.getFeatureSource().getFeatures();
        // sdsInter.dispose();
        // }
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
        String section = (String) feat.getAttribute(ParcelSchema.getMinParcelSectionField());
        return section.startsWith("newSection") && section.endsWith("ConsolidationDivision");
    }
}
