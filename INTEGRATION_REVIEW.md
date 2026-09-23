# Rà soát Java Server ↔ Unity Client — 2026-09-22

## Cập nhật loại bỏ client desktop cũ — 2026-09-23

- Đã xóa module `client/` và tài nguyên riêng `ui/login.png`.
- Maven chỉ build `common` + `server`; CI, `ludo.ps1`, `.env.example` và tài liệu đã cập nhật.
- Bảy luồng kiểm thử tích hợp được viết lại trong `TcpWorkflowIntegrationTest`, dùng Socket
  và framing chung để kiểm tra Server thật với repository trong bộ nhớ; không giữ implementation
  client cũ. Luồng reconnect kiểm tra contract khôi phục phiên, còn tự reconnect Unity được
  kiểm chứng bằng các fixture Unity đã nêu bên dưới.
- `mvnw clean verify`: 106 test, 102 đạt, 4 MySQL test skip, 0 lỗi; exit code 0.
- Các số liệu nhắc tới module đã gỡ ở các đợt cũ bên dưới chỉ là lịch sử kiểm chứng.

Client chính: `client-unity/`, C#, Unity `6000.6.2f1` theo ProjectVersion và Editor
đang kết nối. Backend: `common/` + `server/`, Java 21, TCP, MySQL.
Cập nhật 2026-09-23: Maven chỉ còn common + server; client duy nhất là Unity.
Số liệu của đợt rà soát cũ dưới đây được giữ như lịch sử kiểm chứng.

## Kết luận

Contract của các luồng chính khớp qua đối chiếu source: framing, auth, lobby,
phòng, lời mời, ready/start, roll/move, chat, ranking và history. Unity gửi ý định,
Java giữ quyền quyết định luật và kết quả. **Năm lỗi ghi nhận ban đầu đã được sửa**
và kiểm tra bằng test Java, Unity state regression và Unity Play Mode TCP fixture.
Chưa xác nhận toàn bộ end-to-end với Java/MySQL thật.

## Kết quả sau sửa lỗi

| Vấn đề | Thay đổi |
| --- | --- |
| Bỏ lỡ GAME_OVER | RECONNECT_RESULT có gameOver tùy chọn; Server chụp room/game/result dưới cùng lock, Unity khôi phục và mở Result |
| Presence cùng version | ROOM_UPDATED cập nhật presence riêng; vẫn chặn gameplay snapshot cũ/trùng |
| Điểm Lobby cũ | Response/broadcast online cập nhật profile đúng playerId; header render lại |
| Rich text chat | Escape cả tên người gửi lẫn message |
| Chơi lại cùng phòng | Nút Chơi tiếp gửi READY; lưu kết quả rồi mở WAITING, reset ready/trận, giữ live slots; Start vẫn cần tất cả Ready |

Protocol bổ sung field `RECONNECT_RESULT.data.gameOver`, tương thích client bỏ qua
field lạ; không đổi framing/tên message. READY trong phòng FINISHED mở vòng chờ ván
mới theo mục 32 AGENTS.md. Nếu lưu kết quả lỗi, Server không xóa state trận cũ.

- Maven `verify`: **117 test đạt**, **4 MySQL integration test skip**; common 17,
  server 78 đạt + 4 skip, client JavaFX legacy 22. Không failure/error.
- `RematchIntegrationTest`: 3 test mới, kiểm tra missed-result reconnect và JSON
  round trip, rematch/cấp lại slot/quân/timer, chặn reset khi persistence lỗi,
  retry persistence và callback timeout của match cũ.
- Unity `VerifyIntegrationFixes.Main`: **23 checks đạt** trong Edit Mode.
- Unity `VerifyGame.Main`, args `[false]`: **61 checks đạt** trong Play Mode TCP
  fixture, gồm thực sự không gửi event GAME_OVER trước reconnect và thao tác
  Chơi tiếp qua Button, chống double-click, chuyển Room rồi Game của ván mới.
- Unity compile thành công, Console không error. Script kiểm tra cũ có warning
  API tìm object bị đánh dấu obsolete trên Editor hiện tại.
- Prefab ResultScreen có hai nút đã gán reference; giữ nguyên scene Editor đang mở
  và khôi phục thiết lập Play Mode start scene sau kiểm tra.

Chưa chạy MySQL thật, chưa build player; các giới hạn môi trường ở phần kiểm tra
ban đầu bên dưới vẫn áp dụng. Các bước tái hiện tiếp theo ghi lại **lỗi trước sửa**.

## Các phát hiện ban đầu (đã sửa)

### 1. P1 — Reconnect sau khi bỏ lỡ GAME_OVER không khôi phục bảng kết quả

- [ReconnectResult.java](common/src/main/java/vn/ptit/ltm/common/dto/session/ReconnectResult.java)
  chỉ có `restored`, `presenceState`, `room`, `gameState`.
