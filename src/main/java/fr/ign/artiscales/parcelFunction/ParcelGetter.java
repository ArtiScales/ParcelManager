package fr.ign.artiscales.parcelFunction;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.geotools.data.DataUtilities;
import org.geotools.data.shapefile.ShapefileDataStore;
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
import org.opengis.filter.Filter;
import org.opengis.filter.FilterFactory2;

import fr.ign.artiscales.fields.GeneralFields;
import fr.ign.artiscales.fields.artiscales.ArtiScalesSchemas;
import fr.ign.artiscales.fields.french.FrenchParcelFields;
import fr.ign.artiscales.fields.french.FrenchParcelSchemas;
import fr.ign.artiscales.fields.french.FrenchZoningSchemas;
import fr.ign.cogit.FeaturePolygonizer;
import fr.ign.cogit.geoToolsFunctions.vectors.Collec;
import fr.ign.cogit.geoToolsFunctions.vectors.Geom;

public class ParcelGetter {
	
	static String codeDepFiled = "CODE_DEP";
	static String codeComFiled = "CODE_COM";
	static String typologyField = "typo";
//	public static void main(String[] args) throws Exception {
//		
//	}
	
	/**
	 * get a set of parcel depending to their zoning type. 
	 * @param zone
	 * @param parcelles
	 * @param zoningFile Shapefile containing the french zoning
	 * @return a {@link SimpleFeatureCollection} of parcels that more of the half are contained into the zone 
	 * @throws IOException
	 */
	public static SimpleFeatureCollection getParcelByFrenchZoningType(String zone, SimpleFeatureCollection parcelles, File zoningFile)
			throws IOException {
		ShapefileDataStore zonesSDS = new ShapefileDataStore(zoningFile.toURI().toURL());
		SimpleFeatureCollection zonesSFCBig = zonesSDS.getFeatureSource().getFeatures();
		SimpleFeatureCollection zonesSFC = Collec.cropSFC(zonesSFCBig, parcelles);
		List<String> listZones = FrenchZoningSchemas.getUsualNames(zone);

		DefaultFeatureCollection zoneSelected = new DefaultFeatureCollection();
		try(SimpleFeatureIterator itZonez = zonesSFC.features()) {
			while (itZonez.hasNext()) {
				SimpleFeature zones = itZonez.next();
				if (listZones.contains(zones.getAttribute(GeneralFields.getZoneGenericNameField()))) {
					zoneSelected.add(zones);
				}
			}
		} catch (Exception problem) {
			problem.printStackTrace();
		} 
		DefaultFeatureCollection result = new DefaultFeatureCollection();
		try (SimpleFeatureIterator it = parcelles.features()) {
			while (it.hasNext()) {
				SimpleFeature parcelFeat = it.next();
				try (SimpleFeatureIterator itZone = zoneSelected.features()) {
					while (itZone.hasNext()) {
						SimpleFeature zoneFeat = itZone.next();
						Geometry zoneGeom = (Geometry) zoneFeat.getDefaultGeometry();
						Geometry parcelGeom = (Geometry) parcelFeat.getDefaultGeometry();
						if (zoneGeom.contains(parcelGeom)) {
							result.add(parcelFeat);
						}
						// if the intersection is less than 50% of the parcel, we let it to the other
						// (with the hypothesis that there is only 2 features)
						else if (Geom.scaledGeometryReductionIntersection(Arrays.asList(parcelGeom, zoneGeom)).getArea() > parcelGeom.getArea() / 2) {
							result.add(parcelFeat);
						}
					}
				} catch (Exception problem) {
					problem.printStackTrace();
				} 
			}
		} catch (Exception problem) {
			problem.printStackTrace();
		} 
		zonesSDS.dispose();
		return result.collection();
	}

