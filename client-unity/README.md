# Unity Login, Register, Lobby & Room

Mở project `client-unity` bằng Unity **6000.3.24f1**, mở
`Assets/Scenes/LoginScene.unity` và nhấn **Play**. LoginScene đã là scene đầu trong Build Settings.

- Canvas uGUI, TextMeshPro tiếng Việt, bố cục 1920×1080; CanvasScaler `Scale With Screen Size`, match `0.5`.
- UI được lưu trong `Assets/Prefabs/Login/LoginScreen.prefab`, chỉnh trực tiếp trong Editor.
- Đăng ký: `Assets/Scenes/RegisterScene.unity`, prefab `Assets/Prefabs/Register/RegisterScreen.prefab`.
- Prefab dùng chung: `Assets/Prefabs/Common/PrimaryButton.prefab`, `ConnectionBadge.prefab`.
- Ảnh xem trước: [Login 1920×1080](Documentation/login-1920x1080.png).
- Ảnh đăng ký: [Register 1920×1080](Documentation/register-1920x1080.png).
- `NetworkSession` trong scene giữ kết nối TCP bằng `DontDestroyOnLoad`.

## Kết nối

Mặc định `127.0.0.1:5555`. Đổi `Server Host` / `Server Port` trên GameObject
`NetworkSession` trong Inspector hoặc đặt `SERVER_HOST` / `SERVER_PORT` trước khi mở Unity/player.
Chạy Java Server và MySQL theo hướng dẫn ở thư mục gốc. Khi chưa có Server,
UI báo mất kết nối, khóa đăng nhập và cho bấm **Thử lại**.

Gửi `LOGIN` với `username`, `password`; nhận response `LOGIN` hoặc `ERROR` theo
`requestId`. Framing 4 byte big-endian, UTF-8 nghiêm ngặt, tối đa 65.536 byte;
đọc đủ frame qua nhiều lần TCP read, serialize writes, trả `PONG` cho `PING`.
Timeout kết nối 5 giây, request 10 giây. Không ghi mật khẩu/token vào log hoặc PlayerPrefs.
Session và profile chỉ lưu trong RAM sau response thành công.

Input hỗ trợ Tab giữa hai trường, Enter để gửi, hiện/ẩn mật khẩu. Trong khi gửi,
khóa input/nút để ngăn gửi lặp. Password được xóa sau response hoặc lỗi.

## Phạm vi đã thống nhất

Đã triển khai Login và Register, cả hai scene đã có trong Build Settings.
Đăng ký gửi đúng `REGISTER(username, displayName, password)`. Theo quyết định đã xác nhận,
form dùng tên hiển thị thay email; kiểm tra xác nhận mật khẩu chỉ ở Client.
Thành công chỉ được công nhận khi nhận response REGISTER hợp lệ có profile từ Server,
sau đó quay Login, điền sẵn username và hiện thông báo. Đăng ký không tự đăng nhập.
Nút quay lại cũng giữ username; mật khẩu không được chuyển giữa scene.
Hiện/ẩn áp dụng cho cả hai ô mật khẩu. Tab/Shift+Tab chuyển qua bốn trường, Enter gửi form.

`LobbyScene` đã được tạo và thêm vào Build Settings. Đăng nhập thành công chuyển tới Lobby.
Mở Lobby trực tiếp trong Play Mode khi chưa đăng nhập sẽ quay về Login.
Ba màn hình dùng cùng kết nối TCP xuyên scene.
Player Settings bật Run In Background để cập nhật trạng thái mạng khi cửa sổ mất focus.

Lobby hỗ trợ tự kết nối lại và gửi `RECONNECT` bằng session cũ trong thời gian tối đa
60 giây. Chỉ công bố connected sau khi Server xác nhận khôi phục. Phiên hết hạn quay Login.
Snapshot room/game được lưu nếu Server gửi lại; màn chơi chưa được triển khai.
Không thay đổi code JavaFX, common, server hoặc luật game.

## Kiểm tra đã thực hiện

- Unity compile: không lỗi; Console hiện tại không warning/error.
- 18 kiểm tra trong `AgentScripts/VerifyLogin.cs`: giới hạn UTF-8, validation,
  framing big-endian, partial/coalesced frames, length không hợp lệ, EOF giữa frame,
  UTF-8 sai, heartbeat, response thành công/thất bại và disconnect.
- 6 kiểm tra Play Mode trong `AgentScripts/VerifyLoginUi.cs`: trạng thái socket,
  pending, chống gửi lặp, lỗi tiếng Việt, lưu session và xử lý Lobby chưa tồn tại.
- 20 kiểm tra đăng ký trong `AgentScripts/VerifyRegister.cs`: biên validation, hiện/ẩn,
  mất kết nối, mật khẩu không khớp không gửi request, pending, trùng tên, xóa mật khẩu,
  response thành công không hợp lệ, profile, không tự đăng nhập và chuyển scene giữ cùng socket.
- Kiểm tra bằng MCP: input rỗng, hiện/ẩn mật khẩu và nút bị khóa khi offline.
- Đã capture và kiểm tra bố cục ở 1920×1080, 2560×1440, 3840×2160.

Các bài tích hợp dùng TCP fixture trên localhost với cổng tạm thời, không dùng tài khoản thật.
Chưa kiểm thử end-to-end với Java Server + MySQL thật vì không có Server đang chạy.

