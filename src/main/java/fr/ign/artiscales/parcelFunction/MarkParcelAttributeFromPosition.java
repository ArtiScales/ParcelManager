package fr.ign.artiscales.parcelFunction;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.geotools.data.shapefile.ShapefileDataStore;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.factory.CommonFactoryFinder;
import org.geotools.feature.DefaultFeatureCollection;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Polygon;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.AttributeDescriptor;
import org.opengis.filter.FilterFactory2;
import org.opengis.filter.expression.PropertyName;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.NoSuchAuthorityCodeException;

import fr.ign.artiscales.decomposition.FlagParcelDecomposition;
import fr.ign.artiscales.fields.GeneralFields;
import fr.ign.artiscales.fields.french.FrenchZoningSchemas;
import fr.ign.cogit.geoToolsFunctions.vectors.Collec;
import fr.ign.cogit.geoToolsFunctions.vectors.Geom;

/**
 * Static methods to mark parcel collections relatively to many other geographic elements, parcel attributes or geometries or other sets of parcels. Doesn't change the input
 * {@link SimpleFeatureCollection} structure but add a marking field. This field's name is <i>SPLIT</i> by default and can be changed.
 * 
 * @author Maxime Colomb
 *
 */
public class MarkParcelAttributeFromPosition {
	
//	public static void main(String[] args) throws Exception {
//		File parcelsMarkedF = new File("/home/ubuntu/workspace/ParcelManager/src/main/resources/testData/out/parcelTotZone.shp");
//		File roadFile = new File("/home/ubuntu/workspace/ParcelManager/src/main/resources/testData/road.shp");
//		File isletFileF = new File("/tmp/Islet.shp");
//		long startTime = System.currentTimeMillis();
//		ShapefileDataStore sdsParcel = new ShapefileDataStore(parcelsMarkedF.toURI().toURL());
//		SimpleFeatureCollection parcels = sdsParcel.getFeatureSource().getFeatures();
//		ShapefileDataStore sdsIslet = new ShapefileDataStore(isletFileF.toURI().toURL());
//		SimpleFeatureCollection islet = sdsIslet.getFeatureSource().getFeatures();
//		markParcelsConnectedToRoad(parcels, islet, roadFile);
//		sdsParcel.dispose();
//		long stopTime = System.currentTimeMillis();
//		System.out.println(stopTime - startTime);
//		sdsIslet.dispose();
//		sdsParcel.dispose();
//	}
	
	/**
	 * Name of the field containing the parcel's mark 
	 */
	static String markFieldName = "SPLIT";


