package com.ddicg.erp.modules.merchandise.dto;

import lombok.Builder;
import lombok.Value;

import java.io.Serializable;


/**
 * DTO for Product Analytics Dashboard
 * Chứa các chỉ số phân tích để hiển thị trên dashboard
 */
@Value
@Builder
public class ProductAnalyticsDto implements Serializable {

    /* ============================ Basic Info ============================ */
    Long productId;
    String productSku;
    String productName;

    /* ============================ Sales Metrics ============================ */
    Integer totalSoldQuantity; // Tổng số lượng đã bán
    Double totalRevenue; // Tổng doanh thu
    Double netRevenue; // Doanh thu thuần (sau giảm giá)
    Integer totalOrders; // Số đơn hàng chứa sản phẩm
    Double averageOrderValue; // Giá trị trung bình mỗi đơn

    /*
     * ============================ Inventory Metrics ============================
     */
    Integer currentStock; // Tồn kho hiện tại
    Double sellThroughRate; // Tỉ lệ bán/tồn kho (%)
    String stockStatus; // IN_STOCK, LOW_STOCK, OUT_OF_STOCK

    /*
     * ============================ Performance Metrics ============================
     */
    Double returnRate; // Tỉ lệ hoàn trả (%)
    Double cancellationRate; // Tỉ lệ hủy đơn (%)
    Double conversionRate; // Tỉ lệ chuyển đổi views -> buy (%)
    Double profitMargin; // Biên lợi nhuận (%)

    /*
     * ============================ Engagement Metrics ============================
     */
    Integer viewCount; // Số lượt xem
    Double averageRating; // Đánh giá trung bình
    Integer reviewCount; // Số lượng đánh giá

    /*
     * ============================ Time-based Metrics ============================
     */
    Double revenueToday; // Doanh thu hôm nay
    Double revenueThisWeek; // Doanh thu tuần này
    Double revenueThisMonth; // Doanh thu tháng này
    Double revenueChangePercent; // % thay đổi so với kỳ trước
}
