package tukano.api.rest;


import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Cookie;
import jakarta.ws.rs.core.MediaType;
import tukano.impl.auth.Authentication;

@Path(RestBlobs.PATH)
public interface RestBlobs {
	
	String PATH = "/blobs";
	String BLOB_ID = "blobId";
	String TOKEN = "token";
	String BLOBS = "blobs";
	String USER_ID = "userId";

 	@POST
 	@Path("/{" + BLOB_ID +"}")
 	@Consumes(MediaType.APPLICATION_OCTET_STREAM)
	void upload(@CookieParam(Authentication.COOKIE_KEY) Cookie cookie, @PathParam(BLOB_ID) String blobId, byte[] bytes, @QueryParam(TOKEN) String token);


 	@GET
 	@Path("/{" + BLOB_ID +"}") 	
 	@Produces(MediaType.APPLICATION_OCTET_STREAM)
 	byte[] download(@CookieParam(Authentication.COOKIE_KEY) Cookie cookie, @PathParam(BLOB_ID) String blobId, @QueryParam(TOKEN) String token);
 	
 	
	@DELETE
	@Path("/{" + BLOB_ID + "}")
	void delete(@CookieParam(Authentication.COOKIE_KEY) Cookie cookie, @PathParam(BLOB_ID) String blobId, @QueryParam(TOKEN) String token );

	@DELETE
	@Path("/{" + USER_ID + "}/" + BLOBS)
	void deleteAllBlobs(@CookieParam(Authentication.COOKIE_KEY) Cookie cookie, @PathParam(USER_ID) String userId, @QueryParam(TOKEN) String token );
}
