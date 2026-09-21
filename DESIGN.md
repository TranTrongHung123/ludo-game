# DESIGN.md — ĐẶC TẢ THIẾT KẾ UNITY CLIENT

> Dự án: **Ludo Game / Cờ Cá Ngựa Online**  
> Client mới: **Unity 6.3 LTS, 2D, C#**  
> Server giữ nguyên: **Java 21 + TCP Socket + JSON + MySQL**  
> Mục tiêu: chuyển toàn bộ giao diện và luồng tương tác từ JavaFX cũ sang Unity, đồng thời giữ nguyên protocol và hành vi Server hiện có.

---

# 1. Mục tiêu của tài liệu

Tài liệu này là tài liệu chuẩn để Codex triển khai phần `client-unity/`.

Codex phải dùng đồng thời:

- `AGENTS.md` để hiểu luật game, protocol và kiến trúc;
- `client/` JavaFX cũ để biết các chức năng và nút xử lý sự kiện hiện có;
- `common/` để đọc DTO, enum, message type;
- `server/` để hiểu luồng request/response/broadcast thực tế;
- `DESIGN.md` để dựng giao diện Unity đúng phong cách đã chốt.

Nguyên tắc:

```text
JavaFX cũ = nguồn tham khảo chức năng
common + server + AGENTS.md = nguồn sự thật về protocol và logic
DESIGN.md + ảnh đã chốt = nguồn sự thật về giao diện
```

Không được thay đổi logic Server chỉ để làm UI dễ hơn.

---

# 2. Phạm vi migration

Giữ nguyên:

```text
server/
common/
client/          # chỉ dùng làm tham chiếu
```

Triển khai mới:

```text
client-unity/
```

Không xóa JavaFX cũ cho đến khi Unity Client hoàn thiện và test ổn định.

---

# 3. Yêu cầu toàn màn hình

## 3.1 Chuẩn thiết kế chính

Toàn bộ giao diện Unity phải được thiết kế cho:

```text
1920 x 1080
16:9
```

Đây là chuẩn thiết kế chính.

Canvas phải dùng:

```text
UI Scale Mode        = Scale With Screen Size
Reference Resolution = 1920 x 1080
Screen Match Mode    = Match Width Or Height
Match                = 0.5
```

## 3.2 Fullscreen

Tất cả Scene chính phải hiển thị **toàn màn hình**, không bị cắt nội dung chính.

Yêu cầu:

- không để UI chính vượt ra ngoài vùng 1920x1080;
- không dùng ScrollView cho toàn màn hình;
- chỉ dùng ScrollView cho danh sách dài;
- không để nút quan trọng nằm sát mép màn hình;
- không để chat che bàn cờ;
- không để panel bên phải làm bàn cờ bị co quá nhỏ;
- không tạo thanh cuộn toàn Scene.

Khi build `.exe`, giao diện phải phù hợp với:

```text
1920x1080
2560x1440
3840x2160
```

Ưu tiên 16:9.

## 3.3 Scale trong Game View

`Scale = 1x` trong Unity chỉ là mức zoom preview của Editor.

Không được hard-code logic theo `Scale = 1x`.

---

# 4. Phong cách giao diện chung

Tất cả màn hình phải đồng bộ với các ảnh đã chốt.

Phong cách:

- nền trắng / lavender sáng;
- tím pastel là màu chính;
- navy đậm cho tiêu đề;
- card bo góc lớn;
- shadow nhẹ;
- button gradient tím;
- trạng thái thành công dùng xanh lá;
- trạng thái lỗi / rời phòng / bỏ cuộc dùng đỏ;
- icon cờ cá ngựa, vương miện, xúc xắc;
- quân cờ màu đỏ, xanh dương, xanh lá, vàng;
- giao diện vui nhộn nhưng hiện đại;
- tránh phong cách form quản trị.

Không được tự đổi sang dark theme.

---

# 5. Typography

Dùng TextMeshPro.

Hệ thống chữ:

```text
H1: tiêu đề lớn
H2: tiêu đề card
Body: nội dung chính
Caption: mô tả phụ
Status: trạng thái
Button: chữ nút
```

Không bake chữ động vào ảnh.

Các nội dung sau luôn phải là text thật:

- username;
- điểm;
- số lần hạng nhất;
- room code;
- match ID;
- trạng thái Server;
- trạng thái phòng;
- trạng thái người chơi;
- timer;
- chat;
- kết quả;
- lịch sử.

---

# 6. Cấu trúc Scene

