package com.ddicg.erp.core.common.service;

import com.ddicg.erp.core.common.dto.response.ShippingCalculationResult;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.List;
import java.util.regex.Pattern;

@Service
@Slf4j
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ShippingCalculationService {

    // Kho Tổng mặc định tại Xã Định Hòa, Huyện Yên Định, Tỉnh Thanh Hóa
    public static final String DEFAULT_ORIGIN_NAME = "Kho Tổng Định Hòa (Yên Định, Thanh Hóa)";
    public static final double ORIGIN_LAT = 19.9867;
    public static final double ORIGIN_LON = 105.6425;

    // Bán kính trái đất tính theo km
    private static final double EARTH_RADIUS_KM = 6371.0;

    // Danh sách các Hub trung chuyển chính trên toàn quốc
    private static final List<MajorHub> MAJOR_HUBS = List.of(
            new MajorHub("Hub Thanh Hóa", 19.8067, 105.7852, 1.0),
            new MajorHub("Hub Hà Nội", 21.0285, 105.8542, 1.0),
            new MajorHub("Hub Hải Phòng", 20.8449, 106.6881, 1.0),
            new MajorHub("Hub Vinh (Nghệ An)", 18.6734, 105.6813, 1.0),
            new MajorHub("Hub Đà Nẵng", 16.0544, 108.2022, 1.0),
            new MajorHub("Hub Quy Nhơn (Bình Định)", 13.7820, 109.2197, 1.1),
            new MajorHub("Hub Nha Trang (Khánh Hòa)", 12.2388, 109.1967, 1.1),
            new MajorHub("Hub Buôn Ma Thuột (Tây Nguyên)", 12.6667, 108.0383, 1.2),
            new MajorHub("Hub TP. Hồ Chí Minh", 10.8231, 106.6297, 1.0),
            new MajorHub("Hub Cần Thơ (Miền Tây)", 10.0452, 105.7469, 1.1)
    );

    @Getter
    public static class MajorHub {
        final String name;
        final double latitude;
        final double longitude;
        final double tierFactor;

        public MajorHub(String name, double latitude, double longitude, double tierFactor) {
            this.name = name;
            this.latitude = latitude;
            this.longitude = longitude;
            this.tierFactor = tierFactor;
        }
    }

    /**
     * Tính toán cước phí vận chuyển hoàn chỉnh và thông tin chi tiết tuyến đường
     */
    public ShippingCalculationResult calculateShippingDetails(Double destLat, Double destLon, String formattedAddress) {
        // Nếu thiếu tọa độ, fallback theo tọa độ trung tâm miền Bắc hoặc Thanh Hóa
        double targetLat = (destLat != null) ? destLat : 21.0285;
        double targetLon = (destLon != null) ? destLon : 105.8542;
        String cleanAddress = (formattedAddress != null) ? formattedAddress : "Việt Nam";

        // 1. Tính khoảng cách đường thẳng quy đổi sang đường bộ (Hệ số uốn lượn đường bộ ~1.25x)
        double directDistanceKm = calculateHaversineDistance(ORIGIN_LAT, ORIGIN_LON, targetLat, targetLon);
        double roadDistanceKm = Math.round(directDistanceKm * 1.25 * 10.0) / 10.0;

        // 2. Tìm Hub trung chuyển lớn gần người nhận nhất
        MajorHub nearestHub = findNearestHub(targetLat, targetLon);
        double distFromHubToDestKm = calculateHaversineDistance(nearestHub.latitude, nearestHub.longitude, targetLat, targetLon) * 1.25;

        // 3. Phân loại Zone Tier dựa trên địa chỉ và khoảng cách
        String normalizedAddr = removeAccents(cleanAddress).toLowerCase();
        boolean isLocalDistrict = normalizedAddr.contains("yen dinh") || normalizedAddr.contains("thieu hoa") || normalizedAddr.contains("tho xuan");
        boolean isLocalProvince = normalizedAddr.contains("thanh hoa");
        boolean isSpecialIsland = isSpecialIsland(normalizedAddr);
        boolean isRemoteMountain = isRemoteArea(normalizedAddr);
        boolean isMajorMetropolis = isMajorMetro(normalizedAddr);

        double baseFee;
        double calculatedFee;
        String zoneTier;
        String estimatedTime;

        if (isSpecialIsland) {
            // Huyện đảo đặc thù (Côn Đảo, Lý Sơn, Phú Quý...)
            zoneTier = "TIER_ISLAND (Huyện đảo đặc thù)";
            baseFee = 50000.0;
            calculatedFee = baseFee + (roadDistanceKm * 15.0);
            calculatedFee = Math.max(65000.0, Math.min(calculatedFee, 85000.0));
            estimatedTime = "3 - 5 ngày (Vận chuyển biển/đảo)";

        } else if (isLocalDistrict && roadDistanceKm < 30.0) {
            // Nội huyện / Lân cận kho Định Hòa
            zoneTier = "TIER_LOCAL (Nội huyện / Lân cận kho)";
            calculatedFee = 16000.0;
            estimatedTime = "Giao trong ngày / 24h";

        } else if (isLocalProvince && roadDistanceKm < 75.0) {
            // Nội tỉnh Thanh Hóa
            zoneTier = "TIER_PROVINCIAL (Nội tỉnh Thanh Hóa)";
            calculatedFee = 20000.0;
            estimatedTime = "24h - 48h";

        } else if (isMajorMetropolis) {
            // Các đô thị trung tâm lớn (HN, TP.HCM, Đà Nẵng, Hải Phòng, Vinh...) -> Trục xe tải lớn giá rẻ
            zoneTier = "TIER_1 (Đô thị trục lớn liên tỉnh)";
            if (roadDistanceKm < 250.0) {
                // Miền Bắc (Hà Nội, Hải Phòng, Ninh Bình, Vinh...)
                calculatedFee = 26000.0;
                estimatedTime = "24h - 48h";
            } else if (roadDistanceKm < 800.0) {
                // Miền Trung (Đà Nẵng, Huế...)
                calculatedFee = 32000.0;
                estimatedTime = "2 - 3 ngày";
            } else {
                // Miền Nam (TP.HCM, Cần Thơ, Bình Dương, Đồng Nai...)
                calculatedFee = 35000.0;
                estimatedTime = "2 - 4 ngày";
            }

        } else if (isRemoteMountain) {
            // Vùng cao / Tây Nguyên / Biên giới
            zoneTier = "TIER_3 (Huyện vùng cao / Vùng sâu / Tây Nguyên)";
            baseFee = 30000.0;
            double lineHaulFee = roadDistanceKm * 18.0;
            double lastMileSurcharge = Math.max(12000.0, distFromHubToDestKm * 40.0);
            calculatedFee = baseFee + (lineHaulFee * 0.4) + lastMileSurcharge;
            calculatedFee = Math.max(45000.0, Math.min(calculatedFee, 60000.0));
            estimatedTime = "3 - 5 ngày";

        } else {
            // Huyện/Xã đồng bằng các tỉnh khác (Tier 2)
            zoneTier = "TIER_2 (Thành phố/Huyện cấp tỉnh)";
            if (roadDistanceKm < 250.0) {
                calculatedFee = 28000.0;
                estimatedTime = "1 - 2 ngày";
            } else if (roadDistanceKm < 800.0) {
                calculatedFee = 36000.0;
                estimatedTime = "2 - 3 ngày";
            } else {
                calculatedFee = 42000.0;
                estimatedTime = "3 - 4 ngày";
            }
        }

        // Làm tròn đến hàng nghìn đồng (ví dụ 35.400đ -> 35.000đ)
        double roundedFee = Math.round(calculatedFee / 1000.0) * 1000.0;

        return ShippingCalculationResult.builder()
                .shippingFee(roundedFee)
                .distanceKm(roadDistanceKm)
                .originName(DEFAULT_ORIGIN_NAME)
                .destinationName(cleanAddress)
                .nearestHubName(nearestHub.name)
                .zoneTier(zoneTier)
                .estimatedDeliveryTime(estimatedTime)
                .build();
    }

    /**
     * Hàm tiện ích rút gọn: Chỉ lấy cước phí (Double)
     */
    public Double calculateShippingFee(Double destLat, Double destLon, String formattedAddress) {
        return calculateShippingDetails(destLat, destLon, formattedAddress).getShippingFee();
    }

    /**
     * Công thức Haversine tính khoảng cách đường chim bay giữa 2 tọa độ (km)
     */
    public double calculateHaversineDistance(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_KM * c;
    }

    private MajorHub findNearestHub(double lat, double lon) {
        MajorHub nearest = MAJOR_HUBS.get(0);
        double minDistance = Double.MAX_VALUE;

        for (MajorHub hub : MAJOR_HUBS) {
            double dist = calculateHaversineDistance(hub.latitude, hub.longitude, lat, lon);
            if (dist < minDistance) {
                minDistance = dist;
                nearest = hub;
            }
        }
        return nearest;
    }

    private boolean isSpecialIsland(String norm) {
        return norm.contains("con dao") || norm.contains("ly son") || norm.contains("phu quy")
                || norm.contains("bach long vi") || norm.contains("con co") || norm.contains("phu quoc");
    }

    private boolean isRemoteArea(String norm) {
        return norm.contains("cu m'gar") || norm.contains("meo vac") || norm.contains("dong van")
                || norm.contains("muong te") || norm.contains("dien bien") || norm.contains("lai chau")
                || norm.contains("ha giang") || norm.contains("cao bang") || norm.contains("kon tum")
                || norm.contains("dak lak") || norm.contains("dak nong") || norm.contains("gia lai");
    }

    private boolean isMajorMetro(String norm) {
        return norm.contains("ha noi") || norm.contains("ho chi minh") || norm.contains("tphcm")
                || norm.contains("da nang") || norm.contains("hai phong") || norm.contains("can tho")
                || norm.contains("tp vinh") || norm.contains("tp thanh hoa");
    }

    private String removeAccents(String input) {
        if (input == null) return "";
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFD);
        Pattern pattern = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
        return pattern.matcher(normalized).replaceAll("").replace('đ', 'd').replace('Đ', 'D');
    }
}
