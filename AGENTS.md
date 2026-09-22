# AGENTS.md

## 0. Mục đích tài liệu

Tài liệu này là **nguồn đặc tả trung tâm dành cho AI coding agent và thành viên phát triển dự án**.

Mọi agent khi sửa, sinh hoặc review code cho dự án phải đọc tài liệu này trước.  
Nếu code hiện tại mâu thuẫn với tài liệu này, ưu tiên:

1. Quy tắc đã được ghi rõ trong `AGENTS.md`.
2. Các quyết định mới nhất được nhóm xác nhận trong issue / commit / tài liệu dự án.
3. Code hiện có chỉ được xem là hiện thực hóa, không phải nguồn luật cuối cùng nếu nó trái đặc tả.

Không tự ý thay đổi luật game, protocol mạng, cấu trúc dữ liệu quan trọng hoặc cách đồng bộ trạng thái nếu chưa có yêu cầu rõ ràng.

---

# 1. Thông tin dự án

- **Môn học:** Lập trình mạng
- **Đề tài:** Xây dựng game Cờ Cá Ngựa
- **Lớp:** D23CTPM01
- **Nhóm lớp:** CT01
- **Nhóm BTL:** Nhóm 1
- **Năm:** 2026

| STT | Thành viên      | Mã sinh viên |
| --- | --------------- | ------------ |
| 1   | Hoàng Đình Duy  | B23DCCN237   |
| 2   | Trần Trọng Hùng | B23DCCN363   |
| 3   | Đặng Phi Long   | B23DCCN497   |
| 4   | Tạ Thanh Thiên  | B23DCCN783   |

---

# 2. Mục tiêu hệ thống

Xây dựng game Cờ Cá Ngựa theo mô hình **Client–Server**, hỗ trợ từ **2 đến 4 người chơi** trong một phòng.

Server là **trọng tài trung tâm** và là nguồn dữ liệu đúng duy nhất của trận đấu.

Client chỉ:

- hiển thị giao diện;
- gửi yêu cầu;
- nhận dữ liệu từ Server;
- render lại trạng thái;
- không tự quyết định kết quả xúc xắc;
- không tự xác nhận nước đi;
- không tự thay đổi trạng thái game như nguồn sự thật.

Server chịu trách nhiệm:

- quản lý tài khoản;
- quản lý phiên đăng nhập;
- quản lý người chơi online;
- quản lý phòng;
- quản lý lời mời;
- quản lý trận đấu;
- sinh xúc xắc;
- kiểm tra nước đi;
- áp dụng luật game;
- xử lý ô đặc biệt;
- xử lý timeout;
- xử lý mất kết nối;
- đồng bộ Game State;
- xếp hạng;
- lưu dữ liệu lâu dài;
- broadcast sự kiện cho các Client.

---

# 3. Công nghệ sử dụng

## 3.1. Công nghệ chính

- **Java 21**
- **Maven**
- **Unity 6 + C#** (client chính trong `client-unity/`)
- **uGUI / Canvas + TextMeshPro**
- **Newtonsoft.Json** (Unity); **Jackson** (Java)
- **TCP Socket**
- **JSON**
- **Jackson**
- **MySQL 8**
- **JDBC**
- **HikariCP**
- **Flyway**
- **SLF4J**
- **Logback**
- **BCrypt**
- **JUnit 5**
- **Mockito**
- **Git**
- **GitHub**

## 3.2. Công nghệ không ưu tiên ở phiên bản đầu

Không dùng Spring Boot cho Game Server ở phiên bản đầu nếu không có yêu cầu mới.

Lý do:

- môn học tập trung vào lập trình mạng;
- cần thể hiện rõ `Socket`, `ServerSocket`, TCP, multithreading, protocol, I/O;
- tránh framework che mất phần lõi của môn học.

Không ưu tiên:

- Netty;
- WebSocket;
- UDP cho gameplay chính;
- Spring Data JPA;
- microservice.

---

# 4. Kiến trúc tổng thể

```text
Unity Client 1 ─┐
Unity Client 2 ─┼── TCP + Length-Prefixed UTF-8 JSON
Unity Client N ─┘              │
                              ▼
┌─────────────────────────────┐
│         Game Server         │
│                             │
│ Network                     │
│ SessionManager              │
│ OnlinePlayerManager         │
│ RoomManager                 │
│ GameEngine                  │
│ GameRule                    │
│ TimeoutManager              │
│ AuthService                 │
│ MatchService                │
│ RankingService              │
└──────────────┬──────────────┘
               │
               │ JDBC + HikariCP
               ▼
       ┌───────────────┐
       │    MySQL 8    │
       └───────────────┘
```

---

# 5. Cấu trúc dự án Java Server và Unity Client

```text
ludo-game/
├── AGENTS.md                 # Luật và đặc tả trung tâm
├── DESIGN.md                 # Thiết kế giao diện Unity
├── pom.xml                   # Maven: common, server, client legacy
├── common/src/main/java/     # DTO, enum, framing, JSON contract Java
├── server/src/main/java/     # TCP, session, room, game, service, repository
├── client-unity/             # Client chính, build bằng Unity Editor
│   ├── Assets/
│   │   ├── Scenes/
│   │   ├── Prefabs/
│   │   └── Scripts/
│   │       ├── Network/      # FrameCodec, NetworkClient
│   │       ├── Services/     # NetworkSession và các partial class
│   │       ├── Controllers/
│   │       └── Views/
│   ├── Packages/
│   ├── ProjectSettings/
│   └── AgentScripts/         # Script kiểm tra/authoring ngoài bản build
└── client/                  # JavaFX cũ, giữ để tham khảo và test legacy
```

Unity là client chính. Phiên bản Editor lấy từ
`client-unity/ProjectSettings/ProjectVersion.txt` (hiện tại `6000.6.2f1`).
Maven không build Unity; module `client` còn trong reactor là JavaFX legacy.
Không xóa module cũ hoặc đổi protocol chỉ để cập nhật tài liệu.

## 5.1. Vai trò module `common`

`common` định nghĩa DTO, enum, message, error code và framing phía Java.
Unity không import JAR của `common`: C# gửi/đọc cùng JSON contract qua
`Newtonsoft.Json.Linq.JObject` / `JArray` trong `NetworkSession` hiện tại.
Mọi thay đổi tên field, kiểu dữ liệu, enum hoặc cấu trúc payload phải kiểm tra cả hai phía.

Không đặt logic Server-only hoặc UI Unity vào `common`.
`MessageEnvelope + JsonNode data` là biểu diễn Java của envelope trên wire;
Server map payload sang DTO trước khi gọi nghiệp vụ.

---

# 6. Nguyên tắc thiết kế quan trọng

## 6.1. Server Authoritative

Server là nguồn sự thật duy nhất.

Không được làm kiểu:

```java
piece.position += dice;
sendNewPositionToServer();
```

Client chỉ được gửi ý định:

```text
MOVE_PIECE(pieceId)
```

Server:

1. kiểm tra đúng lượt;
2. kiểm tra trạng thái game;
3. kiểm tra nước đi;
4. tính vị trí mới;
5. xử lý đá quân;
6. xử lý ô đặc biệt;
7. cập nhật Game State;
8. broadcast kết quả.

## 6.2. Client chỉ render trạng thái

Client không được:

- tự sinh xúc xắc;
- tự xếp hạng;
- tự quyết định thắng thua;
- tự xử lý timeout là nguồn sự thật;
- tự sửa Game State chính thức.

## 6.3. Không lưu Game State realtime vào MySQL sau mỗi bước đi

Game đang chạy được giữ trong RAM Server.

MySQL dùng cho:

- tài khoản;
- thông tin người chơi;
- thống kê;
- lịch sử trận;
- bảng xếp hạng;
- dữ liệu lâu dài.

---

# 7. Trạng thái hệ thống

Để tránh mâu thuẫn giữa trạng thái kết nối và trạng thái thi đấu, **không dùng một enum duy nhất cho cả hai khái niệm**.

Một người chơi đang mất kết nối vẫn có thể còn là một participant hợp lệ của trận trong reconnect grace period. Vì vậy trạng thái được tách thành hai lớp độc lập.

## 7.1. `PlayerPresenceState`

Mô tả người chơi đang ở đâu / tình trạng kết nối ở mức ứng dụng:

```java
OFFLINE
IDLE
IN_ROOM
PLAYING
SPECTATING
DISCONNECTED
```

Ý nghĩa:

- `OFFLINE`: chưa đăng nhập hoặc đã đăng xuất.
- `IDLE`: đang ở sảnh, chưa vào phòng.
- `IN_ROOM`: đang ở phòng chờ.
- `PLAYING`: đang tham gia một trận và đang kết nối.
- `SPECTATING`: đang xem trận.
- `DISCONNECTED`: tạm mất kết nối; chưa đồng nghĩa với bị loại khỏi trận.

## 7.2. `MatchParticipantStatus`

Mô tả vòng đời của người chơi **trong một trận cụ thể**:

```java
ACTIVE
COMPLETED
FORFEITED
```

Ý nghĩa:

- `ACTIVE`: vẫn còn thuộc gameplay và vẫn có thể đến lượt.
- `COMPLETED`: đã hoàn thành đủ 4 quân và đã được gán thứ hạng.
- `FORFEITED`: đã bỏ cuộc hoặc bị loại do reconnect grace period hết hạn.

Hai trạng thái này là độc lập.

Ví dụ người chơi mất mạng giữa trận:

```text
presenceState = DISCONNECTED
matchStatus   = ACTIVE
```

Trong 60 giây reconnect grace period, họ **vẫn là participant ACTIVE** và vẫn nằm trong vòng quay lượt.

Nếu quá grace period:

```text
presenceState = DISCONNECTED
matchStatus   = FORFEITED
```

Nếu reconnect thành công khi chưa bị forfeit:

```text
presenceState = PLAYING
matchStatus   = ACTIVE
```

Không tạo `PlayerPresenceState.FINISHED`. Việc hoàn thành trận được biểu diễn bằng:

```text
MatchParticipantStatus.COMPLETED
```

## 7.3. `RoomState`

```java
WAITING
PLAYING
FINISHED
```

## 7.4. `TurnState`

```java
WAITING_FOR_ROLL
WAITING_FOR_MOVE
PROCESSING
FINISHED
```

## 7.5. `PieceState`

```java
IN_YARD
ON_TRACK
IN_FINISH_TRACK
FINISHED
```

Trạng thái hiệu ứng tách khỏi `PieceState`:

