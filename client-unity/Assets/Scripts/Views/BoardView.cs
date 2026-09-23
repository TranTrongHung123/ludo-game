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
        [SerializeField] private UnityEngine.UI.Image[] ownYardHighlights;
        [SerializeField] private TMP_Text[] yardNames;
        [SerializeField] private TMP_Text[] finishCounts;
        [SerializeField] private BoardNotice notice;
        private long renderedVersion = -1;
        private string renderedMatch;
        public bool IsAnimating => pieces.Values.Any(piece => piece != null && piece.IsAnimating);
        public bool HasNotice => notice != null && notice.Visible;
        public void ResetPresentation()
        {
            foreach (var piece in pieces.Values) if (piece != null) piece.Snap();
            if (notice != null) notice.Clear();
            initialized = false;
        }
        private int ownSlot = -1;
        private void Update()
        {
            if (ownSlot < 0 || ownSlot >= ownYardHighlights.Length) return;
            var tint = Color.Lerp(BoardGeometry.Tints[ownSlot], Color.black,
                .16f + .04f * Mathf.Sin(Time.unscaledTime * 3f));
            tint.a = 1f;
            ownYardHighlights[ownSlot].color = tint;
        }
        private readonly Dictionary<string, PieceView> pieces = new Dictionary<string, PieceView>();
        private bool initialized;
        public void Bind(JObject state, string self, string selected, bool canMove, Action<string> select, bool animate)
        {
            long version = (long?)state["stateVersion"] ?? -1;
            string match = (string)state["matchId"];
            bool sameMatch = match == renderedMatch;
            if (!sameMatch) ResetPresentation();
            var move = initialized && animate && sameMatch && version == renderedVersion + 1 ? state["lastMove"] as JObject : null;
            renderedVersion = version; renderedMatch = match;
            for (int i = 0; i < cells.Length; i++) cells[i].text = i % 12 == 0 ? new[]{"→", "↓", "←", "↑"}[i / 12] : "";
            ownSlot = BoardGeometry.Slot((string)(state["participants"] as JArray)?.FirstOrDefault(p => (string)p["playerId"] == self)?["color"]);
            for (int i = 0; i < ownYardHighlights.Length; i++)
            {
                ownYardHighlights[i].gameObject.SetActive(i == ownSlot);
                yardNames[i].text = BoardGeometry.Names[i].ToUpperInvariant() + (i == ownSlot ? " • BẠN" : "");
            }
            foreach (var cell in state["specialCells"] as JArray ?? new JArray())
            {
                int index = (int?)cell["globalIndex"] ?? -1;
                if (index < 0 || index >= cells.Length) continue;
                cells[index].text = (string)cell["type"] switch { "SPEED" => "+2", "SLOW" => "−2", "LUCKY" => "+1", "TRAP" => "!", _ => "" };
            }
            var valid = new HashSet<string>((state["validPieceIds"] as JArray ?? new JArray()).Values<string>());
            var visible = new HashSet<string>();
            if (finishCounts != null) foreach (var count in finishCounts) if (count != null) count.text = "VỀ ĐÍCH  0/4";
            foreach (var participant in state["participants"] as JArray ?? new JArray())
            {
                if ((string)participant["matchStatus"] == "FORFEITED") continue;
                int slot = BoardGeometry.Slot((string)participant["color"]);
                if (slot < 0) continue;
                if (finishCounts != null && slot < finishCounts.Length && finishCounts[slot] != null)
                    finishCounts[slot].text = "VỀ ĐÍCH  " + (participant["pieces"] as JArray ?? new JArray()).Count(p => (string)p["state"] == "FINISHED") + "/4";
                int index = 0;
                foreach (var piece in participant["pieces"] as JArray ?? new JArray())
                {
                    string id = (string)piece["pieceId"];
                    if (!pieces.TryGetValue(id, out var view)) pieces[id] = view = Instantiate(piecePrefab, pieceLayer);
                    visible.Add(id); view.gameObject.SetActive(true);
                    var pieceMove = (string)move?["pieceId"] == id ? move : null;
                    view.Bind(id, slot, index++, (int)piece["stepCount"],
                        canMove && (string)piece["ownerPlayerId"] == self && valid.Contains(id), id == selected, select, initialized && animate,
                        pieceMove, message => { if (notice != null) notice.Show(BoardGeometry.Names[slot] + " • " + message, BoardGeometry.Tints[slot]); });
                }
            }
            foreach (var item in pieces) if (!visible.Contains(item.Key)) item.Value.gameObject.SetActive(false);
            initialized = true;
        }
    }
}
