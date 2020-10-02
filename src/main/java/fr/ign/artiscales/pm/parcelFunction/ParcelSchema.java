package fr.ign.artiscales.pm.parcelFunction;

import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.geotools.referencing.CRS;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Polygon;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.AttributeDescriptor;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.NoSuchAuthorityCodeException;

import fr.ign.artiscales.tools.geoToolsFunctions.vectors.Collec;

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

	public static SimpleFeatureBuilder getSFBMinParcel() throws NoSuchAuthorityCodeException, FactoryException {
		SimpleFeatureTypeBuilder sfTypeBuilder = new SimpleFeatureTypeBuilder();
		sfTypeBuilder.setName("minParcel");
		sfTypeBuilder.setCRS(CRS.decode(epsg));
		sfTypeBuilder.add(Collec.getDefaultGeomName(), Polygon.class);
		sfTypeBuilder.setDefaultGeometry(Collec.getDefaultGeomName());
		sfTypeBuilder.add(minParcelSectionField, String.class);
		sfTypeBuilder.add(minParcelNumberField, String.class);
		sfTypeBuilder.add(minParcelCommunityField, String.class);
		return new SimpleFeatureBuilder(sfTypeBuilder.buildFeatureType());
	}

	public static SimpleFeatureBuilder getSFBMinParcelMulti() throws NoSuchAuthorityCodeException, FactoryException {
		SimpleFeatureTypeBuilder sfTypeBuilder = new SimpleFeatureTypeBuilder();
		sfTypeBuilder.setName("minParcel");
		sfTypeBuilder.setCRS(CRS.decode(epsg));
		sfTypeBuilder.add(Collec.getDefaultGeomName(), MultiPolygon.class);
		sfTypeBuilder.setDefaultGeometry(Collec.getDefaultGeomName());
		sfTypeBuilder.add(minParcelSectionField, String.class);
		sfTypeBuilder.add(minParcelNumberField, String.class);
		sfTypeBuilder.add(minParcelCommunityField, String.class);

		return new SimpleFeatureBuilder(sfTypeBuilder.buildFeatureType());
	}

	public static SimpleFeatureBuilder setSFBMinParcelWithFeat(SimpleFeature feat, SimpleFeatureType schema) {
		SimpleFeatureBuilder finalParcelBuilder = new SimpleFeatureBuilder(schema);
		return finalParcelBuilder = setSFBMinParcelWithFeat(feat, finalParcelBuilder, schema);
	}

	public static SimpleFeatureBuilder setSFBMinParcelWithFeat(SimpleFeature feat, SimpleFeatureBuilder builder, SimpleFeatureType schema) {
		builder.set(schema.getGeometryDescriptor().getName().toString(), (Geometry) feat.getDefaultGeometry());
		builder.set(minParcelSectionField, feat.getAttribute(minParcelSectionField));
		builder.set(minParcelNumberField, feat.getAttribute(minParcelNumberField));

		// setting zipcode
		if (Collec.isSimpleFeatureContainsAttribute(feat, minParcelCommunityField))
			builder.set(minParcelCommunityField, feat.getAttribute(minParcelCommunityField));
		// if looks like French parcel
		else if (Collec.isSimpleFeatureContainsAttribute(feat, "CODE_DEP"))
			builder.set(ParcelSchema.getMinParcelCommunityField(),
					((String) feat.getAttribute("CODE_DEP")).concat((String) feat.getAttribute("CODE_COM")));
		return builder;
	}

	public static SimpleFeatureBuilder getSFBMinParcelSplit() throws NoSuchAuthorityCodeException, FactoryException {
		SimpleFeatureTypeBuilder sfTypeBuilder = new SimpleFeatureTypeBuilder();
		sfTypeBuilder.setName("minParcelSplit");
		sfTypeBuilder.setCRS(CRS.decode(epsg));
		sfTypeBuilder.add(Collec.getDefaultGeomName(), Polygon.class);
		sfTypeBuilder.setDefaultGeometry(Collec.getDefaultGeomName());
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
		SimpleFeatureBuilder finalParcelBuilder = new SimpleFeatureBuilder(schema);
		return finalParcelBuilder = setSFBMinParcelSplitWithFeat(feat, finalParcelBuilder, schema, isSplit);
	}

	public static SimpleFeatureBuilder setSFBMinParcelSplitWithFeat(SimpleFeature feat, SimpleFeatureBuilder builder, SimpleFeatureType schema,
			int isSplit) {
		builder.set(schema.getGeometryDescriptor().getName().toString(), (Geometry) feat.getDefaultGeometry());
		builder.set(MarkParcelAttributeFromPosition.getMarkFieldName(), isSplit);
		builder.set(minParcelSectionField, feat.getAttribute(minParcelSectionField));
		builder.set(minParcelNumberField, feat.getAttribute(minParcelNumberField));

		// setting zipcode
		if (Collec.isSimpleFeatureContainsAttribute(feat, minParcelCommunityField))
			builder.set(minParcelCommunityField, feat.getAttribute(minParcelCommunityField));
		// if looks like french parcel
		else if (Collec.isSimpleFeatureContainsAttribute(feat, "CODE_DEP"))
			builder.set(ParcelSchema.getMinParcelCommunityField(),
					((String) feat.getAttribute("CODE_DEP")).concat((String) feat.getAttribute("CODE_COM")));
		return builder;
	}

	/**
	 * Create a builder out of a SimpleFeatureCollection's schema and add a mark field of type <b>int</i>. The mark name can be set with the method
	 * {@link fr.ign.artiscales.pm.parcelFunction.MarkParcelAttributeFromPosition#setMarkFieldName(String)}.
	 * 
	 * @param schema
	 * @return a SimpleFeatureBuilder felative to the schema + a marking field
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

	public static void setMinParcelCommunityField(String minParcelCommunityField) {
		ParcelSchema.minParcelCommunityField = minParcelCommunityField;
	}

	public static String getMinParcelCommunityField() {
		return minParcelCommunityField;
	}

	public static String getEpsg() {
		return epsg;
	}

	public static void setEpsg(String epsg) {
		ParcelSchema.epsg = epsg;
	}
}
