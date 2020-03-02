package goal;

import java.io.File;
import java.util.Arrays;

import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.feature.DefaultFeatureCollection;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.locationtech.jts.geom.Geometry;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;

import decomposition.ParcelSplit;
import fr.ign.cogit.geoToolsFunctions.vectors.Collec;
import fr.ign.cogit.geoToolsFunctions.vectors.Geom;
import fr.ign.cogit.parcelFunction.ParcelSchema;

public class ParcelConsolidRecomp {
	public static boolean DEBUG = false;
	public static String PROCESS = "OBB";
	public static boolean SAVEINTERMEDIATERESULT = false;

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
	 * @param streetWidth
	 *            : the width of generated street network. this @overload is setting a single size for those roads
	 * @param decompositionLevelWithoutStreet
	 *            : Number of the final row on which street generation doesn't apply
	 * @return the set of parcel with decomposition
	 * @throws Exception
	 */
	public static SimpleFeatureCollection parcelConsolidRecomp(SimpleFeatureCollection parcels, File tmpFolder, double maximalArea,
			double minimalArea, double maximalWidth, double streetWidth, int decompositionLevelWithoutStreet) throws Exception {
		return parcelConsolidRecomp(parcels, tmpFolder, maximalArea, minimalArea, maximalWidth, streetWidth, 999, streetWidth,
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
	public static SimpleFeatureCollection parcelConsolidRecomp(SimpleFeatureCollection parcels, File tmpFolder, double maximalArea,
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
			if (parcel.getAttribute("SPLIT").equals(1)) {
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

		SimpleFeatureBuilder sfBuilder = ParcelSchema.getSFBFrenchParcelSplit();

		Geometry multiGeom = Geom.unionSFC(parcelToMerge);
		for (int i = 0; i < multiGeom.getNumGeometries(); i++) {
			sfBuilder.add(multiGeom.getGeometryN(i));
			sfBuilder.set("SECTION", Integer.toString(i));
			mergedParcels.add(sfBuilder.buildFeature(null));
		}
		if (DEBUG) {
			Collec.exportSFC(mergedParcels.collection(), new File(tmpFolder, "step2.shp"));
			System.out.println("done step 2");
		}

		////////////////
		// third step : cuting of the parcels
		////////////////

		SimpleFeatureBuilder sfBuilderFinalParcel = ParcelSchema.getSFBFrenchParcel();
		DefaultFeatureCollection cutParcels = new DefaultFeatureCollection();

		Arrays.stream(mergedParcels.toArray(new SimpleFeature[0])).forEach(feat -> {
			if (((Geometry) feat.getDefaultGeometry()).getArea() > maximalArea) {
				// Parcel big enough, we cut it
				feat.setAttribute("SPLIT", 1);
				try {
					SimpleFeatureCollection freshCutParcel = new DefaultFeatureCollection();
					switch (PROCESS) {
					case "OBB":
						freshCutParcel = ParcelSplit.splitParcels(feat, maximalArea, maximalWidth, 0.0, 0.0, null ,smallStreetWidth,  largeStreetLevel,
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
					while (it.hasNext()) {
						SimpleFeature freshCut = it.next();
						// that takes time but it's the best way I've found to set a correct section
						// number (to look at the step 2 polygons)
						if (((Geometry) feat.getDefaultGeometry()).getArea() > minimalArea) {
							String sec = "Default";
							SimpleFeatureIterator ilotIt = mergedParcels.features();
							try {
								while (ilotIt.hasNext()) {
									SimpleFeature ilot = ilotIt.next();
									if (((Geometry) ilot.getDefaultGeometry()).intersects((Geometry) freshCut.getDefaultGeometry())) {
										sec = (String) ilot.getAttribute("SECTION");
										break;
									}
								}
							} catch (Exception problem) {
								problem.printStackTrace();
							} finally {
								ilotIt.close();
							}
							String section = "newSection" + sec + "ConsolidRecomp";
							sfBuilderFinalParcel.set("the_geom", freshCut.getDefaultGeometry());
							sfBuilderFinalParcel.set("SECTION", section);
							cutParcels.add(sfBuilderFinalParcel.buildFeature(null));
						}
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
		if (cutParcels.size() == 0) {
			System.out.println("parcelConsolidRecomp produces no cut parcels");
			return result;
		}
		
		// add initial non cut parcel to final parcels only if they are bigger than the limit
		SimpleFeatureType schema = cutParcels.getSchema();
		Arrays.stream(cutParcels.toArray(new SimpleFeature[0])).forEach(feat -> {
			SimpleFeatureBuilder SFBParcel = ParcelSchema.setSFBFrenchParcelWithFeat(feat, schema);
			result.add(SFBParcel.buildFeature(null));
		});
		
		if(SAVEINTERMEDIATERESULT) {
			Collec.exportSFC(result, new File(tmpFolder, "parcelConsolidationOnly.shp"), false);
		}
		
		Arrays.stream(parcelSaved.toArray(new SimpleFeature[0])).forEach(feat -> {
			SimpleFeatureBuilder SFBParcel = ParcelSchema.setSFBFrenchParcelWithFeat(feat, schema);
			result.add(SFBParcel.buildFeature(null));
		});
		if (DEBUG) {
			Collec.exportSFC(result, new File(tmpFolder, "step3.shp"));
			System.out.println("done step 3");
		}
		return result;
	}

}
