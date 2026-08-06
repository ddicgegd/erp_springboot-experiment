package com.ddicg.erp.fineract.service;

import com.ddicg.erp.fineract.dto.FineractClientCreateRequestDTO;
import com.ddicg.erp.model.entity.User;
import com.ddicg.erp.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.ddicg.erp.fineract.config.FineractProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
public class FineractClientService {

    private final RestClient fineractRestClient;
    private final UserRepository userRepository;
    private final FineractProperties fineractProperties;

    public JsonNode getClients() {
        return fineractRestClient.get()
                .uri("/clients")
                .retrieve()
                .body(JsonNode.class);
    }

    public JsonNode createClient(FineractClientCreateRequestDTO request) {
        // Set defaults from properties if not specified
        if (request.getOfficeId() == null) request.setOfficeId(fineractProperties.getDefaultOfficeId());
        if (request.getLegalFormId() == null) request.setLegalFormId(fineractProperties.getDefaultLegalFormId());
        if (request.getActive() == null) request.setActive(true);
        if (request.getActivationDate() == null) request.setActivationDate(LocalDate.now().format(DateTimeFormatter.ofPattern(fineractProperties.getDateFormat())));
        if (request.getDateFormat() == null) request.setDateFormat(fineractProperties.getDateFormat());
        if (request.getLocale() == null) request.setLocale(fineractProperties.getLocale());

        return fineractRestClient.post()
                .uri("/clients")
                .body(request)
                .retrieve()
                .body(JsonNode.class);
    }

    @Transactional
    public String getOrCreateFineractClient(User user) {
        if (user.getFineractClientId() != null) {
            return user.getFineractClientId();
        }

        String fullName = user.getFullName() != null ? user.getFullName() : "ERP User";
        String[] nameParts = fullName.trim().split("\\s+");
        String firstname = nameParts[0];
        String lastname = nameParts.length > 1 ? fullName.substring(firstname.length()).trim() : "LastName";

        FineractClientCreateRequestDTO syncRequest = new FineractClientCreateRequestDTO(
                firstname,
                lastname,
                fineractProperties.getDefaultOfficeId(),
                fineractProperties.getDefaultLegalFormId(),
                true,
                LocalDate.now().format(DateTimeFormatter.ofPattern(fineractProperties.getDateFormat())),
                fineractProperties.getDateFormat(),
                fineractProperties.getLocale()
        );

        JsonNode response = createClient(syncRequest);
        if (response != null && response.has("clientId")) {
            String clientId = response.get("clientId").asText();
            user.setFineractClientId(clientId);
            userRepository.save(user);
            return clientId;
        }

        throw new RuntimeException("Không thể đồng bộ/tạo Client trên Fineract");
    }
}
