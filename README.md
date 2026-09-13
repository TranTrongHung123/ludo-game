# Ludo Game - Bài tập lớn Lập trình mạng

Game **Cờ Cá Ngựa (Ludo)** nhiều người chơi được xây dựng bằng Java theo mô hình **Client–Server**.

## Công nghệ

- Java 21
- Maven multi-module
- JavaFX + FXML
- TCP Socket
- JSON / Jackson
- MySQL 8
- JDBC + HikariCP
- Flyway
- SLF4J + Logback
- JUnit 5 + Mockito

## Kiến trúc

```text
JavaFX Client
      |
      | TCP + Length-Prefixed UTF-8 JSON
      v
  Game Server
      |
      | JDBC + HikariCP
      v
    MySQL
```

Server là **nguồn sự thật duy nhất** của trận đấu:

- Server sinh xúc xắc.
- Server validate nước đi.
- Server quản lý lượt chơi.
- Server cập nhật Game State.
- Server xử lý timeout, disconnect và reconnect.
- Client chỉ gửi yêu cầu và hiển thị kết quả do Server trả về.

## Cấu trúc project

```text
ludo-game/
├── AGENTS.md
├── PROJECT_PLAN.md
├── pom.xml
├── common/
├── server/
└── client/
```

### `common`

Chứa code dùng chung giữa Client và Server:

- DTO
- Protocol
- MessageType
- Error model
- Game State DTO
- Shared enums

### `server`

Chứa:

- TCP Server
- Session
- Lobby
- Room
- Game Engine
- Authentication
- Repository
- Database
- Ranking
- Timeout / Reconnect

### `client`

Chứa:

- JavaFX
- FXML
- Controller
- Network Client
- Client State
- UI Service

## Quy mô game

- 2-4 người chơi / phòng
- 4 quân / người
- 4 màu: Đỏ, Xanh dương, Vàng, Xanh lá
- Ring chung: 48 ô
- Finish Track: 6 nấc / màu

## Luật chính

- Đổ `6` để ra quân.
- Server sinh xúc xắc từ `1..6`.
- Được đi xuyên qua quân khác.
- Không được dừng trên quân cùng màu.
- Dừng trên quân đối phương thì đá quân.
- Đạt chính xác nấc 6 của Finish Track thì quân hoàn thành.
- 4 quân hoàn thành -> người chơi hoàn thành trận.
- Có các ô đặc biệt: Speed, Slow, Lucky, Trap, Shield.

Chi tiết đầy đủ xem [`AGENTS.md`](./AGENTS.md).

## Build

Project sử dụng Maven multi-module.

Trong IntelliJ IDEA có thể chạy:

```text
Maven -> ludo-game -> Lifecycle -> install
```

Kết quả mong đợi:

```text
ludo-game .... SUCCESS
common ....... SUCCESS
server ....... SUCCESS
client ....... SUCCESS

BUILD SUCCESS
```

## Workflow phát triển

Không push feature trực tiếp lên `main`.

Mỗi Jira task dùng một branch riêng:

```text
feat/KAN-10-register
feat/KAN-11-login
feat/KAN-30-create-room
fix/KAN-43-move-piece
```

Commit nên chứa Jira key:

```text
feat(KAN-11): implement login
fix(KAN-43): validate piece movement
```

Quy trình:

```text
Jira Task
  -> Feature Branch
  -> Code + Test
  -> Pull Request
  -> Review
  -> Merge main
  -> Jira Done
```

## Tài liệu dự án

- [`AGENTS.md`](./AGENTS.md): đặc tả kỹ thuật và luật dành cho developer / AI coding agent.
- [`PLAN.md`](./PROJECT_PLAN.md): checklist tiến độ kỹ thuật toàn dự án.
- `README.md`: giới thiệu nhanh repository, kiến trúc và cách làm việc.

## Thành viên

| Thành viên      | Mã sinh viên |
| --------------- | ------------ |
| Hoàng Đình Duy  | B23DCCN237   |
| Trần Trọng Hùng | B23DCCN363   |
| Đặng Phi Long   | B23DCCN497   |
| Tạ Thanh Thiên  | B23DCCN783   |
