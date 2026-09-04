package com.ddicg.erp.modules.order.service;

import com.ddicg.erp.core.common.dto.response.PageableData;
import com.ddicg.erp.core.common.dto.response.PagingResponse;
import com.ddicg.erp.core.common.dto.response.Response;
import com.ddicg.erp.core.common.model.embedded.AuditInfo;
import com.ddicg.erp.core.common.model.enums.OrderStatus;
import com.ddicg.erp.core.common.model.enums.PaymentMethod;
import com.ddicg.erp.core.common.model.enums.ShippingMethod;
import com.ddicg.erp.core.common.model.enums.StockStatus;
import com.ddicg.erp.core.common.service.RedisService;
import com.ddicg.erp.core.config.RedisConfiguration.RedisTable;
import com.ddicg.erp.core.common.service.ShippingCalculationService;
import com.ddicg.erp.core.common.util.UUIDv7Generator;
import com.ddicg.erp.core.exception.BusinessException;
import com.ddicg.erp.core.exception.ErrorCode;
import com.ddicg.erp.core.security.SecurityUtil;
import com.ddicg.erp.modules.cart.service.ShoppingCartService;
import com.ddicg.erp.modules.iam.model.Address;
import com.ddicg.erp.modules.iam.model.User;
import com.ddicg.erp.modules.iam.repository.AddressRepository;
import com.ddicg.erp.modules.iam.repository.UserRepository;
import com.ddicg.erp.modules.merchandise.mapper.OrderMapper;
import com.ddicg.erp.modules.merchandise.model.Attributes;
import com.ddicg.erp.modules.merchandise.repository.AttributesRepository;
import com.ddicg.erp.modules.merchandise.repository.ProductRepository;
import com.ddicg.erp.modules.order.dto.OrderDto;
import com.ddicg.erp.modules.order.dto.request.*;
import com.ddicg.erp.modules.order.dto.response.MyOrderDetailResponse;
import com.ddicg.erp.modules.order.dto.response.MyOrderListResponse;
import com.ddicg.erp.modules.order.model.CustomerInfo;
import com.ddicg.erp.modules.order.model.Order;
import com.ddicg.erp.modules.order.model.OrderItem;
import com.ddicg.erp.modules.order.repository.OrderItemRepository;
import com.ddicg.erp.modules.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService implements iOrder {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final AttributesRepository attributesRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final AddressRepository addressRepository;
    private final OrderMapper orderMapper;
    private final SecurityUtil securityUtil;
    private final OrderStatusHandler orderStatusHandler;
    private final OrderHelper orderHelper;
    private final ShippingCalculationService shippingCalculationService;
    private final ShoppingCartService shoppingCartService;
    private final RedisService redisService;

    @Override
    @Transactional
    public Response<OrderDto> createOrder(CreateOrderRequest request) {
        // 1. Validate phương thức nhận hàng và danh sách sản phẩm
        ShippingMethod shippingMethod = validateShippingMethod(request.getShippingMethod());
        List<Attributes> attributesList = fetchAndValidateAttributes(request.getItems());
        String orderNumber = resolveOrderNumber(request.getOrderNumber());

        // 2. Xác định thông tin khách hàng, địa chỉ & cước phí vận chuyển
        Address selectedAddress = resolveShippingAddress(request, shippingMethod);
        CustomerInfo customerInfo = buildCustomerInfo(selectedAddress);
        Double shippingFee = calculateShippingFee(shippingMethod, selectedAddress);

        // 3. Khởi tạo danh sách OrderItem và tính tổng tiền
        List<OrderItem> items = buildOrderItemsFromAttributes(request.getItems(), attributesList);
        double subtotal = items.stream().mapToDouble(i -> i.getSubtotal() != null ? i.getSubtotal() : 0.0).sum();
        double totalAmount = Math.max(0.0, subtotal + shippingFee);

        List<OrderStatus> initialStatus = determineInitialStatuses(request.getPaymentMethod());

        // 4. Khởi tạo Entity Order bằng Builder Pattern
        Order order = Order.builder()
                .orderNumber(orderNumber)
                .shippingMethod(shippingMethod != null ? shippingMethod.name() : null)
                .customerInfo(customerInfo)
                .auditInfo(createInitialAuditInfo())
                .status(initialStatus)
                .currentStatus(initialStatus.get(initialStatus.size() - 1))
                .customerNotes(request.getCustomerNotes())
                .orderItems(items)
                .subtotal(subtotal)
                .shippingFee(shippingFee)
                .discountAmount(0.0)
                .totalAmount(totalAmount)
                .build();
        items.forEach(item -> item.setOrder(order));

        Order saved = orderRepository.save(order);
        log.debug("✅ ORDER_CREATED: {} | Method: {} | ShippingFee: {} | Total: {}",
                saved.getOrderNumber(), saved.getShippingMethod(), saved.getShippingFee(), saved.getTotalAmount());

        // 5. Dọn giỏ hàng nếu đặt từ Cart
        cleanCartIfRequested(request, saved);

        // 6. Lưu Outbox Event duy nhất
        orderHelper.saveOrderCreatedEvent(saved, request);

        // 7. Đặt khóa xử lý đơn hàng trong Redis cố định 1 giây
        setOrderLockWithTtl(saved.getOrderNumber(), 1L);

        return Response.ok(orderMapper.toDto(saved));
    }

    @Override
    @Transactional(readOnly = true)
    public Response<PagingResponse<MyOrderListResponse>> getMyOrdersList(
            OrderStatus status,
            int page,
            int size,
            String sortBy,
            String sortDirection) {

        Long currentUserId = securityUtil.getCurrentUser()
                .map(User::getId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED, "Vui lòng đăng nhập để xem danh sách đơn hàng"));

        int pageIndex = page > 0 ? page - 1 : 0;

        Sort sort = "ASC".equalsIgnoreCase(sortDirection)
                ? Sort.by(Sort.Direction.ASC, sortBy)
                : Sort.by(Sort.Direction.DESC, sortBy);

        Pageable pageable = PageRequest.of(pageIndex, size, sort);
        Page<Order> orderPage = orderRepository.findMyOrdersByStatus(currentUserId, status, pageable);

        List<MyOrderListResponse> contents = orderPage.getContent().stream()
                .map(orderMapper::toMyOrderListResponse)
                .toList();

        return Response.ok(PagingResponse.<MyOrderListResponse>builder()
                .contents(contents)
                .paging(PageableData.from(orderPage))
                .build());
    }

    @Override
    @Transactional(readOnly = true)
    public Response<MyOrderDetailResponse> getMyOrderDetail(String orderNumber) {
        Long currentUserId = securityUtil.getCurrentUser()
                .map(User::getId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED, "Vui lòng đăng nhập để xem chi tiết đơn hàng"));

        Order order = orderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND, "Không tìm thấy đơn hàng: " + orderNumber));

        if (order.getCustomerInfo() == null || order.getCustomerInfo().getCustomerId() == null || !currentUserId.equals(order.getCustomerInfo().getCustomerId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Bạn không có quyền xem đơn hàng này");
        }

        return Response.ok(orderMapper.toMyOrderDetailResponse(order));
    }

    @Override
    public Response<PagingResponse<OrderDto>> searchOrders(OrderSearchRequest r) {
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

    @Override
    public Response<List<OrderDto>> getPendingOrders() {
        return Response.ok(orderRepository.findPendingOrders().stream().map(orderMapper::toDto).toList());
    }

    @Override
    public Response<List<OrderDto>> getInProgressOrders() {
        return Response.ok(orderRepository.findInProgressOrders().stream().map(orderMapper::toDto).toList());
    }

    @Override
    public Response<?> getOrderStatistics(String a, String b) {
        return Response.ok(null);
    }

    @Override
    @Transactional
    public Response<OrderDto> updateShipping(UpdateShippingRequest r) {
        var o = orderRepository.findById(convertLong(r.getOrderId())).orElseThrow();
        if (r.getShippingMethod() != null) o.setShippingMethod(r.getShippingMethod());
        return Response.ok(orderMapper.toDto(orderRepository.save(o)));
    }

    @Override
    @Transactional
    public Response<OrderDto> updateDelivery(UpdateDeliveryRequest r) {
        var o = orderRepository.findById(convertLong(r.getOrderId())).orElseThrow();
        if (r.getEstimatedDeliveryDate() != null) o.setEstimatedDeliveryDate(r.getEstimatedDeliveryDate());
        if (r.getActualDeliveryDate() != null) o.setActualDeliveryDate(r.getActualDeliveryDate());
        return Response.ok(orderMapper.toDto(orderRepository.save(o)));
    }

    @Override
    @Transactional
    public Response<OrderDto> updateAdminNotes(UpdateAdminNotesRequest r) {
        var o = orderRepository.findById(convertLong(r.getOrderId())).orElseThrow();
        if (r.getAdminNotes() != null) o.setAdminNotes(r.getAdminNotes());
        return Response.ok(orderMapper.toDto(orderRepository.save(o)));
    }

    @Override
    @Transactional
    public Response<OrderDto> confirmOrder(ConfirmOrderRequest r) {
        var o = orderRepository.findById(convertLong(r.getOrderId())).orElseThrow();
        var cur = orderStatusHandler.getCurrentStatus(o);
        if (cur == OrderStatus.PENDING) {
            orderStatusHandler.transitionTo(o, OrderStatus.PROCESSING, r.getConfirmationInfo());
        }
        o.setConfirmedAt(LocalDateTime.now());
        o.setConfirmedBy(securityUtil.getCurrentUsername());
        orderHelper.confirmReservation(o.getOrderItems());
        var s = orderRepository.save(o);
        orderHelper.saveOrderStatusChangedEvent(s, cur, OrderStatus.PROCESSING, r.getConfirmationInfo());
        log.debug("🔄 CONFIRMED_TO_PROCESSING: {}", s.getOrderNumber());
        return Response.ok(orderMapper.toDto(s));
    }

    @Override
    @Transactional
    public Response<OrderDto> cancelOrder(CancelOrderRequest r) {
        var o = orderRepository.findById(convertLong(r.getOrderId())).orElseThrow();
        var cur = orderStatusHandler.getCurrentStatus(o);
        var reason = r.getCancellationReason() != null ? r.getCancellationReason() : "";
        if (!orderStatusHandler.isValidTransition(cur, OrderStatus.CANCELLED))
            throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION, "");
        orderStatusHandler.transitionTo(o, OrderStatus.CANCELLED, reason);
        o.setCancellationReason(reason);
        o.setCancelledAt(LocalDateTime.now());
        o.setCancelledBy(securityUtil.getCurrentUsername());
        orderHelper.releaseInventory(o.getOrderItems());
        var wasPaid = cur == OrderStatus.CONFIRMED || cur == OrderStatus.PROCESSING;
        var s = orderRepository.save(o);
        orderHelper.saveOrderStatusChangedEvent(s, cur, OrderStatus.CANCELLED, reason);
        orderHelper.saveOrderCancelledEvent(s, reason, wasPaid);
        log.debug("🔄 CANCELLED: {} wasPaid={}", s.getOrderNumber(), wasPaid);
        return Response.ok(orderMapper.toDto(s));
    }

    @Override
    @Transactional
    public Response<OrderDto> completeOrder(CompleteOrderRequest r) {
        var o = orderRepository.findById(convertLong(r.getOrderId())).orElseThrow();
        var cur = orderStatusHandler.getCurrentStatus(o);
        orderStatusHandler.transitionTo(o, OrderStatus.COMPLETED, "");
        o.setCompletedAt(LocalDateTime.now());
        var s = orderRepository.save(o);
        orderHelper.saveOrderStatusChangedEvent(s, cur, OrderStatus.COMPLETED, "");
        log.debug("🔄 COMPLETED: {}", s.getOrderNumber());
        return Response.ok(orderMapper.toDto(s));
    }

    @Override
    @Transactional
    public Response<OrderDto> processOrder(ProcessOrderRequest r) {
        var o = orderRepository.findById(convertLong(r.getOrderId())).orElseThrow();
        var cur = orderStatusHandler.getCurrentStatus(o);
        orderStatusHandler.transitionTo(o, OrderStatus.PROCESSING, r.getNote());
        var s = orderRepository.save(o);
        orderHelper.saveOrderStatusChangedEvent(s, cur, OrderStatus.PROCESSING, r.getNote());
        log.debug("🔄 PROCESSING: {}", s.getOrderNumber());
        return Response.ok(orderMapper.toDto(s));
    }

    @Override
    @Transactional
    public Response<OrderDto> shipOrder(ShipOrderRequest r) {
        var o = orderRepository.findById(convertLong(r.getOrderId())).orElseThrow();
        var cur = orderStatusHandler.getCurrentStatus(o);
        orderStatusHandler.transitionTo(o, OrderStatus.SHIPPING, r.getNote());
        o.setShipperId(r.getShipperId());
        o.setShipperName(r.getShipperName());
        o.setShipperPhone(r.getShipperPhone());
        o.setDeliveryToken(UUID.randomUUID().toString());
        var s = orderRepository.save(o);
        orderHelper.saveOrderStatusChangedEvent(s, cur, OrderStatus.SHIPPING, "shipper: " + r.getShipperName());
        log.debug("🔄 SHIPPING: {}", s.getOrderNumber());
        return Response.ok(orderMapper.toDto(s));
    }

    @Override
    @Transactional
    public Response<OrderDto> markDelayed(DelayOrderRequest r) {
        var o = orderRepository.findById(convertLong(r.getOrderId())).orElseThrow();
        var cur = orderStatusHandler.getCurrentStatus(o);
        orderStatusHandler.transitionTo(o, OrderStatus.DELAYED, r.getReason());
        var s = orderRepository.save(o);
        orderHelper.saveOrderStatusChangedEvent(s, cur, OrderStatus.DELAYED, r.getReason());
        log.debug("🔄 DELAYED: {}", s.getOrderNumber());
        return Response.ok(orderMapper.toDto(s));
    }

    @Override
    @Transactional
    public Response<OrderDto> deliverOrder(DeliverOrderRequest r) {
        var o = orderRepository.findById(convertLong(r.getOrderId())).orElseThrow();
        var cur = orderStatusHandler.getCurrentStatus(o);
        orderStatusHandler.transitionTo(o, OrderStatus.DELIVERED, r.getNote());
        o.setActualDeliveryDate(LocalDateTime.now());
        var s = orderRepository.save(o);
        orderHelper.saveOrderStatusChangedEvent(s, cur, OrderStatus.DELIVERED, "giao thành công");
        log.debug("🔄 DELIVERED: {}", s.getOrderNumber());
        return Response.ok(orderMapper.toDto(s));
    }

    @Override
    @Transactional
    public Response<OrderDto> readyForPickup(ReadyForPickupRequest r) {
        var o = orderRepository.findById(convertLong(r.getOrderId())).orElseThrow();
        var cur = orderStatusHandler.getCurrentStatus(o);
        orderStatusHandler.transitionTo(o, OrderStatus.READY_FOR_PICKUP, r.getNote());
        var s = orderRepository.save(o);
        orderHelper.saveOrderStatusChangedEvent(s, cur, OrderStatus.READY_FOR_PICKUP, r.getNote());
        log.debug("🔄 READY_FOR_PICKUP: {}", s.getOrderNumber());
        return Response.ok(orderMapper.toDto(s));
    }

    @Override
    @Transactional
    public Response<OrderDto> pickupOrder(PickupOrderRequest r) {
        var o = orderRepository.findById(convertLong(r.getOrderId())).orElseThrow();
        var cur = orderStatusHandler.getCurrentStatus(o);
        orderStatusHandler.transitionTo(o, OrderStatus.DELIVERED, r.getNote());
        var s = orderRepository.save(o);
        orderHelper.saveOrderStatusChangedEvent(s, cur, OrderStatus.DELIVERED, "khách lấy tại shop");
        log.debug("🔄 PICKUP: {}", s.getOrderNumber());
        return Response.ok(orderMapper.toDto(s));
    }

    @Override
    @Transactional
    public Response<OrderDto> returnOrder(ReturnOrderRequest r) {
        var o = orderRepository.findById(convertLong(r.getOrderId())).orElseThrow();
        var cur = orderStatusHandler.getCurrentStatus(o);
        orderStatusHandler.transitionTo(o, OrderStatus.RETURNING, r.getReason());
        var s = orderRepository.save(o);
        orderHelper.saveOrderStatusChangedEvent(s, cur, OrderStatus.RETURNING, r.getReason());
        log.debug("🔄 RETURNING: {}", s.getOrderNumber());
        return Response.ok(orderMapper.toDto(s));
    }

    @Override
    @Transactional
    public Response<OrderDto> confirmReturn(ConfirmReturnRequest r) {
        var o = orderRepository.findById(convertLong(r.getOrderId())).orElseThrow();
        var cur = orderStatusHandler.getCurrentStatus(o);
        orderStatusHandler.transitionTo(o, OrderStatus.RETURNED, r.getNote());
        var s = orderRepository.save(o);
        orderHelper.saveOrderStatusChangedEvent(s, cur, OrderStatus.RETURNED, r.getCondition());
        log.debug("🔄 RETURNED: {}", s.getOrderNumber());
        return Response.ok(orderMapper.toDto(s));
    }

    @Override
    @Transactional
    public Response<OrderDto> refundOrder(RefundOrderRequest r) {
        var o = orderRepository.findById(convertLong(r.getOrderId())).orElseThrow();
        var cur = orderStatusHandler.getCurrentStatus(o);
        orderStatusHandler.transitionTo(o, OrderStatus.REFUNDED, r.getNote());
        orderHelper.releaseInventory(o.getOrderItems());
        var s = orderRepository.save(o);
        orderHelper.saveOrderStatusChangedEvent(s, cur, OrderStatus.REFUNDED, "hoàn tiền");
        log.debug("🔄 REFUNDED: {}", s.getOrderNumber());
        return Response.ok(orderMapper.toDto(s));
    }

    @Override
    @Transactional
    public Response<OrderDto> processPayment(PaymentCallbackRequest r) {
        var o = orderRepository.findByOrderNumber(r.getOrderNumber()).orElseThrow();
        var cur = orderStatusHandler.getCurrentStatus(o);
        if (cur != OrderStatus.WAITING_PAYMENT && cur != OrderStatus.PENDING)
            throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION, "");
        if ("SUCCESS".equalsIgnoreCase(r.getStatus())) {
            orderStatusHandler.transitionTo(o, OrderStatus.PROCESSING, "");
            o.setConfirmedAt(LocalDateTime.now());
            orderHelper.confirmReservation(o.getOrderItems());
            var s = orderRepository.save(o);
            orderHelper.saveOrderStatusChangedEvent(s, cur, OrderStatus.PROCESSING, "payment OK");
            redisService.delete(RedisTable.LOCK_ORDER, s.getOrderNumber());
            log.debug("🔄 PAYMENT_SUCCESS: {}", s.getOrderNumber());
            return Response.ok(orderMapper.toDto(s));
        } else {
            orderStatusHandler.transitionTo(o, OrderStatus.FAILED, "");
            orderHelper.releaseInventory(o.getOrderItems());
            var s = orderRepository.save(o);
            orderHelper.saveOrderStatusChangedEvent(s, cur, OrderStatus.FAILED, "payment FAILED");
            redisService.delete(RedisTable.LOCK_ORDER, s.getOrderNumber());
            log.debug("🔄 PAYMENT_FAILED: {}", s.getOrderNumber());
            return Response.ok(orderMapper.toDto(s));
        }
    }

    @Override
    @Transactional
    public void setStatus(String orderNumber, OrderStatus status) {
        var o = orderRepository.findByOrderNumber(orderNumber).orElseThrow();
        var cur = orderStatusHandler.getCurrentStatus(o);
        orderStatusHandler.transitionTo(o, status, "");
        var saved = orderRepository.save(o);
        orderHelper.saveOrderStatusChangedEvent(saved, cur, status, "");
    }

    @Transactional
    public void handlePaymentTimeout(String orderNumber) {
        Order order = orderRepository.findByOrderNumber(orderNumber).orElse(null);
        if (order == null) return;
        OrderStatus cur = orderStatusHandler.getCurrentStatus(order);
        if (cur == OrderStatus.WAITING_PAYMENT || cur == OrderStatus.PENDING) {
            orderStatusHandler.transitionTo(order, OrderStatus.FAILED, "Timeout chờ phản hồi thanh toán");
            orderHelper.releaseInventory(order.getOrderItems());
            Order saved = orderRepository.save(order);
            orderHelper.saveOrderStatusChangedEvent(saved, cur, OrderStatus.FAILED, "Payment timeout fallback", "system");
            log.debug("⏱️ TIMEOUT_HANDLED: Order {} transitioned to FAILED and resources released", orderNumber);
        }
    }

    private ShippingMethod validateShippingMethod(ShippingMethod shippingMethod) {
        if (shippingMethod == null) {
            throw new BusinessException("Phương thức nhận hàng (shippingMethod) không được để trống. Vui lòng chọn 'DELIVERY' hoặc 'PICKUP'");
        }
        return shippingMethod;
    }

    private String resolveOrderNumber(String customOrderNumber) {
        if (customOrderNumber != null && !customOrderNumber.trim().isEmpty()) {
            String trimmed = customOrderNumber.trim();
            if (orderRepository.existsByOrderNumber(trimmed)) {
                throw new BusinessException(ErrorCode.INVALID_REQUEST, "Mã đơn hàng '" + trimmed + "' đã tồn tại trong hệ thống");
            }
            return trimmed;
        }
        return UUIDv7Generator.generate().toString();
    }

    private Address resolveShippingAddress(CreateOrderRequest request, ShippingMethod shippingMethod) {
        Address selectedAddress = null;
        if (StringUtils.hasText(request.getAddressSku())) {
            selectedAddress = addressRepository.findBySku(request.getAddressSku()).orElse(null);
        }
        if (shippingMethod == ShippingMethod.DELIVERY && selectedAddress == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Phương thức giao hàng tận nơi yêu cầu mã SKU địa chỉ hợp lệ");
        }
        return selectedAddress;
    }

    private CustomerInfo buildCustomerInfo(Address selectedAddress) {
        Optional<User> currentUserOpt = securityUtil.getCurrentUser();
        Long customerId = null;
        String customerName = (selectedAddress != null) ? selectedAddress.getRecipientName() : null;
        String customerEmail = null;
        String customerPhone = (selectedAddress != null) ? selectedAddress.getPhoneNumber() : null;
        String shippingAddress = (selectedAddress != null) ? selectedAddress.getAddress() : "Nhận tại Kho Tổng: Xã Định Hòa, Huyện Yên Định, Tỉnh Thanh Hóa";

        if (currentUserOpt.isPresent()) {
            User user = currentUserOpt.get();
            customerId = user.getId();
            customerEmail = user.getEmail();
            if (!StringUtils.hasText(customerName)) {
                customerName = StringUtils.hasText(user.getFullName()) ? user.getFullName() : user.getName();
            }
            if (!StringUtils.hasText(customerPhone)) {
                customerPhone = user.getPhoneNumber();
            }
        }

        return CustomerInfo.builder()
                .customerId(customerId)
                .customerName(customerName)
                .customerEmail(customerEmail)
                .customerPhone(customerPhone)
                .shippingAddress(shippingAddress)
                .build();
    }

    private Double calculateShippingFee(ShippingMethod shippingMethod, Address selectedAddress) {
        if (shippingMethod == ShippingMethod.DELIVERY && selectedAddress != null) {
            return shippingCalculationService.calculateShippingFee(
                    selectedAddress.getLatitude(),
                    selectedAddress.getLongitude(),
                    selectedAddress.getAddress()
            );
        }
        return 0.0;
    }

    private AuditInfo createInitialAuditInfo() {
        AuditInfo auditInfo = new AuditInfo();
        String username = securityUtil.getCurrentUsername() != null ? securityUtil.getCurrentUsername() : "SYSTEM";
        auditInfo.addUpdateEntry("Tạo đơn hàng", username);
        return auditInfo;
    }

    private void cleanCartIfRequested(CreateOrderRequest request, Order order) {
        if (request.isFromCart()) {
            try {
                List<String> orderedSkus = request.getItems().stream()
                        .map(CreateOrderRequest.OrderItemRequest::getAttributesSku)
                        .toList();
                shoppingCartService.removeItems(orderedSkus);
                String custId = (order.getCustomerInfo() != null && order.getCustomerInfo().getCustomerId() != null)
                        ? String.valueOf(order.getCustomerInfo().getCustomerId())
                        : "anonymous";
                log.info("🛒 CART_CLEANED_AFTER_ORDER: User: {} | Cleaned SKUs: {}", custId, orderedSkus);
            } catch (Exception e) {
                log.warn("⚠️ Không thể dọn giỏ hàng sau khi đặt: {}", e.getMessage());
            }
        }
    }

    private List<OrderStatus> determineInitialStatuses(PaymentMethod paymentMethod) {
        List<OrderStatus> statuses = new ArrayList<>();
        statuses.add(OrderStatus.PENDING);
        if (paymentMethod == PaymentMethod.COD) {
            statuses.add(OrderStatus.PROCESSING);
        } else if (paymentMethod != null) {
            statuses.add(OrderStatus.WAITING_PAYMENT);
        }
        return statuses;
    }

    private List<Attributes> fetchAndValidateAttributes(List<CreateOrderRequest.OrderItemRequest> itemRequests) {
        if (itemRequests == null || itemRequests.isEmpty()) {
            throw new BusinessException(ErrorCode.ATTRIBUTES_OUT_OF_STOCK, "Danh sách sản phẩm không được rỗng");
        }

        for (CreateOrderRequest.OrderItemRequest req : itemRequests) {
            if (req == null || req.getAttributesSku() == null || req.getAttributesSku().isBlank()) {
                throw new BusinessException(ErrorCode.ATTRIBUTES_OUT_OF_STOCK, "Mã SKU sản phẩm không được để trống");
            }
            if (req.getQuantity() == null || req.getQuantity() <= 0) {
                throw new BusinessException(ErrorCode.ATTRIBUTES_OUT_OF_STOCK, "Số lượng sản phẩm phải lớn hơn 0");
            }
        }

        List<String> skus = itemRequests.stream()
                .map(CreateOrderRequest.OrderItemRequest::getAttributesSku)
                .filter(Objects::nonNull)
                .toList();

        List<Attributes> fetchedAttributes = attributesRepository.findAllBySku_skuIn(skus);

        Set<String> foundSkus = fetchedAttributes.stream()
                .map(a -> (a.getSku() != null) ? a.getSku().getSku() : null)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        boolean missingSku = skus.stream().anyMatch(sku -> !foundSkus.contains(sku));
        if (missingSku) {
            throw new BusinessException(ErrorCode.ATTRIBUTES_OUT_OF_STOCK, "Một hoặc nhiều mã SKU không tồn tại");
        }

        for (Attributes attr : fetchedAttributes) {
            if (attr.getStatusProduct() != StockStatus.AVAILABLE) {
                throw new BusinessException(ErrorCode.ATTRIBUTES_OUT_OF_STOCK,
                        "Sản phẩm SKU " + (attr.getSku() != null ? attr.getSku().getSku() : "") + " hiện không khả dụng");
            }
        }

        return fetchedAttributes;
    }

    private List<OrderItem> buildOrderItemsFromAttributes(
            List<CreateOrderRequest.OrderItemRequest> itemRequests,
            List<Attributes> attributesList) {
        Map<String, Attributes> attrMap = attributesList.stream()
                .collect(Collectors.toMap(a -> a.getSku().getSku(), a -> a, (existing, replacement) -> existing));

        return itemRequests.stream().map(req -> {
            Attributes attr = attrMap.get(req.getAttributesSku());
            return buildItem(attr, req.getQuantity());
        }).toList();
    }

    private void setOrderLockWithTtl(String orderNumber, long timeoutSeconds) {
        redisService.setValueWithExpiry(RedisTable.LOCK_ORDER, orderNumber, "PROCESSING", timeoutSeconds, TimeUnit.SECONDS);
    }

    private String generateOrderNumber() {
        return "ORD-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + "-" + String.format("%04d", (int) (Math.random() * 10000));
    }

    private OrderItem buildItem(Attributes a, int qty) {
        double unitPrice = a.getPrice();
        double salePrice = a.getSalePrice() > 0 ? a.getSalePrice() : unitPrice;
        return OrderItem.builder()
                .attributesId(a.getId())
                .attributesSku(a.getSku().getSku())
                .quantity(qty)
                .unitPrice(unitPrice)
                .salePrice(salePrice)
                .costPrice(a.getCostPrice())
                .variantOptions(a.getVariantOptions() != null ? new ArrayList<>(a.getVariantOptions()) : new ArrayList<>())
                .subtotal(salePrice * qty)
                .build();
    }

    private Long convertLong(String s) {
        return Long.valueOf(s);
    }
}
