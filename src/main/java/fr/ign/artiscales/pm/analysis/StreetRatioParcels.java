package fr.ign.artiscales.pm.analysis;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;

import org.geotools.data.DataStore;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.factory.CommonFactoryFinder;
import org.geotools.feature.DefaultFeatureCollection;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.util.factory.GeoTools;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Polygon;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.filter.FilterFactory2;

import fr.ign.artiscales.pm.fields.GeneralFields;
import fr.ign.artiscales.pm.fields.french.FrenchParcelFields;
import fr.ign.artiscales.pm.parcelFunction.MarkParcelAttributeFromPosition;
import fr.ign.artiscales.pm.parcelFunction.ParcelSchema;
import fr.ign.artiscales.pm.parcelFunction.ParcelState;
import fr.ign.artiscales.tools.geoToolsFunctions.Attribute;
import fr.ign.artiscales.tools.geoToolsFunctions.Csv;
import fr.ign.artiscales.tools.geoToolsFunctions.vectors.Collec;
import fr.ign.artiscales.tools.geoToolsFunctions.vectors.Geom;
import fr.ign.artiscales.tools.geoToolsFunctions.vectors.Geopackages;
import fr.ign.artiscales.tools.geoToolsFunctions.vectors.geom.Polygons;
import fr.ign.artiscales.tools.geometryGeneration.CityGeneration;

/**
 * Street/generated surface ratio. Only developped for french parcels.
 * 
 * @author Maxime Colomb
 *
 */
public class StreetRatioParcels {

	private static boolean overwrite = true;
	private static boolean firstLine = true;

//	public static void main(String[] args) throws Exception {
//		long start = System.currentTimeMillis();
//		File rootFolder = new File("src/main/resources/GeneralTest/");
//		File zoningFile = new File(rootFolder, "zoning.gpkg");
//		DataStore ds = Geopackages.getDataStore(new File(rootFolder, "parcel.gpkg"));
//		SimpleFeatureCollection sfc = ds.getFeatureSource(ds.getTypeNames()[0]).getFeatures();
//		DataStore ds2 = Geopackages.getDataStore(new File(rootFolder,"out/OBB/ParcelConsolidRecomp.gpkg"));
//		SimpleFeatureCollection sfc2 = ds2.getFeatureSource(ds2.getTypeNames()[0]).getFeatures();
//		streetRatioParcels(MarkParcelAttributeFromPosition.markParcelIntersectPreciseZoningType(sfc, "AU", "AUb",
//				zoningFile), sfc2, "", new File("/tmp/"), new File(rootFolder, "road.gpkg"));
//		ds.dispose();
//		ds2.dispose();
//		System.out.println(System.currentTimeMillis() - start);
//	}

	/**
	 * Calculate the ratio between the parcel area and the total area of a zone. It express the quantity of not parcel land, which could be either streets or public spaces.
	 * Calculate zones and then send the whole to the {@link #streetRatioZone(SimpleFeatureCollection, SimpleFeatureCollection,String, File, File)} method.
	 * 
	 * @param initialMarkedParcel
	 *            {@link SimpleFeatureCollection} of the initial set of parcels which are marked if they had to simulated. Marks could be made with the methods contained in the
	 *            class {@link fr.ign.artiscales.pm.parcelFunction.MarkParcelAttributeFromPosition}. The field attribute is named <i>SPLIT</i> by default. It is possible to change it with the
	 *            {@link fr.ign.artiscales.pm.parcelFunction.MarkParcelAttributeFromPosition#setMarkFieldName(String)} function.
	 * @param cutParcel
	 *            A collection of parcels after a Parcel Manager simulation
	 * @param folderOutStat
	 *            folder to store the results
	 * @param roadFile
	 *            the road Shapefile
	 * @throws IOException
	 */
	public static void streetRatioParcels(SimpleFeatureCollection initialMarkedParcel, SimpleFeatureCollection cutParcel, String legend,
			File folderOutStat, File roadFile) throws IOException {

		// We construct zones to analyze the street ratio for each operations.
		DefaultFeatureCollection zone = new DefaultFeatureCollection();
		FilterFactory2 ff = CommonFactoryFinder.getFilterFactory2(GeoTools.getDefaultHints());
		Geometry multiGeom;
		if (Collec.isCollecContainsAttribute(initialMarkedParcel, MarkParcelAttributeFromPosition.getMarkFieldName()))
			multiGeom = Geom
					.unionSFC(initialMarkedParcel.subCollection(ff.like(ff.property(MarkParcelAttributeFromPosition.getMarkFieldName()), "1")));
		else {
			System.out.println("Parcels haven't been previously marked : stop StatParcelStreetRatio");
			return;
		}
		SimpleFeatureBuilder sfBuilderZone = GeneralFields.getSFBZoning();
		for (int i = 0; i < multiGeom.getNumGeometries(); i++) {
			Geometry zoneGeom = multiGeom.getGeometryN(i);
			sfBuilderZone.add(zoneGeom);
			// set needed attributes with initial french parcels
			try (SimpleFeatureIterator it = cutParcel.features()) {
				while (it.hasNext()) {
					SimpleFeature feat = it.next();
					if (zoneGeom.contains(((Geometry) feat.getDefaultGeometry()))) {
						sfBuilderZone.set(ParcelSchema.getMinParcelCommunityField(), FrenchParcelFields.makeDEPCOMCode(feat));
						sfBuilderZone.set(GeneralFields.getZonePreciseNameField(), feat.getAttribute(ParcelSchema.getMinParcelSectionField()));
						break;
					}
				}
			} catch (Exception problem) {
				problem.printStackTrace();
			}
			zone.add(sfBuilderZone.buildFeature(Attribute.makeUniqueId()));
		}
		streetRatioZone(zone, cutParcel, legend, folderOutStat, roadFile);
	}

