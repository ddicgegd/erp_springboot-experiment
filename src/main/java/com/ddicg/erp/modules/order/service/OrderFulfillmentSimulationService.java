package com.ddicg.erp.modules.order.service;

import com.ddicg.erp.core.common.model.enums.OrderStatus;
import com.ddicg.erp.modules.order.dto.response.NextStatusSimulationResponse;
import com.ddicg.erp.modules.order.model.Order;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;

@Service
public class OrderFulfillmentSimulationService {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final Set<OrderStatus> TERMINAL_STATUSES = Set.of(
            OrderStatus.COMPLETED, OrderStatus.CANCELLED, OrderStatus.FAILED, OrderStatus.REFUNDED
    );

    public NextStatusSimulationResponse simulateNextStatus(Order order, LocalDateTime referenceTime) {
        OrderStatus current = order.getCurrentStatus();
        if (current == null || TERMINAL_STATUSES.contains(current)) {
            return null;
        }

        LocalDateTime base = referenceTime != null ? referenceTime : LocalDateTime.now();

        if (current == OrderStatus.PENDING || current == OrderStatus.WAITING_PAYMENT) {
            LocalDateTime est = base.plusMinutes(45);
            return NextStatusSimulationResponse.builder()
                    .predictedStatus(OrderStatus.PROCESSING)
                    .predictedStatusTitle("Đang đóng gói & Kiểm thử")
                    .diagnosticPrediction("Đơn hàng đang chờ thủ kho tiếp nhận xuất kho linh kiện và phụ kiện")
                    .estimatedAt(est)
                    .formattedEstimatedTime(est.format(TIME_FMT))
                    .probabilityScore(0.95)
                    .riskSeverity("LOW")
                    .riskCategory("PAYMENT_TIMEOUT")
                    .riskWarningMessage("Đơn hàng sẽ tự động hủy nếu không hoàn tất thanh toán trước thời hạn.")
                    .prerequisiteTasks(List.of("Xác nhận cổng thanh toán", "Kiểm tra tồn kho vật lý"))
                    .alternativeNextStatus("CANCELLED")
                    .alternativeRiskRate(0.05)
                    .build();
        }

        if (current == OrderStatus.PROCESSING || current == OrderStatus.CONFIRMED) {
            LocalDateTime est = base.toLocalDate().atTime(14, 0);
            if (est.isBefore(base)) {
                est = base.plusHours(2).plusMinutes(30);
            }
            // Normalize carrier: "Horizon Express (Viettel Post)" → "Horizon Express / Viettel Post"
            String rawCarrier = order.getShippingMethod() != null ? order.getShippingMethod() : "Horizon Express (Viettel Post)";
            String carrier = rawCarrier.replace("(", "/ ").replace(")", "").trim();
            // Remove double spaces
            carrier = carrier.replaceAll("\\s+", " ").trim();
            return NextStatusSimulationResponse.builder()
                    .predictedStatus(OrderStatus.SHIPPING)
                    .predictedStatusTitle("Bàn giao đơn vị vận chuyển")
                    .diagnosticPrediction("Kiện hàng đã xuất kho chuyển phát nhanh " + carrier)
                    .estimatedAt(est)
                    .formattedEstimatedTime(est.format(TIME_FMT))
                    .probabilityScore(0.91)
                    .riskSeverity("NORMAL")
                    .riskCategory("CARRIER_DELAY")
                    .riskWarningMessage("Khả năng đúng hạn 91%. Có nguy cơ trễ 15 phút do kiểm định seal vòng cuối.")
                    .prerequisiteTasks(List.of(
                            "Hoàn tất kiểm thử linh kiện và đóng hộp nguyên seal",
                            "Dán mã vận đơn chuyển phát nhanh " + carrier,
                            "Tài xế bưu cục ký biên bản giao nhận"
                    ))
                    .alternativeNextStatus("DELAYED")
                    .alternativeRiskRate(0.09)
                    .build();
        }

        if (current == OrderStatus.SHIPPING) {
            LocalDateTime est = base.toLocalDate().atTime(19, 0);
            return NextStatusSimulationResponse.builder()
                    .predictedStatus(OrderStatus.DELIVERED)
                    .predictedStatusTitle("Giao hàng & Hoàn tất")
                    .diagnosticPrediction("Bưu tá liên hệ người nhận đồng kiểm và ký biên bản giao hàng")
                    .estimatedAt(est)
                    .formattedEstimatedTime(est.format(TIME_FMT))
                    .probabilityScore(0.88)
                    .riskSeverity("NORMAL")
                    .riskCategory("DELIVERY_ABSENCE")
                    .riskWarningMessage("Nguy cơ lưu kho trạm phát nếu không liên lạc được khách hàng.")
                    .prerequisiteTasks(List.of("Trung chuyển qua trạm phát hàng địa phương", "Bưu tá nhận tuyến phát"))
                    .alternativeNextStatus("RETURNING")
                    .alternativeRiskRate(0.12)
                    .build();
        }

        return null;
    }
}
