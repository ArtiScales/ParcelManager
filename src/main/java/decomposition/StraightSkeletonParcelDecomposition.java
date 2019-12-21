package decomposition;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.math3.random.GaussianRandomGenerator;
import org.apache.commons.math3.random.MersenneTwister;
import org.apache.commons.math3.random.RandomGenerator;
import org.geotools.data.DataUtilities;
import org.geotools.data.FeatureWriter;
import org.geotools.data.FileDataStore;
import org.geotools.data.Transaction;
import org.geotools.data.shapefile.ShapefileDataStore;
import org.geotools.data.shapefile.ShapefileDataStoreFactory;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.feature.DefaultFeatureCollection;
import org.geotools.feature.SchemaException;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.locationtech.jts.algorithm.Orientation;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineSegment;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.PrecisionModel;
import org.locationtech.jts.geom.util.LineStringExtracter;
import org.locationtech.jts.linearref.LengthIndexedLine;
import org.locationtech.jts.operation.linemerge.LineMerger;
import org.locationtech.jts.operation.overlay.snap.GeometrySnapper;
import org.locationtech.jts.operation.polygonize.Polygonizer;
import org.locationtech.jts.operation.union.CascadedPolygonUnion;
import org.locationtech.jts.precision.GeometryPrecisionReducer;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;

import decomposition.analysis.FindObjectInDirection;
import decomposition.geom.Strip;
import decomposition.graph.Edge;
import decomposition.graph.Face;
import decomposition.graph.GraphElement;
import decomposition.graph.Node;
import fr.ign.cogit.FeaturePolygonizer;

/**
 * Re-implementation of block decomposition into parcels from :
 * 
 * Vanegas, C. A., Kelly, T., Weber, B., Halatsch, J., Aliaga, D. G., Müller, P., May 2012. Procedural generation of parcels in urban modeling. Comp. Graph. Forum 31 (2pt3).
 * 
 * Decomposition method by using straight skeleton
 * 
 * @author Mickael Brasebin
 *
 */
public class StraightSkeletonParcelDecomposition {

  //////////////////////////////////////////////////////
  // Input data parameters
  // Must be a double attribute
  public final static String NAME_ATT_IMPORTANCE = "length";
  // public final static String NAME_ATT_ROAD = "NOM_VOIE_G";
  public final static String NAME_ATT_ROAD = "n_sq_vo";

  //////////////////////////////////////////////////////

  // Indicate if a boundary of partial skeleton is at the border of input
  // block
  public final static String ATT_IS_INSIDE = "INSIDE";
  public final static int ARC_VALUE_INSIDE = 1;
  public final static int ARC_VALUE_OUTSIDE = 2;

  public final static String ATT_ROAD = "NOM_VOIE";

  public static String ATT_FACE_ID_STRIP = "ID_STRIP";

  // Indicate the importance of the neighbour road
  public static String ATT_IMPORTANCE = "IMPORTANCE";

  public static boolean DEBUG = true;
  public static String FOLDER_OUT_DEBUG = "/tmp/";

  /**
   * Main algorithm to process the algorithm
   * 
   * @param pol
   *          : polygon block that will be decomposed
   * @param roads
   *          : roads around the block polygon, may be empty or null. It determines some priority according to road importance.
   * @param maxDepth
   *          : maximal depth of a parcel
   * @param maxDistanceForNearestRoad
   *          : parameters that determine how far a road is considered from block exterior
   * @param minimalArea
   *          : minimal area of a parcel
   * @param minWidth
   *          : minimum width of a parcel
   * @param maxWidth
   *          : maximal width of a parcel
   * @param noiseParameter
   *          : standard deviation of width distribution beteween minWidth and mawWidthdetermineInteriorLineString
   * @param rng
   *          : Random generator
   * @return
   * @throws SchemaException
   */
  public static SimpleFeatureCollection runStraightSkeleton2(Polygon pol, SimpleFeatureCollection roads, double maxDepth, double maxDistanceForNearestRoad, double minimalArea,
      double minWidth, double maxWidth, double noiseParameter, RandomGenerator rng) throws SchemaException {
    System.out.println("------Begin decomposition with runStraightSkeleton method-----");
    System.out.println("------Partial skelton application-----");
    // Partial skeleton
    CampSkeleton cs = new CampSkeleton(pol, maxDepth);
    // Information is stored in getPoids method
    detectAndAnnotateRoadEdges(pol, cs.getGraph().getEdges());
    detectNeighbourdRoad(pol, cs.getGraph().getEdges(), roads, maxDistanceForNearestRoad);
    if (DEBUG) {
      System.out.println("------Saving for debug  ...-----");
      for (Edge a : cs.getGraph().getEdges()) {
        Face f1 = a.getRight();
        String str = "";
        if (f1 != null) {
          str = f1.toString();
        }
        Face f2 = a.getLeft();
        String str2 = "";
        if (f2 != null) {
          str2 = f2.toString();
        }
        // AttributeManager.addAttribute(a, "FD", str, "String");
        a.setAttribute("FD", str);
        // AttributeManager.addAttribute(a, "FG", str2, "String");
        a.setAttribute("FG", str2);
      }
      debugExport(cs.getGraph().getFaces(), "initialFaces", "Polygon");
      debugExport(cs.getGraph().getEdges(), "allArcs", "LineString");
      debugExport(cs.getInteriorEdges(), "interiorArcs", "LineString");
      debugExport(cs.getExteriorEdges(), "exteriorArcs", "LineString");
      debugExport(cs.getIncludedEdges(), "includedArcs", "LineString");
    }
    System.out.println("------Annotation of external edges-----");
    // Information is stored in getPoids method
    HashMap<String, List<Face>> llFace = detectStrip(cs.getGraph().getFaces(), pol, roads, maxDistanceForNearestRoad);
    if (DEBUG) {
      System.out.println("------Saving for debug  ...-----");
      System.out.println("llFace = " + llFace.size());
      llFace.keySet().stream().forEach(key -> System.out.println("\t" + key + " with " + llFace.get(key).size()));
      debugExport(cs.getGraph().getFaces(), "striproad", "Polygon");
    }
    List<List<Face>> stripFace = splittingInAdjacentStrip(llFace);
    if (DEBUG) {
      System.out.println("------Saving for debug  ...-----");
      List<Face> lf = new ArrayList<>();
      int count = 0;
      for (List<Face> lfTemp : stripFace) {
        for (Face fTemp : lfTemp) {
          lf.add(fTemp);
        }
        count++;
      }
      System.out.println("striproadCorrected " + count);
      debugExport(lf, "striproadCorrected", "Polygon");
    }
    System.out.println("------Fast strip cleaning...-----");
    stripFace = fastStripCleaning(stripFace, minimalArea);
    if (DEBUG) {
      System.out.println("------Saving for debug ...-----");
      debugExport(cs.getGraph().getFaces(), "fastStripCleaning", "Polygon");
    }
    List<LineString> interiorEdgesByStrip = detectInteriorEdges(stripFace);
    if (DEBUG) {
      System.out.println("------Saving for debug  ...-----");
      List<Edge> lf = new ArrayList<>();
      int count = 0;
      for (LineString line : interiorEdgesByStrip) {
        Edge temp = new Edge(null, null, line);
        System.out.println("interiorEdgesByStrip = " + line);
        temp.setAttribute(ATT_FACE_ID_STRIP, count);
        lf.add(temp);
        count++;
      }
      System.out.println("interiorEdgesByStrip = " + count);
      debugExport(lf, "interiorEdgesByStrip", "LineString");
    }
    HashMap<String, Coordinate> limitsPoints = interiorLimitPointsBetweenStrip(stripFace, pol);
    if (DEBUG) {
      System.out.println("------Saving for debug ...-----");
      List<Node> list = new ArrayList<>();
      for (String str : limitsPoints.keySet()) {
        Node temp = new Node(limitsPoints.get(str));
        // AttributeManager.addAttribute(feat, "inter", str, "String");
        temp.setAttribute("inter", str);
        list.add(temp);
      }
      debugExport(list, "interPoints", "Point");
    }
    System.out.println("------Fixing diagonal edges...-----");
    // 3 group of edges : interior/exterior/side
    List<List<LineString>> listOfLists = fixingDiagonal2(stripFace, limitsPoints);
    listOfLists.add(0, interiorEdgesByStrip);
    if (DEBUG) {
      System.out.println("------Saving for debug ...-----");
      List<Edge> list = new ArrayList<>();
      for (List<LineString> ls : listOfLists) {
        for (LineString lls : ls) {
          list.add(new Edge(null, null, lls));
        }
      }
      debugExport(list, "stepanotation5", "LineString");
    }
    return generateParcel(listOfLists, minWidth, maxWidth, noiseParameter, rng);
  }

  ////////////////////////////////////////
  ///////// V2 above
  ///////////////////////////////////////

  private static List<LineString> detectInteriorEdges(List<List<Face>> stripFace) {
    List<List<Edge>> lLLArc = new ArrayList<>();
    for (List<Face> lFtemp : stripFace) {
      List<Edge> currentList = new ArrayList<>();
      lLLArc.add(currentList);
      for (Face f : lFtemp) {
        for (Edge a : f.getEdges()) {
          if (Integer.parseInt(a.getAttribute(ATT_IS_INSIDE).toString()) == ARC_VALUE_OUTSIDE) {
            continue;
          }
          if (lFtemp.contains(a.getRight()) && lFtemp.contains(a.getLeft())) {
            continue;
          }
          if (currentList.contains(a)) {
            continue;
          }
          if (a.getLeft() != null && a.getRight() != null) {
            Object o = a.getLeft().getAttribute(ATT_FACE_ID_STRIP);
            Object o2 = a.getRight().getAttribute(ATT_FACE_ID_STRIP);
            // We do not keep limits between two adjacent strip
            if (o != null && o2 != null) {
              int id1 = Integer.parseInt(o.toString());
              int id2 = Integer.parseInt(o2.toString());
              if (Math.abs(id1 - id2) == 1) {
                continue;
              }
              if (Math.abs(id1 - id2) == (stripFace.size() - 1)) {
                continue;
              }
            }
          }
          currentList.add(a);
        }
      }
    }
    List<LineString> lsListOut = new ArrayList<>();
    // Merge intoLineString
    for (List<Edge> arcs : lLLArc) {
      if (!arcs.isEmpty()) {
        List<LineString> lsList = new ArrayList<>();
        for (Edge a : arcs) {
          lsList.add(a.getGeometry());
        }
        // LineString ls = Operateurs.union(lsList, 0.1);
        LineString ls = union(lsList);
        lsListOut.add(ls);
      }
    }
    return lsListOut;
  }

