package fr.ign.artiscales.fields.artiscales;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.geotools.data.shapefile.ShapefileDataStore;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.feature.DefaultFeatureCollection;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.locationtech.jts.geom.Geometry;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.NoSuchAuthorityCodeException;

import fr.ign.artiscales.fields.french.FrenchParcelFields;
import fr.ign.artiscales.parcelFunction.ParcelState;
import fr.ign.cogit.geoToolsFunctions.vectors.Collec;

public class ArtiScalesParcelFields {
	/**
	 * Set the parcel's attribute after a parcel recomposition processus based on the French model. Won't change parcel's information if they are already set
	 * 
	 * @param parcels
	 *            Whole set of parcels
	 * @param buildingFile
	 *            A shapefile containing the builings of the zone.
	 * @param polygonIntersectionFile
	 *            A shapefile containing outputs of MUP-City. Can be empty
	 * @return The parcel set with the right attributes
	 * @throws IOException 
	 * @throws FactoryException 
	 * @throws NoSuchAuthorityCodeException 
	 */
	public static SimpleFeatureCollection fixParcelAttributes(SimpleFeatureCollection parcels, SimpleFeatureCollection originalParcels, File buildingFile,
			File polygonIntersectionFile, File zoningFile, boolean allOrCell) throws NoSuchAuthorityCodeException, FactoryException, IOException   {
		SimpleFeatureCollection parcelsFrenched = FrenchParcelFields.setOriginalFrenchParcelAttributes(parcels, originalParcels);
		DefaultFeatureCollection parcelFinal = new DefaultFeatureCollection();
		int i = 0;
		SimpleFeatureBuilder featureBuilder = ArtiScalesSchemas.getSFBParcelAsAS();
		ShapefileDataStore shpDSCells = new ShapefileDataStore(polygonIntersectionFile.toURI().toURL());
		SimpleFeatureCollection cellsSFS = shpDSCells.getFeatureSource().getFeatures();
		try (SimpleFeatureIterator parcelIt = parcelsFrenched.features()){
			while (parcelIt.hasNext()) {
				boolean newlyGenerate = true;
				i++;
				SimpleFeature parcel = parcelIt.next();
				featureBuilder.set("the_geom", parcel.getDefaultGeometry());

				String section = (String) parcel.getAttribute("SECTION");
				featureBuilder.set("INSEE", FrenchParcelFields.makeINSEECode(parcel));
				featureBuilder.set("CODE_DEP", parcel.getAttribute("CODE_DEP"));
				featureBuilder.set("CODE_COM", parcel.getAttribute("CODE_COM"));
				featureBuilder.set("SECTION", section);
				featureBuilder.set("NUMERO", parcel.getAttribute("NUMERO"));
				featureBuilder.set("CODE", FrenchParcelFields.makeFrenchParcelCode(parcel));
				featureBuilder.set("COM_ABS", "000");
				
				boolean iPB = ParcelState.isAlreadyBuilt(buildingFile, parcel, (Geometry) parcel.getDefaultGeometry());
				featureBuilder.set("IsBuild", iPB);

				// BigZoneAttribute
				// if those attributes are already set, it means we can switch this step and the parcel is not new
				if (parcel.getAttribute("U") != null) {
					featureBuilder.set("U", parcel.getAttribute("U"));
					featureBuilder.set("AU", parcel.getAttribute("AU"));
					featureBuilder.set("NC", parcel.getAttribute("NC"));
					newlyGenerate = false;
				}
				// else, we search for every zones that are intersecting the parcel
				else {
					List<String> listBigZone = ParcelState.parcelInBigZone(parcel, zoningFile);
					if (listBigZone.contains("U")) {
						featureBuilder.set("U", true);
					} else {
						featureBuilder.set("U", false);
					}
					if (listBigZone.contains("AU")) {
						featureBuilder.set("AU", true);
					} else {
						featureBuilder.set("AU", false);
					}
					if (listBigZone.contains("NC")) {
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
				} else if ((allOrCell && newlyGenerate) || (Collec.isFeatIntersectsSFC(parcel, cellsSFS) && !iPB)) {
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
		shpDSCells.dispose();
		return parcelFinal.collection();
	}
}
