# Unity Client — client chính của Ludo Game

Backend là Java 21 + TCP + MySQL; đây là client duy nhất của dự án.
Rà soát mới nhất ngày 2026-09-22: [INTEGRATION_REVIEW.md](../INTEGRATION_REVIEW.md).
Các số liệu kiểm tra theo từng màn phía dưới là ghi nhận lịch sử, không phải tất cả
đã được chạy lại trên Editor hiện tại. Sau sửa lỗi, đã đạt 23 kiểm tra hồi quy và
61 kiểm tra Game/Result qua TCP fixture; chưa chạy Java/MySQL end-to-end.

Mở project `client-unity` bằng Unity theo `ProjectSettings/ProjectVersion.txt` (hiện tại **6000.6.2f1**), mở
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
Snapshot room/game được khôi phục nếu Server gửi lại; GameScene render lại bàn cờ từ snapshot.
Contract JSON theo `common/` và handler trong `server/`; Unity không import trực tiếp JAR Java.

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
- RankingScene và HistoryScene đã có, tải dữ liệu từ Server.
  Tạo/tham gia phòng gửi request thật và lưu room Server trả;
  sau thành công chuyển đến RoomScene đã có trong Build Settings.
- Nút Lịch sử mở HistoryScene và tải lịch sử của tài khoản đang đăng nhập.

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
- START_GAME nhận snapshot thật và chuyển sang GameScene; thành viên khác chuyển
  màn hình khi nhận GAME_STATE broadcast.

`AgentScripts/VerifyRoom.cs`, entry `VerifyRoom.Main`, chạy Play Mode từ RoomScene
khi Server chưa kết nối: 15 kiểm tra validation quyền/ready, payload TCP, UI slot,
mời, chống gửi lặp, leave và start. Test dùng fixture localhost, chưa dùng Java/MySQL thật.
Các kiểm tra VerifyLoginUi/VerifyLobby cũ có assertion về màn hình chưa tồn tại ở giai đoạn
trước; cần cập nhật các assertion điều hướng khi chạy lại sau khi bổ sung Lobby/Room.
VerifyRoom cũng còn assertion GameScene chưa tồn tại; luồng Room → Game hiện được
kiểm tra trong VerifyGame.

## Màn chơi

Mở `Assets/Scenes/GameScene.unity` để chỉnh giao diện; prefab chính là
`Assets/Prefabs/Game/GameScreen.prefab`, cùng `PlayerCard`, `Piece`, `Dice`.
Để chơi, chạy từ LoginScene, đăng nhập, tạo/tham gia phòng, tất cả sẵn sàng rồi bắt đầu.
Mở GameScene trực tiếp trong Play Mode khi chưa đăng nhập sẽ quay Login.

- Bàn cờ vuông với 48 ô chung, đường về đích và bốn chuồng màu. Vị trí quân,
  ô đặc biệt, người đang đến lượt và quân hợp lệ đều lấy từ GameState Server.
- `ROLL_DICE(roomId)` → `DICE_RESULT`; `MOVE_PIECE(roomId, pieceId)` →
  `MOVE_PIECE_RESULT`. Không gửi số xúc xắc hay vị trí do Client tự quyết định.
- Khóa thao tác khi pending, mất kết nối, sai lượt hoặc hết deadline. Sau phản hồi
  đổ xúc xắc vẫn chờ snapshot mới trước khi mở thao tác tiếp theo.
- Lọc snapshot cũ bằng `stateVersion`, bỏ snapshot của phòng khác; reconnect render
  lại ngay, không chạy animation đường đi từ trạng thái trước khi mất mạng.
- Timer dùng `serverDeadlineEpochMillis` và `phaseDurationMillis`; đồng hồ Client
  chỉ hiển thị/khóa thao tác, không tự đổi lượt. Máy Client cần đồng hồ hệ thống đúng.
- Xúc xắc bounce/xoay theo kết quả đã nhận; quân di chuyển theo snapshot được xác nhận.
- Bỏ cuộc có hộp xác nhận và gửi `LEAVE_ROOM`; chỉ về Lobby sau response thành công.
- Khi nhận `GAME_OVER` đúng trận, chuyển sang ResultScene để hiển thị kết quả
  chính thức và nút Về sảnh.
- Khung chat bên phải gửi `CHAT_MESSAGE(roomId, message)`; Server xác thực membership,
  giới hạn 200 ký tự và broadcast `ChatMessageDto` trong phòng. Client hiển thị broadcast,
  không tự thêm tin gửi vào log. Cả tên người gửi và nội dung đều được escape trước
  khi ghép rich text của ứng dụng.

