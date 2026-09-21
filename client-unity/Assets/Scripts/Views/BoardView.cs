using System;
using System.Collections.Generic;
using System.Linq;
using Newtonsoft.Json.Linq;
using TMPro;
using UnityEngine;

namespace Ludo.Views
{
    public sealed class BoardView : MonoBehaviour
    {
        [SerializeField] private RectTransform pieceLayer;
        [SerializeField] private PieceView piecePrefab;
        [SerializeField] private TMP_Text[] cells;
        private readonly Dictionary<string, PieceView> pieces = new Dictionary<string, PieceView>();
        private bool initialized;
        public void Bind(JObject state, string self, string selected, bool canMove, Action<string> select, bool animate)
        {
            for (int i = 0; i < cells.Length; i++) cells[i].text = i % 12 == 0 ? "→" : "";
            foreach (var cell in state["specialCells"] as JArray ?? new JArray())
            {
                int index = (int?)cell["globalIndex"] ?? -1;
                if (index < 0 || index >= cells.Length) continue;
                cells[index].text = (string)cell["type"] switch { "SPEED" => "+2", "SLOW" => "−2", "LUCKY" => "+1", "TRAP" => "!", "SHIELD" => "K", _ => "" };
            }
            var valid = new HashSet<string>((state["validPieceIds"] as JArray ?? new JArray()).Values<string>());
            var visible = new HashSet<string>();
            foreach (var participant in state["participants"] as JArray ?? new JArray())
            {
                if ((string)participant["matchStatus"] == "FORFEITED") continue;
                int slot = BoardGeometry.Slot((string)participant["color"]);
                if (slot < 0) continue;
                int index = 0;
                foreach (var piece in participant["pieces"] as JArray ?? new JArray())
                {
                    string id = (string)piece["pieceId"];
                    if (!pieces.TryGetValue(id, out var view)) pieces[id] = view = Instantiate(piecePrefab, pieceLayer);
                    visible.Add(id); view.gameObject.SetActive(true);
                    view.Bind(id, slot, index++, (int)piece["stepCount"], (bool?)piece["shielded"] == true, (bool?)piece["slowed"] == true,
                        canMove && (string)piece["ownerPlayerId"] == self && valid.Contains(id), id == selected, select, initialized && animate);
                }
            }
            foreach (var item in pieces) if (!visible.Contains(item.Key)) item.Value.gameObject.SetActive(false);
            initialized = true;
        }
    }
}
