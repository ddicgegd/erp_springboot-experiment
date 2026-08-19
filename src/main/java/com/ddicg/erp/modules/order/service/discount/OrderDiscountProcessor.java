package com.ddicg.erp.modules.order.service.discount;

public interface OrderDiscountProcessor {

    /**
     * Điểm can thiệp (Hook / Extension Point) xử lý chiết khấu / mã khuyến mãi cho đơn hàng.
     * Tách biệt hoàn toàn khỏi luồng OrderService chính.
     *
     * @param context Ngữ cảnh đơn hàng (tiền hàng, phí ship, mã voucher, thông tin khách...)
     * @return Kết quả tiền giảm giá vào sản phẩm và tiền giảm giá vận chuyển
     */
    DiscountEvaluationResult evaluateDiscount(OrderDiscountContext context);
}
