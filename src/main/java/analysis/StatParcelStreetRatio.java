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

import fr.ign.cogit.geoToolsFunctions.Attribute;
import fr.ign.cogit.geoToolsFunctions.Csv;
import fr.ign.cogit.geoToolsFunctions.vectors.Collec;
import fr.ign.cogit.geoToolsFunctions.vectors.Geom;
import fr.ign.cogit.parcelFunction.ParcelSchema;

public class StatParcelStreetRatio {

	static String markFieldName = "SPLIT";

	public static void main(String[] args) throws Exception {
		ShapefileDataStore sds = new ShapefileDataStore(new File("/tmp/parcelMarked.shp").toURI().toURL());
		SimpleFeatureCollection sfc = sds.getFeatureSource().getFeatures();
		ShapefileDataStore sds2 = new ShapefileDataStore(new File("/tmp/parcelCuted-consolid.shp").toURI().toURL());
		SimpleFeatureCollection sfc2 = sds2.getFeatureSource().getFeatures();
		streetRatioParcels(sfc, sfc2, new File("/tmp/"));
		sds.dispose();
		sds2.dispose();
	}

	//TODO calculate the number (precentage) of parcels that doesn't
	
	/**
	 * Calculate the ratio between the parcel area and the total area of a zone. It express the quantity of not parcel land, which could be either streets or public spaces
	 * 
	 * @param initialMarkedParcel:
	 *            Collection of the initial set of parcels which are marked if they had to simulated. Marks could be made with the methods contained in the class
	 *            {@link fr.ign.cogit.parcelFunction}. The field attribute is named <i>SPLIT</i> by default. It is possible to change it with the {@link #setMarkFieldName(String)
	 *            setMarkFieldName()} function.
	 * @param cutParcel:
	 *            A collection of parcels after a Parcel Manager simulation
	 * @param folderOutStat
	 *            : folder to store the results
	 * @return the street ratio
	 * @throws NoSuchAuthorityCodeException
	 * @throws IOException
	 * @throws FactoryException
	 */
	public static double streetRatioParcels(SimpleFeatureCollection initialMarkedParcel, SimpleFeatureCollection cutParcel, File folderOutStat)
			throws IOException, NoSuchAuthorityCodeException, FactoryException {
		FilterFactory2 ff = CommonFactoryFinder.getFilterFactory2(GeoTools.getDefaultHints());
		Filter filter = ff.like(ff.property(markFieldName), "1");
		SimpleFeatureCollection selectedParcels = initialMarkedParcel.subCollection(filter);
		DefaultFeatureCollection zone = new DefaultFeatureCollection();
		Geometry multiGeom = Geom.unionSFC(selectedParcels);
		
		SimpleFeatureBuilder sfBuilder = ParcelSchema.getSFBFrenchZoning();
		for (int i = 0; i < multiGeom.getNumGeometries(); i++) {
			Geometry zoneGeom = multiGeom.getGeometryN(i);
			sfBuilder.add(zoneGeom);

			// set needed attributes
			SimpleFeatureIterator it = cutParcel.features();
			try {
				while (it.hasNext()) {
					SimpleFeature feat = it.next();
					if (zoneGeom.contains(((Geometry) feat.getDefaultGeometry()))) {
						sfBuilder.set("INSEE", Attribute.makeINSEECode(feat));
						sfBuilder.set("LIBELLE", feat.getAttribute("SECTION"));
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
		return streetRatioParcelZone(zone, cutParcel, folderOutStat);
	}

	public static double streetRatioParcelZone(SimpleFeatureCollection zone, SimpleFeatureCollection cutParcel, File folderOutStat) throws IOException {
		System.out.println("++++++++++Road Ratios++++++++++");
		Hashtable<String, String[]> stat = new Hashtable<String, String[]>();

		Double ratio = areaParcelNewlySimulated(cutParcel) / Collec.area(zone);
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
				double iniA = Collec.area(zo);
				double pNew = areaParcelNewlySimulated(df);
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

		Csv.generateCsvFile(stat, folderOutStat, "streetRatioParcelZone", false, firstLine);
		System.out.println("Total ratio: " + ratio);
		return ratio;
	}

	private static double areaParcelNewlySimulated(SimpleFeatureCollection markedParcels) {
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

	public static String getMarkFieldName() {
		return markFieldName;
	}

	public static void setMarkFieldName(String markFieldName) {
		StatParcelStreetRatio.markFieldName = markFieldName;
	}
}
