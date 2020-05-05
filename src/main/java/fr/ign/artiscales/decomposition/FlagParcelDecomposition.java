package fr.ign.artiscales.decomposition;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.geotools.data.DataUtilities;
import org.geotools.data.shapefile.ShapefileDataStore;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.MultiLineString;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.PrecisionModel;
import org.locationtech.jts.operation.union.CascadedPolygonUnion;
import org.locationtech.jts.precision.GeometryPrecisionReducer;
import org.opengis.feature.simple.SimpleFeature;

import fr.ign.artiscales.parcelFunction.ParcelState;
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
  
	// public static void main(String[] args) throws Exception {
	// /////////////////////////
	// //////// try the generateFlagSplitedParcels method
	// /////////////////////////
	// File rootFolder = new File("src/main/resources/GeneralTest/");
	//
	// // Input 1/ the input parcelles to split
	// File inputShapeFile = new File("/tmp/marked.shp");
	// // Input 2 : the buildings that mustnt intersects the allowed roads (facultatif)
	// File inputBuildingFile = new File(rootFolder, "building.shp");
	// // Input 3 (facultative) : the exterior of the urban block (it serves to determiner the multicurve)
	// File inputUrbanBlock = new File(rootFolder, "islet.shp");
	// // Input 4 (facultative) : a road shapefile (it can be used to check road access if this is better than characerizing road as an absence of parcel)
	// File inputRoad = new File(rootFolder, "road.shp");
	//
	// File tmpFolder = new File("/tmp/");
	//
	// FilterFactory2 ff = CommonFactoryFinder.getFilterFactory2();
	// ShapefileDataStore sdsIlot = new ShapefileDataStore(inputUrbanBlock.toURI().toURL());
	// SimpleFeatureCollection collec = sdsIlot.getFeatureSource().getFeatures();
	// ShapefileDataStore sds = new ShapefileDataStore(inputShapeFile.toURI().toURL());
	// try (SimpleFeatureIterator it = sds.getFeatureSource().getFeatures().features()){
	// while (it.hasNext()) {
	// SimpleFeature feat = it.next();
	// List<LineString> lines = Collec.fromSFCtoListRingLines(
	// collec.subCollection(ff.bbox(ff.property(feat.getFeatureType().getGeometryDescriptor().getLocalName()), feat.getBounds())));
	// if (feat.getAttribute(MarkParcelAttributeFromPosition.getMarkFieldName()) != null
	// && (int) feat.getAttribute(MarkParcelAttributeFromPosition.getMarkFieldName()) == 1) {
	// generateFlagSplitedParcels(feat, lines, tmpFolder, inputBuildingFile, inputRoad, 400.0, 15.0, 3.0, false, null);
	// }
	// }
	// } catch (Exception problem) {
	// problem.printStackTrace();
	// }
	// sds.dispose();
	// sdsIlot.dispose();
	// }

	public static SimpleFeatureCollection generateFlagSplitedParcels(SimpleFeature feat, List<LineString> extLines, double noise, File tmpFolder, File buildingFile,
			Double maximalAreaSplitParcel, Double maximalWidthSplitParcel, Double lenDriveway, boolean allowIsolatedParcel) throws Exception {
		return generateFlagSplitedParcels(feat, extLines, noise, tmpFolder, buildingFile, null, maximalAreaSplitParcel, maximalWidthSplitParcel, lenDriveway,
				allowIsolatedParcel, null);
	}

	/**
	 * Main way to access to the flag parcel split algorithm. 
	 * @param feat
	 * @param extLines
	 * @param tmpFolder
	 * @param buildingFile
	 * @param roadFile
	 * @param maximalAreaSplitParcel
	 * @param maximalWidthSplitParcel
	 * @param lenDriveway
	 * @param allowIsolatedParcel
	 * @param exclusionZone
	 * @return
	 * @throws Exception
	 */
	public static SimpleFeatureCollection generateFlagSplitedParcels(SimpleFeature feat, List<LineString> extLines, double noise, File tmpFolder, File buildingFile,
			File roadFile, Double maximalAreaSplitParcel, Double maximalWidthSplitParcel, Double lenDriveway, boolean allowIsolatedParcel,
			Geometry exclusionZone) throws Exception {
		ShapefileDataStore buildingDS = new ShapefileDataStore(buildingFile.toURI().toURL());
		List<Polygon> surfaces = Util.getPolygons((Geometry) feat.getDefaultGeometry());
		// as the road shapefile can be left as null, we differ the FlagParcelDecomposition constructor
		FlagParcelDecomposition fpd;
		if (roadFile != null && roadFile.exists()) {
			ShapefileDataStore roadSDS = new ShapefileDataStore(roadFile.toURI().toURL());
			Geometry geom = ((Geometry) feat.getDefaultGeometry()).buffer(10);
			fpd = new FlagParcelDecomposition(surfaces.get(0), Collec.snapDatas(buildingDS.getFeatureSource().getFeatures(), geom),
					Collec.snapDatas(roadSDS.getFeatureSource().getFeatures(), geom), maximalAreaSplitParcel, maximalWidthSplitParcel, lenDriveway,
					extLines, exclusionZone);
			roadSDS.dispose();
		} else {
			fpd = new FlagParcelDecomposition(surfaces.get(0),
					Collec.snapDatas(buildingDS.getFeatureSource().getFeatures(), ((Geometry) feat.getDefaultGeometry()).buffer(10)),
					maximalAreaSplitParcel, maximalWidthSplitParcel, lenDriveway, extLines, exclusionZone);
		}
		List<Polygon> decomp = fpd.decompParcel(noise);
		// if the size of the collection is 1, no flag cut has been done. We check if we can normal cut it, if allowed
		if (decomp.size() == 1 && allowIsolatedParcel) {
			System.out.println("normal decomp instead of flagg decomp allowed and done");
			return OBBBlockDecomposition.splitParcels(feat, maximalAreaSplitParcel, maximalWidthSplitParcel, 0.5, noise, extLines, 0, true, 99);
		}
		File fileOut = new File(tmpFolder, "tmp_split.shp");
		FeaturePolygonizer.saveGeometries(decomp, fileOut, "Polygon");
		ShapefileDataStore sds = new ShapefileDataStore(fileOut.toURI().toURL());
		SimpleFeatureCollection parcelOut = DataUtilities.collection(sds.getFeatureSource().getFeatures());
		sds.dispose();
		buildingDS.dispose();
		return parcelOut;
	}
  

  private double maximalArea, maximalWidth, drivewayWidth;
  Polygon polygonInit;
  SimpleFeatureCollection buildings;
  SimpleFeatureCollection roads;
  // This line represents the exterior of an urban island (it serves to determine
  // if a parcel has road access)
  private List<LineString> ext = null;
  private Geometry exclusionZone = null;
  
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
    this.drivewayWidth = roadWidth;
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
   * @param drivewayWidth
   *          the width of driveways
   * @param islandExterior
   *          the exterior of this island to assess road access
   */
  public FlagParcelDecomposition(Polygon p, SimpleFeatureCollection buildings, double maximalArea, double maximalWidth, double drivewayWidth, List<LineString> islandExterior, Geometry exclusionZone) {
    super();
    this.maximalArea = maximalArea;
    this.maximalWidth = maximalWidth;
    this.polygonInit = p;
    this.buildings = buildings;
    this.drivewayWidth = drivewayWidth;
    this.setExt(islandExterior);
    this.exclusionZone = exclusionZone;
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
   * @param drivewayWidth
   *          the width of driveways
   * @param islandExterior
   *          the exterior of this island to assess road access
   */
  public FlagParcelDecomposition(Polygon p, SimpleFeatureCollection buildings, SimpleFeatureCollection roads, double maximalArea, double maximalWidth, double drivewayWidth, List<LineString> islandExterior) {
    super();
    this.maximalArea = maximalArea;
    this.maximalWidth = maximalWidth;
    this.polygonInit = p;
    this.buildings = buildings;
    this.roads = roads;
    this.drivewayWidth = drivewayWidth;
    this.setExt(islandExterior);
  }
  
  	/**
	 * Flag decomposition algorithm
	 * 
	 * @param p
	 *            the initial polygon to decompose
	 * @param buildings
	 *            the buildings that will constraint the possibility of adding a road
	 * @param maximalArea
	 *            the maximalArea for a parcel
	 * @param maximalWidth
	 *            the maximal width
	 * @param drivewayWidth
	 *            the width of driveways
	 * @param islandExterior
	 *            the exterior of this island to assess road access
	 * @param exclusionZone
	 *            a zone to find roads from empty parcels area
	 */
  public FlagParcelDecomposition(Polygon p, SimpleFeatureCollection buildings, SimpleFeatureCollection roads, double maximalArea, double maximalWidth, double drivewayWidth, List<LineString> islandExterior, Geometry exclusionZone) {
    super();
    this.maximalArea = maximalArea;
    this.maximalWidth = maximalWidth;
    this.polygonInit = p;
    this.buildings = buildings;
    this.roads = roads;
    this.drivewayWidth = drivewayWidth;
    this.setExt(islandExterior);
    this.exclusionZone = exclusionZone;
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
    List<Polygon> splittingPolygon = OBBBlockDecomposition.computeSplittingPolygon(p, this.getExt(), true, noise, 0.0,0.0,0,0.0,0,0);
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
    List<Polygon> left = new ArrayList<>();
    List<Polygon> right = new ArrayList<>();

    // We get the two geometries with and without road access
    List<Polygon> lPolygonWithRoadAccess = splittedPolygon.stream().filter(x -> hasRoadAccess(x)).collect(Collectors.toList());
    List<Polygon> lPolygonWithNoRoadAccess = splittedPolygon.stream().filter(x -> !hasRoadAccess(x)).collect(Collectors.toList());

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
        Geometry road = side.getKey().buffer(this.drivewayWidth);
        Polygon polygon = side.getValue();
        // The road intersects a building on the property, we do not keep it
        if (!Util.select(Collec.snapDatas(this.buildings,this.polygonInit.buffer(-0.5)), road).isEmpty()) 
          continue;
        try {
        // The first geometry is the polygon with road access and a remove of the geometry
          Geometry geomPol1 = getDifference(polygon, road);
          Geometry geomPol2 = getIntersection(getUnion(currentPoly, road), getUnion(currentPoly,polygon));
        // It might be a multi polygon so we remove the small area <
        List<Polygon> lPolygonsOut1 = Util.getPolygons(geomPol1);
        lPolygonsOut1 = lPolygonsOut1.stream().filter(x -> x.getArea() > TOO_SMALL_PARCEL_AREA).collect(Collectors.toList());

        List<Polygon> lPolygonsOut2 = Util.getPolygons(geomPol2);
        lPolygonsOut2 = lPolygonsOut2.stream().filter(x -> x.getArea() > TOO_SMALL_PARCEL_AREA).collect(Collectors.toList());

        // We check if there is a road acces for all, if not we abort
        for (Polygon pol : lPolygonsOut1) {
          if (!hasRoadAccess(pol)) {
            continue boucleside;
          }
        }
        for (Polygon pol : lPolygonsOut2) {
          if (!hasRoadAccess(pol)) {
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
      // We have added nothing if we are here, we kept the initial polygon
      right.add(currentPoly);
    }
    // We add the polygon with road access
    left.addAll(lPolygonWithRoadAccess);
    return new ImmutablePair<List<Polygon>, List<Polygon>>(left, right);
  }

  //TODO move those functions to ArtiScales-Tools? 
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
       List<LineString> lExterior = Geom.getSegments(polyWithRoadAcces.getExteriorRing());
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
   * End condition : either the area is below a threshold or width to road (which is ultimately allowed to be 0). Goes to the {@link OBBBlockDecomposition} class 
   * 
   * @param area
   *            Area of the current parcel
   * @param frontSideWidth
   *            width of contact between road and parcel
   * @return true if the algorithm must stop
   */
  private boolean endCondition(double area, double frontSideWidth) {
	return OBBBlockDecomposition.endCondition(area, frontSideWidth, maximalArea, maximalWidth);
  }

	/**
	 * Determine the width of the parcel on road
	 * 
	 * @param p
	 *            input {@link Polygon}
	 * @return the length of contact between parcel and road
	 */
	private double frontSideWidth(Polygon p) {
		return ParcelState.getParcelFrontSideWidth(p, roads, ext);
	}
  
	/**
	 * Indicate if the given polygon is near the {@link org.locationtech.jts.geom.Polygon#getExteriorRing() shell} of a given Polygon object. This object is the islandExterior
	 * argument out of {@link #FlagParcelDecomposition(Polygon, SimpleFeatureCollection, double, double, double, List, Geometry)} the FlagParcelDecomposition constructor or if not
	 * set, the bounds of the {@link #polygonInit initial polygon}.
	 * 
	 * If no roads have been found and a road shapefile has been set, we look if a road shapefile has been set and if the given road is nearby
	 * 
	 * @param poly
	 */
 private boolean hasRoadAccess(Polygon poly){
	return ParcelState.isParcelHasRoadAccess(poly, roads, poly.getFactory().createMultiLineString(ext.toArray(new LineString[ext.size()])),exclusionZone);
  }

 private MultiLineString getListAsGeom(List<LineString> list) {
    return Geom.getListAsGeom(list, this.polygonInit.getFactory());
  }
  
	public static boolean isRoadPolygonIntersectsLine(SimpleFeatureCollection roads, LineString ls) {
		return roads != null && Geom.unionGeom(ParcelState.getRoadPolygon(roads)).contains(ls);
	}
	
  /**
   * Get the islet external perimeter
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
}