```java
boolean slowed;
boolean shielded;
```

---

# 8. Tài khoản và xác thực

## 8.1. Đăng ký

Người dùng đăng ký bằng các trường tối thiểu:

- username;
- password;
- displayName.

Server phải:

1. validate dữ liệu;
2. kiểm tra username duy nhất;
3. hash password bằng BCrypt;
4. lưu `password_hash`, không lưu password dạng plain text.

## 8.2. Đăng nhập

Người chơi đăng nhập từ Client.

Server:

1. kiểm tra tài khoản tồn tại;
2. kiểm tra mật khẩu;
3. kiểm tra tài khoản có session đang hoạt động hay không;
4. tạo session;
5. chuyển `PlayerPresenceState` sang `IDLE`;
6. đưa người chơi vào sảnh.

## 8.3. Một tài khoản chỉ có một active session

Nếu tài khoản đã đăng nhập:

```text
LOGIN
→ ACCOUNT_ALREADY_LOGGED_IN
```

Ngoại lệ:

- reconnect hợp lệ bằng session/token đã được Server nhận diện.

## 8.4. Đăng xuất

Khi đăng xuất hợp lệ:

- đóng session;
- cập nhật trạng thái;
- xóa khỏi danh sách online;
- đóng kết nối nếu protocol của hệ thống yêu cầu.

---

# 9. Sảnh chờ

Sảnh hiển thị danh sách người chơi đang online.

Thông tin tối thiểu:

- tên người chơi;
- tổng điểm;
- số lần đạt hạng nhất;
- trạng thái hiện tại.

Các trạng thái hiển thị chính:

- Đang rỗi;
- Đang trong phòng chờ;
- Đang thi đấu.

Có thể mở rộng:

- Mất kết nối;
- Đang xem trận.

---

# 10. Quản lý phòng

## 10.1. Số người chơi

Một phòng hỗ trợ:

- tối thiểu: 2 người;
- tối đa: 4 người.

## 10.2. Màu quân

Bốn màu:

1. Đỏ;
2. Xanh dương;
3. Vàng;
4. Xanh lá.

Server tự cấp màu theo slot / thứ tự tham gia.

Khuyến nghị:

```text
Slot 0 → Đỏ
Slot 1 → Xanh dương
Slot 2 → Vàng
Slot 3 → Xanh lá
```

Client không tự quyết định màu.

Nếu một người rời trước khi trận bắt đầu:

- slot được giải phóng;
- người vào sau có thể nhận slot đó.

## 10.3. Chủ phòng

Người tạo phòng là chủ phòng.

Quyền chính:

- mời người chơi;
- bắt đầu trận khi đủ điều kiện;
- có thể quản lý một số hành động phòng chờ nếu nhóm triển khai thêm.

### Chủ phòng rời phòng

Nếu chủ phòng rời:

- còn người → quyền chủ phòng chuyển cho người tham gia sớm nhất còn lại;
- không còn người → Server xóa phòng.

Chủ phòng không phải Game Server.

Chủ phòng rời giữa trận không được làm trận đấu sập.

## 10.4. Tham gia phòng

Người đang `IDLE` có thể:

- tạo phòng;
- tham gia phòng.

Khi vào phòng:

```text
IDLE → IN_ROOM
```

## 10.5. Mời người chơi

Chủ phòng có thể mời người đang `IDLE`.

Quy tắc canonical của lời mời:

- lời mời có hiệu lực đúng **60 giây** theo đồng hồ Server;
- mỗi người nhận chỉ có tối đa một lời mời đang chờ; lời mời mới thay thế lời mời cũ;
- chỉ đúng người nhận mới được Accept/Reject;
- Accept hoặc Reject tiêu thụ lời mời và không thể dùng lại;
- khi Accept, Server phải kiểm tra lại người nhận vẫn `IDLE`, phòng còn `WAITING` và còn chỗ;
- lời mời của phòng bị hủy khi thành viên phòng thay đổi hoặc trận bắt đầu.

Người nhận lời mời có thể:

- `ACCEPT`;
- `REJECT`.

Người đã ở một phòng không được nhận lời mời phòng khác.

## 10.6. Điều kiện bắt đầu

Chủ phòng được bắt đầu khi:

- có ít nhất 2 người;
- tất cả thành viên hiện tại, **bao gồm chủ phòng**, đã Ready;
- tất cả thành viên đang kết nối và có `presenceState = IN_ROOM`;
- phòng đang ở `RoomState.WAITING`.

Khi bắt đầu:

```text
IN_ROOM → PLAYING
RoomState = PLAYING
```

Server đồng thời:

- vô hiệu hóa các lời mời còn lại của phòng;
- khởi tạo đầy đủ Game State và 4 quân `IN_YARD` cho mỗi participant;
- chọn occupied `slotIndex` nhỏ nhất làm người đi đầu;
- bắt đầu phase `WAITING_FOR_ROLL` với deadline authoritative;
- broadcast `ROOM_UPDATED` và `GAME_STATE` cho mọi thành viên.

---

# 11. Đồng thời và an toàn dữ liệu phòng

Server xử lý nhiều Client đồng thời bằng thread pool.

Khuyến nghị:

```java
ExecutorService
```

Mỗi `GameRoom` phải có lock riêng.

Có thể dùng:

```java
synchronized (room) {
    // validate + update
}
```

hoặc:

```java
ReentrantLock
```

Không dùng một global lock cho toàn Server.

Mục tiêu:

- Room A và Room B vẫn xử lý song song;
- trong cùng một Room, các thay đổi Game State không race condition.

Ví dụ concurrent request:

```text
A → MOVE
B → LEAVE
C → CHAT
```

các thao tác làm thay đổi trạng thái phòng / game phải được serialize hợp lý.

---

# 12. Protocol TCP

## 12.1. Transport

Sử dụng:

- TCP;
- kết nối lâu dài;
- UTF-8;
- JSON.

## 12.2. Framing

TCP là byte stream.

Không được giả định:

```text
1 write = 1 read
```

Dùng **length-prefixed message**:

```text
┌───────────────┬─────────────────────┐
│ 4-byte length │ JSON UTF-8 payload  │
└───────────────┴─────────────────────┘
```

### Giới hạn kích thước frame bắt buộc

Không được cấp phát mảng trực tiếp từ `length` do Client gửi mà chưa validate.

Hằng số chuẩn:

```java
public static final int MAX_FRAME_LENGTH = 64 * 1024; // 65,536 bytes
```

Gửi:

```java
byte[] data = json.getBytes(StandardCharsets.UTF_8);

if (data.length <= 0 || data.length > MAX_FRAME_LENGTH) {
    throw new ProtocolException(
            "Frame too large: " + data.length
    );
}

out.writeInt(data.length);
out.write(data);
out.flush();
```

Đọc:

```java
int length = in.readInt();

if (length <= 0 || length > MAX_FRAME_LENGTH) {
    throw new ProtocolException(
            "Invalid frame length: " + length
    );
}

byte[] data = new byte[length];
in.readFully(data);

String json =
        new String(data, StandardCharsets.UTF_8);
```

Nếu `length` không hợp lệ:

- không cấp phát `byte[]`;
- coi connection đó vi phạm protocol;
- ghi log;
- đóng connection của Client đó;
- không để lỗi của một Client làm sập toàn Server.

`EOFException`, malformed JSON hoặc Client ngắt giữa frame phải được xử lý ở phạm vi connection tương ứng.

Mục tiêu của `MAX_FRAME_LENGTH`:

- tránh `OutOfMemoryError`;
- tránh request bất thường làm cạn heap;
- tạo giới hạn rõ cho protocol;
- giúp test network dễ hơn.

Nếu sau này có chức năng thực sự cần payload lớn hơn 64 KB, phải thay đổi giới hạn một cách có chủ đích và cập nhật tài liệu này.

## 12.3. Cấu trúc message

Khuyến nghị chung:

```json
{
  "type": "MOVE_PIECE",
  "requestId": "uuid",
  "sessionId": "session-id",
  "data": {}
}
```

Response:

```json
{
  "type": "MOVE_PIECE_RESULT",
  "requestId": "uuid",
  "success": true,
  "data": {}
}
```

Error:

```json
{
  "type": "ERROR",
  "requestId": "uuid",
  "success": false,
  "error": {
    "code": "NOT_YOUR_TURN",
    "message": "Chưa đến lượt của bạn"
  }
}
```

### 12.3.1. Envelope và ánh xạ DTO trong module `common`

**Bản chính của dự án dùng một envelope JSON chung với `JsonNode data`.**

Lý do:

- Server cần đọc `type` trước rồi mới biết payload cụ thể;
- dùng trực tiếp `Message<T>` khi deserialize có thể gặp type erasure;
- `JsonNode` giúp dispatch theo `MessageType` đơn giản và rõ ràng;
- payload nghiệp vụ vẫn dùng DTO typed, không truyền `JsonNode` sâu vào Game Engine.

Khuyến nghị:

```java
public record MessageEnvelope(
        MessageType type,
        String requestId,
        String sessionId,
        Boolean success,
        JsonNode data,
        ErrorPayload error
) {}
```

Quy ước field:

- `type`: bắt buộc;
- `requestId`: bắt buộc với Request/Response, có thể `null` với Event thuần;
- `sessionId`: có thể `null` với `REGISTER` / `LOGIN`, bắt buộc với request cần xác thực;
- `success`: `null` với Request/Event, `true` hoặc `false` với Response;
- `data`: payload;
- `error`: chỉ có khi request thất bại.

Mỗi `MessageType` phải có payload DTO cụ thể.

Ví dụ:

```java
public record MovePieceRequest(
        String roomId,
        String pieceId
) {}
```

Sau khi parse envelope:

```java
MovePieceRequest payload =
        objectMapper.treeToValue(
                message.data(),
                MovePieceRequest.class
        );
```

Khi tạo message outbound:

```java
JsonNode data =
        objectMapper.valueToTree(payload);
```

Không truyền `JsonNode` vào Game Engine. Network layer phải map sang DTO/domain object trước.

Có thể dùng generic helper ở compile-time để build message, nhưng **wire envelope canonical vẫn là `MessageEnvelope + JsonNode`**.

## 12.4. Phân loại message

### Request

Client → Server.

### Response

Server → Client tạo request.

### Event

Server chủ động gửi tới một hoặc nhiều Client.

## 12.5. MessageType dự kiến

