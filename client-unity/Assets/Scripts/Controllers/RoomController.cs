using System;
using System.Collections.Generic;
using System.Linq;
using System.Threading.Tasks;
using Ludo.Services;
using Ludo.Views;
using Newtonsoft.Json.Linq;
using TMPro;
using UnityEngine;
using UnityEngine.SceneManagement;

namespace Ludo.Controllers
{
    public sealed class RoomController : MonoBehaviour
    {
        [SerializeField] private TMP_Text roomCode, hostName, roomState, connection, feedback, readyLabel, empty, hint;
        [SerializeField] private UnityEngine.UI.Button copyButton, leaveButton, readyButton, startButton, inviteButton;
        [SerializeField] private RoomSlotView[] slots;
        [SerializeField] private RectTransform rows;
        [SerializeField] private InvitePlayerRow rowPrefab;
        [SerializeField] private string gameScene = "GameScene";
        private readonly List<InvitePlayerRow> playerRows = new List<InvitePlayerRow>();
        private NetworkSession session;
        private bool busy, navigating, refreshing;
        private string selected;
        private string SelfId => (string)session?.Profile?["playerId"];
        private JArray Players => session?.Room?["players"] as JArray ?? new JArray();
        private JObject Self => Players.OfType<JObject>().FirstOrDefault(p => (string)p["playerId"] == SelfId);
        private bool Host => SelfId != null && (string)session?.Room?["hostPlayerId"] == SelfId;
        private bool Waiting => (string)session?.Room?["state"] == "WAITING" && session?.GameState == null;
        private bool CanAct => !busy && !navigating && session?.State == ConnectionState.Connected && Self != null && Waiting;
        private bool CanInvite => CanAct && Host && Players.Count < 4;
        public static bool CanStart(JObject room, string selfId) => room != null && (string)room["state"] == "WAITING" &&
            (string)room["hostPlayerId"] == selfId && room["players"] is JArray players && players.Count >= 2 && players.Count <= 4 &&
            players.All(p => (bool?)p["ready"] == true && (string)p["presenceState"] == "IN_ROOM");
        private void Start()
        {
            session = NetworkSession.Instance;
            if (session?.SessionId == null) { Go("LoginScene"); return; }
            if (session.Room == null) { Go("LobbyScene"); return; }
            copyButton.onClick.AddListener(CopyCode); leaveButton.onClick.AddListener(Leave);
            readyButton.onClick.AddListener(ToggleReady); startButton.onClick.AddListener(StartMatch);
            inviteButton.onClick.AddListener(() => Invite(selected));
            session.LobbyChanged += Render; session.StateChanged += ConnectionChanged;
            ConnectionChanged(session.State);
        }
        private async void ConnectionChanged(ConnectionState state)
        {
            if (session.SessionId == null) { Go("LoginScene"); return; }
            connection.text = state == ConnectionState.Connected ? "Đã kết nối Game Server" : state == ConnectionState.Connecting ? "Đang khôi phục kết nối..." : "Mất kết nối • Đang thử lại";
            Render();
            if (state != ConnectionState.Connected || refreshing || navigating) return;
            refreshing = true;
            try { await session.RefreshOnlineAsync(); }
            catch (Exception e) { if (this != null) Notice(Error(e), true); }
            finally { refreshing = false; }
        }
        private void Render()
        {
            if (navigating) return;
            if (session.Room == null) { Go("LobbyScene"); return; }
            var players = Players.OfType<JObject>().ToArray();
            string host = (string)session.Room["hostPlayerId"];
            roomCode.text = (string)session.Room["roomId"];
            hostName.text = "Chủ phòng: " + ((string)players.FirstOrDefault(p => (string)p["playerId"] == host)?["displayName"] ?? "—");
            roomState.text = Waiting ? "Đang chờ • " + players.Length + "/4 người" : "Trận đấu đã bắt đầu";
            for (int i = 0; i < slots.Length; i++) slots[i].Bind(players.FirstOrDefault(p => (int?)p["slotIndex"] == i), host, SelfId);
            readyLabel.text = (bool?)Self?["ready"] == true ? "Hủy sẵn sàng" : "Sẵn sàng";
            readyButton.interactable = leaveButton.interactable = CanAct;
            startButton.interactable = CanAct && CanStart(session.Room, SelfId);
            startButton.gameObject.SetActive(Host);
            var idle = session.OnlinePlayers.OfType<JObject>().Where(p => (string)p["presenceState"] == "IDLE" && !players.Any(member => (string)member["playerId"] == (string)p["playerId"])).ToArray();
            if (!idle.Any(p => (string)p["playerId"] == selected)) selected = null;
            for (int i = 0; i < idle.Length; i++)
            {
                if (i == playerRows.Count) playerRows.Add(Instantiate(rowPrefab, rows));
                playerRows[i].gameObject.SetActive(true); playerRows[i].Bind(idle[i], CanInvite, selected, SelectPlayer, Invite);
            }
            for (int i = idle.Length; i < playerRows.Count; i++) playerRows[i].gameObject.SetActive(false);
            empty.gameObject.SetActive(idle.Length == 0);
            inviteButton.interactable = CanInvite && selected != null;
            hint.text = !Waiting ? "Trận đấu đang diễn ra" : !Host ? "Chủ phòng sẽ mời bạn bè và bắt đầu trận." : "Cần ít nhất 2 người và tất cả cùng sẵn sàng.";
            if (session.GameState != null)
            {
                if (Application.CanStreamedLevelBeLoaded(gameScene)) Go(gameScene);
                else Notice("Server đã bắt đầu trận. Màn hình GameScene sẽ được bổ sung tiếp theo.", false);
            }
        }
        private void SelectPlayer(string id) { selected = id; Render(); }
        public void CopyCode() { if (session?.Room != null) { GUIUtility.systemCopyBuffer = (string)session.Room["roomId"]; Notice("Đã sao chép mã phòng.", false); } }
        public void ToggleReady() { if (CanAct) _ = Execute(() => session.SetReadyAsync((bool?)Self["ready"] != true), "Đã cập nhật trạng thái sẵn sàng."); }
        public void Leave() { if (CanAct) _ = Execute(session.LeaveRoomAsync, "Đã rời phòng."); }
        public void StartMatch() { if (CanAct && CanStart(session.Room, SelfId)) _ = Execute(session.StartGameAsync, "Server đã bắt đầu trận."); }
        public void Invite(string id)
        {
            if (!CanInvite || id == null || !session.OnlinePlayers.Any(p => (string)p["playerId"] == id && (string)p["presenceState"] == "IDLE")) return;
            _ = Execute(() => session.InvitePlayerAsync(id), "Đã gửi lời mời. Lời mời có hiệu lực 60 giây.");
        }
        private async Task Execute(Func<Task> action, string success)
        {
            busy = true; Render(); Notice("Đang xử lý...", false);
            try { await action(); if (this != null && !navigating) Notice(success, false); }
            catch (Exception e) { if (this != null) Notice(Error(e), true); }
            finally { if (this != null && !navigating) { busy = false; Render(); } }
        }
        private static string Error(Exception e)
        {
            if (e is ServerRequestException server)
                switch (server.Code)
                {
                    case "NOT_ROOM_HOST": return "Chỉ chủ phòng được thực hiện thao tác này.";
                    case "PLAYER_NOT_READY": return "Tất cả người chơi phải kết nối và sẵn sàng.";
                    case "NOT_ENOUGH_PLAYERS": return "Cần ít nhất 2 người để bắt đầu.";
                    case "PLAYER_NOT_IDLE": return "Người chơi không còn ở trạng thái rảnh.";
                    case "ROOM_FULL": return "Phòng đã đủ 4 người.";
                    case "GAME_ALREADY_STARTED": return "Trận đấu đã bắt đầu.";
                    case "NOT_IN_ROOM": case "ROOM_NOT_FOUND": return "Bạn không còn ở trong phòng này.";
                }
            return "Không thể hoàn tất yêu cầu. Vui lòng kiểm tra kết nối và thử lại.";
        }
        private void Notice(string text, bool error) { feedback.text = text; feedback.color = error ? new Color32(185,58,78,255) : new Color32(91,77,143,255); }
        private void Go(string scene) { if (navigating) return; navigating = true; SceneManager.LoadScene(scene); }
        private void OnDestroy() { if (session != null) { session.LobbyChanged -= Render; session.StateChanged -= ConnectionChanged; } }
    }
}
