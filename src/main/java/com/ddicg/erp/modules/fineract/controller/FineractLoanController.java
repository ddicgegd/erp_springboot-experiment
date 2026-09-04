package com.ddicg.erp.modules.fineract.controller;

import com.ddicg.erp.modules.fineract.service.FineractLoanService;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/erp/loans")
@RequiredArgsConstructor
@Tag(name = "Fineract Loans", description = "REST API chuẩn Apache Fineract quản lý toàn bộ vòng đời tài khoản vay (Loan Account)")
public class FineractLoanController {

    private final FineractLoanService loanService;

    @GetMapping
    @Operation(summary = "Truy vấn danh sách khoản vay (Tự động theo User hoặc toàn bộ cho Admin)")
    public ResponseEntity<JsonNode> getLoans() {
        return ResponseEntity.ok(loanService.getLoans());
    }

    @GetMapping("/{loanId}")
    @Operation(summary = "Xem chi tiết khoản vay & lịch trả nợ (Repayment Schedule)")
    public ResponseEntity<JsonNode> getLoanDetails(@PathVariable Long loanId) {
        return ResponseEntity.ok(loanService.getLoanDetails(loanId));
    }

    @GetMapping("/template")
    @Operation(summary = "Lấy template thông số gói vay từ Core Banking")
    public ResponseEntity<JsonNode> getLoanTemplate(@RequestParam Long productId) {
        return ResponseEntity.ok(loanService.getLoanTemplate(productId));
    }

    @PostMapping
    @Operation(summary = "Nộp hồ sơ xin vay vốn (Submit Loan Application)")
    public ResponseEntity<JsonNode> applyForLoan(@RequestBody JsonNode payload) {
        return ResponseEntity.ok(loanService.applyLoan(payload));
    }

    @PostMapping("/{loanId}/approve")
    @Operation(summary = "Phê duyệt khoản vay (Command: approve)")
    public ResponseEntity<JsonNode> approveLoan(@PathVariable Long loanId, @RequestBody(required = false) JsonNode payload) {
        return ResponseEntity.ok(loanService.executeCommand(loanId, "approve", payload));
    }

    @PostMapping("/{loanId}/disburse")
    @Operation(summary = "Giải ngân tiền vay (Command: disburse)")
    public ResponseEntity<JsonNode> disburseLoan(@PathVariable Long loanId, @RequestBody(required = false) JsonNode payload) {
        return ResponseEntity.ok(loanService.executeCommand(loanId, "disburse", payload));
    }

    @PostMapping("/{loanId}/reject")
    @Operation(summary = "Từ chối hồ sơ vay (Command: reject)")
    public ResponseEntity<JsonNode> rejectLoan(@PathVariable Long loanId, @RequestBody(required = false) JsonNode payload) {
        return ResponseEntity.ok(loanService.executeCommand(loanId, "reject", payload));
    }

    @PostMapping("/{loanId}/withdraw")
    @Operation(summary = "Rút đơn xin vay vốn (Command: withdrawnByApplicant)")
    public ResponseEntity<JsonNode> withdrawLoan(@PathVariable Long loanId, @RequestBody(required = false) JsonNode payload) {
        return ResponseEntity.ok(loanService.executeCommand(loanId, "withdrawnByApplicant", payload));
    }

    @PostMapping("/{loanId}/repayments")
    @Operation(summary = "Thanh toán trả nợ khoản vay (Transaction: repayment)")
    public ResponseEntity<JsonNode> repayLoan(@PathVariable Long loanId, @RequestBody(required = false) JsonNode payload) {
        return ResponseEntity.ok(loanService.repayLoan(loanId, payload));
    }
}
