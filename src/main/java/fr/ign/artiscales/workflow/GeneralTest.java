package fr.ign.artiscales.workflow;

import java.io.File;
import java.util.Arrays;
import java.util.stream.Collectors;

import org.geotools.data.DataStore;
import org.geotools.data.DataUtilities;
import org.geotools.data.collection.SpatialIndexFeatureCollection;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.opengis.feature.simple.SimpleFeature;

import fr.ign.artiscales.analysis.MakeStatisticGraphs;
import fr.ign.artiscales.analysis.SingleParcelStat;
import fr.ign.artiscales.analysis.StreetRatioParcels;
import fr.ign.artiscales.fields.french.FrenchParcelFields;
import fr.ign.artiscales.goal.ConsolidationDivision;
import fr.ign.artiscales.goal.Densification;
import fr.ign.artiscales.goal.ZoneDivision;
import fr.ign.artiscales.parcelFunction.MarkParcelAttributeFromPosition;
import fr.ign.artiscales.parcelFunction.ParcelGetter;
import fr.ign.cogit.geoToolsFunctions.vectors.Collec;
import fr.ign.cogit.geoToolsFunctions.vectors.Geopackages;
import fr.ign.cogit.geometryGeneration.CityGeneration;
import fr.ign.cogit.parameter.ProfileUrbanFabric;

/**
 * Class that tests principal goals and analysis of Parcel Manager.
 * 
 * @author Maxime Colomb
 *
 */
public class GeneralTest {

