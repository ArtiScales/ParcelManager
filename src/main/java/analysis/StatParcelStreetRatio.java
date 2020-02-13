package analysis;

import java.io.File;
import java.io.IOException;
import java.util.Hashtable;

import org.geotools.data.shapefile.ShapefileDataStore;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.factory.CommonFactoryFinder;
import org.geotools.feature.DefaultFeatureCollection;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.util.factory.GeoTools;
import org.locationtech.jts.geom.Geometry;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.filter.Filter;
import org.opengis.filter.FilterFactory2;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.NoSuchAuthorityCodeException;

import fr.ign.cogit.GTFunctions.Attribute;
import fr.ign.cogit.GTFunctions.Csv;
import fr.ign.cogit.GTFunctions.Vectors;
import fr.ign.cogit.parcelFunction.ParcelSchema;

public class StatParcelStreetRatio {
	public static void main(String[] args) throws Exception {

		ShapefileDataStore sds = new ShapefileDataStore(new File("/tmp/parcelMarked.shp").toURI().toURL());
		SimpleFeatureCollection sfc = sds.getFeatureSource().getFeatures();
		ShapefileDataStore sds2 = new ShapefileDataStore(new File("/tmp/parcelCuted-consolid.shp").toURI().toURL());
		SimpleFeatureCollection sfc2 = sds2.getFeatureSource().getFeatures();
		streetRatioParcels(sfc, sfc2);
	}

	public static double streetRatioParcels(SimpleFeatureCollection initialMarkedParcel, SimpleFeatureCollection cutParcel)
			throws NoSuchAuthorityCodeException, IOException, FactoryException {
		return streetRatioParcels(initialMarkedParcel, cutParcel, new File("/tmp/"), "SPLIT");
	}

	public static double streetRatioParcels(SimpleFeatureCollection initialMarkedParcel, SimpleFeatureCollection cutParcel, File fileOutStat,
			String field) throws IOException, NoSuchAuthorityCodeException, FactoryException {
		FilterFactory2 ff = CommonFactoryFinder.getFilterFactory2(GeoTools.getDefaultHints());
		Filter filter = ff.like(ff.property(field), "1");
		SimpleFeatureCollection selectedParcels = initialMarkedParcel.subCollection(filter);

		DefaultFeatureCollection zone = new DefaultFeatureCollection();

		Geometry multiGeom = Vectors.unionSFC(selectedParcels);
		SimpleFeatureBuilder sfBuilder = ParcelSchema.getSFBFrenchZoning();
		for (int i = 0; i < multiGeom.getNumGeometries(); i++) {
			Geometry zoneGeom = multiGeom.getGeometryN(i);
			sfBuilder.add(zoneGeom);

			// set needed attributes
			SimpleFeatureIterator it = cutParcel.features();
			try {
				while (it.hasNext()) {
					SimpleFeature p = it.next();
					if (zoneGeom.contains(((Geometry) p.getDefaultGeometry()))) {
						sfBuilder.set("INSEE", Attribute.makeINSEECode(p));
						sfBuilder.set("LIBELLE", p.getAttribute("SECTION"));
						break;
					}
				}
			} catch (Exception problem) {
				problem.printStackTrace();
			} finally {
				it.close();
			}
			zone.add(sfBuilder.buildFeature(null));
		}
		Vectors.exportSFC(zone, new File("/tmp/zones.shp"));

		return streetRatioParcelZone(zone, cutParcel, fileOutStat);
	}

	public static double streetRatioParcelZone(SimpleFeatureCollection zone, SimpleFeatureCollection cutParcel, File fileOutStat) throws IOException {
		System.out.println("++++++++++Road Ratios++++++++++");
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
