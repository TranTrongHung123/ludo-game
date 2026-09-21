using System;
using System.IO;
using System.Threading.Tasks;
using Newtonsoft.Json.Linq;

namespace Ludo.Services
{
    public sealed partial class NetworkSession
    {
        public async Task<JArray> GetHistoryAsync()
        {
            var data = await AuthenticatedRequest("GET_MATCH_HISTORY", new JObject(), "MATCH_HISTORY_RESULT");
            if (data["matches"] is not JArray matches) throw new InvalidDataException("Missing match history.");
            foreach (var match in matches)
            {
                if (match is not JObject || match["matchId"]?.Type != JTokenType.String ||
                    match["startedAtEpochMillis"]?.Type != JTokenType.Integer || match["endedAtEpochMillis"]?.Type != JTokenType.Integer ||
                    match["playerCount"]?.Type != JTokenType.Integer || match["rank"]?.Type != JTokenType.Integer ||
                    match["color"]?.Type != JTokenType.String || match["forfeited"]?.Type != JTokenType.Boolean ||
                    (match["scoreEarned"]?.Type != JTokenType.Integer && match["scoreEarned"]?.Type != JTokenType.Float))
                    throw new InvalidDataException("Invalid match history entry.");
                int players = (int)match["playerCount"], rank = (int)match["rank"];
                long started = (long)match["startedAtEpochMillis"], ended = (long)match["endedAtEpochMillis"];
                string color = (string)match["color"];
                if (players < 2 || players > 4 || rank < 1 || rank > players || started <= 0 || ended < started ||
                    ended > DateTimeOffset.MaxValue.ToUnixTimeMilliseconds() ||
                    (color != "RED" && color != "BLUE" && color != "YELLOW" && color != "GREEN") ||
                    ((bool)match["forfeited"] && (decimal)match["scoreEarned"] != 0))
                    throw new InvalidDataException("Invalid match history values.");
            }
            return matches;
        }
    }
}
