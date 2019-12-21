package fields;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

import org.geotools.data.shapefile.ShapefileDataStore;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.feature.DefaultFeatureCollection;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.locationtech.jts.geom.Geometry;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.NoSuchAuthorityCodeException;

import fr.ign.cogit.GTFunctions.Vectors;
import fr.ign.cogit.parcelFunction.ParcelSchema;
import fr.ign.cogit.parcelFunction.ParcelState;

public class AttributeFromPosition {
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

		final SimpleFeatureType featureSchema = ParcelSchema.getSFBParcelAsASSplit().getFeatureType();
		DefaultFeatureCollection result = new DefaultFeatureCollection();

		Arrays.stream(parcels.toArray(new SimpleFeature[0])).forEach(feat -> {
			SimpleFeatureBuilder featureBuilder = ParcelSchema.setSFBParcelAsASWithFeat(feat, featureSchema);
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
			throws Exception {
		final SimpleFeatureType featureSchema = ParcelSchema.getSFBParcelAsASSplit().getFeatureType();
		DefaultFeatureCollection result = new DefaultFeatureCollection();

		Arrays.stream(parcels.toArray(new SimpleFeature[0])).forEach(feat -> {
			SimpleFeatureBuilder featureBuilder = ParcelSchema.setSFBParcelAsASWithFeat(feat, featureSchema);
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

	public static SimpleFeatureCollection markParcelOfCommunityType(SimpleFeatureCollection parcels, String attribute, File communityFile)
			throws NoSuchAuthorityCodeException, FactoryException {
		return markParcelOfCommunity(parcels, "armature", attribute, communityFile);
	}

	public static SimpleFeatureCollection markParcelOfCommunityNumber(SimpleFeatureCollection parcels, String attribute, File communityFile)
			throws NoSuchAuthorityCodeException, FactoryException {
		return markParcelOfCommunity(parcels, "INSEE", attribute, communityFile);
	}

	public static SimpleFeatureCollection markParcelOfCommunity(SimpleFeatureCollection parcels, String fieldName, String attribute,
			File communityFile) throws NoSuchAuthorityCodeException, FactoryException {
		final SimpleFeatureType featureSchema = ParcelSchema.getSFBParcelAsASSplit().getFeatureType();
		DefaultFeatureCollection result = new DefaultFeatureCollection();
		Arrays.stream(parcels.toArray(new SimpleFeature[0])).forEach(feat -> {
			SimpleFeatureBuilder featureBuilder = ParcelSchema.setSFBParcelAsASWithFeat(feat, featureSchema);
			if (feat.getAttribute(fieldName).equals(attribute)) {
				featureBuilder.set("SPLIT", 1);
			} else {
				featureBuilder.set("SPLIT", 0);
			}
			result.add(featureBuilder.buildFeature(null));
		});
		return result;
	}
}