  private static LineString union(List<LineString> list) {
    if (list.isEmpty())
      return null;
    LineMerger merger = new LineMerger();
    list.forEach(l -> merger.add(l));
    return (LineString) merger.getMergedLineStrings().iterator().next();// FIXME we assume a lot here
  }

  private static Polygon polygonUnion(List<Polygon> list) {
    if (list.isEmpty())
      return null;
    List<Geometry> reducedList = list.stream().map(g -> GeometryPrecisionReducer.reduce(g, new PrecisionModel(100))).collect(Collectors.toList());
    return (Polygon) new CascadedPolygonUnion(reducedList).union();
  }

  private static Polygon polygonUnionWithoutHoles(List<Polygon> list) {
    Polygon union = polygonUnion(list);
    return union.getFactory().createPolygon(union.getExteriorRing().getCoordinates());
  }

  private static Polygon polygonDifference(List<Polygon> a, List<Polygon> b) {
//    Geometry reducedA = GeometryPrecisionReducer.reduce(a, new PrecisionModel(100));
//    Geometry reducedB = GeometryPrecisionReducer.reduce(a, new PrecisionModel(100));
    Geometry difference = FeaturePolygonizer.getDifference(a, b);
    List<Polygon> p = Util.getPolygons(difference);
    if (p.size() != 1) {
      System.out.println(p.size() + " polygons");
      p.forEach(pp->System.out.println(pp));
    }
    return p.get(0);
  }

  private static HashMap<String, Coordinate> interiorLimitPointsBetweenStrip(List<List<Face>> stripFace, Polygon pol) {
    LineString exterior = pol.getExteriorRing();
    HashMap<String, List<Edge>> mapLimitArcs = new HashMap<>();
    HashMap<String, Coordinate> mapInterPoint = new HashMap<>();
    for (List<Face> lFtemp : stripFace) {
      System.out.println("interiorLimitPointsBetweenStrip with " + lFtemp.size() + " faces");
      for (Face f : lFtemp) {
        List<Edge> lArcTemp = f.getEdges();
        System.out.println("interiorLimitPointsBetweenStrip Face " + f.getGeometry() + " with " + lArcTemp.size() + " edges");
        for (Edge a : lArcTemp) {
          if (Integer.parseInt(a.getAttribute(ATT_IS_INSIDE).toString()) == ARC_VALUE_OUTSIDE) {
            continue;
          }
          if (lFtemp.contains(a.getRight()) && lFtemp.contains(a.getLeft())) {
            continue;
          }
          if (a.getLeft() != null && a.getRight() != null) {
            Object o = a.getLeft().getAttribute(ATT_FACE_ID_STRIP);
            Object o2 = a.getRight().getAttribute(ATT_FACE_ID_STRIP);
            // We do not keep limits between two adjacent strip
            if (o != null && o2 != null) {
              int id1 = Integer.parseInt(o.toString());
              int id2 = Integer.parseInt(o2.toString());
              if ((Math.abs(id1 - id2) == 1) || (Math.abs(id1 - id2) == (stripFace.size() - 1))) {
                int idMin = Math.min(id1, id2);
                int idMax = Math.max(id1, id2);
                String id = idMin + "-" + idMax;
                List<Edge> lA = mapLimitArcs.get(id);
                if (lA == null) {
                  lA = new ArrayList<>();
                  System.out.println("mapLimitArcs: " + id);
                  mapLimitArcs.put(id, lA);
                }
                if (!lA.contains(a)) {
                  // AttributeManager.addAttribute(a, "TEMP_KEY", id, "String");
                  a.setAttribute("TEMP_KEY", id);
                  lA.add(a);
                }
                continue;
              }
            }
          }
        }
      }
    }
    List<LineString> lsListOut = new ArrayList<>();
    // Merge intoLineString
    System.out.println("mapLimitArcs : " + mapLimitArcs.size());
    for (String str : mapLimitArcs.keySet()) {
      List<LineString> lsList = new ArrayList<>();
      List<Edge> lA = mapLimitArcs.get(str);
      for (Edge a : lA) {
        lsList.add(a.getGeometry());
      }
      String key = lA.get(0).getAttribute("TEMP_KEY").toString();
      // LineString ls = Operateurs.union(lsList, 0.1);
      LineString ls = union(lsList);
      lsListOut.add(ls);
      Coordinate dp1 = ls.getCoordinateN(0);
      Coordinate dpLast = ls.getCoordinateN(ls.getNumPoints() - 1);
      Point p1 = ls.getFactory().createPoint(dp1);
      Point pLast = ls.getFactory().createPoint(dpLast);
      if (p1.distance(exterior) > pLast.distance(exterior)) {
        mapInterPoint.put(key, dp1);
      } else {
        mapInterPoint.put(key, dpLast);
      }
    }
    return mapInterPoint;
  }

  private static HashMap<String, List<Face>> detectStrip(List<Face> listFace, Polygon pol, SimpleFeatureCollection roads, double thresholdRoad) {
    HashMap<String, List<Face>> hashFaces = new HashMap<>();
    // For edge face
    for (Face f : listFace) {
      List<Edge> arcs = f.getEdges();
      String bestRoadName = "";
      double importance = 0;
      // For each arc we determine the nearest road
      Optional<Pair<String, Double>> option = arcs.stream().map(e -> new ImmutablePair<>(e, FindObjectInDirection.find(e.getGeometry(), pol, roads, thresholdRoad)))
          .filter(p -> p.right != null).max((p1, p2) -> new Double(p1.left.getGeometry().getLength()).compareTo(new Double(p2.left.getGeometry().getLength())))
          .map(p -> new ImmutablePair<>(p.right.map(o -> o.getAttribute(NAME_ATT_ROAD)), Optional.ofNullable(p.left.getAttribute(ATT_IMPORTANCE))))
          .map(p -> new ImmutablePair<>(p.left.orElse("").toString(), Double.parseDouble(p.right.orElse("0.0").toString())));
      if (option.isPresent()) {
        bestRoadName = option.get().getLeft();
        importance = option.get().getRight();
      }
      // double maxLength = Double.NEGATIVE_INFINITY;
      // for (Edge a : arcs) {
      // SimpleFeature feat = FindObjectInDirection.find(a.getGeometry(), pol, roads, thresholdRoad);
      // System.out.println("FindObjectInDirection " + feat);
      // if (feat == null) {
      // continue;
      // }
      // Object o = feat.getAttribute(NAME_ATT_ROAD);
      // String roadName = "";
      // if (o != null) {
      // roadName = o.toString();
      // }
      // double lengthTemp = a.getGeometry().getLength();
      // if (lengthTemp > maxLength) {
      // maxLength = lengthTemp;
      // bestRoadName = roadName;
      // Object otemp = a.getAttribute(ATT_IMPORTANCE);
      // if (otemp != null) {
      // importance = Double.parseDouble(otemp.toString());
      // }
      // }
      // }
      // AttributeManager.addAttribute(f, ATT_IMPORTANCE, importance, "Double");
      f.setAttribute(ATT_IMPORTANCE, importance);
      // AttributeManager.addAttribute(f, ATT_FACE_ID_STRIP, bestRoadName, "String");
      f.setAttribute(ATT_FACE_ID_STRIP, bestRoadName);
      System.out.println("bestRoadName = " + bestRoadName + " importance = " + importance);
      List<Face> lFaces = hashFaces.get(bestRoadName);
      if (lFaces == null) {
        lFaces = new ArrayList<>();
        hashFaces.put(bestRoadName, lFaces);
      }
      lFaces.add(f);
    }
    // ... and group them according to roads name
    return hashFaces;
  }

  private static List<List<Face>> splittingInAdjacentStrip(HashMap<String, List<Face>> llFace) {
    List<List<Face>> lFOut = new ArrayList<>();
    for (List<Face> lF : llFace.values()) {
      List<List<Face>> lFOutTemp = createGroup(lF, 0);
      System.out.println("NB Group : " + lFOutTemp.size());
      for (List<Face> lf : lFOutTemp) {
        lFOut.add(lf);
      }
    }
    // Adding id strip attribute
    int count = 0;
    for (List<Face> lfTemp : lFOut) {
      for (Face fTemp : lfTemp) {
        // AttributeManager.addAttribute(fTemp, ATT_FACE_ID_STRIP, count + "", "String");
        fTemp.setAttribute(ATT_FACE_ID_STRIP, count + "");
      }
      count++;
    }
    return lFOut;
  }

