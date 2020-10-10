package fr.ign.artiscales.pm.parcelFunction;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.geotools.data.DataStore;
import org.geotools.data.collection.SpatialIndexFeatureCollection;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.factory.CommonFactoryFinder;
import org.geotools.feature.DefaultFeatureCollection;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.locationtech.jts.algorithm.match.HausdorffSimilarityMeasure;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.TopologyException;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.AttributeDescriptor;
import org.opengis.filter.FilterFactory2;
import org.opengis.filter.expression.PropertyName;

import fr.ign.artiscales.pm.fields.artiscales.ArtiScalesSchemas;
import fr.ign.artiscales.tools.FeaturePolygonizer;
import fr.ign.artiscales.tools.geoToolsFunctions.Attribute;
import fr.ign.artiscales.tools.geoToolsFunctions.Schemas;
import fr.ign.artiscales.tools.geoToolsFunctions.vectors.Collec;
import fr.ign.artiscales.tools.geoToolsFunctions.vectors.Geom;
import fr.ign.artiscales.tools.geoToolsFunctions.vectors.Geopackages;
import fr.ign.artiscales.tools.geoToolsFunctions.vectors.geom.Polygons;

public class ParcelCollection {

//	public static void main(String[] args) throws Exception {
//		File rootFile = new File("src/main/resources/ParcelComparison/");
//		sortDifferentParcel(new File(rootFile,"parcel2003.gpkg"), new File(rootFile,"parcel2018.gpkg"), new File("/tmp/"));
//	}
	
	/**
	 * Method that compares two set of parcel plans and sort the reference parcel plan with the ones that changed and the ones that doesn't. We compare the parcels area of the
	 * reference parcel to the ones that are intersected. If they are similar with a 3% error rate, we conclude that they are the same.
	 * 
	 * This method creates four geographic files (shapefile or geopackages, regarding the projects default format) in the parcelOutFolder:
	 * <ul>
	 * <li><b>same</b> contains the reference parcels that have not evolved</li>
	 * <li><b>notSame</b> contains the reference parcels that have evolved</li>
	 * <li><b>place</b> contains the <i>notSame</i> parcels with a reduction buffer, used for a precise intersection with other parcel in Parcel Manager
	 * scenarios. The large parcels that are selected for a zone simulation (see below) aren't present.</li>
	 * <li><b>zone</b> contains special zones to be simulated</li>
	 * <li><b>evolvedParcel</b> contains only the compared parcels that have evolved</li>
	 * </ul>
	 * 
	 * @param parcelRefFile
	 *            The reference parcel plan
	 * @param parcelToCompareFile
	 *            The parcel plan to compare
	 * @param parcelOutFolder
	 *            Folder where are stored the result geopackages
	 * @throws IOException
	 */
	public static void sortDifferentParcel(File parcelRefFile, File parcelToCompareFile, File parcelOutFolder) throws IOException {
		sortDifferentParcel(parcelRefFile, parcelToCompareFile, parcelOutFolder, 100, 450);
	}

