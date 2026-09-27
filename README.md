# Ludo Game — Cờ Cá Ngựa

Bài tập lớn môn **Lập trình mạng**, nhóm **1**, lớp **CT01**.

**Đề tài**: Game Cờ Cá Ngựa cho **2 – 4 người chơi** theo mô hình Client–Server.

## Thành viên

| Thành viên      | Mã sinh viên |
| --------------- | ------------ |
| Trần Trọng Hùng | B23DCCN363   |
| Hoàng Đình Duy  | B23DCCN237   |
| Đặng Phi Long   | B23DCCN497   |
| Tạ Thanh Thiên  | B23DCCN783   |

## Chức năng

- Đăng ký, đăng nhập và quản lý phiên tài khoản.
- Sảnh trực tuyến; tạo, tham gia, mời người chơi và Ready trong phòng.
- Thi đấu với 4 quân mỗi người, đá quân, ô đặc biệt và giới hạn thời gian mỗi phase.
- Chat trong phòng, khôi phục kết nối và xử lý bỏ cuộc.
- Kết quả trận, chơi lại cùng phòng, lịch sử và bảng xếp hạng.

## Kiến trúc và công nghệ

```text
Unity Client ── TCP + length-prefixed UTF-8 JSON ── Java Server ── JDBC ── MySQL
```

| Thành phần         | Công nghệ                                                |
| ------------------ | -------------------------------------------------------- |
| Server             | Java 21, Maven, TCP Socket, Jackson                      |
| Client             | Unity 6, C#, uGUI / Canvas, TextMeshPro, Newtonsoft.Json |
| Database           | MySQL 8, JDBC, HikariCP, Flyway                          |
| Bảo mật và logging | BCrypt, SLF4J, Logback                                   |
| Kiểm thử và CI     | JUnit 5, Mockito, GitHub Actions                         |

## Cấu trúc dự án

```text
ludo-game/
├── README.md       # Giới thiệu dự án
├── SYSTEM.md       # Kiến trúc, giao thức, dữ liệu và luật game
├── RUN.md          # Cài đặt, chạy và kiểm thử
├── pom.xml         # Maven: common, server
├── compose.yaml    # MySQL
├── ludo.ps1        # Lệnh chạy bằng PowerShell
├── common/         # DTO, enum và framing Java
├── server/         # Game Server, migration và test
└── client/         # Unity Client
```

Unity sử dụng cùng JSON contract với `common`, không import JAR. Maven chỉ build hai module Java; client được build bằng Unity Editor.

## Chạy nhanh

Yêu cầu **Java 21**, **Docker Desktop** đang chạy và Unity Editor theo
[ProjectVersion.txt](client/ProjectSettings/ProjectVersion.txt).

Từ thư mục gốc, mở PowerShell:

```powershell
.\ludo.ps1 db
.\ludo.ps1 server
```

Sau khi Server khởi động, mở thư mục `client/` bằng Unity Hub, mở
`Assets/Scenes/LoginScene.unity` rồi nhấn **Play**. Kết nối mặc định là `127.0.0.1:5555`.

Đăng ký/đăng nhập, tạo hoặc tham gia phòng; tất cả thành viên Ready rồi chủ phòng bắt đầu trận.
Xem [RUN.md](RUN.md) để cấu hình database, chạy qua LAN và build nhiều client.

## Luật chính

- Mỗi người có 4 quân; đổ **6** để ra quân.
- Vòng chung có **48 ô**, mỗi màu có **6 nấc đích**.
- Được đi xuyên qua quân khác; không được dừng trên quân mình. Dừng trên đối phương thì đá quân đó về bãi.
- Phải đến chính xác nấc 6 để hoàn thành quân. Hoàn thành 4 quân được xếp hạng.
- `SPEED`: tiến 3 bước; `SLOW`: lùi ngay 1 bước; `LUCKY`: thưởng một lần tung; `TRAP`: về bãi.
- Mỗi phase tung/chọn quân có **120 giây**. Mất kết nối có **60 giây** để khôi phục; bỏ cuộc chủ động có hiệu lực ngay.

Quy tắc đầy đủ, hệ điểm và contract được mô tả trong [SYSTEM.md](SYSTEM.md).

## Tài liệu

- [SYSTEM.md](SYSTEM.md): đặc tả hệ thống.
- [RUN.md](RUN.md): hướng dẫn cài đặt, vận hành và kiểm thử.
- [client/README.md](client/README.md): cấu trúc và sử dụng Unity Client.
