# flight-provider (mock airline)

Mock airline service cho **Dididi Booking Platform** (Phase 1.5). Spring Boot 3.5.14 · Java 17 · port **8081** · DB `flight_provider`.

Booking Platform sẽ pull dữ liệu chuyến bay từ service này qua REST API, xác thực bằng header `X-API-KEY`.

## Prerequisites
- JDK 17, MySQL 8 (localhost:3306)
- Không cần Redis, không cần Lombok.

## Setup DB
Chạy trong DBeaver (Alt+X để chạy cả script):
```sql
CREATE DATABASE IF NOT EXISTS flight_provider CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
GRANT ALL PRIVILEGES ON flight_provider.* TO 'booking'@'localhost';
FLUSH PRIVILEGES;
```
(Dùng lại user `booking` đã tạo ở booking-platform. Muốn user riêng thì tạo thêm rồi sửa `application.yml`.)

## Run
```bash
./mvnw spring-boot:run
```
Lần chạy đầu Hibernate tạo bảng (`airlines`, `airports`, `flights`, `flight_bookings`) rồi `data.sql`
seed 30 chuyến (20 chuyến HAN→SGN ngày 2026-07-01). `data.sql` dùng `INSERT IGNORE` nên chạy lại không lỗi.

## Mock website (public, không cần API key)
- http://localhost:8081/ — trang chủ "VN Airlines Mock" + form tìm chuyến
- http://localhost:8081/flights?from=HAN&to=SGN&date=2026-07-01 — danh sách kết quả
- http://localhost:8081/flights/1 — chi tiết 1 chuyến

## REST API (cần header `X-API-KEY: dev-flight-key-12345`)
```bash
KEY="dev-flight-key-12345"

# Search -> trả 20 chuyến HAN-SGN ngày 2026-07-01
curl -s "localhost:8081/api/flights/search?from=HAN&to=SGN&date=2026-07-01" -H "X-API-KEY: $KEY"

# Chi tiết 1 chuyến
curl -s localhost:8081/api/flights/1 -H "X-API-KEY: $KEY"

# Đặt chỗ (giữ ghế, trả confirmationCode)
curl -s -X POST localhost:8081/api/flights/1/book -H "X-API-KEY: $KEY" \
  -H "Content-Type: application/json" \
  -d '{"passengerName":"Nguyen Van A","contactEmail":"a@example.com","seats":2}'

# Huỷ theo confirmationCode (trả ghế lại)
curl -s -X POST "localhost:8081/api/flights/1/cancel?confirmationCode=FLXXXXXXXX" -H "X-API-KEY: $KEY"
```
Không gửi (hoặc sai) `X-API-KEY` → **401** `{"error":"Invalid API key"}`.

## Khác biệt so với tài liệu
- **Không dùng `spring-boot-starter-security`**: việc auth `/api/*` đã do `ApiKeyAuthFilter`
  (servlet filter thường) đảm nhiệm; web page là public. Bỏ security tránh bị form-login mặc định khoá hết.
- Gộp `FlightInventory` vào field `availableSeats` trên `Flight` cho gọn (đủ cho book/cancel của bản mock).
  Cần inventory theo từng ngày thì tách bảng riêng sau.

## Tests
`./mvnw test` — `FlightServiceTest` là unit test thuần (Mockito, không cần DB).