  /**
   * Fixing diagonals as named in the article : removing parts of a following strip if it has less importance than the current one
   * 
   * @param stripFace
   * @param externalSkeltonArcs
   * @return
   */
  private static List<List<LineString>> fixingDiagonal2(List<List<Face>> stripFace, HashMap<String, Coordinate> interPoints) {
    List<List<LineString>> outArcs = new ArrayList<List<LineString>>();
    List<LineString> lExteriorLineString = new ArrayList<>();
    List<LineString> lSide = new ArrayList<>();
    // Determining interior and exterior linestring for each limit
    int nbGroup = stripFace.size();
    for (int i = 0; i < nbGroup; i++) {
      LineString exteriorGroupLineString = determineExteriorLineString(stripFace.get(i));
      lExteriorLineString.add(exteriorGroupLineString);
    }
    // For each group (it is not necessary to treat the last one as we //
    // consider the previous and next strip
    for (int i = 0; i < nbGroup; i++) {
      List<Face> currentGroup = stripFace.get(i);
      int nextIndex = (i == (nbGroup - 1)) ? (0) : (i + 1);
      String idPointInter = Math.min(i, nextIndex) + "-" + Math.max(i, nextIndex);
      double nextImportance = Double.parseDouble(stripFace.get(nextIndex).get(0).getAttribute(ATT_IMPORTANCE).toString());
      double currentImportance = Double.parseDouble(currentGroup.get(0).getAttribute(ATT_IMPORTANCE).toString());
      LineString currentExterior = lExteriorLineString.get(i);
      if (currentExterior.getLength() < 0.5) {
        continue;
      }
      LineString nextExterior = lExteriorLineString.get(nextIndex);
      // Determination of limit point on interior between two strips
      Coordinate pointToCast = interPoints.get(idPointInter);
      if (pointToCast == null) {
        System.out.println("NULL pointToCast for " + idPointInter);
        for (Entry<String, Coordinate> entry : interPoints.entrySet()) {
          System.out.println(entry.getKey() + " => " + entry.getValue());
        }
      }
      LineString lineStringToSplit;
      // Line to split is determined
      // according to the importance b
      boolean splitCurrentLine = (nextImportance > currentImportance);
      if (splitCurrentLine) { // We split the current line
        lineStringToSplit = currentExterior;
      } else { // we split the next line
        lineStringToSplit = nextExterior;
      }
      // Line is splitting by projecting the limit points and each part is
      // re-affected to relevant group
      // List<Coordinate> dpl = new ArrayList<>();
      // dpl.addAll(Arrays.asList(lineStringToSplit.getCoordinates()));
      // int pointIndex = Operateurs.projectAndInsertWithPosition(pointToCast, dpl);
      // Coordinate[] sidePoint = new Coordinate[] { pointToCast, dpl.get(pointIndex) };
      // LineString ls = lineStringToSplit.getFactory().createLineString(sidePoint);
      // lSide.add(ls);
      // if (pointIndex == -1) {
      // System.out.println("This case is not supposed to happen for point projection : " + StraightSkeletonParcelDecomposition.class);
      // }
      // List<Coordinate> dpl1 = new ArrayList<>();
      // List<Coordinate> dpl2 = new ArrayList<>();
      // for (int j = 0; j <= pointIndex; j++) {
      // dpl1.add(dpl.get(j));
      // }
      // for (int j = pointIndex; j < dpl.size(); j++) {
      // dpl2.add(dpl.get(j));
      // }
      // LineString ls1 = lineStringToSplit.getFactory().createLineString(dpl1.toArray(new Coordinate[dpl1.size()]));
      // LineString ls2 = lineStringToSplit.getFactory().createLineString(dpl2.toArray(new Coordinate[dpl2.size()]));
      Coordinate proj = project(pointToCast, lineStringToSplit);
      LineString ls = lineStringToSplit.getFactory().createLineString(new Coordinate[] { pointToCast, proj });
      lSide.add(ls);
      Pair<LineString, LineString> split = splitLine(lineStringToSplit, proj);
      LineString ls1 = split.getLeft();
      LineString ls2 = split.getRight();
      List<LineString> lsList = new ArrayList<>();
      if (splitCurrentLine) { // We merge the next line with part of current line
        // Switching variable
        if (ls1.distance(nextExterior) > 0.01) {
          LineString lsTemp = ls1;
          ls1 = ls2;
          ls2 = lsTemp;
        }
        lsList.add(ls1);
        lsList.add(nextExterior);
        // nextExterior = Operateurs.union(lsList);
        lExteriorLineString.set(i, ls2);
        lExteriorLineString.set(nextIndex, nextExterior);
      } else {
        // We merge the next line with part of next line
        // Switching variable
        if (ls1.distance(currentExterior) > 0.01) {
          LineString lsTemp = ls1;
          ls1 = ls2;
          ls2 = lsTemp;
        }
        lsList.add(ls1);
        lsList.add(currentExterior);
        // currentExterior = Operateurs.union(lsList);
        lExteriorLineString.set(i, currentExterior);
        lExteriorLineString.set(nextIndex, ls2);
      }
    }
    outArcs.add(lExteriorLineString);
    outArcs.add(lSide);
    return outArcs;
  }

  private static List<List<Face>> createGroup(List<? extends Face> facesIn, double connexionDistance) {
    List<List<Face>> listGroup = new ArrayList<>();
    while (!facesIn.isEmpty()) {
      Face face = facesIn.remove(0);
      List<Face> currentGroup = new ArrayList<>();
      currentGroup.add(face);
      int nbElem = facesIn.size();
      bouclei: for (int i = 0; i < nbElem; i++) {
        for (Face faceTemp : currentGroup) {
          if (facesIn.get(i).getGeometry().distance(faceTemp.getGeometry()) <= connexionDistance) {
            currentGroup.add(facesIn.remove(i));
            i = -1;
            nbElem--;
            continue bouclei;
          }
        }
      }
      listGroup.add(currentGroup);
    }
    return listGroup;
  }

  /**
   * Main algorithm to process the algorithm
   * 
   * @param pol
   *          : polygon block that will be decomposed
   * @param roads
   *          : roads around the block polygon, may be empty or null. It determines some priority according to road importance.
   * @param maxDepth
   *          : maximal depth of a parcel
   * @param maxDistanceForNearestRoad
   *          : parameters that determine how far a road is considered from block exterior
   * @param minimalArea
   *          : minimal area of a parcel
   * @param minWidth
   *          : minimum width of a parcel
   * @param maxWidth
   *          : maximal width of a parcel
   * @param noiseParameter
   *          : standard deviation of width distribution beteween minWidth and mawWidth
   * @param rng
   *          : Random generator
   * @return
   */
  public static void runStraightSkeleton(Polygon pol, SimpleFeatureCollection roads, double maxDepth, double maxDistanceForNearestRoad, double minimalArea, double minWidth,
      double maxWidth, double noiseParameter, Random rng) {
    // IFeatureCollection<IFeature> featCollOut = new FT_FeatureCollection<>();
    System.out.println("------Begin decomposition with runStraightSkeleton method-----");
    System.out.println("------Partial skelton application-----");
    // Partial skeleton
    CampSkeleton cs = new CampSkeleton(pol, maxDepth);
    if (DEBUG) {
      System.out.println("------Saving for debug  ...-----");
      debugExport(cs.getGraph().getEdges(), "initialFaces", "Polygon");
      debugExport(cs.getExteriorEdges(), "extArcs", "LineString");
      debugExport(cs.getInteriorEdges(), "intArcs", "LineString");
      debugExport(cs.getIncludedEdges(), "incArcs", "LineString");

    }

    System.out.println("------Annotation of external edges-----");
    // Information is stored in getPoids method
    detectAndAnnotateRoadEdges(pol, cs.getExteriorEdges());

    if (DEBUG) {
      System.out.println("------Saving for debug  ...-----");
      debugExport(cs.getExteriorEdges(), "stepanotation", "LineString");
    }

    System.out.println("------Detecting neighbour roads and affecting importance ...-----");
    detectNeighbourdRoad(pol, cs.getExteriorEdges(), roads, maxDistanceForNearestRoad);

    if (DEBUG) {
      System.out.println("------Saving for debug ...-----");
      debugExport(cs.getExteriorEdges(), "stepanotation2", "LineString");
    }

    System.out.println("------Grouping faces in strips...-----");
    List<List<Face>> stripFace = generateStrip(cs.getGraph().getFaces(), cs.getExteriorEdges());

    if (DEBUG) {
      System.out.println("------Saving for debug ...-----");
      debugExport(cs.getGraph().getFaces(), "stepanotation3", "Polygon");
    }

    System.out.println("------Fast strip cleaning...-----");
    stripFace = fastStripCleaning(stripFace, minimalArea);

    if (DEBUG) {
      System.out.println("------Saving for debug ...-----");
      debugExport(cs.getGraph().getFaces(), "stepanotation4", "Polygon");
    }

    System.out.println("------Fixing diagonal edges...-----");
    // 3 group of edges : interior/exterior/side

    /*
     * List<Strip> listOfLists = fixingDiagonal(stripFace, cs.getExteriorArcs());
     * 
     * featCollOut.add(new DefaultFeature());
     * 
     * 
     * if (DEBUG) { logger.info("------Saving for debug ...-----"); IFeatureCollection<IFeature> featDebug = new FT_FeatureCollection<>(); for (List<ILineString> ls : listOfLists)
     * { for (ILineString lls : ls) { featDebug.add(new DefaultFeature(lls)); } } debugExport(featDebug, "stepanotation5"); }
     * 
     * featCollOut = generateParcel(listOfLists, minWidth, maxWidth, noiseParameter, rng);
     */
    // return featCollOut;
  }

  /**
   * Detect edges from polygon block that lays at the exterior and annotate edges as outside or inside the polygon
   * 
   * @param pol
   * @param arcToAnnotate
   */
  private static void detectAndAnnotateRoadEdges(Polygon pol, List<Edge> arcToAnnotate) {
    LineString ls = pol.getExteriorRing();
    Geometry buffer = ls.buffer(0.5);
    for (Edge a : arcToAnnotate) {
      if (buffer.contains(a.getGeometry())) {
        int value = ARC_VALUE_OUTSIDE;
        // AttributeManager.addAttribute(a, ATT_IS_INSIDE, value, "Integer");
        a.setAttribute(ATT_IS_INSIDE, value);
      } else {
        int value = ARC_VALUE_INSIDE;
        // AttributeManager.addAttribute(a, ATT_IS_INSIDE, value, "Integer");
        a.setAttribute(ATT_IS_INSIDE, value);
      }
    }
  }

