package fr.ign.artiscales.pm.parcelFunction;

import fr.ign.artiscales.pm.fields.GeneralFields;
import fr.ign.artiscales.pm.fields.french.FrenchZoningSchemas;
import fr.ign.artiscales.pm.workflow.ConsolidationDivision;
import fr.ign.artiscales.pm.workflow.Densification;
import fr.ign.artiscales.pm.workflow.ZoneDivision;
import fr.ign.artiscales.tools.geoToolsFunctions.Attribute;
import fr.ign.artiscales.tools.geoToolsFunctions.Schemas;
import fr.ign.artiscales.tools.geoToolsFunctions.vectors.Geom;
import fr.ign.artiscales.tools.geoToolsFunctions.vectors.collec.CollecMgmt;
import fr.ign.artiscales.tools.geoToolsFunctions.vectors.collec.CollecTransform;
import fr.ign.artiscales.tools.geoToolsFunctions.vectors.collec.OpOnCollec;
import fr.ign.artiscales.tools.geoToolsFunctions.vectors.geom.Polygons;
import org.geotools.data.DataStore;
import org.geotools.data.DataUtilities;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.factory.CommonFactoryFinder;
import org.geotools.feature.DefaultFeatureCollection;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.locationtech.jts.geom.Geometry;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.type.AttributeDescriptor;
import org.opengis.filter.FilterFactory2;
import org.opengis.filter.expression.PropertyName;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Static methods to mark parcel collections relatively to many other geographic elements, parcel attributes or geometries or other sets of parcels. Doesn't change the input
 * {@link SimpleFeatureCollection} structure but add a marking field. This field's name is <i>SPLIT</i> by default and can be changed.
 *
 * @author Maxime Colomb
 */
public class MarkParcelAttributeFromPosition {

    /**
     * The buildings that have an area under this value will be considered as light buildings and won't be considered when we are looking if a parcel is built or not
     */
    static double uncountedBuildingArea = 20;
    private static String markFieldName = "SPLIT";
    /**
     * Specify that the marking is made before (false) or after (true) the simulation process.
     * It won't or will allow the marking of the already simulated parcels.
     */
    private static boolean postMark = false;

//    public static void main(String[] args) throws Exception {
//        long startTime = System.currentTimeMillis();
//        File parcelsMarkedF = new File("src/main/resources/TestScenario/OutputResults/OBB/parcelDensification.gpkg");
//        File roadF = new File("src/main/resources/TestScenario/InputData/road.gpkg");
//        DataStore dsParcel = CollecMgmt.getDataStore(parcelsMarkedF);
//        DataStore dsRoad = CollecMgmt.getDataStore(roadF);
//
//        SimpleFeatureCollection parcels = dsParcel.getFeatureSource(dsParcel.getTypeNames()[0]).getFeatures();
//        SimpleFeatureCollection markedZone = MarkParcelAttributeFromPosition.markParcelsNotConnectedToRoad(parcels, CityGeneration.createUrbanBlock(parcels), dsRoad.getFeatureSource(dsRoad.getTypeNames()[0]).getFeatures(), null);
//        CollecMgmt.exportSFC(markedZone, new File("/tmp/parcelNotConnectedToRoad"));
//        dsParcel.dispose();
//        long stopTime = System.currentTimeMillis();
//        System.out.println(stopTime - startTime);
//
////	 File parcelsMarkedF = new File("/tmp/start.gpkg");
////	 File parcelsToMarkF = new File("/tmp/dens.gpkg");
////
////	 ShapefileDataStore sdsParcelMarked = new ShapefileDataStore(parcelsMarkedF.toURI().toURL());
////	 ShapefileDataStore sdsparcelsToMark = new ShapefileDataStore(parcelsToMarkF.toURI().toURL());
////
////	 SimpleFeatureCollection parcelsMarked = sdsParcelMarked.getFeatureSource().getFeatures();
////	 SimpleFeatureCollection parcelsToMark = sdsparcelsToMark.getFeatureSource().getFeatures();
////
////	 Collec.exportSFC(markAlreadyMarkedParcels(parcelsToMark, parcelsMarked),new File("/tmp/das"));
//
//    }

    /**
     * Mark the parcels that have a connection to the road network, represented either by the void of parcels or road lines (optional)
     *
     * @param parcels  Input parcel {@link SimpleFeatureCollection}
     * @param block    {@link SimpleFeatureCollection} containing the morphological block. Can be generated with the
     *                 {@link fr.ign.artiscales.tools.geometryGeneration.CityGeneration#createUrbanBlock(SimpleFeatureCollection)} method.
     * @param roadFile Geopackage containing the road segments
     * @return {@link SimpleFeatureCollection} of the input parcels with marked parcels on the {@link #markFieldName} field.
     * @throws IOException reading roadFile
     */
    public static SimpleFeatureCollection markParcelsConnectedToRoad(SimpleFeatureCollection parcels, SimpleFeatureCollection block, File roadFile) throws IOException {
        return markParcelsConnectedToRoad(parcels, block, roadFile, null);
    }

    /**
     * Mark the parcels that have a connection to the road network, represented either by the void of parcels or road lines (optional)
     *
     * @param parcels       Input parcel {@link SimpleFeatureCollection}
     * @param block         {@link SimpleFeatureCollection} containing the morphological block. Can be generated with the
     *                      {@link fr.ign.artiscales.tools.geometryGeneration.CityGeneration#createUrbanBlock(SimpleFeatureCollection)} method.
     * @param roadFile      Geo file containing the road segments
     * @param exclusionZone Zone to be excluded for not counting an empty parcel as a road
     * @return {@link SimpleFeatureCollection} of the input parcels with marked parcels on the {@link #markFieldName} field.
     * @throws IOException reading roadFile
     */
    public static SimpleFeatureCollection markParcelsConnectedToRoad(SimpleFeatureCollection parcels, SimpleFeatureCollection block, File roadFile, Geometry exclusionZone) throws IOException {
        DataStore ds = CollecMgmt.getDataStore(roadFile);
        SimpleFeatureCollection result = markParcelsConnectedToRoad(parcels, block, ds.getFeatureSource(ds.getTypeNames()[0]).getFeatures(), exclusionZone);
        ds.dispose();
        return result;
    }

