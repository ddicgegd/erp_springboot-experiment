package com.ddicg.erp.modules.cart.storage;

import com.ddicg.erp.modules.cart.model.CartContext;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CartStorageFactory {

    RedisCartStorage redisCartStorage;
    PersistentDbCartStorage persistentDbCartStorage;

    public CartStorageStrategy getStrategy(CartContext context) {
        if (context.isVip()) {
            return persistentDbCartStorage;
        }
        return redisCartStorage;
    }
}
