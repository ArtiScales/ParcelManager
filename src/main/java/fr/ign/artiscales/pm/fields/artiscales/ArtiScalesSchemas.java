package fr.ign.artiscales.pm.fields.artiscales;

import fr.ign.artiscales.pm.fields.french.FrenchParcelFields;
import fr.ign.artiscales.tools.geoToolsFunctions.Schemas;
import fr.ign.artiscales.tools.geoToolsFunctions.vectors.collec.CollecMgmt;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.geotools.referencing.CRS;
import org.locationtech.jts.geom.Polygon;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.referencing.FactoryException;

/**
 * ArtiScale schemas and nomenclature of parcel implementation.
 *
 * @deprecated
 */
public class ArtiScalesSchemas {

    /**
     * Get Artiscales's parcel {@link SimpleFeatureBuilder}. Not updated with recent code change
     *
     * @return parcel builder
     */
    public static SimpleFeatureBuilder getSFBParcelAsAS() {
        SimpleFeatureTypeBuilder sfTypeBuilder = new SimpleFeatureTypeBuilder();
        sfTypeBuilder.setName("parcelAsAS");
        try {
            sfTypeBuilder.setCRS(CRS.decode(Schemas.getEpsg()));
        } catch (FactoryException e) {
            e.printStackTrace();
        }
        sfTypeBuilder.add(CollecMgmt.getDefaultGeomName(), Polygon.class);
        sfTypeBuilder.setDefaultGeometry(CollecMgmt.getDefaultGeomName());
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

    /**
     * Set builder with existing feature
     *
     * @param feat input feature
     * @return pre-filled builder
     */
    public static SimpleFeatureBuilder setSFBParcelAsASWithFeat(SimpleFeature feat) {
        SimpleFeatureBuilder builder = new SimpleFeatureBuilder(feat.getFeatureType());
        builder.set(feat.getFeatureType().getGeometryDescriptor().getName().toString(), feat.getDefaultGeometry());
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

    /**
     * Set builder with existing feature
     *
     * @param feat input feature
     * @return pre-filled builder
     */
    public static SimpleFeatureBuilder setSFBParcelAsASWithFrenchParcelFeat(SimpleFeature feat) {
        SimpleFeatureBuilder finalParcelBuilder = new SimpleFeatureBuilder(feat.getFeatureType());
        finalParcelBuilder.set(feat.getFeatureType().getGeometryDescriptor().getName().toString(), feat.getDefaultGeometry());
        finalParcelBuilder.set("CODE", FrenchParcelFields.makeFrenchParcelCode(feat));
        finalParcelBuilder.set("CODE_DEP", feat.getAttribute("CODE_DEP"));
        finalParcelBuilder.set("CODE_COM", feat.getAttribute("CODE_COM"));
        finalParcelBuilder.set("COM_ABS", feat.getAttribute("COM_ABS"));
        finalParcelBuilder.set("SECTION", feat.getAttribute("SECTION"));
        finalParcelBuilder.set("NUMERO", feat.getAttribute("NUMERO"));
        finalParcelBuilder.set("INSEE", FrenchParcelFields.makeDEPCOMCode(feat));
        finalParcelBuilder.set("eval", "0");
        finalParcelBuilder.set("DoWeSimul", "false");
        finalParcelBuilder.set("IsBuild", "false");
        finalParcelBuilder.set("U", "false");
        finalParcelBuilder.set("AU", "false");
        finalParcelBuilder.set("NC", "false");
        return finalParcelBuilder;
    }

    /**
     * Set builder with existing feature
     *
     * @param feat input feature
     * @return pre-filled builder
     */
    public static SimpleFeatureBuilder setSFBParcelAsASSplitWithFeat(SimpleFeature feat) {
        SimpleFeatureBuilder builder = new SimpleFeatureBuilder(feat.getFeatureType());
        builder.set(feat.getFeatureType().getGeometryDescriptor().getName().toString(), feat.getDefaultGeometry());
        builder.set("CODE", feat.getAttribute("CODE"));
        builder.set("CODE_DEP", feat.getAttribute("CODE_DEP"));
        builder.set("CODE_COM", feat.getAttribute("CODE_COM"));
        builder.set("COM_ABS", feat.getAttribute("COM_ABS"));
        builder.set("SECTION", feat.getAttribute("SECTION"));
        builder.set("NUMERO", feat.getAttribute("NUMERO"));
        builder.set("INSEE", feat.getAttribute("INSEE"));
        builder.set("eval", feat.getAttribute("eval"));
        builder.set("DoWeSimul", feat.getAttribute("DoWeSimul"));
        builder.set("SPLIT", feat.getAttribute("SPLIT"));
        builder.set("IsBuild", feat.getAttribute("IsBuild"));
        builder.set("U", feat.getAttribute("U"));
        builder.set("AU", feat.getAttribute("AU"));
        builder.set("NC", feat.getAttribute("NC"));
        return builder;
    }

    /**
     * parcels schema used in ArtiScales for marking which parcel is cut (or merged) on parcel reshaping process
     *
     * @return parcel schema
     */
    public static SimpleFeatureBuilder getSFBParcelAsASSplit() {
        SimpleFeatureTypeBuilder sfTypeBuilder = new SimpleFeatureTypeBuilder();
        sfTypeBuilder.setName("parcelAsASSplit");
        try {
            sfTypeBuilder.setCRS(CRS.decode(Schemas.getEpsg()));
        } catch (FactoryException e) {
            e.printStackTrace();
        }
        sfTypeBuilder.add(CollecMgmt.getDefaultGeomName(), Polygon.class);
        sfTypeBuilder.setDefaultGeometry(CollecMgmt.getDefaultGeomName());
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


}
