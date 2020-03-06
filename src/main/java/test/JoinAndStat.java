package test;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.List;

import org.geotools.data.shapefile.ShapefileDataStore;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.feature.DefaultFeatureCollection;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.geotools.referencing.CRS;
import org.locationtech.jts.geom.Polygon;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.NoSuchAuthorityCodeException;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

import com.opencsv.CSVReader;

import fr.ign.cogit.geoToolsFunctions.Attribute;
import fr.ign.cogit.geoToolsFunctions.vectors.Collec;

public class JoinAndStat {

	public static void main(String[] args) throws IOException, NoSuchAuthorityCodeException, FactoryException {
		ShapefileDataStore sds = new ShapefileDataStore(new File("/home/ubuntu/Documents/nikoTheseCarto/bdR.shp").toURI().toURL());
		File csvFile = new File("/home/ubuntu/Documents/nikoTheseCarto/tableur.csv");
		CSVReader reader = new CSVReader(new FileReader(csvFile));
		String[] firstline = reader.readNext();
		int cpIndice = Attribute.getIndice(firstline, "cp");
		DefaultFeatureCollection result = new DefaultFeatureCollection();

		// create the builder
		SimpleFeatureTypeBuilder sfTypeBuilder = new SimpleFeatureTypeBuilder();
		CoordinateReferenceSystem sourceCRS = CRS.decode("EPSG:2154");
		sfTypeBuilder.setCRS(sourceCRS);
		sfTypeBuilder.setName("counted");
		sfTypeBuilder.add("the_geom", Polygon.class);
		sfTypeBuilder.add("count", Integer.class);
		sfTypeBuilder.setDefaultGeometry("the_geom");

		SimpleFeatureType featureType = sfTypeBuilder.buildFeatureType();
		SimpleFeatureBuilder build = new SimpleFeatureBuilder(featureType);
		List<String[]> read = reader.readAll();
		for (String[] r : read) {
			System.out.println(r[0]);
		}
		SimpleFeatureIterator it = sds.getFeatureSource().getFeatures().features();
		try {
			while (it.hasNext()) {
				reader = new CSVReader(new FileReader(csvFile));
				SimpleFeature com = it.next();
//				System.out.println(com.getAttribute("cpCode_pos"));
				int count = 0;
				for (String[] line : read) {

//
//					if (line[cpIndice].equals(com.getAttribute("cpCode_pos"))) {
//						System.out.println("pluis");
//						count++;
//					}
				}
				build.add(com.getDefaultGeometry());
				build.set("count", count);
				result.add(build.buildFeature(null));
			}
		} catch (Exception problem) {
			problem.printStackTrace();
		} finally {
			it.close();
		}
		reader.close();
		sds.dispose();
		Collec.exportSFC(result, new File("/home/ubuntu/Documents/nikoTheseCarto/count.shp"));
	}
}
