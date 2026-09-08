package com.ddicg.erp.core.config.cache;

import org.springframework.data.redis.core.RedisTemplate;

@FunctionalInterface
public interface RedisTemplateProvider {
    RedisTemplate<String, Object> getTemplate(int dbIndex);
}
