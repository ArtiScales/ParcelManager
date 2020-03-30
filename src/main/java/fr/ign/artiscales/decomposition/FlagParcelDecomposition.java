package fr.ign.artiscales.decomposition;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.MultiLineString;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.PrecisionModel;
import org.locationtech.jts.operation.union.CascadedPolygonUnion;
import org.locationtech.jts.precision.GeometryPrecisionReducer;
import org.opengis.feature.simple.SimpleFeature;

import fr.ign.cogit.FeaturePolygonizer;
import fr.ign.cogit.geoToolsFunctions.vectors.Collec;
import fr.ign.cogit.geoToolsFunctions.vectors.Geom;

/**
 * Re-implementation of block decomposition into parcels with flag shape. The algorithm is an adaptation from :
 * 
 * Vanegas, C. A., Kelly, T., Weber, B., Halatsch, J., Aliaga, D. G., Müller, P., May 2012. Procedural generation of parcels in urban modeling. Comp. Graph. Forum 31 (2pt3).
 * 
 * As input a polygon that represents the zone to decompose. For each step the decomposition is processed according to the OBBBlockDecomposition algorithm If one of the parcels do
 * not have access to the road, a L parcel is created. A road is added on the other parcel according to 1/ the shortest path to the public road 2/ if this shortest path does not
 * intersect an existing building. The width of the road is parametrable in the attributes : roadWidth
 * 
 * It is a recursive method, the decomposition is stop when a stop criteria is reached either the area or roadwidthaccess is below a given threshold
 * 
 * @author Mickael Brasebin
 *
 */
public class FlagParcelDecomposition {