  /**
   * Detect nearest road from exterior edges and annotates according to the importance.
   * 
   * @param pol
   * @param arcToAnnotate
   * @param roads
   * @param thresholdRoad
   */
  private static void detectNeighbourdRoad(Polygon pol, List<Edge> arcToAnnotate, SimpleFeatureCollection roads, double thresholdRoad) {
    for (Edge a : arcToAnnotate) {
      // If it is not an external arc, 0 is set by default
      if (Integer.parseInt(a.getAttribute(ATT_IS_INSIDE).toString()) == ARC_VALUE_INSIDE) {
        // AttributeManager.addAttribute(a, ATT_IMPORTANCE, 0.0, "Double");
        a.setAttribute(ATT_IMPORTANCE, 0.0);
        // AttributeManager.addAttribute(a, ATT_ROAD, "", "String");
        a.setAttribute(ATT_ROAD, "");
        continue;
      }
      Optional<SimpleFeature> feat = FindObjectInDirection.find(a.getGeometry(), pol, roads, thresholdRoad); // NearestRoadFinder.findNearest(roads,
      System.out.println("FindObjectInDirection (detectNeighbourdRoad) = " + feat);
      // a.getGeom(),
      // thresholdRoad);
      if (!feat.isPresent()) {
        // AttributeManager.addAttribute(a, ATT_IMPORTANCE, 0.0, "Double");
        a.setAttribute(ATT_IMPORTANCE, 0.0);
        // AttributeManager.addAttribute(a, ATT_ROAD, "", "String");
        a.setAttribute(ATT_ROAD, "");
        continue;
      }
      Object o = feat.get().getAttribute(NAME_ATT_IMPORTANCE);
      double value = 0;
      if (o == null || !(o instanceof Double)) {
        System.out.println("Attribute : " + NAME_ATT_IMPORTANCE + "  not found or null ");
      } else {
        value = Double.parseDouble(o.toString());
      }
      // AttributeManager.addAttribute(a, ATT_IMPORTANCE, value, "Double");
      a.setAttribute(ATT_IMPORTANCE, value);
      o = feat.get().getAttribute(NAME_ATT_ROAD);
      String valuestr = "";
      if (o == null || !(o instanceof String)) {
        System.out.println("Attribute : " + NAME_ATT_ROAD + "  not found or null ");
      } else {
        valuestr = o.toString();
      }
      // AttributeManager.addAttribute(a, ATT_ROAD, valuestr, "String");
      a.setAttribute(ATT_ROAD, valuestr);
    }
  }

  /**
   * Generates the different strip according to exterior edges importance
   * 
   * @param popFaces
   * @param exteriorArc
   * @return
   */
  private static List<List<Face>> generateStrip(List<Face> popFaces, List<Edge> exteriorArc) {
    String currentStripName = null;
    int count = -1;
    List<List<Face>> correspondanceMapID = new ArrayList<>();
    // For edge face
    for (Face f : popFaces) {
      // Determining whose that belongs to the same roads and group them according to roads name
      for (Edge a : f.getEdges()) {
        if (!exteriorArc.contains(a)) {
          continue;
        }
        if (Integer.parseInt(a.getAttribute(ATT_IS_INSIDE).toString()) == ARC_VALUE_INSIDE) {
          continue;
        }
        String newRoadName = a.getAttribute(ATT_ROAD).toString();
        if (currentStripName == null || !(currentStripName.equals(newRoadName))) {
          currentStripName = newRoadName;
          count++;
          correspondanceMapID.add(new ArrayList<>());
        }
        correspondanceMapID.get(count).add(f);
        // AttributeManager.addAttribute(f, ATT_FACE_ID_STRIP, count, "Integer");
        f.setAttribute(ATT_FACE_ID_STRIP, count);
        // AttributeManager.addAttribute(f, ATT_IMPORTANCE, a.getAttribute(ATT_IMPORTANCE), "Double");
        f.setAttribute(ATT_IMPORTANCE, a.getAttribute(ATT_IMPORTANCE));
        break;
      }
    }
    // ... and group them according to roads name
    correspondanceMapID = ordonnateGroupe(correspondanceMapID);
    for (int i = 0; i < correspondanceMapID.size(); i++) {
      List<Face> currentGroup = correspondanceMapID.get(i);
      for (Face f : currentGroup) {
        // AttributeManager.addAttribute(f, ATT_FACE_ID_STRIP, i, "Integer");
        f.setAttribute(ATT_FACE_ID_STRIP, i);
      }
    }
    return correspondanceMapID;
  }

  private static List<List<Face>> ordonnateGroupe(List<List<Face>> lGroupes) {
    List<LineString> lExteriorLineString = new ArrayList<>();
    List<List<Face>> orderedGroup = new ArrayList<>();
    for (List<Face> groupe : lGroupes) {
      LineString exteriorLineString = determineExteriorLineString(groupe);
      lExteriorLineString.add(exteriorLineString);
    }
    // FirstGroup
    Geometry currentLineString = lExteriorLineString.remove(0).buffer(0.1);
    orderedGroup.add(lGroupes.remove(0));
    for (int i = 0; i < lGroupes.size(); i++) {
      List<Face> currentGroup = lGroupes.get(i);
      for (Face f : currentGroup) {
        if (!f.getGeometry().intersects(currentLineString)) {
          continue;
        }
        orderedGroup.add(lGroupes.remove(i));
        currentLineString = lExteriorLineString.remove(i).buffer(0.1);
        i = -1;
        break;
      }
    }
    if (!lGroupes.isEmpty()) {
      System.out.println("All groups are not used during ordering. Left groups : " + lGroupes.size());
    }
    return orderedGroup;
  }

  /**
   * Make a fast clean by removing small strips and affect them according to most important adjacent road.
   * 
   * @TODO
   * @param initStripping
   * @param minimalArea
   * @return
   */
  private static List<List<Face>> fastStripCleaning(List<List<Face>> initStripping, double minimalArea) {
    int nbGroup = initStripping.size();
    for (int i = 0; i < nbGroup; i++) {
      List<Face> currentGroup = initStripping.get(i);
      double totalArea = 0;
      for (Face f : currentGroup) {
        totalArea = totalArea + f.getGeometry().getArea();
      }
      if (totalArea < minimalArea) {
        int previousIndex = (i == 0) ? (nbGroup - 1) : (i - 1);
        int nextIndex = (i == (nbGroup - 1)) ? (0) : (i + 1);
        double previousImportance = Double.parseDouble(initStripping.get(previousIndex).get(0).getAttribute(ATT_IMPORTANCE).toString());
        double nextImportance = Double.parseDouble(initStripping.get(nextIndex).get(0).getAttribute(ATT_IMPORTANCE).toString());
        if (previousImportance > nextImportance) {
          // The group is merged with previous one
          initStripping.get(previousIndex).addAll(initStripping.remove(i));
          i--;
          nbGroup--;
        } else if (previousImportance < nextImportance) {
          System.out.println("Je merge");
          // The group is merged with the next one
          initStripping.get(nextIndex--).addAll(initStripping.remove(i));
          i--;
          nbGroup--;
        }
        for (Face f : currentGroup) {
          // AttributeManager.addAttribute(f, ATT_IMPORTANCE, Math.max(previousIndex, nextImportance), "Double");
          f.setAttribute(ATT_IMPORTANCE, Math.max(previousIndex, nextImportance));
        }
      }
    }
    initStripping = ordonnateGroupe(initStripping);
    int count = 0;
    for (int i = 0; i < nbGroup; i++) {
      // Groups are recounted
      List<Face> currentGroup = initStripping.get(i);
      for (Face f : currentGroup) {
        // AttributeManager.addAttribute(f, ATT_FACE_ID_STRIP, count, "Integer");
        f.setAttribute(ATT_FACE_ID_STRIP, count);
      }
      count++;
    }
    return initStripping;
  }

