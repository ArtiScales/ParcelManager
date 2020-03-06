package fields;

import java.io.IOException;
import java.util.Arrays;

import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.feature.DefaultFeatureCollection;
import org.opengis.feature.simple.SimpleFeature;

public class GeneralFileds {

	/**
	 * that method allows to determine if a parcel has been simulated and the filed values for section is upper than 2
	 * 
	 * @param sfc
	 *            : parcel collection to sort
	 * @return the parcel collection with only the simulated parcels
	 * @throws IOException
	 */
	public static SimpleFeatureCollection getParcelWithSimulatedFileds(SimpleFeatureCollection sfc) throws IOException {
		DefaultFeatureCollection result = new DefaultFeatureCollection();
		Arrays.stream(sfc.toArray(new SimpleFeature[0])).forEach(parcel -> {
			if (((String) parcel.getAttribute("SECTION")) != null && ((String) parcel.getAttribute("SECTION")).length() > 3) {
				result.add(parcel);
			}
		});
		return result.collection();
	}

}
