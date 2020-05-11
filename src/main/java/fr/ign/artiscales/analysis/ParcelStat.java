package fr.ign.artiscales.analysis;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;

import org.geotools.data.shapefile.ShapefileDataStore;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Polygon;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.NoSuchAuthorityCodeException;

import com.opencsv.CSVWriter;

import fr.ign.artiscales.parcelFunction.MarkParcelAttributeFromPosition;
import fr.ign.artiscales.parcelFunction.ParcelSchema;
import fr.ign.artiscales.parcelFunction.ParcelState;
import fr.ign.cogit.geoToolsFunctions.vectors.Collec;
import fr.ign.cogit.geoToolsFunctions.vectors.Geom;
import fr.ign.cogit.geometryGeneration.CityGeneration;
/**
 * This class calculates basic statistics for every marked parcels. If no mark parcels are found, stats are calucated for every parcels. 
 * 
 * @author Maxime Colomb
 *
 */
public class ParcelStat {

//	public static void main(String[] args) throws Exception {
//		long strat = System.currentTimeMillis();
//		// ShapefileDataStore sdsParcel = new ShapefileDataStore(
//		// new File("/home/ubuntu/workspace/ParcelManager/src/main/resources/GeneralTest/parcel.shp").toURI().toURL());
//		ShapefileDataStore sdsParcel = new ShapefileDataStore(new File("/tmp/p.shp").toURI().toURL());
//
//		SimpleFeatureCollection parcels = sdsParcel.getFeatureSource().getFeatures();
//		parcels = MarkParcelAttributeFromPosition.markAllParcel(parcels);
//		ShapefileDataStore sdsRoad = new ShapefileDataStore(
//				new File("/home/ubuntu/workspace/ParcelManager/src/main/resources/GeneralTest/road.shp").toURI().toURL());
//		SimpleFeatureCollection roads = sdsRoad.getFeatureSource().getFeatures();
//		File parcelStatCsv = new File("/tmp/parcelStat.csv");
//
//		writeStatSingleParcel(parcels, roads, parcelStatCsv);
//		sdsParcel.dispose();
//		sdsRoad.dispose();
//		System.out.println("time : " + (System.currentTimeMillis() - strat));
//	}

	public static void writeStatSingleParcel(SimpleFeatureCollection parcels, File roadFile, File parcelStatCsv)
			throws NoSuchAuthorityCodeException, IOException, FactoryException {
		ShapefileDataStore sds = new ShapefileDataStore(roadFile.toURI().toURL());
		writeStatSingleParcel(parcels, sds.getFeatureSource().getFeatures(), parcelStatCsv);
		sds.dispose();
	}
	
	public static void writeStatSingleParcel(SimpleFeatureCollection parcels, SimpleFeatureCollection roads, File parcelStatCsv)
			throws NoSuchAuthorityCodeException, IOException, FactoryException {
		// look if there's mark field. If not, every parcels are marked 
		if (!Collec.isCollecContainsAttribute(parcels, MarkParcelAttributeFromPosition.getMarkFieldName())) {
			System.out.println("+++ writeStatSingleParcel: unmarked parcels. Try to mark them with the MarkParcelAttributeFromPosition.markAllParcel() method. Return null ");
			return;
		}
		CSVWriter csv = new CSVWriter(new FileWriter(parcelStatCsv, false));
		String[] firstLine = { "code", "area", "perimeter", "contactWithRoad", "widthContactWithRoad", "numberOfNeighborhood" };
		csv.writeNext(firstLine);
		SimpleFeatureCollection islet = CityGeneration.createUrbanIslet(parcels);
		Arrays.stream(parcels.toArray(new SimpleFeature[0]))
				.filter(p -> (int) p.getAttribute(MarkParcelAttributeFromPosition.getMarkFieldName()) == 1).forEach(parcel -> {
					// if parcel is marked to be analyzed
					Geometry parcelGeom = (Geometry) parcel.getDefaultGeometry();
					double widthRoadContact = ParcelState.getParcelFrontSideWidth((Polygon) Geom.getPolygon(parcelGeom),
							Collec.snapDatas(roads, parcelGeom.buffer(7)),
							Geom.fromMultiToLineString(Collec.fromPolygonSFCtoRingMultiLines(Collec.snapDatas(islet, parcelGeom))));
					boolean contactWithRoad = false;
					if (widthRoadContact != 0)
						contactWithRoad = true;
					// float compactness =
					String[] line = {
							parcel.getAttribute(ParcelSchema.getMinParcelCommunityField()) + "-"
									+ parcel.getAttribute(ParcelSchema.getMinParcelSectionField()) + "-"
									+ parcel.getAttribute(ParcelSchema.getMinParcelNumberField()),
							String.valueOf(parcelGeom.getArea()), String.valueOf(parcelGeom.getLength()), String.valueOf(contactWithRoad),
							String.valueOf(widthRoadContact),
							String.valueOf(ParcelState.countParcelNeighborhood(parcelGeom, Collec.snapDatas(parcels, parcelGeom.buffer(2)))) };
					csv.writeNext(line);
			});
		csv.close();
	}
}