package processus;

import java.io.File;
import java.util.List;

import org.geotools.data.DataUtilities;
import org.geotools.data.shapefile.ShapefileDataStore;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.referencing.CRS;

import fr.ign.cogit.geoxygene.api.feature.IFeature;
import fr.ign.cogit.geoxygene.api.feature.IFeatureCollection;
import fr.ign.cogit.geoxygene.api.spatial.coordgeom.IDirectPosition;
import fr.ign.cogit.geoxygene.api.spatial.coordgeom.IPolygon;
import fr.ign.cogit.geoxygene.api.spatial.geomaggr.IMultiCurve;
import fr.ign.cogit.geoxygene.api.spatial.geomprim.IOrientableCurve;
import fr.ign.cogit.geoxygene.api.spatial.geomprim.IOrientableSurface;
import fr.ign.cogit.geoxygene.api.spatial.geomroot.IGeometry;
import fr.ign.cogit.geoxygene.convert.FromGeomToSurface;
import fr.ign.cogit.geoxygene.feature.FT_FeatureCollection;
import fr.ign.cogit.geoxygene.sig3d.calculation.parcelDecomposition.FlagParcelDecomposition;
import fr.ign.cogit.geoxygene.spatial.coordgeom.DirectPosition;
import fr.ign.cogit.geoxygene.util.conversion.GeOxygeneGeoToolsTypes;
import fr.ign.cogit.geoxygene.util.conversion.ShapefileReader;
import fr.ign.cogit.geoxygene.util.conversion.ShapefileWriter;

public class ParcelSplitFlag {

	// /////////////////////////
	// //////// try the generateFlagSplitedParcels method
	// /////////////////////////
	//
	// File geoFile = new File(, "dataGeo");
	// IFeatureCollection<IFeature> featColl =
	// ShapefileReader.read("/tmp/tmp1.shp");
	//
	// String inputUrbanBlock = GetFromGeom.getIlots(geoFile).getAbsolutePath();
	//
	// IFeatureCollection<IFeature> featC = ShapefileReader.read(inputUrbanBlock);
	// List<IOrientableCurve> lOC =
	// featC.select(featColl.envelope()).parallelStream().map(x ->
	// FromGeomToLineString.convert(x.getGeom())).collect(ArrayList::new,
	// List::addAll,
	// List::addAll);
	//
	// IMultiCurve<IOrientableCurve> iMultiCurve = new GM_MultiCurve<>(lOC);
	//
	// // ShapefileDataStore shpDSZone = new ShapefileDataStore(
	// // new
	// File("/home/mcolomb/informatique/ArtiScales/ParcelSelectionFile/exScenar/variant0/parcelGenExport.shp").toURI().toURL());
	// //
	// // SimpleFeatureCollection featuresZones =
	// shpDSZone.getFeatureSource().getFeatures();
	// // SimpleFeatureIterator it = featuresZones.features();
	// // SimpleFeature waiting = null;
	// // while (it.hasNext()) {
	// // SimpleFeature feat = it.next();
	// // if (((String) feat.getAttribute("CODE")).equals("25598000AB0446") ) {
	// // waiting = feat;
	// // }
	// // }
	//
	// // Vectors.exportSFC(generateSplitedParcels(waiting, tmpFile, p), new
	// // File("/tmp/tmp2.shp"));
	// SimpleFeatureCollection salut = generateFlagSplitedParcels(featColl.get(0),
	// iMultiCurve, geoFile, tmpFile, 2000.0, 15.0, 3.0);
	//
	// Vectors.exportSFC(salut, new File("/tmp/tmp2.shp"));
	//
	// }

