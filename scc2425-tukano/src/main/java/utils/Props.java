package utils;

import java.io.InputStream;
import java.util.Arrays;
import java.util.Properties;

public class Props {

	public static void load(String[] keyValuePairs) {
		System.out.println(Arrays.asList( keyValuePairs));
		for( var pair: keyValuePairs ) {
			var parts = pair.split("=");
			if( parts.length == 2) 
				System.setProperty(parts[0], parts[1]);
		}
	}

	public static void load(String fileName) {
		try (InputStream input = Props.class.getClassLoader().getResourceAsStream(fileName)) {
			if (input == null) {
				throw new IllegalArgumentException("Properties file not found: " + fileName);
			}
			Properties props = new Properties();
			props.load(input);

			for (String key : props.stringPropertyNames()) {
				System.setProperty(key, props.getProperty(key));
			}
		} catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException("Failed to load properties file: " + fileName, e);
		}
	}
}
