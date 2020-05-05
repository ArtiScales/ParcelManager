package fr.ign.artiscales.goal;

import java.io.File;
import java.util.Arrays;

import org.geotools.data.DataUtilities;
import org.geotools.data.shapefile.ShapefileDataStore;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.factory.CommonFactoryFinder;
import org.geotools.feature.DefaultFeatureCollection;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.locationtech.jts.geom.Geometry;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.filter.FilterFactory2;

import fr.ign.artiscales.decomposition.OBBBlockDecomposition;
import fr.ign.artiscales.parcelFunction.MarkParcelAttributeFromPosition;
import fr.ign.artiscales.parcelFunction.ParcelCollection;
import fr.ign.artiscales.parcelFunction.ParcelSchema;
import fr.ign.cogit.geoToolsFunctions.Attribute;
import fr.ign.cogit.geoToolsFunctions.vectors.Collec;
import fr.ign.cogit.geoToolsFunctions.vectors.Geom;
import fr.ign.cogit.geometryGeneration.CityGeneration;

/**
 * Simulation following this goal merge together the contiguous marked parcels to create zones. The chosen parcel division process (OBB by default) is then applied on each created zone.
 * 
 * @author Maxime Colomb
 *
 */
public class ConsolidationDivision {
	/**
	 * If true, will save all the intermediate results in the temporary folder
	 */
	public static boolean DEBUG = false;
	/**
	 * The process used to divide the parcels
	 */
	public static String PROCESS = "OBB";
	/**
	 * If true, will save a shapefile containing only the simulated parcels in the temporary folder.
	 */
	public static boolean SAVEINTERMEDIATERESULT = false;
	/**
	 * If true, overwrite the output saved shapefiles. If false, happend the simulated parcels to a potential already existing shapefile.
	 */
	public static boolean OVERWRITESHAPEFILES = true;


	/**
	 * Method that merges the contiguous marked parcels into zones and then split those zones with a given parcel division algorithm (by default, the Oriented Bounding Box)
	 * overload of {@link #consolidationDivision(SimpleFeatureCollection, File, double, double, double, double, int, double, int)} for a single road size.
	 * @param parcels
	 *            The parcels to be merged and cut. Must be marked with the SPLIT filed (see markParcelIntersectMUPOutput for example, with the method concerning MUP-City's output)
	 * @param tmpFolder
	 *            A temporary folder where will be saved intermediate results
	 * @param maximalArea
	 *            Area under which a parcel won"t be anymore cut
	 * @param minimalArea
	 *            Area under which a polygon won't be kept as a parcel
	 * @param maximalWidth
	 *            The width of parcel connection to street network under which the parcel won"t be anymore cut
	 * @param streetWidth
	 *            the width of generated street network. this @overload is setting a single size for those roads
	 * @param decompositionLevelWithoutStreet
	 *            Number of the final row on which street generation doesn't apply
	 * @return the set of parcel with decomposition
	 * @throws Exception
	 */
	public static SimpleFeatureCollection consolidationDivision(SimpleFeatureCollection parcels, File tmpFolder, double maximalArea,
			double minimalArea, double maximalWidth, double streetWidth, int decompositionLevelWithoutStreet) throws Exception {
		return consolidationDivision(parcels, null, tmpFolder, 0.5, 0.0, maximalArea, minimalArea, maximalWidth, streetWidth, 999, streetWidth,
				decompositionLevelWithoutStreet);
	}

	/**
	 * Method that merges the contiguous marked parcels into zones and then split those zones with a given parcel division algorithm (by default, the Oriented Bounding Box).
	 * 
	 * @param parcels
	 *            The parcels to be merged and cut. Must be marked with the SPLIT filed (see markParcelIntersectMUPOutput for example, with the method concerning MUP-City's output)
	 * @param tmpFolder
	 *            A temporary folder where will be saved intermediate results
	 * @param maximalArea
	 *            Area under which a parcel won"t be anymore cut
	 * @param minimalArea
	 *            Area under which a polygon won't be kept as a parcel
	 * @param maximalWidth
	 *            The width of parcel connection to street network under which the parcel won"t be anymore cut
	 * @param smallStreetWidth
	 *            The width of small street network segments
	 * @param largeStreetLevel
	 *            Level of decomposition after which the streets are considered as large streets
	 * @param largeStreetWidth
	 *            The width of large street network segments
	 * @param decompositionLevelWithoutStreet
	 *            Number of the final row on which street generation doesn't apply
	 * @return the set of parcel with decomposition
	 * @throws Exception
	 */
	public static SimpleFeatureCollection consolidationDivision(SimpleFeatureCollection parcels, File roadFile, File tmpFolder, double roadEpsilon, double noise, double maximalArea,
			double minimalArea, double maximalWidth, double smallStreetWidth, int largeStreetLevel, double largeStreetWidth,
			int decompositionLevelWithoutStreet) throws Exception {
		return consolidationDivision(parcels, roadFile, tmpFolder, null, roadEpsilon, noise, maximalArea, minimalArea, maximalWidth, smallStreetWidth, largeStreetLevel,
				largeStreetWidth, decompositionLevelWithoutStreet);
	}
		
