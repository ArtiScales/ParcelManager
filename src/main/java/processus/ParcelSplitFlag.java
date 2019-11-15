package processus;

import java.io.File;
import java.util.List;

import org.geotools.referencing.CRS;

import fr.ign.cogit.GTFunctions.Vectors;
import fr.ign.cogit.geoxygene.api.feature.IFeature;
import fr.ign.cogit.geoxygene.api.feature.IFeatureCollection;
import fr.ign.cogit.geoxygene.api.spatial.coordgeom.IDirectPosition;
import fr.ign.cogit.geoxygene.api.spatial.coordgeom.IPolygon;
import fr.ign.cogit.geoxygene.api.spatial.geomaggr.IMultiCurve;
import fr.ign.cogit.geoxygene.api.spatial.geomprim.IOrientableCurve;
import fr.ign.cogit.geoxygene.api.spatial.geomprim.IOrientableSurface;
import fr.ign.cogit.geoxygene.api.spatial.geomroot.IGeometry;
import fr.ign.cogit.geoxygene.convert.FromGeomToSurface;
import fr.ign.cogit.geoxygene.feature.FT_FeatureCollection;
import fr.ign.cogit.geoxygene.sig3d.calculation.parcelDecomposition.FlagParcelDecomposition;
import fr.ign.cogit.geoxygene.spatial.coordgeom.DirectPosition;
import fr.ign.cogit.geoxygene.util.attribute.AttributeManager;
import fr.ign.cogit.geoxygene.util.conversion.GeOxygeneGeoToolsTypes;
import fr.ign.cogit.geoxygene.util.conversion.ShapefileReader;
import fr.ign.cogit.parcelFunction.ParcelState;

public class ParcelSplitFlag {

	// /////////////////////////
	// //////// try the generateFlagSplitedParcels method
	// /////////////////////////
	//
	// File geoFile = new File(, "dataGeo");
	// IFeatureCollection<IFeature> featColl =
	// ShapefileReader.read("/tmp/tmp1.shp");
	//
	// String inputUrbanBlock = GetFromGeom.getIlots(geoFile).getAbsolutePath();
	//
	// IFeatureCollection<IFeature> featC = ShapefileReader.read(inputUrbanBlock);
	// List<IOrientableCurve> lOC =
	// featC.select(featColl.envelope()).parallelStream().map(x ->
	// FromGeomToLineString.convert(x.getGeom())).collect(ArrayList::new,
	// List::addAll,
	// List::addAll);
	//
	// IMultiCurve<IOrientableCurve> iMultiCurve = new GM_MultiCurve<>(lOC);
	//
	// // ShapefileDataStore shpDSZone = new ShapefileDataStore(
	// // new
	// File("/home/mcolomb/informatique/ArtiScales/ParcelSelectionFile/exScenar/variant0/parcelGenExport.shp").toURI().toURL());
	// //
	// // SimpleFeatureCollection featuresZones =
	// shpDSZone.getFeatureSource().getFeatures();
	// // SimpleFeatureIterator it = featuresZones.features();
	// // SimpleFeature waiting = null;
	// // while (it.hasNext()) {
	// // SimpleFeature feat = it.next();
	// // if (((String) feat.getAttribute("CODE")).equals("25598000AB0446") ) {
	// // waiting = feat;
	// // }
	// // }
	//
	// // Vectors.exportSFC(generateSplitedParcels(waiting, tmpFile, p), new
	// // File("/tmp/tmp2.shp"));
	// SimpleFeatureCollection salut = generateFlagSplitedParcels(featColl.get(0),
	// iMultiCurve, geoFile, tmpFile, 2000.0, 15.0, 3.0);
	//
	// Vectors.exportSFC(salut, new File("/tmp/tmp2.shp"));
	//
	// }

	// public static SimpleFeatureCollection
	// generateFlagSplitedParcels(SimpleFeatureCollection featColl,
	// IMultiCurve<IOrientableCurve> iMultiCurve, File geoFile, File tmpFile,
	// Parameters p) throws NoSuchAuthorityCodeException, FactoryException,
	// Exception {
	//
	// DefaultFeatureCollection collec = new DefaultFeatureCollection();
	// SimpleFeatureIterator it = featColl.features();
	//
	// try {
	// while (it.hasNext()) {
	// SimpleFeature feat = it.next();
	// collec.addAll(generateFlagSplitedParcels(feat, iMultiCurve, geoFile, tmpFile,
	// p));
	// }
	// } catch (Exception problem) {
	// problem.printStackTrace();
	// } finally {
	// it.close();
	// }
	//
	// return collec;
	// }
	//
	// public static SimpleFeatureCollection
	// generateFlagSplitedParcels(SimpleFeature feat, IMultiCurve<IOrientableCurve>
	// iMultiCurve, File geoFile, File tmpFile, Parameters p)
	// throws Exception {
	// return generateFlagSplitedParcels(feat, iMultiCurve, geoFile, tmpFile,
	// p.getDouble("maximalAreaSplitParcel"),
	// p.getDouble("maximalWidthSplitParcel"),
	// p.getDouble("lenDriveway"));
	// }
	//
	// public static SimpleFeatureCollection
	// generateFlagSplitedParcels(SimpleFeature feat, IMultiCurve<IOrientableCurve>
	// iMultiCurve, File geoFile, File tmpFile,
	// Double maximalAreaSplitParcel, Double maximalWidthSplitParcel, Double
	// lenDriveway) throws Exception {
	//
	// return
	// generateFlagSplitedParcels(GeOxygeneGeoToolsTypes.convert2IFeature(feat),
	// iMultiCurve, geoFile, tmpFile, maximalAreaSplitParcel,
	// maximalWidthSplitParcel,
	// lenDriveway);
	//
	// }

