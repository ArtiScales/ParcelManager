package decomposition;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

import javax.vecmath.Point3d;

import org.apache.commons.lang3.ArrayUtils;
import org.locationtech.jts.algorithm.Orientation;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.Polygon;
import org.twak.camp.Corner;
import org.twak.camp.Edge;
import org.twak.camp.Machine;
import org.twak.camp.OffsetSkeleton;
import org.twak.camp.Output;
import org.twak.camp.Output.Face;
import org.twak.camp.Output.SharedEdge;
import org.twak.camp.Skeleton;
import org.twak.utils.collections.Loop;
import org.twak.utils.collections.LoopL;

import decomposition.graph.Node;
import decomposition.graph.TopologicalGraph;

/**
 * Squelette droit pondéré calculé d'après la librairie campskeleton. Possibilité de pondérer ce squelette. Le résultat est présenté sous la forme d'une carte topo. Les faces les
 * arcs et les noeuds sont renseignés ainsi que la position des arcs par rapport aux différentes faces. Possibilité d'obtenir seulement les arcs intérieurs.
 * 
 * @author MBrasebin
 * 
 */
public class CampSkeleton {
  private Polygon p;

  /**
   * Calcul du squelette droit, le résultat est obtenu par le getCarteTopo() Le même poids est appliqué à tous les arcs
   * 
   * @param p
   */
  public CampSkeleton(Polygon p) {
    this(p, null, 0);
  }

  /**
   * Straight skelton calculation with cap parameters that defines a perpendicular distance from the block contours
   * 
   * @param p
   * @param cap
   */
  public CampSkeleton(Polygon p, double cap) {
    this(p, null, cap);
  }

  public CampSkeleton(Polygon p, double[] angles) {
    this(p, angles, 0);
  }

  /**
   * Calcul du squelette droit, le résultat est obtenu par le getCarteTopo() Une pondération est appliquée
   * 
   * @param p
   * @param angles
   *          : la pondération appliquée pour le calcul de squelette droit. Le nombre d'élément du tableaux doit être au moins égal au nombre de côté (intérieurs inclus du
   *          polygone)
   */
  public CampSkeleton(Polygon inputPolygon, double[] angles, double cap) {
    this.p = inputPolygon;// (Polygon) TopologyPreservingSimplifier.simplify(inputPolygon, 0.1);
    Skeleton s = buildSkeleton(this.p, angles);
    if (cap != 0) {
      s.capAt(cap);
    }
    s.skeleton();
    this.graph = convertOutPut(s.output);
  }

  public static Skeleton buildSkeleton(Polygon p, double[] angles) {
    return new Skeleton(buildEdgeLoops(p, angles), true);
  }

  private static int setMachine(Edge e, double[] angles, int countAngle, Machine machine) {
    if (angles == null) {
      e.machine = machine;
    } else {
      e.machine = new Machine(angles[countAngle]);
    }
    return countAngle + 1;
  }

  public static LoopL<Edge> buildEdgeLoops(Polygon p, double[] angles) {
    int countAngle = 0;
    Machine directionMachine = new Machine();
    LoopL<Edge> input = new LoopL<Edge>();
    Loop<Edge> loop = new Loop<Edge>();
    for (Edge e : convertLineString(p.getExteriorRing(),false)) {
      loop.append(e);
      countAngle = setMachine(e, angles, countAngle, directionMachine);
    }
    input.add(loop);
    for (int i = 0; i < p.getNumInteriorRing(); i++) {
      Loop<Edge> loopIn = new Loop<Edge>();
      input.add(loopIn);
      for (Edge e : convertLineString(p.getInteriorRingN(i),true)) {
        loopIn.append(e);
        countAngle = setMachine(e, angles, countAngle, directionMachine);
      }
    }
    return input;
  }

  private static List<Edge> convertLineString(LineString ring, boolean reverse) {
    Coordinate[] coordinates = ring.getCoordinates();
    if (!Orientation.isCCW(coordinates)^reverse)
      ArrayUtils.reverse(coordinates);
    return fromDPLToEdges(coordinates);
  }

