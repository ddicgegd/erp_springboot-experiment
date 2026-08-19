package com.ddicg.erp.core.common.service;

import com.ddicg.erp.core.common.dto.response.ResolvedAddress;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.*;

class AddressResolutionServiceTest {

    private AddressResolutionService addressResolutionService;

    @BeforeEach
    void setUp() {
        addressResolutionService = new AddressResolutionService("https://nominatim.openstreetmap.org", "ERP-SpringBoot-App/1.0");
    }

    @Test
    @DisplayName("Địa chỉ Huyện và Xã đều ảo ('Xã Ảo Vừa Phải Đa Tốn, Huyện Ảo, Hà Nội') -> Bắt buộc thất bại (success = false)")
    void resolve_fakeWardAndFakeDistrict_shouldFail() {
        ResolvedAddress result = addressResolutionService.resolve("Xã Ảo Vừa Phải Đa Tốn, Huyện Ảo, Hà Nội");
        assertNotNull(result);
        assertFalse(result.isSuccess(), "Địa chỉ có huyện/xã ảo không được trả về success=true");
    }

    @Test
    @DisplayName("Chỉ nhập độc nhất Tỉnh/Thành phố ('Hà Nội') -> Bắt buộc thất bại (success = false)")
    void resolve_onlyProvince_shouldFail() {
        ResolvedAddress result = addressResolutionService.resolve("Hà Nội");
        assertNotNull(result);
        assertFalse(result.isSuccess(), "Chỉ có Tỉnh không đủ điều kiện giao nhận");
    }

    @Test
    @DisplayName("Chỉ nhập Quận/Huyện + Tỉnh thiếu Xã/Đường ('Huyện Gia Lâm, Hà Nội') -> Bắt buộc thất bại (success = false)")
    void resolve_onlyDistrictAndProvince_shouldFail() {
        ResolvedAddress result = addressResolutionService.resolve("Huyện Gia Lâm, Hà Nội");
        assertNotNull(result);
        assertFalse(result.isSuccess(), "Thiếu cấp Xã/Phường/Đường/Thôn phải trả về success=false");
    }

    @Test
    @DisplayName("Đầy đủ Xã + Huyện + Tỉnh ('Xã Đa Tốn, Huyện Gia Lâm, Hà Nội') -> Thành công (success = true)")
    void resolve_validWardDistrictProvince_shouldSucceed() {
        ResolvedAddress result = addressResolutionService.resolve("Xã Đa Tốn, Huyện Gia Lâm, Hà Nội");
        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertNotNull(result.getLatitude());
        assertNotNull(result.getLongitude());
        assertTrue(result.getFormattedAddress().contains("Gia Lâm"));
        assertTrue(result.getFormattedAddress().contains("Hà Nội"));
    }

    @Test
    @DisplayName("Đầy đủ Số nhà/Đường + Phường + Quận + Tỉnh ('Số 10 Hoàng Diệu, Điện Biên, Ba Đình, Hà Nội') -> Thành công (success = true)")
    void resolve_validFullStreetAddress_shouldSucceed() {
        ResolvedAddress result = addressResolutionService.resolve("Số 10 Hoàng Diệu, Phường Điện Biên, Ba Đình, Hà Nội");
        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertNotNull(result.getLatitude());
        assertNotNull(result.getLongitude());
        assertTrue(result.getFormattedAddress().contains("Hoàng Diệu"));
        assertTrue(result.getFormattedAddress().contains("Ba Đình"));
    }

    @Test
    @DisplayName("Đầy đủ Thôn + Xã + Huyện + Tỉnh ('Thôn Đông, Xã Đa Tốn, Huyện Gia Lâm, Hà Nội') -> Thành công (success = true)")
    void resolve_validVillageWardDistrictProvince_shouldSucceed() {
        ResolvedAddress result = addressResolutionService.resolve("Thôn Đông, Xã Đa Tốn, Huyện Gia Lâm, Hà Nội");
        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertNotNull(result.getLatitude());
        assertNotNull(result.getLongitude());
        assertTrue(result.getFormattedAddress().contains("Gia Lâm"));
        assertTrue(result.getFormattedAddress().contains("Hà Nội"));
    }

