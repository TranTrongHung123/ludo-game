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
├── PLAN.md
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

Hướng dẫn chạy ngắn bằng PowerShell xem tại [`RUN.md`](./RUN.md).

Project sử dụng Maven multi-module.

Build toàn bộ project bằng Maven Wrapper (không cần cài Maven riêng):

```powershell
.\mvnw.cmd install
```

Trên Linux/macOS:

```bash
./mvnw install
```

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

## Continuous Integration

Workflow [CI](./.github/workflows/ci.yml) tự động chạy trên mọi `push`, `pull_request` và có thể chạy thủ công bằng `workflow_dispatch`.

CI thực hiện:

- checkout source với quyền chỉ đọc;
- cài Eclipse Temurin JDK 21 và cache Maven dependencies;
- khởi tạo MySQL 8.4 service có health check;
- bật toàn bộ MySQL integration test;
- chạy `./mvnw --batch-mode --no-transfer-progress verify` cho cả `common`, `server` và `client`.

Các mật khẩu trong workflow chỉ là credential tạm thời của MySQL service trong runner, không dùng GitHub secret và không dùng cho môi trường triển khai.

## Chạy MySQL bằng Docker Compose

Yêu cầu Docker Desktop (hoặc Docker Engine có Compose plugin). Từ thư mục gốc dự án, chạy:

```bash
docker compose up -d mysql
docker compose ps
```

Compose khởi tạo database `ludo_game` và lưu dữ liệu trong named volume `mysql_data`. Cấu hình JDBC mặc định cho Game Server chạy trên máy host:

```text
URL:      jdbc:mysql://localhost:3306/ludo_game
User:     ludo
Password: ludo_dev_password
```

Các giá trị trên chỉ dành cho môi trường local. Khi cần thay đổi, sao chép `.env.example` thành `.env` và sửa giá trị trong `.env`; file này đã được Git bỏ qua.

Xem log hoặc dừng database:

```bash
docker compose logs -f mysql
docker compose down
```

Lệnh `docker compose down` giữ lại dữ liệu. Chỉ dùng `docker compose down -v` khi chủ đích muốn xóa toàn bộ database local.

Game Server đọc cấu hình database từ `DB_URL`, hoặc từ bộ `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`. Nếu không khai báo, các giá trị mặc định sẽ khớp với Compose ở trên. `DatabaseManager.initialize(...)` tạo HikariCP pool và chạy Flyway migration trước khi Server phục vụ request.

Integration test với MySQL local chỉ chạy khi bật cờ, để build thông thường không phụ thuộc Docker:

```powershell
$env:RUN_MYSQL_INTEGRATION_TESTS='true'
.\mvnw.cmd -pl server -am test
```

Lần chạy đầu trên database rỗng sẽ tạo `users`, `matches`, `match_players` và `flyway_schema_history`.
Migration mới nhất bổ sung `matches.public_id` duy nhất để việc lưu kết quả có thể retry mà không cộng điểm hai lần.

## Chạy Game Server

Khởi động MySQL trước, sau đó chạy main class:

```text
vn.ptit.ltm.server.ServerApplication
```

Trong IntelliJ IDEA, mở [ServerApplication.java](./server/src/main/java/vn/ptit/ltm/server/ServerApplication.java) và chạy `main`. Server mặc định lắng nghe cổng `5555`; có thể override bằng `SERVER_PORT` và `SERVER_WORKER_THREADS`.

Server hiện hỗ trợ end-to-end:

- `REGISTER`, mật khẩu được hash bằng BCrypt;
- `LOGIN`, mỗi account chỉ có một active session;
- `LOGOUT` và session cleanup;
- `PING` / `PONG`, đóng connection sau 3 heartbeat bị lỡ;
- detect disconnect và giữ session trong reconnect grace period 60 giây;
- `RECONNECT` bằng session ID và khôi phục presence state;
- `GET_ONLINE_PLAYERS` trả danh sách người chơi online cùng điểm, số lần hạng nhất và presence state;
- tự động broadcast `ONLINE_PLAYERS_UPDATED` khi session hoặc presence state thay đổi.
- tạo, tham gia và rời phòng bằng `CREATE_ROOM`, `JOIN_ROOM`, `LEAVE_ROOM`;
- tự cấp slot/màu, giới hạn 4 người, chuyển host và xóa phòng rỗng;
- lock riêng theo phòng và broadcast `ROOM_UPDATED` tới đúng thành viên;
- giữ membership khi mất kết nối và trả lại `RoomDto` khi reconnect trong grace period;
- mời người chơi `IDLE`, Accept/Reject lời mời dùng một lần và tự hết hạn sau 60 giây;
- Ready/Unready và Start Game do chủ phòng thực hiện khi tối thiểu 2 người đã Ready;
- khởi tạo Game State authoritative, 4 quân mỗi người và lượt đầu theo occupied slot nhỏ nhất;
- `ROLL_DICE` do Server sinh, tính `validPieceIds` và tự chuyển lượt khi không có nước đi;
- `MOVE_PIECE` authoritative với spawn, chặn quân cùng màu, capture/Shield, Finish Track và carry-over;
- layout cố định 20 ô đặc biệt và xử lý đầy đủ Speed, Slow, Lucky, Trap, Shield mà không chain effect;
- bonus roll khi ra 6 hoặc gặp Lucky, turn rotation theo slot, hoàn thành quân/người chơi và xếp hạng trong RAM;
- `TimeoutManager` authoritative cho phase Roll 8 giây và Move 12 giây, chống callback cũ đổi lượt hai lần bằng `stateVersion`;
- broadcast `TURN_TIMEOUT` và Game State mới; người chơi `DISCONNECTED + ACTIVE` vẫn nhận lượt và timeout bình thường.
- hết reconnect grace period 60 giây sẽ chuyển participant sang `FORFEITED`, gán hạng thấp nhất còn trống, đưa quân khỏi bàn và kiểm tra cascading Game Over;
- broadcast `GAME_OVER` với bảng hạng authoritative khi forfeit làm trận kết thúc.
- `LEAVE_ROOM` trong trận là Quit chủ động: forfeit ngay, 0 điểm, không có grace period, gỡ membership sống nhưng vẫn giữ participant trong kết quả;
- cho phép người đã hoàn thành hoặc thành viên của phòng `FINISHED` rời phòng, chuyển host cho người còn lại và xóa phòng khi người cuối cùng rời.
- lưu `matches`, `match_players`, cộng điểm và tăng số lần hạng nhất trong cùng một transaction khi trận kết thúc;
- chống lưu/cộng điểm lặp bằng public match ID duy nhất; `GAME_OVER` dùng tổng điểm đã đọc lại từ database;
- hỗ trợ `GET_RANKING` và `GET_MATCH_HISTORY` qua TCP; lịch sử trả tối đa 50 trận gần nhất của account đang đăng nhập.
- chặn request bị phát lại với cùng `requestId` trên cùng connection trong cửa sổ retry, tránh action nghiệp vụ bị thực thi hai lần.

