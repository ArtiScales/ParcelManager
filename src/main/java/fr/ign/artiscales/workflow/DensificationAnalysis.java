package fr.ign.artiscales.workflow;

import java.io.File;

import org.geotools.data.DataStore;
import org.geotools.data.simple.SimpleFeatureCollection;

import fr.ign.artiscales.analysis.SingleParcelStat;
import fr.ign.artiscales.parcelFunction.MarkParcelAttributeFromPosition;
import fr.ign.cogit.geoToolsFunctions.vectors.Collec;
import fr.ign.cogit.geoToolsFunctions.vectors.Geom;
import fr.ign.cogit.geoToolsFunctions.vectors.Geopackages;
import fr.ign.cogit.geometryGeneration.CityGeneration;

public class DensificationAnalysis {
	public static void main(String[] args) throws Exception {
		DataStore ds = Geopackages.getDataStore(
				new File("src/main/resources/ParcelComparison/out/parcelDensificationOnly.gpkg"));
		SimpleFeatureCollection parcelDensifiedOnly = ds.getFeatureSource(ds.getTypeNames()[0]).getFeatures();
		DataStore sdsFin = Geopackages.getDataStore(
				new File("src/main/resources/ParcelComparison/out/parcelCuted-consolidationDivision-smallHouse-NC_.gpkg"));
		SimpleFeatureCollection parcelDensified = sdsFin.getFeatureSource(sdsFin.getTypeNames()[0]).getFeatures();

		DataStore dsSelec = Geopackages.getDataStore(
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
										CityGeneration.createUrbanIslet(parcelDensified), roadFile, Geom.createBufferBorder(parcelDensified)),
								buildingFile));
		Collec.exportSFC(SingleParcelStat.makeHausdorfDistanceMaps(parcelsDensifCreated, parcelSelec), new File("/tmp/HausdorfDensification"));
		DataStore dsRoad = Geopackages.getDataStore(
				new File("/home/ubuntu/workspace/ParcelManager/src/main/resources/ParcelComparison/out/evolvedParcel.gpkg"));
		SimpleFeatureCollection roads = dsRoad.getFeatureSource(dsRoad.getTypeNames()[0]).getFeatures();
		SingleParcelStat.writeStatSingleParcel(parcelsDensifCreated, roads, parcelSelec, new File("/tmp/stat"));
		ds.dispose();
		sdsFin.dispose();
		dsSelec.dispose();
		dsRoad.dispose();
	}
}
