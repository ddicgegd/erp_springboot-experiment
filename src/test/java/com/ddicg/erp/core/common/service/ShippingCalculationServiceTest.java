package com.ddicg.erp.core.common.service;

import com.ddicg.erp.core.common.dto.response.ShippingCalculationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ShippingCalculationServiceTest {

    private ShippingCalculationService shippingCalculationService;

    @BeforeEach
    void setUp() {
        shippingCalculationService = new ShippingCalculationService();
    }

    @Test
    @DisplayName("Test 1: Giao nội huyện Yên Định / Thiệu Hóa gần kho Định Hòa -> Cước phí rẻ nhất (16.000đ)")
    void testCalculate_LocalDistrict() {
        // Tọa độ gần Định Hòa, Yên Định (~5-10km)
        Double lat = 19.9950;
        Double lon = 105.6500;
        String address = "02 ngõ nghè, Xã Định Hòa, Huyện Yên Định, Tỉnh Thanh Hóa";

        ShippingCalculationResult result = shippingCalculationService.calculateShippingDetails(lat, lon, address);

        assertNotNull(result);
        assertEquals(16000.0, result.getShippingFee());
        assertTrue(result.getZoneTier().contains("TIER_LOCAL"));
        assertTrue(result.getDistanceKm() < 30.0);
    }

    @Test
    @DisplayName("Test 2: Giao nội tỉnh Thanh Hóa (TP Thanh Hóa) -> Cước nội tỉnh (20.000đ)")
    void testCalculate_ProvincialThanhHoa() {
        // TP Thanh Hóa (~35km từ Định Hòa)
        Double lat = 19.8067;
        Double lon = 105.7852;
        String address = "120 Lê Hoàn, Phường Điện Biên, Thành phố Thanh Hóa, Thanh Hóa";

        ShippingCalculationResult result = shippingCalculationService.calculateShippingDetails(lat, lon, address);

        assertNotNull(result);
        assertEquals(20000.0, result.getShippingFee());
        assertTrue(result.getZoneTier().contains("TIER_PROVINCIAL"));
    }

    @Test
    @DisplayName("Test 3: Giao trục đô thị lớn Miền Bắc (Hà Nội) -> Cước Tier 1 Miền Bắc (26.000đ)")
    void testCalculate_MajorMetroHanoi() {
        // Hà Nội (~150km từ Định Hòa)
        Double lat = 21.0285;
        Double lon = 105.8542;
        String address = "10 Hoàng Diệu, Phường Điện Biên, Quận Ba Đình, Hà Nội";

        ShippingCalculationResult result = shippingCalculationService.calculateShippingDetails(lat, lon, address);

        assertNotNull(result);
        assertEquals(26000.0, result.getShippingFee());
        assertTrue(result.getZoneTier().contains("TIER_1"));
    }

    @Test
    @DisplayName("Test 4: Giao trục đô thị lớn Miền Nam (Quận 1, TP.HCM) -> Cước Tier 1 trục lớn xa (35.000đ)")
    void testCalculate_MajorMetroHcmc() {
        // TP.HCM (~1500km)
        Double lat = 10.7769;
        Double lon = 106.7009;
        String address = "Số 1 Lê Duẩn, Phường Bến Nghé, Quận 1, TP. Hồ Chí Minh";

        ShippingCalculationResult result = shippingCalculationService.calculateShippingDetails(lat, lon, address);

        assertNotNull(result);
        assertEquals(35000.0, result.getShippingFee());
        assertTrue(result.getZoneTier().contains("TIER_1"));
    }

    @Test
    @DisplayName("Test 5: Giao nông thôn Tây Nguyên (Huyện Cư M'gar, Đắk Lắk) -> Cước Tier 3 phụ phí vùng sâu (~45.000đ - 60.000đ)")
    void testCalculate_RemoteAreaDakLak() {
        Double lat = 12.8333;
        Double lon = 108.0833;
        String address = "Thôn 2, Xã Cuôr Đăng, Huyện Cư M'gar, Tỉnh Đắk Lắk";

        ShippingCalculationResult result = shippingCalculationService.calculateShippingDetails(lat, lon, address);

        assertNotNull(result);
        assertTrue(result.getShippingFee() >= 45000.0 && result.getShippingFee() <= 60000.0);
        assertTrue(result.getZoneTier().contains("TIER_3"));
    }

    @Test
    @DisplayName("Test 6: Giao huyện đảo xa bờ (Côn Đảo) -> Cước Tier Island (65.000đ - 85.000đ)")
    void testCalculate_SpecialIslandConDao() {
        Double lat = 8.6833;
        Double lon = 106.6000;
        String address = "Đường Tôn Đức Thắng, Huyện Côn Đảo, Tỉnh Bà Rịa - Vũng Tàu";

        ShippingCalculationResult result = shippingCalculationService.calculateShippingDetails(lat, lon, address);

        assertNotNull(result);
        assertTrue(result.getShippingFee() >= 65000.0 && result.getShippingFee() <= 85000.0);
        assertTrue(result.getZoneTier().contains("TIER_ISLAND"));
    }
}
