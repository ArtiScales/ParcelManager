package fr.ign.artiscales.decomposition;

import java.io.File;
import java.util.List;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.geotools.data.memory.MemoryDataStore;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.feature.DefaultFeatureCollection;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Polygon;
import org.opengis.feature.Feature;
import org.opengis.feature.FeatureVisitor;
import org.opengis.feature.simple.SimpleFeature;

import fr.ign.artiscales.parcelFunction.MarkParcelAttributeFromPosition;
import fr.ign.artiscales.parcelFunction.ParcelSchema;
import fr.ign.cogit.geoToolsFunctions.vectors.Geom;

public class ParcelSplit {

  /**
   * Splitting parcel processus. It get the usual parcel schema and add the "split" field in order to determine in the parcel will be splited or not. All the parcels are then
   * split.
   * @deprecated
   * @param parcelIn
   *          : collection of parcels
   * @param tmpFolder
   *          : a folder to store temporary files
   * @param maximalArea
   *          : area of the parcel under which the parcel won't be anymore cut
   * @param maximalWidth
   *          : width of the parcel under which the parcel won't be anymore cut
   * @param epsilon
   *          :
   * @param extBlock
   * @param streetWidth
   *          : with of the street composing the street network
   * @param decompositionLevelWithoutStreet
   *          : number of last iteration row for which no street network is generated
   * @param forceStreetAccess
   *          : force the access to the road for each parcel. Not working good yet.
   * @return a collection of subdivised parcels
   * @throws Exception
   */
  public static SimpleFeatureCollection generateSplitAllParcels(SimpleFeature parcelIn, File tmpFolder, double maximalArea, double maximalWidth, double epsilon,
      List<LineString> extBlock, double streetWidth, int decompositionLevelWithoutStreet, boolean forceStreetAccess) throws Exception {

    // putting the need of splitting into attribute

    SimpleFeatureBuilder sfBuilder = ParcelSchema.getSFBParcelAsASSplit();

    DefaultFeatureCollection toSplit = new DefaultFeatureCollection();

    String numParcelValue = "";
    if (parcelIn.getAttribute("CODE") != null) {
      numParcelValue = parcelIn.getAttribute("CODE").toString();
    } else if (parcelIn.getAttribute("CODE_DEP") != null) {
      numParcelValue = ((String) parcelIn.getAttribute("CODE_DEP")) + (parcelIn.getAttribute("CODE_COM").toString()) + (parcelIn.getAttribute("COM_ABS").toString())
          + (parcelIn.getAttribute("SECTION").toString());
    } else if (parcelIn.getAttribute("NUMERO") != null) {
      numParcelValue = parcelIn.getAttribute("NUMERO").toString();
    }
    Object[] attr = { numParcelValue, parcelIn.getAttribute("CODE_DEP"), parcelIn.getAttribute("CODE_COM"), parcelIn.getAttribute("COM_ABS"), parcelIn.getAttribute("SECTION"),
        parcelIn.getAttribute("NUMERO"), parcelIn.getAttribute("INSEE"), parcelIn.getAttribute("eval"), parcelIn.getAttribute("DoWeSimul"), 1 };

    sfBuilder.add(parcelIn.getDefaultGeometry());
    toSplit.add(sfBuilder.buildFeature(null, attr));

    return splitParcels(toSplit, maximalArea, maximalWidth, epsilon, 0, extBlock, streetWidth, forceStreetAccess, decompositionLevelWithoutStreet, tmpFolder);

  }