  /**
   * Fixing diagonals as named in the article : removing parts of a following strip if it has less importance than the current one
   * 
   * @param stripFace
   * @param externalSkeltonArcs
   * @return
   */
  protected static List<Strip> fixingDiagonal(List<List<Face>> stripFace, List<Edge> externalSkeltonArcs) {
    List<List<LineString>> outArcs = new ArrayList<List<LineString>>();
    List<LineString> lExteriorLineString = new ArrayList<>();
    List<LineString> lInteriorLineString = new ArrayList<>();
    List<LineString> lSideIni = new ArrayList<>();
    List<Strip> lStrip = new ArrayList<>();
    lStrip.add(null);
    // Determining interior and exterior linestring for each limit
    for (List<Face> groupe : stripFace) {
      LineString interiorLineString = determineInteriorLineString(groupe);
      LineString exteriorLineString = determineExteriorLineString(groupe);
      lExteriorLineString.add(exteriorLineString);
      lInteriorLineString.add(interiorLineString);
    }
    // For each group (it is not necessary to treat the last one as we consider the previous and next strip
    int nbGroup = stripFace.size();
    for (int i = 0; i < nbGroup; i++) {
      System.out.println("lInteriorLineString (" + i + ") = " + lInteriorLineString.get(i));
    }
    for (int i = 0; i < nbGroup - 1; i++) {
      List<Face> currentGroup = stripFace.get(i);
      int nextIndex = (i == (nbGroup - 1)) ? (0) : (i + 1);
      double nextImportance = Double.parseDouble(stripFace.get(nextIndex).get(0).getAttribute(ATT_IMPORTANCE).toString());
      double currentImportance = Double.parseDouble(currentGroup.get(0).getAttribute(ATT_IMPORTANCE).toString());
      LineString currentExterior = lExteriorLineString.get(i);
      LineString currentInterior = lInteriorLineString.get(i);
      LineString nextExterior = lExteriorLineString.get(nextIndex);
      LineString nextInterior = lInteriorLineString.get(nextIndex);
      // Determination of limit point on interior between two strips
      Coordinate pointToCast = currentInterior.getCoordinateN(0);
      Point ptCas = currentInterior.getFactory().createPoint(pointToCast);
      if (nextInterior.distance(ptCas) > 0.5) {
        pointToCast = currentInterior.getCoordinateN(currentInterior.getNumPoints() - 1);
        ptCas = currentInterior.getFactory().createPoint(pointToCast);
        if (nextInterior.distance(ptCas) > 0.5) {
          System.out.println("This case is not supposed to happen, the test is not robust enough : " + StraightSkeletonParcelDecomposition.class);
          System.out.println(ptCas);
          System.out.println(nextInterior);
        }
      }
      LineString lineStringToSplit;
      // Line to split is determined according to the importance
      boolean splitCurrentLine = (nextImportance > currentImportance);
      if (splitCurrentLine) {
        // We split the current line
        lineStringToSplit = currentExterior;
      } else {
        // we split the next line
        lineStringToSplit = nextExterior;
      }
      // Line is splitting by projecting the limit points and each part is
      // re-affected to relevant group
      // List<Coordinate> dpl = new ArrayList<>(Arrays.asList(lineStringToSplit.getCoordinates()));
      // int pointIndex = Operateurs.projectAndInsertWithPosition(pointToCast, dpl);
      // List<Coordinate> sidePoint = new ArrayList<>();
      // sidePoint.add(pointToCast);
      // sidePoint.add(dpl.get(pointIndex));
      // lSideIni.add(lineStringToSplit.getFactory().createLineString(sidePoint.toArray(new Coordinate[sidePoint.size()])));
      // if (pointIndex == -1) {
      // System.out.println("This case is not supposed to happen for point projection : " + StraightSkeletonParcelDecomposition.class);
      // }
      // List<Coordinate> dpl1 = new ArrayList<>();
      // List<Coordinate> dpl2 = new ArrayList<>();
      // for (int j = 0; j <= pointIndex; j++) {
      // dpl1.add(dpl.get(j));
      // }
      // for (int j = pointIndex; j < dpl.size(); j++) {
      // dpl2.add(dpl.get(j));
      // }
      // LineString ls1 = lineStringToSplit.getFactory().createLineString(dpl1.toArray(new Coordinate[dpl1.size()]));
      // LineString ls2 = lineStringToSplit.getFactory().createLineString(dpl2.toArray(new Coordinate[dpl2.size()]));
      Coordinate proj = project(pointToCast, lineStringToSplit);
      lSideIni.add(lineStringToSplit.getFactory().createLineString(new Coordinate[] { pointToCast, proj }));
      Pair<LineString, LineString> split = splitLine(lineStringToSplit, proj);
      LineString ls1 = split.getLeft();
      LineString ls2 = split.getRight();

      // Splitted line too small
      if (ls1.getLength() < 0.5 || ls2.getLength() < 0.5) {
        continue;
      }
      List<LineString> ls = new ArrayList<>();
      if (splitCurrentLine) {
        // We merge the next line with part of current line
        // Switching variable
        if (ls1.distance(nextExterior) > 0.01) {
          LineString lsTemp = ls1;
          ls1 = ls2;
          ls2 = lsTemp;
        }
        ls.add(ls1);
        ls.add(nextExterior);
        // nextExterior = Operateurs.union(ls);
        lExteriorLineString.set(i, ls2);
        lExteriorLineString.set(nextIndex, nextExterior);
      } else {
        // We merge the next line with part of next line
        // Switching variable
        if (ls1.distance(currentExterior) > 0.01) {
          LineString lsTemp = ls1;
          ls1 = ls2;
          ls2 = lsTemp;
        }
        ls.add(ls1);
        ls.add(currentExterior);
        // currentExterior = Operateurs.union(ls);
        lExteriorLineString.set(i, currentExterior);
        lExteriorLineString.set(nextIndex, ls2);
      }
    }
    outArcs.add(lInteriorLineString);
    outArcs.add(lExteriorLineString);
    outArcs.add(lSideIni);
    return lStrip;
  }

  /**
   * Generate parcels from the list of strips
   * 
   * @param listOfLists
   * @param minWidth
   * @param maxWidth
   * @param noiseParameter
   * @param rng
   * @return
   * @throws SchemaException
   */
  private static SimpleFeatureCollection generateParcel(List<List<LineString>> listOfLists, double minWidth, double maxWidth, double noiseParameter, RandomGenerator rng)
      throws SchemaException {
    // Exterior and interior linstering for each groups
    List<LineString> lInteriorLineString = listOfLists.get(0);
    List<LineString> lExteriorLineString = listOfLists.get(1);
    List<LineString> lSideLineString = listOfLists.get(2);
    int nbStrip = lInteriorLineString.size();
    // Random parameters
    GaussianRandomGenerator rawGenerator = new GaussianRandomGenerator(rng);
    double gaussianCenter = (minWidth + maxWidth) / 2;
    DefaultFeatureCollection result = new DefaultFeatureCollection();
    SimpleFeatureType PARCELTYPE = DataUtilities.createType("location", "geom:Polygon,ID:Integer");
    int count = 0;
    // Nettoyage des strip
    for (int i = 0; i < nbStrip; i++) {
      LineString currentExterior = lExteriorLineString.get(i);
      if (currentExterior == null || currentExterior.getLength() < 0.01) {
        lInteriorLineString.remove(i);
        lExteriorLineString.remove(i);
        i--;
        nbStrip--;
      }
    }
    // For each strip
    for (int i = 2; i < 3; i++) {
      LineString currentExterior = lExteriorLineString.get(i);
      if (currentExterior == null || currentExterior.getLength() < 0.01) {
        lSideLineString.add(i, null);
        continue;
      }
      LineString currentInterior = lInteriorLineString.get(i);
      LineString previousLimitSide = lSideLineString.get(i);
      if (previousLimitSide == null) {
        previousLimitSide = (i == 0) ? lSideLineString.get(lSideLineString.size() - 1) : lSideLineString.get(i);
      }
      LineString nextLimitSide = (i == nbStrip - 1) ? lSideLineString.get(0) : lSideLineString.get(i + 1);
      if (nextLimitSide == null) {
        nextLimitSide = lSideLineString.get(1);// Only case that is suppose to happens
      }
      List<LineString> lsProjected = new ArrayList<>();
      if (currentInterior != null && !(currentInterior.getLength() < 0.01)) {
        lsProjected.add(currentInterior);
      }
      if (nbStrip > 1) {
        if (!previousLimitSide.intersects(currentExterior.buffer(0.05))) {
          lsProjected.add(previousLimitSide);
        }
        if (!nextLimitSide.intersects(currentExterior.buffer(0.05))) {
          lsProjected.add(nextLimitSide);
        }
      }
      currentInterior = union(lsProjected);
      // Strip polygon is deomposed part by part
      boolean endFlag = false;
      boolean firstLimit = true;
      while (!endFlag) {
        // Take a random abscissa
        double s = rawGenerator.nextNormalizedDouble() * noiseParameter * 3 + gaussianCenter;
        // double s = rng.nextGaussian() * noiseParameter * 3 * gaussianCenter;
        // Clamping limits
        s = Math.min(maxWidth, s);
        s = Math.max(s, minWidth);
        System.out.println("s = " + s + " with " + noiseParameter + " and " + gaussianCenter + " between " + minWidth + " and " + maxWidth);
        System.out.println("currentExterior length = " + currentExterior.getLength());
        // This value is the par of exterior lines from which the parcel is produced
        LineString lsTokeep = null;
        // the value is longer than the length of remining line, a parcel is produced with the rest of the line
        if (s > currentExterior.getLength()) {
          System.out.println("C1");
          lsTokeep = currentExterior;
          endFlag = true;
        } else {
          // Line is splitting
          Pair<LineString, LineString> ls = splitLine(currentExterior, s);
          System.out.println("SPLITTING\n" + currentExterior);
          LineString ls1 = ls.getLeft();
          LineString ls2 = ls.getRight();
          System.out.println(ls1);
          System.out.println(ls2);
          // Determining what part of the split lines is the interesting cut
          // If the remaining length for the rest of the line is too small, it is also merged
          if (Math.abs(s - ls1.getLength()) < 0.01) {
            if (ls2.getLength() < minWidth) {
              System.out.println("C2");
              lsTokeep = currentExterior;
              endFlag = true;
            } else {
              System.out.println("C3");
              lsTokeep = ls1;
              currentExterior = ls2;
            }
          } else {
            if (ls1.getLength() < minWidth) {
              System.out.println("C4");
              lsTokeep = currentExterior;
              endFlag = true;
            } else {
              System.out.println("C5");
              currentExterior = ls1;
              lsTokeep = ls2;
            }
          }
        }
        // Lateral lines are generated by projected extremities of exterior parts of the block on interior line
        System.out.println("lsTokeep\n" + lsTokeep);
        System.out.println("currentInterior\n" + currentInterior);
        Coordinate dpExt1 = lsTokeep.getCoordinateN(0);
        Coordinate dpExt2 = lsTokeep.getCoordinateN(lsTokeep.getNumPoints() - 1);
        Coordinate dpCast1 = project(dpExt1, currentInterior);
        Coordinate dpCast2 = project(dpExt2, currentInterior);
        Coordinate[] dplLat = new Coordinate[] { dpCast1, dpExt1 };
        Coordinate[] dplLat2 = new Coordinate[] { dpCast2, dpExt2 };
        LineString lsLat = (firstLimit) ? previousLimitSide : lsTokeep.getFactory().createLineString(dplLat);
        System.out.println("lsLat\n" + lsLat);
        LineString lsLat2 = (endFlag) ? nextLimitSide : lsTokeep.getFactory().createLineString(dplLat2);
        System.out.println("lsLat2\n" + lsLat2);
        List<LineString> lsOut = new ArrayList<>();
        lsOut.add(lsTokeep);
        lsOut.add(lsTokeep.getFactory().createLineString(dplLat));
        System.out.println("HUM\n" + lsTokeep.getFactory().createLineString(dplLat));
        // If lateral lines are interesecting the parcel is not composed by a part of interior line
        if (!lsLat.intersects(lsLat2.buffer(0.05))) {
          // Determining part of interior line between lateral lines
          LineString ls = cutBetweentLats(lsLat, lsLat2, currentInterior);
          lsOut.add(ls);
        } else {
          lsOut.add(null);
        }
        lsOut.add(lsTokeep.getFactory().createLineString(dplLat2));
        System.out.println("HUM\n" + lsTokeep.getFactory().createLineString(dplLat2));
        // IFeature featOut = new DefaultFeature(calculateSurface(lsOut));
        // AttributeManager.addAttribute(featOut, "ID", count++, "Integer");
        // featCollOut.add(featOut);
        SimpleFeature parcelle = SimpleFeatureBuilder.build(PARCELTYPE, new Object[] { calculateSurface(lsOut), count++ }, null);
        result.add(parcelle);
        firstLimit = false;
      }
    }
    return result;
  }

