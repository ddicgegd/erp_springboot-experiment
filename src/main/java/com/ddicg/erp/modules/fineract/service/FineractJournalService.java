package com.ddicg.erp.modules.fineract.service;

import com.ddicg.erp.modules.fineract.config.FineractProperties;
import com.ddicg.erp.modules.fineract.dto.JournalEntryLineDTO;
import com.ddicg.erp.modules.fineract.dto.JournalEntryRequestDTO;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collections;

@Service
@RequiredArgsConstructor
public class FineractJournalService {

    private final RestClient fineractRestClient;
    private final FineractProperties properties;

    public JsonNode recordSale(String orderId, BigDecimal amount, String note) {
        String currentDate = LocalDate.now().format(DateTimeFormatter.ofPattern(properties.getDateFormat()));

        JournalEntryRequestDTO request = new JournalEntryRequestDTO();
        request.setOfficeId(properties.getDefaultOfficeId());
        request.setTransactionDate(currentDate);
        request.setCurrencyCode("USD"); // Could be parameterized in properties if needed
        request.setReferenceNumber("SALE-" + orderId);
        request.setComments(note != null ? note : "Doanh thu bán hàng từ ERP");
        request.setDateFormat(properties.getDateFormat());
        request.setLocale(properties.getLocale());

        // Debit Cash, Credit Sales Revenue
        request.setDebits(Collections.singletonList(
                new JournalEntryLineDTO(properties.getCashGlAccountId(), amount)
        ));
        request.setCredits(Collections.singletonList(
                new JournalEntryLineDTO(properties.getSalesRevenueGlAccountId(), amount)
        ));

        return postJournalEntry(request);
    }

    public JsonNode recordRefund(String orderId, BigDecimal amount, String note) {
        String currentDate = LocalDate.now().format(DateTimeFormatter.ofPattern(properties.getDateFormat()));

        JournalEntryRequestDTO request = new JournalEntryRequestDTO();
        request.setOfficeId(properties.getDefaultOfficeId());
        request.setTransactionDate(currentDate);
        request.setCurrencyCode("USD");
        request.setReferenceNumber("REFUND-" + orderId);
        request.setComments(note != null ? note : "Hoàn tiền trả hàng từ ERP");
        request.setDateFormat(properties.getDateFormat());
        request.setLocale(properties.getLocale());

        // Compensatory entry: Debit Sales Returns, Credit Cash
        request.setDebits(Collections.singletonList(
                new JournalEntryLineDTO(properties.getSalesReturnsGlAccountId(), amount)
        ));
        request.setCredits(Collections.singletonList(
                new JournalEntryLineDTO(properties.getCashGlAccountId(), amount)
        ));

        return postJournalEntry(request);
    }

    private JsonNode postJournalEntry(JournalEntryRequestDTO request) {
        // Validate debit == credit
        BigDecimal totalDebit = request.getDebits().stream().map(JournalEntryLineDTO::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalCredit = request.getCredits().stream().map(JournalEntryLineDTO::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        
        if (totalDebit.compareTo(totalCredit) != 0) {
            throw new IllegalArgumentException("Debit and Credit must be equal. Debit: " + totalDebit + ", Credit: " + totalCredit);
        }

        return fineractRestClient.post()
                .uri("/journalentries")
                .body(request)
                .retrieve()
                .body(JsonNode.class);
    }
}
