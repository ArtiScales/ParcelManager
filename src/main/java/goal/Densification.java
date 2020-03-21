package goal;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.List;

import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.factory.CommonFactoryFinder;
import org.geotools.feature.DefaultFeatureCollection;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.filter.FilterFactory2;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import decomposition.ParcelSplitFlag;
import fr.ign.cogit.geoToolsFunctions.vectors.Collec;
import fr.ign.cogit.parameter.ProfileUrbanFabric;
import fr.ign.cogit.parcelFunction.MarkParcelAttributeFromPosition;
import fr.ign.cogit.parcelFunction.ParcelSchema;

public class Densification {
	public static boolean SAVEINTERMEDIATERESULT = false;
	public static boolean OVERWRITESHAPEFILES = true;

	/**
	 * Apply the densification goal on a set of marked parcels.
	 *
	 * @param parcelCollection
	 * @param isletCollection
	 * @param tmpFolder
	 * @param buildingFile
	 * @param roadFile
	 * @param maximalAreaSplitParcel
	 * @param minimalAreaSplitParcel
	 * @param maximalWidthSplitParcel
	 * @param lenDriveway
	 * @param isArt3AllowsIsolatedParcel
	 * @return
	 * @throws Exception
	 */
	public static SimpleFeatureCollection densification(SimpleFeatureCollection parcelCollection, SimpleFeatureCollection isletCollection,
			File tmpFolder, File buildingFile, File roadFile, double maximalAreaSplitParcel, double minimalAreaSplitParcel, double maximalWidthSplitParcel,
			double lenDriveway, boolean isArt3AllowsIsolatedParcel) throws Exception {
				System.out.println(parcelCollection.size());
		if (!Collec.isCollecContainsAttribute(parcelCollection, MarkParcelAttributeFromPosition.getMarkFieldName())) {
			System.out.println("Densification : unmarked parcels");
			return parcelCollection;
		}

		final String geomName = parcelCollection.getSchema().getGeometryDescriptor().getLocalName();
		FilterFactory2 ff = CommonFactoryFinder.getFilterFactory2();
		DefaultFeatureCollection cutedParcels = new DefaultFeatureCollection();
		DefaultFeatureCollection cutedAll = new DefaultFeatureCollection();
		SimpleFeatureBuilder sFBMinParcel = ParcelSchema.getSFBMinParcel();
		SimpleFeatureIterator iterator = parcelCollection.features();
		try {
			while (iterator.hasNext()) {
				SimpleFeature feat = iterator.next();
				// if the parcel is selected for the simulation and bigger than the limit size
				if (feat.getAttribute(MarkParcelAttributeFromPosition.getMarkFieldName()).equals(1)
						&& ((Geometry) feat.getDefaultGeometry()).getArea() > maximalAreaSplitParcel) {
					//we get the ilot lines
					List<LineString> lines = Collec.fromSFCtoExteriorRingLines(isletCollection.subCollection(
							ff.bbox(ff.property(feat.getFeatureType().getGeometryDescriptor().getLocalName()), feat.getBounds())));
					// we falg cut the parcel
					SimpleFeatureCollection tmp = ParcelSplitFlag.generateFlagSplitedParcels(feat, lines, tmpFolder,
							buildingFile, roadFile, maximalAreaSplitParcel, maximalWidthSplitParcel, lenDriveway,
							isArt3AllowsIsolatedParcel);
					// if the cut parcels are inferior to the minimal size, we cancel all and add
					// the initial parcel
					boolean add = true;
					SimpleFeatureIterator parcelIt = tmp.features();
					try {
						while (parcelIt.hasNext()) {
							if (((Geometry) parcelIt.next().getDefaultGeometry()).getArea() < minimalAreaSplitParcel) {
								System.out.println("densifyed parcel is too small");
								add = false;
								break;
							}
						}
					} catch (Exception problem) {
						System.out.println("problem" + problem + "for " + feat + " feature densification");
						problem.printStackTrace();
					} finally {
						parcelIt.close();
					}
					if (add) {
						// construct the new parcels
						// could have been cleaner with a stream but still don't know how to have an external counter to set parcels number
						int i = 1;
						SimpleFeatureIterator parcelCutedIt = tmp.features();
						try {
							while (parcelCutedIt.hasNext()) {
								SimpleFeature parcelCuted = parcelCutedIt.next();
								sFBMinParcel.set(geomName, parcelCuted.getDefaultGeometry());
								sFBMinParcel.set(ParcelSchema.getMinParcelSectionField(), (String) feat.getAttribute(ParcelSchema.getMinParcelSectionField()) + "-Densifyed");
								sFBMinParcel.set(ParcelSchema.getMinParcelNumberField(), String.valueOf(i++));
								sFBMinParcel.set(ParcelSchema.getMinParcelCommunityFiled(), feat.getAttribute(ParcelSchema.getMinParcelCommunityFiled()));
								SimpleFeature cutedParcel = sFBMinParcel.buildFeature(null);
								cutedAll.add(cutedParcel);
								if (SAVEINTERMEDIATERESULT)
									cutedParcels.add(cutedParcel);
							}
						} catch (Exception problem) {
							problem.printStackTrace();
						} finally {
							parcelCutedIt.close();
						}
					} else {
						sFBMinParcel = ParcelSchema.setSFBMinParcelWithFeat(feat, sFBMinParcel.getFeatureType());
						cutedAll.add(sFBMinParcel.buildFeature(null));
					}
				}
				// if no simulation needed, we ad the normal parcel
				else {
					sFBMinParcel = ParcelSchema.setSFBMinParcelWithFeat(feat, sFBMinParcel.getFeatureType());
					cutedAll.add(sFBMinParcel.buildFeature(null));
				}
			}
		} finally {
			iterator.close();
		}
		if (SAVEINTERMEDIATERESULT) {
			Collec.exportSFC(cutedParcels, new File(tmpFolder, "parcelDensificationOnly.shp"), OVERWRITESHAPEFILES) ;
			OVERWRITESHAPEFILES = false;
		}
		return cutedAll.collection();
	}
	
