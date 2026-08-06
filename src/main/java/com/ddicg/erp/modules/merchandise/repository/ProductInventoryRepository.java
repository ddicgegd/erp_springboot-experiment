package com.ddicg.erp.modules.merchandise.repository;

import com.ddicg.erp.modules.merchandise.model.ProductInventory;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ProductInventoryRepository extends JpaRepository<ProductInventory, Long> {
    
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM ProductInventory i WHERE i.sku = :sku")
    Optional<ProductInventory> findBySkuWithLock(@Param("sku") String sku);
    
    Optional<ProductInventory> findBySku(String sku);
}