	/**
	 * Get parcels by their typology. Default typology field name is "typo" and can be changed using method {@link #setTypologyField(String)}.
	 * 
	 * @param typo
	 *            Name of the searched typology
	 * @param parcels
	 *            Collection of parcels
	 * @param communityFile
	 *            ShapeFile of the communities with a filed describing their typology
	 * @return parcels which are included in the communities of a given typology
	 * @throws IOException
	 */
	public static SimpleFeatureCollection getParcelByTypo(String typo, SimpleFeatureCollection parcels, File communityFile)
			throws IOException {

		ShapefileDataStore communitiesSDS = new ShapefileDataStore(communityFile.toURI().toURL());
		SimpleFeatureCollection communitiesSFCBig = communitiesSDS.getFeatureSource().getFeatures();
		SimpleFeatureCollection communitiesSFC = Collec.cropSFC(communitiesSFCBig, parcels);

		FilterFactory2 ff = CommonFactoryFinder.getFilterFactory2(GeoTools.getDefaultHints());
		DefaultFeatureCollection result = new DefaultFeatureCollection();
		try (SimpleFeatureIterator itParcel = parcels.features()) {
			while (itParcel.hasNext()) {
				SimpleFeature parcelFeat = itParcel.next();
				Geometry parcelGeom = (Geometry) parcelFeat.getDefaultGeometry();
				// if tiny parcel, we don't care
				if (parcelGeom.getArea() < 5.0) {
					continue;
				}
				Filter filter = ff.like(ff.property(typologyField), typo);
				try (SimpleFeatureIterator itTypo = communitiesSFC.subCollection(filter).features()){
					while (itTypo.hasNext()) {
						SimpleFeature typoFeat = itTypo.next();
						Geometry typoGeom = (Geometry) typoFeat.getDefaultGeometry();
						if (typoGeom.intersects(parcelGeom)) {
							if (typoGeom.contains(parcelGeom)) {
								result.add(parcelFeat);
								break;
							}
							// if the intersection is less than 50% of the parcel, we let it to the other
							// (with the hypothesis that there is only 2 features)
							// else if (parcelGeom.intersection(typoGeom).getArea() > parcelGeom.getArea() /
							// 2) {
							else if (Geom.scaledGeometryReductionIntersection(Arrays.asList(typoGeom, parcelGeom))
									.getArea() > (parcelGeom.getArea() / 2)) {
								result.add(parcelFeat);
								break;
							} else {
								break;
							}
						}
					}
				} catch (Exception problem) {
					problem.printStackTrace();
				}
			}
		} catch (Exception problem) {
			problem.printStackTrace();
		}
		communitiesSDS.dispose();
		return result.collection();
	}

	public static File getFrenchParcelByZip(File parcelIn, List<String> vals, File fileOut) throws IOException {
		ShapefileDataStore sds = new ShapefileDataStore(parcelIn.toURI().toURL());
		SimpleFeatureCollection sfc = sds.getFeatureSource().getFeatures();
		SimpleFeatureCollection result = getFrenchParcelByZip(sfc, vals);
		sds.dispose();
		return Collec.exportSFC(result, fileOut);
	}

	/**
	 * Get parcels out of a parcel collection corresponding to a list of zipcodes
	 * Zipcodes are not directly contained in a field of the collection but is composed of two fields. Their values are set by default but it's possible to change them with the methods {@link #setCodeComFiled(String) setCodeComFiled} and {@link #setCodeDepFiled(String) setCodeDepFiled}
	 *
	 * @param parcelIn : input parcel collection
	 * @param vals : a list of zipcode values
	 * @return a simple feature collection of parcels having the values contained in <i>vals</i>.
	 * 	 * @throws IOException
	 */
	public static SimpleFeatureCollection getFrenchParcelByZip(SimpleFeatureCollection parcelIn, List<String> vals) throws IOException {
		DefaultFeatureCollection result = new DefaultFeatureCollection();
		for (String val : vals) {
			result.addAll(getFrenchParcelByZip(parcelIn, val));
		}
		return result.collection();
	}

	/**
	 * Get parcels out of a parcel collection with the zip code of them parcels. Zipcodes are not directly contained in a field of the collection but is composed of two fields.
	 * Their values are set by default but it's possible to change them with the methods {@link #setCodeComFiled(String) setCodeComFiled} and {@link #setCodeDepFiled(String).
	 * setCodeDepFiled}
	 * 
	 * @param parcelIn
	 *            : input parcel collection
	 * @param val
	 *            : value of the zipcode
	 * @return a simple feature collection of parcels having the <i>val</i> value. * @throws IOException
	 * @throws IOException
	 */
	public static SimpleFeatureCollection getFrenchParcelByZip(SimpleFeatureCollection parcelIn, String val) throws IOException {
		return getParcelByZip(parcelIn, val, codeDepFiled, codeComFiled);
	}

