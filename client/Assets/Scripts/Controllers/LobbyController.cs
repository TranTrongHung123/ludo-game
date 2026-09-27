using System;
using System.Collections.Generic;
using System.IO;
using System.Threading.Tasks;
using Ludo.Services;
using Ludo.Views;
using Newtonsoft.Json.Linq;
using TMPro;
using UnityEngine;
using UnityEngine.SceneManagement;

namespace Ludo.Controllers
{
    public sealed class LobbyController : MonoBehaviour
    {
        [SerializeField] private TMP_Text welcome, score, wins, connection, feedback, total, empty, invitationText;
        [SerializeField] private UnityEngine.UI.Image connectionDot;
        [SerializeField] private TMP_InputField roomCode;
        [SerializeField] private UnityEngine.UI.Button refreshButton, createButton, joinButton, rankingButton, historyButton, logoutButton, acceptButton, rejectButton;
        [SerializeField] private RectTransform rows;
        [SerializeField] private OnlinePlayerRow rowPrefab;
        [SerializeField] private GameObject invitationCard;
        [SerializeField] private string roomScene = "RoomScene", rankingScene = "RankingScene", historyScene = "HistoryScene";
        private readonly List<OnlinePlayerRow> renderedRows = new List<OnlinePlayerRow>();
        private NetworkSession session;
        private JArray renderedPlayers;
        private bool busy, refreshing, navigating;
        private long displayedSecond;

