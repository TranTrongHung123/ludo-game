using System;
using System.Collections.Generic;
using Ludo.Services;
using Ludo.Views;
using TMPro;
using UnityEngine;
using UnityEngine.SceneManagement;

namespace Ludo.Controllers
{
    public sealed class RankingController : MonoBehaviour
    {
        [SerializeField] private TMP_Text connection, feedback, empty, count, refreshLabel;
        [SerializeField] private UnityEngine.UI.Image connectionDot;
        [SerializeField] private UnityEngine.UI.Button backButton, refreshButton;
        [SerializeField] private UnityEngine.UI.ScrollRect scroll;
        [SerializeField] private RectTransform rows;
        [SerializeField] private RankingRowView rowPrefab;
        private readonly List<RankingRowView> renderedRows = new List<RankingRowView>();
        private NetworkSession session;
        private bool loading, navigating;

        private void Start()
        {
            session = NetworkSession.Instance;
            if (session?.SessionId == null) { Go("LoginScene"); return; }
            backButton.onClick.AddListener(Back);
            refreshButton.onClick.AddListener(Refresh);
            session.StateChanged += ConnectionChanged;
            ConnectionChanged(session.State);
        }
        private void ConnectionChanged(ConnectionState state)
        {
            if (session.SessionId == null) { Go("LoginScene"); return; }
            connection.text = state == ConnectionState.Connected ? "Đã kết nối Game Server" : state == ConnectionState.Connecting ? "Đang khôi phục kết nối..." : "Mất kết nối • Đang thử lại";
            connectionDot.color = state == ConnectionState.Connected ? new Color32(47,160,119,255) : new Color32(197,110,80,255);
            Controls();
            if (state == ConnectionState.Connected) Refresh();
        }
        public async void Refresh()
        {
            if (loading || navigating || session?.State != ConnectionState.Connected) return;
            loading = true; Controls();
            feedback.text = "Đang tải bảng xếp hạng..."; feedback.color = new Color32(115,110,141,255);
            empty.text = "Đang tải bảng xếp hạng từ Server...";
            try
            {
                var entries = await session.GetRankingAsync();
                if (this == null || navigating) return;
                string self = (string)session.Profile?["playerId"];
                for (int i = 0; i < entries.Count; i++)
                {
                    if (i == renderedRows.Count) renderedRows.Add(Instantiate(rowPrefab, rows));
                    renderedRows[i].gameObject.SetActive(true); renderedRows[i].Bind(entries[i], self);
                }
                for (int i = entries.Count; i < renderedRows.Count; i++) renderedRows[i].gameObject.SetActive(false);
                empty.text = "Chưa có người chơi trong bảng xếp hạng.";
                empty.gameObject.SetActive(entries.Count == 0);
                count.text = entries.Count + " người chơi";
                feedback.text = "Đã cập nhật bảng xếp hạng.";
                Canvas.ForceUpdateCanvases(); scroll.StopMovement(); scroll.verticalNormalizedPosition = 1;
            }
            catch (Exception)
            {
                if (this == null || navigating) return;
                feedback.text = "Chưa tải được bảng xếp hạng. Vui lòng thử làm mới.";
                feedback.color = new Color32(185,65,87,255); empty.text = "Chưa tải được dữ liệu. Hãy thử lại.";
            }
            finally { if (this != null && !navigating) { loading = false; Controls(); } }
        }
        private void Controls()
        {
            refreshButton.interactable = !loading && !navigating && session.State == ConnectionState.Connected;
            refreshLabel.text = loading ? "Đang tải..." : "Làm mới";
        }
        public void Back() => Go("LobbyScene");
        private void Go(string scene) { if (navigating) return; navigating = true; SceneManager.LoadScene(scene); }
        private void OnDestroy()
        {
            if (session != null) session.StateChanged -= ConnectionChanged;
            backButton.onClick.RemoveListener(Back); refreshButton.onClick.RemoveListener(Refresh);
        }
    }
}