```text
REGISTER
LOGIN
LOGOUT
GET_PROFILE
PROFILE_RESULT
GET_RANKING
RANKING_RESULT
GET_MATCH_HISTORY
MATCH_HISTORY_RESULT

GET_ONLINE_PLAYERS
ONLINE_PLAYERS_UPDATED

CREATE_ROOM
JOIN_ROOM
LEAVE_ROOM
INVITE_PLAYER
ACCEPT_INVITE
REJECT_INVITE
READY
UNREADY
START_GAME
ROOM_UPDATED

ROLL_DICE
DICE_RESULT
MOVE_PIECE
MOVE_PIECE_RESULT
GAME_STATE
GAME_STATE_UPDATED
TURN_STARTED
TURN_CHANGED
TURN_TIMEOUT
GAME_OVER

CHAT_MESSAGE

PING
PONG
RECONNECT
RECONNECT_RESULT

ERROR
```

Tên enum cuối cùng có thể thay đổi, nhưng không thay đổi semantics nếu chưa cập nhật tài liệu.

---

# 13. Xử lý connection

## 13.1. Thread phía Unity Client

Không block Unity main thread bằng network I/O, `.Wait()` hoặc `.Result`.
`NetworkClient` dùng `TcpClient`, đọc/ghi bất đồng bộ và serialize writes bằng
`SemaphoreSlim`; network layer không gọi Unity API.

Event từ reader thread được `NetworkSession` đưa vào `ConcurrentQueue<Action>`.
`NetworkSession.Update()` lấy từng action ra và cập nhật state/UI trên Unity main thread.
Các hàm async được gọi từ main thread giữ Unity synchronization context cho phần cập nhật UI.

`NetworkSession` dùng `DontDestroyOnLoad` để giữ socket/session qua các scene.
Controller phải gỡ đăng ký event khi bị hủy và kiểm tra vòng đời scene sau `await`.
Không lưu mật khẩu hoặc session token vào log/PlayerPrefs.

## 13.2. Heartbeat

Khuyến nghị:

```text
Server → PING
Client → PONG
```

Ví dụ:

- heartbeat mỗi 10 giây;
- mất 3 heartbeat liên tiếp thì đánh dấu mất kết nối.

Có thể kết hợp `Socket#setSoTimeout`.

---

# 14. Reconnect

## 14.1. Mất kết nối không đồng nghĩa bỏ cuộc ngay

Khi Client mất kết nối:

```text
presenceState = DISCONNECTED
```

Server giữ trạng thái trong **60 giây**.

## 14.2. Grace period

```text
DISCONNECTED
    ↓
60 giây
```

Trong thời gian này:

- game vẫn tiếp tục;
- các lượt khác vẫn chạy;
- nếu đến lượt người mất kết nối thì timer phase vẫn đếm;
- quá deadline của `WAITING_FOR_ROLL` hoặc `WAITING_FOR_MOVE` thì lượt bị bỏ.

Nếu reconnect trong 60 giây:

1. xác thực session;
2. gắn connection mới;
3. chuyển trạng thái phù hợp;
4. gửi `FULL_GAME_STATE` / `GAME_STATE`;
5. Client render lại toàn bộ trạng thái.

Nếu quá 60 giây:

```text
presenceState = DISCONNECTED
matchStatus   = FORFEITED
```

Sau đó:

- tính bỏ cuộc;
- quân bị loại;
- cập nhật ranking/game-end;
- broadcast trạng thái mới.

## 14.3. Quit khác Disconnect

### Nhấn nút Thoát

- xác nhận bỏ cuộc;
- `matchStatus = FORFEITED` ngay;
- không có grace period;
- 0 điểm;
- quân bị loại.

### Mất mạng

- `DISCONNECTED`;
- chờ 60 giây;
- vẫn có quyền reconnect.

---

# 15. Khởi tạo trận đấu

Khi chủ phòng bắt đầu:

Server:

1. khởi tạo Game State;
2. cấp 4 quân cho mỗi người;
3. tất cả quân ban đầu ở chuồng/bãi;
4. gán màu;
5. xác định thứ tự chơi;
6. xác định người bắt đầu;
7. khởi tạo timer;
8. gửi trạng thái tới toàn bộ Client.

Mỗi người chơi có 4 quân cùng màu.

## 15.1. Người đi đầu tiên

Quy tắc canonical:

> Khi bắt đầu ván, Server chọn participant có `slotIndex` nhỏ nhất trong số các slot đang có người chơi hợp lệ.

Với cấu hình thông thường:

```text
Slot 0 = RED
Slot 1 = BLUE
Slot 2 = YELLOW
Slot 3 = GREEN
```

nên nếu Slot 0 đang có người thì quân Đỏ luôn đi đầu tiên.

Nếu Slot 0 trống do người chơi đã rời phòng chờ trước khi bắt đầu, Server chọn occupied slot nhỏ nhất tiếp theo.

Ví dụ:

```text
Slot 0 = empty
Slot 1 = BLUE
Slot 2 = YELLOW
Slot 3 = empty

→ BLUE đi đầu tiên
```

Pseudo-code:

```java
Player firstPlayer = room.getPlayers().stream()
        .filter(player -> player.getMatchStatus() == MatchParticipantStatus.ACTIVE)
        .min(Comparator.comparingInt(Player::getSlotIndex))
        .orElseThrow();

gameState.setCurrentPlayerId(firstPlayer.getId());
gameState.setCurrentSlot(firstPlayer.getSlotIndex());
```

Client không được tự chọn người đi đầu.

Không random người đi đầu ở phiên bản hiện tại.

---

# 16. Đường đi và cấu trúc bàn cờ

## 16.1. Đường đi chung

Đường đi chung có đúng **48 ô**, đánh chỉ số toàn cục:

```text
0 .. 47
```

Mọi Client và Server phải dùng cùng quy ước này.

Không được tự tạo một hệ tọa độ khác trong Unity rồi suy diễn độc lập với Game Engine.

## 16.2. Global Start Index theo màu

Chỉ số xuất phát cố định:

```text
RED    → START_INDEX = 0
BLUE   → START_INDEX = 12
YELLOW → START_INDEX = 24
GREEN  → START_INDEX = 36
```

Khuyến nghị khai báo trong enum/config dùng chung:

```java
public enum PieceColor {
    RED(0),
    BLUE(12),
    YELLOW(24),
    GREEN(36);

    private final int startIndex;
}
```

## 16.3. Mô hình tọa độ canonical của `Piece`

**Bản chính lưu tiến độ tương đối bằng `stepCount`, không lưu `globalCell` như nguồn sự thật.**

Quy ước:

```text
stepCount = -1
→ PieceState.IN_YARD

stepCount = 0
→ vừa ra quân, đang ở Spawn Cell của màu mình

0 <= stepCount <= 47
→ đang ở vòng chung 48 ô

48 <= stepCount <= 53
→ đang ở Finish Track

stepCount = 53
→ nấc 6, sau khi resolve thì PieceState.FINISHED
```

Lưu ý quan trọng:

> Max hợp lệ là `53`, không phải `54`.

Vì:

- `stepCount = 0` đã là ô xuất phát đầu tiên;
- vòng chung có 48 vị trí tương ứng `0..47`;
- 6 nấc Finish Track tương ứng `48..53`.

## 16.4. Từ Local Step sang Global Ring Index

Khi:

```text
0 <= stepCount <= 47
```

tọa độ toàn cục:

\[
globalCell =
(START_INDEX + stepCount) \bmod 48
\]

Ví dụ RED:

```text
stepCount = 0  → globalCell = 0
stepCount = 5  → globalCell = 5
stepCount = 47 → globalCell = 47
```

Ví dụ BLUE:

```text
stepCount = 0  → globalCell = 12
stepCount = 10 → globalCell = 22
stepCount = 47 → globalCell = 11
```

## 16.5. Finish Track Slot

Khi:

```text
48 <= stepCount <= 53
```

nấc đích:

\[
finishTrackSlot = stepCount - 47
\]

Do đó:

```text
stepCount 48 → slot 1
stepCount 49 → slot 2
stepCount 50 → slot 3
stepCount 51 → slot 4
stepCount 52 → slot 5
stepCount 53 → slot 6
```

## 16.6. Ô cuối vòng chung trước khi vào Finish Track

Ô cuối của từng màu:

```text
RED    → 47
BLUE   → 11
YELLOW → 23
GREEN  → 35
```

Công thức:

\[
entryCell =
(START_INDEX + 47) \bmod 48
\]

## 16.7. Trách nhiệm UI

Unity Client có thể có bảng:

```text
globalCell -> RectTransform/coordinate trên màn hình
finishTrackSlot + color -> RectTransform/coordinate chuồng đích
```

Nhưng UI **không được tự tính tiến độ authoritative**.

Server gửi state gồm tối thiểu:

- `pieceId`;
- `color`;
- `pieceState`;
- `stepCount`.

Client dùng đúng công thức trong mục này để render.

## 16.8. Khi bị đá về chuồng

Khi một quân bị đá:

```text
PieceState = IN_YARD
stepCount = -1
```

Khuyến nghị đồng thời reset các hiệu ứng gắn với quân:

```text
slowed = false
shielded = false
```

để quân ra lại với trạng thái sạch.

---

# 17. Luật lượt chơi

Trong mỗi lượt:

1. Server bắt đầu lượt;
2. Client của người chơi hiện tại được phép Roll;
3. người chơi bấm Đổ xúc xắc;
4. Server sinh số ngẫu nhiên từ 1 đến 6;
5. Server broadcast kết quả;
6. Server tính các nước đi hợp lệ;
7. nếu không có nước đi hợp lệ → kết thúc lượt;
8. nếu có → người chơi chọn quân;
9. Server validate;
10. Server cập nhật game;
11. Server xử lý hiệu ứng;
12. Server xác định bonus roll hoặc đổi lượt;
13. broadcast Game State mới.

---

# 18. Xúc xắc

Server là nơi duy nhất sinh xúc xắc.

```text
dice ∈ [1, 6]
```

Client không gửi giá trị xúc xắc lên Server.

---

# 19. Luật ra quân

## 19.1. Điều kiện cơ bản

Người chơi phải đổ được **6** để đưa một quân từ bãi/chuồng ban đầu ra ô xuất phát.

Khi đổ 6, người chơi có thể:

- ra một quân mới nếu hợp lệ;
- hoặc di chuyển một quân đang ở trên bàn nếu có nước đi hợp lệ.

## 19.2. Ô xuất phát trống

```text
spawn cell = empty
→ được ra quân
```

## 19.3. Ô xuất phát có quân cùng màu

```text
spawn cell = own piece
→ không được ra quân
```

