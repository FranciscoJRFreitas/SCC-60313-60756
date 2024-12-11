package tukano.impl;

import static java.lang.String.format;
import static tukano.api.Result.error;
import static tukano.api.Result.errorOrResult;
import static tukano.api.Result.errorOrValue;
import static tukano.api.Result.ErrorCode.BAD_REQUEST;
import static tukano.api.Result.ErrorCode.FORBIDDEN;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

import redis.clients.jedis.*;

import tukano.api.Result;
import tukano.api.UserDB;
import tukano.api.Users;
import utils.Hash;
import utils.JSON;
import utils.db.CosmosDBLayer;
import utils.db.DBLayer;
import utils.db.PostgreDBLayer;
import utils.db.RedisCache;

public class JavaUsers implements Users {

	private static Logger Log = Logger.getLogger(JavaUsers.class.getName());
	private static final String USERS_CONTAINER = "users";
	private static final String USER_CACHE_PREFIX = "users:";
	private static Users instance;

	private static DBLayer dbLayer;
	private static final boolean useCache;

	static {
		dbLayer = PostgreDBLayer.getInstance();
		useCache = false;
	}

	synchronized public static Users getInstance() {
		if (instance == null)
			instance = new JavaUsers();
		return instance;
	}

	private JavaUsers() {}

	@Override
	public Result<String> createUser(UserDB user) {
		Log.info(() -> format("createUser : %s\n", user));

		if (badUserInfo(user))
			return error(BAD_REQUEST);

		user.setId(user.getUserId());
		user.setPwd(Hash.sha256(user.getPwd()));

		Result<String> result = errorOrValue(dbLayer.insertOne(user, USERS_CONTAINER), user.getUserId());

		if (result.isOK() && useCache) {
			try (Jedis jedis = RedisCache.getCachePool().getResource()) {
				var key = USER_CACHE_PREFIX + result.value();
				jedis.set(key, JSON.encode(user));
				jedis.expire(key, 259200); // Cache for 3 days
			}
		}

		return result;
	}

	@Override
	public Result<UserDB> getUser(String userId, String pwd) {
		Log.info(() -> format("getUser : userId = %s, pwd = %s\n", userId, pwd));

		if (userId == null || pwd == null)
			return error(BAD_REQUEST);

		if (useCache) {
			try (Jedis jedis = RedisCache.getCachePool().getResource()) {
				var key = USER_CACHE_PREFIX + userId;
				String cachedValue = jedis.get(key);
				if (cachedValue != null) {
					UserDB cachedUser = JSON.decode(cachedValue, UserDB.class);
                    return cachedUser.getPwd().equals(Hash.sha256(pwd)) ? Result.ok(cachedUser) : error(FORBIDDEN);
				}
			}
		}

		// Fallback to DB retrieval if not cached
		Result<UserDB> result = validatedUserOrError(dbLayer.getOne(userId, UserDB.class, USERS_CONTAINER), pwd);
		if (useCache && result.isOK()) {
			try (Jedis jedis = RedisCache.getCachePool().getResource()) {
				var key = USER_CACHE_PREFIX + userId;
				jedis.set(key, JSON.encode(result.value()));
				jedis.expire(key, 259200); // Cache for 3 days
			}
		}

		return result;
	}

	@Override
	public Result<UserDB> updateUser(String userId, String pwd, UserDB other) {
		Log.info(() -> format("updateUser : userId = %s, pwd = %s, user: %s\n", userId, pwd, other));

		if (badUpdateUserInfo(userId, pwd, other))
			return error(BAD_REQUEST);

		other.setPwd(Hash.sha256(pwd));

		return errorOrResult(validatedUserOrError(dbLayer.getOne(userId, UserDB.class, USERS_CONTAINER), pwd),
				user -> {
					Result<UserDB> updateResult = dbLayer.updateOne(user.updateFrom(other), USERS_CONTAINER);
					if (useCache && updateResult.isOK()) {
						try (Jedis jedis = RedisCache.getCachePool().getResource()) {
							var key = USER_CACHE_PREFIX + userId;
							jedis.set(key, JSON.encode(updateResult.value()));
							jedis.expire(key, 259200); // Cache for 3 days
						}
					}
					return updateResult;
				});
	}

	@Override
	public Result<UserDB> deleteUser(String userId, String pwd) {
		Log.info(() -> format("deleteUser : userId = %s, pwd = %s\n", userId, pwd));

		if (userId == null || pwd == null)
			return error(BAD_REQUEST);

		return errorOrResult(validatedUserOrError(dbLayer.getOne(userId, UserDB.class, USERS_CONTAINER), pwd), user -> {

			// Delete user shorts and related info asynchronously in a separate thread
			Executors.defaultThreadFactory().newThread(() -> {
				JavaShorts.getInstance().deleteAllShorts(userId, pwd, Token.get(userId));
				JavaBlobs.getInstance().deleteAllBlobs(userId, Token.get(userId));
			}).start();

			if (useCache) {
				try (Jedis jedis = RedisCache.getCachePool().getResource()) {
					var key = USER_CACHE_PREFIX + userId;
					jedis.del(key);
				}
			}

			return dbLayer.deleteOne(user, USERS_CONTAINER);
		});
	}

	@Override
	public Result<List<UserDB>> searchUsers(String pattern) {
		Log.info(() -> format("searchUsers : pattern = %s\n", pattern));
		String query = format("SELECT * FROM %s u WHERE UPPER(u.userId) LIKE '%%%s%%'", USERS_CONTAINER, pattern.toUpperCase());
		Result<List<UserDB>> result = dbLayer.query(UserDB.class, query, USERS_CONTAINER);

		if (result.isOK()) {
			List<UserDB> hits = result.value()
					.stream()
					.map(UserDB::copyWithoutPassword)
					.toList();
			return Result.ok(hits);
		} else {
			return Result.error(result.error());
		}
	}

	private Result<UserDB> validatedUserOrError(Result<UserDB> res, String pwd) {
		if (res.isOK()) {
			return res.value().getPwd().equals(Hash.sha256(pwd)) ? res : error(FORBIDDEN);
		} else {
			return res;
		}
	}

	private boolean badUserInfo(UserDB user) {
		return user.userId() == null
				|| user.pwd() == null
				|| user.displayName() == null
				|| user.email() == null;
	}

	private boolean badUpdateUserInfo(String userId, String pwd, UserDB info) {
		return userId == null || pwd == null || (info.getUserId() != null && !userId.equals(info.getUserId()));
	}
}