package tukano.impl;

import static java.lang.String.format;
import static tukano.api.Result.error;
import static tukano.api.Result.errorOrResult;
import static tukano.api.Result.errorOrValue;
import static tukano.api.Result.errorOrVoid;
import static tukano.api.Result.ok;
import static tukano.api.Result.ErrorCode.FORBIDDEN;

import java.util.*;
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
import utils.JSON;
import utils.db.CosmosDBLayer;
import utils.db.DBLayer;
import utils.db.PostgreDBLayer;
import utils.db.RedisCache;

public class JavaShorts implements Shorts {

	private static Logger Log = Logger.getLogger(JavaShorts.class.getName());
	private static final String REST_BACKEND_URL = System.getenv("REST_BACKEND_URL");
	private static final String SHORT_CACHE_PREFIX = "shorts:";
	private static final String FEED_CACHE_PREFIX = "feeds:";
	private static final String LIKE_CACHE_SUFIX = ":likes";
	private static final String TOTAL_LIKE_CACHE_SUFIX =":totalLikes";
	private static final String SHORTS_CONTAINER = "shorts";
	private static final String FOLLOWS_CONTAINER = "follows";
	private static final String LIKES_CONTAINER = "likes";
	private static Shorts instance;
	private static final DBLayer dbLayer;
	private static final boolean useCache;

	static {
		String dbType = System.getenv("DB_TYPE"); // "NOSQL" vs "POSTGRESQL" (backend env variable)
		if ("POSTGRESQL".equalsIgnoreCase(dbType)) {
			dbLayer = PostgreDBLayer.getInstance();
		} else {
			dbLayer = CosmosDBLayer.getInstance();
		}
		useCache = System.getenv("USE_CACHE").equalsIgnoreCase("true"); // "true" to use cache : "false" if not (backend env variable)
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
			var blobUrl = format("%s/%s/%s", REST_BACKEND_URL, Blobs.NAME, shortId);
			var shrt = new Short(shortId, userId, blobUrl);
			shrt.setId(shrt.getShortId());
			Result<Short> res = errorOrValue(dbLayer.insertOne(shrt, SHORTS_CONTAINER), s -> s.copyWithLikes_And_Token(0));

			if(!res.isOK()){
				return Result.error(res.error());
			}

			JavaBlobs.getInstance().upload(shortId, new byte[0], Token.get(shortId));

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

	@Override
	public Result<Short> getShort(String shortId) {
		if (shortId == null)
			return error(Result.ErrorCode.BAD_REQUEST);

		if (useCache) {
			try (Jedis jedis = RedisCache.getCachePool().getResource()) {
				var key = SHORT_CACHE_PREFIX + shortId;
				String cachedValue = jedis.get(key);
				if (cachedValue != null) {
					Short cachedShort = JSON.decode(cachedValue, Short.class);

					String totalLikesKey = SHORT_CACHE_PREFIX + shortId + ":totalLikes";
					String cachedLikesCount = jedis.get(totalLikesKey);
					if (cachedLikesCount != null) {
						cachedShort.setTotalLikes(Integer.parseInt(cachedLikesCount));
					}

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

					String totalLikesKey = SHORT_CACHE_PREFIX + shortId + ":totalLikes";
					jedis.set(totalLikesKey, String.valueOf(shrt.getTotalLikes()));
					jedis.expire(totalLikesKey, 259200); // Cache for 3 days
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
				JavaBlobs.getInstance().delete(shortId, Token.get(shortId));

				if (useCache) {
					try (Jedis jedis = RedisCache.getCachePool().getResource()) {
						var key = SHORT_CACHE_PREFIX + shortId;
						jedis.del(key); // Delete from Redis
					}
				}
				return dbLayer.deleteOne(shrt, SHORTS_CONTAINER);
			});
        });
	}

