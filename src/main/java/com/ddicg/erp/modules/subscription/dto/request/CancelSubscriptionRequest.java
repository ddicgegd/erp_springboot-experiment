package com.ddicg.erp.modules.subscription.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@Schema(description = "Yêu cầu hủy gia hạn gói dịch vụ (FTC Click-to-Cancel)")
public class CancelSubscriptionRequest {

    @Schema(description = "Lý do hủy gia hạn", example = "Không còn nhu cầu sử dụng dịch vụ")
    String reason;
}