  /**
   * Convertit la sortie de l'algorithme de squelette droit.
   * 
   * @TODO : il subsite un problème, parfois, 2 arrêtes de 2 faces sont équivalentes à 1 arrête d'une autre face.
   * @param out
   * @return
   */
  private static TopologicalGraph convertOutPut(Output out) {
    GeometryFactory factory = new GeometryFactory();
    // On créer la carte Toppo
    TopologicalGraph graph = new TopologicalGraph();
    // Liste des arrêtes rencontrées
    List<SharedEdge> lSharedEdges = new ArrayList<SharedEdge>();
    List<Point3d> lPoints = new ArrayList<Point3d>();
    // Pour chaque face du squelette
    for (Face f : new HashSet<>(out.faces.values())) {
      // On créer une face de la carte topo
      decomposition.graph.Face fTopo = new decomposition.graph.Face();
      fTopo.setPolygon(convertFace(f.points, factory));
      // On récupère les arrête de la face
      LoopL<SharedEdge> lSE = f.edges;
      // On parcourt les arrêtes
      int nbSE = lSE.size();
      for (int i = 0; i < nbSE; i++) {
        for (Loop<SharedEdge> loopSE : lSE) {
          for (SharedEdge se : loopSE) {
            // Est ce une arrête déjà rencontrée ?
            int indexArc = lSharedEdges.indexOf(se);
            if (indexArc == -1) {
              // Non : on doit générer les informations add hoc
              // On récupère les sommets initiaux et finaux
              Point3d p = getStart(se, f);
              Point3d p2 = getEnd(se, f);
              if (p == null || p2 == null) {
                continue;
              }
              // On la rajoute à la liste des arrêtes existants
              lSharedEdges.add(se);
              // S'agit il de sommets déjà rencontrés ?
              int indexP1 = lPoints.indexOf(p);
              int indexP2 = lPoints.indexOf(p2);
              // Non ! on génère la sommet
              if (indexP1 == -1) {
                // On l'ajoute à la liste des sommets rencontrés
                lPoints.add(p);
                // On met à jour le sommet considéré
                indexP1 = lPoints.size() - 1;
                // On génère un noeud
                Node n = new Node(toCoordinate(p));
                // On l'ajoute à la liste des noeuds et à la carte topo
                // lNoeuds.add(n);
                graph.getNodes().add(n);
              }
              // idem avec le second sommet
              if (indexP2 == -1) {
                lPoints.add(p2);
                indexP2 = lPoints.size() - 1;
                Node n = new Node(toCoordinate(p2));
                // lNoeuds.add(n);
                graph.getNodes().add(n);
              }
              // On génère l'arc
              decomposition.graph.Edge a = new decomposition.graph.Edge(graph.getNodes().get(indexP1), graph.getNodes().get(indexP2),
                  factory.createLineString(new Coordinate[] { graph.getNodes().get(indexP1).getCoordinate(), graph.getNodes().get(indexP2).getCoordinate() }));
              // On ajoute l'arc
              graph.getEdges().add(a);
              // cT.addArc(a);
              indexArc = lSharedEdges.size() - 1;
            }
            // On affecte le côté d'où se trouve la face
            // Normalement la carte topo met ça à jour du côté de la face
            decomposition.graph.Edge a = graph.getEdges().get(indexArc);
            boolean isOnRight = (f.equals(se.right));
            boolean isOnLeft = (f.equals(se.left));
            if (isOnRight) {
              a.setRight(fTopo);
            } else if (isOnLeft) {
              a.setLeft(fTopo);
            } else {
              System.out.println("QUICK FIX APPLIED: face is neither at the right or the left of a polygon");
              if (se.right == null) {
                a.setRight(fTopo);
              } else if (se.left == null) {
                a.setLeft(fTopo);
              } else {
                System.out.println("Null both side");
              }
            }
          }
        }
      }
      // On ajoute la faces à la carte topo
      graph.getFaces().add(fTopo);
    }
    // cT.fusionNoeuds(0.2);
    // cT.rendPlanaire(0.5);
    return graph;
  }

  public static Polygon convertFace(LoopL<Point3d> points, GeometryFactory factory) {
    LinearRing exterior = null;
    List<LinearRing> interior = new ArrayList<>();
    for (Loop<Point3d> lP : points) {
      LinearRing ring = convertPoint3dLoop(lP, factory);
      if (exterior == null) {
        exterior = ring;
      } else {
        interior.add(ring);
      }
    }
    return factory.createPolygon(exterior, interior.toArray(new LinearRing[interior.size()]));
  }