Đã đạt **39 kiểm tra TCP localhost** trong `AgentScripts/VerifyGame.cs`, entry
`VerifyGame.Main`: mapping, chuyển Room → Game, pending/chống lặp, lỗi nước đi,
snapshot cũ/khác phòng, timeout hiển thị, reconnect, forfeit, kết thúc và payload.
Chạy bằng Unity MCP `run_script` trong Play Mode từ LoginScene khi Server offline.
Chưa kiểm thử end-to-end với Java Server + MySQL thật.

Đã kiểm tra ảnh ở [Full HD](Documentation/game-1920x1080.png),
[1440p](Documentation/game-2560x1440.png), [4K](Documentation/game-3840x2160.png).
Các ảnh thể hiện trạng thái chờ dữ liệu, không chứa người chơi mẫu.
`AgentScripts/CaptureGameUi.cs` chụp Canvas overlay qua camera tạm thời rồi khôi phục cấu hình.
`AgentScripts/CreateGameUi.cs` chỉ là lệnh authoring một lần ngoài Assets;
runtime sử dụng scene/prefab đã lưu, không dựng toàn bộ UI bằng code.

## Kết quả trận đấu

Scene `Assets/Scenes/ResultScene.unity` đã có trong Build Settings và dùng prefab
`Assets/Prefabs/Result/ResultScreen.prefab`; mỗi dòng là `ResultRow.prefab`.
Có thể mở màn này trong Editor để chỉnh trực tiếp. Khi chơi, đi từ Login; nhận
`GAME_OVER` sẽ tự chuyển Game → Result. Mở Result trực tiếp khi chưa đăng nhập
sẽ quay Login; chưa có kết quả sẽ quay màn phù hợp với session hiện tại.

- Giữ phong cách lavender, vương miện/confetti và bảng 5 cột; có Chơi tiếp và Về sảnh.
- Hạng, tên hiển thị, màu, trạng thái, điểm nhận và tổng điểm đều từ `GAME_OVER`.
  Sắp dòng theo rank Server, đánh dấu Bạn, giữ điểm lẻ, ẩn dòng không sử dụng.
- `LEAVE_ROOM(roomId)` dùng kết nối/session đang có; chờ response thành công mới về Lobby.
  Khóa nút khi pending/offline, lỗi cho phép thử lại; reconnect giữ kết quả đã nhận
  nếu vẫn cùng match. Không tự tính điểm hoặc cộng thắng vào profile.
- Scene/prefab không chứa người chơi mẫu. [Ảnh trống](Documentation/result-1920x1080.png)
  là trạng thái authoring; ảnh [Full HD](Documentation/result-1920x1080-fixture.png),
  [1440p](Documentation/result-2560x1440-fixture.png), [4K](Documentation/result-3840x2160-fixture.png)
  dùng dữ liệu **TCP fixture kiểm thử**, không phải kết quả trận thật.
- `AgentScripts/CreateResultUi.cs` chỉ authoring một lần ngoài Assets;
  `CaptureResultUi.cs` chụp màn hiện tại và khôi phục cấu hình Canvas/camera.

`AgentScripts/VerifyGame.cs`, entry `VerifyGame.Main`, đã cập nhật luồng Result:
**295 assertion đạt**, gồm kiểm tra Game/Result qua TCP localhost và kiểm tra
biên RectTransform ở ba độ phân giải. Bao phủ sắp hạng, điểm lẻ, 2/4 người,
bỏ cuộc, chặn rich text trong tên, kết quả khác trận, reconnect, pending,
chống gửi lặp, lỗi rời phòng và chuyển về Lobby sau xác nhận.
Chạy trong Play Mode từ Login khi Server offline. Unity compile và Console không
có lỗi/cảnh báo; các reference controller/row đầy đủ, một EventSystem và GraphicRaycaster.
Chưa kiểm thử end-to-end với Java Server + MySQL thật.

## Bảng xếp hạng

Scene `Assets/Scenes/RankingScene.unity` đã thêm vào Build Settings, dùng
`Assets/Prefabs/Ranking/RankingScreen.prefab` và `RankingRow.prefab`.
Đăng nhập → bấm **Bảng xếp hạng** từ Lobby. Mở scene trực tiếp khi chưa có session
sẽ quay Login. Scene không chứa dữ liệu người chơi mẫu.

- Tự tải `GET_RANKING` với `data: {}`, nhận `RANKING_RESULT.entries`.
  Giữ thứ tự và rank do Server trả; hiển thị displayName, totalScore và firstPlaceCount.
- Top 3 có màu huy chương, hạng nhất có vương miện; đánh dấu người chơi hiện tại.
  Điểm lẻ được giữ nguyên, tên người chơi không được diễn giải thành rich text.
