package fields;

import java.io.File;

import org.geotools.data.shapefile.ShapefileDataStore;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.feature.DefaultFeatureCollection;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.opengis.feature.simple.SimpleFeature;

import fr.ign.cogit.parcelFunction.ParcelAttribute;
import fr.ign.cogit.parcelFunction.ParcelSchema;

public class FrenchParcelFields {

	/**
	 * 
	 * @param parcels
	 * @param tmpFolder
	 * @param communityFile
	 * @return
	 * @throws Exception
	 */
	public static SimpleFeatureCollection fixParcelAttributes(SimpleFeatureCollection parcels, File tmpFolder,
			File communityFile) throws Exception {
		DefaultFeatureCollection parcelFinal = new DefaultFeatureCollection();
		int i = 0;
		SimpleFeatureIterator parcelIt = parcels.features();
		SimpleFeatureBuilder featureBuilder = ParcelSchema.getSFBFrenchParcel();
		// city information
		ShapefileDataStore shpDSCities = new ShapefileDataStore(communityFile.toURI().toURL());
		SimpleFeatureCollection citiesSFS = shpDSCities.getFeatureSource().getFeatures();
		try {
			while (parcelIt.hasNext()) {
				i++;
				SimpleFeature parcel = parcelIt.next();
				featureBuilder.set("the_geom", parcel.getDefaultGeometry());
				// if the parcel already have informations, we just copy them
				if (parcel.getAttribute("NUMERO") != null) {
					String section = (String) parcel.getAttribute("SECTION");
					featureBuilder.set("CODE_DEP", parcel.getAttribute("CODE_DEP"));
					featureBuilder.set("CODE_COM", parcel.getAttribute("CODE_COM"));
					featureBuilder.set("SECTION", section);
					featureBuilder.set("NUMERO", parcel.getAttribute("NUMERO"));
					featureBuilder.set("CODE", ParcelAttribute.makeFrenchParcelCode(parcel));
					featureBuilder.set("COM_ABS", "000");
				} else {
					// we need to get the infos from somewhere else
					// we get the city info
					String insee = ParcelAttribute.getCommunityCodeFromSFC(citiesSFS, parcel);
					featureBuilder.set("CODE_DEP", insee.substring(0, 2));
					featureBuilder.set("CODE_COM", insee.substring(2, 5));
					// should be already set in the previous method
					String section = (String) parcel.getAttribute("SECTION");
					featureBuilder.set("SECTION", section);
					featureBuilder.set("NUMERO", i);
					featureBuilder.set("CODE", insee + "000" + section + i);
					featureBuilder.set("COM_ABS", "000");
				}
				SimpleFeature feat = featureBuilder.buildFeature(Integer.toString(i));
				parcelFinal.add(feat);
			}
		} catch (Exception problem) {
			problem.printStackTrace();
		} finally {
			parcelIt.close();
		}
		shpDSCities.dispose();
		return parcelFinal.collection();
	}
}
