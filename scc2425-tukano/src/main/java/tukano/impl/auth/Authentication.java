package tukano.impl.auth;

import java.net.URI;
import java.util.UUID;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response.Status;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Cookie;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.NewCookie;
import jakarta.ws.rs.core.Response;
import tukano.impl.JavaUsers;
import tukano.impl.auth.requestCookies.RequestCookies;

@Path(Authentication.PATH)
public class Authentication {
	static final String PATH = "login";
	static final String USER = "username";
	static final String PWD = "password";
	public static final String COOKIE_KEY = "scc:session";
	private static final int MAX_COOKIE_AGE = 3600;

	@POST
	@Path("/")
	public Response login( @FormParam(USER) String user, @FormParam(PWD) String password ) {
		var res = JavaUsers.getInstance().getUser(user, password);
		if (res.isOK()) {
			String uid = UUID.randomUUID().toString();
			var cookie = new NewCookie.Builder(COOKIE_KEY)
					.value(uid).path("/")
					.comment("sessionid")
					.maxAge(MAX_COOKIE_AGE)
					.secure(false)
					.httpOnly(true)
					.build();
			
			FakeRedisLayer.getInstance().putSession( new Session( uid, user));	
			
            return Response.ok()
                    .cookie(cookie)
                    .build();
		} else
			throw new NotAuthorizedException("Incorrect login");
	}

	@GET
	@Produces(MediaType.TEXT_HTML)
	public Response loginPage() {
		try {
			var fileStream = getClass().getClassLoader().getResourceAsStream("login.html");
			if (fileStream == null) {
				throw new WebApplicationException("File not found", Status.NOT_FOUND);
			}
			var loginPageContent = new String(fileStream.readAllBytes());
			return Response.ok(loginPageContent, MediaType.TEXT_HTML).build();
		} catch (Exception e) {
			e.printStackTrace();
			throw new WebApplicationException("Internal Server Error", Status.INTERNAL_SERVER_ERROR);
		}
	}

	static public Session validateSession(String userId) throws NotAuthorizedException {
		var cookies = RequestCookies.get();
		return validateSession( cookies.get(COOKIE_KEY ), userId );
	}
	
	static public Session validateSession(Cookie cookie, String userId) throws NotAuthorizedException {

		if (cookie == null )
			throw new NotAuthorizedException("No session initialized");
		
		var session = FakeRedisLayer.getInstance().getSession( cookie.getValue());
		if( session == null )
			throw new NotAuthorizedException("No valid session initialized");
			
		if (session.user() == null || session.user().isEmpty())
			throw new NotAuthorizedException("No valid session initialized");
		
		if (!session.user().equals(userId))
			throw new NotAuthorizedException("Invalid user : " + session.user());
		
		return session;
	}
}