Không log plain-text password, password hash hay session token.

## Chạy JavaFX Client

Khởi động MySQL và Game Server trước. Sau đó mở terminal khác tại thư mục gốc dự án:

```powershell
.\mvnw.cmd -pl client javafx:run
```

Client mặc định kết nối tới `127.0.0.1:5555`. Có thể cấu hình `SERVER_HOST` và `SERVER_PORT` trong run environment của IDE hoặc terminal. Trong IntelliJ IDEA cũng có thể chạy trực tiếp `main` của [ClientApplication.java](./client/src/main/java/vn/ptit/ltm/client/ClientApplication.java).

Client hiện hỗ trợ:

- màn hình Register và Login bằng JavaFX/FXML;
- gửi `REGISTER`, `LOGIN`, `LOGOUT` qua TCP length-prefixed JSON;
- ghép request/response bằng `requestId` và hiển thị lỗi nghiệp vụ thân thiện;
- giữ session/profile trong RAM sau khi đăng nhập;
- tự phản hồi heartbeat `PING` bằng `PONG`;
- Lobby Screen nhận cả snapshot ban đầu và event `ONLINE_PLAYERS_UPDATED`;
- tạo phòng, nhập mã để tham gia, xem thành viên/slot/màu và rời phòng;
- gửi/nhận lời mời phòng, Accept/Reject và hiển thị thời điểm hết hạn;
- Ready/Unready, Start Game và chuyển sang màn hình trạng thái trận ban đầu;
- nhận event `ROOM_UPDATED`, `INVITE_PLAYER`, `GAME_STATE` không có `requestId` và cập nhật state trước khi render;
- đổ xúc xắc, chọn quân hợp lệ và gửi Move Piece từ màn hình trận;
- render bàn cờ JavaFX gồm 48 ô vòng chung, 6 nấc Finish Track cho mỗi màu, khu vực chuồng và toàn bộ quân từ `GameState` authoritative;
- render đúng danh sách Special Cells Server gửi, hiển thị trạng thái Slow/Shield và highlight quân thuộc `validPieceIds`; click quân chỉ chọn `pieceId`, không tự tính nước đi ở Client;
- render dice, phase, countdown theo deadline Server, lượt hiện tại và vị trí `stepCount` mới nhất từ Game State;
- nhận `TURN_TIMEOUT`, khóa thao tác khi deadline đã hết và hiển thị phản hồi timeout;
- tự thử nối lại trong grace period, gửi `RECONNECT` bằng session cũ và chỉ mở lại thao tác sau khi đã nhận full Room/Game State cùng deadline authoritative;
- nhận `GAME_OVER` và tự chuyển sang Game Result Screen riêng, hiển thị thứ hạng, màu quân, điểm nhận, tổng điểm và người `FORFEITED` với 0 điểm;
- nút `Bỏ cuộc` yêu cầu xác nhận rõ hậu quả trước khi gửi `LEAVE_ROOM`; Game Result Screen có nút `Về sảnh` và chỉ dọn Room/Game State cục bộ sau khi Server xác nhận;
- có Ranking Screen và Match History Screen tải bất đồng bộ dữ liệu authoritative từ Server, hỗ trợ làm mới và quay lại Lobby;
- tự làm mới điểm và số lần hạng nhất trong profile cục bộ từ snapshot Lobby do Server broadcast;
- toàn bộ connect, send, receive và timeout chạy ngoài JavaFX Application Thread.

Test suite còn mô phỏng trọn ván 2/3/4 người từ lúc toàn bộ quân ở chuồng tới cascading Game Over, đồng thời kiểm tra TCP invalid/oversized/malformed/partial frame, duplicate request, wrong-turn, timeout, disconnect/reconnect và tính toàn vẹn ranking/lịch sử.

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
- [`PLAN.md`](./PLAN.md): checklist tiến độ kỹ thuật toàn dự án.
- [`README.md`](./README.md): giới thiệu nhanh repository, kiến trúc và cách làm việc.
- [`RUN.md`](./RUN.md): các lệnh ngắn để chạy database, Server, Client và test.

## Thành viên

| Thành viên      | Mã sinh viên |
| --------------- | ------------ |
| Hoàng Đình Duy  | B23DCCN237   |
| Trần Trọng Hùng | B23DCCN363   |
| Đặng Phi Long   | B23DCCN497   |
| Tạ Thanh Thiên  | B23DCCN783   |