	/**
	 * Get parcels out of a parcel collection with the zip code of them parcels. zipcode is not directly contained in a field of the collection but is composed of two fields
	 * (usually a state-like code and a community code).
	 * 
	 * @param parcelIn
	 *            : input parcel collection
	 * @param val
	 *            : value of the zipcode
	 * @param firstFieldName
	 *            : first part of the field name which compose zipcode field name
	 * @param secondFieldName
	 *            : second part of the field name which compose zipcode field name
	 * @return a simple feature collection of parcels having the <i>val</i> value.
	 * @throws IOException
	 */
	public static SimpleFeatureCollection getParcelByZip(SimpleFeatureCollection parcelIn, String val, String firstFieldName, String secondFieldName) throws IOException {
		DefaultFeatureCollection result = new DefaultFeatureCollection();
		try (SimpleFeatureIterator it = parcelIn.features()) {
			while (it.hasNext()) {
				SimpleFeature feat = it.next();
				String zipCode = ((String) feat.getAttribute(firstFieldName)).concat(((String) feat.getAttribute(secondFieldName)));
				if (zipCode.equals(val)) {
					result.add(feat);
				}
			}
		} catch (Exception problem) {
			problem.printStackTrace();
		} 
		return result.collection();
	}
	
	/**
	 * Get parcels out of a parcel collection with the zip code of them parcels
	 * 
	 * @param parcelIn
	 *            Input {@link SimpleFeatureCollection} of parcel
	 * @param val
	 *            City number value
	 * @return a simple feature collection of parcels having the <i>val</i> value.
	 * @throws IOException
	 */
	public static SimpleFeatureCollection getParcelByZip(SimpleFeatureCollection parcelIn, String val) throws IOException {
		//we check if the field for zipcodes is present, otherwise we try national types of parcels 
		if(!Collec.isCollecContainsAttribute(parcelIn, ParcelSchema.getMinParcelCommunityField())) {
			return getFrenchParcelByZip(parcelIn, val);
		}
		DefaultFeatureCollection result = new DefaultFeatureCollection();
		Arrays.stream(parcelIn.toArray(new SimpleFeature[0])).forEach(feat -> {
			if (Collec.isSimpleFeatureContainsAttribute(feat, ParcelSchema.getMinParcelCommunityField())
					&& feat.getAttribute(ParcelSchema.getMinParcelCommunityField()) != null
					&& ((String) feat.getAttribute(ParcelSchema.getMinParcelCommunityField())).equals(val)) {
				result.add(feat);
			}
		});
		return result.collection();
	}
	
	/**
	 * prepare the parcel SimpleFeatureCollection and add necessary attributes and informations for an ArtiScales Simulation overload to run on every cities contained into the
	 * parcel file, simulate a single community and automatically cut all parcels regarding to the zoning file
	 * 
	 * @param currentFile
	 * @param buildingFile
	 *            Shapefile containing the building features
	 * @param zoningFile
	 *            Shapefile containing the zoning features
	 * @param parcelFile
	 *            Shapefile containing the parcel features
	 * @param preCutParcels
	 * @return the ready to deal with the selection process parcels under a SimpleFeatureCollection format. Also saves it on the tmpFile on a shapeFile format
	 * @throws Exception
	 */
	public static File getParcels(File buildingFile, File zoningFile, File parcelFile, File currentFile, boolean preCutParcels) throws Exception {
		return getParcels(buildingFile, zoningFile, parcelFile, currentFile, new ArrayList<String>());
	}

	/**
	 * prepare the parcel SimpleFeatureCollection and add necessary attributes and informations for an ArtiScales Simulation overload to simulate a single community and
	 * automatically cut all parcels regarding to the zoning file
	 * 
	 * @param buildingFile
	 *            Shapefile containing the building features
	 * @param zoningFile
	 *            Shapefile containing the zoning features
	 * @param parcelFile
	 *            Shapefile containing the parcel features
	 * @param tmpFolder
	 *            Folder where every temporary file is saved
	 * @param zip
	 *            Community code that must be simulated.
	 * @param preCutParcels
	 *            if true, cut all parcels regarding to the zoning file
	 * @return the ready to deal with the selection process parcels under a SimpleFeatureCollection format. Also saves it on the tmpFile on a shapeFile format
	 * @throws Exception
	 */
	public static File getParcels(File buildingFile, File zoningFile, File parcelFile, File tmpFolder, String zip, boolean preCutParcels) throws Exception {
		List<String> lZip = new ArrayList<String>();
		lZip.add(zip);
		return getParcels(buildingFile, zoningFile, parcelFile, tmpFolder, lZip, null, preCutParcels);
	}

