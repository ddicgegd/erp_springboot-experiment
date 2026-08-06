package com.ddicg.erp.core.common.service;

import com.ddicg.erp.core.config.cache.iRedis;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class RedisService implements iRedis {

    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    public boolean hasKey(String key) {
        return redisTemplate.hasKey(key);
    }

    @Override
    public void delete(String... keys) {
        for (String key : keys) redisTemplate.delete(key);
    }

    @Override
    public void getExpire(String key, TimeUnit timeUnit) {
        redisTemplate.getExpire(key, timeUnit);
    }

    @Override
    public void expire(String key, long timeout, TimeUnit timeUnit) {
        redisTemplate.expire(key, timeout, timeUnit);
    }

    @Override
    public void setValue(String key, Object value) {
        redisTemplate.opsForValue().set(key, value);
    }

    @Override
    public void setValueWithExpiry(String key, Object value, long time, TimeUnit timeUnit) {
        redisTemplate.opsForValue().set(key, value, time, timeUnit);
    }

    @Override
    public Object getValue(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    @Override
    public void hSet(String key, String field, Object value) {
        redisTemplate.opsForHash().put(key, field, value);
    }

    @Override
    public Object hGet(String key, String field) {
        return redisTemplate.opsForHash().get(key, field);
    }

    @Override
    public Map<Object, Object> hGetAll(String key) {
        return redisTemplate.opsForHash().entries(key);
    }

    @Override
    public void hDelete(String key, Object... fields) {
        redisTemplate.opsForHash().delete(key, fields);
    }

    @Override
    public void lPush(String key, Object value) {
        redisTemplate.opsForList().rightPush(key, value);
    }

    @Override
    public Object lPop(String key) {
        return redisTemplate.opsForList().rightPop(key);
    }

    @Override
    public List<Object> lRange(String key, long start, long end) {
        return redisTemplate.opsForList().range(key, start, end);
    }

    @Override
    public void sAdd(String key, Object... values) {
        redisTemplate.opsForSet().add(key, values);
    }

    @Override
    public Set<Object> sMembers(String key) {
        return redisTemplate.opsForSet().members(key);
    }

    @Override
    public void sRemove(String key, Object... values) {
        redisTemplate.opsForSet().remove(key, values);
    }
}