    @Test
    @DisplayName("Địa chỉ thực tế Thanh Hóa viết dính ('02 ngõ nghè, lành nhì, định hòa, yên định thanh hóa') -> Thành công (success = true)")
    void resolve_realWorldThanhHoaAddress_shouldSucceed() {
        ResolvedAddress result = addressResolutionService.resolve("02 ngõ nghè, lành nhì, định hòa, yên định thanh hóa");
        assertNotNull(result);
        assertTrue(result.isSuccess(), "Địa chỉ thực tế Thanh Hóa phải phân giải thành công");
        assertNotNull(result.getLatitude());
        assertNotNull(result.getLongitude());
        assertTrue(result.getFormattedAddress().contains("Yên Định"));
        assertTrue(result.getFormattedAddress().contains("Thanh Hóa"));
    }

    @Test
    @DisplayName("Địa chỉ KCN ('Lô CN-01 KCN Quế Võ, Phường Nam Sơn, TP Bắc Ninh, Bắc Ninh') -> Thành công (success = true)")
    void resolve_industrialZoneAddress_shouldSucceed() {
        ResolvedAddress result = addressResolutionService.resolve("Lô CN-01 KCN Quế Võ, Phường Nam Sơn, TP Bắc Ninh, Bắc Ninh");
        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertTrue(result.getFormattedAddress().contains("Bắc Ninh"));
    }

    @Test
    @DisplayName("Huyện đảo Côn Đảo đặc thù không có cấp Xã ('Đường Tôn Đức Thắng, Huyện Côn Đảo, Bà Rịa - Vũng Tàu') -> Thành công (success = true)")
    void resolve_islandSpecialDistrictWithoutWard_shouldSucceed() {
        ResolvedAddress result = addressResolutionService.resolve("Đường Tôn Đức Thắng, Huyện Côn Đảo, Bà Rịa - Vũng Tàu");
        assertNotNull(result);
        assertTrue(result.isSuccess(), "Huyện đảo Côn Đảo phải được chấp nhận");
        assertTrue(result.getFormattedAddress().contains("Côn Đảo"));
    }

    @Test
    @DisplayName("Địa chỉ Nông thôn Tây Nguyên ('Thôn 2, Xã Cư M'gar, Huyện Cư M'gar, Đắk Lắk') -> Thành công (success = true)")
    void resolve_daklakRuralAddress_shouldSucceed() {
        ResolvedAddress result = addressResolutionService.resolve("Thôn 2, Xã Cư M'gar, Huyện Cư M'gar, Đắk Lắk");
        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertTrue(result.getFormattedAddress().contains("Cư M'gar") || result.getFormattedAddress().contains("Đắk Lắk"));
    }

    @Test
    @DisplayName("Địa chỉ viết liền không dấu phẩy có đủ Xã, Huyện, Tỉnh ('02 ngõ nghè xã định hòa huyện yên định tỉnh thanh hóa') -> Nhận diện đầy đủ cấp Xã")
    void resolve_unpunctuatedContinuousThanhHoaAddress_shouldExtractWard() {
        ResolvedAddress result = addressResolutionService.resolve("02 ngõ nghè xã định hòa huyện yên định tỉnh thanh hóa");
        assertNotNull(result);
        assertTrue(result.isSuccess(), "Phải phân giải thành công địa chỉ viết liền");
        assertNotNull(result.getLatitude());
        assertNotNull(result.getLongitude());
        assertTrue(result.getFormattedAddress().contains("Định Hòa"), "Phải nhận diện và có Xã Định Hòa trong địa chỉ chuẩn hóa");
        assertTrue(result.getFormattedAddress().contains("Yên Định"), "Phải có Huyện Yên Định");
        assertTrue(result.getFormattedAddress().contains("Thanh Hóa"), "Phải có Tỉnh Thanh Hóa");
    }
}
