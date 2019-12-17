package algorithm;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.factory.CommonFactoryFinder;
import org.geotools.feature.DefaultFeatureCollection;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Polygon;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.filter.FilterFactory2;

import fr.ign.cogit.parcelFunction.ParcelSchema;
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

		final String geomName = parcelCollection.getSchema().getGeometryDescriptor().getLocalName();

		// the little islands (ilots)
		// File ilotReduced = new File(tmpFolder, "ilotTmp.shp");
		// Vectors.exportSFC(ilotCollection, ilotReduced);
		// IFeatureCollection<IFeature> featC = ShapefileReader.read(ilotReduced.getAbsolutePath());

		// List<IOrientableCurve> lOC = featC.select(parcelCollec.envelope()).parallelStream().map(x -> FromGeomToLineString.convert(x.getGeom()))
		// .collect(ArrayList::new, List::addAll, List::addAll);

		// IMultiCurve<IOrientableCurve> iMultiCurve = new GM_MultiCurve<>(lOC);
		FilterFactory2 ff = CommonFactoryFinder.getFilterFactory2();
		SimpleFeatureCollection blocks = ilotCollection
				.subCollection(ff.bbox(ff.property(ilotCollection.getSchema().getGeometryDescriptor().getLocalName()), parcelCollection.getBounds()));
		List<LineString> lines = new ArrayList<>();
		SimpleFeatureIterator iterator = blocks.features();
		try {
			while (iterator.hasNext()) {
				SimpleFeature feature = iterator.next();
				if(feature.getDefaultGeometry() instanceof MultiPolygon){
					MultiPolygon mp = (MultiPolygon) feature.getDefaultGeometry();
					for (int i = 0 ; i < mp.getNumGeometries();i++) {
						lines.add(((Polygon) mp.getGeometryN(i)).getExteriorRing());
					}
				}
				else {
					lines.add(((Polygon) feature.getDefaultGeometry()).getExteriorRing());
				}
			}
		} finally {
			iterator.close();
		}
		// MultiLineString iMultiCurve = new GeometryFactory().createMultiLineString(lines.toArray(new LineString[lines.size()]));
		DefaultFeatureCollection cutedAll = new DefaultFeatureCollection();
		SimpleFeatureBuilder SFBFrenchParcel = ParcelSchema.getSFBFrenchParcel();
		iterator = parcelCollection.features();
		try {
			while (iterator.hasNext()) {
				SimpleFeature feat = iterator.next();
				// if the parcel is selected for the simulation and bigger than the limit size
				if (feat.getAttribute("SPLIT").equals(1) && ((Geometry) feat.getDefaultGeometry()).getArea() > maximalAreaSplitParcel) {
					// we falg cut the parcel
					SimpleFeatureCollection tmp = ParcelSplitFlag.generateFlagSplitedParcels(feat, lines, tmpFolder, buildingFile,
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
								cutedAll.add(SFBFrenchParcel.buildFeature(null));
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
		return cutedAll.collection();
	}
}