- **Làm mới** khóa khi pending/offline; lỗi giữ danh sách trước đó và cho phép thử lại.
  Có trạng thái tải/rỗng/lỗi; reconnect thành công tự tải lại dữ liệu.
- Danh sách dùng ScrollRect, tái sử dụng row, ẩn row thừa và trở về đầu khi tải lại.
- **Về sảnh** vẫn dùng được khi đang tải; response đến trễ không cập nhật scene đã đóng.
  Điều hướng dùng cùng NetworkSession/TCP connection.

`AgentScripts/VerifyRanking.cs`, entry `VerifyRanking.Main`: **39 kiểm tra đạt**
qua TCP localhost, gồm payload/session, tải ban đầu, chống lặp, lỗi/malformed/empty,
rank authoritative, điểm lẻ, self badge, cuộn/tái dùng row, reconnect, điều hướng,
guard đăng nhập và biên giao diện ở Full HD/1440p/4K. Chạy trong Play Mode từ Login
khi chưa kết nối Server. Console không warning/error; controller và row không thiếu reference.
Chưa kiểm thử end-to-end với Java Server + MySQL thật.

[Ảnh trống trong Editor](Documentation/ranking-1920x1080.png).
Ảnh có dữ liệu từ **fixture kiểm thử**: [Full HD](Documentation/ranking-1920x1080-fixture.png),
[1440p](Documentation/ranking-2560x1440-fixture.png), [4K](Documentation/ranking-3840x2160-fixture.png).
`CreateRankingUi.cs` là script authoring một lần ngoài Assets; runtime dùng prefab đã lưu.
`CaptureRankingUi.cs` chụp màn hiện tại, khôi phục cấu hình Canvas/camera sau khi chụp.

## Lịch sử trận đấu

Scene `Assets/Scenes/HistoryScene.unity` đã có trong Build Settings; prefab chính
`Assets/Prefabs/History/HistoryScreen.prefab`, dòng lịch sử `HistoryRow.prefab`.
Đăng nhập → **Lịch sử** từ Lobby. Mở scene trực tiếp khi chưa đăng nhập sẽ quay Login.

- `GET_MATCH_HISTORY` gửi `data: {}` với session hiện tại; nhận `MATCH_HISTORY_RESULT.matches`.
  Server chọn lịch sử của tài khoản, Client không gửi playerId hoặc tự tạo kết quả.
- Giữ thứ tự Server trả; mỗi dòng có rank/playerCount, thời gian kết thúc theo giờ
  địa phương, màu quân, matchId, scoreEarned và trạng thái forfeited.
- Hạng nhất có vương miện; bỏ cuộc dùng màu đỏ; giữ nguyên điểm lẻ từ Server.
  Thống kê số trận gần nhất/số lần hạng nhất chỉ tính trên danh sách được trả về.
- **Làm mới** có pending, chống gửi lặp, trạng thái tải/rỗng/lỗi. Lỗi giữ dữ liệu cũ;
  reconnect thành công tự tải lại. Danh sách cuộn riêng, tái dùng row và ẩn row thừa.
- **Về sảnh** dùng cùng kết nối TCP, vẫn hoạt động khi đang tải; phản hồi đến trễ
  không cập nhật màn hình đã đóng. Không có dữ liệu mẫu trong scene/prefab.

`AgentScripts/VerifyHistory.cs`, entry `VerifyHistory.Main`: **42 kiểm tra đạt**
qua TCP localhost, gồm protocol/session, rank/số người, timestamp, màu/matchId,
điểm lẻ, bỏ cuộc, thống kê, pending, lỗi/malformed/empty, cuộn, reconnect và điều hướng.
Kiểm tra biên giao diện ở Full HD/1440p/4K; Console không warning/error và không thiếu
reference. Chạy từ Login trong Play Mode khi chưa kết nối Server.
Chưa kiểm thử end-to-end với Java Server + MySQL thật.

[Ảnh trống trong Editor](Documentation/history-1920x1080.png).
Ảnh dùng dữ liệu **fixture kiểm thử**: [Full HD](Documentation/history-1920x1080-fixture.png),
[1440p](Documentation/history-2560x1440-fixture.png), [4K](Documentation/history-3840x2160-fixture.png).
`CreateHistoryUi.cs` là script authoring một lần ngoài Assets; runtime dùng prefab đã lưu.
`CaptureHistoryUi.cs` chụp giao diện và khôi phục Canvas/camera sau khi chụp.

## Tài nguyên

Font Liberation Sans và giấy phép OFL nằm trong `Assets/TextMesh Pro/Fonts`.
Font SDF tiếng Việt ở `Assets/Fonts/Ludo Vietnamese SDF.asset`.
uGUI, Input System và Newtonsoft JSON sử dụng Unity Package Manager.

