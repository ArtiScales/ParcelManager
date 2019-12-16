package decomposition;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.geotools.geometry.jts.WKTReader2;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.MultiLineString;
import org.locationtech.jts.geom.Polygon;

public class OBBBlockDecomposition {
  /**
   * Determine the width of the parcel on road.
   * 
   * @param p
   * @return
   */
  private static double frontSideWidth(Polygon p, List<LineString> ext) {
    MultiLineString l = p.getFactory().createMultiLineString(ext.toArray(new LineString[ext.size()]));
    try {
      return (p.buffer(0.2)).intersection(l).getLength();
    } catch (Exception e) {
      try {
        return (p.buffer(0.4)).intersection(l).getLength();
      } catch (Exception e2) {
        return 0;
      }
    }
  }

  private static boolean endCondition(double area, double frontSideWidth, double maximalArea, double maximalWidth) {
    return (area <= maximalArea) || (frontSideWidth <= maximalWidth);
  }

  /**
   * Computed the splitting polygons composed by two boxes determined from the oriented bounding boxes split from a line at its middle.
   * 
   * @param pol
   *          : the input polygon
   * @param shortDirectionSplit
   *          : it is split by the short edges or by the long edge.
   * @return
   * @throws Exception
   */
  public static List<Polygon> computeSplittingPolygon(Polygon pol, List<LineString> ext, boolean shortDirectionSplit, double noise, double roadWidth,
      int decompositionLevelWithRoad, int decompositionLevel) {
    if (pol.getArea() < 1.0)
      return Collections.emptyList();
    // Determination of the bounding box
    Polygon oBB = MinimalBoundingRectangle.getRectangle(pol);
    Coordinate[] coordinates = oBB.getCoordinates();
    double dist1 = coordinates[0].distance(coordinates[1]);
    double dist2 = coordinates[1].distance(coordinates[2]);
    boolean keepCoordinateOrder = shortDirectionSplit ^ dist1 < dist2;
    Coordinate p0 = keepCoordinateOrder ? coordinates[0] : coordinates[1];
    Coordinate p1 = keepCoordinateOrder ? coordinates[1] : coordinates[2];
    Coordinate p2 = keepCoordinateOrder ? coordinates[2] : coordinates[3];
    Coordinate p3 = keepCoordinateOrder ? coordinates[3] : coordinates[0];
    double width = Math.min(dist1, dist2);
    // The noise value is determined by noise parameters and parcel width (to avoid lines that go out of parcel)
    double noiseTemp = Math.min(width / 3, noise);
    // X and Y move of the centroid
    double alpha = 0.5 + (0.5 - Math.random()) * noiseTemp;
    if (decompositionLevel < decompositionLevelWithRoad) {
      double roadAlpha = roadWidth / p0.distance(p1);
      Coordinate p4 = new Coordinate(p0.x + (alpha - roadAlpha) * (p1.x - p0.x), p0.y + (alpha - roadAlpha) * (p1.y - p0.y));
      Coordinate p5 = new Coordinate(p3.x + (alpha - roadAlpha) * (p2.x - p3.x), p3.y + (alpha - roadAlpha) * (p2.y - p3.y));
      Coordinate p6 = new Coordinate(p0.x + (alpha + roadAlpha) * (p1.x - p0.x), p0.y + (alpha + roadAlpha) * (p1.y - p0.y));
      Coordinate p7 = new Coordinate(p3.x + (alpha + roadAlpha) * (p2.x - p3.x), p3.y + (alpha + roadAlpha) * (p2.y - p3.y));
      ext.add(pol.getFactory().createLineString(new Coordinate[] { p4, p5, p7, p6, p4 }));
      return Arrays.asList(pol.getFactory().createPolygon(new Coordinate[] { p0, p4, p5, p3, p0 }), pol.getFactory().createPolygon(new Coordinate[] { p6, p1, p2, p7, p6 }));
    }
    Coordinate p4 = new Coordinate(p0.x + alpha * (p1.x - p0.x), p0.y + alpha * (p1.y - p0.y));
    Coordinate p5 = new Coordinate(p3.x + alpha * (p2.x - p3.x), p3.y + alpha * (p2.y - p3.y));
    return Arrays.asList(pol.getFactory().createPolygon(new Coordinate[] { p0, p4, p5, p3, p0 }), pol.getFactory().createPolygon(new Coordinate[] { p4, p1, p2, p5, p4 }));
  }

