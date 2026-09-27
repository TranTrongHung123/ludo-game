# Unity Client — Ludo Game

Client C# dùng Unity 6, uGUI / Canvas và TextMeshPro. Server Java quyết định
trạng thái trận đấu; client gửi yêu cầu qua TCP và hiển thị dữ liệu nhận được.

## Chạy client

1. Khởi động MySQL và Java Server theo [RUN.md](../RUN.md).
2. Mở thư mục `client/` bằng Unity Hub, dùng phiên bản trong
   [ProjectVersion.txt](ProjectSettings/ProjectVersion.txt).
3. Mở `Assets/Scenes/LoginScene.unity` và nhấn **Play**.
4. Đăng ký/đăng nhập, tạo hoặc tham gia phòng. Tất cả thành viên Ready rồi chủ phòng bắt đầu trận.

Mặc định kết nối `127.0.0.1:5555`. Đổi `Server Host` / `Server Port` trên
GameObject `NetworkSession` của LoginScene, hoặc đặt `SERVER_HOST` / `SERVER_PORT`
trước khi mở Editor/player. Chơi qua LAN dùng IP máy Server.

## Build desktop

Mở **File → Build Profiles**, chọn nền tảng desktop và bật tám scene:
Login, Register, Lobby, Room, Game, Result, Ranking, History; đặt Login đầu tiên.
Mở nhiều instance player với tài khoản riêng để chơi nhiều người.
Maven chỉ build backend Java; client được build trong Unity Editor.

## Cấu trúc

| Thư mục | Nội dung |
| --- | --- |
| `Assets/Scenes/` | Các màn hình của game |
| `Assets/Prefabs/` | Prefab giao diện và quân cờ |
| `Assets/Scripts/Network/` | TCP, framing, đọc/ghi bất đồng bộ |
| `Assets/Scripts/Services/` | Session, phòng, trận đấu, xếp hạng, lịch sử và âm thanh |
| `Assets/Scripts/Controllers/` | Thao tác người dùng và điều phối màn hình |
| `Assets/Scripts/Views/` | Hiển thị bàn cờ, quân cờ và các thành phần giao diện |
| `Assets/Art/`, `Assets/Audio/`, `Assets/Fonts/` | Hình ảnh, âm thanh và font |
| `Packages/`, `ProjectSettings/` | Dependency và cấu hình Unity |

## Kết nối và trạng thái

- TCP dùng length prefix 4 byte big-endian, JSON UTF-8, tối đa 65.536 byte mỗi frame.
- `NetworkSession` giữ kết nối xuyên scene và cập nhật UI trên Unity main thread.
- Client trả heartbeat và thử reconnect bằng session trong RAM khi mất kết nối.
- Xúc xắc, nước đi hợp lệ, timer, điểm và kết quả đều lấy từ Server.
- Mật khẩu và session token không được ghi vào log hoặc PlayerPrefs.

Luật và contract đầy đủ nằm trong [SYSTEM.md](../SYSTEM.md).

## Tài nguyên

Font Liberation Sans và giấy phép OFL nằm trong `Assets/TextMesh Pro/Fonts/`.
Các thông tin attribution đi kèm tài nguyên được giữ trong `Assets/`.
Hình minh họa `Assets/Art/Login/ludo-pieces.png` được tạo bằng công cụ tạo ảnh AI; các hình nền và biểu tượng giao diện được tạo riêng cho dự án.
