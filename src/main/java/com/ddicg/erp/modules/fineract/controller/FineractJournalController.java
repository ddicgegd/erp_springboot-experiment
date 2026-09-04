package com.ddicg.erp.modules.fineract.controller;

import com.ddicg.erp.modules.fineract.service.FineractJournalService;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/erp/journalentries")
@RequiredArgsConstructor
@Tag(name = "Fineract Journal Entries", description = "REST API chuẩn Apache Fineract ghi sổ cái kế toán kép (General Ledger Journal Entries)")
public class FineractJournalController {

    private final FineractJournalService journalService;

    @GetMapping
    @Operation(summary = "Truy vấn các bút toán sổ cái")
    public ResponseEntity<JsonNode> getJournalEntries() {
        return ResponseEntity.ok(journalService.getJournalEntries());
    }

    @PostMapping
    @Operation(summary = "Ghi bút toán sổ cái kế toán kép (Post Journal Entry)")
    public ResponseEntity<JsonNode> postJournalEntry(@RequestBody JsonNode payload) {
        return ResponseEntity.ok(journalService.postJournalEntry(payload));
    }
}
