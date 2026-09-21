using System.Linq;
using Newtonsoft.Json.Linq;
using TMPro;
using UnityEngine;

namespace Ludo.Views
{
    public sealed class PlayerCardView : MonoBehaviour
    {
        [SerializeField] private TMP_Text playerName, status, progress, colorName;
        [SerializeField] private UnityEngine.UI.Image accent, highlight;
        public void Bind(JToken player, int slot, string self, string current)
        {
            accent.color = BoardGeometry.Tints[slot]; colorName.text = BoardGeometry.Names[slot].ToUpperInvariant(); colorName.color = accent.color;
            highlight.gameObject.SetActive(player != null && (string)player["playerId"] == current);
            playerName.text = player == null ? "Không có người chơi" : (string)player["displayName"] + ((string)player["playerId"] == self ? " (Bạn)" : "");
            status.text = player == null ? "Vị trí trống" : (string)player["matchStatus"] == "FORFEITED" ? "Đã bỏ cuộc" : (string)player["matchStatus"] == "COMPLETED" ? "Hoàn thành • Hạng " + player["rank"] : (string)player["presenceState"] == "DISCONNECTED" ? "Mất kết nối • Chờ quay lại" : (string)player["playerId"] == current ? "Đang đến lượt" : "Đang chờ lượt";
            var pieces = player?["pieces"] as JArray ?? new JArray();
            progress.text = player == null ? "—" : pieces.Count(p => (string)p["state"] == "FINISHED") + "/4 về đích   •   " + pieces.Count(p => (string)p["state"] == "IN_YARD") + " trong chuồng";
        }
    }
}
