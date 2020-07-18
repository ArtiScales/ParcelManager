package fr.ign.artiscales.analysis;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.geotools.data.DataStore;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.feature.DefaultFeatureCollection;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.locationtech.jts.algorithm.distance.DiscreteHausdorffDistance;
import org.locationtech.jts.algorithm.match.HausdorffSimilarityMeasure;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Polygon;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.NoSuchAuthorityCodeException;

import com.opencsv.CSVWriter;

import fr.ign.artiscales.fields.GeneralFields;
import fr.ign.artiscales.fields.french.FrenchParcelFields;
import fr.ign.artiscales.parcelFunction.MarkParcelAttributeFromPosition;
import fr.ign.artiscales.parcelFunction.ParcelSchema;
import fr.ign.artiscales.parcelFunction.ParcelState;
import fr.ign.cogit.geoToolsFunctions.Attribute;
import fr.ign.cogit.geoToolsFunctions.vectors.Collec;
import fr.ign.cogit.geoToolsFunctions.vectors.Geom;
import fr.ign.cogit.geoToolsFunctions.vectors.Geopackages;
import fr.ign.cogit.geometryGeneration.CityGeneration;

/**
 * This class calculates basic statistics for every marked parcels. If no mark parcels are found, stats are calucated for every parcels.
 * 
 * @author Maxime Colomb
 *
 */
public class SingleParcelStat {

	// public static void main(String[] args) throws Exception {
	// long strat = System.currentTimeMillis();
	// // ShapefileDataStore sdsParcel = new ShapefileDataStore(
	// // new File("/home/ubuntu/workspace/ParcelManager/src/main/resources/GeneralTest/parcel.gpkg").toURI().toURL());
	// ShapefileDataStore sdsParcelEv = new ShapefileDataStore(
	// new File("/home/thema/Documents/ParcelManager/ParcelComparison/out/evolvedParcelsSort.gpkg").toURI().toURL());
	// ShapefileDataStore sdsSimu = new ShapefileDataStore(
	// new File("/home/thema/Documents/ParcelManager/ParcelComparison/out/simulatedParcels.gpkg").toURI().toURL());
	// SimpleFeatureCollection parcelEv = MarkParcelAttributeFromPosition.markAllParcel(sdsParcelEv.getFeatureSource().getFeatures());
	// SimpleFeatureCollection parcelSimu = MarkParcelAttributeFromPosition.markAllParcel(sdsSimu.getFeatureSource().getFeatures());
	// ShapefileDataStore sdsRoad = new ShapefileDataStore(
	// new File("/home/thema/Documents/MC/workspace/ParcelManager/src/main/resources/ParcelComparison/road.gpkg").toURI().toURL());
	// SimpleFeatureCollection road = sdsRoad.getFeatureSource().getFeatures();
	//
	// writeStatSingleParcel(parcelEv, road, new File("/home/thema/Documents/ParcelManager/ParcelComparison/out/statEvol"));
	// writeStatSingleParcel(parcelSimu, road, parcelEv, new File("/home/thema/Documents/ParcelManager/ParcelComparison/out/statSumuled"));
	//
	//// Collec.exportSFC(makeHausdorfDistanceMaps(parcelEv, parcelSimu), new File("/tmp/haus"));
	// sdsParcelEv.dispose();
	// sdsSimu.dispose();
	// System.out.println("time : " + (System.currentTimeMillis() - strat));
	// }

	public static void writeStatSingleParcel(SimpleFeatureCollection parcels, File roadFile, File parcelStatCsv)
			throws NoSuchAuthorityCodeException, IOException, FactoryException {
		DataStore sds = Geopackages.getDataStore(roadFile);
		writeStatSingleParcel(parcels, sds.getFeatureSource(sds.getTypeNames()[0]).getFeatures(), parcelStatCsv);
		sds.dispose();
	}

	public static void writeStatSingleParcel(SimpleFeatureCollection parcels, SimpleFeatureCollection roads, File parcelStatCsv)
			throws NoSuchAuthorityCodeException, IOException, FactoryException {
		writeStatSingleParcel(parcels, roads, null, parcelStatCsv);
	}

