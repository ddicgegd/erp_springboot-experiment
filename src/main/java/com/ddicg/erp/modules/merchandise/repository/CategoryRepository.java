package com.ddicg.erp.modules.merchandise.repository;

import com.ddicg.erp.modules.merchandise.model.Category;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;


public interface CategoryRepository extends JpaRepository<Category, Long>, JpaSpecificationExecutor<Category> {
    Optional<Category> findCategoryById(Long id);

    Optional<Category> findCategoryBySkuInfo_Sku(String skuInfoSku);

    @Modifying(clearAutomatically = true)
    @Query(
            value = "DELETE FROM category " +
                    "WHERE deleted_at IS NOT NULL " +
                    "AND deleted_at < SYSDATE",
            nativeQuery = true
    )
    void deleteAllExpiredCategories();

    boolean existsAllByName(String name);


    /**
     * Cập nhật (soft delete) một danh sách các Category bằng cách
     * set trường deletedAt và deletedBy.
     *
     * @param ids         Danh sách ID của các category cần xóa mềm
     * @param deletedBy   Tên người dùng/email thực hiện việc xóa
     * @param deletedAt   Thời điểm thực hiện việc xóa (thường là LocalDateTime.now())
     */
    @Modifying
    @Query("UPDATE Category c SET c.deletedAt = :deletedAt, c.deletedBy = :deletedBy WHERE c.id IN :ids")
    void softDeleteAllByIds(
            @Param("ids") List<Long> ids,
            @Param("deletedBy") String deletedBy,
            @Param("deletedAt") LocalDateTime deletedAt
    );

    @Transactional
    default void softDeleteAllByIds(List<Long> ids, String deletedBy) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        LocalDateTime deletionTime = LocalDateTime.now().plusDays(30);
        softDeleteAllByIds(ids, deletedBy, deletionTime);
    }

    Optional<Category> findCategoryByName(String name);

    @Query("""
            SELECT c FROM Category c
            WHERE c.id IN :ids
            AND (c.isDeleted IS NULL OR c.isDeleted = false)
            AND (c.deletedAt IS NULL OR c.deletedAt > CURRENT_TIMESTAMP)
            """)
    List<Category> findActiveByIdIn(@Param("ids") List<Long> ids);

    @Query("""
            SELECT c.id, c.skuInfo.sku FROM Category c
            WHERE c.skuInfo.sku IN :skus
            AND (c.isDeleted IS NULL OR c.isDeleted = false)
            AND (c.deletedAt IS NULL OR c.deletedAt > CURRENT_TIMESTAMP)
            """)
    List<Object[]> findIdsAndSkusBySkus(@Param("skus") List<String> skus);

    @Query("""
            SELECT c.id FROM Category c
            WHERE c.skuInfo.sku = :sku
            AND (c.isDeleted IS NULL OR c.isDeleted = false)
            AND (c.deletedAt IS NULL OR c.deletedAt > CURRENT_TIMESTAMP)
            """)
    Optional<Long> findIdBySku(@Param("sku") String sku);
}