  /**
   * Split the input polygon by another.
   * 
   * @param poly1
   * @param poly2
   * @return
   */
  public static List<Polygon> split(Polygon poly1, Polygon poly2) {
    Geometry intersection = poly1.intersection(poly2);
    if (intersection instanceof Polygon)
      return Arrays.asList((Polygon) intersection);
    List<Polygon> res = new ArrayList<>(intersection.getNumGeometries());
    for (int i = 0; i < intersection.getNumGeometries(); i++) {
      Geometry geom = intersection.getGeometryN(i);
      if (geom instanceof Polygon)
        res.add((Polygon) geom);
    }
    return res;
  }

  /**
   * Split the input polygons by a list of polygons.
   * 
   * @param poly
   * @param polygones
   * @return
   */
  public static List<Polygon> split(Polygon poly, List<Polygon> polygons) {
    return polygons.stream().flatMap(p -> split(poly, p).stream()).collect(Collectors.toList());
  }

  public static boolean hasRoadAccess(Polygon poly, List<LineString> ext) {
    return poly.intersects(poly.getFactory().createMultiLineString(ext.toArray(new LineString[ext.size()])).buffer(0.5));
  }

  public static Tree<Pair<Polygon, Integer>> decompose(Polygon polygon, List<LineString> ext, double maximalArea, double maximalWidth, double noise, double epsilon, double roadWidth,
      boolean forceRoadAccess, int decompositionLevelWithRoad, int decompositionLevel) {
    double area = polygon.getArea();
    double frontSideWidth = frontSideWidth(polygon, ext);
    System.out.println(
        "endCondition for " + area + " - " + frontSideWidth + " - " + maximalArea + " - " + maximalWidth + " => " + endCondition(area, frontSideWidth, maximalArea, maximalWidth));
    if (endCondition(area, frontSideWidth, maximalArea, maximalWidth)) {
      // System.out.println("endCondition for " + area + " - " + frontSideWidth + " - " + maximalArea + " - " + maximalWidth);
      return new Tree<>(new ImmutablePair<>(polygon, decompositionLevel));
    }
    // Determination of splitting polygon (it is a splitting line in the article)
    List<Polygon> splittingPolygon = computeSplittingPolygon(polygon, ext, true, noise, roadWidth, decompositionLevelWithRoad, decompositionLevel);
    System.out.println(polygon);
    for (Polygon sp : splittingPolygon)
      System.out.println(sp);
    // Split into polygon
    List<Polygon> splitPolygons = split(polygon, splittingPolygon);
    // If a parcel has no road access, there is a probability to make a perpendicular split
    // Probability to make a perpendicular split if no road access or a little probabibility epsilon
    if ((forceRoadAccess && ((!hasRoadAccess(splitPolygons.get(0), ext) || !hasRoadAccess(splitPolygons.get(1), ext)))) || (Math.random() < epsilon)) {
      // Same steps but with different splitting geometries
      splittingPolygon = computeSplittingPolygon(polygon, ext, false, noise, roadWidth, decompositionLevelWithRoad, decompositionLevel);
      splitPolygons = split(polygon, splittingPolygon);
    }
    // All split polygons are split and results added to the output
//    return splitPolygons.stream()
//        .flatMap(pol -> decompose(pol, ext, maximalArea, maximalWidth, noise, epsilon, roadWidth, forceRoadAccess, decompositionLevelWithRoad, decompositionLevel + 1).stream())
//        .collect(Collectors.toList());
    return new Tree<>(new ImmutablePair<>(polygon, decompositionLevel), splitPolygons.stream().map(pol -> decompose(pol, ext, maximalArea, maximalWidth, noise, epsilon, roadWidth, forceRoadAccess, decompositionLevelWithRoad, decompositionLevel + 1)).collect(Collectors.toList()));
  }
  

