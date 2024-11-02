package tukano.impl;

import static java.lang.String.format;
import static tukano.api.Result.error;
import static tukano.api.Result.errorOrResult;
import static tukano.api.Result.errorOrValue;
import static tukano.api.Result.errorOrVoid;
import static tukano.api.Result.ok;
import static tukano.api.Result.ErrorCode.FORBIDDEN;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;
import java.util.stream.Stream;

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
		String dbType = "COSMOS"; // "COSMOS" vs "POSTGRE" (could be env variable)
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

	@Override
	public Result<List<String>> getShorts(String userId) {
		Log.info(() -> format("getShorts : userId = %s\n", userId));

		String cosmosQuery = format("SELECT * FROM %s s WHERE s.ownerId = '%s'", SHORTS_CONTAINER, userId);
		String postgreQuery = format("SELECT s.shortId FROM %s s WHERE s.ownerId = '%s'", SHORTS_CONTAINER, userId);

		Result<List<String>> result = dbLayer instanceof CosmosDBLayer
				? ((CosmosDBLayer) dbLayer).queryAndMapResults(Short.class, cosmosQuery, SHORTS_CONTAINER, Short::getShortId)
				: dbLayer.query(String.class, postgreQuery, SHORTS_CONTAINER);

		return result.isOK() && result.value().isEmpty()
				? error(Result.ErrorCode.NOT_FOUND)
				: result;
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

		String cosmosQuery = format("SELECT * FROM %s f WHERE f.followee = '%s'", FOLLOWS_CONTAINER, userId);
		String postgreQuery = format("SELECT f.follower FROM %s f WHERE f.followee = '%s'", FOLLOWS_CONTAINER, userId);

		return errorOrValue(okUser(userId, password),
				dbLayer instanceof CosmosDBLayer
						? ((CosmosDBLayer) dbLayer).queryAndMapResults(Following.class, cosmosQuery, FOLLOWS_CONTAINER, Following::getFollower)
						: dbLayer.query(String.class, postgreQuery, FOLLOWS_CONTAINER)
		);
	}


	@Override
	public Result<Void> like(String shortId, String userId, LikesData isLiked, String password) {
		Log.info(() -> format("like : shortId = %s, userId = %s, isLiked = %s, pwd = %s\n", shortId, userId, isLiked.getIsLiked(), password));

		return errorOrResult( getShort(shortId), shrt -> {
			var l = new Likes(userId, shortId, shrt.getOwnerId());
			l.setId();
			return errorOrVoid( okUser( userId, password), !isLiked.getIsLiked() ? dbLayer.insertOne( l, LIKES_CONTAINER) : dbLayer.deleteOne( l, LIKES_CONTAINER ));
		});
	}

	@Override
	public Result<List<String>> likes(String shortId, String password) {
		Log.info(() -> format("likes : shortId = %s, pwd = %s\n", shortId, password));

		return errorOrResult(getShort(shortId), shrt -> {

			String cosmosQuery = format("SELECT %s.userId FROM %s WHERE %s.shortId = '%s'",LIKES_CONTAINER, LIKES_CONTAINER, LIKES_CONTAINER, shortId);
			String postgreQuery = format("SELECT userId FROM %s WHERE shortId = '%s'", LIKES_CONTAINER, shortId);

			return errorOrValue(okUser(shrt.getOwnerId(), password), dbLayer instanceof CosmosDBLayer ?
					((CosmosDBLayer) dbLayer).queryAndMapResults(Likes.class, cosmosQuery, LIKES_CONTAINER, Likes::getUserId)
					: dbLayer.query(String.class, postgreQuery, LIKES_CONTAINER));
		});
	}


	@Override
	public Result<List<String>> getFeed(String userId, String password) {
		Log.info(() -> format("getFeed : userId = %s, pwd = %s\n", userId, password));

		String postgreQuery = """
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

		if (dbLayer instanceof CosmosDBLayer) {
			final var USER_OWN_SHORTS_QUERY = format("SELECT * FROM %s s WHERE s.ownerId = '%s'", SHORTS_CONTAINER, userId);
			Result<List<Short>> resOwnShorts = dbLayer.query(Short.class, USER_OWN_SHORTS_QUERY, SHORTS_CONTAINER);

			final var FOLLOWING_USERS_QUERY = format("SELECT * FROM %s f WHERE f.follower = '%s'", FOLLOWS_CONTAINER, userId);
			Result<List<Following>> resFollowees = dbLayer.query(Following.class, FOLLOWING_USERS_QUERY, FOLLOWS_CONTAINER);

			if (resFollowees.isOK()) {
				List<String> followeeIds = resFollowees.value().stream().map(Following::getFollowee).toList();

				List<Short> allFolloweeShorts = new ArrayList<>();
				for (String followeeId : followeeIds) {
					String FOLLOWEE_SHORTS_QUERY = format("SELECT * FROM %s s WHERE s.ownerId = '%s'", SHORTS_CONTAINER, followeeId);
					Result<List<Short>> resFolloweeShorts = dbLayer.query(Short.class, FOLLOWEE_SHORTS_QUERY, SHORTS_CONTAINER);
					if (resFolloweeShorts.isOK()) {
						allFolloweeShorts.addAll(resFolloweeShorts.value());
					}
				}

				List<String> combinedResults = Stream.concat(
								resOwnShorts.value().stream(),
								allFolloweeShorts.stream()
						)
						.sorted(Comparator.comparing(Short::getTimestamp).reversed())
						.map(Short::getShortId)
						.toList();

				return Result.ok(combinedResults);
			} else {
				return Result.error(Result.ErrorCode.INTERNAL_ERROR);
			}
		}

		else {
			return errorOrValue(
					okUser(userId, password),
					dbLayer.query(String.class, format(postgreQuery, userId, userId), SHORTS_CONTAINER)
			);
		}
	}


	@Override
	public Result<Void> deleteAllShorts(String userId, String password, String token) {
		Log.info(() -> format("deleteAllShorts : userId = %s, password = %s, token = %s\n", userId, password, token));

		if (!Token.isValid(token, userId)) {
			return error(FORBIDDEN);
		}

		return errorOrVoid(okUser(userId, password), user -> {
			if (dbLayer instanceof CosmosDBLayer) {

				// Delete from shorts where ownerId = userId
				String shortsQuery = format("SELECT * FROM %s s WHERE s.ownerId = '%s'", SHORTS_CONTAINER, userId);
				Result<List<Short>> shortsToDelete = dbLayer.query(Short.class, shortsQuery, SHORTS_CONTAINER);
				if (shortsToDelete.isOK()) {
					shortsToDelete.value().forEach(shortItem -> dbLayer.deleteOne(shortItem, SHORTS_CONTAINER));
				}

				// Delete from follows where follower = userId or followee = userId
				String followsQuery = format("SELECT * FROM %s f WHERE f.follower = '%s' OR f.followee = '%s'", FOLLOWS_CONTAINER, userId, userId);
				Result<List<Following>> followsToDelete = dbLayer.query(Following.class, followsQuery, FOLLOWS_CONTAINER);
				if (followsToDelete.isOK()) {
					followsToDelete.value().forEach(followItem -> dbLayer.deleteOne(followItem, FOLLOWS_CONTAINER));
				}

				// Delete from likes where ownerId = userId or userId = userId
				String likesQuery = format("SELECT * FROM %s l WHERE l.ownerId = '%s' OR l.userId = '%s'", LIKES_CONTAINER, userId, userId);
				Result<List<Likes>> likesToDelete = dbLayer.query(Likes.class, likesQuery, LIKES_CONTAINER);
				if (likesToDelete.isOK()) {
					likesToDelete.value().forEach(likeItem -> dbLayer.deleteOne(likeItem, LIKES_CONTAINER));
				}

				return Result.ok();
			} else {
				String deleteShortsQuery = format("DELETE FROM %s WHERE ownerId = '%s'", SHORTS_CONTAINER, userId);
				Result<Void> deleteShortsResult = dbLayer.executeUpdate(deleteShortsQuery, SHORTS_CONTAINER);

				String deleteFollowsQuery = format("DELETE FROM %s WHERE follower = '%s' OR followee = '%s'", FOLLOWS_CONTAINER, userId, userId);
				Result<Void> deleteFollowsResult = dbLayer.executeUpdate(deleteFollowsQuery, FOLLOWS_CONTAINER);

				String deleteLikesQuery = format("DELETE FROM %s WHERE ownerId = '%s' OR userId = '%s'", LIKES_CONTAINER, userId, userId);
				Result<Void> deleteLikesResult = dbLayer.executeUpdate(deleteLikesQuery, LIKES_CONTAINER);

				if (deleteShortsResult.isOK() && deleteFollowsResult.isOK() && deleteLikesResult.isOK()) {
					return Result.ok();
				} else {
					return error(Result.ErrorCode.INTERNAL_ERROR);
				}
			}
		});
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