  // We remove some parts that may have a too small area < 25
  public static double TOO_SMALL_PARCEL_AREA = 25;
  private static String widthFieldAttribute = "LARGEUR";
  private static double defaultWidthRoad = 7.5;

//  public static void main(String[] args) throws Exception {
//
//    // Input 1/ the input parcelles to split
//    String inputShapeFile = "src/main/resources/testData/parcelle.shp";
//    // Input 2 : the buildings that mustnt intersects the allowed roads (facultatif)
//    String inputBuildingFile = "src/main/resources/testData/building.shp";
//    // Input 3 (facultative) : the exterior of the urban block (it serves to determiner the multicurve)
//    String inputUrbanBlock = "src/main/resources/testData/ilot.shp";
//    // IFeatureCollection<IFeature> featC = ShapefileReader.read(inputUrbanBlock);
//    ShapefileDataStore blockDS = new ShapefileDataStore(new File(inputUrbanBlock).toURI().toURL());
//    SimpleFeatureCollection blocks = blockDS.getFeatureSource().getFeatures();
//    String folderOut = "data/";
//    // The output file that will contain all the decompositions
//    String shapeFileOut = folderOut + "outflag.shp";
//    (new File(folderOut)).mkdirs();
//    // Reading collection
//    ShapefileDataStore parcelDS = new ShapefileDataStore(new File(inputShapeFile).toURI().toURL());
//    SimpleFeatureCollection parcels = parcelDS.getFeatureSource().getFeatures();
//    ShapefileDataStore buildingsDS = new ShapefileDataStore(new File(inputBuildingFile).toURI().toURL());
//    SimpleFeatureCollection buildings = buildingsDS.getFeatureSource().getFeatures();
//    List<LineString> list = new ArrayList<>();
//    SimpleFeatureIterator iterator = Util.select(blocks, JTS.toGeometry(parcels.getBounds())).features();
//    while (iterator.hasNext()) {
//      SimpleFeature f = iterator.next();
//      Util.getPolygons((Geometry) f.getDefaultGeometry()).stream().forEach(p -> list.add(p.getExteriorRing()));
//    }
//    iterator.close();
//    blockDS.dispose();
//
//    // Maxmimal area for a parcel
//    double maximalArea = 800;
//    // MAximal with to the road
//    double maximalWidth = 15;
//    // Do we want noisy results
//    double noise = 0;
//    // The with of the road that is created
//    double roadWidth = 3;
//    //
//    // IFeatureCollection<IFeature> featCollOut = new FT_FeatureCollection<>();
//    //
//    List<Polygon> finalResult = new ArrayList<>();
//    iterator = parcels.features();
//    // For each shape
//    while (iterator.hasNext()) {
//      SimpleFeature feat = iterator.next();
////      if (feat.getAttribute("NUMERO").toString().equalsIgnoreCase("0024") && feat.getAttribute("FEUILLE").toString().equalsIgnoreCase("2")
////          && feat.getAttribute("SECTION").toString().equalsIgnoreCase("0A")) {
//
//      if (true) {
//        Geometry geom = (Geometry) feat.getDefaultGeometry();
//        // IDirectPosition dp = new DirectPosition(0, 0, 0); // geom.centroid();
//        // geom = geom.translate(-dp.getX(), -dp.getY(), 0);
//
//        // List<IOrientableSurface> surfaces = FromGeomToSurface.convertGeom(geom);
//        List<Polygon> surfaces = Util.getPolygons(geom);
//
//        if (surfaces.size() != 1) {
//          System.out.println("Not simple geometry : " + feat.toString());
//          continue;
//        }
//
//        // We run the algorithm of decomposition
//        FlagParcelDecomposition ffd = new FlagParcelDecomposition(surfaces.get(0), buildings, maximalArea, maximalWidth, roadWidth, list);
//        System.out.println("EXT");
//        System.out.println(ffd.getExtAsGeom());
//        List<Polygon> results = ffd.decompParcel(noise);
//
//        // final int intCurrentCount = i;
//        // results.stream().forEach(x -> AttributeManager.addAttribute(x, "ID", intCurrentCount, "Integer"));
//        // results.stream().forEach(x -> x.setGeom(x.getGeom().translate(dp.getX(), dp.getY(), 0)));
//        // Get the results
//        // featCollOut.addAll(results);
//        finalResult.addAll(results);
//      }
//    }
//    iterator.close();
//    buildingsDS.dispose();
//    parcelDS.dispose();
//
//    FeaturePolygonizer.saveGeometries(finalResult, new File(shapeFileOut), "Polygon");
//    // finalResult.stream().forEach(p->System.out.println(p));
//    // ShapefileWriter.write(featCollOut, shapeFileOut, CRS.decode("EPSG:2154"));
//  }
  

  private double maximalArea, maximalWidth, roadWidth;
  Polygon polygonInit;
  SimpleFeatureCollection buildings;
  SimpleFeatureCollection roads;
  // This line represents the exterior of an urban island (it serves to determine
  // if a parcel has road access)
  private List<LineString> ext = null;
  
  /**
   * Flag decomposition algorithm
   * 
   * @param p
   *          the initial polygon to decompose
   * @param buildings
   *          the buildings that will constraint the possibility of adding a road
   * @param maximalArea
   *          the maximalArea for a parcel
   * @param maximalWidth
   *          the maximal width
   * @param roadWidth
   *          the road width
   */
  public FlagParcelDecomposition(Polygon p, SimpleFeatureCollection buildings, double maximalArea, double maximalWidth, double roadWidth) {
    super();
    this.maximalArea = maximalArea;
    this.maximalWidth = maximalWidth;
    this.polygonInit = p;
    this.buildings = buildings;
    this.roadWidth = roadWidth;
  }

