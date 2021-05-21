package fr.ign.artiscales.pm.parcelFunction;

import fr.ign.artiscales.pm.fields.GeneralFields;
import fr.ign.artiscales.pm.fields.french.FrenchZoningSchemas;
import fr.ign.artiscales.tools.geoToolsFunctions.Attribute;
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
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.AttributeDescriptor;
import org.opengis.filter.FilterFactory2;
import org.opengis.filter.expression.PropertyName;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Static methods to mark parcel collections relatively to many other geographic elements, parcel attributes or geometries or other sets of parcels. Doesn't change the input
 * {@link SimpleFeatureCollection} structure but add a marking field. This field's name is <i>SPLIT</i> by default and can be changed.
 *
 * @author Maxime Colomb
 */
public class MarkParcelAttributeFromPosition {

//	public static void main(String[] args) throws Exception {
//		File parcelsMarkedF = new File("src/main/resources/ParcelComparison/parcel2018.gpkg");
//		File roadFile = new File("src/main/resources/ParcelComparison/road.gpkg");
//		File blockFileF = CityGeneration.createUrbanBlock(parcelsMarkedF, new File("/tmp/"));
//		long startTime = System.currentTimeMillis();
//		DataStore sdsParcel = Geopackages.getDataStore(parcelsMarkedF);
//		SimpleFeatureCollection parcels = sdsParcel.getFeatureSource(sdsParcel.getTypeNames()[0]).getFeatures();
//		DataStore sdsIslet = Geopackages.getDataStore(blockFileF);
//		SimpleFeatureCollection block = sdsIslet.getFeatureSource(sdsIslet.getTypeNames()[0]).getFeatures();
//		markParcelsConnectedToRoad(parcels, block, roadFile);
//		sdsIslet.dispose();
//		sdsParcel.dispose();
//		sdsParcel.dispose();
//		long stopTime = System.currentTimeMillis();
//		System.out.println(stopTime - startTime);
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
//	 }


    private static String markFieldName = "SPLIT";
    /**
     * The buildings that have an area under this value will be considered as light buildings and won't be considered when we are looking if a parcel is built or not
     */
    static double uncountedBuildingArea = 20;
    /**
     * Specify that the marking is made before (false) or after (true) the simulation process. It won't allow or will the marking of the already simulated parcels
     */
    private static boolean postMark = false;

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
     * @param roadFile      Geopackage containing the road segments
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

