package fr.ign.artiscales.goal;

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
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.PrecisionModel;
import org.locationtech.jts.precision.GeometryPrecisionReducer;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.AttributeDescriptor;
import org.opengis.filter.FilterFactory2;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.NoSuchAuthorityCodeException;

import fr.ign.artiscales.decomposition.ParcelSplit;
import fr.ign.artiscales.fields.GeneralFields;
import fr.ign.artiscales.parcelFunction.MarkParcelAttributeFromPosition;
import fr.ign.artiscales.parcelFunction.ParcelAttribute;
import fr.ign.artiscales.parcelFunction.ParcelCollection;
import fr.ign.artiscales.parcelFunction.ParcelSchema;
import fr.ign.cogit.FeaturePolygonizer;
import fr.ign.cogit.geoToolsFunctions.vectors.Collec;
import fr.ign.cogit.geoToolsFunctions.vectors.Geom;

/**
 * Simulation following this goal operates on a zone rather than on parcels. Zones are usually taken from a zoning plan and their integration is made with the
 * {@link #createZoneToCut(String, SimpleFeatureCollection, SimpleFeatureCollection)} method. The parcel which are across the zone are cut and the parts that aren't contained into
 * the zone are kept with their attributes. The chosen parcel division process (OBB by default) is then applied on the zone.
 * 
 * @author Maxime Colomb
 *
 */
public class ZoneDivision {
	/**
	 * The process used to divide the parcels
	 */
	public static String PROCESS = "OBB";
	/**
	 * If true, will save a shapefile containing only the simulated parcels in the temporary folder.
	 */
	public static boolean SAVEINTERMEDIATERESULT = false;
	/**
	 * If true, overwrite the output saved shapefiles. If false, happend the simulated parcels to a potential already existing shapefile.
	 */
	public static boolean OVERWRITESHAPEFILES = true;
	/**
	 * If true, will save all the intermediate results in the temporary folder
	 */
	public static boolean DEBUG = false;

	/**
	 * Merge and recut a specific zone. Cut first the surrounding parcels to keep them unsplit, then split the zone parcel and remerge them all into the original parcel file A bit
	 * complicated algorithm to deal with non-existing pieces of parcels (as road).
	 * 
	 * @overwrite for a single road size.
	 * @param initialZone
	 *            Zone which will be used to cut parcels. Will cut parcels that intersects them and keep their infos. Will then fill the empty spaces in between the zones and feed
	 *            it to the OBB algorithm.
	 * @param parcels
	 *            {@link SimpleFeatureCollection} of the unmarked parcels.
	 * @param tmpFolder
	 *            Folder to stock temporary files
	 * @param zoningFile
	 *            Shapefile of the zoning plan
	 * @param maximalArea
	 *            Area under which a parcel won"t be anymore cut
	 * @param minimalArea
	 *            Area under which a polygon won't be kept as a parcel
	 * @param maximalWidth
	 *            The width of parcel connection to street network under which the parcel won"t be anymore cut
	 * @param streetWidth
	 *            the width of generated street network. this @overload is setting a single size for those roads
	 * @param decompositionLevelWithoutStreet
	 *            Number of the final row on which street generation doesn't apply
	 * @return The input parcel {@link SimpleFeatureCollection} with the marked parcels replaced by the simulated parcels. All parcels have the
	 *         {@link fr.ign.artiscales.parcelFunction.ParcelSchema#getSFBMinParcel()} schema.
	 * @throws Exception
	 */
	public static SimpleFeatureCollection zoneDivision(SimpleFeatureCollection initialZone, SimpleFeatureCollection parcels, File tmpFolder,
			File zoningFile, double maximalArea, double minimalArea, double maximalWidth, double streetWidth, int decompositionLevelWithoutRoad)
			throws Exception {
		return zoneDivision(initialZone, parcels, tmpFolder, zoningFile, maximalArea, minimalArea, maximalWidth, streetWidth, 999, streetWidth,
				decompositionLevelWithoutRoad);
	}

