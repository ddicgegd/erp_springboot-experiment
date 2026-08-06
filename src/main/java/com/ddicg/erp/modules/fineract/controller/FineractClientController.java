package com.ddicg.erp.modules.fineract.controller;

import com.ddicg.erp.modules.fineract.dto.FineractClientCreateRequestDTO;
import com.ddicg.erp.modules.fineract.service.FineractClientService;
import com.ddicg.erp.modules.iam.model.User;
import com.ddicg.erp.modules.iam.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/erp/clients")
@RequiredArgsConstructor
public class FineractClientController {

    private final FineractClientService clientService;
    private final UserRepository userRepository;

    @GetMapping
    public ResponseEntity<JsonNode> getClients() {
        return ResponseEntity.ok(clientService.getClients());
    }

    @PostMapping
    public ResponseEntity<JsonNode> createClient(@RequestBody FineractClientCreateRequestDTO request) {
        return ResponseEntity.ok(clientService.createClient(request));
    }

    @PostMapping("/sync")
    public ResponseEntity<JsonNode> syncClient(@AuthenticationPrincipal UserDetails userDetails) {
        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found: " + userDetails.getUsername()));
        
        String clientId = clientService.getOrCreateFineractClient(user);
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode responseNode = mapper.createObjectNode();
        responseNode.put("clientId", clientId);
        responseNode.put("status", "Synchronized successfully");
        
        return ResponseEntity.ok(responseNode);
    }
}
