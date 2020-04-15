package fr.ign.artiscales.decomposition;

import java.io.File;
import java.util.List;

import org.geotools.data.DataUtilities;
import org.geotools.data.shapefile.ShapefileDataStore;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Polygon;
import org.opengis.feature.simple.SimpleFeature;

import fr.ign.cogit.FeaturePolygonizer;
import fr.ign.cogit.geoToolsFunctions.vectors.Collec;
import fr.ign.artiscales.decomposition.FlagParcelDecomposition;

public class ParcelSplitFlag {
	
//	public static void main(String[] args) throws Exception {
//		/////////////////////////
//		//////// try the generateFlagSplitedParcels method
//		/////////////////////////
//		File rootFolder = new File("/home/ubuntu/PMtest/Densification/");
//		
//		// Input 1/ the input parcelles to split
//		File inputShapeFile = new File("/tmp/marked.shp");
//		// Input 2 : the buildings that mustnt intersects the allowed roads (facultatif)
//		File inputBuildingFile = new File(rootFolder, "building.shp");
//		// Input 3 (facultative) : the exterior of the urban block (it serves to determiner the multicurve)
//		File inputUrbanBlock = new File(rootFolder, "islet.shp");
//		// Input 4 (facultative) : a road shapefile (it can be used to check road access if this is better than characerizing road as an absence of parcel)
//		File inputRoad = new File(rootFolder, "road.shp");
//		
//		File tmpFolder = new File("/tmp/");
//
//		FilterFactory2 ff = CommonFactoryFinder.getFilterFactory2();
//		ShapefileDataStore sdsIlot = new ShapefileDataStore(inputUrbanBlock.toURI().toURL());
//		SimpleFeatureCollection collec = sdsIlot.getFeatureSource().getFeatures();
//		ShapefileDataStore sds = new ShapefileDataStore(inputShapeFile.toURI().toURL());
//		try (SimpleFeatureIterator it = sds.getFeatureSource().getFeatures().features()){
//			while (it.hasNext()) {
//				SimpleFeature feat = it.next();
//				List<LineString> lines = Collec.fromSFCtoListRingLines(
//						collec.subCollection(ff.bbox(ff.property(feat.getFeatureType().getGeometryDescriptor().getLocalName()), feat.getBounds())));
//				if (feat.getAttribute(MarkParcelAttributeFromPosition.getMarkFieldName()) != null
//						&& (int) feat.getAttribute(MarkParcelAttributeFromPosition.getMarkFieldName()) == 1) {
//					generateFlagSplitedParcels(feat, lines, tmpFolder, inputBuildingFile, inputRoad, 400.0, 15.0, 3.0, false, null);
//				}
//			}
//		} catch (Exception problem) {
//			problem.printStackTrace();
//		} 
//		sds.dispose();
//		sdsIlot.dispose();
//	}

	public static SimpleFeatureCollection generateFlagSplitedParcels(SimpleFeature feat, List<LineString> extLines, File tmpFolder,
			File buildingFile, Double maximalAreaSplitParcel, Double maximalWidthSplitParcel, Double lenDriveway, boolean allowIsolatedParcel)
			throws Exception {
		return generateFlagSplitedParcels(feat, extLines, tmpFolder, buildingFile, null, maximalAreaSplitParcel, maximalWidthSplitParcel, 
				lenDriveway, allowIsolatedParcel, null);
	}
	
	public static SimpleFeatureCollection generateFlagSplitedParcels(SimpleFeature feat, List<LineString> extLines, File tmpFolder, File buildingFile,
			File roadFile, Double maximalAreaSplitParcel, Double maximalWidthSplitParcel, Double lenDriveway, boolean allowIsolatedParcel,
			Geometry exclusionZone) throws Exception {
		ShapefileDataStore buildingDS = new ShapefileDataStore(buildingFile.toURI().toURL());
		List<Polygon> surfaces = Util.getPolygons((Geometry) feat.getDefaultGeometry());
		// as the road shapefile can be left as null, we differ the FlagParcelDecomposition constructor
		FlagParcelDecomposition fpd;
		if (roadFile != null && roadFile.exists()) {
			ShapefileDataStore roadSDS = new ShapefileDataStore(roadFile.toURI().toURL());
			Geometry geom = ((Geometry) feat.getDefaultGeometry()).buffer(10);
			fpd = new FlagParcelDecomposition(surfaces.get(0),
					Collec.snapDatas(buildingDS.getFeatureSource().getFeatures(), geom),
					Collec.snapDatas(roadSDS.getFeatureSource().getFeatures(), geom), 
					maximalAreaSplitParcel, maximalWidthSplitParcel, lenDriveway, extLines, exclusionZone);
			roadSDS.dispose();
		} else {
			fpd = new FlagParcelDecomposition(surfaces.get(0),
					Collec.snapDatas(buildingDS.getFeatureSource().getFeatures(), ((Geometry) feat.getDefaultGeometry()).buffer(10)),
					maximalAreaSplitParcel, maximalWidthSplitParcel, lenDriveway, extLines, exclusionZone);
		}
		List<Polygon> decomp = fpd.decompParcel(0);
		// if the size of the collection is 1, no flag cut has been done. We check if we can normal cut it, if allowed
		if (decomp.size() == 1 && allowIsolatedParcel) {
			System.out.println("normal decomp instead of flagg decomp allowed and done");
			return ParcelSplit.splitParcels(feat, maximalAreaSplitParcel, maximalWidthSplitParcel, 0, 0, extLines, 0, false, 8, tmpFolder);
		}
		File fileOut = new File(tmpFolder, "tmp_split.shp");
		FeaturePolygonizer.saveGeometries(decomp, fileOut, "Polygon");
		ShapefileDataStore sds = new ShapefileDataStore(fileOut.toURI().toURL());
		SimpleFeatureCollection parcelOut = DataUtilities.collection(sds.getFeatureSource().getFeatures());
		sds.dispose();
		buildingDS.dispose();
		return parcelOut;
	}
}
