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
import fr.ign.cogit.geoxygene.util.attribute.AttributeManager;
import fr.ign.cogit.geoxygene.util.conversion.ShapefileReader;
import fr.ign.cogit.geoxygene.util.conversion.ShapefileWriter;
import fr.ign.cogit.parcelFunction.ParcelSchema;

public class ParcelSplit {

	public static SimpleFeatureCollection generateSplitParcels(SimpleFeature parcelIn, File tmpFile, double maximalArea, double maximalWidth,
			double epsilon, IMultiCurve<IOrientableCurve> extBlock, double roadWidth, int decompositionLevelWithoutRoad, boolean forceRoadAccess)
			throws Exception {

		// putting the need of splitting into attribute

		SimpleFeatureBuilder sfBuilder = ParcelSchema.getParcelSplitSFBuilder();

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

		return splitParcels(toSplit, maximalArea, maximalWidth, epsilon, 0, extBlock, roadWidth, forceRoadAccess, decompositionLevelWithoutRoad,
				tmpFile);

	}

	public static SimpleFeatureCollection generateSplitedParcels(SimpleFeatureCollection parcelsIn, File tmpFile, double maximalArea,
			double maximalWidth, double epsilon, IMultiCurve<IOrientableCurve> extBlock, int decompositionLevelWithoutRoad, double roadWidth,
			boolean forceRoadAccess) throws Exception {

		///////
		// putting the need of splitting into attribute
		///////

		// create a new collection
		SimpleFeatureBuilder sfBuilder = ParcelSchema.getParcelSplitSFBuilder();
		DefaultFeatureCollection toSplit = new DefaultFeatureCollection();

		// iterate to get all the concerned parcels
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
		return splitParcels(toSplit, maximalArea, maximalWidth, epsilon, 0.0, extBlock, roadWidth, forceRoadAccess, decompositionLevelWithoutRoad,
				tmpFile);
	}

	public static SimpleFeatureCollection splitParcels(SimpleFeature toSplit, double maximalArea, double maximalWidth, double roadEpsilon,
			double noise, IMultiCurve<IOrientableCurve> extBlock, double roadWidth, boolean forceRoadAccess, int decompositionLevelWithoutRoad,
			File tmpFile, boolean addArg) throws Exception {
		DefaultFeatureCollection in = new DefaultFeatureCollection();
		in.add(toSplit);
		return splitParcels(in.collection(), maximalArea, maximalWidth, roadEpsilon, noise, extBlock, roadWidth, forceRoadAccess,
				decompositionLevelWithoutRoad, tmpFile, addArg);

	}

	public static SimpleFeatureCollection splitParcels(SimpleFeatureCollection toSplit, double maximalArea, double maximalWidth, double roadEpsilon,
			double noise, IMultiCurve<IOrientableCurve> extBlock, double roadWidth, boolean forceRoadAccess, int decompositionLevelWithoutRoad,
			File tmpFile) throws Exception {

		return splitParcels(toSplit, maximalArea, maximalWidth, roadEpsilon, noise, extBlock, roadWidth, forceRoadAccess,
				decompositionLevelWithoutRoad, tmpFile, true);
	}

	/**
	 * largely inspired from the simPLU. ParcelSplitting class but rewrote to work with geotools SimpleFeatureCollection objects
	 * 
	 * @param toSplit
	 * @param maximalArea
	 * @param maximalWidth
	 * @param roadEpsilon
	 * @param noise
	 * @return
	 * @thro)ws Exception
	 */
	public static SimpleFeatureCollection splitParcels(SimpleFeatureCollection toSplit, double maximalArea, double maximalWidth, double roadEpsilon,
			double noise, IMultiCurve<IOrientableCurve> extBlock, double roadWidth, boolean forceRoadAccess, int decompositionLevelWithoutRoad,
			File tmpFile, boolean addArg) throws Exception {

		String attNameToTransform = "SPLIT";
		// TODO po belle conversion
		File shpIn = new File(tmpFile, "temp-In.shp");
		Vectors.exportSFC(toSplit, shpIn);
		IFeatureCollection<?> ifeatColl = ShapefileReader.read(shpIn.toString());

		IFeatureCollection<IFeature> ifeatCollOut = new FT_FeatureCollection<IFeature>();
		for (IFeature feat : ifeatColl) {
			Object o = feat.getAttribute(attNameToTransform);
			if (o == null) {
				ifeatCollOut.add(feat);
				continue;
			}
			if (Integer.parseInt(o.toString()) != 1) {
				ifeatCollOut.add(feat);
				continue;
			}
			IPolygon pol = (IPolygon) FromGeomToSurface.convertGeom(feat.getGeom()).get(0);

			int numParcelle = 1;
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
					// MAJ du numéro de la parcelle
					IFeature newFeat = new DefaultFeature(featDecomp.getGeom());
					if (addArg) {
						String newCodeDep = (String) feat.getAttribute("CODE_DEP");
						String newCodeCom = (String) feat.getAttribute("CODE_COM");
						String newSection = (String) feat.getAttribute("SECTION");
						String newNumero = String.valueOf(numParcelle++);
						String newCode = newCodeDep + newCodeCom + "000" + newSection + newNumero;
						AttributeManager.addAttribute(newFeat, "CODE", newCode, "String");
						AttributeManager.addAttribute(newFeat, "CODE_DEP", newCodeDep, "String");
						AttributeManager.addAttribute(newFeat, "CODE_COM", newCodeCom, "String");
						AttributeManager.addAttribute(newFeat, "COM_ABS", "000", "String");
						AttributeManager.addAttribute(newFeat, "SECTION", newSection, "String");
						AttributeManager.addAttribute(newFeat, "NUMERO", newNumero, "String");
						AttributeManager.addAttribute(newFeat, "INSEE", newCodeDep + newCodeCom, "String");
						AttributeManager.addAttribute(newFeat, "eval", "0", "String");
						AttributeManager.addAttribute(newFeat, "DoWeSimul", false, "String");
						AttributeManager.addAttribute(newFeat, "IsBuild", feat.getAttribute("IsBuild"), "String");
						AttributeManager.addAttribute(newFeat, "U", feat.getAttribute("U"), "String");
						AttributeManager.addAttribute(newFeat, "AU", feat.getAttribute("AU"), "String");
						AttributeManager.addAttribute(newFeat, "NC", feat.getAttribute("NC"), "String");
					}
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
		return parcelOut;
	}
}