	/**
	 * Merge and recut a specific zone. Cut first the surrounding parcels to keep them unsplit, then split the zone parcel and remerge them all into the original parcel file A bit
	 * complicated algorithm to deal with non-existing pieces of parcels (as road).
	 * 
	 * @param initialZone
	 *            Zone which will be used to cut parcels. Will cut parcels that intersects them and keep their infos. Will then fill the empty spaces in between the zones and feed
	 *            it to the OBB algorithm.
	 * @param parcels
	 *            {@link SimpleFeatureCollection} of the unmarked parcels.
	 * @param tmpFolder
	 *            Folder to stock temporary files
	 * @param zoningFile
	 *            Shapefile of the zoning plan
	 * @param maximalArea
	 *            Area under which a parcel won"t be anymore cut
	 * @param minimalArea
	 *            Area under which a polygon won't be kept as a parcel
	 * @param maximalWidth
	 *            The width of parcel connection to street network under which the parcel won"t be anymore cut
	 * @param smallStreetWidth
	 *            : the width of small street network segments
	 * @param largeStreetLevel
	 *            : level of decomposition after which the streets are considered as large streets
	 * @param largeStreetWidth
	 *            : the width of large street network segments
	 * @param decompositionLevelWithoutStreet
	 *            : Number of the final row on which street generation doesn't apply
	 * @return
	 * @throws Exception
	 */
	public static SimpleFeatureCollection zoneDivision(SimpleFeatureCollection initialZone, SimpleFeatureCollection parcels, File tmpFolder,
			File zoningFile, double maximalArea, double minimalArea, double maximalWidth, double smallStreetWidth, int largeStreetLevel,
			double largeStreetWidth, int decompositionLevelWithoutStreet) throws Exception {
		return zoneDivision(initialZone, parcels, tmpFolder, zoningFile, maximalArea, minimalArea, maximalWidth, smallStreetWidth, largeStreetLevel,
				largeStreetWidth, decompositionLevelWithoutStreet, 0, 0);
	}

