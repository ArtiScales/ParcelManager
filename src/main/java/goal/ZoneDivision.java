package goal;

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

import decomposition.ParcelSplit;
import fr.ign.cogit.FeaturePolygonizer;
import fr.ign.cogit.geoToolsFunctions.vectors.Collec;
import fr.ign.cogit.geoToolsFunctions.vectors.Geom;
import fr.ign.cogit.parcelFunction.MarkParcelAttributeFromPosition;
import fr.ign.cogit.parcelFunction.ParcelCollection;
import fr.ign.cogit.parcelFunction.ParcelSchema;

public class ZoneDivision {
	private static String ZoneField = "TYPEZONE";
	public static String PROCESS = "OBB";
	public static boolean SAVEINTERMEDIATERESULT = false;
	public static boolean OVERWRITESHAPEFILES = true;

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
	 * @param streetWidth
	 * @param decompositionLevelWithoutRoad
	 * @return
	 * @throws Exception
	 */
	public static SimpleFeatureCollection zoneDivision(SimpleFeatureCollection initialZone, SimpleFeatureCollection parcels, File tmpFolder,
			File zoningFile, double maximalArea, double minimalArea, double maximalWidth, double streetWidth, int decompositionLevelWithoutRoad)
			throws Exception {
		return zoneDivision(initialZone, parcels, tmpFolder, zoningFile, maximalArea, minimalArea, maximalWidth, streetWidth, 999, streetWidth,
				decompositionLevelWithoutRoad);
	}
	
