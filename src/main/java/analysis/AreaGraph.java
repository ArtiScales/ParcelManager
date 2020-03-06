package analysis;

import java.util.List;

public class AreaGraph {
	public AreaGraph(List<Double> sortedDistribution, double boundMin, double boundMax, String nameDistribution) {
		this.sortedDistribution = sortedDistribution;
		this.boundMin = boundMin;
		this.boundMax = boundMax;
		this.nameDistrib = nameDistribution;
	}
	List<Double> sortedDistribution;
	double boundMin, boundMax;
	String nameDistrib;
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
		return "AreaGraph [sortedDistribution=" + sortedDistribution + ", boundMin=" + boundMin + ", boundMax=" + boundMax + ", nameDistrib="
				+ nameDistrib + "]";
	}
}