- [ServerApplication.java](server/src/main/java/vn/ptit/ltm/server/ServerApplication.java)
  tạo reconnect snapshot bằng room/game; handler RECONNECT không gửi lại GAME_OVER.
- [RoomService.java](server/src/main/java/vn/ptit/ltm/server/room/RoomService.java),
  `onSessionsChanged()`, gọi `broadcastGame()` khi presence đổi, chỉ phát GAME_STATE.
- [NetworkSession.cs](client-unity/Assets/Scripts/Services/NetworkSession.cs)
  chỉ giữ GameOver cũ nếu đã nhận trước đó và cùng match.
- [GameController.cs](client-unity/Assets/Scripts/Controllers/GameController.cs)
  chỉ chuyển ResultScene khi `session.GameOver != null`.

Tái hiện: A mất kết nối trong trận hai người; B bỏ cuộc làm trận kết thúc; A nối
lại trong grace period. A nhận game FINISHED nhưng chưa từng nhận GAME_OVER,
nên ở GameScene với thông báo kết thúc, thiếu bảng kết quả và tổng điểm chính thức.
A vẫn có thể về sảnh; đây không phải tình trạng kẹt hoàn toàn.

Hướng sửa: Server phát lại GAME_OVER đã lưu cho connection reconnect khi match
đã kết thúc, theo thứ tự phù hợp sau snapshot. Có thể giữ wire contract hiện tại
bằng event đã có. Cần test thực sự bỏ lỡ event, không chỉ reconnect sau khi đã nhận kết quả.
Phát hiện này dựa trên source; chưa tái hiện với Java/MySQL đang chạy.

### 2. P2 — Snapshot presence cùng version bị Unity bỏ qua

[GameRoom.java](server/src/main/java/vn/ptit/ltm/server/room/GameRoom.java),
`gameSnapshotLocked()`, ghép presence mới từ SessionManager nhưng giữ nguyên
`gameState.stateVersion()`. Khi disconnect/reconnect, Server có thể gửi GAME_STATE
có presence mới và gameplay version không đổi.

[GameSession.cs](client-unity/Assets/Scripts/Services/GameSession.cs),
`ApplyGameState()`, bỏ snapshot nếu version hiện tại `>=` version nhận được.
[PlayerCardView.cs](client-unity/Assets/Scripts/Views/PlayerCardView.cs) lại đọc
presence từ GameState. Người còn online không thấy đối thủ mất mạng/quay lại cho
tới một thay đổi gameplay có version lớn hơn, ví dụ timeout hoặc nước đi mới.

Đã tái hiện bằng class Unity đã compile trong preview scene tạm, không mở Play Mode:

```text
firstSnapshotAccepted = true
presenceUpdateAccepted = false
retainedPresence = PLAYING  (snapshot mới là DISCONNECTED)
```

Hướng sửa: thống nhất version cho presence hoặc cơ chế cập nhật presence riêng.
Không chỉ bỏ toàn bộ bộ lọc version: vẫn cần chống snapshot gameplay cũ.

### 3. P2 — Điểm/số lần hạng nhất ở đầu Lobby không cập nhật sau trận

[NetworkSession.cs](client-unity/Assets/Scripts/Services/NetworkSession.cs) gán
Profile khi LOGIN. [LobbySession.cs](client-unity/Assets/Scripts/Services/LobbySession.cs)
cập nhật OnlinePlayers nhưng không cập nhật Profile của chính người chơi.
[LobbyController.cs](client-unity/Assets/Scripts/Controllers/LobbyController.cs)
chỉ lấy điểm/số lần hạng nhất từ Profile trong `Start()`, không làm mới trong Render.

Đã tái hiện: sau event ONLINE_PLAYERS_UPDATED của chính tài khoản với điểm 3,
`OnlinePlayers` có điểm 3 nhưng `Profile.totalScore` vẫn 0. Sau trận, danh sách
online/ranking có thể đúng còn header Lobby vẫn là số cũ cho tới khi đăng nhập lại.

Hướng sửa: đồng bộ trường profile từ player summary authoritative khớp playerId
ở cả response refresh và broadcast, rồi render lại header. Không tự cộng điểm client.

### 4. P2 — Tên người gửi chat chưa escape rich text

[GameController.cs](client-unity/Assets/Scripts/Controllers/GameController.cs),
`OnChatReceived()`, escape dấu `<` trong message nhưng ghép `senderDisplayName`
trực tiếp vào chuỗi `<color><b>…</b></color>`.
[AuthValidator.java](server/src/main/java/vn/ptit/ltm/server/service/AuthValidator.java)
cho phép tên hiển thị có các ký tự đó.

