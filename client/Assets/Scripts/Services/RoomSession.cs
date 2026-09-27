using System.IO;
using System.Threading.Tasks;
using Newtonsoft.Json.Linq;

namespace Ludo.Services
{
    public sealed partial class NetworkSession
    {
        // Gửi trạng thái Ready và áp dụng phòng được Server trả về.
        public async Task SetReadyAsync(bool ready)
        {
            string id = (string)Room?["roomId"] ?? throw new IOException();
            var result = await AuthenticatedRequest(ready ? "READY" : "UNREADY", new JObject { ["roomId"] = id, ["ready"] = ready });
            ApplyRoom(result["room"] as JObject ?? throw new InvalidDataException());
            LobbyChanged?.Invoke();
        }
        // Gửi ID người được mời để Server kiểm tra điều kiện nhận lời mời.
        public async Task InvitePlayerAsync(string playerId)
        {
            string id = (string)Room?["roomId"] ?? throw new IOException();
            await AuthenticatedRequest("INVITE_PLAYER", new JObject { ["roomId"] = id, ["playerId"] = playerId });
        }
        // Chờ Server xác nhận rời phòng rồi dọn dữ liệu phòng và trận phía client.
        public async Task LeaveRoomAsync()
        {
            string id = (string)Room?["roomId"] ?? throw new IOException();
            await AuthenticatedRequest("LEAVE_ROOM", new JObject { ["roomId"] = id });
            Room = null; ClearGame(); Invitation = null;
            LobbyChanged?.Invoke();
        }
        // Yêu cầu chủ phòng bắt đầu; chỉ áp dụng Game State do Server khởi tạo.
        public async Task StartGameAsync()
        {
            string id = (string)Room?["roomId"] ?? throw new IOException();
            ApplyGameState(await AuthenticatedRequest("START_GAME", new JObject { ["roomId"] = id }));
            LobbyChanged?.Invoke();
        }
    }
}
