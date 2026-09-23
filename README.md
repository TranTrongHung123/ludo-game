# Ludo Game - Bài tập lớn Lập trình mạng

Game **Cờ Cá Ngựa (Ludo)** nhiều người chơi dùng **Unity C# Client + Java Server**, theo mô hình **Client–Server**.

## Công nghệ

- Java 21
- Maven multi-module
- Unity 6 + C#, uGUI / Canvas + TextMeshPro
- Newtonsoft.Json phía Unity
- TCP Socket
- JSON / Jackson
- MySQL 8
- JDBC + HikariCP
- Flyway
- SLF4J + Logback
- JUnit 5 + Mockito

## Kiến trúc

```text
Unity C# Client
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
└── client-unity/    # Unity C# client
```

### `common`

Định nghĩa contract phía Java. Unity ánh xạ JSON tương ứng bằng C#; không import JAR:

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

### `client-unity` — client chính

Unity project có scenes, prefabs, uGUI/TextMeshPro và C# scripts:

- `Network/`: TCP, framing, heartbeat, request/response.
- `Services/`: session, lobby, room, game, ranking, history.
- `Controllers/` và `Views/`: tương tác và render dữ liệu Server.
- `AgentScripts/`: kiểm tra/authoring, không đưa vào player build.

Maven chỉ build `common` và `server`; Unity build riêng trong Editor.
Các kiểm thử TCP độc lập nằm trong `server/src/test/`.

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
- Có các ô đặc biệt: SPEED (+2 bước), SLOW (-2 bước ngay), LUCKY (+1 lần tung), TRAP (về chuồng).

Chi tiết đầy đủ xem [`AGENTS.md`](./AGENTS.md).

## Build

Hướng dẫn chạy ngắn bằng PowerShell xem tại [`RUN.md`](./RUN.md).

Backend sử dụng Maven multi-module; Unity build riêng bằng Editor.
Để build backend: `.\mvnw.cmd -pl server -am install`.

Build backend Java (`common` và `server`) bằng Maven Wrapper (không cần cài Maven riêng):

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
- chạy `./mvnw --batch-mode --no-transfer-progress verify` cho `common`, `server` và `client` legacy.

CI hiện chưa build/test Unity; Maven xanh không chứng minh luồng Unity–Java đã đạt end-to-end.

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
- `MOVE_PIECE` authoritative với spawn, chặn quân cùng màu, capture, Finish Track và carry-over;
- layout cố định 16 ô đặc biệt và xử lý đầy đủ SPEED (+2 bước), SLOW (-2 bước ngay), LUCKY (+1 lần tung), TRAP (về chuồng) mà không chain effect;
- bonus roll khi ra 6 hoặc gặp Lucky, turn rotation theo slot, hoàn thành quân/người chơi và xếp hạng trong RAM;
- `TimeoutManager` authoritative cho phase Roll 120 giây và Move 120 giây, chống callback cũ đổi lượt hai lần bằng `stateVersion`;
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

## Chạy Unity Client

1. Khởi động MySQL và Java Game Server.
2. Mở thư mục `client-unity/` bằng Unity Hub, dùng phiên bản trong
   `ProjectSettings/ProjectVersion.txt` (hiện tại **6000.6.2f1**).
3. Mở `Assets/Scenes/LoginScene.unity` rồi nhấn **Play**.
4. Đăng ký/đăng nhập, tạo hoặc vào phòng; mọi thành viên Ready rồi chủ phòng Start.

Client mặc định kết nối `127.0.0.1:5555`. Đổi `Server Host` / `Server Port` trên
`NetworkSession`, hoặc đặt `SERVER_HOST` / `SERVER_PORT` trước khi mở Editor/player.
Chạy nhiều client bằng bản desktop build của Unity; chi tiết trong [RUN.md](RUN.md).

Các màn đã có: Login, Register, Lobby, Room, Game, Result, Ranking, History.
Unity gửi ý định qua TCP, giữ session trong RAM xuyên scene, tự trả heartbeat,
render board 48 ô và `validPieceIds` từ Server, gửi/nhận chat trong GameScene,
tải xếp hạng/lịch sử và thử reconnect khi mất kết nối.

Chi tiết scene, prefab và script kiểm tra: [client-unity/README.md](client-unity/README.md).

## Kết quả rà soát tích hợp — 2026-09-22

Đã sửa 5 vấn đề: khôi phục kết quả bỏ lỡ bằng `RECONNECT_RESULT.gameOver`, đồng bộ
presence, điểm Lobby, escape tên chat và chơi lại cùng phòng qua nút **Chơi tiếp**.
Chi tiết thay đổi và giới hạn: [INTEGRATION_REVIEW.md](INTEGRATION_REVIEW.md).

- Maven `clean verify` sau khi gỡ client desktop cũ: 102 test đạt, 4 MySQL test skip.
  Gồm 7 test TCP độc lập tại `server/src/test/java/vn/ptit/ltm/server/network/TcpWorkflowIntegrationTest.java`.
- Unity: 23 kiểm tra hồi quy và 61 kiểm tra Game/Result qua TCP fixture đạt.
- Chơi tiếp giữ roomId, reset ready/trận cũ sau khi lưu kết quả; host vẫn phải chờ
  tất cả thành viên Ready trước khi Start.
- Chưa chạy end-to-end với Unity + Java + MySQL thật trong đợt này.

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
- [`DESIGN.md`](./DESIGN.md): thiết kế giao diện Unity.
- [`INTEGRATION_REVIEW.md`](./INTEGRATION_REVIEW.md): bằng chứng rà soát Java–Unity và các lỗi còn mở.
- [`RUN.md`](./RUN.md): các lệnh ngắn để chạy database, Server, Client và test.

## Thành viên

| Thành viên      | Mã sinh viên |
| --------------- | ------------ |
| Hoàng Đình Duy  | B23DCCN237   |
| Trần Trọng Hùng | B23DCCN363   |
| Đặng Phi Long   | B23DCCN497   |
| Tạ Thanh Thiên  | B23DCCN783   |
