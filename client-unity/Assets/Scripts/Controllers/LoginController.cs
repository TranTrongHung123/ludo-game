using System;
using System.Text;
using Ludo.Services;
using TMPro;
using UnityEngine;
using UnityEngine.InputSystem;
using UnityEngine.SceneManagement;

namespace Ludo.Controllers
{
    public sealed class LoginController : MonoBehaviour
    {
        [SerializeField] private TMP_InputField usernameField;
        [SerializeField] private TMP_InputField passwordField;
        [SerializeField] private TMP_InputField serverHostField, serverPortField;
        [SerializeField] private UnityEngine.UI.Button loginButton;
        [SerializeField] private UnityEngine.UI.Button registerButton;
        [SerializeField] private UnityEngine.UI.Button visibilityButton;
        [SerializeField] private UnityEngine.UI.Button retryButton;
        [SerializeField] private TMP_Text loginLabel;
        [SerializeField] private TMP_Text visibilityLabel;
        [SerializeField] private TMP_Text connectionLabel;
        [SerializeField] private TMP_Text feedbackLabel;
        [SerializeField] private UnityEngine.UI.Image connectionDot;
        [SerializeField] private string lobbyScene = "LobbyScene";
        [SerializeField] private string registerScene = "RegisterScene";
        private NetworkSession session;
        private bool pending;

        private async void Start()
        {
            AudioManager.EnsureInstance();
            session = NetworkSession.Instance;
            if (session == null) { Feedback("Chưa cấu hình kết nối Game Server.", true); loginButton.interactable = false; return; }
            loginButton.onClick.AddListener(Submit);
            registerButton.onClick.AddListener(OpenRegister);
            visibilityButton.onClick.AddListener(TogglePassword);
            retryButton.onClick.AddListener(Retry);
            usernameField.onSubmit.AddListener(SubmitFromField);
            passwordField.onSubmit.AddListener(SubmitFromField);
            session.StateChanged += RenderConnection;
            RenderConnection(session.State);
            usernameField.text = session.AuthUsername;
            serverHostField.text = session.ServerHost;
            serverPortField.text = session.ServerPort.ToString();
            if (!string.IsNullOrEmpty(session.AuthNotice))
            {
                Feedback(session.AuthNotice, false);
                session.AuthNotice = "";
            }
            usernameField.ActivateInputField();
            await session.ConnectAsync();
        }

        private void Update()
        {
            if (pending || Keyboard.current == null || !Keyboard.current.tabKey.wasPressedThisFrame) return;
            if (usernameField.isFocused) passwordField.ActivateInputField();
            else if (passwordField.isFocused) serverHostField.ActivateInputField();
            else if (serverHostField.isFocused) serverPortField.ActivateInputField();
            else if (serverPortField.isFocused) usernameField.ActivateInputField();
        }

        public static string Validate(string username, string password)
        {
            if (string.IsNullOrWhiteSpace(username)) return "Vui lòng nhập tên đăng nhập.";
            if (username != username.Trim() || username.Length > 50) return "Tên đăng nhập không hợp lệ (tối đa 50 ký tự).";
            if (string.IsNullOrWhiteSpace(password)) return "Vui lòng nhập mật khẩu.";
            if (Encoding.UTF8.GetByteCount(password) > 72) return "Mật khẩu không được vượt quá 72 byte UTF-8.";
            return null;
        }

        private void SubmitFromField(string _) => Submit();
        public async void Submit()
        {
            if (pending || session == null || session.SessionId != null) return;
            Ludo.Services.AudioManager.Instance?.PlaySfx(Ludo.Services.SfxClip.ButtonClick);
            string error = Validate(usernameField.text, passwordField.text);
            if (error != null) { Feedback(error, true); return; }
            string host = serverHostField.text.Trim();
            if (Uri.CheckHostName(host) == UriHostNameType.Unknown || !int.TryParse(serverPortField.text, out int port) || port < 1 || port > 65535)
            { Feedback("Nhập IP/tên máy chủ hợp lệ và port từ 1 đến 65535.", true); return; }
            if (session.State != ConnectionState.Connected) { Feedback("Vui lòng kết nối Game Server trước.", true); return; }
            pending = true;
            RefreshControls();
            Feedback("Đang đăng nhập...", false);
            try
            {
                if (host != session.ServerHost || port != session.ServerPort)
                    await session.ChangeServerAsync(host, port);
                if (this == null) return;
                if (session.State != ConnectionState.Connected) { Feedback("Không kết nối được máy chủ đã chọn.", true); return; }
                var result = await session.LoginAsync(usernameField.text, passwordField.text);
                if (this == null) return;
                passwordField.text = "";
                if (session.SessionId != null)
                {
                    Ludo.Services.AudioManager.Instance?.PlaySfx(Ludo.Services.SfxClip.Confirm);
                    if (Application.CanStreamedLevelBeLoaded(lobbyScene)) SceneManager.LoadScene(lobbyScene);
                    else Feedback("Đăng nhập thành công. Sảnh chơi sẽ được bổ sung ở bước tiếp theo.", false);
                }
                else Feedback(ErrorMessage((string)result["error"]?["code"]), true);
            }
            catch (Exception exception)
            {
                if (this == null) return;
                passwordField.text = "";
                Feedback(exception is TimeoutException ? "Server phản hồi quá lâu. Vui lòng kết nối lại." : "Kết nối bị gián đoạn. Vui lòng thử lại.", true);
            }
            finally
            {
                if (this != null) { pending = false; RefreshControls(); }
            }
        }

