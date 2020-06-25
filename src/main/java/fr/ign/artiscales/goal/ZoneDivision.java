package fr.ign.artiscales.goal;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.feature.DefaultFeatureCollection;
import org.geotools.feature.SchemaException;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.PrecisionModel;
import org.locationtech.jts.precision.GeometryPrecisionReducer;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.AttributeDescriptor;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.NoSuchAuthorityCodeException;

import fr.ign.artiscales.decomposition.OBBBlockDecomposition;
import fr.ign.artiscales.parcelFunction.MarkParcelAttributeFromPosition;
import fr.ign.artiscales.parcelFunction.ParcelAttribute;
import fr.ign.artiscales.parcelFunction.ParcelCollection;
import fr.ign.artiscales.parcelFunction.ParcelSchema;
import fr.ign.cogit.FeaturePolygonizer;
import fr.ign.cogit.geoToolsFunctions.Attribute;
import fr.ign.cogit.geoToolsFunctions.vectors.Collec;
import fr.ign.cogit.geoToolsFunctions.vectors.Geom;
import fr.ign.cogit.geometryGeneration.CityGeneration;
import fr.ign.cogit.parameter.ProfileUrbanFabric;

/**
 * Simulation following this goal operates on a zone rather than on parcels. Zones can either be taken from a zoning plan or from a ready-to-use zone parcel collection. Their
 * integration is anyhow made with the {@link #createZoneToCut(String, SimpleFeatureCollection, File, SimpleFeatureCollection)} method. The parcel which are across the zone are cut
 * and the parts that aren't contained into * the zone are kept with their attributes. The chosen parcel division process (OBB by default) is then applied on the zone.
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
	 * Merge and recut a specific zone. Cut first the surrounding parcels to keep them unsplit, then split the zone parcel and remerge them all into the original parcel file. A bit
	 * complicated algorithm to deal with non-existing pieces of parcels (as road).
	 * 
	 * Overwrite for no noise or harmonyCoeff.
	 * 
	 * @param initialZone
	 *            Zone which will be used to cut parcels. Will cut parcels that intersects them and keep their infos. Will then fill the empty spaces in between the zones and feed
	 *            it to the OBB algorithm.
	 * @param parcels
	 *            {@link SimpleFeatureCollection} of the unmarked parcels.
	 * @param tmpFolder
	 *            Folder to stock temporary files
	 * @param profile
	 *            {@link ProfileUrbanFabric} contains the parameters of the wanted urban scene
	 * @return The input parcel {@link SimpleFeatureCollection} with the marked parcels replaced by the simulated parcels. All parcels have the
	 *         {@link fr.ign.artiscales.parcelFunction.ParcelSchema#getSFBMinParcel()} schema.
	 * @throws SchemaException
	 * @throws IOException
	 * @throws FactoryException
	 * @throws NoSuchAuthorityCodeException
	 */
	public static SimpleFeatureCollection zoneDivision(SimpleFeatureCollection initialZone, SimpleFeatureCollection parcels,
			ProfileUrbanFabric profile, File tmpFolder, File outFolder)
			throws NoSuchAuthorityCodeException, FactoryException, IOException, SchemaException {
		return zoneDivision(initialZone, parcels, tmpFolder, outFolder, profile, 0.5, 0);
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
	 * @param profile
	 *            {@link ProfileUrbanFabric} contains the parameters of the wanted urban scene
	 * @param harmonyCoeff
	 *            coefficient of minimal ration between length and width of the Oriented Bounding Box
	 * @param noise
	 *            level of perturbation
	 * @return The input parcel {@link SimpleFeatureCollection} with the marked parcels replaced by the simulated parcels. All parcels have the
	 *         {@link fr.ign.artiscales.parcelFunction.ParcelSchema#getSFBMinParcel()} schema.
	 * @throws FactoryException
	 * @throws NoSuchAuthorityCodeException
	 * @throws IOException
	 * @throws SchemaException
	 */
	public static SimpleFeatureCollection zoneDivision(SimpleFeatureCollection initialZone, SimpleFeatureCollection parcels, File tmpFolder,
			File outFolder, ProfileUrbanFabric profile, double harmonyCoeff, double noise)
			throws NoSuchAuthorityCodeException, FactoryException, IOException, SchemaException {
		// parcel geometry name for all
		String geomName = parcels.getSchema().getGeometryDescriptor().getLocalName();
		final Geometry geomZone = Geom.unionSFC(initialZone);
		final SimpleFeatureBuilder finalParcelBuilder = ParcelSchema.getSFBMinParcel();
		// sort in two different collections, the ones that matters and the ones that will be saved for future purposes
		DefaultFeatureCollection parcelsInZone = new DefaultFeatureCollection();
		// parcels to save for after and convert them to the minimal attribute
		DefaultFeatureCollection savedParcels = new DefaultFeatureCollection();
		Arrays.stream(parcels.toArray(new SimpleFeature[0])).forEach(parcel -> {
			if (((Geometry) parcel.getDefaultGeometry()).intersects(geomZone))
				parcelsInZone.add(
						ParcelSchema.setSFBMinParcelWithFeat(parcel, finalParcelBuilder.getFeatureType()).buildFeature(Attribute.makeUniqueId()));
			else
				savedParcels.add(
						ParcelSchema.setSFBMinParcelWithFeat(parcel, finalParcelBuilder.getFeatureType()).buildFeature(Attribute.makeUniqueId()));
		});
		// complete the void left by the existing roads from the zones
		// Also assess a section number
		SimpleFeatureBuilder sfBuilder = ParcelSchema.getSFBMinParcelSplit();
		SimpleFeatureBuilder originalSFB = new SimpleFeatureBuilder(savedParcels.getSchema());
		if (DEBUG)
			Collec.exportSFC(parcelsInZone, new File(tmpFolder, "parcelsInZone.shp"));
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
						// avoid silvers (plants the code)
						if (geom.getArea() > 10) {
							sfBuilder.set(geomName, geom);
							sfBuilder.set(ParcelSchema.getMinParcelSectionField(), makeNewSection(numZone));
							sfBuilder.set(MarkParcelAttributeFromPosition.getMarkFieldName(), 1);
							goOdZone.add(sfBuilder.buildFeature(Attribute.makeUniqueId()));
						}
					}
				}
			}
		} catch (Exception problem) {
			problem.printStackTrace();
		}
		// zone verification
		if (goOdZone.isEmpty() || Collec.area(goOdZone) < profile.getMinimalArea()) {
			System.out.println("ZoneDivision: no zones to cut or zone is too small to be taken into consideration");
			return parcels;
		}
		// parts of parcel outside the zone must not be cut by the algorithm and keep their attributes
		// temporary shapefiles that serves to do polygons with the polygonizer
		File fParcelsInAU = Collec.exportSFC(parcelsInZone, new File(tmpFolder, "parcelCible.shp"));
		File fZone = Collec.exportSFC(goOdZone, new File(tmpFolder, "oneAU.shp"));
		File[] polyFiles = { fParcelsInAU, fZone };
		List<Polygon> polygons = FeaturePolygonizer.getPolygons(polyFiles);
		// apparently less optimized... but nicer
		// List<Geometry> geomList = Arrays.stream(parcels.toArray(new SimpleFeature[0])).map(x -> (Geometry) x.getDefaultGeometry())
		// .collect(Collectors.toList());
		// geomList.addAll(Arrays.stream(parcels.toArray(new SimpleFeature[0])).map(x -> (Geometry) x.getDefaultGeometry()).collect(Collectors.toList()));
		// List<Polygon> polygons = FeaturePolygonizer.getPolygons(geomList);
		Geometry geomSelectedZone = Geom.unionSFC(goOdZone);
		if (DEBUG) {
			Geom.exportGeom(geomSelectedZone, new File(tmpFolder, "geomSelectedZone"));
			Geom.exportGeom(polygons, new File(tmpFolder, "polygons"));
		}
		// big loop on each generated geometry to save the parts that are not contained in the zones. We add them to the savedParcels collection.
		for (Geometry poly : polygons) {
			// if the polygons are not included on the AU zone, we check to which parcel do they belong
			if (!geomSelectedZone.buffer(0.01).contains(poly)) {
				try (SimpleFeatureIterator parcelIt = parcelsInZone.features()) {
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
							savedParcels.add(originalSFB.buildFeature(Attribute.makeUniqueId()));
						}
					}
				} catch (Exception problem) {
					problem.printStackTrace();
				}
			}
		}
		// Parcel subdivision
		SimpleFeatureCollection splitedParcels = new DefaultFeatureCollection();
		SimpleFeatureCollection isletCollection = CityGeneration.createUrbanIslet(parcels);
		try (SimpleFeatureIterator it = goOdZone.features()) {
			while (it.hasNext()) {
				SimpleFeature zone = it.next();
				DefaultFeatureCollection tmpZoneToCut = new DefaultFeatureCollection();
				tmpZoneToCut.add(zone);
				switch (PROCESS) {
				case "OBB":
					((DefaultFeatureCollection) splitedParcels).addAll(OBBBlockDecomposition.splitParcels(tmpZoneToCut, null,
							profile.getMaximalArea(), profile.getMinimalWidthContactRoad(), harmonyCoeff, noise,
							Collec.fromPolygonSFCtoListRingLines(Collec.snapDatas(isletCollection, (Geometry) zone.getDefaultGeometry())),
							profile.getStreetWidth(), profile.getLargeStreetLevel(), profile.getLargeStreetWidth(), true,
							profile.getDecompositionLevelWithoutStreet()));
					break;
				case "SS":
					System.out.println("not implemented yet");
					break;
				case "MS":
					System.out.println("not implemented yet");
					break;
				}
			}
		} catch (Exception problem) {
			problem.printStackTrace();
		}
		if (DEBUG)
			Collec.exportSFC(splitedParcels, new File(tmpFolder, "freshSplitedParcels"));
		// merge the small parcels to bigger ones
		splitedParcels = ParcelCollection.mergeTooSmallParcels(splitedParcels, (int) profile.getMinimalArea());
		DefaultFeatureCollection result = new DefaultFeatureCollection();
		int num = 0;
		// fix attribute for the simulated parcels
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
				result.add(finalParcelBuilder.buildFeature(Attribute.makeUniqueId()));
			}
		} catch (Exception problem) {
			problem.printStackTrace();
		}
		if (DEBUG)
			Collec.exportSFC(result, new File(tmpFolder, "parcelZoneDivisionOnly.shp"), false);
		if (SAVEINTERMEDIATERESULT) {
			Collec.exportSFC(result, new File(outFolder, "parcelZoneDivisionOnly.shp"), OVERWRITESHAPEFILES);
			OVERWRITESHAPEFILES = false;
		}
		// add the saved parcels
		SimpleFeatureType schemaParcel = finalParcelBuilder.getFeatureType();
		try (SimpleFeatureIterator itSavedParcels = savedParcels.features()) {
			while (itSavedParcels.hasNext()) {
				SimpleFeatureBuilder parcelBuilder = ParcelSchema.setSFBMinParcelWithFeat(itSavedParcels.next(), schemaParcel);
				result.add(parcelBuilder.buildFeature(Attribute.makeUniqueId()));
			}
		} catch (Exception problem) {
			problem.printStackTrace();
		}
		return result;
	}

	/**
	 * Create a zone to cut by selecting features from a shapefile regarding a fixed value. Name of the field is by default set to <i>TYPEZONE</i> and must be changed if needed
	 * with the {@link fr.ign.artiscales.fields.GeneralFields#setZoneGenericNameField(String)} method. Name of a Generic Zone is provided and can be null. If null, inputSFC is
	 * usually directly a ready-to-use zone and all given zone are marked. Also takes a bounding {@link SimpleFeatureCollection} to bound the output.
	 * 
	 * @param genericZone
	 *            Name of the generic zone to be cut
	 * @param inputSFC
	 *            ShapeFile of zones to extract the wanted zone from (usually a zoning plan)
	 * @param zoningFile
	 *            The File containing the zoning plan (can be null if no zoning plan is planned to be used)
	 * @param boundingSFC
	 *            {@link SimpleFeatureCollection} to bound the process on a wanted location
	 * @return An extraction of the zoning collection
	 * @throws IOException
	 * @throws FactoryException
	 * @throws NoSuchAuthorityCodeException
	 */
	public static SimpleFeatureCollection createZoneToCut(String genericZone, SimpleFeatureCollection inputSFC, File zoningFile,
			SimpleFeatureCollection boundingSFC) throws IOException, NoSuchAuthorityCodeException, FactoryException {
		return createZoneToCut(genericZone, null, inputSFC, zoningFile, boundingSFC);
	}

	/**
	 * Create a zone to cut by selecting features from a shapefile regarding a fixed value. Name of the field is by default set to <i>TYPEZONE</i> and must be changed if needed
	 * with the {@link fr.ign.artiscales.fields.GeneralFields#setZoneGenericNameField(String)} method. Name of a <i>generic zone</i> and a <i>precise Zone</i> can be provided and
	 * can be null. If null, inputSFC is usually directly a ready-to-use zone and all given zone are marked. Also takes a bounding {@link SimpleFeatureCollection} to bound the
	 * output.
	 * 
	 * @param genericZone
	 *            Name of the generic zone to be cut
	 * @param preciseZone
	 *            Name of the precise zone to be cut. Can be null
	 * @param inputSFC
	 *            ShapeFile of zones to extract the wanted zone from (usually a zoning plan)
	 * @param zoningFile
	 *            The File containing the zoning plan (can be null if no zoning plan is planned to be used)
	 * @param boundingSFC
	 *            {@link SimpleFeatureCollection} to bound the process on a wanted location
	 * @return An extraction of the zoning collection
	 * @throws IOException
	 * @throws FactoryException
	 * @throws NoSuchAuthorityCodeException
	 */
	public static SimpleFeatureCollection createZoneToCut(String genericZone, String preciseZone, SimpleFeatureCollection inputSFC, File zoningFile,
			SimpleFeatureCollection boundingSFC) throws IOException, NoSuchAuthorityCodeException, FactoryException {
		// get the wanted zones from the zoning file
		SimpleFeatureCollection finalZone;
		if (genericZone != null && genericZone != "" && (preciseZone == null || preciseZone != ""))
			finalZone = MarkParcelAttributeFromPosition.getOnlyMarkedParcels(MarkParcelAttributeFromPosition
					.markParcelIntersectGenericZoningType(Collec.snapDatas(inputSFC, Geom.unionSFC(boundingSFC)), genericZone, zoningFile));
		else if (preciseZone != null && preciseZone != "")
			finalZone = MarkParcelAttributeFromPosition.getOnlyMarkedParcels(MarkParcelAttributeFromPosition.markParcelIntersectPreciseZoningType(
					Collec.snapDatas(inputSFC, Geom.unionSFC(boundingSFC)), genericZone, preciseZone, zoningFile));
		else
			finalZone = MarkParcelAttributeFromPosition
					.getOnlyMarkedParcels(MarkParcelAttributeFromPosition.markAllParcel(Collec.snapDatas(inputSFC, Geom.unionSFC(boundingSFC))));
		if (finalZone.isEmpty()) {
			System.out.println("createZoneToCut(): zone is empty");
		}
		return finalZone;
	}

	/**
	 * Create a new section name following a precise rule.
	 * 
	 * @param numZone
	 *            number of the nex zone
	 * @return the section's name
	 */
	public static String makeNewSection(int numZone) {
		return "New" + numZone + "Section";
	}

	/**
	 * Check if the input {@link SimpleFeature} has a section field that has been simulated with this present goal.
	 * 
	 * @param feat
	 *            {@link SimpleFeature} to test.
	 * @return true if the section field is marked with the {@link #makeNewSection(int)} method.
	 */
	public static boolean isNewSection(SimpleFeature feat) {
		String section = (String) feat.getAttribute(ParcelSchema.getMinParcelSectionField());
		return section.startsWith("New") && section.endsWith("Section");
	}
}