	public static File getParcels(File buildingFile, File zoningFile, File parcelFile, File tmpFile, String zip, File specificParcelsToSimul, boolean preCutParcels)
			throws Exception {
		List<String> lZip = new ArrayList<String>();
		lZip.add(zip);
		return getParcels(buildingFile, zoningFile, parcelFile, tmpFile, lZip, specificParcelsToSimul, preCutParcels);
	}

	/**
	 * @deprecated
	 * prepare the parcel SimpleFeatureCollection and add necessary attributes and informations for an ArtiScales Simulation overload to automatically cut all parcels regarding to
	 * the zoning file
	 * 
	 * @param buildingFile
	 *            Shapefile containing the building features
	 * @param zoningFile
	 *            Shapefile containing the zoning features
	 * @param parcelFile
	 *            Shapefile containing the parcel features
	 * @param tmpFolder
	 *            Folder where every temporary file is saved
	 * @param listZip
	 *            : List of all the communities codes that must be simulated. If empty, we run it on every cities contained into the parcel file
	 * @return the ready to deal with the selection process parcels under a SimpleFeatureCollection format. Also saves it on the tmpFile on a shapeFile format
	 * @throws Exception
	 */
	public static File getParcels(File buildingFile, File zoningFile, File parcelFile, File tmpFolder, List<String> listZip) throws Exception {
		return getParcels(buildingFile, zoningFile, parcelFile, tmpFolder, listZip, null, true);
	}

