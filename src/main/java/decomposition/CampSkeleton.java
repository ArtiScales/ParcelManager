package decomposition;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.vecmath.Point3d;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.Polygon;

import decomposition.graph.Node;
import decomposition.graph.TopologicalGraph;
import straightskeleton.Corner;
import straightskeleton.Edge;
import straightskeleton.Machine;
import straightskeleton.Output;
import straightskeleton.Output.Face;
import straightskeleton.Output.SharedEdge;
import straightskeleton.Skeleton;
import utils.Loop;
import utils.LoopL;

/**
 * Squelette droit pondéré calculé d'après la librairie campskeleton. Possibilité de pondérer ce squelette. Le résultat est présenté sous la forme d'une carte topo. Les faces les
 * arcs et les noeuds sont renseignés ainsi que la position des arcs par rapport aux différentes faces. Possibilité d'obtenir seulement les arcs intérieurs
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
  public CampSkeleton(Polygon p, double[] angles, double cap) {

    this.p = p;

    int countAngle = 0;

    Coordinate[] dpl = p.getCoordinates();

    for (Coordinate dp : dpl) {
      dp.setZ(0);
    }

    // PlanEquation pe = new ApproximatedPlanEquation(p);
    //
    // if (pe.getNormale().getZ() < 0) {
    //
    // p = (Polygon) p.reverse();
    //
    // }

    Machine directionMachine = new Machine();

    LoopL<Edge> input = new LoopL<Edge>();

    LineString rExt = p.getExteriorRing();

    Loop<Edge> loop = new Loop<Edge>();

    List<Edge> lEExt = fromDPLToEdges(rExt.getCoordinates());

    for (Edge e : lEExt) {
      loop.append(e);
      if (angles == null) {
        e.machine = directionMachine;
      } else {
        e.machine = new Machine(angles[countAngle++]);
      }
    }

    input.add(loop);

    for (int i = 0; i < p.getNumInteriorRing(); i++) {
      LineString rInt = p.getInteriorRingN(i);
      Loop<Edge> loopIn = new Loop<Edge>();
      input.add(loopIn);
      List<Edge> lInt = fromDPLToEdges(rInt.getCoordinates());
      for (Edge e : lInt) {
        loop.append(e);
        if (angles == null) {
          e.machine = directionMachine;
        } else {
          e.machine = new Machine(angles[countAngle++]);
        }
      }
    }

    Skeleton s = new Skeleton(input, cap);

    s.skeleton();
    Output out = s.output;
    this.graph = convertOutPut(out);
  }

  /**
   * Convertit la sortie de l'algorithme de squelette droit
   * 
   * @TODO : il subsite un problème, parfois, 2 arrêtes de 2 faces sont équivalentes à 1 arrête d'une autre face.
   * @param out
   * @return
   */
  private static TopologicalGraph convertOutPut(Output out) {
    GeometryFactory factory = new GeometryFactory();
    // On créer la carte Toppo
    TopologicalGraph graph = new TopologicalGraph();
    // On récupère les faces
    Map<Corner, Face> faces = out.faces;

    List<Face> collFaces = new ArrayList<>();
    collFaces.addAll(faces.values());

    /*
     * bouclei: for(int i=0;i < collFaces.size(); i++){ for(int j=i+1;j < collFaces.size(); j++){ if(collFaces.get(i).equals(collFaces.get(j))){ collFaces.remove(i); i--;
     * logger.warn("Duplicate faces found : auto-remove applied : " + CampSkeleton.class); continue bouclei; }
     * 
     * } }
     */

    // Liste des arrêtes rencontrées
    List<SharedEdge> lSharedEdges = new ArrayList<SharedEdge>();
    List<decomposition.graph.Edge> lArcs = new ArrayList<>();

    // Liste des noeuds rencontres

    List<Node> lNoeuds = new ArrayList<Node>();
    List<Point3d> lPoints = new ArrayList<Point3d>();

    // Pour chaque face du squelette
    for (Face f : collFaces) {

      // On créer une face de la carte topo
      decomposition.graph.Face fTopo = new decomposition.graph.Face();

      // On génère la géométrie de la face
      LoopL<Point3d> loopLPoint = f.points;

      // On récupère la géométrie du polygone
      // Polygon poly = new GM_Polygon();
      LinearRing exterior = null;
      List<LinearRing> interior = new ArrayList<>();
      for (Loop<Point3d> lP : loopLPoint) {

        List<Coordinate> dpl = convertLoopCorner(lP);

        // Il ne ferme pas ses faces
        dpl.add(dpl.get(0));

        if (exterior == null) {
          exterior = factory.createLinearRing(dpl.toArray(new Coordinate[dpl.size()]));
        } else {
          interior.add(factory.createLinearRing(dpl.toArray(new Coordinate[dpl.size()])));
        }

      }
      Polygon poly = factory.createPolygon(exterior, interior.toArray(new LinearRing[interior.size()]));
      // On affecte la géomégtrie
      fTopo.setPolygon(poly);
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
                Node n = new Node(fromCornerToPosition(p));
                // On l'ajoute à la liste des noeuds et à la
                // carte topo
                lNoeuds.add(n);
                graph.getNodes().add(n);
              }
              // idem avec le second sommet
              if (indexP2 == -1) {
                lPoints.add(p2);
                indexP2 = lPoints.size() - 1;
                Node n = new Node(fromCornerToPosition(p2));
                lNoeuds.add(n);
                graph.getNodes().add(n);
              }
              // On génère l'arc

              // On génère sa géométrie
              List<Coordinate> dpl = new ArrayList<>();
              dpl.add(lNoeuds.get(indexP1).getCoordinate());
              dpl.add(lNoeuds.get(indexP2).getCoordinate());

              decomposition.graph.Edge a = new decomposition.graph.Edge(lNoeuds.get(indexP1), lNoeuds.get(indexP2),
                  factory.createLineString(dpl.toArray(new Coordinate[dpl.size()])));

              // On ajoute l'arc
              graph.getEdges().add(a);
              // cT.addArc(a);
              indexArc = lSharedEdges.size() - 1;
            }
            // On affecte le côté d'où se trouve la face
            // Normalement la carte topo met ça à jour du côté de la
            // face
            decomposition.graph.Edge a = lArcs.get(indexArc);

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

  public static Point3d getStart(SharedEdge se, Face ref) {
    return se.getStart(se.right);
  }

  public static Point3d getEnd(SharedEdge se, Face ref) {
    return se.getEnd(se.right);
  }
  /*
   * Conversion Geoxygene => format de la lib
   */

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
    // lC.add(lC.get(0));
    for (int i = 0; i < nbPoints - 2; i++) {
      lEOut.add(new Edge(lC.get(i), lC.get(i + 1)));
    }
    lEOut.add(new Edge(lC.get(nbPoints - 2), lC.get(0)));
    return lEOut;
  }

  /**
   * Convertit un positon en corner
   * 
   * @param dp
   * @return
   */
  private static Corner fromPositionToCorner(Coordinate dp) {
    // if (dp.getDimension() == 2) {
    // return new Corner(dp.getX(), dp.getY(), 0);
    // }
    return new Corner(dp.getX(), dp.getY(), dp.getZ());
  }

  /**
   * 
   */
  private static List<Coordinate> convertLoopCorner(Loop<Point3d> lC) {
    List<Coordinate> dpl = new ArrayList<>(lC.count());
    for (Point3d c : lC) {
      dpl.add(fromCornerToPosition(c));
    }
    return dpl;
  }

  private static Coordinate fromCornerToPosition(Point3d c) {

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
}
