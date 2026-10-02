package com.ddicg.erp.modules.subscription.controller;

import com.ddicg.erp.core.common.dto.response.Response;
import com.ddicg.erp.modules.subscription.dto.request.GooglePurchaseVerifyRequest;
import com.ddicg.erp.modules.subscription.dto.response.UserSubscriptionResponse;
import com.ddicg.erp.modules.subscription.model.SubscriptionPlan;
import com.ddicg.erp.modules.subscription.service.GooglePlayVerificationService;
import com.ddicg.erp.modules.subscription.service.SubscriptionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/subscriptions")
@RequiredArgsConstructor
public class SubscriptionController {

    private final GooglePlayVerificationService verificationService;
    private final SubscriptionService subscriptionService;

    @GetMapping("/plans")
    @ResponseStatus(HttpStatus.OK)
    public Response<List<SubscriptionPlan>> getActivePlans() {
        return Response.ok(subscriptionService.getAllActivePlans(), "Lấy danh sách gói thành công");
    }

    @PostMapping("/google/verify")
    @ResponseStatus(HttpStatus.OK)
    public Response<UserSubscriptionResponse> verifyGooglePurchase(
            @RequestHeader(value = "X-User-Id", defaultValue = "1") Long userId,
            @Valid @RequestBody GooglePurchaseVerifyRequest request) {
        UserSubscriptionResponse response = verificationService.verifyAndActivate(userId, request);
        return Response.ok(response, "Kích hoạt gói Google Play thành công");
    }

    @GetMapping("/my")
    @ResponseStatus(HttpStatus.OK)
    public Response<UserSubscriptionResponse> getMySubscription(
            @RequestHeader(value = "X-User-Id", defaultValue = "1") Long userId) {
        return subscriptionService.getLatestSubscriptionForUser(userId)
                .map(res -> Response.ok(res, "Lấy thông tin gói thành công"))
                .orElseGet(() -> Response.ok(null, "Chưa đăng ký gói nào"));
    }
}
