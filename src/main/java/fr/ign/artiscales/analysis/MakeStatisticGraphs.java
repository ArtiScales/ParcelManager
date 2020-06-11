package fr.ign.artiscales.analysis;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
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

import fr.ign.artiscales.fields.GeneralFields;

/**
 * Class to automate the creation of stat graphs
 */
public class MakeStatisticGraphs {

	// public static void main(String[] args) throws IOException {
	// makeAreaGraph(new File("/tmp/parcelCuted-consolid.shp"), new File("/tmp/"));
	// }

	/**
	 * Automate the generation of graphs about area of fresh parcel cuts
	 * 
	 * @param freshParcelCut
	 * @param outFolder
	 * @throws IOException
	 */
	public static void makeAreaGraph(File freshParcelCut, File outFolder) throws IOException {
		ShapefileDataStore sds = new ShapefileDataStore(freshParcelCut.toURI().toURL());
		makeGraphHisto(sortValuesAndCategorize(GeneralFields.getParcelWithSimulatedFileds(sds.getFeatureSource().getFeatures()), "area"), outFolder,
				"Distribution de la surface des parcelles subdivisées - " + freshParcelCut.getName(), "Surface d'une parcelle (m2)",
				"Nombre de parcelles", 10);
		sds.dispose();
	}

	/**
	 * Process to sort which parcels have been cut, and get the bounds of the distribution. WARNING - Developed for French Parcels - section is always a two character
	 * 
	 * @param parcelOut
	 *            the parcel to sort and plot
	 * @param nameDistrib
	 *            the name of the distribution
	 * @return a Graph object
	 * @throws IOException
	 */
	public static AreaGraph sortValuesAndCategorize(SimpleFeatureCollection parcelOut, String nameDistrib) throws IOException {
		List<Double> areaParcel = new ArrayList<Double>();
		DescriptiveStatistics stat = new DescriptiveStatistics();
		try (SimpleFeatureIterator parcelIt = parcelOut.features()) {
			while (parcelIt.hasNext()) {
				double area = ((Geometry) parcelIt.next().getDefaultGeometry()).getArea();
				stat.addValue(area);
				areaParcel.add(area);
			}
		} catch (Exception problem) {
			problem.printStackTrace();
		}
		// get the bounds
		double aMax = 0;
		double aMin = 100000000;
		// erase the 10% of min and max
		List<Double> areaParcelWithoutCrest = new ArrayList<Double>();
		double infThres = stat.getPercentile(10);
		double supThres = stat.getPercentile(90);
		for (double a : areaParcel) {
			if (a > infThres && a < supThres) {
				areaParcelWithoutCrest.add(a);
				if (a < aMin) {
					aMin = a;
				}
				if (a > aMax) {
					aMax = a;
				}
			}}
		Collections.sort(areaParcelWithoutCrest);
		return new AreaGraph(areaParcelWithoutCrest, aMin, aMax, nameDistrib);
	}

	/**
	 * Generate a histogram graph
	 * 
	 * @param graph
	 *            area graph object with sorted distribution and bounds
	 * @param graphDepotFolder
	 *            folder where every stats are stocked
	 * @param title
	 *            title of the graph
	 * @param xTitle
	 *            title of the x dimention
	 * @param yTitle
	 *            title of the y dimention
	 * @param range
	 *            number of categories
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
		// TODO FIXME l'échelle en x n'est pas respécté pour le second graph..
		for (AreaGraph ag : graphs) {
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
		BitmapEncoder.saveBitmap(chart, graphDepotFolder + "/" + title, BitmapFormat.PNG);
	}
	
	public static void roadGraph(SimpleFeatureCollection roads, String title, String xTitle, String yTitle, File graphDepotFolder)
			throws IOException {
		HashMap<String, Double> vals = new HashMap<String, Double>();
		try (SimpleFeatureIterator it = roads.features()) {
			while (it.hasNext()) {
				SimpleFeature road = it.next();
				String line = (String) road.getAttribute("NATURE") ;
				if (!((String) road.getAttribute("CL_ADMIN")).equals("Autre")) {
					line = line + "-" + ((String) road.getAttribute("CL_ADMIN"));
				}
				line = line + "-largeur:" +road.getAttribute("LARGEUR");
				vals.put( line,
						((Geometry) road.getDefaultGeometry()).getLength() + vals.getOrDefault((double) road.getAttribute("LARGEUR"), 0.0));
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		// general settings
		CategoryChart chart = new CategoryChartBuilder().width(600).height(600).title(title).xAxisTitle(xTitle).yAxisTitle(yTitle).build();
		System.out.println(vals);
		chart.addSeries("roads length", vals.keySet().stream().collect(Collectors.toList()), vals.values().stream().collect(Collectors.toList()));

		// Customize Chart
		// chart.getStyler().setLegendPosition(LegendPosition.InsideNW);
		chart.getStyler().setLegendVisible(true);
		chart.getStyler().setHasAnnotations(false);
		chart.getStyler().setXAxisLabelRotation(90);
		chart.getStyler().setXAxisDecimalPattern("####");
		chart.getStyler().setXAxisLogarithmicDecadeOnly(true);
		chart.getStyler().setYAxisLogarithmicDecadeOnly(true);
		BitmapEncoder.saveBitmap(chart, graphDepotFolder + "/" + title, BitmapFormat.PNG);
	}
}
