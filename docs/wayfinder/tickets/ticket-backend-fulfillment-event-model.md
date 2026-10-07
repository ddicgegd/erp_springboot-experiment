# ticket-backend-fulfillment-event-model

**Label:** `wayfinder:grilling`  
**Parent Map:** `map-order-fulfillment-diagnostic`  
**Status:** Open  
**Assignee:** Unassigned  
**Blocked by:** []

---

## Question

Backend Spring Boot cần mô hình hóa cấu trúc lưu trữ và chuyển đổi trạng thái đơn hàng như thế nào để ghi nhận đầy đủ dòng thời gian sự kiện (Fulfillment History Event), bao gồm thời điểm (timestamp), tiêu đề bước (stage title), thông điệp chẩn đoán chi tiết (diagnostic message/subtext), tác nhân thực hiện (system/warehouse/carrier), thay vì chỉ lưu enum phẳng `List<OrderStatus>` trong cột CLOB như hiện tại? Cần giải quyết việc tương thích dữ liệu cũ và đảm bảo tính bất biến (append-only audit log) như thế nào?
