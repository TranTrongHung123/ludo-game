using System.IO;
using System.Threading.Tasks;
using Newtonsoft.Json.Linq;

namespace Ludo.Services
{
    public sealed partial class NetworkSession
    {
        public async Task SetReadyAsync(bool ready)
        {
            string id = (string)Room?["roomId"] ?? throw new IOException();
            var result = await AuthenticatedRequest(ready ? "READY" : "UNREADY", new JObject { ["roomId"] = id, ["ready"] = ready });
            Room = result["room"] as JObject ?? throw new InvalidDataException();
            LobbyChanged?.Invoke();
        }
        public async Task InvitePlayerAsync(string playerId)
        {
            string id = (string)Room?["roomId"] ?? throw new IOException();
            await AuthenticatedRequest("INVITE_PLAYER", new JObject { ["roomId"] = id, ["playerId"] = playerId });
        }
        public async Task LeaveRoomAsync()
        {
            string id = (string)Room?["roomId"] ?? throw new IOException();
            await AuthenticatedRequest("LEAVE_ROOM", new JObject { ["roomId"] = id });
            Room = null; ClearGame(); Invitation = null;
            LobbyChanged?.Invoke();
        }
        public async Task StartGameAsync()
        {
            string id = (string)Room?["roomId"] ?? throw new IOException();
            ApplyGameState(await AuthenticatedRequest("START_GAME", new JObject { ["roomId"] = id }));
            LobbyChanged?.Invoke();
        }
    }
}