Người chơi phải chọn nước đi khác nếu có.

Nếu không có nước đi nào khác:

```text
END_TURN
```

## 19.4. Ô xuất phát có quân đối phương

Áp dụng quy tắc đá quân như nước đi thông thường.

```text
spawn cell = opponent
→ resolve capture
```

Nếu đối phương có Khiên:

- áp dụng quy tắc Khiên;
- không viết logic riêng mâu thuẫn với di chuyển thông thường.

---

# 20. Luật di chuyển trên đường chung

## 20.1. Số bước

Số bước theo kết quả xúc xắc, sau khi áp dụng hiệu ứng nếu có.

## 20.2. Đi xuyên qua quân

Cho phép quân đi xuyên qua quân khác.

Server chủ yếu kiểm tra ô kết thúc.

## 20.3. Không được dừng trên quân cùng màu

Nếu ô đích có quân cùng màu:

```text
move = invalid
```

## 20.4. Dừng trên quân đối phương

Nếu ô đích có quân đối phương:

```text
capture
```

trừ khi Khiên ngăn việc đá theo quy tắc Khiên.

## 20.5. Không được vượt quá phạm vi hợp lệ

Nếu nước đi vượt vùng có thể đi:

```text
move = invalid
```

---

# 21. Luật đá quân

Khi quân A kết thúc nước đi đúng tại ô của quân B đối phương:

- nếu B không có Khiên:
  - B bị đá;
  - B trở lại bãi/chuồng ban đầu;
  - B phải lại chờ xúc xắc 6 để ra quân;
  - A chiếm ô đó.

Server xử lý toàn bộ.

Client chỉ hiển thị kết quả.

---

# 22. Luật Khiên

## 22.1. Nhận Khiên

Khi quân dừng đúng ô Khiên:

```text
piece.shielded = true
```

## 22.2. Bị tấn công khi có Khiên

Khi đối phương có một nước đi hợp lệ đến đúng ô của quân có Khiên:

1. Khiên kích hoạt;
2. quân được bảo vệ không bị đá;
3. Khiên bị tiêu hao;
4. quân tấn công không chiếm được ô đó;
5. quân tấn công giữ nguyên / trở về vị trí trước nước đi;
6. hành động di chuyển đã được sử dụng.

Không cho hai quân khác màu đứng chung ô.

Không coi ô có Khiên là vĩnh viễn bất khả xâm phạm.

---

# 23. Ô đặc biệt

Ô đặc biệt được Server xác định và cố định khi khởi tạo bàn cờ.

Chỉ kích hoạt khi quân **kết thúc nước đi** tại ô đó.

Đi ngang qua không kích hoạt.

## 23.1. Vị trí cấm đặt ô đặc biệt

Dù vị trí ô đặc biệt được cấu hình cố định hay sinh khi khởi tạo trận, Server phải validate blacklist.

**Tuyệt đối không đặt ô đặc biệt tại:**

### Spawn Cell

```text
0, 12, 24, 36
```

### Ô cuối vòng chung trước cửa Finish Track

```text
47, 11, 23, 35
```

### Bên trong Finish Track

```text
slot 1..6 của mọi màu
```

Tập blacklist trên vòng chung:

```java
Set<Integer> SPECIAL_CELL_BLACKLIST =
        Set.of(0, 12, 24, 36, 47, 11, 23, 35);
```

Mọi `specialCell.globalIndex` phải thỏa:

```text
0 <= globalIndex < 48
globalIndex not in SPECIAL_CELL_BLACKLIST
```

Finish Track không bao giờ chứa special cell.

Mục tiêu:

- không làm thay đổi semantics của Spawn;
- không làm rối thời điểm chuyển từ vòng chung sang Finish Track;
- Client và Server có layout đặc biệt nhất quán.

### Layout canonical hiện tại

Phiên bản hiện tại dùng layout cố định, đối xứng qua bốn phần tư của vòng chung:

```text
SPEED  → 2, 14, 26, 38
SLOW   → 4, 16, 28, 40
LUCKY  → 6, 18, 30, 42
TRAP   → 8, 20, 32, 44
SHIELD → 10, 22, 34, 46
```

Server đưa toàn bộ layout này vào `GameState.specialCells` khi khởi tạo trận. Client render đúng danh sách Server gửi, không tự tạo layout riêng.

## 23.2. Tăng tốc

Khi dừng đúng ô Tăng tốc:

```text
+2 bước
```

Nếu phần +2 hợp lệ:

- di chuyển thêm 2 ô.

Nếu không hợp lệ do:

- vượt phạm vi;
- rơi vào ô cùng màu;
- điều kiện khác không hợp lệ;

thì:

- giữ quân tại ô Tăng tốc;
- không hủy nước đi gốc.

## 23.3. Làm chậm

Khi dừng đúng ô Làm chậm:

```java
piece.slowed = true;
```

Lần tiếp theo chính quân đó được chọn:

```text
actualSteps = max(0, dice - 2)
```

Sau khi áp dụng:

```java
piece.slowed = false;
```

Nếu kết quả bằng 0:

- quân đứng nguyên;
- Slow bị tiêu hao;
- hành động di chuyển được xem là đã hoàn thành.

Nếu người chơi chọn quân khác:

- Slow của quân đang bị ảnh hưởng vẫn còn.

## 23.4. May mắn

Khi dừng đúng ô May mắn:

- người chơi nhận quyền tung thêm một lần, nếu nước đi đã hoàn thành hợp lệ.

## 23.5. Bẫy

Khi quân dừng đúng ô Bẫy:

```text
lùi 2 bước theo tiến độ local `stepCount`
```

Công thức:

```java
int targetStep = currentStep - 2;
```

### Giới hạn lùi ở đầu vòng

Bẫy **không bao giờ được đưa quân ngược trở lại Yard**.

Vị trí lùi chỉ có thể hợp lệ khi:

```text
targetStep >= 0
```

Nếu:

```text
targetStep < 0
```

thì:

- hiệu ứng lùi không được thực hiện;
- quân giữ nguyên tại ô Bẫy;
- quân không chuyển thành `IN_YARD`;
- người chơi không phải Roll 6 lại chỉ vì Trap.

Ví dụ:

```text
currentStep = 1
targetStep  = 1 - 2 = -1

→ invalid trap destination
→ giữ nguyên stepCount = 1
```

### Resolve ô đích của Trap

Nếu `targetStep >= 0`, Server tiếp tục kiểm tra destination:

- có quân cùng màu → không thực hiện lùi, giữ nguyên ở ô Bẫy;
- có quân đối phương không có Khiên → lùi tới đó và đá quân;
- có quân đối phương có Khiên → áp dụng đúng quy tắc Khiên;
- ô hợp lệ và trống → lùi bình thường.

Do Finish Track không chứa special cell, hiệu ứng Trap chỉ phát sinh từ một special cell trên vòng chung.

Vị trí mới do Trap tạo ra **không kích hoạt thêm special cell**.

## 23.6. Không kích hoạt hiệu ứng dây chuyền

Trong một nước đi:

- quân chỉ kích hoạt tối đa **một ô đặc biệt**;
- vị trí mới do hiệu ứng Tăng tốc/Bẫy tạo ra không kích hoạt thêm ô đặc biệt.

Mục tiêu:

- tránh loop hiệu ứng;
- giảm edge case;
- dễ kiểm thử.

---

# 24. Quy tắc tương tác hiệu ứng và Finish Track

Khuyến nghị chốt để tránh mơ hồ:

- Finish Track không chứa ô đặc biệt;
- quân trong Finish Track không kích hoạt ô đặc biệt mới;
- Slow đã có từ trước vẫn có thể ảnh hưởng lần di chuyển tiếp theo của chính quân đó trong Finish Track;
- Khiên đã có từ trước không còn ý nghĩa chiến đấu khi quân đã vào Finish Track riêng của màu mình, vì đối phương không vào khu vực đó;
- có thể giữ flag Khiên trong model nhưng Game Rule bỏ qua nó trong Finish Track, hoặc xóa Khiên khi vào Finish Track để đơn giản.

Ưu tiên cách đơn giản:

```text
khi quân vào Finish Track
→ shielded = false
```

---

# 25. Luật Finish Track / Chuồng đích

## 25.1. Cấu trúc

Mỗi màu có cột đích gồm 6 nấc:

```text
[1] [2] [3] [4] [5] [6]
```

Theo mô hình `stepCount`:

```text
slot 1 → stepCount 48
slot 2 → stepCount 49
slot 3 → stepCount 50
slot 4 → stepCount 51
slot 5 → stepCount 52
slot 6 → stepCount 53
```

## 25.2. Chuyển từ vòng chung vào Finish Track

Quân **được phép carry-over bước dư trực tiếp vào Finish Track**.

Game Engine không tách thành:

```text
đi đến cửa đích
→ dừng bắt buộc
→ lượt sau mới vào chuồng
```

Mà tính trực tiếp:

```java
int targetStep = piece.stepCount() + actualSteps;
```

Sau đó:

```text
targetStep <= 47
→ vẫn ở vòng chung

48 <= targetStep <= 53
→ vào / di chuyển trong Finish Track

targetStep > 53
→ INVALID
```

## 25.3. Ví dụ Carry-over Steps

Ví dụ:

```text
piece.stepCount = 46
dice = 4
```

Kết quả:

```text
targetStep = 50
```

Nghĩa là quân:

1. đi qua phần cuối của vòng chung;
2. bước thẳng vào Finish Track;
3. dừng tại:

```text
finishTrackSlot = 50 - 47 = 3
```

Không có bước dừng trung gian bắt buộc.

## 25.4. Điều kiện invalid khi vào Finish Track

Nước đi không hợp lệ nếu:

```text
targetStep > 53
```

hoặc target Finish Track slot đang bị quân cùng màu khác chiếm.

Riêng `slot 6`:

- quân đến đúng slot 6 lập tức chuyển `FINISHED`;
- quân Finished không chiếm occupancy của Finish Track;
- vì vậy các quân Finished trước đó không chặn quân sau hoàn thành.

## 25.5. Di chuyển khi đã ở Finish Track

Số bước dùng `actualSteps` sau khi áp dụng Slow nếu có.

Ví dụ:

```text
stepCount = 48  // slot 1
actualSteps = 3
targetStep = 51 // slot 4
```

## 25.6. Không vượt nấc 6

Ví dụ:

```text
stepCount = 52 // slot 5
actualSteps = 2
targetStep = 54
```

Vì:

```text
54 > 53
```

nên:

```text
move = INVALID
```

