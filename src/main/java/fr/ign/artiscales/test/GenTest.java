package fr.ign.artiscales.test;

import java.io.File;

import org.geotools.data.shapefile.ShapefileDataStore;
import org.geotools.data.simple.SimpleFeatureCollection;

import fr.ign.artiscales.analysis.MakeStatisticGraphs;
import fr.ign.artiscales.analysis.StatParcelStreetRatio;
import fr.ign.artiscales.fields.french.FrenchParcelFields;
import fr.ign.artiscales.goal.ConsolidationDivision;
import fr.ign.artiscales.goal.Densification;
import fr.ign.artiscales.goal.ZoneDivision;
import fr.ign.artiscales.parcelFunction.MarkParcelAttributeFromPosition;
import fr.ign.artiscales.parcelFunction.ParcelGetter;
import fr.ign.artiscales.parcelFunction.ParcelState;
import fr.ign.cogit.geoToolsFunctions.vectors.Collec;
import fr.ign.cogit.geometryGeneration.CityGeneration;
import fr.ign.cogit.parameter.ProfileUrbanFabric;

/**
 * Class that tests principal goals and analysis of Parcel Manager.
 * 
 * @author Maxime Colomb
 *
 */
public class GenTest {

	// /////////////////////////
	// //////// try the parcelGenMotif method
	/////////////////////////
	public static void main(String[] args) throws Exception {

		File tmpFolder = new File("/tmp/");
		File rootFolder = new File("src/main/resources/testData/");

		File roadFile = new File(rootFolder, "road.shp");
		File zoningFile = new File(rootFolder, "zoning.shp");
		File buildingFile = new File(rootFolder, "building.shp");
		File polygonIntersection = new File(rootFolder, "polygonIntersection.shp");
		File predicateFile = new File(rootFolder, "predicate.csv");
		File parcelFile = new File(rootFolder, "parcel.shp");
		File profileFolder = new File(rootFolder, "profileBuildingType");
		File outFolder = new File(rootFolder, "out");
		File statFolder = new File(outFolder, "stat");
		statFolder.mkdirs();
		ShapefileDataStore shpDSParcel = new ShapefileDataStore(parcelFile.toURI().toURL());
		SimpleFeatureCollection parcel = ParcelGetter.getFrenchParcelByZip(shpDSParcel.getFeatureSource().getFeatures(), "25267");
		ProfileUrbanFabric profile = ProfileUrbanFabric.convertJSONtoProfile(new File(profileFolder, "detachedHouse.json"));

		/////////////////////////
		// zoneTotRecomp
		/////////////////////////
		System.out.println("/////////////////////////");
		System.out.println("zoneTotRecomp");
		System.out.println("/////////////////////////");
		ZoneDivision.DEBUG = true;
		ShapefileDataStore shpDSZone = new ShapefileDataStore(zoningFile.toURI().toURL());
		SimpleFeatureCollection featuresZones = shpDSZone.getFeatureSource().getFeatures();
		SimpleFeatureCollection zone = ZoneDivision.createZoneToCut("AU", featuresZones, parcel);
		// If no zones, we won't bother
		if (zone.isEmpty()) {
			System.out.println("parcelGenZone : no zones to be cut");
			System.exit(1);
		}
		ZoneDivision.SAVEINTERMEDIATERESULT = true;
		SimpleFeatureCollection parcelCuted = ZoneDivision.zoneDivision(zone, parcel, tmpFolder, zoningFile, profile.getMaximalArea(),
				profile.getMinimalArea(), profile.getMaximalWidth(), profile.getStreetWidth(), profile.getLargeStreetLevel(),
				profile.getLargeStreetWidth(), profile.getDecompositionLevelWithoutStreet());
		Collec.exportSFC(parcelCuted, new File(tmpFolder, "parcelTotZoneTmp.shp"));
		SimpleFeatureCollection finaux = FrenchParcelFields.setOriginalFrenchParcelAttributes(parcelCuted, parcel);
		Collec.exportSFC(finaux, new File(outFolder, "parcelTotZone.shp"));
		Collec.exportSFC(zone, new File(outFolder, "zone.shp"));
		StatParcelStreetRatio.streetRatioParcelZone(zone, finaux, statFolder, roadFile);
		MakeStatisticGraphs.makeAreaGraph(new File(outFolder, "parcelTotZone.shp"), statFolder);
		shpDSZone.dispose();

		/////////////////////////
		//////// try the consolidRecomp method
		/////////////////////////
		System.out.println("/////////////////////////");
		System.out.println("consolidRecomp");
		System.out.println("/////////////////////////");
		ConsolidationDivision.DEBUG = true;
		ConsolidationDivision.SAVEINTERMEDIATERESULT = true;
		SimpleFeatureCollection testmp = MarkParcelAttributeFromPosition.markParcelIntersectPolygonIntersection(finaux, polygonIntersection);
		SimpleFeatureCollection test = MarkParcelAttributeFromPosition.markParcelIntersectZoningType(testmp, "NC", zoningFile);
		SimpleFeatureCollection cuted = ConsolidationDivision.consolidationDivision(test, tmpFolder, profile.getMaximalArea(),
				profile.getMinimalArea(), profile.getMaximalWidth(), profile.getStreetWidth(), profile.getLargeStreetLevel(),
				profile.getLargeStreetWidth(), profile.getDecompositionLevelWithoutStreet());
		SimpleFeatureCollection finaux2 = FrenchParcelFields.setOriginalFrenchParcelAttributes(cuted, parcel);
		Collec.exportSFC(finaux2, new File(outFolder, "ParcelConsolidRecomp.shp"));
		StatParcelStreetRatio.streetRatioParcels(test, finaux2, statFolder, roadFile);
		MakeStatisticGraphs.makeAreaGraph(new File(outFolder, "ParcelConsolidRecomp.shp"), statFolder);

		/////////////////////////
		//////// try the parcelDensification method
		/////////////////////////
		System.out.println("/////////////////////////");
		System.out.println("parcelDensification");
		System.out.println("/////////////////////////");
		SimpleFeatureCollection parcMarked = MarkParcelAttributeFromPosition.markParcelIntersectPolygonIntersection(finaux2, polygonIntersection);
		SimpleFeatureCollection toDensify = MarkParcelAttributeFromPosition.markParcelIntersectZoningType(parcMarked, "U", zoningFile);
		SimpleFeatureCollection salut = Densification.densification(toDensify, CityGeneration.createUrbanIslet(finaux2), tmpFolder, buildingFile,
				new File("/tmp/road.shp"), profile.getMaximalArea(), profile.getMinimalArea(), profile.getMaximalWidth(), profile.getLenDriveway(),
				ParcelState.isArt3AllowsIsolatedParcel(parcMarked.features().next(), predicateFile));
		SimpleFeatureCollection finaux3 = FrenchParcelFields.setOriginalFrenchParcelAttributes(salut, parcel);
		Collec.exportSFC(finaux3, new File(outFolder, "parcelDensification.shp"));
		MakeStatisticGraphs.makeAreaGraph(new File(outFolder, "parcelDensification.shp"), statFolder);

		shpDSParcel.dispose();
	}
}