	/**
	 * Mark the parcels that have a connection to the road network, represented either by the void of parcels or road lines (optional)
	 * 
	 * @param parcels
	 *            Input parcel {@link SimpleFeatureCollection}
	 * @param islet
	 *            {@link SimpleFeatureCollection} containing the morphological islet. Can be generated with the
	 *            {@link fr.ign.cogit.geometryGeneration.CityGeneration#createUrbanIslet(File, File)} method.
	 * @param roadFile
	 *            Shapefile containing the road segments
	 * @return {@link SimpleFeatureCollection} of the input parcels with marked parcels on the {@link #markFieldName} field.
	 * 
	 * @throws NoSuchAuthorityCodeException
	 * @throws FactoryException
	 * @throws IOException
	 */
	public static SimpleFeatureCollection markParcelsConnectedToRoad(SimpleFeatureCollection parcels, SimpleFeatureCollection islet, File roadFile) throws NoSuchAuthorityCodeException, FactoryException, IOException {
		ShapefileDataStore sds = new ShapefileDataStore(roadFile.toURI().toURL());
		SimpleFeatureCollection roads = Collec.snapDatas(sds.getFeatureSource().getFeatures(), parcels);
		final SimpleFeatureType featureSchema = ParcelSchema.getSFBMinParcelSplit().getFeatureType();
		DefaultFeatureCollection result = new DefaultFeatureCollection();
		//if features have the schema that the one intended to set, we bypass
		if (featureSchema.equals(parcels.getSchema())) {
			Arrays.stream(parcels.toArray(new SimpleFeature[0])).forEach(feat -> {
				try {
					Geometry geomFeat = (Geometry) feat.getDefaultGeometry();
					if (isAlreadyMarked(feat) != 0 && FlagParcelDecomposition.isParcelHasRoadAccess((Polygon) Geom.getPolygon(geomFeat),
							Collec.snapDatas(roads, geomFeat), Collec.fromSFCtoRingMultiLines(Collec.snapDatas(islet, geomFeat)))) {
						feat.setAttribute(markFieldName, 1);
					} else {
						feat.setAttribute(markFieldName, 0);
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
				result.add(feat);
			});
			sds.dispose();
			return result;
		}
		
		SimpleFeatureBuilder featureBuilder = ParcelSchema.getSFBMinParcelSplit();
		try (SimpleFeatureIterator it = parcels.features()) {
			while (it.hasNext()) {
				SimpleFeature feat = it.next();
				Geometry geomFeat = (Geometry) feat.getDefaultGeometry();
				featureBuilder = ParcelSchema.setSFBMinParcelSplitWithFeat(feat,featureBuilder, featureSchema, 0);
				if (isAlreadyMarked(feat) != 0 &&  FlagParcelDecomposition.isParcelHasRoadAccess((Polygon) Geom.getPolygon(geomFeat), Collec.snapDatas(roads, geomFeat),
						Collec.fromSFCtoRingMultiLines(Collec.snapDatas(islet, geomFeat)))) {
					featureBuilder.set(markFieldName, 1);
				} 
				result.add(featureBuilder.buildFeature(null));
			}
		} catch (Exception e) {
			System.out.println(e);
		}
		sds.dispose();
		return result.collection();
		}
	
	/**
	 * Mark the parcels that size are superior or equal to a given threshold.
	 * @param parcels
	 *            Input parcel {@link SimpleFeatureCollection}
	 * @param size
	 *            Area threshold
	 * @return {@link SimpleFeatureCollection} of the input parcels with marked parcels on the {@link #markFieldName} field.
	 * @throws NoSuchAuthorityCodeException
	 * @throws FactoryException
	 * @throws IOException
	 */
	public static SimpleFeatureCollection markParcelsInf(SimpleFeatureCollection parcels, int size) throws NoSuchAuthorityCodeException, FactoryException, IOException {
		final SimpleFeatureType featureSchema = ParcelSchema.getSFBMinParcelSplit().getFeatureType();
		DefaultFeatureCollection result = new DefaultFeatureCollection();
		
		//if features have the schema that the one intended to set, we bypass 
		if (featureSchema.equals(parcels.getSchema())) {
			Arrays.stream(parcels.toArray(new SimpleFeature[0])).forEach(feat -> {
				try {
					if (isAlreadyMarked(feat) != 0 && ((Geometry)feat.getDefaultGeometry()).getArea() <= size) {
						feat.setAttribute(markFieldName, 1);
					} else {
						feat.setAttribute(markFieldName, 0);
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
				result.add(feat);
			});
			return result;
		}
		SimpleFeatureBuilder featureBuilder = ParcelSchema.getSFBMinParcelSplit();
		try (SimpleFeatureIterator it = parcels.features()) {
			while (it.hasNext()) {
				SimpleFeature feat = it.next();
				featureBuilder = ParcelSchema.setSFBMinParcelSplitWithFeat(feat,featureBuilder, featureSchema, 0);
				if (isAlreadyMarked(feat) != 0 && ((Geometry)feat.getDefaultGeometry()).getArea() <= size) {
					featureBuilder.set(markFieldName, 1);
				} 
				result.add(featureBuilder.buildFeature(null));
			}
		} catch (Exception e) {
			System.out.println(e);
		}
		return result.collection();
	}
	
	/**
	 * Mark the parcels that size are inferior to a given threshold.
	 * 
	 * @param parcels
	 *            Input parcel {@link SimpleFeatureCollection}
	 * @param size
	 *            Area threshold
	 * @return {@link SimpleFeatureCollection} of the input parcels with marked parcels on the {@link #markFieldName} field.
	 * @throws NoSuchAuthorityCodeException
	 * @throws FactoryException
	 * @throws IOException
	 */
	public static SimpleFeatureCollection markParcelsSup(SimpleFeatureCollection parcels, int size)
			throws NoSuchAuthorityCodeException, FactoryException, IOException {
		final SimpleFeatureType featureSchema = ParcelSchema.getSFBMinParcelSplit().getFeatureType();
		DefaultFeatureCollection result = new DefaultFeatureCollection();
		
		//if features have the schema that the one intended to set, we bypass 
		if (featureSchema.equals(parcels.getSchema())) {
			Arrays.stream(parcels.toArray(new SimpleFeature[0])).forEach(feat -> {
				try {
					if (isAlreadyMarked(feat) != 0 && ((Geometry)feat.getDefaultGeometry()).getArea() > size) {
						feat.setAttribute(markFieldName, 1);
					} else {
						feat.setAttribute(markFieldName, 0);
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
				result.add(feat);
			});
			return result;
		}
		SimpleFeatureBuilder featureBuilder = ParcelSchema.getSFBMinParcelSplit();
		try (SimpleFeatureIterator it = parcels.features()) {
			while (it.hasNext()) {
				SimpleFeature feat = it.next();
				featureBuilder = ParcelSchema.setSFBMinParcelSplitWithFeat(feat,featureBuilder, featureSchema, 0);
				if (isAlreadyMarked(feat) != 0 && ((Geometry)feat.getDefaultGeometry()).getArea() > size) {
					featureBuilder.set(markFieldName, 1);
				} 
				result.add(featureBuilder.buildFeature(null));
			}
		} catch (Exception e) {
			System.out.println(e);
		}
		return result.collection();
}
	
	/**
	 * look if a parcel has the {@link #markFieldName} field and if it is positive or negative
	 * 
	 * @param feat
	 *            input SimpleFeature
	 * @return
	 *         <ul>
	 *         <li>If no <i>mark</i> field or the field is unset, return <b>-1</b>
	 *         <li>If <i>mark</i> field is set to 0, return <b>0</b>
	 *         <li>If <i>mark</i> field is set to 1, return <b>1</b>
	 *         </ul>
	 */
	public static int isAlreadyMarked(SimpleFeature feat) {
		if (Collec.isSimpleFeatureContainsAttribute(feat, markFieldName) && feat.getAttribute(markFieldName) != null) {
			if ((int) feat.getAttribute(markFieldName) == 1) {
				return 1;
			} else if ((int) feat.getAttribute(markFieldName) == 0) {
				return 0;
			}
			else {
				return -1;
			}
		}
		else {
			return -1;
		}
	}

	/**
	 * Mark a given number of parcel for the simulation. The selection is random but parcels must be bigger than a certain area threshold.
	 * 
	 * @param parcels
	 *            Input parcel {@link SimpleFeatureCollection}
	 * @param minSize
	 *            minimal size of parcels to be selected
	 * @param nbParcelToMark
	 *            number of parcel wanted
	 * @return {@link SimpleFeatureCollection} of the input parcels with random marked parcels on the {@link #markFieldName} field.
	 * @throws NoSuchAuthorityCodeException
	 * @throws FactoryException
	 */
	public static SimpleFeatureCollection markRandomParcels(SimpleFeatureCollection parcels, int minSize,
			int nbParcelToMark) throws NoSuchAuthorityCodeException, FactoryException {
		return markRandomParcels(parcels, null, null, minSize, nbParcelToMark);
	}

	/**
	 * Mark a given number of parcel for the simulation. The selection is random but parcels must be bigger than a certain area threshold and must be contained is a given zoning
	 * type.
	 * 
	 * @param parcels
	 *            Input parcel {@link SimpleFeatureCollection}
	 * @param minSize
	 *            Minimal size of parcels to be selected
	 * @param zoningType
	 *            Type of the zoning plan to take into consideration
	 * @param zoningFile
	 *            Shapefile containing the zoning plan
	 * @param nbParcelToMark
	 *            Number of parcel wanted
	 * @return {@link SimpleFeatureCollection} of the input parcels with random marked parcels on the {@link #markFieldName} field.
	 * @throws NoSuchAuthorityCodeException
	 * @throws FactoryException
	 */
	public static SimpleFeatureCollection markRandomParcels(SimpleFeatureCollection parcels, String zoningType,
			File zoningFile, double minSize, int nbParcelToMark) throws NoSuchAuthorityCodeException, FactoryException {
		if (zoningFile != null && zoningType != null) {
			parcels = markParcelIntersectGenericZoningType(parcels, zoningType, zoningFile);
		}
		List<SimpleFeature> list = Arrays.stream(parcels.toArray(new SimpleFeature[0])).filter(feat -> 
		((Geometry)feat.getDefaultGeometry()).getArea() > minSize).collect(Collectors.toList());
		Collections.shuffle(list);
		DefaultFeatureCollection result = new DefaultFeatureCollection();
		while (nbParcelToMark > 0) {
			if (!list.isEmpty()) {
				result.add(list.remove(0));
			}
			nbParcelToMark--;
		}
	return parcels;
	}
	
	/**
	 * Mark the built parcels. Subtract a buffer of 1 meters on the buildings to avoid blurry parcel and builing definition
	 * 
	 * @param parcels
	 *            Input parcel {@link SimpleFeatureCollection}
	 * @param buildingFile
	 *            Shapefile containing building features
	 * @return {@link SimpleFeatureCollection} of the input parcels with marked parcels on the {@link #markFieldName} field.
	 * @throws IOException
	 * @throws NoSuchAuthorityCodeException
	 * @throws FactoryException
	 */
	public static SimpleFeatureCollection markUnBuiltParcel(SimpleFeatureCollection parcels, File buildingFile)
			throws IOException, NoSuchAuthorityCodeException, FactoryException {
		ShapefileDataStore sds = new ShapefileDataStore(buildingFile.toURI().toURL());
		SimpleFeatureCollection buildings = Collec.snapDatas(sds.getFeatureSource().getFeatures(), parcels);
		final SimpleFeatureType featureSchema = ParcelSchema.getSFBMinParcelSplit().getFeatureType();
		DefaultFeatureCollection result = new DefaultFeatureCollection();
		//if features have the schema that the one intended to set, we bypass 
		if (featureSchema.equals(parcels.getSchema())) {
			Arrays.stream(parcels.toArray(new SimpleFeature[0])).forEach(feat -> {
				try {
					if (isAlreadyMarked(feat) != 0
							&& !ParcelState.isAlreadyBuilt(Collec.snapDatas(buildings, (Geometry) feat.getDefaultGeometry()), feat, -1.0)) {
						feat.setAttribute(markFieldName, 1);
					} else {
						feat.setAttribute(markFieldName, 0);
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
				result.add(feat);
			});
			sds.dispose();
			return result;
		}
		SimpleFeatureBuilder featureBuilder = ParcelSchema.getSFBMinParcelSplit();
		try (SimpleFeatureIterator it = parcels.features()) {
			while (it.hasNext()) {
				SimpleFeature feat = it.next();
				featureBuilder = ParcelSchema.setSFBMinParcelSplitWithFeat(feat,featureBuilder, featureSchema, 0);
				if (isAlreadyMarked(feat) != 0 && !ParcelState.isAlreadyBuilt(Collec.snapDatas(buildings, (Geometry) feat.getDefaultGeometry()), feat, -1.0)) {
					featureBuilder.set(markFieldName, 1);
				} 
				result.add(featureBuilder.buildFeature(null));
			}
		} catch (Exception e) {
			System.out.println(e);
		}
		sds.dispose();
		return result.collection();
	}
	
	/**
	 * Mark the built parcels. Subtract a buffer of 1 meters on the buildings to avoid blurry parcel and building definition
	 * 
	 * @param parcels
	 *            Input parcel {@link SimpleFeatureCollection}
	 * @param buildingFile
	 *            Shapefile representing the buildings
	 * @return {@link SimpleFeatureCollection} of the input parcels with marked parcels on the {@link #markFieldName} field.
	 * @throws IOException
	 * @throws NoSuchAuthorityCodeException
	 * @throws FactoryException
	 */
	public static SimpleFeatureCollection markBuiltParcel(SimpleFeatureCollection parcels, File buildingFile)
			throws IOException, NoSuchAuthorityCodeException, FactoryException {
		ShapefileDataStore sds = new ShapefileDataStore(buildingFile.toURI().toURL());
		SimpleFeatureCollection buildings = Collec.snapDatas(sds.getFeatureSource().getFeatures(), parcels);
		final SimpleFeatureType featureSchema = ParcelSchema.getSFBMinParcelSplit().getFeatureType();
		DefaultFeatureCollection result = new DefaultFeatureCollection();
		
		//if features have the schema that the one intended to set, we bypass 
		if (featureSchema.equals(parcels.getSchema())) {
			Arrays.stream(parcels.toArray(new SimpleFeature[0])).forEach(feat -> {
				try {
					if (isAlreadyMarked(feat) != 0 && ParcelState.isAlreadyBuilt(Collec.snapDatas(buildings, (Geometry) feat.getDefaultGeometry()), feat, -1.0)) {
						feat.setAttribute(markFieldName, 1);
					} else {
						feat.setAttribute(markFieldName, 0);
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
				result.add(feat);
			});
			sds.dispose();
			return result;
		}
		SimpleFeatureBuilder featureBuilder = ParcelSchema.getSFBMinParcelSplit();
		try (SimpleFeatureIterator it = parcels.features()) {
			while (it.hasNext()) {
				SimpleFeature feat = it.next();
				featureBuilder = ParcelSchema.setSFBMinParcelSplitWithFeat(feat,featureBuilder, featureSchema, 0);
				if (isAlreadyMarked(feat) != 0 && ParcelState.isAlreadyBuilt(Collec.snapDatas(buildings, (Geometry) feat.getDefaultGeometry()), feat, -1.0)) {
					featureBuilder.set(markFieldName, 1);
				} 
				result.add(featureBuilder.buildFeature(null));
			}
		} catch (Exception e) {
			System.out.println(e);
		}
		sds.dispose();
		return result.collection();
	}
	
	
	/**
	 * Mark parcels that intersects a given collection of polygons. The default field name containing the mark is "SPLIT" but it can be changed with the {@link #setMarkFieldName(String)}
	 * method.
	 * 
	 * @param parcels
	 *            Input parcel {@link SimpleFeatureCollection}
	 * @param polygonIntersectionFile
	 *            A shapefile containing the collection of polygons
	 * @return {@link SimpleFeatureCollection} of the input parcels with marked parcels on the {@link #markFieldName} field.
	 * @throws IOException
	 * @throws FactoryException
	 * @throws NoSuchAuthorityCodeException
	 * @throws Exception
	 */
	public static SimpleFeatureCollection markParcelIntersectPolygonIntersection(SimpleFeatureCollection parcels, File polygonIntersectionFile)
			throws IOException, NoSuchAuthorityCodeException, FactoryException {
		
		DefaultFeatureCollection result = new DefaultFeatureCollection();
		ShapefileDataStore sds = new ShapefileDataStore(polygonIntersectionFile.toURI().toURL());
		Geometry geomPolygonIntersection = Geom.unionSFC(Collec.snapDatas(sds.getFeatureSource().getFeatures(), parcels));
		sds.dispose();

		final SimpleFeatureType featureSchema = ParcelSchema.getSFBMinParcelSplit().getFeatureType();

		//if features have the schema that the one intended to set, we bypass 
		if (featureSchema.equals(parcels.getSchema())) {
			Arrays.stream(parcels.toArray(new SimpleFeature[0])).forEach(feat -> {
				try {
					if (isAlreadyMarked(feat) != 0 && ((Geometry) feat.getDefaultGeometry()).intersects(geomPolygonIntersection)) {
						feat.setAttribute(markFieldName, 1);
					} else {
						feat.setAttribute(markFieldName, 0);
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
				result.add(feat);
			});
			return result;
		}
		SimpleFeatureBuilder featureBuilder = ParcelSchema.getSFBMinParcelSplit();
		try (SimpleFeatureIterator it = parcels.features()) {
			while (it.hasNext()) {
				SimpleFeature feat = it.next();
				featureBuilder = ParcelSchema.setSFBMinParcelSplitWithFeat(feat,featureBuilder, featureSchema, 0);
				if (isAlreadyMarked(feat) != 0 && ((Geometry) feat.getDefaultGeometry()).intersects(geomPolygonIntersection)) {
					featureBuilder.set(markFieldName, 1);
				} 
				result.add(featureBuilder.buildFeature(null));
			}
		} catch (Exception e) {
			System.out.println(e);
		}
		return result.collection();
	}

	/**
	 * Mark parcels that intersects a certain type of zoning.
	 * TODO revoir le marquage pour prendre en compte les zones secondaires
	 * 
	 * @param parcels
	 *            Input parcel {@link SimpleFeatureCollection}
	 * @param zoningType
	 *            The big kind of the zoning (either not constructible (NC), urbanizable (U) or to be urbanize (TBU). Other keywords can be tolerate
	 * @param zoningFile
	 *            A shapefile containing the zoning plan
	 * @return {@link SimpleFeatureCollection} of the input parcels with marked parcels on the {@link #markFieldName} field.
	 * @throws FactoryException 
	 * @throws NoSuchAuthorityCodeException 
	 * @throws IOException
	 * @throws Exception
	 */
	public static SimpleFeatureCollection markParcelIntersectGenericZoningType(SimpleFeatureCollection parcels, String zoningType, File zoningFile) throws NoSuchAuthorityCodeException, FactoryException {
		final SimpleFeatureType featureSchema = ParcelSchema.getSFBMinParcelSplit().getFeatureType();
		DefaultFeatureCollection result = new DefaultFeatureCollection();
		
		//if features have the schema that the one intended to set, we bypass 
		if (featureSchema.equals(parcels.getSchema())) {
			Arrays.stream(parcels.toArray(new SimpleFeature[0])).forEach(feat -> {
				try {
					if (isAlreadyMarked(feat) != 0 && ParcelState.parcelInBigZone(zoningFile, feat).equals(zoningType) ) {
						feat.setAttribute(markFieldName, 1);
					} else {
						feat.setAttribute(markFieldName, 0);
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
				result.add(feat);
			});
			return result;
		}
		SimpleFeatureBuilder featureBuilder = ParcelSchema.getSFBMinParcelSplit();
		try (SimpleFeatureIterator it = parcels.features()) {
			while (it.hasNext()) {
				SimpleFeature feat = it.next();
				featureBuilder = ParcelSchema.setSFBMinParcelSplitWithFeat(feat,featureBuilder, featureSchema, 0);
				if (isAlreadyMarked(feat) != 0 && ParcelState.parcelInBigZone(zoningFile, feat).equals(zoningType)) {
					featureBuilder.set(markFieldName, 1);
				} 
				result.add(featureBuilder.buildFeature(null));
			}
		} catch (Exception e) {
			System.out.println(e);
		}
		return result;
	}
	
	/**
	 * Mark parcels that intersects that are usually constructible for French regulation
	 * 
	 * @param parcels
	 *            Input parcel {@link SimpleFeatureCollection}
	 * @param zoningFile
	 *            A shapefile containing the zoning plan
	 * @return {@link SimpleFeatureCollection} of the input parcels with marked parcels on the {@link #markFieldName} field.
	 * @throws FactoryException 
	 * @throws NoSuchAuthorityCodeException 
	 * @throws IOException
	 * @throws Exception
	 */
	public static SimpleFeatureCollection markParcelIntersectFrenchConstructibleZoningType(SimpleFeatureCollection parcels, File zoningFile) throws NoSuchAuthorityCodeException, FactoryException, IOException {
		final SimpleFeatureType featureSchema = ParcelSchema.getSFBMinParcelSplit().getFeatureType();
		
		ShapefileDataStore sds = new ShapefileDataStore(zoningFile.toURI().toURL());
		SimpleFeatureCollection zonings = sds.getFeatureSource().getFeatures();

		DefaultFeatureCollection result = new DefaultFeatureCollection();
		
		//if features have the schema that the one intended to set, we bypass 
		if (featureSchema.equals(parcels.getSchema())) {
			Arrays.stream(parcels.toArray(new SimpleFeature[0])).forEach(feat -> {
				if (isAlreadyMarked(feat) != 0 && FrenchZoningSchemas
						.isUrbanZoneUsuallyAdmitResidentialConstruction(Collec.getSimpleFeatureFromSFC((Geometry) feat.getDefaultGeometry(), zonings))) {
					feat.setAttribute(markFieldName, 1);
				} else {
					feat.setAttribute(markFieldName, 0);
				}
				result.add(feat);
			});
			sds.dispose();
			return result.collection();
		}
		
		SimpleFeatureBuilder featureBuilder = ParcelSchema.getSFBMinParcelSplit();
		try (SimpleFeatureIterator it = parcels.features()) {
			while (it.hasNext()) {
				SimpleFeature feat = it.next();
				featureBuilder = ParcelSchema.setSFBMinParcelSplitWithFeat(feat,featureBuilder, featureSchema, 0);
				if (isAlreadyMarked(feat) != 0 && FrenchZoningSchemas
						.isUrbanZoneUsuallyAdmitResidentialConstruction(Collec.getSimpleFeatureFromSFC((Geometry) feat.getDefaultGeometry(), zonings))) {
					featureBuilder.set(markFieldName, 1);
				} 
				result.add(featureBuilder.buildFeature(null));
			}
		} catch (Exception e) {
			System.out.println(e);
		}
		sds.dispose();
		return result;
	}

	public static SimpleFeatureCollection markParcelOfCommunityType(SimpleFeatureCollection parcels, String attribute)
			throws NoSuchAuthorityCodeException, FactoryException {
		return markParcelOfCommunity(parcels, ParcelAttribute.getCommunityTypeFieldName(), attribute);
	}

	public static SimpleFeatureCollection markParcelOfCommunityNumber(SimpleFeatureCollection parcels, String attribute)
			throws NoSuchAuthorityCodeException, FactoryException {
		return markParcelOfCommunity(parcels, ParcelSchema.getMinParcelCommunityField(), attribute);
	}

	public static SimpleFeatureCollection markParcelOfCommunity(SimpleFeatureCollection parcels, String fieldName, String attribute) throws NoSuchAuthorityCodeException, FactoryException {
		final SimpleFeatureType featureSchema = ParcelSchema.getSFBMinParcelSplit().getFeatureType();
		DefaultFeatureCollection result = new DefaultFeatureCollection();
		
		//if features have the schema that the one intended to set, we bypass 
		if (featureSchema.equals(parcels.getSchema())) {
			Arrays.stream(parcels.toArray(new SimpleFeature[0])).forEach(feat -> {
				if (isAlreadyMarked(feat) != 0 && feat.getAttribute(fieldName).equals(attribute)){
					feat.setAttribute(markFieldName, 1);
				} else {
					feat.setAttribute(markFieldName, 0);
				}
				result.add(feat);
			});
			return result;
		}
		
		SimpleFeatureBuilder featureBuilder = ParcelSchema.getSFBMinParcelSplit();
		try (SimpleFeatureIterator it = parcels.features()) {
			while (it.hasNext()) {
				SimpleFeature feat = it.next();
				featureBuilder = ParcelSchema.setSFBMinParcelSplitWithFeat(feat,featureBuilder, featureSchema, 0);
				if (isAlreadyMarked(feat) != 0 && feat.getAttribute(fieldName).equals(attribute)) {
					featureBuilder.set(markFieldName, 1);
				} 
				result.add(featureBuilder.buildFeature(null));
			}
		} catch (Exception e) {
			System.out.println(e);
		}
		return result;
	}

	public static String getMarkFieldName() {
		return markFieldName;
	}

	public static void setMarkFieldName(String markFieldName) {
		MarkParcelAttributeFromPosition.markFieldName = markFieldName;
	}

	/**
	 * Mark parcels that have already been marked on an other simple collection feature
	 * 
	 * @param parcelsToMark
	 *            Parcel collection to copy the marks on. Could have a markFieldName or not.
	 * @param parcelsMarked
	 *            Parcel collection that has a markFieldName field
	 * @return {@link SimpleFeatureCollection} of the input parcels with marked parcels on the {@link #markFieldName} field.
	 */
	public static SimpleFeatureCollection markAlreadyMarkedParcels(SimpleFeatureCollection parcelsToMark, SimpleFeatureCollection parcelsMarked) {
		if (!Collec.isCollecContainsAttribute(parcelsMarked, markFieldName)) {
			System.out.println("markAlreadyMarkedParcels: parcelMarked doesn't contain the markFieldName field");
			return parcelsToMark ;
		}
		SimpleFeatureBuilder builder = ParcelSchema.addSplitField(parcelsToMark.getSchema());
		DefaultFeatureCollection result = new DefaultFeatureCollection();
		FilterFactory2 ff = CommonFactoryFinder.getFilterFactory2();
		PropertyName pName = ff.property(parcelsToMark.getSchema().getGeometryDescriptor().getLocalName());
		
		try (SimpleFeatureIterator parcelIt = parcelsToMark.features()){
			toMarkParcel : while (parcelIt.hasNext()) {
				SimpleFeature parcelToMark = parcelIt.next();
				Geometry geomParcelToMark = (Geometry) parcelToMark.getDefaultGeometry();
				double geomArea = geomParcelToMark.getArea();
				//look for exact geometries 
				SimpleFeatureCollection parcelsIntersectRef = parcelsMarked.subCollection(ff.intersects(pName, ff.literal(geomParcelToMark)));
				try (SimpleFeatureIterator itParcelsMarked = parcelsIntersectRef.features()) {
					while (itParcelsMarked.hasNext()) {
						SimpleFeature parcelMarked = itParcelsMarked.next();
						double inter = geomParcelToMark.intersection((Geometry) parcelMarked.getDefaultGeometry()).getArea();
						// if there are parcel intersection and a similar area, we conclude that parcel haven't changed. We put it in the \"same\" collection and stop the search
						if (inter > 0.95 * geomArea && inter < 1.05 * geomArea) {
							for (AttributeDescriptor attr : parcelToMark.getFeatureType().getAttributeDescriptors()) {
								builder.set(attr.getName(), parcelToMark.getAttribute(attr.getName()));
							}
							builder.set(markFieldName, parcelMarked.getAttribute(markFieldName));
							result.add(builder.buildFeature(null));
							continue toMarkParcel;
						}

					}
				} catch (Exception problem) {
					problem.printStackTrace();
				}
				// if we haven't found correspondance
				for (AttributeDescriptor attr : parcelToMark.getFeatureType().getAttributeDescriptors()) {
					builder.set(attr.getName(), parcelToMark.getAttribute(attr.getName()));
				}
				builder.set(markFieldName, 0);
				result.add(builder.buildFeature(null));
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return result;
	}

	/**
	 * Mark a parcel if it has been simulated. This is done using the <i>section</i> field name and the method
	 * {@link fr.ign.artiscales.fields.GeneralFields#isParcelLikeFrenchHasSimulatedFileds(SimpleFeature)}.
	 * 
	 * @param parcels
	 *            Input parcel {@link SimpleFeatureCollection}
	 * @return {@link SimpleFeatureCollection} of the input parcels with marked parcels on the {@link #markFieldName} field.
	 * @throws FactoryException
	 * @throws NoSuchAuthorityCodeException
	 * @throws IOException
	 */
	public static SimpleFeatureCollection markSimulatedParcel(SimpleFeatureCollection parcels) throws NoSuchAuthorityCodeException, FactoryException, IOException {
		final SimpleFeatureType featureSchema = ParcelSchema.getSFBMinParcelSplit().getFeatureType();
		DefaultFeatureCollection result = new DefaultFeatureCollection();
		SimpleFeatureBuilder featureBuilder = ParcelSchema.getSFBMinParcelSplit();
		try (SimpleFeatureIterator it = parcels.features()) {
			while (it.hasNext()) {
				SimpleFeature feat = it.next();
				featureBuilder = ParcelSchema.setSFBMinParcelSplitWithFeat(feat,featureBuilder, featureSchema, 0);
				if (isAlreadyMarked(feat) != 0 && GeneralFields.isParcelLikeFrenchHasSimulatedFileds(feat)) {
					featureBuilder.set(markFieldName, 1);
				} 
				result.add(featureBuilder.buildFeature(null));
			}
		} catch (Exception e) {
			System.out.println(e);
		}
		return result.collection();
	}

	/**
	 * Mark every {@link SimpleFeature} form a {@link SimpleFeatureCollection} on a new {@link #markFieldName} field with a 1 (true). Untested if the collection already contains
	 * the {@link #markFieldName} field.
	 * 
	 * @param sfcIn
	 *            input {@link SimpleFeatureCollection}
	 * @return input {@link SimpleFeatureCollection} with a new {@link #markFieldName} field with only 1 (true) in it.
	 * @throws NoSuchAuthorityCodeException
	 * @throws FactoryException
	 * @throws IOException
	 */
	public static SimpleFeatureCollection markAllParcel(SimpleFeatureCollection sfcIn) throws NoSuchAuthorityCodeException, FactoryException, IOException {
		DefaultFeatureCollection result = new DefaultFeatureCollection();
		SimpleFeatureBuilder featureBuilder = ParcelSchema.addSplitField(sfcIn.getSchema());
		try (SimpleFeatureIterator it = sfcIn.features()) {
			while (it.hasNext()) {
				SimpleFeature feat = it.next();
				for (AttributeDescriptor attr : feat.getFeatureType().getAttributeDescriptors()) {
					featureBuilder.set(attr.getName(), feat.getAttribute(attr.getName()));
				}
				featureBuilder.set(markFieldName, 1);
				result.add(featureBuilder.buildFeature(null));
			}
		} catch (Exception e) {
			System.out.println(e);
		}
		return result.collection();
	}
}
