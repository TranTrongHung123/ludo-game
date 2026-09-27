# Sơ đồ Hệ thống — Game Cờ Cá Ngựa (Ludo Game)

Thư mục này chứa các sơ đồ trực quan hóa luồng của tất cả các feature lớn trong hệ thống, được tạo bằng Mermaid Diagram.

## Danh sách sơ đồ

| #   | File                                                                                           | Nội dung                                                     | Số sơ đồ |
| --- | ---------------------------------------------------------------------------------------------- | ------------------------------------------------------------ | -------- |
| 1   | [01-system-architecture.md](01-system-architecture.md)                                         | Kiến trúc tổng quan hệ thống, phân tầng trách nhiệm          | 2        |
| 2   | [02-authentication-flow.md](02-authentication-flow.md)                                         | Đăng ký, đăng nhập, đăng xuất                                | 3        |
| 3   | [03-room-management-flow.md](03-room-management-flow.md)                                       | Vòng đời phòng, tạo/tham gia, mời, Ready, bắt đầu trận       | 5        |
| 4   | [04-gameplay-turn-flow.md](04-gameplay-turn-flow.md)                                           | Lượt chơi, tung xúc xắc, di chuyển quân, timeout, bonus      | 5        |
| 5   | [05-board-pieces-special-cells.md](05-board-pieces-special-cells.md)                           | Bàn cờ, tọa độ, trạng thái quân, ô đặc biệt & hiệu ứng       | 5        |
| 6   | [06-connection-heartbeat-reconnect.md](06-connection-heartbeat-reconnect.md)                   | TCP framing, heartbeat, mất kết nối, grace period, reconnect | 4        |
| 7   | [07-game-end-scoring-rematch.md](07-game-end-scoring-rematch.md)                               | Kết thúc trận, tính điểm, lưu DB, chơi lại                   | 5        |
| 8   | [08-lobby-chat-ranking-database.md](08-lobby-chat-ranking-database.md)                         | Sảnh online, chat, xếp hạng, lịch sử, ER diagram             | 4        |
| 9   | [09-concurrency-security-message-processing.md](09-concurrency-security-message-processing.md) | Thread model, lock, luồng xử lý message, bảo mật             | 4        |

## Các loại sơ đồ được sử dụng

| Loại sơ đồ           | Mục đích                                                           |
| -------------------- | ------------------------------------------------------------------ |
| **Sequence Diagram** | Luồng tương tác giữa các thành phần theo thời gian                 |
| **State Diagram**    | Vòng đời trạng thái (phòng, quân cờ, player presence, participant) |
| **Flowchart**        | Luồng quyết định và xử lý logic                                    |
| **ER Diagram**       | Mô hình cơ sở dữ liệu                                              |
| **Graph**            | Kiến trúc, phân tầng, bố trí ô đặc biệt                            |
