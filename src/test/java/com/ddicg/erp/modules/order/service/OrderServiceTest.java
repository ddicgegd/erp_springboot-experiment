package com.ddicg.erp.modules.order.service;

import com.ddicg.erp.core.common.dto.response.Response;
import com.ddicg.erp.core.common.model.embedded.SkuInfo;
import com.ddicg.erp.core.common.model.enums.PaymentMethod;
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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
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
    private OutboxOrderHelper outboxOrderHelper;
    @Mock
    private OrderInventoryService orderInventoryService;

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
    }

    @Test
    @DisplayName("Tạo đơn hàng thành công, tự động lấy CustomerInfo từ Token và Address")
    void testCreateOrder_Success() {
        CreateOrderRequest request = CreateOrderRequest.builder()
                .addressSku("ADDR-1001")
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
                    .totalAmount(o.getTotalAmount())
                    .build();
        });

        Response<OrderDto> response = orderService.createOrder(request);

        assertNotNull(response);
        assertNotNull(response.getData());
        assertEquals("Nguyen Van A", response.getData().getCustomerName());
        assertEquals("a@example.com", response.getData().getCustomerEmail());

        verify(orderRepository).save(any(Order.class));
        verify(outboxOrderHelper).saveOrderCreatedEvent(any(Order.class), eq(request));
    }

    @Test
    @DisplayName("Tạo đơn hàng thất bại khi addressSku bị để trống")
    void testCreateOrder_MissingAddressSku_ThrowsException() {
        CreateOrderRequest request = CreateOrderRequest.builder()
                .addressSku(null)
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
}