  public static Coordinate project(Coordinate p, LineString l) {
    List<Pair<Coordinate, Double>> list = new ArrayList<>();
    for (int i = 0; i < l.getNumPoints() - 1; i++) {
      LineSegment segment = new LineSegment(l.getCoordinateN(i), l.getCoordinateN(i + 1));
      Coordinate proj = segment.closestPoint(p);
      list.add(new ImmutablePair<>(proj, proj.distance(p)));
    }
    return list.stream().min((a, b) -> a.getRight().compareTo(b.getRight())).get().getLeft();
  }

  /**
   * Helper to determine exterior linestring from a face list
   * 
   * @param currentGroup
   * @return
   */
  private static LineString determineExteriorLineString(List<Face> currentGroup) {
    List<LineString> lineStringToMerge = new ArrayList<>();
    List<Edge> encounterdEdges = new ArrayList<>();
    for (Face f : currentGroup) {
      System.out.println("FACE " + f.getGeometry() + " EDGES = " + f.getEdges().size());
      for (Edge a : f.getEdges()) {
        Object o = a.getAttribute(ATT_IS_INSIDE);
        System.out.println("ATT_IS_INSIDE = " + o);
        if (o == null) {
          continue;
        }
        if (Integer.parseInt(o.toString()) == ARC_VALUE_OUTSIDE) {
          if (!encounterdEdges.contains(a)) {
            lineStringToMerge.add(a.getGeometry());
            encounterdEdges.add(a);
          }
        }
      }
    }
    return union(lineStringToMerge);
  }

  /**
   * Helper to determine interior linestring from a face list
   * 
   * @param currentGroup
   * @return
   */
  private static LineString determineInteriorLineString(List<Face> currentGroup) {
    List<LineString> lineStringToMerge = new ArrayList<>();
    for (Face f : currentGroup) {
      for (Edge a : f.getEdges()) {
        Object o = a.getAttribute(ATT_IS_INSIDE);
        if (o == null) {
          continue;
        }
        if (Integer.parseInt(o.toString()) == ARC_VALUE_INSIDE) {
          lineStringToMerge.add(a.getGeometry());
        }
      }
    }
    return union(lineStringToMerge);
  }

  /**
   * Cuts the part of a linestring (interiorCurve) between the intersection of two other linestring (lsLat1, lsLat2) WARNING : works only in this specific context
   * 
   * @param lsLat1
   * @param lsLat2
   * @param interiorCurve
   * @return
   */
  private static LineString cutBetweentLats(LineString lsLat1, LineString lsLat2, LineString interiorCurve) {
    Coordinate dp1 = lsLat1.getCoordinateN(0);
    Coordinate dp2 = lsLat1.getCoordinateN(lsLat1.getNumPoints() - 1);
    Coordinate idtemp = null;
    if (interiorCurve.intersects((lsLat1.getFactory().createPoint(dp1)).buffer(0.05))) {
      idtemp = dp1;
    } else {
      idtemp = dp2;
    }
    Coordinate dp3 = lsLat2.getCoordinateN(0);
    Coordinate dp4 = lsLat2.getCoordinateN(lsLat2.getNumPoints() - 1);
    Coordinate idtemp2 = null;
    if (lsLat2.intersects((lsLat1.getFactory().createPoint(dp3)).buffer(0.05))) {
      idtemp2 = dp3;
    } else {
      idtemp2 = dp4;
    }
    Pair<LineString, LineString> lsSpli = splitLine((LineString) interiorCurve, idtemp);
    LineString goodLineString = null;
    if (lsSpli.getRight().getLength() == 0) {
      goodLineString = lsSpli.getLeft();
    }
    if (lsSpli.getLeft().getLength() < 0.05) {
      goodLineString = lsSpli.getRight();
    }
    if (goodLineString == null) {
      if (lsSpli.getLeft().intersects((lsLat1.getFactory().createPoint(idtemp2)).buffer(0.05))) {
        goodLineString = lsSpli.getLeft();
      } else {
        goodLineString = lsSpli.getRight();
      }
    }
    lsSpli = splitLine(goodLineString, idtemp2);
    if (lsSpli.getLeft().intersects((lsLat1.getFactory().createPoint(idtemp)).buffer(0.05))) {
      if (lsSpli.getRight().intersects((lsLat1.getFactory().createPoint(idtemp)).buffer(0.05))) {
        // This case corresponds to a strip that makes a loop
        // We consider to return the shortest
        if (lsSpli.getLeft().getLength() > lsSpli.getRight().getLength()) {
          return lsSpli.getRight();
        }
        return lsSpli.getLeft();
      }
      return lsSpli.getLeft();
    }
    return lsSpli.getRight();
  }

  /**
   * Simple code to determine surface from a list of line string WARNING : works only in this specific context
   * 
   * @param ls
   * @return
   */
  private static Polygon calculateSurface(List<LineString> ls) {
    List<Coordinate> dpl = new ArrayList<>();
    dpl.addAll(Arrays.asList(ls.get(1).reverse().getCoordinates()));
    if (ls.get(2) != null) {
      dpl.addAll(Arrays.asList(ls.get(2).reverse().getCoordinates()));
    }
    dpl.addAll(Arrays.asList(ls.get(3).getCoordinates()));
    dpl.addAll(Arrays.asList(ls.get(0).reverse().getCoordinates()));
    System.out.println("calculateSurface");
    ls.stream().forEach(l -> System.out.println(l));
    System.out.println(ls.get(0).getFactory().createPolygon(dpl.toArray(new Coordinate[dpl.size()])).union());
    return (Polygon) ls.get(0).getFactory().createPolygon(dpl.toArray(new Coordinate[dpl.size()])).union();
  }

  private static Pair<LineString, LineString> splitLine(LineString line, double s) {
    // Coordinate dpInter = Operateurs.pointEnAbscisseCurviligne(line, s);
    // Coordinate dpInter = LengthLocationMap.getLocation(line, s * line.getLength()).getCoordinate(line);
    LengthIndexedLine lil = new LengthIndexedLine(line);
    // return splitLine(line, dpInter);
    return new ImmutablePair<LineString, LineString>((LineString) lil.extractLine(0, s), (LineString) lil.extractLine(s, line.getLength()));
  }

  private static Pair<LineString, LineString> splitLine(LineString line, Coordinate c) {
    LengthIndexedLine lil = new LengthIndexedLine(line);
    return splitLine(line, lil.indexOf(c));
  }
  // private static LineString[] splitLine(LineString line, Coordinate dpInter) {
  // List<Coordinate> dplTemp = new ArrayList<>(Arrays.asList(line.getCoordinates()));
  // int pointIndex = Operateurs.projectAndInsertWithPosition(dpInter, dplTemp);
  // List<Coordinate> dpl1 = new ArrayList<>();
  // for (int j = 0; j <= pointIndex; j++) {
  // dpl1.add(dplTemp.get(j));
  // }
  // LineString ls1 = line.getFactory().createLineString(dpl1.toArray(new Coordinate[dpl1.size()]));
  // List<Coordinate> dpl2 = new ArrayList<>();
  // for (int j = pointIndex; j < dplTemp.size(); j++) {
  // dpl2.add(dplTemp.get(j));
  // }
  // LineString ls2 = line.getFactory().createLineString(dpl2.toArray(new Coordinate[dpl2.size()]));
  // LineString tab[] = new LineString[] { ls1, ls2 };
  // return tab;
  // }

  // ////////////////////////
  // ////// DEBUG METHODS
  // ///////////////////////
  //
  private static <G extends Geometry, E extends GraphElement<G>> void debugExport(List<E> feats, String name, String geomType) {
    System.out.println("save " + feats.size() + " to " + (FOLDER_OUT_DEBUG + name));
    if (feats.isEmpty())
      return;
    try {
      String specs = "geom:" + geomType + ":srid=2154";
      List<String> attributes = feats.get(0).getAttributes();
      for (String attribute : attributes) {
        specs += "," + attribute + ":String";
      }
      ShapefileDataStoreFactory factory = new ShapefileDataStoreFactory();
      FileDataStore dataStore = factory.createDataStore(new File(FOLDER_OUT_DEBUG + name + ".shp").toURI().toURL());
      String featureTypeName = "Object";
      SimpleFeatureType featureType = DataUtilities.createType(featureTypeName, specs);
      dataStore.createSchema(featureType);
      String typeName = dataStore.getTypeNames()[0];
      FeatureWriter<SimpleFeatureType, SimpleFeature> writer = dataStore.getFeatureWriterAppend(typeName, Transaction.AUTO_COMMIT);
      System.setProperty("org.geotools.referencing.forceXY", "true");
      if (DEBUG)
        System.out.println(Calendar.getInstance().getTime() + " write shapefile");
      for (E element : feats) {
        SimpleFeature feature = writer.next();
        Object[] att = new Object[attributes.size() + 1];
        att[0] = element.getGeometry();
        for (int i = 0; i < attributes.size(); i++) {
          att[i + 1] = element.getAttribute(attributes.get(i));
        }
        feature.setAttributes(att);
        writer.write();
      }
      if (DEBUG)
        System.out.println(Calendar.getInstance().getTime() + " done");
      writer.close();
      dataStore.dispose();
      // FeaturePolygonizer.saveGeometries(feats.stream().map(e -> e.getGeometry()).collect(Collectors.toList()), new File(FOLDER_OUT_DEBUG + name + ".shp"), geomType);
    } catch (MalformedURLException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    } catch (SchemaException e) {
      e.printStackTrace();
    }
  }