## 25.7. Có thể đi qua quân cùng màu

Cho phép đi xuyên qua quân.

Chỉ kiểm tra ô kết thúc.

## 25.8. Không được dừng trên nấc có quân cùng màu

Nếu target slot `1..5` đang bị quân mình chiếm:

```text
move = INVALID
```

Không có quân đối phương trong Finish Track riêng của màu.

## 25.9. Hoàn thành quân

Khi:

```text
targetStep = 53
```

thì:

```text
PieceState = FINISHED
stepCount = 53
```

Quân đó:

- không di chuyển nữa;
- được tính là hoàn thành;
- không còn chiếm một slot Finish Track về mặt collision;
- có thể được UI chuyển sang vùng hiển thị Finished.

## 25.10. Điều kiện hoàn thành người chơi

Người chơi hoàn thành khi:

```text
4 / 4 quân = FINISHED
```

## 25.11. Hàm validate nên dùng cùng một mô hình

Khuyến nghị Game Engine có logic tương đương:

```java
int targetStep = currentStep + actualSteps;

if (targetStep > 53) {
    return INVALID;
}

if (targetStep <= 47) {
    int globalCell =
            (startIndex + targetStep) % 48;

    return validateRingDestination(globalCell);
}

int finishSlot = targetStep - 47;

return validateFinishDestination(
        player,
        piece,
        finishSlot
);
```

Không tạo hai cách tính khác nhau giữa `calculateValidMoves()` và `movePiece()`.

---

# 26. Dice = 6 và Bonus Roll

## 26.1. Quy tắc cơ bản

Sau khi người chơi:

- đổ được 6;
- và hoàn thành thành công một nước đi hợp lệ;

thì được tung thêm một lần.

Quy tắc cốt lõi:

> Bonus roll do xúc xắc 6 chỉ được cấp khi một nước đi hợp lệ từ kết quả đó đã hoàn thành thành công.

## 26.2. Đổ 6 liên tiếp

Cho phép:

```text
6 → move → bonus
6 → move → bonus
...
```

Không áp dụng luật mất lượt sau 3 lần 6 ở phiên bản hiện tại.

## 26.3. Dice 6 + Ô May mắn

Không cộng dồn nhiều bonus roll.

Nếu một nước đi đồng thời thỏa:

- dice = 6;
- rơi ô May mắn;

thì:

```text
bonusRoll = 1
```

không phải 2.

## 26.4. Dice 6 nhưng không có nước đi hợp lệ

Nếu:

```text
dice = 6
validMoves = empty
```

thì:

- không được bonus roll;
- kết thúc lượt ngay.

Ví dụ:

- ô xuất phát bị quân mình chặn;
- các quân khác đều gần cuối Finish Track;
- đi 6 sẽ vượt quá nấc 6.

Kết quả:

```text
END_TURN
```

---

# 27. No Valid Moves

Sau khi Server sinh xúc xắc:

```text
validMoves = calculateValidMoves(...)
```

Nếu:

```text
validMoves.isEmpty()
```

thì:

1. Server thông báo không có nước đi hợp lệ;
2. không chờ Client chọn quân;
3. kết thúc lượt;
4. nếu dice = 6 cũng không có bonus;
5. chuyển lượt người tiếp theo.

---

# 28. Timeout mỗi lượt

Theo yêu cầu cập nhật ngày 2026-09-22, **mỗi phase có 120 giây**, một lượt bình thường tối đa 240 giây.

Để tránh trường hợp người chơi Roll quá muộn rồi gần như không còn thời gian chọn quân, thời gian được chia thành **hai phase độc lập**.

Bản chính:

```text
WAITING_FOR_ROLL → 120 giây
WAITING_FOR_MOVE → 120 giây
```

Tổng tối đa của một lượt bình thường:

```text
120 + 120 = 240 giây
```

Server là nguồn thời gian chính thức.

Client chỉ hiển thị countdown.

## 28.1. Phase 1 — WAITING_FOR_ROLL

Khi bắt đầu lượt:

```text
TurnState = WAITING_FOR_ROLL
rollDeadline = serverNow + 120s
```

Nếu Server không nhận được `ROLL_DICE` hợp lệ trước deadline:

```text
TURN_TIMEOUT
→ END_TURN
```

## 28.2. Phase 2 — WAITING_FOR_MOVE

Ngay sau khi Server sinh xúc xắc và xác định có ít nhất một nước đi hợp lệ:

```text
TurnState = WAITING_FOR_MOVE
moveDeadline = serverNow + 120s
```

Người chơi luôn có đủ phase Move riêng, bất kể họ bấm Roll ở giây thứ mấy trong phase Roll.

Nếu không nhận được `MOVE_PIECE` hợp lệ trước deadline:

```text
TURN_TIMEOUT
→ END_TURN
```

## 28.3. Không có nước đi hợp lệ

Ngay sau Roll:

```text
validMoves.isEmpty()
```

thì Server:

1. không mở `WAITING_FOR_MOVE`;
2. không chờ 120 giây;
3. kết thúc lượt ngay;
4. nếu dice = 6 cũng không cấp bonus.

## 28.4. Request đến sau deadline

Deadline do Server quyết định theo thời điểm Server nhận và xử lý request.

Client không được tự khai rằng request được gửi "trước giờ".

Request gameplay đến sau deadline:

```text
TURN_TIMEOUT / MOVE_NOT_ALLOWED
```

tùy state hiện tại.

## 28.5. Bonus roll

Nếu người chơi được bonus roll:

```text
TurnState = WAITING_FOR_ROLL
rollDeadline = serverNow + 120s
```

Nếu bonus roll tạo ra nước đi hợp lệ:

```text
WAITING_FOR_MOVE
moveDeadline = serverNow + 120s
```

Bonus roll tạo một chu kỳ Roll/Move mới của cùng người chơi.

Do đó tổng thời gian thực tế người đó giữ lượt có thể lớn hơn 240 giây nếu liên tiếp nhận bonus; đây là hành vi chủ đích.

## 28.6. Dữ liệu timer gửi Client

Event bắt đầu phase nên chứa tối thiểu:

```text
turnState
phaseDurationMillis
```

Có thể kèm:

```text
serverDeadline
```

để phục vụ debug/UI.

Quyết định timeout cuối cùng vẫn thuộc Server; countdown trên Unity chỉ mang tính hiển thị.

---

# 29. Thứ tự lượt và Turn Rotation

Server quyết định hoàn toàn thứ tự lượt và lưu `currentSlot` / `currentPlayerId`.

Client không được gửi:

```text
nextPlayer = ...
```

Server phải reject request từ người không đúng lượt:

```text
NOT_YOUR_TURN
```

## 29.1. Thứ tự slot

Vòng quay lượt cố định theo slot:

```text
0 → 1 → 2 → 3 → 0 → ...
```

Slot trống được bỏ qua.

Không reorder danh sách người chơi để tính lượt.

## 29.2. Điều kiện một participant còn được nhận lượt

Một participant còn eligible cho Turn Rotation khi:

```text
matchStatus == ACTIVE
```

Connection state **không quyết định** participant có bị loại khỏi vòng lượt hay không.

Do đó:

```text
ACTIVE + presenceState = PLAYING
→ nhận lượt bình thường

ACTIVE + presenceState = DISCONNECTED
→ vẫn nhận lượt trong reconnect grace period

COMPLETED
→ luôn skip

FORFEITED
→ luôn skip
```

Spectator và slot trống luôn bị bỏ qua.

## 29.3. Người DISCONNECTED vẫn có lượt

Một người đang:

```text
presenceState = DISCONNECTED
matchStatus   = ACTIVE
```

vẫn được `nextTurn()` chọn.

Khi tới lượt họ:

1. Server tạo phase `WAITING_FOR_ROLL`;
2. deadline 120 giây vẫn chạy;
3. nếu reconnect trước deadline, Client nhận Full Game State và có thể Roll trong thời gian còn lại;
4. nếu không thao tác kịp, lượt timeout;
5. Server chuyển sang participant ACTIVE tiếp theo.

Điều này ngăn trường hợp mất mạng vô tình trở thành lợi thế vì được bỏ qua mọi lượt.

## 29.4. Thuật toán `nextTurn()`

Pseudo-code canonical:

```java
Optional<Player> findNextTurnPlayer(GameRoom room, int currentSlot) {
    for (int offset = 1; offset <= 4; offset++) {
        int candidateSlot = (currentSlot + offset) % 4;

        Player candidate = room.getPlayerAtSlot(candidateSlot);

        if (candidate == null) {
            continue;
        }

        if (candidate.getMatchStatus() != MatchParticipantStatus.ACTIVE) {
            continue;
        }

        return Optional.of(candidate);
    }

    return Optional.empty();
}
```

Không kiểm tra `presenceState == PLAYING` trong điều kiện eligibility.

## 29.5. Kiểm tra Game Over trước khi đổi lượt

Sau mọi event có thể làm thay đổi trạng thái participant, ví dụ:

- hoàn thành đủ 4 quân;
- Quit;
- reconnect grace period hết hạn;
- bất kỳ hành động nào dẫn đến `COMPLETED` hoặc `FORFEITED`;

Server phải chạy:

```text
evaluateGameEnd()
```

**trước** `nextTurn()`.

Flow:

```text
Move / Complete / Forfeit / GraceExpired
                 ↓
          update GameState
                 ↓
        evaluateGameEnd()
          /            \
      GAME OVER       CONTINUE
          │               │
      broadcast        nextTurn()
```

Nếu chỉ còn đúng một participant `ACTIVE`, áp dụng Forfeit/Completion Cascading ở Mục 30 và kết thúc trận; không tạo một lượt mới không cần thiết.

## 29.6. Invariant của lượt

Tại mọi thời điểm đang gameplay:

- chỉ có tối đa một `currentPlayerId`;
- `currentPlayerId`, nếu tồn tại, phải trỏ tới participant `ACTIVE`;
- `COMPLETED` và `FORFEITED` không bao giờ nhận lượt mới;
- `DISCONNECTED + ACTIVE` vẫn có thể là current player;
- Server là nơi duy nhất thay đổi `currentPlayerId`.

---

# 30. Kết thúc và xếp hạng

## 30.1. Hoàn thành

Một người hoàn thành khi 4 quân đều `PieceState.FINISHED`.

Khi đó Server:

1. gán `matchStatus = COMPLETED`;
2. ghi nhận rank ngay lúc hoàn thành;
3. loại participant đó khỏi Turn Rotation;
4. chạy `evaluateGameEnd()`.

