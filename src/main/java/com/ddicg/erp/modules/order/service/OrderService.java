package com.ddicg.erp.modules.order.service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import com.ddicg.erp.modules.merchandise.mapper.OrderMapper;
import com.ddicg.erp.core.common.model.embedded.AuditInfo;
import com.ddicg.erp.modules.iam.model.*;
import com.ddicg.erp.modules.merchandise.model.*;
import com.ddicg.erp.modules.order.model.*;
import com.ddicg.erp.modules.cart.model.*;
import com.ddicg.erp.core.common.model.enums.OrderStatus;
import com.ddicg.erp.core.common.model.enums.PaymentMethod;
import com.ddicg.erp.core.common.model.enums.SearchOperation;
import com.ddicg.erp.modules.iam.repository.*;
import com.ddicg.erp.modules.merchandise.repository.*;
import com.ddicg.erp.modules.order.repository.*;
import com.ddicg.erp.modules.cart.repository.*;
import com.ddicg.erp.core.common.repository.specification.SearchCriteria;
import com.ddicg.erp.core.common.repository.specification.SpecificationBuilder;
import com.ddicg.erp.modules.order.service.OutboxOrderHelper;
import com.ddicg.erp.modules.order.service.OrderInventoryService;
import com.ddicg.erp.modules.order.dto.OrderDto;
import com.ddicg.erp.modules.iam.dto.request.*;
import com.ddicg.erp.modules.merchandise.dto.request.*;
import com.ddicg.erp.modules.order.dto.request.*;
import com.ddicg.erp.core.common.dto.request.*;
import com.ddicg.erp.core.common.dto.response.PageableData;
import com.ddicg.erp.core.common.dto.response.PagingResponse;
import com.ddicg.erp.core.common.dto.response.Response;
import com.ddicg.erp.modules.order.service.iOrder;
import com.ddicg.erp.core.security.SecurityUtil;
import com.ddicg.erp.core.exception.BusinessException;
import com.ddicg.erp.core.exception.ErrorCode;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService implements iOrder {
    private static final Logger log = LoggerFactory.getLogger(OrderService.class);


    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final AttributesRepository attributesRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final ShoppingCartRepository shoppingCartRepository;
    private final OrderMapper orderMapper;
    private final SecurityUtil securityUtil;
    private final OrderStatusHandler orderStatusHandler;
    private final OutboxOrderHelper outboxOrderHelper;
    private final OrderInventoryService orderInventoryService;

    @Override @Transactional
    public Response<OrderDto> createOrder(CreateOrderRequest request) {
        User customer = securityUtil.getCurrentUser().orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED, ""));
        Order order = new Order();
        order.setOrderNumber(generateOrderNumber());
        var initialStatus = new ArrayList<OrderStatus>();
        initialStatus.add(OrderStatus.PENDING);
        if (request.getPaymentMethod() == PaymentMethod.COD) {
            initialStatus.add(OrderStatus.CONFIRMED);
            initialStatus.add(OrderStatus.PROCESSING);
        } else if (request.getPaymentMethod() != null) {
            initialStatus.add(OrderStatus.WAITING_PAYMENT);
        }
        order.setStatus(initialStatus);
        order.setCurrentStatus(initialStatus.get(initialStatus.size() - 1));
        order.setCustomer(customer);
        order.setCustomerName(customer.getFullName());
        order.setCustomerEmail(customer.getEmail());
        order.setCustomerPhone(customer.getPhoneNumber());
        order.setShippingMethod(request.getShippingMethod());
        order.setCustomerNotes(request.getCustomerNotes());
        order.setDiscountCode(request.getDiscountCode());
        order.setShippingFee(30000.0);
        order.setAuditInfo(new AuditInfo());
        order.getAuditInfo().addUpdateEntry("Tạo đơn hàng", securityUtil.getCurrentUsername());

        List<OrderItem> items = new ArrayList<>();
        if (request.isFromCart()) {
            var cart = shoppingCartRepository.findByUser(customer).orElseThrow();
            items = cart.getCartItems().stream().map(i -> {
                var a = attributesRepository.findAttributesBySku_sku(i.getSku()).orElseThrow();
                return buildItem(a, i.getQuantity(), order);
            }).toList();
            cart.clearItems();
            shoppingCartRepository.save(cart);
        } else {
            var skus = request.getItems().stream().map(CreateOrderRequest.OrderItemRequest::getAttributesSku).toList();
            var qty = request.getItems().stream().map(CreateOrderRequest.OrderItemRequest::getQuantity).toList();
            List<SearchCriteria> c = new ArrayList<>();
            c.add(new SearchCriteria("sku.sku", SearchOperation.IN, skus));
            var attrs = attributesRepository.findAll(new SpecificationBuilder<Attributes>(c).build());
            if (attrs.size() != qty.size()) throw new BusinessException(ErrorCode.ATTRIBUTES_OUT_OF_STOCK, "");
            items = buildItems(request.getItems(), order);
        }
        order.setOrderItems(items);
        items.forEach(i -> i.setOrder(order));
        calcTotal(order);
        Order saved = orderRepository.save(order);
        log.info("✅ ORDER_CREATED: {}", saved.getOrderNumber());
        outboxOrderHelper.saveOrderCreatedEvent(saved, request);
        // Ghi outbox event cho auto-transition
        if (request.getPaymentMethod() == PaymentMethod.COD) {
            outboxOrderHelper.saveOrderStatusChangedEvent(saved, OrderStatus.PENDING, OrderStatus.PROCESSING, "COD auto", "system");
        } else if (request.getPaymentMethod() != null) {
            outboxOrderHelper.saveOrderStatusChangedEvent(saved, OrderStatus.PENDING, OrderStatus.WAITING_PAYMENT, "Online chờ thanh toán", "system");
        }
        return Response.ok(orderMapper.toDto(saved));
    }

    @Override public Response<OrderDto> getOrderById(String id) {
        var o = orderRepository.findById(convertLong(id)).orElseThrow();
        return Response.ok(orderMapper.toDto(o));
    }

    @Override public Response<OrderDto> getOrderByOrderNumber(String n) {
        return Response.ok(orderMapper.toDto(orderRepository.findByOrderNumber(n).orElseThrow()));
    }

    @Override public Response<PagingResponse<OrderDto>> getMyOrders(OrderSearchRequest r) {
        var u = securityUtil.getCurrentUser().orElseThrow();
        var p = orderRepository.findByCustomerId(u.getId(), PageRequest.of(0,20));
        return Response.ok(PagingResponse.<OrderDto>builder().contents(p.map(orderMapper::toDto).getContent())
                .paging(PageableData.builder().pageNumber(p.getNumber()).totalPages(p.getTotalPages())
                        .totalElements(p.getTotalElements()).pageSize(p.getSize()).build()).build());
    }

    @Override public Response<PagingResponse<OrderDto>> searchOrders(OrderSearchRequest r) {
        int page = r.getPage() != null ? r.getPage() : 0;
        int size = r.getSize() != null ? r.getSize() : 20;
        Sort sort = Sort.by(Sort.Direction.DESC, "auditInfo.createdAt");
        if (r.getSortBy() != null && !r.getSortBy().isEmpty()) {
            Sort.Direction direction = "ASC".equalsIgnoreCase(r.getSortDirection()) ? Sort.Direction.ASC : Sort.Direction.DESC;
            sort = Sort.by(direction, r.getSortBy());
        }
        Pageable pageable = PageRequest.of(page, size, sort);
        
        Specification<Order> spec = com.ddicg.erp.core.common.repository.specification.OrderSpecification.build(r);
        var p = orderRepository.findAll(spec, pageable);
        
        return Response.ok(PagingResponse.<OrderDto>builder().contents(p.map(orderMapper::toDto).getContent())
                .paging(PageableData.builder().pageNumber(p.getNumber()).totalPages(p.getTotalPages())
                        .totalElements(p.getTotalElements()).pageSize(p.getSize()).build()).build());
    }

    @Override public Response<List<OrderDto>> getPendingOrders() {
        return Response.ok(orderRepository.findPendingOrders().stream().map(orderMapper::toDto).toList());
    }

    @Override public Response<List<OrderDto>> getInProgressOrders() {
        return Response.ok(orderRepository.findInProgressOrders().stream().map(orderMapper::toDto).toList());
    }

    @Override public Response<?> getOrderStatistics(String a, String b) { return Response.ok(null); }

    @Override @Transactional
    public Response<OrderDto> updateShipping(UpdateShippingRequest r) {
        var o = orderRepository.findById(convertLong(r.getOrderId())).orElseThrow();
        if (r.getShippingMethod() != null) o.setShippingMethod(r.getShippingMethod());
        return Response.ok(orderMapper.toDto(orderRepository.save(o)));
    }

    @Override @Transactional
    public Response<OrderDto> updateDelivery(UpdateDeliveryRequest r) {
        var o = orderRepository.findById(convertLong(r.getOrderId())).orElseThrow();
        if (r.getEstimatedDeliveryDate() != null) o.setEstimatedDeliveryDate(r.getEstimatedDeliveryDate());
        if (r.getActualDeliveryDate() != null) o.setActualDeliveryDate(r.getActualDeliveryDate());
        return Response.ok(orderMapper.toDto(orderRepository.save(o)));
    }

    @Override @Transactional
    public Response<OrderDto> updateAdminNotes(UpdateAdminNotesRequest r) {
        var o = orderRepository.findById(convertLong(r.getOrderId())).orElseThrow();
        if (r.getAdminNotes() != null) o.setAdminNotes(r.getAdminNotes());
        return Response.ok(orderMapper.toDto(orderRepository.save(o)));
    }

    @Override @Transactional
    public Response<OrderDto> confirmOrder(ConfirmOrderRequest r) {
        var o = orderRepository.findById(convertLong(r.getOrderId())).orElseThrow();
        var cur = orderStatusHandler.getCurrentStatus(o);
        orderStatusHandler.transitionTo(o, OrderStatus.CONFIRMED, r.getConfirmationInfo());
        o.setConfirmedAt(LocalDateTime.now());
        o.setConfirmedBy(securityUtil.getCurrentUsername());
        orderInventoryService.confirmReservation(o.getOrderItems());
        var s = orderRepository.save(o);
        outboxOrderHelper.saveOrderStatusChangedEvent(s, cur, OrderStatus.CONFIRMED, r.getConfirmationInfo());
        log.info("🔄 CONFIRMED: {}", s.getOrderNumber());
        return Response.ok(orderMapper.toDto(s));
    }

    @Override @Transactional
    public Response<OrderDto> cancelOrder(CancelOrderRequest r) {
        var o = orderRepository.findById(convertLong(r.getOrderId())).orElseThrow();
        var cur = orderStatusHandler.getCurrentStatus(o);
        var reason = r.getCancellationReason() != null ? r.getCancellationReason() : "";
        if (!orderStatusHandler.isValidTransition(cur, OrderStatus.CANCELLED))
            throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION, "");
        orderStatusHandler.transitionTo(o, OrderStatus.CANCELLED, reason);
        o.setCancellationReason(reason); o.setCancelledAt(LocalDateTime.now());
        o.setCancelledBy(securityUtil.getCurrentUsername());
        orderInventoryService.releaseInventory(o.getOrderItems());
        var wasPaid = cur == OrderStatus.CONFIRMED || cur == OrderStatus.PROCESSING;
        var s = orderRepository.save(o);
        outboxOrderHelper.saveOrderStatusChangedEvent(s, cur, OrderStatus.CANCELLED, reason);
        outboxOrderHelper.saveOrderCancelledEvent(s, reason, wasPaid);
        log.info("🔄 CANCELLED: {} wasPaid={}", s.getOrderNumber(), wasPaid);
        return Response.ok(orderMapper.toDto(s));
    }

    @Override @Transactional
    public Response<OrderDto> completeOrder(CompleteOrderRequest r) {
        var o = orderRepository.findById(convertLong(r.getOrderId())).orElseThrow();
        var cur = orderStatusHandler.getCurrentStatus(o);
        orderStatusHandler.transitionTo(o, OrderStatus.COMPLETED, "");
        o.setCompletedAt(LocalDateTime.now());
        var s = orderRepository.save(o);
        outboxOrderHelper.saveOrderStatusChangedEvent(s, cur, OrderStatus.COMPLETED, "");
        log.info("🔄 COMPLETED: {}", s.getOrderNumber());
        return Response.ok(orderMapper.toDto(s));
    }

    @Override @Transactional
    public Response<OrderDto> processOrder(ProcessOrderRequest r) {
        var o = orderRepository.findById(convertLong(r.getOrderId())).orElseThrow();
        var cur = orderStatusHandler.getCurrentStatus(o);
        orderStatusHandler.transitionTo(o, OrderStatus.PROCESSING, r.getNote());
        var s = orderRepository.save(o);
        outboxOrderHelper.saveOrderStatusChangedEvent(s, cur, OrderStatus.PROCESSING, r.getNote());
        log.info("🔄 PROCESSING: {}", s.getOrderNumber());
        return Response.ok(orderMapper.toDto(s));
    }

    @Override @Transactional
    public Response<OrderDto> shipOrder(ShipOrderRequest r) {
        var o = orderRepository.findById(convertLong(r.getOrderId())).orElseThrow();
        var cur = orderStatusHandler.getCurrentStatus(o);
        orderStatusHandler.transitionTo(o, OrderStatus.SHIPPING, r.getNote());
        o.setShipperId(r.getShipperId()); o.setShipperName(r.getShipperName());
        o.setShipperPhone(r.getShipperPhone()); o.setDeliveryToken(UUID.randomUUID().toString());
        var s = orderRepository.save(o);
        outboxOrderHelper.saveOrderStatusChangedEvent(s, cur, OrderStatus.SHIPPING, "shipper: "+r.getShipperName());
        log.info("🔄 SHIPPING: {}", s.getOrderNumber());
        return Response.ok(orderMapper.toDto(s));
    }

    @Override @Transactional
    public Response<OrderDto> markDelayed(DelayOrderRequest r) {
        var o = orderRepository.findById(convertLong(r.getOrderId())).orElseThrow();
        var cur = orderStatusHandler.getCurrentStatus(o);
        orderStatusHandler.transitionTo(o, OrderStatus.DELAYED, r.getReason());
        var s = orderRepository.save(o);
        outboxOrderHelper.saveOrderStatusChangedEvent(s, cur, OrderStatus.DELAYED, r.getReason());
        log.info("🔄 DELAYED: {}", s.getOrderNumber());
        return Response.ok(orderMapper.toDto(s));
    }

    @Override @Transactional
    public Response<OrderDto> deliverOrder(DeliverOrderRequest r) {
        var o = orderRepository.findById(convertLong(r.getOrderId())).orElseThrow();
        var cur = orderStatusHandler.getCurrentStatus(o);
        orderStatusHandler.transitionTo(o, OrderStatus.DELIVERED, r.getNote());
        o.setActualDeliveryDate(LocalDateTime.now());
        var s = orderRepository.save(o);
        outboxOrderHelper.saveOrderStatusChangedEvent(s, cur, OrderStatus.DELIVERED, "giao thành công");
        log.info("🔄 DELIVERED: {}", s.getOrderNumber());
        return Response.ok(orderMapper.toDto(s));
    }

    @Override @Transactional
    public Response<OrderDto> readyForPickup(ReadyForPickupRequest r) {
        var o = orderRepository.findById(convertLong(r.getOrderId())).orElseThrow();
        var cur = orderStatusHandler.getCurrentStatus(o);
        orderStatusHandler.transitionTo(o, OrderStatus.READY_FOR_PICKUP, r.getNote());
        var s = orderRepository.save(o);
        outboxOrderHelper.saveOrderStatusChangedEvent(s, cur, OrderStatus.READY_FOR_PICKUP, r.getNote());
        log.info("🔄 READY_FOR_PICKUP: {}", s.getOrderNumber());
        return Response.ok(orderMapper.toDto(s));
    }

    @Override @Transactional
    public Response<OrderDto> pickupOrder(PickupOrderRequest r) {
        var o = orderRepository.findById(convertLong(r.getOrderId())).orElseThrow();
        var cur = orderStatusHandler.getCurrentStatus(o);
        orderStatusHandler.transitionTo(o, OrderStatus.DELIVERED, r.getNote());
        var s = orderRepository.save(o);
        outboxOrderHelper.saveOrderStatusChangedEvent(s, cur, OrderStatus.DELIVERED, "khách lấy tại shop");
        log.info("🔄 PICKUP: {}", s.getOrderNumber());
        return Response.ok(orderMapper.toDto(s));
    }

    @Override @Transactional
    public Response<OrderDto> returnOrder(ReturnOrderRequest r) {
        var o = orderRepository.findById(convertLong(r.getOrderId())).orElseThrow();
        var cur = orderStatusHandler.getCurrentStatus(o);
        orderStatusHandler.transitionTo(o, OrderStatus.RETURNING, r.getReason());
        var s = orderRepository.save(o);
        outboxOrderHelper.saveOrderStatusChangedEvent(s, cur, OrderStatus.RETURNING, r.getReason());
        log.info("🔄 RETURNING: {}", s.getOrderNumber());
        return Response.ok(orderMapper.toDto(s));
    }

    @Override @Transactional
    public Response<OrderDto> confirmReturn(ConfirmReturnRequest r) {
        var o = orderRepository.findById(convertLong(r.getOrderId())).orElseThrow();
        var cur = orderStatusHandler.getCurrentStatus(o);
        orderStatusHandler.transitionTo(o, OrderStatus.RETURNED, r.getNote());
        var s = orderRepository.save(o);
        outboxOrderHelper.saveOrderStatusChangedEvent(s, cur, OrderStatus.RETURNED, r.getCondition());
        log.info("🔄 RETURNED: {}", s.getOrderNumber());
        return Response.ok(orderMapper.toDto(s));
    }

    @Override @Transactional
    public Response<OrderDto> refundOrder(RefundOrderRequest r) {
        var o = orderRepository.findById(convertLong(r.getOrderId())).orElseThrow();
        var cur = orderStatusHandler.getCurrentStatus(o);
        orderStatusHandler.transitionTo(o, OrderStatus.REFUNDED, r.getNote());
        orderInventoryService.releaseInventory(o.getOrderItems());
        var s = orderRepository.save(o);
        outboxOrderHelper.saveOrderStatusChangedEvent(s, cur, OrderStatus.REFUNDED, "hoàn tiền");
        log.info("🔄 REFUNDED: {}", s.getOrderNumber());
        return Response.ok(orderMapper.toDto(s));
    }

    @Override @Transactional
    public Response<OrderDto> processPayment(PaymentCallbackRequest r) {
        var o = orderRepository.findByOrderNumber(r.getOrderNumber()).orElseThrow();
        var cur = orderStatusHandler.getCurrentStatus(o);
        if (cur != OrderStatus.WAITING_PAYMENT && cur != OrderStatus.PENDING)
            throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION, "");
        if ("SUCCESS".equalsIgnoreCase(r.getStatus())) {
            orderStatusHandler.transitionTo(o, OrderStatus.CONFIRMED, "");
            orderStatusHandler.transitionTo(o, OrderStatus.PROCESSING, "");
            o.setConfirmedAt(LocalDateTime.now());
            orderInventoryService.confirmReservation(o.getOrderItems());
            var s = orderRepository.save(o);
            outboxOrderHelper.saveOrderStatusChangedEvent(s, cur, OrderStatus.PROCESSING, "payment OK");
            log.info("🔄 PAYMENT_SUCCESS: {}", s.getOrderNumber());
            return Response.ok(orderMapper.toDto(s));
        } else {
            orderStatusHandler.transitionTo(o, OrderStatus.FAILED, "");
            var s = orderRepository.save(o);
            outboxOrderHelper.saveOrderStatusChangedEvent(s, cur, OrderStatus.FAILED, "payment FAILED");
            log.info("🔄 PAYMENT_FAILED: {}", s.getOrderNumber());
            return Response.ok(orderMapper.toDto(s));
        }
    }

    @Override
    public void setStatus(String orderNumber, OrderStatus status) {
        var o = orderRepository.findByOrderNumber(orderNumber).orElseThrow();
        orderStatusHandler.transitionTo(o, status, "");
        orderRepository.save(o);
    }

    private void calcTotal(Order order) {
        double sub = order.getOrderItems().stream().mapToDouble(i -> i.getSubtotal()!=null?i.getSubtotal():0).sum();
        order.setSubtotal(sub);
        order.setTotalAmount(Math.max(0, sub - (order.getDiscountAmount()!=null?order.getDiscountAmount():0)
                + (order.getShippingFee()!=null?order.getShippingFee():0)));
    }

    private String generateOrderNumber() {
        return "ORD-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + "-" + String.format("%04d",(int)(Math.random()*10000));
    }

    private OrderItem buildItem(Attributes a, int qty, Order order) {
        return OrderItem.builder().order(order).product(a.getProduct()).productName(a.getProduct().getName())
                .attributes(a).productSku(a.getProduct().getSkuInfo().getSku()).attributesSku(a.getSku().getSku())
                .quantity(qty).unitPrice(a.getPrice()).salePrice(a.getSalePrice())
                .subtotal(a.getSalePrice()*qty).build();
    }

    private List<OrderItem> buildItems(List<CreateOrderRequest.OrderItemRequest> reqs, Order order) {
        return reqs.stream().map(r -> {
            var a = attributesRepository.findAttributesBySku_sku(r.getAttributesSku()).orElseThrow();
            return buildItem(a, r.getQuantity(), order);
        }).toList();
    }

    private Long convertLong(String s) { return Long.valueOf(s); }
}