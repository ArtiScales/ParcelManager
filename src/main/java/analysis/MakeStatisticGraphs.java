package analysis;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.geotools.data.shapefile.ShapefileDataStore;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.knowm.xchart.BitmapEncoder;
import org.knowm.xchart.BitmapEncoder.BitmapFormat;
import org.knowm.xchart.CategoryChart;
import org.knowm.xchart.CategoryChartBuilder;
import org.knowm.xchart.Histogram;
import org.locationtech.jts.geom.Geometry;
import org.opengis.feature.simple.SimpleFeature;

import fields.GeneralFileds;

public class MakeStatisticGraphs {

//	/**
//	 * Class to automate the creation of stat graphs
//	 */
//	public static void main(String[] args) throws IOException {
//		makeAreaGraph(new File("/tmp/parcelCuted-consolid.shp"), new File("/tmp/"));
//	}
	
	/**
	 * Automate the generation of graphs about area of fresh parcel cuts
	 * 
	 * @param args
	 * @throws IOException
	 */	
	public static void makeAreaGraph(File freshParcelCut, File folderOut) throws IOException {
		ShapefileDataStore sds = new ShapefileDataStore(freshParcelCut.toURI().toURL());
		SimpleFeatureCollection sfc = GeneralFileds.getParcelWithSimulatedFileds(sds.getFeatureSource().getFeatures());
		AreaGraph areaGraph = sortValuesAndCategorize(sfc, "area");
		makeGraphHisto(areaGraph, folderOut, "Distribution de la surface des parcelles subdivisées", "Surface d'une parcelle (m2)",
				"Nombre de parcelles", 10);
		sds.dispose();
	}

	/**
	 * Process to sort which parcels have been cuted, and get the bounds of the distribution
	 * @warning Developed for French Parcels - section is always a two character 
	 * @param parcelOut the parcel to sort and plot
	 * @param nameDistrib the name of the distribution
	 * @param filed which can be used to filter the features of the parcel collection. Can be null
	 * @return a Graph object  
	 * @throws IOException
	 */
	public static AreaGraph sortValuesAndCategorize(SimpleFeatureCollection parcelOut, String nameDistrib) throws IOException {
		List<Double> areaParcel = new ArrayList<Double>();
		
		//get the bounds
		double aMax = 0;
		double aMin = 100000000;
		SimpleFeatureIterator parcelIt = parcelOut.features();
		try {
			while (parcelIt.hasNext()) {
				SimpleFeature feat = parcelIt.next();
				double area = ((Geometry) feat.getDefaultGeometry()).getArea();
				areaParcel.add(area);
				if (area < aMin) {
					aMin = area;
				}
				if (area > aMax) {
					aMax = area;
				}
			}
		} catch (Exception problem) {
			problem.printStackTrace();
		} finally {
			parcelIt.close();
		}
		return new AreaGraph(areaParcel, aMin, aMax,nameDistrib);
	}

	/**
	 * Generate a histogram graph
	 * @param graph : area graph object with sorted distribution and bounds
	 * @param graphDepotFolder : folder where every stats are stocked
	 * @param title : title of the graph
	 * @param x : Name of the distribution
	 * @param xTitle: title of the x dimention
	 * @param yTitle: title of the y dimention
	 * @param range : number of categories
	 * @throws IOException
	 */
	public static void makeGraphHisto(AreaGraph graph, File graphDepotFolder, String title, String xTitle, String yTitle, int range)
			throws IOException {
		List<AreaGraph> list = new ArrayList<AreaGraph>();
		list.add(graph);
		makeGraphHisto(list, graphDepotFolder, title, xTitle, yTitle, range);
	}

	public static void makeGraphHisto(List<AreaGraph> graphs, File graphDepotFolder, String title, String xTitle, String yTitle, int range)
			throws IOException {

		// general settings
		CategoryChart chart = new CategoryChartBuilder().width(600).height(600).title(title).xAxisTitle(xTitle).yAxisTitle(yTitle).build();
//TODO FIXME l'échelle en x n'est pas respécté pour le second graph.. 
		for (AreaGraph ag : graphs) {
			System.out.println(ag.getBoundMin()+ ag.getBoundMax());
			Histogram histo = new Histogram(ag.getSortedDistribution(), range, ag.getBoundMin(), ag.getBoundMax());
			chart.addSeries(ag.getNameDistrib(), histo.getxAxisData(), histo.getyAxisData());
		}
		// Customize Chart
		// chart.getStyler().setLegendPosition(LegendPosition.InsideNW);
		chart.getStyler().setLegendVisible(true);
		chart.getStyler().setHasAnnotations(false);
		chart.getStyler().setXAxisLabelRotation(45);
		chart.getStyler().setXAxisDecimalPattern("####");
		chart.getStyler().setXAxisLogarithmicDecadeOnly(true);
		chart.getStyler().setYAxisLogarithmicDecadeOnly(true);
		BitmapEncoder.saveBitmap(chart, graphDepotFolder + "/" + xTitle + yTitle, BitmapFormat.PNG);

	}
}
