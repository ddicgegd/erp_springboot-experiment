# Task List: Tối ưu hóa cấu trúc RedisConfiguration & Chuẩn hóa toàn diện các Service sử dụng Redis

## Task 1: Chuẩn hóa RedisConfiguration.java (Enum RedisTable & Lookup fromKey)
- [x] Enum `RedisTable` định nghĩa rõ ràng các bảng logic trên db(0) kèm cờ `boolean immutable` và phương thức `fromKey(String key)`.
- [x] Lược bỏ hoàn toàn các function logic nghiệp vụ khỏi file cấu hình.

## Task 2: Tự động hóa kiểm soát quyền qua Spring AOP Aspect (RedisImmutabilityAspect) & Tinh gọn RedisService
- [x] `RedisImmutabilityAspect` (đặt tại `com.ddicg.erp.core.exception`) tự động chặn mọi lệnh ghi vi phạm bảng bất biến.
- [x] `RedisService.java` hoàn toàn sạch sẽ, không còn dòng code kiểm tra quyền thủ công.

## Task 3: Rà soát & Đồng bộ toàn bộ các Service sử dụng Redis trong dự án
- [x] Module IAM: Chuẩn hóa `UserService.java`, `RefreshTokenService.java`, `RedisRecoveryTokenStore.java`.
- [x] Module Cart: Chuẩn hóa `ShoppingCartServiceImpl.java`.
- [x] Module Merchandise: Chuẩn hóa `ProductCachingService.java`.
- [x] Module Order: Chuẩn hóa `VoucherRedisService.java`, `OrderService.java` (dùng `RedisTable.LOCK_ORDER`).
- [x] Module Core/Security: Chuẩn hóa `RedisProducerService.java`, `RedisConsumer.java`.

## Checkpoint: Hoàn thành & Toàn bộ Test Suite Dự án Pass 100%
- [x] `./mvnw test-compile` thành công 100%.
- [x] `./mvnw test` toàn dự án: **86/86 tests PASS 100%**, không có bất kỳ lỗi hay regression nào.
