package algorithm;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.factory.CommonFactoryFinder;
import org.geotools.feature.DefaultFeatureCollection;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.util.factory.GeoTools;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryCollection;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.PrecisionModel;
import org.locationtech.jts.precision.GeometryPrecisionReducer;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.filter.FilterFactory2;

import fr.ign.cogit.FeaturePolygonizer;
import fr.ign.cogit.GTFunctions.Vectors;
import fr.ign.cogit.parcelFunction.ParcelSchema;
import processus.ParcelSplit;

public class ParcelTotRecomp {
	private static String ZoneField = "TYPEZONE";

	/**
	 * Merge and recut a specific zone. Cut first the surrounding parcels to keep them unsplited, then split the zone parcel and remerge them all into the original parcel file A
	 * bit complicated algorithm to deal with unexisting peaces of parcels (as road)
	 * 
	 * @param initialZone
	 *            : Zone wich will be used to cut parcels. Will cut parcels that intersects them and keep their infos. Will then fill the empty spaces in between the zones and feed
	 *            it to the OBB algorithm. 
	 * @param parcels
	 *            : Parcel plan
	 * @param tmpFolder
	 * @param zoningFile
	 * @param maximalArea
	 * @param minimalArea
	 * @param maximalWidth
	 * @param lenRoad
	 * @param decompositionLevelWithoutRoad
	 * @return
	 * @throws Exception
	 */
	public static SimpleFeatureCollection parcelTotRecomp(SimpleFeatureCollection initialZone, SimpleFeatureCollection parcels, File tmpFolder,
			File zoningFile, double maximalArea, double minimalArea, double maximalWidth, double lenRoad, int decompositionLevelWithoutRoad)
			throws Exception {

		// parcel geometry name for all
		String geomName = parcels.getSchema().getGeometryDescriptor().getLocalName();

		final Geometry geomAU = Vectors.unionSFC(initialZone);
		Geometry unionParcel = Vectors.unionSFC(parcels);

		// sort in two different collections, the ones that matters and the ones that will besaved for future purposes
		DefaultFeatureCollection parcelsInZone = new DefaultFeatureCollection();
		// parcels to save for after
		DefaultFeatureCollection savedParcels = new DefaultFeatureCollection();
		Arrays.stream(parcels.toArray(new SimpleFeature[0])).forEach(parcel -> {
			if (((Geometry) parcel.getDefaultGeometry()).intersects(geomAU)) {
				parcelsInZone.add(parcel);
			} else {
				savedParcels.add(parcel);
			}
		});

		// complete the void left by the existing roads from the zones
		// Also assess a section number
		// tricky operations to avoid geometry problems
		SimpleFeatureBuilder sfBuilder = ParcelSchema.getSFBFrenchParcelSplit();
		int numZone = 0;

		DefaultFeatureCollection goOdZone = new DefaultFeatureCollection();
		SimpleFeatureIterator zoneIt = initialZone.features();
		try {
			while (zoneIt.hasNext()) {
				numZone++;
				SimpleFeature feat = zoneIt.next();
				// avoid most of tricky geometry problems
				Geometry intersection = Vectors
						.scaledGeometryReductionIntersection(Arrays.asList(((Geometry) feat.getDefaultGeometry()), unionParcel));
				if (!intersection.isEmpty() && intersection.getArea() > 5.0) {
					if (intersection instanceof MultiPolygon) {
						for (int i = 0; i < intersection.getNumGeometries(); i++) {
							Geometry geom = GeometryPrecisionReducer.reduce(intersection.getGeometryN(i), new PrecisionModel(100));
							if (geom.getArea() > 5.0) {
								sfBuilder.set(geomName, geom);
								sfBuilder.set("SECTION", "New" + numZone + "Section");
								sfBuilder.set("SPLIT", 1);
								goOdZone.add(sfBuilder.buildFeature(null));
							}
						}
					} else if (intersection instanceof GeometryCollection) {
						for (int i = 0; i < intersection.getNumGeometries(); i++) {
							Geometry g = intersection.getGeometryN(i);
							if (g instanceof Polygon && g.getArea() > 5.0) {
								sfBuilder.set(geomName, GeometryPrecisionReducer.reduce(intersection, new PrecisionModel(100)));
								sfBuilder.set("SECTION", "New" + numZone + "Section");
								sfBuilder.set("SPLIT", 1);
								goOdZone.add(sfBuilder.buildFeature(null));
							}
						}
					} else {
						Geometry geom = GeometryPrecisionReducer.reduce(intersection, new PrecisionModel(100));
						if (geom.getArea() > 5.0) {
							sfBuilder.set(geomName, geom);
							sfBuilder.set("SECTION", "New" + numZone + "Section");
							sfBuilder.set("SPLIT", 1);
							goOdZone.add(sfBuilder.buildFeature(null));
						}
					}
				}
			}
		} catch (Exception problem) {
			problem.printStackTrace();
		} finally {
			zoneIt.close();
		}

		// zones verification
		if (goOdZone.isEmpty()) {
			System.out.println("parcelGenZone : no zones to cut");
			return parcels;
		}
		// detect if the zone is a leftover
		SimpleFeatureIterator itGoOD = goOdZone.features();
		double totAireGoOD = 0.0;
		try {
			while (itGoOD.hasNext()) {
				totAireGoOD = totAireGoOD + ((Geometry) itGoOD.next().getDefaultGeometry()).getArea();
			}
		} catch (Exception problem) {
			problem.printStackTrace();
		} finally {
			itGoOD.close();
		}
		if (totAireGoOD < minimalArea) {
			System.out.println("Tot zone is too small to be taken into consideration -- return is null");
			return parcels;
		}

		// parts of parcel outside the zone must not be cut by the algorithm and keep their attributes
		// temporary shapefiles that serves to do polygons with the polygonizer
		File fParcelsInAU = Vectors.exportSFC(parcelsInZone, new File(tmpFolder, "parcelCible.shp"));
		File fZone = Vectors.exportSFC(goOdZone, new File(tmpFolder, "oneAU.shp"));
		Geometry geomSelectedZone = Vectors.unionSFC(goOdZone);
		File[] polyFiles = { fParcelsInAU, fZone };
		List<Polygon> polygons = FeaturePolygonizer.getPolygons(polyFiles);

		// big loop on each generated geometry
		for (Geometry poly : polygons) {
			// if the polygons are not included on the AU zone, we check to which parcel they belong
			if (!geomSelectedZone.buffer(0.01).contains(poly)) {
				sfBuilder.set(geomName, poly);
				SimpleFeatureIterator parcelIt = parcelsInZone.features();
				try {
					while (parcelIt.hasNext()) {
						SimpleFeature feat = parcelIt.next();
						// we copy the previous parcels informations
						if (((Geometry) feat.getDefaultGeometry()).buffer(0.01).contains(poly)) {
							sfBuilder = ParcelSchema.fillSFBFrenchParcelSplitWithFeat(feat, sfBuilder, geomName, poly, 0);
							goOdZone.add(sfBuilder.buildFeature(null));
						}
					}
				} catch (Exception problem) {
					problem.printStackTrace();
				} finally {
					parcelIt.close();
				}
			}
		}

		double roadEpsilon = 0;
		double noise = 0;

		// Parcel subdivision
		// Sometimes it bugs (like on Sector NV in Besançon)
		SimpleFeatureCollection splitedZoneParcels = ParcelSplit.splitParcels(goOdZone, maximalArea, maximalWidth, roadEpsilon, noise, null, lenRoad,
				false, decompositionLevelWithoutRoad, tmpFolder);

		int i = 0;
		DefaultFeatureCollection result = new DefaultFeatureCollection();
		SimpleFeatureIterator itZoneParcel = splitedZoneParcels.features();
		SimpleFeatureBuilder finalParcelBuilder = ParcelSchema.getSFBFrenchParcel();
		SimpleFeatureType schemaFrenchParcel = finalParcelBuilder.getFeatureType();
		try {
			while (itZoneParcel.hasNext()) {
				SimpleFeature parcel = itZoneParcel.next();
				if (((Geometry) parcel.getDefaultGeometry()).getArea() > minimalArea) {
					finalParcelBuilder.set(geomName, parcel.getDefaultGeometry());
					finalParcelBuilder.set("SECTION", parcel.getAttribute("SECTION"));
					result.add(finalParcelBuilder.buildFeature(null));
				}
			}
		} catch (Exception problem) {
			problem.printStackTrace();
		} finally {
			itZoneParcel.close();
		}

		// add the saved parcels
		SimpleFeatureIterator itSavedParcels = savedParcels.features();
		try {
			while (itSavedParcels.hasNext()) {
				SimpleFeature parcel = itSavedParcels.next();
				finalParcelBuilder = ParcelSchema.setSFBFrenchParcelWithFeat(parcel, schemaFrenchParcel);
				result.add(finalParcelBuilder.buildFeature(Integer.toString(i++)));
			}
		} catch (Exception problem) {
			problem.printStackTrace();
		} finally {
			itSavedParcels.close();
		}

		return result;
	}

	/**
	 * 
	 * @param splitZone
	 * @param zoning
	 * @param parcels
	 * @return
	 * @throws IOException
	 */
	public static SimpleFeatureCollection createZoneToCut(String splitZone, SimpleFeatureCollection zoning, SimpleFeatureCollection parcels)
			throws IOException {
		Geometry unionParcel = Vectors.unionSFC(parcels);
		// get the wanted zones from the zoning file
		FilterFactory2 ff = CommonFactoryFinder.getFilterFactory2(GeoTools.getDefaultHints());
		SimpleFeatureCollection initialZone = zoning.subCollection(ff.like(ff.property(ZoneField), splitZone))
				.subCollection(ff.intersects(ff.property(zoning.getSchema().getGeometryDescriptor().getLocalName()), ff.literal(unionParcel)));

		if (initialZone.isEmpty()) {
			System.out.println("zone is empty");
		}
		return initialZone;
	}

	public static void setZoneField(String zoneField) {
		ZoneField = zoneField;
	}
}