  /**
   * Flag decomposition algorithm
   * 
   * @param p
   *          the initial polygon to decompose
   * @param buildings
   *          the buildings that will constraint the possibility of adding a road
   * @param maximalArea
   *          the maximalArea for a parcel
   * @param maximalWidth
   *          the maximal width
   * @param roadWidth
   *          the road width
   * @param isLandExterior
   *          the exterior of this island to assess road access
   */
  public FlagParcelDecomposition(Polygon p, SimpleFeatureCollection buildings, double maximalArea, double maximalWidth, double roadWidth, List<LineString> islandExterior) {
    super();
    this.maximalArea = maximalArea;
    this.maximalWidth = maximalWidth;
    this.polygonInit = p;
    this.buildings = buildings;
    this.roadWidth = roadWidth;
    this.setExt(islandExterior);
  }

  /**
   * Flag decomposition algorithm
   * 
   * @param p
   *          the initial polygon to decompose
   * @param buildings
   *          the buildings that will constraint the possibility of adding a road
   * @param maximalArea
   *          the maximalArea for a parcel
   * @param maximalWidth
   *          the maximal width
   * @param roadWidth
   *          the road width
   * @param isLandExterior
   *          the exterior of this island to assess road access
   */
  public FlagParcelDecomposition(Polygon p, SimpleFeatureCollection buildings, SimpleFeatureCollection roads, double maximalArea, double maximalWidth, double roadWidth, List<LineString> islandExterior) {
    super();
    this.maximalArea = maximalArea;
    this.maximalWidth = maximalWidth;
    this.polygonInit = p;
    this.buildings = buildings;
    this.roads = roads;
    this.roadWidth = roadWidth;
    this.setExt(islandExterior);
  }
  
  /**
   * The decomposition method
   * 
   * @return List of parcels
   * @throws Exception
   */
  public List<Polygon> decompParcel(double noise) throws Exception {
    return decompParcel(this.polygonInit, noise);
  }

  /**
   * The core algorithm
   * 
   * @param p
   * @return the flag cut parcel if possible. The input parcel otherwise
   * @throws Exception
   */
  private List<Polygon> decompParcel(Polygon p, double noise) throws Exception {
    double area = p.getArea();
    double frontSideWidth = this.frontSideWidth(p);
    // End test condition
    if (this.endCondition(area, frontSideWidth)) {
      return Collections.singletonList(p);
    }
    // Determination of splitting polygon (it is a splitting line in the article)
    List<Polygon> splittingPolygon = OBBBlockDecomposition.computeSplittingPolygon(p, this.getExt(), true, noise,0.0,0,0.0,0,0);
    // Split into polygon
    List<Polygon> splitPolygon = OBBBlockDecomposition.split(p, splittingPolygon);
    long nbNoRoadAccess = splitPolygon.stream().filter(x -> !hasRoadAccess(x)).count();
    // If a parcel has no road access, there is a probability to make a perpendicular split
    List<Polygon> result = new ArrayList<>();
    if (nbNoRoadAccess != 0) {
      Pair<List<Polygon>, List<Polygon>> polGeneratedParcel = generateFlagParcel(splitPolygon);
      splitPolygon = polGeneratedParcel.getLeft();
      result.addAll(polGeneratedParcel.getRight());
	  }
    // All split polygons are split and results added to the output
    for (Polygon pol : splitPolygon) {
      // System.out.println("---" + pol.area());
      result.addAll(decompParcel(pol, noise));
    }
    return result;
  }

  private List<MultiLineString> regroupLineStrings(List<LineString> lineStrings) {
    List<MultiLineString> curvesOutput = new ArrayList<>();
    while (!lineStrings.isEmpty()) {
      LineString currentLineString = (LineString) lineStrings.remove(0);
      List<LineString> currentMultiCurve = new ArrayList<>();
      currentMultiCurve.add(currentLineString);
      Geometry buffer = currentLineString.buffer(0.1);
      for (int i = 0; i < lineStrings.size(); i++) {
        if (buffer.intersects(lineStrings.get(i))) {
          // Adding line in MultiCurve
          currentMultiCurve.add(lineStrings.remove(i));
          i = -1;
          // Updating the buffer
          buffer = getListAsGeom(currentMultiCurve).buffer(0.1);
        }
      }
      curvesOutput.add(getListAsGeom(currentMultiCurve));
    }
    return curvesOutput;
  }

