package algorithm;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.feature.DefaultFeatureCollection;
import org.geotools.referencing.CRS;
import org.locationtech.jts.geom.Geometry;

import fr.ign.cogit.GTFunctions.Vectors;
import fr.ign.cogit.geoxygene.api.feature.IFeature;
import fr.ign.cogit.geoxygene.api.feature.IFeatureCollection;
import fr.ign.cogit.geoxygene.api.spatial.geomaggr.IMultiCurve;
import fr.ign.cogit.geoxygene.api.spatial.geomprim.IOrientableCurve;
import fr.ign.cogit.geoxygene.convert.FromGeomToLineString;
import fr.ign.cogit.geoxygene.spatial.geomaggr.GM_MultiCurve;
import fr.ign.cogit.geoxygene.util.conversion.GeOxygeneGeoToolsTypes;
import fr.ign.cogit.geoxygene.util.conversion.ShapefileReader;
import processus.ParcelSplitFlag;

public class ParcelDensification {

	/**
	 * Apply the densification process
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
			File tmpFolder, File buildingFile, double maximalAreaSplitParcel, double minimalAreaSplitParcel, double maximalWidthSplitParcel,
			double lenDriveway, boolean isArt3AllowsIsolatedParcel) throws Exception {

		File pivotFile = new File(tmpFolder, "parcelsToSplit.shp");
		Vectors.exportSFC(parcelCollection, pivotFile);
		IFeatureCollection<IFeature> parcelCollec = ShapefileReader.read(pivotFile.getAbsolutePath());

		// the little islands (ilots)
		File ilotReduced = new File(tmpFolder, "ilotTmp.shp");
		Vectors.exportSFC(ilotCollection, ilotReduced);
		IFeatureCollection<IFeature> featC = ShapefileReader.read(ilotReduced.getAbsolutePath());

		List<IOrientableCurve> lOC = featC.select(parcelCollec.envelope()).parallelStream().map(x -> FromGeomToLineString.convert(x.getGeom()))
				.collect(ArrayList::new, List::addAll, List::addAll);
		IMultiCurve<IOrientableCurve> iMultiCurve = new GM_MultiCurve<>(lOC);

		DefaultFeatureCollection cutedAll = new DefaultFeatureCollection();
		for (IFeature iFeat : parcelCollec) {
			// if the parcel is selected for the simulation and bigger than the limit size
			if (iFeat.getAttribute("SPLIT").equals(1) && iFeat.getGeom().area() > maximalAreaSplitParcel) {
				// we falg cut the parcel
				SimpleFeatureCollection tmp = ParcelSplitFlag.generateFlagSplitedParcels(iFeat, iMultiCurve, tmpFolder, buildingFile,
						maximalAreaSplitParcel, maximalWidthSplitParcel, lenDriveway, isArt3AllowsIsolatedParcel);
				// if the cut parcels are inferior to the minimal size, we cancel all and add the initial parcel
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
					problem.printStackTrace();
				} finally {
					parcelIt.close();
				}
				if (add) {
					System.out.println("add "+ tmp.size() +" densyfied parcels");
					cutedAll.addAll(tmp);
				} else {
					System.out.println("add former parcel");
					cutedAll.add(GeOxygeneGeoToolsTypes.convert2SimpleFeature(iFeat, CRS.decode("EPSG:2154")));
				}
			}
			// if no simulation needed, we ad the normal parcel
			else {
				cutedAll.add(GeOxygeneGeoToolsTypes.convert2SimpleFeature(iFeat, CRS.decode("EPSG:2154")));
			}
		}

		return cutedAll.collection();
	}

}
