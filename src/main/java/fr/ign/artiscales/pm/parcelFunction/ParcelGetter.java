package fr.ign.artiscales.pm.parcelFunction;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import org.geotools.data.DataStore;
import org.geotools.data.collection.SpatialIndexFeatureCollection;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.factory.CommonFactoryFinder;
import org.geotools.feature.DefaultFeatureCollection;
import org.geotools.util.factory.GeoTools;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.CoordinateXY;
import org.locationtech.jts.geom.Geometry;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.filter.Filter;
import org.opengis.filter.FilterFactory2;

import fr.ign.artiscales.pm.fields.GeneralFields;
import fr.ign.artiscales.pm.fields.french.FrenchZoningSchemas;
import fr.ign.artiscales.tools.geoToolsFunctions.vectors.Collec;
import fr.ign.artiscales.tools.geoToolsFunctions.vectors.Geom;
import fr.ign.artiscales.tools.geoToolsFunctions.vectors.Geopackages;

public class ParcelGetter {
	
	static String codeDepFiled = "CODE_DEP";
	static String codeComFiled = "CODE_COM";
	static String typologyField = "typo";
//	public static void main(String[] args) throws Exception {
//		
//	}
	
	/**
	 * Get a set of parcel depending to their zoning type. 
	 * @param zone
	 * @param parcelles
	 * @param zoningFile Shapefile containing the french zoning
	 * @return a {@link SimpleFeatureCollection} of parcels that more of the half are contained into the zone 
	 * @throws IOException
	 */
	public static SimpleFeatureCollection getParcelByFrenchZoningType(String zone, SimpleFeatureCollection parcelles, File zoningFile)
			throws IOException {
		DataStore zonesSDS = Geopackages.getDataStore(zoningFile);
		SimpleFeatureCollection zonesSFC = Collec.snapDatas(zonesSDS.getFeatureSource(zonesSDS.getTypeNames()[0]).getFeatures(), parcelles);
		List<String> listZones = FrenchZoningSchemas.getUsualNames(zone);
		DefaultFeatureCollection zoneSelected = new DefaultFeatureCollection();
		try (SimpleFeatureIterator itZonez = zonesSFC.features()) {
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
				Geometry parcelGeom = (Geometry) parcelFeat.getDefaultGeometry();
				try (SimpleFeatureIterator itZone = zoneSelected.features()) {
					while (itZone.hasNext()) {
						SimpleFeature zoneFeat = itZone.next();
						Geometry zoneGeom = (Geometry) zoneFeat.getDefaultGeometry();
						if (zoneGeom.contains(parcelGeom))
							result.add(parcelFeat);
						// if the intersection is less than 50% of the parcel, we let it to the other
						// (with the hypothesis that there is only 2 features)
						else if (Geom.scaledGeometryReductionIntersection(Arrays.asList(parcelGeom, zoneGeom)).getArea() > parcelGeom.getArea() / 2) 
							result.add(parcelFeat);
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
	 * @param zoningFile
	 *            Geopackage of the communities with a filed describing their typology
	 * @return parcels which are included in the communities of a given typology
	 * @throws IOException
	 */
	public static SimpleFeatureCollection getParcelByTypo(String typo, SimpleFeatureCollection parcels, File zoningFile) throws IOException {
		DataStore zoningDS = Geopackages.getDataStore(zoningFile);
		SimpleFeatureCollection zoningSFC = Collec.snapDatas(new SpatialIndexFeatureCollection(zoningDS.getFeatureSource(zoningDS.getTypeNames()[0]).getFeatures()), parcels);
		FilterFactory2 ff = CommonFactoryFinder.getFilterFactory2(GeoTools.getDefaultHints());
		Filter filter = ff.like(ff.property(typologyField), typo);
		DefaultFeatureCollection result = new DefaultFeatureCollection();
		try (SimpleFeatureIterator itParcel = parcels.features()) {
			while (itParcel.hasNext()) {
				SimpleFeature parcelFeat = itParcel.next();
				Geometry parcelGeom = (Geometry) parcelFeat.getDefaultGeometry();
				// if tiny parcel, we don't care
				Filter filterGeom = ff.bbox(ff.property(parcelFeat.getFeatureType().getGeometryDescriptor().getLocalName()), parcelFeat.getBounds());
				if (parcelGeom.getArea() < 5.0) 
					continue;
				try (SimpleFeatureIterator itTypo = zoningSFC.subCollection(filter).subCollection(filterGeom).features()){
					while (itTypo.hasNext()) {
						Geometry typoGeom = (Geometry) itTypo.next().getDefaultGeometry();
						// if there's an containing or the intersection is less than 50% of the parcel, we let it to the other
						// (with the hypothesis that there is only 2 features)
						if (typoGeom.contains(parcelGeom) || Geom.scaledGeometryReductionIntersection(Arrays.asList(typoGeom, parcelGeom))
								.getArea() > (parcelGeom.getArea() / 2)) {
							result.add(parcelFeat);
							break;
						}
					}
				} catch (Exception problem) {
					problem.printStackTrace();
				}
			}
		} catch (Exception problem) {
			problem.printStackTrace();
		}
		zoningDS.dispose();
		return result.collection();
	}

	public static File getFrenchParcelByZip(File parcelIn, List<String> vals, File fileOut) throws IOException {
		DataStore ds = Geopackages.getDataStore(parcelIn);
		SimpleFeatureCollection result = getFrenchParcelByZip(ds.getFeatureSource(ds.getTypeNames()[0]).getFeatures(), vals);
		ds.dispose();
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
	 * Their values are set by default but it's possible to change them with the methods {@link #setCodeComFiled(String)} and {@link #setCodeDepFiled(String)}
	 * 
	 * @param parcelIn
	 *            Input parcel collection
	 * @param val
	 *            Value of the zipcode
	 * @return A simple feature collection of parcels having the <i>val</i> value. * @throws IOException
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
	 *            Input parcel collection
	 * @param val
	 *            Value of the zipcode
	 * @param firstFieldName
	 *            First part of the field name which compose zipcode field name
	 * @param secondFieldName
	 *            Second part of the field name which compose zipcode field name
	 * @return a simple feature collection of parcels having the <i>val</i> value.
	 * @throws IOException
	 */
	public static SimpleFeatureCollection getParcelByZip(SimpleFeatureCollection parcelIn, String val, String firstFieldName, String secondFieldName) throws IOException  {
		DefaultFeatureCollection result = new DefaultFeatureCollection();
		try (SimpleFeatureIterator it = parcelIn.features()) {
			while (it.hasNext()) {
				SimpleFeature feat = it.next();
				if (((String) feat.getAttribute(firstFieldName)).concat(((String) feat.getAttribute(secondFieldName))).equals(val)) {
				  Geometry original = (Geometry) feat.getDefaultGeometry();
          Geometry g = original.getFactory().createGeometry(original);
          g.apply((Coordinate c) -> coord2D(c));
          g.geometryChanged();
          feat.setDefaultGeometry(g);
					result.add(feat);
				}
			}
		} catch (Exception problem) {
			problem.printStackTrace();
		} 
		return result.collection();
	}
  private static void coord2D(Coordinate c) {
    if (!CoordinateXY.class.isInstance(c))
      c.setZ(Double.NaN);
  }	
	/**
	 * Get parcels out of a parcel collection with the zip code of them parcels.
	 * 
	 * @param parcelIn
	 *            Input {@link SimpleFeatureCollection} of parcel
	 * @param val
	 *            City number value
	 * @return a simple feature collection of parcels having the <i>val</i> value.
	 * @throws IOException
	 */
	public static SimpleFeatureCollection getParcelByCommunityCode(SimpleFeatureCollection parcelIn, String val) throws IOException {
		// we check if the field for zipcodes is present, otherwise we try national types of parcels
		if (parcelIn == null || parcelIn.isEmpty())
			return null;
		if (!Collec.isCollecContainsAttribute(parcelIn, ParcelSchema.getMinParcelCommunityField())) {
			switch (GeneralFields.getParcelFieldType()) {
			case "french":
				return getFrenchParcelByZip(parcelIn, val);
			}
		}
		DefaultFeatureCollection result = new DefaultFeatureCollection();
		Arrays.stream(parcelIn.toArray(new SimpleFeature[0])).forEach(feat -> {
			if (Collec.isSimpleFeatureContainsAttribute(feat, ParcelSchema.getMinParcelCommunityField())
					&& feat.getAttribute(ParcelSchema.getMinParcelCommunityField()) != null
					&& ((String) feat.getAttribute(ParcelSchema.getMinParcelCommunityField())).equals(val))
				result.add(feat);
		});
		return result.collection();
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
