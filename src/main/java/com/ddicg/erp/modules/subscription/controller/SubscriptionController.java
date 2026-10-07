package com.ddicg.erp.modules.subscription.controller;

import com.ddicg.erp.core.common.dto.response.Response;
import com.ddicg.erp.core.security.SecurityUtil;
import com.ddicg.erp.modules.subscription.dto.request.GooglePayWebRegisterRequest;
import com.ddicg.erp.modules.subscription.dto.request.CancelSubscriptionRequest;
import com.ddicg.erp.modules.subscription.dto.request.OneTimeSubscriptionPurchaseRequest;
import com.ddicg.erp.modules.subscription.dto.response.UserSubscriptionResponse;
import com.ddicg.erp.modules.subscription.model.SubscriptionPlan;
import com.ddicg.erp.modules.subscription.service.GooglePayWebService;
import com.ddicg.erp.modules.subscription.service.SubscriptionBillingService;
import com.ddicg.erp.modules.subscription.service.SubscriptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/subscriptions")
@RequiredArgsConstructor
@Tag(name = "Subscriptions", description = "APIs quản lý gói dịch vụ và thanh toán định kỳ Google Pay Web")
public class SubscriptionController {

    private final SubscriptionService subscriptionService;
    private final GooglePayWebService googlePayWebService;
    private final SubscriptionBillingService billingService;
    private final SecurityUtil securityUtil;

    @GetMapping("/plans")
    @Operation(summary = "Lấy danh sách các gói Subscription đang hoạt động")
    public ResponseEntity<Response<List<SubscriptionPlan>>> getActivePlans() {
        List<SubscriptionPlan> plans = subscriptionService.getActivePlans();
        return ResponseEntity.ok(Response.ok(plans, "Lấy danh sách gói thành công"));
    }

    @GetMapping("/my")
    @Operation(summary = "Xem thông tin gói Subscription hiện tại của user đang đăng nhập")
    public ResponseEntity<Response<UserSubscriptionResponse>> getMySubscription() {
        String userIdStr = securityUtil.getCurrentUserId();
        Long currentUserId = userIdStr != null ? Long.parseLong(userIdStr) : null;
        UserSubscriptionResponse response = subscriptionService.getMySubscription(currentUserId);
        return ResponseEntity.ok(Response.ok(response, "Lấy thông tin gói thành công"));
    }

    @PostMapping("/google-pay/register")
    @Operation(summary = "Đăng ký gói Subscription bằng Google Pay Web Token (Lưu Vault thẻ & Kích hoạt 30 ngày Free Trial)")
    public ResponseEntity<Response<UserSubscriptionResponse>> registerGooglePayWebSubscription(
            @Valid @RequestBody GooglePayWebRegisterRequest request) {
        String userIdStr = securityUtil.getCurrentUserId();
        Long currentUserId = userIdStr != null ? Long.parseLong(userIdStr) : 1L;
        UserSubscriptionResponse response = googlePayWebService.registerTrialSubscription(currentUserId, request);
        return ResponseEntity.ok(Response.ok(response, "Đăng ký gói dùng thử Google Pay Web thành công"));
    }

    @PostMapping("/purchase-onetime")
    @Operation(summary = "Mua gói dịch vụ trả 1 lần (VNPay/Thẻ/Ngân hàng) - Thu tiền ngay, cộng dồn ngày dùng thử vào thời hạn, không tự động gia hạn")
    public ResponseEntity<Response<UserSubscriptionResponse>> purchaseOneTimeSubscription(
            @Valid @RequestBody OneTimeSubscriptionPurchaseRequest request) {
        String userIdStr = securityUtil.getCurrentUserId();
        Long currentUserId = userIdStr != null ? Long.parseLong(userIdStr) : 1L;
        UserSubscriptionResponse response = subscriptionService.purchaseOneTimeSubscription(currentUserId, request);
        return ResponseEntity.ok(Response.ok(response, "Thanh toán mua gói dịch vụ 1 lần thành công"));
    }

    @PostMapping("/{id}/renew")
    @Operation(summary = "Kích hoạt gia hạn trừ tiền định kỳ MIT và hạch toán tự động vào Fineract")
    public ResponseEntity<Response<Boolean>> triggerRenewal(@PathVariable("id") Long subscriptionId) {
        boolean renewed = billingService.processRecurringRenewal(subscriptionId);
        return ResponseEntity.ok(Response.ok(renewed, renewed ? "Gia hạn và hạch toán Fineract thành công" : "Gia hạn thất bại"));
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Hủy tự động gia hạn gói dịch vụ (FTC Click-to-Cancel) - Duy trì quyền lợi VIP cho đến hết ngày hết hạn")
    public ResponseEntity<Response<UserSubscriptionResponse>> cancelSubscription(
            @PathVariable("id") Long subscriptionId,
            @RequestBody(required = false) CancelSubscriptionRequest request) {
        String userIdStr = securityUtil.getCurrentUserId();
        Long currentUserId = userIdStr != null ? Long.parseLong(userIdStr) : 1L;
        String reason = request != null ? request.getReason() : null;
        UserSubscriptionResponse response = subscriptionService.cancelSubscription(currentUserId, subscriptionId, reason);
        return ResponseEntity.ok(Response.ok(response, "Hủy gia hạn gói thành công. Quyền lợi hội viên được duy trì đến ngày hết hạn."));
    }
}
