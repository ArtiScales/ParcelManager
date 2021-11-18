package fr.ign.artiscales.pm.analysis;

import fr.ign.artiscales.pm.fields.GeneralFields;
import fr.ign.artiscales.pm.fields.french.FrenchParcelFields;
import fr.ign.artiscales.pm.parcelFunction.MarkParcelAttributeFromPosition;
import fr.ign.artiscales.pm.parcelFunction.ParcelSchema;
import fr.ign.artiscales.pm.parcelFunction.ParcelState;
import fr.ign.artiscales.tools.geoToolsFunctions.Attribute;
import fr.ign.artiscales.tools.geoToolsFunctions.vectors.Geom;
import fr.ign.artiscales.tools.geoToolsFunctions.vectors.collec.CollecMgmt;
import fr.ign.artiscales.tools.geoToolsFunctions.vectors.collec.CollecTransform;
import fr.ign.artiscales.tools.geoToolsFunctions.vectors.geom.Polygons;
import fr.ign.artiscales.tools.geometryGeneration.CityGeneration;
import fr.ign.artiscales.tools.io.csv.CsvExport;
import org.geotools.data.DataStore;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.factory.CommonFactoryFinder;
import org.geotools.feature.DefaultFeatureCollection;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.util.factory.GeoTools;
import org.locationtech.jts.geom.Geometry;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.filter.FilterFactory2;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;

/**
 * Street/generated surface ratio. Only developed for french parcels.
 *
 * @author Maxime Colomb
 */
public class RoadRatioParcels {

    private static boolean overwrite = true;

    // public static void main(String[] args) throws IOException {
    // long start = System.currentTimeMillis();
    // File rootFolder = new File("src/main/resources/TestScenario/");
    // File zoningFile = new File(rootFolder, "zoning.gpkg");
    // DataStore ds = Geopackages.getDataStore(new File(rootFolder, "parcel.gpkg"));
    // SimpleFeatureCollection sfc = ds.getFeatureSource(ds.getTypeNames()[0]).getFeatures();
    // DataStore ds2 = Geopackages.getDataStore(new File(rootFolder,"out/OBB/ParcelConsolidation.gpkg"));
    // SimpleFeatureCollection sfc2 = ds2.getFeatureSource(ds2.getTypeNames()[0]).getFeatures();
    // streetRatioParcels(MarkParcelAttributeFromPosition.markParcelIntersectPreciseZoningType(sfc, "AU", "AUb",
    // zoningFile), sfc2, "", new File("/tmp/"), new File(rootFolder, "road.gpkg"));
    // ds.dispose();
    // ds2.dispose();
    // System.out.println(System.currentTimeMillis() - start);
    // }

    /**
     * Calculate the ratio between the parcel area and the total area of a zone. It express the quantity of not parcel land, which could be either streets or public spaces.
     * Calculate zones and then send the whole to the {@link #roadRatioZone(SimpleFeatureCollection, SimpleFeatureCollection, String, File, File)} method.
     *
     * @param initialMarkedParcel {@link SimpleFeatureCollection} of the initial set of parcels which are marked if they had to simulated. Marks could be made with the methods contained in the
     *                            class {@link fr.ign.artiscales.pm.parcelFunction.MarkParcelAttributeFromPosition}. The field attribute is named <i>SPLIT</i> by default. It is possible to change
     *                            it with the {@link fr.ign.artiscales.pm.parcelFunction.MarkParcelAttributeFromPosition#setMarkFieldName(String)} function.
     * @param cutParcel           A collection of parcels after a Parcel Manager simulation
     * @param folderOutStat       folder to store the results
     * @param roadFile            the road geo file
     * @param legend              name of the zone
     * @throws IOException reading geo files and exporting csv
     */
    public static void roadRatioParcels(SimpleFeatureCollection initialMarkedParcel, SimpleFeatureCollection cutParcel, String legend, File folderOutStat, File roadFile) throws IOException {
        // We construct zones to analyze the street ratio for each operations.
        DefaultFeatureCollection zone = new DefaultFeatureCollection();
        FilterFactory2 ff = CommonFactoryFinder.getFilterFactory2(GeoTools.getDefaultHints());
        Geometry multiGeom;
        if (CollecMgmt.isCollecContainsAttribute(initialMarkedParcel, MarkParcelAttributeFromPosition.getMarkFieldName()))
            multiGeom = Geom.unionSFC(initialMarkedParcel.subCollection(ff.like(ff.property(MarkParcelAttributeFromPosition.getMarkFieldName()), "1")));
        else {
            System.out.println("Parcels haven't been previously marked : stop StatParcelStreetRatio");
            return;
        }
        SimpleFeatureBuilder sfBuilderZone = GeneralFields.getSFBZoning();
        for (int i = 0; i < multiGeom.getNumGeometries(); i++) {
            Geometry zoneGeom = multiGeom.getGeometryN(i);
            sfBuilderZone.add(zoneGeom);
            // set needed attributes with initial french parcels
            try (SimpleFeatureIterator it = cutParcel.features()) {
                while (it.hasNext()) {
                    SimpleFeature feat = it.next();
                    if (zoneGeom.contains(((Geometry) feat.getDefaultGeometry()))) {
                        sfBuilderZone.set(ParcelSchema.getParcelCommunityField(), FrenchParcelFields.makeDEPCOMCode(feat));
                        sfBuilderZone.set(GeneralFields.getZonePreciseNameField(), feat.getAttribute(ParcelSchema.getParcelSectionField()));
                        break;
                    }
                }
            } catch (Exception problem) {
                problem.printStackTrace();
            }
            zone.add(sfBuilderZone.buildFeature(Attribute.makeUniqueId()));
        }
        roadRatioZone(zone, cutParcel, legend, folderOutStat, roadFile);
    }