Ví dụ tên `</b><size=80>X</size><b>` có thể làm thay đổi định dạng dòng chat.
Đây là lỗi hiển thị rich text, không phải thực thi mã. Hướng sửa: escape cả tên và
nội dung người dùng, chỉ giữ các tag do ứng dụng tạo. Phát hiện qua source.

### 5. P2 — Chơi ván mới cùng phòng chưa đạt mục 32 AGENTS.md

Đặc tả cho phép Ready/Start ván mới sau Game Over. Hiện tại
[ResultController.cs](client-unity/Assets/Scripts/Controllers/ResultController.cs)
chỉ có Về sảnh; [GameRoom.java](server/src/main/java/vn/ptit/ltm/server/room/GameRoom.java)
yêu cầu WAITING cho Ready/Start và chưa có đường reset FINISHED → WAITING.

Luồng đang dùng: rời phòng sau kết quả rồi tạo/tham gia phòng mới. Cần triển khai
rematch theo đặc tả hoặc chốt thay đổi phạm vi với nhóm; không tự đổi luật để che
khoảng trống này. Đây là thiếu chức năng hai phía, không phải sai tên payload.

## Đối chiếu contract hiện tại

| Phần | Unity ↔ Java | Kết quả đối chiếu source |
| --- | --- | --- |
| Transport | 4 byte big-endian, UTF-8, 1..65.536 byte | Khớp; đọc đủ frame, writes có khóa |
| Envelope | type/requestId/sessionId/data; success/error | Khớp; Java bỏ field null nên Unity nhận event không có success |
| Register/Login | REGISTER, LOGIN trả cùng type, profile; login thêm sessionId | Khớp; đăng ký không tự login |
| Heartbeat | PING → PONG giữ requestId/data | Khớp với handler Java |
| Lobby | GET_ONLINE_PLAYERS → ONLINE_PLAYERS_UPDATED.players | Khớp; profile đã đồng bộ theo playerId |
| Phòng | CREATE_ROOM/JOIN_ROOM/ACCEPT_INVITE/READY/UNREADY → data.room | Khớp; server cấp slot/màu/host |
| Lời mời | INVITE_PLAYER, invitationId, expiresAtEpochMillis; Accept/Reject | Khớp; Server xác nhận hiệu lực |
| Start | START_GAME → GameState trực tiếp; ROOM_UPDATED và GAME_STATE broadcast | Khớp; host, 2–4 người, mọi người Ready và IN_ROOM |
| Roll | ROLL_DICE(roomId) → DICE_RESULT | Khớp; C# không gửi giá trị xúc xắc |
| Move | MOVE_PIECE(roomId,pieceId) → MOVE_PIECE_RESULT.gameState | Khớp; C# dùng validPieceIds |
| Render | stepCount -1, 0..47, 48..53; màu có start 0/12/24/36 | BoardGeometry khớp quy ước Java; vị trí hiển thị quân FINISHED tách riêng |
| Timeout | deadline epoch millis, phase 8s/12s, TURN_TIMEOUT | Server quyết định; Unity dùng đồng hồ máy để hiển thị/khóa nút |
| Reconnect | RECONNECT(data.sessionId) → RECONNECT_RESULT | Room/game và gameOver tùy chọn; presence qua ROOM_UPDATED |
| Quit/kết quả | LEAVE_ROOM; GAME_OVER.standings | Luồng online khớp; quit có xác nhận và chờ response |
| Chat | CHAT_MESSAGE(roomId,message) → response và broadcast | Đã triển khai hai phía; Server giới hạn 200 ký tự, kiểm membership |
| Ranking | GET_RANKING → RANKING_RESULT.entries | Khớp rank, totalScore, firstPlaceCount; giữ điểm lẻ |
| History | GET_MATCH_HISTORY → MATCH_HISTORY_RESULT.matches | Khớp timestamp, rank, playerCount, color, scoreEarned, forfeited |

Timer Unity hiện so deadline Server với `DateTimeOffset.UtcNow`; chưa có clock
offset synchronization. Máy client lệch giờ có thể khóa nút quá sớm/quá muộn,
nhưng Server vẫn kiểm tra deadline chính thức. Cần đưa clock skew vào test thực tế.

## Kiểm tra ban đầu trước khi sửa

| Kiểm tra | Kết quả |
| --- | --- |
| `.\mvnw.cmd -pl server -am test --batch-mode --no-transfer-progress` | BUILD SUCCESS; common 17 đạt, server 75 đạt + 4 skip; không failure/error |
| MySQL integration | 4 test skip: AuthSession, DatabaseMigration, Lobby, MatchPersistence |
| Unity Editor status | 6000.6.2f1; compilationFailed=false, không compile error |
| `AgentScripts/VerifyLogin.cs`, entry `VerifyLogin.Main` qua MCP run_script | 18 checks đạt: framing, Unicode, partial/coalesced frame, invalid length/UTF-8/EOF, heartbeat, correlation, disconnect |
| Kiểm tra state Unity trong preview scene tạm | Tái hiện mục 2 và 3; preview scene được đóng sau kiểm tra |
| Môi trường thật | Docker Desktop Linux engine chưa chạy; không có listener local 3306/5555 lúc kiểm tra |

