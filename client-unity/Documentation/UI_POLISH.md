# Cập nhật giao diện — 2026-09-22

- Con trỏ nhập liệu đậm, rộng 3 px; viewport/text/caret đồng nhất ở đăng nhập, đăng ký và chat.
- Đăng nhập có IP/tên máy chủ và port, nút Kết nối; kiểm tra port 1–65535. Endpoint dùng chung cho đăng ký và các scene tiếp theo.
- Trạng thái server nằm trong khung bo góc, căn giữa: xanh khi kết nối, vàng khi đang kết nối, đỏ khi mất kết nối.
- Ẩn số người chơi trên bảng xếp hạng, nhãn giờ địa phương trong lịch sử và hai thông báo cập nhật thành công. Vẫn hiển thị lỗi tải dữ liệu.
- Chat có vùng cuộn, tự xuống tin mới, cho phép cuộn xem tin cũ; giữ tối đa 200 tin.
- Chuồng thu gọn để không chồng đường đi; chuồng của mình có nhãn BẠN và viền đổi độ sáng.
- Quân ngựa bằng uGUI mesh, tô màu tương ứng, bỏ số quân; giữ dấu hiệu nước đi hợp lệ và hiệu ứng quân.
- Mũi tên theo thứ tự ô của từng màu; xúc xắc có hiệu ứng đổi mặt/lắc và chốt đúng kết quả server.
- Nút rời phòng màu hồng đỏ; chủ phòng có viền vàng/nền riêng và cập nhật khi chuyển chủ phòng.
- Server: 120 giây Roll và 120 giây Move, mỗi phase độc lập; cập nhật AGENTS.md và kiểm thử deadline. Cần khởi động lại server bằng bản code mới để áp dụng.

## Kiểm chứng

- Unity 6000.6.2f1 biên dịch thành công; prefab/controller đã kiểm tra tham chiếu.
- `VerifyGame.Main(false)`: 61 kiểm tra game/result qua TCP localhost đạt.
- `VerifyUiPolish.Main()`: 210 kiểm tra đạt, gồm port tùy chọn, port sai, 16 mesh ngựa, bốn hướng mũi tên, chuồng không chồng 48 ô, 80 tin nhắn, cuộn chat và xúc xắc.
- Maven common/server: 95 test đạt, 4 test tích hợp MySQL bỏ qua do chưa bật `RUN_MYSQL_INTEGRATION_TESTS`.
- Kiểm tra thêm deadline Roll/Move tại 119.999 giây và 120 giây đạt.
- Ảnh kiểm tra: `game-1920x1080-login-focused.png` và `ui-polish-game.png` (dữ liệu giả lập giao diện).

Chưa chạy một trận nhiều máy qua LAN hoặc xuất bản build standalone trong đợt này.