    public static SimpleFeatureCollection markParcelsConnectedToRoad(SimpleFeatureCollection parcels, SimpleFeatureCollection block, SimpleFeatureCollection road, Geometry exclusionZone) {
        SimpleFeatureCollection roads = CollecTransform.selectIntersection(road, parcels);
        final SimpleFeatureType featureSchema = ParcelSchema.getSFBMinParcelSplit().getFeatureType();
        DefaultFeatureCollection result = new DefaultFeatureCollection();
        // if features have the schema that the one intended to set, we bypass
        if (featureSchema.equals(parcels.getSchema())) {
            Arrays.stream(parcels.toArray(new SimpleFeature[0])).forEach(feat -> {
                try {
                    Geometry geomFeat = (Geometry) feat.getDefaultGeometry();
                    if (isAlreadyMarked(feat) != 0
                            && ParcelState.isParcelHasRoadAccess(Polygons.getPolygon(geomFeat), CollecTransform.selectIntersection(roads, geomFeat),
                            CollecTransform.fromPolygonSFCtoRingMultiLines(CollecTransform.selectIntersection(block, geomFeat)), exclusionZone))
                        feat.setAttribute(markFieldName, 1);
                    else
                        feat.setAttribute(markFieldName, 0);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                result.add(feat);
            });
            return result;
        }
        try (SimpleFeatureIterator it = parcels.features()) {
            while (it.hasNext()) {
                SimpleFeature feat = it.next();
                Geometry geomFeat = (Geometry) feat.getDefaultGeometry();
                SimpleFeatureBuilder featureBuilder = ParcelSchema.setSFBMinParcelSplitWithFeat(feat, ParcelSchema.getSFBMinParcelSplit(), featureSchema, 0);
                if (isAlreadyMarked(feat) != 0
                        && ParcelState.isParcelHasRoadAccess(Polygons.getPolygon(geomFeat), CollecTransform.selectIntersection(roads, geomFeat),
                        CollecTransform.fromPolygonSFCtoRingMultiLines(CollecTransform.selectIntersection(block, geomFeat)), exclusionZone))
                    featureBuilder.set(markFieldName, 1);
                result.add(featureBuilder.buildFeature(Attribute.makeUniqueId()));
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
        final SimpleFeatureType featureSchema = ParcelSchema.getSFBMinParcelSplit().getFeatureType();
        DefaultFeatureCollection result = new DefaultFeatureCollection();
        // if features have the schema that the one intended to set, we bypass
        if (featureSchema.equals(parcels.getSchema())) {
            Arrays.stream(parcels.toArray(new SimpleFeature[0])).forEach(feat -> {
                try {
                    if (isAlreadyMarked(feat) != 0 && ((Geometry) feat.getDefaultGeometry()).getArea() <= size)
                        feat.setAttribute(markFieldName, 1);
                    else
                        feat.setAttribute(markFieldName, 0);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                result.add(feat);
            });
            return result;
        }
        try (SimpleFeatureIterator it = parcels.features()) {
            while (it.hasNext()) {
                SimpleFeature feat = it.next();
                SimpleFeatureBuilder featureBuilder = ParcelSchema.setSFBMinParcelSplitWithFeat(feat, ParcelSchema.getSFBMinParcelSplit(), featureSchema, 0);
                if (isAlreadyMarked(feat) != 0 && ((Geometry) feat.getDefaultGeometry()).getArea() <= size)
                    featureBuilder.set(markFieldName, 1);
                result.add(featureBuilder.buildFeature(Attribute.makeUniqueId()));
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
        final SimpleFeatureType featureSchema = ParcelSchema.getSFBMinParcelSplit().getFeatureType();
        DefaultFeatureCollection result = new DefaultFeatureCollection();
        // if features have the schema that the one intended to set, we bypass
        if (featureSchema.equals(parcels.getSchema())) {
            Arrays.stream(parcels.toArray(new SimpleFeature[0])).forEach(feat -> {
                try {
                    if (isAlreadyMarked(feat) != 0 && ((Geometry) feat.getDefaultGeometry()).getArea() > size)
                        feat.setAttribute(markFieldName, 1);
                    else
                        feat.setAttribute(markFieldName, 0);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                result.add(feat);
            });
            return result;
        }
        try (SimpleFeatureIterator it = parcels.features()) {
            while (it.hasNext()) {
                SimpleFeature feat = it.next();
                SimpleFeatureBuilder featureBuilder = ParcelSchema.setSFBMinParcelSplitWithFeat(feat, ParcelSchema.getSFBMinParcelSplit(), featureSchema, 0);
                if (isAlreadyMarked(feat) != 0 && ((Geometry) feat.getDefaultGeometry()).getArea() > size)
                    featureBuilder.set(markFieldName, 1);
                result.add(featureBuilder.buildFeature(Attribute.makeUniqueId()));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        signalIfNoParcelMarked(result, "markParcelsSup");
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
     * Mark a given number of parcel for the simulation. The selection is random but parcels must be bigger than a certain area threshold.
     *
     * @param parcels        Input parcel {@link SimpleFeatureCollection}
     * @param minSize        minimal size of parcels to be selected
     * @param nbParcelToMark number of parcel wanted
     * @return {@link SimpleFeatureCollection} of the input parcels with random marked parcels on the {@link #markFieldName} field.
     * @throws IOException reading zoningFile
     */
    public static SimpleFeatureCollection markRandomParcels(SimpleFeatureCollection parcels, int minSize, int nbParcelToMark) throws IOException {
        return markRandomParcels(parcels, null, null, minSize, nbParcelToMark);
    }

    /**
     * Mark a given number of parcel for the simulation. The selection is random but parcels must be bigger than a certain area threshold and must be contained is a given zoning
     * type.
     *
     * @param parcels        Input parcel {@link SimpleFeatureCollection}
     * @param minSize        Minimal size of parcels to be selected
     * @param genericZone    Type of the zoning plan to take into consideration
     * @param zoningFile     Geopackage containing the zoning plan
     * @param nbParcelToMark Number of parcel wanted
     * @return {@link SimpleFeatureCollection} of the input parcels with random marked parcels on the {@link #markFieldName} field.
     * @throws IOException reading zoningFile
     */
    public static SimpleFeatureCollection markRandomParcels(SimpleFeatureCollection parcels, String genericZone, File zoningFile, double minSize,
                                                            int nbParcelToMark) throws IOException {
        if (zoningFile != null && genericZone != null)
            parcels = markParcelIntersectGenericZoningType(parcels, genericZone, zoningFile);
        List<SimpleFeature> list = Arrays.stream(parcels.toArray(new SimpleFeature[0]))
                .filter(feat -> ((Geometry) feat.getDefaultGeometry()).getArea() > minSize).collect(Collectors.toList());
        Collections.shuffle(list);
        DefaultFeatureCollection result = new DefaultFeatureCollection();
        while (nbParcelToMark > 0) {
            if (!list.isEmpty())
                result.add(list.remove(0));
            nbParcelToMark--;
        }
        signalIfNoParcelMarked(result, "markRandomParcels");
        return parcels;
    }

    /**
     * Mark the built parcels. Subtract a buffer of 1 meters on the buildings to avoid blurry parcel and builing definition
     *
     * @param parcels      Input parcel {@link SimpleFeatureCollection}
     * @param buildingFile Geopackage containing building features
     * @return {@link SimpleFeatureCollection} of the input parcels with marked parcels on the {@link #markFieldName} field.
     * @throws IOException
     */
    public static SimpleFeatureCollection markUnBuiltParcel(SimpleFeatureCollection parcels, File buildingFile) throws IOException {
        DataStore ds = CollecMgmt.getDataStore(buildingFile);
        SimpleFeatureCollection result = markUnBuiltParcel(parcels, ds.getFeatureSource(ds.getTypeNames()[0]).getFeatures());
        ds.dispose();
        return result;
    }

    public static SimpleFeatureCollection markUnBuiltParcel(SimpleFeatureCollection parcels, SimpleFeatureCollection buildingSFC) {
        SimpleFeatureCollection buildings = CollecTransform.selectIntersection(buildingSFC, parcels);
        final SimpleFeatureType featureSchema = ParcelSchema.getSFBMinParcelSplit().getFeatureType();
        DefaultFeatureCollection result = new DefaultFeatureCollection();
        // if features have the schema that the one intended to set, we bypass
        if (featureSchema.equals(parcels.getSchema())) {
            Arrays.stream(parcels.toArray(new SimpleFeature[0])).forEach(feat -> {
                try {
                    if (isAlreadyMarked(feat) != 0
                            && !ParcelState.isAlreadyBuilt(CollecTransform.selectIntersection(buildings, (Geometry) feat.getDefaultGeometry()), feat, -1.0,
                            uncountedBuildingArea))
                        feat.setAttribute(markFieldName, 1);
                    else
                        feat.setAttribute(markFieldName, 0);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                result.add(feat);
            });
            return result;
        }
        try (SimpleFeatureIterator it = parcels.features()) {
            while (it.hasNext()) {
                SimpleFeature feat = it.next();
                SimpleFeatureBuilder featureBuilder = ParcelSchema.setSFBMinParcelSplitWithFeat(feat, ParcelSchema.getSFBMinParcelSplit(), featureSchema, 0);
                if (isAlreadyMarked(feat) != 0
                        && !ParcelState.isAlreadyBuilt(CollecTransform.selectIntersection(buildings, (Geometry) feat.getDefaultGeometry()), feat, -1.0,
                        uncountedBuildingArea))
                    featureBuilder.set(markFieldName, 1);
                result.add(featureBuilder.buildFeature(Attribute.makeUniqueId()));
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
     * @param buildingFile Geopackage representing the buildings
     * @return {@link SimpleFeatureCollection} of the input parcels with marked parcels on the {@link #markFieldName} field.
     * @throws IOException
     */
    public static SimpleFeatureCollection markBuiltParcel(SimpleFeatureCollection parcels, File buildingFile) throws IOException {
        DataStore buildingDS = CollecMgmt.getDataStore(buildingFile);
        SimpleFeatureCollection result = markBuiltParcel(parcels, buildingDS.getFeatureSource(buildingDS.getTypeNames()[0]).getFeatures());
        buildingDS.dispose();
        return result;
    }

    public static SimpleFeatureCollection markBuiltParcel(SimpleFeatureCollection parcels, SimpleFeatureCollection building) {
        SimpleFeatureCollection buildings = CollecTransform.selectIntersection(DataUtilities.collection(building), parcels);
        final SimpleFeatureType featureSchema = ParcelSchema.getSFBMinParcelSplit().getFeatureType();
        DefaultFeatureCollection result = new DefaultFeatureCollection();

        // if features have the schema that the one intended to set, we bypass
        if (featureSchema.equals(parcels.getSchema())) {
            Arrays.stream(parcels.toArray(new SimpleFeature[0])).forEach(feat -> {
                try {
                    if (isAlreadyMarked(feat) != 0 && ParcelState.isAlreadyBuilt(
                            CollecTransform.selectIntersection(buildings, (Geometry) feat.getDefaultGeometry()), feat, -1.0, uncountedBuildingArea))
                        feat.setAttribute(markFieldName, 1);
                    else
                        feat.setAttribute(markFieldName, 0);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                result.add(feat);
            });
            return result;
        }
        try (SimpleFeatureIterator it = parcels.features()) {
            while (it.hasNext()) {
                SimpleFeature feat = it.next();
                SimpleFeatureBuilder featureBuilder = ParcelSchema.setSFBMinParcelSplitWithFeat(feat, ParcelSchema.getSFBMinParcelSplit(), featureSchema, 0);
                if (isAlreadyMarked(feat) != 0 && ParcelState.isAlreadyBuilt(
                        CollecTransform.selectIntersection(buildings, (Geometry) feat.getDefaultGeometry()), feat, -1.0, uncountedBuildingArea))
                    featureBuilder.set(markFieldName, 1);
                result.add(featureBuilder.buildFeature(Attribute.makeUniqueId()));
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
        final SimpleFeatureType featureSchema = ParcelSchema.getSFBMinParcelSplit().getFeatureType();
        // if features have the schema that the one intended to set, we bypass
        if (featureSchema.equals(parcels.getSchema())) {
            Arrays.stream(parcels.toArray(new SimpleFeature[0])).forEach(feat -> {
                try {
                    if (isAlreadyMarked(feat) != 0 && !Geom
                            .unionPrecisionReduce(
                                    geoms.stream().filter(g -> g.intersects((Geometry) feat.getDefaultGeometry())).collect(Collectors.toList()), 100)
                            .isEmpty())
                        feat.setAttribute(markFieldName, 1);
                    else
                        feat.setAttribute(markFieldName, 0);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                result.add(feat);
            });
            signalIfNoParcelMarked(result, "markParcelIntersectPolygonIntersection");
            return result;
        }
        try (SimpleFeatureIterator it = parcels.features()) {
            while (it.hasNext()) {
                SimpleFeature feat = it.next();
                SimpleFeatureBuilder featureBuilder = ParcelSchema.setSFBMinParcelSplitWithFeat(feat, ParcelSchema.getSFBMinParcelSplit(), featureSchema, 0);
                if (isAlreadyMarked(feat) != 0 && !Geom
                        .unionPrecisionReduce(
                                geoms.stream().filter(g -> g.intersects((Geometry) feat.getDefaultGeometry())).collect(Collectors.toList()), 100)
                        .isEmpty())
                    featureBuilder.set(markFieldName, 1);
                result.add(featureBuilder.buildFeature(Attribute.makeUniqueId()));
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
        final SimpleFeatureType featureSchema = ParcelSchema.getSFBMinParcelSplit().getFeatureType();
        // if features have the schema that the one intended to set, we bypass
        if (featureSchema.equals(parcels.getSchema())) {
            Arrays.stream(parcels.toArray(new SimpleFeature[0])).forEach(feat -> {
                try {
                    if (isAlreadyMarked(feat) != 0 && OpOnCollec.isFeatIntersectsSFC(feat, polyCollec))
                        feat.setAttribute(markFieldName, 1);
                    else
                        feat.setAttribute(markFieldName, 0);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                result.add(feat);
            });
            ds.dispose();
            return result;
        }
        try (SimpleFeatureIterator it = parcels.features()) {
            while (it.hasNext()) {
                SimpleFeature feat = it.next();
                SimpleFeatureBuilder featureBuilder = ParcelSchema.setSFBMinParcelSplitWithFeat(feat, ParcelSchema.getSFBMinParcelSplit(), featureSchema, 0);
                if (isAlreadyMarked(feat) != 0 && OpOnCollec.isFeatIntersectsSFC(feat, polyCollec))
                    featureBuilder.set(markFieldName, 1);
                result.add(featureBuilder.buildFeature(Attribute.makeUniqueId()));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        ds.dispose();
        signalIfNoParcelMarked(result, "markParcelIntersectPolygonIntersection");
        return result;
    }

    /**
     * Mark parcels that intersects a certain type of <i>generic zone</i>.
     *
     * @param sfcIn               Input {@link SimpleFeatureCollection}
     * @param attributeFieldName  Name of the attribute field
     * @param attributeFieldValue Value of the attribute field to mark
     * @return {@link SimpleFeatureCollection} of the input parcels with marked parcels on the {@link #markFieldName} field
     */
    public static SimpleFeatureCollection markSFCWithAttributeField(SimpleFeatureCollection sfcIn, String attributeFieldName, String attributeFieldValue) {
        DefaultFeatureCollection result = new DefaultFeatureCollection();
        // if features have the schema that the one intended to set, we bypass
        if (!CollecMgmt.isCollecContainsAttribute(sfcIn, attributeFieldName)) {
            signalIfNoParcelMarked(sfcIn, "markParcelWithAttributeField");
            return sfcIn;
        }
        SimpleFeatureBuilder featureBuilder = ParcelSchema.addSplitField(sfcIn.getSchema());
        try (SimpleFeatureIterator it = sfcIn.features()) {
            while (it.hasNext()) {
                SimpleFeature feat = it.next();
                if (isAlreadyMarked(feat) != 0 && feat.getAttribute(attributeFieldName).equals(attributeFieldValue))
                    featureBuilder.set(markFieldName, 1);
                result.add(featureBuilder.buildFeature(Attribute.makeUniqueId()));
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
        final SimpleFeatureType featureSchema = ParcelSchema.getSFBMinParcelSplit().getFeatureType();
        DefaultFeatureCollection result = new DefaultFeatureCollection();
        DataStore dsZone = CollecMgmt.getDataStore(zoningFile);
        SimpleFeatureCollection zoningSFC = CollecTransform.selectIntersection(dsZone.getFeatureSource(dsZone.getTypeNames()[0]).getFeatures(), parcels);
        List<String> genericZoneUsualNames = GeneralFields.getGenericZoneUsualNames(genericZone);
        // if features have the schema that the one intended to set, we bypass
        if (featureSchema.equals(parcels.getSchema())) {
            Arrays.stream(parcels.toArray(new SimpleFeature[0])).forEach(feat -> {
                try {
                    if (isAlreadyMarked(feat) != 0 && Objects.requireNonNull(genericZoneUsualNames).contains(CollecTransform
                            .getIntersectingFieldFromSFC((Geometry) feat.getDefaultGeometry(), zoningSFC, GeneralFields.getZoneGenericNameField())))
                        feat.setAttribute(markFieldName, 1);
                    else
                        feat.setAttribute(markFieldName, 0);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                result.add(feat);
            });
            dsZone.dispose();
            return result;
        }
        try (SimpleFeatureIterator it = parcels.features()) {
            while (it.hasNext()) {
                SimpleFeature feat = it.next();
                SimpleFeatureBuilder featureBuilder = ParcelSchema.setSFBMinParcelSplitWithFeat(feat, ParcelSchema.getSFBMinParcelSplit(), featureSchema, 0);
                if (isAlreadyMarked(feat) != 0 && genericZoneUsualNames.contains(
                        CollecTransform.getIntersectingFieldFromSFC((Geometry) feat.getDefaultGeometry(), zoningSFC, GeneralFields.getZoneGenericNameField())))
                    featureBuilder.set(markFieldName, 1);
                result.add(featureBuilder.buildFeature(Attribute.makeUniqueId()));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        signalIfNoParcelMarked(result, "markParcelIntersectGenericZoningType");
        dsZone.dispose();
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
     * @throws IOException
     */
    public static SimpleFeatureCollection markParcelIntersectZoningWithoutPreciseZonings(SimpleFeatureCollection parcels, String genericZone,
                                                                                         List<String> preciseZone, File zoningFile) throws IOException {
        final SimpleFeatureType featureSchema = ParcelSchema.getSFBMinParcelSplit().getFeatureType();
        DefaultFeatureCollection result = new DefaultFeatureCollection();
        List<String> genericZoneUsualNames = GeneralFields.getGenericZoneUsualNames(genericZone);
        DataStore dsZone = CollecMgmt.getDataStore(zoningFile);
        SimpleFeatureCollection zoningSFC = CollecTransform.selectIntersection(dsZone.getFeatureSource(dsZone.getTypeNames()[0]).getFeatures(), parcels);
        try (SimpleFeatureIterator it = parcels.features()) {
            while (it.hasNext()) {
                SimpleFeature feat = it.next();
                SimpleFeatureBuilder featureBuilder = ParcelSchema.setSFBMinParcelSplitWithFeat(feat, ParcelSchema.getSFBMinParcelSplit(), featureSchema, 0);
                if (isAlreadyMarked(feat) != 0
                        && genericZoneUsualNames.contains(CollecTransform.getIntersectingFieldFromSFC((Geometry) feat.getDefaultGeometry(), zoningSFC,
                        GeneralFields.getZoneGenericNameField()))
                        && !preciseZone.contains(CollecTransform.getIntersectingFieldFromSFC((Geometry) feat.getDefaultGeometry(), zoningSFC,
                        GeneralFields.getZonePreciseNameField())))
                    featureBuilder.set(markFieldName, 1);
                result.add(featureBuilder.buildFeature(Attribute.makeUniqueId()));
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
        final SimpleFeatureType featureSchema = ParcelSchema.getSFBMinParcelSplit().getFeatureType();
        // Get the zoning usual names
        List<String> genericZoneUsualNames = GeneralFields.getGenericZoneUsualNames(genericZone);
        DefaultFeatureCollection result = new DefaultFeatureCollection();
        DataStore dsZone = CollecMgmt.getDataStore(zoningFile);
        SimpleFeatureCollection zoningSFC = CollecTransform.selectIntersection(dsZone.getFeatureSource(dsZone.getTypeNames()[0]).getFeatures(), parcels);
        // if features have the schema that the one intended to set, we bypass
        if (featureSchema.equals(parcels.getSchema())) {
            Arrays.stream(parcels.toArray(new SimpleFeature[0])).forEach(feat -> {
                try {
                    if (isAlreadyMarked(feat) != 0 && (genericZone == null || genericZone.equals("") ||
                            genericZoneUsualNames.contains(CollecTransform.getIntersectingFieldFromSFC((Geometry) feat.getDefaultGeometry(), zoningSFC, GeneralFields.getZoneGenericNameField())))
                            && preciseZone.equals(CollecTransform.getIntersectingFieldFromSFC((Geometry) feat.getDefaultGeometry(), zoningSFC, GeneralFields.getZonePreciseNameField())))
                        feat.setAttribute(markFieldName, 1);
                    else
                        feat.setAttribute(markFieldName, 0);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                result.add(feat);
            });
            dsZone.dispose();
            return result;
        }
        try (SimpleFeatureIterator it = parcels.features()) {
            while (it.hasNext()) {
                SimpleFeature feat = it.next();
                SimpleFeatureBuilder featureBuilder = ParcelSchema.setSFBMinParcelSplitWithFeat(feat, ParcelSchema.getSFBMinParcelSplit(), featureSchema, 0);
                if (isAlreadyMarked(feat) != 0 && (genericZone == null || genericZone.equals("") ||
                        genericZoneUsualNames.contains(CollecTransform.getIntersectingFieldFromSFC((Geometry) feat.getDefaultGeometry(), zoningSFC, GeneralFields.getZoneGenericNameField())))
                        && preciseZone.equalsIgnoreCase(CollecTransform.getIntersectingFieldFromSFC((Geometry) feat.getDefaultGeometry(), zoningSFC, GeneralFields.getZonePreciseNameField())))
                    featureBuilder.set(markFieldName, 1);
                result.add(featureBuilder.buildFeature(Attribute.makeUniqueId()));
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
     * @param parcels    Input parcel {@link SimpleFeatureCollection}
     * @param zoning A collection containing the zoning plan
     * @return {@link SimpleFeatureCollection} of the input parcels with marked parcels on the {@link #markFieldName} field.
     */
    public static SimpleFeatureCollection markParcelIntersectFrenchConstructibleZoningType(SimpleFeatureCollection parcels, SimpleFeatureCollection zoning){
        final SimpleFeatureType featureSchema = ParcelSchema.getSFBMinParcelSplit().getFeatureType();
        DefaultFeatureCollection result = new DefaultFeatureCollection();
        // if features have the schema that the one intended to set, we bypass
        if (featureSchema.equals(parcels.getSchema())) {
            Arrays.stream(parcels.toArray(new SimpleFeature[0])).forEach(parcel -> {
                if (isAlreadyMarked(parcel) != 0 && FrenchZoningSchemas.isUrbanZoneUsuallyAdmitResidentialConstruction(
                        CollecTransform.getIntersectingSimpleFeatureFromSFC((Geometry) parcel.getDefaultGeometry(), zoning)))
                    parcel.setAttribute(markFieldName, 1);
                else
                    parcel.setAttribute(markFieldName, 0);
                result.add(parcel);
            });
            signalIfNoParcelMarked(result, "markParcelIntersectFrenchConstructibleZoningType");
            return result;
        }
        try (SimpleFeatureIterator it = parcels.features()) {
            while (it.hasNext()) {
                SimpleFeature feat = it.next();
                SimpleFeatureBuilder featureBuilder = ParcelSchema.setSFBMinParcelSplitWithFeat(feat, ParcelSchema.getSFBMinParcelSplit(), featureSchema, 0);
                if (isAlreadyMarked(feat) != 0 && FrenchZoningSchemas.isUrbanZoneUsuallyAdmitResidentialConstruction(
                        CollecTransform.getIntersectingSimpleFeatureFromSFC((Geometry) feat.getDefaultGeometry(), zoning)))
                    featureBuilder.set(markFieldName, 1);
                result.add(featureBuilder.buildFeature(Attribute.makeUniqueId()));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        signalIfNoParcelMarked(result, "markParcelIntersectFrenchConstructibleZoningType");
        return result;
    }

    public static SimpleFeatureCollection markParcelOfCommunityType(SimpleFeatureCollection parcels, String attribute) {
        return markParcelOfCommunity(parcels, ParcelAttribute.getCommunityTypeFieldName(), attribute);
    }

    public static SimpleFeatureCollection markParcelOfCommunityNumber(SimpleFeatureCollection parcels, String attribute) {
        return markParcelOfCommunity(parcels, ParcelSchema.getMinParcelCommunityField(), attribute);
    }

    public static SimpleFeatureCollection markParcelOfCommunity(SimpleFeatureCollection parcels, String fieldName, String attribute) {
        final SimpleFeatureType featureSchema = ParcelSchema.getSFBMinParcelSplit().getFeatureType();
        DefaultFeatureCollection result = new DefaultFeatureCollection();
        // if features have the schema that the one intended to set, we bypass
        if (featureSchema.equals(parcels.getSchema())) {
            Arrays.stream(parcels.toArray(new SimpleFeature[0])).forEach(feat -> {
                if (isAlreadyMarked(feat) != 0 && feat.getAttribute(fieldName).equals(attribute))
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
                SimpleFeatureBuilder featureBuilder = ParcelSchema.setSFBMinParcelSplitWithFeat(feat, ParcelSchema.getSFBMinParcelSplit(), featureSchema, 0);
                if (isAlreadyMarked(feat) != 0 && feat.getAttribute(fieldName).equals(attribute))
                    featureBuilder.set(markFieldName, 1);
                result.add(featureBuilder.buildFeature(Attribute.makeUniqueId()));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        signalIfNoParcelMarked(result, "markParcelOfCommunity");
        return result;
    }

    /**
     *  Get the name of the field containing the parcel's mark
     * @return the field name
     */
    public static String getMarkFieldName() {
        return markFieldName;
    }

    /**
     *  Set the name of the field containing the parcel's mark
     * @param markFieldName the field name
     */
    public static void setMarkFieldName(String markFieldName) {
        MarkParcelAttributeFromPosition.markFieldName = markFieldName;
    }

    /**
     * Mark parcels that have already been marked on an other simple collection feature. They may not have the same attribute, so we rely on geometries
     *
     * @param parcelsToMark Parcel collection to copy the marks on. Could have a markFieldName or not.
     * @param parcelsMarked Parcel collection that has a markFieldName field
     * @return {@link SimpleFeatureCollection} of the input parcels with marked parcels on the {@link #markFieldName} field.
     */
    public static SimpleFeatureCollection markAlreadyMarkedParcels(SimpleFeatureCollection parcelsToMark, SimpleFeatureCollection parcelsMarked) {
        if (!CollecMgmt.isCollecContainsAttribute(parcelsMarked, markFieldName)) {
            System.out.println("markAlreadyMarkedParcels: parcelMarked doesn't contain the markFieldName field");
            return parcelsToMark;
        }
        SimpleFeatureBuilder builder = ParcelSchema.addSplitField(parcelsToMark.getSchema());
        DefaultFeatureCollection result = new DefaultFeatureCollection();
        FilterFactory2 ff = CommonFactoryFinder.getFilterFactory2();
        PropertyName geomName = ff.property(parcelsToMark.getSchema().getGeometryDescriptor().getLocalName());
        try (SimpleFeatureIterator parcelIt = parcelsToMark.features()) {
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
        final SimpleFeatureType featureSchema = ParcelSchema.getSFBMinParcelSplit().getFeatureType();
        DefaultFeatureCollection result = new DefaultFeatureCollection();
        if (featureSchema.equals(parcels.getSchema())) {
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
                SimpleFeatureBuilder featureBuilder = ParcelSchema.setSFBMinParcelSplitWithFeat(feat, ParcelSchema.getSFBMinParcelSplit(), featureSchema, 0);
                if (isAlreadyMarked(feat) != 0 && GeneralFields.isParcelHasSimulatedFields(feat))
                    featureBuilder.set(markFieldName, 1);
                result.add(featureBuilder.buildFeature(Attribute.makeUniqueId()));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        signalIfNoParcelMarked(result, "markSimulatedParcel");
        return result;
    }

    /**
     * Mark every {@link SimpleFeature} form a {@link SimpleFeatureCollection} on a new {@link #markFieldName} field with a 1 (true). Untested if the collection already contains
     * the {@link #markFieldName} field.
     *
     * @param sfcIn input {@link SimpleFeatureCollection}
     * @return input {@link SimpleFeatureCollection} with a new {@link #markFieldName} field with only 1 (true) in it.
     */
    public static SimpleFeatureCollection markAllParcel(SimpleFeatureCollection sfcIn) {
        DefaultFeatureCollection result = new DefaultFeatureCollection();
        SimpleFeatureBuilder featureBuilder = ParcelSchema.addSplitField(sfcIn.getSchema());
        try (SimpleFeatureIterator it = sfcIn.features()) {
            while (it.hasNext()) {
                SimpleFeature feat = it.next();
                for (AttributeDescriptor attr : feat.getFeatureType().getAttributeDescriptors())
                    featureBuilder.set(attr.getName(), feat.getAttribute(attr.getName()));
                featureBuilder.set(markFieldName, 1);
                result.add(featureBuilder.buildFeature(Attribute.makeUniqueId()));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
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
            return Arrays.stream(sfcIn.toArray(new SimpleFeature[0])).noneMatch(x -> (int) x.getAttribute(markFieldName) != 0);
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
        DefaultFeatureCollection result = new DefaultFeatureCollection();
        if (!CollecMgmt.isCollecContainsAttribute(in, getMarkFieldName())) {
            System.out.println("getOnlyMarkedParcels : no " + getMarkFieldName() + " field");
            return null;
        }
        Arrays.stream(in.toArray(new SimpleFeature[0])).forEach(feat -> {
            if ((int) feat.getAttribute(markFieldName) == 1)
                result.add(feat);
        });
        return result;
    }

    /**
     *
     * @param sfc
     * @return
     */
    public static SimpleFeatureCollection resetMarkingField(SimpleFeatureCollection sfc) {
        DefaultFeatureCollection result = new DefaultFeatureCollection();
        Arrays.stream(sfc.toArray(new SimpleFeature[0])).forEach(feat -> {
            feat.setAttribute(markFieldName, null);
            result.add(feat);
        });
        return result;
    }

    /**
     * Merge SimpleFeatures that are marked and touches each other and keep the attribute of the largest feature
     *
     * @param parcelCollection
     * @return The collection with the same schema and its touching parcels merged
     */
    public static SimpleFeatureCollection unionTouchingMarkedGeometries(SimpleFeatureCollection parcelCollection) {
        DefaultFeatureCollection result = new DefaultFeatureCollection();
        SimpleFeatureBuilder sfb = new SimpleFeatureBuilder(parcelCollection.getSchema());
        List<Geometry> lGmerged = Geom.unionTouchingGeometries(Arrays.stream(parcelCollection.toArray(new SimpleFeature[0])).filter(sf -> ((Integer) sf.getAttribute(getMarkFieldName()))== 1).map(sf -> (Geometry) sf.getDefaultGeometry()).collect(Collectors.toList()));
        FilterFactory2 ff = CommonFactoryFinder.getFilterFactory2();
        String geomName = parcelCollection.getSchema().getGeometryDescriptor().getLocalName();
        for (Geometry g : lGmerged){
            SimpleFeature s = CollecTransform.getBiggestSF(parcelCollection.subCollection(ff.within(ff.property(geomName), ff.literal(g.buffer(1)))));
            for (AttributeDescriptor attr : parcelCollection.getSchema().getAttributeDescriptors()) {
                if (attr.getLocalName().equals(geomName))
                    continue;
                sfb.set(attr.getLocalName(), s.getAttribute(attr.getLocalName()));
            }
            sfb.add(s);
            sfb.set(geomName,g);
            result.add(sfb.buildFeature(Attribute.makeUniqueId()));
        }
        Arrays.stream(parcelCollection.toArray(new SimpleFeature[0])).forEach(feat -> {
            if ((int) feat.getAttribute(getMarkFieldName()) !=1 ){
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
}
