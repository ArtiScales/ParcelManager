package fields;

import java.io.File;
import java.util.List;

import org.geotools.data.shapefile.ShapefileDataStore;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.feature.DefaultFeatureCollection;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.locationtech.jts.geom.Geometry;
import org.opengis.feature.simple.SimpleFeature;

import fr.ign.cogit.parcelFunction.ParcelAttribute;
import fr.ign.cogit.parcelFunction.ParcelSchema;
import fr.ign.cogit.parcelFunction.ParcelState;

public class ArtiScalesFields {
	/**
	 * Set the parcel's attribute after a parcel recomposition processus based on the French model. Won't change parcel's information if they are already set
	 * 
	 * @param parcels
	 *            : Whole set of parcels
	 * @param tmpFolder
	 *            : A temporary folder where will be saved intermediate results
	 * @param buildingFile
	 *            : A shapefile containing the builings of the zone
	 * @param communityFile
	 *            : A shapefile containing the communities of the zone
	 * @param mupOutputFile
	 *            : A shapefile containing outputs of MUP-City. Can be empty
	 * @return The parcel set with the right attributes
	 * @throws Exception
	 */
	public static SimpleFeatureCollection fixParcelAttributes(SimpleFeatureCollection parcels, File tmpFolder, File buildingFile, File communityFile,
			File mupOutputFile, File zoningFile, boolean allOrCell) throws Exception {

		DefaultFeatureCollection parcelFinal = new DefaultFeatureCollection();

		int i = 0;
		SimpleFeatureIterator parcelIt = parcels.features();
		SimpleFeatureBuilder featureBuilder = ParcelSchema.getSFBParcelAsAS();

		// city information
		ShapefileDataStore shpDSCities = new ShapefileDataStore(communityFile.toURI().toURL());
		SimpleFeatureCollection citiesSFS = shpDSCities.getFeatureSource().getFeatures();

		ShapefileDataStore shpDSCells = new ShapefileDataStore(mupOutputFile.toURI().toURL());
		SimpleFeatureCollection cellsSFS = shpDSCells.getFeatureSource().getFeatures();

		try {
			while (parcelIt.hasNext()) {
				boolean newlyGenerate = false;
				i++;
				SimpleFeature parcel = parcelIt.next();
				featureBuilder.set("the_geom", parcel.getDefaultGeometry());

				// if the parcel already have informations, we just copy them
				if (parcel.getAttribute("NUMERO") != null) {
					featureBuilder.set("INSEE", ParcelAttribute.makeINSEECode(parcel));
					featureBuilder.set("CODE_DEP", parcel.getAttribute("CODE_DEP"));
					featureBuilder.set("CODE_COM", parcel.getAttribute("CODE_COM"));

					featureBuilder.set("SECTION", parcel.getAttribute("SECTION"));
					featureBuilder.set("NUMERO", parcel.getAttribute("NUMERO"));
					featureBuilder.set("CODE", ParcelAttribute.makeParcelCode(parcel));
					featureBuilder.set("COM_ABS", "000");
				} else {
					newlyGenerate = true;
					// we get the city info
					String insee = ParcelAttribute.getInseeFromParcel(citiesSFS, parcel);

					featureBuilder.set("INSEE", insee);
					featureBuilder.set("CODE_DEP", insee.substring(0, 2));
					featureBuilder.set("CODE_COM", insee.substring(2, 5));

					// should be already set in the previous method
					String section = (String) parcel.getAttribute("SECTION");

					featureBuilder.set("SECTION", section);
					featureBuilder.set("NUMERO", i);
					featureBuilder.set("CODE", insee + "000" + section + i);
					featureBuilder.set("COM_ABS", "000");
				}

				boolean iPB = ParcelState.isAlreadyBuilt(buildingFile, parcel, (Geometry) parcel.getDefaultGeometry());
				featureBuilder.set("IsBuild", iPB);

				// BigZoneAttribute
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

				// Simulation information
				// if already set from the parcel file
				if (parcel.getAttribute("DoWeSimul") != null) {
					featureBuilder.set("DoWeSimul", parcel.getAttribute("DoWeSimul"));
					featureBuilder.set("eval", parcel.getAttribute("eval"));
				}
				// if not, we generate it
				else {
					if ((allOrCell && newlyGenerate) || (ParcelState.isParcelInCell(parcel, cellsSFS) && !iPB)) {
						featureBuilder.set("DoWeSimul", "true");
						featureBuilder.set("eval", ParcelState.getEvalInParcel(parcel, mupOutputFile));
					} else {
						featureBuilder.set("DoWeSimul", "false");
						featureBuilder.set("eval", 0);
					}
				}
				SimpleFeature feat = featureBuilder.buildFeature(Integer.toString(i));
				parcelFinal.add(feat);
			}
		} catch (Exception problem) {
			problem.printStackTrace();
		} finally {
			parcelIt.close();
		}

		shpDSCells.dispose();
		shpDSCities.dispose();

		return parcelFinal.collection();
	}
}
