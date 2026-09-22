# Chạy Ludo Game — Unity Client + Java Server

Client chính nằm trong `client-unity/`. Cần Java 21, Docker Desktop đang chạy
và Unity Editor theo `client-unity/ProjectSettings/ProjectVersion.txt`
(hiện tại **6000.6.2f1**).

Nếu PowerShell chặn script, chạy một lần trong terminal hiện tại:

```powershell
Set-ExecutionPolicy -Scope Process Bypass
```

## Khởi động

Terminal 1 — MySQL:

```powershell
.\ludo.ps1 db
```

Terminal 1 — Server:

```powershell
.\ludo.ps1 server
```

Unity Client:

1. Unity Hub → Add project from disk → chọn thư mục `client-unity`.
2. Mở bằng phiên bản Editor ghi trong `ProjectVersion.txt`.
3. Mở `Assets/Scenes/LoginScene.unity`, nhấn **Play**.
4. Đăng ký/đăng nhập, tạo hoặc vào phòng; tất cả Ready rồi chủ phòng Start.

Mặc định `127.0.0.1:5555`. Đổi `Server Host` / `Server Port` trên GameObject
`NetworkSession` của LoginScene, hoặc đặt `SERVER_HOST`, `SERVER_PORT` trước khi
khởi động Editor/player. Editor đang chạy không nhận biến môi trường vừa đặt
trong terminal khác. Nếu chơi qua LAN, dùng IP máy Server và mở cổng TCP tương ứng.

Chạy nhiều client: dùng **File → Build Profiles** để build desktop player.
Bật đủ tám scene Login, Register, Lobby, Room, Game, Result, Ranking, History,
với Login đứng đầu. Mở nhiều instance player, mỗi instance đăng nhập tài khoản
riêng; cũng có thể dùng một Editor Play Mode cùng một player.

`ludo.ps1 client` vẫn chạy **JavaFX legacy**, không mở Unity. Để demo client hiện tại,
dùng các bước Unity ở trên.

## Build và kiểm tra

Build/test backend Java:

```powershell
.\mvnw.cmd -pl server -am install
.\mvnw.cmd -pl server -am test
```

Bật MySQL integration test sau khi database sẵn sàng:

```powershell
$env:RUN_MYSQL_INTEGRATION_TESTS='true'
.\mvnw.cmd -pl server -am test
Remove-Item Env:RUN_MYSQL_INTEGRATION_TESTS
```

Dùng database local dành cho kiểm thử; các bài này tạo/xóa dữ liệu test.
Unity build riêng trong Editor. Script `client-unity/AgentScripts/Verify*.cs`
chạy bằng Unity MCP `run_script`; điều kiện từng script trong
[README của Unity](client-unity/README.md). `VerifyLogin.Main` chạy được khi
Editor không ở Play Mode, dùng TCP fixture localhost, không cần Java/MySQL.

## Các lệnh khác

```powershell
.\ludo.ps1 build    # Build module Java, gồm client legacy; không build Unity
.\ludo.ps1 test     # Maven verify; không chạy test Unity
.\ludo.ps1 status   # Xem trạng thái MySQL
.\ludo.ps1 stop     # Dừng MySQL, giữ nguyên dữ liệu
.\ludo.ps1 help     # Xem trợ giúp
```

Xem [INTEGRATION_REVIEW.md](INTEGRATION_REVIEW.md) cho kết quả rà soát và các lỗi
còn mở. Đợt 2026-09-22 chưa xác nhận end-to-end Unity + Java + MySQL thật.
