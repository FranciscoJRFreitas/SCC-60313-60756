package utils.db;

import redis.clients.jedis.JedisPool;
import redis.clients.jedis.Jedis;

public class RedisCache {

        private static final String RedisHostname = "scc2425cache6031360756.redis.cache.windows.net";
        private static final String RedisKey = "rqF3jQkUsTBs4umOTMcEv1Kv3N4NsQhLjAzCaMcXNj0=";
        private static final int REDIS_PORT = 6380;
        private static final int REDIS_TIMEOUT = 1000;
        private static final boolean Redis_USE_TLS = true;

        private static JedisPool instance;

        public synchronized static JedisPool getCachePool() {
            if( instance != null)
                return instance;

            var poolConfig = new JedisPoolConfig();
            poolConfig.setMaxTotal(128);
            poolConfig.setMaxIdle(128);
            poolConfig.setMinIdle(16);
            poolConfig.setTestOnBorrow(true);
            poolConfig.setTestOnReturn(true);
            poolConfig.setTestWhileIdle(true);
            poolConfig.setNumTestsPerEvictionRun(3);
            poolConfig.setBlockWhenExhausted(true);
            instance = new JedisPool(poolConfig, RedisHostname, REDIS_PORT, REDIS_TIMEOUT, RedisKey, Redis_USE_TLS);
            return instance;
        }
}


