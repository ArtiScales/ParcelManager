package fr.ign.artiscales.goal;

import java.io.File;
import java.util.Arrays;
import java.util.List;

import org.geotools.data.DataStore;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.factory.CommonFactoryFinder;
import org.geotools.feature.DefaultFeatureCollection;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.filter.FilterFactory2;

import fr.ign.artiscales.decomposition.FlagParcelDecomposition;
import fr.ign.artiscales.decomposition.OBBBlockDecomposition;
import fr.ign.artiscales.fields.GeneralFields;
import fr.ign.artiscales.parcelFunction.MarkParcelAttributeFromPosition;
import fr.ign.artiscales.parcelFunction.ParcelSchema;
import fr.ign.cogit.geoToolsFunctions.Attribute;
import fr.ign.cogit.geoToolsFunctions.vectors.Collec;
import fr.ign.cogit.geoToolsFunctions.vectors.Geopackages;
import fr.ign.cogit.parameter.ProfileUrbanFabric;

/**
 * Simulation following that goal divides parcels to ensure that they could be densified. The
 * {@link FlagParcelDecomposition#generateFlagSplitedParcels(SimpleFeature, List, double, File, File, Double, Double, Double, boolean, Geometry)} method is applied on the
 * selected parcels. If the creation of a flag parcel is impossible and the local rules allows parcel to be disconnected from the road network, the
 * {@link OBBBlockDecomposition#splitParcels(SimpleFeature, double, double, double, double, List, double, boolean, int)} is applied. Other behavior can be set relatively to the
 * parcel's sizes.
 * 
 * @author Maxime Colomb
 *
 */
public class Densification {
	
	/**
	 * If true, will save a Geopackage containing only the simulated parcels in the temporary folder.
	 */
	public static boolean SAVEINTERMEDIATERESULT = false;
	/**
	 * If true, overwrite the output saved Geopackages. If false, happend the simulated parcels to a potential already existing Geopackage.
	 */
	public static boolean OVERWRITEGEOPACKAGE = true;


