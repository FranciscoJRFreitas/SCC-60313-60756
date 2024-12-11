package tukano.impl.rest;

import jakarta.inject.Singleton;
import jakarta.ws.rs.core.Cookie;
import tukano.api.Blobs;
import tukano.api.rest.RestBlobs;
import tukano.impl.JavaBlobs;
import tukano.impl.auth.Authentication;

@Singleton
public class RestBlobsResource extends RestResource implements RestBlobs {

	final Blobs impl;
	
	public RestBlobsResource() {
		this.impl = JavaBlobs.getInstance();
	}
	
	@Override
	public void upload(Cookie cookie, String blobId, byte[] bytes, String token) {
		String userId = blobId.split("\\+")[0];
		Authentication.validateSession( cookie, userId );
		super.resultOrThrow( impl.upload(blobId, bytes, token));
	}

	@Override
	public byte[] download(Cookie cookie, String blobId, String token) {
		String userId = blobId.split("\\+")[0];
		Authentication.validateSession( cookie, userId );
		return super.resultOrThrow( impl.download( blobId, token ));
	}

	@Override
	public void delete(Cookie cookie, String blobId, String token) {
		String userId = blobId.split("\\+")[0];
		Authentication.validateSession( cookie, userId );
		super.resultOrThrow( impl.delete( blobId, token ));
	}
	
	@Override
	public void deleteAllBlobs(Cookie cookie, String userId, String password) {
		Authentication.validateSession( cookie, userId );
		super.resultOrThrow( impl.deleteAllBlobs( userId, password ));
	}
}
