package algorithm;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

import org.geotools.data.shapefile.ShapefileDataStore;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.feature.DefaultFeatureCollection;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.geotools.referencing.CRS;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Polygon;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

import fr.ign.cogit.GTFunctions.Vectors;
import fr.ign.cogit.parcelFunction.ParcelSchema;
import fr.ign.cogit.parcelFunction.ParcelState;
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

		Vectors.exportSFC(parcelToMerge.collection(), new File(tmpFolder, "step1.shp"));
		System.out.println("done step 1");

		////////////////
		// second step : merge of the parcel that touches themselves by lil island
		////////////////

		DefaultFeatureCollection mergedParcels = new DefaultFeatureCollection();
		SimpleFeatureTypeBuilder sfTypeBuilder = new SimpleFeatureTypeBuilder();

		CoordinateReferenceSystem sourceCRS = CRS.decode("EPSG:2154");

		sfTypeBuilder.setName("toSplit");
		sfTypeBuilder.setCRS(sourceCRS);
		sfTypeBuilder.add("the_geom", Polygon.class);
		sfTypeBuilder.add("SPLIT", Integer.class);
		sfTypeBuilder.add("Section", Integer.class);
		sfTypeBuilder.setDefaultGeometry("the_geom");

		SimpleFeatureBuilder sfBuilder = new SimpleFeatureBuilder(sfTypeBuilder.buildFeatureType());

		Geometry multiGeom = Vectors.unionSFC(parcelToMerge);
		for (int i = 0; i < multiGeom.getNumGeometries(); i++) {
			sfBuilder.add(multiGeom.getGeometryN(i));
			sfBuilder.set("Section", i);
			mergedParcels.add(sfBuilder.buildFeature(null));
		}

		Vectors.exportSFC(mergedParcels.collection(), new File(tmpFolder, "step2.shp"));
		System.out.println("done step 2");

		////////////////
		// third step : cuting of the parcels
		////////////////

		SimpleFeatureBuilder sfBuilderFinalParcel = ParcelSchema.getParcelSFBuilder();
		DefaultFeatureCollection cutParcels = new DefaultFeatureCollection();

		Arrays.stream(mergedParcels.toArray(new SimpleFeature[0])).forEach(feat -> {
			if (((Geometry) feat.getDefaultGeometry()).getArea() > maximalArea) {
				// Parcel big enough, we cut it
				feat.setAttribute("SPLIT", 1);
				try {
					SimpleFeatureCollection freshCutParcel = ParcelSplit.splitParcels(feat, maximalArea, maximalWidth, 0, 0, null, streetWidth, false,
							decompositionLevelWithoutStreet, tmpFolder, false);
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
										sec = String.valueOf(ilot.getAttribute("Section"));
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
			SimpleFeatureBuilder SFBParcel = ParcelSchema.setSFBParcelWithFeat(feat);
			cutParcels.add(SFBParcel.buildFeature(null));
		});

		SimpleFeatureCollection result = Vectors.delTinyParcels(cutParcels, 10.0);

		Vectors.exportSFC(result, new File(tmpFolder, "step3.shp"));

		System.out.println("done step 3");

		return result;
	}



	/**
	 * mark parcels that intersects mupCity's output on the "SPLIT" field.
	 * 
	 * @param parcels
	 *            : The collection of parcels to mark
	 * @param mupOutputFile
	 *            : A shapefile containing outputs of MUP-City
	 * @return
	 * @throws IOException
	 * @throws Exception
	 */
	public static SimpleFeatureCollection markParcelIntersectMUPOutput(SimpleFeatureCollection parcels, File mUPOutputFile)
			throws IOException, Exception {

		ShapefileDataStore sds = new ShapefileDataStore(mUPOutputFile.toURI().toURL());
		Geometry sfcMUP = Vectors.unionSFC(Vectors.snapDatas(sds.getFeatureSource().getFeatures(), parcels));

		final SimpleFeatureType featureSchema = ParcelSchema.getParcelSplitSFBuilder().getFeatureType();
		DefaultFeatureCollection result = new DefaultFeatureCollection();

		Arrays.stream(parcels.toArray(new SimpleFeature[0])).forEach(feat -> {
			SimpleFeatureBuilder featureBuilder = ParcelSchema.setSFBParcelWithFeatAsAS(feat, featureSchema);
			if (((Geometry) feat.getDefaultGeometry()).intersects(sfcMUP)) {
				featureBuilder.set("SPLIT", 1);
			} else {
				featureBuilder.set("SPLIT", 0);
			}
			result.add(featureBuilder.buildFeature(null));
		});
		sds.dispose();
		return result.collection();
	}
	/// **
	// * To do !!
	// * @param parcels
	// * @param minimalParcelSize
	// * @return
	// */
	// public static SimpleFeatureCollection mergeTooSmallParcels(SimpleFeatureCollection parcels, int minimalParcelSize) {
	// final SimpleFeatureType featureSchema = ParcelSchema.getParcelSFBuilder().getFeatureType();
	// DefaultFeatureCollection result = new DefaultFeatureCollection();
	//// List<SimpleFeature> smallParcels = Arrays.stream(parcels.toArray(new SimpleFeature[0])).filter(feat ->
	/// ((Geometry)feat.getDefaultGeometry()).getArea()<minimalParcelSize).collect(Collector(ToList()));
	// //TODO not implemented yet
	// return null;
	// }

	/**
	 * mark parcels that intersects a certain type of zoning.
	 * 
	 * @param parcels
	 * @param zoningType
	 *            : The big kind of the zoning (either not constructible (NC), urbanizable (U) or to be urbanize (TBU). Other keywords can be tolerate
	 * @param zoningFile
	 *            : A shapefile containing the zoning plan
	 * @return The same collection of parcels with the SPLIT field
	 * @throws IOException
	 * @throws Exception
	 */
	public static SimpleFeatureCollection markParcelIntersectZoningType(SimpleFeatureCollection parcels, String zoningType, File zoningFile)
			throws IOException, Exception {
		final SimpleFeatureType featureSchema = ParcelSchema.getParcelSplitSFBuilder().getFeatureType();
		DefaultFeatureCollection result = new DefaultFeatureCollection();

		Arrays.stream(parcels.toArray(new SimpleFeature[0])).forEach(feat -> {
			SimpleFeatureBuilder featureBuilder = ParcelSchema.setSFBParcelWithFeatAsAS(feat, featureSchema);
			try {
				if (ParcelState.parcelInBigZone(zoningFile, feat).equals(zoningType)
						&& (feat.getFeatureType().getDescriptor("SPLIT") == null || feat.getAttribute("SPLIT").equals(1))) {
					featureBuilder.set("SPLIT", 1);
				} else {
					featureBuilder.set("SPLIT", 0);
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
			result.add(featureBuilder.buildFeature(null));
		});
		return result;
	}
}
