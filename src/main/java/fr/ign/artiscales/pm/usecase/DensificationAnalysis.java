package fr.ign.artiscales.pm.usecase;

import fr.ign.artiscales.pm.analysis.SingleParcelStat;
import fr.ign.artiscales.pm.parcelFunction.MarkParcelAttributeFromPosition;
import fr.ign.artiscales.tools.geoToolsFunctions.vectors.collec.CollecMgmt;
import fr.ign.artiscales.tools.geometryGeneration.CityGeneration;
import org.geotools.data.DataStore;
import org.geotools.data.simple.SimpleFeatureCollection;

import java.io.File;

public class DensificationAnalysis {
    public static void main(String[] args) throws Exception {
        DataStore ds = CollecMgmt.getDataStore(
                new File("src/main/resources/ParcelComparison/out/parcelDensificationOnly.gpkg"));
        SimpleFeatureCollection parcelDensifiedOnly = ds.getFeatureSource(ds.getTypeNames()[0]).getFeatures();
        DataStore sdsFin = CollecMgmt.getDataStore(
                new File("src/main/resources/ParcelComparison/out/parcelCuted-consolidationDivision-smallHouse-NC_.gpkg"));
        SimpleFeatureCollection parcelDensified = sdsFin.getFeatureSource(sdsFin.getTypeNames()[0]).getFeatures();

        DataStore dsSelec = CollecMgmt.getDataStore(
                new File("/home/ubuntu/workspace/ParcelManager/src/main/resources/ParcelComparison/out/evolvedParcel.gpkg"));
        SimpleFeatureCollection parcelSelec = dsSelec.getFeatureSource(dsSelec.getTypeNames()[0]).getFeatures();

        File buildingFile = new File("/home/ubuntu/workspace/ParcelManager/src/main/resources/ParcelComparison/building.gpkg");
        File roadFile = new File("/home/ubuntu/workspace/ParcelManager/src/main/resources/ParcelComparison/road.gpkg");
        MarkParcelAttributeFromPosition.setPostMark(true);
        SimpleFeatureCollection parcelsDensifCreated = MarkParcelAttributeFromPosition
                .getOnlyMarkedParcels(
                        MarkParcelAttributeFromPosition.markUnBuiltParcel(
                                MarkParcelAttributeFromPosition.markParcelsConnectedToRoad(
                                        MarkParcelAttributeFromPosition.markSimulatedParcel(parcelDensifiedOnly),
                                        CityGeneration.createUrbanBlock(parcelDensified), roadFile, CityGeneration.createBufferBorder(parcelDensified)),
                                buildingFile));
        CollecMgmt.exportSFC(SingleParcelStat.makeHausdorfDistanceMaps(parcelsDensifCreated, parcelSelec), new File("/tmp/HausdorfDensification"));
        DataStore dsRoad = CollecMgmt.getDataStore(
                new File("/home/ubuntu/workspace/ParcelManager/src/main/resources/ParcelComparison/out/evolvedParcel.gpkg"));
        SimpleFeatureCollection roads = dsRoad.getFeatureSource(dsRoad.getTypeNames()[0]).getFeatures();
        SingleParcelStat.writeStatSingleParcel(parcelsDensifCreated, roads, parcelSelec, new File("/tmp/stat"));
        ds.dispose();
        sdsFin.dispose();
        dsSelec.dispose();
        dsRoad.dispose();
    }
}
