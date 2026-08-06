package com.ddicg.erp.fineract.controller;

import com.ddicg.erp.fineract.service.FineractLoanService;
import com.ddicg.erp.fineract.dto.LoanApplicationRequestDTO;
import com.ddicg.erp.fineract.dto.LoanRepaymentRequestDTO;
import com.ddicg.erp.model.entity.User;
import com.ddicg.erp.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/erp/loans")
@RequiredArgsConstructor
public class FineractLoanController {

    private final FineractLoanService loanService;
    private final UserRepository userRepository;

    private User getAuthenticatedUser(UserDetails userDetails) {
        return userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found: " + userDetails.getUsername()));
    }

    @GetMapping
    public ResponseEntity<JsonNode> getLoans() {
        return ResponseEntity.ok(loanService.getLoans());
    }

    @GetMapping("/my")
    public ResponseEntity<JsonNode> getMyLoans(@AuthenticationPrincipal UserDetails userDetails) {
        User user = getAuthenticatedUser(userDetails);
        return ResponseEntity.ok(loanService.getLoansForUser(user));
    }

    @PostMapping("/my")
    public ResponseEntity<JsonNode> applyForLoan(@AuthenticationPrincipal UserDetails userDetails, @Valid @RequestBody LoanApplicationRequestDTO payload) {
        User user = getAuthenticatedUser(userDetails);
        return ResponseEntity.ok(loanService.applyLoanForUser(user, payload));
    }

    @PostMapping("/{loanId}/repayments")
    public ResponseEntity<JsonNode> repayLoan(@AuthenticationPrincipal UserDetails userDetails, @PathVariable Long loanId, @Valid @RequestBody LoanRepaymentRequestDTO payload) {
        User user = getAuthenticatedUser(userDetails);
        return ResponseEntity.ok(loanService.repayLoanForUser(user, loanId, payload));
    }
}
