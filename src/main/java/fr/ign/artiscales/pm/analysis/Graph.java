package fr.ign.artiscales.pm.analysis;

import fr.ign.artiscales.tools.io.csv.CsvExport;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;

/**
 * Class to export fast and automated graph created with Xchart library
 */
public class Graph {
    private List<Double> sortedDistribution;
    private double boundMin, boundMax;
    private String nameDistrib;

    /**
     * Put every information in a graph object.
     *
     * @param sortedDistribution distribution sorted in the wanted order
     * @param boundMin           minimal bound
     * @param boundMax           maximal bound
     * @param nameDistribution   name of the distribution
     */
    public Graph(List<Double> sortedDistribution, double boundMin, double boundMax, String nameDistribution) {
        this.sortedDistribution = sortedDistribution;
        this.boundMin = boundMin;
        this.boundMax = boundMax;
        this.nameDistrib = nameDistribution;
    }

    /**
     * Get value for sorted distribution
     *
     * @return sorted distribution
     */
    public List<Double> getSortedDistribution() {
        return sortedDistribution;
    }

    /**
     * Set value for sorted distribution
     *
     * @param sortedDistribution new value
     */
    public void setSortedDistribution(List<Double> sortedDistribution) {
        this.sortedDistribution = sortedDistribution;
    }

    /**
     * Get value for minimal bound represented on the schema
     *
     * @return minimal bound
     */
    public double getBoundMin() {
        return boundMin;
    }

    /**
     * Set value for minimal bound represented on the schema
     *
     * @param boundMin minimal bound
     */
    public void setBoundMin(double boundMin) {
        this.boundMin = boundMin;
    }

    /**
     * Set value for maximal bound represented on the schema
     *
     * @return maximal bound
     */
    public double getBoundMax() {
        return boundMax;
    }

    /**
     * Set value for maximal bound represented on the schema
     *
     * @param boundMax maximal bound
     */
    public void setBoundMax(double boundMax) {
        this.boundMax = boundMax;
    }

    /**
     * Get the name of the distribution
     *
     * @return name
     */
    public String getNameDistrib() {
        return nameDistrib;
    }

    /**
     * Set the name of the distribution
     *
     * @param nameDistrib name
     */
    public void setNameDistrib(String nameDistrib) {
        this.nameDistrib = nameDistrib;
    }

    @Override
    public String toString() {
        return "Graph [sortedDistribution=" + sortedDistribution + ", boundMin=" + boundMin + ", boundMax=" + boundMax + ", nameDistrib=" + nameDistrib + "]";
    }

    /**
     * Write distribution to a .csv file;
     *
     * @param folderOut folder where the .csv is wrote
     * @throws IOException writing .csv
     */
    public void toCSV(File folderOut) throws IOException {
        HashMap<String, Object[]> data = new HashMap<>();
        data.put("sortedDistribution", sortedDistribution.toArray());
        CsvExport.generateCsvFileCol(data, folderOut, nameDistrib);
//		Csv.calculateColumnsBasicStat(new File(folderOut, nameDistrib + ".csv"), 0, true);
    }
}
