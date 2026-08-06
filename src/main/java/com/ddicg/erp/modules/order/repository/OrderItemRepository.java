package com.ddicg.erp.modules.order.repository;

import com.ddicg.erp.modules.order.model.Order;
import com.ddicg.erp.modules.order.model.OrderItem;
import com.ddicg.erp.modules.merchandise.model.Product;
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
        * Tìm tất cả orders có chứa product
        */
       List<OrderItem> findByProduct(Product product);

       /**
        * Tìm tất cả orders có chứa product theo product ID
        */
       @Query("SELECT oi FROM OrderItem oi WHERE oi.product.id = :productId")
       List<OrderItem> findByProductId(@Param("productId") Long productId);

       /**
        * Thống kê sản phẩm bán chạy nhất
        */
       @Query("SELECT oi.product, SUM(oi.quantity) as totalQuantity " +
                     "FROM OrderItem oi " +
                     "JOIN oi.order o " +
                     "WHERE o.status = 'COMPLETED' " +
                     "GROUP BY oi.product " +
                     "ORDER BY totalQuantity DESC")
       List<Object[]> findBestSellingProducts();

       /**
        * Tính tổng số lượng đã bán của một product
        */
       @Query("SELECT SUM(oi.quantity) FROM OrderItem oi " +
                     "JOIN oi.order o " +
                     "WHERE oi.product.id = :productId AND o.status = 'COMPLETED'")
       Long sumQuantitySoldByProductId(@Param("productId") Long productId);

       /**
        * Tính tổng doanh thu của một product
        */
       @Query("SELECT SUM(oi.subtotal) FROM OrderItem oi " +
                     "JOIN oi.order o " +
                     "WHERE oi.product.id = :productId AND o.status = 'COMPLETED'")
       Double sumRevenueByProductId(@Param("productId") Long productId);

       /*
        * ============================ 📊 Analytics Queries
        * ============================
        */

       /**
        * Đếm số đơn hàng COMPLETED chứa product
        */
       @Query("SELECT COUNT(DISTINCT oi.order.id) FROM OrderItem oi " +
                     "JOIN oi.order o " +
                     "WHERE oi.product.id = :productId AND o.status = 'COMPLETED'")
       Integer countOrdersByProductId(@Param("productId") Long productId);

       /**
        * Tính doanh thu theo khoảng thời gian
        */
       @Query("SELECT COALESCE(SUM(oi.subtotal), 0) FROM OrderItem oi " +
                     "JOIN oi.order o " +
                     "WHERE oi.product.id = :productId " +
                     "AND o.status = 'COMPLETED' " +
                     "AND o.completedAt BETWEEN :startDate AND :endDate")
       Double sumRevenueByProductIdAndPeriod(
                     @Param("productId") Long productId,
                     @Param("startDate") java.time.LocalDateTime startDate,
                     @Param("endDate") java.time.LocalDateTime endDate);

       /**
        * Đếm số đơn hàng bị CANCELLED chứa product
        */
       @Query("SELECT COUNT(DISTINCT oi.order.id) FROM OrderItem oi " +
                     "JOIN oi.order o " +
                     "WHERE oi.product.id = :productId AND o.status = 'CANCELLED'")
       Integer countCancelledOrdersByProductId(@Param("productId") Long productId);

       /**
        * Đếm số đơn hàng bị RETURNED chứa product
        */
       @Query("SELECT COUNT(DISTINCT oi.order.id) FROM OrderItem oi " +
                     "JOIN oi.order o " +
                     "WHERE oi.product.id = :productId AND o.status = 'RETURNED'")
       Integer countReturnedOrdersByProductId(@Param("productId") Long productId);

       /**
        * Tổng số lượng đã bán của một Attributes (variant)
        */
       @Query("SELECT COALESCE(SUM(oi.quantity), 0) FROM OrderItem oi " +
                     "JOIN oi.order o " +
                     "WHERE oi.attributes.id = :attributesId AND o.status = 'COMPLETED'")
       Integer sumQuantitySoldByAttributesId(@Param("attributesId") Long attributesId);
}
