package com.ddicg.erp.modules.merchandise.service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import com.ddicg.erp.modules.merchandise.model.ProductInventory;
import com.ddicg.erp.modules.merchandise.repository.ProductInventoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryService {
    private static final Logger log = LoggerFactory.getLogger(InventoryService.class);

    
    private final ProductInventoryRepository inventoryRepository;
    private final RedissonClient redissonClient;
    
    private static final String LOCK_PREFIX = "inventory:lock:";
    private static final long LOCK_WAIT_TIME = 5;
    private static final long LOCK_LEASE_TIME = 10;
    
    @Transactional
    public boolean reserveStock(String sku, int quantity) {
        RLock lock = redissonClient.getLock(LOCK_PREFIX + sku);
        try {
            if (lock.tryLock(LOCK_WAIT_TIME, LOCK_LEASE_TIME, TimeUnit.SECONDS)) {
                try {
                    ProductInventory inventory = inventoryRepository.findBySkuWithLock(sku)
                        .orElseThrow(() -> new RuntimeException("Inventory not found for SKU: " + sku));
                    
                    if (inventory.getAvailableQuantity() < quantity) {
                        log.warn("Insufficient stock for SKU: {}. Available: {}, Requested: {}", 
                            sku, inventory.getAvailableQuantity(), quantity);
                        return false;
                    }
                    
                    inventory.setAvailableQuantity(inventory.getAvailableQuantity() - quantity);
                    inventory.setReservedQuantity(inventory.getReservedQuantity() + quantity);
                    inventoryRepository.save(inventory);
                    
                    log.info("Reserved {} units for SKU: {}", quantity, sku);
                    return true;
                } finally {
                    lock.unlock();
                }
            } else {
                log.error("Failed to acquire lock for SKU: {}", sku);
                return false;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Lock acquisition interrupted for SKU: {}", sku, e);
            return false;
        }
    }
    
    @Transactional
    public void confirmReservation(String sku, int quantity) {
        ProductInventory inventory = inventoryRepository.findBySku(sku)
            .orElseThrow(() -> new RuntimeException("Inventory not found for SKU: " + sku));
        
        inventory.setReservedQuantity(inventory.getReservedQuantity() - quantity);
        inventoryRepository.save(inventory);
        log.info("Confirmed reservation for SKU: {}, Quantity: {}", sku, quantity);
    }
    
    @Transactional
    public void releaseReservation(String sku, int quantity) {
        ProductInventory inventory = inventoryRepository.findBySku(sku)
            .orElseThrow(() -> new RuntimeException("Inventory not found for SKU: " + sku));
        
        inventory.setAvailableQuantity(inventory.getAvailableQuantity() + quantity);
        inventory.setReservedQuantity(inventory.getReservedQuantity() - quantity);
        inventoryRepository.save(inventory);
        log.info("Released reservation for SKU: {}, Quantity: {}", sku, quantity);
    }
    
    public boolean checkAvailability(String sku, int quantity) {
        return inventoryRepository.findBySku(sku)
            .map(inv -> inv.getAvailableQuantity() >= quantity)
            .orElse(false);
    }
}