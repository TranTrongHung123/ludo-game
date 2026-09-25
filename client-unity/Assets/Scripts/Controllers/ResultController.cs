using System;
using System.Linq;
using Ludo.Services;
using Ludo.Views;
using Newtonsoft.Json.Linq;
using TMPro;
using UnityEngine;
using UnityEngine.SceneManagement;

namespace Ludo.Controllers
{
    public sealed class ResultController : MonoBehaviour
    {
        [SerializeField] private TMP_Text headline, matchId, connection, feedback, count, lobbyLabel;
        [SerializeField] private UnityEngine.UI.Image connectionDot;
        [SerializeField] private UnityEngine.UI.Button lobbyButton;
        [SerializeField] private UnityEngine.UI.Button rematchButton;
        [SerializeField] private TMP_Text rematchLabel;
        [SerializeField] private ResultRowView[] rows;
        [SerializeField] private GameObject emptyState;
        private NetworkSession session;
        private bool leaving, replaying, navigating;

        private void Start()
        {
            session = NetworkSession.Instance;
            if (session?.SessionId == null) { Go("LoginScene"); return; }
            if (session.GameOver == null) { Go(session.GameState != null ? "GameScene" : session.Room != null ? "RoomScene" : "LobbyScene"); return; }
            lobbyButton.onClick.AddListener(ReturnToLobby);
            if (rematchButton != null) rematchButton.onClick.AddListener(PlayAgain);
            session.StateChanged += ConnectionChanged;
            session.GameEvent += GameEvent;
            session.LobbyChanged += RoomChanged;
            Render();
            ConnectionChanged(session.State);
        }

        private bool soundPlayed;

        private void Render()
        {
            var result = session.GameOver;
            var standings = (result?["standings"] as JArray ?? new JArray()).OrderBy(p => (int?)p["rank"] ?? int.MaxValue).ToArray();
            string self = (string)session.Profile?["playerId"];
            var own = standings.FirstOrDefault(p => (string)p["playerId"] == self);
            headline.text = own == null ? "Trận đấu đã kết thúc" : (string)own["matchStatus"] == "FORFEITED"
                ? "Bạn đã bỏ cuộc • Hạng " + own["rank"] : "Bạn về hạng " + own["rank"] + "!";
            if (!soundPlayed && own != null)
            {
                soundPlayed = true;
                if ((string)own["matchStatus"] == "FORFEITED")
                {
                    Ludo.Services.AudioManager.Instance?.PlaySfx(Ludo.Services.SfxClip.Forfeit);
                }
                else if ((int?)own["rank"] == 1)
                {
                    Ludo.Services.AudioManager.Instance?.PlaySfx(Ludo.Services.SfxClip.Victory);
                    Ludo.Services.AudioManager.Instance?.PlaySfx(Ludo.Services.SfxClip.HorseNeigh);
                }
                else
                {
                    Ludo.Services.AudioManager.Instance?.PlaySfx(Ludo.Services.SfxClip.Notification);
                }
            }
            matchId.text = "Mã trận  •  " + (string)result?["matchId"];
            count.text = standings.Length + " người chơi";
            emptyState.SetActive(standings.Length == 0);
            for (int i = 0; i < rows.Length; i++) rows[i].Bind(i < standings.Length ? standings[i] : null, self);
        }

        private void GameEvent(string type, JObject data) { if (type == "GAME_OVER" && !navigating) Render(); }
        private void RoomChanged()
        {
            if (navigating) return;
            if (session.Room != null && (string)session.Room["state"] == "WAITING") Go("RoomScene");
            else if (session.GameState != null && (string)session.GameState["roomState"] == "PLAYING") Go("GameScene");
        }
        private void ConnectionChanged(ConnectionState state)
        {
            if (session.SessionId == null) { Go("LoginScene"); return; }
            RoomChanged();
            if (navigating) return;
            connection.text = state == ConnectionState.Connected ? "Kết quả đã đồng bộ với Server" : state == ConnectionState.Connecting ? "Đang khôi phục kết nối..." : "Mất kết nối • Đang thử lại";
            connectionDot.color = state == ConnectionState.Connected ? new Color32(47,160,119,255) : new Color32(197,137,68,255);
            RefreshAction();
        }
        private void RefreshAction()
        {
            bool available = !leaving && !replaying && !navigating && session.State == ConnectionState.Connected;
            lobbyButton.interactable = available;
            if (rematchButton != null) rematchButton.interactable = available && session.Room != null;
            if (rematchLabel != null) rematchLabel.text = replaying ? "Đang chuẩn bị..." : "Chơi tiếp";
            lobbyLabel.text = leaving ? "Đang về sảnh..." : "Về sảnh";
        }

        public async void ReturnToLobby()
        {
            if (leaving || replaying || navigating || session?.State != ConnectionState.Connected) return;
            Ludo.Services.AudioManager.Instance?.PlaySfx(Ludo.Services.SfxClip.ButtonClick);
            if (session.Room == null) { Go("LobbyScene"); return; }
            leaving = true; RefreshAction(); feedback.text = "Đang rời phòng và trở về sảnh...";
            try
            {
                await session.LeaveRoomAsync();
                if (this != null) Go("LobbyScene");
            }
            catch (Exception e)
            {
                Ludo.Services.AudioManager.Instance?.PlaySfx(Ludo.Services.SfxClip.ErrorSoft);
                if (this != null && !navigating)
                    feedback.text = e is ServerRequestException ? "Chưa thể rời phòng. Vui lòng thử lại." : "Kết nối gián đoạn. Vui lòng thử lại khi kết nối được khôi phục.";
            }
            finally { if (this != null && !navigating) { leaving = false; RefreshAction(); } }
        }

        public async void PlayAgain()
        {
            if (leaving || replaying || navigating || session?.State != ConnectionState.Connected || session.Room == null) return;
            Ludo.Services.AudioManager.Instance?.PlaySfx(Ludo.Services.SfxClip.Confirm);
            replaying = true; RefreshAction(); feedback.text = "Đang chuẩn bị ván mới...";
            try
            {
                await session.SetReadyAsync(true);
                if (this != null) Go("RoomScene");
            }
            catch (Exception)
            {
                if (this != null && !navigating) feedback.text = "Chưa thể chơi tiếp. Vui lòng thử lại khi kết nối ổn định.";
            }
            finally { if (this != null && !navigating) { replaying = false; RefreshAction(); } }
        }

        private void Go(string scene) { if (navigating) return; navigating = true; SceneManager.LoadScene(scene); }
        private void OnDestroy()
        {
            if (session != null) { session.StateChanged -= ConnectionChanged; session.GameEvent -= GameEvent; session.LobbyChanged -= RoomChanged; }
            if (lobbyButton != null) lobbyButton.onClick.RemoveListener(ReturnToLobby);
            if (rematchButton != null) rematchButton.onClick.RemoveListener(PlayAgain);
        }
    }
}
