# PLAN.md

> Roadmap cho dự án **Ludo Game - BTL Lập trình mạng**.  


## Quy ước trạng thái

- `[ ]` Chưa làm
- `[-]` Đang làm
- `[x]` Hoàn thành
- `[!]` Bị chặn

---

# Phase 1 - Project Foundation

- [x] Tạo Maven multi-module project
- [x] Tạo module `common`
- [x] Tạo module `server`
- [x] Tạo module `client`
- [x] Cấu hình dependency giữa các module
- [x] Maven reactor build thành công
- [x] Tạo package structure ban đầu
- [x] Tạo Git repository
- [x] Push project lên GitHub
- [x] Xóa `.idea` khỏi Git tracking
- [x] Cấu hình `.gitignore`
- [x] Tạo `AGENTS.md`
- [x] Tạo `README.md`
- [x] Cấu hình toàn bộ dependency Maven
- [x] Maven `install` lại sau khi thêm dependency
- [x] Invite đầy đủ thành viên vào GitHub repository
- [x] Thiết lập branch / Pull Request workflow
- [x] Liên kết Jira task với GitHub branch/commit/PR

---

# Phase 2 - Common Protocol Foundation

- [x] Thiết kế `MessageType`
- [x] Thiết kế `MessageEnvelope`
- [x] Thiết kế `ErrorPayload`
- [x] Chuẩn hóa `requestId`
- [x] Chuẩn hóa `sessionId`
- [x] Chuẩn hóa response success/error
- [x] Chuẩn hóa DTO theo feature
- [x] Chuẩn hóa JSON serialization/deserialization
- [x] Viết test cho protocol common

---

# Phase 3 - TCP Networking Foundation

- [x] Xây dựng TCP framing protocol
- [x] Xây dựng `FrameReader`
- [x] Xây dựng `FrameWriter`
- [x] Áp dụng giới hạn frame 64 KB
- [ ] Xây dựng TCP Server cơ bản
- [ ] Xây dựng `ClientHandler`
- [ ] Xây dựng thread pool cho nhiều Client
- [ ] Xây dựng TCP Client cơ bản
- [ ] Xây dựng background listener phía Client
- [ ] Hoàn thiện PING/PONG smoke test
- [ ] Test nhiều Client kết nối đồng thời
- [x] Test malformed JSON / invalid frame
- [ ] Test disconnect đột ngột

---

# Phase 4 - Authentication & Session

- [ ] Register end-to-end
- [ ] Login end-to-end
- [ ] Logout end-to-end
- [ ] Session creation
- [ ] Một account chỉ có một active session
- [ ] Session cleanup
- [ ] Heartbeat
- [ ] Detect disconnect
- [ ] Reconnect trong grace period
- [ ] Session restore sau reconnect

---

# Phase 5 - Database Foundation

- [ ] Tạo database `ludo_game`
- [ ] Cấu hình JDBC
- [ ] Cấu hình HikariCP
- [ ] Cấu hình Flyway
- [ ] Migration bảng `users`
- [ ] Migration bảng `matches`
- [ ] Migration bảng `match_players`
- [ ] Hoàn thiện repository layer cơ bản
- [ ] Kiểm thử migration từ database rỗng

---

# Phase 6 - Lobby

- [ ] Online Player List end-to-end
- [ ] Player Presence end-to-end
- [ ] Đồng bộ trạng thái `IDLE`
- [ ] Đồng bộ trạng thái `IN_ROOM`
- [ ] Đồng bộ trạng thái `PLAYING`
- [ ] Broadcast Lobby State
- [ ] Hiển thị điểm người chơi
- [ ] Hiển thị số lần hạng nhất

---

# Phase 7 - Room Management

- [ ] Create Room end-to-end
- [ ] Join Room end-to-end
- [ ] Leave Room end-to-end
- [ ] Invite Player end-to-end
- [ ] Accept Invite end-to-end
- [ ] Reject Invite end-to-end
- [ ] Ready / Unready end-to-end
- [ ] Start Game end-to-end
- [ ] Auto assign color theo slot
- [ ] Host transfer
- [ ] Room lifecycle
- [ ] Room concurrency
- [ ] Ngăn vượt quá 4 người / phòng

---

# Phase 8 - Game Domain Model

- [x] Chuẩn hóa `PieceColor`
- [x] Chuẩn hóa `PieceState`
- [x] Chuẩn hóa `PlayerPresenceState`
- [x] Chuẩn hóa `MatchParticipantStatus`
- [x] Chuẩn hóa `TurnState`
- [x] Chuẩn hóa `Piece`
- [x] Chuẩn hóa `GameState`
- [x] Chuẩn hóa board coordinate model
- [x] Unit test Local Step / Global Ring
- [x] Unit test Finish Track coordinate

---

# Phase 9 - Core Gameplay

- [ ] First Turn
- [ ] Turn Rotation
- [ ] Roll Dice end-to-end
- [ ] Spawn Piece end-to-end
- [ ] Move Piece end-to-end
- [ ] Capture Piece end-to-end
- [ ] No Valid Moves handling
- [ ] Finish Track
- [ ] Carry-over Steps
- [ ] Piece Completion
- [ ] Player Completion
- [ ] Bonus Roll
- [ ] Game Over detection
- [ ] Rank assignment

