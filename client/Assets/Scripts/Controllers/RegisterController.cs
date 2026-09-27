using System;
using Ludo.Services;
using TMPro;
using UnityEngine;
using UnityEngine.InputSystem;
using UnityEngine.SceneManagement;

namespace Ludo.Controllers
{
    public sealed class RegisterController : MonoBehaviour
    {
        [SerializeField] private TMP_InputField usernameField;
        [SerializeField] private TMP_InputField displayNameField;
        [SerializeField] private TMP_InputField passwordField;
        [SerializeField] private TMP_InputField confirmPasswordField;
        [SerializeField] private UnityEngine.UI.Button registerButton;
        [SerializeField] private UnityEngine.UI.Button loginButton;
        [SerializeField] private UnityEngine.UI.Button visibilityButton;
        [SerializeField] private UnityEngine.UI.Button retryButton;
        [SerializeField] private TMP_Text registerLabel;
        [SerializeField] private TMP_Text visibilityLabel;
        [SerializeField] private TMP_Text connectionLabel;
        [SerializeField] private TMP_Text feedbackLabel;
        [SerializeField] private UnityEngine.UI.Image connectionDot;
        [SerializeField] private string loginScene = "LoginScene";
        private NetworkSession session;
        private TMP_InputField[] fields;
        private bool pending;
        private bool completed;

        // Gắn sự kiện form và hiển thị trạng thái kết nối khi mở màn hình.
        private async void Start()
        {
            fields = new[] { usernameField, displayNameField, passwordField, confirmPasswordField };
            session = NetworkSession.Instance;
            if (session == null) { Feedback("Chưa cấu hình kết nối Game Server.", true); registerButton.interactable = false; return; }
            registerButton.onClick.AddListener(Submit);
            loginButton.onClick.AddListener(OpenLogin);
            visibilityButton.onClick.AddListener(TogglePassword);
            retryButton.onClick.AddListener(Retry);
            foreach (var field in fields) field.onSubmit.AddListener(SubmitFromField);
            usernameField.text = session.AuthUsername;
            session.StateChanged += RenderConnection;
            RenderConnection(session.State);
            (string.IsNullOrEmpty(usernameField.text) ? usernameField : displayNameField).ActivateInputField();
            await session.ConnectAsync();
        }

        // Xử lý chuyển ô nhập bằng bàn phím trong form.
        private void Update()
        {
            if (pending || completed || fields == null || Keyboard.current == null || !Keyboard.current.tabKey.wasPressedThisFrame) return;
            int step = Keyboard.current.shiftKey.isPressed ? -1 : 1;
            for (int i = 0; i < fields.Length; i++)
                if (fields[i].isFocused) { fields[(i + step + fields.Length) % fields.Length].ActivateInputField(); break; }
        }

        // Kiểm tra dữ liệu nhập để báo lỗi sớm; Server vẫn xác thực lại yêu cầu.
        public static string Validate(string username, string displayName, string password, string confirmation)
        {
            string error = LoginController.Validate(username, password);
            if (error != null) return error;
            if (string.IsNullOrWhiteSpace(displayName)) return "Vui lòng nhập tên hiển thị.";
            if (displayName != displayName.Trim() || displayName.Length > 100)
                return "Tên hiển thị không hợp lệ (tối đa 100 ký tự).";
            if (password != confirmation) return "Mật khẩu xác nhận không khớp.";
            return null;
        }

        private void SubmitFromField(string _) => Submit();
        // Khóa form trong khi gửi và kiểm tra controller còn tồn tại sau mỗi await.
        public async void Submit()
        {
            if (pending || completed || session == null || session.SessionId != null) return;
            Ludo.Services.AudioManager.Instance?.PlaySfx(Ludo.Services.SfxClip.ButtonClick);
            string error = Validate(usernameField.text, displayNameField.text, passwordField.text, confirmPasswordField.text);
            if (error != null) { Feedback(error, true); return; }
            if (session.State != ConnectionState.Connected) { Feedback("Vui lòng kết nối Game Server trước.", true); return; }
            string username = usernameField.text;
            pending = true;
            RefreshControls();
            Feedback("Đang tạo tài khoản...", false);
            try
            {
                var result = await session.RegisterAsync(username, displayNameField.text, passwordField.text);
                if (this == null) return;
                ClearPasswords();
                if ((bool)result["success"])
                {
                    completed = true;
                    Ludo.Services.AudioManager.Instance?.PlaySfx(Ludo.Services.SfxClip.Confirm);
                    session.AuthUsername = username;
                    session.AuthNotice = "Đăng ký thành công. Bạn có thể đăng nhập ngay.";
                    if (Application.CanStreamedLevelBeLoaded(loginScene)) SceneManager.LoadScene(loginScene);
                    else Feedback(session.AuthNotice, false);
                }
                else Feedback(ErrorMessage((string)result["error"]?["code"]), true);
            }
            catch (Exception exception)
            {
                if (this == null) return;
                ClearPasswords();
                Feedback(exception is TimeoutException
                    ? "Chưa nhận được xác nhận. Hãy kết nối lại và thử đăng nhập trước khi đăng ký lại."
                    : "Kết nối bị gián đoạn. Vui lòng thử lại.", true);
            }
            finally { if (this != null) { pending = false; RefreshControls(); } }
        }