  public static Point3d getStart(SharedEdge se, Face ref) {
    return se.getStart(se.right);
  }

  public static Point3d getEnd(SharedEdge se, Face ref) {
    return se.getEnd(se.right);
  }

  /**
   * Convertit une liste de sommets formant un cycle en arrêtes
   * 
   * @param dpl
   * @return
   */
  public static List<Edge> fromDPLToEdges(Coordinate[] dpl) {
    int nbPoints = dpl.length;
    List<Edge> lEOut = new ArrayList<Edge>();
    List<Corner> lC = new ArrayList<Corner>();
    for (int i = 0; i < nbPoints - 1; i++) {
      lC.add(fromPositionToCorner(dpl[i]));
    }
    for (int i = 0; i < nbPoints - 2; i++) {
      lEOut.add(new Edge(lC.get(i), lC.get(i + 1)));
    }
    lEOut.add(new Edge(lC.get(nbPoints - 2), lC.get(0)));
    return lEOut;
  }

  /**
   * Convertit un positon en corner.
   * 
   * @param dp
   * @return
   */
  private static Corner fromPositionToCorner(Coordinate dp) {
    // if (dp.getDimension() == 2) {
    // return new Corner(dp.getX(), dp.getY(), 0);
    // }
    return new Corner(dp.getX(), dp.getY(), 0);
  }

  /**
   * 
   */
  private static LinearRing convertPoint3dLoop(Loop<Point3d> lC, GeometryFactory factory) {
    List<Coordinate> dpl = new ArrayList<>(lC.count());
    for (Point3d c : lC) {
      dpl.add(toCoordinate(c));
    }
    dpl.add(dpl.get(0));// close the ring
    return factory.createLinearRing(dpl.toArray(new Coordinate[dpl.size()]));
  }

  private static Coordinate toCoordinate(Point3d c) {
    return new Coordinate(c.x, c.y, c.z);
  }

  private TopologicalGraph graph = null;

  /**
   * 
   * @return perment d'obtenir la carte topo générée
   */
  public TopologicalGraph getGraph() {
    return this.graph;
  }

  /**
   * 
   * @return extrait les arcs extérieurs du polygone
   */
  public List<decomposition.graph.Edge> getExteriorEdges() {
    return this.graph.getEdges().stream().filter(p -> (p.getRight() == null) ^ (p.getLeft() == null)).collect(Collectors.toList());
  }

  /**
   * 
   * @return extrait les arcs générés lors du calcul du squelette droit
   */
  public List<decomposition.graph.Edge> getInteriorEdges() {
    return this.graph.getEdges().stream().filter(p -> (p.getRight() != null) && (p.getLeft() != null)).collect(Collectors.toList());
  }

  /**
   * 
   * @return extrait les arcs générés ne touchant pas la frontière du polygone
   */
  public List<decomposition.graph.Edge> getIncludedEdges() {
    Geometry geom = p.buffer(-0.5);
    return this.graph.getEdges().stream().filter(p -> geom.contains(p.getGeometry())).collect(Collectors.toList());
  }

  private static LinearRing convertCornerLoop(Loop<Corner> lC, GeometryFactory factory) {
    List<Coordinate> dpl = new ArrayList<>(lC.count());
    for (Corner c : lC) {
      dpl.add(new Coordinate(c.x, c.y, c.z));
    }
    dpl.add(dpl.get(0));// close the ring
    return factory.createLinearRing(dpl.toArray(new Coordinate[dpl.size()]));
  }

  public static Polygon convertCornerLoops(LoopL<Corner> points, GeometryFactory factory) {
    LinearRing exterior = null;
    List<LinearRing> interior = new ArrayList<>();
    for (Loop<Corner> lP : points) {
      LinearRing ring = convertCornerLoop(lP, factory);
      if (exterior == null) {
        exterior = ring;
      } else {
        interior.add(ring);
      }
    }
    return factory.createPolygon(exterior, interior.toArray(new LinearRing[interior.size()]));
  }

  public static Polygon shrink(Polygon p, double value) {
    return convertCornerLoops(OffsetSkeleton.shrink(buildEdgeLoops(p, null), value), p.getFactory());
  }
}
