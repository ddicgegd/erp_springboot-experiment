
package com.ddicg.erp.repository;

import com.ddicg.erp.model.entity.Attributes;
import com.ddicg.erp.model.entity.Product;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AttributesRepository extends JpaRepository<Attributes, Long>, JpaSpecificationExecutor<Attributes> {

  Optional<Attributes> findAttributesBySku_sku(String skuSku);

  @Query("""
      SELECT a FROM Attributes a
      WHERE a.sku.sku = :sku
      AND (a.isDeleted IS NULL OR a.isDeleted = false)
      AND (a.deletedAt IS NULL OR a.deletedAt > CURRENT_TIMESTAMP)
      """)
  Optional<Attributes> findAttributesBySkuNotDeleted(@Param("sku") String sku);

  Optional<Attributes> findAttributesById(Long id);

  List<Attributes> findAllBySku_skuIn(List<String> skus);

  @Query("""
      SELECT a FROM Attributes a
      WHERE a.id IN :ids
      AND (a.isDeleted IS NULL OR a.isDeleted = false)
      AND (a.deletedAt IS NULL OR a.deletedAt > CURRENT_TIMESTAMP)
      """)
  List<Attributes> getQuantityAttributesById(@Param("ids") List<Long> ids);

  List<Attributes> findAllByProduct(Product product);

  @Query("""
      SELECT a FROM Attributes a
      WHERE a.product = :product
      AND (a.isDeleted IS NULL OR a.isDeleted = false)
      AND (a.deletedAt IS NULL OR a.deletedAt > CURRENT_TIMESTAMP)
      """)
  List<Attributes> findAllByProductNotDeleted(@Param("product") Product product);

  List<Attributes> findAllByProduct_Id(Long productId);

  @Query("""
      SELECT a FROM Attributes a
      WHERE a.product.id = :productId
      AND (a.isDeleted IS NULL OR a.isDeleted = false)
      AND (a.deletedAt IS NULL OR a.deletedAt > CURRENT_TIMESTAMP)
      """)
  List<Attributes> findAllByProductIdNotDeleted(@Param("productId") Long productId);

  boolean existsBySku_sku(String skuSku);

  long countByProduct(Product product);

  @Query("""
      SELECT COUNT(a) FROM Attributes a
      WHERE a.product = :product
      AND (a.isDeleted IS NULL OR a.isDeleted = false)
      AND (a.deletedAt IS NULL OR a.deletedAt > CURRENT_TIMESTAMP)
      """)
  long countByProductNotDeleted(@Param("product") Product product);

  @Query("""
      SELECT a FROM Attributes a
      WHERE LOWER(a.name) LIKE LOWER(CONCAT('%', :name, '%'))
      AND (a.isDeleted IS NULL OR a.isDeleted = false)
      AND (a.deletedAt IS NULL OR a.deletedAt > CURRENT_TIMESTAMP)
      """)
  List<Attributes> findByNameContainingNotDeleted(@Param("name") String name);

  @Query("""
      SELECT a FROM Attributes a
      WHERE a.price BETWEEN :minPrice AND :maxPrice
      AND (a.isDeleted IS NULL OR a.isDeleted = false)
      AND (a.deletedAt IS NULL OR a.deletedAt > CURRENT_TIMESTAMP)
      """)
  List<Attributes> findByPriceBetweenNotDeleted(@Param("minPrice") Double minPrice, @Param("maxPrice") Double maxPrice);

  @Query("""
      SELECT a FROM Attributes a
      WHERE a.salePrice IS NOT NULL AND a.salePrice > 0
      AND (a.isDeleted IS NULL OR a.isDeleted = false)
      AND (a.deletedAt IS NULL OR a.deletedAt > CURRENT_TIMESTAMP)
      """)
  List<Attributes> findAllOnSale();

  @Modifying(clearAutomatically = true)
  @Query(value = "DELETE FROM attributes " +
      "WHERE deleted_at IS NOT NULL " +
      "AND deleted_at < SYSDATE", nativeQuery = true)
  void deleteAllExpiredAttributes();

  @Modifying
  @Query("UPDATE Attributes a SET a.salePrice = :salePrice WHERE a.sku.sku = :sku")
  void updateSalePrice(@Param("sku") String sku, @Param("salePrice") Double salePrice);

  @Modifying
  @Transactional
  @Query("UPDATE Attributes a SET a.soldQuantity = COALESCE(a.soldQuantity, 0) + :quantity WHERE a.id = :id")
  void updateSoldQuantity(@Param("id") Long id, @Param("quantity") Integer quantity);

  @Modifying
  @Transactional
  @Query("UPDATE Attributes a SET a.totalOrders = COALESCE(a.totalOrders, 0) + 1 WHERE a.id = :id")
  void updateTotalOrders(@Param("id") Long id);

  @Query("""
      SELECT a.id, a.sku.sku FROM Attributes a
      WHERE a.sku.sku IN :skus
      AND (a.isDeleted IS NULL OR a.isDeleted = false)
      AND (a.deletedAt IS NULL OR a.deletedAt > CURRENT_TIMESTAMP)
      """)
  List<Object[]> findIdsAndSkusBySkus(@Param("skus") List<String> skus);

  @Query("""
      SELECT a.id FROM Attributes a
      WHERE a.product.id IN :productIds
      AND (a.isDeleted IS NULL OR a.isDeleted = false)
      AND (a.deletedAt IS NULL OR a.deletedAt > CURRENT_TIMESTAMP)
      """)
  List<Long> findActiveIdsByProductIds(@Param("productIds") List<Long> productIds);

  @Query("""
      SELECT a.id FROM Attributes a
      WHERE a.sku.sku = :sku
      AND (a.isDeleted IS NULL OR a.isDeleted = false)
      AND (a.deletedAt IS NULL OR a.deletedAt > CURRENT_TIMESTAMP)
      """)
  Optional<Long> findIdBySkuNotDeleted(@Param("sku") String sku);

  @Query("""
      SELECT a FROM Attributes a
      WHERE (:productIds IS NULL OR a.product.id IN (:productIds))
      AND (:skus IS NULL OR a.sku.sku IN (:skus))
      """)
  List<Attributes> findCandidateSkusByProductIds(
      @Param("productIds") List<Long> productIds,
      @Param("skus") List<String> skus);
}
