package com.ddicg.erp.core.common.service;

import com.ddicg.erp.core.config.RedisConfiguration.RedisTable;
import com.ddicg.erp.core.config.cache.RedisTemplateProvider;
import com.ddicg.erp.core.config.cache.iRedis;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@Service
public class RedisService implements iRedis {

    public static final String NULL_SENTINEL = "__NULL__";

    private final RedisTemplate<String, Object> defaultTemplate;
    private final RedisTemplateProvider templateProvider;

    public RedisService(RedisTemplate<String, Object> redisTemplate) {
        this(redisTemplate, db -> redisTemplate);
    }

    @Autowired
    public RedisService(RedisTemplate<String, Object> redisTemplate, @Autowired(required = false) RedisTemplateProvider templateProvider) {
        this.defaultTemplate = redisTemplate;
        this.templateProvider = templateProvider != null ? templateProvider : (db -> redisTemplate);
    }

    public RedisTemplate<String, Object> getTemplateForDb(int dbIndex) {
        if (templateProvider != null) {
            return templateProvider.getTemplate(dbIndex);
        }
        return defaultTemplate;
    }

    private RedisTemplate<String, Object> getTemplate(String key) {
        if (key != null && templateProvider != null) {
            Optional<RedisTable> tableOpt = RedisTable.fromKey(key);
            if (tableOpt.isPresent()) {
                return templateProvider.getTemplate(tableOpt.get().getDatabase());
            }
        }
        return defaultTemplate;
    }

    private RedisTemplate<String, Object> getTemplate(RedisTable table) {
        if (table != null && templateProvider != null) {
            return templateProvider.getTemplate(table.getDatabase());
        }
        return defaultTemplate;
    }

    @Override
    public boolean hasKey(String key) {
        return Boolean.TRUE.equals(getTemplate(key).hasKey(key));
    }

    @Override
    public void delete(String... keys) {
        if (keys != null && keys.length > 0) {
            for (String key : keys) {
                getTemplate(key).delete(key);
            }
        }
    }

    @Override
    public void unlink(String... keys) {
        if (keys != null && keys.length > 0) {
            for (String key : keys) {
                getTemplate(key).unlink(key);
            }
        }
    }

    @Override
    public void getExpire(String key, TimeUnit timeUnit) {
        getTemplate(key).getExpire(key, timeUnit);
    }

    @Override
    public Long getExpiry(String key, TimeUnit timeUnit) {
        return getTemplate(key).getExpire(key, timeUnit);
    }

    public Long getExpireSeconds(String key) {
        return getTemplate(key).getExpire(key, TimeUnit.SECONDS);
    }

    @Override
    public void expire(String key, long timeout, TimeUnit timeUnit) {
        getTemplate(key).expire(key, timeout, timeUnit);
    }

    @Override
    public void setValue(String key, Object value) {
        getTemplate(key).opsForValue().set(key, value);
    }

    @Override
    public void setValueWithExpiry(String key, Object value, long time, TimeUnit timeUnit) {
        getTemplate(key).opsForValue().set(key, value, time, timeUnit);
    }

    @Override
    public void setValueWithJitter(String key, Object value, long baseTtl, long maxJitterSeconds, TimeUnit timeUnit) {
        long jitter = maxJitterSeconds > 0 ? ThreadLocalRandom.current().nextLong(1, maxJitterSeconds + 1) : 0;
        long totalSeconds = timeUnit.toSeconds(baseTtl) + jitter;
        getTemplate(key).opsForValue().set(key, value, Math.max(1, totalSeconds), TimeUnit.SECONDS);
    }

    @Override
    public void setNullSentinel(String key, long ttlSeconds) {
        getTemplate(key).opsForValue().set(key, NULL_SENTINEL, Math.max(1, ttlSeconds), TimeUnit.SECONDS);
    }

    @Override
    public boolean isNullSentinel(String key) {
        Object val = getTemplate(key).opsForValue().get(key);
        return NULL_SENTINEL.equals(val);
    }

    @Override
    public Object getValue(String key) {
        return getTemplate(key).opsForValue().get(key);
    }

    @Override
    public void hSet(String key, String field, Object value) {
        getTemplate(key).opsForHash().put(key, field, value);
    }

    @Override
    public Object hGet(String key, String field) {
        return getTemplate(key).opsForHash().get(key, field);
    }

    @Override
    public Map<Object, Object> hGetAll(String key) {
        return getTemplate(key).opsForHash().entries(key);
    }