  /**
   * Splitting parcel processus. It get the usual parcel schema and add the "split" field in order to determine in the parcel will be splited or not. All the parcels bigger than
   * the maximal area are split.
   * @deprecated
   * @param parcelIn
   *          : collection of parcels
   * @param tmpFolder
   *          : a folder to store temporary files
   * @param maximalArea
   *          : area of the parcel under which the parcel won't be anymore cut
   * @param maximalWidth
   *          : width of the parcel under which the parcel won't be anymore cut
   * @param epsilon
   *          :
   * @param extBlock
   * @param decompositionLevelWithoutStreet
   *          : number of last iteration row for which no street network is generated
   * @param streetWidth
   *          : with of the street composing the street network
   * @param forceStreetAccess
   *          : force the access to the road for each parcel. Not working good yet.
   * @return a collection of subdivised parcels
   * @throws Exception
   */
  public static SimpleFeatureCollection generateSplitParcelsIfBigger(SimpleFeatureCollection parcelsIn, File tmpFolder, double maximalArea, double maximalWidth, double epsilon,
      List<LineString> extBlock, int decompositionLevelWithoutStreet, double streetWidth, boolean forceStreetAccess) throws Exception {

    // create a new collection
    SimpleFeatureBuilder sfBuilder = ParcelSchema.getSFBParcelAsASSplit();
    DefaultFeatureCollection toSplit = new DefaultFeatureCollection();

    // iterate on the parcels
    int i = 0;
    try ( SimpleFeatureIterator parcelIt = parcelsIn.features()) {
      while (parcelIt.hasNext()) {
        SimpleFeature feat = parcelIt.next();
        String numParcelValue = "";
        if (feat.getAttribute("CODE") != null) {
          numParcelValue = feat.getAttribute("CODE").toString();
        } else if (feat.getAttribute("CODE_DEP") != null) {
          numParcelValue = ((String) feat.getAttribute("CODE_DEP")) + (feat.getAttribute("CODE_COM").toString()) + (feat.getAttribute("COM_ABS").toString())
              + (feat.getAttribute("SECTION").toString());
        } else if (feat.getAttribute("NUMERO") != null) {
          numParcelValue = feat.getAttribute("NUMERO").toString();
        }
        Object[] attr = { numParcelValue, feat.getAttribute("CODE_DEP"), feat.getAttribute("CODE_COM"), feat.getAttribute("COM_ABS"), feat.getAttribute("SECTION"),
            feat.getAttribute("NUMERO"), feat.getAttribute("INSEE"), feat.getAttribute("eval"), feat.getAttribute("DoWeSimul"), 0 };

        if (((Geometry) feat.getDefaultGeometry()).getArea() > maximalArea) {
          attr[9] = 1;
        }
        sfBuilder.add(feat.getDefaultGeometry());
        toSplit.add(sfBuilder.buildFeature(String.valueOf(i), attr));
        i = i + 1;
      }
    } catch (Exception problem) {
      problem.printStackTrace();
    }
    return splitParcels(toSplit, maximalArea, maximalWidth, epsilon, 0.0, extBlock, streetWidth, forceStreetAccess, decompositionLevelWithoutStreet, tmpFolder);
  }
  
  /**
   * Overload to split a single parcel
   * 
   * @param toSplit
   *          : collection of parcels
   * @param maximalArea
   *          : area of the parcel under which the parcel won't be anymore cut
   * @param maximalWidth
   *          : width of the parcel under which the parcel won't be anymore cut
   * @param epsilon
   *          :
   * @param extBlock
   * @param streetWidth
   *          : with of the street composing the street network
   * @param decompositionLevelWithoutStreet
   *          : number of last iteration row for which no street network is generated
   * @param forceStreetAccess
   *          : force the access to the road for each parcel. Not working good yet.
   * @param tmpFolder
   *          : a folder to store temporary files
   * @param addArg
   *          : add the parent parcels attributes to the new cuted parcels by re-working them
   * @return a collection of subdivised parcels
   * @throws Exception
   */
  
  public static SimpleFeatureCollection splitParcels(SimpleFeature toSplit, double maximalArea, double maximalWidth, double streetEpsilon, double noise, List<LineString> extBlock,
	      double streetWidth, boolean forceStreetAccess, int decompositionLevelWithoutStreet, File tmpFolder) throws Exception {
	    return splitParcels(toSplit, maximalArea, maximalWidth, streetEpsilon, noise, extBlock, streetWidth,  999,  streetWidth, forceStreetAccess, decompositionLevelWithoutStreet, tmpFolder);
	  }
  
  public static SimpleFeatureCollection splitParcels(SimpleFeature toSplit, double maximalArea, double maximalWidth, double streetEpsilon, double noise, List<LineString> extBlock,
      double smallStreetWidth, int largeStreetLevel, double largeStreetWidth, boolean forceStreetAccess, int decompositionLevelWithoutStreet, File tmpFolder) throws Exception {
    DefaultFeatureCollection in = new DefaultFeatureCollection();
    in.add(toSplit);
    return splitParcels(in.collection(), maximalArea, maximalWidth, streetEpsilon, noise, extBlock, smallStreetWidth, largeStreetLevel, largeStreetWidth, forceStreetAccess, decompositionLevelWithoutStreet, tmpFolder);
  }