	// public static SimpleFeatureCollection
	// generateFlagSplitedParcels(SimpleFeatureCollection featColl,
	// IMultiCurve<IOrientableCurve> iMultiCurve, File geoFile, File tmpFile,
	// Parameters p) throws NoSuchAuthorityCodeException, FactoryException,
	// Exception {
	//
	// DefaultFeatureCollection collec = new DefaultFeatureCollection();
	// SimpleFeatureIterator it = featColl.features();
	//
	// try {
	// while (it.hasNext()) {
	// SimpleFeature feat = it.next();
	// collec.addAll(generateFlagSplitedParcels(feat, iMultiCurve, geoFile, tmpFile,
	// p));
	// }
	// } catch (Exception problem) {
	// problem.printStackTrace();
	// } finally {
	// it.close();
	// }
	//
	// return collec;
	// }
	//
	// public static SimpleFeatureCollection
	// generateFlagSplitedParcels(SimpleFeature feat, IMultiCurve<IOrientableCurve>
	// iMultiCurve, File geoFile, File tmpFile, Parameters p)
	// throws Exception {
	// return generateFlagSplitedParcels(feat, iMultiCurve, geoFile, tmpFile,
	// p.getDouble("maximalAreaSplitParcel"),
	// p.getDouble("maximalWidthSplitParcel"),
	// p.getDouble("lenDriveway"));
	// }
	//
	// public static SimpleFeatureCollection
	// generateFlagSplitedParcels(SimpleFeature feat, IMultiCurve<IOrientableCurve>
	// iMultiCurve, File geoFile, File tmpFile,
	// Double maximalAreaSplitParcel, Double maximalWidthSplitParcel, Double
	// lenDriveway) throws Exception {
	//
	// return
	// generateFlagSplitedParcels(GeOxygeneGeoToolsTypes.convert2IFeature(feat),
	// iMultiCurve, geoFile, tmpFile, maximalAreaSplitParcel,
	// maximalWidthSplitParcel,
	// lenDriveway);
	//
	// }

	public static SimpleFeatureCollection generateFlagSplitedParcels(IFeature ifeat, IMultiCurve<IOrientableCurve> iMultiCurve, File tmpFile,
			File buildingFile, Double maximalAreaSplitParcel, Double maximalWidthSplitParcel, Double lenDriveway, boolean isArt3AllowsIsolatedParcel)
			throws Exception {
		DirectPosition.PRECISION = 3;
		IFeatureCollection<IFeature> buildingLargeCollec = ShapefileReader.read(buildingFile.getAbsolutePath());
		IFeatureCollection<IFeature> buildingCollec = new FT_FeatureCollection<>();
		buildingCollec.addAll(buildingLargeCollec.select(ifeat.getGeom().buffer(10.0)));

		IGeometry geom = ifeat.getGeom();

		// what would that be for?
		IDirectPosition dp = new DirectPosition(0, 0, 0); // geom.centroid();
		geom = geom.translate(-dp.getX(), -dp.getY(), 0);

		List<IOrientableSurface> surfaces = FromGeomToSurface.convertGeom(geom);
		FlagParcelDecomposition fpd = new FlagParcelDecomposition((IPolygon) surfaces.get(0), buildingCollec, maximalAreaSplitParcel,
				maximalWidthSplitParcel, lenDriveway, iMultiCurve);
		IFeatureCollection<IFeature> decomp = fpd.decompParcel(0);

		// if the size of the collection is 1, no flag cut has been done. We check if we can normal cut it, if allowed
		if (decomp.size() == 1 && isArt3AllowsIsolatedParcel) {
			System.out.println("normal decomp instead of flagg decomp allowed and done");
			return ParcelSplit.splitParcels(GeOxygeneGeoToolsTypes.convert2SimpleFeature(ifeat, CRS.decode("EPSG:2154")), maximalAreaSplitParcel,
					maximalWidthSplitParcel, 0, 0, iMultiCurve, 0, false, 8, tmpFile, false);
		}
		
		IFeatureCollection<IFeature> ifeatCollOut = new FT_FeatureCollection<>();
		ifeatCollOut.addAll(decomp);
		// dirty translation from geox to geotools TODO clean that one day
		File fileOut = new File(tmpFile, "tmp_split.shp");
		ShapefileWriter.write(ifeatCollOut, fileOut.toString(), CRS.decode("EPSG:2154"));

		ShapefileDataStore sds = new ShapefileDataStore(fileOut.toURI().toURL());
		SimpleFeatureCollection parcelOut = DataUtilities.collection(sds.getFeatureSource().getFeatures());
		sds.dispose();
		return parcelOut;

	}
}