```text
Assets/Scenes/
├── LoginScene.unity
├── RegisterScene.unity
├── LobbyScene.unity
├── RoomScene.unity
├── GameScene.unity
├── ResultScene.unity
├── RankingScene.unity
└── HistoryScene.unity
```

Không dùng một Scene duy nhất để chứa toàn bộ game nếu không cần thiết.

---

# 7. LOGIN SCENE

## 7.1 Bố cục

Toàn màn hình 1920x1080.

Chia 2 vùng:

```text
Bên trái  ~55%
Bên phải  ~45%
```

### Bên trái

Có:

- logo vương miện;
- chữ `CỜ CÁ NGỰA`;
- tiêu đề lớn `Ludo Game`;
- mô tả;
- 4 tính năng:
  - Nhiều người chơi;
  - Bảng xếp hạng;
  - Kết nối ổn định;
  - Giải trí lành mạnh;
- cụm quân cờ ngựa + xúc xắc trang trí.

### Bên phải

Card trắng bo góc.

Có:

- `CHÀO MỪNG TRỞ LẠI`;
- tiêu đề `Đăng nhập`;
- mô tả;
- ô `Tên đăng nhập`;
- ô `Mật khẩu`;
- icon hiện/ẩn mật khẩu;
- nút `Đăng nhập`;
- dòng:
  `Chưa có tài khoản? Đăng ký ngay`;
- trạng thái kết nối Game Server.

Không có:

- Ghi nhớ đăng nhập;
- Quên mật khẩu.

## 7.2 Tương tác với Server

### Trạng thái kết nối

Phải lấy từ NetworkClient thật:

```text
Đang kết nối...
Đã kết nối Game Server
Mất kết nối Game Server
```

Không fake.

### Đăng nhập

Khi bấm:

1. validate input;
2. disable nút;
3. gửi request LOGIN;
4. chờ response;
5. nếu lỗi -> hiển thị lỗi;
6. nếu thành công -> lưu session/user;
7. chuyển LobbyScene.

### Đăng ký ngay

Chuyển RegisterScene.

---

# 8. REGISTER SCENE

## 8.1 Bố cục

Giữ cùng nền và bố cục với Login.

Bên trái giống Login.

Bên phải là card đăng ký.

Có:

- `CHÀO MỪNG ĐẾN VỚI LUDO GAME`;
- `Tạo tài khoản`;
- tên đăng nhập;
- tên hiển thị (`displayName`, thay email theo protocol hiện tại);
- mật khẩu;
- xác nhận mật khẩu;
- nút `Đăng ký`;
- `Đã có tài khoản? Đăng nhập ngay`;
- trạng thái kết nối Server.

Nếu protocol đăng ký hiện tại có thêm trường khác, Codex phải đọc server/common và thêm đúng trường cần thiết nhưng giữ style.

Quyết định đã xác nhận: dùng **Tên hiển thị** thay ô email. `REGISTER` hiện nhận
`username`, `displayName`, `password`; xác nhận mật khẩu chỉ được kiểm tra ở Client.

## 8.2 Tương tác Server

- validate confirm password tại client;
- gửi REGISTER request;
- hiển thị server validation;
- không tự xác nhận đăng ký thành công;
- sau thành công chuyển theo flow cũ;
- `Đăng nhập ngay` quay LoginScene.

---

# 9. LOBBY SCENE

## 9.1 Bố cục toàn màn hình

Full HD 1920x1080.

Header trên cùng:

- logo LUDO GAME bên trái;
- `Xin chào, {username}!`;
- `Điểm {score}`;
- `Hạng nhất {winCount}`;
- trạng thái kết nối Game Server;
- nút `Đăng xuất`.

Body chia:

```text
Bên trái ~65%
Bên phải ~35%
```

## 9.2 Bên trái — Người chơi trực tuyến

Card lớn.

Có:

- icon nhóm người;
- tiêu đề `Người chơi trực tuyến`;
- mô tả;
- nút `Làm mới`;
- bảng người chơi;
- dòng tổng số người online ở chính giữa phía dưới.

Header bảng:

```text
#
Người chơi
Trạng thái
Điểm
Số lần hạng nhất
```

Mỗi row:

- số thứ tự;
- icon user;
- username;
- `(Bạn)` nếu là current user;
- trạng thái online/presence;
- điểm;
- số lần hạng nhất.

Danh sách dài -> ScrollRect.

Không hard-code user1/user2.

## 9.3 Bên phải — Phòng chơi

Card `Phòng chơi`.

Có đúng các nút:

- `Tạo phòng`;
- `Bảng xếp hạng`;
- `Lịch sử`;
- input `Mã phòng`;
- `Tham gia`.

Không thêm nút khác.

Nếu có lời mời:

- hiển thị card lời mời;
- người mời;
- room code;
- thời gian hết hạn nếu protocol có;
- `Chấp nhận`;
- `Từ chối`.

Không có lời mời -> ẩn card lời mời.

## 9.4 Tương tác Server

### Làm mới
Lấy lại danh sách người chơi từ Server.

### Tạo phòng
Gửi Create Room request.
Thành công -> RoomScene.

### Tham gia
Gửi Join Room request.
Thành công -> RoomScene.

### Bảng xếp hạng
Mở RankingScene và load dữ liệu Server.

### Lịch sử
Mở HistoryScene và load dữ liệu Server.

### Đăng xuất
Gửi logout/session close theo flow hiện tại.
Quay LoginScene.

### Chấp nhận / Từ chối lời mời
Dùng message type hiện có.
Không invent message.

---

# 10. ROOM SCENE — PHÒNG CHỜ

## 10.1 Bố cục

Full HD.

Header:

- tiêu đề `Phòng chờ`;
- mô tả;
- connection badge;
- nút `Rời phòng`.

Phần thông tin phòng:

- Mã phòng;
- nút copy;
- Chủ phòng;
- Trạng thái phòng.

Main body chia:

```text
Bên trái: 4 slot người chơi
Bên phải: danh sách người đang rỗi + điều khiển
```

## 10.2 4 slot người chơi

4 card theo màu:

```text
Slot 0 = Đỏ
Slot 1 = Xanh dương
Slot 2 = Vàng
Slot 3 = Xanh lá
```

Lấy mapping thật từ code nếu khác.

Mỗi slot có:

- username;
- màu;
- host badge nếu chủ phòng;
- ready status;
- presence;
- icon/horse decoration.

Slot trống:

- ghi `Chưa có người chơi`;
- không fake user.

## 10.3 Panel người đang rỗi

Có:

- tiêu đề `Người đang rỗi`;
- danh sách người có thể mời;
- mỗi row:
  - username;
  - điểm;
  - nút `Mời`.

Cuối panel:

- nút `Gửi lời mời`.

## 10.4 Nút xử lý sự kiện

Giữ đúng:

- `Rời phòng`;
- `Mời`;
- `Gửi lời mời`;
- `Sẵn sàng`;
- `Bắt đầu trận`.

Không thêm bớt.

## 10.5 Logic Server

### Sẵn sàng
Gửi request ready.

### Bắt đầu trận
Chỉ enable khi Server/logic cho phép.

### Rời phòng
Gửi leave room request.
Server xác nhận -> LobbyScene.

### Mời
Gửi invite request.

### Host
Không client tự chuyển host.
Render theo Server broadcast.

---

# 11. GAME SCENE — MÀN CHƠI

## 11.1 Bàn cờ

Bắt buộc dùng:

```text
Bàn cờ vuông truyền thống
```

Không dùng bàn tròn.

Bàn cờ phải nằm ở trung tâm và là thành phần lớn nhất.

## 11.2 Bố cục tổng thể

```text
Trái: player list
Giữa: bàn cờ
Phải trên: turn + timer + xúc xắc + action
Phải dưới: chat
```

Chat tuyệt đối không che bàn cờ.

## 11.3 Header

Có:

- logo nhỏ;
- phòng;
- room ID nếu cần;
- trạng thái trận;
- connection badge;
- `Rời phòng` hoặc action đúng flow hiện có nếu có.

## 11.4 Player list

4 card:

- username;
- màu;
- trạng thái;
- số quân;
- disconnect indicator;
- current turn highlight.

Không cần avatar ảnh người thật.

Dùng icon user hoặc màu người chơi.

## 11.5 Bàn cờ

Render:

- yard 4 màu;
- common track;
- finish track;
- center;
- special cells;
- piece positions.

Server authoritative.

Client không tự quyết định:

- spawn;
- capture;
- shield;
- special cell;
- legal move;
- finish;
- ranking.

## 11.6 Turn panel

Có:

- `Lượt hiện tại`;
- username;
- màu;
- timer;
- phase;
- dice state.

Timer lấy từ Server.

Không hard-code 25s/20s/3 phút.

## 11.7 Dice panel

Có:

- dice sprite;
- text;
- nút `Đổ xúc xắc`.

Khi bấm:

- gửi Roll request;
- chờ Server trả dice;
- animate;
- kết thúc đúng value Server trả.

## 11.8 Di chuyển quân

Có đúng action hiện có:

- chọn quân legal;
- `Di chuyển quân` nếu flow cũ dùng button;
- hoặc click quân nếu có thể map 1:1 mà không thay protocol.

Legal piece phải do Server xác nhận/state cho phép.

## 11.9 Bỏ cuộc

Giữ nút:

```text
Bỏ cuộc
```

Gửi request thật.

Không thay bằng local leave.

## 11.10 Chat

Khung chat nằm bên phải dưới.

Có:

- tiêu đề `Trò chuyện`;
- danh sách tin nhắn;
- username;
- nội dung;
- timestamp nếu có;
- input;
- nút gửi.

Không có quick chat.

Không che bàn cờ.

Message phải đi Server/broadcast thật.

## 11.11 Animation

### Dice
- xoay;
- bounce;
- glow;
- 0.5–1s.

### Piece
- di chuyển từng ô;
- easing;
- bounce khi đáp.

### Capture
- flash nhỏ;
- quân bị bắt về yard.

### Shield
- shield flash.

### Finish
- sparkle/pulse.

### Turn change
- highlight player card.

Animation không thay đổi game state.

---

# 12. RESULT SCENE

## 12.1 Bố cục

Full screen.

Header:

- logo;
- crown/confetti;
- tiêu đề:
  `Bạn về hạng X!`;
- match ID;
- sync Server badge.

Card lớn:

```text
Kết quả chính thức
```

Bảng gồm:

```text
Hạng
Người chơi
Trạng thái
Điểm nhận
Tổng điểm
```

## 12.2 Dynamic data

Tất cả lấy Server:

- placement;
- username;
- color;
- completed/forfeited;
- score gained;
- total score.

## 12.3 Nút

Giữ đúng:

```text
Về sảnh
```

Không thêm.

---

# 13. RANKING SCENE

## 13.1 Bố cục

Full screen.

Header:

- `Về sảnh`;
- `Bảng xếp hạng`;
- mô tả;
- connection badge;
- `Làm mới`.

Body:

- bảng ranking lớn;
- Top 1/2/3 highlight.

Columns:

```text
Hạng
Người chơi
Tổng điểm
Số lần hạng nhất
```

## 13.2 Nút

Giữ đúng:

- `Về sảnh`;
- `Làm mới`.

Không thêm.

## 13.3 Server

Refresh phải load ranking thật.

---

# 14. HISTORY SCENE

## 14.1 Bố cục

Full screen.

Header:

- `Về sảnh`;
- `Lịch sử trận đấu`;
- thống kê;
- connection badge;
- `Làm mới`.

Body:

- bảng lịch sử.

Columns:

```text
Kết quả
Thời gian và trận đấu
Điểm nhận
Trạng thái
```

Mỗi row:

- hạng;
- thời gian;
- màu quân;
- match ID;
- score;
- completed/forfeited.

Danh sách dài -> ScrollRect.

## 14.2 Nút

Giữ đúng:

- `Về sảnh`;
- `Làm mới`.

Không thêm.

---

# 15. Các tương tác Server bắt buộc phải giữ

Codex phải kiểm tra JavaFX cũ và giữ nguyên mọi chức năng tương ứng.

Danh sách tối thiểu:

```text
Login
Register
Logout

Refresh online players
Create room
Join room
Accept invitation
Decline invitation
Open ranking
Open history

Invite player
Ready
Start match
Leave room

Roll dice
Move piece
Forfeit
Send chat

Return to lobby
Refresh ranking
Refresh history
```

Không thêm action Server mới nếu không có protocol.

---

# 16. Static UI và Dynamic UI

## Static UI

Có thể dựng cố định:

- background;
- decoration;
- logo;
- title;
- card;
- border;
- icon;
- button visual;
- table header.

## Dynamic UI

Bắt buộc bind từ state:

- username;
- điểm;
- hạng nhất;
- player list;
- room code;
- host;
- ready;
- room state;
- timer;
- dice;
- piece position;
- legal moves;
- chat;
- result;
- ranking;
- history;
- connection state.

Không hard-code sample value trong ảnh.

---

# 17. NetworkSession

Nên có object:

```text
NetworkSession
```

hoặc tương đương.

Dùng:

```text
DontDestroyOnLoad
```

Giữ một TCP connection xuyên các Scene.

Không mở TCP mới mỗi lần đổi Scene nếu kiến trúc hiện tại không yêu cầu.