	/**
	 * @deprecated prepare the parcel SimpleFeatureCollection and add necessary attributes and informations for an ArtiScales Simulation
	 * 
	 * @param buildingFile
	 *            Shapefile containing the building features
	 * @param zoningFile
	 *            Shapefile containing the zoning features
	 * @param parcelFile
	 *            Shapefile containing the parcel features
	 * @param tmpFolder
	 *            Folder where every temporary file is saved
	 * @param listZip
	 *            List of all the communities codes that must be simulated. If empty, we work on every cities contained into the parcel file
	 * @param specificParcelsToSimul
	 *            ShapeFile of specific parcel that will be simulated. If empty, will simulate all parcels
	 * @param preCutParcels
	 *            If cut all parcels regarding to the zoning file
	 * @return the ready to deal with the selection process parcels under a SimpleFeatureCollection format. Also saves it on the tmpFile on a shapeFile format
	 * @throws Exception
	 */
	public static File getParcels(File buildingFile, File zoningFile, File parcelFile, File tmpFolder, List<String> listZip, File specificParcelsToSimul,
			boolean preCutParcels) throws Exception {

//		DirectPosition.PRECISION = 3;
//		File result = new File("");
//		for (File f : geoFile.listFiles()) {
//			if (f.toString().contains("parcel.shp")) {
//				result = f;
//			}
//		}
		
		File result = parcelFile;
		ShapefileDataStore parcelSDS = new ShapefileDataStore(result.toURI().toURL());
		SimpleFeatureCollection parcelsSFC = parcelSDS.getFeatureSource().getFeatures();
		ShapefileDataStore shpDSBati = new ShapefileDataStore(buildingFile.toURI().toURL());

		// if we decided to work on a set of parcels
		if (specificParcelsToSimul != null && specificParcelsToSimul.exists()) {
			ShapefileDataStore parcelSpecificSDS = new ShapefileDataStore(specificParcelsToSimul.toURI().toURL());
			parcelsSFC = DataUtilities.collection(parcelSpecificSDS.getFeatureSource().getFeatures());
			parcelSpecificSDS.dispose();
		}
		// if we decided to work on a set of cities
		else if (!listZip.isEmpty()) {
			FilterFactory2 ff = CommonFactoryFinder.getFilterFactory2(GeoTools.getDefaultHints());
			DefaultFeatureCollection df = new DefaultFeatureCollection();
			for (String zip : listZip) {

				// could have been nicer, but Filters are some pain in the socks and doesn't
				// work in I factor the code

				// if the zip contains Bensançons's Section parts
				if (zip.length() > 5) {
					// those zip are containing the Section value too
					String codep = zip.substring(0, 2);
					String cocom = zip.substring(2, 5);
					String section = zip.substring(5);

					Filter filterDep = ff.like(ff.property("CODE_DEP"), codep);
					Filter filterCom = ff.like(ff.property("CODE_COM"), cocom);
					Filter filterSection = ff.like(ff.property("SECTION"), section);
					df.addAll(parcelsSFC.subCollection(filterDep).subCollection(filterCom).subCollection(filterSection));
				} else {
					String codep = zip.substring(0, 2);
					String cocom = zip.substring(2, 5);
					Filter filterDep = ff.like(ff.property("CODE_DEP"), codep);
					Filter filterCom = ff.like(ff.property("CODE_COM"), cocom);
					df.addAll(parcelsSFC.subCollection(filterDep).subCollection(filterCom));
				}
			}
			parcelsSFC = df.collection();
		}

		// if we cut all the parcel regarding to the zoning code
		if (preCutParcels) {
			File tmpParcel = Collec.exportSFC(parcelsSFC, new File(tmpFolder, "tmpParcel.shp"));
			File[] polyFiles = { tmpParcel, zoningFile };
			List<Polygon> polygons = FeaturePolygonizer.getPolygons(polyFiles);
			// register to precise every parcel that are in the output
			List<String> codeParcelsTot = new ArrayList<String>();

			// auto parcel feature builder
			SimpleFeatureBuilder sfSimpleBuilder = FrenchParcelSchemas.getSFBFrenchParcel();
			DefaultFeatureCollection write = new DefaultFeatureCollection();

			// for every made up polygons out of zoning and parcels
			for (Geometry poly : polygons) {
				// for every parcels around the polygon
				SimpleFeatureCollection snaped = Collec.snapDatas(parcelsSFC, poly.getBoundary());
				SimpleFeatureIterator parcelIt = snaped.features();
				try {
					while (parcelIt.hasNext()) {
						SimpleFeature feat = parcelIt.next();
						// if the polygon part was between that parcel, we add its attribute
						if (((Geometry) feat.getDefaultGeometry()).buffer(1).contains(poly)) {
							sfSimpleBuilder.set("the_geom", GeometryPrecisionReducer.reduce(poly, new PrecisionModel(100)));
							sfSimpleBuilder.set("CODE_DEP", feat.getAttribute("CODE_DEP"));
							sfSimpleBuilder.set("CODE_COM", feat.getAttribute("CODE_COM"));
							sfSimpleBuilder.set("COM_ABS", feat.getAttribute("COM_ABS"));
							sfSimpleBuilder.set("SECTION", feat.getAttribute("SECTION"));
							String num = (String) feat.getAttribute("NUMERO");
							// if a part has already been added
							String code = FrenchParcelFields.makeFrenchParcelCode(feat);
							if (codeParcelsTot.contains(code)) {
								while (true) {
									num = num + "bis";
									code = code + "bis";
									sfSimpleBuilder.set("NUMERO", num);
									if (!codeParcelsTot.contains(code)) {
										codeParcelsTot.add(code);
										break;
									}
								}
							} else {
								sfSimpleBuilder.set("NUMERO", feat.getAttribute("NUMERO"));
								codeParcelsTot.add(code);
							}
							sfSimpleBuilder.set("CODE", code);
							write.add(sfSimpleBuilder.buildFeature(null));
							// this could be nicer but it doesn't work
							// for (int i = 0; i < codeParcelsTot.size(); i++) {
							// if (codeParcelsTot.get(i).substring(0, 13).equals(code)) {
							// num = num + "bis";
							// code = code + "bis";
							// }
							// }
							// sfSimpleBuilder.set("NUMERO", num);
							// sfSimpleBuilder.set("CODE", code);
							// codeParcelsTot.add(code);
							// write.add(sfSimpleBuilder.buildFeature(null));

						}
					}
				} catch (Exception problem) {
					problem.printStackTrace();
				} finally {
					parcelIt.close();
				}
			}
			parcelsSFC = write.collection();
		}
		// under the carpet
		// ReferencedEnvelope carpet = parcelsSFC.getBounds();
		// Coordinate[] coord = { new Coordinate(carpet.getMaxX(), carpet.getMaxY()), new Coordinate(carpet.getMaxX(), carpet.getMinY()),
		// new Coordinate(carpet.getMinX(), carpet.getMinY()), new Coordinate(carpet.getMinX(), carpet.getMaxY()),
		// new Coordinate(carpet.getMaxX(), carpet.getMaxY()) };
		//
		// GeometryFactory gf = new GeometryFactory();
		// Polygon bbox = gf.createPolygon(coord);
		// SimpleFeatureCollection batiSFC = Vectors.snapDatas(shpDSBati.getFeatureSource().getFeatures(), bbox);
		SimpleFeatureCollection batiSFC = shpDSBati.getFeatureSource().getFeatures();

		// SimpleFeatureCollection batiSFC =
		// Vectors.snapDatas(shpDSBati.getFeatureSource().getFeatures(),
		// Vectors.unionSFC(parcels));

		SimpleFeatureBuilder finalParcelBuilder = ArtiScalesSchemas.getSFBParcelAsAS();
		DefaultFeatureCollection newParcel = new DefaultFeatureCollection();

		// int tot = parcels.size();
		try (SimpleFeatureIterator parcelIt = parcelsSFC.features()) {
			parc: while (parcelIt.hasNext()) {
				SimpleFeature feat = parcelIt.next();
				Geometry geom = (Geometry) feat.getDefaultGeometry();
				if (geom.getArea() > 5.0) {
					// put the best cell evaluation into the parcel
					// say if the parcel intersects a particular zoning type
					boolean u = false;
					boolean au = false;
					boolean nc = false;

					for (String s : ParcelState.parcelInBigZone(feat, zoningFile)) {
						if (s.equals("AU")) {
							au = true;
						} else if (s.equals("U")) {
							u = true;
						} else if (s.equals("NC")) {
							nc = true;
						} else {
							// if the parcel is outside of the zoning file, we don't keep it
							continue parc;
						}
					}
					finalParcelBuilder.set("the_geom", geom);
					finalParcelBuilder.set("CODE", FrenchParcelFields.makeFrenchParcelCode(feat));
					finalParcelBuilder.set("CODE_DEP", feat.getAttribute("CODE_DEP"));
					finalParcelBuilder.set("CODE_COM", feat.getAttribute("CODE_COM"));
					finalParcelBuilder.set("COM_ABS", feat.getAttribute("COM_ABS"));
					finalParcelBuilder.set("SECTION", feat.getAttribute("SECTION"));
					finalParcelBuilder.set("NUMERO", feat.getAttribute("NUMERO"));
					finalParcelBuilder.set("INSEE", ((String) feat.getAttribute("CODE_DEP")) + ((String) feat.getAttribute("CODE_COM")));
					finalParcelBuilder.set("eval", 0);
					finalParcelBuilder.set("DoWeSimul", false);
					finalParcelBuilder.set("IsBuild", ParcelState.isAlreadyBuilt( batiSFC,feat));
					finalParcelBuilder.set("U", u);
					finalParcelBuilder.set("AU", au);
					finalParcelBuilder.set("NC", nc);
					newParcel.add(finalParcelBuilder.buildFeature(null));
				}
			}

		} catch (Exception problem) {
			problem.printStackTrace();
		} 
		parcelSDS.dispose();
		shpDSBati.dispose();

		return Collec.exportSFC(newParcel.collection(), new File(tmpFolder, "parcelProcessed.shp"));
	}

	public static String getCodeDepFiled() {
		return codeDepFiled;
	}

	public static void setCodeDepFiled(String codeDepFiled) {
		ParcelGetter.codeDepFiled = codeDepFiled;
	}

	public static String getCodeComFiled() {
		return codeComFiled;
	}

	public static void setCodeComFiled(String codeComFiled) {
		ParcelGetter.codeComFiled = codeComFiled;
	}

	public static String getTypologyField() {
		return typologyField;
	}

	public static void setTypologyField(String typologyField) {
		ParcelGetter.typologyField = typologyField;
	}
}
