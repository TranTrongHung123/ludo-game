using System.Globalization;
using Newtonsoft.Json.Linq;
using TMPro;
using UnityEngine;

namespace Ludo.Views
{
    public sealed class ResultRowView : MonoBehaviour
    {
        [SerializeField] private TMP_Text rank, playerName, colorName, status, earned, total;
        [SerializeField] private UnityEngine.UI.Image background, accent, medal;
        [SerializeField] private GameObject selfBadge;

        public void Bind(JToken standing, string selfId)
        {
            gameObject.SetActive(standing != null);
            if (standing == null) return;
            int place = (int)standing["rank"];
            bool self = (string)standing["playerId"] == selfId;
            bool forfeited = (string)standing["matchStatus"] == "FORFEITED";
            int slot = BoardGeometry.Slot((string)standing["color"]);
            rank.text = "#" + place;
            playerName.text = (string)standing["displayName"];
            colorName.text = slot >= 0 ? "Quân " + BoardGeometry.Names[slot].ToLowerInvariant() : "—";
            accent.color = slot >= 0 ? BoardGeometry.Tints[slot] : Color.gray;
            status.text = forfeited ? "Đã bỏ cuộc" : "Hoàn thành";
            status.color = forfeited ? new Color32(185,65,87,255) : new Color32(39,133,103,255);
            earned.text = "+" + Score(standing["scoreEarned"]);
            total.text = Score(standing["totalScore"]);
            selfBadge.SetActive(self);
            background.color = self ? new Color32(239,232,255,255) : new Color32(249,248,253,255);
            medal.color = place == 1 ? new Color32(251,224,154,255) : place == 2 ? new Color32(225,230,244,255) : place == 3 ? new Color32(244,218,200,255) : new Color32(235,229,245,255);
        }

        private static string Score(JToken value) => ((decimal?)value)?.ToString("0.############################", CultureInfo.InvariantCulture) ?? "—";
    }
}