  /**
   * Split the parcels into sub parcels. The parcel that are going to be cut must have a field "SPLIT" with the value of 1. A dirty conversion is made from GeoTools format to
   * GeOxygene format because the functions that must translate them doesn't work yet.
   * 
   * @param toSplit
   *          : collection of parcels
   * @param maximalArea
   *          : area of the parcel under which the parcel won't be anymore cut
   * @param maximalWidth
   *          : width of the parcel under which the parcel won't be anymore cut
   * @param epsilon
   *          :
   * @param extBlock
   * @param streetWidth
   *          : with of the street composing the street network
   * @param decompositionLevelWithoutStreet
   *          : number of last iteration row for which no street network is generated
   * @param forceStreetAccess
   *          : force the access to the road for each parcel. Not working good yet.
   * @param tmpFolder
   *          : a folder to store temporary files
   * @param addArg
   *          : add the parent parcels attributes to the new cuted parcels by re-working them
   * @return a collection of subdivised parcels
   * @throws Exception
   */
  public static SimpleFeatureCollection splitParcels(SimpleFeatureCollection toSplit, double maximalArea, double maximalWidth, double streetEpsilon, double noise,
      List<LineString> extBlock, double streetWidth, boolean forceStreetAccess, int decompositionLevelWithoutStreet, File tmpFile) throws Exception {
		return splitParcels(toSplit, maximalArea, maximalWidth, streetEpsilon, noise, extBlock, streetWidth, 999, streetWidth, forceStreetAccess,
				decompositionLevelWithoutStreet, tmpFile);
  }
	  public static SimpleFeatureCollection splitParcels(SimpleFeatureCollection toSplit, double maximalArea, double maximalWidth, double streetEpsilon, double noise,
		      List<LineString> extBlock, double smallStreetWidth, int largeStreetLevel, double largeStreetWidth, boolean forceStreetAccess, int decompositionLevelWithoutStreet, File tmpFile) throws Exception {
	  
    // Configure memory datastore
    final MemoryDataStore memory = new MemoryDataStore();
    memory.createSchema(toSplit.getSchema());
    toSplit.accepts(new FeatureVisitor() {
      public void visit(Feature f) {
        SimpleFeature feature = (SimpleFeature) f;
        SimpleFeatureBuilder builder = new SimpleFeatureBuilder(toSplit.getSchema());
        builder.init(feature);
        Object o = feature.getAttribute(MarkParcelAttributeFromPosition.getMarkFieldName());
        // // if the parcel is not to be split, we add it on the final result and continue to iterate through the parcels.
        if (o == null || Integer.parseInt(o.toString()) != 1) {
          SimpleFeature newFeature = builder.buildFeature(feature.getIdentifier().getID());
          memory.addFeature(newFeature);
        } else {
            Polygon polygon = (Polygon) Geom.getPolygon((Geometry) feature.getDefaultGeometry());
//          List<LineString> list = new ArrayList<>(extBlock.getNumGeometries());
//          for (int i = 0; i < extBlock.getNumGeometries(); i++) list.add((LineString) extBlock.getGeometryN(i));
          DescriptiveStatistics dS = new DescriptiveStatistics();
		  OBBBlockDecomposition.decompose(polygon, extBlock, maximalArea, maximalWidth, noise, streetEpsilon, smallStreetWidth, largeStreetLevel,
									largeStreetWidth, forceStreetAccess, 0, decompositionLevelWithoutStreet)
							.stream().forEach(c -> dS.addValue(c.getValue()));
		  int decompositionLevelWithRoad = (int) dS.getPercentile(50) - decompositionLevelWithoutStreet;
		  int decompositionLevelWithLargeRoad = (int) dS.getPercentile(50) - largeStreetLevel ;
		  OBBBlockDecomposition
		    .decompose(polygon, extBlock, maximalArea, maximalWidth, noise, streetEpsilon, smallStreetWidth, decompositionLevelWithLargeRoad ,
									largeStreetWidth, forceStreetAccess, decompositionLevelWithRoad, decompositionLevelWithoutStreet)
		  	.childrenStream().forEach(p-> {
            SimpleFeature newFeature = builder.buildFeature(null);
            newFeature.setDefaultGeometry(p.getKey());
            memory.addFeature(newFeature);
          });
        }
      }
    }, null);
    return memory.getFeatureSource(toSplit.getSchema().getName()).getFeatures();
  }
}
