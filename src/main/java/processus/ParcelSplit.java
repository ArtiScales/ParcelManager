package processus;

import java.io.File;

import org.geotools.data.DataUtilities;
import org.geotools.data.shapefile.ShapefileDataStore;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.feature.DefaultFeatureCollection;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.referencing.CRS;
import org.locationtech.jts.geom.Geometry;
import org.opengis.feature.simple.SimpleFeature;

import fr.ign.cogit.GTFunctions.Vectors;
import fr.ign.cogit.geoxygene.api.feature.IFeature;
import fr.ign.cogit.geoxygene.api.feature.IFeatureCollection;
import fr.ign.cogit.geoxygene.api.spatial.coordgeom.IPolygon;
import fr.ign.cogit.geoxygene.api.spatial.geomaggr.IMultiCurve;
import fr.ign.cogit.geoxygene.api.spatial.geomprim.IOrientableCurve;
import fr.ign.cogit.geoxygene.convert.FromGeomToSurface;
import fr.ign.cogit.geoxygene.feature.DefaultFeature;
import fr.ign.cogit.geoxygene.feature.FT_FeatureCollection;
import fr.ign.cogit.geoxygene.sig3d.calculation.parcelDecomposition.OBBBlockDecomposition;
import fr.ign.cogit.geoxygene.util.conversion.ShapefileReader;
import fr.ign.cogit.geoxygene.util.conversion.ShapefileWriter;
import fr.ign.cogit.parcelFunction.ParcelSchema;

public class ParcelSplit {

	/**
	 * Splitting parcel processus. It get the usual parcel schema and add the "split" field in order to determine in the parcel will be splited or not. All the parcels are then
	 * split.
	 * 
	 * @param parcelIn
	 *            : collection of parcels
	 * @param tmpFolder
	 *            : a folder to store temporary files
	 * @param maximalArea
	 *            : area of the parcel under which the parcel won't be anymore cut
	 * @param maximalWidth
	 *            : width of the parcel under which the parcel won't be anymore cut
	 * @param epsilon
	 *            :
	 * @param extBlock
	 * @param streetWidth
	 *            : with of the street composing the street network
	 * @param decompositionLevelWithoutStreet
	 *            : number of last iteration row for which no street network is generated
	 * @param forceStreetAccess
	 *            : force the access to the road for each parcel. Not working good yet.
	 * @return a collection of subdivised parcels
	 * @throws Exception
	 */
	public static SimpleFeatureCollection generateSplitAllParcels(SimpleFeature parcelIn, File tmpFolder, double maximalArea, double maximalWidth,
			double epsilon, IMultiCurve<IOrientableCurve> extBlock, double streetWidth, int decompositionLevelWithoutStreet,
			boolean forceStreetAccess) throws Exception {

		// putting the need of splitting into attribute

		SimpleFeatureBuilder sfBuilder = ParcelSchema.getSFBParcelAsASSplit();

		DefaultFeatureCollection toSplit = new DefaultFeatureCollection();

		String numParcelValue = "";
		if (parcelIn.getAttribute("CODE") != null) {
			numParcelValue = parcelIn.getAttribute("CODE").toString();
		} else if (parcelIn.getAttribute("CODE_DEP") != null) {
			numParcelValue = ((String) parcelIn.getAttribute("CODE_DEP")) + (parcelIn.getAttribute("CODE_COM").toString())
					+ (parcelIn.getAttribute("COM_ABS").toString()) + (parcelIn.getAttribute("SECTION").toString());
		} else if (parcelIn.getAttribute("NUMERO") != null) {
			numParcelValue = parcelIn.getAttribute("NUMERO").toString();
		}
		Object[] attr = { numParcelValue, parcelIn.getAttribute("CODE_DEP"), parcelIn.getAttribute("CODE_COM"), parcelIn.getAttribute("COM_ABS"),
				parcelIn.getAttribute("SECTION"), parcelIn.getAttribute("NUMERO"), parcelIn.getAttribute("INSEE"), parcelIn.getAttribute("eval"),
				parcelIn.getAttribute("DoWeSimul"), 1 };

		sfBuilder.add(parcelIn.getDefaultGeometry());
		toSplit.add(sfBuilder.buildFeature(null, attr));

		return splitParcels(toSplit, maximalArea, maximalWidth, epsilon, 0, extBlock, streetWidth, forceStreetAccess, decompositionLevelWithoutStreet,
				tmpFolder);

	}

