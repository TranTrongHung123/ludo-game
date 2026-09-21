using System.Globalization;
using Newtonsoft.Json.Linq;
using TMPro;
using UnityEngine;

namespace Ludo.Views
{
    public sealed class RankingRowView : MonoBehaviour
    {
        [SerializeField] private TMP_Text rank, playerName, initial, score, wins;
        [SerializeField] private UnityEngine.UI.Image background, medal, accent;
        [SerializeField] private GameObject selfBadge, crown;

        public void Bind(JToken entry, string selfId)
        {
            int place = (int)entry["rank"];
            bool self = (string)entry["playerId"] == selfId;
            string name = (string)entry["displayName"];
            rank.text = "#" + place;
            playerName.text = name;
            initial.text = string.IsNullOrEmpty(name) ? "?" : StringInfo.GetNextTextElement(name).ToUpperInvariant();
            score.text = ((decimal)entry["totalScore"]).ToString("0.############################", CultureInfo.InvariantCulture);
            wins.text = entry["firstPlaceCount"].ToString();
            selfBadge.SetActive(self); crown.SetActive(place == 1);
            Color tint = place == 1 ? new Color32(246,208,117,255) : place == 2 ? new Color32(202,211,233,255) : place == 3 ? new Color32(232,192,166,255) : new Color32(225,217,242,255);
            medal.color = tint; accent.color = tint;
            background.color = self ? new Color32(238,231,253,255) : place <= 3 ? Color.Lerp(tint, Color.white, .86f) : new Color32(249,248,253,255);
        }
    }
}
