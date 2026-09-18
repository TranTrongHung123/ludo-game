# Chạy Ludo Game

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

Terminal 2 — Client:

```powershell
.\ludo.ps1 client
```

Muốn mở thêm Client, tạo terminal mới và chạy lại lệnh `client`.

## Các lệnh khác

```powershell
.\ludo.ps1 build    # Build toàn bộ dự án
.\ludo.ps1 test     # Chạy toàn bộ test
.\ludo.ps1 status   # Xem trạng thái MySQL
.\ludo.ps1 stop     # Dừng MySQL, giữ nguyên dữ liệu
.\ludo.ps1 help     # Xem trợ giúp
```