Cụm ngựa/xúc xắc: `Assets/Art/Login/ludo-pieces.png`, tạo bằng công cụ imagegen tích hợp.
Prompt và nguồn tài nguyên được ghi ở [art-source.md](Documentation/art-source.md).

## Sửa lỗi tích hợp và kiểm tra hồi quy — 2026-09-22

- Presence cập nhật qua ROOM_UPDATED, độc lập với gameplay stateVersion.
- RECONNECT_RESULT có thêm gameOver tùy chọn: kết quả được khôi phục ngay cả khi
  client chưa từng nhận event GAME_OVER; chỉ chấp nhận đúng room/match.
- Profile và header Lobby cập nhật điểm/số lần hạng nhất từ online snapshot Server.
- Chat escape cả senderDisplayName và message.
- Result có **Chơi tiếp**: gửi READY, chờ Server mở lại WAITING rồi về RoomScene.
  Server lưu kết quả trước khi reset, giữ phòng/slot của thành viên còn lại,
  reset ready, rồi chờ host Start khi mọi người đã Ready.
- `VerifyIntegrationFixes.Main`: 23 kiểm tra đạt trong Edit Mode, dùng preview scene tạm.
- `VerifyGame.Main`, args `[false]`: 61 kiểm tra đạt trong Play Mode từ Login,
  gồm bỏ lỡ GAME_OVER, reconnect, rematch pending/chống lặp và chuyển scene.
  Args `[true]` bật thêm chụp ảnh/kiểm tra biên ở ba độ phân giải; không chạy chế độ
  chụp ảnh trong đợt này. Các số liệu ảnh/assertion cũ phía trên là lịch sử.

Giới hạn kiểm thử:

- `VerifyLoginUi`, `VerifyLobby`, `VerifyRoom` có assertion cũ về scene chưa tồn tại;
  cần cập nhật trước khi dùng làm regression suite cho client đầy đủ.
- Chưa chạy end-to-end với Java/MySQL thật; chưa build desktop player trong đợt này.

Xem [báo cáo tích hợp](../INTEGRATION_REVIEW.md) cho cách tái hiện và checklist kiểm thử.

## Ô đặc biệt — 2026-09-23

Bàn cờ có 16 ô: SPEED (+2 bước), SLOW (lùi ngay 2 bước), LUCKY (+1 lần tung),
TRAP (về chuồng). Bỏ Khiên và trạng thái làm chậm lượt sau. BoardView chỉ render
`specialCells` và `stepCount` do Server gửi; reconnect cũng dùng snapshot này.
Cập nhật Server/client cùng phiên bản; contract bỏ `slowed`, `shielded`, `shieldConsumed`.

Kiểm chứng: Unity 6000.6.2f1 compile thành công, Console không lỗi/cảnh báo.
`VerifySpecialCells.Main` đạt 39 kiểm tra Edit Mode trên prefab thật (ký hiệu 16 ô,
ô Khiên cũ thành ô thường, vị trí bốn màu sau lùi/về chuồng/ra lại/về đích,
snapshot thay layout và chú giải). `VerifyIntegrationFixes.Main` đạt 23 kiểm tra hồi quy.
Chưa chạy end-to-end Unity với Java/MySQL hoặc build desktop player cho thay đổi này.

## Animation và ngựa về đích — 2026-09-23

- Đọc `GameState.lastMove` do Server gửi để animate riêng chặng xúc xắc và chặng +2/−2.
  Dừng nhấn hiệu ứng khoảng 0,38 giây; có nhãn +2/−2/+1/! tại quân.
- Bẫy đi tới ô kích hoạt rồi về chuồng; +2/−2 bị chặn có thông báo riêng.
- Ngựa hoàn thành chạm tâm đích, nhấn sáng rồi chuyển lên khay màu có vương miện và bộ đếm.
- `BoardNotice` tự tắt trong khoảng 2,6 giây, không bắt xác nhận hoặc chặn chuột.
- Reconnect/hụt snapshot đặt lại đúng vị trí, không phát lại animation/thông báo cũ.
  UI refresh giữ animation đang chạy; Result chờ hoàn tất trình diễn cuối trận.
- Authoring: `PolishMovePresentation.Main`, cập nhật đúng prefab hiện có.
- Kiểm chứng: `VerifyMovePresentation.Main` đạt **66 kiểm tra Play Mode**;
  `VerifyGame.Main(false)` đạt **61 kiểm tra TCP localhost**. Engine/JSON/room Java đạt.
- [Ảnh xem trước](Documentation/move-presentation-preview.png) dùng fixture hiển thị,
  không phải dữ liệu trận thật. Chưa kiểm thử end-to-end Unity + Java/MySQL trong đợt này.
