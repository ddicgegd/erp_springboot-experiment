package com.ddicg.erp.core.config.cache;

import com.ddicg.erp.core.config.RedisConfiguration.RedisTable;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public interface iRedis {
    boolean hasKey(String key);
    void delete(String... keys);
    void unlink(String... keys);
    void getExpire(String key, TimeUnit timeUnit);
    Long getExpiry(String key, TimeUnit timeUnit);
    void expire(String key, long timeout, TimeUnit timeUnit);
    void setValue(String key, Object value);
    void setValueWithExpiry(String key, Object value, long time, TimeUnit timeUnit);
    void setValueWithJitter(String key, Object value, long baseTtl, long maxJitterSeconds, TimeUnit timeUnit);
    void setNullSentinel(String key, long ttlSeconds);
    boolean isNullSentinel(String key);
    Object getValue(String key);

    void hSet(String key, String field, Object value);
    Object hGet(String key, String field);
    Map<Object, Object> hGetAll(String key);
    List<Object> hValues(String key);
    Long hIncrBy(String key, String field, long delta);
    void hDelete(String key, Object... fields);

    void lPush(String key, Object value);
    Object lPop(String key);
    List<Object> lRange(String key, long start, long end);

    void sAdd(String key, Object... values);
    Set<Object> sMembers(String key);
    void sRemove(String key, Object... values);

    void zAdd(String key, Object member, double score);
    Double zScore(String key, Object member);
    Long zRevRank(String key, Object member);
    Set<Object> zRevRange(String key, long start, long stop);
    Long zRemRangeByScore(String key, double min, double max);
    Long zCard(String key);

    boolean hasKey(RedisTable table, Object id);
    void delete(RedisTable table, Object id);
    void unlink(RedisTable table, Object id);
    void expire(RedisTable table, Object id, long timeout, TimeUnit timeUnit);
    void setValue(RedisTable table, Object id, Object value);
    void setValueWithExpiry(RedisTable table, Object id, Object value, long time, TimeUnit timeUnit);
    void setValueWithJitter(RedisTable table, Object id, Object value, long baseTtl, long maxJitterSeconds, TimeUnit timeUnit);
    void setNullSentinel(RedisTable table, Object id, long ttlSeconds);
    boolean isNullSentinel(RedisTable table, Object id);
    Object getValue(RedisTable table, Object id);

    void hSet(RedisTable table, Object id, String field, Object value);
    Object hGet(RedisTable table, Object id, String field);
    Map<Object, Object> hGetAll(RedisTable table, Object id);
    List<Object> hValues(RedisTable table, Object id);
    Long hIncrBy(RedisTable table, Object id, String field, long delta);
    void hDelete(RedisTable table, Object id, Object... fields);
}
