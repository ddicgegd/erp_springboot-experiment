package com.ddicg.erp.service.dto.request;
import lombok.Data;
@Data
public class UpdateAdminNotesRequest {
    private String orderId;
    private String adminNotes;
    private String notes;
}
