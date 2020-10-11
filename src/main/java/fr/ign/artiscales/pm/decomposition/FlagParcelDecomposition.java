package fr.ign.artiscales.pm.decomposition;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.geotools.data.DataStore;
import org.geotools.data.DataUtilities;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.MultiLineString;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.PrecisionModel;
import org.locationtech.jts.operation.union.CascadedPolygonUnion;
import org.locationtech.jts.precision.GeometryPrecisionReducer;
import org.opengis.feature.simple.SimpleFeature;

import fr.ign.artiscales.pm.parcelFunction.ParcelState;
import fr.ign.artiscales.tools.FeaturePolygonizer;
import fr.ign.artiscales.tools.geoToolsFunctions.Schemas;
import fr.ign.artiscales.tools.geoToolsFunctions.vectors.Collec;
import fr.ign.artiscales.tools.geoToolsFunctions.vectors.Geom;
import fr.ign.artiscales.tools.geoToolsFunctions.vectors.Geopackages;
import fr.ign.artiscales.tools.geoToolsFunctions.vectors.geom.Lines;
import fr.ign.artiscales.tools.geoToolsFunctions.vectors.geom.Polygons;

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
  
//	 public static void main(String[] args) throws Exception {
//	 /////////////////////////
//	 //////// try the generateFlagSplitedParcels method
//		/////////////////////////
//		long start = System.currentTimeMillis();
//		File rootFolder = new File("src/main/resources/GeneralTest/");
//
//		// Input 1/ the input parcels to split
//		File inputShapeFile = new File(rootFolder, "parcel.gpkg");
//		// Input 2 : the buildings that mustnt intersects the allowed roads (facultatif)
//		File inputBuildingFile = new File(rootFolder, "building.gpkg");
//		// Input 4 (facultative) : a road Geopacakge (it can be used to check road access
//		// if this is better than characerizing road as an absence of parcel)
//		File inputRoad = new File(rootFolder, "road.gpkg");
//		File zoningFile = new File(rootFolder, "zoning.gpkg");
//
//		FilterFactory2 ff = CommonFactoryFinder.getFilterFactory2();
//		ShapefileDataStore sds = new ShapefileDataStore(inputShapeFile.toURI().toURL());
//		SimpleFeatureCollection parcels = MarkParcelAttributeFromPosition.markParcelIntersectGenericZoningType(sds.getFeatureSource().getFeatures(),"U",zoningFile);
//		SimpleFeatureCollection islet = CityGeneration.createUrbanIslet(parcels);
//		try (SimpleFeatureIterator it = parcels.features()) {
//			while (it.hasNext()) {
//				SimpleFeature feat = it.next();
//				if (feat.getAttribute(MarkParcelAttributeFromPosition.getMarkFieldName()) != null
//						&& (int) feat.getAttribute(MarkParcelAttributeFromPosition.getMarkFieldName()) == 1) {
//					generateFlagSplitedParcels(feat, Collec.fromPolygonSFCtoListRingLines(islet.subCollection(ff.bbox(
//							ff.property(feat.getFeatureType().getGeometryDescriptor().getLocalName()), feat.getBounds()))), 0, tmpFolder, inputBuildingFile, inputRoad, 400.0, 15.0, 3.0, false, null);
//				}
//			}
//		} catch (Exception problem) {
//			problem.printStackTrace();
//		}
//		sds.dispose();
//		System.out.println("time : " + (System.currentTimeMillis() - start));
//	}

	public static SimpleFeatureCollection generateFlagSplitedParcels(SimpleFeature feat, List<LineString> extLines, double harmonyCoeff, double noise,
			File buildingFile, Double maximalAreaSplitParcel, Double maximalWidthSplitParcel, Double lenDriveway) throws IOException {
		return generateFlagSplitedParcels(feat, extLines, harmonyCoeff, noise, buildingFile, null, maximalAreaSplitParcel, maximalWidthSplitParcel,
				lenDriveway, null);
	}

	/**
	 * Main way to access to the flag parcel split algorithm. 
	 * @param feat
	 * @param extLines
	 * @param harmonyCoeff
	 * @param noise
	 * @param buildingFile
	 * @param roadFile
	 * @param maximalAreaSplitParcel
	 * @param maximalWidthSplitParcel
	 * @param lenDriveway
	 * @param exclusionZone
	 * @throws IOException
	 */
	public static SimpleFeatureCollection generateFlagSplitedParcels(SimpleFeature feat, List<LineString> extLines, double harmonyCoeff, double noise,
			File buildingFile, File roadFile, Double maximalAreaSplitParcel, Double maximalWidthSplitParcel, Double lenDriveway,
			Geometry exclusionZone) throws IOException {
		DataStore buildingDS = Geopackages.getDataStore(buildingFile);
		Polygon parcelGeom = Polygons.getPolygon((Geometry) feat.getDefaultGeometry());
		// as the road Geopacakge can be left as null, we differ the FlagParcelDecomposition constructor
		FlagParcelDecomposition fpd;
		if (roadFile != null && roadFile.exists()) {
			DataStore roadSDS = Geopackages.getDataStore(roadFile);
			Geometry selectionBuffer = parcelGeom.buffer(10);
			fpd = new FlagParcelDecomposition(parcelGeom,
					Collec.selectIntersection(buildingDS.getFeatureSource(buildingDS.getTypeNames()[0]).getFeatures(), selectionBuffer),
					DataUtilities.collection(
							Collec.selectIntersection(roadSDS.getFeatureSource(roadSDS.getTypeNames()[0]).getFeatures(), selectionBuffer)),
					maximalAreaSplitParcel, maximalWidthSplitParcel, lenDriveway, extLines, exclusionZone);
			roadSDS.dispose();
		} else
			fpd = new FlagParcelDecomposition(parcelGeom,
					Collec.selectIntersection(buildingDS.getFeatureSource(buildingDS.getTypeNames()[0]).getFeatures(),
							((Geometry) feat.getDefaultGeometry()).buffer(10)),
					maximalAreaSplitParcel, maximalWidthSplitParcel, lenDriveway, extLines, exclusionZone);
		List<Polygon> decomp = fpd.decompParcel(harmonyCoeff, noise);
		buildingDS.dispose();
		return Geom.geomsToCollec(decomp, Schemas.getBasicSchemaMultiPolygon("geom"));
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
   */
  public List<Polygon> decompParcel(double harmonyCoeff, double noise)  {
    return decompParcel(this.polygonInit, harmonyCoeff, noise);
  }

  /**
   * The core algorithm
   * 
   * @param p
   * @return the flag cut parcel if possible. The input parcel otherwise
   */
  private List<Polygon> decompParcel(Polygon p, double harmonyCoeff, double noise)  {
    // End test condition
    if (this.endCondition(p.getArea(), this.frontSideWidth(p))) 
      return Collections.singletonList(p);
    // Determination of splitting polygon (it is a splitting line in the article)
    List<Polygon> splittingPolygon = OBBBlockDecomposition.computeSplittingPolygon(p, this.getExt(), true, harmonyCoeff, noise, 0.0,0,0.0,0,0);
    // Split into polygon 
    List<Polygon> splitPolygon = OBBBlockDecomposition.split(p, splittingPolygon);

	// If a parcel has no road access, there is a probability to make a flag split 
	List<Polygon> result = new ArrayList<>();
	if (splitPolygon.stream().filter(x -> !hasRoadAccess(x)).count() != 0) {
		Pair<List<Polygon>, List<Polygon>> polGeneratedParcel = generateFlagParcel(splitPolygon);
		// We check if both parcels have road access, if false we abort the decomposition (that may be useless here...)
		for (Polygon pol1 : polGeneratedParcel.getLeft())
			for (Polygon pol2 : polGeneratedParcel.getRight())
				if (!hasRoadAccess(pol2) || !hasRoadAccess(pol1))
					return Collections.singletonList(p);
		splitPolygon = polGeneratedParcel.getLeft();
		result.addAll(polGeneratedParcel.getRight());
	}
	// All split polygons are split and results added to the output
    for (Polygon pol : splitPolygon) 
      result.addAll(decompParcel(pol, harmonyCoeff, noise));
    return result;
  }

  /**
   * Generate flag parcels: check if parcels have access to road and if not, try to generate a road throught other parcels
   * @param splittedPolygon list of polygon split with the OBB method
   * @return The output is a pair of two elements:<ul>
   *  <li> the left one contains parcel with an initial road access and may continue to be decomposed</li>
   *  <li> the right one contains parcel with added road access</li>
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
        if (!Collec.selectIntersection(Collec.selectIntersection(this.buildings,this.polygonInit.buffer(-0.5)), road).isEmpty()) 
          continue;
		try {
			// The first geometry is the polygon with road access and a remove of the geometry
			Geometry geomPol1 = getDifference(polygon, road);
			Geometry geomPol2 = getIntersection(getUnion(currentPoly, road), getUnion(currentPoly, polygon));
			// It might be a multi polygon so we remove the small area <
			List<Polygon> lPolygonsOut1 = Polygons.getPolygons(geomPol1);
			lPolygonsOut1 = lPolygonsOut1.stream().filter(x -> x.getArea() > TOO_SMALL_PARCEL_AREA).collect(Collectors.toList());

			List<Polygon> lPolygonsOut2 = Polygons.getPolygons(geomPol2);
			lPolygonsOut2 = lPolygonsOut2.stream().filter(x -> x.getArea() > TOO_SMALL_PARCEL_AREA).collect(Collectors.toList());

			// We check if there is a road acces for all, if not we abort
			for (Polygon pol : lPolygonsOut1)
				if (!hasRoadAccess(pol))
					continue boucleside;
			for (Polygon pol : lPolygonsOut2)
				if (!hasRoadAccess(pol))
					continue boucleside;
			
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
  private Geometry getDifference(Geometry a, Geometry b) {
    PrecisionModel pm = new PrecisionModel(100);
    Geometry jtsGeomA = GeometryPrecisionReducer.reduce(a, pm);
    Geometry jtsGeomB = GeometryPrecisionReducer.reduce(b, pm);
    return FeaturePolygonizer.getDifference(new ArrayList<Geometry>(Arrays.asList(jtsGeomA)), new ArrayList<Geometry>(Arrays.asList(jtsGeomB)));
  }

  @SuppressWarnings("unused")
private Pair<Geometry,Geometry> getIntersectionDifference(Geometry a, Geometry b) {
    PrecisionModel pm = new PrecisionModel(100);
    Geometry jtsGeomA = GeometryPrecisionReducer.reduce(a, pm);
    Geometry jtsGeomB = GeometryPrecisionReducer.reduce(b, pm);
    return FeaturePolygonizer.getIntersectionDifference(new ArrayList<Geometry>(Arrays.asList(jtsGeomA)), new ArrayList<Geometry>(Arrays.asList(jtsGeomB)));
  }

  private Geometry getUnion(Geometry a, Geometry b) {
    PrecisionModel pm = new PrecisionModel(100);
    Geometry jtsGeomA = GeometryPrecisionReducer.reduce(a, pm);
    Geometry jtsGeomB = GeometryPrecisionReducer.reduce(b, pm);
    return new CascadedPolygonUnion(new ArrayList<Geometry>(Arrays.asList(jtsGeomA, jtsGeomB))).union();
  }

  private Geometry getIntersection(Geometry a, Geometry b) {
    try {
      return a.intersection(b);
    } catch (Exception e) {
      PrecisionModel pm = new PrecisionModel(100);
      Geometry jtsGeomA = GeometryPrecisionReducer.reduce(a, pm);
      Geometry jtsGeomB = GeometryPrecisionReducer.reduce(b, pm);
      return FeaturePolygonizer.getIntersection(new ArrayList<Geometry>(Arrays.asList(jtsGeomA, jtsGeomB)));
    }
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
	          buffer = getListLSAsMultiLS(currentMultiCurve).buffer(0.1);
	        }
	      }
	      curvesOutput.add(getListLSAsMultiLS(currentMultiCurve));
	    }
	    return curvesOutput;
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
		if (!polyWithRoadAcces.intersects(buffer))
			continue;
		// We list the segments of the polygon with road access and keep the ones that does not intersect the buffer of new no-road-access polygon and the
		// Then regroup the lines according to their connectivity. We finnaly add elements to list the correspondance between pears
		this.regroupLineStrings(Lines.getSegments(polyWithRoadAcces.getExteriorRing()).stream().filter(
				x -> (!buffer.contains(x)) && !getListLSAsMultiLS(this.getExt()).buffer(0.1).contains(x) && !isRoadPolygonIntersectsLine(roads, x))
				.collect(Collectors.toList())).stream().forEach(x -> listMap.add(new ImmutablePair<>(x, polyWithRoadAcces)));
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
	 * If no roads have been found and a road Geopacakge has been set, we look if a road Geopacakge has been set and if the given road is nearby
	 * 
	 * @param poly
	 */
 private boolean hasRoadAccess(Polygon poly){
	return ParcelState.isParcelHasRoadAccess(poly, roads, poly.getFactory().createMultiLineString(ext.toArray(new LineString[ext.size()])),exclusionZone);
  }

 private MultiLineString getListLSAsMultiLS(List<LineString> list) {
    return Lines.getListLineStringAsMultiLS(list, this.polygonInit.getFactory());
  }
  
	public static boolean isRoadPolygonIntersectsLine(SimpleFeatureCollection roads, LineString ls) {
		return roads != null && Geom.unionGeom(ParcelState.getRoadPolygon(roads)).contains(ls);
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
