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

        // Bắt buộc 1: Phải nhận diện được Tỉnh/Thành phố hợp lệ tại Việt Nam
        if (hierarchy.province == null) {
            log.warn("No valid Vietnam province/city recognized for address: [{}]", normalizedAddress);
            return ResolvedAddress.builder()
                    .success(false)
                    .rawAddress(rawAddress)
                    .error("Không nhận diện được Tỉnh/Thành phố hợp lệ tại Việt Nam.")
                    .build();
        }

        // Bắt buộc 2: Phải nhận diện được Quận/Huyện/Thị xã hợp lệ
        if (hierarchy.district == null) {
            log.warn("No valid Vietnam district recognized for address: [{}]", normalizedAddress);
            return ResolvedAddress.builder()
                    .success(false)
                    .rawAddress(rawAddress)
                    .error("Địa chỉ thiếu hoặc không nhận diện được Quận/Huyện/Thị xã hợp lệ.")
                    .build();
        }

        // TẦNG 2: Nếu có thông tin đường/phố/ngõ/làng cụ thể, ưu tiên tìm tọa độ cấp Tuyến đường / Số nhà
        if (hierarchy.hasSpecificStreet()) {
            ResolvedAddress exactStreetAddress = resolveExactStreetLevel(hierarchy, rawAddress);
            if (exactStreetAddress != null && exactStreetAddress.isSuccess()) {
                return exactStreetAddress;
            }
        }

        // Bắt buộc 3: Phải có ít nhất cấp Phường/Xã/Thị trấn HOẶC Tuyến đường/Ngõ/Thôn/Làng (Ngoại lệ: Huyện đảo đặc thù)
        boolean isIslandSpecialDistrict = isSpecialIslandDistrictWithoutWards(hierarchy.district);
        if (hierarchy.ward != null || hierarchy.hasSpecificStreet() || isIslandSpecialDistrict) {
            log.info("Resolved at District/Ward level: [ward={}, street={}, district={}, province={}]",
                    hierarchy.ward, hierarchy.street, hierarchy.district.name, hierarchy.province.name);
            return ResolvedAddress.builder()
                    .success(true)
                    .rawAddress(rawAddress)
                    .latitude(hierarchy.district.latitude)
                    .longitude(hierarchy.district.longitude)
                    .formattedAddress(synthesizeCleanAddress(hierarchy.houseNumber, hierarchy.street, hierarchy.ward, hierarchy.district.name, hierarchy.province.name))
                    .build();
        }

        // Từ chối nếu chỉ có Tỉnh + Huyện mà thiếu hoàn toàn cấp Xã/Phường/Đường/Ngõ/Làng
        log.warn("Address lacks ward, street, alley or village level: [{}]", normalizedAddress);
        return ResolvedAddress.builder()
                .success(false)
                .rawAddress(rawAddress)
                .error("Địa chỉ chưa đủ chi tiết (yêu cầu tối thiểu có Phường/Xã/Thị trấn hoặc Tuyến đường/Ngõ/Làng/KCN).")
                .build();
    }

    private boolean isSpecialIslandDistrictWithoutWards(AdminNode district) {
        if (district == null) return false;
        String name = removeAccents(district.name).toLowerCase();
        return name.contains("con dao") || name.contains("ly son") || name.contains("bach long vi") || name.contains("phu quy") || name.contains("con co") || name.contains("hoang sa") || name.contains("truong sa");
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

    private String capitalizeWords(String str) {
        if (str == null || str.isBlank()) return str;
        String[] words = str.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (w.isEmpty()) continue;
            if (!sb.isEmpty()) sb.append(" ");
            sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1).toLowerCase());
        }
        return sb.toString();
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

        // Tách phần District dính liền với Province trong cùng một segment (ví dụ: "yên định thanh hóa", "bình chánh tphcm")
        if (provinceIndexInSeg >= 0 && provinceIndexInSeg < segments.size()) {
            String seg = segments.get(provinceIndexInSeg);
            String segCleanKey = cleanKey(seg);
            String provCleanKey = cleanKey(foundProvince.name);
            if (!segCleanKey.equals(provCleanKey) && segCleanKey.endsWith(provCleanKey)) {
                int cutIdx = seg.toLowerCase().lastIndexOf(foundProvince.name.toLowerCase());
                if (cutIdx <= 0) {
                    cutIdx = seg.length() - foundProvince.name.length();
                }
                if (cutIdx > 0) {
                    String prefix = seg.substring(0, cutIdx).trim().replaceAll("(?i)(Tỉnh|Thành\\s*phố|TP\\.|TP|[,\\s-])+$", "").trim();
                    if (!prefix.isBlank()) {
                        segments.set(provinceIndexInSeg, prefix);
                        segments.add(provinceIndexInSeg + 1, foundProvince.name);
                    }
                }
            }
        }

        AdminNode foundDistrict = null;
        int districtIndexInSeg = -1;
        int maxScanIndex = (provinceIndexInSeg >= 0) ? Math.min(provinceIndexInSeg, segments.size() - 1) : segments.size() - 1;

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

        // Nếu District không nằm trong cache tĩnh nhưng có tiền tố Quận/Huyện/Thị xã rõ ràng
        if (foundDistrict == null) {
            for (int i = maxScanIndex; i >= 0; i--) {
                String seg = segments.get(i).trim();
                Matcher districtMatcher = Pattern.compile("(?i)^(?:Quận|Huyện|Thị\\s*xã|TP\\.|Thành\\s*phố)\\s+([A-Za-zÀ-ỹ\\s\\d]+)$").matcher(seg);
                if (districtMatcher.matches()) {
                    String rawDistrictName = districtMatcher.group(1).trim();
                    String key = cleanKey(rawDistrictName);
                    // Lọc bỏ từ khóa rác rõ ràng như 'ảo', 'test'
                    if (!key.isBlank() && !key.equals("ao") && !key.equals("test") && !key.contains("khong ton tai") && !key.contains("khong co that")) {
                        foundDistrict = new AdminNode(
                                seg,
                                foundProvince.latitude,
                                foundProvince.longitude,
                                foundProvince.minLat,
                                foundProvince.maxLat,
                                foundProvince.minLon,
                                foundProvince.maxLon,
                                foundProvince.name
                        );
                        districtIndexInSeg = i;
                        break;
                    }
                }
            }
        }

        // Bóc tách District nếu dính liền vào segment trước đó (ví dụ: "02 ngõ nghè xã định hòa huyện yên định")
        if (foundDistrict != null && districtIndexInSeg < 0) {
            String distCleanKey = cleanKey(foundDistrict.name);
            for (int i = 0; i < segments.size(); i++) {
                String seg = segments.get(i);
                String segKey = cleanKey(seg);
                if (segKey.contains(distCleanKey)) {
                    int cutIdx = seg.toLowerCase().lastIndexOf(foundDistrict.name.toLowerCase());
                    if (cutIdx <= 0) {
                        String rawNoPrefix = foundDistrict.name.replaceAll("(?i)^(Quận|Huyện|Thị\\s*xã|TP\\.|TP|Thành\\s*phố)\\s+", "").trim();
                        cutIdx = seg.toLowerCase().lastIndexOf(rawNoPrefix.toLowerCase());
                    }
                    if (cutIdx > 0) {
                        String prefix = seg.substring(0, cutIdx).trim().replaceAll("(?i)(Huyện|Quận|Thị\\s*xã|TP\\.|Thành\\s*phố|[,\\s-])+$", "").trim();
                        if (!prefix.isBlank()) {
                            segments.set(i, prefix);
                            segments.add(i + 1, foundDistrict.name);
                            districtIndexInSeg = i + 1;
                        }
                    } else if (cutIdx == 0) {
                        districtIndexInSeg = i;
                    }
                    break;
                }
            }
        }

        // Bóc tách Số nhà, Tên đường và Phường/Xã/Thôn/Làng/Lành/Ấp/Bản/Buôn/KCN
        String houseNumber = null;
        String street = null;
        String ward = null;

        if (!segments.isEmpty()) {
            String firstSeg = segments.get(0).trim();

            // Kiểm tra bóc tách Phường/Xã/Thị trấn/Thôn/Làng nằm ở đoạn giữa hoặc cuối segment (ví dụ: "02 ngõ nghè xã định hòa")
            Pattern inlineWardPattern = Pattern.compile("(?i)(?:^|[\\s,.-]+)(Phường|Xã|Thị\\s*trấn|Thôn|Làng|Lành|Ấp|Bản|Buôn|Xóm|Tổ|KDC|KĐT|KCN|Lô)\\s+([A-Za-zÀ-ỹ\\d\\s]+)$");
            Matcher wardMatcher = inlineWardPattern.matcher(firstSeg);

            if (wardMatcher.find()) {
                String prefixType = capitalizeWords(wardMatcher.group(1));
                String wardName = capitalizeWords(wardMatcher.group(2));
                ward = prefixType + " " + wardName;

                String remainingStreet = firstSeg.substring(0, wardMatcher.start()).trim();
                if (!remainingStreet.isBlank()) {
                    Pattern numPattern = Pattern.compile("(?i)(?:Số\\s+|Ngõ\\s+|Ngách\\s+|Hẻm\\s+|Lô\\s+)?(\\d+[a-zA-Z]?(?:/\\d+[a-zA-Z]?)?)");
                    Matcher m = numPattern.matcher(remainingStreet);
                    if (m.find()) {
                        houseNumber = m.group(1);
                    }
                    street = extractCleanStreetName(remainingStreet);
                }
            } else if (firstSeg.matches("(?i)^\\s*(Phường|Xã|Thị\\s*trấn|Thôn|Làng|Lành|Ấp|Bản|Buôn|Xóm|Tổ|KDC|KĐT|KCN|Lô|Căn\\s*hộ|Tòa)\\s+.*")) {
                ward = capitalizeWords(firstSeg);
            } else if ((foundDistrict == null || !cleanKey(firstSeg).equals(cleanKey(foundDistrict.name)))
                    && !cleanKey(firstSeg).equals(cleanKey(foundProvince.name))) {

                Pattern numPattern = Pattern.compile("(?i)(?:Số\\s+|Ngõ\\s+|Ngách\\s+|Hẻm\\s+|Lô\\s+)?(\\d+[a-zA-Z]?(?:/\\d+[a-zA-Z]?)?)");
                Matcher m = numPattern.matcher(firstSeg);
                if (m.find()) {
                    houseNumber = m.group(1);
                }

                street = extractCleanStreetName(firstSeg);
            }

            // Kiểm tra các phân đoạn tiếp theo nếu chưa có ward
            if (ward == null) {
                for (int i = 1; i < segments.size(); i++) {
                    if (i == districtIndexInSeg || i == provinceIndexInSeg) continue;
                    String candidateWard = segments.get(i).trim();
                    if (candidateWard.matches("(?i).*(Phường|Xã|Thị\\s*trấn|Thôn|Làng|Lành|Ấp|Bản|Buôn|Xóm|Tổ|KDC|KĐT|KCN|Lô).*")
                            || (foundDistrict != null && !cleanKey(candidateWard).equals(cleanKey(foundDistrict.name)))) {
                        ward = capitalizeWords(candidateWard);
                        break;
                    }
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
                .replaceAll("(?i)^(Số\\s+\\d+[a-zA-Z]?|Đường\\s+|Phố\\s+|Ngõ\\s+\\d+[a-zA-Z]?|Hẻm\\s+\\d+[a-zA-Z]?|\\d+[a-zA-Z]?)\\s*", "")
                .replaceAll("(?i)\\s+(Phường|Xã|Thị\\s*trấn|Quận|Huyện|TP\\.|TP|Q\\.)\\s+.*$", "")
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
        // 63 Tỉnh/Thành phố Việt Nam kèm tọa độ và Bounding Box
        addProvince("Hà Nội", 21.028511, 105.854444, 20.50, 21.40, 105.20, 106.10, "ha noi", "hn", "thanh pho ha noi", "tp ha noi", "tp. ha noi", "ha tay");
        addProvince("Hồ Chí Minh", 10.776889, 106.700806, 10.30, 11.20, 106.30, 107.10, "ho chi minh", "hcm", "tp hcm", "tp. hcm", "tphcm", "sai gon", "saigon", "thanh pho ho chi minh");
        addProvince("Đà Nẵng", 16.047079, 108.206230, 15.90, 16.25, 107.90, 108.40, "da nang", "tp da nang", "tp. da nang", "thanh pho da nang", "dn");
        addProvince("Hải Phòng", 20.844912, 106.688084, 20.60, 21.05, 106.40, 107.10, "hai phong", "tp hai phong", "thanh pho hai phong", "hp");
        addProvince("Cần Thơ", 10.045162, 105.746857, 9.90, 10.35, 105.20, 105.90, "can tho", "tp can tho", "thanh pho can tho");
        addProvince("Bắc Ninh", 21.186096, 106.076317, 21.00, 21.30, 105.90, 106.35, "bac ninh", "tinh bac ninh", "bn");
        addProvince("Bình Dương", 11.166667, 106.666667, 10.85, 11.50, 106.40, 107.05, "binh duong", "tinh binh duong", "bd");
        addProvince("Đồng Nai", 10.957444, 106.842713, 10.50, 11.60, 106.70, 107.60, "dong nai", "tinh dong nai", "dnai");
        addProvince("Quảng Ninh", 21.006389, 107.292500, 20.70, 21.70, 106.50, 108.10, "quang ninh", "tinh quang ninh", "qn");
        addProvince("Thanh Hóa", 19.806692, 105.785181, 19.20, 20.70, 104.50, 106.10, "thanh hoa", "tinh thanh hoa", "th");
        addProvince("Nghệ An", 18.673398, 105.681328, 18.50, 20.00, 103.80, 106.00, "nghe an", "tinh nghe an", "na");
        addProvince("Khánh Hòa", 12.238791, 109.196749, 11.70, 12.90, 108.60, 109.50, "khanh hoa", "tinh khanh hoa", "nha trang");
        addProvince("Lâm Đồng", 11.940419, 108.458313, 11.30, 12.40, 107.30, 108.80, "lam dong", "tinh lam dong", "da lat");
        addProvince("Thừa Thiên Huế", 16.463713, 107.590866, 16.00, 16.80, 107.00, 108.20, "thua thien hue", "hue", "tinh thua thien hue", "tp hue");
        addProvince("Bà Rịa - Vũng Tàu", 10.411389, 107.136389, 10.20, 10.80, 106.80, 107.60, "ba ria vung tau", "vung tau", "ba ria - vung tau", "brvt");
        addProvince("Đắk Lắk", 12.666667, 108.050000, 12.10, 13.45, 107.45, 109.00, "dak lak", "dac lac", "daklak", "tinh dak lak", "buon ma thuot");
        addProvince("Hải Dương", 20.937222, 106.314444, 20.70, 21.25, 106.10, 106.65, "hai duong", "tinh hai duong", "hd");
        addProvince("Hưng Yên", 20.646389, 106.051111, 20.55, 21.05, 105.90, 106.25, "hung yen", "tinh hung yen", "hy");
        addProvince("Nam Định", 20.420000, 106.168333, 19.95, 20.60, 105.90, 106.60, "nam dinh", "tinh nam dinh", "nd");
        addProvince("Thái Bình", 20.446389, 106.336667, 20.30, 20.70, 106.15, 106.65, "thai binh", "tinh thai binh", "tb");
        addProvince("Vĩnh Phúc", 21.308889, 105.604722, 21.10, 21.60, 105.30, 105.80, "vinh phuc", "tinh vinh phuc", "vp");
        addProvince("Phú Thọ", 21.322778, 105.401944, 20.90, 21.75, 104.80, 105.50, "phu tho", "tinh phu tho", "pt");
        addProvince("Hà Nam", 20.545556, 105.912222, 20.35, 20.70, 105.75, 106.10, "ha nam", "tinh ha nam");
        addProvince("Ninh Bình", 20.250556, 105.974444, 20.00, 20.50, 105.50, 106.20, "ninh binh", "tinh ninh binh", "nb");
        addProvince("Quảng Nam", 15.566667, 108.000000, 14.95, 16.05, 107.20, 108.75, "quang nam", "tinh quang nam", "hoi an");
        addProvince("Quảng Ngãi", 15.120000, 108.800000, 14.50, 15.45, 108.10, 109.15, "quang ngai", "tinh quang ngai");
        addProvince("Bình Định", 13.776389, 109.223056, 13.50, 14.70, 108.60, 109.40, "binh dinh", "tinh binh dinh", "quy nhon");
        addProvince("Phú Yên", 13.088056, 109.313889, 12.70, 13.70, 108.65, 109.50, "phu yen", "tinh phu yen", "tuy hoa");
        addProvince("Kiên Giang", 10.013889, 105.080556, 9.40, 10.55, 104.40, 105.60, "kien giang", "tinh kien giang", "phu quoc", "rach gia");
        addProvince("An Giang", 10.383333, 105.416667, 10.20, 10.95, 104.75, 105.60, "an giang", "tinh an giang", "long xuyen", "chau doc");
        addProvince("Tiền Giang", 10.350000, 106.350000, 10.20, 10.60, 105.80, 106.80, "tien giang", "tinh tien giang", "my tho");
        addProvince("Bến Tre", 10.233333, 106.383333, 9.80, 10.40, 106.20, 106.80, "ben tre", "tinh ben tre");
        addProvince("Long An", 10.533333, 106.400000, 10.35, 11.00, 105.80, 106.80, "long an", "tinh long an", "tan an");

        // Các Quận/Huyện/Thị xã trọng điểm kèm Bounding Box
        // 1. Hà Nội
        addDistrict("Ba Đình", 21.0341, 105.8242, 21.01, 21.06, 105.80, 105.86, "Hà Nội", "ba dinh", "q ba dinh", "quan ba dinh");
        addDistrict("Hoàn Kiếm", 21.0307, 105.8524, 21.01, 21.05, 105.83, 105.88, "Hà Nội", "hoan kiem", "q hoan kiem", "quan hoan kiem");
        addDistrict("Đông Anh", 21.1444, 105.8394, 21.10, 21.22, 105.75, 105.95, "Hà Nội", "dong anh", "h dong anh", "huyen dong anh");
        addDistrict("Cầu Giấy", 21.0313, 105.7938, 21.01, 21.05, 105.77, 105.82, "Hà Nội", "cau giay", "q cau giay", "quan cau giay");
        addDistrict("Đống Đa", 21.0181, 105.8272, 21.00, 21.04, 105.80, 105.85, "Hà Nội", "dong da", "q dong da", "quan dong da");
        addDistrict("Gia Lâm", 21.0183, 105.9525, 20.95, 21.08, 105.88, 106.02, "Hà Nội", "gia lam", "h gia lam", "huyen gia lam");
        addDistrict("Hai Bà Trưng", 21.0069, 105.8553, 20.98, 21.02, 105.83, 105.88, "Hà Nội", "hai ba trung", "q hai ba trung", "quan hai ba trung");
        addDistrict("Thanh Xuân", 20.9980, 105.8119, 20.97, 21.02, 105.78, 105.84, "Hà Nội", "thanh xuan", "q thanh xuan", "quan thanh xuan");
        addDistrict("Hà Đông", 20.9712, 105.7744, 20.92, 21.00, 105.72, 105.82, "Hà Nội", "ha dong", "q ha dong", "quan ha dong");
        addDistrict("Hoàng Mai", 20.9760, 105.8488, 20.94, 21.01, 105.81, 105.90, "Hà Nội", "hoang mai", "q hoang mai", "quan hoang mai");
        addDistrict("Long Biên", 21.0366, 105.8920, 21.00, 21.08, 105.84, 105.95, "Hà Nội", "long bien", "q long bien", "quan long bien");
        addDistrict("Tây Hồ", 21.0664, 105.8214, 21.04, 21.09, 105.79, 105.85, "Hà Nội", "tay ho", "q tay ho", "quan tay ho");
        addDistrict("Nam Từ Liêm", 21.0134, 105.7674, 20.98, 21.05, 105.72, 105.80, "Hà Nội", "nam tu liem", "q nam tu liem", "quan nam tu liem");
        addDistrict("Bắc Từ Liêm", 21.0673, 105.7592, 21.03, 21.10, 105.71, 105.79, "Hà Nội", "bac tu liem", "q bac tu liem", "quan bac tu liem");
        addDistrict("Thanh Trì", 20.9482, 105.8466, 20.90, 20.98, 105.80, 105.90, "Hà Nội", "thanh tri", "h thanh tri", "huyen thanh tri");
        addDistrict("Sóc Sơn", 21.2825, 105.8480, 21.20, 21.36, 105.75, 105.95, "Hà Nội", "soc son", "h soc son", "huyen soc son");
        addDistrict("Sơn Tây", 21.1394, 105.5061, 21.08, 21.18, 105.45, 105.56, "Hà Nội", "son tay", "tx son tay", "thi xa son tay");

        // 2. TP. Hồ Chí Minh
        addDistrict("Quận 1", 10.7756, 106.7004, 10.75, 10.80, 106.68, 106.72, "Hồ Chí Minh", "quan 1", "q1", "q.1", "1");
        addDistrict("Quận 3", 10.7844, 106.6844, 10.76, 10.80, 106.66, 106.70, "Hồ Chí Minh", "quan 3", "q3", "q.3", "3");
        addDistrict("Quận 4", 10.7578, 106.7013, 10.74, 10.78, 106.68, 106.72, "Hồ Chí Minh", "quan 4", "q4", "q.4", "4");
        addDistrict("Quận 5", 10.7540, 106.6634, 10.73, 10.77, 106.64, 106.68, "Hồ Chí Minh", "quan 5", "q5", "q.5", "5");
        addDistrict("Quận 6", 10.7470, 106.6350, 10.72, 10.76, 106.61, 106.66, "Hồ Chí Minh", "quan 6", "q6", "q.6", "6");
        addDistrict("Quận 7", 10.7340, 106.7219, 10.71, 10.76, 106.70, 106.76, "Hồ Chí Minh", "quan 7", "q7", "q.7", "7");
        addDistrict("Quận 8", 10.7241, 106.6286, 10.70, 10.75, 106.60, 106.68, "Hồ Chí Minh", "quan 8", "q8", "q.8", "8");
        addDistrict("Quận 10", 10.7715, 106.6678, 10.75, 10.79, 106.65, 106.69, "Hồ Chí Minh", "quan 10", "q10", "q.10", "10");
        addDistrict("Quận 11", 10.7629, 106.6503, 10.74, 10.78, 106.63, 106.67, "Hồ Chí Minh", "quan 11", "q11", "q.11", "11");
        addDistrict("Quận 12", 10.8672, 106.6414, 10.82, 10.91, 106.58, 106.71, "Hồ Chí Minh", "quan 12", "q12", "q.12", "12");
        addDistrict("Thủ Đức", 10.8494, 106.7537, 10.78, 10.90, 106.70, 106.85, "Hồ Chí Minh", "thu duc", "tp thu duc", "tp. thu duc", "thanh pho thu duc", "quan 2", "q2", "quan 9", "q9");
        addDistrict("Bình Thạnh", 10.8106, 106.7091, 10.78, 10.84, 106.68, 106.74, "Hồ Chí Minh", "binh thanh", "q binh thanh", "quan binh thanh");
        addDistrict("Tân Bình", 10.8014, 106.6526, 10.77, 10.83, 106.63, 106.68, "Hồ Chí Minh", "tan binh", "q tan binh", "quan tan binh");
        addDistrict("Gò Vấp", 10.8387, 106.6653, 10.81, 10.87, 106.64, 106.70, "Hồ Chí Minh", "go vap", "q go vap", "quan go vap");
        addDistrict("Phú Nhuận", 10.7992, 106.6803, 10.78, 10.82, 106.66, 106.70, "Hồ Chí Minh", "phu nhuan", "q phu nhuan", "quan phu nhuan");
        addDistrict("Tân Phú", 10.7900, 106.6285, 10.76, 10.82, 106.60, 106.65, "Hồ Chí Minh", "tan phu", "q tan phu", "quan tan phu");
        addDistrict("Bình Tân", 10.7653, 106.6038, 10.72, 10.81, 106.57, 106.63, "Hồ Chí Minh", "binh tan", "q binh tan", "quan binh tan");
        addDistrict("Bình Chánh", 10.6875, 106.5886, 10.60, 10.78, 106.50, 106.68, "Hồ Chí Minh", "binh chanh", "h binh chanh", "huyen binh chanh");
        addDistrict("Hóc Môn", 10.8847, 106.5925, 10.83, 10.94, 106.53, 106.65, "Hồ Chí Minh", "hoc mon", "h hoc mon", "huyen hoc mon");
        addDistrict("Củ Chi", 11.0064, 106.4950, 10.90, 11.15, 106.35, 106.65, "Hồ Chí Minh", "cu chi", "h cu chi", "huyen cu chi");
        addDistrict("Nhà Bè", 10.6558, 106.7347, 10.58, 10.72, 106.68, 106.78, "Hồ Chí Minh", "nha be", "h nha be", "huyen nha be");

        // 3. Thanh Hóa (Đầy đủ các Huyện/Thị/TP)
        addDistrict("Yên Định", 19.9867, 105.6425, 19.88, 20.08, 105.52, 105.78, "Thanh Hóa", "yen dinh", "h yen dinh", "huyen yen dinh");
        addDistrict("Thanh Hóa", 19.8067, 105.7852, 19.75, 19.88, 105.72, 105.86, "Thanh Hóa", "tp thanh hoa", "thanh pho thanh hoa");
        addDistrict("Sầm Sơn", 19.7431, 105.9042, 19.70, 19.80, 105.85, 105.95, "Thanh Hóa", "sam son", "tp sam son", "thanh pho sam son", "tx sam son");
        addDistrict("Bỉm Sơn", 20.0631, 105.8644, 20.00, 20.12, 105.80, 105.92, "Thanh Hóa", "bim son", "tx bim son", "thi xa bim son");
        addDistrict("Nghi Sơn", 19.4167, 105.7833, 19.30, 19.60, 105.65, 105.90, "Thanh Hóa", "nghi son", "tx nghi son", "thi xa nghi son", "tinh gia", "huyen tinh gia");
        addDistrict("Hoằng Hóa", 19.8667, 105.8667, 19.80, 19.95, 105.78, 105.95, "Thanh Hóa", "hoang hoa", "h hoang hoa", "huyen hoang hoa");
        addDistrict("Thọ Xuân", 19.9333, 105.5333, 19.85, 20.02, 105.42, 105.65, "Thanh Hóa", "tho xuan", "h tho xuan", "huyen tho xuan");
        addDistrict("Triệu Sơn", 19.8167, 105.5833, 19.72, 19.90, 105.48, 105.68, "Thanh Hóa", "trieu son", "h trieu son", "huyen trieu son");
        addDistrict("Nông Cống", 19.6167, 105.6833, 19.52, 19.72, 105.58, 105.78, "Thanh Hóa", "nong cong", "h nong cong", "huyen nong cong");
        addDistrict("Quảng Xương", 19.7167, 105.7833, 19.62, 19.80, 105.72, 105.88, "Thanh Hóa", "quang xuong", "h quang xuong", "huyen quang xuong");
        addDistrict("Đông Sơn", 19.8000, 105.7167, 19.74, 19.86, 105.66, 105.78, "Thanh Hóa", "dong son", "h dong son", "huyen dong son");
        addDistrict("Thiệu Hóa", 19.9000, 105.7000, 19.82, 19.98, 105.62, 105.78, "Thanh Hóa", "thieu hoa", "h thieu hoa", "huyen thieu hoa");
        addDistrict("Hà Trung", 20.0333, 105.8333, 19.95, 20.12, 105.75, 105.92, "Thanh Hóa", "ha trung", "h ha trung", "huyen ha trung");
        addDistrict("Hậu Lộc", 19.9333, 105.9000, 19.86, 20.02, 105.82, 105.98, "Thanh Hóa", "hau loc", "h hau loc", "huyen hau loc");
        addDistrict("Nga Sơn", 20.0000, 106.0000, 19.92, 20.08, 105.92, 106.08, "Thanh Hóa", "nga son", "h nga son", "huyen nga son");
        addDistrict("Vĩnh Lộc", 20.0333, 105.6500, 19.95, 20.12, 105.55, 105.75, "Thanh Hóa", "vinh loc", "h vinh loc", "huyen vinh loc");
        addDistrict("Thạch Thành", 20.2167, 105.6333, 20.10, 20.35, 105.50, 105.78, "Thanh Hóa", "thach thanh", "h thach thanh", "huyen thach thanh");
        addDistrict("Cẩm Thủy", 20.2167, 105.4500, 20.10, 20.32, 105.35, 105.58, "Thanh Hóa", "cam thuy", "h cam thuy", "huyen cam thuy");
        addDistrict("Ngọc Lặc", 20.0667, 105.3833, 19.95, 20.18, 105.25, 105.52, "Thanh Hóa", "ngoc lac", "h ngoc lac", "huyen ngoc lac");

        // 4. Đà Nẵng
        addDistrict("Hải Châu", 16.0594, 108.2208, 16.02, 16.09, 108.20, 108.25, "Đà Nẵng", "hai chau", "q hai chau", "quan hai chau");
        addDistrict("Thanh Khê", 16.0645, 108.1884, 16.04, 16.09, 108.16, 108.21, "Đà Nẵng", "thanh khe", "q thanh khe", "quan thanh khe");
        addDistrict("Sơn Trà", 16.0907, 108.2586, 16.05, 16.14, 108.22, 108.30, "Đà Nẵng", "son tra", "q son tra", "quan son tra");
        addDistrict("Ngũ Hành Sơn", 16.0028, 108.2550, 15.96, 16.04, 108.23, 108.28, "Đà Nẵng", "ngu hanh son", "q ngu hanh son", "quan ngu hanh son");
        addDistrict("Liên Chiểu", 16.1040, 108.1448, 16.05, 16.15, 108.08, 108.18, "Đà Nẵng", "lien chieu", "q lien chieu", "quan lien chieu");
        addDistrict("Cẩm Lệ", 15.9989, 108.1963, 15.97, 16.03, 108.16, 108.23, "Đà Nẵng", "cam le", "q cam le", "quan cam le");
        addDistrict("Hòa Vang", 16.0500, 108.1000, 15.92, 16.18, 107.95, 108.20, "Đà Nẵng", "hoa vang", "h hoa vang", "huyen hoa vang");

        // 5. Bắc Ninh
        addDistrict("Bắc Ninh", 21.1861, 106.0763, 21.15, 21.22, 106.02, 106.12, "Bắc Ninh", "tp bac ninh", "thanh pho bac ninh");
        addDistrict("Từ Sơn", 21.1186, 105.9647, 21.08, 21.16, 105.92, 106.02, "Bắc Ninh", "tu son", "tx tu son", "tp tu son");
        addDistrict("Quế Võ", 21.1500, 106.1833, 21.08, 21.22, 106.10, 106.28, "Bắc Ninh", "que vo", "tx que vo", "h que vo", "huyen que vo");
        addDistrict("Yên Phong", 21.2058, 105.9861, 21.15, 21.25, 105.92, 106.05, "Bắc Ninh", "yen phong", "h yen phong", "huyen yen phong");
        addDistrict("Thuận Thành", 21.0500, 106.0833, 20.98, 21.12, 106.00, 106.18, "Bắc Ninh", "thuan thanh", "tx thuan thanh", "h thuan thanh");

        // 6. Bình Dương
        addDistrict("Thủ Dầu Một", 10.9804, 106.6519, 10.92, 11.04, 106.60, 106.72, "Bình Dương", "thu dau mot", "tp thu dau mot", "tdm");
        addDistrict("Dĩ An", 10.9069, 106.7631, 10.86, 10.96, 106.72, 106.82, "Bình Dương", "di an", "tp di an", "thanh pho di an");
        addDistrict("Thuận An", 10.9250, 106.7000, 10.88, 10.98, 106.65, 106.75, "Bình Dương", "thuan an", "tp thuan an");
        addDistrict("Bến Cát", 11.1333, 106.6000, 11.05, 11.22, 106.52, 106.70, "Bình Dương", "ben cat", "tx ben cat", "tp ben cat");
        addDistrict("Tân Uyên", 11.0833, 106.8000, 11.00, 11.16, 106.72, 106.90, "Bình Dương", "tan uyen", "tx tan uyen", "tp tan uyen");

        // 7. Đồng Nai
        addDistrict("Biên Hòa", 10.9574, 106.8427, 10.88, 11.05, 106.78, 106.92, "Đồng Nai", "bien hoa", "tp bien hoa");
        addDistrict("Long Thành", 10.7833, 106.9500, 10.68, 10.90, 106.85, 107.08, "Đồng Nai", "long thanh", "h long thanh");
        addDistrict("Nhơn Trạch", 10.6833, 106.8833, 10.58, 10.78, 106.80, 107.00, "Đồng Nai", "nhon trach", "h nhon trach");

        // 8. Đắk Lắk
        addDistrict("Buôn Ma Thuột", 12.6667, 108.0500, 12.58, 12.78, 107.95, 108.18, "Đắk Lắk", "buon ma thuot", "tp buon ma thuot", "bmt");
        addDistrict("Cư M'gar", 12.8333, 108.1000, 12.70, 13.00, 108.00, 108.25, "Đắk Lắk", "cu mgar", "cu m'gar", "h cu mgar", "huyen cu mgar");

        // 9. Đặc khu Hải đảo (Không có cấp Xã)
        addDistrict("Côn Đảo", 8.6833, 106.6000, 8.60, 8.78, 106.50, 106.70, "Bà Rịa - Vũng Tàu", "con dao", "h con dao", "huyen con dao");
        addDistrict("Phú Quốc", 10.2289, 103.9572, 10.05, 10.45, 103.80, 104.10, "Kiên Giang", "phu quoc", "tp phu quoc", "huyen phu quoc");
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
