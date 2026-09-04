package com.ddicg.erp.modules.fineract.service;

import com.ddicg.erp.core.common.util.VietnameseNameParser;
import com.ddicg.erp.core.exception.BusinessException;
import com.ddicg.erp.core.exception.ErrorCode;
import com.ddicg.erp.core.security.SecurityUtil;
import com.ddicg.erp.modules.fineract.config.FineractProperties;
import com.ddicg.erp.modules.fineract.dto.FineractClientCreateRequestDTO;
import com.ddicg.erp.modules.iam.model.User;
import com.ddicg.erp.modules.iam.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class FineractClientService {

    private final RestClient fineractRestClient;
    private final UserRepository userRepository;
    private final FineractProperties fineractProperties;
    private final SecurityUtil securityUtil;

    public JsonNode getClients() {
        if (securityUtil != null && securityUtil.isStaffOrAdmin()) {
            return fineractRestClient.get()
                    .uri("/clients")
                    .retrieve()
                    .body(JsonNode.class);
        }
        String userId = securityUtil != null ? securityUtil.getCurrentUserId() : null;
        if (userId != null && !userId.isBlank()) {
            return getClients(userId);
        }
        return fineractRestClient.get()
                .uri("/clients")
                .retrieve()
                .body(JsonNode.class);
    }

    public String getOrCreateCurrentClient() {
        if (securityUtil != null) {
            String clientId = securityUtil.getCurrentFineractClientId();
            if (clientId != null && !clientId.isBlank()) {
                return clientId;
            }
            User user = securityUtil.getCurrentUser()
                    .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED, "Người dùng chưa được xác thực"));
            return getOrCreateFineractClient(user);
        }
        throw new BusinessException(ErrorCode.UNAUTHORIZED, "Người dùng chưa được xác thực");
    }

    public JsonNode getClients(String externalId) {
        if (externalId != null && !externalId.isBlank()) {
            return fineractRestClient.get()
                    .uri("/clients?externalId={externalId}", externalId)
                    .retrieve()
                    .body(JsonNode.class);
        }
        return fineractRestClient.get()
                .uri("/clients")
                .retrieve()
                .body(JsonNode.class);
    }

    public JsonNode getClient(Long clientId) {
        return fineractRestClient.get()
                .uri("/clients/{clientId}", clientId)
                .retrieve()
                .body(JsonNode.class);
    }

    public JsonNode createClient(JsonNode payload) {
        return fineractRestClient.post()
                .uri("/clients")
                .body(payload)
                .retrieve()
                .body(JsonNode.class);
    }

    public JsonNode createClient(FineractClientCreateRequestDTO request) {
        if (request.getFirstname() == null || request.getFirstname().isBlank()
                || request.getLastname() == null || request.getLastname().isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Họ (lastname) và Tên (firstname) không được để trống");
        }

        return fineractRestClient.post()
                .uri("/clients")
                .body(request)
                .retrieve()
                .body(JsonNode.class);
    }

    public Optional<String> findClientIdByExternalId(String externalId) {
        if (externalId == null || externalId.isBlank()) {
            return Optional.empty();
        }
        try {
            JsonNode response = fineractRestClient.get()
                    .uri("/clients?externalId={externalId}", externalId)
                    .retrieve()
                    .body(JsonNode.class);

            if (response != null && response.has("pageItems") && response.get("pageItems").isArray()) {
                for (JsonNode item : response.get("pageItems")) {
                    if (item.has("id")) {
                        return Optional.of(item.get("id").asText());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Tra cứu Client theo externalId [{}] trên Fineract thất bại: {}", externalId, e.getMessage());
        }
        return Optional.empty();
    }

    @Transactional
    public String getOrCreateFineractClient(User user) {
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND, "Không tìm thấy thông tin người dùng");
        }

        // 1. Kiểm tra nếu tài khoản đã có fineractClientId trong DB
        if (user.getFineractClientId() != null && !user.getFineractClientId().isBlank()) {
            return user.getFineractClientId();
        }

        // 2. Fail-fast: Phân tích & xác thực họ tên thực tế từ User trước khi gọi mạng
        VietnameseNameParser.ParsedName parsedName = VietnameseNameParser.parse(user.getFullName());

        String externalId = String.valueOf(user.getId());

        // 3. Chống trùng lặp định danh (Idempotency): Kiểm tra xem Fineract đã có client với externalId này chưa
        Optional<String> existingClientId = findClientIdByExternalId(externalId);
        if (existingClientId.isPresent()) {
            String clientId = existingClientId.get();
            log.info("Tìm thấy Client Fineract đã tồn tại với externalId [{}] -> ClientId [{}]", externalId, clientId);
            user.setFineractClientId(clientId);
            userRepository.save(user);
            return clientId;
        }

        // 4. Khởi tạo Client Fineract với thông tin thực tế
        String currentDateFormatted = LocalDate.now().format(DateTimeFormatter.ofPattern(fineractProperties.getDateFormat()));

        FineractClientCreateRequestDTO syncRequest = FineractClientCreateRequestDTO.builder()
                .firstname(parsedName.firstname())
                .lastname(parsedName.lastname())
                .externalId(externalId)
                .emailAddress(user.getEmail())
                .mobileNo(user.getPhoneNumber())
                .officeId(fineractProperties.getOfficeId())
                .legalFormId(fineractProperties.getLegalFormId())
                .active(true)
                .activationDate(currentDateFormatted)
                .dateFormat(fineractProperties.getDateFormat())
                .locale(fineractProperties.getLocale())
                .build();

        log.info("Khởi tạo Client mới trên Fineract cho User ID [{}] với tên: firstname='{}', lastname='{}'",
                user.getId(), parsedName.firstname(), parsedName.lastname());

        JsonNode response = createClient(syncRequest);
        if (response != null && response.has("clientId")) {
            String clientId = response.get("clientId").asText();
            user.setFineractClientId(clientId);
            userRepository.save(user);
            log.info("Đã đồng bộ thành công User ID [{}] với Fineract Client ID [{}]", user.getId(), clientId);
            return clientId;
        }

        throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Không thể đồng bộ/tạo Client trên hệ thống Core Banking");
    }
}
