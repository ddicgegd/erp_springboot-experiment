# ticket-api-contract-order-journey

**Label:** `wayfinder:prototype`  
**Parent Map:** `map-order-fulfillment-diagnostic`  
**Status:** Open  
**Assignee:** Unassigned  
**Blocked by:** [ticket-backend-fulfillment-event-model]

---

## Question

Hợp đồng dữ liệu API RESTful (DTO Response) giữa Spring Boot Backend và Frontend cần được thiết kế ra sao để mở rộng `MyOrderDetailResponse` (hoặc cung cấp endpoint chuyên biệt `GET /api/v1/orders/my-orders/{orderNumber}/journey`) trả về cấu trúc `DeliveryStepResponse` chuẩn xác với các trường: `title`, `diagnosticDescription` (chữ chẩn đoán mờ), `timestamp`, `stage`, `completed`, `active`, `carrierInfo`? Làm thế nào để đảm bảo backward compatibility với các client cũ đang đọc `currentStatusDescription`?
