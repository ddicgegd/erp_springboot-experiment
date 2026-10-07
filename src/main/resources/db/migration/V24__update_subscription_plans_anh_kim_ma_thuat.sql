-- V24: Cập nhật danh sách gói hội viên theo kế hoạch mới: Gói Ánh Kim (30k/tháng, 7 ngày Free Trial) và Gói Ma Thuật (120k/tháng, No Free Trial)
DELETE FROM subscription_plans WHERE plan_code IN ('GOLD_3M', 'GOLD_6M', 'PLATINUM_12M', 'ANH_KIM', 'MA_THUAT');

INSERT INTO subscription_plans (id, plan_code, name, target_rank, billing_period_months, free_trial_days, price_amount, currency, is_active, description, created_at, updated_at, is_deleted)
VALUES (1, 'ANH_KIM', 'Gói Ánh Kim', 'SILVER', 1, 7, 30000.00, 'VND', 1, 'Gói Ánh Kim: 30k/tháng, 7 ngày dùng thử miễn phí, giỏ hàng 100 sản phẩm, không chiết khấu đơn hàng', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

INSERT INTO subscription_plans (id, plan_code, name, target_rank, billing_period_months, free_trial_days, price_amount, currency, is_active, description, created_at, updated_at, is_deleted)
VALUES (2, 'MA_THUAT', 'Gói Ma Thuật', 'DIAMOND', 1, 0, 120000.00, 'VND', 1, 'Gói Ma Thuật: 120k/tháng, thu tiền ngay, chiết khấu 3% toàn đơn, giỏ hàng không giới hạn, luôn free vận chuyển', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);