	/**
	 * Method that compares two set of parcel plans and sort the reference parcel plan with the ones that changed and the ones that doesn't. We compare the parcels area of the
	 * reference parcel to the ones that are intersected. If they are similar with a 3% error rate, we conclude that they are the same.
	 * 
	 * This method creates four geographic files (shapefile or geopackages, regarding the projects default format) in the parcelOutFolder:
	 * <ul>
	 * <li><b>same</b> contains the reference parcels that have not evolved</li>
	 * <li><b>notSame</b> contains the reference parcels that have evolved</li>
	 * <li><b>evolvedParcel</b> contains the compared parcels that have evolved</li>
	 * <li><b>zone</b> contains reference parcels that evolved as a zone. Zones are a part of a very large parcels for which a small part has been cut in order to be
	 * urbanized.</li>
	 * <li><b>place</b> contains reference parcels that evolved and aren't a <b>zone</b>. We apply a reduction buffer for a precise intersection with other parcel in Parcel Manager
	 * scenarios.</li>
	 * </ul>
	 * 
	 * @param parcelRefFile
	 *            The reference parcel plan
	 * @param parcelToCompareFile
	 *            The parcel plan to compare
	 * @param parcelOutFolder
	 *            Folder where are stored the result geopackages
	 * @param minParcelSimulatedSize
	 *            The minimal size of parcels of the usual urban fabric profile. If the algorithm is used outside the simulation, default value of 100 square meters is used.
	 * @param maxParcelSimulatedSize
	 *            The maximal size of parcels of the usual urban fabric profile. If the algorithm is used outside the simulation, default value of 450 square meters is used.
	 * @throws IOException
	 */
	public static void sortDifferentParcel(File parcelRefFile, File parcelToCompareFile, File parcelOutFolder, double minParcelSimulatedSize,
			double maxParcelSimulatedSize) throws IOException {
		File fSame = new File(parcelOutFolder, "same"+Collec.getDefaultGISFileType());
		File fEvolved = new File(parcelOutFolder, "evolvedParcel"+Collec.getDefaultGISFileType());
		File fNotSame = new File(parcelOutFolder, "notSame"+Collec.getDefaultGISFileType());
		File fInter = new File(parcelOutFolder, "place"+Collec.getDefaultGISFileType());
		File fZone = new File(parcelOutFolder, "zone"+Collec.getDefaultGISFileType());
		if (fSame.exists() && fEvolved.exists() && fNotSame.exists() && fInter.exists() && fZone.exists()) {
			System.out.println("markDiffParcel(...) already calculated");
			return ;
		}
		
		DataStore ds = Geopackages.getDataStore(parcelToCompareFile);
		SimpleFeatureCollection parcelToSort = new SpatialIndexFeatureCollection(ds.getFeatureSource(ds.getTypeNames()[0]).getFeatures());
		DataStore dsRef = Geopackages.getDataStore(parcelRefFile);
		SimpleFeatureCollection parcelRef = dsRef.getFeatureSource(dsRef.getTypeNames()[0]).getFeatures();
		FilterFactory2 ff = CommonFactoryFinder.getFilterFactory2();
		PropertyName pName = ff.property(parcelRef.getSchema().getGeometryDescriptor().getLocalName());
		SimpleFeatureBuilder intersecPolygon = Schemas.getBasicSchemaMultiPolygon("intersectionPolygon");
		DefaultFeatureCollection same = new DefaultFeatureCollection();
		DefaultFeatureCollection notSame = new DefaultFeatureCollection();
		DefaultFeatureCollection polygonIntersection = new DefaultFeatureCollection();

		// for every reference parcels, we check if an intersection with the intersection compared parcels are +/- 5% of its area and their shapes are similar regarding to the Hausdorf distance mesure
		try (SimpleFeatureIterator itRef = parcelRef.features()) {
			refParcel: while (itRef.hasNext()) {
				SimpleFeature pRef = itRef.next();
				Geometry geomPRef = (Geometry) pRef.getDefaultGeometry();
				double geomArea = geomPRef.getArea();
				//for every intersected parcels, we check if it is close to (as tiny geometry changes)
				SimpleFeatureCollection parcelsIntersectRef = parcelToSort.subCollection(ff.intersects(pName, ff.literal(geomPRef)));
				HausdorffSimilarityMeasure hausDis = new HausdorffSimilarityMeasure();
				try (SimpleFeatureIterator itParcelIntersectRef = parcelsIntersectRef.features()) {
					while (itParcelIntersectRef.hasNext()) {
						Geometry g = (Geometry) itParcelIntersectRef.next().getDefaultGeometry();
						double inter = Geom.scaledGeometryReductionIntersection(Arrays.asList(geomPRef, g)).getArea();
						// if there are parcel intersection and a similar area, we conclude that parcel haven't changed. We put it in the \"same\" collection and stop the search
						if ((inter > 0.95 * geomArea && inter < 1.05 * geomArea) || hausDis.measure(g, geomPRef) > 0.95) {
							same.add(pRef);
							continue refParcel;
						}
					}
				} catch (Exception problem) {
					problem.printStackTrace();
				} 
				//we check if the parcel has been intentionally deleted by generating new polygons (same technique of area comparison, but with a way smaller error bound)
				// if it has been cleaned, we don't add it to no additional parcels
				List<Geometry> geomList = Arrays.stream(parcelsIntersectRef.toArray(new SimpleFeature[0])).map(x -> (Geometry) x.getDefaultGeometry())
						.collect(Collectors.toList());
				geomList.add(geomPRef);
				List<Polygon> polygons = FeaturePolygonizer.getPolygons(geomList);
				for (Polygon polygon : polygons)
					if ((polygon.getArea() > geomArea * 0.9 && polygon.getArea() < geomArea * 1.1) && polygon.buffer(0.5).contains(geomPRef))
						continue refParcel;
				notSame.add(pRef);
				intersecPolygon.set(intersecPolygon.getFeatureType().getGeometryDescriptor().getName(),
						((Geometry) pRef.getDefaultGeometry()).buffer(-2));
				polygonIntersection.add(intersecPolygon.buildFeature(Attribute.makeUniqueId()));
			}
		} catch (Exception problem) {
			problem.printStackTrace();
		} 
		
		// attempt to use Hausdorf mesures to sort better and avoid the cases where parcels have moved a little, but failed attempt. 
//		try (SimpleFeatureIterator itRef = parcelRef.features()){
//			refParcel: while (itRef.hasNext()) {
//				SimpleFeature pRef = itRef.next();
//				Geometry geomPRef = (Geometry) pRef.getDefaultGeometry();
//
//				//for every intersected parcels, we check if it is close to (as tiny geometry changes)
//				SimpleFeatureCollection parcelsComparedIntersectRef = parcelToSort.subCollection(ff.intersects(pName, ff.literal(geomPRef)));
//				try (SimpleFeatureIterator itParcelIntersectRef = parcelsComparedIntersectRef.features()) {
//					while (itParcelIntersectRef.hasNext()) {
//						Geometry comparedGeom = (Geometry) itParcelIntersectRef.next().getDefaultGeometry();
//						// if there are parcel intersection and a similar area, we conclude that parcel haven't changed. We put it in the \"same\" collection and stop the search
//						if (sameCondition(geomPRef, comparedGeom)) {
//							same.add(pRef);
//							continue refParcel;
//						}
//					}
//				} catch (Exception problem) {
//					problem.printStackTrace();
//				} 
//				// we check if the parcel has been intentionally deleted by generating new polygons (same technique of area comparison, but with a way smaller error bound)
//				// if it has been cleaned, we don't add it to no additional parcels
//				List<Geometry> geomList = Arrays.stream(parcelsComparedIntersectRef.toArray(new SimpleFeature[0])).map(x -> (Geometry) x.getDefaultGeometry())
//						.collect(Collectors.toList());
//				geomList.add(geomPRef);
//				// We generate a list of polygons including the filled void. If a similarity is now found and wasn't during the last step, it means the parcel in question has intentionally been deleted
//				List<Polygon> listPoly = FeaturePolygonizer.getPolygons(geomList);
//				// if no nex parcel has been created, we don't do this test
////				if (listPoly.size() != geomList.size()) {
//				Geometry polyPRef = GeometryPrecisionReducer.reduce(geomPRef, new PrecisionModel(100));
//				polyPRef.normalize();	
//				for (Polygon polygon : listPoly) {
//						polygon.normalize();
//						// if a new parcel is now found corresponding to the criterion of similarity, that parcel was intentionaly deleted
//						if (!polygon.equalsTopo(polyPRef) && sameCondition(polyPRef, polygon) ) {
//							System.out.println("for "+polyPRef+" and "+polygon);
//							System.out.println(eq(polyPRef,  polygon));
//							HausdorffSimilarityMeasure hausDis = new HausdorffSimilarityMeasure();
//							System.out.println(hausDis.measure(polyPRef, polygon) > 0.9);
//							System.out.println(polyPRef.getArea() > 0.9 * polygon.getArea() && polyPRef.getArea() < 1.1 * polygon.getArea());
//							continue refParcel;
//						}
//					}
//					}
//				notSame.add(pRef);
//				intersecPolygon.set(intersecPolygon.getFeatureType().getGeometryDescriptor().getName(),	((Geometry) pRef.getDefaultGeometry()).buffer(-2));
//				polygonIntersection.add(intersecPolygon.buildFeature(Attribute.makeUniqueId()));
//			}
//		} catch (Exception problem) {
//			problem.printStackTrace();
//		} 
		Collec.exportSFC(same, fSame);
		Collec.exportSFC(notSame, fNotSame);
		
		// isolate the compared parcels that have changed
		SimpleFeatureCollection evolvedParcel = parcelToSort.subCollection(ff.intersects(pName, ff.literal(Geom.unionSFC(polygonIntersection))));

		// We now seek if a large part of the evolved parcel stays intact and small parts, which represents parcels created for residential development purposes, are generated.
		List<Geometry> notSameMerged = Geom.unionTouchingGeometries(
				Arrays.stream(notSame.toArray(new SimpleFeature[0])).map(x -> (Geometry) x.getDefaultGeometry()).collect(Collectors.toList()));
		List<Geometry> zones = new ArrayList<Geometry>();
		List<Geometry> intersectionGeoms = new ArrayList<Geometry>();
		for (Geometry firstZone : notSameMerged) {
			SimpleFeatureCollection parcelsEvolved = Collec.selectIntersection(evolvedParcel, firstZone);
			// If the area of the tested zone is 10x higher than the maximal simulated parcels (by default, 450m), it could be a 'zone'
			if (firstZone.getArea() > 10 * maxParcelSimulatedSize) {
				DescriptiveStatistics stat = new DescriptiveStatistics();
				//create distribution of area
				Arrays.stream(parcelsEvolved.toArray(new SimpleFeature[0])).forEach(sf -> stat.addValue(((Geometry) sf.getDefaultGeometry()).getArea()));
				// if parcel's area is 20x higher than the median of the distribution
				if (stat.getMax() > stat.getPercentile(50) * 20) {
					List<Geometry> intrestingZones = new ArrayList<Geometry>();
					Arrays.stream(parcelsEvolved.toArray(new SimpleFeature[0])).forEach(sf -> {
						Geometry geom = (Geometry) sf.getDefaultGeometry();
						if (geom.getArea() < 10 * stat.getPercentile(50))
							intrestingZones.add(((Geometry) sf.getDefaultGeometry()));
					});
					List<Geometry> lG = Geom.unionTouchingGeometries(intrestingZones);
					for (Geometry g : lG) {
						if (g.getArea() > 5 * maxParcelSimulatedSize)
							zones.add(g);
						else if (g.getArea() > minParcelSimulatedSize)
							intersectionGeoms.add(g);
					}
				} else
					Arrays.stream(parcelsEvolved.toArray(new SimpleFeature[0])).forEach(sf -> {
						intersectionGeoms.add((Geometry) sf.getDefaultGeometry());
					});
			} 
			// Otherwise, goes to the 'place' collection
			else
				Arrays.stream(parcelsEvolved.toArray(new SimpleFeature[0])).forEach(sf -> {
					intersectionGeoms.add((Geometry) sf.getDefaultGeometry());
				});
		}
		Geom.exportGeom(zones, fZone);
		List<Geometry> listGeom = Geom.unionTouchingGeometries(
				intersectionGeoms.stream().filter(g -> g.getArea() < maxParcelSimulatedSize * 20)
						.collect(Collectors.toList()).stream().map(g -> g.buffer(-1)).collect(Collectors.toList()));
		Geom.exportGeom(listGeom, fInter);
		listGeom.addAll(zones);
		DefaultFeatureCollection finalEvolvedParcels = new DefaultFeatureCollection();
		listGeom.stream().forEach(g -> {finalEvolvedParcels.addAll(Collec.selectIntersection(evolvedParcel, g.buffer(-1)));});
		Collec.exportSFC(finalEvolvedParcels, fEvolved);
		ds.dispose();
		dsRef.dispose();
	}

//	private static boolean sameCondition(Geometry g1, Geometry g2) {
//		HausdorffSimilarityMeasure hausDis = new HausdorffSimilarityMeasure();
//		return hausDis.measure(g1, g2) > 0.92 && g1.getArea() > 0.92 * g2.getArea() && g1.getArea() < 1.08 * g2.getArea();
//	}
//	
//	private static boolean eq(Geometry g1, Geometry g2) {
//		return g1.buffer(0.1).contains(g2) && g2.buffer(0.1).contains(g1);
//	}

