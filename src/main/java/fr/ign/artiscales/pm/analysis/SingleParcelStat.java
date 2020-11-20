package fr.ign.artiscales.pm.analysis;

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
import org.locationtech.jts.algorithm.MinimumBoundingCircle;
import org.locationtech.jts.algorithm.construct.MaximumInscribedCircle;
import org.locationtech.jts.algorithm.distance.DiscreteHausdorffDistance;
import org.locationtech.jts.algorithm.match.HausdorffSimilarityMeasure;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Polygon;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;

import com.opencsv.CSVWriter;

import fr.ign.artiscales.pm.fields.GeneralFields;
import fr.ign.artiscales.pm.fields.french.FrenchParcelFields;
import fr.ign.artiscales.pm.parcelFunction.MarkParcelAttributeFromPosition;
import fr.ign.artiscales.pm.parcelFunction.ParcelSchema;
import fr.ign.artiscales.pm.parcelFunction.ParcelState;
import fr.ign.artiscales.tools.geoToolsFunctions.Attribute;
import fr.ign.artiscales.tools.geoToolsFunctions.vectors.Collec;
import fr.ign.artiscales.tools.geoToolsFunctions.vectors.Geopackages;
import fr.ign.artiscales.tools.geoToolsFunctions.vectors.geom.Lines;
import fr.ign.artiscales.tools.geoToolsFunctions.vectors.geom.Polygons;
import fr.ign.artiscales.tools.geometryGeneration.CityGeneration;

/**
 * This class calculates basic statistics for every marked parcels. If no mark parcels are found, stats are calucated for every parcels.
 * 
 * @author Maxime Colomb
 *
 */
public class SingleParcelStat {

	 public static void main(String[] args) throws IOException {
	 long strat = System.currentTimeMillis();
	 File root = new File("/home/mcolomb/PMtest/ParcelComparison/");
	 DataStore dsParcelEv = Geopackages.getDataStore(new File(root,"/out/evolvedParcel.gpkg"));
	 DataStore dsParcelSimu = Geopackages.getDataStore(new File(root, "/out/simulatedParcels.gpkg"));
	 SimpleFeatureCollection parcelEv = FrenchParcelFields.addCommunityCode(
	 MarkParcelAttributeFromPosition.markAllParcel(dsParcelEv.getFeatureSource(dsParcelEv.getTypeNames()[0]).getFeatures()));
	 SimpleFeatureCollection parcelSimu = MarkParcelAttributeFromPosition
	 .markAllParcel(dsParcelSimu.getFeatureSource(dsParcelSimu.getTypeNames()[0]).getFeatures());
	 DataStore dsRoad = Geopackages.getDataStore(new File(root, "/road.gpkg"));
	 SimpleFeatureCollection road = dsRoad.getFeatureSource(dsRoad.getTypeNames()[0]).getFeatures();
	
	 writeStatSingleParcel(parcelEv, road, new File(root,"out2/ev.csv"));
	 writeStatSingleParcel(parcelSimu, road, parcelEv, new File(root,"out2/sim.csv"));
	
	 // Collec.exportSFC(makeHausdorfDistanceMaps(parcelEv, parcelSimu), new File("/tmp/haus"));
	 dsParcelEv.dispose();
	 dsParcelSimu.dispose();
	 dsRoad.dispose();
	 System.out.println("time : " + (System.currentTimeMillis() - strat));
	 }

	public static void writeStatSingleParcel(File parcelFile, File roadFile, File parcelStatCsv, boolean markAll) throws IOException {
		writeStatSingleParcel(parcelFile, null, roadFile, parcelStatCsv, markAll);
	}