  /**
   * Generate flag parcels: check if parcels have access to road and if not, try to generate a road throught other parcels
   * @param splittedPolygon list of polygon split with the OBB method
   * @return The output is a pair of two elements:<ul>
   *  <li> the left one contains parcel with an initial road access and may continue to be decomposed</li>
   *  <li> the right one contains parcel with added road access </li>
   *  </ul>
   */
  private Pair<List<Polygon>, List<Polygon>> generateFlagParcel(List<Polygon> splittedPolygon) {
//    splittedPolygon.stream().forEach(p -> System.out.println(p));

    List<Polygon> left = new ArrayList<>();
    List<Polygon> right = new ArrayList<>();

    // We get the two geometries with and without road access
    List<Polygon> lPolygonWithRoadAccess = splittedPolygon.stream().filter(x -> hasRoadAccess(x)).collect(Collectors.toList());
    List<Polygon> lPolygonWithNoRoadAccess = splittedPolygon.stream().filter(x -> !hasRoadAccess(x)).collect(Collectors.toList());
//    System.out.println("lPolygonWithNoRoadAccess");
//    lPolygonWithNoRoadAccess.stream().forEach(p -> System.out.println(p));

    bouclepoly: for (Polygon currentPoly : lPolygonWithNoRoadAccess) {
      List<Pair<MultiLineString, Polygon>> listMap = generateCandidateForCreatingRoad(currentPoly, lPolygonWithRoadAccess);
      // We order the proposition according to the length (we will try at first to build the road on the shortest side
      listMap.sort(new Comparator<Pair<MultiLineString, Polygon>>() {
        @Override
        public int compare(Pair<MultiLineString, Polygon> o1, Pair<MultiLineString, Polygon> o2) {
          return Double.compare(o1.getKey().getLength(), o2.getKey().getLength());
        }
      });

      boucleside: for (Pair<MultiLineString, Polygon> side : listMap) {
        // The geometry road
        Geometry road = side.getKey().buffer(this.roadWidth);
        Polygon polygon = side.getValue();
//        System.out.println("ROAD");
//        System.out.println(road);
        // The road intersects a building on the property, we do not keep it
        if (!Util.select(Collec.snapDatas(this.buildings,this.polygonInit.buffer(-0.5)), road).isEmpty()) {
//           System.out.println("Building case : " + this.polygonInit);
          continue;
        }
        try {
          Geometry geomPol1 = getDifference(polygon, road);
          Geometry geomPol2 = getIntersection(getUnion(currentPoly, road), getUnion(currentPoly,polygon));
          // The first geometry is the polygon with road access and a remove of the geometry
//        Geometry geomPol1 = null;
//        Geometry roadToAdd = null;
//        try {
//          Pair<Geometry,Geometry> intersectionDifference = getIntersectionDifference(side.getValue(), road);
//          roadToAdd = intersectionDifference.getLeft();
//          geomPol1 = intersectionDifference.getRight();
//        } catch (Exception e) {
//          e.printStackTrace();
//        }
//        Geometry geomPol2;
//        try {
//          geomPol2 = getUnion(currentPoly, roadToAdd);
//        } catch (Exception e) {
//          e.printStackTrace();
//          geomPol2 = currentPoly.union(roadToAdd.buffer(0.01)).buffer(0.0);
//        }

        // It might be a multi polygon so we remove the small area <
        List<Polygon> lPolygonsOut1 = Util.getPolygons(geomPol1);
        lPolygonsOut1 = lPolygonsOut1.stream().filter(x -> x.getArea() > TOO_SMALL_PARCEL_AREA).collect(Collectors.toList());

        List<Polygon> lPolygonsOut2 = Util.getPolygons(geomPol2);
        lPolygonsOut2 = lPolygonsOut2.stream().filter(x -> x.getArea() > TOO_SMALL_PARCEL_AREA).collect(Collectors.toList());
//        System.out.println("lPolygonsOut1");
//        lPolygonsOut1.stream().forEach(p -> System.out.println(p));
//        System.out.println("lPolygonsOut2");
//        lPolygonsOut2.stream().forEach(p -> System.out.println(p));

        // We check if there is a road acces for all, if not we abort
        for (Polygon pol : lPolygonsOut1) {
          if (!hasRoadAccess(pol)) {
            System.out.println("Road access is missing for the parcel that used to have a road access ; polyinit : " + this.polygonInit);
            System.out.println("Current polyg : " + pol);
            continue boucleside;
          }
        }
        for (Polygon pol : lPolygonsOut2) {
          if (!hasRoadAccess(pol)) {
            System.out.println("Road access is missing for the densified parcel ; polyinit : " + this.polygonInit);
            System.out.println("Current polyg : " + pol);
            continue boucleside;
          }
        }

        // We directly add the result from polygon 2 to the results
        right.addAll(lPolygonsOut2);
        
        // We update the geometry of the first polygon
        lPolygonWithRoadAccess.remove(side.getValue());
        lPolygonWithRoadAccess.addAll(lPolygonsOut1);

        // We go to the next polygon
        continue bouclepoly;
        } catch (Exception e) {
          e.printStackTrace();
        }
      }
      // System.out.println("I am empty"); generateFlagParcel(splittedPolygon);
      // We have added nothing if we are here, we kept the initial polygon
      right.add(currentPoly);
    }
    // We add the polygon with road access
    left.addAll(lPolygonWithRoadAccess);
    return new ImmutablePair<List<Polygon>, List<Polygon>>(left, right);
  }