Chạy lại bằng Unity MCP `run_script`, file `AgentScripts/VerifyLogin.cs`, entry
`VerifyLogin.Main`. Với bài UI, mở LoginScene và vào Play Mode ở trạng thái mất kết nối,
rồi chạy `AgentScripts/VerifyLoginUi.cs`, entry `VerifyLoginUi.Main`; thoát Play Mode sau kiểm tra.
Các script kiểm tra nằm ngoài Assets và không được đưa vào bản build.
Để kiểm tra đăng ký, mở RegisterScene, vào Play Mode khi Server chưa kết nối và chạy
`AgentScripts/VerifyRegister.cs`, entry `VerifyRegister.Main`; thoát Play Mode sau kiểm tra.

## Lobby

Scene `Assets/Scenes/LobbyScene.unity`; prefab `Assets/Prefabs/Lobby/LobbyScreen.prefab`.
Các dòng người chơi dùng `OnlinePlayerRow.prefab`; card lời mời dùng `InvitationCard.prefab`.
[Ảnh Lobby](Documentation/lobby-1920x1080.png) thể hiện trạng thái chưa có dữ liệu trong Editor.
Runtime hiển thị profile và danh sách thật, không tạo người chơi mẫu.

- `GET_ONLINE_PLAYERS` nhận `ONLINE_PLAYERS_UPDATED`, đồng thời nhận broadcast chủ động.
- Dùng `displayName` vì PlayerSummaryDto không có username; đánh dấu Bạn theo playerId.
- `CREATE_ROOM`, `JOIN_ROOM`, `ACCEPT_INVITE`, `REJECT_INVITE`, `LOGOUT` gửi sessionId thật.
- Lời mời hiển thị người mời, roomId và thời gian còn lại từ deadline Server gửi.
  Server quyết định lời mời hợp lệ; UI ẩn lời mời đã hết hạn và bảo toàn lời mời mới
  nếu response từ chối lời mời cũ đến trễ.
- RankingScene, HistoryScene chưa được triển khai. Các nút điều hướng
  báo rõ màn hình chưa có. Tạo/tham gia phòng vẫn gửi request thật và lưu room Server trả;
  sau thành công chuyển đến RoomScene đã có trong Build Settings.
- Bảng xếp hạng/lịch sử hiện chỉ là điểm nối điều hướng, chưa tải dữ liệu hai màn hình đó.

`AgentScripts/VerifyLobby.cs`, entry `VerifyLobby.Main`: chạy Play Mode từ Lobby/Login
khi Server offline. Đã qua 12 kiểm tra với TCP fixture (20 người chơi, scroll, self marker,
lời mời, join lỗi, chống gửi lặp, deferred navigation, reconnect, create và logout).
Chưa kiểm thử với Java Server/MySQL thật. Thoát Play Mode sau kiểm tra.

## Phòng chờ

`Assets/Scenes/RoomScene.unity` dùng `Assets/Prefabs/Room/RoomScreen.prefab`.
[Ảnh phòng chờ](Documentation/room-1920x1080.png) là bố cục trống trong Editor;
runtime nhận thành viên thật từ Server. Vào Play Mode trực tiếp khi chưa đăng nhập
sẽ quay Login; đăng nhập và tạo/tham gia phòng từ Lobby để sử dụng.

- Bốn slot Đỏ/Xanh dương/Vàng/Xanh lá theo slotIndex Server; tên hiển thị, host badge,
  presence và ready lấy từ RoomDto, không tự chuyển chủ phòng.
- Sao chép roomId; Ready/Unready gửi roomId và ready; rời phòng chỉ quay Lobby sau response.
- Danh sách lọc IDLE và loại thành viên phòng. Chủ phòng có thể bấm Mời trực tiếp
  hoặc chọn một dòng rồi Gửi lời mời. Phòng đầy hoặc pending sẽ khóa nút.
- Start chỉ dành cho host, tối thiểu hai người, tất cả ready và IN_ROOM, phòng WAITING.
- Nhận ROOM_UPDATED, GAME_STATE/GAME_STATE_UPDATED và snapshot reconnect.
- GameScene chưa triển khai. START_GAME vẫn là request thật: Server có thể bắt đầu
  trận và timer. UI báo rõ màn chơi chưa có, khóa thao tác phòng chờ; không thể chơi
  ván đó bằng Unity cho đến khi GameScene được bổ sung.

`AgentScripts/VerifyRoom.cs`, entry `VerifyRoom.Main`, chạy Play Mode từ RoomScene
khi Server chưa kết nối: 15 kiểm tra validation quyền/ready, payload TCP, UI slot,
mời, chống gửi lặp, leave và start. Test dùng fixture localhost, chưa dùng Java/MySQL thật.
Các kiểm tra VerifyLoginUi/VerifyLobby cũ có assertion về màn hình chưa tồn tại ở giai đoạn
trước; cần cập nhật các assertion điều hướng khi chạy lại sau khi bổ sung Lobby/Room.

## Tài nguyên

Font Liberation Sans và giấy phép OFL nằm trong `Assets/TextMesh Pro/Fonts`.
Font SDF tiếng Việt ở `Assets/Fonts/Ludo Vietnamese SDF.asset`.
uGUI, Input System và Newtonsoft JSON sử dụng Unity Package Manager.

Cụm ngựa/xúc xắc: `Assets/Art/Login/ludo-pieces.png`, tạo bằng công cụ imagegen tích hợp.
Prompt và nguồn tài nguyên được ghi ở [art-source.md](Documentation/art-source.md).