Những participant còn `ACTIVE` tiếp tục.

## 30.2. Forfeit Cascading và điều kiện dừng sớm

Định nghĩa người **còn đang thi đấu thực sự**:

```text
active =
    matchStatus == ACTIVE
```

Sau mọi sự kiện có thể làm thay đổi số người active, đặc biệt:

- một người hoàn thành;
- một người bấm Thoát;
- một người hết grace period reconnect và bị forfeit;

Server phải tính lại:

```text
activePlayerCount
```

Nếu:

```text
activePlayerCount == 1
```

thì:

1. dừng gameplay ngay;
2. không bắt người cuối chơi một mình;
3. người cuối được gán **thứ hạng tốt nhất còn chưa có chủ**;
4. người đó nhận điểm tương ứng với thứ hạng đó;
5. trận chuyển `FINISHED`;
6. Server lưu kết quả và broadcast `GAME_OVER`.

Ví dụ phòng 2 người:

```text
A vs B
B FORFEIT
→ A = rank 1
→ A +3 điểm
→ B = rank 2
→ B 0 điểm
→ GAME_OVER ngay
```

Ví dụ phòng 4 người:

```text
D FORFEIT → rank 4, 0 điểm
C FORFEIT → rank 3, 0 điểm

còn A, B
```

Nếu B sau đó FORFEIT:

```text
B → rank 2, 0 điểm
A → rank 1, +3 điểm
GAME_OVER
```

Nếu trước đó đã có người hoàn thành rank 1, thì người active cuối nhận rank tốt nhất còn lại, ví dụ rank 2.

## 30.3. Điểm theo số người

### Phòng 2 người

| Hạng | Điểm |
| ---- | ---: |
| 1    |   +3 |
| 2    |   +0 |

### Phòng 3 người

| Hạng | Điểm |
| ---- | ---: |
| 1    |   +3 |
| 2    | +1.5 |
| 3    |   +0 |

### Phòng 4 người

| Hạng | Điểm |
| ---- | ---: |
| 1    |   +3 |
| 2    | +1.5 |
| 3    | +0.5 |
| 4    |   +0 |

## 30.4. Bỏ cuộc

Người bỏ cuộc luôn:

- `scoreEarned = 0`;
- ghi nhận là thua trận.

### Cách gán rank khi forfeit

Forfeit nhận **hạng thấp nhất còn chưa có chủ** tại thời điểm bị loại.

Có thể tư duy bằng hai con trỏ:

```text
nextBestRank  = 1
nextWorstRank = playerCount
```

- người hoàn thành tự nhiên nhận `nextBestRank`, sau đó tăng lên;
- người forfeit nhận `nextWorstRank`, sau đó giảm xuống;
- khi chỉ còn một active player, người đó nhận rank còn tốt nhất.

Ví dụ 4 người:

```text
D bỏ trước → rank 4, score 0
C bỏ sau  → rank 3, score 0
B hoàn thành → rank 1
A → rank 2
```

Không cấp điểm theo rank thông thường cho người đã `FORFEITED`; điểm của họ luôn bằng 0.

## 30.5. Kiểu dữ liệu điểm

Không dùng `FLOAT` nếu lưu điểm tích lũy chính xác.

Khuyến nghị MySQL:

```sql
DECIMAL(10,1)
```

---

# 31. Xử lý người chơi thoát giữa trận

Khi người chơi chọn Thoát:

1. xác nhận thao tác nếu UI có dialog;
2. gửi request tới Server;
3. Server chuyển `matchStatus = FORFEITED`;
4. ghi nhận thua;
5. điểm trận = 0;
6. loại quân khỏi bàn;
7. broadcast;
8. game tiếp tục nếu còn đủ điều kiện.

---

# 32. Chơi ván mới

### Luồng triển khai hiện tại

- Nút **Chơi tiếp** trong ResultScene gửi `READY(roomId, ready=true)`.
- Với phòng FINISHED, Server phải lưu kết quả thành công trước khi mở lại WAITING.
  Nếu persistence lỗi, giữ nguyên trận và trả lỗi để thử lại.
- Khi mở lại, loại membership đã rời, giữ roomId/slot/màu/chủ phòng của thành viên
  còn lại; reset ready của mọi người rồi đặt ready cho người yêu cầu.
- Thành viên còn lại chuyển sang IN_ROOM; người mất kết nối vẫn DISCONNECTED
  nhưng trạng thái cần khôi phục khi reconnect là IN_ROOM.
- Broadcast ROOM_UPDATED đưa Unity về RoomScene và xóa cache trận cũ. Snapshot
  đến trễ thuộc match đã đóng không được khôi phục trận cũ.
- Chủ phòng Start khi ít nhất 2 người và tất cả ready/đang kết nối. Ván mới có
  matchId mới, timer mới, 4 quân IN_YARD/người và hiệu ứng được reset.
- Về sảnh vẫn gửi LEAVE_ROOM và đợi Server xác nhận.

Sau Game Over, hiển thị:

- danh sách người chơi;
- màu quân;
- thứ hạng;
- điểm nhận;
- tổng điểm mới.

Người chơi có thể:

- Ready chơi ván mới;
- Rời phòng.

Nếu ít nhất 2 người sẵn sàng:

- chủ phòng có thể bắt đầu ván mới.

Khi ván mới bắt đầu:

- reset Game State;
- 4 quân về bãi;
- reset hiệu ứng;
- giữ điểm tích lũy tài khoản.

---

# 33. Chat trong phòng

Chức năng mở rộng:

- realtime;
- chỉ gửi trong cùng phòng;
- Server relay / broadcast.

Không để chat làm block Game Engine.

Có thể xử lý riêng khỏi lock gameplay nếu không sửa trạng thái game.

---

# 34. Chế độ khán giả

Chức năng mở rộng.

Tách:

```java
List<PlayerSession> players;
List<ClientSession> spectators;
```

Khán giả được:

- nhận Game State;
- nhận sự kiện;
- xem trận;
- có thể chat nếu nhóm cho phép.

Khán giả không được:

- Roll;
- Move;
- Ready thay Player;
- thay đổi Game State.

Nếu gửi action không hợp lệ:

```text
SPECTATOR_NOT_ALLOWED
```

Spectator không phải chức năng ưu tiên trước core gameplay.

---

# 35. Database

## 35.1. Bảng `users`

Khuyến nghị:

```text
id
username
password_hash
display_name
score
first_place_count
created_at
updated_at
```

## 35.2. Bảng `matches`

```text
id
started_at
ended_at
status
created_at
```

## 35.3. Bảng `match_players`

```text
id
match_id
user_id
color
rank
score_earned
result
```

## 35.4. Bảng thống kê

Có thể có:

```text
player_statistics
```

gồm:

```text
user_id
total_games
wins
losses
first_place_count
```

Hoặc tính một phần từ lịch sử nếu dữ liệu nhỏ.

## 35.5. Flyway

Dùng migration:

```text
V1__create_users.sql
V2__create_matches.sql
V3__create_match_players.sql
...
```

Không để `ddl-auto` tự thay đổi schema như hệ Spring Boot.

---

# 36. Logging

Dùng:

- SLF4J;
- Logback.

Log Server cần đủ để debug:

- connect;
- disconnect;
- login;
- room join/leave;
- game start;
- roll;
- move;
- timeout;
- reconnect;
- forfeit;
- exception.

Không log:

- plain text password;
- password hash nếu không cần;
- token/session bí mật toàn bộ ở mức production-like.

---

# 37. Error Code khuyến nghị

```text
INVALID_REQUEST
INVALID_MESSAGE
UNAUTHORIZED
SESSION_EXPIRED
ACCOUNT_ALREADY_LOGGED_IN
USERNAME_ALREADY_EXISTS
INVALID_CREDENTIALS

ROOM_NOT_FOUND
ROOM_FULL
ALREADY_IN_ROOM
NOT_IN_ROOM
NOT_ROOM_HOST
PLAYER_NOT_IDLE

GAME_NOT_STARTED
GAME_ALREADY_STARTED
NOT_YOUR_TURN
INVALID_GAME_STATE
INVALID_MOVE
NO_VALID_MOVE
PIECE_NOT_FOUND
PIECE_ALREADY_FINISHED

ROLL_NOT_ALLOWED
MOVE_NOT_ALLOWED
TURN_TIMEOUT

SPECTATOR_NOT_ALLOWED

INTERNAL_SERVER_ERROR
```

Agent không nên trả exception Java thô trực tiếp cho Client.

---

# 38. Phân công công việc

## 38.1. Hoàng Đình Duy — B23DCCN237

Phụ trách:

- thiết kế CSDL;
- đăng ký;
- đăng nhập;
- đăng xuất;
- quản lý thông tin người chơi;
- điểm số;
- thành tích;
- lịch sử trận đấu;
- bảng xếp hạng.

Giao diện:

- đăng nhập;
- đăng ký;
- thông tin cá nhân;
- bảng xếp hạng;
- lịch sử trận đấu.

## 38.2. Đặng Phi Long — B23DCCN497

Phụ trách:

- danh sách người chơi online;
- tạo phòng;
- tham gia phòng;
- rời phòng;
- mời người chơi;
- Accept/Reject;
- quản lý chủ phòng;
- trạng thái phòng;
- đồng bộ thông tin phòng.

Giao diện:

- sảnh chờ;
- danh sách người chơi;
- tạo phòng;
- phòng chờ;
- mời người chơi.

## 38.3. Tạ Thanh Thiên — B23DCCN783

Phụ trách:

- bàn cờ;
- quản lý quân cờ;
- điều phối lượt;
- xúc xắc;
- kiểm tra nước đi;
- ra quân;
- di chuyển;
- đá quân;
- ô đặc biệt;
- Finish Track;
- xác định thứ hạng;
- điều kiện hoàn thành.

Giao diện:

- bàn cờ;
- quân cờ;
- xúc xắc;
- ô đặc biệt;
- hiển thị lượt;
- hiển thị thứ hạng.

## 38.4. Trần Trọng Hùng — B23DCCN363

Phụ trách:

- TCP Server;
- Socket;
- nhiều Client đồng thời;
- Protocol;
- Request/Response;
- broadcast;
- Session;
- xử lý lỗi;
- mất kết nối;
- reconnect;
- timeout 240 giây;
- đồng bộ Game State;
- xử lý Player rời/mất kết nối;
- thread safety / concurrency phần mạng.

Giao diện:

- màn hình kết nối Server;
- trạng thái kết nối;
- đồng hồ 120 giây cho từng phase Roll/Move;
- thông báo mất kết nối;
- thông báo người chơi thoát trận.

