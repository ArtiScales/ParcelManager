package fr.ign.artiscales.workflow;

import java.io.File;

import org.geotools.data.shapefile.ShapefileDataStore;
import org.geotools.data.simple.SimpleFeatureCollection;

import fr.ign.artiscales.analysis.SingleParcelStat;
import fr.ign.artiscales.parcelFunction.MarkParcelAttributeFromPosition;
import fr.ign.cogit.geoToolsFunctions.vectors.Collec;
import fr.ign.cogit.geoToolsFunctions.vectors.Geom;
import fr.ign.cogit.geometryGeneration.CityGeneration;

public class DensificationAnalysis {
	public static void main(String[] args) throws Exception {
		ShapefileDataStore sds = new ShapefileDataStore(
				new File("src/main/resources/ParcelComparison/out/parcelDensificationOnly.shp").toURI().toURL());
		SimpleFeatureCollection parcelDensifiedOnly = sds.getFeatureSource().getFeatures();
		ShapefileDataStore sdsFin = new ShapefileDataStore(
				new File("src/main/resources/ParcelComparison/out/parcelCuted-consolidationDivision-smallHouse-NC_.shp").toURI().toURL());
		SimpleFeatureCollection parcelDensified = sdsFin.getFeatureSource().getFeatures();

		ShapefileDataStore sdsSelec = new ShapefileDataStore(
				new File("/home/ubuntu/workspace/ParcelManager/src/main/resources/ParcelComparison/out/evolvedParcel.shp").toURI().toURL());
		SimpleFeatureCollection parcelSelec = sdsSelec.getFeatureSource().getFeatures();

		File buildingFile = new File("/home/ubuntu/workspace/ParcelManager/src/main/resources/ParcelComparison/building.shp");
		File roadFile = new File("/home/ubuntu/workspace/ParcelManager/src/main/resources/ParcelComparison/road.shp");
		MarkParcelAttributeFromPosition.setPostMark(true);
		SimpleFeatureCollection parcelsDensifCreated = MarkParcelAttributeFromPosition
				.getOnlyMarkedParcels(
						MarkParcelAttributeFromPosition.markUnBuiltParcel(
								MarkParcelAttributeFromPosition.markParcelsConnectedToRoad(
										MarkParcelAttributeFromPosition.markSimulatedParcel(parcelDensifiedOnly),
										CityGeneration.createUrbanIslet(parcelDensified), roadFile, Geom.createBufferBorder(parcelDensified)),
								buildingFile));
		Collec.exportSFC(SingleParcelStat.makeHausdorfDistanceMaps(parcelsDensifCreated, parcelSelec), new File("/tmp/HausdorfDensification"));
		ShapefileDataStore sdsRoad = new ShapefileDataStore(
				new File("/home/ubuntu/workspace/ParcelManager/src/main/resources/ParcelComparison/out/evolvedParcel.shp").toURI().toURL());
		SimpleFeatureCollection roads = sdsRoad.getFeatureSource().getFeatures();
		SingleParcelStat.writeStatSingleParcel(parcelsDensifCreated, roads, parcelSelec, new File("/tmp/stat"));
		sds.dispose();
		sdsFin.dispose();
		sdsSelec.dispose();
		sdsRoad.dispose();
	}
}
