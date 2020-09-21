package fr.ign.artiscales.pm.analysis;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.stream.Collectors;

import org.geotools.data.DataStore;
import org.geotools.data.DataUtilities;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.locationtech.jts.geom.Geometry;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.NoSuchAuthorityCodeException;

import fr.ign.artiscales.pm.fields.GeneralFields;
import fr.ign.artiscales.pm.parcelFunction.MarkParcelAttributeFromPosition;
import fr.ign.artiscales.pm.parcelFunction.ParcelAttribute;
import fr.ign.artiscales.pm.parcelFunction.ParcelGetter;
import fr.ign.artiscales.tools.geoToolsFunctions.vectors.Collec;
import fr.ign.artiscales.tools.geoToolsFunctions.vectors.Geom;
import fr.ign.artiscales.tools.geoToolsFunctions.vectors.Geopackages;
import fr.ign.artiscales.tools.geometryGeneration.CityGeneration;

/**
 * This class generates values for the description of an urban fabric. It is aimed to help the setting of {@link fr.ign.cogit.parameter.ProfileUrbanFabric} parameters. It can work
 * on different scales, from the ilot or the {@link GeneralFields#zonePreciseNameField} to a whole community.
 * 
 * @author Maxime Colomb
 *
 */
public class GetParametersOfScene {

	/**
	 * The scale of the studied zone. Can either be community, genericZone, preciseZone or islet
	 */
	static String scaleZone = "community";
	// TODO mettre la possibilité de mélanger ces échelles List<String> scaleZone = Arrays.asList("community");
	static File parcelFile, buildingFile, zoningFile, roadFile, outFolder;

	public static void main(String[] args) throws NoSuchAuthorityCodeException, IOException, FactoryException {
		outFolder = new File("/tmp/ParametersOfScene");
		setFiles(new File("src/main/resources/ParcelComparison/"));
		parcelFile = new File("/tmp/parcelChosen"+Collec.getDefaultGISFileType());
		// genParcelAreaBoundaries(pF);
		scaleZone = "genericZone";
		// genParcelAreaBoundaries(pF);
		// scaleZone = "preciseZone";
		genParcelAreaBoundaries();
	}

	public static void setFiles(File mainFolder) {
		parcelFile = new File(mainFolder, "parcel"+Collec.getDefaultGISFileType());
		if (!parcelFile.exists())
			System.out.println(parcelFile + " doesn't exist");
		buildingFile = new File(mainFolder, "building"+Collec.getDefaultGISFileType());
		if (!buildingFile.exists())
			System.out.println(buildingFile + " doesn't exist");
		zoningFile = new File(mainFolder, "zoning"+Collec.getDefaultGISFileType());
		if (!zoningFile.exists())
			System.out.println(zoningFile + " doesn't exist");
		roadFile = new File(mainFolder, "road"+Collec.getDefaultGISFileType());
		if (!roadFile.exists())
			System.out.println(roadFile + " doesn't exist");
		outFolder.mkdirs();
	}

	public static void genParcelAreaBoundaries() throws NoSuchAuthorityCodeException, IOException, FactoryException {
		DataStore sds = Geopackages.getDataStore(parcelFile);
		DataStore sdsRoad = Geopackages.getDataStore(roadFile);
//		sdsRoad.setCharset(Charset.forName("UTF-8"));
		SimpleFeatureCollection parcels = sds.getFeatureSource(sds.getTypeNames()[0]).getFeatures();
		HashMap<String, SimpleFeatureCollection> listSFC = new HashMap<String, SimpleFeatureCollection>();
		DataStore sdsZone = Geopackages.getDataStore(zoningFile);
		SimpleFeatureCollection zonings = DataUtilities.collection(Collec.snapDatas(sdsZone.getFeatureSource(sdsZone.getTypeNames()[0]).getFeatures(), parcels));
		sdsZone.dispose();
		switch (scaleZone) {
		case "community":
			for (String cityCodes : ParcelAttribute.getCityCodesOfParcels(parcels))
				listSFC.put(cityCodes, ParcelGetter.getFrenchParcelByZip(parcels, cityCodes));
			break;
		case "genericZone":
			for (String genericZone : GeneralFields.getGenericZoningTypes(zonings))
				listSFC.put(genericZone, MarkParcelAttributeFromPosition.getOnlyMarkedParcels(
						MarkParcelAttributeFromPosition.markParcelIntersectGenericZoningType(parcels, genericZone, zoningFile)));
			break;
		case "preciseZone":
			for (String preciseZone : GeneralFields.getPreciseZoningTypes(zonings))
				listSFC.put(preciseZone, MarkParcelAttributeFromPosition.getOnlyMarkedParcels(
						MarkParcelAttributeFromPosition.markParcelIntersectPreciseZoningType(parcels, "", preciseZone, zoningFile)));
			break;
		case "islet":
			SimpleFeatureCollection islet = CityGeneration.createUrbanIslet(parcels);
			int i = 0;
			try (SimpleFeatureIterator it = islet.features()) {
				while (it.hasNext())
					listSFC.put(String.valueOf(i++), MarkParcelAttributeFromPosition.getOnlyMarkedParcels(MarkParcelAttributeFromPosition
							.markParcelIntersectPolygonIntersection(parcels, Arrays.asList((Geometry) it.next().getDefaultGeometry()))));
			} catch (Exception e) {
				e.printStackTrace();
			}
			break;
		}
		for (String zone : listSFC.keySet()) {
			// Parcel's area
			SimpleFeatureCollection sfc = MarkParcelAttributeFromPosition
					.getOnlyMarkedParcels(MarkParcelAttributeFromPosition.markBuiltParcel(listSFC.get(zone), buildingFile));
			if (sfc.size() > 1) {
				AreaGraph vals = MakeStatisticGraphs.sortValuesAndCategorize(
						Arrays.stream(sfc.toArray(new SimpleFeature[0])).collect(Collectors.toList()), scaleZone + zone, true);
				vals.toCSV(outFolder);
				MakeStatisticGraphs.makeGraphHisto(vals, outFolder, "area of the built parcel of the " + scaleZone + " " + zone + " without crests",
						"parcel area", "nb parcels", 15);
				// Road informations is harder to produce. We are based on the road Geopackage and on the ratio of road/area calculation to produce estimations
				// we create a buffer around the zone to get corresponding road segments. The buffer length depends on the type of scale
				double buffer = 42;
				switch (scaleZone) {
				case "genericZone":
					buffer = 20;
					break;
				case "preciseZone":
				case "islet":
					buffer = 10;
					break;
				}
				SimpleFeatureCollection roads = Collec.snapDatas(sdsRoad.getFeatureSource(sdsRoad.getTypeNames()[0]).getFeatures(),
						Geom.unionSFC(sfc).buffer(buffer).buffer(-buffer));
				if (roads.size() > 1)
					MakeStatisticGraphs.roadGraph(roads, "length of the " + scaleZone + " " + zone + " roads ", "width of the road",
							"lenght of the type of road", outFolder);
				// TODO ajouter ratio parcels?
			}
		}
		sdsRoad.dispose();
		sds.dispose();
	}
}
