package com.ddicg.erp.modules.order.repository;

import com.ddicg.erp.modules.order.model.OrderFulfillmentEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderFulfillmentEventRepository extends JpaRepository<OrderFulfillmentEvent, Long> {
    List<OrderFulfillmentEvent> findByOrderIdOrderByEventTimeAsc(Long orderId);
    List<OrderFulfillmentEvent> findByOrderNumberOrderByEventTimeAsc(String orderNumber);
}
