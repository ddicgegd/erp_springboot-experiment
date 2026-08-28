package com.ddicg.erp.modules.fineract.consumer;

import com.ddicg.erp.modules.fineract.service.FineractJournalService;
import com.ddicg.erp.modules.order.model.Order;
import com.ddicg.erp.modules.order.repository.OrderRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderFineractConsumerTest {

    @Mock
    private FineractJournalService fineractJournalService;

    @Mock
    private OrderRepository orderRepository;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private OrderFineractConsumer orderFineractConsumer;

    private Order sampleOrder;

    @BeforeEach
    void setUp() {
        sampleOrder = new Order();
        sampleOrder.setOrderNumber("ORD-FINERACT-001");
        sampleOrder.setTotalAmount(500000.0);
    }

    @Test
    @DisplayName("Consume ORDER_STATUS_CHANGED to PROCESSING: Record Sale in Fineract")
    void testConsume_StatusProcessing_RecordSale() {
        when(orderRepository.findByOrderNumber("ORD-FINERACT-001")).thenReturn(Optional.of(sampleOrder));

        String payload = "{\"eventType\":\"ORDER_STATUS_CHANGED\",\"orderNumber\":\"ORD-FINERACT-001\",\"newStatus\":\"PROCESSING\"}";

        orderFineractConsumer.consume(payload, "ORD-FINERACT-001");

        verify(fineractJournalService).recordSale(eq("ORD-FINERACT-001"), eq(BigDecimal.valueOf(500000.0)), any());
    }

    @Test
    @DisplayName("Consume ORDER_STATUS_CHANGED to REFUNDED: Record Refund in Fineract")
    void testConsume_StatusRefunded_RecordRefund() {
        when(orderRepository.findByOrderNumber("ORD-FINERACT-001")).thenReturn(Optional.of(sampleOrder));

        String payload = "{\"eventType\":\"ORDER_STATUS_CHANGED\",\"orderNumber\":\"ORD-FINERACT-001\",\"newStatus\":\"REFUNDED\"}";

        orderFineractConsumer.consume(payload, "ORD-FINERACT-001");

        verify(fineractJournalService).recordRefund(eq("ORD-FINERACT-001"), eq(BigDecimal.valueOf(500000.0)), any());
    }

    @Test
    @DisplayName("Consume ORDER_CREATED with initialStatus PROCESSING: Record Sale in Fineract")
    void testConsume_OrderCreated_Processing_RecordSale() {
        when(orderRepository.findByOrderNumber("ORD-FINERACT-001")).thenReturn(Optional.of(sampleOrder));

        String payload = "{\"eventType\":\"ORDER_CREATED\",\"orderNumber\":\"ORD-FINERACT-001\",\"initialStatus\":\"PROCESSING\"}";

        orderFineractConsumer.consume(payload, "ORD-FINERACT-001");

        verify(fineractJournalService).recordSale(eq("ORD-FINERACT-001"), eq(BigDecimal.valueOf(500000.0)), any());
    }

    @Test
    @DisplayName("Consume unrelated event type: Skip Fineract call")
    void testConsume_UnrelatedEvent_Skip() {
        String payload = "{\"eventType\":\"OTHER_EVENT\",\"orderNumber\":\"ORD-FINERACT-001\",\"newStatus\":\"PROCESSING\"}";

        orderFineractConsumer.consume(payload, "ORD-FINERACT-001");

        verifyNoInteractions(fineractJournalService);
    }
}
