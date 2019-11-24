package algorithm;

import java.io.File;
import java.util.Arrays;
import java.util.List;

import org.geotools.data.shapefile.ShapefileDataStore;
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
import org.opengis.filter.Filter;
import org.opengis.filter.FilterFactory2;

import fr.ign.cogit.FeaturePolygonizer;
import fr.ign.cogit.Schema;
import fr.ign.cogit.GTFunctions.Vectors;
import fr.ign.cogit.parcelFunction.ParcelAttribute;
import fr.ign.cogit.parcelFunction.ParcelSchema;
import fr.ign.cogit.parcelFunction.ParcelState;
import processus.ParcelSplit;

public class ParcelTotRecomp {

	/**
	 * Merge and recut the to urbanised (AU) zones Cut first the U parcels to keep them unsplited, then split the AU parcel and remerge them all into the original parcel file
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
	public static SimpleFeatureCollection parcelTotRecomp(String splitZone, SimpleFeatureCollection parcels, File tmpFolder, File zoningFile,
			File batiFile, File regulFile, File mupOutput, double maximalArea, double maximalWidth, double lenRoad, int decompositionLevelWithoutRoad,
			boolean allOrCell) throws Exception {

		// parcel schema for all
		SimpleFeatureType schema = parcels.getSchema();

		// import of the zoning file
		ShapefileDataStore shpDSZone = new ShapefileDataStore(zoningFile.toURI().toURL());
		SimpleFeatureCollection featuresZones = shpDSZone.getFeatureSource().getFeatures();

		Geometry unionParcel = Vectors.unionSFC(parcels);
		String geometryParcelPropertyName = schema.getGeometryDescriptor().getLocalName();

		// get the wanted zones from the zoning file
		FilterFactory2 ff = CommonFactoryFinder.getFilterFactory2(GeoTools.getDefaultHints());
		Filter filterTypeZone = ff.like(ff.property("TYPEZONE"), splitZone);

		Filter filterEmprise = ff.intersects(ff.property(geometryParcelPropertyName), ff.literal(unionParcel));
		SimpleFeatureCollection initialZone = featuresZones.subCollection(filterTypeZone).subCollection(filterEmprise);

		// If no AU zones, we won't bother
		if (initialZone.isEmpty()) {
			System.out.println("parcelGenZone : no " + splitZone + " zones");
			return parcels;
		}

		// get the insee number
		List<String> insees = ParcelAttribute.getInseeParcels(parcels);
		String insee = insees.get(0);
		if (insees.size() > 1) {
			System.out.println("Warning: more than one insee number in the parcel collection ");
		}
//		SimpleFeatureIterator pInsee = parcels.features();
//		(String) pInsee.next().getAttribute("INSEE");
//		pInsee.close();

		// all the AU zones
		final Geometry geomAU = Vectors.unionSFC(initialZone);
		DefaultFeatureCollection parcelsInzone = new DefaultFeatureCollection();
		// parcels to save for after
		DefaultFeatureCollection savedParcels = new DefaultFeatureCollection();
		// sort in two different collections, the ones that matters and the ones that will besaved for future purposes
		Arrays.stream(parcels.toArray(new SimpleFeature[0])).forEach(parcel -> {
			if (((Geometry) parcel.getDefaultGeometry()).intersects(geomAU)) {
				parcelsInzone.add(parcel);
			} else {
				savedParcels.add(parcel);
			}});

		
//		SimpleFeatureIterator parcIt = parcels.features();
//		try {
//			while (parcIt.hasNext()) {
//				SimpleFeature feat = parcIt.next();
//				if (((Geometry) feat.getDefaultGeometry()).intersects(geomAU)) {
//					parcelsInAU.add(feat);
//				} else {
//					savedParcels.add(feat);
//				}
//			}
//		} catch (Exception problem) {
//			problem.printStackTrace();
//		} finally {
//			parcIt.close();
//		}

		// delete the existing roads from the AU zones
		//tricky operations to avoid geometry problems
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
							simpleSFB.set("the_geom", GeometryPrecisionReducer.reduce(intersection.getGeometryN(i), new PrecisionModel(100)));
							simpleSFB.set("INSEE", insee);
							goOdZone.add(simpleSFB.buildFeature(null));
						}
					} else if (intersection instanceof GeometryCollection) {
						for (int i = 0; i < intersection.getNumGeometries(); i++) {
							Geometry g = intersection.getGeometryN(i);
							if (g instanceof Polygon) {
								simpleSFB.set("the_geom", g.buffer(1).buffer(-1));
								simpleSFB.set("INSEE", insee);
								goOdZone.add(simpleSFB.buildFeature(null));
							}
						}
					} else {
						simpleSFB.set("the_geom", intersection.buffer(1).buffer(-1));
						simpleSFB.set("INSEE", insee);
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
			System.out.println("parcelGenZone : no " + splitZone + " zones");
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
		if (totAireGoOD < 15) {
			System.out.println("Tot zone is too small to be taken into consideration -- return null");
			return parcels;
		}

		// parts of parcel outside the zone must not be cut by the algorithm and keep
		// their attributes
		// temporary shapefiles that serves to do polygons
		File fParcelsInAU = Vectors.exportSFC(parcelsInzone, new File(tmpFolder, "parcelCible.shp"));
		File fZone = Vectors.exportSFC(selectedZone, new File(tmpFolder, "oneAU.shp"));
		Geometry geomSelectedZone = Vectors.unionSFC(selectedZone);
		File[] polyFiles = { fParcelsInAU, fZone };
		List<Polygon> polygons = FeaturePolygonizer.getPolygons(polyFiles);

		SimpleFeatureBuilder sfBuilder = ParcelSchema.getParcelSplitSFBuilder();
		DefaultFeatureCollection write = new DefaultFeatureCollection();

		geoms: for (Geometry poly : polygons) {
			// if the polygons are not included on the AU zone
			if (!geomSelectedZone.buffer(0.01).contains(poly)) {
				sfBuilder.set("the_geom", poly);
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
				write.add(sfBuilder.buildFeature(null));
			}
		}
		String geometryOutputName = "";
		try {
			geometryOutputName = write.getSchema().getGeometryDescriptor().getLocalName();
		} catch (NullPointerException e) {
			// no parts are outside the zones, so we automaticaly set the geo name attribute
			// with the most used one
			geometryOutputName = "the_geom";
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
				sfBuilder.set("eval", "0");
				sfBuilder.set("DoWeSimul", false);
				sfBuilder.set("SPLIT", 1);
				// @warning the AU Parcels are mostly unbuilt, but maybe not?
				sfBuilder.set("IsBuild", false);
				sfBuilder.set("U", false);
				sfBuilder.set("AU", true);
				sfBuilder.set("NC", false);
				// avoid multi geom bugs

				Geometry intersectedGeom = Vectors
						.scaledGeometryReductionIntersection(Arrays.asList((Geometry) zone.getDefaultGeometry(), unionParcel));

				if (!intersectedGeom.isEmpty()) {
					write = Vectors.addSimpleGeometry(sfBuilder, write, geometryOutputName, intersectedGeom);
				} else {
					System.out.println("this intersection is empty");
					write = Vectors.addSimpleGeometry(sfBuilder, write, geometryOutputName, intersectedGeom);
				}
				numZone++;
			}
		} catch (Exception problem) {
			problem.printStackTrace();
		} finally {
			it.close();
		}

		shpDSZone.dispose();
		SimpleFeatureCollection toSplit = Vectors.delTinyParcels(write.collection(), 5.0);
		double roadEpsilon = 00;
		double noise = 0;
		// Sometimes it bugs (like on Sector NV in Besançon)
		SimpleFeatureCollection splitedZoneParcels = ParcelSplit.splitParcels(toSplit, maximalArea, maximalWidth, roadEpsilon, noise, null, lenRoad,
				false, decompositionLevelWithoutRoad, tmpFolder);

		// Finally, put them all features in a same collec
		// mup output
		ShapefileDataStore mupSDS = new ShapefileDataStore(mupOutput.toURI().toURL());
		SimpleFeatureCollection mupSFC = mupSDS.getFeatureSource().getFeatures();

		
		SimpleFeatureIterator finalIt = splitedZoneParcels.features();
		try {
			while (finalIt.hasNext()) {
				SimpleFeature feat = finalIt.next();
				// erase soon to be erased super thin polygons TODO one is in double and have
				// unknown parameters : how to delete this one?
				if (((Geometry) feat.getDefaultGeometry()).getArea() > 5.0) {
					// set if the parcel is simulable or not
					if (allOrCell) {
						// must be contained into the zones
						if (geomSelectedZone.buffer(1).contains((Geometry) feat.getDefaultGeometry())) {
							double eval = ParcelState.getEvalInParcel(feat, mupSFC);
							if (eval == 0.0) {
								eval = ParcelState.getCloseEvalInParcel(feat, mupSFC);
							}
							feat.setAttribute("DoWeSimul", "true");
							feat.setAttribute("eval", eval);
						} else {
							feat.setAttribute("DoWeSimul", "false");
							feat.setAttribute("eval", "0.0");

						}
					} else {
						if (ParcelState.isParcelInCell(feat, mupSFC)) {
							feat.setAttribute("DoWeSimul", "true");
							feat.setAttribute("eval", ParcelState.getEvalInParcel(feat, mupSFC));
						} else {
							feat.setAttribute("DoWeSimul", "false");
							feat.setAttribute("eval", "0.0");
						}
					}

					// if the parcel is already build, no simulation
					boolean iPB = ParcelState.isAlreadyBuilt(batiFile, feat, unionParcel);
					if (iPB) {
						feat.setAttribute("DoWeSimul", "false");
						feat.setAttribute("IsBuild", "true");
						feat.setAttribute("eval", "0.0");
					} else {
						feat.setAttribute("IsBuild", "false");
					}

					SimpleFeatureBuilder finalParcelBuilder = ParcelSchema.setSFBParcelWithFeat(feat, schema);

					if (feat.getAttribute("CODE") == null) {
						finalParcelBuilder = Schema.setSFBParDefaut(feat, schema, geometryOutputName);
					}
					savedParcels.add(finalParcelBuilder.buildFeature(null));
				}
			}
		} catch (Exception problem) {
			problem.printStackTrace();
		} finally {
			finalIt.close();
		}
		mupSDS.dispose();
		SimpleFeatureCollection result = Vectors.delTinyParcels(savedParcels.collection(), 5.0);

		Vectors.exportSFC(result, new File(tmpFolder, "parcelFinal.shp"));

		return result;

	}
}
