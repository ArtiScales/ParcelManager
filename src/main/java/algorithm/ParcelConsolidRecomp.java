package algorithm;

import java.io.File;
import java.util.Arrays;

import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.feature.DefaultFeatureCollection;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.locationtech.jts.geom.Geometry;
import org.opengis.feature.simple.SimpleFeature;

import fr.ign.cogit.GTFunctions.Vectors;
import fr.ign.cogit.parcelFunction.ParcelSchema;
import processus.ParcelSplit;

public class ParcelConsolidRecomp {

	/**
	 * Methods that merge the contiguous indicated zones and the split them with the geoxygene block-subdiviser algorithm
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
	 *            : the width of generated street network
	 * @param decompositionLevelWithoutStreet
	 *            : Number of the final row on which street generation doesn't apply
	 * @return the set of parcel with decomposition
	 * @throws Exception
	 */
	public static SimpleFeatureCollection parcelConsolidRecomp(SimpleFeatureCollection parcels, File tmpFolder, double maximalArea,
			double minimalArea, double maximalWidth, double streetWidth, int decompositionLevelWithoutStreet) throws Exception {

		DefaultFeatureCollection parcelResult = new DefaultFeatureCollection();
		parcelResult.addAll(parcels);

		DefaultFeatureCollection parcelToMerge = new DefaultFeatureCollection();

		Vectors.exportSFC(parcels, new File(tmpFolder, "step0.shp"));
		System.out.println("done step 0");

		////////////////
		// first step : round of selection of the intersected parcels
		////////////////

		Arrays.stream(parcels.toArray(new SimpleFeature[0])).forEach(parcel -> {
			if (parcel.getAttribute("SPLIT").equals(1)) {
				parcelToMerge.add(parcel);
				parcelResult.remove(parcel);
			}
		});

//		Vectors.exportSFC(parcelToMerge.collection(), new File(tmpFolder, "step1.shp"));
		System.out.println("done step 1");

		////////////////
		// second step : merge of the parcel that touches themselves by lil island
		////////////////

		DefaultFeatureCollection mergedParcels = new DefaultFeatureCollection();

		SimpleFeatureBuilder sfBuilder = ParcelSchema.getSFBFrenchParcelSplit();

		Geometry multiGeom = Vectors.unionSFC(parcelToMerge);
		for (int i = 0; i < multiGeom.getNumGeometries(); i++) {
			sfBuilder.add(multiGeom.getGeometryN(i));
			sfBuilder.set("SECTION", Integer.toString(i));
			mergedParcels.add(sfBuilder.buildFeature(null));
		}

//		Vectors.exportSFC(mergedParcels.collection(), new File(tmpFolder, "step2.shp"));
		System.out.println("done step 2");

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
					SimpleFeatureCollection freshCutParcel = ParcelSplit.splitParcels(feat, maximalArea, maximalWidth, 0, 0, null, streetWidth, false,
							decompositionLevelWithoutStreet, tmpFolder);
					SimpleFeatureIterator it = freshCutParcel.features();
					// every single parcel goes into new collection
					while (it.hasNext()) {
						SimpleFeature f = it.next();
						// that takes time but it's the best way I've found to set a correct section
						// number (to look at the step 2 polygons)
						if (((Geometry) feat.getDefaultGeometry()).getArea() > minimalArea) {
							String sec = "Default";
							SimpleFeatureIterator ilotIt = mergedParcels.features();
							try {
								while (ilotIt.hasNext()) {
									SimpleFeature ilot = ilotIt.next();
									if (((Geometry) ilot.getDefaultGeometry()).intersects((Geometry) f.getDefaultGeometry())) {
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
							sfBuilderFinalParcel.set("the_geom", f.getDefaultGeometry());
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

		// add initial non cut parcel to final parcels only if they are bigger than the limit
		Arrays.stream(parcelResult.toArray(new SimpleFeature[0])).forEach(feat -> {
			SimpleFeatureBuilder SFBParcel = ParcelSchema.setSFBParcelAsASWithFeat(feat);
			cutParcels.add(SFBParcel.buildFeature(null));
		});

		SimpleFeatureCollection result = Vectors.delTinyParcels(cutParcels, 5.0);

		Vectors.exportSFC(result, new File(tmpFolder, "step3.shp"));

		System.out.println("done step 3");

		return result;
	}


}