	public static void writeStatSingleParcel(SimpleFeatureCollection parcels, SimpleFeatureCollection roads, SimpleFeatureCollection parcelToCompare,
			File parcelStatCsv) throws NoSuchAuthorityCodeException, IOException, FactoryException {
		// look if there's mark field. If not, every parcels are marked
		if (!Collec.isCollecContainsAttribute(parcels, MarkParcelAttributeFromPosition.getMarkFieldName())) {
			System.out.println(
					"+++ writeStatSingleParcel: unmarked parcels. Try to mark them with the MarkParcelAttributeFromPosition.markAllParcel() method. Return null ");
			return;
		}
		CSVWriter csv = new CSVWriter(new FileWriter(parcelStatCsv, false));
		String[] firstLine = { "code", "area", "perimeter", "contactWithRoad", "widthContactWithRoad", "numberOfNeighborhood", "Geometry", "HausDist",
				"DisHausDst", "CodeAppar" };
		csv.writeNext(firstLine);
		SimpleFeatureCollection islet = CityGeneration.createUrbanIslet(parcels);
		Arrays.stream(parcels.toArray(new SimpleFeature[0]))
				.filter(p -> (int) p.getAttribute(MarkParcelAttributeFromPosition.getMarkFieldName()) == 1).forEach(parcel -> {
					// if parcel is marked to be analyzed
					Geometry parcelGeom = (Geometry) parcel.getDefaultGeometry();
					double widthRoadContact = ParcelState.getParcelFrontSideWidth((Polygon) Geom.getPolygon(parcelGeom),
							Collec.snapDatas(roads, parcelGeom.buffer(7)),
							Geom.fromMultiToLineString(Collec.fromPolygonSFCtoRingMultiLines(Collec.snapDatas(islet, parcelGeom.buffer(7)))));
					boolean contactWithRoad = false;
					if (widthRoadContact != 0)
						contactWithRoad = true;
					// if we set a parcel plan to compare, we calculate the Hausdorf distances for the parcels that intersects the most parts.
					double HausDist = 0;
					double DisHausDst = 0;
					String CodeAppar = "";
					if (parcelToCompare != null) {
						HausdorffSimilarityMeasure hausDis = new HausdorffSimilarityMeasure();
						SimpleFeature parcelCompare = Collec.getSimpleFeatureFromSFC(parcelGeom, parcelToCompare);
						if (parcelCompare != null) {
							Geometry parcelCompareGeom = (Geometry) parcelCompare.getDefaultGeometry();
							DiscreteHausdorffDistance dhd = new DiscreteHausdorffDistance(parcelGeom, parcelCompareGeom);
							HausDist = hausDis.measure(parcelGeom, parcelCompareGeom);
							DisHausDst = dhd.distance();
							if (!Collec.isSchemaContainsAttribute(parcelCompare.getFeatureType(), "CODE")
									&& GeneralFields.getParcelFieldType().equals("french"))
								CodeAppar = FrenchParcelFields.makeDEPCOMCode(parcelCompare);
							else
								CodeAppar = (String) parcelCompare.getAttribute("CODE");
						}
					}
					String[] line = {
							parcel.getAttribute(ParcelSchema.getMinParcelCommunityField()) + "-"
									+ parcel.getAttribute(ParcelSchema.getMinParcelSectionField()) + "-"
									+ parcel.getAttribute(ParcelSchema.getMinParcelNumberField()),
							String.valueOf(parcelGeom.getArea()), String.valueOf(parcelGeom.getLength()), String.valueOf(contactWithRoad),
							String.valueOf(widthRoadContact),
							String.valueOf(ParcelState.countParcelNeighborhood(parcelGeom, Collec.snapDatas(parcels, parcelGeom.buffer(2)))),
							parcelGeom.toString(), String.valueOf(HausDist), String.valueOf(DisHausDst), CodeAppar };
					csv.writeNext(line);
				});
		csv.close();
	}

	// public static double egress(Geometry geom) {
	// MinimumBoundingCircle mbc = new MinimumBoundingCircle(geom);
	//// MaximumInscribedCircle mic = new MaximumInscribedCircle(geom, 1);
	//// return mic.getRadiusLine().getLength() / mbc.getDiameter().getLength();
	// return mbc.getDiameter().getLength();
	// }
	public static double hausdorfDistanceAverage(File parcelInFile, File parcelToCompareFile) throws IOException {
		DataStore sdsParcelIn = Geopackages.getDataStore(parcelInFile);
		DataStore sdsParcelToCompareFile = Geopackages.getDataStore(parcelToCompareFile);
		double result = hausdorfDistanceAverage(sdsParcelIn.getFeatureSource(sdsParcelIn.getTypeNames()[0]).getFeatures(),
				sdsParcelToCompareFile.getFeatureSource(sdsParcelToCompareFile.getTypeNames()[0]).getFeatures());
		sdsParcelIn.dispose();
		sdsParcelToCompareFile.dispose();
		return result;
	}

	public static int diffNumberOfParcel(File parcelInFile, File parcelToCompareFile) throws IOException {
		DataStore sdsParcelIn = Geopackages.getDataStore(parcelInFile);
		DataStore sdsParcelToCompareFile = Geopackages.getDataStore(parcelToCompareFile);
		int result = sdsParcelIn.getFeatureSource(sdsParcelIn.getTypeNames()[0]).getFeatures().size()
				- sdsParcelToCompareFile.getFeatureSource(sdsParcelToCompareFile.getTypeNames()[0]).getFeatures().size();
		sdsParcelIn.dispose();
		sdsParcelToCompareFile.dispose();
		return result;
	}

