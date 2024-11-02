package tukano.impl;

import static java.lang.String.format;
import static tukano.api.Result.error;
import static tukano.api.Result.errorOrResult;
import static tukano.api.Result.errorOrValue;
import static tukano.api.Result.errorOrVoid;
import static tukano.api.Result.ok;
import static tukano.api.Result.ErrorCode.FORBIDDEN;

import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;
import redis.clients.jedis.*;

import tukano.api.Blobs;
import tukano.api.Result;
import tukano.api.Short;
import tukano.api.Shorts;
import tukano.api.User;
import tukano.impl.data.Following;
import tukano.impl.data.FollowingData;
import tukano.impl.data.Likes;
import tukano.impl.data.LikesData;
import tukano.impl.rest.TukanoRestServer;
import utils.JSON;
import utils.db.CosmosDBLayer;
import utils.db.DBLayer;
import utils.db.PostgreDBLayer;
import utils.db.RedisCache;

public class JavaShorts implements Shorts {

	private static Logger Log = Logger.getLogger(JavaShorts.class.getName());
	private static final String SHORT_CACHE_PREFIX = "shorts:";
	private static final String NUM_USERS_COUNTER = "NumUsers";
	private static final String SHORTS_CONTAINER = "shorts";

	private static final String FOLLOWS_CONTAINER = "follows";
	private static final String LIKES_CONTAINER = "likes";
	private static Shorts instance;

	private static final DBLayer dbLayer;
	private static final boolean useCache;

	static {
		String dbType = "POSTGRE"; // "COSMOS" vs "POSTGRE" (could be env variable)
		if ("POSTGRE".equalsIgnoreCase(dbType)) {
			dbLayer = PostgreDBLayer.getInstance();
		} else {
			dbLayer = CosmosDBLayer.getInstance();
		}
		useCache = false;
	}
	
	synchronized public static Shorts getInstance() {
		if( instance == null )
			instance = new JavaShorts();
		return instance;
	}
	
	private JavaShorts() {}

	@Override
	public Result<Short> createShort(String userId, String password) {
		Log.info(() -> format("createShort : userId = %s, pwd = %s\n", userId, password));

		return errorOrResult(okUser(userId, password), user -> {
			var shortId = format("%s+%s", userId, UUID.randomUUID());
			var blobUrl = format("%s/%s/%s", TukanoRestServer.serverURI, Blobs.NAME, shortId);
			var shrt = new Short(shortId, userId, blobUrl);
			shrt.setId(shrt.getShortId());
			Result<Short> res = errorOrValue(dbLayer.insertOne(shrt, SHORTS_CONTAINER), s -> s.copyWithLikes_And_Token(0));

			if (useCache) {
				// Cache short in Redis
				try (Jedis jedis = RedisCache.getCachePool().getResource()) {
					var key = SHORT_CACHE_PREFIX + shortId;
					var value = JSON.encode(shrt);
					jedis.set(key, value);
					jedis.expire(key, 259200); // 3-day expiration
				}
			}
			return res;
		});
	}

	//TODO FALTA FAZER O GET!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
	/* @Override
	public Result<Short> getShort(String shortId) {
		Log.info(() -> format("getShort : shortId = %s\n", shortId));

		if( shortId == null )
			return error(BAD_REQUEST);

		var query = format("SELECT count(*) FROM Likes l WHERE l.shortId = '%s'", shortId);
		var likes = DB.sql(query, Long.class);
		return errorOrValue( getOne(shortId, Short.class), shrt -> shrt.copyWithLikes_And_Token( likes.get(0)));
	}*/
	@Override
	public Result<Short> getShort(String shortId) {
		if (shortId == null)
			return error(Result.ErrorCode.BAD_REQUEST);

		if (useCache) {
			// Retrieve from Redis if available
			try (Jedis jedis = RedisCache.getCachePool().getResource()) {
				var key = SHORT_CACHE_PREFIX + shortId;
				String cachedValue = jedis.get(key);
				if (cachedValue != null) {
					Short cachedShort = JSON.decode(cachedValue, Short.class);
					return ok(cachedShort);
				}
			}
		}

		// Fallback to DB retrieval if not cached
		return errorOrValue(dbLayer.getOne(shortId, Short.class, SHORTS_CONTAINER), shrt -> {
			if (useCache) {
				try (Jedis jedis = RedisCache.getCachePool().getResource()) {
					var key = SHORT_CACHE_PREFIX + shortId;
					jedis.set(key, JSON.encode(shrt));
					jedis.expire(key, 259200); // Cache for 3 days
				}
			}
			return shrt;
		});
	}

	@Override
	public Result<Void> deleteShort(String shortId, String password) {
		Log.info(() -> format("deleteShort : shortId = %s, pwd = %s\n", shortId, password));

		return errorOrResult(getShort(shortId), shrt -> {
			return errorOrVoid(okUser(shrt.getOwnerId(), password), user -> {

				// Delete blob from JavaBlobs
				//JavaBlobs.getInstance().delete(shrt.getBlobUrl(), Token.get());

				// Delete from Redis cache if enabled
				if (useCache) {
					try (Jedis jedis = RedisCache.getCachePool().getResource()) {
						var key = SHORT_CACHE_PREFIX + shortId;
						jedis.del(key); // Delete from Redis
					}
				}
				// Delete from DB
				return dbLayer.deleteOne(shrt, SHORTS_CONTAINER);
			});
        });
	}