    @Override
    public List<Object> hValues(String key) {
        return getTemplate(key).opsForHash().values(key);
    }

    @Override
    public Long hIncrBy(String key, String field, long delta) {
        return getTemplate(key).opsForHash().increment(key, field, delta);
    }

    @Override
    public void hDelete(String key, Object... fields) {
        if (fields != null && fields.length > 0) {
            getTemplate(key).opsForHash().delete(key, fields);
        }
    }

    @Override
    public void lPush(String key, Object value) {
        getTemplate(key).opsForList().rightPush(key, value);
    }

    @Override
    public Object lPop(String key) {
        return getTemplate(key).opsForList().rightPop(key);
    }

    @Override
    public List<Object> lRange(String key, long start, long end) {
        return getTemplate(key).opsForList().range(key, start, end);
    }

    @Override
    public void sAdd(String key, Object... values) {
        if (values != null && values.length > 0) {
            getTemplate(key).opsForSet().add(key, values);
        }
    }

    @Override
    public Set<Object> sMembers(String key) {
        return getTemplate(key).opsForSet().members(key);
    }

    @Override
    public void sRemove(String key, Object... values) {
        if (values != null && values.length > 0) {
            getTemplate(key).opsForSet().remove(key, values);
        }
    }

    @Override
    public void zAdd(String key, Object member, double score) {
        getTemplate(key).opsForZSet().add(key, member, score);
    }

    @Override
    public Double zScore(String key, Object member) {
        return getTemplate(key).opsForZSet().score(key, member);
    }

    @Override
    public Long zRevRank(String key, Object member) {
        return getTemplate(key).opsForZSet().reverseRank(key, member);
    }

    @Override
    public Set<Object> zRevRange(String key, long start, long stop) {
        return getTemplate(key).opsForZSet().reverseRange(key, start, stop);
    }

    @Override
    public Long zRemRangeByScore(String key, double min, double max) {
        return getTemplate(key).opsForZSet().removeRangeByScore(key, min, max);
    }

    @Override
    public Long zCard(String key) {
        return getTemplate(key).opsForZSet().zCard(key);
    }

    @Override
    public boolean hasKey(RedisTable table, Object id) {
        return hasKey(table.key(id));
    }

    @Override
    public void delete(RedisTable table, Object id) {
        delete(table.key(id));
    }

    @Override
    public void unlink(RedisTable table, Object id) {
        unlink(table.key(id));
    }

    @Override
    public void expire(RedisTable table, Object id, long timeout, TimeUnit timeUnit) {
        expire(table.key(id), timeout, timeUnit);
    }

    @Override
    public void setValue(RedisTable table, Object id, Object value) {
        setValue(table.key(id), value);
    }

    @Override
    public void setValueWithExpiry(RedisTable table, Object id, Object value, long time, TimeUnit timeUnit) {
        setValueWithExpiry(table.key(id), value, time, timeUnit);
    }

    @Override
    public void setValueWithJitter(RedisTable table, Object id, Object value, long baseTtl, long maxJitterSeconds, TimeUnit timeUnit) {
        setValueWithJitter(table.key(id), value, baseTtl, maxJitterSeconds, timeUnit);
    }

    @Override
    public void setNullSentinel(RedisTable table, Object id, long ttlSeconds) {
        setNullSentinel(table.key(id), ttlSeconds);
    }

    @Override
    public boolean isNullSentinel(RedisTable table, Object id) {
        return isNullSentinel(table.key(id));
    }

    @Override
    public Object getValue(RedisTable table, Object id) {
        return getValue(table.key(id));
    }

    @Override
    public void hSet(RedisTable table, Object id, String field, Object value) {
        hSet(table.key(id), field, value);
    }

    @Override
    public Object hGet(RedisTable table, Object id, String field) {
        return hGet(table.key(id), field);
    }

    @Override
    public Map<Object, Object> hGetAll(RedisTable table, Object id) {
        return hGetAll(table.key(id));
    }

    @Override
    public List<Object> hValues(RedisTable table, Object id) {
        return hValues(table.key(id));
    }

    @Override
    public Long hIncrBy(RedisTable table, Object id, String field, long delta) {
        return hIncrBy(table.key(id), field, delta);
    }

    @Override
    public void hDelete(RedisTable table, Object id, Object... fields) {
        hDelete(table.key(id), fields);
    }
}
