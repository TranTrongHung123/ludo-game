using System;
using System.Globalization;
using Newtonsoft.Json.Linq;
using TMPro;
using UnityEngine;

namespace Ludo.Views
{
    public sealed class HistoryRowView : MonoBehaviour
    {
        [SerializeField] private TMP_Text rank, time, metadata, score, status;
        [SerializeField] private UnityEngine.UI.Image background, medal, accent;
        [SerializeField] private GameObject crown;
        public void Bind(JToken match)
        {
            int place = (int)match["rank"];
            bool forfeited = (bool)match["forfeited"];
            int slot = BoardGeometry.Slot((string)match["color"]);
            rank.text = "Hạng " + place + "/" + match["playerCount"];
            time.text = DateTimeOffset.FromUnixTimeMilliseconds((long)match["endedAtEpochMillis"]).ToLocalTime().ToString("dd/MM/yyyy • HH:mm", CultureInfo.InvariantCulture);
            metadata.text = "Quân " + BoardGeometry.Names[slot].ToLowerInvariant() + " • " + (string)match["matchId"];
            score.text = "+" + ((decimal)match["scoreEarned"]).ToString("0.############################", CultureInfo.InvariantCulture);
            status.text = forfeited ? "Bỏ cuộc" : "Hoàn thành";
            status.color = forfeited ? new Color32(185,65,87,255) : new Color32(39,133,103,255);
            score.color = forfeited ? status.color : new Color32(124,88,210,255);
            accent.color = BoardGeometry.Tints[slot];
            crown.SetActive(place == 1 && !forfeited);
            medal.color = place == 1 && !forfeited ? new Color32(251,224,154,255) : new Color32(234,227,247,255);
            background.color = forfeited ? new Color32(253,245,247,255) : place == 1 ? new Color32(253,249,238,255) : new Color32(249,248,253,255);
        }
    }
}
