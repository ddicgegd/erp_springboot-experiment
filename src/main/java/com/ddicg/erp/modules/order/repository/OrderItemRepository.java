package com.ddicg.erp.modules.order.repository;

import com.ddicg.erp.modules.order.model.Order;
import com.ddicg.erp.modules.order.model.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    /**
     * Tìm tất cả items của một order
     */
    List<OrderItem> findByOrder(Order order);

    /**
     * Tìm tất cả items của một order theo order ID
     */
    @Query("SELECT oi FROM OrderItem oi WHERE oi.order.id = :orderId")
    List<OrderItem> findByOrderId(@Param("orderId") Long orderId);

    /**
     * Tìm tất cả order items theo attributes SKU
     */
    List<OrderItem> findByAttributesSku(String attributesSku);

    /**
     * Tìm tất cả order items theo product SKU
     */
    List<OrderItem> findByProductSku(String productSku);

    /**
     * Thống kê sản phẩm bán chạy nhất theo attributes SKU
     */
    @Query("SELECT oi.attributesSku, oi.productName, SUM(oi.quantity) as totalQuantity " +
           "FROM OrderItem oi " +
           "JOIN oi.order o " +
           "WHERE o.status = 'COMPLETED' " +
           "GROUP BY oi.attributesSku, oi.productName " +
           "ORDER BY totalQuantity DESC")
    List<Object[]> findBestSellingProducts();

    /**
     * Tính tổng số lượng đã bán của một attributes SKU
     */
    @Query("SELECT COALESCE(SUM(oi.quantity), 0) FROM OrderItem oi " +
           "JOIN oi.order o " +
           "WHERE oi.attributesSku = :attributesSku AND o.status = 'COMPLETED'")
    Long sumQuantitySoldByAttributesSku(@Param("attributesSku") String attributesSku);

    /**
     * Tính tổng doanh thu của một attributes SKU
     */
    @Query("SELECT COALESCE(SUM(oi.subtotal), 0) FROM OrderItem oi " +
           "JOIN oi.order o " +
           "WHERE oi.attributesSku = :attributesSku AND o.status = 'COMPLETED'")
    Double sumRevenueByAttributesSku(@Param("attributesSku") String attributesSku);

    /**
     * Đếm số đơn hàng COMPLETED chứa attributes SKU
     */
    @Query("SELECT COUNT(DISTINCT oi.order.id) FROM OrderItem oi " +
           "JOIN oi.order o " +
           "WHERE oi.attributesSku = :attributesSku AND o.status = 'COMPLETED'")
    Integer countOrdersByAttributesSku(@Param("attributesSku") String attributesSku);

    /**
     * Tính doanh thu theo khoảng thời gian của một attributes SKU
     */
    @Query("SELECT COALESCE(SUM(oi.subtotal), 0) FROM OrderItem oi " +
           "JOIN oi.order o " +
           "WHERE oi.attributesSku = :attributesSku " +
           "AND o.status = 'COMPLETED' " +
           "AND o.completedAt BETWEEN :startDate AND :endDate")
    Double sumRevenueByAttributesSkuAndPeriod(
           @Param("attributesSku") String attributesSku,
           @Param("startDate") java.time.LocalDateTime startDate,
           @Param("endDate") java.time.LocalDateTime endDate);

    /**
     * Đếm số đơn hàng bị CANCELLED chứa attributes SKU
     */
    @Query("SELECT COUNT(DISTINCT oi.order.id) FROM OrderItem oi " +
           "JOIN oi.order o " +
           "WHERE oi.attributesSku = :attributesSku AND o.status = 'CANCELLED'")
    Integer countCancelledOrdersByAttributesSku(@Param("attributesSku") String attributesSku);

    /**
     * Đếm số đơn hàng bị RETURNED chứa attributes SKU
     */
    @Query("SELECT COUNT(DISTINCT oi.order.id) FROM OrderItem oi " +
           "JOIN oi.order o " +
           "WHERE oi.attributesSku = :attributesSku AND o.status = 'RETURNED'")
    Integer countReturnedOrdersByAttributesSku(@Param("attributesSku") String attributesSku);
}

