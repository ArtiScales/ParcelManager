package scenario;

import java.io.File;
import java.io.IOException;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

public class JSONtoScenario {
	public static void main(String[] args) throws JsonParseException, IOException {
		// Read JSON file and convert to java object
		JsonFactory factory = new JsonFactory();
		JsonParser parser = factory.createParser(
				new File("/home/thema/Documents/MC/workspace/ParcelManager/src/main/resources/testData/jsonEx.json"));
		JsonToken token = parser.nextToken();

		while (!parser.isClosed()) {
			token = parser.nextToken();
//			if (token == JsonToken.FIELD_NAME && "rootfile".equals(parser.getCurrentName())) {
//				System.out.println(parser.getCurrentName());
//				token = parser.nextToken();
//				if (token == JsonToken.VALUE_STRING) {
//					System.out.println("ID : " + parser.getText());
//				}
//			}

			if (token == JsonToken.FIELD_NAME && "steps".equals(parser.getCurrentName())) {

				System.out.println("Steps:");
//				token = parser.nextToken(); // // Read left bracket i.e. [
				// Loop to print array elements until right bracket i.e ]
				while (token != JsonToken.END_ARRAY) {
					token = parser.nextToken();
					System.out.println("cn " + parser.getCurrentName());
					if (token == JsonToken.VALUE_STRING) {
						System.out.println("lala " + parser.getText());
					}
				}
			}

		}
		parser.close();

		// token = parser.nextToken();
//     	 
//    	System.out.println(token.asString());

//    	while (token != JsonToken.END_ARRAY) {
//        token = parser.nextToken();
//}
	}
}
