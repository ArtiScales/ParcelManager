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

import fr.ign.cogit.geoToolsFunctions.Attribute;
import fr.ign.cogit.parcelFunction.ParcelAttribute;
import fr.ign.cogit.parcelFunction.ParcelSchema;
import fr.ign.cogit.parcelFunction.ParcelState;

public class ArtiScalesParcelFields {
	/**
	 * Set the parcel's attribute after a parcel recomposition processus based on the French model. Won't change parcel's information if they are already set
	 * 
	 * @param parcels       : Whole set of parcels
	 * @param tmpFolder     : A temporary folder where will be saved intermediate results
	 * @param buildingFile  : A shapefile containing the builings of the zone.
	 * @param communityFile : A shapefile containing the communities of the zone.
	 * @param polygonIntersectionFile : A shapefile containing outputs of MUP-City. Can be empty
	 * @return The parcel set with the right attributes
	 * @throws Exception
	 */
	public static SimpleFeatureCollection fixParcelAttributes(SimpleFeatureCollection parcels, File tmpFolder,
			File buildingFile, File communityFile, File polygonIntersectionFile, File zoningFile, boolean allOrCell)
			throws Exception {
		SimpleFeatureCollection parcelsFrenched = FrenchParcelFields.fixParcelAttributes(parcels, tmpFolder, communityFile);
		DefaultFeatureCollection parcelFinal = new DefaultFeatureCollection();

		int i = 0;
		SimpleFeatureIterator parcelIt = parcelsFrenched.features();
		SimpleFeatureBuilder featureBuilder = ParcelSchema.getSFBParcelAsAS();

		ShapefileDataStore shpDSCells = new ShapefileDataStore(polygonIntersectionFile.toURI().toURL());
		SimpleFeatureCollection cellsSFS = shpDSCells.getFeatureSource().getFeatures();
		try {
			while (parcelIt.hasNext()) {
				boolean newlyGenerate = true;
				i++;
				SimpleFeature parcel = parcelIt.next();
				featureBuilder.set("the_geom", parcel.getDefaultGeometry());

				String section = (String) parcel.getAttribute("SECTION");
				featureBuilder.set("INSEE", Attribute.makeINSEECode(parcel));
				featureBuilder.set("CODE_DEP", parcel.getAttribute("CODE_DEP"));
				featureBuilder.set("CODE_COM", parcel.getAttribute("CODE_COM"));
				featureBuilder.set("SECTION", section);
				featureBuilder.set("NUMERO", parcel.getAttribute("NUMERO"));
				featureBuilder.set("CODE", ParcelAttribute.makeParcelCode(parcel));
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
				} else if ((allOrCell && newlyGenerate) || (ParcelState.isParcelInCell(parcel, cellsSFS) && !iPB)) {
					featureBuilder.set("DoWeSimul", "true");
					featureBuilder.set("eval", ParcelState.getEvalInParcel(parcel, polygonIntersectionFile));
				} else {
					featureBuilder.set("DoWeSimul", "false");
					featureBuilder.set("eval", 0);
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

		return parcelFinal.collection();
	}
}