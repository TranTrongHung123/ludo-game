using Newtonsoft.Json.Linq;
using TMPro;
using UnityEngine;

namespace Ludo.Views
{
    public sealed class RoomSlotView : MonoBehaviour
    {
        [SerializeField] private TMP_Text playerName, hostBadge, readyStatus, presence;
        [SerializeField] private UnityEngine.UI.Image hostFrame;
        public void Bind(JObject player, string hostId, string selfId)
        {
            playerName.text = player == null ? "Chưa có người chơi" : (string)player["displayName"] + ((string)player["playerId"] == selfId ? " (Bạn)" : "");
            hostBadge.gameObject.SetActive(player != null && (string)player["playerId"] == hostId);
            hostFrame.gameObject.SetActive(player != null && (string)player["playerId"] == hostId);
            readyStatus.text = player == null ? "Đang chờ bạn bè tham gia" : (bool?)player["ready"] == true ? "Đã sẵn sàng" : "Chưa sẵn sàng";
            readyStatus.color = (bool?)player?["ready"] == true ? new Color32(47,150,111,255) : new Color32(119,118,143,255);
            presence.text = player == null ? "" : (string)player["presenceState"] == "IN_ROOM" ? "Đang trong phòng" : (string)player["presenceState"] == "DISCONNECTED" ? "Mất kết nối" : "Đang thi đấu";
        }
    }
}