	/**
	 * Splitting parcel processus. It get the usual parcel schema and add the "split" field in order to determine in the parcel will be splited or not. All the parcels bigger than
	 * the maximal area are split.
	 * 
	 * @param parcelIn
	 *            : collection of parcels
	 * @param tmpFolder
	 *            : a folder to store temporary files
	 * @param maximalArea
	 *            : area of the parcel under which the parcel won't be anymore cut
	 * @param maximalWidth
	 *            : width of the parcel under which the parcel won't be anymore cut
	 * @param epsilon
	 *            :
	 * @param extBlock
	 * @param decompositionLevelWithoutStreet
	 *            : number of last iteration row for which no street network is generated
	 * @param streetWidth
	 *            : with of the street composing the street network
	 * @param forceStreetAccess
	 *            : force the access to the road for each parcel. Not working good yet.
	 * @return a collection of subdivised parcels
	 * @throws Exception
	 */
	public static SimpleFeatureCollection generateSplitParcelsIfBigger(SimpleFeatureCollection parcelsIn, File tmpFolder, double maximalArea,
			double maximalWidth, double epsilon, IMultiCurve<IOrientableCurve> extBlock, int decompositionLevelWithoutStreet, double streetWidth,
			boolean forceStreetAccess) throws Exception {

		///////
		// putting the need of splitting into attribute
		///////

		// create a new collection
		SimpleFeatureBuilder sfBuilder = ParcelSchema.getSFBParcelAsASSplit();
		DefaultFeatureCollection toSplit = new DefaultFeatureCollection();

		// iterate on the parcels
		int i = 0;
		SimpleFeatureIterator parcelIt = parcelsIn.features();
		try {
			while (parcelIt.hasNext()) {
				SimpleFeature feat = parcelIt.next();
				String numParcelValue = "";
				if (feat.getAttribute("CODE") != null) {
					numParcelValue = feat.getAttribute("CODE").toString();
				} else if (feat.getAttribute("CODE_DEP") != null) {
					numParcelValue = ((String) feat.getAttribute("CODE_DEP")) + (feat.getAttribute("CODE_COM").toString())
							+ (feat.getAttribute("COM_ABS").toString()) + (feat.getAttribute("SECTION").toString());
				} else if (feat.getAttribute("NUMERO") != null) {
					numParcelValue = feat.getAttribute("NUMERO").toString();
				}
				Object[] attr = { numParcelValue, feat.getAttribute("CODE_DEP"), feat.getAttribute("CODE_COM"), feat.getAttribute("COM_ABS"),
						feat.getAttribute("SECTION"), feat.getAttribute("NUMERO"), feat.getAttribute("INSEE"), feat.getAttribute("eval"),
						feat.getAttribute("DoWeSimul"), 0 };

				if (((Geometry) feat.getDefaultGeometry()).getArea() > maximalArea) {
					attr[9] = 1;
				}
				sfBuilder.add(feat.getDefaultGeometry());
				toSplit.add(sfBuilder.buildFeature(String.valueOf(i), attr));
				i = i + 1;
			}
		} catch (Exception problem) {
			problem.printStackTrace();
		} finally {
			parcelIt.close();
		}
		return splitParcels(toSplit, maximalArea, maximalWidth, epsilon, 0.0, extBlock, streetWidth, forceStreetAccess,
				decompositionLevelWithoutStreet, tmpFolder);
	}

	/**
	 * Overload to split a single parcel
	 * 
	 * @param toSplit
	 *            : collection of parcels
	 * @param maximalArea
	 *            : area of the parcel under which the parcel won't be anymore cut
	 * @param maximalWidth
	 *            : width of the parcel under which the parcel won't be anymore cut
	 * @param epsilon
	 *            :
	 * @param extBlock
	 * @param streetWidth
	 *            : with of the street composing the street network
	 * @param decompositionLevelWithoutStreet
	 *            : number of last iteration row for which no street network is generated
	 * @param forceStreetAccess
	 *            : force the access to the road for each parcel. Not working good yet.
	 * @param tmpFolder
	 *            : a folder to store temporary files
	 * @param addArg
	 *            : add the parent parcels attributes to the new cuted parcels by re-working them
	 * @return a collection of subdivised parcels
	 * @throws Exception
	 */
	public static SimpleFeatureCollection splitParcels(SimpleFeature toSplit, double maximalArea, double maximalWidth, double streetEpsilon,
			double noise, IMultiCurve<IOrientableCurve> extBlock, double streetWidth, boolean forceStreetAccess, int decompositionLevelWithoutStreet,
			File tmpFolder) throws Exception {
		DefaultFeatureCollection in = new DefaultFeatureCollection();
		in.add(toSplit);
		return splitParcels(in.collection(), maximalArea, maximalWidth, streetEpsilon, noise, extBlock, streetWidth, forceStreetAccess,
				decompositionLevelWithoutStreet, tmpFolder);

	}