  private Geometry getDifference(Geometry a, Geometry b) throws Exception {
    PrecisionModel pm = new PrecisionModel(100);
    Geometry jtsGeomA = GeometryPrecisionReducer.reduce(a, pm);
    Geometry jtsGeomB = GeometryPrecisionReducer.reduce(b, pm);
    return FeaturePolygonizer.getDifference(new ArrayList<Geometry>(Arrays.asList(jtsGeomA)), new ArrayList<Geometry>(Arrays.asList(jtsGeomB)));
  }

  @SuppressWarnings("unused")
private Pair<Geometry,Geometry> getIntersectionDifference(Geometry a, Geometry b) throws Exception {
    PrecisionModel pm = new PrecisionModel(100);
    Geometry jtsGeomA = GeometryPrecisionReducer.reduce(a, pm);
    Geometry jtsGeomB = GeometryPrecisionReducer.reduce(b, pm);
    return FeaturePolygonizer.getIntersectionDifference(new ArrayList<Geometry>(Arrays.asList(jtsGeomA)), new ArrayList<Geometry>(Arrays.asList(jtsGeomB)));
  }

  private Geometry getUnion(Geometry a, Geometry b) throws Exception {
    PrecisionModel pm = new PrecisionModel(100);
    Geometry jtsGeomA = GeometryPrecisionReducer.reduce(a, pm);
    Geometry jtsGeomB = GeometryPrecisionReducer.reduce(b, pm);
    return new CascadedPolygonUnion(new ArrayList<Geometry>(Arrays.asList(jtsGeomA, jtsGeomB))).union();
  }

  private Geometry getIntersection(Geometry a, Geometry b) throws Exception {
    try {
      return a.intersection(b);
    } catch (Exception e) {
//      GeometryFactory fact = new GeometryFactory();
      PrecisionModel pm = new PrecisionModel(100);
      Geometry jtsGeomA = GeometryPrecisionReducer.reduce(a, pm);
      Geometry jtsGeomB = GeometryPrecisionReducer.reduce(b, pm);
      return FeaturePolygonizer.getIntersection(new ArrayList<Geometry>(Arrays.asList(jtsGeomA, jtsGeomB)));
    }
  }