	/**
	 * Method that merges the contiguous marked parcels into zones and then split those zones with a given parcel division algorithm (by default, the Oriented Bounding Box).
	 * 
	 * @param parcels
	 *            The parcels to be merged and cut. Must be marked with the SPLIT filed (see markParcelIntersectMUPOutput for example, with the method concerning MUP-City's output)
	 * @param tmpFolder
	 *            A temporary folder where will be saved intermediate results
	 * @param polygonIntersection
	 *            Optional polygon layer that was used to process to the selection of parcels with their intersection. Used to keep only the intersecting simulated parcels.
	 * @param maximalArea
	 *            Area under which a parcel won"t be anymore cut
	 * @param minimalArea
	 *            Area under which a polygon won't be kept as a parcel
	 * @param maximalWidth
	 *            The width of parcel connection to street network under which the parcel won"t be anymore cut
	 * @param smallStreetWidth
	 *            The width of small street network segments
	 * @param largeStreetLevel
	 *            Level of decomposition after which the streets are considered as large streets
	 * @param largeStreetWidth
	 *            The width of large street network segments
	 * @param decompositionLevelWithoutStreet
	 *            Number of the final row on which street generation doesn't apply
	 * @return the set of parcel with decomposition
	 * @throws Exception
	 */
	public static SimpleFeatureCollection consolidationDivision(SimpleFeatureCollection parcels, File roadFile, File tmpFolder,
			File polygongIntersection, double roadEpsilon, double noise, double maximalArea, double minimalArea, double maximalWidth,
			double smallStreetWidth, int largeStreetLevel, double largeStreetWidth, int decompositionLevelWithoutStreet) throws Exception {

		DefaultFeatureCollection parcelSaved = new DefaultFeatureCollection();
		parcelSaved.addAll(parcels);
		DefaultFeatureCollection parcelToMerge = new DefaultFeatureCollection();

		if (DEBUG) {
			Collec.exportSFC(parcels, new File(tmpFolder, "step0.shp"));
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
			Collec.exportSFC(parcelToMerge.collection(), new File(tmpFolder, "step1.shp"));
			System.out.println("done step 1");
		}

		////////////////
		// second step : merge of the parcel that touches themselves by islet
		////////////////

		DefaultFeatureCollection mergedParcels = new DefaultFeatureCollection();
		SimpleFeatureBuilder sfBuilder = ParcelSchema.getSFBMinParcelSplit();

		Geometry multiGeom = Geom.unionSFC(parcelToMerge);
		for (int i = 0; i < multiGeom.getNumGeometries(); i++) {
			sfBuilder.add(multiGeom.getGeometryN(i));
			sfBuilder.set(ParcelSchema.getMinParcelSectionField(), Integer.toString(i));
			sfBuilder.set(ParcelSchema.getMinParcelCommunityField(),
					Collec.getFieldFromSFC(multiGeom.getGeometryN(i), parcels, ParcelSchema.getMinParcelCommunityField()));
			mergedParcels.add(sfBuilder.buildFeature(Attribute.makeUniqueId()));
		}
		if (DEBUG) {
			Collec.exportSFC(mergedParcels.collection(), new File(tmpFolder, "step2.shp"));
			System.out.println("done step 2");
		}

		////////////////
		// third step : cuting of the parcels
		////////////////

		SimpleFeatureCollection roads;
		if (roadFile != null && roadFile.exists()) {
			ShapefileDataStore sdsRoad = new ShapefileDataStore(roadFile.toURI().toURL());
			roads = DataUtilities.collection(sdsRoad.getFeatureSource().getFeatures());
			sdsRoad.dispose();
		} else {
			roads = null;
		}
		
		SimpleFeatureBuilder sfBuilderFinalParcel = ParcelSchema.getSFBMinParcel();
		DefaultFeatureCollection cutParcels = new DefaultFeatureCollection();
		SimpleFeatureCollection isletCollection = CityGeneration.createUrbanIslet(parcels);
		FilterFactory2 ff = CommonFactoryFinder.getFilterFactory2();

		Arrays.stream(mergedParcels.toArray(new SimpleFeature[0])).forEach(feat -> {
			if (((Geometry) feat.getDefaultGeometry()).getArea() > maximalArea) {
				// Parcel big enough, we cut it
				feat.setAttribute(MarkParcelAttributeFromPosition.getMarkFieldName(), 1);
				try {
					SimpleFeatureCollection freshCutParcel = new DefaultFeatureCollection();
					switch (PROCESS) {
					case "OBB":
						freshCutParcel = OBBBlockDecomposition.splitParcels(feat,
								(roads != null && !roads.isEmpty()) ? Collec.snapDatas(roads, (Geometry) feat.getDefaultGeometry()) : null,
								maximalArea, maximalWidth, roadEpsilon, noise,
								Collec.fromPolygonSFCtoListRingLines(isletCollection.subCollection(
										ff.bbox(ff.property(feat.getFeatureType().getGeometryDescriptor().getLocalName()), feat.getBounds()))),
								smallStreetWidth, largeStreetLevel, largeStreetWidth, true, decompositionLevelWithoutStreet);
						break;
					case "SS":
						System.out.println("not implemented yet");
						break;
					case "MS":
						System.out.println("not implemented yet");
						break;
					}
					SimpleFeatureIterator it = freshCutParcel.features();
					// every single parcel goes into new collection
					int i = 0 ;
					while (it.hasNext()) {
						SimpleFeature freshCut = it.next();
						// that takes time but it's the best way I've found to set a correct section
						// number (to look at the step 2 polygons)
						String sec = "Default";
						try (SimpleFeatureIterator ilotIt = mergedParcels.features()){
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
						sfBuilderFinalParcel.set("the_geom", freshCut.getDefaultGeometry());
						sfBuilderFinalParcel.set(ParcelSchema.getMinParcelSectionField(), "newSection" + sec + "ConsolidRecomp");
						sfBuilderFinalParcel.set(ParcelSchema.getMinParcelNumberField(), String.valueOf(i++));
						sfBuilderFinalParcel.set(ParcelSchema.getMinParcelCommunityField(),
								feat.getAttribute(ParcelSchema.getMinParcelCommunityField()));
						cutParcels.add(sfBuilderFinalParcel.buildFeature(Attribute.makeUniqueId()));
					}
					it.close();
				} catch (Exception e) {
					e.printStackTrace();
				}
			} else {
				// parcel not big enough, we directly put it in the collection
				cutParcels.add(sfBuilderFinalParcel.buildFeature(Attribute.makeUniqueId(), new Object[] { feat.getDefaultGeometry() }));
			}
		});
		
		DefaultFeatureCollection result = new DefaultFeatureCollection();
//		if (cutParcels.size() == 0) {
//			System.out.println("parcelConsolidRecomp produces no cut parcels");
//			return parcels;
//		}

		if (DEBUG) {
			Collec.exportSFC(cutParcels, new File(tmpFolder, "step3.shp"));
			System.out.println("done step 3");
		}
		
		// merge small parcels
		SimpleFeatureCollection cutBigParcels = ParcelCollection.mergeTooSmallParcels(cutParcels, (int) minimalArea);
		SimpleFeatureType schema = ParcelSchema.getSFBMinParcel().getFeatureType();
		Arrays.stream(cutBigParcels.toArray(new SimpleFeature[0])).forEach(feat -> {
			SimpleFeatureBuilder SFBParcel = ParcelSchema.setSFBMinParcelWithFeat(feat, schema);
			result.add(SFBParcel.buildFeature(Attribute.makeUniqueId()));
		});
		
		if(SAVEINTERMEDIATERESULT) {
			Collec.exportSFC(result, new File(tmpFolder, "parcelConsolidationOnly.shp"), OVERWRITESHAPEFILES);
			OVERWRITESHAPEFILES = false;
		}
		
		// add initial non cut parcel to final parcels 
		Arrays.stream(parcelSaved.toArray(new SimpleFeature[0])).forEach(feat -> {
			SimpleFeatureBuilder SFBParcel = ParcelSchema.setSFBMinParcelWithFeat(feat, schema);
			result.add(SFBParcel.buildFeature(Attribute.makeUniqueId()));
		});
		
		if (DEBUG) {
			Collec.exportSFC(result, new File(tmpFolder, "step4.shp"));
			System.out.println("done step 4");
		}
		
//		//If the selection of parcel was based on a polygon intersection file, we keep only the intersection parcels
//		//TODO avec une emprise plus large que juste les parcelles : regarder du côté de morpholim pour un calculer un buffer suffisant ?)
//		if (polygonIntersection != null && polygonIntersection.exists()) {
//			ShapefileDataStore sdsInter = new ShapefileDataStore(polygonIntersection.toURI().toURL());
//			SimpleFeatureCollection sfc = sdsInter.getFeatureSource().getFeatures();
//			
//			
//			sdsInter.dispose();
//		}
		return result;
	}

}
