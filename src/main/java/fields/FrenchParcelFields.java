package fields;

import java.io.File;
import java.io.IOException;

import org.geotools.data.shapefile.ShapefileDataStore;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.feature.DefaultFeatureCollection;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.NoSuchAuthorityCodeException;

import fr.ign.cogit.parcelFunction.ParcelAttribute;
import fr.ign.cogit.parcelFunction.ParcelSchema;

public class FrenchParcelFields {

	/**
	 * Make sure the parcel collection contains all the required fields for Parcel Manager simulation. 
	 * @param parcels
	 * @return
	 * @throws FactoryException 
	 * @throws NoSuchAuthorityCodeException 
	 * @throws IOException 
	 */
	public static SimpleFeatureCollection frenchParcelToMinParcel(SimpleFeatureCollection parcels) throws NoSuchAuthorityCodeException, FactoryException, IOException {
		SimpleFeatureBuilder builder = ParcelSchema.getSFBMinParcel();
		SimpleFeatureIterator parcelIt = parcels.features();
		DefaultFeatureCollection result = new DefaultFeatureCollection();
		try {
			while (parcelIt.hasNext()) {
				SimpleFeature parcel = parcelIt.next();
				result.add(ParcelSchema.setSFBMinParcelWithFeat(parcel, builder, parcel.getFeatureType()).buildFeature(null));
			}
		} catch (Exception problem) {
			problem.printStackTrace();
		} finally {
			parcelIt.close();
		}
		return result.collection();
	}
	
	public static SimpleFeatureCollection setOriginalFrenchParcelAttributes(SimpleFeatureCollection parcels, SimpleFeatureCollection initialParcels) throws Exception {
		DefaultFeatureCollection parcelFinal = new DefaultFeatureCollection();
		SimpleFeatureIterator parcelIt = parcels.features();
		SimpleFeatureBuilder featureBuilder = ParcelSchema.getSFBFrenchParcel();
		try {
			while (parcelIt.hasNext()) {
				SimpleFeature parcel = parcelIt.next();
				featureBuilder.set("the_geom", parcel.getDefaultGeometry());
				String section = (String) parcel.getAttribute(ParcelSchema.getMinParcelNumberField());
				featureBuilder.set("SECTION", section);
				String numero = (String) parcel.getAttribute(ParcelSchema.getMinParcelNumberField());
				featureBuilder.set("NUMERO", numero);
				String insee = (String) parcel.getAttribute(ParcelSchema.getMinParcelCommunityFiled());
				featureBuilder.set("CODE_DEP", insee.substring(0, 2));
				featureBuilder.set("CODE_COM", insee.substring(2, 5));
				featureBuilder.set("CODE", insee + "000" + section + numero);
				featureBuilder.set("COM_ABS", "000");
				parcelFinal.add(featureBuilder.buildFeature(null));
			}
		} catch (Exception problem) {
			problem.printStackTrace();
		} finally {
			parcelIt.close();
		}
		return parcelFinal.collection();
	}
	
	
	/**
	 * Fix the parcel attribute for a french parcel collection. If a parcel has intact attributes, they will be copied. If the parcel has been simulated and misses some attributes,
	 * they will be generated.
	 * 
	 * @param parcels
	 * @param tmpFolder
	 * @param communityFile
	 * @return
	 * @throws Exception
	 */
	public static SimpleFeatureCollection fixParcelAttributes(SimpleFeatureCollection parcels, File communityFile) throws Exception {
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
				if (parcel.getAttribute("CODE_COM") != null) {
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
				parcelFinal.add(featureBuilder.buildFeature(Integer.toString(i)));
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