  private List<LineString> getSegments(LineString l) {
    List<LineString> result = new ArrayList<>();
    for (int i = 0; i < l.getNumPoints() - 1; i++) {
      result.add(l.getFactory().createLineString(new Coordinate[] { l.getCoordinateN(i), l.getCoordinateN(i + 1) }));
    }
    return result;
  }

  /**
   * Generate a list of candidate for creating roads. The pair is composed of a linestring that may be used to generate the road and the parcel on which it may be built
   * 
   * @param currentPoly
   * @param lPolygonWithRoadAcces
   * @return
   */
  private List<Pair<MultiLineString, Polygon>> generateCandidateForCreatingRoad(Polygon currentPoly, List<Polygon> lPolygonWithRoadAcces) {
    // A buffer to get the sides of the polygon with no road access
    Geometry buffer = currentPoly.buffer(0.1);
    // A map to know to which polygon belongs a potential road
    List<Pair<MultiLineString, Polygon>> listMap = new ArrayList<>();
    for (Polygon polyWithRoadAcces : lPolygonWithRoadAcces) {
      if (!polyWithRoadAcces.intersects(buffer)) {
        continue;
      }
      // We list the segments of the polygon with road access
      List<LineString> lExterior = getSegments(polyWithRoadAcces.getExteriorRing());
      // We keep the ones that does not intersect the buffer of new no-road-access polygon and the 
      List<LineString> lExteriorToKeep = lExterior.stream().filter(x -> (!buffer.contains(x)))
    		  .filter(x -> (!this.getExtAsGeom().buffer(0.1).contains(x) && !isRoadPolygonIntersectsLine(roads,x)))
          .collect(Collectors.toList());
      // We regroup the lines according to their connectivity
      List<MultiLineString> sides = this.regroupLineStrings(lExteriorToKeep);
      // We add elements to list the correspondance between pears
      sides.stream().forEach(x -> listMap.add(new ImmutablePair<>(x, polyWithRoadAcces)));
    }
    return listMap;
  }

  /**
   * End condition : either the area is below a threshold or width to road
   * 
   * @param area
   * @param frontSideWidth
   * @return
   */
  private boolean endCondition(double area, double frontSideWidth) {
	return (area <= maximalArea) || ((frontSideWidth <= maximalWidth) && (frontSideWidth != 0.0));
  }

  /**
   * Determine the width of the parcel on road
   * 
   * @param p
   * @return
   */
  private double frontSideWidth(Polygon p) {
    Geometry geom = p.buffer(1).intersection(this.getExtAsGeom());
    if (geom == null) {
      geom = p.buffer(5).intersection(this.getExtAsGeom());
    }
    if (geom == null) {
      System.out.println("Cannot process to intersection between");
      System.out.println(p.toString());
      System.out.println(this.getExt().toString());
      return 0;
    }
    return geom.getLength();
  }
  
  public boolean hasRoadAccess(Polygon poly){
	return isParcelHasRoadAccess(poly, roads, poly.getFactory().createMultiLineString(ext.toArray(new LineString[ext.size()])));
  }
  
//  public boolean hasRoadAccess(Polygon poly) {
//	    return (poly.intersects(getExtAsGeom().buffer(0.5)));
//	  }

  public MultiLineString getListAsGeom(List<LineString> list) {
    return getListAsGeom(list, this.polygonInit);
  }

  public static MultiLineString getListAsGeom(List<LineString> list, Polygon polygon) {
	    return polygon.getFactory().createMultiLineString(list.toArray(new LineString[list.size()]));
  }
  
	public static boolean isRoadPolygonIntersectsLine(SimpleFeatureCollection roads, LineString ls) {
		return roads != null && Geom.unionGeom(getRoadPolygon(roads)).contains(ls);
	}
	
