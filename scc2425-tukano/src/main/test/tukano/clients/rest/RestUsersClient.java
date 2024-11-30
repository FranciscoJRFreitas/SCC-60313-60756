package tukano.clients.rest;

import java.util.List;

import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.GenericType;
import jakarta.ws.rs.core.MediaType;
import tukano.api.Result;
import tukano.api.UserDB;
import tukano.api.Users;
import tukano.api.rest.RestUsers;


public class RestUsersClient extends tukano.clients.rest.RestClient implements Users {

	public RestUsersClient( String serverURI ) {
		super( serverURI, RestUsers.PATH );
	}
		
	private Result<String> _createUser(UserDB user) {
		return super.toJavaResult( 
			target.request()
			.accept(MediaType.APPLICATION_JSON)
			.post(Entity.entity(user, MediaType.APPLICATION_JSON)), String.class );
	}

	private Result<UserDB> _getUser(String userId, String pwd) {
		return super.toJavaResult(
				target.path( userId )
				.queryParam(RestUsers.PWD, pwd).request()
				.accept(MediaType.APPLICATION_JSON)
				.get(), UserDB.class);
	}
	
	public Result<UserDB> _updateUser(String userId, String password, UserDB user) {
		return super.toJavaResult(
				target
				.path( userId )
				.queryParam(RestUsers.PWD, password)
				.request()
				.accept(MediaType.APPLICATION_JSON)
				.put(Entity.entity(user, MediaType.APPLICATION_JSON)), UserDB.class);
	}

	public Result<UserDB> _deleteUser(String userId, String password) {
		return super.toJavaResult(
				target
				.path( userId )
				.queryParam(RestUsers.PWD, password)
				.request()
				.accept(MediaType.APPLICATION_JSON)
				.delete(), UserDB.class);
	}

	public Result<List<UserDB>> _searchUsers(String pattern) {
		return super.toJavaResult(
				target
				.queryParam(RestUsers.QUERY, pattern)
				.request()
				.accept(MediaType.APPLICATION_JSON)
				.get(), new GenericType<List<UserDB>>() {});
	}

	@Override
	public Result<String> createUser(UserDB user) {
		return super.reTry( () -> _createUser(user));
	}

	@Override
	public Result<UserDB> getUser(String userId, String pwd) {
		return super.reTry( () -> _getUser(userId, pwd));
	}

	@Override
	public Result<UserDB> updateUser(String userId, String pwd, UserDB user) {
		return super.reTry( () -> _updateUser(userId, pwd, user));
	}

	@Override
	public Result<UserDB> deleteUser(String userId, String pwd) {
		return super.reTry( () -> _deleteUser(userId, pwd));
	}

	@Override
	public Result<List<UserDB>> searchUsers(String pattern) {
		return super.reTry( () -> _searchUsers(pattern));
	}
}