    /**
     * Calculate the ratio between the area of a set of parcels and the total area of a zone. The fact that the zone and the area must be verified by the user. The result express
     * the quantity of not parcel land, which could be either roads or public spaces.
     *
     * @param zone          {@link SimpleFeatureCollection} of initial zones
     * @param cutParcel     {@link SimpleFeatureCollection} of the cuted parcels
     * @param folderOutStat folder to store the results
     * @param roadFile      the road geo file
     * @param legend        name of the zone
     * @throws IOException reading geo files and exporting csv
     */
    public static void roadRatioZone(SimpleFeatureCollection zone, SimpleFeatureCollection cutParcel, String legend, File folderOutStat, File roadFile) throws IOException {
        System.out.println("++++++++++Road Ratios++++++++++");
        HashMap<String, String[]> stat = new HashMap<>();

        DataStore dsRoad = CollecMgmt.getDataStore(roadFile);
        SimpleFeatureCollection roads = CollecTransform.selectIntersection(dsRoad.getFeatureSource(dsRoad.getTypeNames()[0]).getFeatures(), zone);
        SimpleFeatureCollection blocks = CityGeneration.createUrbanBlock(cutParcel);

        String[] firstLine = {"CODE", "Urban fabric type", ParcelSchema.getParcelCommunityField(), GeneralFields.getZonePreciseNameField(),
                "InitialArea", "ParcelsArea", "RatioArea", "RatioParcelConnectionRoad"};
        int count = 0;
        try (SimpleFeatureIterator zones = zone.features()) {
            while (zones.hasNext()) {
                String[] tab = new String[7];
                SimpleFeature z = zones.next();
                tab[0] = legend;
                tab[1] = (String) z.getAttribute(ParcelSchema.getParcelCommunityField());
                tab[2] = (String) z.getAttribute(GeneralFields.getZonePreciseNameField());
                DefaultFeatureCollection intersectingParcels = new DefaultFeatureCollection();
                // get the intersecting parcels
                try (SimpleFeatureIterator parcelIt = cutParcel.features()) {
                    while (parcelIt.hasNext()) {
                        SimpleFeature parcel = parcelIt.next();
                        if (((Geometry) z.getDefaultGeometry()).buffer(0.5).contains((Geometry) parcel.getDefaultGeometry()))
                            intersectingParcels.add(parcel);
                    }
                } catch (Exception problem) {
                    problem.printStackTrace();
                }
                double iniA = ((Geometry) z.getDefaultGeometry()).getArea();
                GeneralFields.setParcelFieldType("every");
                double pNew = areaParcelNewlySimulated(intersectingParcels);
                GeneralFields.setParcelFieldType("french");
                if (pNew == 0.0)
                    continue;
                tab[3] = Double.toString(iniA);
                tab[4] = Double.toString(pNew);
                tab[5] = Double.toString(1 - (pNew / iniA));
                // get the ratio of parcels having a connection to the road
                tab[6] = String.valueOf(((double) Arrays.stream(intersectingParcels.toArray(new SimpleFeature[0]))
                        .filter(feat -> ParcelState.isParcelHasRoadAccess(Polygons.getPolygon((Geometry) feat.getDefaultGeometry()),
                                CollecTransform.selectIntersection(roads, ((Geometry) feat.getDefaultGeometry())),
                                CollecTransform.fromPolygonSFCtoRingMultiLines(CollecTransform.selectIntersection(blocks, (Geometry) z.getDefaultGeometry()))))
                        .count() / (double) intersectingParcels.size()));
                stat.put(count++ + "-" + tab[1] + "-" + tab[2], tab);
            }
        } catch (Exception problem) {
            problem.printStackTrace();
        }
        dsRoad.dispose();
        CsvExport.generateCsvFile(stat, folderOutStat, "streetRatioParcelZone", !overwrite, firstLine);
        overwrite = false;
        CsvExport.needFLine = true;
    }

    /**
     * Get the area of parcels that have been marked with a simulated field
     *
     * @param markedParcels input set of parcels with marked fileds
     * @return the sum of marked parcel's area
     */
    private static double areaParcelNewlySimulated(SimpleFeatureCollection markedParcels) {
        double totArea = 0.0;
        try (SimpleFeatureIterator parcels = markedParcels.features()) {
            while (parcels.hasNext()) {
                SimpleFeature parcel = parcels.next();
                if (GeneralFields.isParcelHasSimulatedFields(parcel))
                    totArea += ((Geometry) parcel.getDefaultGeometry()).getArea();
            }
        } catch (Exception problem) {
            problem.printStackTrace();
        }
        return totArea;
    }
}