	/**
	 * This algorithm merges parcels when they are under an area threshold. It seek the surrounding parcel that share the largest side with the small parcel and merge their
	 * geometries. Parcel must touch at least. If no surrounding parcels are found touching (or intersecting) the small parcel, the parcel is deleted and left as a public space.
	 * Attributes from the large parcel are kept.
	 * 
	 * @param parcelsUnsorted
	 *            {@link SimpleFeatureCollection} to check every parcels
	 * @param minimalParcelSize
	 *            Threshold which parcels are under to be merged
	 * @return The input {@link SimpleFeatureCollection} with small parcels merged or removed
	 * @throws IOException
	 */
	public static SimpleFeatureCollection mergeTooSmallParcels(SimpleFeatureCollection parcelsUnsorted, double minimalParcelSize) throws IOException {
		List<Integer> sizeResults = new ArrayList<Integer>();
		SimpleFeatureCollection result = recursiveMergeTooSmallParcel(parcelsUnsorted, minimalParcelSize);
		sizeResults.add(result.size());
		do {
			// recursive application of the merge algorithm to merge little parcels to big ones one-by-one
			result = recursiveMergeTooSmallParcel(result, minimalParcelSize);
			sizeResults.add(result.size());
		}
		// while parcels are still getting merged, we run the recursive algorithm
		while (!sizeResults.get(sizeResults.size() - 1).equals(sizeResults.get(sizeResults.size() - 2)));
		return result;
	}
	
