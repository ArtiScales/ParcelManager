package fr.ign.artiscales.pm.analysis;

import fr.ign.artiscales.pm.fields.GeneralFields;
import fr.ign.artiscales.pm.parcelFunction.ParcelState;
import fr.ign.artiscales.tools.geoToolsFunctions.vectors.collec.CollecMgmt;
import fr.ign.artiscales.tools.geoToolsFunctions.vectors.collec.CollecTransform;
import fr.ign.artiscales.tools.geoToolsFunctions.vectors.geom.Lines;
import fr.ign.artiscales.tools.geoToolsFunctions.vectors.geom.Polygons;
import fr.ign.artiscales.tools.io.csv.CsvExport;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.geotools.data.DataStore;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.knowm.xchart.BitmapEncoder;
import org.knowm.xchart.BitmapEncoder.BitmapFormat;
import org.knowm.xchart.CategoryChart;
import org.knowm.xchart.CategoryChartBuilder;
import org.knowm.xchart.Histogram;
import org.locationtech.jts.geom.Geometry;
import org.opengis.feature.simple.SimpleFeature;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Class to automate the creation of stat graphs
 *
 * @author Maxime Colomb
 */
public class MakeStatisticGraphs {

    // public static void main(String[] args) throws IOException {
    // makeAreaGraph(new File("/tmp/parcelCuted-consolid.gpkg"), new File("/tmp/"));
    // }

    /**
     * Automate the generation of graphs about area of fresh parcel cuts.
     *
     * @param parcelFile Shapefile of the parcel plan
     * @param outFolder  Folder where the graph have to be exported
     * @param name       Title of the graph
     * @throws IOException Reading parcelFile
     */
    public static void makeAreaGraph(File parcelFile, File outFolder, String name) throws IOException {
        DataStore ds = CollecMgmt.getDataStore(parcelFile);
        makeGraphHisto(sortValuesAreaAndCategorize(GeneralFields.getParcelWithSimulatedFileds(ds.getFeatureSource(ds.getTypeNames()[0]).getFeatures()), "area"), outFolder,
                name, "parcels area (m2)", "Number of parcels", 10);
        ds.dispose();
    }

    /**
     * Automate the generation of graphs about area of fresh parcel cuts.
     *
     * @param markedParcelFile {@link List} of parcels.
     * @param outFolder        Folder where the graph have to be exported
     * @param name             Title of the graph
     */
    public static void makeAreaGraph(List<SimpleFeature> markedParcelFile, File outFolder, String name) {
        makeGraphHisto(sortValuesAreaAndCategorize(markedParcelFile, "area", false), outFolder, name, "Parcels area (m2)", "Number of parcels", 10);
    }

    /**
     * Automate the generation of graphs about the contact length on road of fresh parcel cuts.
     *
     * @param markedParcelFile {@link List} of parcels.
     * @param outFolder        Folder where the graph have to be exported
     * @param name             Title of the graph
     * @param block            morphological islet
     * @param roadFile         geo file containing road
     * @throws IOException writing result
     */
    public static void makeWidthContactRoadGraph(List<SimpleFeature> markedParcelFile, SimpleFeatureCollection block, File roadFile, File outFolder, String name) throws IOException {
        DataStore roadDS = CollecMgmt.getDataStore(roadFile);
        List<Double> parcelMesure = new ArrayList<>();
        markedParcelFile.stream().forEach(parcel -> {
            // if parcel is marked to be analyzed
            Geometry parcelGeom = (Geometry) parcel.getDefaultGeometry();
            double widthRoadContact = 0;
            try {
                widthRoadContact = ParcelState.getParcelFrontSideWidth(Polygons.getPolygon(parcelGeom),
                        CollecTransform.selectIntersection(roadDS.getFeatureSource(roadDS.getTypeNames()[0]).getFeatures(), parcelGeom.buffer(7)), Lines.fromMultiToLineString(
                                CollecTransform.fromPolygonSFCtoRingMultiLines(CollecTransform.selectIntersection(block, parcelGeom.buffer(7)))));
            } catch (IOException e) {
                e.printStackTrace();
            }
            parcelMesure.add(widthRoadContact);
        });
        roadDS.dispose();
        Graph graph = sortValuesAndCategorize(parcelMesure, false, "Length of contact between road and parcel");
        makeGraphHisto(graph, outFolder, name, "Length (m)", "Number of parcels", 10);
    }

