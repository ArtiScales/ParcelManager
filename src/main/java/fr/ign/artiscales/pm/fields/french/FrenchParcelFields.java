package fr.ign.artiscales.pm.fields.french;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.feature.DefaultFeatureCollection;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.locationtech.jts.geom.Geometry;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.AttributeDescriptor;

import fr.ign.artiscales.pm.fields.GeneralFields;
import fr.ign.artiscales.pm.parcelFunction.MarkParcelAttributeFromPosition;
import fr.ign.artiscales.pm.parcelFunction.ParcelSchema;
import fr.ign.artiscales.tools.geoToolsFunctions.Attribute;
import fr.ign.artiscales.tools.geoToolsFunctions.vectors.Collec;

public class FrenchParcelFields {

	/**
	 * Make sure the parcel collection contains all the required fields for Parcel Manager simulation.
	 * 
	 * @param parcels
	 *            Input parcel {@link SimpleFeatureCollection}.
	 * @return A {@link SimpleFeatureCollection} with the fileds of parcels that have been converted to the
	 *         {@link fr.ign.artiscales.pm.parcelFunction.ParcelSchema#getSFBMinParcel()} schema.
	 * @throws IOException
	 */
	public static SimpleFeatureCollection frenchParcelToMinParcel(SimpleFeatureCollection parcels) throws IOException {
		DefaultFeatureCollection result = new DefaultFeatureCollection();
		if (Collec.isCollecContainsAttribute(parcels, MarkParcelAttributeFromPosition.getMarkFieldName())) {
			SimpleFeatureBuilder builder = ParcelSchema.getSFBMinParcelSplit();
			try (SimpleFeatureIterator parcelIt = parcels.features()){
				while (parcelIt.hasNext()) {
					SimpleFeature parcel = parcelIt.next();
					result.add(ParcelSchema.setSFBMinParcelSplitWithFeat(parcel, builder, builder.getFeatureType(),(int) parcel.getAttribute(MarkParcelAttributeFromPosition.getMarkFieldName())).buildFeature(Attribute.makeUniqueId()));
				}
			} catch (Exception problem) {
				problem.printStackTrace();
			}	
		} else {
		SimpleFeatureBuilder builder = ParcelSchema.getSFBMinParcel();
		try (SimpleFeatureIterator parcelIt = parcels.features()){
			while (parcelIt.hasNext()) {
				SimpleFeature parcel = parcelIt.next();
				result.add(ParcelSchema.setSFBMinParcelWithFeat(parcel, builder, builder.getFeatureType()).buildFeature(Attribute.makeUniqueId()));
			}
		} catch (Exception problem) {
			problem.printStackTrace();
		}
		}
		return result.collection();
	}
	
	/**
	 * Fix the parcel attributes of a simulated {@link SimpleFeatureCollection} of parcel with original parcels. If a parcel has intact attributes, they will be copied. If the parcel has been simulated and misses some attributes,
	 * they will be generated.
	 * 
	 * @param parcels {@link SimpleFeatureCollection} containing parcels which to fix attributes
	 * @param initialParcels {@link SimpleFeatureCollection} containing the original parcels which their original attributes
	 * @return A {@link SimpleFeatureCollection} with their original attributes
	 * @throws IOException 
	 */
	public static SimpleFeatureCollection setOriginalFrenchParcelAttributes(SimpleFeatureCollection parcels, SimpleFeatureCollection initialParcels) throws IOException {
		DefaultFeatureCollection parcelFinal = new DefaultFeatureCollection();
		SimpleFeatureBuilder featureBuilder = FrenchParcelSchemas.getSFBFrenchParcel();
		try (SimpleFeatureIterator parcelIt = parcels.features()){
			while (parcelIt.hasNext()) {
				SimpleFeature parcel = parcelIt.next();
				SimpleFeature iniParcel;
				String insee;
				if (GeneralFields.isParcelLikeFrenchHasSimulatedFileds(parcel)) {
					iniParcel = parcel ; 
					insee = (String) iniParcel.getAttribute(ParcelSchema.getMinParcelCommunityField());
				} 
				else {
					iniParcel = Collec.getIntersectingSimpleFeatureFromSFC((Geometry) parcel.getDefaultGeometry(), initialParcels);
					try {
						insee = makeDEPCOMCode(iniParcel);
					} catch (Exception c) {
						insee = "";
						c.printStackTrace();
						System.out.println("rr " + iniParcel);
					}
				}
				featureBuilder.set(Collec.getDefaultGeomName(), parcel.getDefaultGeometry());
				String section = (String) iniParcel.getAttribute(ParcelSchema.getMinParcelSectionField());
				featureBuilder.set("SECTION", section);
				String numero = (String) iniParcel.getAttribute(ParcelSchema.getMinParcelNumberField());
				featureBuilder.set("NUMERO", numero);
				featureBuilder.set("CODE_DEP", Objects.requireNonNull(insee).substring(0, 2));
				featureBuilder.set("CODE_COM", insee.substring(2, 5));
				featureBuilder.set("CODE", insee + "000" + section + numero);
				featureBuilder.set("COM_ABS", "000");
				featureBuilder.set("FEUILLE", iniParcel.getAttribute("FEUILLE"));
				featureBuilder.set("NOM_COM", iniParcel.getAttribute("NOM_COM"));
				featureBuilder.set("CODE_ARR", iniParcel.getAttribute("CODE_ARR"));
				parcelFinal.add(featureBuilder.buildFeature(Attribute.makeUniqueId()));
			}
		} catch (Exception problem) {
			problem.printStackTrace();
		}
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
		return "Parcel in " + parcel.getAttribute("CODE_DEP") + parcel.getAttribute("CODE_COM") + ". Section "
				+ (parcel.getAttribute("SECTION")) + " and number " + parcel.getAttribute("NUMERO");
	}
	
