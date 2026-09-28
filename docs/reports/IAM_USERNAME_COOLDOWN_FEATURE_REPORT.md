# 📊 BÁO CÁO KỸ THUẬT: HẠN ĐỔI USERNAME TRÊN REDIS & PHẢN HỒI THỜI GIAN TRÊN API /ME
## *Hệ thống ERP Spring Boot 3.5.0 — Module IAM*

- **Dự án:** ERP Spring Boot Experiment System
- **Module:** IAM (`com.ddicg.erp.modules.iam`)
- **Tài liệu tham chiếu:** `docs/features/IAM_NOTIFICATION_FE_SYNC.md`
- **Phiên bản:** `v1.2.2`
- **Ngày hoàn thành:** 26/09/2026
- **Trạng thái:** ✅ **Production-Ready & Fully Verified** (275/275 Tests Passed)

---

## 1. ⚙️ CƠ CHẾ HOẠT ĐỘNG (2 BƯỚC CỐT LÕI)

```
[1. KHI NGƯỜI DÙNG ĐỔI USERNAME]
PUT /api/auth/change-username
  │
  ├─ 1. Lưu username mới vào CSDL Oracle.
  ├─ 2. Cài khóa 1 tháng trên Redis:
  │     Key: "auth:guard:cooldown:{userId}"  |  TTL: 30 ngày (TimeUnit.DAYS)
  └─ 3. Thu hồi toàn bộ Access & Refresh Token cũ.

─────────────────────────────────────────────────────────────────────────────

[2. KHI GỌI LẤY PROFILE]
GET /api/auth/me
  │
  ├─ 1. Service đọc TTL còn lại của key trên Redis.
  └─ 2. Trả về đúng 1 trường thời gian hết hạn duy nhất:
        • Đang bị khóa  -> usernameCooldownUntil: "2026-10-26 20:15:00"
        • Không bị khóa -> usernameCooldownUntil: null
```

---

## 2. 💻 CHI TIẾT CODE XỬ LÝ (`UserService.java`)

### A. Khi đổi tên: Lưu khóa 1 tháng vào Redis
```java
// Đặt khóa Cooldown 1 tháng (30 ngày) cho user:
redisService.setValueWithExpiry(RedisTable.AUTH_GUARD_COOLDOWN, user.getId(), "true", 30, TimeUnit.DAYS);
```

### B. Khi gọi `/me`: Đọc thời hạn từ Redis
```java
LocalDateTime usernameCooldownUntil = null;
if (user.getId() != null && redisService != null) {
    Long ttlSeconds = redisService.getExpireSeconds(RedisTable.AUTH_GUARD_COOLDOWN.key(user.getId()));
    if (ttlSeconds != null && ttlSeconds > 0) {
        usernameCooldownUntil = LocalDateTime.now().plusSeconds(ttlSeconds);
    }
}
```

---

## 3. 📋 JSON PHẢN HỒI `GET /api/auth/me`

### A. Trường hợp đang trong hạn 1 tháng (Có ngày hết hạn)
```json
{
  "status": {
    "code": 200,
    "message": "Success"
  },
  "data": {
    "id": "100",
    "username": "alex_developer",
    "email": "alex@example.com",
    "fullName": "Alex Developer",
    "phoneNumber": "0901234567",
    "usernameCooldownUntil": "2026-10-26 20:15:00",
    "dateOfBirth": "1998-09-26",
    "avatarUrl": "https://s3.ddicg.com/avatars/user-100.png",
    "gender": "MALE",
    "rank": "PLATINUM",
    "status": "ACTIVE",
    "roles": ["ROLE_USER"]
  }
}
```

### B. Trường hợp được phép đổi tên bình thường (`null`)
```json
{
  "status": {
    "code": 200,
    "message": "Success"
  },
  "data": {
    "id": "100",
    "username": "alex_developer",
    "email": "alex@example.com",
    "fullName": "Alex Developer",
    "phoneNumber": "0901234567",
    "usernameCooldownUntil": null,
    "dateOfBirth": "1998-09-26",
    "avatarUrl": "https://s3.ddicg.com/avatars/user-100.png",
    "gender": "MALE",
    "rank": "PLATINUM",
    "status": "ACTIVE",
    "roles": ["ROLE_USER"]
  }
}
```

---

## 4. 🧪 KẾT QUẢ KIỂM THỬ (TEST VERIFICATION)
- Kiểm tra tính toán `usernameCooldownUntil` trong `GET /api/auth/me`: ✅ **Passed**.
- Kiểm tra chặn `PUT /api/auth/change-username` khi còn hạn 30 ngày: ✅ **Passed**.
- Toàn bộ test suite: ✅ **275/275 tests passed (BUILD SUCCESS)**.
