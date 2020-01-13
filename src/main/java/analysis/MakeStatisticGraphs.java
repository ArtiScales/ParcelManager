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

public class MakeStatisticGraphs {

	public static void main(String[] args) throws IOException {
		ShapefileDataStore sds = new ShapefileDataStore(new File("/tmp/parcelCuted-consolid.shp").toURI().toURL());
		SimpleFeatureCollection sfc = sds.getFeatureSource().getFeatures();
		sortValuesAndCategorize(sfc, new File("/tmp"));
	}

	public static void sortValuesAndCategorize(SimpleFeatureCollection parcelOut, File folderOut) throws IOException {
		List<Double> areaParcel = new ArrayList<Double>();
		double aMax = 0;
		double aMin = 100000000;

		// Arrays.stream(parcelOut.toArray(new SimpleFeature[0])).forEach(feat -> {

		SimpleFeatureIterator parcelIt = parcelOut.features();
		try {
			while (parcelIt.hasNext()) {
				SimpleFeature feat = parcelIt.next();

				double area = ((Geometry) feat.getDefaultGeometry()).getArea();
				if (((String) feat.getAttribute("SECTION")) != null && ((String) feat.getAttribute("SECTION")).length() > 3) {
					areaParcel.add(area);
					if (area < aMin) {
						aMin = area;
					}
					if (area > aMax) {
						aMax = area;
					}
				}
				// });
			}
		} catch (Exception problem) {
			problem.printStackTrace();
		} finally {
			parcelIt.close();
		}
		makeGraph(areaParcel, folderOut, "Distribution de la surface des parcelles subdivisées", "x", "Surface d'une parcelle (m2)",
				"Nombre de parcelles", (int) aMin, (int) aMax, 10);
	}

	public static void makeGraph(List<Double> values, File graphDepotFile, String title, String x, String xTitle, String yTitle, int xMin, int xMax,
			int range) throws IOException {

		Histogram histo = new Histogram(values, range, xMin, xMax);
		CategoryChart chart = new CategoryChartBuilder().width(600).height(600).title(title).xAxisTitle(xTitle).yAxisTitle(yTitle).build();
		chart.addSeries(x, histo.getxAxisData(), histo.getyAxisData());

		// Customize Chart
		// chart.getStyler().setLegendPosition(LegendPosition.InsideNW);
		chart.getStyler().setLegendVisible(false);
		chart.getStyler().setHasAnnotations(false);
		chart.getStyler().setXAxisLabelRotation(45);
		chart.getStyler().setXAxisDecimalPattern("####");
		chart.getStyler().setXAxisLogarithmicDecadeOnly(true);
		chart.getStyler().setYAxisLogarithmicDecadeOnly(true);
		BitmapEncoder.saveBitmap(chart, graphDepotFile + "/" + xTitle + yTitle, BitmapFormat.PNG);

	}
}
