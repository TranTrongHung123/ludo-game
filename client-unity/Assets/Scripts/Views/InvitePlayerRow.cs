using System;
using Newtonsoft.Json.Linq;
using TMPro;
using UnityEngine;
namespace Ludo.Views
{
    public sealed class InvitePlayerRow : MonoBehaviour
    {
        [SerializeField] private TMP_Text playerName, score;
        [SerializeField] private UnityEngine.UI.Button selectButton, inviteButton;
        [SerializeField] private UnityEngine.UI.Image background;
        public string PlayerId { get; private set; }
        public void Bind(JObject player, bool enabled, string selectedId, Action<string> select, Action<string> invite)
        {
            PlayerId = (string)player["playerId"];
            playerName.text = (string)player["displayName"];
            score.text = player["totalScore"] + " điểm";
            background.color = selectedId == PlayerId ? new Color32(235,224,250,255) : new Color32(249,247,253,255);
            selectButton.onClick.RemoveAllListeners(); inviteButton.onClick.RemoveAllListeners();
            selectButton.onClick.AddListener(() => select(PlayerId)); inviteButton.onClick.AddListener(() => invite(PlayerId));
            selectButton.interactable = inviteButton.interactable = enabled;
        }
    }
}