	/**
	 * Calculate the difference between the average area of two Geopackages.
	 * 
	 * @param parcelInFile        
	 * 			The reference Geopackage
	 * @param parcelToCompareFile 
	 * 			The Geopackage to compare
	 * @return the difference of average.
	 * @throws IOException
	 */
	public static double diffAreaAverage(File parcelInFile, File parcelToCompareFile) throws IOException {
		DataStore sdsParcelIn = Geopackages.getDataStore(parcelInFile);
		DataStore sdsParcelToCompareFile = Geopackages.getDataStore(parcelToCompareFile);
		double result = Collec.area(sdsParcelIn.getFeatureSource(sdsParcelIn.getTypeNames()[0]).getFeatures())
				- Collec.area(sdsParcelToCompareFile.getFeatureSource(sdsParcelToCompareFile.getTypeNames()[0]).getFeatures());
		sdsParcelIn.dispose();
		sdsParcelToCompareFile.dispose();
		return result;
	}
	
	
	public static double hausdorfDistanceAverage(SimpleFeatureCollection parcelIn, SimpleFeatureCollection parcelToCompare) {
		HausdorffSimilarityMeasure hausDis = new HausdorffSimilarityMeasure();
		DescriptiveStatistics stat = new DescriptiveStatistics();
		try (SimpleFeatureIterator parcelIt = parcelIn.features()) {
			while (parcelIt.hasNext()) {
				SimpleFeature parcel = parcelIt.next();
				Geometry parcelGeom = (Geometry) parcel.getDefaultGeometry();
				SimpleFeature parcelCompare = Collec.getSimpleFeatureFromSFC(parcelGeom, parcelToCompare);
				if (parcelCompare != null)
					stat.addValue(hausDis.measure(parcelGeom, (Geometry) parcelCompare.getDefaultGeometry()));
			}
		} catch (Exception problem) {
			problem.printStackTrace();
		}
		return stat.getMean();
	}

	public static SimpleFeatureCollection makeHausdorfDistanceMaps(SimpleFeatureCollection parcelIn, SimpleFeatureCollection parcelToCompare)
			throws NoSuchAuthorityCodeException, FactoryException, IOException {
		if (!Collec.isCollecContainsAttribute(parcelIn, "CODE"))
			GeneralFields.addParcelCode(parcelIn);
		if (!Collec.isCollecContainsAttribute(parcelToCompare, "CODE"))
			GeneralFields.addParcelCode(parcelToCompare);
		SimpleFeatureType schema = parcelIn.getSchema();
		DefaultFeatureCollection result = new DefaultFeatureCollection();
		SimpleFeatureTypeBuilder sfTypeBuilder = new SimpleFeatureTypeBuilder();
		sfTypeBuilder.setName("minParcel");
		sfTypeBuilder.setCRS(schema.getCoordinateReferenceSystem());
		sfTypeBuilder.add(schema.getGeometryDescriptor().getLocalName(), Polygon.class);
		sfTypeBuilder.setDefaultGeometry(schema.getGeometryDescriptor().getLocalName());
		sfTypeBuilder.add("DisHausDst", Double.class);
		sfTypeBuilder.add("HausDist", Double.class);
		sfTypeBuilder.add("CODE", String.class);
		sfTypeBuilder.add("CodeAppar", String.class);
		SimpleFeatureBuilder builder = new SimpleFeatureBuilder(sfTypeBuilder.buildFeatureType());
		HausdorffSimilarityMeasure hausDis = new HausdorffSimilarityMeasure();
		try (SimpleFeatureIterator parcelIt = parcelIn.features()) {
			while (parcelIt.hasNext()) {
				SimpleFeature parcel = parcelIt.next();
				Geometry parcelGeom = (Geometry) parcel.getDefaultGeometry();
				SimpleFeature parcelCompare = Collec.getSimpleFeatureFromSFC(parcelGeom, parcelToCompare);
				if (parcelCompare != null) {
					Geometry parcelCompareGeom = (Geometry) parcelCompare.getDefaultGeometry();
					DiscreteHausdorffDistance dhd = new DiscreteHausdorffDistance(parcelGeom, parcelCompareGeom);
					builder.set("DisHausDst", dhd.distance());
					builder.set("HausDist", hausDis.measure(parcelGeom, parcelCompareGeom));
					builder.set("CODE", parcel.getAttribute("CODE"));
					builder.set("CodeAppar", parcelCompare.getAttribute("CODE"));
					builder.set(schema.getGeometryDescriptor().getLocalName(), parcelGeom);
					result.add(builder.buildFeature(Attribute.makeUniqueId()));
				} else {
					builder.set("CODE", parcel.getAttribute("CODE"));
					builder.set(schema.getGeometryDescriptor().getLocalName(), parcelGeom);
					result.add(builder.buildFeature(Attribute.makeUniqueId()));
				}
			}
		} catch (Exception problem) {
			problem.printStackTrace();
		}
		return result.collection();
	}
}
