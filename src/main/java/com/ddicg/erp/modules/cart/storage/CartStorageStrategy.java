package com.ddicg.erp.modules.cart.storage;

import com.ddicg.erp.modules.cart.model.CartContext;

import java.util.Collection;
import java.util.Map;

public interface CartStorageStrategy {

    Map<String, Integer> getCartEntries(CartContext context);

    int getTotalItemsCount(CartContext context);

    void addOrIncrementItem(CartContext context, String sku, int quantity);

    void setItemQuantity(CartContext context, String sku, int quantity);

    void removeItem(CartContext context, String sku);

    void removeItems(CartContext context, Collection<String> skus);

    void clearCart(CartContext context);

    void refreshExpiry(CartContext context);
}