        private static string ErrorMessage(string code)
        {
            switch (code)
            {
                case "INVALID_CREDENTIALS": return "Tên đăng nhập hoặc mật khẩu không đúng.";
                case "ACCOUNT_ALREADY_LOGGED_IN": return "Tài khoản này đang đăng nhập ở nơi khác.";
                case "INVALID_REQUEST": return "Thông tin đăng nhập chưa hợp lệ.";
                case "UNAUTHORIZED": case "SESSION_EXPIRED": return "Phiên đăng nhập không còn hợp lệ.";
                default: return "Server không thể xử lý yêu cầu lúc này. Vui lòng thử lại.";
            }
        }

        public void TogglePassword()
        {
            Ludo.Services.AudioManager.Instance?.PlaySfx(Ludo.Services.SfxClip.ButtonClick);
            bool reveal = passwordField.contentType == TMP_InputField.ContentType.Password;
            passwordField.contentType = reveal ? TMP_InputField.ContentType.Standard : TMP_InputField.ContentType.Password;
            visibilityLabel.text = reveal ? "Ẩn" : "Hiện";
            passwordField.ForceLabelUpdate();
            passwordField.ActivateInputField();
        }

        private void OpenRegister()
        {
            if (pending || session == null || session.SessionId != null) return;
            Ludo.Services.AudioManager.Instance?.PlaySfx(Ludo.Services.SfxClip.ButtonClick);
            session.AuthUsername = usernameField.text;
            session.AuthNotice = "";
            if (Application.CanStreamedLevelBeLoaded(registerScene)) SceneManager.LoadScene(registerScene);
            else Feedback("Màn hình đăng ký sẽ được bổ sung ở bước tiếp theo.", false);
        }

        private async void Retry()
        {
            if (session == null || pending || session.State == ConnectionState.Connecting) return;
            Ludo.Services.AudioManager.Instance?.PlaySfx(Ludo.Services.SfxClip.ButtonClick);
            string host = serverHostField.text.Trim();
            if (Uri.CheckHostName(host) == UriHostNameType.Unknown || !int.TryParse(serverPortField.text, out int port) || port < 1 || port > 65535)
            { Feedback("Nhập IP/tên máy chủ hợp lệ và port từ 1 đến 65535.", true); return; }
            await session.ChangeServerAsync(host, port);
        }
        private void RenderConnection(ConnectionState state)
        {
            connectionLabel.text = state == ConnectionState.Connected ? "Đã kết nối Game Server" :
                state == ConnectionState.Connecting ? "Đang kết nối..." : "Mất kết nối Game Server";
            connectionDot.color = state == ConnectionState.Connected ? new Color32(47, 164, 119, 255) :
                state == ConnectionState.Connecting ? new Color32(214, 158, 54, 255) : new Color32(211, 88, 106, 255);
            retryButton.gameObject.SetActive(true);
            retryButton.interactable = !pending && state != ConnectionState.Connecting;
            RefreshControls();
        }

        private void RefreshControls()
        {
            bool authenticated = session != null && session.SessionId != null;
            loginButton.interactable = !pending && !authenticated && session != null && session.State == ConnectionState.Connected;
            loginLabel.text = pending ? "Đang đăng nhập..." : authenticated ? "Đã đăng nhập" : "Đăng nhập";
            usernameField.interactable = passwordField.interactable = !pending && !authenticated;
            registerButton.interactable = visibilityButton.interactable = !pending && !authenticated;
            serverHostField.interactable = serverPortField.interactable = !pending && !authenticated && session.State != ConnectionState.Connecting;
            retryButton.interactable = !pending && !authenticated && session.State != ConnectionState.Connecting;
        }

        private void Feedback(string message, bool error)
        {
            feedbackLabel.text = message;
            feedbackLabel.color = error ? new Color32(185, 58, 78, 255) : new Color32(91, 77, 143, 255);
            if (error) Ludo.Services.AudioManager.Instance?.PlaySfx(Ludo.Services.SfxClip.ErrorSoft);
        }

        private void OnDestroy()
        {
            if (session != null) session.StateChanged -= RenderConnection;
        }
    }
}