---

# 39. Trách nhiệm tích hợp chung

Cả nhóm phải thống nhất trước khi tách code:

- model;
- DTO;
- enum;
- MessageType;
- ErrorCode;
- Game State representation;
- Room State;
- protocol JSON;
- quy tắc game.

Không để mỗi module tự tạo:

```text
Player
Room
GameState
Message
```

theo cấu trúc khác nhau.

Contract chuẩn phía Java phải nằm trong `common`; Unity ánh xạ đúng contract JSON tương ứng bằng C#, không tham chiếu trực tiếp assembly/JAR Java.

---

# 40. Game Engine — yêu cầu thiết kế

Nên có API logic dạng thuần Java, ít phụ thuộc Network/UI.

Ví dụ:

```java
RollResult rollDice(GameState state, PlayerId playerId);

List<ValidMove> calculateValidMoves(
    GameState state,
    PlayerId playerId,
    int dice
);

MoveResult movePiece(
    GameState state,
    PlayerId playerId,
    PieceId pieceId
);
```

Game Engine không nên trực tiếp gọi UI Unity.

Game Engine cũng không nên phụ thuộc Socket.

Tốt nhất:

```text
Network
   ↓
Application/Service
   ↓
GameEngine
   ↓
GameState
```

---

# 41. Quy tắc validate action

Mọi gameplay request phải validate tối thiểu:

1. session hợp lệ;
2. người chơi thuộc room;
3. room đang PLAYING;
4. Game State hợp lệ;
5. đúng lượt;
6. đúng TurnState;
7. piece thuộc người chơi;
8. piece có thể thao tác;
9. nước đi nằm trong `validMoves`;
10. request không bị lặp gây double-action.

---

# 42. Request ID và chống xử lý lặp

Khuyến nghị mỗi request có:

```text
requestId
```

Server có thể dùng để:

- map Response;
- debug;
- hạn chế double-submit;
- idempotency cho một số action nếu cần.

Ví dụ người dùng double-click Move:

```text
MOVE request A
MOVE request A
```

không được khiến quân đi hai lần.

---

# 43. Broadcast

Server broadcast khi có thay đổi quan trọng:

- người chơi online/offline;
- room update;
- player join/leave;
- game start;
- dice result;
- piece move;
- capture;
- special effect;
- turn change;
- timeout;
- disconnect;
- reconnect;
- ranking;
- game over.

Không broadcast dữ liệu nhạy cảm.

---

# 44. Full State và Incremental Event

Khuyến nghị kết hợp:

## 44.1. Event nhỏ

Trong luồng bình thường:

```text
DICE_ROLLED
PIECE_MOVED
TURN_CHANGED
```

## 44.2. Full State

Dùng khi:

- reconnect;
- join spectator;
- nghi ngờ Client lệch state;
- bắt đầu trận;
- recovery.

Server luôn có khả năng serialize Game State đầy đủ.

---

# 45. Testing

## 45.1. Unit Test

Ưu tiên test Game Engine.

### Dice / movement

- đi bình thường;
- ô cùng màu;
- đá đối phương;
- vượt phạm vi;
- đi qua quân khác.

### Spawn

- spawn trống;
- spawn bị own piece chặn;
- spawn đá đối phương;
- spawn gặp Shield.

### Tọa độ và Finish Track

- mapping `START_INDEX` của 4 màu;
- `stepCount -> globalCell`;
- `stepCount 47 ->` ô cuối vòng chung đúng màu;
- carry-over từ vòng chung vào Finish Track;
- ví dụ `46 + 4 -> stepCount 50 -> slot 3`;
- vượt max `53`;
- nấc đích bị chiếm;
- đạt `stepCount 53` → FINISHED;
- nhiều quân hoàn thành tuần tự.

### Special Cell

- Speed;
- Slow;
- Lucky;
- Trap;
- Trap tại `stepCount = 1` không được lùi về `-1`;
- Trap destination có own piece;
- Trap destination có opponent;
- Trap destination có opponent + Shield;
- Shield;
- không chain effect.

### Bonus

- roll 6 + valid move;
- roll 6 + no valid move;
- roll 6 + Lucky;
- nhiều lần 6 liên tiếp.

## 45.2. Integration Test

Test:

- 2 Client;
- 3 Client;
- 4 Client;
- create/join room;
- concurrent join;
- start game;
- Slot 0 có người → Red đi đầu;
- Slot 0 trống → occupied slot nhỏ nhất đi đầu;
- turn rotation bỏ qua slot trống;
- turn rotation bỏ qua `COMPLETED`;
- turn rotation bỏ qua `FORFEITED`;
- `DISCONNECTED + ACTIVE` vẫn nhận lượt và timeout bình thường;
- disconnect;
- reconnect;
- timeout;
- quit;
- host leave;
- game over;
- update ranking.

## 45.3. Network Test

Test:

- partial TCP read;
- nhiều message liên tiếp;
- `length <= 0`;
- `length > MAX_FRAME_LENGTH`;
- header giả cực lớn nhưng Server không được cấp phát mảng;
- malformed JSON;
- disconnect giữa message;
- duplicate request;
- client gửi action sai lượt.

## 45.4. Timeout / Ranking Test

Test:

- quá 120 giây chưa Roll;
- Roll hợp lệ rồi có đủ 120 giây Move;
- no-valid-move kết thúc ngay, không mở Move phase;
- bonus roll mở lại phase 120s/120s;
- phòng 2 người: 1 người forfeit → người còn lại rank 1 ngay;
- cascading forfeit ở phòng 3/4 người;
- người forfeit luôn nhận 0 điểm.

---

# 46. Các invariant quan trọng

Agent phải bảo vệ các invariant sau.

## 46.1. Room

```text
2 <= players <= 4 khi bắt đầu game
players <= 4 mọi lúc
```

## 46.2. Piece ownership

Mỗi quân chỉ thuộc đúng một Player.

## 46.3. Turn

Tại một thời điểm chỉ có tối đa một current player.

Nếu `currentPlayerId` tồn tại:

```text
currentPlayer.matchStatus == ACTIVE
```

`COMPLETED` và `FORFEITED` không được nhận lượt mới.

`DISCONNECTED + ACTIVE` vẫn được phép là current player trong reconnect grace period.

## 46.4. Dice

Chỉ Server sinh dice.

## 46.5. Occupancy

Không có hai quân cùng màu kết thúc ở cùng một ô logical đang chiếm chỗ.

Không có hai quân khác màu cùng ô sau khi resolve capture/shield.

## 46.6. Piece progress

`stepCount` phải thỏa:

```text
IN_YARD  → -1
ON_TRACK → 0..47
IN_FINISH_TRACK → 48..52
FINISHED → 53
```

Không tồn tại authoritative `stepCount > 53`.

## 46.7. Finished piece

`FINISHED` không được Move lại.

## 46.8. Ranking

Một người chỉ nhận một rank.

## 46.9. Match participant lifecycle

Mỗi participant trong một match có đúng một:

```text
ACTIVE
COMPLETED
FORFEITED
```

`presenceState` và `matchStatus` không được nhập làm một khái niệm.

Đặc biệt:

```text
DISCONNECTED != FORFEITED
```

cho tới khi reconnect grace period hết hạn hoặc người chơi chủ động Quit.

## 46.10. Trap lower bound

Trap không được tạo authoritative position:

```text
stepCount < 0
```

cho một quân đang ở trên bàn.

Nếu `currentStep - 2 < 0`, quân giữ nguyên tại ô Trap.

## 46.11. Session

Một account chỉ có tối đa một active session, trừ luồng reconnect thay thế connection cũ.

---

# 47. Thứ tự phát triển khuyến nghị

## Phase 1 — Đóng đặc tả

- luật game;
- model;
- protocol;
- enum;
- ErrorCode;
- Game State.

## Phase 2 — Common

- DTO;
- message;
- model;
- serialization.

## Phase 3 — Core Server

- TCP Server;
- Session;
- Room;
- basic Game Engine;
- thread safety.

## Phase 4 — Client Core

- connect;
- login;
- lobby;
- room;
- listener;
- Unity state update trên main thread.

## Phase 5 — Gameplay

- board;
- roll;
- move;
- capture;
- Finish Track;
- special cell;
- timer.

## Phase 6 — Persistence

- account;
- match history;
- score;
- ranking.

## Phase 7 — Robustness

- reconnect;
- heartbeat;
- malformed message;
- duplicate action;
- concurrent actions.

## Phase 8 — Extension

- chat;
- spectator;
- animation;
- sound;
- replay nếu có thời gian.

---

# 48. Các chức năng core bắt buộc

- Đăng ký.
- Đăng nhập.
- Đăng xuất.
- Danh sách người chơi online.
- Tạo phòng.
- Join phòng.
- Rời phòng.
- Mời người chơi.
- Accept/Reject.
- Host.
- 2–4 người.
- Start game.
- Tung xúc xắc.
- Ra quân.
- Di chuyển.
- Đá quân.
- Finish Track.
- Ô đặc biệt.
- Timeout.
- Đồng bộ Game State.
- Game Over.
- Xếp hạng.
- Lưu lịch sử.
- Cập nhật điểm.
- Xử lý thoát trận.
- Xử lý mất kết nối cơ bản.

---

# 49. Chức năng mở rộng

- Chat trong phòng.
- Spectator.
- Reconnect đầy đủ.
- Heartbeat.
- Animation.
- Âm thanh.
- Avatar.
- Room password.
- Friend/invite nâng cao.
- Replay.
- Matchmaking.
- Bot player.

Không ưu tiên extension trước khi core game ổn định.

---

# 50. Coding convention cho AI Agent

## 50.1. Java

- dùng Java 21;
- ưu tiên immutable DTO khi phù hợp;
- có thể dùng `record` cho message/DTO;
- dùng enum thay cho magic string;
- không dùng static global mutable state tràn lan;
- không swallow exception;
- không dùng `System.out.println` cho logging lâu dài;
- service/repository/network/game tách trách nhiệm.

## 50.1.1. Unity / C#

- Scene/prefab chứa giao diện; controller xử lý tương tác và render.
- `NetworkClient` chịu trách nhiệm TCP, framing và request correlation.
- `NetworkSession` giữ trạng thái nhận từ Server xuyên scene.
- Không tự sinh dice, tính nước đi, cộng điểm hoặc đổi lượt authoritative ở C#.
- Network event chỉ cập nhật Unity API trên main thread.
- Snapshot, presence và kết quả sau reconnect phải đồng bộ theo contract Server;
  xem các sai lệch hiện còn trong `INTEGRATION_REVIEW.md` trước khi sửa cơ chế version.

