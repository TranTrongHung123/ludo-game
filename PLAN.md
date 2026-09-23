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
- [x] Thiết lập GitHub Actions CI cho toàn bộ Maven reactor và MySQL integration test
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
- [x] Bổ sung Maven Wrapper để build không phụ thuộc Maven cài toàn hệ thống
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
- [x] Xây dựng TCP Server cơ bản
- [x] Xây dựng `ClientHandler`
- [x] Xây dựng thread pool cho nhiều Client
- [x] Xây dựng TCP Client cơ bản
- [x] Xây dựng background listener phía Client
- [x] Hoàn thiện PING/PONG smoke test
- [x] Test nhiều Client kết nối đồng thời
- [x] Test malformed JSON / invalid frame
- [x] Test disconnect đột ngột

---

# Phase 4 - Authentication & Session

- [x] Register end-to-end
- [x] Login end-to-end
- [x] Logout end-to-end
- [x] Session creation
- [x] Một account chỉ có một active session
- [x] Session cleanup
- [x] Heartbeat
- [x] Detect disconnect
- [x] Reconnect trong grace period
- [x] Session restore sau reconnect

---

# Phase 5 - Database Foundation

- [x] Tạo database `ludo_game`
- [x] Cấu hình JDBC
- [x] Cấu hình HikariCP
- [x] Cấu hình Flyway
- [x] Migration bảng `users`
- [x] Migration bảng `matches`
- [x] Migration bảng `match_players`
- [x] Hoàn thiện repository layer cơ bản
- [x] Kiểm thử migration từ database rỗng

---

# Phase 6 - Lobby

- [x] Online Player List end-to-end
- [x] Player Presence end-to-end
- [x] Đồng bộ trạng thái `IDLE`
- [x] Đồng bộ trạng thái `IN_ROOM`
- [x] Đồng bộ trạng thái `PLAYING`
- [x] Broadcast Lobby State
- [x] Hiển thị điểm người chơi
- [x] Hiển thị số lần hạng nhất

---

# Phase 7 - Room Management

- [x] Create Room end-to-end
- [x] Join Room end-to-end
- [x] Leave Room end-to-end
- [x] Invite Player end-to-end
- [x] Accept Invite end-to-end
- [x] Reject Invite end-to-end
- [x] Ready / Unready end-to-end
- [x] Start Game end-to-end
- [x] Auto assign color theo slot
- [x] Host transfer
- [x] Room lifecycle
- [x] Room concurrency
- [x] Ngăn vượt quá 4 người / phòng

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

- [x] First Turn
- [x] Turn Rotation
- [x] Roll Dice end-to-end
- [x] Spawn Piece end-to-end
- [x] Move Piece end-to-end
- [x] Capture Piece end-to-end
- [x] No Valid Moves handling
- [x] Finish Track
- [x] Carry-over Steps
- [x] Piece Completion
- [x] Player Completion
- [x] Bonus Roll
- [x] Game Over detection
- [x] Rank assignment

---

# Phase 10 - Special Cells

- [x] Special Cell placement
- [x] Special Cell blacklist
- [x] Speed Cell
- [x] Slow Cell: lùi ngay 2 bước
- [x] Lucky Cell
- [x] Trap Cell: về chuồng ngay
- [x] Bỏ Shield Cell và hiệu ứng kéo dài (2026-09-23)
- [x] Special-effect destination resolution
- [x] Ngăn chain effect
- [x] Unit test toàn bộ Special Cells

---

# Phase 11 - Turn Timeout & Robustness

- [x] `WAITING_FOR_ROLL` timeout
- [x] `WAITING_FOR_MOVE` timeout
- [x] Server-authoritative deadline
- [x] Đồng bộ countdown tới Client
- [x] Bonus phase timeout reset
- [x] Ngăn double-next-turn
- [x] Disconnect trong lượt
- [x] Reconnect trong lượt
- [x] Grace-period expiration
- [x] Forfeit processing

---

# Phase 12 - Ranking & Match Persistence

- [x] Lưu match result
- [x] Lưu match players
- [x] Cập nhật score
- [x] Cập nhật số lần hạng nhất
- [x] Ranking end-to-end
- [x] Match History end-to-end
- [x] Forfeit scoring
- [x] Cascading Game Over
- [x] Kiểm thử score theo phòng 2 người
- [x] Kiểm thử score theo phòng 3 người
- [x] Kiểm thử score theo phòng 4 người

---

# Phase 13 - Unity Client (client chính)

> Các mục hoàn thành dưới đây phản ánh code/scene đã có. Chưa xác nhận toàn bộ
> luồng end-to-end Unity + Java + MySQL. Unity là client duy nhất; backend Maven gồm common và server.

- [x] Unity project, C#, uGUI/Canvas và TextMeshPro
- [x] TCP framing, requestId, heartbeat và session xuyên scene
- [x] Login / Register / Lobby / Room
- [x] Game / Result / Ranking / History
- [x] Render 48 ô, Finish Track, quân, ô đặc biệt từ Server
- [x] Highlight validPieceIds, dice, lượt và countdown authoritative
- [x] Network I/O bất đồng bộ; dispatch event qua Unity main thread
- [x] Chat GameScene qua CHAT_MESSAGE
- [x] Reconnect với room/game snapshot
- [x] Khôi phục GAME_OVER đã bỏ lỡ qua RECONNECT_RESULT.gameOver
- [x] Nhận presence từ ROOM_UPDATED dù gameplay stateVersion không đổi
- [x] Làm mới điểm/số lần hạng nhất trên profile và header Lobby
- [x] Escape tên người gửi chat trước khi đưa vào rich text
- [x] Chơi ván mới trong cùng phòng theo mục 32 AGENTS.md (Server + Unity TCP fixture đã test)
- [ ] Chạy đủ luồng Unity–Java–MySQL và tự động hóa test Unity trên CI

Chi tiết, bằng chứng và giới hạn kiểm tra: [INTEGRATION_REVIEW.md](INTEGRATION_REVIEW.md).

---

# Phase 14 - Extended Features

> Không ưu tiên trước khi core game ổn định.

- [x] Chat trong phòng
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

> Checklist lịch sử của bộ test Java; không đại diện cho test end-to-end Unity.
> Sau sửa lỗi 2026-09-22: Maven verify đạt 117 test, 4 MySQL test skip;
> Unity đạt 23 regression check và 61 Game/Result TCP fixture check.

- [x] Full match 2 players
- [x] Full match 3 players
- [x] Full match 4 players
- [x] Concurrent room join
- [x] Host leave
- [x] Player quit
- [x] Disconnect
- [x] Reconnect
- [x] Timeout
- [x] Grace-period forfeit
- [x] Invalid frame
- [x] Oversized frame
- [x] Malformed JSON
- [x] Partial frame
- [x] Duplicate request
- [x] Wrong-turn request
- [x] Database integrity
- [x] Ranking integrity

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

# Definition of Done

Một task lớn được đánh `[x]` khi phạm vi triển khai local đáp ứng:

- Feature hoạt động end-to-end theo scope.
- Code compile thành công.
- Test liên quan pass.
- Maven build không bị phá.
- Không mâu thuẫn `AGENTS.md`.

---

# Current Focus

Hiện tại ưu tiên:

1. Duy trì Maven `verify` và MySQL integration test xanh trên CI.
2. Kiểm thử các sửa lỗi trong `INTEGRATION_REVIEW.md` bằng nhiều Unity Client với Java/MySQL trước demo.
3. Phase 14 và Phase 16 được thực hiện theo kế hoạch riêng khi nhóm ưu tiên.
