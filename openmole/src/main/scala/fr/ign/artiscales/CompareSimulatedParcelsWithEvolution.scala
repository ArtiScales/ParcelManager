package fr.ign.artiscales

import java.io.File

import fr.ign.artiscales.pm.analysis.SingleParcelStat
import fr.ign.artiscales.pm.workflow.ZoneDivision
import fr.ign.cogit.parameter.ProfileUrbanFabric

object compSimuParcel {
  /*
"workflow": "zoneDivision",
"parcelProcess": "OBB",
"urbanFabricType": "smallHouse"
*/

  def SortUniqueZoning(
    toSortFile: File,
    zoningFile: File,
    outFolder: File): (File) = {
    CompareSimulatedParcelsWithEvolutionOM.sortUniqueZoning(toSortFile, zoningFile, outFolder)
  }
  
  def ZoneDivisionOM(zoneFile: File,
            parcelFile: File,
            parcelEvolved:File,
            outFolder: File,
            maximalArea: Double,
            decompositionLevelWithoutStreet: Int,
            largeStreetLevel: Int,
            streetWidth: Double,
            largeStreetWidth: Double,
            minimalWidthContactRoad: Double,
            harmonyCoeff: Double
           ) : (Double, Int, Double) = {
//             ) : (Int, Double) = {
    val profile = new ProfileUrbanFabric(maximalArea, decompositionLevelWithoutStreet, largeStreetLevel, streetWidth,
      largeStreetWidth, minimalWidthContactRoad, harmonyCoeff)
    val parcelSimuled = ZoneDivision.zoneDivision(zoneFile, parcelFile, profile, outFolder)
    val hausdorfDistance = SingleParcelStat.hausdorfDistanceAverage(parcelSimuled, parcelEvolved)
    val nbParcelDiff = SingleParcelStat.diffNumberOfParcel(parcelSimuled, parcelEvolved)
    val areaParcelDiff = SingleParcelStat.diffAreaAverage(parcelSimuled, parcelEvolved)
    (-hausdorfDistance, nbParcelDiff, areaParcelDiff)
//   (nbParcelDiff, areaParcelDiff)
  }
}


/*
	"Workflow": "densificationOrNeighborhood",
	"parcelProcess": "OBB",
	"genericZone": "U",
	"preciseZone":"UD",
	"communityNumber":"77470,77390,77243",
	"urbanFabricType": "largeCollective"
*/


/*
	"workflow": "densificationOrNeighborhood",
	"genericZone": "U",
	"urbanFabricType": "smallHouse"
*/

/*
	"workflow": "consolidationDivision",
	"parcelProcess": "OBB",
	"genericZone": "AU",
	"urbanFabricType": "smallHouse"
*/


/*
	"workflow": "consolidationDivision",
	"parcelProcess": "OBB",
	"genericZone": "NC",
	"urbanFabricType": "smallHouse"
*/

