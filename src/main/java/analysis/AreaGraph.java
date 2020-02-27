package analysis;

import java.util.List;

public class AreaGraph {
	public AreaGraph(List<Double> sortedDistribution, double boundMin, double boundMax) {
		this.sortedDistribution = sortedDistribution;
		this.boundMin = boundMin;
		this.boundMax = boundMax;
	}
	List<Double> sortedDistribution;
	double boundMin, boundMax;
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
}