  private static boolean isReflex(Node node, Edge previous, Edge next) {
    return isReflex(node.getCoordinate(), previous.getGeometry(), next.getGeometry());
  }

  private static Coordinate getNextCoordinate(Coordinate current, LineString line) {
    return (line.getCoordinateN(0).equals2D(current)) ? line.getCoordinateN(1) : line.getCoordinateN(line.getNumPoints() - 2);
  }

  private static boolean isReflex(Coordinate current, LineString previous, LineString next) {
    Coordinate previousCoordinate = getNextCoordinate(current, previous);
    Coordinate nextCoordinate = getNextCoordinate(current, next);
    return Orientation.index(previousCoordinate, current, nextCoordinate) == Orientation.CLOCKWISE;
  }

  public static final int NONE = 0;
  public static final int PREVIOUS = 1;
  public static final int NEXT = 2;

  private static int classify(Node node, Pair<Edge, Optional<Pair<String, Double>>> previous, Pair<Edge, Optional<Pair<String, Double>>> next) {
    if (isReflex(node, previous.getLeft(), next.getLeft()))
      return NONE;
    if (previous.getRight().map(a -> a.getRight()).orElse(0.0) > next.getRight().map(a -> a.getRight()).orElse(0.0)) {
      return PREVIOUS;
    }
    return NEXT;
  }

  private static Pair<String, Double> attributes(SimpleFeature s) {
    String name = s.getAttribute(NAME_ATT_ROAD).toString();
    String impo = s.getAttribute(NAME_ATT_IMPORTANCE).toString();
    return new ImmutablePair<>(name, Double.parseDouble(impo.replaceAll(",", ".")));
  }

  public static SimpleFeatureCollection decompose(Polygon pol, SimpleFeatureCollection roads, double offsetDistance, double maxDistanceForNearestRoad, double minimalArea,
      double minWidth, double maxWidth, double noiseParameter, RandomGenerator rng) throws SchemaException {
    // Partial skeleton
    CampSkeleton cs = new CampSkeleton(pol, offsetDistance);
    cs.getGraph().getFaces().forEach(f->System.out.println(f.getGeometry()));
    List<Edge> edges = cs.getExteriorEdges();
    System.out.println("ExteriorEdges = " + edges.size());
    List<Edge> orderedEdges = new ArrayList<>();
    Edge first = edges.get(0);
    orderedEdges.add(first);
    Node currentNode = first.getTarget();
    Edge current = cs.getGraph().next(currentNode, first, edges);
    orderedEdges.add(current);
    currentNode = (current.getOrigin() == currentNode) ? current.getTarget() : current.getOrigin();
    while (current != first) {
      current = cs.getGraph().next(currentNode, current, edges);
      currentNode = (current.getOrigin() == currentNode) ? current.getTarget() : current.getOrigin();
      if (current != first)
        orderedEdges.add(current);
    }
    System.out.println("OrderedEdges = " + orderedEdges.size());
    List<Pair<Edge, Optional<Pair<String, Double>>>> attributes = orderedEdges.stream()
        // .map(e -> new ImmutablePair<>(e,Closest.find(e.getGeometry().getCentroid(), roads, maxDistanceForNearestRoad)))
        .map(e -> new ImmutablePair<>(e, FindObjectInDirection.find(e.getGeometry(), pol, roads, maxDistanceForNearestRoad)))
        .map(so -> new ImmutablePair<>(so.left, so.right.map(s -> attributes(s)))).collect(Collectors.toList());
//    System.out.println("Names = " + attributes.size());
//    for (Pair<Edge, Optional<Pair<String, Double>>> n : attributes) {
//      System.out.println(n.getLeft().getGeometry());
//      System.out.println(n.getRight());
//    }
    Map<Pair<Edge, Optional<Pair<String, Double>>>, Integer> map = new HashMap<>();
    Pair<Edge, Optional<Pair<String, Double>>> firstPair = attributes.get(0);
    int stripId = 0;
    map.put(firstPair, stripId++);
    Map<Node, Pair<Edge, Edge>> supportingVertices = new HashMap<>();
    for (int i = 0; i < attributes.size() - 1; i++) {
      Pair<Edge, Optional<Pair<String, Double>>> p1 = attributes.get(i);
      Pair<Edge, Optional<Pair<String, Double>>> p2 = attributes.get((i + 1) % attributes.size());
      if (p1.getRight().map(p -> p.getLeft()).orElse("").equals(p2.getRight().map(p -> p.getLeft()).orElse(""))) {
        map.put(p2, map.get(p1));
      } else {
        map.put(p2, stripId++);
        supportingVertices.put(cs.getGraph().getCommonNode(p1.getLeft(), p2.getLeft()), new ImmutablePair<>(p1.getLeft(), p2.getLeft()));
      }
    }
    Pair<Edge, Optional<Pair<String, Double>>> lastPair = attributes.get(attributes.size() - 1);
    if (lastPair.getRight().map(p -> p.getLeft()).orElse("").equals(firstPair.getRight().map(p -> p.getLeft()).orElse(""))) {
      // we merge the first and last strips
      Integer firstStrip = map.get(firstPair);
      Integer lastStrip = map.get(lastPair);
      map.keySet().stream().filter(p -> map.get(p).equals(lastStrip)).forEach(p -> map.put(p, firstStrip));
    } else {
      supportingVertices.put(cs.getGraph().getCommonNode(lastPair.getLeft(), firstPair.getLeft()), new ImmutablePair<>(lastPair.getLeft(), firstPair.getLeft()));
    }
    Set<Integer> stripIds = new HashSet<>(map.values());
    System.out.println("Strips = " + stripIds.size());
    List<List<Edge>> alphaStrips = new ArrayList<>();
    Map<Edge, Integer> alphaStripEdgeMap = new HashMap<>();
    for (Integer index : stripIds) {
      // find all edges with the corresponding index
      List<Edge> stripEdges = map.entrySet().stream().filter(p -> p.getValue().equals(index)).map(p -> p.getKey()).map(p -> p.getLeft()).collect(Collectors.toList());
      alphaStrips.add(stripEdges);
      stripEdges.forEach(e -> alphaStripEdgeMap.put(e, index));
    }
    Map<Integer, LineString> psiMap = new HashMap<>();
    for (List<Edge> strip : alphaStrips) {
      Integer currentStripId = alphaStripEdgeMap.get(strip.get(0));
      LineString l = union(strip.stream().map(e -> e.getGeometry()).collect(Collectors.toList()));
      System.out.println(l);
      psiMap.put(currentStripId, l);
    }
    Map<Face, Integer> alphaStripFaceMap = new HashMap<>();
    Map<Integer, Polygon> alphaStripPolygonMap = new HashMap<>();
    for (List<Edge> strip : alphaStrips) {
      Integer currentStripId = alphaStripEdgeMap.get(strip.get(0));
      List<Face> stripFaces = strip.stream().map(e -> (e.getRight() == null) ? e.getLeft() : e.getRight()).collect(Collectors.toList());
      stripFaces.forEach(p -> alphaStripFaceMap.put(p, currentStripId));
      List<Polygon> polygons = stripFaces.stream().map(f -> f.getGeometry()).collect(Collectors.toList());
      alphaStripPolygonMap.put(currentStripId, polygonUnionWithoutHoles(polygons));
      System.out.println(polygonUnionWithoutHoles(polygons));
    }
    System.out.println("Supporting vertices");
    Map<Integer, Polygon> betaStripPolygonMap = new HashMap<>(alphaStripPolygonMap);
    // classify supporting vertices
    for (Node n : supportingVertices.keySet()) {
      System.out.println(n.getGeometry());
      Pair<Edge, Edge> pair = supportingVertices.get(n);
      Pair<Edge, Optional<Pair<String, Double>>> previous = attributes.stream().filter(p -> p.getLeft() == pair.getLeft()).findFirst().get();
      Pair<Edge, Optional<Pair<String, Double>>> next = attributes.stream().filter(p -> p.getLeft() == pair.getRight()).findFirst().get();
      int supportingVertexClass = classify(n, previous, next);
//      System.out.println(supportingVertexClass);
      Edge previousEdge = previous.getLeft();
      Edge nextEdge = next.getLeft();
      Face prevFace = (previousEdge.getLeft() == null) ? previousEdge.getRight() : previousEdge.getLeft();
      Face nextFace = (nextEdge.getLeft() == null) ? nextEdge.getRight() : nextEdge.getLeft();
      Integer prevFaceId = alphaStripFaceMap.get(prevFace);
      Integer nextFaceId = alphaStripFaceMap.get(nextFace);
      List<Edge> edgeList = cs.getGraph().getEdges().stream().filter(e -> (alphaStripFaceMap.get(e.getLeft()) == prevFaceId && alphaStripFaceMap.get(e.getRight()) == nextFaceId)
          || (alphaStripFaceMap.get(e.getLeft()) == nextFaceId && alphaStripFaceMap.get(e.getRight()) == prevFaceId)).collect(Collectors.toList());
      List<Edge> diagonalEdgeList = new ArrayList<>();
      Edge currEdge = cs.getGraph().next(n, null, edgeList);
      Node currNode = n;
      while (currEdge != null) {
        currNode = (currEdge.getOrigin() == currNode) ? currEdge.getTarget() : currEdge.getOrigin();
        diagonalEdgeList.add(currEdge);
        currEdge = cs.getGraph().next(currNode, currEdge, edgeList);
      }
//      System.out.println(currNode.getGeometry());
      if (supportingVertexClass != NONE) {
        final Integer removedId = (supportingVertexClass == PREVIOUS) ? nextFaceId : prevFaceId;
        List<Face> facesToRemove = diagonalEdgeList.stream().map(e -> (alphaStripFaceMap.get(e.getLeft()) == removedId) ? e.getLeft() : e.getRight()).collect(Collectors.toList());
        Coordinate projection = project(currNode.getCoordinate(), psiMap.get(removedId));
//        System.out.println(psiMap.get(removedId));
//        System.out.println(currNode.getGeometry().getFactory().createPoint(projection));
//        System.out.println(facesToRemove.get(facesToRemove.size() - 1).getGeometry());
        Polygon toRemove = facesToRemove.get(facesToRemove.size() - 1).getGeometry();
        Pair<Polygon, Polygon> split = splitPolygon(toRemove, toRemove.getFactory().createLineString(new Coordinate[] { currNode.getCoordinate(), projection }));
//        System.out.println(split.getLeft());
//        System.out.println(split.getRight());
        Integer absorbingBetaSplitId = (supportingVertexClass == PREVIOUS) ? prevFaceId : nextFaceId;
        Integer absorbedBetaSplitId = (supportingVertexClass == PREVIOUS) ? nextFaceId : prevFaceId;
        Polygon absorbingBetaSplit = betaStripPolygonMap.get(absorbingBetaSplitId);
        Polygon absorbedBetaSplit = betaStripPolygonMap.get(absorbedBetaSplitId);
        int leftShared = sharedPoints(split.getLeft(), absorbingBetaSplit);
//        int rightShared = sharedPoints(split.getRight(), absorbingBetaSplit);
//        System.out.println("SHARED " + leftShared + " - " + rightShared);
        System.out.println(absorbingBetaSplit);
        Polygon absorbedPolygonPart = (leftShared == 2) ? split.getLeft() : split.getRight(); 
//        System.out.println("ABSORBED:\n"  + absorbedPolygonPart);
        List<Polygon> absorbedPolygons = facesToRemove.subList(0, facesToRemove.size() - 1).stream().map(f->f.getGeometry()).collect(Collectors.toList());
        absorbedPolygons.add(absorbedPolygonPart);
        List<Polygon> newAbsorbing = new ArrayList<>(absorbedPolygons);
        newAbsorbing.add(absorbingBetaSplit);
        betaStripPolygonMap.put(absorbingBetaSplitId, polygonUnion(newAbsorbing));
        betaStripPolygonMap.put(absorbedBetaSplitId, polygonDifference(Arrays.asList(absorbedBetaSplit), absorbedPolygons));
      }
      System.out.println("DONE");
    }
    System.out.println("betaStripPolygonMap=");
    betaStripPolygonMap.values().forEach(p->System.out.println(p)); 

    // System.out.println("FACES");
    // for (Face f : cs.getGraph().getFaces()) {
    // System.out.println(f.getGeometry());
    // }
    // System.out.println("EDGES");
    // for (Edge e : cs.getGraph().getEdges()) {
    // System.out.println(e.getGeometry());
    // }
    // System.out.println("NODES");
    // for (Node n : cs.getGraph().getNodes()) {
    // System.out.println(n.getGeometry());
    // }
    return null;
  }