  public static void main(String[] args) throws Exception {
    WKTReader2 reader = new WKTReader2();
    // Polygon polygon = (Polygon) reader.read("Polygon ((649379.16181726532522589 6885977.27824439946562052, 649350.48205229756422341 6885958.32196427881717682,
    // 649321.11027246410958469 6885938.80467639211565256, 649303.95970264670904726 6885934.63994785025715828, 649299.7298646034905687 6885933.10889059863984585,
    // 649279.80155124072916806 6885928.01213758252561092, 649278.08649017277639359 6885928.26078001130372286, 649254.9299936534371227 6885940.80999842565506697,
    // 649253.41156708274502307 6885941.81322594825178385, 649245.6222488796338439 6885949.33359128795564175, 649243.25362194620538503 6885950.87816456519067287,
    // 649227.81945605424698442 6885951.60343707911670208, 649187.53828619793057442 6885942.99358111713081598, 649166.85899228765629232 6885934.77850280236452818,
    // 649142.94809431175235659 6885925.54649946372956038, 649127.30002219369634986 6885919.42267288453876972, 649124.69985389441717416 6885917.91074120998382568,
    // 649121.95153685880359262 6885915.34351161867380142, 649110.30871332378592342 6885903.51211098674684763, 649105.43352986394893378 6885898.41667408682405949,
    // 649104.50972036807797849 6885898.06891389843076468, 649103.23792614869307727 6885898.03564407397061586, 649098.80896453245077282 6885899.62069576047360897,
    // 649068.16007484716828912 6885911.05781747121363878, 649042.4395647298078984 6885910.4285151157528162, 649021.43179685168433934 6885909.73544138204306364,
    // 649006.39100269158370793 6885908.67828329000622034, 648980.51974246872123331 6885906.70480725727975368, 648966.13694621750619262 6885905.68642424512654543,
    // 648960.88724910700693727 6885905.35470734070986509, 648954.09799244673922658 6885916.84835541807115078, 648948.6472744015045464 6885926.00563614070415497,
    // 648941.88857388042379171 6885937.64361888356506824, 648941.09884283901192248 6885937.58387456741183996, 648934.1684974692761898 6885937.25593313481658697,
    // 648902.92821806040592492 6885951.16829667147248983, 648901.28233664226718247 6885945.19914575107395649, 648895.31165173125918955 6885922.04006222821772099,
    // 648887.68662045325618237 6885892.78957577049732208, 648882.89304037112742662 6885874.65842700749635696, 648864.60117630031891167 6885877.00045717414468527,
    // 648838.52245823969133198 6885883.79372073616832495, 648803.90249998506624252 6885892.36457657627761364, 648785.86744919035118073 6885895.63884066883474588,
    // 648772.9922788969706744 6885897.35473182797431946, 648758.44334077928215265 6885899.02990567497909069, 648736.72319707018323243 6885903.70507960394024849,
    // 648711.66335186280775815 6885909.17745192162692547, 648699.76282807067036629 6885912.01930461917072535, 648676.94807372998911887 6885919.31814761552959681,
    // 648642.45297004969324917 6885932.01519389357417822, 648633.30327224638313055 6885935.21085355617105961, 648621.6310356508474797 6885938.23991335928440094,
    // 648617.22759887552820146 6885939.39134455472230911, 648614.15827018232084811 6885941.00915062334388494, 648589.17345072887837887 6885958.97159373946487904,
    // 648582.61000003374647349 6885963.73476203996688128, 648573.62022965610958636 6885969.28699424490332603, 648567.41953696380369365 6885972.93472934141755104,
    // 648555.88249704567715526 6885978.82111870404332876, 648546.31312593107577413 6885983.28858901839703321, 648531.83813801920041442 6885989.94641486089676619,
    // 648516.50706216343678534 6885995.65540913213044405, 648507.16215966967865825 6885997.42937471903860569, 648490.5097890020115301 6885999.19073465000838041,
    // 648462.07194013171829283 6886001.91381431650370359, 648424.63282032625284046 6886005.60726574063301086, 648413.54218836058862507 6886006.64068060740828514,
    // 648410.68932305602356791 6886008.75715949852019548, 648407.79700080573093146 6886028.52489993441849947, 648404.69673641608096659 6886049.55131215788424015,
    // 648403.5478252952452749 6886057.53619550261646509, 648395.5681706489995122 6886068.96331732720136642, 648384.78957414883188903 6886084.44172395952045918,
    // 648375.90880282782018185 6886097.23384544160217047, 648369.03917155368253589 6886108.57322137989103794, 648368.31330168631393462 6886110.74854505620896816,
    // 648365.13954939041286707 6886130.07395684439688921, 648361.13640992343425751 6886154.7009635241702199, 648358.0890898210927844 6886172.63498033303767443,
    // 648356.56745828688144684 6886182.23593777604401112, 648357.06130120519082993 6886192.50843185000121593, 648357.45973779330961406 6886197.02047965582460165,
    // 648361.75218930118717253 6886208.80496127344667912, 648374.75733314314857125 6886244.56880985852330923, 648385.55686591635458171 6886273.12299272697418928,
    // 648395.42980898427776992 6886299.39431426115334034, 648412.7925172500545159 6886335.49745313636958599, 648429.84202855720650405 6886361.92712363041937351,
    // 648448.35218903608620167 6886390.74619680549949408, 648471.8369702126365155 6886420.25496824085712433, 648500.4929374068742618 6886447.92699874378740788,
    // 648529.17682714632246643 6886474.66465974599123001, 648562.80987461109180003 6886506.63027538917958736, 648577.35067726403940469 6886520.61475815530866385,
    // 648583.51137829758226871 6886522.38389402069151402, 648622.84819853468798101 6886490.11264646425843239, 648660.53843308286741376 6886459.14651049114763737,
    // 648658.63725373637862504 6886399.87087189313024282, 648655.46997967781499028 6886295.21679418720304966, 648653.62826706818304956 6886227.03182873874902725,
    // 648654.38782739406451583 6886226.16865938901901245, 648666.21834495186340064 6886217.17674592137336731, 648678.35666338109876961 6886208.28221946861594915,
    // 648694.16704871959518641 6886199.82219598907977343, 648717.96960102114826441 6886187.04249325674027205, 648744.33991326694376767 6886172.93878176994621754,
    // 648780.77250073908362538 6886153.09578192699700594, 648812.80753339733928442 6886135.8501349464058876, 648841.5313569288700819 6886120.1132807107642293,
    // 648875.79964498383924365 6886101.69148231204599142, 648920.14847474463749677 6886091.93379457388073206, 648931.86950911919120699 6886089.48325486946851015,
    // 648982.94692125252913684 6886078.72111394628882408, 649086.13064931135158986 6886109.14081902801990509, 649134.99021079274825752 6886123.63582586869597435,
    // 649185.40674177673645318 6886087.61170822475105524, 649219.2509035465773195 6886063.20084820687770844, 649286.53069255221635103 6886072.55230130068957806,
    // 649359.45657330728136003 6886082.48894198145717382, 649408.01922338618896902 6886089.01425970811396837, 649403.31828265869989991 6886086.24157305061817169,
    // 649417.54883116891141981 6886056.62078983429819345, 649408.70864871342200786 6886051.80454400833696127, 649406.36665184888988733 6886035.55334458872675896,
    // 649404.20660132542252541 6886026.71903944574296474, 649402.26285339531023055 6886020.88582434691488743, 649399.79974495805799961 6886015.82459462527185678,
    // 649396.30879928136710078 6886009.38210996706038713, 649386.79939044394996017 6885994.09468733984977007, 649373.70180196687579155 6885985.4453320661559701,
    // 649373.00930309621617198 6885984.8063229089602828, 649372.56328345078509301 6885983.94270770903676748, 649372.77321662614122033 6885982.8842598544433713,
    // 649373.6375416403170675 6885981.45303847547620535, 649374.43836226977873594 6885981.11234666593372822, 649375.30787167639937252 6885981.10471723135560751,
    // 649376.30676846520509571 6885981.68542742449790239, 649379.16181726532522589 6885977.27824439946562052),(649090.18097044946625829 6886000.89741663541644812,
    // 649109.81043964333366603 6886053.30988282430917025, 649067.58794361096806824 6886068.91984335239976645, 649047.9800604545744136 6886016.50732026528567076,
    // 649090.18097044946625829 6886000.89741663541644812))");
    Polygon polygon = (Polygon) reader.read(
        "Polygon ((932178.11999999999534339 6703143.58000000007450581, 932124.42000000004190952 6702979.25, 932092.9599999999627471 6702884.13999999966472387, 932058.7099999999627471 6702778.75999999977648258, 932025.80000000004656613 6702679.16999999992549419, 932026.27000000001862645 6702660.50999999977648258, 932026.22999999998137355 6702651.4599999999627471, 932025.4599999999627471 6702641.30999999959021807, 932024.26000000000931323 6702612.34999999962747097, 932024.5 6702602.36000000033527613, 932025.2900000000372529 6702582.70000000018626451, 932025.51000000000931323 6702567.00999999977648258, 932032.47999999998137355 6702526.5400000000372529, 932037.18999999994412065 6702495.08000000007450581, 932038.68000000005122274 6702469.20000000018626451, 932044.67000000004190952 6702442.21999999973922968, 932050.5 6702417.12999999988824129, 932055.55000000004656613 6702414.12000000011175871, 932059.85999999998603016 6702413.86000000033527613, 932074.36999999999534339 6702424.74000000022351742, 932077.52000000001862645 6702422.74000000022351742, 932078.69999999995343387 6702413.04999999981373549, 932061.33999999996740371 6702390.76999999955296516, 932045.60999999998603016 6702372.57000000029802322, 932053.15000000002328306 6702367.94000000040978193, 932071.25 6702386.08000000007450581, 932080.09999999997671694 6702392.63999999966472387, 932090.67000000004190952 6702396.76999999955296516, 932102.16000000003259629 6702410.7099999999627471, 932105.43999999994412065 6702414.34999999962747097, 932108.22999999998137355 6702416.2099999999627471, 932111.36999999999534339 6702417.09999999962747097, 932113.30000000004656613 6702416.69000000040978193, 932115.43999999994412065 6702415.96999999973922968, 932135.77000000001862645 6702399.30999999959021807, 932137.65000000002328306 6702396.80999999959021807, 932138.48999999999068677 6702394.94000000040978193, 932137.68000000005122274 6702391.69000000040978193, 932118.56999999994877726 6702361.7900000000372529, 932088.31999999994877726 6702314.0400000000372529, 932092.28000000002793968 6702311.63999999966472387, 932085.16000000003259629 6702296.99000000022351742, 932081.42000000004190952 6702282.75, 932076.89000000001396984 6702263.87999999988824129, 932074.98999999999068677 6702236.58999999985098839, 932073.69999999995343387 6702222.67999999970197678, 932069.67000000004190952 6702208.75, 932067.88000000000465661 6702202.25, 932067.17000000004190952 6702194.03000000026077032, 932067.36999999999534339 6702183.36000000033527613, 932072.68999999994412065 6702176.11000000033527613, 932087.71999999997206032 6702164.17999999970197678, 932110.71999999997206032 6702153.5400000000372529, 932117.02000000001862645 6702149.67999999970197678, 932119.69999999995343387 6702146.58000000007450581, 932122.7900000000372529 6702142.26999999955296516, 932124.21999999997206032 6702138.25, 932125 6702133.41000000014901161, 932108 6702111.59999999962747097, 932100.18999999994412065 6702107.91999999992549419, 932095.93000000005122274 6702113.15000000037252903, 932094.84999999997671694 6702114.58000000007450581, 932093.66000000003259629 6702114.75999999977648258, 932091.93999999994412065 6702114.12999999988824129, 932086.41000000003259629 6702108.12000000011175871, 932084.13000000000465661 6702105.63999999966472387, 932080.23999999999068677 6702106.67999999970197678, 932051.78000000002793968 6702114.33000000007450581, 932022.23999999999068677 6702122.26999999955296516, 932022.31999999994877726 6702124.98000000044703484, 932022.35999999998603016 6702126.19000000040978193, 932023.02000000001862645 6702148.13999999966472387, 932019.47999999998137355 6702162.2900000000372529, 932007.14000000001396984 6702184.94000000040978193, 932002.05000000004656613 6702192.94000000040978193, 931988.56999999994877726 6702209.08999999985098839, 931983.25 6702216.99000000022351742, 931978.27000000001862645 6702226.96999999973922968, 931866.52000000001862645 6702346.17999999970197678, 931789.26000000000931323 6702425.0400000000372529, 931673.27000000001862645 6702548.94000000040978193, 931641.69999999995343387 6702577.45000000018626451, 931694.65000000002328306 6702540.75999999977648258, 931722.11999999999534339 6702518.99000000022351742, 931773.96999999997206032 6702619.20000000018626451, 931805.88000000000465661 6702677.08999999985098839, 931825.05000000004656613 6702712.66000000014901161, 931833.10999999998603016 6702727.61000000033527613, 931667.39000000001396984 6702822.55999999959021807, 931573.57999999995809048 6702875.75, 931628.17000000004190952 6703031.07000000029802322, 931661.34999999997671694 6703141.16999999992549419, 931705.06999999994877726 6703289.99000000022351742, 931813.90000000002328306 6703256.67999999970197678, 932178.11999999999534339 6703143.58000000007450581))");
    // LineString ext = (LineString) reader.read("LineStringZ (931732.69999999995343387 6703401.90000000037252903 264.30000000000001137, 931719.80000000004656613
    // 6703356.90000000037252903 264, 931632.69999999995343387 6703069.59999999962747097 264.80000000000001137, 931609.59999999997671694 6702999.79999999981373549 264,
    // 931562.09999999997671694 6702849.20000000018626451 263.60000000000002274, 931522.40000000002328306 6702708.40000000037252903 264.10000000000002274, 931512.59999999997671694
    // 6702672.5 264.89999999999997726, 931507.30000000004656613 6702652.20000000018626451 265.10000000000002274, 931505.40000000002328306 6702647.09999999962747097 264.5,
    // 931502.40000000002328306 6702642.59999999962747097 264.5, 931499.5 6702639.59999999962747097 264.39999999999997726, 931496.90000000002328306 6702636.59999999962747097
    // 264.30000000000001137, 931494.09999999997671694 6702635.09999999962747097 263.69999999999998863, 931489.80000000004656613 6702634 263.39999999999997726)");
    LineString ext = polygon.getExteriorRing();
    List<LineString> list = new ArrayList<>();
    list.add(ext);
    // List<Polygon> splittingPolygon = computeSplittingPolygon(
    // polygon,
    // true,
    // 0.5);
    // split(polygon, splittingPolygon).stream().forEach(p->System.out.println(p));
    Tree<Pair<Polygon, Integer>> tree = decompose(polygon, list, 10000, 7, 0, 0, 5, false, 2, 0);
    DescriptiveStatistics dS = new DescriptiveStatistics();
    tree.childrenStream().forEach(c->dS.addValue(c.getValue()));
    int depth = (int) dS.getPercentile(50);
    System.out.println("DEPTH = " + depth);
    tree.childrenStream().forEach(p -> System.out.println(p.getValue() + " " + p.getKey()));
  }
}