    /**
     * Mark the parcels that have a connection to the road network, represented either by the void of parcels or road lines (optional)
     *
     * @param parcels       Input parcel {@link SimpleFeatureCollection}
     * @param block         {@link SimpleFeatureCollection} containing the morphological block. Can be generated with the
     *                      {@link fr.ign.artiscales.tools.geometryGeneration.CityGeneration#createUrbanBlock(SimpleFeatureCollection)} method.
     * @param road          road segments
     * @param exclusionZone Zone to be excluded for not counting an empty parcel as a road
     * @return {@link SimpleFeatureCollection} of the input parcels with marked parcels on the {@link #markFieldName} field.
     */
    public static SimpleFeatureCollection markParcelsConnectedToRoad(SimpleFeatureCollection parcels, SimpleFeatureCollection block, SimpleFeatureCollection road, Geometry exclusionZone) {
        SimpleFeatureCollection roads = CollecTransform.selectIntersection(road, parcels);
        SimpleFeatureBuilder builder = ParcelSchema.addMarkField(parcels.getSchema());
        DefaultFeatureCollection result = new DefaultFeatureCollection();
        try (SimpleFeatureIterator it = parcels.features()) {
            while (it.hasNext()) {
                SimpleFeature feat = it.next();
                Geometry geomFeat = (Geometry) feat.getDefaultGeometry();
                Schemas.setFieldsToSFB(builder, feat);
                if (isAlreadyMarked(feat) != 0
                        && ParcelState.isParcelHasRoadAccess(Polygons.getPolygon(geomFeat), CollecTransform.selectIntersection(roads, geomFeat),
                        CollecTransform.fromPolygonSFCtoRingMultiLines(CollecTransform.selectIntersection(block, geomFeat)), exclusionZone))
                    builder.set(markFieldName, 1);
                else
                    builder.set(markFieldName, 0);
                result.add(builder.buildFeature(Attribute.makeUniqueId()));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        signalIfNoParcelMarked(result, "markParcelsConnectedToRoad");
        return result;
    }

    /**
     * Mark the parcels that have no connection to the road network, represented either by the void of parcels or road lines (optional).
     *
     * @param parcels       Input parcel {@link SimpleFeatureCollection}
     * @param block         {@link SimpleFeatureCollection} containing the morphological block. Can be generated with the
     *                      {@link fr.ign.artiscales.tools.geometryGeneration.CityGeneration#createUrbanBlock(SimpleFeatureCollection)} method.
     * @param roadFile      road File
     * @param exclusionZone Zone to be excluded for not counting an empty parcel as a road
     * @return {@link SimpleFeatureCollection} of the input parcels with marked parcels on the {@link #markFieldName} field.
     */
    public static SimpleFeatureCollection markParcelsNotConnectedToRoad(SimpleFeatureCollection parcels, SimpleFeatureCollection block, File roadFile, Geometry exclusionZone) throws IOException {
        DataStore ds = CollecMgmt.getDataStore(roadFile);
        SimpleFeatureCollection result = markParcelsNotConnectedToRoad(parcels, block, ds.getFeatureSource(ds.getTypeNames()[0]).getFeatures(), exclusionZone);
        ds.dispose();
        return result;
    }

    /**
     * Mark the parcels that have no connection to the road network, represented either by the void of parcels or road lines (optional).
     *
     * @param parcels       Input parcel {@link SimpleFeatureCollection}
     * @param block         {@link SimpleFeatureCollection} containing the morphological block. Can be generated with the
     *                      {@link fr.ign.artiscales.tools.geometryGeneration.CityGeneration#createUrbanBlock(SimpleFeatureCollection)} method.
     * @param road          road segments
     * @param exclusionZone Zone to be excluded for not counting an empty parcel as a road
     * @return {@link SimpleFeatureCollection} of the input parcels with marked parcels on the {@link #markFieldName} field.
     */
    public static SimpleFeatureCollection markParcelsNotConnectedToRoad(SimpleFeatureCollection parcels, SimpleFeatureCollection block, SimpleFeatureCollection road, Geometry exclusionZone) {
        SimpleFeatureCollection roads = CollecTransform.selectIntersection(road, parcels);
        SimpleFeatureBuilder builder = ParcelSchema.addMarkField(parcels.getSchema());
        DefaultFeatureCollection result = new DefaultFeatureCollection();
        try (SimpleFeatureIterator it = parcels.features()) {
            while (it.hasNext()) {
                SimpleFeature feat = it.next();
                Geometry geomFeat = (Geometry) feat.getDefaultGeometry();
                Schemas.setFieldsToSFB(builder, feat);
                if (isAlreadyMarked(feat) != 0
                        && !ParcelState.isParcelHasRoadAccess(Polygons.getPolygon(geomFeat), CollecTransform.selectIntersection(roads, geomFeat),
                        CollecTransform.fromPolygonSFCtoRingMultiLines(CollecTransform.selectIntersection(block, geomFeat)), exclusionZone))
                    builder.set(markFieldName, 1);
                else
                    builder.set(markFieldName, 0);
                result.add(builder.buildFeature(Attribute.makeUniqueId()));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        signalIfNoParcelMarked(result, "markParcelsConnectedToRoad");
        return result;
    }

    /**
     * Mark the parcels that size are superior or equal to a given threshold.
     *
     * @param parcels Input parcel {@link SimpleFeatureCollection}
     * @param size    Area threshold
     * @return {@link SimpleFeatureCollection} of the input parcels with marked parcels on the {@link #markFieldName} field.
     */
    public static SimpleFeatureCollection markParcelsInf(SimpleFeatureCollection parcels, double size) {
        SimpleFeatureBuilder builder = ParcelSchema.addMarkField(parcels.getSchema());
        DefaultFeatureCollection result = new DefaultFeatureCollection();
        try (SimpleFeatureIterator it = parcels.features()) {
            while (it.hasNext()) {
                SimpleFeature feat = it.next();
                Schemas.setFieldsToSFB(builder, feat);
                if (isAlreadyMarked(feat) != 0 && ((Geometry) feat.getDefaultGeometry()).getArea() <= size)
                    builder.set(markFieldName, 1);
                else
                    builder.set(markFieldName, 0);
                result.add(builder.buildFeature(Attribute.makeUniqueId()));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        signalIfNoParcelMarked(result, "markParcelsInf");
        return result;
    }

    /**
     * Mark the parcels that size are inferior to a given threshold.
     *
     * @param parcels Input parcel {@link SimpleFeatureCollection}
     * @param size    Area threshold
     * @return {@link SimpleFeatureCollection} of the input parcels with marked parcels on the {@link #markFieldName} field.
     */
    public static SimpleFeatureCollection markParcelsSup(SimpleFeatureCollection parcels, double size) {
        SimpleFeatureBuilder builder = ParcelSchema.addMarkField(parcels.getSchema());
        DefaultFeatureCollection result = new DefaultFeatureCollection();
        try (SimpleFeatureIterator it = parcels.features()) {
            while (it.hasNext()) {
                SimpleFeature feat = it.next();
                Schemas.setFieldsToSFB(builder, feat);
                if (isAlreadyMarked(feat) != 0 && ((Geometry) feat.getDefaultGeometry()).getArea() > size)
                    builder.set(markFieldName, 1);
                else
                    builder.set(markFieldName, 0);
                result.add(builder.buildFeature(Attribute.makeUniqueId()));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        signalIfNoParcelMarked(result, "markParcelsSup");
        return result;
    }

    /**
     * Mark a given number of parcel for the simulation. The selection is random but parcels must be bigger than a certain area threshold.
     *
     * @param parcels           Input parcel {@link SimpleFeatureCollection}
     * @param nbParcelToMark    number of parcel wanted
     * @param markExistingMarks if true, randomly marked parcels will only be pe-existing parcels. If false, random follows a new selection
     * @return {@link SimpleFeatureCollection} of the input parcels with random marked parcels on the {@link #markFieldName} field.
     * @throws IOException reading zoningFile
     */
    public static SimpleFeatureCollection markRandomParcels(SimpleFeatureCollection parcels, int nbParcelToMark, boolean markExistingMarks) throws IOException {
        return markRandomParcels(parcels, null, null, 0, nbParcelToMark, markExistingMarks);
    }

    /**
     * Mark a given number of parcel for the simulation. The selection is random but parcels can be bigger than a certain area threshold and can be contained is a given zoning type.
     *
     * @param parcels           Input parcel {@link SimpleFeatureCollection}
     * @param minSize           Minimal size of parcels to be selected (optional)
     * @param genericZone       Type of the zoning plan to take into consideration
     * @param zoningFile        Geopackage containing the zoning plan
     * @param nbParcelToMark    Number of parcel wanted
     * @param markExistingMarks if true, randomly marked parcels will only be pe-existing parcels. If false, random follows a new selection
     * @return {@link SimpleFeatureCollection} of the input parcels with random marked parcels on the {@link #markFieldName} field.
     * @throws IOException reading zoningFile
     */
    public static SimpleFeatureCollection markRandomParcels(SimpleFeatureCollection parcels, String genericZone, File zoningFile, double minSize, int nbParcelToMark, boolean markExistingMarks) throws IOException {
        if (zoningFile != null && genericZone != null)
            parcels = markParcelIntersectGenericZoningType(parcels, genericZone, zoningFile);
        DefaultFeatureCollection result = new DefaultFeatureCollection();
        if (markExistingMarks) {
            List<SimpleFeature> list = Arrays.stream(getOnlyMarkedParcels(parcels).toArray(new SimpleFeature[0])).filter(feat -> ((Geometry) feat.getDefaultGeometry()).getArea() > minSize).collect(Collectors.toList());
            Collections.shuffle(list);
            for (SimpleFeature sf : list) {
                if (nbParcelToMark > 0) {
                    result.add(sf);
                    nbParcelToMark--;
                } else {
                    sf.setAttribute(markFieldName, 0);
                    result.add(sf);
                }
            }
            result.addAll(getNotMarkedParcels(parcels));
        } else {
            List<SimpleFeature> list = Arrays.stream(parcels.toArray(new SimpleFeature[0])).filter(feat -> ((Geometry) feat.getDefaultGeometry()).getArea() > minSize).collect(Collectors.toList());
            Collections.shuffle(list);
            for (SimpleFeature sf : list)
                if (nbParcelToMark > 0) {
                    result.add(markParcel(sf));
                    nbParcelToMark--;
                } else
                    result.add(markParcel(sf, 0));
        }
        signalIfNoParcelMarked(result, "markRandomParcels");
        return result;
    }

    /**
     * Mark the unbuilt parcels. Subtract a buffer of 1 meters on the buildings to avoid blurry parcel and building definition
     *
     * @param parcels      Input parcel {@link SimpleFeatureCollection}
     * @param buildingFile Geo file containing building features
     * @return {@link SimpleFeatureCollection} of the input parcels with marked parcels on the {@link #markFieldName} field.
     * @throws IOException reading building file
     */
    public static SimpleFeatureCollection markUnBuiltParcel(SimpleFeatureCollection parcels, File buildingFile) throws IOException {
        DataStore ds = CollecMgmt.getDataStore(buildingFile);
        SimpleFeatureCollection result = markUnBuiltParcel(parcels, ds.getFeatureSource(ds.getTypeNames()[0]).getFeatures());
        ds.dispose();
        return result;
    }

    /**
     * Mark the unbuilt parcels. Subtract a buffer of 1 meters on the buildings to avoid blurry parcel and building definition
     *
     * @param parcels     Input parcel {@link SimpleFeatureCollection}
     * @param buildingSFC building features
     * @return {@link SimpleFeatureCollection} of the input parcels with marked parcels on the {@link #markFieldName} field.
     */
    public static SimpleFeatureCollection markUnBuiltParcel(SimpleFeatureCollection parcels, SimpleFeatureCollection buildingSFC) {
        SimpleFeatureCollection buildings = CollecTransform.selectIntersection(buildingSFC, parcels);
        SimpleFeatureBuilder builder = ParcelSchema.addMarkField(parcels.getSchema());
        DefaultFeatureCollection result = new DefaultFeatureCollection();
        try (SimpleFeatureIterator it = parcels.features()) {
            while (it.hasNext()) {
                SimpleFeature feat = it.next();
                Schemas.setFieldsToSFB(builder, feat);
                if (isAlreadyMarked(feat) != 0
                        && !ParcelState.isAlreadyBuilt(CollecTransform.selectIntersection(buildings, (Geometry) feat.getDefaultGeometry()), feat, -1.0, uncountedBuildingArea))
                    builder.set(markFieldName, 1);
                else
                    builder.set(markFieldName, 0);
                result.add(builder.buildFeature(Attribute.makeUniqueId()));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        signalIfNoParcelMarked(result, "markUnBuiltParcel");
        return result;
    }

    /**
     * Mark the built parcels. Subtract a buffer of 1 meters on the buildings to avoid blurry parcel and building definition
     *
     * @param parcels      Input parcel {@link SimpleFeatureCollection}
     * @param buildingFile Geo file representing the buildings
     * @return {@link SimpleFeatureCollection} of the input parcels with marked parcels on the {@link #markFieldName} field.
     * @throws IOException reading building file
     */
    public static SimpleFeatureCollection markBuiltParcel(SimpleFeatureCollection parcels, File buildingFile) throws IOException {
        DataStore buildingDS = CollecMgmt.getDataStore(buildingFile);
        SimpleFeatureCollection result = markBuiltParcel(parcels, buildingDS.getFeatureSource(buildingDS.getTypeNames()[0]).getFeatures());
        buildingDS.dispose();
        return result;
    }

    /**
     * Mark the built parcels. Subtract a buffer of 1 meters on the buildings to avoid blurry parcel and building definition
     *
     * @param parcels  Input parcel {@link SimpleFeatureCollection}
     * @param building collection of  buildings
     * @return {@link SimpleFeatureCollection} of the input parcels with marked parcels on the {@link #markFieldName} field.
     */
    public static SimpleFeatureCollection markBuiltParcel(SimpleFeatureCollection parcels, SimpleFeatureCollection building) {
        SimpleFeatureCollection buildings = CollecTransform.selectIntersection(DataUtilities.collection(building), parcels);
        SimpleFeatureBuilder builder = ParcelSchema.addMarkField(parcels.getSchema());
        DefaultFeatureCollection result = new DefaultFeatureCollection();
        try (SimpleFeatureIterator it = parcels.features()) {
            while (it.hasNext()) {
                SimpleFeature feat = it.next();
                Schemas.setFieldsToSFB(builder, feat);
                if (isAlreadyMarked(feat) != 0 && ParcelState.isAlreadyBuilt(
                        CollecTransform.selectIntersection(buildings, (Geometry) feat.getDefaultGeometry()), feat, -1.0, uncountedBuildingArea))
                    builder.set(markFieldName, 1);
                else
                    builder.set(markFieldName, 0);
                result.add(builder.buildFeature(Attribute.makeUniqueId()));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        signalIfNoParcelMarked(result, "markBuiltParcel");
        return result;
    }

    /**
     * Mark parcels that intersects a given collection of polygons. The default field name containing the mark is "SPLIT" but it can be changed with the
     * {@link #setMarkFieldName(String)} method. Less optimized method that takes a Geometry as an input
     *
     * @param parcels Input parcel {@link SimpleFeatureCollection}
     * @param geoms   a {@link List} of {@link Geometry} which can intersect parcels
     * @return {@link SimpleFeatureCollection} of the input parcels with marked parcels on the {@link #markFieldName} field.
     */
    public static SimpleFeatureCollection markParcelIntersectPolygonIntersection(SimpleFeatureCollection parcels, List<Geometry> geoms) {
        DefaultFeatureCollection result = new DefaultFeatureCollection();
        SimpleFeatureBuilder builder = ParcelSchema.addMarkField(parcels.getSchema());
        try (SimpleFeatureIterator it = parcels.features()) {
            while (it.hasNext()) {
                SimpleFeature feat = it.next();
                Schemas.setFieldsToSFB(builder, feat);
                if (isAlreadyMarked(feat) != 0 && !Geom
                        .unionPrecisionReduce(
                                geoms.stream().filter(g -> g.intersects((Geometry) feat.getDefaultGeometry())).collect(Collectors.toList()), 100)
                        .isEmpty())
                    builder.set(markFieldName, 1);
                else
                    builder.set(markFieldName, 0);
                result.add(builder.buildFeature(Attribute.makeUniqueId()));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        signalIfNoParcelMarked(result, "markParcelIntersectPolygonIntersection");
        return result;
    }

    /**
     * Mark parcels that intersects a given collection of polygons. The default field name containing the mark is "SPLIT" but it can be changed with the
     * {@link #setMarkFieldName(String)} method.
     *
     * @param parcels                 Input parcel {@link SimpleFeatureCollection}
     * @param polygonIntersectionFile A Geopackage containing the collection of polygons
     * @return {@link SimpleFeatureCollection} of the input parcels with marked parcels on the {@link #markFieldName} field.
     * @throws IOException reading polygonIntersectionFile
     */
    public static SimpleFeatureCollection markParcelIntersectPolygonIntersection(SimpleFeatureCollection parcels, File polygonIntersectionFile) throws IOException {
        DefaultFeatureCollection result = new DefaultFeatureCollection();
        DataStore ds = CollecMgmt.getDataStore(polygonIntersectionFile);
        SimpleFeatureCollection polyCollec = ds.getFeatureSource(ds.getTypeNames()[0]).getFeatures();
        SimpleFeatureBuilder builder = ParcelSchema.addMarkField(parcels.getSchema());
        try (SimpleFeatureIterator it = parcels.features()) {
            while (it.hasNext()) {
                SimpleFeature feat = it.next();
                Schemas.setFieldsToSFB(builder, feat);
                if (isAlreadyMarked(feat) != 0 && OpOnCollec.isFeatIntersectsSFC(feat, polyCollec))
                    builder.set(markFieldName, 1);
                else
                    builder.set(markFieldName, 0);
                result.add(builder.buildFeature(Attribute.makeUniqueId()));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        ds.dispose();
        signalIfNoParcelMarked(result, "markParcelIntersectPolygonIntersection");
        return result;
    }

    /**
     * Mark parcels that meet a certain pattern in an attribute
     *
     * @param parcels             Input {@link SimpleFeatureCollection}
     * @param attributeFieldName  Name of the attribute field
     * @param attributeFieldValue Value of the attribute field to mark
     * @return {@link SimpleFeatureCollection} of the input parcels with marked parcels on the {@link #markFieldName} field
     */
    public static SimpleFeatureCollection markSFCWithAttributeField(SimpleFeatureCollection parcels, String attributeFieldName, String attributeFieldValue) {
        DefaultFeatureCollection result = new DefaultFeatureCollection();
        if (!CollecMgmt.isCollecContainsAttribute(parcels, attributeFieldName)) {
            signalIfNoParcelMarked(parcels, "markParcelWithAttributeField");
            return parcels;
        }
        SimpleFeatureBuilder builder = ParcelSchema.addMarkField(parcels.getSchema());
        try (SimpleFeatureIterator it = parcels.features()) {
            while (it.hasNext()) {
                SimpleFeature feat = it.next();
                if (isAlreadyMarked(feat) != 0 && feat.getAttribute(attributeFieldName).equals(attributeFieldValue))
                    builder.set(markFieldName, 1);
                else
                    builder.set(markFieldName, 0);
                result.add(builder.buildFeature(Attribute.makeUniqueId()));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        signalIfNoParcelMarked(result, "markParcelIntersectGenericZoningType");
        return result;
    }

    /**
     * Mark parcels that intersects a certain type of <i>generic zone</i>.
     *
     * @param parcels     Input parcel {@link SimpleFeatureCollection}
     * @param genericZone The big kind of the zoning (either not constructible (NC), urbanizable (U) or to be urbanize (TBU). Other keywords can be tolerate
     * @param zoningFile  A Geopackage containing the zoning plan
     * @return {@link SimpleFeatureCollection} of the input parcels with marked parcels on the {@link #markFieldName} field.
     * @throws IOException reading zoningFile
     */
    public static SimpleFeatureCollection markParcelIntersectGenericZoningType(SimpleFeatureCollection parcels, String genericZone, File zoningFile) throws IOException {
        DataStore dsZone = CollecMgmt.getDataStore(zoningFile);
        SimpleFeatureCollection result = markParcelIntersectGenericZoningType(parcels, genericZone, CollecTransform.selectIntersection(dsZone.getFeatureSource(dsZone.getTypeNames()[0]).getFeatures(), parcels));
        dsZone.dispose();
        return result;
    }

    /**
     * Mark parcels that intersects a certain type of <i>generic zone</i>.
     *
     * @param parcels     Input parcel {@link SimpleFeatureCollection}
     * @param genericZone The big kind of the zoning (either not constructible (NC), urbanizable (U) or to be urbanize (TBU). Other keywords can be tolerate
     * @param zoningSFC   Collection of the zoning plan
     * @return {@link SimpleFeatureCollection} of the input parcels with marked parcels on the {@link #markFieldName} field.
     */
    public static SimpleFeatureCollection markParcelIntersectGenericZoningType(SimpleFeatureCollection parcels, String genericZone, SimpleFeatureCollection zoningSFC) {
        SimpleFeatureBuilder builder = ParcelSchema.addMarkField(parcels.getSchema());
        DefaultFeatureCollection result = new DefaultFeatureCollection();
        List<String> genericZoneUsualNames = GeneralFields.getGenericZoneUsualNames(genericZone);
        try (SimpleFeatureIterator it = parcels.features()) {
            while (it.hasNext()) {
                SimpleFeature feat = it.next();
                Schemas.setFieldsToSFB(builder, feat);
                if (isAlreadyMarked(feat) != 0 && genericZoneUsualNames.contains(
                        CollecTransform.getIntersectingFieldFromSFC((Geometry) feat.getDefaultGeometry(), zoningSFC, GeneralFields.getZoneGenericNameField())))
                    builder.set(markFieldName, 1);
                else
                    builder.set(markFieldName, 0);
                result.add(builder.buildFeature(Attribute.makeUniqueId()));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        signalIfNoParcelMarked(result, "markParcelIntersectGenericZoningType");
        return result;
    }

    /**
     * Mark parcels that intersects a certain type of a <i>generic zone</i> but not mark them if they are from a given list of <i>precise zone</i>.
     *
     * @param parcels     Input parcel {@link SimpleFeatureCollection}
     * @param genericZone The big kind of the zoning (either not constructible (NC), urbanizable (U) or to be urbanize (TBU). Other keywords can be tolerate
     * @param preciseZone List of precise zone to not take into account
     * @param zoningFile  A Geopackage containing the zoning plan
     * @return {@link SimpleFeatureCollection} of the input parcels with marked parcels on the {@link #markFieldName} field.
     * @throws IOException reading zoningFile
     */
    public static SimpleFeatureCollection markParcelIntersectZoningWithoutPreciseZonings(SimpleFeatureCollection parcels, String genericZone,
                                                                                         List<String> preciseZone, File zoningFile) throws IOException {
        SimpleFeatureBuilder builder = ParcelSchema.addMarkField(parcels.getSchema());
        DefaultFeatureCollection result = new DefaultFeatureCollection();
        List<String> genericZoneUsualNames = GeneralFields.getGenericZoneUsualNames(genericZone);
        DataStore dsZone = CollecMgmt.getDataStore(zoningFile);
        SimpleFeatureCollection zoningSFC = CollecTransform.selectIntersection(dsZone.getFeatureSource(dsZone.getTypeNames()[0]).getFeatures(), parcels);
        try (SimpleFeatureIterator it = parcels.features()) {
            while (it.hasNext()) {
                SimpleFeature feat = it.next();
                Schemas.setFieldsToSFB(builder, feat);
                if (isAlreadyMarked(feat) != 0
                        && genericZoneUsualNames.contains(CollecTransform.getIntersectingFieldFromSFC((Geometry) feat.getDefaultGeometry(), zoningSFC,
                        GeneralFields.getZoneGenericNameField()))
                        && !preciseZone.contains(CollecTransform.getIntersectingFieldFromSFC((Geometry) feat.getDefaultGeometry(), zoningSFC,
                        GeneralFields.getZonePreciseNameField())))
                    builder.set(markFieldName, 1);
                else
                    builder.set(markFieldName, 0);
                result.add(builder.buildFeature(Attribute.makeUniqueId()));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        dsZone.dispose();
        signalIfNoParcelMarked(result, "markParcelIntersectZoningWithoutPreciseZonings");
        return result;
    }

    /**
     * Mark parcels that intersects a certain type of Generic zoning.
     *
     * @param parcels     Input parcel {@link SimpleFeatureCollection}
     * @param genericZone The generic type the zoning (either not constructible (NC), urbanizable (U) or to be urbanize (TBU). Other keywords can be tolerate
     * @param preciseZone The precise zoning type. Can be anything.
     * @param zoningFile  A geo file containing the zoning plan
     * @return {@link SimpleFeatureCollection} of the input parcels with marked parcels on the {@link #markFieldName} field.
     * @throws IOException reading zoningFile
     */
    public static SimpleFeatureCollection markParcelIntersectPreciseZoningType(SimpleFeatureCollection parcels, String genericZone, String preciseZone, File zoningFile) throws IOException {
        SimpleFeatureBuilder builder = ParcelSchema.addMarkField(parcels.getSchema());
        // Get the zoning usual names
        List<String> genericZoneUsualNames = GeneralFields.getGenericZoneUsualNames(genericZone);
        DefaultFeatureCollection result = new DefaultFeatureCollection();
        DataStore dsZone = CollecMgmt.getDataStore(zoningFile);
        SimpleFeatureCollection zoningSFC = CollecTransform.selectIntersection(dsZone.getFeatureSource(dsZone.getTypeNames()[0]).getFeatures(), parcels);
        try (SimpleFeatureIterator it = parcels.features()) {
            while (it.hasNext()) {
                SimpleFeature feat = it.next();
                Schemas.setFieldsToSFB(builder, feat);
                if (isAlreadyMarked(feat) != 0 && (genericZone == null || genericZone.equals("") ||
                        genericZoneUsualNames.contains(CollecTransform.getIntersectingFieldFromSFC((Geometry) feat.getDefaultGeometry(), zoningSFC, GeneralFields.getZoneGenericNameField())))
                        && preciseZone.equalsIgnoreCase(CollecTransform.getIntersectingFieldFromSFC((Geometry) feat.getDefaultGeometry(), zoningSFC, GeneralFields.getZonePreciseNameField())))
                    builder.set(markFieldName, 1);
                else
                    builder.set(markFieldName, 0);
                result.add(builder.buildFeature(Attribute.makeUniqueId()));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        dsZone.dispose();
        signalIfNoParcelMarked(result, "markParcelIntersectPreciseZoningType with " + preciseZone);
        return result;
    }

    /**
     * Mark parcels that intersects that are usually constructible for French regulation
     *
     * @param parcels    Input parcel {@link SimpleFeatureCollection}
     * @param zoningFile A geo file containing the zoning plan
     * @return {@link SimpleFeatureCollection} of the input parcels with marked parcels on the {@link #markFieldName} field.
     * @throws IOException reading zoningFile
     */
    public static SimpleFeatureCollection markParcelIntersectFrenchConstructibleZoningType(SimpleFeatureCollection parcels, File zoningFile) throws IOException {
        DataStore ds = CollecMgmt.getDataStore(zoningFile);
        SimpleFeatureCollection result = markParcelIntersectFrenchConstructibleZoningType(parcels, ds.getFeatureSource(ds.getTypeNames()[0]).getFeatures());
        ds.dispose();
        return result;
    }

    /**
     * Mark parcels that intersects that are usually constructible for French regulation
     *
     * @param parcels Input parcel {@link SimpleFeatureCollection}
     * @param zoning  A collection containing the zoning plan
     * @return {@link SimpleFeatureCollection} of the input parcels with marked parcels on the {@link #markFieldName} field.
     */
    public static SimpleFeatureCollection markParcelIntersectFrenchConstructibleZoningType(SimpleFeatureCollection parcels, SimpleFeatureCollection zoning) {
        SimpleFeatureBuilder builder = ParcelSchema.addMarkField(parcels.getSchema());
        DefaultFeatureCollection result = new DefaultFeatureCollection();
        try (SimpleFeatureIterator it = parcels.features()) {
            while (it.hasNext()) {
                SimpleFeature feat = it.next();
                Schemas.setFieldsToSFB(builder, feat);
                if (isAlreadyMarked(feat) != 0 && FrenchZoningSchemas.isUrbanZoneUsuallyAdmitResidentialConstruction(
                        CollecTransform.getIntersectingSimpleFeatureFromSFC((Geometry) feat.getDefaultGeometry(), zoning)))
                    builder.set(markFieldName, 1);
                else
                    builder.set(markFieldName, 0);
                result.add(builder.buildFeature(Attribute.makeUniqueId()));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        signalIfNoParcelMarked(result, "markParcelIntersectFrenchConstructibleZoningType");
        return result;
    }

    /**
     * Mark parcels if its community type equals a given value
     *
     * @param parcels   parcel feature
     * @param attribute value for the community type to be marked
     * @return the collection with marked parcels
     */
    public static SimpleFeatureCollection markParcelOfCommunityType(SimpleFeatureCollection parcels, String attribute) {
        return markParcelWithAttribute(parcels, ParcelAttribute.getCommunityTypeFieldName(), attribute);
    }

    /**
     * Mark parcels if its community number equals a given value
     *
     * @param parcels   parcel feature
     * @param attribute value for the community number to be marked
     * @return the collection with marked parcels
     */
    public static SimpleFeatureCollection markParcelOfCommunityNumber(SimpleFeatureCollection parcels, String attribute) {
        return markParcelWithAttribute(parcels, ParcelSchema.getParcelCommunityField(), attribute);
    }

    /**
     * Mark parcels if one of its attribute equals a given value
     *
     * @param parcels   parcel feature
     * @param fieldName name of the field
     * @param attribute value for the given field to be marked
     * @return the collection with marked parcels
     */
    public static SimpleFeatureCollection markParcelWithAttribute(SimpleFeatureCollection parcels, String fieldName, String attribute) {
        SimpleFeatureBuilder builder = ParcelSchema.addMarkField(parcels.getSchema());
        DefaultFeatureCollection result = new DefaultFeatureCollection();
        try (SimpleFeatureIterator it = parcels.features()) {
            while (it.hasNext()) {
                SimpleFeature feat = it.next();
                Schemas.setFieldsToSFB(builder, feat);
                if (isAlreadyMarked(feat) != 0 && feat.getAttribute(fieldName).equals(attribute))
                    builder.set(markFieldName, 1);
                else
                    builder.set(markFieldName, 0);
                result.add(builder.buildFeature(Attribute.makeUniqueId()));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        signalIfNoParcelMarked(result, "markParcelWithAttribute");
        return result;
    }

    /**
     * Get the name of the field containing the parcel's mark
     *
     * @return the field name
     */
    public static String getMarkFieldName() {
        return markFieldName;
    }

    /**
     * Set the name of the field containing the parcel's mark
     *
     * @param markFieldName the field name
     */
    public static void setMarkFieldName(String markFieldName) {
        MarkParcelAttributeFromPosition.markFieldName = markFieldName;
    }

    /**
     * Mark parcels that have already been marked on another simple collection feature. They may not have the same attribute, so we rely on geometries
     *
     * @param parcels       Parcel collection to copy the marks on. Could have a markFieldName or not.
     * @param parcelsMarked Parcel collection that has a markFieldName field
     * @return {@link SimpleFeatureCollection} of the input parcels with marked parcels on the {@link #markFieldName} field.
     */
    public static SimpleFeatureCollection markAlreadyMarkedParcels(SimpleFeatureCollection parcels, SimpleFeatureCollection parcelsMarked) {
        if (!CollecMgmt.isCollecContainsAttribute(parcelsMarked, markFieldName)) {
            System.out.println("markAlreadyMarkedParcels: parcelMarked doesn't contain the markFieldName field");
            return parcels;
        }
        SimpleFeatureBuilder builder = ParcelSchema.addMarkField(parcels.getSchema());
        DefaultFeatureCollection result = new DefaultFeatureCollection();
        FilterFactory2 ff = CommonFactoryFinder.getFilterFactory2();
        PropertyName geomName = ff.property(parcels.getSchema().getGeometryDescriptor().getLocalName());
        try (SimpleFeatureIterator parcelIt = parcels.features()) {
            toMarkParcel:
            while (parcelIt.hasNext()) {
                SimpleFeature parcelToMark = parcelIt.next();
                Geometry geomParcelToMark = (Geometry) parcelToMark.getDefaultGeometry();
                // look for exact geometries
                SimpleFeatureCollection parcelsIntersectRef = parcelsMarked.subCollection(ff.intersects(geomName, ff.literal(geomParcelToMark)));
                try (SimpleFeatureIterator itParcelsMarked = parcelsIntersectRef.features()) {
                    while (itParcelsMarked.hasNext()) {
                        SimpleFeature parcelMarked = itParcelsMarked.next();
                        if ((int) parcelMarked.getAttribute(markFieldName) == 1 && ((Geometry) parcelMarked.getDefaultGeometry()).buffer(1).contains(geomParcelToMark)) {
                            for (AttributeDescriptor attr : parcelToMark.getFeatureType().getAttributeDescriptors())
                                builder.set(attr.getName(), parcelToMark.getAttribute(attr.getName()));
                            builder.set(markFieldName, 1);
                            result.add(builder.buildFeature(Attribute.makeUniqueId()));
                            continue toMarkParcel;
                        }
                    }
                } catch (Exception problem) {
                    problem.printStackTrace();
                }
                // if we haven't found correspondance
                for (AttributeDescriptor attr : parcelToMark.getFeatureType().getAttributeDescriptors())
                    builder.set(attr.getName(), parcelToMark.getAttribute(attr.getName()));
                builder.set(markFieldName, 0);
                result.add(builder.buildFeature(Attribute.makeUniqueId()));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        signalIfNoParcelMarked(result, "markAlreadyMarkedParcels");
        return result;
    }

    /**
     * Mark a parcel if it has been simulated. This is done using the <i>section</i> field name and the method
     * {@link fr.ign.artiscales.pm.fields.GeneralFields#isParcelHasSimulatedFields(SimpleFeature)}.
     *
     * @param parcels Input parcel {@link SimpleFeatureCollection}
     * @return {@link SimpleFeatureCollection} of the input parcels with marked parcels on the {@link #markFieldName} field.
     */
    public static SimpleFeatureCollection markSimulatedParcel(SimpleFeatureCollection parcels) {
        SimpleFeatureBuilder builder = ParcelSchema.addMarkField(parcels.getSchema());
        DefaultFeatureCollection result = new DefaultFeatureCollection();
        if (builder.getFeatureType().equals(parcels.getSchema())) {
            Arrays.stream(parcels.toArray(new SimpleFeature[0])).forEach(feat -> {
                if (isAlreadyMarked(feat) != 0 && GeneralFields.isParcelHasSimulatedFields(feat))
                    feat.setAttribute(markFieldName, 1);
                else
                    feat.setAttribute(markFieldName, 0);
                result.add(feat);
            });
            return result;
        }
        try (SimpleFeatureIterator it = parcels.features()) {
            while (it.hasNext()) {
                SimpleFeature feat = it.next();
                Schemas.setFieldsToSFB(builder, feat);
                if (isAlreadyMarked(feat) != 0 && GeneralFields.isParcelHasSimulatedFields(feat))
                    builder.set(markFieldName, 1);
                else
                    builder.set(markFieldName, 0);
                result.add(builder.buildFeature(Attribute.makeUniqueId()));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        signalIfNoParcelMarked(result, "markSimulatedParcel");
        return result;
    }

    /**
     * Mark a parcel if it has been simulated with a specific workflow.
     * This is done using the <i>section</i> field name and the method
     * {@link fr.ign.artiscales.pm.fields.GeneralFields#isParcelHasSimulatedFields(SimpleFeature)}.
     *
     * @param parcels      Input parcel {@link SimpleFeatureCollection}
     * @param workflowName can either be <ul>
     *                     <li>densification</li>
     *                     <li>consolidation</li>
     *                     <li>zone</li>
     *                     </ul>
     * @return {@link SimpleFeatureCollection} of the input parcels with marked parcels on the {@link #markFieldName} field.
     */
    public static SimpleFeatureCollection markWorkflowSimulatedParcel(SimpleFeatureCollection parcels, String workflowName) {
        SimpleFeatureBuilder builder = ParcelSchema.addMarkField(parcels.getSchema());
        DefaultFeatureCollection result = new DefaultFeatureCollection();
        if (builder.getFeatureType().equals(parcels.getSchema())) {
            Arrays.stream(parcels.toArray(new SimpleFeature[0])).forEach(feat -> {
                if (markWorkflowSimulatedParcelCondition(feat, workflowName))
                    feat.setAttribute(markFieldName, 1);
                else
                    feat.setAttribute(markFieldName, 0);
                result.add(feat);
            });
            return result;
        }
        try (SimpleFeatureIterator it = parcels.features()) {
            while (it.hasNext()) {
                SimpleFeature feat = it.next();
                Schemas.setFieldsToSFB(builder, feat);
                if (markWorkflowSimulatedParcelCondition(feat, workflowName))
                    builder.set(markFieldName, 1);
                else
                    builder.set(markFieldName, 0);
                result.add(builder.buildFeature(Attribute.makeUniqueId()));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        signalIfNoParcelMarked(result, "markSimulatedParcel");
        return result;
    }

    private static boolean markWorkflowSimulatedParcelCondition(SimpleFeature feat, String workflowName) {
        switch (workflowName) {
            case "densification":
                if ((new Densification()).isNewSection(feat))
                    return true;
            case "zone":
                if ((new ZoneDivision()).isNewSection(feat))
                    return true;
            case "consolidation":
                if ((new ConsolidationDivision()).isNewSection(feat))
                    return true;
        }
        throw new IllegalArgumentException("Workflow unknown");
    }

    /**
     * Mark every {@link SimpleFeature} form a {@link SimpleFeatureCollection} on a new {@link #markFieldName} field with a 1 (true). Untested if the collection already contains
     * the {@link #markFieldName} field.
     *
     * @param parcels input {@link SimpleFeatureCollection}
     * @return input {@link SimpleFeatureCollection} with a new {@link #markFieldName} field with only 1 (true) in it.
     */
    public static SimpleFeatureCollection markAllParcel(SimpleFeatureCollection parcels) {
        DefaultFeatureCollection result = new DefaultFeatureCollection();
        SimpleFeatureBuilder builder = ParcelSchema.addMarkField(parcels.getSchema());
        try (SimpleFeatureIterator it = parcels.features()) {
            while (it.hasNext()) {
                SimpleFeature feat = it.next();
                for (AttributeDescriptor attr : feat.getFeatureType().getAttributeDescriptors())
                    builder.set(attr.getName(), feat.getAttribute(attr.getName()));
                builder.set(markFieldName, 1);
                result.add(builder.buildFeature(Attribute.makeUniqueId()));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }


    /**
     * look if a parcel has the {@link #markFieldName} field and if it is positive or negative. Also look if a parcel has already been simulated. The default method for the kind of
     * nomenclature is used. If already simulated and not in {@link #postMark} mode, we won't re-simulate it.
     *
     * @param feat input SimpleFeature
     * @return <ul>
     * <li>If no <i>mark</i> field or the field is unset, return <b>-1</b></li>
     * <li>If <i>mark</i> field is set to 0, return <b>0</b></li>
     * <li>If <i>mark</i> field is set to 1, return <b>1</b></li>
     * </ul>
     */
    public static int isAlreadyMarked(SimpleFeature feat) {
        if (CollecMgmt.isSimpleFeatureContainsAttribute(feat, markFieldName) && feat.getAttribute(markFieldName) != null) {
            if ((int) feat.getAttribute(markFieldName) == 0 || (GeneralFields.isParcelHasSimulatedFields(feat) && !postMark))
                return 0;
            else if ((int) feat.getAttribute(markFieldName) == 1)
                return 1;
            else
                return -1;
        } else
            return -1;
    }

    /**
     * Write on the console if a set of parcels haven't been marked at all. Making that function a lil earlier could have saved me a lot of time
     *
     * @param sfcIn      Input {@link SimpleFeatureCollection}
     * @param methodName Name of the method to wave this name if no parcels have been marked
     */
    public static void signalIfNoParcelMarked(SimpleFeatureCollection sfcIn, String methodName) {
        if (isNoParcelMarked(sfcIn))
            System.out.println(" ------ No parcels marked for " + methodName + " method ------");
    }

    /**
     * return true if the {@link SimpleFeatureCollection} has no parcels where the {@link MarkParcelAttributeFromPosition#markFieldName} value equals 1.
     *
     * @param sfcIn input collection
     * @return true is no parcels are marked, false otherwise
     */
    public static boolean isNoParcelMarked(SimpleFeatureCollection sfcIn) {
        if (sfcIn == null || sfcIn.isEmpty() || !CollecMgmt.isCollecContainsAttribute(sfcIn, markFieldName))
            return true;
        try {
            return countMarkedParcels(sfcIn) == 0;
        } catch (NullPointerException np) {
            np.printStackTrace();
            System.out.println("Maybe you have null values on your attribute table (not premise). Returned false");
            return false;
        }
    }

    /**
     * Return a {@link SimpleFeatureCollection} containing only the marked parcels on their <i>markFieldName</i> field.
     *
     * @param in input {@link SimpleFeatureCollection} with marking attribute
     * @return {@link SimpleFeatureCollection} with only marked parcels and same schema as input
     */
    public static SimpleFeatureCollection getOnlyMarkedParcels(SimpleFeatureCollection in) {
        return getMarkedOrNotMarkedParcels(in, true);
    }

    /**
     * Return a {@link SimpleFeatureCollection} containing only the not marked parcels on their <i>markFieldName</i> field.
     *
     * @param in input {@link SimpleFeatureCollection} with marking attribute
     * @return {@link SimpleFeatureCollection} with only marked parcels and same schema as input
     */
    public static SimpleFeatureCollection getNotMarkedParcels(SimpleFeatureCollection in) {
        return getMarkedOrNotMarkedParcels(in, false);
    }

    private static SimpleFeatureCollection getMarkedOrNotMarkedParcels(SimpleFeatureCollection in, boolean marked) {
        DefaultFeatureCollection result = new DefaultFeatureCollection();
        if (!CollecMgmt.isCollecContainsAttribute(in, getMarkFieldName())) {
            System.out.println("getOnlyMarkedParcels : no " + getMarkFieldName() + " field");
            return null;
        }
        Arrays.stream(in.toArray(new SimpleFeature[0])).forEach(feat -> {
            if (marked == ((int) feat.getAttribute(markFieldName) == 1))
                result.add(feat);
        });
        return result;
    }

    /**
     * Affect a null value to every mark field of a parcel collection.
     *
     * @param parcels input parcel collection to remove mark.
     * @return collection with null values in the marking field.
     */
    public static SimpleFeatureCollection resetMarkingField(SimpleFeatureCollection parcels) {
        DefaultFeatureCollection result = new DefaultFeatureCollection();
        if (!CollecMgmt.isCollecContainsAttribute(parcels, markFieldName)) {
            SimpleFeatureBuilder builder = ParcelSchema.addMarkField(parcels.getSchema());
            try (SimpleFeatureIterator featIt = parcels.features()) {
                while (featIt.hasNext()) {
                    SimpleFeature parcel = featIt.next();
                    for (AttributeDescriptor attr : parcels.getSchema().getAttributeDescriptors())
                        builder.set(attr.getLocalName(), parcel.getAttribute(attr.getLocalName()));
                    builder.set(markFieldName, null);
                    result.add(builder.buildFeature(Attribute.makeUniqueId()));
                }
            }
        } else {
            Arrays.stream(parcels.toArray(new SimpleFeature[0])).forEach(feat -> {
                feat.setAttribute(markFieldName, null);
                result.add(feat);
            });
        }
        return result;
    }

    /**
     * Merge SimpleFeatures that are marked and touches each other and keep the attribute of the largest feature
     *
     * @param parcelCollection input parcel collection to mark
     * @return The collection with the same schema and its touching parcels merged
     */
    public static SimpleFeatureCollection unionTouchingMarkedGeometries(SimpleFeatureCollection parcelCollection) {
        DefaultFeatureCollection result = new DefaultFeatureCollection();
        SimpleFeatureBuilder sfb = new SimpleFeatureBuilder(parcelCollection.getSchema());
        List<Geometry> lGmerged = Geom.unionTouchingGeometries(Arrays.stream(parcelCollection.toArray(new SimpleFeature[0])).filter(sf -> ((Integer) sf.getAttribute(getMarkFieldName())) == 1).map(sf -> (Geometry) sf.getDefaultGeometry()).collect(Collectors.toList()));
        FilterFactory2 ff = CommonFactoryFinder.getFilterFactory2();
        String geomName = parcelCollection.getSchema().getGeometryDescriptor().getLocalName();
        for (Geometry g : lGmerged) {
            SimpleFeature s = CollecTransform.getBiggestSF(parcelCollection.subCollection(ff.within(ff.property(geomName), ff.literal(g.buffer(1)))));
            for (AttributeDescriptor attr : parcelCollection.getSchema().getAttributeDescriptors()) {
                if (attr.getLocalName().equals(geomName))
                    continue;
                sfb.set(attr.getLocalName(), s.getAttribute(attr.getLocalName()));
            }
            sfb.add(s);
            sfb.set(geomName, g);
            result.add(sfb.buildFeature(Attribute.makeUniqueId()));
        }
        Arrays.stream(parcelCollection.toArray(new SimpleFeature[0])).forEach(feat -> {
            if ((int) feat.getAttribute(getMarkFieldName()) != 1) {
                result.add(feat);
            }
        });
        return result;
    }

    /**
     * Marking parcels is made before (false) or after (true) the simulation process. It won't allow or will the marking of the already simulated parcels
     *
     * @return value
     */
    public static boolean isPostMark() {
        return postMark;
    }

    /**
     * Specify that the marking is made before (false) or after (true) the simulation process. It won't allow or will the marking of the already simulated parcels
     *
     * @param postMark new value
     */
    public static void setPostMark(boolean postMark) {
        MarkParcelAttributeFromPosition.postMark = postMark;
    }

    /**
     * Count how many parcels of a set are marked.
     *
     * @param parcelCollection input parcel collection to count
     * @return number of marked parcels
     */
    public static long countMarkedParcels(SimpleFeatureCollection parcelCollection) {
        return Arrays.stream(parcelCollection.toArray(new SimpleFeature[0])).filter(sf -> sf.getAttribute(getMarkFieldName()) != null && ((Integer) sf.getAttribute(getMarkFieldName())) == 1).count();
    }

    /**
     * Mark a parcel's feat with value 1, either if they contain a <i>markField</i> attribute or not.
     *
     * @param parcel input parcel to mark.
     * @return the parcel marked (possibly with an extra attribute)
     */
    public static SimpleFeature markParcel(SimpleFeature parcel) {
        return markParcel(parcel, 1);
    }

    /**
     * Mark a parcel's feat, either if they contain a <i>markField</i> attribute or not.
     *
     * @param parcel input parcel to mark.
     * @param value  whether 0 (won't be simulated) or 1 (will be simulated)
     * @return the parcel marked (possibly with an extra attribute)
     */
    public static SimpleFeature markParcel(SimpleFeature parcel, int value) {
        if (!CollecMgmt.isSimpleFeatureContainsAttribute(parcel, markFieldName)) {
            SimpleFeatureBuilder parcelSchema = ParcelSchema.addMarkField(parcel.getFeatureType());
            for (AttributeDescriptor attr : parcelSchema.getFeatureType().getAttributeDescriptors())
                parcelSchema.set(attr.getLocalName(), parcel.getAttribute(attr.getLocalName()));
            parcelSchema.set(markFieldName, value);
            return parcelSchema.buildFeature(Attribute.makeUniqueId());
        } else {
            parcel.setAttribute(markFieldName, value);
            return parcel;
        }
    }
}
