package algorithm;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.geotools.data.shapefile.ShapefileDataStore;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.referencing.CRS;

import fr.ign.cogit.GTFunctions.Vectors;
import fr.ign.cogit.geoxygene.api.feature.IFeature;
import fr.ign.cogit.geoxygene.api.feature.IFeatureCollection;
import fr.ign.cogit.geoxygene.api.spatial.geomaggr.IMultiCurve;
import fr.ign.cogit.geoxygene.api.spatial.geomprim.IOrientableCurve;
import fr.ign.cogit.geoxygene.convert.FromGeomToLineString;
import fr.ign.cogit.geoxygene.feature.FT_FeatureCollection;
import fr.ign.cogit.geoxygene.spatial.geomaggr.GM_MultiCurve;
import fr.ign.cogit.geoxygene.util.attribute.AttributeManager;
import fr.ign.cogit.geoxygene.util.conversion.ShapefileReader;
import fr.ign.cogit.geoxygene.util.conversion.ShapefileWriter;
import processus.ParcelSplitFlag;

public class ParcelDensification {

	// /////////////////////////
	// //////// try the parcelDensification method
	// /////////////////////////
	//
	// ShapefileDataStore shpDSZone = new ShapefileDataStore(
	// new
	// File("/home/mcolomb/informatique/ArtiScales/ParcelSelectionFile/exScenar/variant0/parcelGenExport.shp").toURI().toURL());
	// SimpleFeatureCollection featuresZones =
	// shpDSZone.getFeatureSource().getFeatures();
	//
	// // Vectors.exportSFC(generateSplitedParcels(waiting, tmpFile, p), new
	// // File("/tmp/tmp2.shp"));
	// SimpleFeatureCollection salut = parcelDensification("U", featuresZones,
	// tmpFile, new File("/home/mcolomb/informatique/ArtiScales"), new File(
	// "/home/mcolomb/informatique/ArtiScales/MupCityDepot/exScenar/variant0/exScenar-DataSys-CM20.0-S0.0-GP_915948.0_6677337.0--N6_Ba_ahpx_seed_42-evalAnal-20.0.shp"),
	// 800.0, 15.0, 5.0);
	//
	// Vectors.exportSFC(salut, new File("/tmp/parcelDensification.shp"));
	// shpDSZone.dispose();
	//

	/**
	 * Apply the densification process
	 * 
	 * @param splitZone
	 * @param parcelCollection
	 * @param tmpFile
	 * @param rootFile
	 * @param p
	 * @return
	 * @throws Exception
	 */
	public static SimpleFeatureCollection parcelDensification(String splitZone, SimpleFeatureCollection parcelCollection,
			SimpleFeatureCollection ilotCollection, File tmpFile, File zoningFile, File regulFile, File mupFile, double maximalAreaSplitParcel,
			Double maximalWidthSplitParcel, Double lenDriveway) throws Exception {

		File pivotFile = new File(tmpFile, "parcelsInbfFlaged.shp");
		Vectors.exportSFC(parcelCollection, pivotFile);
		IFeatureCollection<IFeature> parcelCollec = ShapefileReader.read(pivotFile.getAbsolutePath());

		// the little islands (ilots)
		File ilotReduced = new File(tmpFile, "ilotReduced.shp");
		Vectors.exportSFC(ilotCollection, ilotReduced);
		IFeatureCollection<IFeature> featC = ShapefileReader.read(ilotReduced.getAbsolutePath());

		List<IOrientableCurve> lOC = featC.select(parcelCollec.envelope()).parallelStream().map(x -> FromGeomToLineString.convert(x.getGeom()))
				.collect(ArrayList::new, List::addAll, List::addAll);
		IMultiCurve<IOrientableCurve> iMultiCurve = new GM_MultiCurve<>(lOC);

		IFeatureCollection<IFeature> cutedAll = new FT_FeatureCollection<>();
		for (IFeature feat : parcelCollec) {
			// if the parcel is selected for the simulation
			if (feat.getAttribute("DoWeSimul").equals("true") && ((boolean) feat.getAttribute(splitZone))) {
				// if the parcel is bigger than the limit size
				if (feat.getGeom().area() > maximalAreaSplitParcel) {
					// we falg cut the parcel
					IFeatureCollection<IFeature> tmp = ParcelSplitFlag.generateFlagSplitedParcels(feat, iMultiCurve, tmpFile, zoningFile, regulFile,
							mupFile, maximalAreaSplitParcel, maximalWidthSplitParcel, lenDriveway);
					cutedAll.addAll(tmp);
				} else {
					if ((boolean) feat.getAttribute("IsBuild")) {
						AttributeManager.addAttribute(feat, "DoWeSimul", "false", "String");
						AttributeManager.addAttribute(feat, "eval", "0.0", "String");
					}
					cutedAll.add(feat);
				}
			}
			// if no simulation needed, we ad the normal parcel
			else {
				cutedAll.add(feat);
			}
		}

		File fileTmp = new File(tmpFile, "tmpFlagSplit.shp");
		ShapefileWriter.write(cutedAll, fileTmp.toString(), CRS.decode("EPSG:2154"));

		// TODO that's an ugly thing, i thought i could go without it, but apparently it
		// seems like my only option to get it done
		// return GeOxygeneGeoToolsTypes.convert2FeatureCollection(ifeatCollOut,
		// CRS.decode("EPSG:2154"));

		ShapefileDataStore sds = new ShapefileDataStore(fileTmp.toURI().toURL());
		SimpleFeatureCollection parcelFlaged = sds.getFeatureSource().getFeatures();
		sds.dispose();

		return parcelFlaged;
	}

}
