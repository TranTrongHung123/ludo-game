using System;
using System.IO;
using System.Threading.Tasks;
using Newtonsoft.Json.Linq;

namespace Ludo.Services
{
    public sealed class ServerRequestException : Exception
    {
        public string Code { get; }
        public ServerRequestException(string code) : base("Server rejected request.") { Code = code; }
    }

    public sealed partial class NetworkSession
    {
        public JArray OnlinePlayers { get; private set; } = new JArray();
        public JObject Invitation { get; private set; }
        public JObject Room { get; private set; }
        public JObject GameState { get; private set; }
        public event Action LobbyChanged;
        private bool reconnecting;
        private int onlineVersion;

        private async Task ReconnectAsync()
        {
            if (reconnecting || disposed) return;
            reconnecting = true;
            var deadline = DateTime.UtcNow.AddSeconds(60);
            try
            {
                while (!disposed && SessionId != null && DateTime.UtcNow < deadline)
                {
                    await Task.Delay(1000);
                    if (disposed || SessionId == null) return;
                    await ConnectAsync();
                    if (State == ConnectionState.Connected) { LobbyChanged?.Invoke(); return; }
                }
                if (!disposed && SessionId != null)
                {
                    ClearAuthentication();
                    AuthNotice = "Không thể khôi phục kết nối. Vui lòng đăng nhập lại.";
                    SetState(ConnectionState.Disconnected);
                }
            }
            finally { reconnecting = false; }
        }

        private void ApplyLobbyEvent(JObject message)
        {
            if (SessionId == null) return;
            var data = message["data"] as JObject;
            switch ((string)message["type"])
            {
                case "ONLINE_PLAYERS_UPDATED":
                    if (data?["players"] is not JArray players) return;
                    OnlinePlayers = players; onlineVersion++; break;
                case "INVITE_PLAYER":
                    if (data?["invitationId"]?.Type != JTokenType.String || data["expiresAtEpochMillis"]?.Type != JTokenType.Integer) return;
                    Invitation = data; break;
                case "ROOM_UPDATED":
                    if (data == null) return;
                    Room = data["room"] as JObject; break;
                case "GAME_STATE":
                case "GAME_STATE_UPDATED":
                    if (data == null) return;
                    if (!ApplyGameState(data)) return; break;
                case "DICE_RESULT":
                case "TURN_TIMEOUT":
                case "GAME_OVER":
                    ApplyGameEvent((string)message["type"], data); return;
                case "CHAT_MESSAGE":
                    ApplyChatEvent(data); return;
                default: return;
            }
            LobbyChanged?.Invoke();
        }

        private async Task<JObject> AuthenticatedRequest(string type, JObject data, string responseType = null)
        {
            if (State != ConnectionState.Connected || SessionId == null) throw new IOException();
            var activeClient = client;
            var response = await activeClient.RequestAsync(type, data, SessionId, responseType);
            if (disposed || activeClient != client) throw new IOException();
            if ((bool?)response["success"] != true)
            {
                string code = (string)response["error"]?["code"];
                if (code == "SESSION_EXPIRED" || code == "UNAUTHORIZED")
                { ClearAuthentication(); activeClient.Dispose(); SetState(ConnectionState.Disconnected); }
                throw new ServerRequestException(code);
            }
            return response["data"] as JObject ?? throw new InvalidDataException();
        }

        public async Task RefreshOnlineAsync()
        {
            int version = onlineVersion;
            var data = await AuthenticatedRequest("GET_ONLINE_PLAYERS", new JObject(), "ONLINE_PLAYERS_UPDATED");
            if (data["players"] is not JArray players) throw new InvalidDataException();
            if (version == onlineVersion) OnlinePlayers = players;
            LobbyChanged?.Invoke();
        }
        public async Task EnterRoomAsync(string type, string value = null)
        {
            if (type != "CREATE_ROOM" && type != "JOIN_ROOM" && type != "ACCEPT_INVITE") throw new ArgumentException(nameof(type));
            var data = type == "CREATE_ROOM" ? new JObject() : new JObject { [type == "JOIN_ROOM" ? "roomId" : "invitationId"] = value };
            var result = await AuthenticatedRequest(type, data);
            Room = result["room"] as JObject ?? throw new InvalidDataException();
            Invitation = null;
            LobbyChanged?.Invoke();
        }
        public void ClearInvitation(string id)
        {
            if ((string)Invitation?["invitationId"] == id) { Invitation = null; LobbyChanged?.Invoke(); }
        }
        public async Task RejectInvitationAsync(string id)
        {
            await AuthenticatedRequest("REJECT_INVITE", new JObject { ["invitationId"] = id });
            ClearInvitation(id);
        }
        public async Task LogoutAsync()
        {
            await AuthenticatedRequest("LOGOUT", new JObject());
            ClearAuthentication();
            AuthNotice = "Đã đăng xuất an toàn.";
        }
        private void ClearAuthentication()
        {
            SessionId = null; Profile = null; Room = null; ClearGame(); Invitation = null;
            OnlinePlayers = new JArray(); onlineVersion++;
        }
    }
}
