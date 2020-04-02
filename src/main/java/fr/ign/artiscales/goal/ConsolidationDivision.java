package fr.ign.artiscales.goal;

import java.io.File;
import java.util.Arrays;

import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.feature.DefaultFeatureCollection;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.locationtech.jts.geom.Geometry;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;

import fr.ign.artiscales.decomposition.ParcelSplit;
import fr.ign.artiscales.parcelFunction.MarkParcelAttributeFromPosition;
import fr.ign.artiscales.parcelFunction.ParcelCollection;
import fr.ign.artiscales.parcelFunction.ParcelSchema;
import fr.ign.cogit.geoToolsFunctions.vectors.Collec;
import fr.ign.cogit.geoToolsFunctions.vectors.Geom;

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
	 * overload of {@link #consolidationDivision(SimpleFeatureCollection, File, double, double, double, double, int, double, int)} for a single road size usage
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
		return consolidationDivision(parcels, tmpFolder, maximalArea, minimalArea, maximalWidth, streetWidth, 999, streetWidth,
				decompositionLevelWithoutStreet);
	}
	
	/**
	 * Method that merges the contiguous marked parcels into zones and then split those zones with a given parcel division algorithm (by default, the Oriented Bounding Box)
	 * 
	 * @param parcels
	 *            The parcels to be merged and cut. Must be marked with the SPLIT filed (see markParcelIntersectMUPOutput for example, with the method concerning MUP-City's output)
	 * @param tmpFolder
	 *            : A temporary folder where will be saved intermediate results
	 * @param maximalArea
	 *            : Area under which a parcel won"t be anymore cut
	 * @param minimalArea
	 *            : Area under which a polygon won't be kept as a parcel
	 * @param maximalWidth
	 *            : The width of parcel connection to street network under which the parcel won"t be anymore cut
	 * @param smallStreetWidth
	 *            : the width of small street network segments
	 * @param largeStreetLevel
	 *            : level of decomposition after which the streets are considered as large streets
	 * @param largeStreetWidth
	 *            : the width of large street network segments
	 * @param decompositionLevelWithoutStreet
	 *            : Number of the final row on which street generation doesn't apply
	 * @return the set of parcel with decomposition
	 * @throws Exception
	 */
	public static SimpleFeatureCollection consolidationDivision(SimpleFeatureCollection parcels, File tmpFolder, double maximalArea,
			double minimalArea, double maximalWidth, double smallStreetWidth, int largeStreetLevel, double largeStreetWidth, int decompositionLevelWithoutStreet) throws Exception {

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
			if (parcel.getAttribute(MarkParcelAttributeFromPosition.getMarkFieldName()).equals(1)) {
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
			mergedParcels.add(sfBuilder.buildFeature(null));
		}
		if (DEBUG) {
			Collec.exportSFC(mergedParcels.collection(), new File(tmpFolder, "step2.shp"));
			System.out.println("done step 2");
		}

		////////////////
		// third step : cuting of the parcels
		////////////////

		SimpleFeatureBuilder sfBuilderFinalParcel = ParcelSchema.getSFBMinParcel();
		DefaultFeatureCollection cutParcels = new DefaultFeatureCollection();

		Arrays.stream(mergedParcels.toArray(new SimpleFeature[0])).forEach(feat -> {
			if (((Geometry) feat.getDefaultGeometry()).getArea() > maximalArea) {
				// Parcel big enough, we cut it
				feat.setAttribute(MarkParcelAttributeFromPosition.getMarkFieldName(), 1);
				try {
					SimpleFeatureCollection freshCutParcel = new DefaultFeatureCollection();
					switch (PROCESS) {
					case "OBB":
						freshCutParcel = ParcelSplit.splitParcels(feat, maximalArea, maximalWidth, 0.0, 0.0, null, smallStreetWidth, largeStreetLevel,
								largeStreetWidth, false, decompositionLevelWithoutStreet, tmpFolder);
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
						cutParcels.add(sfBuilderFinalParcel.buildFeature(null));
					}
					it.close();
				} catch (Exception e) {
					e.printStackTrace();
				}
			} else {
				// parcel not big enough, we directly put it in the collection
				cutParcels.add(sfBuilderFinalParcel.buildFeature(null, new Object[] { feat.getDefaultGeometry() }));
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
			result.add(SFBParcel.buildFeature(null));
		});
		
		if(SAVEINTERMEDIATERESULT) {
			Collec.exportSFC(result, new File(tmpFolder, "parcelConsolidationOnly.shp"), OVERWRITESHAPEFILES);
			OVERWRITESHAPEFILES = false;
		}
		
		// add initial non cut parcel to final parcels 
		Arrays.stream(parcelSaved.toArray(new SimpleFeature[0])).forEach(feat -> {
			SimpleFeatureBuilder SFBParcel = ParcelSchema.setSFBMinParcelWithFeat(feat, schema);
			result.add(SFBParcel.buildFeature(null));
		});
		
		if (DEBUG) {
			Collec.exportSFC(result, new File(tmpFolder, "step4.shp"));
			System.out.println("done step 4");
		}
		return result;
	}

}
