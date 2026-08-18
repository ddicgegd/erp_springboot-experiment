package com.ddicg.erp.core.common.service;

import com.ddicg.erp.core.common.dto.response.ResolvedAddress;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.text.Normalizer;
import java.time.Duration;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AddressResolutionService {

    RestClient nominatimClient;
    RestClient photonClient;
    Map<String, AdminNode> provinceIndex = new HashMap<>();
    Map<String, AdminNode> districtIndex = new HashMap<>();

    public AddressResolutionService(
            @Value("${geocoding.nominatim.url}") String baseUrl,
            @Value("${geocoding.user-agent}") String userAgent) {

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(4));
        factory.setReadTimeout(Duration.ofSeconds(4));

        this.nominatimClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .defaultHeader("User-Agent", userAgent)
                .defaultHeader("Accept-Language", "vi,en")
                .build();

        this.photonClient = RestClient.builder()
                .baseUrl("https://photon.komoot.io")
                .requestFactory(factory)
                .defaultHeader("User-Agent", userAgent)
                .build();

        initializeVietnamAdministrativeIndex();
    }

    /**
     * Forward Geocoding: Phân giải địa chỉ đa tầng kết hợp Clean Synthesizer và Zero-Cost Geocoding.
     */
    public ResolvedAddress resolve(String rawAddress) {
        if (rawAddress == null || rawAddress.isBlank()) {
            return ResolvedAddress.builder()
                    .success(false)
                    .rawAddress(rawAddress)
                    .error("Address must not be null or blank")
                    .build();
        }

        String normalizedAddress = normalizeWhitespace(rawAddress);

        if (!hasMinimalAddressCriteria(normalizedAddress)) {
            log.warn("Input does not satisfy minimal address criteria: [{}]", normalizedAddress);
            return ResolvedAddress.builder()
                    .success(false)
                    .rawAddress(rawAddress)
                    .error("Input does not satisfy minimal address criteria")
                    .build();
        }

        // TẦNG 1: Bóc tách cấu trúc địa giới hành chính (Province -> District -> Ward -> Street -> HouseNumber)
        ParsedHierarchy hierarchy = parseHierarchy(normalizedAddress);

        // Trường hợp 1: Không nhận diện được Tỉnh/Thành phố nào hợp lệ tại Việt Nam
        if (hierarchy.province == null) {
            log.warn("No valid Vietnam province/city recognized for address: [{}]", normalizedAddress);
            return ResolvedAddress.builder()
                    .success(false)
                    .rawAddress(rawAddress)
                    .error("Location not found")
                    .build();
        }

        // TẦNG 2: Nếu có thông tin đường/phố cụ thể, ưu tiên tìm tọa độ cấp Tuyến đường / Số nhà
        if (hierarchy.hasSpecificStreet()) {
            ResolvedAddress exactStreetAddress = resolveExactStreetLevel(hierarchy, rawAddress);
            if (exactStreetAddress != null && exactStreetAddress.isSuccess()) {
                return exactStreetAddress;
            }
        }

        // Trường hợp 2: Thôn/Xã/Đường bị sai hoặc ảo, tự động lùi về tâm Quận/Huyện hợp lệ
        if (hierarchy.district != null) {
            log.info("Fallback to District level: [{} - {}]", hierarchy.district.name, hierarchy.province.name);
            return ResolvedAddress.builder()
                    .success(true)
                    .rawAddress(rawAddress)
                    .latitude(hierarchy.district.latitude)
                    .longitude(hierarchy.district.longitude)
                    .formattedAddress(synthesizeCleanAddress(null, null, null, hierarchy.district.name, hierarchy.province.name))
                    .build();
        }

        // Trường hợp 3: Cả Đường, Xã và Huyện đều sai, tự động lùi về tâm Tỉnh/Thành phố hợp lệ
        log.info("Fallback to Province level: [{}]", hierarchy.province.name);
        return ResolvedAddress.builder()
                .success(true)
                .rawAddress(rawAddress)
                .latitude(hierarchy.province.latitude)
                .longitude(hierarchy.province.longitude)
                .formattedAddress(hierarchy.province.name + ", Việt Nam")
                .build();
    }

    /**
     * Reverse Geocoding: Phân giải tọa độ (lat, lon) ngược lại thành địa chỉ thực tế (chỉ chấp nhận trong lãnh thổ Việt Nam).
     */
    public ResolvedAddress reverse(Double latitude, Double longitude) {
        if (!isValidCoordinate(latitude, longitude)) {
            log.warn("Invalid coordinate input for reverse geocoding: [lat={}, lon={}]", latitude, longitude);
            return ResolvedAddress.builder()
                    .success(false)
                    .latitude(latitude)
                    .longitude(longitude)
                    .error("Invalid geographic coordinates")
                    .build();
        }

        if (!isInsideVietnam(latitude, longitude)) {
            log.warn("Coordinates are outside Vietnam territory: [lat={}, lon={}]", latitude, longitude);
            return ResolvedAddress.builder()
                    .success(false)
                    .latitude(latitude)
                    .longitude(longitude)
                    .error("Coordinates are outside Vietnam territory")
                    .build();
        }

        try {
            NominatimPlace place = nominatimClient.get()
                    .uri(uri -> uri.path("/reverse")
                            .queryParam("lat", latitude)
                            .queryParam("lon", longitude)
                            .queryParam("countrycodes", "vn")
                            .queryParam("format", "json")
                            .queryParam("addressdetails", "1")
                            .build())
                    .retrieve()
                    .body(NominatimPlace.class);

            if (place == null || place.displayName() == null || place.displayName().isBlank()) {
                log.warn("No address found for coordinates [lat={}, lon={}]", latitude, longitude);
                return ResolvedAddress.builder()
                        .success(false)
                        .latitude(latitude)
                        .longitude(longitude)
                        .error("Location address not found")
                        .build();
            }

            return ResolvedAddress.builder()
                    .success(true)
                    .latitude(latitude)
                    .longitude(longitude)
                    .formattedAddress(place.displayName())
                    .rawAddress(place.displayName())
                    .build();

        } catch (Exception ex) {
            log.error("Reverse geocoding failed for [lat={}, lon={}]: {}", latitude, longitude, ex.getMessage());
            return ResolvedAddress.builder()
                    .success(false)
                    .latitude(latitude)
                    .longitude(longitude)
                    .error("Reverse geocoding error: " + ex.getMessage())
                    .build();
        }
    }

    private ResolvedAddress resolveExactStreetLevel(ParsedHierarchy hierarchy, String rawAddress) {
        String cleanFormatted = synthesizeCleanAddress(
                hierarchy.houseNumber,
                hierarchy.street,
                hierarchy.ward,
                hierarchy.district != null ? hierarchy.district.name : null,
                hierarchy.province.name
        );

        // Chiến lược 1: Thử Photon Engine với tọa độ neo (Biased Geocoder)
        try {
            String photonQuery = (hierarchy.houseNumber != null ? hierarchy.houseNumber + " " : "")
                    + hierarchy.street + ", "
                    + (hierarchy.ward != null ? hierarchy.ward + ", " : "")
                    + (hierarchy.district != null ? hierarchy.district.name + ", " : "")
                    + hierarchy.province.name;

            PhotonFeatureCollection photonResp = photonClient.get()
                    .uri(uri -> uri.path("/api/")
                            .queryParam("q", photonQuery)
                            .queryParam("lat", hierarchy.province.latitude)
                            .queryParam("lon", hierarchy.province.longitude)
                            .queryParam("limit", 3)
                            .build())
                    .retrieve()
                    .body(PhotonFeatureCollection.class);

            if (photonResp != null && photonResp.features() != null && !photonResp.features().isEmpty()) {
                for (PhotonFeature feature : photonResp.features()) {
                    if (feature.geometry() != null && feature.geometry().coordinates() != null && feature.geometry().coordinates().size() >= 2) {
                        Double lon = feature.geometry().coordinates().get(0);
                        Double lat = feature.geometry().coordinates().get(1);

                        if (isInsideValidBoundary(lat, lon, hierarchy)) {
                            log.info("Resolved Street level via Photon: [query={}, lat={}, lon={}]", photonQuery, lat, lon);
                            return ResolvedAddress.builder()
                                    .success(true)
                                    .rawAddress(rawAddress)
                                    .formattedAddress(cleanFormatted)
                                    .latitude(lat)
                                    .longitude(lon)
                                    .build();
                        }
                    }
                }
            }
        } catch (Exception ignored) {}

        // Chiến lược 2: Nominatim Search theo query kết hợp
        try {
            String query = hierarchy.street + (hierarchy.district != null ? ", " + hierarchy.district.name : "") + ", " + hierarchy.province.name;
            List<NominatimPlace> places = nominatimClient.get()
                    .uri(uri -> uri.path("/search")
                            .queryParam("q", query)
                            .queryParam("countrycodes", "vn")
                            .queryParam("format", "json")
                            .queryParam("limit", 1)
                            .build())
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<NominatimPlace>>() {});

            if (places != null && !places.isEmpty()) {
                NominatimPlace place = places.get(0);
                Double lat = Double.valueOf(place.lat());
                Double lon = Double.valueOf(place.lon());

                if (isInsideValidBoundary(lat, lon, hierarchy)) {
                    log.info("Resolved Street level via Nominatim: [query={}, lat={}, lon={}]", query, lat, lon);
                    return ResolvedAddress.builder()
                            .success(true)
                            .rawAddress(rawAddress)
                            .formattedAddress(cleanFormatted)
                            .latitude(lat)
                            .longitude(lon)
                            .build();
                }
            }
        } catch (Exception ignored) {}

        return null;
    }

    private String synthesizeCleanAddress(String houseNumber, String street, String ward, String district, String province) {
        StringBuilder sb = new StringBuilder();
        if (houseNumber != null && !houseNumber.isBlank()) {
            sb.append(houseNumber).append(" ");
        }
        if (street != null && !street.isBlank()) {
            sb.append(street);
        }
        if (ward != null && !ward.isBlank()) {
            if (!sb.isEmpty()) sb.append(", ");
            if (!ward.toLowerCase().startsWith("phường") && !ward.toLowerCase().startsWith("xã")) {
                sb.append("Phường ");
            }
            sb.append(ward);
        }
        if (district != null && !district.isBlank()) {
            if (!sb.isEmpty()) sb.append(", ");
            sb.append(district);
        }
        if (province != null && !province.isBlank()) {
            if (!sb.isEmpty()) sb.append(", ");
            sb.append(province);
        }
        if (!sb.isEmpty()) {
            sb.append(", Việt Nam");
        }
        return sb.toString();
    }

    private boolean isInsideValidBoundary(Double lat, Double lon, ParsedHierarchy hierarchy) {
        if (!isValidCoordinate(lat, lon) || !isInsideVietnam(lat, lon)) return false;
        if (hierarchy.district != null && !hierarchy.district.contains(lat, lon)) {
            log.warn("Discarding out-of-boundary result [lat={}, lon={}] for District [{}]", lat, lon, hierarchy.district.name);
            return false;
        }
        if (hierarchy.province != null && !hierarchy.province.contains(lat, lon)) {
            log.warn("Discarding out-of-boundary result [lat={}, lon={}] for Province [{}]", lat, lon, hierarchy.province.name);
            return false;
        }
        return true;
    }

    private ParsedHierarchy parseHierarchy(String input) {
        String clean = input.replaceAll("(?i)\\b(Việt Nam|Vietnam)\\b", "").trim();
        clean = clean.replaceAll(",\\s*$", "");

        String[] rawSegments = clean.split(",");
        List<String> segments = new ArrayList<>();
        for (String seg : rawSegments) {
            String s = seg.trim();
            if (!s.isBlank()) segments.add(s);
        }

        AdminNode foundProvince = null;
        int provinceIndexInSeg = -1;

        for (int i = segments.size() - 1; i >= 0; i--) {
            AdminNode node = findProvinceMatch(segments.get(i));
            if (node != null) {
                foundProvince = node;
                provinceIndexInSeg = i;
                break;
            }
        }

        if (foundProvince == null) {
            for (Map.Entry<String, AdminNode> entry : provinceIndex.entrySet()) {
                if (matchesWordBoundary(clean, entry.getKey())) {
                    foundProvince = entry.getValue();
                    break;
                }
            }
        }

        if (foundProvince == null) {
            return new ParsedHierarchy(null, null, null, null, null);
        }

        AdminNode foundDistrict = null;
        int districtIndexInSeg = -1;
        int maxScanIndex = (provinceIndexInSeg >= 0) ? provinceIndexInSeg - 1 : segments.size() - 1;

        for (int i = maxScanIndex; i >= 0; i--) {
            AdminNode dNode = findDistrictMatch(segments.get(i), foundProvince.name);
            if (dNode != null) {
                foundDistrict = dNode;
                districtIndexInSeg = i;
                break;
            }
        }

        if (foundDistrict == null) {
            for (Map.Entry<String, AdminNode> entry : districtIndex.entrySet()) {
                if (entry.getValue().parentProvince.equalsIgnoreCase(foundProvince.name)
                        && matchesWordBoundary(clean, entry.getKey())) {
                    foundDistrict = entry.getValue();
                    break;
                }
            }
        }

        // Bóc tách Số nhà, Tên đường và Phường/Xã
        String houseNumber = null;
        String street = null;
        String ward = null;

        if (!segments.isEmpty()) {
            String candidateStreet = segments.get(0);
            if ((foundDistrict == null || !cleanKey(candidateStreet).equals(cleanKey(foundDistrict.name)))
                    && !cleanKey(candidateStreet).equals(cleanKey(foundProvince.name))) {

                // Trích xuất số nhà
                Pattern numPattern = Pattern.compile("(?i)(?:Số\\s+)?(\\d+[a-zA-Z]?(?:/\\d+[a-zA-Z]?)?)");
                Matcher m = numPattern.matcher(candidateStreet);
                if (m.find()) {
                    houseNumber = m.group(1);
                }

                street = extractCleanStreetName(candidateStreet);
            }

            // Kiểm tra phân đoạn 1 xem có phải Phường/Xã không
            if (segments.size() > 1 && (districtIndexInSeg < 0 || districtIndexInSeg > 1)) {
                String candidateWard = segments.get(1);
                if (candidateWard.matches("(?i).*(Phường|Xã|Thị trấn).*")
                        || (foundDistrict != null && !cleanKey(candidateWard).equals(cleanKey(foundDistrict.name)))) {
                    ward = candidateWard.replaceAll("(?i)\\b(Phường|Xã|Thị trấn)\\s*", "").trim();
                }
            }
        }

        return new ParsedHierarchy(foundProvince, foundDistrict, ward, street, houseNumber);
    }

    private String extractCleanStreetName(String rawStreet) {
        if (rawStreet == null || rawStreet.isBlank()) return null;
        if (rawStreet.matches("(?i)^(Thôn|Xóm|Làng|Ấp|Bản|Xã|Tổ\\s+\\d+).*")) {
            return null;
        }
        String cleaned = rawStreet
                .replaceAll("(?i)\\b(Số\\s+\\d+[a-zA-Z]?|Đường\\s+|Phố\\s+|Ngõ\\s+\\d+[a-zA-Z]?|Hẻm\\s+\\d+[a-zA-Z]?|\\d+[a-zA-Z]?)\\s*", "")
                .replaceAll("(?i)\\b(Phường|Xã|Thị trấn|Quận|Huyện|TP\\.|TP|Q\\.)\\s+.*$", "")
                .trim();
        return cleaned.isBlank() ? null : cleaned;
    }

    private AdminNode findProvinceMatch(String segment) {
        String key = cleanKey(segment);
        return provinceIndex.get(key);
    }

    private AdminNode findDistrictMatch(String segment, String provinceName) {
        String key = cleanKey(segment);
        AdminNode node = districtIndex.get(key);
        if (node != null && node.parentProvince.equalsIgnoreCase(provinceName)) {
            return node;
        }
        return null;
    }

    private String cleanKey(String input) {
        String s = stripAdministrativePrefixes(input).trim();
        return removeAccents(s).toLowerCase();
    }

    private String removeAccents(String input) {
        if (input == null) return "";
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFD);
        Pattern pattern = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
        return pattern.matcher(normalized).replaceAll("").replace('đ', 'd').replace('Đ', 'D');
    }

    private boolean matchesWordBoundary(String text, String key) {
        String normalizedText = removeAccents(text).toLowerCase();
        String pattern = "(?i).*\\b" + Pattern.quote(key) + "\\b.*";
        return normalizedText.matches(pattern);
    }

    private String normalizeWhitespace(String input) {
        return input.trim().replaceAll("\\s+", " ");
    }

    private String stripAdministrativePrefixes(String input) {
        return input
                .replaceAll("(?i)\\b(Số\\s+\\d+[a-zA-Z]?|Đường\\s+|Phố\\s+|Phường\\s+|Xã\\s+|Thị trấn\\s+|Quận\\s+|Huyện\\s+|Thị xã\\s+|Thành phố\\s+|Tỉnh\\s+|TP\\.\\s*|TP\\s+|TX\\.\\s*|TX\\s+|Q\\.\\s*|Q\\s+)", "")
                .replaceAll("\\s+,", ",")
                .replaceAll(",\\s*,", ",")
                .replaceAll("^\\s*,|\\s*,\\s*$", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private boolean hasMinimalAddressCriteria(String address) {
        if (address == null || address.length() < 3) {
            return false;
        }
        long letterCount = address.codePoints().filter(Character::isLetter).count();
        return letterCount >= 2;
    }

    private boolean isValidCoordinate(Double lat, Double lon) {
        return lat != null && lon != null
                && lat >= -90.0 && lat <= 90.0
                && lon >= -180.0 && lon <= 180.0;
    }

    public boolean isInsideVietnam(Double lat, Double lon) {
        return lat != null && lon != null
                && lat >= 8.0 && lat <= 23.5
                && lon >= 102.0 && lon <= 117.5;
    }

    private void initializeVietnamAdministrativeIndex() {
        // Tỉnh/Thành phố Việt Nam kèm tọa độ và Bounding Box
        addProvince("Hà Nội", 21.028511, 105.854444, 20.50, 21.40, 105.20, 106.10, "ha noi", "hn", "thanh pho ha noi", "tp ha noi", "tp. ha noi");
        addProvince("Hồ Chí Minh", 10.776889, 106.700806, 10.30, 11.20, 106.30, 107.10, "ho chi minh", "hcm", "tp hcm", "tp. hcm", "tphcm", "sai gon", "saigon", "thanh pho ho chi minh");
        addProvince("Đà Nẵng", 16.047079, 108.206230, 15.90, 16.25, 107.90, 108.40, "da nang", "tp da nang", "tp. da nang", "thanh pho da nang");
        addProvince("Hải Phòng", 20.844912, 106.688084, 20.60, 21.05, 106.40, 107.10, "hai phong", "tp hai phong", "thanh pho hai phong");
        addProvince("Cần Thơ", 10.045162, 105.746857, 9.90, 10.35, 105.20, 105.90, "can tho", "tp can tho", "thanh pho can tho");
        addProvince("Bắc Ninh", 21.186096, 106.076317, 21.00, 21.30, 105.90, 106.35, "bac ninh", "tinh bac ninh");
        addProvince("Bình Dương", 11.166667, 106.666667, 10.85, 11.50, 106.40, 107.05, "binh duong", "tinh binh duong");
        addProvince("Đồng Nai", 10.957444, 106.842713, 10.50, 11.60, 106.70, 107.60, "dong nai", "tinh dong nai");
        addProvince("Quảng Ninh", 21.006389, 107.292500, 20.70, 21.70, 106.50, 108.10, "quang ninh", "tinh quang ninh");
        addProvince("Thanh Hóa", 19.806692, 105.785181, 19.20, 20.70, 104.50, 106.10, "thanh hoa", "tinh thanh hoa");
        addProvince("Nghệ An", 18.673398, 105.681328, 18.50, 20.00, 103.80, 106.00, "nghe an", "tinh nghe an");
        addProvince("Khánh Hòa", 12.238791, 109.196749, 11.70, 12.90, 108.60, 109.50, "khanh hoa", "tinh khanh hoa", "nha trang");
        addProvince("Lâm Đồng", 11.940419, 108.458313, 11.30, 12.40, 107.30, 108.80, "lam dong", "tinh lam dong", "da lat");
        addProvince("Thừa Thiên Huế", 16.463713, 107.590866, 16.00, 16.80, 107.00, 108.20, "thua thien hue", "hue", "tinh thua thien hue");
        addProvince("Bà Rịa - Vũng Tàu", 10.411389, 107.136389, 10.20, 10.80, 106.80, 107.60, "ba ria vung tau", "vung tau", "ba ria - vung tau");

        // Các Quận/Huyện tiêu biểu kèm Bounding Box
        addDistrict("Ba Đình", 21.0341, 105.8242, 21.01, 21.06, 105.80, 105.86, "Hà Nội", "ba dinh", "q ba dinh", "quan ba dinh");
        addDistrict("Hoàn Kiếm", 21.0307, 105.8524, 21.01, 21.05, 105.83, 105.88, "Hà Nội", "hoan kiem", "q hoan kiem", "quan hoan kiem");
        addDistrict("Đông Anh", 21.1444, 105.8394, 21.10, 21.22, 105.75, 105.95, "Hà Nội", "dong anh", "h dong anh", "huyen dong anh");
        addDistrict("Cầu Giấy", 21.0313, 105.7938, 21.01, 21.05, 105.77, 105.82, "Hà Nội", "cau giay", "q cau giay", "quan cau giay");
        addDistrict("Đống Đa", 21.0181, 105.8272, 21.00, 21.04, 105.80, 105.85, "Hà Nội", "dong da", "q dong da", "quan dong da");

        addDistrict("Quận 1", 10.7756, 106.7004, 10.75, 10.80, 106.68, 106.72, "Hồ Chí Minh", "quan 1", "q1", "q.1", "1");
        addDistrict("Quận 3", 10.7844, 106.6844, 10.76, 10.80, 106.66, 106.70, "Hồ Chí Minh", "quan 3", "q3", "q.3", "3");
        addDistrict("Quận 7", 10.7340, 106.7219, 10.71, 10.76, 106.70, 106.76, "Hồ Chí Minh", "quan 7", "q7", "q.7", "7");
        addDistrict("Thủ Đức", 10.8494, 106.7537, 10.78, 10.90, 106.70, 106.85, "Hồ Chí Minh", "thu duc", "tp thu duc", "tp. thu duc", "thanh pho thu duc");
        addDistrict("Bình Thạnh", 10.8106, 106.7091, 10.78, 10.84, 106.68, 106.74, "Hồ Chí Minh", "binh thanh", "q binh thanh", "quan binh thanh");

        addDistrict("Hải Châu", 16.0594, 108.2208, 16.02, 16.09, 108.20, 108.25, "Đà Nẵng", "hai chau", "q hai chau", "quan hai chau");
        addDistrict("Từ Sơn", 21.1186, 105.9647, 21.08, 21.16, 105.92, 106.02, "Bắc Ninh", "tu son", "tx tu son", "tp tu son");
        addDistrict("Yên Phong", 21.2058, 105.9861, 21.15, 21.25, 105.92, 106.05, "Bắc Ninh", "yen phong", "h yen phong", "huyen yen phong");
    }

    private void addProvince(String canonicalName, Double lat, Double lon, Double minLat, Double maxLat, Double minLon, Double maxLon, String... aliases) {
        AdminNode node = new AdminNode(canonicalName, lat, lon, minLat, maxLat, minLon, maxLon, null);
        for (String alias : aliases) {
            provinceIndex.put(removeAccents(alias).toLowerCase().trim(), node);
        }
        provinceIndex.put(removeAccents(canonicalName).toLowerCase().trim(), node);
    }

    private void addDistrict(String canonicalName, Double lat, Double lon, Double minLat, Double maxLat, Double minLon, Double maxLon, String parentProvince, String... aliases) {
        AdminNode node = new AdminNode(canonicalName, lat, lon, minLat, maxLat, minLon, maxLon, parentProvince);
        for (String alias : aliases) {
            districtIndex.put(removeAccents(alias).toLowerCase().trim(), node);
        }
        districtIndex.put(removeAccents(canonicalName).toLowerCase().trim(), node);
    }

    private record AdminNode(
            String name,
            Double latitude,
            Double longitude,
            Double minLat,
            Double maxLat,
            Double minLon,
            Double maxLon,
            String parentProvince
    ) {
        boolean contains(Double lat, Double lon) {
            if (lat == null || lon == null) return false;
            return lat >= minLat && lat <= maxLat && lon >= minLon && lon <= maxLon;
        }
    }

    private record ParsedHierarchy(AdminNode province, AdminNode district, String ward, String street, String houseNumber) {
        boolean hasSpecificStreet() {
            return street != null && !street.isBlank();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record NominatimPlace(
            String lat,
            String lon,
            @JsonProperty("display_name") String displayName
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record PhotonFeatureCollection(
            List<PhotonFeature> features
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record PhotonFeature(
            PhotonGeometry geometry
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record PhotonGeometry(
            List<Double> coordinates
    ) {}
}