---

# 18. Cấu trúc code gợi ý

```text
Scripts/
├── Network/
│   ├── NetworkClient.cs
│   ├── MessageRouter.cs
│   └── MainThreadDispatcher.cs
├── Services/
│   ├── SessionService.cs
│   └── SceneNavigationService.cs
├── Controllers/
│   ├── LoginController.cs
│   ├── RegisterController.cs
│   ├── LobbyController.cs
│   ├── RoomController.cs
│   ├── GameController.cs
│   ├── ResultController.cs
│   ├── RankingController.cs
│   └── HistoryController.cs
└── Views/
    ├── ConnectionStatusView.cs
    ├── OnlinePlayerRowView.cs
    ├── RoomSlotView.cs
    ├── PlayerCardView.cs
    ├── BoardView.cs
    ├── PieceView.cs
    ├── DiceView.cs
    ├── ChatPanelView.cs
    ├── ResultRowView.cs
    ├── RankingRowView.cs
    └── HistoryRowView.cs
```

Không bắt buộc y hệt nhưng phải tách trách nhiệm rõ.

---

# 19. Prefab nên có

```text
Prefabs/Common/
- PrimaryButton
- SecondaryButton
- DangerButton
- ConnectionBadge

Prefabs/Lobby/
- OnlinePlayerRow
- InvitationCard

Prefabs/Room/
- RoomSlot
- InvitePlayerRow

Prefabs/Game/
- PlayerCard
- Piece
- ChatMessage
- Dice

Prefabs/Result/
- ResultRow

Prefabs/Ranking/
- RankingRow

Prefabs/History/
- HistoryRow
```

---

# 20. Không được tạo giant builder

Không tạo:

```text
LoginSceneBuilder.cs 800 dòng
LobbySceneBuilder.cs 1000 dòng
GameSceneBuilder.cs 2000 dòng
```

UI phải tồn tại trong:

```text
Scene
Prefab
```

C# chỉ:

- xử lý state;
- bind dữ liệu;
- nhận event;
- gửi request;
- chơi animation.

---

# 21. State loading / pending

Mỗi action Server cần trạng thái pending.

Ví dụ:

```text
Login pending -> disable Login
Create pending -> disable Create
Join pending -> disable Join
Roll pending -> disable Roll
Ready pending -> disable Ready
Refresh pending -> tránh spam
```

Nếu Server trả lỗi:

- hiển thị lỗi bằng tiếng Việt;
- không crash;
- không khóa toàn app.

---

# 22. Quy tắc dữ liệu

Không hard-code:

```text
user1
user2
test
player3
room UUID mẫu
match UUID mẫu
điểm mẫu
timer mẫu
dice mẫu
ranking mẫu
```

Ảnh chỉ là visual reference.

---

# 23. Quy trình Codex phải làm

Trước mỗi Scene:

1. đọc JavaFX cũ;
2. liệt kê nút/sự kiện hiện có;
3. tìm message type liên quan;
4. đọc ảnh UI tương ứng;
5. xác định static/dynamic;
6. tạo scene/prefab;
7. bind network;
8. compile;
9. test;
10. không sửa screen khác nếu không cần.

Trước khi code, Codex phải ghi ngắn:

```text
File sẽ tạo/sửa
Sự kiện JavaFX được giữ
Message protocol sử dụng
Dữ liệu động từ Server
Scene transition
```

---

# 24. Definition of Done

Migration hoàn thành khi:

- có đủ Login;
- Register;
- Lobby;
- Room;
- Game;
- Result;
- Ranking;
- History;
- giao diện đồng bộ style;
- Full HD 1920x1080 không bị cắt;
- responsive 1440p và 4K;
- Game dùng bàn cờ vuông truyền thống;
- Chat không che board;
- mọi action JavaFX cũ vẫn hoạt động;
- không fake state;
- Server vẫn authoritative;
- TCP framing đúng;
- không có Missing Reference;
- không compile error;
- không giant scene-builder script.

---

# 25. Chỉ thị cuối cho Codex

Không được hiểu nhiệm vụ này là:

```text
"vẽ UI giống screenshot"
```

Mà phải hiểu là:

```text
JavaFX cũ
  +
Protocol hiện tại
  +
Server authoritative
  +
Giao diện đã chốt
        ↓
Unity 2D Client hoàn chỉnh
```

Ưu tiên theo thứ tự:

1. đúng chức năng;
2. đúng protocol;
3. đúng state Server;
4. đúng giao diện;
5. animation;
6. polish.