	public static void writeStatSingleParcel(File parcelFile, File parcelToCompare, File roadFile, File parcelStatCsv, boolean markAll)
			throws IOException {
		DataStore dsRoad = Geopackages.getDataStore(roadFile);
		DataStore dsParcel = Geopackages.getDataStore(parcelFile);
		SimpleFeatureCollection parcels;
		if (markAll)
			parcels = MarkParcelAttributeFromPosition.markAllParcel(dsParcel.getFeatureSource(dsParcel.getTypeNames()[0]).getFeatures());
		else
			parcels = dsParcel.getFeatureSource(dsParcel.getTypeNames()[0]).getFeatures();
		if (parcelToCompare == null)
			writeStatSingleParcel(parcels, dsRoad.getFeatureSource(dsRoad.getTypeNames()[0]).getFeatures(), parcelStatCsv);
		else {
			DataStore dsParcel2 = Geopackages.getDataStore(parcelToCompare);
			writeStatSingleParcel(parcels, dsParcel2.getFeatureSource(dsParcel2.getTypeNames()[0]).getFeatures(),
					dsRoad.getFeatureSource(dsRoad.getTypeNames()[0]).getFeatures(), parcelStatCsv);
			dsParcel2.dispose();
		}
		dsRoad.dispose();
		dsParcel.dispose();
	}

	public static void writeStatSingleParcel(SimpleFeatureCollection parcels, File roadFile, File parcelStatCsv) throws IOException {
		DataStore sds = Geopackages.getDataStore(roadFile);
		writeStatSingleParcel(parcels, sds.getFeatureSource(sds.getTypeNames()[0]).getFeatures(), parcelStatCsv);
		sds.dispose();
	}

	public static void writeStatSingleParcel(SimpleFeatureCollection parcels, SimpleFeatureCollection roads, File parcelStatCsv) throws IOException {
		writeStatSingleParcel(parcels, roads, null, parcelStatCsv);
	}

	public static void writeStatSingleParcel(SimpleFeatureCollection parcels, SimpleFeatureCollection roads, SimpleFeatureCollection parcelToCompare,
			File parcelStatCsv) throws IOException {
		// look if there's mark field. If not, every parcels are marked
		if (!Collec.isCollecContainsAttribute(parcels, MarkParcelAttributeFromPosition.getMarkFieldName())) {
			System.out.println(
					"+++ writeStatSingleParcel: unmarked parcels. Try to mark them with the MarkParcelAttributeFromPosition.markAllParcel() method. Return null ");
			return;
		}
		CSVWriter csv = new CSVWriter(new FileWriter(parcelStatCsv, false));
		String[] firstLine = { "code", "area", "perimeter", "contactWithRoad", "widthContactWithRoad", "numberOfNeighborhood", "Geometry", "HausDist",
				"DisHausDst", "CodeAppar", "AspctRatio" };
		csv.writeNext(firstLine);
		SimpleFeatureCollection block = CityGeneration.createUrbanBlock(parcels);
		Arrays.stream(parcels.toArray(new SimpleFeature[0]))
				.filter(p -> (int) p.getAttribute(MarkParcelAttributeFromPosition.getMarkFieldName()) == 1).forEach(parcel -> {
					// if parcel is marked to be analyzed
					Geometry parcelGeom = (Geometry) parcel.getDefaultGeometry();
					double widthRoadContact = ParcelState.getParcelFrontSideWidth((Polygon) Polygons.getPolygon(parcelGeom),
							Collec.selectIntersection(roads, parcelGeom.buffer(7)), Lines.fromMultiToLineString(
									Collec.fromPolygonSFCtoRingMultiLines(Collec.selectIntersection(block, parcelGeom.buffer(7)))));
					boolean contactWithRoad = false;
					if (widthRoadContact != 0)
						contactWithRoad = true;
					// if we set a parcel plan to compare, we calculate the Hausdorf distances for the parcels that intersects the most parts.
					String HausDist = "NA";
					String DisHausDst = "NA";
					String CodeAppar = "";
					// Setting of Hausdorf distance
					if (parcelToCompare != null) {
						HausdorffSimilarityMeasure hausDis = new HausdorffSimilarityMeasure();
						SimpleFeature parcelCompare = Collec.getIntersectingSimpleFeatureFromSFC(parcelGeom, parcelToCompare);
						if (parcelCompare != null) {
							Geometry parcelCompareGeom = (Geometry) parcelCompare.getDefaultGeometry();
							DiscreteHausdorffDistance dhd = new DiscreteHausdorffDistance(parcelGeom, parcelCompareGeom);
							HausDist = String.valueOf(hausDis.measure(parcelGeom, parcelCompareGeom));
							DisHausDst = String.valueOf(dhd.distance());
							if (!Collec.isSchemaContainsAttribute(parcelCompare.getFeatureType(), "CODE")
									&& GeneralFields.getParcelFieldType().equals("french"))
								CodeAppar = FrenchParcelFields.makeDEPCOMCode(parcelCompare);
							else
								CodeAppar = (String) parcelCompare.getAttribute("CODE");
						}
					}
					// Calculating Aspect Ratio
					MinimumBoundingCircle mbc = new MinimumBoundingCircle(parcelGeom);
					MaximumInscribedCircle mic = new MaximumInscribedCircle(parcelGeom, 1);
					String[] line = {
							parcel.getAttribute(ParcelSchema.getMinParcelCommunityField()) + "-"
									+ parcel.getAttribute(ParcelSchema.getMinParcelSectionField()) + "-"
									+ parcel.getAttribute(ParcelSchema.getMinParcelNumberField()),
							String.valueOf(parcelGeom.getArea()), String.valueOf(parcelGeom.getLength()), String.valueOf(contactWithRoad),
							String.valueOf(widthRoadContact),
							String.valueOf(ParcelState.countParcelNeighborhood(parcelGeom, Collec.selectIntersection(parcels, parcelGeom.buffer(2)))),
							parcelGeom.toString(), HausDist, DisHausDst, CodeAppar,
							String.valueOf(mic.getRadiusLine().getLength() / mbc.getDiameter().getLength()) };
					csv.writeNext(line);
				});
		csv.close();
	}

