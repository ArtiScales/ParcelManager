package fr.ign.artiscales.pm.parcelFunction;

import fr.ign.artiscales.tools.geoToolsFunctions.Schemas;
import fr.ign.artiscales.tools.geoToolsFunctions.vectors.collec.CollecMgmt;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.geotools.referencing.CRS;
import org.locationtech.jts.geom.Polygon;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.AttributeDescriptor;
import org.opengis.referencing.FactoryException;

/**
 * Methods to deal with parcel's schemas
 */
public class ParcelSchema {

    static String parcelNumberField = "NUMERO";
    static String parcelSectionField = "SECTION";
    static String parcelCommunityField = "DEPCOM";

    public static SimpleFeatureBuilder getSFBWithoutSplit(SimpleFeatureType schema) {
        if (!Schemas.isSchemaContainsAttribute(schema, MarkParcelAttributeFromPosition.getMarkFieldName()))
            return new SimpleFeatureBuilder(schema);
        SimpleFeatureTypeBuilder sfTypeBuilder = new SimpleFeatureTypeBuilder();
        for (AttributeDescriptor attr : schema.getAttributeDescriptors())
            if (!attr.getLocalName().equals(MarkParcelAttributeFromPosition.getMarkFieldName()))
                sfTypeBuilder.add(attr);
        sfTypeBuilder.setName(schema.getName());
        sfTypeBuilder.setCRS(schema.getCoordinateReferenceSystem());
        sfTypeBuilder.setDefaultGeometry(schema.getGeometryDescriptor().getLocalName());
        return new SimpleFeatureBuilder(sfTypeBuilder.buildFeatureType());
    }

    /////////////////////
    /////////////////////
    //// Minimal Parcel Schema : minimal parcel schema for a Parcel Manager Processing
    /////////////////////
    /////////////////////

    /**
     * Get minimal builder for a parcel
     *
     * @return the builder
     */
    public static SimpleFeatureBuilder getSFBMinParcel() {
        SimpleFeatureTypeBuilder sfTypeBuilder = new SimpleFeatureTypeBuilder();
        sfTypeBuilder.setName("minParcel");
        try {
            sfTypeBuilder.setCRS(CRS.decode(Schemas.getEpsg()));
        } catch (FactoryException e) {
            e.printStackTrace();
        }
        sfTypeBuilder.add(CollecMgmt.getDefaultGeomName(), Polygon.class);
        sfTypeBuilder.setDefaultGeometry(CollecMgmt.getDefaultGeomName());
        sfTypeBuilder.add(parcelSectionField, String.class);
        sfTypeBuilder.add(parcelNumberField, String.class);
        sfTypeBuilder.add(parcelCommunityField, String.class);
        return new SimpleFeatureBuilder(sfTypeBuilder.buildFeatureType());
    }

    public static SimpleFeatureBuilder setSFBMinParcelWithFeat(SimpleFeature feat, SimpleFeatureType schema) {
        return setSFBMinParcelWithFeat(feat, new SimpleFeatureBuilder(schema), schema);
    }

    public static SimpleFeatureBuilder setSFBMinParcelWithFeat(SimpleFeature feat, SimpleFeatureBuilder builder, SimpleFeatureType schema) {
        builder.set(schema.getGeometryDescriptor().getName().toString(), feat.getDefaultGeometry());
        builder.set(parcelSectionField, feat.getAttribute(parcelSectionField));
        builder.set(parcelNumberField, feat.getAttribute(parcelNumberField));
        // setting zipcode
        if (CollecMgmt.isSimpleFeatureContainsAttribute(feat, parcelCommunityField))
            builder.set(parcelCommunityField, feat.getAttribute(parcelCommunityField));
        else if (CollecMgmt.isSimpleFeatureContainsAttribute(feat, "CODE_DEP")) // if it looks like French parcel
            builder.set(ParcelSchema.getParcelCommunityField(),
                    ((String) feat.getAttribute("CODE_DEP")).concat((String) feat.getAttribute("CODE_COM")));
        return builder;
    }

