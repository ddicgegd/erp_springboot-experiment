package com.ddicg.erp.modules.cart.controller;

import com.ddicg.erp.core.common.model.enums.StockStatus;
import com.ddicg.erp.core.common.service.MinioService;
import com.ddicg.erp.modules.cart.dto.CartItemRequest;
import com.ddicg.erp.modules.cart.dto.UpdateCartItemRequest;
import com.ddicg.erp.modules.iam.model.User;
import com.ddicg.erp.modules.iam.repository.UserRepository;
import com.ddicg.erp.modules.iam.service.EmailService;
import com.ddicg.erp.modules.merchandise.model.Attributes;
import com.ddicg.erp.modules.merchandise.repository.AttributesRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ShoppingCartIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AttributesRepository attributesRepository;

    @Autowired
    private UserRepository userRepository;

    @MockitoBean
    private EmailService emailService;

    @MockitoBean
    private MinioService minioService;

    private static final String GUEST_ID = "guest-integration-uuid-1";
    private static String testSku1;
    private static String testSku2;

    @BeforeEach
    void setUp() {
        if (testSku1 == null) {
            List<Attributes> attrs = attributesRepository.findAll();
            for (Attributes attr : attrs) {
                if (attr.getSku() != null && attr.getSku().getSku() != null && attr.getStatusProduct() == StockStatus.AVAILABLE) {
                    if (testSku1 == null) {
                        testSku1 = attr.getSku().getSku();
                    } else if (testSku2 == null && !attr.getSku().getSku().equals(testSku1)) {
                        testSku2 = attr.getSku().getSku();
                        break;
                    }
                }
            }
        }
        assertNotNull(testSku1, "Cần có ít nhất 1 SKU khả dụng từ seeder");
        assertNotNull(testSku2, "Cần có ít nhất 2 SKU khả dụng từ seeder");
    }

    @Test
    @Order(1)
    @DisplayName("1. POST /api/cart/items -> Thêm 2 sản phẩm vào giỏ thành công (200 OK)")
    void test1_addToCart() throws Exception {
        // Đảm bảo giỏ hàng trống trước khi test
        mockMvc.perform(delete("/api/cart").header("X-Guest-Id", GUEST_ID));

        List<CartItemRequest> items = List.of(
                CartItemRequest.builder().sku(testSku1).quantity(2).build(),
                CartItemRequest.builder().sku(testSku2).quantity(1).build()
        );

        mockMvc.perform(post("/api/cart/items")
                        .header("X-Guest-Id", GUEST_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(items)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.code").value(200))
                .andExpect(jsonPath("$.data.totalItems").value(3))
                .andExpect(jsonPath("$.data.items", hasSize(2)));
    }

    @Test
    @Order(2)
    @DisplayName("2. GET /api/cart/count -> Lấy nhanh số lượng item trong giỏ (Badge)")
    void test2_getCartCount() throws Exception {
        mockMvc.perform(get("/api/cart/count")
                        .header("X-Guest-Id", GUEST_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.code").value(200))
                .andExpect(jsonPath("$.data").value(3));
    }

    @Test
    @Order(3)
    @DisplayName("3. GET /api/cart -> Lấy thông tin giỏ hàng đầy đủ")
    void test3_getCartFull() throws Exception {
        mockMvc.perform(get("/api/cart")
                        .header("X-Guest-Id", GUEST_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.code").value(200))
                .andExpect(jsonPath("$.data.totalItems").value(3))
                .andExpect(jsonPath("$.data.items", hasSize(2)))
                .andExpect(jsonPath("$.data.finalAmount", greaterThan(0.0)));
    }

    @Test
    @Order(4)
    @DisplayName("4. GET /api/cart?fields=totalItems,username -> Fast Path (chỉ trả về fields yêu cầu)")
    void test4_getCartFastPath() throws Exception {
        mockMvc.perform(get("/api/cart")
                        .header("X-Guest-Id", GUEST_ID)
                        .param("fields", "totalItems,username"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.code").value(200))
                .andExpect(jsonPath("$.data.totalItems").value(3))
                .andExpect(jsonPath("$.data.username").value("guest:" + GUEST_ID))
                .andExpect(jsonPath("$.data.items").doesNotExist())
                .andExpect(jsonPath("$.data.totalPrice").doesNotExist());
    }

    @Test
    @Order(5)
    @DisplayName("5. PUT /api/cart/items/{sku} -> Cập nhật số lượng sản phẩm")
    void test5_updateQuantity() throws Exception {
        UpdateCartItemRequest updateReq = UpdateCartItemRequest.builder().quantity(5).build();

        mockMvc.perform(put("/api/cart/items/" + testSku1)
                        .header("X-Guest-Id", GUEST_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.code").value(200))
                .andExpect(jsonPath("$.data.totalItems").value(6)); // 5 (sku1) + 1 (sku2)
    }

    @Test
    @Order(6)
    @DisplayName("6. DELETE /api/cart/items/{sku} -> Xóa 1 SKU cụ thể")
    void test6_removeItem() throws Exception {
        mockMvc.perform(delete("/api/cart/items/" + testSku2)
                        .header("X-Guest-Id", GUEST_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.code").value(200))
                .andExpect(jsonPath("$.data.totalItems").value(5))
                .andExpect(jsonPath("$.data.items", hasSize(1)));
    }

    @Test
    @Order(7)
    @DisplayName("7. DELETE /api/cart?skus=... -> Xóa batch các SKU")
    void test7_removeItemsBatch() throws Exception {
        // Thêm testSku2 vào lại giỏ
        List<CartItemRequest> items = List.of(CartItemRequest.builder().sku(testSku2).quantity(2).build());
        mockMvc.perform(post("/api/cart/items")
                .header("X-Guest-Id", GUEST_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(items)));

        // Xóa testSku2 bằng DELETE /api/cart?skus=...
        mockMvc.perform(delete("/api/cart")
                        .header("X-Guest-Id", GUEST_ID)
                        .param("skus", testSku2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.code").value(200))
                .andExpect(jsonPath("$.data.totalItems").value(5));
    }

    @Test
    @Order(8)
    @DisplayName("8. DELETE /api/cart -> Xóa sạch toàn bộ giỏ hàng khi không truyền skus")
    void test8_clearEntireCart() throws Exception {
        mockMvc.perform(delete("/api/cart")
                        .header("X-Guest-Id", GUEST_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.code").value(200))
                .andExpect(jsonPath("$.data.totalItems").value(0))
                .andExpect(jsonPath("$.data.items", hasSize(0)));
    }

    @Test
    @Order(9)
    @WithMockUser(username = "integration-user@example.com")
    @DisplayName("9. POST /api/cart/merge -> Hợp nhất giỏ hàng Guest sang User đã xác thực")
    void test9_mergeCart() throws Exception {
        userRepository.save(User.builder()
                .email("integration-user@example.com")
                .name("integration-user")
                .password("password")
                .build());

        // Guest thêm testSku1
        List<CartItemRequest> items = List.of(CartItemRequest.builder().sku(testSku1).quantity(2).build());
        mockMvc.perform(post("/api/cart/items")
                .header("X-Guest-Id", GUEST_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(items)));

        // Gọi merge
        mockMvc.perform(post("/api/cart/merge")
                        .header("X-Guest-Id", GUEST_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.code").value(200))
                .andExpect(jsonPath("$.data.totalItems").value(2))
                .andExpect(jsonPath("$.data.username").value("integration-user@example.com"));
    }

    @Test
    @Order(10)
    @DisplayName("10. Error Case: Thêm SKU không tồn tại -> 404 NOT_FOUND")
    void test10_invalidSku() throws Exception {
        List<CartItemRequest> items = List.of(CartItemRequest.builder().sku("NON-EXISTENT-SKU").quantity(1).build());

        mockMvc.perform(post("/api/cart/items")
                        .header("X-Guest-Id", GUEST_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(items)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @Order(11)
    @DisplayName("11. Error Case: Số lượng vượt quá 99 -> 400 VALIDATION_FAILED")
    void test11_exceedQuantity() throws Exception {
        List<CartItemRequest> items = List.of(CartItemRequest.builder().sku(testSku1).quantity(100).build());

        mockMvc.perform(post("/api/cart/items")
                        .header("X-Guest-Id", GUEST_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(items)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(12)
    @DisplayName("12. Error Case: Không có X-Guest-Id và chưa đăng nhập -> 401 UNAUTHORIZED")
    void test12_unauthorized() throws Exception {
        mockMvc.perform(get("/api/cart"))
                .andExpect(status().isUnauthorized());
    }
}