        // Khởi tạo sảnh và theo dõi trạng thái phiên, phòng cùng danh sách online.
        private void Start()
        {
            AudioManager.EnsureInstance();
            session = NetworkSession.Instance;
            if (session == null || session.SessionId == null || session.Profile == null) { SceneManager.LoadScene("LoginScene"); return; }
            welcome.text = "Xin chào, " + ((string)session.Profile["displayName"] ?? (string)session.Profile["username"]) + "!";
            score.text = "Điểm  " + session.Profile["totalScore"];
            wins.text = "Hạng nhất  " + session.Profile["firstPlaceCount"];
            refreshButton.onClick.AddListener(Refresh);
            createButton.onClick.AddListener(CreateRoom); joinButton.onClick.AddListener(JoinRoom);
            rankingButton.onClick.AddListener(OpenRanking); historyButton.onClick.AddListener(OpenHistory);
            logoutButton.onClick.AddListener(Logout); acceptButton.onClick.AddListener(AcceptInvitation); rejectButton.onClick.AddListener(RejectInvitation);
            roomCode.onSubmit.AddListener(_ => JoinRoom());
            session.LobbyChanged += Render; session.StateChanged += ConnectionChanged;
            Render(); ConnectionChanged(session.State);
        }
        // Cập nhật thời hạn lời mời đang hiển thị.
        private void Update()
        {
            long second = DateTimeOffset.UtcNow.ToUnixTimeSeconds();
            if (session != null && second != displayedSecond) { displayedSecond = second; RenderInvitation(); }
        }
        // Điều hướng theo phiên đã khôi phục và làm mới điều khiển khi đổi kết nối.
        private void ConnectionChanged(ConnectionState state)
        {
            if (session.SessionId == null) { if (!navigating) { navigating = true; SceneManager.LoadScene("LoginScene"); } return; }
            connection.text = state == ConnectionState.Connected ? "Đã kết nối Game Server" : state == ConnectionState.Connecting ? "Đang khôi phục kết nối..." : "Mất kết nối • Đang thử lại";
            connectionDot.color = state == ConnectionState.Connected ? new Color32(47,164,119,255) : new Color32(211,88,106,255);
            if (state == ConnectionState.Connected) { Render(); Refresh(); }
            Controls();
        }
        // Tải lại danh sách online và ngăn nhiều lần tải chồng nhau.
        public async void Refresh()
        {
            if (refreshing || navigating || session == null || session.State != ConnectionState.Connected) return;
            Ludo.Services.AudioManager.Instance?.PlaySfx(Ludo.Services.SfxClip.ButtonClick);
            refreshing = true; Controls(); empty.text = "Đang tải danh sách người chơi...";
            try { await session.RefreshOnlineAsync(); if (this != null) { empty.text = "Chưa có người chơi trực tuyến."; Render(); } }
            catch (Exception e) { if (this != null) { empty.text = "Chưa tải được danh sách. Hãy thử làm mới."; Notice(Error(e), true); } }
            finally { if (this != null) { refreshing = false; Controls(); } }
        }
        // Hiển thị hồ sơ, người chơi online và chuyển màn theo phòng hiện tại.
        private void Render()
        {
            if (session == null || navigating) return;
            if (session.Profile != null)
            {
                welcome.text = "Xin chào, " + ((string)session.Profile["displayName"] ?? (string)session.Profile["username"]) + "!";
                score.text = "Điểm  " + session.Profile["totalScore"];
                wins.text = "Hạng nhất  " + session.Profile["firstPlaceCount"];
            }
            if (renderedPlayers != session.OnlinePlayers)
            {
                renderedPlayers = session.OnlinePlayers;
                int index = 0;
                foreach (var player in renderedPlayers)
                {
                    if (player is not JObject data) continue;
                    if (index == renderedRows.Count) renderedRows.Add(Instantiate(rowPrefab, rows));
                    renderedRows[index].gameObject.SetActive(true);
                    renderedRows[index].Bind(data, index, (string)session.Profile?["playerId"]); index++;
                }
                for (int i = index; i < renderedRows.Count; i++) renderedRows[i].gameObject.SetActive(false);
                total.text = index + " người chơi trực tuyến";
                empty.gameObject.SetActive(index == 0);
            }
            RenderInvitation(); Controls();
            if (session.Room != null)
            {
                if (Application.CanStreamedLevelBeLoaded(roomScene)) { navigating = true; SceneManager.LoadScene(roomScene); }
                else Notice("Đã vào phòng " + (string)session.Room["roomId"] + ". Màn hình phòng chờ sẽ được bổ sung tiếp theo.", false);
            }
        }
        // Hiển thị lời mời còn hiệu lực và khóa thao tác khi hết hạn.
        private void RenderInvitation()
        {
            var invite = session.Invitation;
            long remaining = invite == null ? 0 : ((long?)invite["expiresAtEpochMillis"] ?? 0) - DateTimeOffset.UtcNow.ToUnixTimeMilliseconds();
            bool visible = invite != null && remaining > 0 && session.Room == null;
            if (visible && !invitationCard.activeSelf)
            {
                Ludo.Services.AudioManager.Instance?.PlaySfx(Ludo.Services.SfxClip.RoomInvite);
            }
            invitationCard.SetActive(visible);
            if (visible) invitationText.text = (string)invite["inviterDisplayName"] + " mời bạn vào phòng\n" + (string)invite["roomId"] + "\nCòn " + Math.Ceiling(remaining / 1000d) + " giây để phản hồi";
            Controls();
        }
        // Gửi yêu cầu tạo phòng cho người đang ở sảnh.
        public void CreateRoom() { if (Available()) { Ludo.Services.AudioManager.Instance?.PlaySfx(Ludo.Services.SfxClip.ButtonClick); _ = RoomAction("CREATE_ROOM", null); } }
        // Kiểm tra mã phòng trên form rồi yêu cầu Server cho tham gia.
        public void JoinRoom()
        {
            if (!Available()) return;
            string code = roomCode.text.Trim();
            if (code.Length == 0) { Notice("Vui lòng nhập mã phòng.", true); return; }
            Ludo.Services.AudioManager.Instance?.PlaySfx(Ludo.Services.SfxClip.ButtonClick);
            _ = RoomAction("JOIN_ROOM", code);
        }
        // Gửi chấp nhận lời mời hiện tại để Server kiểm tra lại phòng và thời hạn.
        public void AcceptInvitation()
        {
            if (Available() && invitationCard.activeSelf)
            {
                Ludo.Services.AudioManager.Instance?.PlaySfx(Ludo.Services.SfxClip.Confirm);
                _ = RoomAction("ACCEPT_INVITE", (string)session.Invitation["invitationId"]);
            }
        }
        // Dùng chung trạng thái chờ và xử lý lỗi cho thao tác vào phòng.
        private async Task RoomAction(string type, string value)
        {
            busy = true; Controls(); Notice(type == "CREATE_ROOM" ? "Đang tạo phòng..." : "Đang tham gia phòng...", false);
            try { await session.EnterRoomAsync(type, value); if (this != null) Render(); }
            catch (Exception e)
            {
                if (this == null) return;
                if (type == "ACCEPT_INVITE" && e is ServerRequestException) session.ClearInvitation(value);
                Notice(Error(e), true);
            }
            finally { if (this != null) { busy = false; Controls(); } }
        }
        // Gửi từ chối lời mời và cập nhật giao diện sau xác nhận.
        public async void RejectInvitation()
        {
            if (!Available() || !invitationCard.activeSelf) return;
            Ludo.Services.AudioManager.Instance?.PlaySfx(Ludo.Services.SfxClip.ButtonClick);
            string id = (string)session.Invitation["invitationId"];
            busy = true; Controls();
            try { await session.RejectInvitationAsync(id); if (this != null) Notice("Đã từ chối lời mời.", false); }
            catch (Exception e) { if (this != null) { if (e is ServerRequestException) session.ClearInvitation(id); Notice(Error(e), true); } }
            finally { if (this != null) { busy = false; Controls(); } }
        }
        // Chờ Server xác nhận đăng xuất trước khi chuyển về màn đăng nhập.
        public async void Logout()
        {
            if (busy || session == null || session.State != ConnectionState.Connected) return;
            Ludo.Services.AudioManager.Instance?.PlaySfx(Ludo.Services.SfxClip.ButtonClick);
            busy = true; Controls(); Notice("Đang đăng xuất...", false);
            try { await session.LogoutAsync(); if (this != null) { navigating = true; SceneManager.LoadScene("LoginScene"); } }
            catch (Exception e) { if (this != null) Notice(Error(e), true); }
            finally { if (this != null) { busy = false; Controls(); } }
        }
        public void OpenRanking() { Ludo.Services.AudioManager.Instance?.PlaySfx(Ludo.Services.SfxClip.ButtonClick); OpenDestination(rankingScene, "Bảng xếp hạng"); }
        public void OpenHistory() { Ludo.Services.AudioManager.Instance?.PlaySfx(Ludo.Services.SfxClip.ButtonClick); OpenDestination(historyScene, "Lịch sử trận đấu"); }
        // Ngăn chuyển màn khi tác vụ sảnh đang chờ hoặc trạng thái không phù hợp.
        private void OpenDestination(string scene, string title)
        {
            if (busy || navigating) return;
            if (Application.CanStreamedLevelBeLoaded(scene)) { navigating = true; SceneManager.LoadScene(scene); }
            else Notice(title + " sẽ được bổ sung ở bước tiếp theo.", false);
        }
        private bool Available() => !busy && !navigating && session != null && session.State == ConnectionState.Connected && session.SessionId != null && session.Room == null;
        // Bật tắt thao tác theo kết nối và yêu cầu đang xử lý.
        private void Controls()
        {
            bool connected = session != null && session.State == ConnectionState.Connected && session.SessionId != null && !navigating;
            createButton.interactable = joinButton.interactable = roomCode.interactable = Available();
            acceptButton.interactable = rejectButton.interactable = Available() && invitationCard.activeSelf;
            logoutButton.interactable = connected && !busy;
            refreshButton.interactable = connected && !refreshing && !busy;
            rankingButton.interactable = historyButton.interactable = connected && !busy;
        }
        private void Notice(string message, bool error) { feedback.text = message; feedback.color = error ? new Color32(185,58,78,255) : new Color32(91,77,143,255); }
        // Ánh xạ lỗi vào phòng hoặc lời mời sang thông báo dễ hiểu.
        private static string Error(Exception e)
        {
            if (e is ServerRequestException server)
            {
                switch (server.Code)
                {
                    case "ROOM_NOT_FOUND": return "Không tìm thấy phòng.";
                    case "ROOM_FULL": return "Phòng đã đủ 4 người.";
                    case "ALREADY_IN_ROOM": return "Bạn đã ở trong một phòng.";
                    case "PLAYER_NOT_IDLE": return "Bạn phải ở trạng thái rảnh để tham gia.";
                    case "GAME_ALREADY_STARTED": return "Trận đấu trong phòng đã bắt đầu.";
                    case "INVITATION_EXPIRED": return "Lời mời đã hết hạn.";
                    case "INVITATION_NOT_FOUND": return "Lời mời không còn hiệu lực.";
                    case "INVALID_REQUEST": return "Thông tin yêu cầu chưa hợp lệ.";
                }
            }
            return e is IOException || e is TimeoutException ? "Kết nối bị gián đoạn. Đang khôi phục phiên..." : "Server chưa thể xử lý yêu cầu. Vui lòng thử lại.";
        }
        // Gỡ sự kiện để sảnh đã đóng không nhận cập nhật từ session.
        private void OnDestroy() { if (session != null) { session.LobbyChanged -= Render; session.StateChanged -= ConnectionChanged; } }
    }
}
