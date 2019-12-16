package test;

import java.io.File;

import org.geotools.data.shapefile.ShapefileDataStore;
import org.geotools.data.simple.SimpleFeatureCollection;

public class ConversionTest {
	public static void main(String[] args) throws Exception {

		File f = new File("/tmp/parcel.shp");
		
		ShapefileDataStore shpDSParcel = new ShapefileDataStore(f.toURI().toURL());
		SimpleFeatureCollection parcel = shpDSParcel.getFeatureSource().getFeatures();
//		IFeatureCollection<?> IFeatResult = GeOxygeneGeoToolsTypes.convert2IFeatureCollection(parcel);
//		ShapefileWriter.write(IFeatResult, "/tmp/Ifeat.shp", CRS.decode("EPSG:2154"));
//	
//		
//		IFeatureCollection<?> ifeatColl = ShapefileReader.read(f.toString());
//SimpleFeatureCollection parcelSFC = GeOxygeneGeoToolsTypes.convert2FeatureCollection(ifeatColl, CRS.decode("EPSG:2154"));
//		Vectors.exportSFC(parcelSFC, new File("/tmp/parcelSFC"));

	}
}
