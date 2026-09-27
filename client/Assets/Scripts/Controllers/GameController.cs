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
        [SerializeField] private TMP_InputField chatInput;
        [SerializeField] private UnityEngine.UI.Button sendButton;
        [SerializeField] private TMP_Text chatLog;
        [SerializeField] private UnityEngine.UI.ScrollRect chatScroll;
        private readonly System.Collections.Generic.List<string> chatLines = new System.Collections.Generic.List<string>();
        private NetworkSession session;
        private bool busy, navigating, snap = true;
        private string selected;
        private long? awaitingStateVersion;
        private string lastCurrentPlayerId;
        private int lastCountdownSecond = -1;
        private JObject Game => session?.GameState;
        private string SelfId => (string)session?.Profile?["playerId"];
        private JToken Self => (Game?["participants"] as JArray)?.FirstOrDefault(p => (string)p["playerId"] == SelfId);
        private bool Connected => session?.State == ConnectionState.Connected;
        private bool Active => (string)Self?["matchStatus"] == "ACTIVE" && (string)Game?["roomState"] == "PLAYING";
        private bool CanMove => CanAct("WAITING_FOR_MOVE");
        // Kiểm tra lượt, phase và deadline để khóa nút phía UI; Server vẫn quyết định tính hợp lệ.
        public static bool MayAct(JObject game, string self, string requiredPhase, long now)
        {
            return game != null && (string)game["roomState"] == "PLAYING" && (string)game["currentPlayerId"] == self &&
                (string)game["turnState"] == requiredPhase && (long?)game["serverDeadlineEpochMillis"] > now &&
                (game["participants"] as JArray)?.Any(p => (string)p["playerId"] == self && (string)p["matchStatus"] == "ACTIVE") == true;
        }
        // Khóa thao tác khi đang gửi, chạy animation, chờ snapshot hoặc hiện xác nhận.
        private bool CanAct(string state) => !busy && !navigating && !board.IsAnimating && awaitingStateVersion == null && !confirmation.activeSelf && Connected && MayAct(Game, SelfId, state, DateTimeOffset.UtcNow.ToUnixTimeMilliseconds());
        // Gắn các sự kiện gameplay và điều hướng nếu không có phiên hoặc trận hợp lệ.
        private void Start()
        {
            AudioManager.EnsureInstance();
            session = NetworkSession.Instance;
            if (session?.SessionId == null) { Go("LoginScene"); return; }
            if (Game == null) { Go(session.Room == null ? "LobbyScene" : "RoomScene"); return; }
            rollButton.onClick.AddListener(Roll); moveButton.onClick.AddListener(Move);
            leaveButton.onClick.AddListener(Leave); confirmButton.onClick.AddListener(ConfirmLeave);
            cancelButton.onClick.AddListener(() => { Ludo.Services.AudioManager.Instance?.PlaySfx(Ludo.Services.SfxClip.ButtonClick); confirmation.SetActive(false); Render(); });
            if (sendButton != null) sendButton.onClick.AddListener(SendChat);
            if (chatInput != null)
            {
                chatInput.richText = false;
                if (chatInput.textComponent != null)
                {
                    chatInput.textComponent.richText = false;
                }
                chatInput.onSubmit.AddListener(_ => SendChat());
            }
            session.LobbyChanged += Render; session.StateChanged += ConnectionChanged; session.GameEvent += Event; session.ChatReceived += OnChatReceived;
            ConnectionChanged(session.State);
            Ludo.Services.AudioManager.Instance?.PlaySfx(Ludo.Services.SfxClip.GameStart);
        }
        // Khôi phục màn hình theo phiên và reset trình diễn khi kết nối thay đổi.
        private void ConnectionChanged(ConnectionState state)
        {
            if (session.SessionId == null) { Go("LoginScene"); return; }
            snap = true;
            board.ResetPresentation();
            awaitingStateVersion = null;
            connection.text = state == ConnectionState.Connected ? "Đã kết nối Game Server" : state == ConnectionState.Connecting ? "Đang khôi phục kết nối..." : "Mất kết nối • Đang thử lại";
            Render();
        }
        // Hiển thị snapshot Server và cập nhật quyền thao tác của người chơi.
        private void Render()
        {
            if (navigating) return;
            if (Game == null)
            {
                if (session?.Room != null && (string)session.Room["state"] == "WAITING") Go("RoomScene");
                return;
            }
            if (awaitingStateVersion != null && (long?)Game["stateVersion"] > awaitingStateVersion) awaitingStateVersion = null;
            roomLabel.text = "PHÒNG  " + (string)Game["roomId"];
            matchLabel.text = "Trận " + (string)Game["matchId"];
            var members = Game["participants"] as JArray ?? new JArray();
            string current = (string)Game["currentPlayerId"];
            if (current == SelfId && lastCurrentPlayerId != SelfId && (string)Game["turnState"] != "FINISHED")
            {
                Ludo.Services.AudioManager.Instance?.PlaySfx(Ludo.Services.SfxClip.TurnStart);
            }
            lastCurrentPlayerId = current;
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
            ShowResultWhenReady();
        }
        // Chờ animation và thông báo cuối trận hoàn tất trước khi mở bảng kết quả.
        private void ShowResultWhenReady()
        {
            if (session?.GameOver != null && !board.IsAnimating && !board.HasNotice && Application.CanStreamedLevelBeLoaded("ResultScene")) Go("ResultScene");
        }
        private bool moveAllowedLastFrame;
        // Cập nhật đếm ngược và trạng thái nút theo thời gian hiển thị.
        private void Update()
        {
            if (Game == null || navigating) return;
            long remaining = Math.Max(0, ((long?)Game["serverDeadlineEpochMillis"] ?? 0) - DateTimeOffset.UtcNow.ToUnixTimeMilliseconds());
            long remainingSec = (remaining + 999) / 1000;
            timer.text = Game["serverDeadlineEpochMillis"]?.Type == JTokenType.Integer ? remainingSec + "s" : "—";
            if ((string)Game["currentPlayerId"] == SelfId && (string)Game["roomState"] == "PLAYING" && remainingSec <= 5 && remainingSec > 0)
            {
                if ((int)remainingSec != lastCountdownSecond)
                {
                    lastCountdownSecond = (int)remainingSec;
                    Ludo.Services.AudioManager.Instance?.PlaySfx(Ludo.Services.SfxClip.CountdownTick);
                }
            }
            else
            {
                lastCountdownSecond = -1;
            }
            long duration = (long?)Game["phaseDurationMillis"] ?? 0;
            timerFill.fillAmount = duration > 0 ? Mathf.Clamp01((float)remaining / duration) : 0;
            phase.text = (string)Game["turnState"] switch { "WAITING_FOR_ROLL" => "Chờ đổ xúc xắc", "WAITING_FOR_MOVE" => "Chọn quân để di chuyển", "FINISHED" => "Đã kết thúc", _ => "Đang xử lý" };
            RefreshActions();
            if (CanMove != moveAllowedLastFrame)
            { moveAllowedLastFrame = CanMove; board.Bind(Game, SelfId, selected, CanMove, SelectPiece, false); }
            ShowResultWhenReady();
        }
        // Bật nút tung hoặc đi quân theo phase và trạng thái chờ phản hồi.
        private void RefreshActions()
        {
            rollButton.interactable = CanAct("WAITING_FOR_ROLL");
            moveButton.interactable = CanMove && selected != null;
            leaveButton.interactable = Connected && !busy && !navigating;
            confirmButton.interactable = Connected && !busy; cancelButton.interactable = !busy;
            if (sendButton != null) sendButton.interactable = Connected && !busy;
            if (chatInput != null) chatInput.interactable = Connected;
        }

        // Lấy nội dung người dùng, xử lý định dạng nhập và bắt đầu gửi chat.
        public void SendChat()
        {
            if (chatInput == null || string.IsNullOrWhiteSpace(chatInput.text) || !Connected || busy) return;
            string text = chatInput.text.Trim();
            // Xóa các thẻ underline do bộ gõ tiếng Việt (IME) chèn vào nếu có
            text = text.Replace("<u>", "").Replace("</u>", "").Trim();
            if (string.IsNullOrWhiteSpace(text)) return;
            chatInput.text = "";
            chatInput.ActivateInputField();
            _ = ExecuteChat(text);
        }

        // Gửi chat bất đồng bộ và chỉ cập nhật UI nếu controller còn tồn tại.
        private async Task ExecuteChat(string text)
        {
            try
            {
                Ludo.Services.AudioManager.Instance?.PlaySfx(Ludo.Services.SfxClip.ChatSend);
                await session.SendChatMessageAsync(text);
            }
            catch (Exception)
            {
                Ludo.Services.AudioManager.Instance?.PlaySfx(Ludo.Services.SfxClip.ErrorSoft);
                if (this != null && feedback != null) feedback.text = "Không thể gửi tin nhắn. Vui lòng thử lại.";
            }
        }

        // Hiển thị tin nhắn đã được Server phát và tránh ghép trực tiếp markup người dùng.
        private void OnChatReceived(JObject data)
        {
            if (data == null) return;
            string senderId = (string)data["senderPlayerId"];
            if (senderId != SelfId)
            {
                Ludo.Services.AudioManager.Instance?.PlaySfx(Ludo.Services.SfxClip.ChatReceive);
            }
            string senderName = EscapeChatText((string)data["senderDisplayName"] ?? "Người chơi");
            string color = (string)data["senderColor"] ?? "RED";
            string colorHex = color switch
            {
                "RED" => "#E03E3E",
                "BLUE" => "#2E7BD6",
                "YELLOW" => "#D69E2E",
                "GREEN" => "#2EA85C",
                _ => "#7C58D2"
            };
            string colorLabel = color switch
            {
                "RED" => "Đỏ",
                "BLUE" => "Xanh",
                "YELLOW" => "Vàng",
                "GREEN" => "Lá",
                _ => ""
            };
            string tag = string.IsNullOrEmpty(colorLabel) ? "" : $"[{colorLabel}] ";
            string msg = (string)data["message"] ?? "";
            // Thoát ký tự '<' để tránh làm hỏng định dạng rich text trong chatLog
            msg = EscapeChatText(msg);
            string line = $"<color={colorHex}><b>{tag}{senderName}:</b></color> {msg}";
            chatLines.Add(line);
            if (chatLines.Count > 200) chatLines.RemoveAt(0);
            if (chatLog != null)
            {
                chatLog.text = string.Join("\n", chatLines);
                Canvas.ForceUpdateCanvases();
                UnityEngine.UI.LayoutRebuilder.ForceRebuildLayoutImmediate(chatScroll.content);
                chatScroll.StopMovement();
                chatScroll.verticalNormalizedPosition = 0;
            }
        }
        // Ngăn ký tự mở thẻ trong tên hoặc nội dung bị TMP hiểu thành rich text.
        public static string EscapeChatText(string text) => (text ?? "").Replace("<", "<\u200B");
        // Cập nhật trình diễn khi nhận kết quả xúc xắc, timeout hoặc kết thúc trận.
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
        // Ghi lựa chọn trên UI, không tự thay đổi vị trí quân.
        public void SelectPiece(string id)
        {
            if (!CanMove || !(Game["validPieceIds"] as JArray ?? new JArray()).Values<string>().Contains(id) ||
                !(Self?["pieces"] as JArray ?? new JArray()).Any(p => (string)p["pieceId"] == id)) return;
            selected = id; Render();
        }
        // Gửi ý định tung xúc xắc rồi chờ dữ liệu mới từ Server.
        public void Roll()
        {
            if (CanAct("WAITING_FOR_ROLL"))
            {
                Ludo.Services.AudioManager.Instance?.PlaySfx(Ludo.Services.SfxClip.ButtonClick);
                awaitingStateVersion = (long?)Game["stateVersion"];
                _ = Execute(session.RollDiceAsync);
            }
        }
        // Gửi quân đã chọn và khóa thao tác trong lúc chờ kết quả.
        public void Move()
        {
            if (CanMove && selected != null)
            {
                Ludo.Services.AudioManager.Instance?.PlaySfx(Ludo.Services.SfxClip.ButtonClick);
                awaitingStateVersion = (long?)Game["stateVersion"];
                _ = Execute(() => session.MovePieceAsync(selected));
            }
        }
        // Hiện xác nhận để người chơi biết rời trận sẽ tính bỏ cuộc.
        public void Leave()
        {
            if (busy || !Connected) return;
            Ludo.Services.AudioManager.Instance?.PlaySfx(Ludo.Services.SfxClip.ButtonClick);
            if (Active) { confirmation.SetActive(true); Render(); }
            else _ = Execute(LeaveAcknowledged);
        }
        // Chỉ gửi yêu cầu rời sau khi người chơi xác nhận bỏ cuộc.
        public void ConfirmLeave()
        {
            if (confirmation.activeSelf && !busy && Connected)
            {
                Ludo.Services.AudioManager.Instance?.PlaySfx(Ludo.Services.SfxClip.Forfeit);
                _ = Execute(LeaveAcknowledged);
            }
        }
        // Chuyển về sảnh sau khi Server xác nhận đã rời phòng.
        private async Task LeaveAcknowledged() { await session.LeaveRoomAsync(); if (this != null) Go("LobbyScene"); }
        // Chặn gửi lặp, hiển thị lỗi và khôi phục điều khiển sau tác vụ mạng.
        private async Task Execute(Func<Task> action)
        {
            busy = true; Render(); feedback.text = "Đang chờ phản hồi...";
            try { await action(); if (this != null && !navigating) feedback.text = "Đã cập nhật."; }
            catch (Exception e)
            {
                Ludo.Services.AudioManager.Instance?.PlaySfx(Ludo.Services.SfxClip.ErrorSoft);
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
        // Chặn nhiều sự kiện cùng yêu cầu chuyển scene.
        private void Go(string scene) { if (navigating) return; navigating = true; SceneManager.LoadScene(scene); }
        // Gỡ listener để scene cũ không nhận sự kiện và cập nhật UI đã bị hủy.
        private void OnDestroy()
        {
            if (session != null)
            {
                session.LobbyChanged -= Render;
                session.StateChanged -= ConnectionChanged;
                session.GameEvent -= Event;
                session.ChatReceived -= OnChatReceived;
            }
        }
    }
}
