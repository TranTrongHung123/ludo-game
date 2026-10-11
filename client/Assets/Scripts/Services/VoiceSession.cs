using System.IO;
using System.Threading.Tasks;
using Newtonsoft.Json.Linq;

namespace Ludo.Services
{
    public sealed partial class NetworkSession
    {
        // Token UDP chỉ được cấp qua TCP sau khi Server xác thực người chơi đang ở trong trận.
        public async Task<JObject> RegisterVoiceAsync(string roomId)
        {
            if (string.IsNullOrWhiteSpace(roomId)) throw new IOException("Phòng không hợp lệ.");
            return await AuthenticatedRequest(
                "VOICE_REGISTER",
                new JObject { ["roomId"] = roomId },
                "VOICE_REGISTERED");
        }
    }
}