	private static SimpleFeatureCollection recursiveMergeTooSmallParcel(SimpleFeatureCollection parcelsUnsorted, double minimalParcelSize) throws IOException {
		DefaultFeatureCollection result = new DefaultFeatureCollection();
		// we sort the parcel collection to process the smallest parcels in first
		List<String> ids = new ArrayList<String>();	
		//easy hack to sort parcels by their size
		SortedMap<Double, SimpleFeature> index = new TreeMap<>();
		try (SimpleFeatureIterator itr = parcelsUnsorted.features()) {
			while (itr.hasNext()) {
				SimpleFeature feature = itr.next();
				//get the area an generate random numbers for the last 4 for out of 14 decimal. this hack is done to avoid exaclty same key area and delete some features
				index.put(((Geometry) feature.getDefaultGeometry()).getArea()+Math.random()/1000000, feature);
			}
		} catch (Exception problem) {
			problem.printStackTrace();
		} 
		for (Entry<Double, SimpleFeature> entry : index.entrySet()) {
			SimpleFeature feat = entry.getValue();
			// if the parcel has already been merged with a smaller one, we skip (and we made the hypotheses that a merged parcel will always be bigger than the threshold)
			if (ids.contains(feat.getID())) {
				continue;
			}
			Geometry geom = Polygons.getMultiPolygonGeom((Geometry) feat.getDefaultGeometry());
			if (geom.getArea() < minimalParcelSize) {
				// System.out.println(feat.getID() + " is too small");
				DefaultFeatureCollection intersect = new DefaultFeatureCollection();
				Arrays.stream(parcelsUnsorted.toArray(new SimpleFeature[0])).forEach(interParcel -> {
					if (((Geometry) interParcel.getDefaultGeometry()).intersects(geom) && !interParcel.getID().equals(feat.getID()))
						intersect.add(interParcel);
				});
				// if the small parcel is intersecting others and will be merge to them
				if (intersect.size() > 0) {
					// System.out.println(intersect.size() + " intersecting");
					// if the tiny parcel intersects a bigger parcel, we seek the longest side to which parcel could be incorporated
					HashMap<String, Double> repart = new HashMap<String, Double>();
					Arrays.stream(intersect.toArray(new SimpleFeature[0])).forEach(interParcel -> {
						repart.put(interParcel.getID(),
								Geom.scaledGeometryReductionIntersection(Arrays.asList((Geometry) interParcel.getDefaultGeometry(), geom.buffer(1)))
										.getArea());
					});
					// we sort to place the biggest intersecting parcel in first
					List<Entry<String, Double>> entryList = new ArrayList<Entry<String, Double>>(repart.entrySet());
					Collections.sort(entryList, new Comparator<Entry<String, Double>>() {
						@Override
						public int compare(Entry<String, Double> obj1, Entry<String, Double> obj2) {
							return obj2.getValue().compareTo(obj1.getValue());
						}
					});
					String idToMerge = entryList.get(0).getKey();
					// if the big parcel has already been merged with a small parcel, we skip it and will return to that small parcel in a future iteration
					if (ids.contains(idToMerge)) {
						result.add(Schemas.setSFBSchemaWithMultiPolygon(feat).buildFeature(Attribute.makeUniqueId()));
						continue;
					}
					ids.add(idToMerge);
					// we now merge geometries and copy attributes to the new Feature
					List<Geometry> lG = new ArrayList<Geometry>();
					lG.add(geom);
					SimpleFeatureBuilder build = Schemas.getSFBSchemaWithMultiPolygon(parcelsUnsorted.getSchema());
					Arrays.stream(intersect.toArray(new SimpleFeature[0])).forEach(thaParcel -> {
						if (thaParcel.getID().equals(idToMerge)) {
							for (AttributeDescriptor attr : thaParcel.getFeatureType().getAttributeDescriptors()) {
								if (attr.getLocalName().equals(Collec.getDefaultGeomName()))
									continue;
								build.set(attr.getName(),thaParcel.getAttribute(attr.getName()) );
							}
							lG.add(Polygons.getMultiPolygonGeom((Geometry) thaParcel.getDefaultGeometry()));
						}
					});
					Geometry g;
					try {
						g = Geom.unionGeom(lG);
					} catch (TopologyException tp) {
						System.out.println("problem with +"+lG);
						g = Geom.scaledGeometryReductionIntersection(lG);
					}
					build.set(Collec.getDefaultGeomName(), g);
					SimpleFeature f = build.buildFeature(idToMerge);
					result.add(f);
				}
				// no else - if the small parcel doesn't touch any other parcels, we left it as a blank space and will be left as a public space
			} else
				result.add(Schemas.setSFBSchemaWithMultiPolygon(feat).buildFeature(Attribute.makeUniqueId()));
		}
		return result;
	}
	
