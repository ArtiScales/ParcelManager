package fr.ign.artiscales.pm

import fr.ign.artiscales.pm.analysis.SingleParcelStat
import fr.ign.artiscales.pm.usecase.CompareSimulatedWithRealParcelsOM
import fr.ign.artiscales.pm.workflow.{Workflow, ZoneDivision}
import fr.ign.artiscales.tools.parameter.ProfileUrbanFabric

import java.io.File

object compSimuParcel {
  /*
"workflow": "zoneDivision",
"parcelProcess": "OBB",
"urbanFabricType": "smallHouse"
*/

  def ZoneDivisionOM(parcelFile: File,
                     initialZone: File,
                     parcelEvolved: File,
                     buildingFile: File,
                     roadFile: File,
                     outFolder: File,
                     maximalArea: Double,
                     minimalArea: Double,
                     blockShape: Int,
                     streetLane: Int,
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
                     processType: Int
                    ): (Double, Int, Double) = {
    //             ) : (Int, Double) = {
    val profile = new ProfileUrbanFabric("calibration", maximalArea, minimalArea, minimalWidthContactRoad,
      laneWidth, streetWidth, streetLane, blockShape, lenDriveway,
      maxDepth, maxDistanceForNearestRoad, maxWidth, approxNumberParcelPerBlock, harmonyCoeff, irregularityCoeff)
    CompareSimulatedWithRealParcelsOM.setProcess(processType)
    val parcelSimuled = (new ZoneDivision()).zoneDivision(initialZone: File, parcelFile: File, outFolder: File, profile: ProfileUrbanFabric, roadFile: File, buildingFile: File)

    //todo Check those return and add other?
    val hausdorfDistance = SingleParcelStat.hausdorffDistance(parcelSimuled, parcelEvolved)
    val nbParcelDiff = SingleParcelStat.diffNumberOfParcel(parcelSimuled, parcelEvolved)
    val areaParcelDiff = SingleParcelStat.diffAreaAverage(parcelSimuled, parcelEvolved)
    (hausdorfDistance, nbParcelDiff, areaParcelDiff)
    //   (nbParcelDiff, areaParcelDiff)
  }

  def ZoneDivisionSSOM(parcelFile: File,
                       initialZone: File,
                       parcelEvolved: File,
                       buildingFile: File,
                       roadFile: File,
                       outFolder: File,
                       minimalArea: Double,
                       laneWidth: Double,
                       maxDepth: Double,
                       maxWidth: Double,
                       maxDistanceForNearestRoad: Double,
                       minimalWidthContactRoad: Double,
                       irregularityCoeff: Double
                      ): (Double, Int, Double) = {
    //             ) : (Int, Double) = {
    val profile = new ProfileUrbanFabric("calibration", minimalArea, maxDepth, maxDistanceForNearestRoad,
      minimalWidthContactRoad, maxWidth, laneWidth, irregularityCoeff)
    Workflow.PROCESS = "SS"
    val parcelSimuled = (new ZoneDivision()).zoneDivision(initialZone: File, parcelFile: File, outFolder: File, profile: ProfileUrbanFabric, roadFile: File, buildingFile: File)

    //todo Check those return and add other?
    val hausdorfDistance = SingleParcelStat.hausdorffDistance(parcelSimuled, parcelEvolved)
    val nbParcelDiff = SingleParcelStat.diffNumberOfParcel(parcelSimuled, parcelEvolved)
    val areaParcelDiff = SingleParcelStat.diffAreaAverage(parcelSimuled, parcelEvolved)
    (hausdorfDistance, nbParcelDiff, areaParcelDiff)
    //   (nbParcelDiff, areaParcelDiff)
  }

  def ZoneDivisionSSThenOBBThenSSOM(parcelFile: File,
                                    initialZone: File,
                                    parcelEvolved: File,
                                    buildingFile: File,
                                    roadFile: File,
                                    outFolder: File,
                                    maximalArea: Double,
                                    minimalArea: Double,
                                    blockShape: Int,
                                    streetLane: Int,
                                    laneWidth: Double,
                                    streetWidth: Double,
                                    maxDepth: Double,
                                    maxWidth: Double,
                                    maxDistanceForNearestRoad: Double,
                                    approxNumberParcelPerBlock: Int,
                                    minimalWidthContactRoad: Double,
                                    harmonyCoeff: Double,
                                    irregularityCoeff: Double,
                                   ): (Double, Int, Double) = {
    val profile = new ProfileUrbanFabric("calibration", maximalArea, minimalArea, minimalWidthContactRoad,
      laneWidth, streetWidth, streetLane, blockShape, 0,
      maxDepth, maxDistanceForNearestRoad, maxWidth, approxNumberParcelPerBlock, harmonyCoeff, irregularityCoeff)
    Workflow.PROCESS = "OBBThenSS"
    val parcelSimuled = (new ZoneDivision()).zoneDivision(initialZone: File, parcelFile: File, outFolder: File, profile: ProfileUrbanFabric, roadFile: File, buildingFile: File)

    //todo Check those return and add other?
    val hausdorfDistance = SingleParcelStat.hausdorffDistance(parcelSimuled, parcelEvolved)
    val nbParcelDiff = SingleParcelStat.diffNumberOfParcel(parcelSimuled, parcelEvolved)
    val areaParcelDiff = SingleParcelStat.diffAreaAverage(parcelSimuled, parcelEvolved)
    (hausdorfDistance, nbParcelDiff, areaParcelDiff)
    //   (nbParcelDiff, areaParcelDiff)
  }

  def ZoneDivisionOBBOM(parcelFile: File,
                        initialZone: File,
                        parcelEvolved: File,
                        roadFile: File,
                        outFolder: File,
                        maximalArea: Double,
                        minimalArea: Double,
                        blockShape: Int,
                        streetLane: Int,
                        laneWidth: Double,
                        streetWidth: Double,
                        minimalWidthContactRoad: Double,
                        harmonyCoeff: Double,
                        irregularityCoeff: Double
                       ): (Double, Int, Double) = {
    //             ) : (Int, Double) = {
    val profile = new ProfileUrbanFabric("calibration", maximalArea, minimalArea,
      minimalWidthContactRoad, streetWidth, streetLane, laneWidth, blockShape, harmonyCoeff, irregularityCoeff)
    Workflow.PROCESS = "OBB"
    val parcelSimuled = (new ZoneDivision()).zoneDivision(initialZone: File, parcelFile: File, outFolder: File, profile: ProfileUrbanFabric, roadFile: File, null)

    val hausdorfDistance = SingleParcelStat.hausdorffDistance(parcelSimuled, parcelEvolved)
    val nbParcelDiff = SingleParcelStat.diffNumberOfParcel(parcelSimuled, parcelEvolved)
    val areaParcelDiff = SingleParcelStat.diffAreaAverage(parcelSimuled, parcelEvolved)
    (hausdorfDistance, nbParcelDiff, areaParcelDiff)
    //   (nbParcelDiff, areaParcelDiff)
  }
}

