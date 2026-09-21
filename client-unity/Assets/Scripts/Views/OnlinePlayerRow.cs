using System.Globalization;
using Newtonsoft.Json.Linq;
using TMPro;
using UnityEngine;

namespace Ludo.Views
{
    public sealed class OnlinePlayerRow : MonoBehaviour
    {
        [SerializeField] private TMP_Text number, playerName, presence, score, wins, initial;
        [SerializeField] private UnityEngine.UI.Image background;
        public void Bind(JObject player, int index, string currentId)
        {
            bool self = (string)player["playerId"] == currentId;
            string name = (string)player["displayName"] ?? "";
            number.text = (index + 1).ToString();
            playerName.text = name + (self ? " (Bạn)" : "");
            initial.text = name.Length == 0 ? "?" : StringInfo.GetNextTextElement(name).ToUpperInvariant();
            score.text = player["totalScore"]?.ToString() ?? "—";
            wins.text = player["firstPlaceCount"]?.ToString() ?? "—";
            presence.text = Presence((string)player["presenceState"]);
            presence.color = (string)player["presenceState"] == "IDLE" ? new Color32(45,142,106,255) : new Color32(119,103,151,255);
            background.color = self ? new Color32(240,233,252,255) : index % 2 == 0 ? new Color32(250,249,253,255) : Color.white;
        }
        private static string Presence(string state)
        {
            switch(state)
            {
                case "IDLE": return "Đang rỗi";
                case "IN_ROOM": return "Trong phòng";
                case "PLAYING": return "Đang thi đấu";
                case "SPECTATING": return "Đang xem";
                case "DISCONNECTED": return "Mất kết nối";
                case "OFFLINE": return "Ngoại tuyến";
                default: return "Chưa rõ";
            }
        }
    }
}