	// public static double aspectRatio(Geometry geom) {
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
		return Math.abs(result);
	}

	/**
	 * Calculate the difference between the average area of two Geopackages. Find a better indicator to compare distribution.
	 * 
	 * @param parcelInFile
	 *            The reference Geopackage
	 * @param parcelToCompareFile
	 *            The Geopackage to compare
	 * @return the difference of average (absolute value)
	 * @throws IOException
	 */
	public static double diffAreaAverage(File parcelInFile, File parcelToCompareFile) throws IOException {
		DataStore sdsParcelIn = Geopackages.getDataStore(parcelInFile);
		DataStore sdsParcelToCompareFile = Geopackages.getDataStore(parcelToCompareFile);
		double result = Collec.area(sdsParcelIn.getFeatureSource(sdsParcelIn.getTypeNames()[0]).getFeatures())
				- Collec.area(sdsParcelToCompareFile.getFeatureSource(sdsParcelToCompareFile.getTypeNames()[0]).getFeatures());
		sdsParcelIn.dispose();
		sdsParcelToCompareFile.dispose();
		return Math.abs(result);
	}

	public static double hausdorfDistanceAverage(SimpleFeatureCollection parcelIn, SimpleFeatureCollection parcelToCompare) {
		HausdorffSimilarityMeasure hausDis = new HausdorffSimilarityMeasure();
		DescriptiveStatistics stat = new DescriptiveStatistics();
		try (SimpleFeatureIterator parcelIt = parcelIn.features()) {
			while (parcelIt.hasNext()) {
				SimpleFeature parcel = parcelIt.next();
				Geometry parcelGeom = (Geometry) parcel.getDefaultGeometry();
				SimpleFeature parcelCompare = Collec.getIntersectingSimpleFeatureFromSFC(parcelGeom, parcelToCompare);
				if (parcelCompare != null)
					stat.addValue(hausDis.measure(parcelGeom, (Geometry) parcelCompare.getDefaultGeometry()));
			}
		} catch (Exception problem) {
			problem.printStackTrace();
		}
		return stat.getMean();
	}

	public static SimpleFeatureCollection makeHausdorfDistanceMaps(SimpleFeatureCollection parcelIn, SimpleFeatureCollection parcelToCompare)
			throws IOException {
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
				SimpleFeature parcelCompare = Collec.getIntersectingSimpleFeatureFromSFC(parcelGeom, parcelToCompare);
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
