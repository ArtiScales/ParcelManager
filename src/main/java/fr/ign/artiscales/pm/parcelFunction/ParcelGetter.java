package fr.ign.artiscales.pm.parcelFunction;

import fr.ign.artiscales.pm.fields.GeneralFields;
import fr.ign.artiscales.pm.fields.french.FrenchZoningSchemas;
import fr.ign.artiscales.tools.geoToolsFunctions.vectors.Geom;
import fr.ign.artiscales.tools.geoToolsFunctions.vectors.collec.CollecMgmt;
import fr.ign.artiscales.tools.geoToolsFunctions.vectors.collec.CollecTransform;
import org.geotools.data.DataStore;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.factory.CommonFactoryFinder;
import org.geotools.feature.DefaultFeatureCollection;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.CoordinateXY;
import org.locationtech.jts.geom.Geometry;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.filter.Filter;
import org.opengis.filter.FilterFactory2;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * Methods to get parcels from collections regarding specific criterion
 */
public class ParcelGetter {

    private static String codeDepFieldName = "CODE_DEP";
    private static String codeComFieldName = "CODE_COM";
    private static String typologyFieldName = "typo";
//	public static void main(String[] args) throws Exception {
//		
//	}

    /**
     * Get a set of parcel depending to their zoning type.
     *
     * @param zone       zone to select parcels from
     * @param parcels    input parcels
     * @param zoningFile Shapefile containing the french zoning
     * @return a {@link SimpleFeatureCollection} of parcels that more of the half are contained into the zone
     * @throws IOException reading zoning file
     */
    public static SimpleFeatureCollection getParcelByFrenchZoningType(String zone, SimpleFeatureCollection parcels, File zoningFile) throws IOException {
        DataStore zonesDS = CollecMgmt.getDataStore(zoningFile);
        SimpleFeatureCollection zonesSFC = CollecTransform.selectIntersection(zonesDS.getFeatureSource(zonesDS.getTypeNames()[0]).getFeatures(), parcels);
        List<String> listZones = FrenchZoningSchemas.getUsualNames(zone);
        DefaultFeatureCollection zoneSelected = new DefaultFeatureCollection();
        try (SimpleFeatureIterator itZonez = zonesSFC.features()) {
            while (itZonez.hasNext()) {
                SimpleFeature zones = itZonez.next();
                if (listZones.contains((String) zones.getAttribute(GeneralFields.getZoneGenericNameField())))
                    zoneSelected.add(zones);
            }
        } catch (Exception problem) {
            problem.printStackTrace();
        }
        DefaultFeatureCollection result = new DefaultFeatureCollection();
        try (SimpleFeatureIterator it = parcels.features()) {
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
                        else if (Geom.safeIntersection(Arrays.asList(parcelGeom, zoneGeom)).getArea() > parcelGeom.getArea() / 2)
                            result.add(parcelFeat);
                    }
                } catch (Exception problem) {
                    problem.printStackTrace();
                }
            }
        } catch (Exception problem) {
            problem.printStackTrace();
        }
        zonesDS.dispose();
        return result;
    }

    /**
     * Get parcels by their typology. Default typology field name is "typo" and can be changed using method {@link #setTypologyFieldName(String)}.
     *
     * @param typo       Name of the searched typology
     * @param parcels    Collection of parcels
     * @param zoningFile Geopackage of the communities with a filed describing their typology
     * @return parcels which are included in the communities of a given typology
     * @throws IOException Reading zoning file
     */
    public static SimpleFeatureCollection getParcelByTypo(String typo, SimpleFeatureCollection parcels, File zoningFile) throws IOException {
        DataStore zoningDS = CollecMgmt.getDataStore(zoningFile);
        SimpleFeatureCollection zoningSFC = CollecTransform.selectIntersection(zoningDS.getFeatureSource(zoningDS.getTypeNames()[0]).getFeatures(), parcels);
        FilterFactory2 ff = CommonFactoryFinder.getFilterFactory2();
        Filter filter = ff.like(ff.property(typologyFieldName), typo);
        DefaultFeatureCollection result = new DefaultFeatureCollection();
        try (SimpleFeatureIterator itParcel = parcels.features()) {
            while (itParcel.hasNext()) {
                SimpleFeature parcelFeat = itParcel.next();
                Geometry parcelGeom = (Geometry) parcelFeat.getDefaultGeometry();
                // if tiny parcel, we don't care
                Filter filterGeom = ff.bbox(ff.property(parcelFeat.getFeatureType().getGeometryDescriptor().getLocalName()), parcelFeat.getBounds());
                if (parcelGeom.getArea() < 5.0)
                    continue;
                try (SimpleFeatureIterator itTypo = zoningSFC.subCollection(filter).subCollection(filterGeom).features()) {
                    while (itTypo.hasNext()) {
                        Geometry typoGeom = (Geometry) itTypo.next().getDefaultGeometry();
                        // if there's an containing or the intersection is less than 50% of the parcel, we let it to the other
                        // (with the hypothesis that there is only 2 features)
                        if (typoGeom.contains(parcelGeom) || Geom.safeIntersection(Arrays.asList(typoGeom, parcelGeom))
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
        return result;
    }

    /**
     * Write parcels out of a parcel collection corresponding to a list of zipcodes in a new geo file.
     * Zipcodes are not directly contained in a field of the collection but is composed of two fields. Their values are set by default but it's possible to change them with the methods {@link #setCodeComFieldName(String)} and {@link #setCodeDepFieldName(String)}
     *
     * @param parcelIn input parcel collection
     * @param vals     a list of zipcode values
     * @param fileOut  file to export selected parcels
     * @return a simple feature collection of parcels having the values contained in <i>vals</i>.
     * @throws IOException writing file
     */
    public static File getParcelByZip(File parcelIn, List<String> vals, File fileOut) throws IOException {
        DataStore ds = CollecMgmt.getDataStore(parcelIn);
        SimpleFeatureCollection result = getParcelByZip(ds.getFeatureSource(ds.getTypeNames()[0]).getFeatures(), vals);
        ds.dispose();
        return CollecMgmt.exportSFC(result, fileOut);
    }

    /**
     * Get parcels out of a parcel collection corresponding to a list of zipcodes
     * Zipcodes are not directly contained in a field of the collection but is composed of two fields. Their values are set by default but it's possible to change them with the methods {@link #setCodeComFieldName(String)} and {@link #setCodeDepFieldName(String)}
     *
     * @param parcelIn input parcel collection
     * @param vals     a list of zipcode values
     * @return a simple feature collection of parcels having the values contained in <i>vals</i>.
     */
    public static SimpleFeatureCollection getParcelByZip(SimpleFeatureCollection parcelIn, List<String> vals) {
        DefaultFeatureCollection result = new DefaultFeatureCollection();
        for (String val : vals) {
            result.addAll(getParcelByZip(parcelIn, val));
        }
        return result;
    }

    /**
     * Get parcels out of a parcel collection with the zip code of them parcels. Zipcodes are not directly contained in a field of the collection but is composed of two fields.
     * Their values are set by default but it's possible to change them with the methods {@link #setCodeComFieldName(String)} and {@link #setCodeDepFieldName(String)}
     *
     * @param parcelIn Input parcel collection
     * @param val      Value of the zipcode. Can contain comma separated values
     * @return A simple feature collection of parcels having the <i>val</i> value. * @throws IOException
     */
    public static SimpleFeatureCollection getParcelByZip(SimpleFeatureCollection parcelIn, String val) {
        if (val.contains(","))
            return getParcelByZip(parcelIn, Arrays.asList(val.split(",")));
        return getParcelByZip(parcelIn, val, codeDepFieldName, codeComFieldName);
    }

    /**
     * Get parcels out of a parcel collection with the zip code of them parcels. zipcode is not directly contained in a field of the collection but is composed of two fields
     * (usually a state-like code and a community code).
     * todo why are we working on geometries like that ??
     *
     * @param parcelIn        Input parcel collection
     * @param val             Value of the zipcode
     * @param firstFieldName  First part of the field name which compose zipcode field name
     * @param secondFieldName Second part of the field name which compose zipcode field name
     * @return a simple feature collection of parcels having the <i>val</i> value.
     */
    public static SimpleFeatureCollection getParcelByZip(SimpleFeatureCollection parcelIn, String val, String firstFieldName, String secondFieldName) {
        DefaultFeatureCollection result = new DefaultFeatureCollection();
        try (SimpleFeatureIterator it = parcelIn.features()) {
            while (it.hasNext()) {
                SimpleFeature feat = it.next();
                if (((String) feat.getAttribute(firstFieldName)).concat(((String) feat.getAttribute(secondFieldName))).equals(val)) {
                    Geometry original = (Geometry) feat.getDefaultGeometry();
                    Geometry g = original.getFactory().createGeometry(original);
                    g.apply(ParcelGetter::coord2D);
                    g.geometryChanged();
                    feat.setDefaultGeometry(g);
                    result.add(feat);
                }
            }
        } catch (Exception problem) {
            problem.printStackTrace();
        }
        return result;
    }

    private static void coord2D(Coordinate c) {
        if (!(c instanceof CoordinateXY))
            c.setZ(Double.NaN);
    }

    /**
     * Get parcels out of a parcel collection with the zip code of them parcels.
     *
     * @param parcelIn Input {@link SimpleFeatureCollection} of parcel
     * @param val      City number value
     * @return a simple feature collection of parcels having the <i>val</i> value.
     */
    public static SimpleFeatureCollection getParcelByCommunityCode(SimpleFeatureCollection parcelIn, String val) {
        // we check if the field for zipcodes is present, otherwise we try national types of parcels
        if (parcelIn == null || parcelIn.isEmpty())
            return null;
        if (!CollecMgmt.isCollecContainsAttribute(parcelIn, ParcelSchema.getParcelCommunityField())) {
            switch (GeneralFields.getParcelFieldType()) {
                case "french":
                    return getParcelByZip(parcelIn, val);
            }
        }
        DefaultFeatureCollection result = new DefaultFeatureCollection();
        try (SimpleFeatureIterator it = parcelIn.features()) {
            while (it.hasNext()) {
                SimpleFeature feat = it.next();
                if (CollecMgmt.isSimpleFeatureContainsAttribute(feat, ParcelSchema.getParcelCommunityField())
                        && feat.getAttribute(ParcelSchema.getParcelCommunityField()) != null
                        && feat.getAttribute(ParcelSchema.getParcelCommunityField()).equals(val))
                    result.add(feat);
            }
        }

        return result;
    }

    /**
     * Get the department code field name that later forms the zipcode.
     *
     * @return department code field name (<i>CODE_DEP</i> by default)
     */
    public static String getCodeDepFieldName() {
        return codeDepFieldName;
    }

    /**
     * Set a new department code field name that later forms the zipcode.
     *
     * @param codeDepFieldName new department code field name.
     */
    public static void setCodeDepFieldName(String codeDepFieldName) {
        ParcelGetter.codeDepFieldName = codeDepFieldName;
    }

    /**
     * Get a new community field name that later forms the zipcode.
     *
     * @return current community code field name (<i>CODE_COM</i> by default).
     */
    public static String getCodeComFieldName() {
        return codeComFieldName;
    }

    /**
     * Set a new community field name that later forms the zipcode.
     *
     * @param codeComFieldName new community code field name.
     */
    public static void setCodeComFieldName(String codeComFieldName) {
        ParcelGetter.codeComFieldName = codeComFieldName;
    }

    /**
     * Get the field that sets a typology.
     *
     * @return topology field name (<i>typo</i> by default).
     */
    public static String getTypologyFieldName() {
        return typologyFieldName;
    }

    /**
     * Set the field that sets a typology.
     *
     * @param typologyFieldName new typology field name
     */
    public static void setTypologyFieldName(String typologyFieldName) {
        ParcelGetter.typologyFieldName = typologyFieldName;
    }
}
