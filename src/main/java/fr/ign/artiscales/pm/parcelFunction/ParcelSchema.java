package fr.ign.artiscales.pm.parcelFunction;

import fr.ign.artiscales.tools.geoToolsFunctions.vectors.collec.CollecMgmt;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.geotools.referencing.CRS;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Polygon;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.AttributeDescriptor;
import org.opengis.referencing.FactoryException;

/**
 * Methods to deal with parcel's schemas
 */
public class ParcelSchema {

    static String minParcelNumberField = "NUMERO";
    static String minParcelSectionField = "SECTION";
    static String minParcelCommunityField = "DEPCOM";

    static String epsg = "EPSG:2154";

    /////////////////////
    /////////////////////
    //// Minimal Parcel Schema : minimal parcel schema for a Parcel Manager Processing
    /////////////////////
    /////////////////////

    public static SimpleFeatureBuilder getSFBMinParcel() {
        SimpleFeatureTypeBuilder sfTypeBuilder = new SimpleFeatureTypeBuilder();
        sfTypeBuilder.setName("minParcel");
        try {
            sfTypeBuilder.setCRS(CRS.decode(epsg));
        } catch (FactoryException e) {
            e.printStackTrace();
        }
        sfTypeBuilder.add(CollecMgmt.getDefaultGeomName(), Polygon.class);
        sfTypeBuilder.setDefaultGeometry(CollecMgmt.getDefaultGeomName());
        sfTypeBuilder.add(minParcelSectionField, String.class);
        sfTypeBuilder.add(minParcelNumberField, String.class);
        sfTypeBuilder.add(minParcelCommunityField, String.class);
        return new SimpleFeatureBuilder(sfTypeBuilder.buildFeatureType());
    }

    public static SimpleFeatureBuilder getSFBMinParcelMulti() {
        SimpleFeatureTypeBuilder sfTypeBuilder = new SimpleFeatureTypeBuilder();
        sfTypeBuilder.setName("minParcel");
        try {
            sfTypeBuilder.setCRS(CRS.decode(epsg));
        } catch (FactoryException e) {
            e.printStackTrace();
        }
        sfTypeBuilder.add(CollecMgmt.getDefaultGeomName(), MultiPolygon.class);
        sfTypeBuilder.setDefaultGeometry(CollecMgmt.getDefaultGeomName());
        sfTypeBuilder.add(minParcelSectionField, String.class);
        sfTypeBuilder.add(minParcelNumberField, String.class);
        sfTypeBuilder.add(minParcelCommunityField, String.class);

        return new SimpleFeatureBuilder(sfTypeBuilder.buildFeatureType());
    }

    public static SimpleFeatureBuilder setSFBMinParcelWithFeat(SimpleFeature feat, SimpleFeatureType schema) {
        return setSFBMinParcelWithFeat(feat, new SimpleFeatureBuilder(schema), schema);
    }

    public static SimpleFeatureBuilder setSFBMinParcelWithFeat(SimpleFeature feat, SimpleFeatureBuilder builder, SimpleFeatureType schema) {
        builder.set(schema.getGeometryDescriptor().getName().toString(), feat.getDefaultGeometry());
        builder.set(minParcelSectionField, feat.getAttribute(minParcelSectionField));
        builder.set(minParcelNumberField, feat.getAttribute(minParcelNumberField));

        // setting zipcode
        if (CollecMgmt.isSimpleFeatureContainsAttribute(feat, minParcelCommunityField))
            builder.set(minParcelCommunityField, feat.getAttribute(minParcelCommunityField));
            // if looks like French parcel
        else if (CollecMgmt.isSimpleFeatureContainsAttribute(feat, "CODE_DEP"))
            builder.set(ParcelSchema.getMinParcelCommunityField(),
                    ((String) feat.getAttribute("CODE_DEP")).concat((String) feat.getAttribute("CODE_COM")));
        return builder;
    }

