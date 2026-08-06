package com.ddicg.erp.modules.order.repository;

import com.ddicg.erp.modules.order.model.Order;
import com.ddicg.erp.modules.iam.model.User;
import com.ddicg.erp.core.common.model.enums.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;


@Repository
public interface OrderRepository extends JpaRepository<Order, Long>, JpaSpecificationExecutor<Order> {

    /**
     * Tìm order theo order number (kèm orderItems)
     */
    @EntityGraph(attributePaths = "orderItems")
    Optional<Order> findByOrderNumber(String orderNumber);

    /**
     * Kiểm tra order number đã tồn tại chưa
     */
    boolean existsByOrderNumber(String orderNumber);

    /**
     * Tìm tất cả orders của một customer (kèm orderItems)
     */
    @EntityGraph(attributePaths = "orderItems")
    Page<Order> findByCustomer(User customer, Pageable pageable);

    /**
     * Tìm orders theo customer ID (kèm orderItems)
     */
    @Query(value = "SELECT DISTINCT o FROM Order o JOIN FETCH o.orderItems WHERE o.customer.id = :customerId",
           countQuery = "SELECT COUNT(DISTINCT o) FROM Order o WHERE o.customer.id = :customerId")
    Page<Order> findByCustomerId(@Param("customerId") Long customerId, Pageable pageable);

    /**
     * Tìm orders theo status (kèm orderItems)
     */
    @Query(value = "SELECT DISTINCT o FROM Order o JOIN FETCH o.orderItems WHERE o.status LIKE %:status%",
           countQuery = "SELECT COUNT(DISTINCT o) FROM Order o WHERE o.status LIKE %:status%")
    Page<Order> findByStatus(@Param("status") String status, Pageable pageable);

    /**
     * Tìm orders theo customer và status (kèm orderItems)
     */
    @Query(value = "SELECT DISTINCT o FROM Order o JOIN FETCH o.orderItems WHERE o.customer = :customer AND o.status LIKE %:status%",
           countQuery = "SELECT COUNT(DISTINCT o) FROM Order o WHERE o.customer = :customer AND o.status LIKE %:status%")
    Page<Order> findByCustomerAndStatus(@Param("customer") User customer, @Param("status") String status, Pageable pageable);

    /**
     * Tìm orders trong khoảng thời gian (kèm orderItems)
     */
    @Query(value = "SELECT DISTINCT o FROM Order o JOIN FETCH o.orderItems WHERE o.auditInfo.createdAt BETWEEN :startDate AND :endDate",
           countQuery = "SELECT COUNT(DISTINCT o) FROM Order o WHERE o.auditInfo.createdAt BETWEEN :startDate AND :endDate")
    Page<Order> findByCreatedAtBetween(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            Pageable pageable
    );

    /**
     * Tìm orders theo customer trong khoảng thời gian (kèm orderItems)
     */
    @Query(value = "SELECT DISTINCT o FROM Order o JOIN FETCH o.orderItems WHERE o.customer.id = :customerId AND o.auditInfo.createdAt BETWEEN :startDate AND :endDate",
           countQuery = "SELECT COUNT(DISTINCT o) FROM Order o WHERE o.customer.id = :customerId AND o.auditInfo.createdAt BETWEEN :startDate AND :endDate")
    Page<Order> findByCustomerIdAndCreatedAtBetween(
            @Param("customerId") Long customerId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            Pageable pageable
    );

    /**
     * Đếm số orders theo status
     */
    @Query("SELECT COUNT(o) FROM Order o WHERE o.status LIKE %:status%")
    long countByStatus(@Param("status") String status);

    /**
     * Đếm số orders của customer
     */
    long countByCustomer(User customer);

    /**
     * Tính tổng doanh thu theo status
     */
    @Query("SELECT SUM(o.totalAmount) FROM Order o WHERE o.status LIKE %:status%")
    Double sumTotalAmountByStatus(@Param("status") String status);

    /**
     * Tính tổng doanh thu trong khoảng thời gian
     */
    @Query("SELECT SUM(o.totalAmount) FROM Order o WHERE o.auditInfo.createdAt BETWEEN :startDate AND :endDate")
    Double sumTotalAmountByDateRange(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );

    /**
     * Tìm orders cần xử lý (PENDING, CONFIRMED) — kèm orderItems
     */
    @Query("SELECT DISTINCT o FROM Order o JOIN FETCH o.orderItems WHERE o.status LIKE '%PENDING%' OR o.status LIKE '%CONFIRMED%' ORDER BY o.auditInfo.createdAt ASC")
    List<Order> findPendingOrders();

    /**
     * Tìm orders đang giao hàng — kèm orderItems
     */
    @Query("SELECT DISTINCT o FROM Order o JOIN FETCH o.orderItems WHERE o.status LIKE '%PROCESSING%' OR o.status LIKE '%PACKED%' OR o.status LIKE '%SHIPPED%' ORDER BY o.auditInfo.createdAt ASC")
    List<Order> findInProgressOrders();

    /**
     * Tìm top customers theo tổng giá trị đơn hàng
     */
    @Query("SELECT o.customer, SUM(o.totalAmount) as total FROM Order o " +
           "WHERE o.status LIKE '%COMPLETED%' " +
           "GROUP BY o.customer " +
           "ORDER BY total DESC")
    Page<Object[]> findTopCustomersByTotalAmount(Pageable pageable);

    /**
     * Thống kê orders theo ngày
     */
    @Query("SELECT CAST(o.auditInfo.createdAt AS date) as createdDate, COUNT(o) as count, SUM(o.totalAmount) as total " +
           "FROM Order o " +
           "WHERE o.auditInfo.createdAt BETWEEN :startDate AND :endDate " +
           "GROUP BY CAST(o.auditInfo.createdAt AS date) " +
           "ORDER BY CAST(o.auditInfo.createdAt AS date) DESC")
    List<Object[]> getOrderStatisticsByDate(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );
}
