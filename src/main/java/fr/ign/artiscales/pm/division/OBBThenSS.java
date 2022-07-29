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

/**
 * Apply SS division on OBB blocks.
 */
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

    /**
     * Apply SS on OBB blocks from
     *
     * @param sfcIn    List of marked parcels to be divided.
     * @param roadFile List of road. Must have required attributes for {@link StraightSkeletonDivision}.
     * @param profile  Description of the urban fabric profile planed to be simulated on this zone.
     * @return Collection of divided parcels
     * @throws IOException reading road file
     */
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

    /**
     * @param feat    parcel to divide
     * @param roads   Collection of road. Must have required attributes for {@link StraightSkeletonDivision}.
     * @param profile Description of the urban fabric profile planed to be simulated on this zone.
     * @param block   SimpleFeatureCollection containing the morphological block.
     * @return Collection of divided parcels
     */
    public static SimpleFeatureCollection applyOBBThenSS(SimpleFeature feat, SimpleFeatureCollection roads, ProfileUrbanFabric profile, List<LineString> block) {
        DefaultFeatureCollection result = new DefaultFeatureCollection();
//        if (((Geometry) feat.getDefaultGeometry()).getArea() > profile.getMaximalArea()) {
        SimpleFeatureCollection obbSplit = OBBDivision.splitParcel(feat, roads, profile.getMaximalArea() * profile.getApproxNumberParcelPerBlock(), profile.getMaxWidth(), profile.getHarmonyCoeff(),profile.getIrregularityCoeff(),
                block, Math.max(profile.getStreetWidth() - 2 * profile.getLaneWidth(), 1), 0, Math.max(profile.getStreetWidth() - 2 * profile.getLaneWidth(), 1), true, 0);
        if (isDEBUG())
            try {
                CollecMgmt.exportSFC(obbSplit, new File("/tmp/obb" + feat.getFeatureType().getTypeName() + Math.random()));
            } catch (IOException e) {
                e.printStackTrace();
            }
        MarkParcelAttributeFromPosition.setMarkFieldName("SIMULATED");
        StraightSkeletonDivision.setGeneratePeripheralRoad(true);
        try (SimpleFeatureIterator it = obbSplit.features()) {
            while (it.hasNext())
                result.addAll(StraightSkeletonDivision.runTopologicalStraightSkeletonParcelDecomposition(it.next(), roads, "NOM_VOIE_G", "IMPORTANCE", 0,
                        profile.getMaxDistanceForNearestRoad(), profile.getMinimalArea(), 12, profile.getMaxWidth(),
                        (profile.getIrregularityCoeff() == 0) ? 0.1 : profile.getIrregularityCoeff(), profile.getLaneWidth(), "finalState"));
        }
        MarkParcelAttributeFromPosition.setMarkFieldName("SPLIT");
        return result;
    }
}