	// /////////////////////////
	// //////// try the parcelGenMotif method
	/////////////////////////
	public static void main(String[] args) throws Exception {
//		org.geotools.util.logging.Logging.getLogger("org.hsqldb.persist.Logger").setLevel(Level.OFF);
//		org.geotools.util.logging.Logging.getLogger("org.geotools.jdbc.JDBCDataStore").setLevel(Level.OFF);
		long start  = System.currentTimeMillis();

		File rootFolder = new File("src/main/resources/GeneralTest/");
		File roadFile = new File(rootFolder, "road.gpkg");
		File zoningFile = new File(rootFolder, "zoning.gpkg");
		File buildingFile = new File(rootFolder, "building.gpkg");
		File polygonIntersection = new File(rootFolder, "polygonIntersection.gpkg");
		File parcelFile = new File(rootFolder, "parcel.gpkg");
		File profileFolder = new File(rootFolder, "profileUrbanFabric");
		File outFolder = new File(rootFolder, "out");
		File statFolder = new File(outFolder, "stat");
		boolean allowIsolatedParcel = false;
		statFolder.mkdirs();
		DataStore gpkgDSParcel = Geopackages.getDataStore(parcelFile);
		SimpleFeatureCollection parcel = DataUtilities.collection(
				ParcelGetter.getFrenchParcelByZip(gpkgDSParcel.getFeatureSource(gpkgDSParcel.getTypeNames()[0]).getFeatures(), "25267"));
		gpkgDSParcel.dispose();
		ProfileUrbanFabric profileDetached = ProfileUrbanFabric.convertJSONtoProfile(new File(profileFolder, "detachedHouse.json"));
		ProfileUrbanFabric profileSmallHouse = ProfileUrbanFabric.convertJSONtoProfile(new File(profileFolder, "smallHouse.json"));
		ProfileUrbanFabric profileLargeCollective = ProfileUrbanFabric.convertJSONtoProfile(new File(profileFolder, "largeCollective.json"));

		/////////////////////////
		// zoneTotRecomp
		/////////////////////////
		System.out.println("/////////////////////////");
		System.out.println("zoneTotRecomp");
		System.out.println("/////////////////////////");
//		ZoneDivision.DEBUG = true;
		DataStore gpkgDSZoning = Geopackages.getDataStore(zoningFile);
		SimpleFeatureCollection zoning = new SpatialIndexFeatureCollection(DataUtilities.collection((gpkgDSZoning.getFeatureSource(gpkgDSZoning.getTypeNames()[0]).getFeatures())));
		gpkgDSZoning.dispose();
		SimpleFeatureCollection zone = ZoneDivision.createZoneToCut("AU", zoning, zoningFile, parcel);
		// If no zones, we won't bother
		if (zone.isEmpty()) {
			System.out.println("parcelGenZone : no zones to be cut");
			System.exit(1);
		}
		ZoneDivision.SAVEINTERMEDIATERESULT = true;
		SimpleFeatureCollection parcelCuted = (new ZoneDivision()).zoneDivision(zone, parcel, outFolder, profileLargeCollective);
		SimpleFeatureCollection finaux = FrenchParcelFields.setOriginalFrenchParcelAttributes(parcelCuted, parcel);
		Collec.exportSFC(finaux, new File(outFolder, "parcelTotZone.gpkg"));
		Collec.exportSFC(zone, new File(outFolder, "zone.gpkg"));
		StreetRatioParcels.streetRatioZone(zone, finaux, profileLargeCollective.getNameBuildingType(), statFolder, roadFile);
		MakeStatisticGraphs.makeAreaGraph(
				Arrays.stream(finaux.toArray(new SimpleFeature[0])).filter(sf -> (new ZoneDivision()).isNewSection(sf)).collect(Collectors.toList()),
				statFolder, "Zone division - large collective building simulation");

		/////////////////////////
		//////// try the consolidRecomp method
		/////////////////////////
		System.out.println("/////////////////////////");
		System.out.println("consolidRecomp");
		System.out.println("/////////////////////////");
		ConsolidationDivision.DEBUG = true;
		ConsolidationDivision.SAVEINTERMEDIATERESULT = true;
		ConsolidationDivision.PROCESS = "OBB";
		SimpleFeatureCollection markedZone = MarkParcelAttributeFromPosition.markParcelIntersectGenericZoningType(
				MarkParcelAttributeFromPosition.markParcelIntersectPolygonIntersection(parcel, polygonIntersection), "NC", zoningFile);
		System.out.println(profileDetached);
		SimpleFeatureCollection cutedNormalZone = (new ConsolidationDivision()).consolidationDivision(markedZone, roadFile, outFolder, profileDetached);
		SimpleFeatureCollection finalNormalZone = FrenchParcelFields.setOriginalFrenchParcelAttributes(cutedNormalZone, parcel);
		Collec.exportSFC(finalNormalZone, new File(outFolder, "ParcelConsolidRecomp.gpkg"));
		StreetRatioParcels.streetRatioParcels(markedZone, finalNormalZone, profileDetached.getNameBuildingType(), statFolder, roadFile);
		MakeStatisticGraphs.makeAreaGraph(Arrays.stream(finalNormalZone.toArray(new SimpleFeature[0]))
				.filter(sf -> (new ConsolidationDivision()).isNewSection(sf)).collect(Collectors.toList()), statFolder,
				"Consolidation-division - detached houses simulation");

		/////////////////////////
		//////// try the parcelDensification method
		/////////////////////////
		System.out.println("/////////////////////////");
		System.out.println("parcelDensification");
		System.out.println("/////////////////////////");
		SimpleFeatureCollection parcelDensified = (new Densification()).densification(
				MarkParcelAttributeFromPosition.markParcelIntersectFrenchConstructibleZoningType(
						MarkParcelAttributeFromPosition.markParcelIntersectPolygonIntersection(finalNormalZone, polygonIntersection), zoningFile),
				CityGeneration.createUrbanIslet(finalNormalZone), outFolder, buildingFile, roadFile, profileSmallHouse.getMaximalArea(),
				profileSmallHouse.getMinimalArea(), profileSmallHouse.getMinimalWidthContactRoad(), profileSmallHouse.getLenDriveway(), allowIsolatedParcel);
		SimpleFeatureCollection finaux3 = FrenchParcelFields.setOriginalFrenchParcelAttributes(parcelDensified, parcel);
		Collec.exportSFC(finaux3, new File(outFolder, "parcelDensification.gpkg"));
		MakeStatisticGraphs.makeAreaGraph(Arrays.stream(parcelDensified.toArray(new SimpleFeature[0])).filter(sf -> (new Densification()).isNewSection(sf))
				.collect(Collectors.toList()), statFolder, "Densification - small houses simulation");
		SingleParcelStat.writeStatSingleParcel(MarkParcelAttributeFromPosition.markSimulatedParcel(finaux3), roadFile,
				new File(outFolder, "stat/statParcel.csv"));
		System.out.println(start - System.currentTimeMillis());
	}
}
