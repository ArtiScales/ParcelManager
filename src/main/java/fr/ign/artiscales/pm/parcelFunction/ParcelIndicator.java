package fr.ign.artiscales.pm.parcelFunction;

import fr.ign.artiscales.pm.analysis.SingleParcelStat;
import fr.ign.artiscales.tools.geoToolsFunctions.vectors.collec.CollecMgmt;
import fr.ign.artiscales.tools.geoToolsFunctions.vectors.collec.CollecTransform;
import fr.ign.artiscales.tools.geoToolsFunctions.vectors.collec.OpOnCollec;
import fr.ign.artiscales.tools.indicator.Dispertion;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.geotools.data.DataStore;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.locationtech.jts.algorithm.match.HausdorffSimilarityMeasure;
import org.locationtech.jts.geom.Geometry;
import org.opengis.feature.simple.SimpleFeature;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ParcelIndicator {

    public static double giniArea(SimpleFeatureCollection parcels) {
        List<Double> lArea = new ArrayList<>(parcels.size());
        try (SimpleFeatureIterator parcelIt = parcels.features()) {
            while (parcelIt.hasNext())
                lArea.add(((Geometry) parcelIt.next().getDefaultGeometry()).getArea());
        } catch (Exception problem) {
            problem.printStackTrace();
        }
        if (lArea.isEmpty())
            return -1d;
        return Dispertion.gini(lArea);
    }

    public static double meanNeighborhood(SimpleFeatureCollection parcels) {
        DescriptiveStatistics lNeigh = new DescriptiveStatistics();
        try (SimpleFeatureIterator parcelIt = parcels.features()) {
            while (parcelIt.hasNext())
                lNeigh.addValue(ParcelState.countParcelNeighborhood((Geometry) parcelIt.next().getDefaultGeometry(), parcels));
        } catch (Exception problem) {
            problem.printStackTrace();
        }
        if (lNeigh.getN() == 0)
            return -1d;
        return lNeigh.getMean();
    }

    public static double meanAspectRatio(SimpleFeatureCollection collection) {
        DescriptiveStatistics ds = new DescriptiveStatistics();
        try(SimpleFeatureIterator it = collection.features()){
            while(it.hasNext()){
                ds.addValue(SingleParcelStat.aspectRatio((Geometry) it.next().getDefaultGeometry()));
            }
        }
        return ds.getMean();
    }



    /**
     * Calculation Hausdorff Similarity average for a set of parcels. Candidate must have been reduced before methode call
     *
     * @param parcelSFC           The reference geo file
     * @param parcelToCompareFile The geo file to compare
     * @return Hausdorff Similarity average
     * @throws IOException reading files
     */
    public static double hausdorffDistance(SimpleFeatureCollection parcelSFC, File parcelToCompareFile) throws IOException {
        DataStore sdsParcelToCompareFile = CollecMgmt.getDataStore(parcelToCompareFile);
        double result = 1 - hausdorffSimilarityAverage(parcelSFC,
                sdsParcelToCompareFile.getFeatureSource(sdsParcelToCompareFile.getTypeNames()[0]).getFeatures());
        sdsParcelToCompareFile.dispose();
        return result;
    }

    /**
     * Calculate the difference of number of parcels between two geo files.
     *
     * @param parcelSFC           The reference feature collection
     * @param parcelToCompareFile The geo file to compare
     * @return the difference of average (absolute value)
     * @throws IOException reading files
     */
    public static int diffNumberOfParcel(SimpleFeatureCollection parcelSFC, File parcelToCompareFile) throws IOException {
        DataStore dsParcelToCompareFile = CollecMgmt.getDataStore(parcelToCompareFile);
        int result = parcelSFC.size()
                - dsParcelToCompareFile.getFeatureSource(dsParcelToCompareFile.getTypeNames()[0]).getFeatures().size();
        dsParcelToCompareFile.dispose();
        return Math.abs(result);
    }

    /**
     * Calculate the difference between the average area of two Geopackages. Find a better indicator to compare distribution.
     *
     * @param parcelSFC           The reference Geopackage
     * @param parcelToCompareFile The Geopackage to compare
     * @return the difference of average (absolute value)
     * @throws IOException reading files
     */
    public static double diffAreaAverage(SimpleFeatureCollection parcelSFC, File parcelToCompareFile) throws IOException {
        DataStore dsParcelToCompareFile = CollecMgmt.getDataStore(parcelToCompareFile);
        double result = OpOnCollec.area(parcelSFC)
                - OpOnCollec.area(dsParcelToCompareFile.getFeatureSource(dsParcelToCompareFile.getTypeNames()[0]).getFeatures());
        dsParcelToCompareFile.dispose();
        return Math.abs(result);
    }

    /**
     * Calculation Hausdorff Similarity average for a set of parcels. Candidate must have been reduced before methode call
     *
     * @param parcelIn        reference parcel set
     * @param parcelToCompare parcels to compare shapes
     * @return Mean of Hausdorff distances
     */
    public static double hausdorffSimilarityAverage(SimpleFeatureCollection parcelIn, SimpleFeatureCollection parcelToCompare) {
        HausdorffSimilarityMeasure hausDis = new HausdorffSimilarityMeasure();
        DescriptiveStatistics stat = new DescriptiveStatistics();
        try (SimpleFeatureIterator parcelIt = parcelIn.features()) {
            while (parcelIt.hasNext()) {
                SimpleFeature parcel = parcelIt.next();
                Geometry parcelGeom = (Geometry) parcel.getDefaultGeometry();
                SimpleFeature parcelCompare = CollecTransform.getIntersectingSimpleFeatureFromSFC(parcelGeom, parcelToCompare);
                if (parcelCompare != null)
                    stat.addValue(hausDis.measure(parcelGeom, (Geometry) parcelCompare.getDefaultGeometry()));
            }
        } catch (Exception problem) {
            problem.printStackTrace();
        }
        return stat.getMean();
    }
}