	/**
	 * Add a given collection of parcels to another collection of parcel, for which the schema is kept.
	 * @deprecated
	 * @param parcelIn
	 *            Parcels that receive the other parcels
	 * @param parcelAdd
	 *            Parcel to add
	 * @return parcelIn {@link SimpleFeatureCollection} with added parcels
	 */
	public static DefaultFeatureCollection addAllParcels(SimpleFeatureCollection parcelIn, SimpleFeatureCollection parcelAdd) {
		DefaultFeatureCollection result = new DefaultFeatureCollection();
		result.addAll(parcelIn);
		try (SimpleFeatureIterator parcelAddIt = parcelAdd.features()) {
			while (parcelAddIt.hasNext()) {
				SimpleFeature featAdd = parcelAddIt.next();
				SimpleFeatureBuilder fit = ArtiScalesSchemas.setSFBParcelAsASWithFeat(featAdd);
				result.add(fit.buildFeature(Attribute.makeUniqueId()));
			}
		} catch (Exception problem) {
			problem.printStackTrace();
		}
		return result;
	}
	
	
	// public static SimpleFeatureCollection
	// completeParcelMissing(SimpleFeatureCollection parcelTot,
	// SimpleFeatureCollection parcelCuted)
	// throws NoSuchAuthorityCodeException, FactoryException {
	// DefaultFeatureCollection result = new DefaultFeatureCollection();
	// SimpleFeatureType schema = parcelTot.features().next().getFeatureType();
	// // result.addAll(parcelCuted);
	// SimpleFeatureIterator parcelCutedIt = parcelCuted.features();
	// try {
	// while (parcelCutedIt.hasNext()) {
	// SimpleFeature featCut = parcelCutedIt.next();
	// SimpleFeatureBuilder fit = GetFromGeom.setSFBParcelWithFeat(featCut, schema);
	// result.add(fit.buildFeature(null));
	// }
	// } catch (Exception problem) {
	// problem.printStackTrace();
	// } finally {
	// parcelCutedIt.close();
	// }
	//
	// SimpleFeatureIterator totIt = parcelTot.features();
	// try {
	// while (totIt.hasNext()) {
	// SimpleFeature featTot = totIt.next();
	// boolean add = true;
	// SimpleFeatureIterator cutIt = parcelCuted.features();
	// try {
	// while (cutIt.hasNext()) {
	// SimpleFeature featCut = cutIt.next();
	// if (((Geometry)
	// featTot.getDefaultGeometry()).buffer(0.1).contains(((Geometry)
	// featCut.getDefaultGeometry()))) {
	// add = false;
	// break;
	// }
	// }
	// } catch (Exception problem) {
	// problem.printStackTrace();
	// } finally {
	// cutIt.close();
	// }
	// if (add) {
	// SimpleFeatureBuilder fit = GetFromGeom.setSFBParcelWithFeat(featTot, schema);
	// result.add(fit.buildFeature(null));
	// }
	// }
	// } catch (Exception problem) {
	// problem.printStackTrace();
	// } finally {
	// totIt.close();
	// }
	//
	// return result;
	// }
	
//	/**
//	 * @FIXME fix that 
//	 * @param parcelToComplete
//	 * @param originalParcel
//	 * @return
//	 * @throws NoSuchAuthorityCodeException
//	 * @throws FactoryException
//	 * @throws IOException
//	 */
//	public static SimpleFeatureCollection completeParcelMissingWithOriginal(SimpleFeatureCollection parcelToComplete,
//			SimpleFeatureCollection originalParcel) throws NoSuchAuthorityCodeException, FactoryException, IOException {
//		DefaultFeatureCollection result = new DefaultFeatureCollection();
//		result.addAll(parcelToComplete);
//		// List<String> codeParcelAdded = new ArrayList<String>();
//
//		// SimpleFeatureType schema =
//		// parcelToComplete.features().next().getFeatureType();
//
//		// result.addAll(parcelCuted);
//
//		SimpleFeatureIterator parcelToCompletetIt = parcelToComplete.features();
//		try {
//			while (parcelToCompletetIt.hasNext()) {
//				SimpleFeature featToComplete = parcelToCompletetIt.next();
//				Geometry geomToComplete = (Geometry) featToComplete.getDefaultGeometry();
//				Geometry geomsOrigin = Vectors.unionSFC(Vectors.snapDatas(originalParcel, geomToComplete));
//				if (!geomsOrigin.buffer(1).contains(geomToComplete)) {
//					// System.out.println("this parcel has disapeard : " + geomToComplete);
//					// SimpleFeatureBuilder fit = FromGeom.setSFBParcelWithFeat(featToComplete,
//					// schema);
//					// result.add(fit.buildFeature(null));
//					// SimpleFeatureBuilder builder =
//					// FromGeom.setSFBOriginalParcelWithFeat(featToComplete, schema);
//					// result.add(builder.buildFeature(null));
//					// codeParcelAdded.add(ParcelFonction.makeParcelCode(featToComplete));
//				}
//			}
//		} catch (Exception problem) {
//			problem.printStackTrace();
//		} finally {
//			parcelToCompletetIt.close();
//		}
//
//		// SimpleFeatureIterator parcelOriginal = originalParcel.features();
//		// try {
//		// while (parcelOriginal.hasNext()) {
//		// SimpleFeature featOriginal = parcelOriginal.next();
//		// Geometry geom = (Geometry) featOriginal.getDefaultGeometry();
//		// Geometry geomToComplete =
//		// Vectors.unionSFC(Vectors.snapDatas(parcelToComplete, geom.buffer(10)));
//		// if (!geomToComplete.contains(geom.buffer(-1))) {
//		// System.out.println(geomToComplete);
//		// System.out.println();
//		// SimpleFeatureBuilder builder =
//		// FromGeom.setSFBOriginalParcelWithFeat(featOriginal, schema);
//		// result.add(builder.buildFeature(null));
//		// codeParcelAdded.add(ParcelFonction.makeParcelCode(featOriginal));
//		// }
//		// SimpleFeatureBuilder fit = FromGeom.setSFBParcelWithFeat(featOriginal,
//		// schema);
//		// result.add(fit.buildFeature(null));
//		// }
//		// } catch (Exception problem) {
//		// problem.printStackTrace();
//		// } finally {
//		// parcelOriginal.close();
//		// }
//
//		return result;
//	}
/**
 * WARNING: NOT SURE IT'S WORKING
 * @deprecated
 * @param parcelTot
 * @param parcelCuted
 * @param parcelToNotAdd
 * @return completed parcel collection
 * @throws IOException
 */
	public static SimpleFeatureCollection completeParcelMissing(SimpleFeatureCollection parcelTot, SimpleFeatureCollection parcelCuted,
			List<String> parcelToNotAdd) throws IOException {
		DefaultFeatureCollection result = new DefaultFeatureCollection();
		SimpleFeatureType schema = parcelTot.features().next().getFeatureType();
		// result.addAll(parcelCuted);
		try (SimpleFeatureIterator parcelCutedIt = parcelCuted.features()) {
			while (parcelCutedIt.hasNext()) {
				SimpleFeature featCut = parcelCutedIt.next();
				SimpleFeatureBuilder fit = ArtiScalesSchemas.setSFBParcelAsASWithFeat(featCut, schema);
				result.add(fit.buildFeature(null));
			}
		} catch (Exception problem) {
			problem.printStackTrace();
		} 
		try (SimpleFeatureIterator totIt = parcelTot.features()) {
			while (totIt.hasNext()) {
				SimpleFeature featTot = totIt.next();
				boolean add = true;
				for (String code : parcelToNotAdd) {
					if (featTot.getAttribute("CODE").equals(code)) {
						add = false;
						break;
					}
				}
				if (add) {
					SimpleFeatureBuilder fit = ArtiScalesSchemas.setSFBParcelAsASWithFeat(featTot, schema);
					result.add(fit.buildFeature(null));
				}
			}
		} catch (Exception problem) {
			problem.printStackTrace();
		}
		return result.collection();
	}
	
