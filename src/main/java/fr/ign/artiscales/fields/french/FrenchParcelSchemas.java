package fr.ign.artiscales.fields.french;

import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.geotools.referencing.CRS;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Polygon;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.NoSuchAuthorityCodeException;

import fr.ign.artiscales.parcelFunction.MarkParcelAttributeFromPosition;

public class FrenchParcelSchemas {
	/////////////////////
	/////////////////////
	//// FrenchZoning Schemas : basic parcels schema used in the French IGN norm
	/////////////////////
	/////////////////////

	public static SimpleFeatureBuilder getSFBFrenchZoning() throws NoSuchAuthorityCodeException, FactoryException {
		SimpleFeatureTypeBuilder sfTypeBuilder = new SimpleFeatureTypeBuilder();
		sfTypeBuilder.setName("frenchZoning");
		sfTypeBuilder.setCRS(CRS.decode("EPSG:2154"));
		sfTypeBuilder.add("the_geom", Polygon.class);
		sfTypeBuilder.setDefaultGeometry("the_geom");
		sfTypeBuilder.add("LIBELLE", String.class);
		sfTypeBuilder.add("TYPEZONE", String.class);
		sfTypeBuilder.add("TYPEPLAN", String.class);
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
		sfTypeBuilder.setName("frenchParcel");
		sfTypeBuilder.setCRS(CRS.decode("EPSG:2154"));
		sfTypeBuilder.add("the_geom", Polygon.class);
		sfTypeBuilder.setDefaultGeometry("the_geom");
		sfTypeBuilder.add("NUMERO", String.class);
		sfTypeBuilder.add("FEUILLE", String.class);
		sfTypeBuilder.add("SECTION", String.class);
		sfTypeBuilder.add("CODE_DEP", String.class);
		sfTypeBuilder.add("NOM_COM", String.class);
		sfTypeBuilder.add("CODE_COM", String.class);
		sfTypeBuilder.add("COM_ABS", String.class);
		sfTypeBuilder.add("CODE_ARR", String.class);
		sfTypeBuilder.add("CODE", String.class);
		return new SimpleFeatureBuilder(sfTypeBuilder.buildFeatureType());
	}
	
	public static SimpleFeatureBuilder setSFBFrenchParcelWithFeat(SimpleFeature feat) {
		return  setSFBFrenchParcelWithFeat(feat, feat.getFeatureType()) ;
	}

	public static SimpleFeatureBuilder setSFBFrenchParcelWithFeat(SimpleFeature feat, SimpleFeatureType schema) {
		SimpleFeatureBuilder parcelBuilder = new SimpleFeatureBuilder(schema);
		parcelBuilder.set(schema.getGeometryDescriptor().getName().toString(), (Geometry) feat.getDefaultGeometry());
		parcelBuilder.set("NUMERO", feat.getAttribute("NUMERO"));
		parcelBuilder.set("FEUILLE", feat.getAttribute("FEUILLE"));
		parcelBuilder.set("SECTION", feat.getAttribute("SECTION"));
		parcelBuilder.set("CODE_DEP", feat.getAttribute("CODE_DEP"));
		parcelBuilder.set("NOM_COM", feat.getAttribute("NOM_COM"));
		parcelBuilder.set("CODE_COM",feat.getAttribute("CODE_COM"));
		parcelBuilder.set("COM_ABS", feat.getAttribute("COM_ABS"));
		parcelBuilder.set("CODE_ARR", feat.getAttribute("CODE_ARR"));
		parcelBuilder.set("CODE", feat.getAttribute("CODE"));
		return parcelBuilder;
	}

	public static SimpleFeatureBuilder getSFBFrenchParcelSplit() throws NoSuchAuthorityCodeException, FactoryException {
		SimpleFeatureTypeBuilder sfTypeBuilder = new SimpleFeatureTypeBuilder();
		sfTypeBuilder.setName("frenchParcelSplit");
		sfTypeBuilder.setCRS(CRS.decode("EPSG:2154"));
		sfTypeBuilder.add("the_geom", Polygon.class);
		sfTypeBuilder.setDefaultGeometry("the_geom");
		sfTypeBuilder.add("NUMERO", String.class);
		sfTypeBuilder.add("FEUILLE", String.class);
		sfTypeBuilder.add("SECTION", String.class);
		sfTypeBuilder.add("CODE_DEP", String.class);
		sfTypeBuilder.add("NOM_COM", String.class);
		sfTypeBuilder.add("CODE_COM", String.class);
		sfTypeBuilder.add("COM_ABS", String.class);
		sfTypeBuilder.add("CODE_ARR", String.class);
		sfTypeBuilder.add("CODE", String.class);
		sfTypeBuilder.add(MarkParcelAttributeFromPosition.getMarkFieldName(), Integer.class);

		return new SimpleFeatureBuilder(sfTypeBuilder.buildFeatureType());
	}

	public static SimpleFeatureBuilder setSFBFrenchParcelSplitWithFeat(SimpleFeature feat, SimpleFeatureType schema, int split) {
		return fillSFBFrenchParcelSplitWithFeat(feat, new SimpleFeatureBuilder(schema), schema.getGeometryDescriptor().getName().toString(), split);
	}

	public static SimpleFeatureBuilder fillSFBFrenchParcelSplitWithFeat(SimpleFeature feat, SimpleFeatureBuilder builder, String geomName,
			int split) {
		return fillSFBFrenchParcelSplitWithFeat(feat, builder, geomName, (Geometry) feat.getDefaultGeometry(), split);

	}

	public static SimpleFeatureBuilder fillSFBFrenchParcelSplitWithFeat(SimpleFeature feat, SimpleFeatureBuilder parcelBuilder, String geomName,
			Geometry geom, int split) {
		parcelBuilder.set(geomName, geom);
		parcelBuilder.set("NUMERO", feat.getAttribute("NUMERO"));
		parcelBuilder.set("FEUILLE", feat.getAttribute("FEUILLE"));
		parcelBuilder.set("SECTION", feat.getAttribute("SECTION"));
		parcelBuilder.set("CODE_DEP", feat.getAttribute("CODE_DEP"));
		parcelBuilder.set("NOM_COM", feat.getAttribute("NOM_COM"));
		parcelBuilder.set("CODE_COM",feat.getAttribute("CODE_COM"));
		parcelBuilder.set("COM_ABS", feat.getAttribute("COM_ABS"));
		parcelBuilder.set("CODE_ARR", feat.getAttribute("CODE_ARR"));
		parcelBuilder.set("CODE", feat.getAttribute("CODE"));
		parcelBuilder.set(MarkParcelAttributeFromPosition.getMarkFieldName(), split);
		return parcelBuilder;
	}
}