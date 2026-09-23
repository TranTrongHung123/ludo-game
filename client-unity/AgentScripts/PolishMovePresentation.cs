using System;
using System.Linq;
using Ludo.Views;
using TMPro;
using UnityEditor;
using UnityEngine;

// Targeted, idempotent authoring. Does not rebuild the existing screen or touch open scenes.
public static class PolishMovePresentation
{
    static TMP_FontAsset Font => AssetDatabase.LoadAssetAtPath<TMP_FontAsset>("Assets/Fonts/Ludo Vietnamese SDF.asset");
    static RectTransform Rect(string name, Transform parent, Vector2 position, Vector2 size)
    {
        var found = parent.Find(name);
        var rect = found != null ? (RectTransform)found : new GameObject(name, typeof(RectTransform)).GetComponent<RectTransform>();
        rect.SetParent(parent, false); rect.anchorMin = rect.anchorMax = rect.pivot = new Vector2(.5f,.5f);
        rect.anchoredPosition = position; rect.sizeDelta = size; return rect;
    }
    static UnityEngine.UI.Image Image(RectTransform rect, Color color, string sprite)
    {
        var image = rect.GetComponent<UnityEngine.UI.Image>() ?? rect.gameObject.AddComponent<UnityEngine.UI.Image>();
        image.sprite = AssetDatabase.LoadAssetAtPath<Sprite>("Assets/Art/Login/" + sprite + ".png");
        image.type = UnityEngine.UI.Image.Type.Sliced; image.color = color; image.raycastTarget = false; return image;
    }
    static TMP_Text Text(RectTransform rect, string value, Color color, float size)
    {
        var text = rect.GetComponent<TextMeshProUGUI>() ?? rect.gameObject.AddComponent<TextMeshProUGUI>();
        text.font = Font; text.text = value; text.fontSize = size; text.color = color;
        text.fontStyle = FontStyles.Bold; text.alignment = TextAlignmentOptions.Center;
        text.richText = false; text.raycastTarget = false; text.textWrappingMode = TextWrappingModes.NoWrap;
        text.overflowMode = TextOverflowModes.Truncate;
        return text;
    }
    static void Set(UnityEngine.Object target, string field, UnityEngine.Object value)
    {
        var so = new SerializedObject(target); so.FindProperty(field).objectReferenceValue = value; so.ApplyModifiedPropertiesWithoutUndo();
    }
    public static string Main()
    {
        if (EditorApplication.isPlaying) throw new Exception("Run outside Play Mode.");
        const string piecePath = "Assets/Prefabs/Game/Piece.prefab";
        var piece = PrefabUtility.LoadPrefabContents(piecePath);
        try
        {
            var badge = Rect("FinishBadge", piece.transform, new Vector2(17,20), new Vector2(22,22));
            Image(badge, new Color32(255,241,187,255), "circle");
            Image(Rect("Crown", badge, Vector2.zero, new Vector2(15,13)), new Color32(153,101,22,255), "crown");
            Set(piece.GetComponent<PieceView>(), "finishBadge", badge.gameObject); badge.gameObject.SetActive(false);
            var effect = piece.transform.Find("Effect").GetComponent<TMP_Text>(); effect.fontSize = 23; effect.fontStyle = FontStyles.Bold;
            effect.color = new Color32(92,53,174,255);
            PrefabUtility.SaveAsPrefabAsset(piece, piecePath);
        }
        finally { PrefabUtility.UnloadPrefabContents(piece); }
        const string path = "Assets/Prefabs/Game/GameScreen.prefab";
        var root = PrefabUtility.LoadPrefabContents(path);
        try
        {
            var board = root.GetComponentInChildren<BoardView>(true);
            var counts = new TMP_Text[4];
            for (int slot = 0; slot < 4; slot++)
            {
                var tint = BoardGeometry.Tints[slot];
                var podium = Rect("FinishedPodium" + slot, board.transform, BoardGeometry.Podium(slot), new Vector2(218,64));
                Image(podium, Color.Lerp(tint, Color.white, .9f), "rounded");
                counts[slot] = Text(Rect("Count", podium, new Vector2(0,22), new Vector2(204,18)), "VỀ ĐÍCH  0/4", Color.Lerp(tint, Color.black, .3f), 14);
                for (int i = 0; i < 4; i++)
                {
                    var socket = Rect("Place" + i, podium, new Vector2(-72 + 48*i,-9), new Vector2(39,39));
                    Image(socket, Color.white, "circle");
                    Text(Rect("Number",socket,Vector2.zero,new Vector2(30,30)),(i+1).ToString(),Color.Lerp(tint,Color.white,.55f),13);
                }
                podium.SetSiblingIndex(board.transform.Find("Pieces").GetSiblingIndex());
            }
            var so = new SerializedObject(board); var field = so.FindProperty("finishCounts"); field.arraySize = 4;
            for (int i=0;i<4;i++) field.GetArrayElementAtIndex(i).objectReferenceValue=counts[i];
            so.ApplyModifiedPropertiesWithoutUndo();
            var content = root.transform.Find("Content");
            // Prominent above the board, without intercepting gameplay input.
            var toast = Rect("BoardNotice", content, new Vector2(-60,392), new Vector2(640,84));
            Image(toast,new Color32(69,45,112,255),"rounded");
            var accent = Image(Rect("Accent",toast,new Vector2(-307,0),new Vector2(5,48)),new Color32(124,88,210,255),"rounded");
            var label = Text(Rect("Message",toast,new Vector2(5,0),new Vector2(596,68)),"",Color.white,25);
            label.textWrappingMode = TextWrappingModes.Normal;
            var group = toast.GetComponent<CanvasGroup>(); if (group == null) group = toast.gameObject.AddComponent<CanvasGroup>();
            group.alpha=0; group.blocksRaycasts=false; group.interactable=false;
            var notice=toast.GetComponent<BoardNotice>(); if (notice == null) notice=toast.gameObject.AddComponent<BoardNotice>();
            Set(notice,"group",group);Set(notice,"label",label);Set(notice,"accent",accent);Set(board,"notice",notice);
            toast.SetSiblingIndex(content.Find("ForfeitConfirmation").GetSiblingIndex());
            PrefabUtility.SaveAsPrefabAsset(root,path);
        }
        finally { PrefabUtility.UnloadPrefabContents(root); }
        return "Saved 4 finished podiums, horse crown badge and non-modal notice.";
    }
}
