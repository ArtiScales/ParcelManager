package fr.ign.artiscales.pm.fields.artiscales;

import fr.ign.artiscales.pm.fields.french.FrenchParcelFields;
import fr.ign.artiscales.pm.parcelFunction.ParcelState;
import fr.ign.artiscales.tools.geoToolsFunctions.vectors.collec.CollecMgmt;
import fr.ign.artiscales.tools.geoToolsFunctions.vectors.collec.OpOnCollec;
import org.geotools.data.DataStore;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.feature.DefaultFeatureCollection;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.locationtech.jts.geom.Geometry;
import org.opengis.feature.simple.SimpleFeature;

import java.io.File;
import java.io.IOException;

public class ArtiScalesParcelFields {
    /**
     * Set the parcel's attribute after a parcel recomposition processus based on the French model. Won't change parcel's information if they are already set
     *
     * @param parcels                 Whole set of parcels
     * @param buildingFile            A Geopacakge containing the builings of the zone.
     * @param polygonIntersectionFile A Geopacakge containing outputs of MUP-City. Can be empty
     * @param originalParcels
     * @param zoningFile
     * @param allOrCell
     * @return The parcel set with the right attributes
     * @throws IOException
     */
    public static SimpleFeatureCollection fixParcelAttributes(SimpleFeatureCollection parcels, SimpleFeatureCollection originalParcels, File buildingFile,
                                                              File polygonIntersectionFile, File zoningFile, boolean allOrCell) throws IOException {
        SimpleFeatureCollection parcelsFrenched = FrenchParcelFields.setOriginalFrenchParcelAttributes(parcels, originalParcels);
        DefaultFeatureCollection parcelFinal = new DefaultFeatureCollection();
        int i = 0;
        SimpleFeatureBuilder featureBuilder = ArtiScalesSchemas.getSFBParcelAsAS();
        DataStore dsCells = CollecMgmt.getDataStore(polygonIntersectionFile);
        SimpleFeatureCollection cellsSFS = dsCells.getFeatureSource(dsCells.getTypeNames()[0]).getFeatures();
        try (SimpleFeatureIterator parcelIt = parcelsFrenched.features()) {
            while (parcelIt.hasNext()) {
                boolean newlyGenerate = true;
                i++;
                SimpleFeature parcel = parcelIt.next();
                featureBuilder.set(CollecMgmt.getDefaultGeomName(), parcel.getDefaultGeometry());

                String section = (String) parcel.getAttribute("SECTION");
                featureBuilder.set("INSEE", FrenchParcelFields.makeDEPCOMCode(parcel));
                featureBuilder.set("CODE_DEP", parcel.getAttribute("CODE_DEP"));
                featureBuilder.set("CODE_COM", parcel.getAttribute("CODE_COM"));
                featureBuilder.set("SECTION", section);
                featureBuilder.set("NUMERO", parcel.getAttribute("NUMERO"));
                featureBuilder.set("CODE", FrenchParcelFields.makeFrenchParcelCode(parcel));
                featureBuilder.set("COM_ABS", "000");

                boolean iPB = ParcelState.isAlreadyBuilt(buildingFile, parcel, (Geometry) parcel.getDefaultGeometry());
                featureBuilder.set("IsBuild", iPB);

                // GenericAttribute
                // if those attributes are already set, it means we can switch this step and the parcel is not new
                if (parcel.getAttribute("U") != null) {
                    featureBuilder.set("U", parcel.getAttribute("U"));
                    featureBuilder.set("AU", parcel.getAttribute("AU"));
                    featureBuilder.set("NC", parcel.getAttribute("NC"));
                    newlyGenerate = false;
                }
                // else, we search for every zones that are intersecting the parcel
                else {
                    String listBigZone = ParcelState.parcelInGenericZone(zoningFile, parcel);
                    if (listBigZone.equals("U")) {
                        featureBuilder.set("U", true);
                    } else {
                        featureBuilder.set("U", false);
                    }
                    if (listBigZone.equals("AU")) {
                        featureBuilder.set("AU", true);
                    } else {
                        featureBuilder.set("AU", false);
                    }
                    if (listBigZone.equals("NC")) {
                        featureBuilder.set("NC", true);
                    } else {
                        featureBuilder.set("NC", false);
                    }
                }
                // Simulation information
                // if already set from the parcel file
                if (!newlyGenerate && parcel.getAttribute("DoWeSimul") != null) {
                    featureBuilder.set("DoWeSimul", parcel.getAttribute("DoWeSimul"));
                    featureBuilder.set("eval", parcel.getAttribute("eval"));
                } else if ((allOrCell && newlyGenerate) || (OpOnCollec.isFeatIntersectsSFC(parcel, cellsSFS) && !iPB)) {
                    featureBuilder.set("DoWeSimul", "true");
                    featureBuilder.set("eval", ParcelState.getEvalInParcel(parcel, polygonIntersectionFile));
                } else {
                    featureBuilder.set("DoWeSimul", "false");
                    featureBuilder.set("eval", 0);
                }
                parcelFinal.add(featureBuilder.buildFeature(Integer.toString(i)));
            }
        } catch (Exception problem) {
            problem.printStackTrace();
        }
        dsCells.dispose();
        return parcelFinal.collection();
    }
}
