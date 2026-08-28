# Implementation Plan: Tối ưu hóa cấu trúc RedisConfiguration & Chuẩn hóa phân quyền bảng logic

## Overview
Chuẩn hóa cấu trúc `RedisConfiguration`, định nghĩa tập trung các logical tables qua enum `RedisTable` (nằm trong `db(0)`) kèm theo cờ `boolean` phân quyền sửa đổi dữ liệu (`immutable`). Đảm bảo cơ chế tự động kiểm tra và báo lỗi (`validateWrite`) khi thực hiện các thao tác ghi/sửa đổi dữ liệu (`setValue`, `hSet`, v.v.) trên các bảng cấm sửa đổi, mang lại mã nguồn sạch sẽ, chuyên nghiệp và đúng chuẩn Spring Boot.

## Architecture Decisions
- **Single Database (db(0)):** Toàn bộ dữ liệu Redis của ứng dụng ERP nằm trên database index 0, sử dụng cấu hình tập trung đơn giản và nhất quán.
- **Logical Partitioning & Boolean Permission in `RedisTable`:** Mỗi bảng logic có prefix riêng biệt và cờ `boolean immutable` để quy định quyền sửa đổi dữ liệu đã tồn tại.
- **Automated Validation on Write Operations:** Mọi thao tác ghi dữ liệu theo bảng (`setValue`, `setValueWithExpiry`, `setValueWithJitter`, `hSet`,...) trong `RedisService` đều tự động kích hoạt `table.validateWrite(keyExists)` để ném `BusinessException(ErrorCode.FORBIDDEN, ...)` nếu cố tình ghi đè lên dữ liệu đã tồn tại trên bảng `immutable`.
- **Spring Beans Standardization:** Cấu hình Bean `RedisTemplate<String, Object>` với serializer chuẩn (`StringRedisSerializer`, `GenericJackson2JsonRedisSerializer`) và `StreamMessageListenerContainer` cho Redis Streams.

## Task List

### Phase 1: Chuẩn hóa Cấu hình & Enum Table
- [x] Task 1: Chuẩn hóa `RedisConfiguration.java` (Enum `RedisTable` & Bean configurations)

### Phase 2: Chuẩn hóa Tầng Service & Kiểm tra Tự động
- [x] Task 2: Rà soát và đồng bộ hóa tự động kiểm tra quyền trong `RedisService.java` & `iRedis.java`

### Phase 3: Kiểm thử & Đảm bảo Chất lượng
- [x] Task 3: Cập nhật & mở rộng Unit Test trong `RedisTableConfigTest.java` và `RedisServiceTest.java`

### Checkpoint: Complete
- [x] `./mvnw test-compile` thành công 100% không cảnh báo lỗi
- [x] Các test liên quan đến Redis (`RedisTableConfigTest`, `RedisServiceTest`, `VoucherRedisServiceTest`) pass 100% (15/15 tests)

## Risks and Mitigations
| Risk | Impact | Mitigation |
|------|--------|------------|
| Thiếu sót kiểm tra quyền ở một phương thức ghi theo bảng trong `RedisService` | Med | Rà soát toàn bộ các phương thức thao tác bảng trong `RedisService` và đối chiếu với interface `iRedis` |
| Thay đổi làm ảnh hưởng đến các service nghiệp vụ đang gọi `RedisTable` | High | Giữ nguyên tên enum `RedisTable`, tên bảng và chữ ký phương thức `key(Object id)` |

## Open Questions
- Không còn câu hỏi mở (đã hoàn thành kiểm thử thành công).