    /**
     * Process to sort which parcels have been cut, and get the bounds of the distribution. WARNING - Developed for French Parcels - section is always a two character. By default,
     * does not cut the top and low 10% values
     *
     * @param parcelOut   The parcel to sort and plot
     * @param nameDistrib The name of the distribution
     * @return An {@link Graph} object
     */
    public static Graph sortValuesAreaAndCategorize(SimpleFeatureCollection parcelOut, String nameDistrib) {
        return sortValuesAreaAndCategorize(Arrays.stream(parcelOut.toArray(new SimpleFeature[0])).collect(Collectors.toList()), nameDistrib, false);
    }

    /**
     * Process to sort which parcels have been cut, and get the bounds of the distribution. WARNING - Developed for French Parcels - section is always a two character. Can spare
     * the crest values (the top and low 10%)
     *
     * @param parcelOut   the parcel to sort and plot
     * @param nameDistrib the name of the distribution
     * @param cutCrest    Cut the top and low 10% values
     * @return a Graph object
     */
    public static Graph sortValuesAreaAndCategorize(List<SimpleFeature> parcelOut, String nameDistrib, boolean cutCrest) {
        List<Double> areaParcel = new ArrayList<>();

        for (SimpleFeature sf : parcelOut)
            areaParcel.add(((Geometry) sf.getDefaultGeometry()).getArea());
        return sortValuesAndCategorize(areaParcel, cutCrest, nameDistrib);
    }

    /**
     * Process to sort which parcels have been cut, and get the bounds of the distribution. WARNING - Developed for French Parcels - section is always a two character. Can spare
     * the crest values (the top and low 10%)
     *
     * @param parcelMesure Mesures of the parcel to sort and plot
     * @param nameDistrib  the name of the distribution
     * @param cutCrest     Cut the top and low 10% values
     * @return a Graph object
     */
    public static Graph sortValuesAndCategorize(List<Double> parcelMesure, boolean cutCrest, String nameDistrib) {
        DescriptiveStatistics stat = new DescriptiveStatistics();
        for (double m : parcelMesure)
            stat.addValue(m);
        // get the bounds
        double aMax = 0;
        double aMin = 100000000;
        // erase the 10% of min and max
        List<Double> areaParcelWithoutCrest = new ArrayList<>();
        double infThres = stat.getPercentile(10);
        double supThres = stat.getPercentile(90);
        for (double a : parcelMesure) {
            if (cutCrest && (a <= infThres || a >= supThres))
                continue;
            if (a < aMin)
                aMin = a;
            if (a > aMax)
                aMax = a;
            areaParcelWithoutCrest.add(a);
        }
        Collections.sort(areaParcelWithoutCrest);
        return new Graph(areaParcelWithoutCrest, aMin, aMax, nameDistrib);
    }

    /**
     * Generate a histogram graph.
     *
     * @param graph            area graph object with sorted distribution and bounds
     * @param graphDepotFolder folder where every stats are stocked
     * @param title            title of the graph
     * @param xTitle           title of the x dimention
     * @param yTitle           title of the y dimention
     * @param range            number of categories
     */
    public static void makeGraphHisto(Graph graph, File graphDepotFolder, String title, String xTitle, String yTitle, int range) {
        makeGraphHisto(Collections.singletonList(graph), graphDepotFolder, title, xTitle, yTitle, range);
    }

