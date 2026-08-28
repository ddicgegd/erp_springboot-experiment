package com.ddicg.erp.modules.order.repository;

import com.ddicg.erp.core.common.model.enums.OrderStatus;
import com.ddicg.erp.modules.order.model.Order;
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

    @EntityGraph(attributePaths = "orderItems")
    Optional<Order> findByOrderNumber(String orderNumber);

    boolean existsByOrderNumber(String orderNumber);

    @Query(value = "SELECT o FROM Order o " +
                   "WHERE o.customerInfo.customerId = :customerId " +
                   "AND o.currentStatus = :status",
           countQuery = "SELECT COUNT(o) FROM Order o " +
                        "WHERE o.customerInfo.customerId = :customerId " +
                        "AND o.currentStatus = :status")
    Page<Order> findMyOrdersByStatus(
            @Param("customerId") Long customerId,
            @Param("status") OrderStatus status,
            Pageable pageable
    );

    @Query(value = "SELECT DISTINCT o FROM Order o JOIN FETCH o.orderItems WHERE o.customerInfo.customerId = :customerId",
           countQuery = "SELECT COUNT(DISTINCT o) FROM Order o WHERE o.customerInfo.customerId = :customerId")
    Page<Order> findByCustomerId(@Param("customerId") Long customerId, Pageable pageable);

    @Query(value = "SELECT DISTINCT o FROM Order o JOIN FETCH o.orderItems WHERE o.status LIKE %:status%",
           countQuery = "SELECT COUNT(DISTINCT o) FROM Order o WHERE o.status LIKE %:status%")
    Page<Order> findByStatus(@Param("status") String status, Pageable pageable);

    @Query(value = "SELECT DISTINCT o FROM Order o JOIN FETCH o.orderItems WHERE o.customerInfo.customerId = :customerId AND o.status LIKE %:status%",
           countQuery = "SELECT COUNT(DISTINCT o) FROM Order o WHERE o.customerInfo.customerId = :customerId AND o.status LIKE %:status%")
    Page<Order> findByCustomerIdAndStatus(@Param("customerId") Long customerId, @Param("status") String status, Pageable pageable);

    @Query(value = "SELECT DISTINCT o FROM Order o JOIN FETCH o.orderItems WHERE o.auditInfo.createdAt BETWEEN :startDate AND :endDate",
           countQuery = "SELECT COUNT(DISTINCT o) FROM Order o WHERE o.auditInfo.createdAt BETWEEN :startDate AND :endDate")
    Page<Order> findByCreatedAtBetween(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            Pageable pageable
    );

    @Query(value = "SELECT DISTINCT o FROM Order o JOIN FETCH o.orderItems WHERE o.customerInfo.customerId = :customerId AND o.auditInfo.createdAt BETWEEN :startDate AND :endDate",
           countQuery = "SELECT COUNT(DISTINCT o) FROM Order o WHERE o.customerInfo.customerId = :customerId AND o.auditInfo.createdAt BETWEEN :startDate AND :endDate")
    Page<Order> findByCustomerIdAndCreatedAtBetween(
            @Param("customerId") Long customerId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            Pageable pageable
    );

    @Query("SELECT COUNT(o) FROM Order o WHERE o.status LIKE %:status%")
    long countByStatus(@Param("status") String status);

    @Query("SELECT COUNT(o) FROM Order o WHERE o.customerInfo.customerId = :customerId")
    long countByCustomerId(@Param("customerId") Long customerId);

    @Query("SELECT SUM(o.totalAmount) FROM Order o WHERE o.status LIKE %:status%")
    Double sumTotalAmountByStatus(@Param("status") String status);

    @Query("SELECT SUM(o.totalAmount) FROM Order o WHERE o.auditInfo.createdAt BETWEEN :startDate AND :endDate")
    Double sumTotalAmountByDateRange(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );

    @Query("SELECT DISTINCT o FROM Order o JOIN FETCH o.orderItems WHERE o.status LIKE '%PENDING%' OR o.status LIKE '%CONFIRMED%' ORDER BY o.auditInfo.createdAt ASC")
    List<Order> findPendingOrders();

    @Query("SELECT DISTINCT o FROM Order o JOIN FETCH o.orderItems WHERE o.status LIKE '%PROCESSING%' OR o.status LIKE '%PACKED%' OR o.status LIKE '%SHIPPED%' ORDER BY o.auditInfo.createdAt ASC")
    List<Order> findInProgressOrders();

    @Query("SELECT o.customerInfo.customerId, SUM(o.totalAmount) as total FROM Order o " +
           "WHERE o.status LIKE '%COMPLETED%' " +
           "GROUP BY o.customerInfo.customerId " +
           "ORDER BY total DESC")
    Page<Object[]> findTopCustomersByTotalAmount(Pageable pageable);

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
