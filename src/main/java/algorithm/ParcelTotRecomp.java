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
import fr.ign.cogit.parcelFunction.ParcelAttribute;
import fr.ign.cogit.parcelFunction.ParcelSchema;
import processus.ParcelSplit;

public class ParcelTotRecomp {
	private static String ZoneField = "TYPEZONE";
	private static String CityField = "INSEE";

	/**
	 * Merge and recut a specific zone. Cut first the surrounding parcels to keep them unsplited, then split the zone parcel and remerge them all into the original parcel file A
	 * bit complicated algorithm to deal with unexisting peaces of parcels (as road)
	 * 
	 * TODO working algorithm, tho still improvement for the arguments management
	 * 
	 * @param splitZone
	 * @param parcels
	 * @param tmpFolder
	 * @param zoningFile
	 * @param maximalArea
	 * @param maximalWidth
	 * @param lenRoad
	 * @param decompositionLevelWithoutRoad
	 * @param allOrCell
	 *            if true, all the new parcels in the zone will be set as simulable. If false, nothing is set on those new parcels (we need to check the intersection with cells at
	 *            a different point)
	 * @return the whole parcels
	 * @return
	 * @throws Exception
	 */
	public static SimpleFeatureCollection parcelTotRecomp(SimpleFeatureCollection initialZone, SimpleFeatureCollection parcels, File tmpFolder,
			File zoningFile, double maximalArea, double minimalArea, double maximalWidth, double lenRoad, int decompositionLevelWithoutRoad)
			throws Exception {

		// parcel schema for all
		SimpleFeatureType schema = parcels.getSchema();
		String geomName = schema.getGeometryDescriptor().getLocalName();

		final Geometry geomAU = Vectors.unionSFC(initialZone);
		Geometry unionParcel = Vectors.unionSFC(parcels);

		// get the city number
		List<String> insees = ParcelAttribute.getCityCodeFromParcels(parcels, CityField);
		String insee = insees.get(0);
		if (insees.size() > 1) {
			System.out.println("Warning: more than one insee number in the parcel collection ");
		}

		DefaultFeatureCollection parcelsInzone = new DefaultFeatureCollection();
		// parcels to save for after
		DefaultFeatureCollection savedParcels = new DefaultFeatureCollection();
		// sort in two different collections, the ones that matters and the ones that will besaved for future purposes
		Arrays.stream(parcels.toArray(new SimpleFeature[0])).forEach(parcel -> {
			if (((Geometry) parcel.getDefaultGeometry()).intersects(geomAU)) {
				parcelsInzone.add(parcel);
			} else {
				savedParcels.add(parcel);
			}
		});

		// complete the void left by the existing roads from the zones
		// tricky operations to avoid geometry problems
		SimpleFeatureBuilder simpleSFB = new SimpleFeatureBuilder(initialZone.getSchema());

		DefaultFeatureCollection goOdZone = new DefaultFeatureCollection();
		SimpleFeatureIterator zoneIt = initialZone.features();
		try {
			while (zoneIt.hasNext()) {
				SimpleFeature feat = zoneIt.next();
				Geometry intersection = Vectors
						.scaledGeometryReductionIntersection(Arrays.asList(((Geometry) feat.getDefaultGeometry()), unionParcel));
				if (!intersection.isEmpty() && intersection.getArea() > 5.0) {
					if (intersection instanceof MultiPolygon) {
						for (int i = 0; i < intersection.getNumGeometries(); i++) {
							simpleSFB.set(geomName, GeometryPrecisionReducer.reduce(intersection.getGeometryN(i), new PrecisionModel(100)));
							simpleSFB.set(CityField, insee);
							goOdZone.add(simpleSFB.buildFeature(null));
						}
					} else if (intersection instanceof GeometryCollection) {
						for (int i = 0; i < intersection.getNumGeometries(); i++) {
							Geometry g = intersection.getGeometryN(i);
							if (g instanceof Polygon) {
								simpleSFB.set(geomName, g.buffer(1).buffer(-1));
								simpleSFB.set(CityField, insee);
								goOdZone.add(simpleSFB.buildFeature(null));
							}
						}
					} else {
						simpleSFB.set(geomName, intersection.buffer(1).buffer(-1));
						simpleSFB.set(CityField, insee);
						goOdZone.add(simpleSFB.buildFeature(null));
					}
				}
			}
		} catch (Exception problem) {
			problem.printStackTrace();
		} finally {
			zoneIt.close();
		}
		SimpleFeatureCollection selectedZone = goOdZone.collection();
		if (selectedZone.isEmpty()) {
			System.out.println("parcelGenZone : no zones to cut");
			return parcels;
		}
		// if the zone is a leftover (this could be done as a stream. When I'll have time I'll get used to it
		SimpleFeatureIterator itGoOD = selectedZone.features();
		double totAireGoOD = 0.0;
		try {
			while (itGoOD.hasNext()) {
				totAireGoOD = +((Geometry) itGoOD.next().getDefaultGeometry()).getArea();
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

		// parts of parcel outside the zone must not be cut by the algorithm and keep
		// their attributes
		// temporary shapefiles that serves to do polygons with the polygonizer
		File fParcelsInAU = Vectors.exportSFC(parcelsInzone, new File(tmpFolder, "parcelCible.shp"));
		File fZone = Vectors.exportSFC(selectedZone, new File(tmpFolder, "oneAU.shp"));
		Geometry geomSelectedZone = Vectors.unionSFC(selectedZone);
		File[] polyFiles = { fParcelsInAU, fZone };
		List<Polygon> polygons = FeaturePolygonizer.getPolygons(polyFiles);

		SimpleFeatureBuilder sfBuilder = ParcelSchema.getParcelSplitSFBuilder();
		DefaultFeatureCollection splitPrep = new DefaultFeatureCollection();
		// big loop on each generated geometry
		geoms: for (Geometry poly : polygons) {
			// if the polygons are not included on the AU zone
			if (!geomSelectedZone.buffer(0.01).contains(poly)) {
				sfBuilder.set(geomName, poly);
				SimpleFeatureIterator parcelIt = parcelsInzone.features();
				boolean isCode = false;
				try {
					while (parcelIt.hasNext()) {
						SimpleFeature feat = parcelIt.next();
						if (((Geometry) feat.getDefaultGeometry()).buffer(0.01).contains(poly)) {
							// set those good ol attributes
							sfBuilder.set("CODE", feat.getAttribute("CODE"));
							sfBuilder.set("CODE_DEP", feat.getAttribute("CODE_DEP"));
							sfBuilder.set("CODE_COM", feat.getAttribute("CODE_COM"));
							sfBuilder.set("COM_ABS", feat.getAttribute("COM_ABS"));
							sfBuilder.set("SECTION", feat.getAttribute("SECTION"));
							sfBuilder.set("NUMERO", feat.getAttribute("NUMERO"));
							sfBuilder.set("INSEE", feat.getAttribute("INSEE"));
							sfBuilder.set("eval", feat.getAttribute("eval"));
							sfBuilder.set("DoWeSimul", feat.getAttribute("DoWeSimul"));
							sfBuilder.set("SPLIT", 0);
							sfBuilder.set("IsBuild", feat.getAttribute("IsBuild"));
							sfBuilder.set("U", feat.getAttribute("U"));
							sfBuilder.set("AU", feat.getAttribute("AU"));
							sfBuilder.set("NC", feat.getAttribute("NC"));
							isCode = true;
						}
					}
				} catch (Exception problem) {
					problem.printStackTrace();
				} finally {
					parcelIt.close();
				}
				// if some polygons are inside others but aren't selected they gon be polygonised, we ain't puttin 'em
				if (!isCode) {
					continue geoms;
				}
				splitPrep.add(sfBuilder.buildFeature(null));
			}
		}

		SimpleFeatureIterator it = selectedZone.features();
		int numZone = 0;

		// mark and add the zones to the collection
		try {
			while (it.hasNext()) {
				SimpleFeature zone = it.next();
				// get the insee number for that zone
				// String insee = (String) zone.getAttribute("INSEE");
				sfBuilder.set("CODE", insee + "000" + "New" + numZone + "Section");
				sfBuilder.set("CODE_DEP", insee.substring(0, 2));
				sfBuilder.set("CODE_COM", insee.substring(2, 5));
				sfBuilder.set("COM_ABS", "000");
				sfBuilder.set("SECTION", "New" + numZone + "Section");
				sfBuilder.set("NUMERO", "");
				sfBuilder.set("INSEE", insee);
				sfBuilder.set("SPLIT", 1);
				// @warning the AU Parcels are mostly unbuilt, but maybe not?

				// avoid multi geom bugs
				Geometry intersectedGeom = Vectors
						.scaledGeometryReductionIntersection(Arrays.asList((Geometry) zone.getDefaultGeometry(), unionParcel));

				if (!intersectedGeom.isEmpty()) {
					splitPrep = Vectors.addSimpleGeometry(sfBuilder, splitPrep, geomName, intersectedGeom);
				} else {
					System.out.println("this intersection is empty");
					splitPrep = Vectors.addSimpleGeometry(sfBuilder, splitPrep, geomName, intersectedGeom);
				}
				numZone++;
			}
		} catch (Exception problem) {
			problem.printStackTrace();
		} finally {
			it.close();
		}

		SimpleFeatureCollection toSplit = Vectors.delTinyParcels(splitPrep.collection(), 5.0);
		double roadEpsilon = 00;
		double noise = 0;

		// Parcel subdivision
		// Sometimes it bugs (like on Sector NV in Besançon)
		SimpleFeatureCollection splitedZoneParcels = ParcelSplit.splitParcels(toSplit, maximalArea, maximalWidth, roadEpsilon, noise, null, lenRoad,
				false, decompositionLevelWithoutRoad, tmpFolder);

		int i = 0;
		DefaultFeatureCollection result = new DefaultFeatureCollection();
		SimpleFeatureIterator itFinal = splitedZoneParcels.features();
		try {
			while (itFinal.hasNext()) {
				SimpleFeature parcel = itFinal.next();
				if (((Geometry) parcel.getDefaultGeometry()).getArea() > minimalArea) {
					SimpleFeatureBuilder finalParcelBuilder = new SimpleFeatureBuilder(schema);
					finalParcelBuilder.set(geomName, parcel.getDefaultGeometry());
					result.add(finalParcelBuilder.buildFeature(null));
				}
			}
		} catch (Exception problem) {
			problem.printStackTrace();
		} finally {
			itFinal.close();
		}
		System.out.println(schema);
		SimpleFeatureIterator itFinal2 = savedParcels.features();
		try {
			while (itFinal2.hasNext()) {
				SimpleFeature parcel = itFinal2.next();
				SimpleFeatureBuilder finalParcelBuilder = ParcelSchema.setSFBNormalParcelWithFeat(parcel, schema);
				result.add(finalParcelBuilder.buildFeature(Integer.toString(i++)));
			}
		} catch (Exception problem) {
			problem.printStackTrace();
		} finally {
			itFinal2.close();
		}

		// Arrays.stream(splitedZoneParcels.toArray(new SimpleFeature[0])).forEach(parcel -> {
		// if (((Geometry) parcel.getDefaultGeometry()).getArea() > minimalArea) {
		// SimpleFeatureBuilder finalParcelBuilder = new SimpleFeatureBuilder(schema);
		// savedParcels.add(finalParcelBuilder.buildFeature(null));
		// }
		// });

		return result;
	}

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

	public static void setCityField(String cityField) {
		CityField = cityField;
	}
}