    /**
     * Generate a histogram graph.
     *
     * @param graphs           area graph objects with sorted distribution and bounds
     * @param graphDepotFolder folder where every stats are stocked
     * @param title            title of the graph
     * @param xTitle           title of the x dimention
     * @param yTitle           title of the y dimention
     * @param range            number of categories
     */
    public static void makeGraphHisto(List<Graph> graphs, File graphDepotFolder, String title, String xTitle, String yTitle, int range) {
        // general settings
        CategoryChart chart = new CategoryChartBuilder().width(450).height(350).title(title).xAxisTitle(xTitle).yAxisTitle(yTitle).build();
        // TODO FIXME l'échelle en x n'est pas respécté pour le second graph..
        for (Graph ag : graphs) {
            Histogram histo = new Histogram(ag.getSortedDistribution(), range, ag.getBoundMin(), ag.getBoundMax());
            chart.addSeries(ag.getNameDistrib(), histo.getxAxisData(), histo.getyAxisData());
        }
        // Customize Chart
        // chart.getStyler().setLegendPosition(LegendPosition.InsideNW);
        chart.getStyler().setLegendVisible(false);
        chart.getStyler().setHasAnnotations(false);
        chart.getStyler().setXAxisLabelRotation(45);
        chart.getStyler().setXAxisDecimalPattern("####");
        chart.getStyler().setXAxisLogarithmicDecadeOnly(true);
        chart.getStyler().setYAxisLogarithmicDecadeOnly(true);
        if (!graphDepotFolder.exists())
            graphDepotFolder.mkdirs();
        try {
            BitmapEncoder.saveBitmap(chart, graphDepotFolder + "/" + title, BitmapFormat.PNG);
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }

    /**
     * Makes a graph for the road (following french attributes, but that easily can be changed if needed).
     *
     * @param roads            Collection of Road segments.
     * @param graphDepotFolder folder where every stats are stocked
     * @param title            title of the graph
     * @param xTitle           title of the x dimention
     * @param yTitle           title of the y dimention
     * @throws IOException write .csv and graph
     */
    public static void roadGraph(SimpleFeatureCollection roads, String title, String xTitle, String yTitle, File graphDepotFolder) throws IOException {
        HashMap<String, Double> vals = new HashMap<>();
        try (SimpleFeatureIterator it = roads.features()) {
            while (it.hasNext()) {
                SimpleFeature road = it.next();
                String line = (String) road.getAttribute("NATURE");
//                if (!Objects.equals(road.getAttribute("CL_ADMIN"), "Autre"))
//                    line = line + "-" + road.getAttribute("CL_ADMIN");
                line = line + "- Width:" + road.getAttribute("LARGEUR") + "m";
                vals.put(line, ((Geometry) road.getDefaultGeometry()).getLength() + vals.getOrDefault(road.getAttribute("LARGEUR"), 0.0));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        //export to .csv
        HashMap<String, double[]> valsCsv = new HashMap<>();
        for (String key : vals.keySet()) {
//            System.out.println(key);
//            ByteBuffer buffer = StandardCharsets.US_ASCII.encode(key);
//            String encoded_String = StandardCharsets.UTF_8.decode(buffer).toString();
//            System.out.println(encoded_String);
            valsCsv.put(key, new double[]{vals.get(key)});
        }
        CsvExport.generateCsvFile(valsCsv, title + ".csv", graphDepotFolder, new String[]{"roadType", "totalLength"}, false);

        // general settings
        CategoryChart chart = new CategoryChartBuilder().width(500).height(600).title(title).xAxisTitle(xTitle).yAxisTitle(yTitle).build();
        chart.addSeries("roads length", new ArrayList<>(vals.keySet()), new ArrayList<>(vals.values()));

        // Customize Chart
        // chart.getStyler().setLegendPosition(LegendPosition.InsideNW);
        chart.getStyler().setLegendVisible(false);
        chart.getStyler().setHasAnnotations(false);
        chart.getStyler().setXAxisLabelRotation(90);
        chart.getStyler().setXAxisDecimalPattern("####");
        chart.getStyler().setXAxisLogarithmicDecadeOnly(true);
        chart.getStyler().setYAxisLogarithmicDecadeOnly(true);
        BitmapEncoder.saveBitmap(chart, graphDepotFolder + "/" + title, BitmapFormat.PNG);
    }
}