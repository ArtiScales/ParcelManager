package fr.ign.artiscales.pm.usecase;

import com.opencsv.CSVReader;
import fr.ign.artiscales.pm.division.DivisionType;
import fr.ign.artiscales.pm.workflow.ConsolidationDivision;
import fr.ign.artiscales.pm.workflow.Workflow;
import fr.ign.artiscales.tools.geoToolsFunctions.vectors.collec.CollecMgmt;
import fr.ign.artiscales.tools.io.csv.CsvTransformation;
import fr.ign.artiscales.tools.parameter.ProfileUrbanFabric;
import org.geotools.data.DataStore;
import org.geotools.data.simple.SimpleFeatureCollection;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class GeneratePSE {
    public static void main(String[] args) throws IOException {
        simulateZoneDivisionFromCSV(new File("src/main/resources/pse/population500OBBSS.csv"), new File("src/main/resources/pse/InputData/road.gpkg"),
                new File("src/main/resources/pse/InputData/parcel.gpkg"), new File("/tmp/pseOBBSS/"), "OBBThenSS");
    }

    public static void simulateZoneDivisionFromCSV(File csvIn, File roadFile, File parcelFile, File outFolder, String process) throws IOException {
        CSVReader r = new CSVReader(new FileReader(csvIn));
        outFolder.mkdir();
        String[] firstLine = r.readNext();
        List<Integer> listId = new ArrayList<>();
        for (int i = 0; i < firstLine.length; i++)
            if (firstLine[i].startsWith("objective"))
                listId.add(i);
        DataStore parcelDS = CollecMgmt.getDataStore(parcelFile);
        SimpleFeatureCollection parcel = parcelDS.getFeatureSource(parcelDS.getTypeNames()[0]).getFeatures();
        int i = 0;
        Workflow.PROCESS = DivisionType.valueOf(process);
        for (String[] line : r.readAll()) {
            ProfileUrbanFabric p = new ProfileUrbanFabric(firstLine, line);
            setFixParameters(DivisionType.valueOf(process), p);
            CollecMgmt.exportSFC((new ConsolidationDivision()).consolidationDivision(parcel, roadFile, outFolder, p),
                    new File(outFolder, CsvTransformation.makeLine(listId, line)));

        }
        r.close();
    }


    private static void setFixParameters(DivisionType divisionType, ProfileUrbanFabric profile) {
        switch (divisionType) {
            case OBBThenSS:
                profile.setStreetWidth(7.0);
                profile.setLaneWidth(5.0);
                profile.setMinimalArea(80.0);
                profile.setMinimalWidthContactRoad(15.0);
                profile.setMaxWidth(15.0);
                profile.setMaxDepth(0.0);
                profile.setMaxDistanceForNearestRoad(50);
                break;
            case OBB:
                profile.setStreetWidth(15);
                profile.setLaneWidth(10);
                profile.setMinimalArea(80);
                break;
        }
    }
}
