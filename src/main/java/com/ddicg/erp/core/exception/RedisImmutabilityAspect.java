package com.ddicg.erp.core.exception;

import com.ddicg.erp.core.config.RedisConfiguration.RedisTable;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

@Aspect
@Component
@RequiredArgsConstructor
public class RedisImmutabilityAspect {

    private final RedisTemplate<String, Object> redisTemplate;

    @Pointcut("execution(* com.ddicg.erp.core.common.service.RedisService.setValue*(..)) || " +
              "execution(* com.ddicg.erp.core.common.service.RedisService.hSet*(..)) || " +
              "execution(* com.ddicg.erp.core.common.service.RedisService.hIncrBy*(..))")
    public void redisWriteOperations() {}

    @Before("redisWriteOperations()")
    public void validateWritePermission(JoinPoint joinPoint) {
        Object[] args = joinPoint.getArgs();
        if (args == null || args.length == 0) {
            return;
        }

        Object firstArg = args[0];

        if (firstArg instanceof RedisTable table) {
            if (table.isImmutable()) {
                Object id = args.length > 1 ? args[1] : null;
                checkAndEnforceImmutability(table, table.key(id));
            }
            return;
        }

        if (firstArg instanceof String key) {
            RedisTable.fromKey(key).ifPresent(table -> {
                if (table.isImmutable()) {
                    checkAndEnforceImmutability(table, key);
                }
            });
        }
    }

    private void checkAndEnforceImmutability(RedisTable table, String fullKey) {
        if (Boolean.TRUE.equals(redisTemplate.hasKey(fullKey))) {
            throw new BusinessException(
                ErrorCode.FORBIDDEN,
                "Bản ghi trên bảng [" + table.name() + "] là bất biến (Immutable), không được phép sửa đổi!"
            );
        }
    }
}