## 50.2. SOLID

Không ép SOLID một cách máy móc, nhưng phải đảm bảo:

- Game Rule không phụ thuộc Unity/UI;
- Repository không xử lý gameplay;
- Network layer không chứa toàn bộ luật;
- UI controller không query DB trực tiếp;
- SessionManager không tự xử lý ranking.

## 50.3. Naming

Tên class/method/code bằng tiếng Anh.

UI text có thể dùng tiếng Việt.

Ví dụ:

```java
GameRoom
GameState
Piece
Player
RoomManager
SessionManager
GameEngine
GameRule
MatchRepository
RankingService
```

---

# 51. Những điều AI Agent không được tự ý làm

Không tự ý:

- chuyển TCP thành HTTP/WebSocket;
- đưa Spring Boot vào Server;
- đổi MySQL sang DB khác;
- cho Client tự sinh xúc xắc;
- cho Client tự update authoritative state;
- thay luật Finish Track;
- thay luật Shield;
- thay hệ điểm;
- cho bonus roll cộng dồn;
- bỏ timeout hoặc đổi mốc phase `120s Roll / 120s Move` mà không cập nhật đặc tả;
- bỏ reconnect grace period;
- thay protocol framing hoặc bỏ `MAX_FRAME_LENGTH`;
- thay số lượng người chơi;
- thay số quân mỗi người;
- đổi 48 ô đường chung;
- đổi 6 nấc Finish Track;
- đổi quy ước `stepCount` / `START_INDEX` mà không migrate toàn bộ GameEngine + UI;
- đặt ô đặc biệt vào blacklist đã cấm;
- đổi số 6 là điều kiện ra quân.

Nếu cần đổi, phải cập nhật tài liệu này cùng code.

---

# 52. Tóm tắt luật game cuối cùng

| Vấn đề                     | Quy tắc                                                                        |
| -------------------------- | ------------------------------------------------------------------------------ |
| Người chơi                 | 2–4                                                                            |
| Quân mỗi người             | 4                                                                              |
| Ra quân                    | Phải roll 6                                                                    |
| Đi qua quân                | Được                                                                           |
| Dừng cùng quân mình        | Không                                                                          |
| Dừng trên đối thủ          | Đá                                                                             |
| Spawn gặp own piece        | Không được spawn                                                               |
| Spawn gặp opponent         | Đá                                                                             |
| Shield                     | Đỡ 1 lần, Shield mất, attacker không chiếm ô                                   |
| Speed                      | +2 nếu hợp lệ                                                                  |
| Slow                       | `max(0, dice - 2)` ở lần di chuyển tiếp theo của quân                          |
| Lucky                      | +1 bonus roll                                                                  |
| Trap                       | -2 nếu hợp lệ                                                                  |
| Trap lower bound           | `targetStep = currentStep - 2`; nếu `< 0` thì giữ nguyên ở Trap, không về Yard |
| Chain special effect       | Không                                                                          |
| Roll 6                     | Bonus sau khi hoàn thành move hợp lệ                                           |
| Roll 6 nhưng không có move | Không bonus, end turn                                                          |
| Roll 6 + Lucky             | Chỉ 1 bonus                                                                    |
| 6 liên tiếp                | Cho phép                                                                       |
| First Turn                 | occupied `slotIndex` nhỏ nhất; thường là Red / Slot 0                          |
| Turn Rotation              | `0→1→2→3→0`, chỉ chọn `MatchParticipantStatus.ACTIVE`                          |
| Disconnected turn          | `DISCONNECTED + ACTIVE` vẫn nhận lượt và vẫn timeout                           |
| Completed/Forfeited turn   | luôn skip                                                                      |
| Timeout                    | `WAITING_FOR_ROLL = 120s`, `WAITING_FOR_MOVE = 120s`, Server quản lý              |
| Bonus action               | Mở chu kỳ phase mới `120s Roll / 120s Move`                                       |
| Disconnect                 | Grace period 60 giây                                                           |
| Quit                       | Forfeit ngay                                                                   |
| Host leave                 | Chuyển Host                                                                    |
| Ring                       | 48 ô global `0..47`                                                            |
| Start Index                | Red `0`, Blue `12`, Yellow `24`, Green `36`                                    |
| Piece progress             | `stepCount=-1` ở Yard, `0..47` Ring, `48..53` Finish                           |
| Finish Track               | 6 nấc, slot = `stepCount - 47`                                                 |
| Carry-over                 | Cho phép bước dư từ Ring vào Finish Track                                      |
| Vượt nấc 6                 | `targetStep > 53` → Invalid                                                    |
| Đạt đúng nấc 6             | `stepCount = 53` → FINISHED                                                    |
| Win/complete               | 4 quân FINISHED                                                                |
| Game Authority             | Server                                                                         |
| Transport                  | TCP                                                                            |
| Encoding                   | UTF-8                                                                          |
| Payload                    | JSON, envelope `MessageEnvelope` + `JsonNode data`, payload DTO typed          |
| Framing                    | 4-byte length prefix                                                           |
| Max frame                  | 64 KB (`65,536` bytes)                                                         |
| Special-cell blacklist     | `0,12,24,36,47,11,23,35` + toàn bộ Finish Track                                |
| Concurrency                | Lock theo Room                                                                 |

---

# 53. Checklist trước khi merge feature

AI agent hoặc developer phải tự kiểm:

- [ ] Có làm thay đổi protocol không?
- [ ] Có làm thay đổi Game Rule không?
- [ ] Có làm Client thành authoritative không?
- [ ] Có race condition với Room/Game State không?
- [ ] Có block Unity main thread không?
- [ ] Có xử lý malformed network message không?
- [ ] Có validate đúng session/turn/room không?
- [ ] Có test edge case không?
- [ ] Có log lỗi đủ để debug không?
- [ ] Có làm lộ password/token không?
- [ ] Có làm vỡ reconnect không?
- [ ] Có làm sai ranking/score không?
- [ ] Turn Rotation có vô tình chọn `COMPLETED`/`FORFEITED` không?
- [ ] Có vô tình skip `DISCONNECTED + ACTIVE` không?
- [ ] Trap có thể tạo `stepCount < 0` không?
- [ ] First Turn có đúng occupied slot nhỏ nhất không?
- [ ] Có cần cập nhật `AGENTS.md` không?

---

# 54. Quy tắc khi AI Agent gặp phần chưa được đặc tả

Nếu gặp tình huống chưa có trong tài liệu:

1. Không tự âm thầm tạo luật mới.
2. Xác định phần còn mơ hồ.
3. Đưa ra 1–3 phương án.
4. Ưu tiên phương án:
   - dễ hiểu;
   - dễ kiểm thử;
   - ít edge case;
   - giữ Server authoritative;
   - không phá luật hiện tại.
5. Chỉ code sau khi quyết định đã rõ.
6. Nếu đã chốt quyết định mới, cập nhật `AGENTS.md`.

---

# 55. Nguồn đặc tả

Tài liệu này tổng hợp:

- nội dung báo cáo sơ bộ của dự án Game Cờ Cá Ngựa;
- các quyết định thiết kế đã thống nhất thêm trong quá trình phân tích;
- các quy tắc bổ sung nhằm loại bỏ tình huống mơ hồ khi triển khai Client–Server và Game Engine.

`AGENTS.md` được dùng như đặc tả làm việc cho AI agent, không thay thế báo cáo học thuật cuối kỳ. Báo cáo cuối có thể trình bày lại dưới dạng formal hơn với Use Case, ERD, Class Diagram, Sequence Diagram, Deployment Diagram và kết quả kiểm thử.

## 55.1. Quyết định canonical mới nhất

Các quyết định sau là bản chính:

- Trap không được lùi quân về Yard; `targetStep < 0` → giữ nguyên tại Trap.
- Người đi đầu là occupied `slotIndex` nhỏ nhất; thường là Red / Slot 0.
- Turn Rotation chạy theo `0 → 1 → 2 → 3 → 0`.
- Eligibility của lượt dựa trên `MatchParticipantStatus.ACTIVE`, không dựa riêng vào trạng thái kết nối.
- `DISCONNECTED + ACTIVE` vẫn nhận lượt trong grace period và có thể timeout.
- `COMPLETED` và `FORFEITED` luôn bị bỏ qua.
- Trước `nextTurn()`, Server luôn gọi `evaluateGameEnd()`.
- Trạng thái hiện diện/kết nối và trạng thái participant trong match là hai khái niệm riêng.
- Lời mời phòng hết hạn sau đúng 60 giây theo đồng hồ Server; mỗi người nhận chỉ giữ lời mời mới nhất.
- Mọi thành viên phòng, kể cả chủ phòng, phải Ready trước khi chủ phòng được Start Game.

## 55.2. Client chính và trạng thái kiểm chứng

Từ đợt rà soát ngày 2026-09-22, tài liệu dùng **Unity C# trong `client-unity/`**
làm client chính; JavaFX trong `client/` chỉ là legacy. Backend vẫn là Java 21,
TCP length-prefixed UTF-8 JSON và MySQL. Luật game trong tài liệu này giữ nguyên.

Các yêu cầu là đặc tả, không mặc nhiên có nghĩa mọi luồng đã được triển khai hoàn chỉnh.
Đối chiếu `INTEGRATION_REVIEW.md` để biết kết quả kiểm tra và các giới hạn kiểm thử.

### Contract bổ sung sau sửa lỗi tích hợp

- `RECONNECT_RESULT.data.gameOver` là field tùy chọn, chứa `GameOverDto` của đúng
  room/match đã kết thúc; null/không có khi chưa có kết quả. Room/game/gameOver
  được chụp dưới cùng lock phòng. Constructor Java bốn tham số vẫn được giữ.
- Unity khôi phục kết quả từ snapshot này nếu bỏ lỡ event GAME_OVER. Client cũ
  có thể bỏ qua field bổ sung; tên message và framing không đổi.
- Presence được cập nhật từ ROOM_UPDATED độc lập với gameplay stateVersion.
  Client vẫn loại snapshot gameplay cũ/trùng, không tự tăng version.
- ONLINE_PLAYERS_UPDATED và response refresh cập nhật profile của chính tài
  khoản theo playerId; điểm/header Lobby render lại từ các giá trị Server gửi.
- Chat escape cả tên người gửi và nội dung trước khi ghép rich text do UI tạo.
