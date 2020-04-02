package fr.ign.artiscales.fields;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
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

import fr.ign.artiscales.parcelFunction.ParcelAttribute;
import fr.ign.artiscales.parcelFunction.ParcelSchema;
import fr.ign.cogit.geoToolsFunctions.vectors.Collec;

public class FrenchParcelFields {

	/**
	 * Make sure the parcel collection contains all the required fields for Parcel Manager simulation.
	 * 
	 * @param parcels
	 *            Input parcel {@link SimpleFeatureCollection}.
	 * @return A {@link SimpleFeatureCollection} with the fileds of parcels that have been converted to the {@link fr.ign.artiscales.parcelFunction.ParcelSchema#getSFBMinParcel()}
	 *         schema.
	 * @throws FactoryException
	 * @throws NoSuchAuthorityCodeException
	 * @throws IOException
	 */
	public static SimpleFeatureCollection frenchParcelToMinParcel(SimpleFeatureCollection parcels) throws NoSuchAuthorityCodeException, FactoryException, IOException {
		SimpleFeatureBuilder builder = ParcelSchema.getSFBMinParcel();
		DefaultFeatureCollection result = new DefaultFeatureCollection();
		try (SimpleFeatureIterator parcelIt = parcels.features()){
			while (parcelIt.hasNext()) {
				SimpleFeature parcel = parcelIt.next();
				result.add(ParcelSchema.setSFBMinParcelWithFeat(parcel, builder, parcel.getFeatureType()).buildFeature(null));
			}
		} catch (Exception problem) {
			problem.printStackTrace();
		}
		return result.collection();
	}
	
	/**
	 * Fix the parcel attributes of a simulated {@link SimpleFeatureCollection} of parcel with original parcels. 
	 * 
	 * @param parcels
	 * @param initialParcels
	 * @return A {@link SimpleFeatureCollection} with their original attributes
	 * @throws Exception
	 */
	public static SimpleFeatureCollection setOriginalFrenchParcelAttributes(SimpleFeatureCollection parcels, SimpleFeatureCollection initialParcels) throws Exception {
		DefaultFeatureCollection parcelFinal = new DefaultFeatureCollection();
		SimpleFeatureBuilder featureBuilder = ParcelSchema.getSFBFrenchParcel();
		try (SimpleFeatureIterator parcelIt = parcels.features()){
			while (parcelIt.hasNext()) {
				SimpleFeature parcel = parcelIt.next();
				SimpleFeature iniParcel = Collec.getSimpleFeatureFromSFC((Geometry) parcel.getDefaultGeometry(), initialParcels);
				if (GeneralFields.isParcelLikeFrenchHasSimulatedFileds(parcel)) {
					iniParcel = parcel ; 
				}
				featureBuilder.set("the_geom", parcel.getDefaultGeometry());
				String section = (String) iniParcel.getAttribute(ParcelSchema.getMinParcelNumberField());
				featureBuilder.set("SECTION", section);
				String numero = (String) iniParcel.getAttribute(ParcelSchema.getMinParcelNumberField());
				featureBuilder.set("NUMERO", numero);
				String insee = (String) iniParcel.getAttribute(ParcelSchema.getMinParcelCommunityField());
				featureBuilder.set("CODE_DEP", insee.substring(0, 2));
				featureBuilder.set("CODE_COM", insee.substring(2, 5));
				featureBuilder.set("CODE", insee + "000" + section + numero);
				featureBuilder.set("COM_ABS", "000");
				parcelFinal.add(featureBuilder.buildFeature(null));
			}
		} catch (Exception problem) {
			problem.printStackTrace();
		}
		return parcelFinal.collection();
	}
	
	
	/**
	 * Fix the parcel attribute for a french parcel collection. If a parcel has intact attributes, they will be copied. If the parcel has been simulated and misses some attributes,
	 * they will be generated.
	 * 
	 * @param parcels
	 * @param communityFile
	 * @return The French Parcel {@link SimpleFeatureCollection} with fixed attributes
	 * @throws IOException
	 * @throws FactoryException
	 * @throws NoSuchAuthorityCodeException
	 */
	public static SimpleFeatureCollection fixParcelAttributes(SimpleFeatureCollection parcels, File communityFile) throws IOException, NoSuchAuthorityCodeException, FactoryException {
		DefaultFeatureCollection parcelFinal = new DefaultFeatureCollection();
		int i = 0;
		SimpleFeatureBuilder featureBuilder = ParcelSchema.getSFBFrenchParcel();
		// city information
		ShapefileDataStore shpDSCities = new ShapefileDataStore(communityFile.toURI().toURL());
		SimpleFeatureCollection citiesSFS = shpDSCities.getFeatureSource().getFeatures();
		try (SimpleFeatureIterator parcelIt = parcels.features()){
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
					featureBuilder.set("CODE", makeFrenchParcelCode(parcel));
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
		}
		shpDSCities.dispose();
		return parcelFinal.collection();
	}
	
	/**
	 * generate French parcels informations for displaying purposes.
	 * 
	 * @param parcel
	 *            Input {@link SimpleFeature} parcel.
	 * @return French parcels informations
	 */
	public static String showFrenchParcel(SimpleFeature parcel) {
		return "Parcel in " + (String) parcel.getAttribute("CODE_DEP") + ((String) parcel.getAttribute("CODE_COM")) + ". Section "
				+ ((String) parcel.getAttribute("SECTION")) + " and number " + ((String) parcel.getAttribute("NUMERO"));
	}
	
	/**
	 * Construct a french parcel code
	 * 
	 * @param parcel
	 *            French parcel feature
	 * @return the string code
	 */
	public static String makeFrenchParcelCode(SimpleFeature parcel) {
		return ((String) parcel.getAttribute("CODE_DEP")) + ((String) parcel.getAttribute("CODE_COM")) + ((String) parcel.getAttribute("COM_ABS"))
				+ ((String) parcel.getAttribute("SECTION")) + ((String) parcel.getAttribute("NUMERO"));
	}
	
	/**
	 * Get the parcel codes (Attribute CODE of the given SimpleFeatureCollection)
	 * 
	 * @param parcels
	 *            {@link SimpleFeatureCollection} of parcels
	 * @return A list with all unique parcel codes
	 */
	public static List<String> getFrenchCodeParcels(SimpleFeatureCollection parcels) {
		List<String> result = new ArrayList<String>();
		try (SimpleFeatureIterator parcelIt = parcels.features()) {
			while (parcelIt.hasNext()) {
				SimpleFeature feat = parcelIt.next();
				String code = ((String) feat.getAttribute("CODE"));
				if (code != null && !code.isEmpty()) {
					result.add(code);
				} else {
					try {
						result.add(FrenchParcelFields.makeFrenchParcelCode(feat));
					} catch (Exception e) {
					}
				}
			}
		} catch (Exception problem) {
			problem.printStackTrace();
		}
		return result;
	}
}
