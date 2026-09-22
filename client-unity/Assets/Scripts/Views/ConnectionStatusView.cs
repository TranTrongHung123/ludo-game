using Ludo.Services;
using TMPro;
using UnityEngine;

namespace Ludo.Views
{
    public sealed class ConnectionStatusView : MonoBehaviour
    {
        [SerializeField] private UnityEngine.UI.Image background;
        [SerializeField] private TMP_Text label;
        private NetworkSession session;
        private void Start()
        {
            session = NetworkSession.Instance;
            if (session != null) session.StateChanged += Render;
            Render(session == null ? ConnectionState.Disconnected : session.State);
        }
        private void Render(ConnectionState state)
        {
            background.color = state == ConnectionState.Connected ? new Color32(211,244,227,255) :
                state == ConnectionState.Connecting ? new Color32(255,235,188,255) : new Color32(255,218,225,255);
            label.color = state == ConnectionState.Connected ? new Color32(22,100,67,255) :
                state == ConnectionState.Connecting ? new Color32(120,77,12,255) : new Color32(151,40,63,255);
        }
        private void OnDestroy() { if (session != null) session.StateChanged -= Render; }
    }
}