    public static SimpleFeatureBuilder getSFBMinParcelSplit() {
        SimpleFeatureTypeBuilder sfTypeBuilder = new SimpleFeatureTypeBuilder();
        sfTypeBuilder.setName("minParcelSplit");
        try {
            sfTypeBuilder.setCRS(CRS.decode(epsg));
        } catch (FactoryException e) {
            e.printStackTrace();
        }
        sfTypeBuilder.add(CollecMgmt.getDefaultGeomName(), Polygon.class);
        sfTypeBuilder.setDefaultGeometry(CollecMgmt.getDefaultGeomName());
        sfTypeBuilder.add(minParcelSectionField, String.class);
        sfTypeBuilder.add(minParcelCommunityField, String.class);
        sfTypeBuilder.add(minParcelNumberField, String.class);
        sfTypeBuilder.add(MarkParcelAttributeFromPosition.getMarkFieldName(), Integer.class);
        return new SimpleFeatureBuilder(sfTypeBuilder.buildFeatureType());
    }

    public static SimpleFeatureBuilder setSFBMinParcelSplitWithFeat(SimpleFeature feat, SimpleFeatureType schema) {
        return setSFBMinParcelSplitWithFeat(feat, schema, (int) feat.getAttribute(MarkParcelAttributeFromPosition.getMarkFieldName()));
    }

    public static SimpleFeatureBuilder setSFBMinParcelSplitWithFeat(SimpleFeature feat, SimpleFeatureType schema, int isSplit) {
        return setSFBMinParcelSplitWithFeat(feat, new SimpleFeatureBuilder(schema), schema, isSplit);
    }

    public static SimpleFeatureBuilder setSFBMinParcelSplitWithFeat(SimpleFeature feat, SimpleFeatureBuilder builder, SimpleFeatureType schema,
                                                                    int isSplit) {
        builder.set(schema.getGeometryDescriptor().getName().toString(), feat.getDefaultGeometry());
        builder.set(MarkParcelAttributeFromPosition.getMarkFieldName(), isSplit);
        builder.set(minParcelSectionField, feat.getAttribute(minParcelSectionField));
        builder.set(minParcelNumberField, feat.getAttribute(minParcelNumberField));

        // setting zipcode
        if (CollecMgmt.isSimpleFeatureContainsAttribute(feat, minParcelCommunityField))
            builder.set(minParcelCommunityField, feat.getAttribute(minParcelCommunityField));
            // if looks like french parcel
        else if (CollecMgmt.isSimpleFeatureContainsAttribute(feat, "CODE_DEP"))
            builder.set(ParcelSchema.getMinParcelCommunityField(),
                    ((String) feat.getAttribute("CODE_DEP")).concat((String) feat.getAttribute("CODE_COM")));
        return builder;
    }

    /**
     * Create a builder out of a SimpleFeatureCollection's schema and add a mark field of type <i>int</i>. The mark name can be set with the method
     * {@link fr.ign.artiscales.pm.parcelFunction.MarkParcelAttributeFromPosition#setMarkFieldName(String)}.
     *
     * @param schema input schema
     * @return a SimpleFeatureBuilder relative to the schema + a marking field
     */
    public static SimpleFeatureBuilder addSplitField(SimpleFeatureType schema) {
        SimpleFeatureTypeBuilder sfTypeBuilder = new SimpleFeatureTypeBuilder();
        for (AttributeDescriptor attr : schema.getAttributeDescriptors())
            sfTypeBuilder.add(attr);
        sfTypeBuilder.add(MarkParcelAttributeFromPosition.markFieldName, int.class);
        sfTypeBuilder.setName(schema.getName());
        sfTypeBuilder.setCRS(schema.getCoordinateReferenceSystem());
        sfTypeBuilder.setDefaultGeometry(schema.getGeometryDescriptor().getLocalName());
        return new SimpleFeatureBuilder(sfTypeBuilder.buildFeatureType());
    }

    public static String getMinParcelNumberField() {
        return minParcelNumberField;
    }

    public static void setMinParcelNumberField(String minParcelNumberField) {
        ParcelSchema.minParcelNumberField = minParcelNumberField;
    }

    public static String getMinParcelSectionField() {
        return minParcelSectionField;
    }

    public static void setMinParcelSectionField(String minParcelSectionField) {
        ParcelSchema.minParcelSectionField = minParcelSectionField;
    }

    public static String getMinParcelCommunityField() {
        return minParcelCommunityField;
    }

    public static void setMinParcelCommunityField(String minParcelCommunityField) {
        ParcelSchema.minParcelCommunityField = minParcelCommunityField;
    }

    public static String getEpsg() {
        return epsg;
    }

    public static void setEpsg(String epsg) {
        ParcelSchema.epsg = epsg;
    }
}