  // private static Pair<Polygon, Polygon> splitPolygon(Polygon toRemove, LineString createLineString) {
  //
  // return null;
  // }

  @SuppressWarnings("rawtypes")
  public static List<Geometry> polygonize(Geometry geometry) {
    List lines = LineStringExtracter.getLines(geometry);
    Polygonizer polygonizer = new Polygonizer();
    polygonizer.add(lines);
    Collection polys = polygonizer.getPolygons();
    return Arrays.asList(GeometryFactory.toPolygonArray(polys));
  }

  public static Pair<Polygon, Polygon> splitPolygon(Polygon poly, LineString line) {
    Geometry[] snapped = GeometrySnapper.snap(poly, line, 0.001);
    Geometry nodedLinework = snapped[0].getBoundary().union(snapped[1]);
    List<Geometry> polys = polygonize(nodedLinework);
    // Only keep polygons which are inside the input
    List<Polygon> output = polys.stream().map(g -> (Polygon) g).filter(g -> poly.contains(g.getInteriorPoint())).collect(Collectors.toList());
    if (output.size() != 2) {
      System.out.println("OUTPUT WITH " + output.size());
      System.out.println("SPLIT WITH");
      System.out.println(line);
      System.out.println("NODED");
      System.out.println(nodedLinework);
      System.out.println("POLYGONIZED");
      System.out.println(polys);
    }
    return new ImmutablePair<>(output.get(0), output.get(1));
  }
  public static int sharedPoints(Geometry a, Geometry b) {
    Geometry[] snapped = GeometrySnapper.snap(a, b, 0.1);
    Set<Coordinate> ca = new HashSet<>(Arrays.asList(snapped[0].getCoordinates()));
    Set<Coordinate> cb = new HashSet<>(Arrays.asList(snapped[1].getCoordinates()));
    return (int) ca.stream().filter(c->cb.contains(c)).count();
  }

  //
  // if (feats != null) {
  // ShapefileWriter.write(feats, FOLDER_OUT_DEBUG + name);
  // }
  // }
  //
  // private static void debugExport(List<? extends IFeature> feats, String name) {
  //
  // IFeatureCollection<IFeature> featCollOut = new FT_FeatureCollection<>();
  // featCollOut.addAll(feats);
  // ShapefileWriter.write(featCollOut, FOLDER_OUT_DEBUG + name);
  // }
  public static void main(String[] args) throws IOException, SchemaException {
    // Input 1/ the input parcelles to split
    // String inputShapeFile = "src/main/resources/testData/parcelle.shp";
    // Input 3 (facultative) : the exterior of the urban block (it serves to determiner the multicurve)
    // String inputUrbanBlock = "src/main/resources/testData/ilot.shp";
    // IFeatureCollection<IFeature> featC = ShapefileReader.read(inputUrbanBlock);
    // ShapefileDataStore blockDS = new ShapefileDataStore(new File(inputUrbanBlock).toURI().toURL());
    // SimpleFeatureCollection blocks = blockDS.getFeatureSource().getFeatures();
    // String inputParcelShapeFile = "/home/julien/data/PLU_PARIS/PARCELLE_CADASTRALE/PARCELLE_13.shp";
    String inputParcelShapeFile = "/home/julien/data/PLU_PARIS/ilots_13.shp";
    String inputRoadShapeFile = "/home/julien/data/PLU_PARIS/voie/voie_l93.shp";
    String folderOut = "data/";
    // The output file that will contain all the decompositions
    String shapeFileOut = folderOut + "outflag.shp";
    (new File(folderOut)).mkdirs();
    // Reading collection
    ShapefileDataStore parcelDS = new ShapefileDataStore(new File(inputParcelShapeFile).toURI().toURL());
    SimpleFeatureCollection parcels = parcelDS.getFeatureSource().getFeatures();
    ShapefileDataStore roadDS = new ShapefileDataStore(new File(inputRoadShapeFile).toURI().toURL());
    SimpleFeatureCollection roads = roadDS.getFeatureSource().getFeatures();
    // SimpleFeatureIterator iterator = Util.select(blocks, JTS.toGeometry(parcels.getBounds())).features();
    // DefaultFeatureCollection roads = new DefaultFeatureCollection();
    // SimpleFeatureType ROADTYPE = DataUtilities.createType("road", "geom:LineString");
    // while (iterator.hasNext()) {
    // SimpleFeature f = iterator.next();
    // Util.getPolygons((Geometry) f.getDefaultGeometry()).stream().forEach(p -> roads.add(SimpleFeatureBuilder.build(ROADTYPE, new Object[] { p.getExteriorRing() }, null)));
    // }
    // iterator.close();
    SimpleFeatureIterator iterator = parcels.features();
    SimpleFeature feature = iterator.next();
    List<Polygon> polygons = Util.getPolygons((Geometry) feature.getDefaultGeometry());
    double maxDepth = 10, maxDistanceForNearestRoad = 100, minimalArea = 20, minWidth = 5, maxWidth = 40, noiseParameter = 0.1;
    // SimpleFeatureCollection result = runStraightSkeleton2(polygons.get(0), roads, maxDepth, maxDistanceForNearestRoad, minimalArea, minWidth, maxWidth, noiseParameter,
    // new MersenneTwister(42));
    //
    // ShapefileDataStoreFactory factory = new ShapefileDataStoreFactory();
    // FileDataStore dataStore = factory.createDataStore(new File(shapeFileOut).toURI().toURL());
    // dataStore.createSchema(result.getSchema());
    // String typeName = dataStore.getTypeNames()[0];
    // SimpleFeatureSource featureSource = dataStore.getFeatureSource(typeName);
    // Transaction transaction = new DefaultTransaction("create");
    // SimpleFeatureStore featureStore = (SimpleFeatureStore) featureSource;
    // featureStore.setTransaction(transaction);
    // try {
    // featureStore.addFeatures(result);
    // transaction.commit();
    // } catch (Exception problem) {
    // problem.printStackTrace();
    // transaction.rollback();
    // } finally {
    // transaction.close();
    // }
    // dataStore.dispose();
    iterator.close();
    decompose(polygons.get(0), roads, maxDepth, maxDistanceForNearestRoad, minimalArea, minWidth, maxWidth, noiseParameter, new MersenneTwister(42));
    roadDS.dispose();
    parcelDS.dispose();
    // blockDS.dispose();
  }
}
