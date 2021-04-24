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
    List<Double> sortedDistribution;
    double boundMin, boundMax;
    String nameDistrib;
    public Graph(List<Double> sortedDistribution, double boundMin, double boundMax, String nameDistribution) {
        this.sortedDistribution = sortedDistribution;
        this.boundMin = boundMin;
        this.boundMax = boundMax;
        this.nameDistrib = nameDistribution;
    }

    public List<Double> getSortedDistribution() {
        return sortedDistribution;
    }

    public void setSortedDistribution(List<Double> sortedDistribution) {
        this.sortedDistribution = sortedDistribution;
    }

    public double getBoundMin() {
        return boundMin;
    }

    public void setBoundMin(double boundMin) {
        this.boundMin = boundMin;
    }

    public double getBoundMax() {
        return boundMax;
    }

    public void setBoundMax(double boundMax) {
        this.boundMax = boundMax;
    }

    public String getNameDistrib() {
        return nameDistrib;
    }

    public void setNameDistrib(String nameDistrib) {
        this.nameDistrib = nameDistrib;
    }

    @Override
    public String toString() {
        return "Graph [sortedDistribution=" + sortedDistribution + ", boundMin=" + boundMin + ", boundMax=" + boundMax + ", nameDistrib="
                + nameDistrib + "]";
    }

    public void toCSV(File folderOut) throws IOException {
        HashMap<String, Object[]> data = new HashMap<>();
        data.put("sortedDistribution", sortedDistribution.toArray());
        CsvExport.generateCsvFileCol(data, folderOut, nameDistrib);
//		Csv.calculateColumnsBasicStat(new File(folderOut, nameDistrib + ".csv"), 0, true);
    }
}
