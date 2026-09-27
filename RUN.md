# Hướng dẫn chạy Ludo Game

## 1. Yêu cầu

- Java 21; Maven Wrapper đã có trong repository.
- Docker Desktop hoặc Docker Engine có Compose để chạy MySQL 8.4.
- Unity Editor theo [client/ProjectSettings/ProjectVersion.txt](client/ProjectSettings/ProjectVersion.txt).
- Chạy các lệnh bên dưới từ thư mục gốc dự án.

Nếu PowerShell chặn script, cho phép trong terminal hiện tại:

```powershell
Set-ExecutionPolicy -Scope Process Bypass
```

## 2. Khởi động database

```powershell
.\ludo.ps1 db
```

Đợi MySQL có trạng thái healthy trước khi chạy Server.

| Tham số mặc định | Giá trị             |
| ---------------- | ------------------- |
| Host/port        | `localhost:3306`    |
| Database         | `ludo_game`         |
| User             | `ludo`              |
| Password local   | `ludo_dev_password` |
| Volume dữ liệu   | `mysql_data`        |

Compose chỉ publish MySQL trên loopback của máy host. Các client chơi qua TCP Game Server, không cần truy cập cổng database.

## 3. Khởi động Java Server

```powershell
.\ludo.ps1 server
```

Lệnh build `common` và `server` rồi chạy Server; giữ terminal mở. Dùng **Ctrl+C** để dừng.

Server mặc định lắng nghe TCP `5555`, tạo HikariCP pool và chạy Flyway migration trước khi nhận request.

## 4. Khởi động Unity Client

1. Unity Hub → **Add project from disk** → chọn thư mục `client/`.
2. Mở bằng phiên bản Editor ghi trong `ProjectVersion.txt`.
3. Mở `Assets/Scenes/LoginScene.unity`, nhấn **Play**.
4. Đăng ký/đăng nhập, tạo hoặc vào phòng; tất cả Ready rồi chủ phòng Start.

Client mặc định dùng `127.0.0.1:5555`. Đổi `Server Host` / `Server Port` trên GameObject
`NetworkSession` của LoginScene hoặc đặt `SERVER_HOST` / `SERVER_PORT` trước khi mở Editor/player.
Editor đã chạy không nhận biến môi trường vừa đặt từ terminal khác.

Chơi qua LAN: dùng IP máy Server cho `SERVER_HOST` trên các client và cho phép cổng TCP của Server qua firewall.

### Build và chạy nhiều client

Mở **File → Build Profiles**, chọn nền tảng desktop. Bật tám scene Login, Register,
Lobby, Room, Game, Result, Ranking, History; đặt Login đầu tiên.
Mở nhiều instance player và đăng nhập các tài khoản khác nhau. Có thể dùng một Editor Play Mode cùng các player.
Maven không build Unity; chi tiết client nằm trong [client/README.md](client/README.md).

## 5. Cấu hình môi trường

Có thể sao chép [.env.example](.env.example) thành `.env` để thay đổi biến Compose như
`MYSQL_DATABASE`, `MYSQL_USER`, `MYSQL_PASSWORD`, `MYSQL_ROOT_PASSWORD`, `MYSQL_PORT` và `TZ`.
`.env` được Git bỏ qua.

**Java Server không tự đọc file `.env`.** Các biến cho Server phải đặt trong terminal khởi chạy hoặc Run Configuration của IDE. Nếu đổi database/user/password/port của Compose, phải cấu hình Server tương ứng. Thay biến khởi tạo MySQL không tự đổi tài khoản trong volume đã có dữ liệu.

| Biến Server                | Mặc định / ý nghĩa                                             |
| -------------------------- | -------------------------------------------------------------- |
| `SERVER_PORT`              | `5555`                                                         |
| `SERVER_WORKER_THREADS`    | `32`                                                           |
| `DB_URL`                   | JDBC URL đầy đủ; ưu tiên hơn host/port/name                    |
| `DB_HOST`                  | `localhost`                                                    |
| `DB_PORT`                  | `3306`; có thể lấy từ `MYSQL_PORT` trong môi trường tiến trình |
| `DB_NAME`                  | `ludo_game`; có thể lấy từ `MYSQL_DATABASE`                    |
| `DB_USER`                  | `ludo`; có thể lấy từ `MYSQL_USER`                             |
| `DB_PASSWORD`              | `ludo_dev_password`; có thể lấy từ `MYSQL_PASSWORD`            |
| `DB_POOL_SIZE`             | `10`                                                           |
| `DB_CONNECTION_TIMEOUT_MS` | `10000`                                                        |

Ví dụ đặt cổng Server:

```powershell
$env:SERVER_PORT='5556'
.\ludo.ps1 server
```

Client phải dùng cùng cổng. Mật khẩu mẫu chỉ phục vụ môi trường local.

## 6. Build và kiểm thử

Build backend kèm test:

```powershell
.\mvnw.cmd -pl server -am install
```

Chạy toàn bộ Maven verify:

```powershell
.\mvnw.cmd verify
```

Bật MySQL integration test khi database dành cho kiểm thử sẵn sàng:

```powershell
$env:RUN_MYSQL_INTEGRATION_TESTS='true'
.\mvnw.cmd verify
Remove-Item Env:RUN_MYSQL_INTEGRATION_TESTS
```

Các test MySQL tạo/xóa dữ liệu test. Nếu không bật cờ, chúng được bỏ qua.
[CI](.github/workflows/ci.yml) bật cờ này và cấp MySQL service riêng.
Test Java nằm trong `common/src/test/` và `server/src/test/`; Maven không kiểm thử Unity.

Kiểm tra toàn hệ thống bằng 2–4 client: đăng nhập, tạo/vào phòng, Ready/Start,
tung và đi quân, chat, ngắt/kết nối lại, thoát trận, xem kết quả, chơi lại, lịch sử và xếp hạng.
Đặc tả tình huống và bất biến nằm trong [SYSTEM.md](SYSTEM.md).

## 7. Dừng và theo dõi

```powershell
.\ludo.ps1 status   # Trạng thái MySQL
.\ludo.ps1 stop     # Dừng MySQL, giữ dữ liệu
.\ludo.ps1 build    # Maven install
.\ludo.ps1 test     # Maven verify
.\ludo.ps1 help     # Trợ giúp
```

Xem log database bằng `docker compose logs -f mysql`. Dừng Java Server bằng Ctrl+C,
thoát Play Mode hoặc đóng Unity player. `docker compose down` giữ volume;
`docker compose down -v` xóa dữ liệu database và chỉ dùng khi muốn khởi tạo lại.