	/**
	 * Sort a parcel collection by the feature's sizes in two collections : the ones that are less a threshold and the ones that are above that threshold
	 * 
	 * @param parcelIn
	 * @param size
	 * @return a pair
	 * @throws IOException
	 */
	public static Pair<SimpleFeatureCollection,SimpleFeatureCollection> sortParcelsBySize(SimpleFeatureCollection parcelIn, double size) throws IOException {
		DefaultFeatureCollection less = new DefaultFeatureCollection();
		DefaultFeatureCollection more = new DefaultFeatureCollection();
		Arrays.stream(parcelIn.toArray(new SimpleFeature[0])).forEach(feat -> {
			if (((Geometry) feat.getDefaultGeometry()).getArea() >= size) {
				more.add(feat);
			} else {
				less.add(feat);
			}
		});
		return new ImmutablePair<SimpleFeatureCollection, SimpleFeatureCollection>(
				less, more);
	}

	/**
	 * WARNING not tested (maybe not needed)
	 * @param parcelToNotAdd
	 * @param bigZoned
	 * @return A LIST
	 */
	public static List<String> dontAddParcel(List<String> parcelToNotAdd, SimpleFeatureCollection bigZoned) {
		try (SimpleFeatureIterator feat = bigZoned.features()) {
			while (feat.hasNext())
				parcelToNotAdd.add((String) feat.next().getAttribute("CODE"));
		} catch (Exception problem) {
			problem.printStackTrace();
		} 
		return parcelToNotAdd;
	}
	
//	/**
//	 * this method aims to select the simulated parcels, the parcel that haven't been selected and if no building have been simulated on the selected and/or cuted parcel, get the
//	 * older ones. This is not finished nor working TODO finish to have beautiful results
//	 * 
//	 * @return
//	 * @throws IOException
//	 */
//	public SimpleFeatureCollection reuniteParcelOGAndSimuled() throws IOException {
//		DefaultFeatureCollection reuniteParcel = new DefaultFeatureCollection();
//
//		ShapefileDataStore parcelOGSDS = new ShapefileDataStore(parcelOGFile.toURI().toURL());
//		SimpleFeatureCollection parcelOG = parcelOGSDS.getFeatureSource().getFeatures();
//		List<String> oGCode = FrenchParcelFields.getFrenchCodeParcels(parcelOG);
//
//		ShapefileDataStore parcelSimuledSDS = new ShapefileDataStore(getParcelDepotGenFile().toURI().toURL());
//		SimpleFeatureCollection parcelSimuled = parcelSimuledSDS.getFeatureSource().getFeatures();
//		List<String> simuledCode = FrenchParcelFields.getFrenchCodeParcels(parcelSimuled);
//
//		List<String> intactParcels = new ArrayList<String>();
//		List<String> cuttedButIntactParcels = new ArrayList<String>();
//		List<String> changedParcels = new ArrayList<String>();
//
//		List<String> simuledParcels = new ArrayList<String>();
//
//		for (String simuC : simuledCode) {
//			if (oGCode.contains(simuC)) {
//				intactParcels.add(simuC);
//			} else {
//				changedParcels.add(simuC);
//			}
//		}
//
//		changedP: for (String changedParcel : simuledCode) {
//			SimpleFeatureIterator itParcel = parcelSimuled.features();
//			try {
//				while (itParcel.hasNext()) {
//					SimpleFeature ft = itParcel.next();
//					String codeTmp = (String) ft.getAttribute("CODE");
//					if (codeTmp.equals(changedParcel)) {
//						// no construction has been simulated in this parcel
//						if (isParcelReallySimulated(ft)) {
//							simuledParcels.add(codeTmp);
//						} else {
//							cuttedButIntactParcels.add(codeTmp);
//						}
//						continue changedP;
//					}
//				}
//			} catch (Exception problem) {
//				problem.printStackTrace();
//			} finally {
//				itParcel.close();
//			}
//		}
//		System.out.println("isolated problematic parcels");
//		DefaultFeatureCollection toMergeIftouch = new DefaultFeatureCollection();
//		SimpleFeatureIterator itParcel = parcelSimuled.features();
//		try {
//			while (itParcel.hasNext()) {
//				SimpleFeature f = itParcel.next();
//				if (cuttedButIntactParcels.contains((String) f.getAttribute("CODE"))) {
//					toMergeIftouch.add(f);
//				}
//			}
//		} catch (Exception problem) {
//			problem.printStackTrace();
//		} finally {
//			itParcel.close();
//		}
//
//		Collec.exportSFC(toMergeIftouch, new File("/tmp/toMergeIfTouch.gpkg"));
//
//		SimpleFeatureIterator ItToMergeIftouch = toMergeIftouch.features();
//
//		try {
//			while (ItToMergeIftouch.hasNext()) {
//				SimpleFeature f = ItToMergeIftouch.next();
//				Geometry aggregate = mergeIfTouch((Geometry) f.getDefaultGeometry(), toMergeIftouch);
//
//				// find attribute infos
//				SimpleFeatureIterator getAttributeIt = Collec.snapDatas(parcelOG, aggregate).features();
//				try {
//					while (getAttributeIt.hasNext()) {
//						SimpleFeature model = getAttributeIt.next();
//						if (((Geometry) model.getDefaultGeometry()).intersects(aggregate)) {
//							SimpleFeatureBuilder sfbuild = ArtiScalesSchemas.setSFBParcelAsASWithFeat(model);
//							sfbuild.set(model.getFeatureType().getGeometryDescriptor().getName().toString(), aggregate);
//							reuniteParcel.add(sfbuild.buildFeature(null));
//							break;
//						}
//
//					}
//				} catch (Exception problem) {
//					problem.printStackTrace();
//				} finally {
//					getAttributeIt.close();
//				}
//
//			}
//		} catch (Exception problem) {
//			problem.printStackTrace();
//		} finally {
//			ItToMergeIftouch.close();
//		}
//
//		Collec.exportSFC(reuniteParcel, new File("/tmp/unitedParcels.gpkg"));
//
//		return toMergeIftouch;
//
//		// maybe some created parcels are made of OG parcels. We then have to make some particular stuff
//	}
//
//	/**
//	 * This method recursively add geometries to a solo one if they touch each other not sure this is working
//	 * 
//	 * not safe at work
//	 * 
//	 * @param geomIn
//	 * @param df
//	 * @return
//	 * @throws IOException
//	 */
//	public Geometry mergeIfTouch(Geometry geomIn, DefaultFeatureCollection df) throws IOException {
//		DefaultFeatureCollection result = new DefaultFeatureCollection();
//		result.addAll(df.collection());
//
//		SimpleFeatureIterator features = df.features();
//
//		Geometry aggreg = geomIn;
//
//		try {
//			while (features.hasNext()) {
//				SimpleFeature f = features.next();
//				Geometry geomTemp = (((Geometry) f.getDefaultGeometry()));
//				if (geomIn.intersects(geomTemp) && !geomIn.equals(geomTemp)) {
//					result.remove(f);
//					aggreg = Geom.unionGeom(geomIn, geomTemp);
//					aggreg = mergeIfTouch(aggreg, result);
//					break;
//				}
//			}
//		} catch (Exception problem) {
//			problem.printStackTrace();
//		} finally {
//			features.close();
//		}
//		return aggreg;
//
//	}
}
