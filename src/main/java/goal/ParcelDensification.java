package goal;

import java.io.File;
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

import decomposition.ParcelSplitFlag;
import fr.ign.cogit.geoToolsFunctions.vectors.Collec;
import fr.ign.cogit.parcelFunction.ParcelSchema;

public class ParcelDensification {
	public static boolean SAVEINTERMEDIATERESULT = false;
	public static boolean OVERWRITESHAPEFILES = true;

	/**
	 * Apply the densification process.
	 * Only applied for french parcel models
	 * 
	 * @param splitZone
	 * @param parcelCollection
	 * @param tmpFolder
	 * @param rootFile
	 * @param p
	 * @return
	 * @throws Exception
	 */
	public static SimpleFeatureCollection parcelDensification(SimpleFeatureCollection parcelCollection, SimpleFeatureCollection ilotCollection,
			File tmpFolder, File buildingFile, File roadFile, double maximalAreaSplitParcel, double minimalAreaSplitParcel, double maximalWidthSplitParcel,
			double lenDriveway, boolean isArt3AllowsIsolatedParcel) throws Exception {
				
		if (!Collec.isCollecContainsAttribute(parcelCollection, "SPLIT")) {
			System.out.println("Densification : unmarked parcels");
			return parcelCollection;
		}

		final String geomName = parcelCollection.getSchema().getGeometryDescriptor().getLocalName();
		FilterFactory2 ff = CommonFactoryFinder.getFilterFactory2();
		DefaultFeatureCollection cutedParcels = new DefaultFeatureCollection();
		DefaultFeatureCollection cutedAll = new DefaultFeatureCollection();
		SimpleFeatureBuilder SFBFrenchParcel = ParcelSchema.getSFBFrenchParcel();
		SimpleFeatureIterator iterator = parcelCollection.features();
		try {
			while (iterator.hasNext()) {
				SimpleFeature feat = iterator.next();
				// if the parcel is selected for the simulation and bigger than the limit size
				if (feat.getAttribute("SPLIT").equals(1)
						&& ((Geometry) feat.getDefaultGeometry()).getArea() > maximalAreaSplitParcel) {
					//we get the ilot lines
					List<LineString> lines = Collec.fromSFCtoExteriorRingLines(ilotCollection.subCollection(
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
						// Arrays.stream(tmp.toArray(new SimpleFeature[0])).forEach(parcelCuted -> {
						int i = 1;
						SimpleFeatureIterator parcelCutedIt = tmp.features();
						try {
							while (parcelCutedIt.hasNext()) {
								SimpleFeature parcelCuted = parcelCutedIt.next();
								String newCodeDep = (String) feat.getAttribute("CODE_DEP");
								String newCodeCom = (String) feat.getAttribute("CODE_COM");
								String newSection = (String) feat.getAttribute("SECTION") + "-Densifyed";
								String newNumero = String.valueOf(i++);
								String newCode = newCodeDep + newCodeCom + "000" + newSection + newNumero;
								SFBFrenchParcel.set(geomName, parcelCuted.getDefaultGeometry());
								SFBFrenchParcel.set("CODE", newCode);
								SFBFrenchParcel.set("CODE_DEP", newCodeDep);
								SFBFrenchParcel.set("CODE_COM", newCodeCom);
								SFBFrenchParcel.set("COM_ABS", "000");
								SFBFrenchParcel.set("SECTION", newSection);
								SFBFrenchParcel.set("NUMERO", newNumero);
								SimpleFeature cutedParcel = SFBFrenchParcel.buildFeature(null);
								cutedAll.add(cutedParcel);
								if (SAVEINTERMEDIATERESULT)
									cutedParcels.add(cutedParcel);
							}
						} catch (Exception problem) {
							problem.printStackTrace();
						} finally {
							parcelCutedIt.close();
						}
						// });
					} else {
						SFBFrenchParcel = ParcelSchema.setSFBFrenchParcelWithFeat(feat, SFBFrenchParcel.getFeatureType());
						cutedAll.add(SFBFrenchParcel.buildFeature(null));
					}
				}
				// if no simulation needed, we ad the normal parcel
				else {
					SFBFrenchParcel = ParcelSchema.setSFBFrenchParcelWithFeat(feat, SFBFrenchParcel.getFeatureType());
					cutedAll.add(SFBFrenchParcel.buildFeature(null));
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
	
	public static SimpleFeatureCollection parcelDensification(SimpleFeatureCollection parcelCollection, SimpleFeatureCollection ilotCollection,
			File tmpFolder, File buildingFile, double maximalAreaSplitParcel, double minimalAreaSplitParcel, double maximalWidthSplitParcel,
			double lenDriveway, boolean isArt3AllowsIsolatedParcel) throws Exception {
		return parcelDensification(parcelCollection, ilotCollection, tmpFolder, buildingFile, null, maximalAreaSplitParcel, minimalAreaSplitParcel,
				maximalWidthSplitParcel, lenDriveway, isArt3AllowsIsolatedParcel);
	}
}
