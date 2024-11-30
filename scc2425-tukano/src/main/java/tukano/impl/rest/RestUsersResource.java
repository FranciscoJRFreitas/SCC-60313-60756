package tukano.impl.rest;

import java.util.List;

import jakarta.inject.Singleton;
import tukano.api.UserDB;
import tukano.api.Users;
import tukano.api.rest.RestUsers;
import tukano.impl.JavaUsers;

@Singleton
public class RestUsersResource extends RestResource implements RestUsers {

	final Users impl;
	public RestUsersResource() {
		this.impl = JavaUsers.getInstance();
	}
	
	@Override
	public String createUser(UserDB user) {
		return super.resultOrThrow( impl.createUser( user));
	}

	@Override
	public UserDB getUser(String name, String pwd) {
		return super.resultOrThrow( impl.getUser(name, pwd));
	}
	
	@Override
	public UserDB updateUser(String name, String pwd, UserDB user) {
		return super.resultOrThrow( impl.updateUser(name, pwd, user));
	}

	@Override
	public UserDB deleteUser(String name, String pwd) {
		return super.resultOrThrow( impl.deleteUser(name, pwd));
	}

	@Override
	public List<UserDB> searchUsers(String pattern) {
		return super.resultOrThrow( impl.searchUsers( pattern));
	}
}
