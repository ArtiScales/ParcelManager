package fr.ign.artiscales.workflow;

import java.io.File;
import java.util.Arrays;
import java.util.stream.Collectors;

import org.geotools.data.DataUtilities;
import org.geotools.data.collection.SpatialIndexFeatureCollection;
import org.geotools.data.shapefile.ShapefileDataStore;
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

		File tmpFolder = new File("/tmp/");
		File rootFolder = new File("src/main/resources/GeneralTest/");
		File roadFile = new File(rootFolder, "road.shp");
		File zoningFile = new File(rootFolder, "zoning.shp");
		File buildingFile = new File(rootFolder, "building.shp");
		File polygonIntersection = new File(rootFolder, "polygonIntersection.shp");
		File parcelFile = new File(rootFolder, "parcel.shp");
		File profileFolder = new File(rootFolder, "profileUrbanFabric");
		File outFolder = new File(rootFolder, "out");
		File statFolder = new File(outFolder, "stat");
		boolean allowIsolatedParcel = false;
		statFolder.mkdirs();
		ShapefileDataStore shpDSParcel = new ShapefileDataStore(parcelFile.toURI().toURL());
		SimpleFeatureCollection parcel = new SpatialIndexFeatureCollection(
				ParcelGetter.getFrenchParcelByZip(shpDSParcel.getFeatureSource().getFeatures(), "25267"));
		ProfileUrbanFabric profileDetached = ProfileUrbanFabric.convertJSONtoProfile(new File(profileFolder, "detachedHouse.json"));
		ProfileUrbanFabric profileSmallHouse = ProfileUrbanFabric.convertJSONtoProfile(new File(profileFolder, "smallHouse.json"));
		ProfileUrbanFabric profilelargeCollective = ProfileUrbanFabric.convertJSONtoProfile(new File(profileFolder, "largeCollective.json"));

		/////////////////////////
		// zoneTotRecomp
		/////////////////////////
		System.out.println("/////////////////////////");
		System.out.println("zoneTotRecomp");
		System.out.println("/////////////////////////");
		ZoneDivision.DEBUG = true;
		ShapefileDataStore shpDSZoning = new ShapefileDataStore(zoningFile.toURI().toURL());
		SimpleFeatureCollection zoning = new SpatialIndexFeatureCollection(DataUtilities.collection((shpDSZoning.getFeatureSource().getFeatures())));
		shpDSZoning.dispose();
		SimpleFeatureCollection zone = ZoneDivision.createZoneToCut("AU", zoning, zoningFile, parcel);
		// If no zones, we won't bother
		if (zone.isEmpty()) {
			System.out.println("parcelGenZone : no zones to be cut");
			System.exit(1);
		}
		ZoneDivision.SAVEINTERMEDIATERESULT = true;
		SimpleFeatureCollection parcelCuted = ZoneDivision.zoneDivision(zone, parcel, tmpFolder, outFolder, profilelargeCollective.getRoadEpsilon(),
				profilelargeCollective.getNoise(), profilelargeCollective.getMaximalArea(), profilelargeCollective.getMinimalArea(),
				profilelargeCollective.getMaximalWidth(), profilelargeCollective.getStreetWidth(), profilelargeCollective.getLargeStreetLevel(),
				profilelargeCollective.getLargeStreetWidth(), profilelargeCollective.getDecompositionLevelWithoutStreet());
		SimpleFeatureCollection finaux = FrenchParcelFields.setOriginalFrenchParcelAttributes(parcelCuted, parcel);
		Collec.exportSFC(finaux, new File(outFolder, "parcelTotZone.shp"));
		Collec.exportSFC(zone, new File(outFolder, "zone.shp"));
		StreetRatioParcels.streetRatioParcelZone(zone, finaux, statFolder, roadFile);
		MakeStatisticGraphs.makeAreaGraph(
				Arrays.stream(finaux.toArray(new SimpleFeature[0])).filter(sf -> ZoneDivision.isNewSection(sf)).collect(Collectors.toList()),
				statFolder, "Zone division - large collective building simulation");

		/////////////////////////
		//////// try the consolidRecomp method
		/////////////////////////
		System.out.println("/////////////////////////");
		System.out.println("consolidRecomp");
		System.out.println("/////////////////////////");
		ConsolidationDivision.DEBUG = true;
		ConsolidationDivision.SAVEINTERMEDIATERESULT = true;
		SimpleFeatureCollection markedZone = MarkParcelAttributeFromPosition.markParcelIntersectGenericZoningType(
				MarkParcelAttributeFromPosition.markParcelIntersectPolygonIntersection(finaux, polygonIntersection), "NC", zoningFile);
		SimpleFeatureCollection cutedNormalZone = ConsolidationDivision.consolidationDivision(markedZone, roadFile, tmpFolder,
				profileDetached.getRoadEpsilon(), profileDetached.getNoise(), profileDetached.getMaximalArea(), profileDetached.getMinimalArea(),
				profileDetached.getMaximalWidth(), profileDetached.getStreetWidth(), profileDetached.getLargeStreetLevel(),
				profileDetached.getLargeStreetWidth(), profileDetached.getDecompositionLevelWithoutStreet());
		SimpleFeatureCollection finalNormalZone = FrenchParcelFields.setOriginalFrenchParcelAttributes(cutedNormalZone, parcel);
		Collec.exportSFC(finalNormalZone, new File(outFolder, "ParcelConsolidRecomp.shp"));
		StreetRatioParcels.streetRatioParcels(markedZone, finalNormalZone, statFolder, roadFile);

		MakeStatisticGraphs.makeAreaGraph(Arrays.stream(finalNormalZone.toArray(new SimpleFeature[0]))
				.filter(sf -> ConsolidationDivision.isNewSection(sf)).collect(Collectors.toList()), statFolder,
				"Consolidation-division - detached houses simulation");

		/////////////////////////
		//////// try the parcelDensification method
		/////////////////////////
		System.out.println("/////////////////////////");
		System.out.println("parcelDensification");
		System.out.println("/////////////////////////");
		SimpleFeatureCollection parcelDensified = Densification.densification(
				MarkParcelAttributeFromPosition.markParcelIntersectFrenchConstructibleZoningType(
						MarkParcelAttributeFromPosition.markParcelIntersectPolygonIntersection(finalNormalZone, polygonIntersection), zoningFile),
				CityGeneration.createUrbanIslet(finalNormalZone), tmpFolder, buildingFile, roadFile, profileSmallHouse.getMaximalArea(),
				profileSmallHouse.getMinimalArea(), profileSmallHouse.getMaximalWidth(), profileSmallHouse.getLenDriveway(), allowIsolatedParcel);
		SimpleFeatureCollection finaux3 = FrenchParcelFields.setOriginalFrenchParcelAttributes(parcelDensified, parcel);
		Collec.exportSFC(finaux3, new File(outFolder, "parcelDensification.shp"));
		MakeStatisticGraphs.makeAreaGraph(Arrays.stream(parcelDensified.toArray(new SimpleFeature[0])).filter(sf -> Densification.isNewSection(sf))
				.collect(Collectors.toList()), statFolder, "Densification - small houses simulation");
		shpDSParcel.dispose();
		SingleParcelStat.writeStatSingleParcel(MarkParcelAttributeFromPosition.markSimulatedParcel(finaux3), roadFile,
				new File(outFolder, "stat/statParcel.csv"));
	}
}
