package decomposition.geom;

import org.locationtech.jts.geom.LineString;

public class Strip {
  public Strip() {
  }

  private LineString generatorEdge;
  private LineString aimEdge;
  private LineString initLatEdge;
  private LineString endEdge;

  public LineString getGeneratorEdge() {
    return generatorEdge;
  }

  public void setGeneratorEdge(LineString generatorEdge) {
    this.generatorEdge = generatorEdge;
  }

  public LineString getAimEdge() {
    return aimEdge;
  }

  public void setAimEdge(LineString aimEdge) {
    this.aimEdge = aimEdge;
  }

  public LineString getInitLatEdge() {
    return initLatEdge;
  }

  public void setInitLatEdge(LineString initLatEdge) {
    this.initLatEdge = initLatEdge;
  }

  public LineString getEndEdge() {
    return endEdge;
  }

  public void setEndEdge(LineString endEdge) {
    this.endEdge = endEdge;
  }
}