    public static SimpleFeatureBuilder getSFBMinParcelSplit() {
        SimpleFeatureTypeBuilder sfTypeBuilder = new SimpleFeatureTypeBuilder();
        sfTypeBuilder.setName("minParcelSplit");
        try {
            sfTypeBuilder.setCRS(CRS.decode(Schemas.getEpsg()));
        } catch (FactoryException e) {
            e.printStackTrace();
        }
        sfTypeBuilder.add(CollecMgmt.getDefaultGeomName(), Polygon.class);
        sfTypeBuilder.setDefaultGeometry(CollecMgmt.getDefaultGeomName());
        sfTypeBuilder.add(parcelSectionField, String.class);
        sfTypeBuilder.add(parcelCommunityField, String.class);
        sfTypeBuilder.add(parcelNumberField, String.class);
        sfTypeBuilder.add(MarkParcelAttributeFromPosition.getMarkFieldName(), Integer.class);
        return new SimpleFeatureBuilder(sfTypeBuilder.buildFeatureType());
    }

    public static SimpleFeatureBuilder setSFBMinParcelSplitWithFeat(SimpleFeature feat, SimpleFeatureBuilder builder, SimpleFeatureType schema, int isSplit) {
        builder.set(schema.getGeometryDescriptor().getName().toString(), feat.getDefaultGeometry());
        builder.set(MarkParcelAttributeFromPosition.getMarkFieldName(), isSplit);
        builder.set(parcelSectionField, feat.getAttribute(parcelSectionField));
        builder.set(parcelNumberField, feat.getAttribute(parcelNumberField));

        if (CollecMgmt.isSimpleFeatureContainsAttribute(feat, parcelCommunityField)) // setting zipcode
            builder.set(getParcelCommunityField(), feat.getAttribute(parcelCommunityField));
        else if (CollecMgmt.isSimpleFeatureContainsAttribute(feat, "CODE_DEP")) // if it looks like french parcel
            builder.set(getParcelCommunityField(), ((String) feat.getAttribute("CODE_DEP")).concat((String) feat.getAttribute("CODE_COM")));
        return builder;
    }

    /**
     * Create a builder out of a SimpleFeatureCollection's schema and add a mark field of type <i>int</i>.
     *
     * @param schema input schema
     * @return a SimpleFeatureBuilder relative to the schema + a marking field
     */
    public static SimpleFeatureBuilder addMarkField(SimpleFeatureType schema) {
        return Schemas.addFieldToSFB(schema, MarkParcelAttributeFromPosition.getMarkFieldName(), int.class);
    }

    /**
     * Create a builder out of a SimpleFeatureCollection's schema and add a <i>SIMULATED</i> field of type <i>int</i>.
     *
     * @param schema input schema
     * @return a SimpleFeatureBuilder relative to the schema + a marking field
     */
    public static SimpleFeatureBuilder addSimulatedField(SimpleFeatureType schema) {
        return Schemas.addFieldToSFB(schema, "SIMULATED", int.class);

    }

    public static String getParcelNumberField() {
        return parcelNumberField;
    }

    public static void setParcelNumberField(String parcelNumberField) {
        ParcelSchema.parcelNumberField = parcelNumberField;
    }

    public static String getParcelSectionField() {
        return parcelSectionField;
    }

    public static void setParcelSectionField(String parcelSectionField) {
        ParcelSchema.parcelSectionField = parcelSectionField;
    }

    public static String getParcelCommunityField() {
        return parcelCommunityField;
    }

    public static void setParcelCommunityField(String parcelCommunityField) {
        ParcelSchema.parcelCommunityField = parcelCommunityField;
    }

    /**
     * Parcel ID is composed of its {@link #parcelCommunityField}, {@link #parcelSectionField} and {@link #parcelNumberField}, separated by '_' character.
     *
     * @param feat parcel feature with minimal values
     * @return new parcel's id.
     */
    public static String getParcelID(SimpleFeature feat) {
        return String.valueOf(feat.getAttribute(parcelCommunityField)) + '_' + feat.getAttribute(parcelSectionField) + '_' + feat.getAttribute(parcelNumberField);
    }
}