	/**
	 * Apply the densification goal on a set of marked parcels.
	 *
	 * @overload if we choose to not use a road Shapefile
	 * @param parcelCollection
	 * @param isletCollection
	 * @param tmpFolder
	 * @param buildingFile
	 * @param maximalAreaSplitParcel
	 * @param minimalAreaSplitParcel
	 * @param maximalWidthSplitParcel
	 * @param lenDriveway
	 * @param isArt3AllowsIsolatedParcel
	 * @return
	 * @throws Exception
	 */
	public static SimpleFeatureCollection densification(SimpleFeatureCollection parcelCollection, SimpleFeatureCollection isletCollection,
			File tmpFolder, File buildingFile, double maximalAreaSplitParcel, double minimalAreaSplitParcel, double maximalWidthSplitParcel,
			double lenDriveway, boolean isArt3AllowsIsolatedParcel) throws Exception {
		return densification(parcelCollection, isletCollection, tmpFolder, buildingFile, null, maximalAreaSplitParcel, minimalAreaSplitParcel,
				maximalWidthSplitParcel, lenDriveway, isArt3AllowsIsolatedParcel);
	}
	
	
	/**
	 * Apply the densification goal on a set of marked parcels.
	 *
	 * @overload iwith a profile building type input
	 * @param parcelCollection
	 * @param isletCollection
	 * @param tmpFolder
	 * @param buildingFile
	 * @param isArt3AllowsIsolatedParcel
	 * @return
	 * @throws Exception
	 */
	public static SimpleFeatureCollection densification(SimpleFeatureCollection parcelCollection, SimpleFeatureCollection isletCollection,
			File tmpFolder, File buildingFile, File roadFile, File profileFile, boolean isArt3AllowsIsolatedParcel) throws Exception {
		// profile building
		ObjectMapper mapper = new ObjectMapper();
		mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
		InputStream fileInputStream = new FileInputStream(profileFile);
		ProfileUrbanFabric profile = mapper.readValue(fileInputStream, ProfileUrbanFabric.class);
		return densification(parcelCollection, isletCollection, tmpFolder, buildingFile, roadFile,
				profile.getMaximalArea(), profile.getMinimalArea(), profile.getMaximalWidth(), profile.getLenDriveway(),
				isArt3AllowsIsolatedParcel);
	}
}