	/**
	 * Apply the densification goal on a set of marked parcels.
	 *
	 * @param parcelCollection
	 *            {@link SimpleFeatureCollection} of marked parcels.
	 * @param isletCollection
	 *            {@link SimpleFeatureCollection} containing the morphological islet. Can be generated with the
	 *            {@link fr.ign.cogit.geometryGeneration.CityGeneration#createUrbanIslet(SimpleFeatureCollection)} method.
	 * @param outFolder
	 *            Folder to store result files
	 * @param buildingFile
	 *            Geopackage representing the buildings
	 * @param roadFile
	 *            Geopackage representing the roads. If road not needed, use the overloaded method.
	 * @param maximalAreaSplitParcel
	 *            threshold of parcel area above which the OBB algorithm stops to decompose parcels
	 * @param minimalAreaSplitParcel
	 *            threshold under which the parcels is not kept. If parcel simulated is under this goal will keep the unsimulated parcel.
	 * @param maximalWidthSplitParcel
	 *            threshold of parcel connection to road under which the OBB algorithm stops to decompose parcels
	 * @param lenDriveway
	 *            lenght of the driveway to connect a parcel through another parcel to the road
	 * @param allowIsolatedParcel
	 *            true if the simulated parcels have the right to be isolated from the road, false otherwise.
	 * @param exclusionZone
	 *            Exclude a zone that won't be considered as a potential road connection. Useful to represent border of the parcel plan. Can be null.
	 * @return The input parcel {@link SimpleFeatureCollection} with the marked parcels replaced by the simulated parcels. All parcels have the
	 *         {@link fr.ign.artiscales.parcelFunction.ParcelSchema#getSFBMinParcel()} schema. * @throws Exception
	 */
	public static SimpleFeatureCollection densification(SimpleFeatureCollection parcelCollection, SimpleFeatureCollection isletCollection,
			File outFolder, File buildingFile, File roadFile, double maximalAreaSplitParcel, double minimalAreaSplitParcel, double maximalWidthSplitParcel,
			double lenDriveway, boolean allowIsolatedParcel, Geometry exclusionZone) throws Exception {
		// if parcels doesn't contains the markParcelAttribute field or have no marked parcels 
		if (!Collec.isCollecContainsAttribute(parcelCollection, MarkParcelAttributeFromPosition.getMarkFieldName())
				|| Arrays.stream(parcelCollection.toArray(new SimpleFeature[0]))
						.filter(feat -> feat.getAttribute(MarkParcelAttributeFromPosition.getMarkFieldName()).equals(1)).count() == 0) {
			System.out.println("Densification : unmarked parcels");
			return GeneralFields.transformSFCToMinParcel(parcelCollection);
		}
		//preparation of the builder and empty collections
		final String geomName = parcelCollection.getSchema().getGeometryDescriptor().getLocalName();
		FilterFactory2 ff = CommonFactoryFinder.getFilterFactory2();
		DefaultFeatureCollection onlyCutedParcels = new DefaultFeatureCollection();
		DefaultFeatureCollection resultParcels = new DefaultFeatureCollection();
		SimpleFeatureBuilder sFBMinParcel = ParcelSchema.getSFBMinParcel();
		try (SimpleFeatureIterator iterator = parcelCollection.features()){
			while (iterator.hasNext()) {
				SimpleFeature feat = iterator.next();
				// if the parcel is selected for the simulation and bigger than the limit size
				if (feat.getAttribute(MarkParcelAttributeFromPosition.getMarkFieldName()) != null
						&& feat.getAttribute(MarkParcelAttributeFromPosition.getMarkFieldName()).equals(1)
						&& ((Geometry) feat.getDefaultGeometry()).getArea() > maximalAreaSplitParcel) {
					//we get the needed islet lines
					List<LineString> lines = Collec.fromPolygonSFCtoListRingLines(isletCollection.subCollection(
							ff.bbox(ff.property(feat.getFeatureType().getGeometryDescriptor().getLocalName()), feat.getBounds())));
					// we flag cut the parcel
					SimpleFeatureCollection unsortedFlagParcel = FlagParcelDecomposition.generateFlagSplitedParcels(feat, lines, 0.0, buildingFile,
							roadFile, maximalAreaSplitParcel, maximalWidthSplitParcel, lenDriveway, allowIsolatedParcel, exclusionZone);
					// if the cut parcels are inferior to the minimal size, we cancel all and add the initial parcel
					boolean add = true;
					// If the flag cut parcel size is too small, we won't add anything
					try (SimpleFeatureIterator parcelIt = unsortedFlagParcel.features()){
						while (parcelIt.hasNext()) {
							if (((Geometry) parcelIt.next().getDefaultGeometry()).getArea() < minimalAreaSplitParcel) {
								add = false;
								break;
							}
						}
					} catch (Exception problem) {
						System.out.println("problem" + problem + "for " + feat + " feature densification");
						problem.printStackTrace();
					} 
					if (add) {
						// construct the new parcels
						int i = 1;
						try(SimpleFeatureIterator parcelCutedIt = unsortedFlagParcel.features()) {
							while (parcelCutedIt.hasNext()) {
								SimpleFeature parcelCuted = parcelCutedIt.next();
								sFBMinParcel.set(geomName, parcelCuted.getDefaultGeometry());
								sFBMinParcel.set(ParcelSchema.getMinParcelSectionField(),
										makeNewSection((String) feat.getAttribute(ParcelSchema.getMinParcelSectionField())));
								sFBMinParcel.set(ParcelSchema.getMinParcelNumberField(), String.valueOf(i++));
								sFBMinParcel.set(ParcelSchema.getMinParcelCommunityField(),
										feat.getAttribute(ParcelSchema.getMinParcelCommunityField()));
								SimpleFeature cutedParcel = sFBMinParcel.buildFeature(Attribute.makeUniqueId());
								resultParcels.add(cutedParcel);
								if (SAVEINTERMEDIATERESULT)
									onlyCutedParcels.add(cutedParcel);
							}
						} catch (Exception problem) {
							problem.printStackTrace();
						} 
					} else {
						sFBMinParcel = ParcelSchema.setSFBMinParcelWithFeat(feat, sFBMinParcel.getFeatureType());
						resultParcels.add(sFBMinParcel.buildFeature(Attribute.makeUniqueId()));
					}
				}
				// if no simulation needed, we ad the normal parcel
				else {
					sFBMinParcel = ParcelSchema.setSFBMinParcelWithFeat(feat, sFBMinParcel.getFeatureType());
					resultParcels.add(sFBMinParcel.buildFeature(Attribute.makeUniqueId()));
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		if (SAVEINTERMEDIATERESULT) {
			Collec.exportSFC(onlyCutedParcels, new File(outFolder, "parcelDensificationOnly"), OVERWRITEGEOPACKAGE) ;
			OVERWRITEGEOPACKAGE = false;
		}
		return resultParcels.collection();
	}
	
	/**
	 * Apply the densification goal on a set of marked parcels.
	 *
	 * overload of the {@link #densification(SimpleFeatureCollection, SimpleFeatureCollection, File, File, File, double, double, double, double, boolean)} method if we choose to
	 * not use a road Geopackage and no geometry of exclusion
	 * 
	 * @param parcelCollection
	 *            SimpleFeatureCollection of marked parcels.
	 * @param isletCollection
	 *            SimpleFeatureCollection containing the morphological islet. Can be generated with the
	 *            {@link fr.ign.cogit.geometryGeneration.CityGeneration#createUrbanIslet(SimpleFeatureCollection)} method.
	 * @param outFolder
	 *            folder to store created files
	 * @param buildingFile
	 *            Geopackage representing the buildings
	 * @param maximalAreaSplitParcel
	 *            threshold of parcel area above which the OBB algorithm stops to decompose parcels
	 * @param minimalAreaSplitParcel
	 *            threshold under which the parcels is not kept. If parcel simulated is under this goal will keep the unsimulated parcel.
	 * @param maximalWidthSplitParcel
	 *            threshold of parcel connection to road under which the OBB algorithm stops to decompose parcels
	 * @param lenDriveway
	 *            length of the driveway to connect a parcel through another parcel to the road
	 * @param allowIsolatedParcel
	 *            true if the simulated parcels have the right to be isolated from the road, false otherwise.
	 * @return The input parcel {@link SimpleFeatureCollection} with the marked parcels replaced by the simulated parcels. All parcels have the
	 *         {@link fr.ign.artiscales.parcelFunction.ParcelSchema#getSFBMinParcel()} schema. * @throws Exception
	 */
	public static SimpleFeatureCollection densification(SimpleFeatureCollection parcelCollection, SimpleFeatureCollection isletCollection,
			File outFolder, File buildingFile, File roadFile, double maximalAreaSplitParcel, double minimalAreaSplitParcel, double maximalWidthSplitParcel,
			double lenDriveway, boolean allowIsolatedParcel) throws Exception {
		return densification(parcelCollection, isletCollection, outFolder, buildingFile, null, maximalAreaSplitParcel, minimalAreaSplitParcel,
				maximalWidthSplitParcel, lenDriveway, allowIsolatedParcel, null);
	}
	
	/**
	 * Apply the densification goal on a set of marked parcels.
	 *
	 * overload of the {@link #densification(SimpleFeatureCollection, SimpleFeatureCollection, File, File, File, double, double, double, double, boolean)} method if we choose to
	 * not use a road Geopackage
	 * 
	 * @param parcelCollection
	 *            SimpleFeatureCollection of marked parcels.
	 * @param isletCollection
	 *            SimpleFeatureCollection containing the morphological islet. Can be generated with the
	 *            {@link fr.ign.cogit.geometryGeneration.CityGeneration#createUrbanIslet(SimpleFeatureCollection)} method.
	 * @param outFolder
	 *            folder to store created files
	 * @param buildingFile
	 *            Geopackage representing the buildings
	 * @param maximalAreaSplitParcel
	 *            threshold of parcel area above which the OBB algorithm stops to decompose parcels
	 * @param minimalAreaSplitParcel
	 *            threshold under which the parcels is not kept. If parcel simulated is under this goal will keep the unsimulated parcel.
	 * @param maximalWidthSplitParcel
	 *            threshold of parcel connection to road under which the OBB algorithm stops to decompose parcels
	 * @param lenDriveway
	 *            lenght of the driveway to connect a parcel through another parcel to the road
	 * @param allowIsolatedParcel
	 *            true if the simulated parcels have the right to be isolated from the road, false otherwise.
	 * @return The input parcel {@link SimpleFeatureCollection} with the marked parcels replaced by the simulated parcels. All parcels have the
	 *         {@link fr.ign.artiscales.parcelFunction.ParcelSchema#getSFBMinParcel()} schema. * @throws Exception
	 */
	public static SimpleFeatureCollection densification(SimpleFeatureCollection parcelCollection, SimpleFeatureCollection isletCollection,
			File outFolder, File buildingFile, double maximalAreaSplitParcel, double minimalAreaSplitParcel, double maximalWidthSplitParcel,
			double lenDriveway, boolean allowIsolatedParcel) throws Exception {
		return densification(parcelCollection, isletCollection, outFolder, buildingFile, null, maximalAreaSplitParcel, minimalAreaSplitParcel,
				maximalWidthSplitParcel, lenDriveway, allowIsolatedParcel);
	}
	
	/**
	 * Apply the densification goal on a set of marked parcels. Overload
	 * {@link #densification(SimpleFeatureCollection, SimpleFeatureCollection, File, File, File, double, double, double, double, boolean)} method with a profile building type input
	 * (which automatically report its parameters to the fields)
	 * 
	 * @param parcelCollection
	 *            SimpleFeatureCollection of marked parcels.
	 * @param isletCollection
	 *            SimpleFeatureCollection containing the morphological islet. Can be generated with the
	 *            {@link fr.ign.cogit.geometryGeneration.CityGeneration#createUrbanIslet(SimpleFeatureCollection)} method.
	 * @param outFolder
	 *            folder to store result files.
	 * @param buildingFile
	 *            Geopackage representing the buildings.
	 * @param roadFile
	 *            Geopackage representing the roads (optional).
	 * @param profile
	 *            Description of the urban fabric profile planed to be simulated on this zone.
	 * @param allowIsolatedParcel
	 *            true if the simulated parcels have the right to be isolated from the road, false otherwise.
	 * @return The input parcel {@link SimpleFeatureCollection} with the marked parcels replaced by the simulated parcels. All parcels have the
	 *         {@link fr.ign.artiscales.parcelFunction.ParcelSchema#getSFBMinParcel()} schema.
	 * @throws Exception
	 */
	public static SimpleFeatureCollection densification(SimpleFeatureCollection parcelCollection, SimpleFeatureCollection isletCollection,
			File outFolder, File buildingFile, File roadFile, ProfileUrbanFabric profile, boolean allowIsolatedParcel) throws Exception {
		return densification(parcelCollection, isletCollection, outFolder, buildingFile, roadFile, profile, allowIsolatedParcel, null);
	}
	
	/**
	 * Apply the densification goal on a set of marked parcels. Overload
	 * {@link #densification(SimpleFeatureCollection, SimpleFeatureCollection, File, File, File, double, double, double, double, boolean)} method with a profile building type input
	 * (which automatically report its parameters to the fields)
	 * 
	 * @param parcelCollection
	 *            SimpleFeatureCollection of marked parcels.
	 * @param isletCollection
	 *            SimpleFeatureCollection containing the morphological islet. Can be generated with the
	 *            {@link fr.ign.cogit.geometryGeneration.CityGeneration#createUrbanIslet(SimpleFeatureCollection)} method.
	 * @param outFolder
	 *            folder to store result files.
	 * @param buildingFile
	 *            Geopackage representing the buildings.
	 * @param roadFile
	 *            Geopackage representing the roads (optional).
	 * @param profile
	 *            Description of the urban fabric profile planed to be simulated on this zone.
	 * @param allowIsolatedParcel
	 *            true if the simulated parcels have the right to be isolated from the road, false otherwise. exclusionZone
	 * @return The input parcel {@link SimpleFeatureCollection} with the marked parcels replaced by the simulated parcels. All parcels have the
	 *         {@link fr.ign.artiscales.parcelFunction.ParcelSchema#getSFBMinParcel()} schema. * @throws Exception
	 */
	public static SimpleFeatureCollection densification(SimpleFeatureCollection parcelCollection, SimpleFeatureCollection isletCollection,
			File outFolder, File buildingFile, File roadFile, ProfileUrbanFabric profile, boolean allowIsolatedParcel, Geometry exclusionZone) throws Exception {
		return densification(parcelCollection, isletCollection, outFolder, buildingFile, roadFile,
				profile.getMaximalArea(), profile.getMinimalArea(), profile.getMinimalWidthContactRoad(), profile.getLenDriveway(),
				allowIsolatedParcel, exclusionZone);
	}
	
	/**
	 * Apply a hybrid densification process on the coming parcel collection. The parcels that size are inferior to 4x the maximal area of parcel type to create are runned with the
	 * densication goal. The parcels that size are superior to 4x the maximal area are considered as able to build neighborhood. They are divided with the
	 * {@link fr.ign.artiscales.goal.ConsolidationDivision#consolidationDivision(SimpleFeatureCollection, File, File, ProfileUrbanFabric)} method.
	 *
	 * @param parcelCollection
	 *            SimpleFeatureCollection of marked parcels.
	 * @param isletCollection
	 *            SimpleFeatureCollection containing the morphological islet. Can be generated with the
	 *            {@link fr.ign.cogit.geometryGeneration.CityGeneration#createUrbanIslet(SimpleFeatureCollection)} method.
	 * @param outFolder
	 *            folder to store result files.
	 * @param buildingFile
	 *            Geopackage representing the buildings.
	 * @param roadFile
	 *            Geopackage representing the roads (optional).
	 * @param profile
	 *            ProfileUrbanFabric of the simulated urban scene.
	 * @param allowIsolatedParcel
	 *            true if the simulated parcels have the right to be isolated from the road, false otherwise.
	 * @param exclusionZone
	 *            Exclude a zone that won't be considered as a potential road connection. Useful to represent border of the parcel plan. Can be null.
	 * @param factorOflargeZoneCreation
	 *            If the area of the parcel to be simulated is superior to the maximal size of parcels multiplied by this factor, the simulation will be done with the
	 *            {@link fr.ign.artiscales.goal.ConsolidationDivision#consolidationDivision(SimpleFeatureCollection, File, File, ProfileUrbanFabric)} method.
	 * @return The input parcel {@link SimpleFeatureCollection} with the marked parcels replaced by the simulated parcels. All parcels have the
	 *         {@link fr.ign.artiscales.parcelFunction.ParcelSchema#getSFBMinParcel()} schema.
	 * @throws Exception
	 */
	public static SimpleFeatureCollection densificationOrNeighborhood(SimpleFeatureCollection parcelCollection,
			SimpleFeatureCollection isletCollection, File outFolder, File buildingFile, File roadFile, ProfileUrbanFabric profile,
			boolean allowIsolatedParcel, Geometry exclusionZone, int factorOflargeZoneCreation) throws Exception {
		//TODO stupid hack but I can't figure out how those SimpleFeatuceCollection's attributes are changed if not wrote in hard
		File tmpDens = Collec.exportSFC(parcelCollection, new File(outFolder, "tmp/Dens"));
		// We flagcut the parcels which size is inferior to 4x the max parcel size
		SimpleFeatureCollection parcelDensified = densification(MarkParcelAttributeFromPosition.markParcelsInf(parcelCollection,
				profile.getMaximalArea() * factorOflargeZoneCreation), isletCollection, outFolder, buildingFile, roadFile,
				profile.getMaximalArea(), profile.getMinimalArea(), profile.getMinimalWidthContactRoad(), profile.getLenDriveway(),
				allowIsolatedParcel, exclusionZone);
		// if parcels are too big, we try to create neighborhoods inside them with the consolidation algorithm
		// We first re-mark the parcels that were marked.
		DataStore ds = Geopackages.getDataStore(tmpDens);
		SimpleFeatureCollection supParcels = MarkParcelAttributeFromPosition.markParcelsSup(
				MarkParcelAttributeFromPosition.markAlreadyMarkedParcels(parcelDensified, ds.getFeatureSource(ds.getTypeNames()[0]).getFeatures()),
				profile.getMaximalArea() * factorOflargeZoneCreation);
		if (!MarkParcelAttributeFromPosition.isNoParcelMarked(supParcels)) {
			profile.setLargeStreetWidth(profile.getStreetWidth());
			parcelDensified = ConsolidationDivision.consolidationDivision(supParcels, roadFile, outFolder, profile);
		}
		ds.dispose();
		return parcelDensified;
	}

	/**
	 * Create a new section name following a precise rule.
	 * 
	 * @param section
	 *            name of the former section
	 * @return the new section's name
	 */
	public static String makeNewSection(String section) {
		return section + "-Densifyed";
	}

	/**
	 * Check if the input {@link SimpleFeature} has a section field that has been simulated with this present goal.
	 * 
	 * @param feat
	 *            {@link SimpleFeature} to test.
	 * @return true if the section field is marked with the {@link #makeNewSection(String)} method.
	 */
	public static boolean isNewSection(SimpleFeature feat) {
		return ((String) feat.getAttribute(ParcelSchema.getMinParcelSectionField())).endsWith("-Densifyed");
	}
}
