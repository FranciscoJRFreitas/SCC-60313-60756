package tukano.impl.rest;

import java.net.URI;
import java.util.logging.Logger;

import org.glassfish.jersey.jdkhttp.JdkHttpServerFactory;
import org.glassfish.jersey.server.ResourceConfig;

import tukano.impl.Token;
import utils.Args;
import utils.IP;
import utils.Props;


public class TukanoRestServer {
	final private static Logger Log = Logger.getLogger(TukanoRestServer.class.getName());

	static final String INETADDR_ANY = "0.0.0.0";
	static String SERVER_BASE_URI = "http://%s:%s/rest";

	public static final int PORT = 8080;

	public static String serverURI;

	public static String blobURL = "https://sccproj.blob.core.windows.net/shorts";
			
	static {
		System.setProperty("java.util.logging.SimpleFormatter.format", "%4$s: %5$s");
	}
	
	public TukanoRestServer() {
		serverURI = String.format(SERVER_BASE_URI, IP.hostname(), PORT);
	}


	protected void start() throws Exception {
	
		ResourceConfig config = new ResourceConfig();
		
		config.register(RestBlobsResource.class);
		config.register(RestUsersResource.class); 
		config.register(RestShortsResource.class);
		
		JdkHttpServerFactory.createHttpServer( URI.create(serverURI.replace(IP.hostname(), INETADDR_ANY)), config);
		
		Log.info(String.format("Tukano Server ready @ %s\n",  serverURI));
	}
	
	
	public static void main(String[] args) throws Exception {
		Args.use(args);
		
		Token.setSecret( Args.valueOf("-secret", "6031360756"));
		
		new TukanoRestServer().start();
	}
}