  /**
   * Get a list of the surrounding buffered road segments.
   * 
   * The buffer length is calculated with an attribute field. The default name of the field is <i>LARGEUR</i> and can be set with the {@link #setWidthFieldAttribute(String)} method. 
   * If no field is found, a default value of 7.5 meters is used (this default value can be set with the {@link #setDefaultWidthRoad(double)} method). 
   * @param roads collection of road
   * @return
   */
	public static List<Geometry> getRoadPolygon(SimpleFeatureCollection roads) {
		// List<Geometry> roadGeom = Arrays.stream(Collec.snapDatas(roads, poly.buffer(5)).toArray(new SimpleFeature[0]))
		// .map(g -> ((Geometry) g.getDefaultGeometry()).buffer((double) g.getAttribute("LARGEUR"))).collect(Collectors.toList());
		List<Geometry> roadGeom = new ArrayList<Geometry>();
		SimpleFeatureIterator roadSnapIt = roads.features();
		try {
			while (roadSnapIt.hasNext()) {
				SimpleFeature feat = roadSnapIt.next();
				roadGeom.add(((Geometry) feat.getDefaultGeometry())
						.buffer((Collec.isCollecContainsAttribute(roads, widthFieldAttribute) ? (double) feat.getAttribute(widthFieldAttribute) + 2.5
								: defaultWidthRoad)));
			}
		} catch (Exception problem) {
			problem.printStackTrace();
		} finally {
			roadSnapIt.close();
		}
		return roadGeom;
  }
	
	/**
	 * Indicate if the given polygon is near the {@link org.locationtech.jts.geom.Polygon#getExteriorRing() shell} of a given Polygon object. This object is the islandExterior
	 * argument out of {@link #FlagParcelDecomposition(Polygon, SimpleFeatureCollection, double, double, double, List) the FlagParcelDecomposition constructor} or if not set, the
	 * bounds of the {@link #polygonInit initial polygon}.
	 * 
	 * If no roads have been found and a road shapefile has been set, we look if a road shapefile has been set and if the given road is nearby
	 * 
	 * @param poly
	 * @param geometry
	 * @return
	 * @throws Exception
	 * @throws IOException
	 */
	public static boolean isParcelHasRoadAccess(Polygon poly, SimpleFeatureCollection roads, MultiLineString ext) {
//		if (poly.intersects((((Polygon) ext).getExteriorRing()).buffer(0.5))) {
		if (poly.intersects(ext.buffer(0.5))) {
			return true;
		}
		// System.out.println(Geom.unionGeom(getRoadPolygon(roads)));
		if (roads != null && poly.intersects(Geom.unionGeom(getRoadPolygon(roads)))) {
			return true;
		}
		return false;
	}
  
  /**
   * Get the islet external perimeter
   * @return
   */
  public MultiLineString getExtAsGeom() {
    return getListAsGeom(this.getExt());
  }

  public List<LineString> getExt() {
    if (ext == null) {
      generateExt();
    }
    return ext;
  }

  public void setExt(List<LineString> ext) {
    this.ext = ext;
  }

	/**
	 * We generate an exterior with the studied polygon itself if no exterior islet has been set
	 *   
	 */
  private void generateExt() {
    // FIXME this code doesn't change a thing? If it would (with the use of setExt()), the road could be generated to the exterior of the polygon, possibly leading to nowhere? 
	  this.polygonInit.getFactory().createMultiLineString(new LineString[] { this.polygonInit.getExteriorRing() });
  }

public static String getWidthFieldAttribute() {
	return widthFieldAttribute;
}

public static void setWidthFieldAttribute(String widthFieldAttribute) {
	FlagParcelDecomposition.widthFieldAttribute = widthFieldAttribute;
}

public static double getDefaultWidthRoad() {
	return defaultWidthRoad;
}

public static void setDefaultWidthRoad(double defaultWidthRoad) {
	FlagParcelDecomposition.defaultWidthRoad = defaultWidthRoad;
}

}
