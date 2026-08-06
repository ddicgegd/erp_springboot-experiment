package com.ddicg.erp.model.embedded;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditEntry {
    private String action;
    private String performBy;
    private String updatedBy;
    private LocalDateTime timestamp;
    private LocalDateTime updatedAt;
    private String details;
}