	public static SimpleFeatureCollection zoneDivision(SimpleFeatureCollection initialZone, SimpleFeatureCollection parcels, File tmpFolder,
			File zoningFile, double maximalArea, double minimalArea, double maximalWidth, double smallStreetWidth, int largeStreetLevel, double largeStreetWidth, int decompositionLevelWithoutStreet, double roadEpsilon ,double noise)
			throws Exception {

		// parcel geometry name for all
		String geomName = parcels.getSchema().getGeometryDescriptor().getLocalName();
		final Geometry geomZone = Geom.unionSFC(initialZone);
		final SimpleFeatureBuilder finalParcelBuilder = ParcelSchema.getSFBMinParcel();
		// sort in two different collections, the ones that matters and the ones that will be saved for future purposes
		DefaultFeatureCollection parcelsInZone = new DefaultFeatureCollection();
		// parcels to save for after and convert them to the minimal attribute
		DefaultFeatureCollection savedParcels = new DefaultFeatureCollection();
		Arrays.stream(parcels.toArray(new SimpleFeature[0])).forEach(parcel -> {
			if (((Geometry) parcel.getDefaultGeometry()).intersects(geomZone)) {
				parcelsInZone.add(ParcelSchema.setSFBMinParcelWithFeat(parcel, finalParcelBuilder.getFeatureType()).buildFeature(null));
			} else {
				savedParcels.add(ParcelSchema.setSFBMinParcelWithFeat(parcel, finalParcelBuilder.getFeatureType()).buildFeature(null));
			}
		});
		// complete the void left by the existing roads from the zones
		// Also assess a section number 
		SimpleFeatureBuilder sfBuilder = ParcelSchema.getSFBMinParcelSplit();
		SimpleFeatureBuilder originalSFB = new SimpleFeatureBuilder(savedParcels.getSchema());

		int numZone = 0;
		Geometry unionParcel = Geom.unionSFC(parcels);
		DefaultFeatureCollection goOdZone = new DefaultFeatureCollection();
		try (SimpleFeatureIterator zoneIt = initialZone.features()) {
			while (zoneIt.hasNext()) {
				numZone++;
				SimpleFeature feat = zoneIt.next();
				// avoid most of tricky geometry problems
				Geometry intersection = Geom.scaledGeometryReductionIntersection(Arrays.asList(((Geometry) feat.getDefaultGeometry()), unionParcel));
				if (!intersection.isEmpty()) {
					List<Geometry> geomsZone = Geom.getPolygons(intersection);
					for (Geometry geomPartZone : geomsZone) {
						Geometry geom = GeometryPrecisionReducer.reduce(geomPartZone, new PrecisionModel(100));
						sfBuilder.set(geomName, geom);
						sfBuilder.set(ParcelSchema.getMinParcelSectionField(), "New" + numZone + "Section");
						sfBuilder.set(MarkParcelAttributeFromPosition.getMarkFieldName(), 1);
						goOdZone.add(sfBuilder.buildFeature(null));
					}
				}
			}
		} catch (Exception problem) {
			problem.printStackTrace();
		}
		// zone verification
		if (goOdZone.isEmpty() || Collec.area(goOdZone) < minimalArea) {
			System.out.println("ZoneDivision: no zones to cut or zone is too small to be taken into consideration");
			return parcels;
		}

		// parts of parcel outside the zone must not be cut by the algorithm and keep their attributes
		// temporary shapefiles that serves to do polygons with the polygonizer
		File fParcelsInAU = Collec.exportSFC(parcelsInZone, new File(tmpFolder, "parcelCible.shp"));
		File fZone = Collec.exportSFC(goOdZone, new File(tmpFolder, "oneAU.shp"));
		Geometry geomSelectedZone = Geom.unionSFC(goOdZone);
		File[] polyFiles = { fParcelsInAU, fZone };
		List<Polygon> polygons = FeaturePolygonizer.getPolygons(polyFiles);

		// big loop on each generated geometry to save the parts that are not contained in the zones. We add them to the savedParcels collection.
		for (Geometry poly : polygons) {
			// if the polygons are not included on the AU zone, we check to which parcel do they belong
			if (!geomSelectedZone.buffer(0.01).contains(poly)) {
				try (SimpleFeatureIterator parcelIt = parcelsInZone.features()){
					while (parcelIt.hasNext()) {
						SimpleFeature feat = parcelIt.next();
						// if that original parcel contains that piece of parcel, we copy the previous parcels informations
						if (((Geometry) feat.getDefaultGeometry()).buffer(0.01).contains(poly)) {
							originalSFB.set(geomName, poly);
							for (AttributeDescriptor attr : feat.getFeatureType().getAttributeDescriptors()) {
								if (attr.getLocalName().equals(geomName))
									continue;
								originalSFB.set(attr.getName(), feat.getAttribute(attr.getName()));
							}
							savedParcels.add(originalSFB.buildFeature(null));
						}
					}
				} catch (Exception problem) {
					problem.printStackTrace();
				} 
			}
		}

		if (DEBUG) {
			Collec.exportSFC(goOdZone, new File(tmpFolder, "goOdZone.shp"));
		}
		
		// Parcel subdivision
		SimpleFeatureCollection splitedParcels  = new DefaultFeatureCollection();
		switch (PROCESS) {
		case "OBB":
			splitedParcels = ParcelSplit.splitParcels(goOdZone, maximalArea, maximalWidth, roadEpsilon, noise, null, smallStreetWidth,
					largeStreetLevel, largeStreetWidth, false, decompositionLevelWithoutStreet, tmpFolder);
			break;
		case "SS":
			System.out.println("not implemented yet");
			break;
		case "MS":
			System.out.println("not implemented yet");
			break;
		}
		
		//merge the small parcels to bigger ones
		splitedParcels = ParcelCollection.mergeTooSmallParcels(splitedParcels, (int) minimalArea);
		DefaultFeatureCollection result = new DefaultFeatureCollection();
		
		int num = 0;
		//fix attribute for the simulated parcels
		try (SimpleFeatureIterator itParcel = splitedParcels.features()) {
			while (itParcel.hasNext()) {
				SimpleFeature parcel = itParcel.next();
				Geometry parcelGeom = (Geometry) parcel.getDefaultGeometry();
				finalParcelBuilder.set(geomName, parcelGeom);
				// get the section name
				String section = "";
				try (SimpleFeatureIterator goOdZoneIt = goOdZone.features()) {
					while (goOdZoneIt.hasNext()) {
						SimpleFeature zone = goOdZoneIt.next();
						if (((Geometry) zone.getDefaultGeometry()).buffer(2).contains(parcelGeom)) {
							section = (String) zone.getAttribute(ParcelSchema.getMinParcelSectionField());
							break;
						}
					}
				} catch (Exception problem) {
					problem.printStackTrace();
				}
				finalParcelBuilder.set(ParcelSchema.getMinParcelSectionField(), section);
				finalParcelBuilder.set(ParcelSchema.getMinParcelCommunityField(), ParcelAttribute.getCommunityCodeFromSFC(parcelsInZone, parcel));
				finalParcelBuilder.set(ParcelSchema.getMinParcelNumberField(), String.valueOf(num++));
				result.add(finalParcelBuilder.buildFeature(null));
			}
		} catch (Exception problem) {
			problem.printStackTrace();
		} 
		
		if (SAVEINTERMEDIATERESULT || DEBUG) {
			Collec.exportSFC(result, new File(tmpFolder,"parcelZoneDivisionOnly"), OVERWRITESHAPEFILES);
			OVERWRITESHAPEFILES = false;
		}
		// add the saved parcels
		SimpleFeatureType schemaParcel = finalParcelBuilder.getFeatureType();
		int i = 0;
		try (SimpleFeatureIterator itSavedParcels = savedParcels.features()){
			while (itSavedParcels.hasNext()) {
				SimpleFeatureBuilder parcelBuilder = ParcelSchema.setSFBMinParcelWithFeat(itSavedParcels.next(), schemaParcel);
				result.add(parcelBuilder.buildFeature(Integer.toString(i++)));
			}
		} catch (Exception problem) {
			problem.printStackTrace();
		}
		return result;
	}