	/**
	 * Construct a french parcel code for a {@link SimpleFeature}.
	 * 
	 * @param parcel
	 *            French parcel feature
	 * @return the string code
	 */
	public static String makeFrenchParcelCode(SimpleFeature parcel) {
		return parcel.getAttribute("CODE_DEP") + ((String) parcel.getAttribute("CODE_COM")) + parcel.getAttribute("COM_ABS")
				+ parcel.getAttribute("SECTION") + parcel.getAttribute("NUMERO");
	}
	
	/**
	 * Add a "CODE" field for every french parcel like of a {@link SimpleFeatureCollection}.
	 * 
	 * @param parcels
	 *            {@link SimpleFeatureCollection} of French parcels.
	 * @return The collection with the "CODE" field added.
	 */
	public static SimpleFeatureCollection addFrenchParcelCode(SimpleFeatureCollection parcels) {
		DefaultFeatureCollection result = new DefaultFeatureCollection();
		SimpleFeatureTypeBuilder sfTypeBuilder = new SimpleFeatureTypeBuilder();
		SimpleFeatureType schema = parcels.getSchema();
		for (AttributeDescriptor attr : schema.getAttributeDescriptors())
			sfTypeBuilder.add(attr);
		sfTypeBuilder.add("CODE", String.class);
		sfTypeBuilder.setName(schema.getName());
		sfTypeBuilder.setCRS(schema.getCoordinateReferenceSystem());
		sfTypeBuilder.setDefaultGeometry(schema.getGeometryDescriptor().getLocalName());
		SimpleFeatureBuilder builder = new SimpleFeatureBuilder(sfTypeBuilder.buildFeatureType());
		try (SimpleFeatureIterator parcelIt = parcels.features()) {
			while (parcelIt.hasNext()) {
				SimpleFeature feat = parcelIt.next();
				for (AttributeDescriptor attr : feat.getFeatureType().getAttributeDescriptors())
					builder.set(attr.getName(), feat.getAttribute(attr.getName()));
				builder.set("CODE", makeFrenchParcelCode(feat));
				result.add(builder.buildFeature(Attribute.makeUniqueId()));
			}
		} catch (Exception problem) {
			problem.printStackTrace();
		}
		return result;
	}
	
	
	/**
	 * Add a "CODE" field for every french parcel like of a {@link SimpleFeatureCollection}.
	 * 
	 * @param parcels
	 *            {@link SimpleFeatureCollection} of French parcels.
	 * @return The collection with the "CODE" field added.
	 */
	public static SimpleFeatureCollection addCommunityCode(SimpleFeatureCollection parcels) {
		DefaultFeatureCollection result = new DefaultFeatureCollection();
		SimpleFeatureTypeBuilder sfTypeBuilder = new SimpleFeatureTypeBuilder();
		SimpleFeatureType schema = parcels.getSchema();
		for (AttributeDescriptor attr : schema.getAttributeDescriptors())
			sfTypeBuilder.add(attr);
		sfTypeBuilder.add(GeneralFields.getZoneCommunityCode(), String.class);
		sfTypeBuilder.setName(schema.getName());
		sfTypeBuilder.setCRS(schema.getCoordinateReferenceSystem());
		sfTypeBuilder.setDefaultGeometry(schema.getGeometryDescriptor().getLocalName());
		SimpleFeatureBuilder builder = new SimpleFeatureBuilder(sfTypeBuilder.buildFeatureType());
		try (SimpleFeatureIterator parcelIt = parcels.features()) {
			while (parcelIt.hasNext()) {
				SimpleFeature feat = parcelIt.next();
				for (AttributeDescriptor attr : feat.getFeatureType().getAttributeDescriptors())
					builder.set(attr.getName(), feat.getAttribute(attr.getName()));
				builder.set(GeneralFields.getZoneCommunityCode(), makeDEPCOMCode(feat));
				result.add(builder.buildFeature(Attribute.makeUniqueId()));
			}
		} catch (Exception problem) {
			problem.printStackTrace();
		}
		return result;
	}
	

	/**
	 * Get the parcel codes (Attribute CODE of the given SimpleFeatureCollection)
	 * 
	 * @param parcels
	 *            {@link SimpleFeatureCollection} of parcels
	 * @return A list with all unique parcel codes
	 */
	public static List<String> getFrenchCodeParcels(SimpleFeatureCollection parcels) {
		List<String> result = new ArrayList<>();
		try (SimpleFeatureIterator parcelIt = parcels.features()) {
			while (parcelIt.hasNext()) {
				SimpleFeature feat = parcelIt.next();
				String code = ((String) feat.getAttribute("CODE"));
				if (code != null && !code.isEmpty()) {
					result.add(code);
				} else {
					try {
						result.add(makeFrenchParcelCode(feat));
					} catch (Exception e) {
						e.printStackTrace();
						System.err.println("Are you sure to use French Parcels?");
					}
				}
			}
		} catch (Exception problem) {
			problem.printStackTrace();
		}
		return result;
	}
	
	/**
	 * Construct the French community code number (INSEE) from a French parcel.
	 * @param parcel
	 * @return the INSEE number
	 */
	public static String makeDEPCOMCode(SimpleFeature parcel) {
		if (Collec.isSimpleFeatureContainsAttribute(parcel, "CODE_DEP") && Collec.isSimpleFeatureContainsAttribute(parcel, "CODE_COM")) {
			return parcel.getAttribute("CODE_DEP") + ((String) parcel.getAttribute("CODE_COM"));
		} else {
			return null;
		}
	}
}
