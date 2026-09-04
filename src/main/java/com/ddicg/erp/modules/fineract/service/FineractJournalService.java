package com.ddicg.erp.modules.fineract.service;

import com.ddicg.erp.core.common.model.enums.PaymentMethod;
import com.ddicg.erp.modules.fineract.config.FineractProperties;
import com.ddicg.erp.modules.fineract.dto.JournalEntryLineDTO;
import com.ddicg.erp.modules.fineract.dto.JournalEntryRequestDTO;
import com.ddicg.erp.modules.fineract.service.accounting.GlAccountResolver;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collections;

@Slf4j
@Service
@RequiredArgsConstructor
public class FineractJournalService {

    private final RestClient fineractRestClient;
    private final FineractProperties properties;
    private final GlAccountResolver glAccountResolver;

    public JsonNode recordSale(String orderId, BigDecimal amount, String note) {
        return recordSale(orderId, amount, note, PaymentMethod.COD);
    }

    public JsonNode recordSale(String orderId, BigDecimal amount, String note, PaymentMethod paymentMethod) {
        String currentDate = LocalDate.now().format(DateTimeFormatter.ofPattern(properties.getDateFormat()));
        Long assetGlAccount = glAccountResolver.resolveAssetGlAccount(paymentMethod);
        Long revenueGlAccount = glAccountResolver.getSalesRevenueGlAccountId();

        JournalEntryRequestDTO request = new JournalEntryRequestDTO();
        request.setOfficeId(properties.getOfficeId());
        request.setTransactionDate(currentDate);
        request.setCurrencyCode(properties.getCurrencyCode());
        request.setReferenceNumber("SALE-" + orderId);
        request.setComments(note != null && !note.isBlank() ? note : ("Doanh thu bán hàng đơn " + orderId));
        request.setDateFormat(properties.getDateFormat());
        request.setLocale(properties.getLocale());

        // Hạch toán Nợ TK Tiền (Cash/Bank), Có TK Doanh thu bán hàng (Revenue)
        request.setDebits(Collections.singletonList(
                new JournalEntryLineDTO(assetGlAccount, amount)
        ));
        request.setCredits(Collections.singletonList(
                new JournalEntryLineDTO(revenueGlAccount, amount)
        ));

        log.info("Hạch toán DOANH THU Fineract đơn [{}]: Tiền tệ={}, Số tiền={}, TK Nợ={}, TK Có={}",
                orderId, properties.getCurrencyCode(), amount, assetGlAccount, revenueGlAccount);

        return postJournalEntry(request);
    }

    public JsonNode recordRefund(String orderId, BigDecimal amount, String note) {
        return recordRefund(orderId, amount, note, PaymentMethod.COD);
    }

    public JsonNode recordRefund(String orderId, BigDecimal amount, String note, PaymentMethod paymentMethod) {
        String currentDate = LocalDate.now().format(DateTimeFormatter.ofPattern(properties.getDateFormat()));
        Long assetGlAccount = glAccountResolver.resolveAssetGlAccount(paymentMethod);
        Long returnGlAccount = glAccountResolver.getSalesReturnsGlAccountId();

        JournalEntryRequestDTO request = new JournalEntryRequestDTO();
        request.setOfficeId(properties.getOfficeId());
        request.setTransactionDate(currentDate);
        request.setCurrencyCode(properties.getCurrencyCode());
        request.setReferenceNumber("REFUND-" + orderId);
        request.setComments(note != null && !note.isBlank() ? note : ("Hoàn tiền trả hàng đơn " + orderId));
        request.setDateFormat(properties.getDateFormat());
        request.setLocale(properties.getLocale());

        // Hạch toán đảo: Nợ TK Hàng bán bị trả lại (Sales Returns), Có TK Tiền (Cash/Bank)
        request.setDebits(Collections.singletonList(
                new JournalEntryLineDTO(returnGlAccount, amount)
        ));
        request.setCredits(Collections.singletonList(
                new JournalEntryLineDTO(assetGlAccount, amount)
        ));

        log.info("Hạch toán HOÀN TIỀN Fineract đơn [{}]: Tiền tệ={}, Số tiền={}, TK Nợ={}, TK Có={}",
                orderId, properties.getCurrencyCode(), amount, returnGlAccount, assetGlAccount);

        return postJournalEntry(request);
    }

    private JsonNode postJournalEntry(JournalEntryRequestDTO request) {
        // Kiểm tra nguyên tắc hạch toán kép (Tổng Nợ == Tổng Có)
        BigDecimal totalDebit = request.getDebits().stream()
                .map(JournalEntryLineDTO::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalCredit = request.getCredits().stream()
                .map(JournalEntryLineDTO::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (totalDebit.compareTo(totalCredit) != 0) {
            throw new IllegalArgumentException("Nguyên tắc kế toán kép vi phạm: Tổng Nợ (" + totalDebit + ") khác Tổng Có (" + totalCredit + ")");
        }

        return fineractRestClient.post()
                .uri("/journalentries")
                .body(request)
                .retrieve()
                .body(JsonNode.class);
    }

    public JsonNode getJournalEntries() {
        return fineractRestClient.get()
                .uri("/journalentries")
                .retrieve()
                .body(JsonNode.class);
    }

    public JsonNode postJournalEntry(JsonNode payload) {
        return fineractRestClient.post()
                .uri("/journalentries")
                .body(payload)
                .retrieve()
                .body(JsonNode.class);
    }
}
