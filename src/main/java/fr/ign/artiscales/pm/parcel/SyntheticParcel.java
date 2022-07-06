package fr.ign.artiscales.pm.parcel;

import fr.ign.artiscales.tools.geoToolsFunctions.Attribute;
import fr.ign.artiscales.tools.geoToolsFunctions.vectors.Geom;
import fr.ign.artiscales.tools.geoToolsFunctions.vectors.collec.CollecMgmt;
import org.geotools.feature.DefaultFeatureCollection;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.geotools.referencing.CRS;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Polygon;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.referencing.FactoryException;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

public class SyntheticParcel {

    public Geometry geom;
    public String id;
    public double area, distanceToCenter;
    public long nbNeighborhood;
    public int ownerID, regionID;
    public List<String> lIdNeighborhood;

    public SyntheticParcel(Geometry geom, double area, double distanceToCenter, long nbNeighborhood, int ownerID, int regionID) {
        this.id = "Parcel" + Attribute.makeUniqueId();
        this.geom = geom;
        this.area = area;
        this.distanceToCenter = distanceToCenter;
        this.nbNeighborhood = nbNeighborhood;
        this.ownerID = ownerID;
        this.regionID = regionID;
    }

    public static List<Double> sumOwnerOwnedArea(List<SyntheticParcel> lSP) {
        HashMap<Integer, Double> ow = new HashMap<>();
        for (SyntheticParcel sp : lSP)
            if (ow.containsKey(sp.ownerID))
                ow.put(sp.ownerID, sp.area + ow.get(sp.ownerID));
            else
                ow.put(sp.ownerID, sp.area);
        return new ArrayList<>(ow.values());
    }

    public static void exportToGPKG(List<SyntheticParcel> lSP, File outFile) throws IOException {
        DefaultFeatureCollection result = new DefaultFeatureCollection();
        SimpleFeatureBuilder sfb = getSFB();
        for (SyntheticParcel sp : lSP) {
            sfb.set(CollecMgmt.getDefaultGeomName(), sp.geom);
            sfb.set("nbNeighborhood", sp.nbNeighborhood);
            sfb.set("idParcelNeighborhood", String.join("---", sp.lIdNeighborhood));
            sfb.set("area", sp.area);
            sfb.set("distanceToCenter", sp.distanceToCenter);
            sfb.set("parcelID", sp.id);
            sfb.set("ownerID", sp.ownerID);
            sfb.set("regionID", sp.regionID);
            result.add(sfb.buildFeature(Attribute.makeUniqueId()));
        }
        CollecMgmt.exportSFC(result, outFile);
    }

    private static SimpleFeatureBuilder getSFB() {
        SimpleFeatureTypeBuilder sfTypeBuilder = new SimpleFeatureTypeBuilder();
        try {
            sfTypeBuilder.setCRS(CRS.decode("EPSG:2154"));
        } catch (FactoryException e) {
            e.printStackTrace();
        }
        sfTypeBuilder.setName("SyntheticParcel");
        sfTypeBuilder.add(CollecMgmt.getDefaultGeomName(), Polygon.class);
        sfTypeBuilder.add("nbNeighborhood", Long.class);
        sfTypeBuilder.add("idParcelNeighborhood", String.class);
        sfTypeBuilder.add("area", Double.class);
        sfTypeBuilder.add("distanceToCenter", Double.class);
        sfTypeBuilder.add("parcelID", String.class);
        sfTypeBuilder.add("ownerID", Integer.class);
        sfTypeBuilder.add("regionID", Integer.class);
        sfTypeBuilder.setDefaultGeometry(CollecMgmt.getDefaultGeomName());
        SimpleFeatureType featureType = sfTypeBuilder.buildFeatureType();
        return new SimpleFeatureBuilder(featureType);
    }

    public void setIdNeighborhood(List<SyntheticParcel> lSP) {
        lIdNeighborhood = lSP.stream().filter(g -> Geom.safeIntersect(g.geom.buffer(1), geom)).filter(g -> !g.geom.equals(geom)).map(sp -> sp.id).collect(Collectors.toList());
    }
}