        // Đổi mã lỗi Server thành thông báo tiếng Việt cho người dùng.
        private static string ErrorMessage(string code)
        {
            switch (code)
            {
                case "USERNAME_ALREADY_EXISTS": return "Tên đăng nhập đã được sử dụng. Vui lòng chọn tên khác.";
                case "INVALID_REQUEST": return "Thông tin đăng ký chưa hợp lệ. Vui lòng kiểm tra lại.";
                case "UNAUTHORIZED": return "Không thể đăng ký trong phiên hiện tại.";
                default: return "Server chưa thể tạo tài khoản. Vui lòng thử lại.";
            }
        }

        // Đổi chế độ hiển thị mật khẩu mà giữ nguyên dữ liệu đang nhập.
        public void TogglePassword()
        {
            Ludo.Services.AudioManager.Instance?.PlaySfx(Ludo.Services.SfxClip.ButtonClick);
            bool reveal = passwordField.contentType == TMP_InputField.ContentType.Password;
            foreach (var field in new[] { passwordField, confirmPasswordField })
            {
                field.contentType = reveal ? TMP_InputField.ContentType.Standard : TMP_InputField.ContentType.Password;
                field.ForceLabelUpdate();
            }
            visibilityLabel.text = reveal ? "Ẩn" : "Hiện";
        }

        // Quay về đăng nhập và giữ thông tin cần thiết để người dùng tiếp tục.
        public void OpenLogin()
        {
            if (pending || session == null) return;
            Ludo.Services.AudioManager.Instance?.PlaySfx(Ludo.Services.SfxClip.ButtonClick);
            session.AuthUsername = usernameField.text;
            ClearPasswords();
            if (Application.CanStreamedLevelBeLoaded(loginScene)) SceneManager.LoadScene(loginScene);
            else Feedback("Chưa tìm thấy màn hình đăng nhập.", true);
        }
        // Xóa cả mật khẩu và xác nhận sau khi kết thúc thao tác.
        private void ClearPasswords() { passwordField.text = ""; confirmPasswordField.text = ""; }
        // Thử kết nối lại khi người dùng yêu cầu.
        private async void Retry() { Ludo.Services.AudioManager.Instance?.PlaySfx(Ludo.Services.SfxClip.ButtonClick); if (session != null) await session.ConnectAsync(); }
        // Hiển thị kết nối và cập nhật khả năng gửi form.
        private void RenderConnection(ConnectionState state)
        {
            connectionLabel.text = state == ConnectionState.Connected ? "Đã kết nối Game Server" :
                state == ConnectionState.Connecting ? "Đang kết nối..." : "Mất kết nối Game Server";
            connectionDot.color = state == ConnectionState.Connected ? new Color32(47, 164, 119, 255) :
                state == ConnectionState.Connecting ? new Color32(214, 158, 54, 255) : new Color32(211, 88, 106, 255);
            retryButton.gameObject.SetActive(state == ConnectionState.Disconnected);
            RefreshControls();
        }
        // Khóa các nút và ô nhập khi yêu cầu đang chờ hoặc chưa kết nối.
        private void RefreshControls()
        {
            bool editable = !pending && !completed && session != null && session.SessionId == null;
            registerButton.interactable = editable && session.State == ConnectionState.Connected;
            registerLabel.text = pending ? "Đang tạo tài khoản..." : completed ? "Đã tạo tài khoản" : "Đăng ký";
            if (fields != null) foreach (var field in fields) field.interactable = editable;
            visibilityButton.interactable = editable;
            loginButton.interactable = !pending;
        }
        // Hiển thị thông báo thành công hoặc lỗi với màu tương ứng.
        private void Feedback(string message, bool error)
        {
            feedbackLabel.text = message;
            feedbackLabel.color = error ? new Color32(185, 58, 78, 255) : new Color32(91, 77, 143, 255);
            if (error) Ludo.Services.AudioManager.Instance?.PlaySfx(Ludo.Services.SfxClip.ErrorSoft);
        }
        // Gỡ theo dõi kết nối khi màn hình đăng nhập hoặc đăng ký bị hủy.
        private void OnDestroy() { if (session != null) session.StateChanged -= RenderConnection; }
    }
}
