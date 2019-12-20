package analysis;

import java.io.File;
import java.io.IOException;
import java.util.Hashtable;

import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.feature.DefaultFeatureCollection;
import org.locationtech.jts.geom.Geometry;
import org.opengis.feature.simple.SimpleFeature;

import fr.ign.cogit.GTFunctions.Csv;
import fr.ign.cogit.GTFunctions.Vectors;

public class StatParcelStreetRatio {

	public static double streetRatioParcels(SimpleFeatureCollection initialMarkedParcel, SimpleFeatureCollection cutParcel) {
		return areaParcelNew(cutParcel) / areaParcelMarked(initialMarkedParcel);
	}

	public static double streetRatioParcelZone(SimpleFeatureCollection zone, SimpleFeatureCollection cutParcel, File fileOutStat) throws IOException {
		Hashtable<String, String[]> stat = new Hashtable<String, String[]>();

		Double ratio = areaParcelNew(cutParcel) / area(zone);
		SimpleFeatureIterator zones = zone.features();
		String[] firstLine = { "CODE", "INSEE", "LIBELLE", "InitialArea", "ParcelsArea", "Ratio" };
		int count = 0;
		try {
			while (zones.hasNext()) {
				String[] tab = new String[5];
				SimpleFeature z = zones.next();
				tab[0] = (String) z.getAttribute("INSEE");
				tab[1] = (String) z.getAttribute("LIBELLE");

				SimpleFeatureIterator parcelIt = cutParcel.features();
				DefaultFeatureCollection df = new DefaultFeatureCollection();
				DefaultFeatureCollection zo = new DefaultFeatureCollection();
				try {
					while (parcelIt.hasNext()) {
						SimpleFeature parcel = parcelIt.next();
						if (((Geometry) z.getDefaultGeometry()).buffer(2).contains((Geometry) parcel.getDefaultGeometry())) {
							df.add(parcel);
						}
					}
				} catch (Exception problem) {
					problem.printStackTrace();
				} finally {
					parcelIt.close();
				}
				zo.add(z);
				double iniA = area(zo);
				double pNew = areaParcelNew(df);
				tab[2] = Double.toString(iniA);
				tab[3] = Double.toString(pNew);
				tab[4] = Double.toString(pNew / iniA);
				System.out.println("zone " + z.getAttribute("LIBELLE") + " of " + z.getAttribute("INSEE"));
				System.out.println(pNew / iniA);
				stat.put(count++ + "-" + tab[0] + "-" + tab[1], tab);
			}
		} catch (Exception problem) {
			problem.printStackTrace();
		} finally {
			zones.close();
		}

		Csv.generateCsvFile(stat, fileOutStat, "streetRatioParcelZone", false, firstLine);
		System.out.println("Total ratio: " + ratio);
		return ratio;
	}

	private static double areaParcelMarked(SimpleFeatureCollection markedParcels) {
		return areaParcelMarked(markedParcels, "SPLIT");
	}

	private static double areaParcelMarked(SimpleFeatureCollection markedParcels, String markingField) {
		SimpleFeatureIterator parcels = markedParcels.features();
		double totArea = 0.0;
		try {
			while (parcels.hasNext()) {
				SimpleFeature parcel = parcels.next();
				if (parcel.getAttribute(markingField).equals(1)) {
					totArea = totArea + ((Geometry) parcel.getDefaultGeometry()).getArea();
				}
			}
		} catch (Exception problem) {
			problem.printStackTrace();
		} finally {
			parcels.close();
		}
		return totArea;
	}

	private static double areaParcelNew(SimpleFeatureCollection markedParcels) {
		SimpleFeatureIterator parcels = markedParcels.features();
		double totArea = 0.0;
		try {
			while (parcels.hasNext()) {
				SimpleFeature parcel = parcels.next();
				if (((String) parcel.getAttribute("SECTION")) != null && ((String) parcel.getAttribute("SECTION")).length() != 2) {
					totArea = totArea + ((Geometry) parcel.getDefaultGeometry()).getArea();
				}
			}
		} catch (Exception problem) {
			problem.printStackTrace();
		} finally {
			parcels.close();
		}
		return totArea;
	}

	private static double area(SimpleFeatureCollection markedParcels) throws IOException {
		Vectors.exportSFC(markedParcels, new File("/tmp/batar.shp"));
		SimpleFeatureIterator parcels = markedParcels.features();
		double totArea = 0.0;
		try {
			while (parcels.hasNext()) {
				totArea = totArea + ((Geometry) parcels.next().getDefaultGeometry()).getArea();
			}
		} catch (Exception problem) {
			problem.printStackTrace();
		} finally {
			parcels.close();
		}
		return totArea;
	}
}