	/**
	 * Split the parcels into sub parcels. The parcel that are going to be cut must have a field "SPLIT" with the value of 1. A dirty conversion is made from GeoTools format to
	 * GeOxygene format because the functions that must translate them doesn't work yet.
	 * 
	 * @param toSplit
	 *            : collection of parcels
	 * @param maximalArea
	 *            : area of the parcel under which the parcel won't be anymore cut
	 * @param maximalWidth
	 *            : width of the parcel under which the parcel won't be anymore cut
	 * @param epsilon
	 *            :
	 * @param extBlock
	 * @param streetWidth
	 *            : with of the street composing the street network
	 * @param decompositionLevelWithoutStreet
	 *            : number of last iteration row for which no street network is generated
	 * @param forceStreetAccess
	 *            : force the access to the road for each parcel. Not working good yet.
	 * @param tmpFolder
	 *            : a folder to store temporary files
	 * @param addArg
	 *            : add the parent parcels attributes to the new cuted parcels by re-working them
	 * @return a collection of subdivised parcels
	 * @throws Exception
	 */
	public static SimpleFeatureCollection splitParcels(SimpleFeatureCollection toSplit, double maximalArea, double maximalWidth, double roadEpsilon,
			double noise, IMultiCurve<IOrientableCurve> extBlock, double roadWidth, boolean forceRoadAccess, int decompositionLevelWithoutRoad,
			File tmpFile) throws Exception {

		String attNameToTransform = "SPLIT";
		// TODO work on that conversion
		File shpIn = new File(tmpFile, "temp-In.shp");
		Vectors.exportSFC(toSplit, shpIn);
		IFeatureCollection<?> ifeatColl = ShapefileReader.read(shpIn.toString());

		IFeatureCollection<IFeature> ifeatCollOut = new FT_FeatureCollection<IFeature>();
		for (IFeature feat : ifeatColl) {
			Object o = feat.getAttribute(attNameToTransform);
			// if the parcel is not to be split, we add it on the final result and continue to iterate through the parcels.
			if (o == null || Integer.parseInt(o.toString()) != 1) {
				ifeatCollOut.add(feat);
				continue;
			}
			IPolygon pol = (IPolygon) FromGeomToSurface.convertGeom(feat.getGeom()).get(0);

			int decompositionLevelWithRoad = OBBBlockDecomposition.howManyIt(pol, noise, forceRoadAccess, maximalArea, maximalWidth)
					- decompositionLevelWithoutRoad;
			if (decompositionLevelWithRoad < 0) {
				decompositionLevelWithRoad = 0;
			}
			OBBBlockDecomposition obb = new OBBBlockDecomposition(pol, maximalArea, maximalWidth, roadEpsilon, extBlock, roadWidth, forceRoadAccess,
					decompositionLevelWithRoad);
			try {
				IFeatureCollection<IFeature> featCollDecomp = obb.decompParcel(noise);
				for (IFeature featDecomp : featCollDecomp) {
					IFeature newFeat = new DefaultFeature(featDecomp.getGeom());
					ifeatCollOut.add(newFeat);
				}
			} catch (NullPointerException n) {
				System.out.println("erreur sur le split pour la parcelle " + String.valueOf(feat.getAttribute("CODE")));
				IFeature featTemp = feat.cloneGeom();
				ifeatCollOut.add(featTemp);
			}
		}
		if (ifeatColl.isEmpty()) {
			System.out.println("nothing cuted ");
			return toSplit;
		}
		File fileOut = new File(tmpFile, "tmp_split.shp");
		ShapefileWriter.write(ifeatCollOut, fileOut.toString(), CRS.decode("EPSG:2154"));
		// TODO that's an ugly thing, i thought i could go without it, but apparently it
		// seems like my only option to get it done
		// return GeOxygeneGeoToolsTypes.convert2FeatureCollection(ifeatCollOut,
		// CRS.decode("EPSG:2154"));

		ShapefileDataStore sds = new ShapefileDataStore(fileOut.toURI().toURL());
		SimpleFeatureCollection parcelOut = DataUtilities.collection(sds.getFeatureSource().getFeatures());
		sds.dispose();
		System.out.println("beuda");
		return parcelOut;
	}
}
