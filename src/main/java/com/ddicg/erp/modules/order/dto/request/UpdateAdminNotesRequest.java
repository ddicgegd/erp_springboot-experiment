package com.ddicg.erp.modules.order.dto.request;
import lombok.Data;
@Data
public class UpdateAdminNotesRequest {
    private String orderId;
    private String adminNotes;
    private String notes;

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }
    public String getAdminNotes() { return adminNotes; }
    public void setAdminNotes(String adminNotes) { this.adminNotes = adminNotes; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

}
