# Tài liệu hệ thống — Game Cờ Cá Ngựa

Tài liệu mô tả kiến trúc, chức năng, giao thức TCP, mô hình dữ liệu và luật của hệ thống Ludo Game. Đây là đặc tả thống nhất giữa Java Server và Unity Client. Hướng dẫn cài đặt, chạy và kiểm thử nằm trong [RUN.md](RUN.md).

## Mục lục

1. [Thông tin và phạm vi](#1-thông-tin-và-phạm-vi)
2. [Kiến trúc và công nghệ](#2-kiến-trúc-và-công-nghệ)
3. [Mô hình trạng thái](#3-mô-hình-trạng-thái)
4. [Tài khoản và sảnh](#4-tài-khoản-và-sảnh)
5. [Quản lý phòng](#5-quản-lý-phòng)
6. [Giao thức TCP](#6-giao-thức-tcp)
7. [Kết nối và khôi phục phiên](#7-kết-nối-và-khôi-phục-phiên)
8. [Bàn cờ và tọa độ](#8-bàn-cờ-và-tọa-độ)
9. [Luật di chuyển](#9-luật-di-chuyển)
10. [Ô đặc biệt](#10-ô-đặc-biệt)
11. [Lượt chơi và thời gian](#11-lượt-chơi-và-thời-gian)
12. [Kết thúc trận và tính điểm](#12-kết-thúc-trận-và-tính-điểm)
13. [Chơi lại và chat](#13-chơi-lại-và-chat)
14. [Đồng bộ và trình diễn trên client](#14-đồng-bộ-và-trình-diễn-trên-client)
15. [Cơ sở dữ liệu](#15-cơ-sở-dữ-liệu)
16. [Đồng thời, bảo mật và xử lý lỗi](#16-đồng-thời-bảo-mật-và-xử-lý-lỗi)
17. [Kiểm thử và các bất biến](#17-kiểm-thử-và-các-bất-biến)

## 1. Thông tin và phạm vi

- **Môn học:** Lập trình mạng.
- **Đề tài:** Xây dựng game Cờ Cá Ngựa.
- **Lớp:** D23CTPM01 — nhóm lớp CT01 — nhóm BTL 1.
- **Năm:** 2026.

| Thành viên      | Mã sinh viên | Phạm vi phụ trách                                     |
| --------------- | ------------ | ----------------------------------------------------- |
| Hoàng Đình Duy  | B23DCCN237   | Tài khoản, cơ sở dữ liệu, điểm, lịch sử và xếp hạng   |
| Trần Trọng Hùng | B23DCCN363   | TCP, protocol, session, đồng bộ, timeout và reconnect |
| Đặng Phi Long   | B23DCCN497   | Sảnh, phòng, lời mời, chủ phòng và trạng thái phòng   |
| Tạ Thanh Thiên  | B23DCCN783   | Bàn cờ, quân cờ, xúc xắc, lượt chơi và luật game      |

Hệ thống phục vụ từ 2 đến 4 người chơi trong một phòng, mỗi người có 4 quân. Chức năng gồm đăng ký, đăng nhập, sảnh trực tuyến, quản lý phòng, lời mời, Ready, thi đấu, chat trong phòng, khôi phục kết nối, lưu kết quả, lịch sử trận và bảng xếp hạng.

## 2. Kiến trúc và công nghệ

```text
Unity Client 1 ─┐
Unity Client 2 ─┼── TCP + length-prefixed UTF-8 JSON ── Java Game Server
Unity Client N ─┘                                           │
                                                   JDBC + HikariCP
                                                           │
                                                        MySQL 8
```

| Thành phần             | Công nghệ                                        |
| ---------------------- | ------------------------------------------------ |
| Server                 | Java 21, Maven, Socket/ServerSocket, thread pool |
| Client                 | Unity 6, C#, uGUI / Canvas, TextMeshPro          |
| JSON                   | Jackson phía Java; Newtonsoft.Json phía Unity    |
| Lưu trữ                | MySQL 8, JDBC, HikariCP, Flyway                  |
| Bảo mật mật khẩu       | BCrypt                                           |
| Logging                | SLF4J, Logback                                   |
| Kiểm thử Java          | JUnit 5, Mockito                                 |
| Quản lý mã nguồn và CI | Git, GitHub Actions                              |

Server là nguồn dữ liệu chính thức duy nhất: sinh xúc xắc, kiểm tra nước đi, cập nhật quân, giải quyết hiệu ứng, quản lý lượt, timeout, xếp hạng và lưu kết quả. Client gửi ý định như `ROLL_DICE` hoặc `MOVE_PIECE`, rồi hiển thị trạng thái Server trả về. Client không tự quyết định kết quả xúc xắc, nước đi hợp lệ, điểm hoặc thắng thua.

Game State đang diễn ra được giữ trong RAM Server. MySQL lưu tài khoản, điểm, thành tích và lịch sử; không ghi lại trạng thái sau từng bước di chuyển.

```text
ludo-game/
├── README.md                 # Giới thiệu dự án
├── SYSTEM.md                 # Đặc tả hệ thống
├── RUN.md                    # Cài đặt, chạy và kiểm thử
├── pom.xml                   # Maven: common, server
├── compose.yaml              # MySQL
├── ludo.ps1                  # Các lệnh chạy bằng PowerShell
├── common/src/               # DTO, enum, framing và test Java
├── server/src/               # Server, migration và test Java
└── client/                   # Dự án Unity
    ├── Assets/               # Scene, prefab, script, hình ảnh và âm thanh
    ├── Packages/             # Dependency Unity
    ├── ProjectSettings/      # Cấu hình và phiên bản Editor
    └── README.md             # Hướng dẫn client
```

`common` định nghĩa contract Java. Unity ánh xạ cùng JSON contract bằng C#, không import JAR. Mọi thay đổi field, kiểu dữ liệu hay enum trên wire phải được kiểm tra ở cả hai phía. Network layer ánh xạ `JsonNode data` sang DTO trước khi gọi nghiệp vụ; Game Engine không phụ thuộc JSON, socket hoặc UI.

Maven chỉ build `common` và `server`. Unity build riêng bằng phiên bản Editor ghi trong [ProjectVersion.txt](client/ProjectSettings/ProjectVersion.txt). Server dùng TCP trực tiếp, không sử dụng Spring Boot, Netty hay WebSocket cho gameplay.

## 3. Mô hình trạng thái

Trạng thái hiện diện và trạng thái thi đấu là hai khái niệm độc lập.

| Enum                     | Giá trị                                                               | Ý nghĩa                                     |
| ------------------------ | --------------------------------------------------------------------- | ------------------------------------------- |
| `PlayerPresenceState`    | `OFFLINE`, `IDLE`, `IN_ROOM`, `PLAYING`, `SPECTATING`, `DISCONNECTED` | Vị trí và tình trạng kết nối của người chơi |
| `MatchParticipantStatus` | `ACTIVE`, `COMPLETED`, `FORFEITED`                                    | Vòng đời participant trong một trận         |
| `RoomState`              | `WAITING`, `PLAYING`, `FINISHED`                                      | Vòng đời phòng                              |
| `TurnState`              | `WAITING_FOR_ROLL`, `WAITING_FOR_MOVE`, `PROCESSING`, `FINISHED`      | Giai đoạn xử lý lượt                        |
| `PieceState`             | `IN_YARD`, `ON_TRACK`, `IN_FINISH_TRACK`, `FINISHED`                  | Trạng thái quân cờ                          |

`OFFLINE` là chưa đăng nhập/đã đăng xuất; `IDLE` là ở sảnh; `IN_ROOM` là ở phòng chờ; `PLAYING` là đang kết nối trong trận; `DISCONNECTED` là mất kết nối tạm thời.

Người mất mạng trong grace period có `presenceState = DISCONNECTED` nhưng vẫn `matchStatus = ACTIVE`. Họ còn trong vòng quay lượt. Khi quá hạn khôi phục, participant chuyển `FORFEITED`. Người hoàn thành 4 quân chuyển `COMPLETED`; không có `PlayerPresenceState.FINISHED`.

## 4. Tài khoản và sảnh

### 4.1. Đăng ký và đăng nhập

Đăng ký nhận `username`, `password`, `displayName`. Server validate dữ liệu, bảo đảm username duy nhất, hash mật khẩu bằng BCrypt rồi lưu `password_hash`. Client có thể kiểm tra xác nhận mật khẩu, nhưng Server quyết định đăng ký thành công.

Đăng nhập kiểm tra tài khoản, mật khẩu và phiên đang hoạt động. Mỗi tài khoản chỉ có một active session. Đăng nhập trùng trả `ACCOUNT_ALREADY_LOGGED_IN`; reconnect hợp lệ được phép thay thế connection của phiên cũ.

Đăng nhập thành công tạo session và chuyển người chơi sang `IDLE`. Đăng xuất đóng phiên, dọn kết nối và loại người chơi khỏi danh sách online.

### 4.2. Sảnh và hồ sơ

Sảnh hiển thị tên, tổng điểm, số lần hạng nhất và trạng thái của người chơi online. `GET_ONLINE_PLAYERS` lấy danh sách; `ONLINE_PLAYERS_UPDATED` cập nhật khi kết nối hoặc presence thay đổi.

Client cập nhật hồ sơ của chính mình theo `playerId` từ dữ liệu Server, bao gồm điểm và số lần hạng nhất. `GET_RANKING` lấy bảng xếp hạng; `GET_MATCH_HISTORY` lấy tối đa 50 trận gần nhất của tài khoản đã đăng nhập.

## 5. Quản lý phòng

### 5.1. Thành viên, slot và chủ phòng

Người `IDLE` có thể tạo hoặc tham gia phòng `WAITING`, sau đó chuyển `IN_ROOM`. Phòng tối đa 4 người; trận cần ít nhất 2 người.

| Slot | Màu      | Chỉ số xuất phát |
| ---- | -------- | ---------------- |
| 0    | `RED`    | 0                |
| 1    | `BLUE`   | 12               |
| 2    | `YELLOW` | 24               |
| 3    | `GREEN`  | 36               |

Server cấp slot/màu. Slot được giải phóng khi thành viên rời phòng chờ và có thể cấp lại. Client không tự chọn màu.

Người tạo phòng là chủ phòng. Chủ phòng được mời người chơi và bắt đầu trận khi đủ điều kiện. Nếu chủ phòng rời, quyền chuyển cho người tham gia sớm nhất còn lại; nếu không còn thành viên thì xóa phòng. Chủ phòng không phải Server, nên rời giữa trận không làm sập trận đấu.

### 5.2. Lời mời

- Chỉ chủ phòng mời người đang `IDLE`.
- Mỗi người nhận có tối đa một lời mời chờ; lời mời mới thay thế lời mời cũ.
- Lời mời có hiệu lực 60 giây theo đồng hồ Server.
- Chỉ người nhận được Accept/Reject; cả hai hành động đều tiêu thụ lời mời.
- Accept phải kiểm tra lại người nhận còn `IDLE`, phòng còn `WAITING` và còn chỗ.
- Thay đổi thành viên hoặc bắt đầu trận hủy các lời mời còn lại của phòng.

### 5.3. Bắt đầu trận

Chủ phòng chỉ được Start khi phòng `WAITING`, có ít nhất 2 người, tất cả thành viên đang kết nối và `IN_ROOM`, đồng thời tất cả đã Ready, kể cả chủ phòng.

Server khởi tạo 4 quân `IN_YARD` cho mỗi participant, cấp `matchId`, chuyển phòng sang `PLAYING`, chuyển người chơi sang `PLAYING`, hủy lời mời và chọn occupied `slotIndex` nhỏ nhất đi đầu. Không chọn ngẫu nhiên người bắt đầu; nếu slot 0 trống thì chọn slot có người nhỏ nhất tiếp theo.

Phase đầu là `WAITING_FOR_ROLL` với deadline Server. Server broadcast `ROOM_UPDATED` và `GAME_STATE` cho các thành viên.

## 6. Giao thức TCP

### 6.1. Framing

Kết nối TCP được giữ lâu dài. Mỗi message là một frame:

```text
[4 byte độ dài, big-endian][JSON UTF-8]
```

`MAX_FRAME_LENGTH = 64 * 1024 = 65.536 byte`. Độ dài tính theo byte UTF-8, không theo số ký tự. Cả đọc và ghi đều kiểm tra `0 < length <= MAX_FRAME_LENGTH` trước khi cấp phát hoặc gửi payload.

TCP là byte stream: một lần ghi không tương ứng với một lần đọc. Reader phải đọc đủ prefix và payload, xử lý được frame phân mảnh và nhiều frame liên tiếp. Độ dài sai, EOF giữa frame hoặc JSON lỗi được xử lý trong phạm vi connection; đóng connection vi phạm và ghi log, không làm dừng Server.

### 6.2. Envelope

Java dùng `MessageEnvelope` với `JsonNode data`; payload nghiệp vụ được map sang DTO typed. Unity đọc/ghi cùng cấu trúc bằng `JObject`/`JArray`.

| Field       | Quy ước                                                               |
| ----------- | --------------------------------------------------------------------- |
| `type`      | Bắt buộc, thuộc `MessageType`                                         |
| `requestId` | Bắt buộc với request/response; event thuần có thể không có            |
| `sessionId` | Dùng cho request đã xác thực; có thể không có với `REGISTER`/`LOGIN`  |
| `success`   | `true`/`false` trong response; không có hoặc null trong request/event |
| `data`      | Payload theo loại message                                             |
| `error`     | `code` và `message` khi request thất bại                              |

Ví dụ request:

```json
{
  "type": "MOVE_PIECE",
  "requestId": "request-uuid",
  "sessionId": "session-id",
  "data": { "roomId": "room-id", "pieceId": "piece-id" }
}
```

Ví dụ lỗi:

```json
{
  "type": "ERROR",
  "requestId": "request-uuid",
  "success": false,
  "error": { "code": "NOT_YOUR_TURN", "message": "Chưa đến lượt của bạn" }
}
```

Request đi từ Client tới Server. Response trả về connection đã gửi request, giữ `requestId`. Event được Server chủ động gửi cho các client liên quan. Request ID đồng thời phục vụ chống thực thi lại action trong cửa sổ retry trên cùng connection.

### 6.3. MessageType

Bộ enum được định nghĩa trong [MessageType.java](common/src/main/java/vn/ptit/ltm/common/enums/MessageType.java):

| Nhóm              | MessageType                                                                                                                                                    |
| ----------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Tài khoản         | `REGISTER`, `LOGIN`, `LOGOUT`, `GET_PROFILE`, `PROFILE_RESULT`                                                                                                 |
| Xếp hạng, lịch sử | `GET_RANKING`, `RANKING_RESULT`, `GET_MATCH_HISTORY`, `MATCH_HISTORY_RESULT`                                                                                   |
| Sảnh              | `GET_ONLINE_PLAYERS`, `ONLINE_PLAYERS_UPDATED`                                                                                                                 |
| Phòng             | `CREATE_ROOM`, `JOIN_ROOM`, `LEAVE_ROOM`, `INVITE_PLAYER`, `ACCEPT_INVITE`, `REJECT_INVITE`, `READY`, `UNREADY`, `START_GAME`, `ROOM_UPDATED`                  |
| Gameplay          | `ROLL_DICE`, `DICE_RESULT`, `MOVE_PIECE`, `MOVE_PIECE_RESULT`, `GAME_STATE`, `GAME_STATE_UPDATED`, `TURN_STARTED`, `TURN_CHANGED`, `TURN_TIMEOUT`, `GAME_OVER` |
| Chat              | `CHAT_MESSAGE`                                                                                                                                                 |
| Kết nối           | `PING`, `PONG`, `RECONNECT`, `RECONNECT_RESULT`                                                                                                                |
| Lỗi               | `ERROR`                                                                                                                                                        |

Tên enum không thay thế định nghĩa payload. Contract đầy đủ của các payload nằm trong [common/dto](common/src/main/java/vn/ptit/ltm/common/dto); một enum được khai báo không đồng nghĩa mọi luồng đều phát riêng message đó.

### 6.4. Mã lỗi

[ErrorCode.java](common/src/main/java/vn/ptit/ltm/common/error/ErrorCode.java) định nghĩa:

```text
INVALID_REQUEST, INVALID_MESSAGE, UNAUTHORIZED, SESSION_EXPIRED,
ACCOUNT_ALREADY_LOGGED_IN, USERNAME_ALREADY_EXISTS, INVALID_CREDENTIALS,
ROOM_NOT_FOUND, ROOM_FULL, ALREADY_IN_ROOM, NOT_IN_ROOM, NOT_ROOM_HOST,
PLAYER_NOT_IDLE, INVITATION_NOT_FOUND, INVITATION_EXPIRED,
NOT_ENOUGH_PLAYERS, PLAYER_NOT_READY, GAME_NOT_STARTED,
GAME_ALREADY_STARTED, NOT_YOUR_TURN, INVALID_GAME_STATE, INVALID_MOVE,
NO_VALID_MOVE, PIECE_NOT_FOUND, PIECE_ALREADY_FINISHED,
ROLL_NOT_ALLOWED, MOVE_NOT_ALLOWED, TURN_TIMEOUT,
SPECTATOR_NOT_ALLOWED, INTERNAL_SERVER_ERROR
```

Client nhận lỗi nghiệp vụ có mã và thông báo phù hợp; không nhận exception Java hoặc stack trace thô.

## 7. Kết nối và khôi phục phiên

Server gửi `PING` mỗi 10 giây, Client trả `PONG`. Mất 3 heartbeat liên tiếp dẫn tới xử lý mất kết nối.

Khi mất kết nối, presence chuyển `DISCONNECTED`. Server giữ phiên trong **60 giây**. Trận vẫn tiếp tục; participant `ACTIVE` vẫn có lượt và deadline vẫn chạy. Hết deadline phase thì bỏ lượt như bình thường.

Reconnect hợp lệ xác thực session, gắn connection mới, khôi phục presence và trả snapshot phòng/trận. `RECONNECT_RESULT.data` gồm `restored`, `presenceState`, `room`, `gameState` và `gameOver` tùy chọn. Các snapshot được chụp dưới cùng lock phòng. Nếu có kết quả, `gameOver` phải thuộc đúng `roomId` và `matchId`, giúp client khôi phục màn kết quả dù bỏ lỡ `GAME_OVER`.

Quá grace period, participant chuyển `FORFEITED`, quân bị loại, nhận 0 điểm và Server kiểm tra điều kiện kết thúc trận. Nhấn Thoát trong trận là Quit chủ động: forfeit ngay, không có grace period. Client hiển thị xác nhận trước khi gửi yêu cầu bỏ cuộc.

## 8. Bàn cờ và tọa độ

Vòng chung có đúng **48 ô**, chỉ số toàn cục `0..47`. Mỗi màu có Finish Track riêng gồm 6 nấc. Nguồn dữ liệu vị trí là tiến độ tương đối `stepCount`.

| `stepCount` | Trạng thái                       |
| ----------- | -------------------------------- |
| -1          | `IN_YARD`                        |
| 0..47       | `ON_TRACK`; 0 là ô xuất phát     |
| 48..52      | `IN_FINISH_TRACK`; nấc 1..5      |
| 53          | Nấc 6; sau resolve là `FINISHED` |

Giá trị lớn nhất hợp lệ là **53**, không phải 54.

```text
globalCell = (START_INDEX + stepCount) mod 48    khi 0 <= stepCount <= 47
finishTrackSlot = stepCount - 47               khi 48 <= stepCount <= 53
entryCell = (START_INDEX + 47) mod 48
```

| Màu      | `START_INDEX` | Ô cuối vòng chung trước đích |
| -------- | ------------- | ---------------------------- |
| `RED`    | 0             | 47                           |
| `BLUE`   | 12            | 11                           |
| `YELLOW` | 24            | 23                           |
| `GREEN`  | 36            | 35                           |

`PieceDto` gửi `pieceId`, `ownerPlayerId`, `color`, `state`, `stepCount`. Client dùng công thức trên để ánh xạ sang tọa độ hiển thị; không tự tính tiến độ chính thức. Khi quân bị đá hoặc gặp bẫy, Server đặt `state = IN_YARD`, `stepCount = -1`.

## 9. Luật di chuyển

### 9.1. Xúc xắc và ra quân

Chỉ Server sinh xúc xắc trong `[1, 6]`. Client không gửi giá trị xúc xắc.

Phải đổ 6 để đưa quân từ bãi ra ô xuất phát. Người chơi có thể chọn ra quân hoặc đi quân đang trên bàn nếu nước đi hợp lệ. Ô xuất phát trống thì được ra; có quân mình thì bị chặn; có quân đối phương thì đá quân đó về bãi.

### 9.2. Vòng chung và đá quân

Quân đi đúng số bước xúc xắc trước khi xử lý ô đặc biệt. Được đi xuyên qua quân khác; chỉ kiểm tra ô kết thúc. Dừng trên quân mình là không hợp lệ. Dừng trên quân đối phương thì đá đối phương về bãi, chiếm ô đó rồi xử lý hiệu ứng nếu có.

### 9.3. Finish Track

Bước dư được đưa thẳng từ vòng chung vào Finish Track, không bắt buộc dừng tại cửa đích. Ví dụ `stepCount = 46`, xúc xắc 4 đưa quân tới `stepCount = 50`, tức nấc 3.

Nước đi có đích lớn hơn 53 không hợp lệ. Có thể đi xuyên qua quân mình trong Finish Track nhưng không dừng trên quân mình ở nấc 1..5. Quân đến đúng nấc 6 lập tức `FINISHED`, không còn chiếm occupancy và không chặn quân sau. Quân hoàn thành không được đi lại. Không có quân đối phương trong Finish Track riêng.

Hàm xác định nước đi hợp lệ và hàm áp dụng nước đi phải dùng cùng mô hình tọa độ và kiểm tra đích.

## 10. Ô đặc biệt

Server khởi tạo 16 ô đặc biệt và gửi toàn bộ trong `GameState.specialCells`. Client render danh sách Server gửi.

| Loại trên wire | Chỉ số toàn cục | Hiệu ứng            |
| -------------- | --------------- | ------------------- |
| `SPEED`        | 2, 14, 26, 38   | Tiến thêm 3 bước    |
| `SLOW`         | 4, 16, 28, 40   | Lùi ngay 1 bước     |
| `LUCKY`        | 6, 18, 30, 42   | Thưởng một lần tung |
| `TRAP`         | 8, 20, 32, 44   | Về bãi ngay         |

Các ô 10, 22, 34, 46 là ô thường. Không có Khiên hoặc hiệu ứng kéo dài sang lượt sau.

Ô đặc biệt chỉ kích hoạt khi kết thúc nước đi, không kích hoạt khi đi ngang. Mỗi nước đi kích hoạt tối đa một ô; đích do hiệu ứng tạo ra không kích hoạt tiếp hiệu ứng khác.

- **SPEED:** cộng 3 vào `stepCount` nếu đích hợp lệ, kể cả đi vào Finish Track. Nếu vượt 53 hoặc đích có quân mình thì giữ nguyên tại ô SPEED; nước đi gốc vẫn thành công.
- **SLOW:** trừ ngay 1 khỏi `stepCount`. Nếu đích âm hoặc có quân mình thì giữ nguyên tại ô SLOW. Không giảm xúc xắc ở lượt sau.
- **SPEED/SLOW:** nếu đích hiệu ứng có đối phương trên vòng chung thì đá đối phương.
- **LUCKY:** cấp một bonus roll sau khi nước đi hoàn thành, không cộng dồn với bonus do xúc xắc 6.
- **TRAP:** resolve capture ở ô bẫy trước, sau đó đưa quân vừa đi về bãi. Nước đi vẫn thành công; nếu xúc xắc là 6 thì vẫn xét bonus roll.

Server validate mọi chỉ số ô đặc biệt trong `[0, 47]`, loại trừ spawn `0, 12, 24, 36` và cửa đích `47, 11, 23, 35`. Finish Track không bao giờ có ô đặc biệt.

## 11. Lượt chơi và thời gian

Luồng một lượt:

1. Server mở `WAITING_FOR_ROLL`; người đang có lượt gửi `ROLL_DICE`.
2. Server sinh xúc xắc, tính `validPieceIds` và gửi kết quả.
3. Nếu không có nước đi, kết thúc lượt ngay, kể cả khi đổ 6.
4. Nếu có nước đi, mở `WAITING_FOR_MOVE`; người chơi gửi `MOVE_PIECE`.
5. Server validate, di chuyển, đá quân, xử lý hiệu ứng và cập nhật trạng thái.
6. Server xét hoàn thành/bỏ cuộc và kết thúc trận trước khi cấp bonus hoặc chuyển lượt.
7. Broadcast trạng thái mới.

### 11.1. Thời gian

| Phase              | Thời hạn                              |
| ------------------ | ------------------------------------- |
| `WAITING_FOR_ROLL` | 120 giây                              |
| `WAITING_FOR_MOVE` | 120 giây kể từ khi phase Move bắt đầu |

Một chu kỳ Roll/Move bình thường tối đa 240 giây. Roll muộn không làm giảm thời gian Move. Nếu không có nước đi hợp lệ thì không mở phase Move.

Server quyết định deadline và thời điểm xử lý request; request đến sau hạn bị từ chối theo phase/trạng thái. Timeout bỏ lượt, phát `TURN_TIMEOUT` và cập nhật Game State. Client chỉ hiển thị countdown dựa trên `phaseDurationMillis` và `serverDeadlineEpochMillis`.

### 11.2. Bonus roll

Đổ 6 chỉ được tung thêm sau khi hoàn thành một nước đi hợp lệ. Đổ 6 nhưng không có nước đi không được bonus. Dice 6 và LUCKY cùng lúc chỉ cho một bonus, không phải hai. Không áp dụng mất lượt sau ba lần 6.

Bonus mở chu kỳ Roll/Move mới với thời hạn 120/120 giây; vì vậy tổng thời gian giữ lượt có thể vượt 240 giây khi liên tục có bonus. Participant đã hoàn thành hoặc trận đã kết thúc không nhận lượt mới.

### 11.3. Thứ tự lượt

Vòng quay cố định `0 → 1 → 2 → 3 → 0`, bỏ slot trống. Điều kiện được nhận lượt là `matchStatus = ACTIVE`, không phụ thuộc đang kết nối hay `DISCONNECTED` trong grace period. `COMPLETED` và `FORFEITED` luôn bị bỏ qua.

Sau hoàn thành quân/người chơi, Quit hoặc hết grace period, Server chạy kiểm tra kết thúc trận trước `nextTurn()`. Không tạo thêm lượt khi chỉ còn một participant ACTIVE.

## 12. Kết thúc trận và tính điểm

Hoàn thành đủ 4 quân chuyển participant sang `COMPLETED` và gán hạng tốt nhất chưa có chủ. Những người ACTIVE khác tiếp tục thi đấu.

Forfeit nhận hạng thấp nhất chưa có chủ tại thời điểm bị loại, luôn 0 điểm và được ghi nhận bỏ cuộc. Quân bị loại khỏi bàn.

Khi chỉ còn một participant ACTIVE, kết thúc trận ngay; người đó nhận hạng tốt nhất còn trống và điểm tương ứng, không phải chơi một mình cho đủ 4 quân.

| Số người | Hạng 1 | Hạng 2 | Hạng 3 | Hạng 4 |
| -------- | ------ | ------ | ------ | ------ |
| 2        | 3      | 0      | —      | —      |
| 3        | 3      | 1,5    | 0      | —      |
| 4        | 3      | 1,5    | 0,5    | 0      |

Bảng điểm áp dụng cho người không forfeit. Ví dụ phòng 2 người, B bỏ cuộc thì B hạng 2/0 điểm, A hạng 1/3 điểm và trận kết thúc. Trong phòng 4 người, D rồi C bỏ cuộc lần lượt nhận hạng 4 và 3; nếu B tiếp tục bỏ thì B hạng 2/0 điểm, A hạng 1/3 điểm.

Mỗi participant chỉ nhận một hạng. Server lưu kết quả và cập nhật tổng điểm, số lần hạng nhất trong transaction, rồi gửi `GAME_OVER` với tổng điểm đọc lại từ database. Điểm lưu bằng `DECIMAL(10,1)`, không dùng số thực nhị phân để cộng điểm tích lũy.

## 13. Chơi lại và chat

### 13.1. Chơi lại cùng phòng

Nút **Chơi tiếp** trong Result gửi `READY` với `roomId` và `ready = true`. Phòng FINISHED chỉ được mở lại WAITING sau khi lưu kết quả thành công. Nếu lưu thất bại, giữ trận và trả lỗi để có thể thử lại.

Khi mở lại, Server loại membership đã rời, giữ `roomId`, slot, màu và chủ phòng của thành viên còn lại; reset Ready rồi đặt Ready cho người yêu cầu. Thành viên đang kết nối chuyển `IN_ROOM`; người mất kết nối vẫn `DISCONNECTED`, với trạng thái cần khôi phục là `IN_ROOM`.

`ROOM_UPDATED` đưa client về Room và xóa cache trận cũ. Snapshot đến trễ của match đã đóng không được khôi phục trận cũ. Chủ phòng Start khi ít nhất 2 người và mọi người đã Ready/đang kết nối. Ván mới có `matchId`, timer và 4 quân trong bãi mới; điểm tài khoản được giữ.

Nút về sảnh gửi `LEAVE_ROOM` và chờ Server xác nhận. Thành viên đã hoàn thành hoặc ở phòng FINISHED có thể rời; Server chuyển chủ phòng hoặc xóa phòng rỗng.

### 13.2. Chat

Client gửi `CHAT_MESSAGE`; Server xác thực membership và chuyển nội dung tới các thành viên cùng phòng. Chat không thay đổi luật hoặc Game State. Unity escape cả tên người gửi và nội dung trước khi đưa vào rich text, tránh diễn giải nội dung người dùng thành markup.

## 14. Đồng bộ và trình diễn trên client

### 14.1. Network và vòng đời Unity

`NetworkClient` dùng `TcpClient`, I/O bất đồng bộ và `SemaphoreSlim` để serialize writes. Network layer không gọi Unity API. Không block Unity main thread bằng `.Wait()`, `.Result` hoặc I/O đồng bộ.

`NetworkSession` dùng `DontDestroyOnLoad`; event từ reader được đưa vào `ConcurrentQueue<Action>` và xử lý trong `Update()`. Controller hủy đăng ký event khi bị hủy, kiểm tra vòng đời scene sau `await`. Session/token chỉ giữ trong RAM, không lưu vào PlayerPrefs hoặc log.

### 14.2. Snapshot

`GameStateDto` gồm `roomId`, `matchId`, `roomState`, `currentPlayerId`, `currentSlot`, `turnState`, `diceValue`, `validPieceIds`, `phaseDurationMillis`, `serverDeadlineEpochMillis`, `participants`, `specialCells`, `stateVersion` và `lastMove` tùy chọn.

Client loại snapshot gameplay cũ/trùng, không tự tăng `stateVersion`. Presence từ `ROOM_UPDATED` được cập nhật độc lập với version gameplay. Reconnect khôi phục toàn bộ trạng thái và kết quả đúng room/match; dữ liệu kết quả hoặc trận khác không được áp dụng.

Server cung cấp trạng thái đầy đủ khi bắt đầu và khôi phục, đồng thời broadcast các thay đổi phòng, xúc xắc, nước đi, lượt, timeout, forfeit và kết thúc trận tới đúng nhóm người nhận.

### 14.3. Trình diễn nước đi

`lastMove` chứa `pieceId`, `fromStep`, `landedStep` (đích sau xúc xắc), `toStep` (đích sau hiệu ứng), `triggeredEffect`. Đây là dữ liệu trình diễn do Server tạo, gửi trong response/broadcast của nước đi và giữ khi chụp presence; roll/timeout tiếp theo không giữ metadata nước đi cũ.

Unity chỉ animate snapshot mới liên tiếp trong cùng match. Reconnect, hụt snapshot hoặc thiếu metadata thì đặt quân ngay tại vị trí chính thức, không phát lại thông báo. Refresh UI hoặc response/event trùng không phát lại animation.

Quân đi tới `landedStep`, thể hiện hiệu ứng rồi đi tới `toStep`; trường hợp lùi về đúng vị trí ban đầu vẫn có animation. Quân đạt 53 chạm tâm bàn rồi chuyển lên khay theo màu; khay và vương miện không thay đổi trạng thái `FINISHED / 53`.

Thông báo hiệu ứng/về đích tự tắt khoảng 2,6 giây, không chặn chuột hoặc yêu cầu xác nhận. Màn Result mở sau khi trình diễn nước đi cuối hoàn tất.

## 15. Cơ sở dữ liệu

### 15.1. Các bảng

| Bảng                    | Trường dữ liệu                                                                                              |
| ----------------------- | ----------------------------------------------------------------------------------------------------------- |
| `users`                 | `id`, `username`, `password_hash`, `display_name`, `score`, `first_place_count`, `created_at`, `updated_at` |
| `matches`               | `id`, `public_id`, `started_at`, `ended_at`, `status`, `created_at`                                         |
| `match_players`         | `id`, `match_id`, `user_id`, `color`, `rank`, `score_earned`, `result`, `created_at`                        |
| `flyway_schema_history` | Lịch sử migration do Flyway quản lý                                                                         |

Quan hệ: một user có nhiều `match_players`; một match có nhiều `match_players`. Khóa ngoại liên kết tới `users.id` và `matches.id`.

`username` và `matches.public_id` là duy nhất. Trong một trận, user, màu và hạng không được trùng. Điểm không âm; hạng hợp lệ từ 1 đến 4; thời điểm kết thúc không trước bắt đầu.

### 15.2. Giao dịch và migration

Lưu `matches`, `match_players`, cộng điểm và tăng số lần hạng nhất thuộc cùng transaction. `public_id` bảo đảm retry lưu một trận không cộng điểm hai lần.

HikariCP quản lý pool JDBC. `DatabaseManager` chạy Flyway trước khi Server nhận request. Schema được quản lý bằng các migration trong [server/src/main/resources/db/migration](server/src/main/resources/db/migration), không tạo bảng thống kê hoặc thay schema ngoài contract đã định nghĩa.

## 16. Đồng thời, bảo mật và xử lý lỗi

Server xử lý nhiều connection bằng thread pool. Mỗi phòng có lock riêng; validate và cập nhật trạng thái trong cùng phòng phải được serialize. Các phòng độc lập có thể xử lý song song, không dùng một global lock cho mọi phòng.

Mọi gameplay action phải kiểm tra session, membership, trạng thái phòng/trận, đúng người/phase, quyền sở hữu quân, nước đi hợp lệ và request trùng. Timeout callback phải kiểm tra phiên bản/trạng thái để không đổi lượt lần thứ hai sau một thao tác đã xử lý.

Network, service, game và repository tách trách nhiệm. Game Rule không truy cập UI; repository không xử lý luật; client không truy cập MySQL trực tiếp. Lỗi phải được xử lý và ghi log phù hợp, không bỏ qua exception âm thầm.

Log gồm connect/disconnect, login, phòng, game start, roll/move, timeout, reconnect, forfeit và lỗi. Không log mật khẩu thô, password hash hoặc toàn bộ session token. Broadcast không chứa dữ liệu xác thực nhạy cảm.

## 17. Kiểm thử và các bất biến

### 17.1. Phạm vi kiểm thử

| Nhóm              | Tình huống cần kiểm tra                                                                                              |
| ----------------- | -------------------------------------------------------------------------------------------------------------------- |
| Luật game         | Spawn bị chặn/đá quân, đi xuyên, đích cùng màu, capture, tọa độ theo màu, carry-over, vượt 53, hoàn thành nhiều quân |
| Hiệu ứng          | SPEED/SLOW hợp lệ và bị chặn, lùi âm, capture sau hiệu ứng, TRAP, LUCKY, blacklist và không chain effect             |
| Lượt              | Dice 6 có/không có nước đi, bonus không cộng dồn, nhiều lần 6, slot trống, bỏ qua completed/forfeited                |
| Timeout/reconnect | 120 giây mỗi phase, bỏ lượt, participant mất mạng vẫn có lượt, grace 60 giây, khôi phục snapshot/kết quả             |
| TCP               | Frame phân mảnh/gộp, length sai/quá lớn, JSON lỗi, ngắt giữa frame, request trùng và action sai lượt                 |
| Phòng             | 2/3/4 người, join đồng thời, host rời, lời mời hết hạn/dùng lại, Ready/Start, chơi lại                               |
| Persistence       | Transaction kết quả, retry không cộng điểm lặp, lịch sử và xếp hạng                                                  |
| Client            | Chuyển scene, UI từ snapshot, chống phát lại animation, thoát trận, chat, nhiều client và mất kết nối                |

Test Java nằm trong [common/src/test](common/src/test) và [server/src/test](server/src/test). GitHub Actions chạy Maven verify với MySQL integration test. Build/test Unity và kiểm tra toàn bộ luồng Unity–Java–MySQL là bước riêng; kết quả Maven không thay thế kiểm thử client.

### 17.2. Bất biến hệ thống

- Phòng không quá 4 người; trận bắt đầu có 2..4 người và mỗi người có 4 quân.
- Mỗi quân thuộc đúng một participant; mỗi slot trong trận chỉ thuộc một participant.
- Có tối đa một current player; nếu có thì participant đó phải ACTIVE.
- `DISCONNECTED` không đồng nghĩa `FORFEITED` trước khi hết grace period.
- Xúc xắc thuộc `[1,6]` và chỉ do Server sinh.
- Trạng thái quân luôn khớp `stepCount`: `-1`, `0..47`, `48..52`, `53` tương ứng bãi, vòng chung, đích, hoàn thành.
- Sau resolve, mỗi ô vòng chung và mỗi nấc 1..5 trong Finish Track của từng màu có tối đa một quân; quân FINISHED không chiếm occupancy ở nấc 6.
- Quân hoàn thành không đi lại; mỗi participant chỉ nhận một hạng.
- Một nước đi kích hoạt tối đa một ô đặc biệt; Finish Track và các ô blacklist không có hiệu ứng.
- Một tài khoản chỉ có một active session, trừ thao tác thay connection của reconnect hợp lệ.
- Luật, tọa độ, framing, timer, hệ điểm và field trên wire phải nhất quán giữa Server, Client và tài liệu này.