	//TODO |||||||||||||||||||||||||||||||||||||||||||||||||| Fica a faltar blobs e implementar e testar este métodos abaixo |||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||
	//TODO REVER E TESTAR GETSHORTS
	@Override
	public Result<List<String>> getShorts(String userId) {
		Log.info(() -> format("getShorts : userId = %s\n", userId));

		String query = format("SELECT * FROM shorts s WHERE s.ownerId = '%s'", userId);

		Result<List<Short>> result = dbLayer.query(Short.class, query, SHORTS_CONTAINER);

		if (result.isOK()) {
			List<String> idShorts = result.value()
					.stream()
					.map(Short::getShortId)
					.toList();

			if(idShorts.isEmpty())
				return error(Result.ErrorCode.NOT_FOUND);

			return Result.ok(idShorts);
		} else {
			return Result.error(result.error());
		}
	}

	@Override
	public Result<Void> follow(String userId1, String userId2, FollowingData isFollowing, String password) {
		Log.info(() -> format("follow : userId1 = %s, userId2 = %s, isFollowing = @%s, pwd = %s\n", userId1, userId2, isFollowing.getIsFollowing(), password));

		return errorOrResult( okUser(userId1, password), user -> {
			var f = new Following(userId1, userId2);
			f.setId();
			return errorOrVoid( okUser( userId2), !isFollowing.getIsFollowing() ? dbLayer.insertOne( f, FOLLOWS_CONTAINER ) : dbLayer.deleteOne( f, FOLLOWS_CONTAINER ));
		});
	}

	@Override
	public Result<List<String>> followers(String userId, String password) {
		Log.info(() -> format("followers : userId = %s, pwd = %s\n", userId, password));

		String query = format("SELECT * FROM follows f WHERE f.followee = '%s'", userId);
		Result<List<Following>> result = dbLayer.query(Following.class, query, FOLLOWS_CONTAINER);

		if (result.isOK()) {
			List<String> followers = result.value()
					.stream()
					.map(Following::getFollower)
					.toList();

			return Result.ok(followers);
		} else {
			return Result.error(result.error());
		}

		//return errorOrValue( okUser(userId, password), result.value());
	}

	@Override
	public Result<Void> like(String shortId, String userId, LikesData isLiked, String password) {
		Log.info(() -> format("like : shortId = %s, userId = %s, isLiked = %s, pwd = %s\n", shortId, userId, isLiked.getIsLiked(), password));

		
		return errorOrResult( getShort(shortId), shrt -> {
			var l = new Likes(userId, shortId, shrt.getOwnerId());
			return errorOrVoid( okUser( userId, password), !isLiked.getIsLiked() ? dbLayer.insertOne( l, LIKES_CONTAINER) : dbLayer.deleteOne( l, LIKES_CONTAINER ));
		});
	}

	@Override
	public Result<List<String>> likes(String shortId, String password) {
		Log.info(() -> format("likes : shortId = %s, pwd = %s\n", shortId, password));

		return errorOrResult( getShort(shortId), shrt -> {
			
			var query = format("SELECT l.userId FROM likes l WHERE l.shortId = '%s'", shortId);
			
			return errorOrValue( okUser( shrt.getOwnerId(), password ), dbLayer.query(String.class, query, LIKES_CONTAINER));
		});
	}


	@Override
	public Result<List<String>> getFeed(String userId, String password) {
		Log.info(() -> format("getFeed : userId = %s, pwd = %s\n", userId, password));

		final var QUERY_FMT = """
				SELECT shortId
				FROM (
				    SELECT s.shortId, s.timestamp
				    FROM shorts s
				    WHERE s.ownerId = '%s'
				    UNION
				    SELECT s.shortId, s.timestamp
				    FROM shorts s
				    JOIN follows f ON f.followee = s.ownerId
				    WHERE f.follower = '%s'
				) AS combined_shorts
				ORDER BY timestamp DESC;
				""";

		return errorOrValue( okUser( userId, password), dbLayer.query(String.class, format(QUERY_FMT, userId, userId), SHORTS_CONTAINER));
	}
		
	//TODO REVER E TESTAR DELETE ALL
	@Override
	public Result<Void> deleteAllShorts(String userId, String password, String token) {
//		Log.info(() -> format("deleteAllShorts : userId = %s, password = %s, token = %s\n", userId, password, token));
//
//		if( ! Token.isValid( token, userId ) )
//			return error(FORBIDDEN);
//
//		return DB.transaction( (hibernate) -> {
//
//			//delete shorts
//			var query1 = format("DELETE Short s WHERE s.ownerId = '%s'", userId);
//			hibernate.createQuery(query1, Short.class).executeUpdate();
//
//			//delete follows
//			var query2 = format("DELETE Following f WHERE f.follower = '%s' OR f.followee = '%s'", userId, userId);
//			hibernate.createQuery(query2, Following.class).executeUpdate();
//
//			//delete likes
//			var query3 = format("DELETE Likes l WHERE l.ownerId = '%s' OR l.userId = '%s'", userId, userId);
//			hibernate.createQuery(query3, Likes.class).executeUpdate();
//
//		});
		return error(Result.ErrorCode.NOT_IMPLEMENTED);
	}

	protected Result<User> okUser( String userId, String pwd) {
		return JavaUsers.getInstance().getUser(userId, pwd);
	}

	private Result<Void> okUser(String userId) {
		var res = okUser(userId, "");
		if (res.error() == Result.ErrorCode.NOT_FOUND) return error(Result.ErrorCode.NOT_FOUND);
		return res.error() == FORBIDDEN ? ok() : error(res.error());
	}
	
}