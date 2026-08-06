package com.ddicg.erp.modules.merchandise.repository;

import com.ddicg.erp.modules.merchandise.model.Product;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;


public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

    @Modifying(clearAutomatically = true)
    @Query(value = "DELETE FROM product " +
            "WHERE deleted_at IS NOT NULL " +
            "AND deleted_at < SYSDATE", nativeQuery = true)
    void deleteAllExpiredProducts();

    @Modifying
    @Query("UPDATE Product c SET c.deletedAt = :deletedAt, c.deletedBy = :deletedBy WHERE c.id IN :ids")
    void softDeleteAllByIds(
            @Param("ids") List<Long> ids,
            @Param("deletedBy") String deletedBy,
            @Param("deletedAt") LocalDateTime deletedAt);

    @Transactional
    default void softDeleteAllByIds(List<Long> ids, String deletedBy) {
        if (ids == null || ids.isEmpty()) {
            return;
        }

        LocalDateTime deletionTime = LocalDateTime.now().plusDays(30);
        softDeleteAllByIds(ids, deletedBy, deletionTime);
    }

    @Modifying
    @Transactional
    @Query("UPDATE Product p SET p.viewCount = COALESCE(p.viewCount, 0) + 1 WHERE p.id = :id")
    void updateViewCount(@Param("id") Long id);

    @Modifying
    @Transactional
    @Query("UPDATE Product p SET p.totalSoldQuantity = COALESCE(p.totalSoldQuantity, 0) + :quantity WHERE p.id = :id")
    void updateTotalSoldQuantity(@Param("id") Long id, @Param("quantity") Integer quantity);

    @Modifying
    @Transactional
    @Query("UPDATE Product p SET p.totalOrders = COALESCE(p.totalOrders, 0) + 1 WHERE p.id = :id")
    void updateTotalOrders(@Param("id") Long id);

    @Modifying
    @Transactional
    @Query("update Product p SET p.totalRevenue = COALESCE(p.totalRevenue, 0) + :price WHERE p.id = :id")
    void updateTotalRevenue(@Param("id") Long id, @Param("price") BigDecimal price);

    Optional<Product> findProductByName(String name);

    Optional<Product> findProductBySkuInfo_Sku(String skuInfoSku);

    @Query("""
            SELECT p FROM Product p
            WHERE p.id IN :ids
            AND (p.isDeleted IS NULL OR p.isDeleted = false)
            AND (p.deletedAt IS NULL OR p.deletedAt > CURRENT_TIMESTAMP)
            """)
    List<Product> findActiveByIdIn(@Param("ids") List<Long> ids);

    @Query("""
            SELECT p.id, p.skuInfo.sku FROM Product p
            WHERE p.skuInfo.sku IN :skus
            AND (p.isDeleted IS NULL OR p.isDeleted = false)
            AND (p.deletedAt IS NULL OR p.deletedAt > CURRENT_TIMESTAMP)
            """)
    List<Object[]> findIdsAndSkusBySkus(@Param("skus") List<String> skus);

    @Query("""
            SELECT p.id FROM Product p
            WHERE p.category.id IN :categoryIds
            AND (p.isDeleted IS NULL OR p.isDeleted = false)
            AND (p.deletedAt IS NULL OR p.deletedAt > CURRENT_TIMESTAMP)
            """)
    List<Long> findActiveIdsByCategoryIds(@Param("categoryIds") List<Long> categoryIds);

    @Query("""
            SELECT p.id FROM Product p
            WHERE p.name = :name
            AND (p.isDeleted IS NULL OR p.isDeleted = false)
            AND (p.deletedAt IS NULL OR p.deletedAt > CURRENT_TIMESTAMP)
            """)
    Optional<Long> findIdByName(@Param("name") String name);

    @Query("""
            SELECT p.id FROM Product p
            WHERE p.skuInfo.sku = :sku
            AND (p.isDeleted IS NULL OR p.isDeleted = false)
            AND (p.deletedAt IS NULL OR p.deletedAt > CURRENT_TIMESTAMP)
            """)
    Optional<Long> findIdBySku(@Param("sku") String sku);

    // Eager Load: Tải sản phẩm cùng Category trong 1 câu SQL (chống N+1 query).
    // Dùng cho CacheSyncService khi đồng bộ chạy ngầm.
    @Query("""
            SELECT p FROM Product p LEFT JOIN FETCH p.category
            WHERE p.id = :id
            AND (p.isDeleted IS NULL OR p.isDeleted = false)
            AND (p.deletedAt IS NULL OR p.deletedAt > CURRENT_TIMESTAMP)
            """)
    Optional<Product> findByIdWithDetails(@Param("id") Long id);
}
