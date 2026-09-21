using System.IO;
using System.Threading.Tasks;
using Newtonsoft.Json.Linq;

namespace Ludo.Services
{
    public sealed partial class NetworkSession
    {
        public async Task<JArray> GetRankingAsync()
        {
            var data = await AuthenticatedRequest("GET_RANKING", new JObject(), "RANKING_RESULT");
            if (data["entries"] is not JArray entries) throw new InvalidDataException("Missing ranking entries.");
            foreach (var entry in entries)
            {
                if (entry is not JObject || entry["rank"]?.Type != JTokenType.Integer || (int)entry["rank"] < 1 ||
                    entry["playerId"]?.Type != JTokenType.String || entry["displayName"]?.Type != JTokenType.String ||
                    (entry["totalScore"]?.Type != JTokenType.Integer && entry["totalScore"]?.Type != JTokenType.Float) ||
                    entry["firstPlaceCount"]?.Type != JTokenType.Integer || (int)entry["firstPlaceCount"] < 0)
                    throw new InvalidDataException("Invalid ranking entry.");
            }
            return entries;
        }
    }
}
