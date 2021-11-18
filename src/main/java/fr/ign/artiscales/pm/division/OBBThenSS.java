package fr.ign.artiscales.pm.division;

import fr.ign.artiscales.pm.parcelFunction.MarkParcelAttributeFromPosition;
import fr.ign.artiscales.tools.geoToolsFunctions.vectors.collec.CollecMgmt;
import fr.ign.artiscales.tools.geoToolsFunctions.vectors.collec.CollecTransform;
import fr.ign.artiscales.tools.geometryGeneration.CityGeneration;
import fr.ign.artiscales.tools.parameter.ProfileUrbanFabric;
import org.apache.commons.math3.random.MersenneTwister;
import org.geotools.data.DataStore;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.feature.DefaultFeatureCollection;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import org.opengis.feature.simple.SimpleFeature;

import java.io.File;
import java.io.IOException;
import java.util.List;

import static fr.ign.artiscales.pm.workflow.Workflow.PROCESS;

public class OBBThenSS extends Division {

//    public static void main(String[] args) throws IOException {
//        DataStore dsP = CollecMgmt.getDataStore(new File("src/main/resources/TestScenario/InputData/parcel.gpkg"));
//        DataStore dsR = CollecMgmt.getDataStore(new File("src/main/resources/TestScenario/InputData/road.gpkg"));
//        SimpleFeatureCollection markedZone = MarkParcelAttributeFromPosition.markParcelsSup(dsP.getFeatureSource(dsP.getTypeNames()[0]).getFeatures(), 227724);
//
//        CollecMgmt.exportSFC(applyOBBThenSS(markedZone, dsR.getFeatureSource(dsR.getTypeNames()[0]).getFeatures(), ProfileUrbanFabric.convertJSONtoProfile(new File("/home/mc/workspace/parcelmanager/src/main/resources/TestScenario/profileUrbanFabric/mediumHouse.json")), 25), new File("/tmp/s"));
//        dsR.dispose();
//        dsP.dispose();
//    }

    public static SimpleFeatureCollection applyOBBThenSS(SimpleFeatureCollection sfcIn, File roadFile, ProfileUrbanFabric profile) throws IOException {
        DefaultFeatureCollection result = new DefaultFeatureCollection();
        List<LineString> block = CollecTransform.fromPolygonSFCtoListRingLines(CityGeneration.createUrbanBlock(sfcIn));
        DataStore dsRoad = CollecMgmt.getDataStore(roadFile);
        try (SimpleFeatureIterator it = sfcIn.features()) {
            while (it.hasNext()) {
                SimpleFeature f = it.next();
                result.addAll(applyOBBThenSS(f, CollecTransform.selectIntersection(dsRoad.getFeatureSource(dsRoad.getTypeNames()[0]).getFeatures(), ((Geometry) f.getDefaultGeometry()).buffer(profile.getMaxDistanceForNearestRoad())), profile, block));
            }
        }
        dsRoad.dispose();
        return result;
    }

    public static SimpleFeatureCollection applyOBBThenSS(SimpleFeature feat, SimpleFeatureCollection roads, ProfileUrbanFabric profile, List<LineString> block) throws IOException {
        DefaultFeatureCollection result = new DefaultFeatureCollection();
//        if (((Geometry) feat.getDefaultGeometry()).getArea() > profile.getMaximalArea()) {
        SimpleFeatureCollection obbSplit = OBBDivision.splitParcel(feat, roads, profile.getMaximalArea() * profile.getApproxNumberParcelPerBlock(), profile.getMaxWidth(), 0.2, 0.1
                , block, Math.max(profile.getLaneWidth() - 2 * profile.getStreetWidth(), 1), 0, Math.max(profile.getLaneWidth() - 2 * profile.getStreetWidth(), 1), true, 0);
        if (isDEBUG())
            CollecMgmt.exportSFC(obbSplit, new File("/tmp/obb" + feat.getFeatureType().getTypeName() + Math.random()));
        MarkParcelAttributeFromPosition.setMarkFieldName("SIMULATED");
        StraightSkeletonDivision.setGeneratePeripheralRoad(true);
        try (SimpleFeatureIterator it = obbSplit.features()) {
            while (it.hasNext())
                result.addAll(StraightSkeletonDivision.runTopologicalStraightSkeletonParcelDecomposition(it.next(), roads, "NOM_VOIE_G", "IMPORTANCE", PROCESS.equals("SSoffset") ? profile.getMaxDepth() : 0,
                        profile.getMaxDistanceForNearestRoad(), profile.getMinimalArea(), profile.getMinimalWidthContactRoad(), profile.getMaxWidth(),
                        (profile.getNoise() == 0) ? 0.1 : profile.getNoise(), new MersenneTwister(1), profile.getLaneWidth(), "finalState"));
        }
        MarkParcelAttributeFromPosition.setMarkFieldName("SPLIT");
//        } else {
//            SimpleFeatureBuilder sfb =ParcelSchema.addField(feat.getFeatureType(), "SIMULATED");
//            Schemas.setFieldsToSFB(sfb, feat);
//            result.add(sfb.buildFeature(Attribute.makeUniqueId()));
//        }
        return result;
    }
}
