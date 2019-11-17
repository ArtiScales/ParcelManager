package algorithm;

import java.awt.Polygon;
import java.io.File;

import org.geotools.data.shapefile.ShapefileDataStore;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.feature.DefaultFeatureCollection;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.geotools.referencing.CRS;
import org.locationtech.jts.geom.Geometry;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

import fr.ign.cogit.Schema;
import fr.ign.cogit.GTFunctions.Vectors;
import fr.ign.cogit.parcelFunction.ParcelAttribute;
import fr.ign.cogit.parcelFunction.ParcelState;
import processus.ParcelSplit;

public class ParcelPartRecomp {

	// // /////////////////////////
	// // //////// try the parcelGenMotif method
	// /////////////////////////
	//
	// ShapefileDataStore shpDSZone = new ShapefileDataStore(
	// new
	// File("/home/mcolomb/informatique/ArtiScales/ParcelSelectionFile/exScenar/variant0/parcelGenExport.shp").toURI().toURL());
	// SimpleFeatureCollection featuresZones =
	// shpDSZone.getFeatureSource().getFeatures();
	//
	// // Vectors.exportSFC(generateSplitedParcels(waiting, tmpFile, p), new
	// // File("/tmp/tmp2.shp"));
	// SimpleFeatureCollection salut = parcelGenMotif("NC", featuresZones, tmpFile,
	// ), new File(
	// "/home/mcolomb/informatique/ArtiScales/MupCityDepot/exScenar/variant0/exScenar-DataSys-CM20.0-S0.0-GP_915948.0_6677337.0--N6_Ba_ahpx_seed_42-evalAnal-20.0.shp"),
	// 800.0, 7.0, 3.0, 2);
	//
	// Vectors.exportSFC(salut, new File("/tmp/parcelDensification.shp"));
	// shpDSZone.dispose();
	//

