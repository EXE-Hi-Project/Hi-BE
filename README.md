# Hi API - Java Spring Boot

## Yêu cầu

- Java 17+
- Maven 3.8+
- MongoDB (hoặc MongoDB Atlas)

## Chạy development

```bash
# Cách 1: Dùng Maven
mvn spring-boot:run -Dspring-boot.run.profiles=local

# Cách 2: Build rồi chạy
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
| GET | /api/cycles | ✓ | Danh sách chu kỳ |
| POST | /api/cycles | ✓ | Tạo chu kỳ mới |
| PUT | /api/cycles/:id | ✓ | Cập nhật chu kỳ |
| DELETE | /api/cycles/:id | ✓ | Xóa chu kỳ |
| GET | /api/symptoms | ✓ | Danh sách triệu chứng |
| POST | /api/symptoms | ✓ | Tạo triệu chứng |
| DELETE | /api/symptoms/:id | ✓ | Xóa triệu chứng |
| GET | /api/notifications | ✓ | Danh sách thông báo |
| PATCH | /api/notifications/mark-all-read | ✓ | Đánh dấu tất cả đã đọc |
| PATCH | /api/notifications/:id/read | ✓ | Đánh dấu đã đọc |
| GET | /api/chat/history | ✓ | Lịch sử chat |
| POST | /api/chat/send | ✓ | Gửi tin nhắn AI |
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
│   ├── CycleController.java
│   ├── SymptomController.java
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
