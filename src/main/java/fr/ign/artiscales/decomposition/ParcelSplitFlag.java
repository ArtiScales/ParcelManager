package fr.ign.artiscales.decomposition;

import java.io.File;
import java.util.List;

import org.geotools.data.DataUtilities;
import org.geotools.data.shapefile.ShapefileDataStore;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.factory.CommonFactoryFinder;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Polygon;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.filter.FilterFactory2;

import fr.ign.cogit.FeaturePolygonizer;
import fr.ign.cogit.geoToolsFunctions.vectors.Collec;
import fr.ign.artiscales.parcelFunction.MarkParcelAttributeFromPosition;

public class ParcelSplitFlag {
	
	public static void main(String[] args) throws Exception {
		/////////////////////////
		//////// try the generateFlagSplitedParcels method
		/////////////////////////
		// Input 1/ the input parcelles to split
		File inputShapeFile = new File("/tmp/lala.shp");
		// Input 2 : the buildings that mustnt intersects the allowed roads (facultatif)
		File inputBuildingFile = new File("/media/ubuntu/2a3b1227-9bf5-461e-bcae-035a8845f72f/Documents/boulot/theseIGN/PM/PMtest/Ponteau/building.shp");
		// Input 3 (facultative) : the exterior of the urban block (it serves to determiner the multicurve)
		File inputUrbanBlock = new File("/media/ubuntu/2a3b1227-9bf5-461e-bcae-035a8845f72f/Documents/boulot/theseIGN/PM/PMtest/Ponteau/ilot.shp");
		// Input 4 (facultative) : a road shapefile (it can be used to check road access if this is better than characerizing road as an absence of parcel)
		File inputRoad = new File("/media/ubuntu/2a3b1227-9bf5-461e-bcae-035a8845f72f/Documents/boulot/theseIGN/PM/PMtest/ROUTE.SHP");
		
		File tmpFolder = new File("/tmp/");

		FilterFactory2 ff = CommonFactoryFinder.getFilterFactory2();
		ShapefileDataStore sdsIlot = new ShapefileDataStore(inputUrbanBlock.toURI().toURL());
		SimpleFeatureCollection collec = sdsIlot.getFeatureSource().getFeatures();
		ShapefileDataStore sds = new ShapefileDataStore(inputShapeFile.toURI().toURL());
		SimpleFeatureIterator it = sds.getFeatureSource().getFeatures().features();
		try {
			while (it.hasNext()) {
				SimpleFeature feat = it.next();
				List<LineString> lines = Collec.fromSFCtoExteriorRingLines(
						collec.subCollection(ff.bbox(ff.property(feat.getFeatureType().getGeometryDescriptor().getLocalName()), feat.getBounds())));
				if (feat.getAttribute(MarkParcelAttributeFromPosition.getMarkFieldName()) != null
						&& (int) feat.getAttribute(MarkParcelAttributeFromPosition.getMarkFieldName()) == 1) {
					generateFlagSplitedParcels(feat, lines, tmpFolder, inputBuildingFile,inputRoad, 400.0, 15.0, 3.0, true);
				}
			}
		} catch (Exception problem) {
			problem.printStackTrace();
		} finally {
			it.close();
		}
		sds.dispose();
		sdsIlot.dispose();
	}

	public static SimpleFeatureCollection generateFlagSplitedParcels(SimpleFeature feat, List<LineString> extLines, File tmpFolder,
			File buildingFile, Double maximalAreaSplitParcel, Double maximalWidthSplitParcel, Double lenDriveway, boolean isArt3AllowsIsolatedParcel)
			throws Exception {
		return generateFlagSplitedParcels(feat, extLines, tmpFolder, buildingFile, null, maximalAreaSplitParcel, maximalWidthSplitParcel, 
				lenDriveway, isArt3AllowsIsolatedParcel);
	}
	
	public static SimpleFeatureCollection generateFlagSplitedParcels(SimpleFeature feat, List<LineString> extLines, File tmpFolder,
			File buildingFile, File roadFile, Double maximalAreaSplitParcel, Double maximalWidthSplitParcel, Double lenDriveway, boolean isArt3AllowsIsolatedParcel)
			throws Exception {
		ShapefileDataStore buildingDS = new ShapefileDataStore(buildingFile.toURI().toURL());
		List<Polygon> surfaces = Util.getPolygons((Geometry) feat.getDefaultGeometry());
		//as the road shapefile can be left as null, we differ the FlagParcelDecomposition constructor
		FlagParcelDecomposition fpd;
		if (roadFile != null & roadFile.exists()) {
			ShapefileDataStore roadSDS = new ShapefileDataStore(roadFile.toURI().toURL());
			fpd = new FlagParcelDecomposition(surfaces.get(0),
					Collec.snapDatas(buildingDS.getFeatureSource().getFeatures(), ((Geometry) feat.getDefaultGeometry()).buffer(10)),
					Collec.snapDatas(roadSDS.getFeatureSource().getFeatures(), ((Geometry) feat.getDefaultGeometry()).buffer(10)),
					maximalAreaSplitParcel, maximalWidthSplitParcel, lenDriveway, extLines);
			roadSDS.dispose();
		} else {
			fpd = new FlagParcelDecomposition(surfaces.get(0), buildingDS.getFeatureSource().getFeatures(), maximalAreaSplitParcel,
					maximalWidthSplitParcel, lenDriveway, extLines);
		}
		List<Polygon> decomp = fpd.decompParcel(0);
		// if the size of the collection is 1, no flag cut has been done. We check if we can normal cut it, if allowed
		if (decomp.size() == 1 && isArt3AllowsIsolatedParcel) {
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