	public static SimpleFeatureCollection parcelPartRecomp(String typeZone, SimpleFeatureCollection parcels, File tmpFile, File zoningFile, File buildingFile,
			File regulFile, File mupOutput, double maximalArea, double maximalWidth, double roadWidth, int decompositionLevelWithoutRoad,
			boolean dontTouchUZones) throws Exception {
		Geometry emprise = Vectors.unionSFC(parcels);

		DefaultFeatureCollection parcelResult = new DefaultFeatureCollection();
		parcelResult.addAll(parcels);

		ShapefileDataStore shpDSCells = new ShapefileDataStore(mupOutput.toURI().toURL());
		SimpleFeatureCollection cellsSFS = shpDSCells.getFeatureSource().getFeatures();
		DefaultFeatureCollection parcelToMerge = new DefaultFeatureCollection();

		// city information
		ShapefileDataStore shpDSCities = new ShapefileDataStore(zoningFile.toURI().toURL());
		SimpleFeatureCollection citiesSFS = shpDSCities.getFeatureSource().getFeatures();

		// Vectors.exportSFC(parcels, new File(tmpFile, "step0.shp"));
		// System.out.println("done step 0");

		////////////////
		// first step : round of selection of the intersected parcels
		////////////////
		// dontTouchUZones is very ugly but i was very tired of it
		SimpleFeatureIterator parcelIt = parcels.features();
		try {
			while (parcelIt.hasNext()) {
				SimpleFeature parcel = parcelIt.next();
				if (dontTouchUZones) {
					if (((boolean) parcel.getAttribute(typeZone)) && !((boolean) parcel.getAttribute("U"))) {
						if (parcel.getAttribute("DoWeSimul").equals("true")) {
							parcelToMerge.add(parcel);
							parcelResult.remove(parcel);
						}
					}
				} else {
					if ((boolean) parcel.getAttribute(typeZone)) {
						if (parcel.getAttribute("DoWeSimul").equals("true")) {
							parcelToMerge.add(parcel);
							parcelResult.remove(parcel);
						}
					}
				}
			}
		} catch (Exception problem) {
			problem.printStackTrace();
		} finally {
			parcelIt.close();
		}

		Vectors.exportSFC(parcelToMerge.collection(), new File(tmpFile, "step1.shp"));
		System.out.println("done step 1");

		////////////////
		// second step : merge of the parcel that touches themselves by lil island
		////////////////

		DefaultFeatureCollection mergedParcels = new DefaultFeatureCollection();
		SimpleFeatureTypeBuilder sfTypeBuilder = new SimpleFeatureTypeBuilder();

		CoordinateReferenceSystem sourceCRS = CRS.decode("EPSG:2154");

		sfTypeBuilder.setName("toSplit");
		sfTypeBuilder.setCRS(sourceCRS);
		sfTypeBuilder.add("the_geom", Polygon.class);
		sfTypeBuilder.add("SPLIT", Integer.class);
		sfTypeBuilder.add("Section", Integer.class);
		sfTypeBuilder.setDefaultGeometry("the_geom");

		SimpleFeatureBuilder sfBuilder = new SimpleFeatureBuilder(sfTypeBuilder.buildFeatureType());

		SimpleFeatureBuilder sfBuilderSimple = Schema.getBasicSFB();

		Geometry multiGeom = Vectors.unionSFC(parcelToMerge);
		for (int i = 0; i < multiGeom.getNumGeometries(); i++) {
			sfBuilder.add(multiGeom.getGeometryN(i));
			sfBuilder.set("Section", i);
			mergedParcels.add(sfBuilder.buildFeature(null));
		}

		SimpleFeatureCollection forSection = mergedParcels.collection();

		Vectors.exportSFC(mergedParcels.collection(), new File(tmpFile, "step2.shp"));
		System.out.println("done step 2");

		////////////////
		// third step : cuting of the parcels
		////////////////

		SimpleFeatureIterator bigParcelIt = mergedParcels.features();
		DefaultFeatureCollection cutParcels = new DefaultFeatureCollection();
		try {
			while (bigParcelIt.hasNext()) {
				SimpleFeature feat = bigParcelIt.next();
				// if the parcel is bigger than the limit size
				if (((Geometry) feat.getDefaultGeometry()).getArea() > maximalArea) {
					// we cut the parcel
					feat.setAttribute("SPLIT", 1);
					SimpleFeatureCollection da = ParcelSplit.splitParcels(feat, maximalArea, maximalWidth, 0, 0, null, roadWidth, false,
							decompositionLevelWithoutRoad, tmpFile, false);
					SimpleFeatureIterator it = da.features();
					// time out coz some parcels are too big
					// System.out.println("sixty seconds");
					// final Duration timeout = Duration.ofSeconds(60);
					// ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);
					//
					// final Future<SimpleFeatureCollection> handler = executor.submit(new Callable() {
					// public SimpleFeatureCollection call() throws Exception {
					// return splitParcels(feat, maximalArea, maximalWidth, 0, 0, null, roadWidth, false,
					// decompositionLevelWithoutRoad, tmpFile, false);
					// }
					// });
					//
					// executor.schedule(new Runnable() {
					// @Override
					// public void run(){
					// handler.cancel(true);
					// }
					// }, timeout.toMillis(), TimeUnit.MILLISECONDS);
					// System.out.println("done ?!");
					//
					// executor.shutdownNow();
					// if (handler.get().isEmpty()) {
					// System.out.println("zobinet");
					// return null;
					// }
					// SimpleFeatureIterator it = handler.get().features();
					while (it.hasNext()) {
						SimpleFeature f = it.next();
						cutParcels.add(sfBuilderSimple.buildFeature(null, new Object[] { f.getDefaultGeometry() }));
					}
					it.close();
				} else {
					cutParcels.add(sfBuilderSimple.buildFeature(null, new Object[] { feat.getDefaultGeometry() }));
				}
			}
		} catch (Exception problem) {
			problem.printStackTrace();
		} finally {
			bigParcelIt.close();
		}
		Vectors.exportSFC(cutParcels.collection(), new File(tmpFile, "step3.shp"));
		System.out.println("done step 3");

		////////////////
		// fourth step : selection of the parcels intersecting the cells
		////////////////

		int i = 0;
		SimpleFeatureIterator parcelFinal = cutParcels.features();
		SimpleFeatureBuilder featureBuilder = new SimpleFeatureBuilder(parcelResult.getSchema());

		try {
			while (parcelFinal.hasNext()) {
				SimpleFeature parcel = parcelFinal.next();
				featureBuilder.add(parcel.getDefaultGeometry());

				// we get the city info
				String insee = ParcelAttribute.getInseeFromParcel(citiesSFS, parcel);

				featureBuilder.set("INSEE", insee);
				featureBuilder.set("CODE_DEP", insee.substring(0, 2));
				featureBuilder.set("CODE_COM", insee.substring(2, 5));

				// that takes time but it's the best way I've found to set a correct section
				// number (to look at the step 2 polygons)
				String sec = "Default";
				SimpleFeatureIterator sectionIt = forSection.features();
				try {
					while (sectionIt.hasNext()) {
						SimpleFeature feat = sectionIt.next();
						if (((Geometry) feat.getDefaultGeometry()).intersects((Geometry) parcel.getDefaultGeometry())) {
							sec = String.valueOf(feat.getAttribute("Section"));
							break;
						}
					}

				} catch (Exception problem) {
					problem.printStackTrace();
				} finally {
					sectionIt.close();
				}

				String section = "newSection" + sec + "Natural";

				featureBuilder.set("SECTION", section);
				featureBuilder.set("NUMERO", i);
				featureBuilder.set("CODE", insee + "000" + section + i);
				featureBuilder.set("COM_ABS", "000");

				boolean iPB = ParcelState.isAlreadyBuilt(buildingFile, parcel, emprise);
				featureBuilder.set("IsBuild", iPB);

				featureBuilder.set("U", false);
				featureBuilder.set("AU", false);
				featureBuilder.set("NC", true);

				if (ParcelState.isParcelInCell(parcel, cellsSFS) && !iPB) {
					featureBuilder.set("DoWeSimul", "true");
					featureBuilder.set("eval", ParcelState.getEvalInParcel(parcel, mupOutput));
				} else {
					featureBuilder.set("DoWeSimul", "false");
					featureBuilder.set("eval", 0);
				}

				parcelResult.add(featureBuilder.buildFeature(String.valueOf(i++)));
			}
		} catch (Exception problem) {
			problem.printStackTrace();
		} finally {
			parcelFinal.close();
		}
		shpDSCells.dispose();
		shpDSCities.dispose();
		shpDSCells.dispose();

		SimpleFeatureCollection result = Vectors.delTinyParcels(parcelResult, 10.0);

		Vectors.exportSFC(result, new File(tmpFile, "step4.shp"));

		return result;
	}
}