	public static IFeatureCollection<IFeature> generateFlagSplitedParcels(IFeature ifeat, IMultiCurve<IOrientableCurve> iMultiCurve, File tmpFile,
			File zoningFile, File regulFile, File outMupFile, Double maximalAreaSplitParcel, Double maximalWidthSplitParcel, Double lenDriveway)
			throws Exception {
		DirectPosition.PRECISION = 3;
		IFeatureCollection<IFeature> batiLargeCollec = ShapefileReader.read(zoningFile.getAbsolutePath());
		IFeatureCollection<IFeature> batiCollec = new FT_FeatureCollection<>();
		batiCollec.addAll(batiLargeCollec.select(ifeat.getGeom()));

		IGeometry geom = ifeat.getGeom();

		// what would that be for?
		IDirectPosition dp = new DirectPosition(0, 0, 0); // geom.centroid();
		geom = geom.translate(-dp.getX(), -dp.getY(), 0);

		List<IOrientableSurface> surfaces = FromGeomToSurface.convertGeom(geom);
		FlagParcelDecomposition fpd = new FlagParcelDecomposition((IPolygon) surfaces.get(0), batiCollec, maximalAreaSplitParcel,
				maximalWidthSplitParcel, lenDriveway, iMultiCurve);
		IFeatureCollection<IFeature> decomp = fpd.decompParcel(0);
		IFeatureCollection<IFeature> ifeatCollOut = new FT_FeatureCollection<>();
		long numParcelle = Math.round(Math.random() * 10000);

		// may we need to normal cut it?
		if (decomp.size() == 1 && ParcelState.isArt3AllowsIsolatedParcel(decomp.get(0), regulFile)) {
			System.out.println("normal decomp instead of flagg decomp allowed");
			File superTemp = Vectors
					.exportSFC(
							ParcelSplit.splitParcels(GeOxygeneGeoToolsTypes.convert2SimpleFeature(ifeat, CRS.decode("EPSG:2154")),
									maximalAreaSplitParcel, maximalWidthSplitParcel, 0, 0, iMultiCurve, 0, false, 5, tmpFile, false),
							new File(tmpFile, "normalCutedParcel.shp"));
			decomp = ShapefileReader.read(superTemp.getAbsolutePath());
		}

		for (IFeature newFeat : decomp) {
			// impeach irregularities
			newFeat.setGeom(newFeat.getGeom().buffer(0.5).buffer(-0.5));

			String newCodeDep = (String) ifeat.getAttribute("CODE_DEP");
			String newCodeCom = (String) ifeat.getAttribute("CODE_COM");
			String newSection = (String) ifeat.getAttribute("SECTION") + "div";
			String newNumero = String.valueOf(numParcelle++);
			String newCode = newCodeDep + newCodeCom + "000" + newSection + newNumero;
			AttributeManager.addAttribute(newFeat, "CODE", newCode, "String");
			AttributeManager.addAttribute(newFeat, "CODE_DEP", newCodeDep, "String");
			AttributeManager.addAttribute(newFeat, "CODE_COM", newCodeCom, "String");
			AttributeManager.addAttribute(newFeat, "COM_ABS", "000", "String");
			AttributeManager.addAttribute(newFeat, "SECTION", newSection, "String");
			AttributeManager.addAttribute(newFeat, "NUMERO", newNumero, "String");
			AttributeManager.addAttribute(newFeat, "INSEE", newCodeDep + newCodeCom, "String");

			double eval = 0.0;
			boolean bati = false;
			boolean simul = false;
			boolean u = false;
			boolean au = false;
			boolean nc = false;

			// we put a small buffer because a lot of houses are just biting neighborhood
			// parcels
			for (IFeature batiIFeat : batiCollec) {
				if (newFeat.getGeom().buffer(-1.5).intersects(batiIFeat.getGeom())) {
					bati = true;
				}
			}

			// we decide here if we want to simul that parcel
			if (!bati) {
				// if the parcels hasn't been decomposed
				if (decomp.size() == 1) {
					// has access to road, we put it whole to simul
					if (fpd.hasRoadAccess((IPolygon) surfaces.get(0))) {
						simul = true;
					}
					// doesn't has to be connected to the road to be urbanized
					else if (ParcelState.isArt3AllowsIsolatedParcel(newFeat, regulFile)) {
						simul = true;
					}
				} else {
					simul = true;
				}
			}

			List<String> zones = ParcelState.parcelInBigZone(newFeat, zoningFile);

			if (zones.contains("U")) {
				u = true;
			}
			if (zones.contains("AU")) {
				au = true;
			}
			if (zones.contains("NC")) {
				nc = true;
			}

			if (simul) {
				eval = ParcelState.getEvalInParcel(newFeat, outMupFile);
			}

			AttributeManager.addAttribute(newFeat, "eval", eval, "String");
			AttributeManager.addAttribute(newFeat, "DoWeSimul", simul, "String");
			AttributeManager.addAttribute(newFeat, "IsBuild", bati, "String");
			AttributeManager.addAttribute(newFeat, "U", u, "String");
			AttributeManager.addAttribute(newFeat, "AU", au, "String");
			AttributeManager.addAttribute(newFeat, "NC", nc, "String");

			ifeatCollOut.add(newFeat);
		}
		return decomp;

	}
}