	public static SimpleFeatureCollection createZoneToCut(String genericZone, SimpleFeatureCollection zoning, SimpleFeatureCollection parcels)
			throws IOException, NoSuchAuthorityCodeException, FactoryException {
		return createZoneToCut(genericZone, null, zoning, parcels);
	}
	
	/**
	 * Create a zone to cut by selecting features from a shapefile regarding a fixed value. Name of the field is by default set to <i>TYPEZONE</i> and must be changed if needed
	 * with the {@link fr.ign.artiscales.fields.GeneralFields#setZoneGenericNameField(String)} method. Also takes a bounding SimpleFeatureCollection to bound the output.
	 * 
	 * @param genericZone
	 *            Name of the generic zone to be cut
	 * @param preciseZone
	 *            Name of the precise zone to be cut. Can be null
	 * @param zoning
	 *            {@link SimpleFeatureCollection} of zones to extract the wanted zone from (usually a zoning plan)
	 * @param parcels
	 *            {@link SimpleFeatureCollection} of parcel to bound the process on a wanted location
	 * @return An extraction of the zoning collection
	 * @throws IOException
	 * @throws FactoryException
	 * @throws NoSuchAuthorityCodeException
	 */
	public static SimpleFeatureCollection createZoneToCut(String genericZone,String preciseZone, SimpleFeatureCollection zoning, SimpleFeatureCollection parcels)
			throws IOException, NoSuchAuthorityCodeException, FactoryException {
		// get the wanted zones from the zoning file
		FilterFactory2 ff = CommonFactoryFinder.getFilterFactory2(GeoTools.getDefaultHints());
		SimpleFeatureCollection finalZone = zoning.subCollection(ff.like(ff.property(GeneralFields.getZoneGenericNameField()), genericZone))
				.subCollection(ff.intersects(ff.property(zoning.getSchema().getGeometryDescriptor().getLocalName()), ff.literal(Geom.unionSFC(parcels))));
		if (preciseZone != null && preciseZone != "") {
			finalZone  = finalZone.subCollection(ff.like(ff.property(GeneralFields.getZonePreciseNameField()), preciseZone));
		}
		finalZone = MarkParcelAttributeFromPosition.markAllParcel(finalZone);
		if (finalZone.isEmpty()) {
			System.out.println("createZoneToCut(): zone is empty");
		}
		return finalZone;
	}
}