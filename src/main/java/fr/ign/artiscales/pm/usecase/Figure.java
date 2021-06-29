package fr.ign.artiscales.pm.usecase;

import fr.ign.artiscales.pm.division.OBBDivision;
import fr.ign.artiscales.pm.scenario.PMScenario;
import fr.ign.artiscales.pm.scenario.PMStep;
import fr.ign.artiscales.tools.geoToolsFunctions.vectors.collec.CollecMgmt;
import fr.ign.artiscales.tools.geoToolsFunctions.vectors.collec.CollecTransform;
import fr.ign.artiscales.tools.geometryGeneration.CityGeneration;
import fr.ign.artiscales.tools.parameter.ProfileUrbanFabric;
import org.geotools.data.DataStore;
import org.geotools.data.simple.SimpleFeatureCollection;

import java.io.File;

/**
 * Method generating figures in Colomb 2021 et al.
 */
public class Figure {
    public static void main(String[] args) throws Exception {
        long start = System.currentTimeMillis();
        // Generate parcel reshaping exemplifying the road network generation in Section 3 of Colomb et al. 2021.
        PMStep.setGENERATEATTRIBUTES(false);
        PMScenario.setReuseSimulatedParcels(false);
//        PMScenario pm = new PMScenario(new File("src/main/resources/Figure/scenarioRoadCreation.json"));
//        pm.executeStep();
//        PMScenario pmSS = new PMScenario(new File("src/main/resources/Figure/scenarioRoadCreationSS.json"));
//        pmSS.executeStep();

        // Generate parcel reshaping for the Straight Skeleton example in Section 3 of Colomb et al. 2021.
        PMScenario pmSSfig = new PMScenario(new File("src/main/resources/Figure/scenarioFigureSS.json"));
        UseCase.setSAVEINTERMEDIATERESULT(true);
        UseCase.setDEBUG(true);
        for (PMStep pmstep : pmSSfig.getStepList()) {
            System.out.println("try " + pmstep);
            // set new out folder to avoid overwriting
            PMStep.setOUTFOLDER(new File(PMStep.getOUTFOLDER(), (pmstep.isPeripheralRoad() ? "peripheralRoad" : "noPeripheralRoad") + "_"+(ProfileUrbanFabric.convertJSONtoProfile(new File(PMStep.getPROFILEFOLDER() + "/" + pmstep.getUrbanFabricType() + ".json")).getMaxDepth() != 0  ? "offset" : "noOffset")));
            pmstep.execute();
        }
        System.out.println("Figure took "+(System.currentTimeMillis() - start) + " milliseconds");

//        // Iteration step and depth of parameter
//        DataStore ds = CollecMgmt.getDataStore(new File("src/main/resources/Figure/It/parcel.gpkg"));
//        SimpleFeatureCollection p = ds.getFeatureSource(ds.getTypeNames()[0]).getFeatures();
//        SimpleFeatureCollection split = OBBDivision.splitParcels(p, null, 1000, 15, 0.5, 0,
//                CollecTransform.fromPolygonSFCtoListRingLines(CityGeneration.createUrbanBlock(p)), 2, 3, 5, true, 1);
//        CollecMgmt.exportSFC(split, new File("src/main/resources/Figure/It/pSplit.gpkg"));
//        ds.dispose();

    }
}
