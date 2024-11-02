package tukano.impl;

import static java.lang.String.format;
import static tukano.api.Result.error;
import static tukano.api.Result.errorOrResult;
import static tukano.api.Result.errorOrValue;
import static tukano.api.Result.ok;
import static tukano.api.Result.ErrorCode.BAD_REQUEST;
import static tukano.api.Result.ErrorCode.FORBIDDEN;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

import tukano.api.Result;
import tukano.api.User;
import tukano.api.Users;
import utils.Hash;
import utils.db.CosmosDBLayer;
import utils.db.DBLayer;
import utils.db.PostgreDBLayer;

public class JavaUsers implements Users {

	private static Logger Log = Logger.getLogger(JavaUsers.class.getName());
	private static final String USERS_CONTAINER = "users";
	private static Users instance;
	private static DBLayer dbLayer;

	synchronized public static Users getInstance() {
		if (instance == null)
			instance = new JavaUsers();
		return instance;
	}

	private JavaUsers() {
		String dbType = "POSTGRE";  // "COSMOS" vs "POSTGRE" (could be env variable)
		if ("POSTGRE".equalsIgnoreCase(dbType)) {
			dbLayer = PostgreDBLayer.getInstance();
		} else {
			dbLayer = CosmosDBLayer.getInstance();
		}
	}

	@Override
	public Result<String> createUser(User user) {
		Log.info(() -> format("createUser : %s\n", user));

		if (badUserInfo(user))
			return error(BAD_REQUEST);

		user.setId(user.getUserId());
		user.setPwd(Hash.sha256(user.getPwd()));

		return errorOrValue(dbLayer.insertOne(user, USERS_CONTAINER), user.getUserId());
	}

	@Override
	public Result<User> getUser(String userId, String pwd) {
		Log.info(() -> format("getUser : userId = %s, pwd = %s\n", userId, pwd));

		if (userId == null || pwd == null)
			return error(BAD_REQUEST);

		return validatedUserOrError(dbLayer.getOne(userId, User.class, USERS_CONTAINER), pwd);
	}

	@Override
	public Result<User> updateUser(String userId, String pwd, User other) {
		Log.info(() -> format("updateUser : userId = %s, pwd = %s, user: %s\n", userId, pwd, other));

		if (badUpdateUserInfo(userId, pwd, other))
			return error(BAD_REQUEST);

		other.setPwd(Hash.sha256(pwd));

		return errorOrResult(validatedUserOrError(dbLayer.getOne(userId, User.class, USERS_CONTAINER), pwd),
				user -> dbLayer.updateOne(user.updateFrom(other), USERS_CONTAINER));
	}

	@Override
	public Result<User> deleteUser(String userId, String pwd) {
		Log.info(() -> format("deleteUser : userId = %s, pwd = %s\n", userId, pwd));

		if (userId == null || pwd == null)
			return error(BAD_REQUEST);

		return errorOrResult(validatedUserOrError(dbLayer.getOne(userId, User.class, USERS_CONTAINER), pwd), user -> {

			// Delete user shorts and related info asynchronously in a separate thread
			Executors.defaultThreadFactory().newThread(() -> {
				JavaShorts.getInstance().deleteAllShorts(userId, pwd, Token.get(userId));
				JavaBlobs.getInstance().deleteAllBlobs(userId, Token.get(userId));
			}).start();

			return dbLayer.deleteOne(user, USERS_CONTAINER);
		});
	}

	@Override
	public Result<List<User>> searchUsers(String pattern) {
		Log.info(() -> format("searchUsers : pattern = %s\n", pattern));
		String query = format("SELECT * FROM %s u WHERE UPPER(u.userId) LIKE '%%%s%%'", USERS_CONTAINER, pattern.toUpperCase());
		Result<List<User>> result = dbLayer.query(User.class, query, USERS_CONTAINER);

		if (result.isOK()) {
			List<User> hits = result.value()
					.stream()
					.map(User::copyWithoutPassword)
					.toList();
			return Result.ok(hits);
		} else {
			return Result.error(result.error());
		}
	}

	private Result<User> validatedUserOrError(Result<User> res, String pwd) {
		if (res.isOK()) {
			return res.value().getPwd().equals(Hash.sha256(pwd)) ? res : error(FORBIDDEN);
		} else {
			return res;
		}
	}

	private boolean badUserInfo(User user) {
		return user.userId() == null
				|| user.pwd() == null
				|| user.displayName() == null
				|| user.email() == null;
	}

	private boolean badUpdateUserInfo(String userId, String pwd, User info) {
		return userId == null || pwd == null || (info.getUserId() != null && !userId.equals(info.getUserId()));
	}
}
