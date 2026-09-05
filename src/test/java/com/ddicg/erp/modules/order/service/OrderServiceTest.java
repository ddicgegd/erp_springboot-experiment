package com.ddicg.erp.modules.order.service;

import com.ddicg.erp.core.common.dto.response.Response;
import com.ddicg.erp.core.common.model.embedded.SkuInfo;
import com.ddicg.erp.core.common.model.enums.PaymentMethod;
import com.ddicg.erp.core.common.model.enums.ShippingMethod;
import com.ddicg.erp.core.config.RedisConfiguration.RedisTable;
import com.ddicg.erp.core.exception.BusinessException;
import com.ddicg.erp.core.security.SecurityUtil;
import com.ddicg.erp.modules.merchandise.mapper.OrderMapper;
import com.ddicg.erp.modules.merchandise.model.Attributes;
import com.ddicg.erp.modules.merchandise.repository.AttributesRepository;
import com.ddicg.erp.modules.merchandise.repository.ProductRepository;
import com.ddicg.erp.modules.order.dto.OrderDto;
import com.ddicg.erp.modules.order.dto.request.CreateOrderRequest;
import com.ddicg.erp.modules.order.model.Order;
import com.ddicg.erp.modules.order.repository.OrderItemRepository;
import com.ddicg.erp.modules.order.repository.OrderRepository;
import com.ddicg.erp.modules.order.service.discount.DiscountEvaluationResult;
import com.ddicg.erp.modules.order.service.discount.OrderDiscountContext;
import com.ddicg.erp.modules.order.service.discount.OrderDiscountProcessor;
import com.ddicg.erp.modules.order.service.discount.VoucherReservationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OrderItemRepository orderItemRepository;
    @Mock
    private AttributesRepository attributesRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private OrderMapper orderMapper;
    @Mock
    private SecurityUtil securityUtil;
    @Mock
    private com.ddicg.erp.modules.iam.repository.UserRepository userRepository;
    @Mock
    private com.ddicg.erp.modules.iam.repository.AddressRepository addressRepository;
    @Mock
    private OrderStatusHandler orderStatusHandler;
    @Mock
    private OrderHelper orderHelper;
    @Mock
    private com.ddicg.erp.core.common.service.ShippingCalculationService shippingCalculationService;
    @Mock
    private com.ddicg.erp.modules.cart.service.ShoppingCartService shoppingCartService;
    @Mock
    private com.ddicg.erp.core.common.service.RedisService redisService;
    @Mock
    private OrderDiscountProcessor orderDiscountProcessor;
    @Mock
    private VoucherReservationService voucherReservationService;

    @InjectMocks
    private OrderService orderService;

    private Attributes sampleAttr;

    @BeforeEach
    void setUp() {
        sampleAttr = Attributes.builder()
                .sku(SkuInfo.builder().sku("SKU-1001").build())
                .name("Sample Red Shirt")
                .price(200000.0)
                .salePrice(180000.0)
                .costPrice(100000.0)
                .build();

        // Default stub: no-discount result so all existing tests are unaffected
        when(orderDiscountProcessor.evaluateDiscount(any(OrderDiscountContext.class)))
                .thenReturn(DiscountEvaluationResult.builder().build());
    }

    @Test
    @DisplayName("Tạo đơn hàng thành công, tự động lấy CustomerInfo từ Token và Address")
    void testCreateOrder_Success() {
        CreateOrderRequest request = CreateOrderRequest.builder()
                .addressSku("ADDR-1001")
                .shippingMethod(ShippingMethod.DELIVERY)
                .paymentMethod(PaymentMethod.COD)
                .items(List.of(
                        CreateOrderRequest.OrderItemRequest.builder()
                                .attributesSku("SKU-1001")
                                .quantity(2)
                                .build()
                ))
                .build();

        com.ddicg.erp.modules.iam.model.User mockUser = new com.ddicg.erp.modules.iam.model.User();
        mockUser.setId(101L);
        mockUser.setFullName("Nguyen Van A");
        mockUser.setEmail("a@example.com");

        com.ddicg.erp.modules.iam.model.Address mockAddress = new com.ddicg.erp.modules.iam.model.Address();
        mockAddress.setAddress("123 Ha Noi");
        mockAddress.setSku("ADDR-1001");

        when(securityUtil.getCurrentUser()).thenReturn(Optional.of(mockUser));
        when(addressRepository.findBySku("ADDR-1001")).thenReturn(Optional.of(mockAddress));
        when(shippingCalculationService.calculateShippingFee(any(), any(), any())).thenReturn(26000.0);

        when(attributesRepository.findAllBySku_skuIn(List.of("SKU-1001"))).thenReturn(List.of(sampleAttr));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order o = invocation.getArgument(0);
            o.setId(1L);
            return o;
        });
        when(orderMapper.toDto(any(Order.class))).thenAnswer(invocation -> {
            Order o = invocation.getArgument(0);
            return OrderDto.builder()
                    .id(o.getId())
                    .orderNumber(o.getOrderNumber())
                    .customerName(o.getCustomerInfo().getCustomerName())
                    .customerEmail(o.getCustomerInfo().getCustomerEmail())
                    .shippingFee(o.getShippingFee())
                    .totalAmount(o.getTotalAmount())
                    .build();
        });

        Response<OrderDto> response = orderService.createOrder(request);

        assertNotNull(response);
        assertNotNull(response.getData());
        assertEquals("Nguyen Van A", response.getData().getCustomerName());
        assertEquals("a@example.com", response.getData().getCustomerEmail());
        assertEquals(26000.0, response.getData().getShippingFee());

        verify(orderRepository).save(any(Order.class));
        verify(orderHelper).saveOrderCreatedEvent(any(Order.class), eq(request));
    }

    @Test
    @DisplayName("Tạo đơn hàng PICKUP tại kho -> Phí ship luôn bằng 0đ và không bắt buộc addressSku")
    void testCreateOrder_PickupAtWarehouse_ZeroShippingFee() {
        CreateOrderRequest request = CreateOrderRequest.builder()
                .shippingMethod(ShippingMethod.PICKUP)
                .paymentMethod(PaymentMethod.COD)
                .items(List.of(
                        CreateOrderRequest.OrderItemRequest.builder()
                                .attributesSku("SKU-1001")
                                .quantity(1)
                                .build()
                ))
                .build();

        com.ddicg.erp.modules.iam.model.User mockUser = new com.ddicg.erp.modules.iam.model.User();
        mockUser.setId(101L);
        mockUser.setFullName("Nguyen Van A");

        when(securityUtil.getCurrentUser()).thenReturn(Optional.of(mockUser));
        when(attributesRepository.findAllBySku_skuIn(List.of("SKU-1001"))).thenReturn(List.of(sampleAttr));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order o = invocation.getArgument(0);
            o.setId(2L);
            return o;
        });
        when(orderMapper.toDto(any(Order.class))).thenAnswer(invocation -> {
            Order o = invocation.getArgument(0);
            return OrderDto.builder()
                    .id(o.getId())
                    .shippingFee(o.getShippingFee())
                    .totalAmount(o.getTotalAmount())
                    .build();
        });

        Response<OrderDto> response = orderService.createOrder(request);

        assertNotNull(response);
        assertEquals(0.0, response.getData().getShippingFee());
        assertEquals(180000.0, response.getData().getTotalAmount()); // 1 item giá 180k
    }

    @Test
    @DisplayName("Tạo đơn hàng DELIVERY thất bại khi addressSku bị để trống")
    void testCreateOrder_MissingAddressSku_ThrowsException() {
        CreateOrderRequest request = CreateOrderRequest.builder()
                .addressSku(null)
                .shippingMethod(ShippingMethod.DELIVERY)
                .paymentMethod(PaymentMethod.COD)
                .items(List.of(
                        CreateOrderRequest.OrderItemRequest.builder()
                                .attributesSku("SKU-1001")
                                .quantity(1)
                                .build()
                ))
                .build();

        assertThrows(BusinessException.class, () -> orderService.createOrder(request));
    }

    @Test
    @DisplayName("Tạo đơn hàng thất bại khi addressSku không tồn tại trong DB")
    void testCreateOrder_AddressNotFound_ThrowsException() {
        CreateOrderRequest request = CreateOrderRequest.builder()
                .addressSku("ADDR-NOT-FOUND")
                .paymentMethod(PaymentMethod.COD)
                .items(List.of(
                        CreateOrderRequest.OrderItemRequest.builder()
                                .attributesSku("SKU-1001")
                                .quantity(1)
                                .build()
                ))
                .build();

        when(addressRepository.findBySku("ADDR-NOT-FOUND")).thenReturn(Optional.empty());

        assertThrows(BusinessException.class, () -> orderService.createOrder(request));
    }

    @Test
    @DisplayName("Tạo đơn hàng thất bại khi SKU không tồn tại trong DB")
    void testCreateOrder_MissingSku_ThrowsException() {
        CreateOrderRequest request = CreateOrderRequest.builder()
                .addressSku("ADDR-1001")
                .paymentMethod(PaymentMethod.COD)
                .items(List.of(
                        CreateOrderRequest.OrderItemRequest.builder()
                                .attributesSku("SKU-9999")
                                .quantity(1)
                                .build()
                ))
                .build();

        com.ddicg.erp.modules.iam.model.Address mockAddress = new com.ddicg.erp.modules.iam.model.Address();
        mockAddress.setSku("ADDR-1001");
        when(addressRepository.findBySku("ADDR-1001")).thenReturn(Optional.of(mockAddress));
        when(attributesRepository.findAllBySku_skuIn(List.of("SKU-9999"))).thenReturn(List.of());

        assertThrows(BusinessException.class, () -> orderService.createOrder(request));
    }

    @Test
    @DisplayName("Tạo đơn hàng thất bại khi số lượng <= 0")
    void testCreateOrder_InvalidQuantity_ThrowsException() {
        CreateOrderRequest request = CreateOrderRequest.builder()
                .addressSku("ADDR-1001")
                .paymentMethod(PaymentMethod.COD)
                .items(List.of(
                        CreateOrderRequest.OrderItemRequest.builder()
                                .attributesSku("SKU-1001")
                                .quantity(0)
                                .build()
                ))
                .build();

        com.ddicg.erp.modules.iam.model.Address mockAddress = new com.ddicg.erp.modules.iam.model.Address();
        mockAddress.setSku("ADDR-1001");
        when(addressRepository.findBySku("ADDR-1001")).thenReturn(Optional.of(mockAddress));

        assertThrows(BusinessException.class, () -> orderService.createOrder(request));
    }

    @Test
    @DisplayName("Tạo đơn hàng thất bại khi SKU hết hàng / không khả dụng")
    void testCreateOrder_UnavailableStock_ThrowsException() {
        Attributes outOfStockAttr = Attributes.builder()
                .sku(SkuInfo.builder().sku("SKU-OUT").build())
                .statusProduct(com.ddicg.erp.core.common.model.enums.StockStatus.UNAVAILABLE)
                .build();

        CreateOrderRequest request = CreateOrderRequest.builder()
                .addressSku("ADDR-1001")
                .paymentMethod(PaymentMethod.COD)
                .items(List.of(
                        CreateOrderRequest.OrderItemRequest.builder()
                                .attributesSku("SKU-OUT")
                                .quantity(1)
                                .build()
                ))
                .build();

        com.ddicg.erp.modules.iam.model.Address mockAddress = new com.ddicg.erp.modules.iam.model.Address();
        mockAddress.setSku("ADDR-1001");
        when(addressRepository.findBySku("ADDR-1001")).thenReturn(Optional.of(mockAddress));
        when(attributesRepository.findAllBySku_skuIn(List.of("SKU-OUT"))).thenReturn(List.of(outOfStockAttr));

        assertThrows(BusinessException.class, () -> orderService.createOrder(request));
    }

    @Test
    @DisplayName("Tạo đơn hàng thành công với OrderNumber dạng UUIDv7")
    void testCreateOrder_UUIDv7Generated() {
        CreateOrderRequest request = CreateOrderRequest.builder()
                .addressSku("ADDR-1001")
                .paymentMethod(PaymentMethod.COD)
                .shippingMethod(com.ddicg.erp.core.common.model.enums.ShippingMethod.DELIVERY)
                .items(List.of(
                        CreateOrderRequest.OrderItemRequest.builder()
                                .attributesSku("SKU-1001")
                                .quantity(1)
                                .build()
                ))
                .build();

        com.ddicg.erp.modules.iam.model.Address mockAddress = new com.ddicg.erp.modules.iam.model.Address();
        mockAddress.setSku("ADDR-1001");
        when(addressRepository.findBySku("ADDR-1001")).thenReturn(Optional.of(mockAddress));
        when(attributesRepository.findAllBySku_skuIn(List.of("SKU-1001"))).thenReturn(List.of(sampleAttr));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(orderMapper.toDto(any(Order.class))).thenAnswer(invocation -> {
            Order o = invocation.getArgument(0);
            return OrderDto.builder()
                    .orderNumber(o.getOrderNumber())
                    .build();
        });

        Response<OrderDto> response = orderService.createOrder(request);

        assertNotNull(response);
        assertNotNull(response.getData());
        String orderNumber = response.getData().getOrderNumber();
        assertNotNull(orderNumber);
        // Verify UUID format (36 chars with dashes)
        assertEquals(36, orderNumber.length());
        assertDoesNotThrow(() -> java.util.UUID.fromString(orderNumber));
    }

    @Test
    @DisplayName("Lấy danh sách đơn hàng tóm tắt thành công với status bắt buộc và page 1-indexed")
    void testGetMyOrdersList_Success() {
        com.ddicg.erp.modules.iam.model.User mockUser = new com.ddicg.erp.modules.iam.model.User();
        mockUser.setId(101L);
        when(securityUtil.getCurrentUser()).thenReturn(Optional.of(mockUser));

        Order order = new Order();
        order.setId(1L);
        order.setOrderNumber("ORD-1001");
        order.setTotalAmount(350000.0);
        order.setCurrentStatus(com.ddicg.erp.core.common.model.enums.OrderStatus.PROCESSING);

        org.springframework.data.domain.Page<Order> mockPage = new org.springframework.data.domain.PageImpl<>(
                List.of(order),
                org.springframework.data.domain.PageRequest.of(0, 20),
                1
        );

        when(orderRepository.findMyOrdersByStatus(eq(101L), eq(com.ddicg.erp.core.common.model.enums.OrderStatus.PROCESSING), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(mockPage);

        when(orderMapper.toMyOrderListResponse(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            return com.ddicg.erp.modules.order.dto.response.MyOrderListResponse.builder()
                    .orderNumber(o.getOrderNumber())
                    .totalAmount(o.getTotalAmount())
                    .currentStatus(o.getCurrentStatus())
                    .productNames(List.of("SKU-1001"))
                    .build();
        });

        var response = orderService.getMyOrdersList(
                com.ddicg.erp.core.common.model.enums.OrderStatus.PROCESSING,
                1,
                20,
                "auditInfo.createdAt",
                "DESC"
        );

        assertNotNull(response);
        assertNotNull(response.getData());
        assertEquals(1, response.getData().getContents().size());
        assertEquals("ORD-1001", response.getData().getContents().get(0).getOrderNumber());
        assertEquals(350000.0, response.getData().getContents().get(0).getTotalAmount());
        assertEquals(com.ddicg.erp.core.common.model.enums.OrderStatus.PROCESSING, response.getData().getContents().get(0).getCurrentStatus());
    }

    @Test
    @DisplayName("Lấy chi tiết đơn hàng thành công khi là chủ sở hữu đơn hàng")
    void testGetMyOrderDetail_Success() {
        com.ddicg.erp.modules.iam.model.User mockUser = new com.ddicg.erp.modules.iam.model.User();
        mockUser.setId(101L);
        when(securityUtil.getCurrentUser()).thenReturn(Optional.of(mockUser));

        Order order = new Order();
        order.setId(1L);
        order.setOrderNumber("ORD-1001");
        order.setCustomerInfo(com.ddicg.erp.modules.order.model.CustomerInfo.builder().customerId(101L).build());

        when(orderRepository.findByOrderNumber("ORD-1001")).thenReturn(Optional.of(order));
        when(orderMapper.toMyOrderDetailResponse(order)).thenReturn(
                com.ddicg.erp.modules.order.dto.response.MyOrderDetailResponse.builder()
                        .orderNumber("ORD-1001")
                        .customerId(101L)
                        .build()
        );

        var response = orderService.getMyOrderDetail("ORD-1001");

        assertNotNull(response);
        assertNotNull(response.getData());
        assertEquals("ORD-1001", response.getData().getOrderNumber());
        assertEquals(101L, response.getData().getCustomerId());
    }

    @Test
    @DisplayName("Lấy chi tiết đơn hàng thất bại khi không phải chủ sở hữu (FORBIDDEN)")
    void testGetMyOrderDetail_ForbiddenWhenNotOwner() {
        com.ddicg.erp.modules.iam.model.User mockUser = new com.ddicg.erp.modules.iam.model.User();
        mockUser.setId(101L);
        when(securityUtil.getCurrentUser()).thenReturn(Optional.of(mockUser));

        Order order = new Order();
        order.setId(1L);
        order.setOrderNumber("ORD-1001");
        order.setCustomerInfo(com.ddicg.erp.modules.order.model.CustomerInfo.builder().customerId(999L).build());

        when(orderRepository.findByOrderNumber("ORD-1001")).thenReturn(Optional.of(order));

        BusinessException ex = assertThrows(BusinessException.class, () -> orderService.getMyOrderDetail("ORD-1001"));
        assertEquals(com.ddicg.erp.core.exception.ErrorCode.FORBIDDEN, ex.getErrorCode());
    }

    @Test
    @DisplayName("Tạo đơn hàng với orderNumber tùy chỉnh thành công")
    void testCreateOrder_CustomOrderNumber_Success() {
        CreateOrderRequest request = CreateOrderRequest.builder()
                .orderNumber("CUSTOM-ORD-001")
                .addressSku("ADDR-1001")
                .shippingMethod(ShippingMethod.PICKUP)
                .paymentMethod(PaymentMethod.COD)
                .items(List.of(
                        CreateOrderRequest.OrderItemRequest.builder()
                                .attributesSku("SKU-1001")
                                .quantity(1)
                                .build()
                ))
                .build();

        when(orderRepository.existsByOrderNumber("CUSTOM-ORD-001")).thenReturn(false);
        when(attributesRepository.findAllBySku_skuIn(List.of("SKU-1001"))).thenReturn(List.of(sampleAttr));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(orderMapper.toDto(any(Order.class))).thenAnswer(invocation -> {
            Order o = invocation.getArgument(0);
            return OrderDto.builder()
                    .orderNumber(o.getOrderNumber())
                    .build();
        });

        Response<OrderDto> response = orderService.createOrder(request);

        assertNotNull(response);
        assertNotNull(response.getData());
        assertEquals("CUSTOM-ORD-001", response.getData().getOrderNumber());
    }

    @Test
    @DisplayName("Tạo đơn hàng với orderNumber tùy chỉnh đã tồn tại ném BusinessException")
    void testCreateOrder_CustomOrderNumber_AlreadyExists_ThrowsException() {
        CreateOrderRequest request = CreateOrderRequest.builder()
                .orderNumber("DUPLICATE-ORD-001")
                .shippingMethod(ShippingMethod.PICKUP)
                .paymentMethod(PaymentMethod.COD)
                .items(List.of(
                        CreateOrderRequest.OrderItemRequest.builder()
                                .attributesSku("SKU-1001")
                                .quantity(1)
                                .build()
                ))
                .build();

        when(attributesRepository.findAllBySku_skuIn(List.of("SKU-1001"))).thenReturn(List.of(sampleAttr));
        when(orderRepository.existsByOrderNumber("DUPLICATE-ORD-001")).thenReturn(true);

        BusinessException ex = assertThrows(BusinessException.class, () -> orderService.createOrder(request));
        assertEquals(com.ddicg.erp.core.exception.ErrorCode.INVALID_REQUEST, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("DUPLICATE-ORD-001"));
    }

    @Test
    @DisplayName("ShippingMethod và Order.getShippingMethodEnum xử lý linh hoạt chuỗi tiếng Việt lẫn Enum")
    void testShippingMethod_FlexibleParsing() {
        Order order1 = new Order();
        order1.setShippingMethod("Giao hàng tiết kiệm");
        assertEquals(ShippingMethod.DELIVERY, order1.getShippingMethodEnum());
        assertEquals("Giao hàng tận nơi", order1.getShippingMethodEnum().getDescription());

        Order order2 = new Order();
        order2.setShippingMethod(ShippingMethod.PICKUP);
        assertEquals("PICKUP", order2.getShippingMethod());
        assertEquals(ShippingMethod.PICKUP, order2.getShippingMethodEnum());
        assertEquals("Nhận tại cửa hàng", order2.getShippingMethodEnum().getDescription());
    }

    @Test
    @DisplayName("Tạo đơn hàng thành công -> Tự động đặt Redis lock với TTL và sinh 1 Outbox Event")
    void testCreateOrder_SetsRedisLockWithTtl() {
        CreateOrderRequest request = CreateOrderRequest.builder()
                .orderNumber("REDIS-ORD-001")
                .addressSku("ADDR-1001")
                .shippingMethod(ShippingMethod.PICKUP)
                .paymentMethod(PaymentMethod.COD)
                .items(List.of(
                        CreateOrderRequest.OrderItemRequest.builder()
                                .attributesSku("SKU-1001")
                                .quantity(1)
                                .build()
                ))
                .build();

        when(orderRepository.existsByOrderNumber("REDIS-ORD-001")).thenReturn(false);
        when(attributesRepository.findAllBySku_skuIn(List.of("SKU-1001"))).thenReturn(List.of(sampleAttr));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(orderMapper.toDto(any(Order.class))).thenAnswer(invocation -> {
            Order o = invocation.getArgument(0);
            return OrderDto.builder().orderNumber(o.getOrderNumber()).build();
        });

        Response<OrderDto> response = orderService.createOrder(request);

        assertNotNull(response);
        assertNotNull(response.getData());
        assertEquals("REDIS-ORD-001", response.getData().getOrderNumber());

        // Kiểm tra đúng 1 Outbox Event được lưu
        verify(orderHelper, times(1)).saveOrderCreatedEvent(any(Order.class), eq(request));
        verify(orderHelper, never()).saveOrderStatusChangedEvent(any(), any(), any(), any(), any());

        // Kiểm tra Redis lock được set với TTL cố định 1 giây
        verify(redisService).setValueWithExpiry(eq(RedisTable.LOCK_ORDER), eq("REDIS-ORD-001"), eq("PROCESSING"), eq(1L), eq(java.util.concurrent.TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("handlePaymentTimeout chuyển trạng thái sang FAILED và giải phóng tài nguyên")
    void testHandlePaymentTimeout_Success() {
        Order order = new Order();
        order.setOrderNumber("ORD-TO-001");
        order.setStatus(new java.util.ArrayList<>(List.of(com.ddicg.erp.core.common.model.enums.OrderStatus.PENDING, com.ddicg.erp.core.common.model.enums.OrderStatus.WAITING_PAYMENT)));
        order.setCurrentStatus(com.ddicg.erp.core.common.model.enums.OrderStatus.WAITING_PAYMENT);

        when(orderRepository.findByOrderNumber("ORD-TO-001")).thenReturn(Optional.of(order));
        when(orderStatusHandler.getCurrentStatus(order)).thenReturn(com.ddicg.erp.core.common.model.enums.OrderStatus.WAITING_PAYMENT);
        when(orderRepository.save(order)).thenReturn(order);

        orderService.handlePaymentTimeout("ORD-TO-001");

        verify(orderStatusHandler).transitionTo(order, com.ddicg.erp.core.common.model.enums.OrderStatus.FAILED, "Timeout chờ phản hồi thanh toán");
        verify(orderHelper).releaseInventory(any());
        verify(orderHelper).saveOrderStatusChangedEvent(eq(order), eq(com.ddicg.erp.core.common.model.enums.OrderStatus.WAITING_PAYMENT), eq(com.ddicg.erp.core.common.model.enums.OrderStatus.FAILED), anyString(), eq("system"));
    }

    @Test
    @DisplayName("Tạo đơn hàng COD khởi tạo trạng thái PENDING và PROCESSING (bỏ qua CONFIRMED)")
    void testCreateOrder_COD_InitializesDirectlyToProcessing() {
        CreateOrderRequest request = CreateOrderRequest.builder()
                .addressSku("ADDR-1001")
                .shippingMethod(ShippingMethod.PICKUP)
                .paymentMethod(PaymentMethod.COD)
                .items(List.of(
                        CreateOrderRequest.OrderItemRequest.builder()
                                .attributesSku("SKU-1001")
                                .quantity(1)
                                .build()
                ))
                .build();

        when(attributesRepository.findAllBySku_skuIn(List.of("SKU-1001"))).thenReturn(List.of(sampleAttr));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order o = invocation.getArgument(0);
            assertEquals(List.of(com.ddicg.erp.core.common.model.enums.OrderStatus.PENDING, com.ddicg.erp.core.common.model.enums.OrderStatus.PROCESSING), o.getStatus());
            assertEquals(com.ddicg.erp.core.common.model.enums.OrderStatus.PROCESSING, o.getCurrentStatus());
            return o;
        });
        when(orderMapper.toDto(any(Order.class))).thenReturn(OrderDto.builder().build());

        orderService.createOrder(request);
        verify(orderRepository).save(any(Order.class));
    }

    @Test
    @DisplayName("Thanh toán thành công chuyển thẳng từ WAITING_PAYMENT sang PROCESSING")
    void testProcessPayment_Success_TransitionsDirectlyToProcessing() {
        Order order = new Order();
        order.setOrderNumber("ORD-PAY-001");
        order.setStatus(new java.util.ArrayList<>(List.of(com.ddicg.erp.core.common.model.enums.OrderStatus.PENDING, com.ddicg.erp.core.common.model.enums.OrderStatus.WAITING_PAYMENT)));
        order.setCurrentStatus(com.ddicg.erp.core.common.model.enums.OrderStatus.WAITING_PAYMENT);

        when(orderRepository.findByOrderNumber("ORD-PAY-001")).thenReturn(Optional.of(order));
        when(orderStatusHandler.getCurrentStatus(order)).thenReturn(com.ddicg.erp.core.common.model.enums.OrderStatus.WAITING_PAYMENT);
        when(orderRepository.save(order)).thenReturn(order);
        when(orderMapper.toDto(order)).thenReturn(OrderDto.builder().orderNumber("ORD-PAY-001").build());

        com.ddicg.erp.modules.order.dto.request.PaymentCallbackRequest callback = new com.ddicg.erp.modules.order.dto.request.PaymentCallbackRequest();
        callback.setOrderNumber("ORD-PAY-001");
        callback.setStatus("SUCCESS");

        var response = orderService.processPayment(callback);

        assertNotNull(response);
        verify(orderStatusHandler).transitionTo(order, com.ddicg.erp.core.common.model.enums.OrderStatus.PROCESSING, "");
        verify(orderHelper).confirmReservation(any());
        verify(redisService).delete(eq(RedisTable.LOCK_ORDER), eq("ORD-PAY-001"));
    }

    // =========================================================================
    // VOUCHER INTEGRATION TEST CASES
    // =========================================================================

    @Test
    @DisplayName("Tạo đơn hàng với voucher hợp lệ -> Tính đúng chiết khấu, ghi vào Order và gọi reserveVouchers")
    void createOrder_withValidVouchers_shouldApplyDiscountsAndReserveVouchers() {
        // Arrange: discount processor trả về 20k giảm cho SKU-1001 và 10k giảm ship
        DiscountEvaluationResult discountResult = DiscountEvaluationResult.builder()
                .productDiscountAmount(20000.0)
                .itemLevelDiscountAmount(20000.0)
                .globalDiscountAmount(0.0)
                .shippingDiscountAmount(10000.0)
                .appliedDiscountCodes(List.of("SALE10", "FREESHIP50"))
                .itemDiscounts(Map.of("SKU-1001", 20000.0))
                .itemDiscountPercentages(Map.of("SKU-1001", 10.0))
                .build();

        when(orderDiscountProcessor.evaluateDiscount(any(OrderDiscountContext.class)))
                .thenReturn(discountResult);

        CreateOrderRequest request = CreateOrderRequest.builder()
                .orderNumber("ORD-VOUCHER-001")
                .addressSku("ADDR-1001")
                .shippingMethod(ShippingMethod.DELIVERY)
                .paymentMethod(PaymentMethod.COD)
                .discountCodes(List.of("SALE10", "FREESHIP50"))
                .items(List.of(
                        CreateOrderRequest.OrderItemRequest.builder()
                                .attributesSku("SKU-1001")
                                .quantity(1)
                                .build()
                ))
                .build();

        com.ddicg.erp.modules.iam.model.Address mockAddress = new com.ddicg.erp.modules.iam.model.Address();
        mockAddress.setSku("ADDR-1001");
        when(addressRepository.findBySku("ADDR-1001")).thenReturn(Optional.of(mockAddress));
        when(shippingCalculationService.calculateShippingFee(any(), any(), any())).thenReturn(30000.0);
        when(orderRepository.existsByOrderNumber("ORD-VOUCHER-001")).thenReturn(false);
        when(attributesRepository.findAllBySku_skuIn(anyList())).thenReturn(List.of(sampleAttr));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            // Assert: discount fields được điền đúng
            assertEquals(30000.0, o.getDiscountAmount(), 0.001); // 20k product + 10k ship
            assertEquals(20000.0, o.getShippingFee(), 0.001);    // 30k - 10k discount
            assertEquals(List.of("SALE10", "FREESHIP50"), o.getDiscountCodes());
            return o;
        });
        when(orderMapper.toDto(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            return OrderDto.builder().orderNumber(o.getOrderNumber()).totalAmount(o.getTotalAmount()).build();
        });

        Response<OrderDto> response = orderService.createOrder(request);

        assertNotNull(response);
        // Verify reserveVouchers được gọi với đúng codes và orderNumber
        verify(voucherReservationService).reserveVouchers(
                eq(List.of("SALE10", "FREESHIP50")),
                eq("ORD-VOUCHER-001"),
                any(Double.class)
        );
        verify(orderDiscountProcessor).evaluateDiscount(any(OrderDiscountContext.class));
    }

    @Test
    @DisplayName("confirmOrder -> Gọi commitVouchers để tăng usedQuantity và xóa Redis hold")
    void confirmOrder_shouldCommitVouchers() {
        Order order = new Order();
        order.setOrderNumber("ORD-CONFIRM-001");
        order.setStatus(new java.util.ArrayList<>(List.of(com.ddicg.erp.core.common.model.enums.OrderStatus.PENDING)));
        order.setCurrentStatus(com.ddicg.erp.core.common.model.enums.OrderStatus.PENDING);
        order.setDiscountCodes(List.of("SALE10"));
        order.setOrderItems(new java.util.ArrayList<>());

        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderStatusHandler.getCurrentStatus(order)).thenReturn(com.ddicg.erp.core.common.model.enums.OrderStatus.PENDING);
        when(orderRepository.save(order)).thenReturn(order);
        when(orderMapper.toDto(order)).thenReturn(OrderDto.builder().orderNumber("ORD-CONFIRM-001").build());
        when(securityUtil.getCurrentUsername()).thenReturn("admin");

        com.ddicg.erp.modules.order.dto.request.ConfirmOrderRequest r =
                new com.ddicg.erp.modules.order.dto.request.ConfirmOrderRequest();
        r.setOrderId("1");
        r.setConfirmationInfo("OK");

        orderService.confirmOrder(r);

        verify(voucherReservationService).commitVouchers(eq(List.of("SALE10")), eq("ORD-CONFIRM-001"));
    }

    @Test
    @DisplayName("cancelOrder -> Gọi releaseVouchers để xóa Redis hold khi đơn bị hủy")
    void cancelOrder_shouldReleaseVouchers() {
        Order order = new Order();
        order.setOrderNumber("ORD-CANCEL-001");
        order.setStatus(new java.util.ArrayList<>(List.of(com.ddicg.erp.core.common.model.enums.OrderStatus.PENDING)));
        order.setCurrentStatus(com.ddicg.erp.core.common.model.enums.OrderStatus.PENDING);
        order.setDiscountCodes(List.of("FREESHIP50"));
        order.setOrderItems(new java.util.ArrayList<>());

        when(orderRepository.findById(2L)).thenReturn(Optional.of(order));
        when(orderStatusHandler.getCurrentStatus(order)).thenReturn(com.ddicg.erp.core.common.model.enums.OrderStatus.PENDING);
        when(orderStatusHandler.isValidTransition(
                com.ddicg.erp.core.common.model.enums.OrderStatus.PENDING,
                com.ddicg.erp.core.common.model.enums.OrderStatus.CANCELLED)).thenReturn(true);
        when(orderRepository.save(order)).thenReturn(order);
        when(orderMapper.toDto(order)).thenReturn(OrderDto.builder().orderNumber("ORD-CANCEL-001").build());
        when(securityUtil.getCurrentUsername()).thenReturn("admin");

        com.ddicg.erp.modules.order.dto.request.CancelOrderRequest r =
                new com.ddicg.erp.modules.order.dto.request.CancelOrderRequest();
        r.setOrderId("2");
        r.setCancellationReason("Khách đổi ý");

        orderService.cancelOrder(r);

        verify(voucherReservationService).releaseVouchers(eq(List.of("FREESHIP50")), eq("ORD-CANCEL-001"));
    }

    @Test
    @DisplayName("processPayment failure -> Gọi releaseVouchers để giải phóng hold khi thanh toán thất bại")
    void processPayment_failure_shouldReleaseVouchers() {
        Order order = new Order();
        order.setOrderNumber("ORD-FAIL-001");
        order.setStatus(new java.util.ArrayList<>(List.of(
                com.ddicg.erp.core.common.model.enums.OrderStatus.PENDING,
                com.ddicg.erp.core.common.model.enums.OrderStatus.WAITING_PAYMENT)));
        order.setCurrentStatus(com.ddicg.erp.core.common.model.enums.OrderStatus.WAITING_PAYMENT);
        order.setDiscountCodes(List.of("GIAM50K"));
        order.setOrderItems(new java.util.ArrayList<>());

        when(orderRepository.findByOrderNumber("ORD-FAIL-001")).thenReturn(Optional.of(order));
        when(orderStatusHandler.getCurrentStatus(order)).thenReturn(com.ddicg.erp.core.common.model.enums.OrderStatus.WAITING_PAYMENT);
        when(orderRepository.save(order)).thenReturn(order);
        when(orderMapper.toDto(order)).thenReturn(OrderDto.builder().orderNumber("ORD-FAIL-001").build());

        com.ddicg.erp.modules.order.dto.request.PaymentCallbackRequest callback =
                new com.ddicg.erp.modules.order.dto.request.PaymentCallbackRequest();
        callback.setOrderNumber("ORD-FAIL-001");
        callback.setStatus("FAILED");

        orderService.processPayment(callback);

        verify(voucherReservationService).releaseVouchers(eq(List.of("GIAM50K")), eq("ORD-FAIL-001"));
        verify(redisService).delete(eq(RedisTable.LOCK_ORDER), eq("ORD-FAIL-001"));
    }
}
