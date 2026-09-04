package com.ddicg.erp.modules.fineract.controller;

import com.ddicg.erp.modules.fineract.service.FineractClientService;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/erp/clients")
@RequiredArgsConstructor
@Tag(name = "Fineract Clients", description = "REST API chuẩn Apache Fineract quản lý khách hàng tài chính (Client)")
public class FineractClientController {

    private final FineractClientService clientService;

    @GetMapping
    @Operation(summary = "Truy vấn thông tin khách hàng (Tự động theo User hoặc toàn bộ cho Admin)")
    public ResponseEntity<JsonNode> getClients() {
        return ResponseEntity.ok(clientService.getClients());
    }

    @GetMapping("/{clientId}")
    @Operation(summary = "Truy vấn thông tin chi tiết khách hàng theo Client ID")
    public ResponseEntity<JsonNode> getClient(@PathVariable Long clientId) {
        return ResponseEntity.ok(clientService.getClient(clientId));
    }

    @PostMapping
    @Operation(summary = "Tạo mới hồ sơ khách hàng (Create Client)")
    public ResponseEntity<JsonNode> createClient(@RequestBody JsonNode payload) {
        return ResponseEntity.ok(clientService.createClient(payload));
    }
}