---

# Phase 10 - Special Cells

- [ ] Special Cell placement
- [x] Special Cell blacklist
- [ ] Speed Cell
- [ ] Slow Cell
- [ ] Lucky Cell
- [ ] Trap Cell
- [ ] Shield Cell
- [ ] Special-effect destination resolution
- [ ] Ngăn chain effect
- [ ] Unit test toàn bộ Special Cells

---

# Phase 11 - Turn Timeout & Robustness

- [ ] `WAITING_FOR_ROLL` timeout
- [ ] `WAITING_FOR_MOVE` timeout
- [ ] Server-authoritative deadline
- [ ] Đồng bộ countdown tới Client
- [ ] Bonus phase timeout reset
- [ ] Ngăn double-next-turn
- [ ] Disconnect trong lượt
- [ ] Reconnect trong lượt
- [ ] Grace-period expiration
- [ ] Forfeit processing

---

# Phase 12 - Ranking & Match Persistence

- [ ] Lưu match result
- [ ] Lưu match players
- [ ] Cập nhật score
- [ ] Cập nhật số lần hạng nhất
- [ ] Ranking end-to-end
- [ ] Match History end-to-end
- [ ] Forfeit scoring
- [ ] Cascading Game Over
- [ ] Kiểm thử score theo phòng 2 người
- [ ] Kiểm thử score theo phòng 3 người
- [ ] Kiểm thử score theo phòng 4 người

---

# Phase 13 - JavaFX Client

- [ ] JavaFX bootstrap
- [ ] FXML loading
- [ ] CSS foundation
- [ ] Login Screen
- [ ] Register Screen
- [ ] Lobby Screen
- [ ] Room Screen
- [ ] Game Board Screen
- [ ] Ranking Screen
- [ ] Match History Screen
- [ ] Game Result Screen
- [ ] Render 48 ring cells
- [ ] Render Finish Track
- [ ] Render Pieces
- [ ] Highlight valid moves
- [ ] Render Dice
- [ ] Render Turn Indicator
- [ ] Render Countdown
- [ ] Render Special Cells
- [ ] Network thread không block JavaFX Application Thread

---

# Phase 14 - Extended Features

> Không ưu tiên trước khi core game ổn định.

- [ ] Chat trong phòng
- [ ] Spectator mode
- [ ] Animation
- [ ] Sound
- [ ] Avatar
- [ ] Room password
- [ ] Friend system
- [ ] Replay
- [ ] Matchmaking
- [ ] Bot player

---

# Phase 15 - Integration Testing

- [ ] Full match 2 players
- [ ] Full match 3 players
- [ ] Full match 4 players
- [ ] Concurrent room join
- [ ] Host leave
- [ ] Player quit
- [ ] Disconnect
- [ ] Reconnect
- [ ] Timeout
- [ ] Grace-period forfeit
- [ ] Invalid frame
- [ ] Oversized frame
- [ ] Malformed JSON
- [ ] Partial frame
- [ ] Duplicate request
- [ ] Wrong-turn request
- [ ] Database integrity
- [ ] Ranking integrity

---

# Phase 16 - Final Report & Demo

- [ ] Architecture Diagram
- [ ] Use Case Diagram
- [ ] ERD
- [ ] Class Diagram
- [ ] Sequence Diagram - Login
- [ ] Sequence Diagram - Room
- [ ] Sequence Diagram - Roll / Move
- [ ] Sequence Diagram - Reconnect
- [ ] Deployment Diagram
- [ ] UI screenshots
- [ ] Test cases
- [ ] Test results
- [ ] Installation guide
- [ ] Server run guide
- [ ] Client run guide
- [ ] Slide presentation
- [ ] Demo script
- [ ] Final review

---

# Jira / GitHub Mapping

`PLAN.md` chỉ theo dõi task lớn.

Mỗi task lớn nên có một Jira issue / story tương ứng hoặc được chia thành nhiều Jira issue độc lập.

Ví dụ:

```text
PLAN.md
[ ] Login end-to-end

        ↓

Jira
KAN-11 Login end-to-end

        ↓

GitHub Branch
feat/KAN-11-login

        ↓

Pull Request
KAN-11 Login end-to-end
```

Quy ước branch:

```text
feat/KAN-xx-feature-name
fix/KAN-xx-bug-name
test/KAN-xx-test-name
docs/KAN-xx-doc-name
```

Quy ước commit:

```text
feat(KAN-xx): ...
fix(KAN-xx): ...
test(KAN-xx): ...
docs(KAN-xx): ...
```

---

# Definition of Done

Một task lớn chỉ được đánh `[x]` khi:

- Feature hoạt động end-to-end theo scope.
- Code compile thành công.
- Test liên quan pass.
- Maven build không bị phá.
- Không mâu thuẫn `AGENTS.md`.
- Pull Request đã được merge vào `main`.
- Jira issue tương ứng đã chuyển sang `Done`.

---

# Current Focus

Hiện tại ưu tiên:

1. Hoàn thiện Maven dependencies.
2. Hoàn thiện Common Protocol Foundation.
3. Hoàn thiện TCP Networking Foundation.
4. Sau đó mở các vertical slice task để 4 thành viên làm song song.