	@Override
	public Result<List<String>> getShorts(String userId) {
		Log.info(() -> format("getShorts : userId = %s\n", userId));

		if (useCache) {
			try (Jedis jedis = RedisCache.getCachePool().getResource()) {
				var key = SHORT_CACHE_PREFIX + userId;
				List<String> cachedShortIds = jedis.lrange(key, 0, -1);
				if (!cachedShortIds.isEmpty()) {
					return Result.ok(cachedShortIds);
				}
			}
		}

		String cosmosQuery = format("SELECT * FROM %s s WHERE s.ownerId = '%s'", SHORTS_CONTAINER, userId);
		String postgreQuery = format("SELECT s.shortId FROM %s s WHERE s.ownerId = '%s'", SHORTS_CONTAINER, userId);

		Result<List<String>> result = dbLayer instanceof CosmosDBLayer
				? ((CosmosDBLayer) dbLayer).queryAndMapResults(Short.class, cosmosQuery, SHORTS_CONTAINER, Short::getShortId)
				: dbLayer.query(String.class, postgreQuery, SHORTS_CONTAINER);

		if (useCache && result.isOK() && !result.value().isEmpty()) {
			try (Jedis jedis = RedisCache.getCachePool().getResource()) {
				var key = SHORT_CACHE_PREFIX + userId;
				jedis.rpush(key, result.value().toArray(new String[0]));
				jedis.expire(key, 259200); // Cache for 3 days
			}
		}

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

		return errorOrResult(getShort(shortId), shrt -> {
			var l = new Likes(userId, shortId, shrt.getOwnerId());
			l.setId();
			Result<Void> result = errorOrVoid(okUser(userId, password),
					!isLiked.getIsLiked() ? dbLayer.insertOne(l, LIKES_CONTAINER) : dbLayer.deleteOne(l, LIKES_CONTAINER));

			if (result.isOK()) {
				if (useCache) {
					String totalLikesKey = SHORT_CACHE_PREFIX + shortId + TOTAL_LIKE_CACHE_SUFIX;
					String likesListKey = SHORT_CACHE_PREFIX + shortId + LIKE_CACHE_SUFIX;

					try (Jedis jedis = RedisCache.getCachePool().getResource()) {
						if (isLiked.getIsLiked()) {
							jedis.decr(totalLikesKey);
							jedis.lrem(likesListKey, 1, userId);
						} else {
							jedis.incr(totalLikesKey);
							jedis.rpush(likesListKey, userId);
						}

						shrt.setTotalLikes((int) Long.parseLong(jedis.get(totalLikesKey)));
						dbLayer.updateOne(shrt, SHORTS_CONTAINER);
					}
				} else {
					// not using cache
					int newTotalLikes = isLiked.getIsLiked() ? shrt.getTotalLikes() - 1 : shrt.getTotalLikes() + 1;
					shrt.setTotalLikes(newTotalLikes);
					dbLayer.updateOne(shrt, SHORTS_CONTAINER);
				}
			}

			return result;
		});
	}


	@Override
	public Result<List<String>> likes(String shortId, String password) {
		Log.info(() -> format("likes : shortId = %s, pwd = %s\n", shortId, password));

		return errorOrResult(getShort(shortId), shrt -> {
			List<String> userLikes;

			if (useCache) {
				try (Jedis jedis = RedisCache.getCachePool().getResource()) {
					String likesKey = SHORT_CACHE_PREFIX + shortId + LIKE_CACHE_SUFIX;
					userLikes = jedis.lrange(likesKey, 0, -1);

					if (!userLikes.isEmpty() || jedis.exists(likesKey)) {
						return Result.ok(userLikes); // cache contains no likes
					}
				}
			}

			String cosmosQuery = format("SELECT %s.userId FROM %s WHERE %s.shortId = '%s'", LIKES_CONTAINER, LIKES_CONTAINER, LIKES_CONTAINER, shortId);
			String postgreQuery = format("SELECT userId FROM %s WHERE shortId = '%s'", LIKES_CONTAINER, shortId);

			Result<List<String>> result = errorOrValue(okUser(shrt.getOwnerId(), password),
					dbLayer instanceof CosmosDBLayer ?
							((CosmosDBLayer) dbLayer).queryAndMapResults(Likes.class, cosmosQuery, LIKES_CONTAINER, Likes::getUserId) :
							dbLayer.query(String.class, postgreQuery, LIKES_CONTAINER));

			if (result.isOK()) {
				userLikes = result.value();

				if (useCache) {
					try (Jedis jedis = RedisCache.getCachePool().getResource()) {
						String likesKey = SHORT_CACHE_PREFIX + shortId + LIKE_CACHE_SUFIX;

						if (!userLikes.isEmpty()) {
							jedis.rpush(likesKey, userLikes.toArray(new String[0]));
						} else {
							jedis.rpush(likesKey, "");
						}
						jedis.expire(likesKey, 259200); // Cache for 3 days
					}
				}

				return Result.ok(userLikes);
			} else {
				return result;
			}
		});
	}


