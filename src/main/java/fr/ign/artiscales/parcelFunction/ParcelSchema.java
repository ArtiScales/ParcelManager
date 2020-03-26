package fr.ign.artiscales.parcelFunction;

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
import org.opengis.referencing.crs.CoordinateReferenceSystem;

import fr.ign.cogit.geoToolsFunctions.Attribute;
import fr.ign.cogit.geoToolsFunctions.vectors.Collec;

public class ParcelSchema {

	static String minParcelNumberField = "NUMERO";
	static String minParcelSectionField = "SECTION";
	static String minParcelCommunityField = "DEPCOM";
	
	/////////////////////
	/////////////////////
	////Minimal Parcel Schema : minimal parcel schema for a Parcel Manager Processing
	/////////////////////
	/////////////////////
	
	public static SimpleFeatureBuilder getSFBMinParcel() throws NoSuchAuthorityCodeException, FactoryException {
		SimpleFeatureTypeBuilder sfTypeBuilder = new SimpleFeatureTypeBuilder();
		CoordinateReferenceSystem sourceCRS = CRS.decode("EPSG:2154");
		sfTypeBuilder.setName("testType");
		sfTypeBuilder.setCRS(sourceCRS);
		sfTypeBuilder.add("the_geom", Polygon.class);
		sfTypeBuilder.setDefaultGeometry("the_geom");
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

		//setting zipcode
		if (Collec.isSimpleFeatureContainsAttribute(feat,minParcelCommunityField)) {
			builder.set(minParcelCommunityField, feat.getAttribute(minParcelCommunityField));
		}
		//if looks like french parcel
		else if (Collec.isSimpleFeatureContainsAttribute(feat, "CODE_DEP")) {
			builder.set(ParcelSchema.getMinParcelCommunityFiled(), ((String) feat.getAttribute("CODE_DEP")).concat((String) feat.getAttribute("CODE_COM")));
		}
		return builder;
	}
	