	public static SimpleFeatureCollection zoneDivision(SimpleFeatureCollection initialZone, SimpleFeatureCollection parcels, File tmpFolder,
			File zoningFile, double maximalArea, double minimalArea, double maximalWidth, double smallStreetWidth, int largeStreetLevel, double largeStreetWidth, int decompositionLevelWithoutRoad)
			throws Exception {

		// parcel geometry name for all
		String geomName = parcels.getSchema().getGeometryDescriptor().getLocalName();

		final Geometry geomAU = Geom.unionSFC(initialZone);
		Geometry unionParcel = Geom.unionSFC(parcels);

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
		SimpleFeatureBuilder sfBuilder = ParcelSchema.getSFBMinParcelSplit();
		int numZone = 0;

		DefaultFeatureCollection goOdZone = new DefaultFeatureCollection();
		SimpleFeatureIterator zoneIt = initialZone.features();
		try {
			while (zoneIt.hasNext()) {
				numZone++;
				SimpleFeature feat = zoneIt.next();
				// avoid most of tricky geometry problems
				Geometry intersection = Geom
						.scaledGeometryReductionIntersection(Arrays.asList(((Geometry) feat.getDefaultGeometry()), unionParcel));
				if (!intersection.isEmpty() && intersection.getArea() > 5.0) {
					if (intersection instanceof MultiPolygon) {
						for (int i = 0; i < intersection.getNumGeometries(); i++) {
							Geometry geom = GeometryPrecisionReducer.reduce(intersection.getGeometryN(i), new PrecisionModel(100));
							if (geom.getArea() > 5.0) {
								sfBuilder.set(geomName, geom);
								sfBuilder.set(ParcelSchema.getMinParcelSectionField(), "New" + numZone + "Section");
								sfBuilder.set(MarkParcelAttributeFromPosition.getMarkFieldName(), 1);
								goOdZone.add(sfBuilder.buildFeature(null));
							}
						}
					} else if (intersection instanceof GeometryCollection) {
						for (int i = 0; i < intersection.getNumGeometries(); i++) {
							Geometry g = intersection.getGeometryN(i);
							if (g instanceof Polygon && g.getArea() > 5.0) {
								sfBuilder.set(geomName, GeometryPrecisionReducer.reduce(intersection, new PrecisionModel(100)));
								sfBuilder.set(ParcelSchema.getMinParcelSectionField(), "New" + numZone + "Section");
								sfBuilder.set(MarkParcelAttributeFromPosition.getMarkFieldName(), 1);
								goOdZone.add(sfBuilder.buildFeature(null));
							}
						}
					} else {
						Geometry geom = GeometryPrecisionReducer.reduce(intersection, new PrecisionModel(100));
						if (geom.getArea() > 5.0) {
							sfBuilder.set(geomName, geom);
							sfBuilder.set(ParcelSchema.getMinParcelSectionField(), "New" + numZone + "Section");
							sfBuilder.set(MarkParcelAttributeFromPosition.getMarkFieldName(), 1);
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
		File fParcelsInAU = Collec.exportSFC(parcelsInZone, new File(tmpFolder, "parcelCible.shp"));
		File fZone = Collec.exportSFC(goOdZone, new File(tmpFolder, "oneAU.shp"));
		Geometry geomSelectedZone = Geom.unionSFC(goOdZone);
		File[] polyFiles = { fParcelsInAU, fZone };
		List<Polygon> polygons = FeaturePolygonizer.getPolygons(polyFiles);

		// big loop on each generated geometry
		for (Geometry poly : polygons) {
			// if the polygons are not included on the AU zone, we check to which parcel they belong
			if (!geomSelectedZone.buffer(0.01).contains(poly)) {
				SimpleFeatureIterator parcelIt = parcelsInZone.features();
				try {
					while (parcelIt.hasNext()) {
						SimpleFeature feat = parcelIt.next();
						// we copy the previous parcels informations
						if (((Geometry) feat.getDefaultGeometry()).buffer(0.01).contains(poly)) {
							sfBuilder = ParcelSchema.setSFBMinParcelSplitWithFeat(feat, sfBuilder, feat.getFeatureType(), 0);
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
		SimpleFeatureCollection splitedZoneParcels  = new DefaultFeatureCollection();
		switch (PROCESS) {
		case "OBB":
			splitedZoneParcels = ParcelSplit.splitParcels(goOdZone, maximalArea, maximalWidth, roadEpsilon, noise, null, smallStreetWidth,
					largeStreetLevel, largeStreetWidth, false, decompositionLevelWithoutRoad, tmpFolder);
			break;
		case "SS":
			System.out.println("not implemented yet");
			break;
		case "MS":
			System.out.println("not implemented yet");
			break;
		}
		//merge the small parcels to bigger ones
		splitedZoneParcels = ParcelCollection.mergeTooSmallParcels(splitedZoneParcels, (int) minimalArea);
		//get the section numbers 
		int i = 0;
		DefaultFeatureCollection result = new DefaultFeatureCollection();
		SimpleFeatureIterator itZoneParcel = splitedZoneParcels.features();
		SimpleFeatureBuilder finalParcelBuilder = ParcelSchema.getSFBMinParcel();
		SimpleFeatureType schemaParcel = finalParcelBuilder.getFeatureType();
		try {
			while (itZoneParcel.hasNext()) {
				SimpleFeature parcel = itZoneParcel.next();
				Geometry parcelGeom = (Geometry) parcel.getDefaultGeometry();
//				if (parcelGeom.getArea() > minimalArea) {
					finalParcelBuilder.set(geomName, parcel.getDefaultGeometry());
					// get the section name
					String section = "";
					SimpleFeatureIterator goOdZoneIt = goOdZone.features();
					try {
						while (goOdZoneIt.hasNext()) {
							SimpleFeature zone = goOdZoneIt.next();
							if (((Geometry) zone.getDefaultGeometry()).buffer(2).contains(parcelGeom)) {
								section = (String) zone.getAttribute(ParcelSchema.getMinParcelSectionField());
								break;
							}
						}
					} catch (Exception problem) {
						problem.printStackTrace();
					} finally {
						goOdZoneIt.close();
					}
					finalParcelBuilder.set(ParcelSchema.getMinParcelSectionField(), section);
					result.add(finalParcelBuilder.buildFeature(null));
//				}
			}
		} catch (Exception problem) {
			problem.printStackTrace();
		} finally {
			itZoneParcel.close();
		}

		if (SAVEINTERMEDIATERESULT) {
			Collec.exportSFC(result, new File(tmpFolder,"parcelZoneDivisionOnly"), OVERWRITESHAPEFILES);
			OVERWRITESHAPEFILES = false;
		}
		
		// add the saved parcels
		SimpleFeatureIterator itSavedParcels = savedParcels.features();
		try {
			while (itSavedParcels.hasNext()) {
				SimpleFeature parcel = itSavedParcels.next();
				finalParcelBuilder = ParcelSchema.setSFBMinParcelWithFeat(parcel, schemaParcel);
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
	 * Create a zone to cut by selecting features from a shapefile regarding a fixed value.
	 * Name of the field is by default set to "TYPEZONE" and must be changed if needed with the {@link #setZoneField(String) setZoneField} method
	 * Also takes a bounding SimpleFeatureCollection to bound the output
	 * @param zoneToCutName: Name of the zone to be cut
	 * @param zoning: Collection of zones to extract the wanted zone from (ussualy a zoning plan)
	 * @param parcels: Collection of parcel to bound the process on a wanted location
	 * @return An extraction of the zoning collection
	 * @throws IOException
	 */
	public static SimpleFeatureCollection createZoneToCut(String zoneToCutName, SimpleFeatureCollection zoning, SimpleFeatureCollection parcels)
			throws IOException {
		// get the wanted zones from the zoning file
		FilterFactory2 ff = CommonFactoryFinder.getFilterFactory2(GeoTools.getDefaultHints());
		SimpleFeatureCollection initialZone = zoning.subCollection(ff.like(ff.property(ZoneField), zoneToCutName))
				.subCollection(ff.intersects(ff.property(zoning.getSchema().getGeometryDescriptor().getLocalName()), ff.literal(Geom.unionSFC(parcels))));
		if (initialZone.isEmpty()) {
			System.out.println("createZoneToCut(): zone is empty");
		}
		return initialZone;
	}

	public static void setZoneField(String zoneField) {
		ZoneField = zoneField;
	}
}
