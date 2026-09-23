// Edit Mode: run_script, entry VerifySpecialCells.Main. Uses isolated prefab contents.
using System;
using System.Linq;
using System.Reflection;
using Ludo.Views;
using Newtonsoft.Json.Linq;
using TMPro;
using UnityEditor;
using UnityEngine;

public static class VerifySpecialCells
{
    public static string Main()
    {
        if (EditorApplication.isPlaying) throw new Exception("Run outside Play Mode.");
        int checks = 0;
        Action<bool, string> check = (ok, message) => { if (!ok) throw new Exception(message); checks++; };
        var root = PrefabUtility.LoadPrefabContents("Assets/Prefabs/Game/GameScreen.prefab");
        try
        {
            var board = root.GetComponentInChildren<BoardView>(true);
            var cells = (TMP_Text[])typeof(BoardView).GetField("cells", BindingFlags.NonPublic | BindingFlags.Instance).GetValue(board);
            var specials = new JArray();
            var members = new JArray();
            string[] types = { "SPEED", "SLOW", "LUCKY", "TRAP" };
            string[] symbols = { "+2", "−2", "+1", "!" };
            for (int slot = 0; slot < 4; slot++)
            {
                for (int i = 0; i < 4; i++) specials.Add(new JObject { ["globalIndex"] = slot * 12 + 2 + i * 2, ["type"] = types[i] });
                members.Add(new JObject {
                    ["playerId"] = "p" + slot, ["color"] = BoardGeometry.Colors[slot], ["matchStatus"] = "ACTIVE",
                    ["pieces"] = new JArray(new JObject { ["pieceId"] = "p" + slot + "-0", ["ownerPlayerId"] = "p" + slot,
                        ["color"] = BoardGeometry.Colors[slot], ["state"] = "ON_TRACK", ["stepCount"] = 5 }) });
            }
            var snapshot = new JObject { ["participants"] = members, ["specialCells"] = specials, ["validPieceIds"] = new JArray() };
            board.Bind(snapshot, "p0", null, false, _ => {}, false);
            for (int slot = 0; slot < 4; slot++)
            {
                for (int i = 0; i < 4; i++) check(cells[slot * 12 + 2 + i * 2].text == symbols[i], "Special symbol");
                check(cells[slot * 12 + 10].text == "", "Former shield cell is ordinary");
            }
            var views = board.GetComponentsInChildren<PieceView>(true);
            check(views.Length == 4, "Pieces rendered without obsolete effect fields");
            // Server snapshots model immediate retreat, trap and reconnect; client only maps coordinates.
            foreach (int step in new[] { 2, -1, 0, 53 })
            {
                foreach (var member in members) { member["pieces"][0]["stepCount"] = step; member["pieces"][0]["state"] = step < 0 ? "IN_YARD" : step == 53 ? "FINISHED" : "ON_TRACK"; }
                board.Bind(snapshot, "p0", null, false, _ => {}, false);
                for (int slot = 0; slot < 4; slot++) check(Vector2.Distance(((RectTransform)views[slot].transform).anchoredPosition, BoardGeometry.Position(slot, step, 0)) < .01f, "Snapshot position for all colors");
            }
            snapshot["specialCells"] = new JArray();
            board.Bind(snapshot, "p0", null, false, _ => {}, false);
            check(cells[2].text == "" && cells[4].text == "" && cells[6].text == "" && cells[8].text == "", "New snapshot clears old layout");
            var legend = root.GetComponentsInChildren<TMP_Text>(true).Single(t => t.name == "Legend");
            check(legend.text.Contains("Về chuồng") && legend.text.Contains("Lùi 2 bước") && !legend.text.Contains("Khiên"), "Saved prefab legend matches rules");
            return checks + " special-cell checks passed";
        }
        finally { PrefabUtility.UnloadPrefabContents(root); }
    }
}
