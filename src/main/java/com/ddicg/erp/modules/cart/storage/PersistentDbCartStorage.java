package com.ddicg.erp.modules.cart.storage;

import com.ddicg.erp.core.common.service.RedisService;
import com.ddicg.erp.modules.cart.model.Cart;
import com.ddicg.erp.modules.cart.model.CartContext;
import com.ddicg.erp.modules.cart.model.CartItem;
import com.ddicg.erp.modules.cart.repository.CartItemRepository;
import com.ddicg.erp.modules.cart.repository.CartRepository;
import com.ddicg.erp.modules.iam.model.User;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.TimeUnit;

@Component("persistentDbCartStorage")
@Slf4j
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PersistentDbCartStorage implements CartStorageStrategy {

    CartRepository cartRepository;
    CartItemRepository cartItemRepository;
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

    private Cart getOrCreateCart(User user) {
        return cartRepository.findWithItemsByUserId(user.getId())
                .orElseGet(() -> {
                    Cart newCart = Cart.builder()
                            .user(user)
                            .items(new ArrayList<>())
                            .build();
                    return cartRepository.save(newCart);
                });
    }

    @Override
    @Transactional
    public Map<String, Integer> getCartEntries(CartContext context) {
        // 1. Kiểm tra Redis Cache (L1 in-memory)
        Map<Object, Object> cached = redisService.hGetAll(context.getKey());
        if (cached != null && !cached.isEmpty()) {
            Map<String, Integer> result = new HashMap<>();
            for (Map.Entry<Object, Object> entry : cached.entrySet()) {
                String sku = entry.getKey().toString();
                int qty = parseQuantity(entry.getValue());
                if (qty > 0) {
                    result.put(sku, qty);
                }
            }
            return result;
        }

        // 2. Cache miss -> Nạp từ Oracle DB (L2 persistent)
        User user = context.getUser();
        if (user == null || user.getId() == null) {
            return Collections.emptyMap();
        }

        Optional<Cart> cartOpt = cartRepository.findWithItemsByUserId(user.getId());
        if (cartOpt.isEmpty() || cartOpt.get().getItems().isEmpty()) {
            return Collections.emptyMap();
        }

        Cart cart = cartOpt.get();
        Map<String, Integer> result = new HashMap<>();
        for (CartItem item : cart.getItems()) {
            if (item.getQuantity() != null && item.getQuantity() > 0) {
                result.put(item.getSku(), item.getQuantity());
                redisService.hSet(context.getKey(), item.getSku(), String.valueOf(item.getQuantity()));
            }
        }
        refreshExpiry(context);
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public int getTotalItemsCount(CartContext context) {
        List<Object> quantities = redisService.hValues(context.getKey());
        if (quantities != null && !quantities.isEmpty()) {
            return quantities.stream()
                    .mapToInt(PersistentDbCartStorage::parseQuantity)
                    .sum();
        }
        return getCartEntries(context).values().stream().mapToInt(Integer::intValue).sum();
    }

    @Override
    @Transactional
    public void addOrIncrementItem(CartContext context, String sku, int quantity) {
        User user = context.getUser();
        if (user == null || user.getId() == null) {
            return;
        }

        Cart cart = getOrCreateCart(user);
        Optional<CartItem> itemOpt = cart.getItems().stream()
                .filter(i -> sku.equals(i.getSku()))
                .findFirst();

        int finalQty;
        if (itemOpt.isPresent()) {
            CartItem item = itemOpt.get();
            finalQty = item.getQuantity() + quantity;
            item.setQuantity(finalQty);
            cartItemRepository.save(item);
        } else {
            finalQty = quantity;
            CartItem newItem = CartItem.builder()
                    .cart(cart)
                    .sku(sku)
                    .quantity(quantity)
                    .build();
            cart.getItems().add(newItem);
            cartItemRepository.save(newItem);
        }

        // Sync Redis cache
        redisService.hSet(context.getKey(), sku, String.valueOf(finalQty));
        refreshExpiry(context);
    }

    @Override
    @Transactional
    public void setItemQuantity(CartContext context, String sku, int quantity) {
        User user = context.getUser();
        if (user == null || user.getId() == null) {
            return;
        }

        Cart cart = getOrCreateCart(user);
        Optional<CartItem> itemOpt = cart.getItems().stream()
                .filter(i -> sku.equals(i.getSku()))
                .findFirst();

        if (quantity <= 0) {
            itemOpt.ifPresent(item -> {
                cart.getItems().remove(item);
                cartItemRepository.delete(item);
            });
            redisService.hDelete(context.getKey(), sku);
        } else {
            if (itemOpt.isPresent()) {
                CartItem item = itemOpt.get();
                item.setQuantity(quantity);
                cartItemRepository.save(item);
            } else {
                CartItem newItem = CartItem.builder()
                        .cart(cart)
                        .sku(sku)
                        .quantity(quantity)
                        .build();
                cart.getItems().add(newItem);
                cartItemRepository.save(newItem);
            }
            redisService.hSet(context.getKey(), sku, String.valueOf(quantity));
        }
        refreshExpiry(context);
    }

    @Override
    @Transactional
    public void removeItem(CartContext context, String sku) {
        User user = context.getUser();
        if (user == null || user.getId() == null) {
            return;
        }

        cartRepository.findByUser_Id(user.getId()).ifPresent(cart -> {
            cartItemRepository.deleteAllByCart_IdAndSkuIn(cart.getId(), List.of(sku));
        });

        redisService.hDelete(context.getKey(), sku);
        refreshExpiry(context);
    }

    @Override
    @Transactional
    public void removeItems(CartContext context, Collection<String> skus) {
        User user = context.getUser();
        if (user == null || user.getId() == null || skus == null || skus.isEmpty()) {
            return;
        }

        List<String> skuList = (skus instanceof List<String> list) ? list : new ArrayList<>(skus);
        cartRepository.findByUser_Id(user.getId()).ifPresent(cart -> {
            cartItemRepository.deleteAllByCart_IdAndSkuIn(cart.getId(), skuList);
        });

        redisService.hDelete(context.getKey(), skuList.toArray(new Object[0]));
        refreshExpiry(context);
    }

    @Override
    @Transactional
    public void clearCart(CartContext context) {
        User user = context.getUser();
        if (user != null && user.getId() != null) {
            cartRepository.findByUser_Id(user.getId()).ifPresent(cart -> {
                cartItemRepository.deleteAllByCart_Id(cart.getId());
            });
        }
        redisService.unlink(context.getKey());
    }

    @Override
    public void refreshExpiry(CartContext context) {
        if (context.getTtlDays() > 0) {
            redisService.expire(context.getKey(), context.getTtlDays(), TimeUnit.DAYS);
        }
    }
}