	@Override
	public Result<List<String>> getFeed(String userId, String password) {
		Log.info(() -> format("getFeed : userId = %s, pwd = %s\n", userId, password));

		if (useCache) {
			try (Jedis jedis = RedisCache.getCachePool().getResource()) {
				var key = FEED_CACHE_PREFIX + userId;
				List<String> cachedFeed = jedis.lrange(key, 0, -1);
				if (!cachedFeed.isEmpty()) {
					return Result.ok(cachedFeed);
				}
			}
		}

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

				if (useCache) {
					try (Jedis jedis = RedisCache.getCachePool().getResource()) {
						var key = FEED_CACHE_PREFIX + userId;
						jedis.rpush(key, combinedResults.toArray(new String[0]));
						jedis.expire(key, 259200); // Cache for 3 days
					}
				}

				return Result.ok(combinedResults);
			} else {
				return Result.error(Result.ErrorCode.INTERNAL_ERROR);
			}
		} else {
			Result<List<String>> result = errorOrValue(
					okUser(userId, password),
					dbLayer.query(String.class, format(postgreQuery, userId, userId), SHORTS_CONTAINER)
			);

			if (useCache && result.isOK()) {
				try (Jedis jedis = RedisCache.getCachePool().getResource()) {
					var key = FEED_CACHE_PREFIX + userId;
					jedis.rpush(key, result.value().toArray(new String[0]));
					jedis.expire(key, 259200); // Cache for 3 days
				}
			}

			return result;
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
				String followerQuery = format("SELECT * FROM %s f WHERE f.follower = '%s'", FOLLOWS_CONTAINER, userId);
				String followeeQuery = format("SELECT * FROM %s f WHERE f.followee = '%s'", FOLLOWS_CONTAINER, userId);
				Result<List<Following>> followsToDeleteFollower = dbLayer.query(Following.class, followerQuery, FOLLOWS_CONTAINER);
				Result<List<Following>> followsToDeleteFollowee = dbLayer.query(Following.class, followeeQuery, FOLLOWS_CONTAINER);

				if (followsToDeleteFollower.isOK())
					followsToDeleteFollower.value().forEach(followItem -> dbLayer.deleteOne(followItem, FOLLOWS_CONTAINER));

				if (followsToDeleteFollowee.isOK())
					followsToDeleteFollowee.value().forEach(followItem -> dbLayer.deleteOne(followItem, FOLLOWS_CONTAINER));

				// Delete from likes where ownerId = userId or userId = userId
				String likesOwnerQuery = format("SELECT * FROM %s l WHERE l.ownerId = '%s' ", LIKES_CONTAINER, userId);
				String likesUserQuery = format("SELECT * FROM %s l WHERE l.userId = '%s'", LIKES_CONTAINER, userId);
				Result<List<Likes>> likesToDeleteOwner = dbLayer.query(Likes.class, likesOwnerQuery, LIKES_CONTAINER);
				Result<List<Likes>> likesToDeleteUser = dbLayer.query(Likes.class, likesUserQuery, LIKES_CONTAINER);
				if (likesToDeleteOwner.isOK()) {
					likesToDeleteOwner.value().forEach(likeItem -> dbLayer.deleteOne(likeItem, LIKES_CONTAINER));
				}
				if (likesToDeleteUser.isOK()) {
					likesToDeleteUser.value().forEach(likeItem -> dbLayer.deleteOne(likeItem, LIKES_CONTAINER));
				}

				if (useCache) {
					try (Jedis jedis = RedisCache.getCachePool().getResource()) {
						Set<String> keys = jedis.keys(SHORT_CACHE_PREFIX + userId + "+");
						for (String key : keys) {
							jedis.del(key);
						}
						jedis.del(FEED_CACHE_PREFIX + userId); // Delete feed
					}
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
					if (useCache) {
						try (Jedis jedis = RedisCache.getCachePool().getResource()) {
							Set<String> keys = jedis.keys(SHORT_CACHE_PREFIX + userId);
							for (String key : keys) {
								jedis.del(key);
							}
							jedis.del(FEED_CACHE_PREFIX + userId); // Delete feed
						}
					}
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