Không chạy lại toàn bộ VerifyGame/Room/Lobby/History/Ranking UI, không build player,
không chơi end-to-end với Java/MySQL thật. Các test fixture C# không thay thế được
test contract với Java thật. Số lượng test lịch sử trong README Unity không phải
kết quả chạy lại toàn bộ suite ngày hôm nay.

CI chạy Maven common + server, có MySQL service; chưa chạy Unity.
Một số assertion trong VerifyLoginUi/VerifyLobby/VerifyRoom còn dựa trên giai đoạn
scene chưa tồn tại, như README Unity đã ghi; cần cập nhật trước khi dùng làm gate.

## Checklist xác nhận tích hợp tiếp theo

- [ ] Chạy MySQL + Java Server + 2 Unity players: register/login, invite, ready/start.
- [ ] Chơi đủ ván 2/3/4 người: roll/move, hiệu ứng, timeout, kết quả, ranking/history.
- [ ] Disconnect/reconnect trước và sau timeout; kiểm presence trên client khác.
- [ ] Ngắt A, B kết thúc trận khi A offline, reconnect A: phải hiện Result chính thức.
- [ ] Về Lobby sau thắng: header, online list và ranking có cùng điểm/số lần hạng nhất.
- [ ] Chat Unicode, tên chứa markup, quá 200 ký tự, gửi từ người ngoài phòng.
- [ ] Grace period hết hạn, host quit, response đến trễ, nhiều client thao tác cùng phòng.
- [ ] Đồng hồ client lệch giờ; đóng/mở scene lúc request pending; chạy nhiều player build.
- [x] Kiểm tra lại các sửa lỗi mục 1–5 bằng test Java và Unity regression/TCP fixture.

## Tài liệu đã cập nhật

`AGENTS.md`, `README.md`, `RUN.md`, `PLAN.md`, `DESIGN.md` và
`client-unity/README.md` thống nhất Unity là client duy nhất.
Maven không build Unity; hướng dẫn chạy chính
dùng Unity Hub/Editor/player. Rematch hiện được triển khai theo mục 32 AGENTS.md;
DESIGN.md cập nhật hai nút Chơi tiếp/Về sảnh. Các kết quả trước sửa được giữ làm
lịch sử chẩn đoán, tách khỏi kết quả kiểm tra mới ở đầu báo cáo.
# Cập nhật ô đặc biệt — 2026-09-23

- Chỉ còn SPEED (+2 bước), SLOW (-2 bước ngay), LUCKY (+1 lần tung), TRAP (về chuồng).
- Layout 16 ô; bỏ Khiên và trạng thái kéo dài. JSON bỏ `slowed`, `shielded`, `shieldConsumed`.
- Maven reactor: common, server và client legacy không có test lỗi; 4 test tích hợp MySQL
  được bỏ qua theo cấu hình. Các test engine/JSON đã chạy lại sau bổ sung kiểm tra contract
  và +2 vào Finish Track, exit code 0.
- Unity compile thành công, 39 kiểm tra ô đặc biệt và 23 kiểm tra tích hợp Edit Mode đạt;
  Console không lỗi/cảnh báo. Đã lưu chú giải mới trong GameScreen prefab.
- Chưa chạy end-to-end Unity + Java/MySQL hoặc build desktop player cho đợt này.

Các phần phía trên ghi lại đợt kiểm chứng trước đó.

## Trình diễn ô đặc biệt và về đích — 2026-09-23

- Thêm `GameState.lastMove` tùy chọn, chứa các chặng đã được Server xác nhận;
  snapshot của người khác trong phòng cũng nhận cùng metadata. Các phase mới xóa metadata.
- Unity đi tới ô kích hoạt rồi tiến/lùi; có nhịp nhấn và thông báo tự tắt 2,6 giây.
  Quân hoàn thành chạm tâm, chuyển lên khay theo màu và mang vương miện.
- `VerifyMovePresentation.Main`: 66 kiểm tra Play Mode đạt, gồm lùi về vị trí cũ,
  effect bị chặn, trap, lucky, về đích, refresh trùng, reconnect, hụt version,
  không chặn input và tự tắt ngay cả khi `timeScale=0`.
- `VerifyGame.Main(false)`: 61 kiểm tra TCP localhost đạt. 39 kiểm tra SpecialCells
  và 23 kiểm tra IntegrationFixes Edit Mode cũng đạt.
- [Ảnh giao diện bằng fixture](client-unity/Documentation/move-presentation-preview.png).
  Chưa chạy end-to-end Unity với Java/MySQL thật hoặc build player cho đợt này.
