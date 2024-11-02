package utils.db;


import redis.clients.jedis.*;

public class RedisCache {

        //private static final String RedisHostname = "scc2425cache6031360756.redis.cache.windows.net";
        private static final String RedisHostname = "scc2024cache60313.redis.cache.windows.net";
        //private static final String RedisKey = "LblCrwoy8clpo6Be4rGdIGtVobgYIWRrBAzCaHN2w8I=";
        private static final String RedisKey = "xsUMtkxzn6nyqvZaPYJ9S0A8NM82KTntaAzCaLf6fE8=";
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


