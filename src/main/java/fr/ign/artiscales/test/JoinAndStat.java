package fr.ign.artiscales.test;

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

import com.opencsv.CSVReader;

import fr.ign.cogit.geoToolsFunctions.Attribute;
import fr.ign.cogit.geoToolsFunctions.vectors.Collec;

public class JoinAndStat {

	public static void main(String[] args) throws IOException, NoSuchAuthorityCodeException, FactoryException {
		ShapefileDataStore sds = new ShapefileDataStore(new File("/home/ubuntu/Documents/nikoTheseCarto/bdR.shp").toURI().toURL());
		File csvFile = new File("/home/ubuntu/Documents/nikoTheseCarto/newAttempt.csv");
		CSVReader reader = new CSVReader(new FileReader(csvFile));
		String[] firstline = reader.readNext();
		int cpIndice = Attribute.getIndice(firstline, "cp");
		reader.close();
		DefaultFeatureCollection result = new DefaultFeatureCollection();

		// create the builder
		SimpleFeatureTypeBuilder sfTypeBuilder = new SimpleFeatureTypeBuilder();
		sfTypeBuilder.setCRS(CRS.decode("EPSG:2154"));
		sfTypeBuilder.setName("counted");
		sfTypeBuilder.add("the_geom", Polygon.class);
		sfTypeBuilder.add("count", Integer.class);
		sfTypeBuilder.add("NameCity", String.class);
		sfTypeBuilder.setDefaultGeometry("the_geom");

		SimpleFeatureType featureType = sfTypeBuilder.buildFeatureType();
		SimpleFeatureBuilder build = new SimpleFeatureBuilder(featureType);
		
		try (SimpleFeatureIterator it = sds.getFeatureSource().getFeatures().features();) {
			while (it.hasNext()) {
				CSVReader r = new CSVReader(new FileReader(csvFile));
				r.readNext();
				List<String[]> read = r.readAll();
				SimpleFeature com = it.next();
				int count = 0;
				for (String[] line : read) {
					if (line[cpIndice].equals(String.valueOf(com.getAttribute("cpCode_pos")))) {
						count++;
					}
				}
				build.add(com.getDefaultGeometry());
				build.set("count", count);
				String comname = (String) com.getAttribute("NOM_COM"); 
				if (comname.startsWith("MARSEILLE-")) {
					System.out.println(comname);
					comname = comname.replace("MARSEILLE-", "");
					System.out.println(comname);

				}
				build.set("NameCity", comname);
				result.add(build.buildFeature(null));
				r.close();
			}
		} catch (Exception problem) {
			problem.printStackTrace();
		} 
		reader.close();
		sds.dispose();
		Collec.exportSFC(result, new File("/home/ubuntu/Documents/nikoTheseCarto/count.shp"));
	}
}
