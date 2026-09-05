package com.ddicg.erp.modules.cart.storage;

import com.ddicg.erp.core.common.service.RedisService;
import com.ddicg.erp.modules.cart.model.CartContext;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component("redisCartStorage")
@Slf4j
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class RedisCartStorage implements CartStorageStrategy {

    RedisService redisService;

    private static int parseQuantity(Object value) {
        if (value == null) {
            return 0;
        }
        try {
            return Integer.parseInt(value.toString().trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    @Override
    public Map<String, Integer> getCartEntries(CartContext context) {
        Map<Object, Object> raw = redisService.hGetAll(context.getKey());
        if (raw == null || raw.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, Integer> result = new HashMap<>();
        for (Map.Entry<Object, Object> entry : raw.entrySet()) {
            String sku = entry.getKey().toString();
            int qty = parseQuantity(entry.getValue());
            if (qty > 0) {
                result.put(sku, qty);
            }
        }
        return result;
    }

    @Override
    public int getTotalItemsCount(CartContext context) {
        List<Object> quantities = redisService.hValues(context.getKey());
        if (quantities == null || quantities.isEmpty()) {
            return 0;
        }
        return quantities.stream()
                .mapToInt(RedisCartStorage::parseQuantity)
                .sum();
    }

    @Override
    public void addOrIncrementItem(CartContext context, String sku, int quantity) {
        redisService.hIncrBy(context.getKey(), sku, quantity);
        refreshExpiry(context);
    }

    @Override
    public void setItemQuantity(CartContext context, String sku, int quantity) {
        if (quantity <= 0) {
            redisService.hDelete(context.getKey(), sku);
        } else {
            redisService.hSet(context.getKey(), sku, String.valueOf(quantity));
        }
        refreshExpiry(context);
    }

    @Override
    public void removeItem(CartContext context, String sku) {
        redisService.hDelete(context.getKey(), sku);
        refreshExpiry(context);
    }

    @Override
    public void removeItems(CartContext context, Collection<String> skus) {
        if (skus != null && !skus.isEmpty()) {
            redisService.hDelete(context.getKey(), skus.toArray(new Object[0]));
            refreshExpiry(context);
        }
    }

    @Override
    public void clearCart(CartContext context) {
        redisService.unlink(context.getKey());
    }

    @Override
    public void refreshExpiry(CartContext context) {
        if (context.getTtlDays() > 0) {
            redisService.expire(context.getKey(), context.getTtlDays(), TimeUnit.DAYS);
        }
    }
}
