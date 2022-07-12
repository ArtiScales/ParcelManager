package fr.ign.artiscales.pm.usecase;

import fr.ign.artiscales.pm.division.OBBDivision;
import fr.ign.artiscales.pm.parcel.SyntheticParcel;
import fr.ign.artiscales.pm.parcelFunction.ParcelState;
import fr.ign.artiscales.tools.geoToolsFunctions.vectors.geom.Lines;
import fr.ign.artiscales.tools.geoToolsFunctions.vectors.geom.Polygons;
import org.apache.commons.lang3.tuple.Pair;
import org.geotools.geometry.jts.WKTReader2;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.io.ParseException;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

public class GenerateSyntheticParcel {
    public static void main(String[] args) throws ParseException, IOException {
        generate(15, 0.65, 100, 0.001f, new File("/tmp/result.gpkg"));
    }

    /**
     * @param nbOwner               number of owners in the simulation
     * @param giniObjective         objective for the gini value of the distribution of parcel's area sums owned by single owners
     * @param approxNumberOfParcels very approximate number of parcels in the zone (will automatically be more - could be double). The more they are, the best the gini value can be reached
     * @param tolerence             difference that the distribution can have between the objective gini value and the effective gini value
     * @param exportFile            if not null, write the parcels in a geopackage
     * @return Parcels with attributes
     */
    public static List<SyntheticParcel> generate(int nbOwner, double giniObjective, int approxNumberOfParcels, float tolerence, File exportFile) {
        Geometry iniZone = createInitialZone();
        List<Polygon> lP = new ArrayList<>();
        HashMap<Integer, Geometry> regionIDS = new HashMap<>();

        assert iniZone != null;
        double maximalArea = iniZone.getArea() / approxNumberOfParcels;
        int i = 1;
        for (Polygon subRegion : Polygons.getPolygons(iniZone)) {
            lP.addAll(OBBDivision.decompose(subRegion, Lines.getLineStrings(iniZone), null, maximalArea,
                    0, 0.5, 0.5,
                    0, 0, 0, false, 0, 0
//                    10, 2, 20, false, 2, 0
            ).stream().map(Pair::getLeft).collect(Collectors.toList()));
            //dummy task to remove initial polygon which is returned by the previous method
            lP.remove(lP.stream().filter(p -> p.getArea() == subRegion.getArea()).findFirst().get());
            regionIDS.put(i++, subRegion);
        }

        List<SyntheticParcel> lSP = new ArrayList<>(lP.size());
        for (Polygon p : lP)
            lSP.add(new SyntheticParcel(p, p.getArea(), p.distance(iniZone.getCentroid()), ParcelState.countParcelNeighborhood(p, lP), 0,
                    regionIDS.keySet().stream().filter(regionID -> regionIDS.get(regionID).buffer(1).contains(p)).findFirst().get()));
        // initialize parcel ownership : nobody left behind
        if (!initializeOwnership(lSP, nbOwner))
            return null;

        double currentGini = gini(lSP.stream().map(sp -> sp.area).collect(Collectors.toList()));
        int tentatives = 0;
        while (Math.abs(currentGini - giniObjective) > tolerence && tentatives < 1000000) {
            List<SyntheticParcel> newLSP = correctOwnership(lSP, nbOwner);
            if (Math.abs(gini(SyntheticParcel.sumOwnerOwnedArea(newLSP)) - giniObjective) <
                    Math.abs(gini(SyntheticParcel.sumOwnerOwnedArea(lSP)) - giniObjective)) {
                lSP = newLSP;
                currentGini = gini(SyntheticParcel.sumOwnerOwnedArea(lSP));
            }
            tentatives++;
        }
        if (tentatives == 1000000) {
            System.out.println("gini unreachable. change parameters. return null");
            return null;
        }

        //set parcel neighborhood number
        for (SyntheticParcel sp : lSP)
            sp.setIdNeighborhood(lSP);

        // not really needed infos
        System.out.println("final gini for parcels : " + gini(SyntheticParcel.sumOwnerOwnedArea(lSP)));
        if (exportFile != null)
            try {
                SyntheticParcel.exportToGPKG(lSP, exportFile);
            } catch (IOException e) {
                e.printStackTrace();
            }
        return lSP;
    }

    private static boolean initializeOwnership(List<SyntheticParcel> lSP, int nbOwner) {
        int iteration = 0;
        do {
            for (SyntheticParcel sp : lSP)
                sp.ownerID = getRandomNumberInRange(1, nbOwner);
            iteration++;
        } while (lSP.stream().map(sp -> sp.ownerID).distinct().count() != nbOwner && iteration < 10000);
        if (iteration == 10000)
            System.out.println("Cannot intiate ownership (too much owner ?). Return null");
        return iteration != 10000;
    }

    private static List<SyntheticParcel> correctOwnership(List<SyntheticParcel> lSP, int nbOwner) {
        ArrayList<SyntheticParcel> newList = new ArrayList<>(lSP.size());
        for (SyntheticParcel sp : lSP) //clone
            newList.add(new SyntheticParcel(sp.geom, sp.area, sp.distanceToCenter, sp.nbNeighborhood, sp.ownerID, sp.regionID));
        do
            newList.get(getRandomNumberInRange(0, newList.size() - 1)).ownerID = getRandomNumberInRange(1, nbOwner);
        while (newList.stream().map(sp -> sp.ownerID).distinct().count() != nbOwner);
        return newList;
    }

    public static int getRandomNumberInRange(int min, int max) {
        if (min >= max)
            throw new IllegalArgumentException("max must be greater than min");
        return new Random().nextInt((max - min) + 1) + min;
    }

    public static double gini(List<Double> values) {
        double sumOfDifference = values.stream().flatMapToDouble(v1 -> values.stream().mapToDouble(v2 -> Math.abs(v1 - v2))).sum();
        double mean = values.stream().mapToDouble(v -> v).average().getAsDouble();
        return sumOfDifference / (2 * values.size() * values.size() * mean);
    }

    public static Geometry createInitialZone() {
        try {
            return new WKTReader2().read("MultiPolygon (((0 0, 1000 0, 500 333, 0 0)),((0 0, 500 1000, 500 333,0 0)),((500 1000, 500 333, 1000 0, 500 1000)))");
        } catch (ParseException e) {
            e.printStackTrace();
        }
        return null;
    }
}


