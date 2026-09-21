using System;
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
    public sealed class GameController : MonoBehaviour
    {
        [SerializeField] private TMP_Text roomLabel, matchLabel, connection, turnName, phase, timer, diceLabel, feedback, selection, leaveLabel;
        [SerializeField] private UnityEngine.UI.Button rollButton, moveButton, leaveButton, confirmButton, cancelButton;
        [SerializeField] private UnityEngine.UI.Image timerFill;
        [SerializeField] private GameObject confirmation;
        [SerializeField] private BoardView board;
        [SerializeField] private DiceView dice;
        [SerializeField] private PlayerCardView[] players;
        private NetworkSession session;
        private bool busy, navigating, snap = true;
        private string selected;
        private long? awaitingStateVersion;
        private JObject Game => session?.GameState;
        private string SelfId => (string)session?.Profile?["playerId"];
        private JToken Self => (Game?["participants"] as JArray)?.FirstOrDefault(p => (string)p["playerId"] == SelfId);
        private bool Connected => session?.State == ConnectionState.Connected;
        private bool Active => (string)Self?["matchStatus"] == "ACTIVE" && (string)Game?["roomState"] == "PLAYING";
        private bool CanMove => CanAct("WAITING_FOR_MOVE");
        public static bool MayAct(JObject game, string self, string requiredPhase, long now)
        {
            return game != null && (string)game["roomState"] == "PLAYING" && (string)game["currentPlayerId"] == self &&
                (string)game["turnState"] == requiredPhase && (long?)game["serverDeadlineEpochMillis"] > now &&
                (game["participants"] as JArray)?.Any(p => (string)p["playerId"] == self && (string)p["matchStatus"] == "ACTIVE") == true;
        }
        private bool CanAct(string state) => !busy && !navigating && awaitingStateVersion == null && !confirmation.activeSelf && Connected && MayAct(Game, SelfId, state, DateTimeOffset.UtcNow.ToUnixTimeMilliseconds());
        private void Start()
        {
            session = NetworkSession.Instance;
            if (session?.SessionId == null) { Go("LoginScene"); return; }
            if (Game == null) { Go(session.Room == null ? "LobbyScene" : "RoomScene"); return; }
            rollButton.onClick.AddListener(Roll); moveButton.onClick.AddListener(Move);
            leaveButton.onClick.AddListener(Leave); confirmButton.onClick.AddListener(ConfirmLeave);
            cancelButton.onClick.AddListener(() => { confirmation.SetActive(false); Render(); });
            session.LobbyChanged += Render; session.StateChanged += ConnectionChanged; session.GameEvent += Event;
            ConnectionChanged(session.State);
        }
        private void ConnectionChanged(ConnectionState state)
        {
            if (session.SessionId == null) { Go("LoginScene"); return; }
            snap = true;
            awaitingStateVersion = null;
            connection.text = state == ConnectionState.Connected ? "Đã kết nối Game Server" : state == ConnectionState.Connecting ? "Đang khôi phục kết nối..." : "Mất kết nối • Đang thử lại";
            Render();
        }
        private void Render()
        {
            if (navigating || Game == null) return;
            if (session.GameOver != null && Application.CanStreamedLevelBeLoaded("ResultScene")) { Go("ResultScene"); return; }
            if (awaitingStateVersion != null && (long?)Game["stateVersion"] > awaitingStateVersion) awaitingStateVersion = null;
            roomLabel.text = "PHÒNG  " + (string)Game["roomId"];
            matchLabel.text = "Trận " + (string)Game["matchId"];
            var members = Game["participants"] as JArray ?? new JArray();
            string current = (string)Game["currentPlayerId"];
            turnName.text = (string)Game["turnState"] == "FINISHED" ? "Trận đấu kết thúc" : (string)members.FirstOrDefault(p => (string)p["playerId"] == current)?["displayName"] ?? "Đang chờ";
            for (int i = 0; i < players.Length; i++) players[i].Bind(members.FirstOrDefault(p => (int?)p["slotIndex"] == i), i, SelfId, current);
            if (!(Game["validPieceIds"] as JArray ?? new JArray()).Values<string>().Contains(selected) || (string)Game["currentPlayerId"] != SelfId) selected = null;
            board.Bind(Game, SelfId, selected, CanMove, SelectPiece, !snap && Connected); snap = false;
            int value = (int?)session.LastDice?["diceValue"] ?? (int?)Game["diceValue"] ?? 0;
            dice.Show(value); diceLabel.text = value == 0 ? "Chưa đổ xúc xắc" : "Kết quả gần nhất: " + value;
            selection.text = selected == null ? "Chọn quân được đánh dấu trên bàn cờ" : "Đã chọn quân • Sẵn sàng di chuyển";
            leaveLabel.text = Active ? "Bỏ cuộc" : "Về sảnh";
            RefreshActions();
            if ((string)Game["turnState"] == "FINISHED")
            {
                var rank = Self?["rank"];
                feedback.text = rank?.Type == JTokenType.Integer ? "Kết thúc • Bạn về hạng " + rank + " • Điểm nhận: " + Self?["scoreEarned"] : "Trận đấu đã kết thúc. Bạn có thể về sảnh.";
            }
        }
        private bool moveAllowedLastFrame;
        private void Update()
        {
            if (Game == null || navigating) return;
            long remaining = Math.Max(0, ((long?)Game["serverDeadlineEpochMillis"] ?? 0) - DateTimeOffset.UtcNow.ToUnixTimeMilliseconds());
            timer.text = Game["serverDeadlineEpochMillis"]?.Type == JTokenType.Integer ? ((remaining + 999) / 1000) + "s" : "—";
            long duration = (long?)Game["phaseDurationMillis"] ?? 0;
            timerFill.fillAmount = duration > 0 ? Mathf.Clamp01((float)remaining / duration) : 0;
            phase.text = (string)Game["turnState"] switch { "WAITING_FOR_ROLL" => "Chờ đổ xúc xắc", "WAITING_FOR_MOVE" => "Chọn quân để di chuyển", "FINISHED" => "Đã kết thúc", _ => "Đang xử lý" };
            RefreshActions();
            if (CanMove != moveAllowedLastFrame)
            { moveAllowedLastFrame = CanMove; board.Bind(Game, SelfId, selected, CanMove, SelectPiece, false); }
        }
        private void RefreshActions()
        {
            rollButton.interactable = CanAct("WAITING_FOR_ROLL");
            moveButton.interactable = CanMove && selected != null;
            leaveButton.interactable = Connected && !busy && !navigating;
            confirmButton.interactable = Connected && !busy; cancelButton.interactable = !busy;
        }
        private void Event(string type, JObject data)
        {
            if (type == "DICE_RESULT")
            {
                int value = (int?)data["diceValue"] ?? 0;
                dice.Animate(value); diceLabel.text = "Kết quả gần nhất: " + value;
                if ((data["validPieceIds"] as JArray)?.Count == 0) feedback.text = "Không có nước đi hợp lệ • Chờ lượt tiếp theo";
            }
            else if (type == "TURN_TIMEOUT") feedback.text = "Đã hết thời gian. Chờ lượt tiếp theo.";
            else if (type == "GAME_OVER") Render();
        }
        public void SelectPiece(string id)
        {
            if (!CanMove || !(Game["validPieceIds"] as JArray ?? new JArray()).Values<string>().Contains(id) ||
                !(Self?["pieces"] as JArray ?? new JArray()).Any(p => (string)p["pieceId"] == id)) return;
            selected = id; Render();
        }
        public void Roll() { if (CanAct("WAITING_FOR_ROLL")) { awaitingStateVersion = (long?)Game["stateVersion"]; _ = Execute(session.RollDiceAsync); } }
        public void Move() { if (CanMove && selected != null) { awaitingStateVersion = (long?)Game["stateVersion"]; _ = Execute(() => session.MovePieceAsync(selected)); } }
        public void Leave()
        {
            if (busy || !Connected) return;
            if (Active) { confirmation.SetActive(true); Render(); }
            else _ = Execute(LeaveAcknowledged);
        }
        public void ConfirmLeave() { if (confirmation.activeSelf && !busy && Connected) _ = Execute(LeaveAcknowledged); }
        private async Task LeaveAcknowledged() { await session.LeaveRoomAsync(); if (this != null) Go("LobbyScene"); }
        private async Task Execute(Func<Task> action)
        {
            busy = true; Render(); feedback.text = "Đang chờ phản hồi...";
            try { await action(); if (this != null && !navigating) feedback.text = "Đã cập nhật."; }
            catch (Exception e)
            {
                awaitingStateVersion = null;
                if (this != null) feedback.text = e is ServerRequestException error ? error.Code switch
                {
                    "NOT_YOUR_TURN" => "Chưa đến lượt của bạn.",
                    "TURN_TIMEOUT" => "Đã hết thời gian của lượt này.",
                    "MOVE_NOT_ALLOWED" or "INVALID_MOVE" => "Nước đi không còn hợp lệ. Hãy chọn lại quân.",
                    _ => "Yêu cầu chưa được chấp nhận. Vui lòng thử lại."
                } : "Kết nối gián đoạn. Đang chờ khôi phục.";
            }
            finally { if (this != null && !navigating) { busy = false; Render(); } }
        }
        private void Go(string scene) { if (navigating) return; navigating = true; SceneManager.LoadScene(scene); }
        private void OnDestroy()
        { if (session != null) { session.LobbyChanged -= Render; session.StateChanged -= ConnectionChanged; session.GameEvent -= Event; } }
    }
}
