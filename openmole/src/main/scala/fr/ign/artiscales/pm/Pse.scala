package fr.ign.artiscales.pm

import fr.ign.artiscales.pm.analysis.SingleParcelStat
import fr.ign.artiscales.pm.division.DivisionType
import fr.ign.artiscales.pm.parcelFunction.{MarkParcelAttributeFromPosition, ParcelIndicator}
import fr.ign.artiscales.pm.workflow.{ConsolidationDivision, Workflow}
import fr.ign.artiscales.tools.geoToolsFunctions.vectors.collec.CollecMgmt
import fr.ign.artiscales.tools.parameter.ProfileUrbanFabric
import org.geotools.data.simple.SimpleFeatureCollection

import java.io.File

object Pse extends App {
  run()

  def run(): Unit = {
//    val result = pseOBBSS(new java.io.File("/home/mc/workspace/parcelmanager/src/main/resources/pse/InputData/parcel.gpkg"), new java.io.File("/home/mc/workspace/parcelmanager/src/main/resources/pse/InputData/road.gpkg"),
//      new java.io.File("/tmp/export.gpkg"), 1000, 100.0, 5.0, 7.0, 4.0, 0, 15.0, 50, 3, 15.0, 1, 1,)
    val result = pseOBB(new java.io.File("/home/mc/workspace/parcelmanager/src/main/resources/pse/InputData/parcel.gpkg"), new java.io.File("/home/mc/workspace/parcelmanager/src/main/resources/pse/InputData/road.gpkg"),
      new java.io.File("/tmp/export.gpkg"), 517.2303998939033,
  80.0,5,1, 10.0, 15.0,
  7.5114862479752125, 0.5757017027575442, 0.7066044940807303)
    println(result)
  }

  def pseOBBSS(parcelFile: File,
               roadFile: File,
               outFolder: File,
               maximalArea: Double,
               minimalArea: Double,
               laneWidth: Double,
               streetWidth: Double,
               lenDriveway: Double,
               maxDepth: Double,
               maxWidth: Double,
               maxDistanceForNearestRoad: Double,
               approxNumberParcelPerBlock: Int,
               minimalWidthContactRoad: Double,
               harmonyCoeff: Double,
               irregularityCoeff: Double,
              ): (Double, Int, Double, Double) = {
    val profile = new ProfileUrbanFabric("calibration", maximalArea, minimalArea, minimalWidthContactRoad,
      laneWidth, streetWidth, 0, 0, lenDriveway,
      maxDepth, maxDistanceForNearestRoad, maxWidth, approxNumberParcelPerBlock, harmonyCoeff, irregularityCoeff)
    Workflow.PROCESS = DivisionType.OBBThenSS
    val dsParcelEv = CollecMgmt.getDataStore(parcelFile)
    val parcel = dsParcelEv.getFeatureSource(dsParcelEv.getTypeNames()(0)).getFeatures
    print("marked parcels : "+MarkParcelAttributeFromPosition.countMarkedParcels(parcel))
print(profile)
    val parcelSimuled = MarkParcelAttributeFromPosition.getOnlySimulatedParcels(new ConsolidationDivision().consolidationDivision(parcel, roadFile, null, profile))


    //todo Check those return and add other?
    val aspectRatio: Double = SingleParcelStat.meanAspectRatio(parcelSimuled)
    val nbParcel: Int = parcelSimuled.size()
    val giniAreaParcel: Double = ParcelIndicator.giniArea(parcelSimuled)
    val nbNeighborhood : Double = ParcelIndicator.meanNeighborhood(parcelSimuled)

    dsParcelEv.dispose()
    (aspectRatio, nbParcel, giniAreaParcel,nbNeighborhood )
  }


  def pseOBB(parcelFile: File,
             roadFile: File,
             outFolder: File,
             maximalArea: Double,
             minimalArea: Double,
             streetLane: Int,
             blockShape: Int,
             laneWidth: Double,
             streetWidth: Double,
             minimalWidthContactRoad: Double,
             harmonyCoeff: Double,
             irregularityCoeff: Double,
            ): (Double, Int, Double, Double) = {
    val profile = new ProfileUrbanFabric("pseOBB", maximalArea, minimalArea, minimalWidthContactRoad, streetWidth, streetLane, laneWidth, blockShape, harmonyCoeff, irregularityCoeff)
    Workflow.PROCESS = DivisionType.OBB
    val dsParcelEv = CollecMgmt.getDataStore(parcelFile)
    val parcel = dsParcelEv.getFeatureSource(dsParcelEv.getTypeNames()(0)).getFeatures
    val parcelSimuled = MarkParcelAttributeFromPosition.getOnlySimulatedParcels(new ConsolidationDivision().consolidationDivision(parcel, roadFile, null, profile))
      //    CollecMgmt.exportSFC(parcelSimuled, new File("/tmp/obb.gpkg"))

    val aspectRatio: Double = SingleParcelStat.meanAspectRatio(parcelSimuled)
    val nbParcel: Int = parcelSimuled.size()
    val giniAreaParcel: Double = ParcelIndicator.giniArea(parcelSimuled)
    val nbNeighborhood : Double = ParcelIndicator.meanNeighborhood(parcelSimuled)
    dsParcelEv.dispose()
    (aspectRatio, nbParcel, giniAreaParcel, nbNeighborhood)
  }
}

