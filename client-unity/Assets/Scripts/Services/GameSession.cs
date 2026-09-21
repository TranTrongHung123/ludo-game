using System;
using System.IO;
using System.Threading.Tasks;
using Newtonsoft.Json.Linq;

namespace Ludo.Services
{
    public sealed partial class NetworkSession
    {
        public JObject LastDice { get; private set; }
        public JObject GameOver { get; private set; }
        public event Action<string, JObject> GameEvent;

        // Responses and broadcasts may reach the main thread in a different order.
        private bool ApplyGameState(JObject snapshot)
        {
            if (snapshot == null || snapshot["participants"] is not JArray || snapshot["stateVersion"]?.Type != JTokenType.Integer)
                return false;
            string room = (string)Room?["roomId"];
            if (room == null || (string)snapshot["roomId"] != room) return false;
            if ((string)GameState?["matchId"] == (string)snapshot["matchId"] &&
                (long?)GameState?["stateVersion"] >= (long)snapshot["stateVersion"]) return false;
            if ((string)GameState?["matchId"] != (string)snapshot["matchId"]) { LastDice = null; GameOver = null; }
            GameState = snapshot;
            return true;
        }

        private void ApplyGameEvent(string type, JObject data)
        {
            if (GameState == null || data == null || (string)data["roomId"] != (string)GameState["roomId"]) return;
            if (type == "DICE_RESULT") LastDice = data;
            if (type == "GAME_OVER")
            {
                if ((string)data["matchId"] != (string)GameState["matchId"]) return;
                GameOver = data;
            }
            GameEvent?.Invoke(type, data);
        }

        public async Task RollDiceAsync()
        {
            string room = (string)GameState?["roomId"] ?? throw new IOException();
            var result = await AuthenticatedRequest("ROLL_DICE", new JObject { ["roomId"] = room }, "DICE_RESULT");
            if ((string)GameState?["roomId"] != room) return;
            LastDice = result;
            LobbyChanged?.Invoke();
        }

        public async Task MovePieceAsync(string pieceId)
        {
            string room = (string)GameState?["roomId"] ?? throw new IOException();
            var result = await AuthenticatedRequest("MOVE_PIECE", new JObject { ["roomId"] = room, ["pieceId"] = pieceId }, "MOVE_PIECE_RESULT");
            if (ApplyGameState(result["gameState"] as JObject)) LobbyChanged?.Invoke();
        }

        private void ClearGame() { GameState = null; LastDice = null; GameOver = null; }
    }
}