	public static SimpleFeatureBuilder getSFBMinParcelSplit() throws NoSuchAuthorityCodeException, FactoryException {
		SimpleFeatureTypeBuilder sfTypeBuilder = new SimpleFeatureTypeBuilder();
		CoordinateReferenceSystem sourceCRS = CRS.decode("EPSG:2154");
		sfTypeBuilder.setName("testType");
		sfTypeBuilder.setCRS(sourceCRS);
		sfTypeBuilder.add("the_geom", Polygon.class);
		sfTypeBuilder.setDefaultGeometry("the_geom");
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

	public static SimpleFeatureBuilder setSFBMinParcelSplitWithFeat(SimpleFeature feat, SimpleFeatureBuilder builder, SimpleFeatureType schema, int isSplit) {
		builder.set(schema.getGeometryDescriptor().getName().toString(), (Geometry) feat.getDefaultGeometry());
		builder.set(MarkParcelAttributeFromPosition.getMarkFieldName(), isSplit);
		builder.set(minParcelSectionField, feat.getAttribute(minParcelSectionField));
		builder.set(minParcelNumberField, feat.getAttribute(minParcelNumberField));

		//setting zipcode
		if (Collec.isSimpleFeatureContainsAttribute(feat,minParcelCommunityField)) {
			builder.set(minParcelCommunityField, feat.getAttribute(minParcelCommunityField));
		}
		//if looks like french parcel
		else if (Collec.isSimpleFeatureContainsAttribute(feat, "CODE_DEP")) {
			builder.set(ParcelSchema.getMinParcelCommunityFiled(), ((String) feat.getAttribute("CODE_DEP")).concat((String) feat.getAttribute("CODE_COM")));
		}
		return builder;
	}
	
	/////////////////////
	/////////////////////
	//// FrenchZoning Schemas : basic parcels schema used in the French IGN norm
	/////////////////////
	/////////////////////

	public static SimpleFeatureBuilder getSFBFrenchZoning() throws NoSuchAuthorityCodeException, FactoryException {
		SimpleFeatureTypeBuilder sfTypeBuilder = new SimpleFeatureTypeBuilder();
		CoordinateReferenceSystem sourceCRS = CRS.decode("EPSG:2154");
		sfTypeBuilder.setName("testType");
		sfTypeBuilder.setCRS(sourceCRS);
		sfTypeBuilder.add("the_geom", Polygon.class);
		sfTypeBuilder.setDefaultGeometry("the_geom");
		sfTypeBuilder.add("LIBELLE", String.class);
		sfTypeBuilder.add("TYPEZONE", String.class);
		sfTypeBuilder.add("TYPLEPLAN", String.class);
		sfTypeBuilder.add("INSEE", String.class);
		return new SimpleFeatureBuilder(sfTypeBuilder.buildFeatureType());
	}
	
	/////////////////////
	/////////////////////
	//// FrenchParcel Schemas : basic parcels schema used in the french IGN norm
	/////////////////////
	/////////////////////

	public static SimpleFeatureBuilder getSFBFrenchParcel() throws NoSuchAuthorityCodeException, FactoryException {
		SimpleFeatureTypeBuilder sfTypeBuilder = new SimpleFeatureTypeBuilder();
		CoordinateReferenceSystem sourceCRS = CRS.decode("EPSG:2154");
		sfTypeBuilder.setName("testType");
		sfTypeBuilder.setCRS(sourceCRS);
		sfTypeBuilder.add("the_geom", Polygon.class);
		sfTypeBuilder.setDefaultGeometry("the_geom");
		sfTypeBuilder.add("CODE", String.class);
		sfTypeBuilder.add("CODE_DEP", String.class);
		sfTypeBuilder.add("CODE_COM", String.class);
		sfTypeBuilder.add("COM_ABS", String.class);
		sfTypeBuilder.add("SECTION", String.class);
		sfTypeBuilder.add("NUMERO", String.class);

		return new SimpleFeatureBuilder(sfTypeBuilder.buildFeatureType());
	}
	
	public static SimpleFeatureBuilder setSFBFrenchParcelWithFeat(SimpleFeature feat) {
		return  setSFBFrenchParcelWithFeat(feat, feat.getFeatureType()) ;
	}

	public static SimpleFeatureBuilder setSFBFrenchParcelWithFeat(SimpleFeature feat, SimpleFeatureType schema) {
		SimpleFeatureBuilder finalParcelBuilder = new SimpleFeatureBuilder(schema);
		finalParcelBuilder.set(schema.getGeometryDescriptor().getName().toString(), (Geometry) feat.getDefaultGeometry());
		finalParcelBuilder.set("CODE_DEP", feat.getAttribute("CODE_DEP"));
		finalParcelBuilder.set("CODE_COM", feat.getAttribute("CODE_COM"));
		finalParcelBuilder.set("COM_ABS", feat.getAttribute("COM_ABS"));
		finalParcelBuilder.set("SECTION", feat.getAttribute("SECTION"));
		finalParcelBuilder.set("NUMERO", feat.getAttribute("NUMERO"));
		return finalParcelBuilder;
	}

	public static SimpleFeatureBuilder getSFBFrenchParcelSplit() throws NoSuchAuthorityCodeException, FactoryException {
		SimpleFeatureTypeBuilder sfTypeBuilder = new SimpleFeatureTypeBuilder();
		CoordinateReferenceSystem sourceCRS = CRS.decode("EPSG:2154");
		sfTypeBuilder.setName("testType");
		sfTypeBuilder.setCRS(sourceCRS);
		sfTypeBuilder.add("the_geom", Polygon.class);
		sfTypeBuilder.setDefaultGeometry("the_geom");
		sfTypeBuilder.add("CODE", String.class);
		sfTypeBuilder.add("CODE_DEP", String.class);
		sfTypeBuilder.add("CODE_COM", String.class);
		sfTypeBuilder.add("COM_ABS", String.class);
		sfTypeBuilder.add("SECTION", String.class);
		sfTypeBuilder.add("NUMERO", String.class);
		sfTypeBuilder.add(MarkParcelAttributeFromPosition.getMarkFieldName(), String.class);

		return new SimpleFeatureBuilder(sfTypeBuilder.buildFeatureType());
	}

	public static SimpleFeatureBuilder setSFBFrenchParcelSplitWithFeat(SimpleFeature feat, SimpleFeatureType schema, int split) {
		return fillSFBFrenchParcelSplitWithFeat(feat, new SimpleFeatureBuilder(schema), schema.getGeometryDescriptor().getName().toString(), split);
	}

	public static SimpleFeatureBuilder fillSFBFrenchParcelSplitWithFeat(SimpleFeature feat, SimpleFeatureBuilder builder, String geomName,
			int split) {
		return fillSFBFrenchParcelSplitWithFeat(feat, builder, geomName, (Geometry) feat.getDefaultGeometry(), split);

	}

	public static SimpleFeatureBuilder fillSFBFrenchParcelSplitWithFeat(SimpleFeature feat, SimpleFeatureBuilder builder, String geomName,
			Geometry geom, int split) {
		builder.set(geomName, geom);
		builder.set("CODE_DEP", feat.getAttribute("CODE_DEP"));
		builder.set("CODE_COM", feat.getAttribute("CODE_COM"));
		builder.set("COM_ABS", feat.getAttribute("COM_ABS"));
		builder.set("SECTION", feat.getAttribute("SECTION"));
		builder.set("NUMERO", feat.getAttribute("NUMERO"));
		builder.set(MarkParcelAttributeFromPosition.getMarkFieldName(), split);

		return builder;
	}

	/////////////////////
	/////////////////////
	//// AsAS Schemas : basic parcels schema used in ArtiScales
	/////////////////////
	/////////////////////
	public static SimpleFeatureBuilder getSFBParcelAsAS() throws NoSuchAuthorityCodeException, FactoryException {
		SimpleFeatureTypeBuilder sfTypeBuilder = new SimpleFeatureTypeBuilder();
		CoordinateReferenceSystem sourceCRS = CRS.decode("EPSG:2154");
		sfTypeBuilder.setName("testType");
		sfTypeBuilder.setCRS(sourceCRS);
		sfTypeBuilder.add("the_geom", Polygon.class);
		sfTypeBuilder.setDefaultGeometry("the_geom");
		sfTypeBuilder.add("CODE", String.class);
		sfTypeBuilder.add("CODE_DEP", String.class);
		sfTypeBuilder.add("CODE_COM", String.class);
		sfTypeBuilder.add("COM_ABS", String.class);
		sfTypeBuilder.add("SECTION", String.class);
		sfTypeBuilder.add("NUMERO", String.class);
		sfTypeBuilder.add("INSEE", String.class);
		sfTypeBuilder.add("eval", String.class);
		sfTypeBuilder.add("DoWeSimul", String.class);
		sfTypeBuilder.add("IsBuild", Boolean.class);
		sfTypeBuilder.add("U", Boolean.class);
		sfTypeBuilder.add("AU", Boolean.class);
		sfTypeBuilder.add("NC", Boolean.class);

		return new SimpleFeatureBuilder(sfTypeBuilder.buildFeatureType());
	}

	public static SimpleFeatureBuilder setSFBParcelAsASWithFeat(SimpleFeature feat) {
		return setSFBParcelAsASWithFeat(feat, feat.getFeatureType());
	}

	public static SimpleFeatureBuilder setSFBParcelAsASWithFeat(SimpleFeature feat, SimpleFeatureType schema) {
		SimpleFeatureBuilder finalParcelBuilder = new SimpleFeatureBuilder(schema);
		return finalParcelBuilder = fillSFBParcelAsASWithFeat(feat, finalParcelBuilder, schema);
	}

	public static SimpleFeatureBuilder fillSFBParcelAsASWithFeat(SimpleFeature feat, SimpleFeatureBuilder builder, SimpleFeatureType schema) {
		builder.set(schema.getGeometryDescriptor().getName().toString(), (Geometry) feat.getDefaultGeometry());
		builder.set("CODE", feat.getAttribute("CODE"));
		builder.set("CODE_DEP", feat.getAttribute("CODE_DEP"));
		builder.set("CODE_COM", feat.getAttribute("CODE_COM"));
		builder.set("COM_ABS", feat.getAttribute("COM_ABS"));
		builder.set("SECTION", feat.getAttribute("SECTION"));
		builder.set("NUMERO", feat.getAttribute("NUMERO"));
		builder.set("INSEE", feat.getAttribute("INSEE"));
		builder.set("eval", feat.getAttribute("eval"));
		builder.set("DoWeSimul", feat.getAttribute("DoWeSimul"));
		builder.set("IsBuild", feat.getAttribute("IsBuild"));
		builder.set("U", feat.getAttribute("U"));
		builder.set("AU", feat.getAttribute("AU"));
		builder.set("NC", feat.getAttribute("NC"));
		return builder;
	}

	public static SimpleFeatureBuilder setSFBParcelAsASWithFrenchParcelFeat(SimpleFeature feat) {
		return setSFBParcelAsASWithFrenchParcelFeat(feat, feat.getFeatureType(), feat.getFeatureType().getGeometryDescriptor().getName().toString());
	}

	public static SimpleFeatureBuilder setSFBParcelAsASWithFrenchParcelFeat(SimpleFeature feat, SimpleFeatureType schema) {
		return setSFBParcelAsASWithFrenchParcelFeat(feat, schema, schema.getGeometryDescriptor().getName().toString());
	}

	public static SimpleFeatureBuilder setSFBParcelAsASWithFrenchParcelFeat(SimpleFeature feat, SimpleFeatureType schema, String geometryOutputName) {
		SimpleFeatureBuilder finalParcelBuilder = new SimpleFeatureBuilder(schema);
		finalParcelBuilder.set(geometryOutputName, (Geometry) feat.getDefaultGeometry());
		finalParcelBuilder.set("CODE", ParcelAttribute.makeFrenchParcelCode(feat));
		finalParcelBuilder.set("CODE_DEP", feat.getAttribute("CODE_DEP"));
		finalParcelBuilder.set("CODE_COM", feat.getAttribute("CODE_COM"));
		finalParcelBuilder.set("COM_ABS", feat.getAttribute("COM_ABS"));
		finalParcelBuilder.set("SECTION", feat.getAttribute("SECTION"));
		finalParcelBuilder.set("NUMERO", feat.getAttribute("NUMERO"));
		finalParcelBuilder.set("INSEE", Attribute.makeINSEECode(feat));
		finalParcelBuilder.set("eval", "0");
		finalParcelBuilder.set("DoWeSimul", "false");
		finalParcelBuilder.set("IsBuild", "false");
		finalParcelBuilder.set("U", "false");
		finalParcelBuilder.set("AU", "false");
		finalParcelBuilder.set("NC", "false");
		return finalParcelBuilder;
	}

	/////////////////////
	/////////////////////
	//// AsASSplit Schemas : parcels schema used in ArtiScales for marking which parcel is cut (or merged) on parcel reshaping process
	/////////////////////
	/////////////////////
	public static SimpleFeatureBuilder getSFBParcelAsASSplit() throws NoSuchAuthorityCodeException, FactoryException {
		SimpleFeatureTypeBuilder sfTypeBuilder = new SimpleFeatureTypeBuilder();
		CoordinateReferenceSystem sourceCRS = CRS.decode("EPSG:2154");
		sfTypeBuilder.setName("testType");
		sfTypeBuilder.setCRS(sourceCRS);
		sfTypeBuilder.add("the_geom", Polygon.class);
		sfTypeBuilder.setDefaultGeometry("the_geom");
		sfTypeBuilder.add("CODE", String.class);
		sfTypeBuilder.add("CODE_DEP", String.class);
		sfTypeBuilder.add("CODE_COM", String.class);
		sfTypeBuilder.add("COM_ABS", String.class);
		sfTypeBuilder.add("SECTION", String.class);
		sfTypeBuilder.add("NUMERO", String.class);
		sfTypeBuilder.add("INSEE", String.class);
		sfTypeBuilder.add("eval", String.class);
		sfTypeBuilder.add("DoWeSimul", String.class);
		sfTypeBuilder.add("SPLIT", Integer.class);
		sfTypeBuilder.add("IsBuild", Boolean.class);
		sfTypeBuilder.add("U", Boolean.class);
		sfTypeBuilder.add("AU", Boolean.class);
		sfTypeBuilder.add("NC", Boolean.class);

		return new SimpleFeatureBuilder(sfTypeBuilder.buildFeatureType());
	}

	public static SimpleFeatureBuilder setSFBParcelAsASSplitWithFeat(SimpleFeature feat, SimpleFeatureType schema) {
		SimpleFeatureBuilder finalParcelBuilder = new SimpleFeatureBuilder(schema);
		finalParcelBuilder = fillSFBParcelAsASSplitWithFeat(feat, finalParcelBuilder, schema);
		return finalParcelBuilder;
	}

	public static SimpleFeatureBuilder fillSFBParcelAsASSplitWithFeat(SimpleFeature feat, SimpleFeatureBuilder builder, SimpleFeatureType schema) {
		return fillSFBParcelAsASSplitWithFeat(feat, builder, schema.getGeometryDescriptor().getName().toString(), (int) feat.getAttribute("SPLIT"));
	}

	public static SimpleFeatureBuilder fillSFBParcelAsASSplitWithFeat(SimpleFeature feat, SimpleFeatureBuilder builder, String geomName, int split) {
		builder.set(geomName, (Geometry) feat.getDefaultGeometry());
		builder.set("CODE", feat.getAttribute("CODE"));
		builder.set("CODE_DEP", feat.getAttribute("CODE_DEP"));
		builder.set("CODE_COM", feat.getAttribute("CODE_COM"));
		builder.set("COM_ABS", feat.getAttribute("COM_ABS"));
		builder.set("SECTION", feat.getAttribute("SECTION"));
		builder.set("NUMERO", feat.getAttribute("NUMERO"));
		builder.set("INSEE", feat.getAttribute("INSEE"));
		builder.set("eval", feat.getAttribute("eval"));
		builder.set("DoWeSimul", feat.getAttribute("DoWeSimul"));
		builder.set("SPLIT", split);
		builder.set("IsBuild", feat.getAttribute("IsBuild"));
		builder.set("U", feat.getAttribute("U"));
		builder.set("AU", feat.getAttribute("AU"));
		builder.set("NC", feat.getAttribute("NC"));
		return builder;
	}

	public static SimpleFeatureBuilder getSFBSchemaWithMultiPolygon(SimpleFeatureType schema) {
		SimpleFeatureTypeBuilder sfTypeBuilder = new SimpleFeatureTypeBuilder();
		CoordinateReferenceSystem sourceCRS = schema.getCoordinateReferenceSystem();
		for (AttributeDescriptor attr : schema.getAttributeDescriptors()) {
			if (attr.getLocalName().equals("the_geom"))
				continue;
			sfTypeBuilder.add(attr);
		}
		sfTypeBuilder.setName("testType");
		sfTypeBuilder.setCRS(sourceCRS);
		sfTypeBuilder.add("the_geom", MultiPolygon.class);
		sfTypeBuilder.setDefaultGeometry("the_geom");
		return new SimpleFeatureBuilder(sfTypeBuilder.buildFeatureType());
	}
	
	public static SimpleFeatureBuilder setSFBSchemaWithMultiPolygon(SimpleFeature feat) {
		SimpleFeatureBuilder builder = getSFBSchemaWithMultiPolygon(feat.getFeatureType());
		for (AttributeDescriptor attr : feat.getFeatureType().getAttributeDescriptors()) {
//			if (attr.getLocalName().equals("the_geom"))
//				continue;
			builder.set(attr.getName(), feat.getAttribute(attr.getName()));
		}
		return builder;
	}
	
	/**
	 * Create a builder out of a SimpleFeatureCollection's schema and add a mark field of type <b>int</i>. The mark name can be set with the method {@link fr.ign.cogit.parcelFunction.MarkParcelAttributeFromPosition#setMarkFieldName(String)}.
	 * @param schema
	 * @return a SimpleFeatureBuilder felative to the schema + a marking field
	 */
	public static SimpleFeatureBuilder addSplitField(SimpleFeatureType schema) {
		SimpleFeatureTypeBuilder sfTypeBuilder = new SimpleFeatureTypeBuilder();
		for (AttributeDescriptor attr : schema.getAttributeDescriptors()) {
			sfTypeBuilder.add(attr);
		}
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

	public static String getMinParcelCommunityFiled() {
		return minParcelCommunityField;
	}

	public static void setMinParcelCommunityField(String minParcelCommunityField) {
		ParcelSchema.minParcelCommunityField = minParcelCommunityField;
	}
}