	/**
	 * Calculate the ratio between the parcel area and the total area of a zone. It express the quantity of not parcel land, which could be either streets or public spaces
	 * 
	 * @param zone
	 *            {@link SimpleFeatureCollection} of initial zones
	 * @param cutParcel
	 *            {@link SimpleFeatureCollection} of the cuted parcels
	 * @param folderOutStat
	 *            folder to store the results
	 * @param roadFile
	 *            the road Shapefile
	 * @throws IOException
	 */
	public static void streetRatioZone(SimpleFeatureCollection zone, SimpleFeatureCollection cutParcel, String legend, File folderOutStat,
			File roadFile) throws IOException {
		System.out.println("++++++++++Road Ratios++++++++++");
		HashMap<String, String[]> stat = new HashMap<String, String[]>();

		DataStore sdsRoad = Geopackages.getDataStore(roadFile);
		SimpleFeatureCollection roads = Collec.selectIntersection(sdsRoad.getFeatureSource(sdsRoad.getTypeNames()[0]).getFeatures(), zone);
		SimpleFeatureCollection islets = CityGeneration.createUrbanIslet(cutParcel);

		String[] firstLine = { "CODE", "Urban fabric type", ParcelSchema.getMinParcelCommunityField(), GeneralFields.getZonePreciseNameField(),
				"InitialArea", "ParcelsArea", "RatioArea", "RatioParcelConnectionRoad" };
		int count = 0;
		try (SimpleFeatureIterator zones = zone.features()) {
			while (zones.hasNext()) {
				String[] tab = new String[7];
				SimpleFeature z = zones.next();
				tab[0] = legend;
				tab[1] = (String) z.getAttribute(ParcelSchema.getMinParcelCommunityField());
				tab[2] = (String) z.getAttribute(GeneralFields.getZonePreciseNameField());
				DefaultFeatureCollection df = new DefaultFeatureCollection();
				// get the intersecting parcels
				try (SimpleFeatureIterator parcelIt = cutParcel.features()) {
					while (parcelIt.hasNext()) {
						SimpleFeature parcel = parcelIt.next();
						if (((Geometry) z.getDefaultGeometry()).buffer(0.5).contains((Geometry) parcel.getDefaultGeometry()))
							df.add(parcel);
					}
				} catch (Exception problem) {
					problem.printStackTrace();
				}
				double iniA = ((Geometry) z.getDefaultGeometry()).getArea();
				double pNew = areaParcelNewlySimulated(df);
				if (pNew == 0.0)
					continue ;
				tab[3] = Double.toString(iniA);
				tab[4] = Double.toString(pNew);
				tab[5] = Double.toString(1 - (pNew / iniA));
				long nbParcelsWithContactToRoad = Arrays.stream(df.toArray(new SimpleFeature[0]))
						.filter(feat -> ParcelState.isParcelHasRoadAccess((Polygon) Polygons.getPolygon((Geometry) feat.getDefaultGeometry()),
								Collec.selectIntersection(roads, ((Geometry) feat.getDefaultGeometry())),
								Collec.fromPolygonSFCtoRingMultiLines(Collec.selectIntersection(islets, (Geometry) z.getDefaultGeometry()))))
						.count();
				tab[6] = String.valueOf(((double) nbParcelsWithContactToRoad / (double) df.size()));
				stat.put(count++ + "-" + tab[1] + "-" + tab[2], tab);
			}
		} catch (Exception problem) {
			problem.printStackTrace();
		}
		sdsRoad.dispose();
		if (StreetRatioParcels.firstLine) {
			Csv.needFLine = true;
			StreetRatioParcels.firstLine = false;
		} else
			Csv.needFLine = false;
		Csv.generateCsvFile(stat, folderOutStat, "streetRatioParcelZone", !overwrite, firstLine);
		overwrite = false;
	}

	private static double areaParcelNewlySimulated(SimpleFeatureCollection markedParcels) {
		double totArea = 0.0;
		try (SimpleFeatureIterator parcels = markedParcels.features()) {
			while (parcels.hasNext()) {
				SimpleFeature parcel = parcels.next();
				if (GeneralFields.isParcelHasSimulatedFields(parcel))
					totArea = totArea + ((Geometry) parcel.getDefaultGeometry()).getArea();
			}
		} catch (Exception problem) {
			problem.printStackTrace();
		}
		return totArea;
	}
}
