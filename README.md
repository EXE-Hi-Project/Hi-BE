# Hi API - Java Spring Boot

## Yêu cầu

- Java 17+
- Maven 3.8+
- MongoDB (hoặc MongoDB Atlas)

## Chạy development

```bash
# Cách 1: Wrapper npm, dùng được khi chạy riêng BE-Java
npm run dev

# Cách 2: Dùng Maven
mvn spring-boot:run -Dspring-boot.run.profiles=local

# Cách 3: Build rồi chạy
mvn clean package -DskipTests
java -jar target/hi-api-1.0.0.jar --spring.profiles.active=local
```

## Cấu hình

Copy `.env` và chỉnh sửa các biến môi trường:
- `MONGODB_URI` - MongoDB connection string
- `JWT_SECRET` - Secret key cho JWT (ít nhất 32 ký tự)
- `GOOGLE_CLIENT_ID` - Google OAuth Client ID
- `AWS_ACCESS_KEY_ID` / `AWS_SECRET_ACCESS_KEY` - AWS credentials cho Bedrock AI
- `ADMIN_EMAILS` - Danh sách email admin (phân cách bằng dấu phẩy)

## API Endpoints

| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| POST | /api/auth/register | - | Đăng ký |
| POST | /api/auth/login | - | Đăng nhập |
| GET | /api/auth/me | ✓ | Thông tin user hiện tại |
| POST | /api/auth/refresh | ✓ | Làm mới token |
| POST | /api/auth/google | - | Đăng nhập Google |
| GET | /api/users/profile | ✓ | Lấy profile |
| PUT | /api/users/profile | ✓ | Cập nhật profile |
| POST | /api/users/connect-partner | ✓ | Kết nối đối tác |
| DELETE | /api/users/disconnect-partner | ✓ | Ngắt kết nối đối tác |
| GET | /api/users/partner-cycles | ✓ | Chu kỳ của đối tác |
| GET | /api/cycle-records | ✓ | Danh sách chu kỳ |
| GET | /api/cycle-records/insights | ✓ | Kỳ đã ghi nhận, trạng thái dự đoán/trễ, fertility window và xu hướng triệu chứng |
| POST | /api/cycle-records | ✓ | Tạo chu kỳ mới |
| PUT | /api/cycle-records/:id | ✓ | Cập nhật chu kỳ |
| DELETE | /api/cycle-records/:id | ✓ | Xóa chu kỳ |
| GET | /api/daily-logs | ✓ | Nhật ký sức khỏe theo ngày |
| PUT | /api/daily-logs/:date | ✓ | Upsert nhật ký ngày; hỗ trợ `confirmPeriodStart=true` khi có lượng kinh |
| PUT | /api/daily-logs/:date/symptoms/:symptomId | ✓ | Upsert một triệu chứng |
| DELETE | /api/daily-logs/:date/symptoms/:symptomId | ✓ | Xóa một triệu chứng |
| GET | /api/symptom-dictionaries | ✓ | Danh mục triệu chứng active |
| GET | /api/notifications | ✓ | Danh sách thông báo |
| PATCH | /api/notifications/mark-all-read | ✓ | Đánh dấu tất cả đã đọc |
| PATCH | /api/notifications/:id/read | ✓ | Đánh dấu đã đọc |
| GET | /api/chat/history | ✓ | Lịch sử chat |
| POST | /api/chat/send | ✓ | Gửi tin nhắn AI |

`GET /api/cycle-records/insights` trả thêm `daysUntilEstimatedPeriod` cho trạng thái `UPCOMING`
và `estimatedPeriodDay` cho trạng thái `PREDICTED`. Frontend dùng hai field này thay vì tự tính
chênh lệch ngày để tránh sai lệch timezone.
| GET | /api/admin/overview | Admin | Tổng quan admin |
| GET | /api/admin/users | Admin | Danh sách users |
| PATCH | /api/admin/users/:id/role | Admin | Cập nhật role |
| GET | /health | - | Health check |

## Cấu trúc dự án

```
src/main/java/com/hi/api/
├── HiApiApplication.java
├── config/
│   ├── SecurityConfig.java
│   ├── MongoConfig.java
│   └── BedrockConfig.java
├── controller/
│   ├── AuthController.java
│   ├── UserController.java
│   ├── CycleRecordController.java
│   ├── DailyLogController.java
│   ├── SymptomDictionaryController.java
│   ├── NotificationController.java
│   ├── ChatController.java
│   ├── AdminController.java
│   └── HealthController.java
├── dto/
│   ├── ApiResponse.java
│   └── request/
│       └── ...
├── exception/
│   └── GlobalExceptionHandler.java
├── model/
│   ├── User.java
│   ├── Cycle.java
│   ├── Symptom.java
│   ├── Notification.java
│   └── ChatMessage.java
├── repository/
│   └── ...
├── security/
│   ├── JwtUtil.java
│   └── JwtAuthFilter.java
└── service/
    └── ...
```

## Migration dữ liệu sức khỏe

Các collection canonical là `cycle_records`, `daily_logs`, `daily_log_symptoms`, `symptom_dictionaries`.
Collection legacy `cycles` và `symptoms` chỉ được giữ để đối soát, không còn endpoint public.

1. Backup MongoDB trước khi chạy.
2. Chạy dry-run với `HEALTH_DATA_MIGRATION_ENABLED=true` và `HEALTH_DATA_MIGRATION_DRY_RUN=true`.
3. Đặt `HEALTH_DATA_MIGRATION_REPORT_PATH` nếu cần xuất JSON report.
4. Kiểm tra `invalidItems`, `unmappedSymptoms`, rồi chạy lại với `HEALTH_DATA_MIGRATION_DRY_RUN=false`.
5. Chạy dry-run lần cuối; report phải không còn mutation dự kiến.
