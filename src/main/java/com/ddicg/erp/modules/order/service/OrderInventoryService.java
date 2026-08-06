package com.ddicg.erp.modules.order.service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import com.ddicg.erp.modules.merchandise.model.Attributes;
import com.ddicg.erp.modules.order.model.Order;
import com.ddicg.erp.modules.order.model.OrderItem;
import com.ddicg.erp.modules.merchandise.model.ProductInventory;
import com.ddicg.erp.modules.merchandise.repository.AttributesRepository;
import com.ddicg.erp.modules.merchandise.repository.ProductInventoryRepository;
import com.ddicg.erp.modules.merchandise.service.InventoryService;
import com.ddicg.erp.core.exception.BusinessException;
import com.ddicg.erp.core.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Service orchestration cho việc lock inventory trước khi tạo order.
 * Đảm bảo không oversell khi nhiều users mua cùng một sản phẩm.
 * 
 * @en Inventory orchestration service for order creation
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderInventoryService {
    private static final Logger log = LoggerFactory.getLogger(OrderInventoryService.class);


    private final ProductInventoryRepository inventoryRepository;
    private final AttributesRepository attributesRepository;
    private final RedissonClient redissonClient;
    private final InventoryService inventoryService;

    private static final String LOCK_PREFIX = "inventory:lock:";
    private static final long LOCK_WAIT_TIME = 10;
    private static final long LOCK_LEASE_TIME = 30;

    /**
     * Validate và reserve inventory cho một danh sách items.
     * Sử dụng distributed lock để ngăn oversell.
     * 
     * @en Validate and reserve inventory for order items
     * @param items Danh sách order items cần reserve
     * @return Map of SKU -> reserved quantity
     * @throws BusinessException nếu không đủ hàng hoặc lock fails
     */
    @Transactional
    public Map<String, Integer> reserveInventory(List<OrderItem> items) {
        if (items == null || items.isEmpty()) {
            return Collections.emptyMap();
        }

        // Extract unique SKUs
        List<String> skus = items.stream()
                .map(OrderItem::getAttributesSku)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        // Acquire locks for all SKUs
        List<RLock> acquiredLocks = new ArrayList<>();
        Map<String, Integer> reservedQuantities = new HashMap<>();

        try {
            // Sort SKUs to prevent deadlocks (always acquire in same order)
            Collections.sort(skus);
            
            // Acquire all locks
            for (String sku : skus) {
                RLock lock = redissonClient.getLock(LOCK_PREFIX + sku);
                boolean acquired = lock.tryLock(LOCK_WAIT_TIME, LOCK_LEASE_TIME, TimeUnit.SECONDS);
                
                if (!acquired) {
                    log.error("Failed to acquire lock for SKU: {}", sku);
                    throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, 
                            "Hệ thống đang bận, vui lòng thử lại sau: " + sku);
                }
                acquiredLocks.add(lock);
            }

            log.info("Acquired {} locks for order creation", skus.size());

            // Validate và reserve từng item
            for (OrderItem item : items) {
                String sku = item.getAttributesSku();
                int requestedQty = item.getQuantity();

                // Find inventory
                ProductInventory inventory = inventoryRepository.findBySku(sku)
                        .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND,
                                "Không tìm thấy thông tin kho cho SKU: " + sku));

                // Check availability
                if (inventory.getAvailableQuantity() < requestedQty) {
                    log.warn("Insufficient stock for SKU: {}. Available: {}, Requested: {}",
                            sku, inventory.getAvailableQuantity(), requestedQty);
                    throw new BusinessException(ErrorCode.INSUFFICIENT_STOCK,
                            "Sản phẩm " + sku + " chỉ còn " + inventory.getAvailableQuantity() + " sản phẩm")
                            .with("sku", sku)
                            .with("available", inventory.getAvailableQuantity())
                            .with("requested", requestedQty);
                }

                // Check product is not deleted
                if (Boolean.TRUE.equals(inventory.getProduct().getIsDeleted())) {
                    throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND,
                            "Sản phẩm " + sku + " không còn khả dụng");
                }

                // Reserve stock
                inventoryService.reserveStock(sku, requestedQty);
                reservedQuantities.put(sku, requestedQty);

                log.info("Reserved {} units for SKU: {}", requestedQty, sku);
            }

            return reservedQuantities;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Lock acquisition interrupted", e);
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "Hệ thống đang bận, vui lòng thử lại");
        } finally {
            // Always release locks
            for (RLock lock : acquiredLocks) {
                try {
                    if (lock.isHeldByCurrentThread()) {
                        lock.unlock();
                    }
                } catch (Exception e) {
                    log.error("Error releasing lock: {}", lock.getName(), e);
                }
            }
        }
    }

    /**
     * Release reserved inventory (khi order bị hủy).
     * 
     * @en Release reserved inventory when order is cancelled
     * @param items Danh sách order items
     */
    @Transactional
    public void releaseInventory(List<OrderItem> items) {
        if (items == null || items.isEmpty()) {
            return;
        }

        for (OrderItem item : items) {
            try {
                String sku = item.getAttributesSku();
                int quantity = item.getQuantity();
                
                inventoryService.releaseReservation(sku, quantity);
                
                log.info("Released {} units for SKU: {}", quantity, sku);
            } catch (Exception e) {
                // Log error nhưng không throw để không block order cancellation
                log.error("Failed to release inventory for SKU: {}", item.getAttributesSku(), e);
            }
        }
    }

    /**
     * Confirm reservation (khi payment thành công).
     * Chuyển từ reserved -> sold.
     * 
     * @en Confirm reservation when payment succeeds
     * @param items Danh sách order items
     */
    @Transactional
    public void confirmReservation(List<OrderItem> items) {
        if (items == null || items.isEmpty()) {
            return;
        }

        for (OrderItem item : items) {
            try {
                String sku = item.getAttributesSku();
                int quantity = item.getQuantity();
                
                inventoryService.confirmReservation(sku, quantity);
                
                log.info("Confirmed reservation for SKU: {}, Quantity: {}", sku, quantity);
            } catch (Exception e) {
                // Log error nhưng không throw
                log.error("Failed to confirm reservation for SKU: {}", item.getAttributesSku(), e);
            }
        }
    }

    /**
     * Validate cart items trước checkout.
     * Kiểm tra availability và price changes.
     * 
     * @en Validate cart items before checkout
     * @param skus List of SKUs with quantities
     * @return ValidationResult chứa thông tin valid/invalid items
     */
    public ValidationResult validateCartItems(Map<String, Integer> skusWithQuantities) {
        ValidationResult result = new ValidationResult();
        
        for (Map.Entry<String, Integer> entry : skusWithQuantities.entrySet()) {
            String sku = entry.getKey();
            int requestedQty = entry.getValue();

            try {
                ProductInventory inventory = inventoryRepository.findBySku(sku).orElse(null);
                
                if (inventory == null) {
                    result.addInvalidItem(sku, "Sản phẩm không tồn tại");
                    continue;
                }

                if (Boolean.TRUE.equals(inventory.getProduct().getIsDeleted())) {
                    result.addInvalidItem(sku, "Sản phẩm đã ngừng bán");
                    continue;
                }

                if (inventory.getAvailableQuantity() < requestedQty) {
                    result.addInvalidItem(sku, "Chỉ còn " + inventory.getAvailableQuantity() + " sản phẩm");
                    continue;
                }

                result.addValidItem(sku, inventory.getAvailableQuantity());

            } catch (Exception e) {
                log.error("Error validating SKU: {}", sku, e);
                result.addInvalidItem(sku, "Lỗi kiểm tra sản phẩm");
            }
        }

        return result;
    }

    /**
     * Result class cho cart validation
     */
    public static class ValidationResult {
        private List<ValidItem> validItems = new ArrayList<>();
        
        private List<InvalidItem> invalidItems = new ArrayList<>();

        public boolean isValid() {
            return invalidItems.isEmpty();
        }

        public void addValidItem(String sku, int availableQuantity) {
            validItems.add(new ValidItem(sku, availableQuantity));
        }

        public void addInvalidItem(String sku, String reason) {
            invalidItems.add(new InvalidItem(sku, reason));
        }

        public static class ValidItem {
            public ValidItem() {} 
            public ValidItem(String sku, int availableQuantity) { this.sku = sku; this.availableQuantity = availableQuantity; }
            private String sku;
            private int availableQuantity;
        }

        public static class InvalidItem {
            public InvalidItem() {}
            public InvalidItem(String sku, String reason) { this.sku = sku; this.reason = reason; }
            private String sku;
            private String reason;
        }
    }
}