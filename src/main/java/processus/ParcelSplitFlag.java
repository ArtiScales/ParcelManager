package processus;

import java.io.File;
import java.util.List;

import org.geotools.data.DataUtilities;
import org.geotools.data.shapefile.ShapefileDataStore;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Polygon;
import org.opengis.feature.simple.SimpleFeature;

import decomposition.FlagParcelDecomposition;
import fr.ign.cogit.FeaturePolygonizer;

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

	public static SimpleFeatureCollection generateFlagSplitedParcels(SimpleFeature ifeat, List<LineString> iMultiCurve, File tmpFile,
			File buildingFile, Double maximalAreaSplitParcel, Double maximalWidthSplitParcel, Double lenDriveway, boolean isArt3AllowsIsolatedParcel)
			throws Exception {
//		DirectPosition.PRECISION = 3;
//		SimpleFeatureCollection buildingLargeCollec = ShapefileReader.read(buildingFile.getAbsolutePath());
//		IFeatureCollection<IFeature> buildingCollec = new FT_FeatureCollection<>();
//		buildingCollec.addAll(buildingLargeCollec.select(ifeat.getGeom().buffer(10.0)));
    ShapefileDataStore buildingDS = new ShapefileDataStore(buildingFile.toURI().toURL());
    SimpleFeatureCollection buildingCollec = buildingDS.getFeatureSource().getFeatures();

		Geometry geom = (Geometry) ifeat.getDefaultGeometry();

		// what would that be for?
//		IDirectPosition dp = new DirectPosition(0, 0, 0); // geom.centroid();
//		geom = geom.translate(-dp.getX(), -dp.getY(), 0);

		List<Polygon> surfaces = decomposition.FlagParcelDecomposition.getPolygons(geom);
//		List<IOrientableSurface> surfaces = FromGeomToSurface.convertGeom(geom);
		FlagParcelDecomposition fpd = new FlagParcelDecomposition(surfaces.get(0), buildingCollec, maximalAreaSplitParcel,
				maximalWidthSplitParcel, lenDriveway, iMultiCurve);
		List<Polygon> decomp = fpd.decompParcel(0);

		// if the size of the collection is 1, no flag cut has been done. We check if we can normal cut it, if allowed
		if (decomp.size() == 1 && isArt3AllowsIsolatedParcel) {
			System.out.println("normal decomp instead of flagg decomp allowed and done");
			return ParcelSplit.splitParcels(ifeat, maximalAreaSplitParcel, maximalWidthSplitParcel, 0, 0, iMultiCurve, 0, false, 8, tmpFile);
		}
		
//		IFeatureCollection<IFeature> ifeatCollOut = new FT_FeatureCollection<>();
//		ifeatCollOut.addAll(decomp);
		// dirty translation from geox to geotools TODO clean that one day
		File fileOut = new File(tmpFile, "tmp_split.shp");
//		ShapefileWriter.write(ifeatCollOut, fileOut.toString(), CRS.decode("EPSG:2154"));
    FeaturePolygonizer.saveGeometries(decomp, fileOut, "Polygon");

		ShapefileDataStore sds = new ShapefileDataStore(fileOut.toURI().toURL());
		SimpleFeatureCollection parcelOut = DataUtilities.collection(sds.getFeatureSource().getFeatures());
		sds.dispose();
		return parcelOut;
	}
}
