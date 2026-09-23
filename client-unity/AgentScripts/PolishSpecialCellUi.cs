using System;
using System.Linq;
using Ludo.Views;
using TMPro;
using UnityEditor;
using UnityEngine;

public static class PolishSpecialCellUi
{
    static RectTransform Rect(string name, Transform parent, Vector2 position, Vector2 size)
    {
        var child = parent.Find(name);
        var rect = child != null ? (RectTransform)child : new GameObject(name,typeof(RectTransform)).GetComponent<RectTransform>();
        rect.SetParent(parent,false); rect.anchorMin=rect.anchorMax=rect.pivot=new Vector2(0,1);
        rect.anchoredPosition=position; rect.sizeDelta=size; return rect;
    }
    static void FormatTip(Transform content)
    {
        var tip=(RectTransform)content.Find("Tip"); tip.sizeDelta=new Vector2(368,96);
        var old=tip.Find("TipText").GetComponent<TMP_Text>();
        old.text=""; old.gameObject.SetActive(false);
        var entries=Rect("Entries",tip,new Vector2(16,-34),new Vector2(336,52));
        var grid=entries.GetComponent<UnityEngine.UI.GridLayoutGroup>() ?? entries.gameObject.AddComponent<UnityEngine.UI.GridLayoutGroup>();
        grid.cellSize=new Vector2(162,24); grid.spacing=new Vector2(12,4);
        grid.constraint=UnityEngine.UI.GridLayoutGroup.Constraint.FixedColumnCount; grid.constraintCount=2;
        string[] names={"Speed","Slow","Lucky","Trap"};
        string[] symbols={"+3","−1","","!"};
        string[] labels={"Tiến 3 bước","Lùi 1 bước","Tung thêm 1 lần","Về chuồng"};
        for(int i=0;i<4;i++)
        {
            var row=Rect(names[i],entries,Vector2.zero,new Vector2(162,24));
            foreach(bool isSymbol in new[]{true,false})
            {
                var r=Rect(isSymbol?"Symbol":"Description",row,new Vector2(isSymbol?0:34,0),new Vector2(isSymbol?28:128,24));
                var text=r.GetComponent<TextMeshProUGUI>() ?? r.gameObject.AddComponent<TextMeshProUGUI>();
                text.font=old.font; text.fontSize=17; text.text=isSymbol?symbols[i]:labels[i];
                text.color=isSymbol?new Color32(124,88,210,255):old.color;
                text.alignment=isSymbol?TextAlignmentOptions.Center:TextAlignmentOptions.MidlineLeft;
                text.textWrappingMode=TextWrappingModes.NoWrap; text.richText=false; text.raycastTarget=false;
                if(i==2&&isSymbol) Icon(r,Vector2.zero,new Vector2(26,24)).SetActive(true);
            }
        }
    }
    static GameObject Icon(Transform parent, Vector2 position, Vector2 size)
    {
        var existing = parent.Find("BonusRollIcon");
        var go = existing != null ? existing.gameObject : new GameObject("BonusRollIcon", typeof(RectTransform), typeof(BonusRollGraphic));
        var rect = (RectTransform)go.transform;
        rect.SetParent(parent, false); rect.anchorMin = rect.anchorMax = rect.pivot = new Vector2(.5f,.5f);
        rect.anchoredPosition = position; rect.sizeDelta = size;
        var graphic = go.GetComponent<BonusRollGraphic>();
        if (go.GetComponent<CanvasRenderer>() == null) go.AddComponent<CanvasRenderer>();
        graphic.color = new Color32(124,88,210,255); graphic.raycastTarget = false;
        return go;
    }
    public static string Main()
    {
        if (EditorApplication.isPlaying) throw new Exception("Run outside Play Mode.");
        const string path = "Assets/Prefabs/Game/GameScreen.prefab";
        var root = PrefabUtility.LoadPrefabContents(path);
        try
        {
            var board = root.GetComponentInChildren<BoardView>(true);
            var so = new SerializedObject(board);
            var cells = so.FindProperty("cells"); var icons = so.FindProperty("bonusRollIcons"); icons.arraySize = cells.arraySize;
            for (int i = 0; i < cells.arraySize; i++)
            {
                var label = (TMP_Text)cells.GetArrayElementAtIndex(i).objectReferenceValue;
                var icon = Icon(label.transform, Vector2.zero, new Vector2(36,32)); icon.SetActive(false);
                icons.GetArrayElementAtIndex(i).objectReferenceValue = icon;
            }
            so.ApplyModifiedPropertiesWithoutUndo();
            var content = root.transform.Find("Content");
            var legend = content.Find("BoardCard/Legend"); legend.GetComponent<TMP_Text>().text = ""; legend.gameObject.SetActive(false);
            FormatTip(content);
            var notice = (RectTransform)content.Find("BoardNotice");
            notice.anchoredPosition = new Vector2(-60,392); notice.sizeDelta = new Vector2(640,84);
            var image = notice.GetComponent<UnityEngine.UI.Image>(); image.color = new Color32(69,45,112,255);
            var message = notice.Find("Message").GetComponent<TMP_Text>();
            message.rectTransform.sizeDelta = new Vector2(596,68); message.fontSize = 25; message.color = Color.white;
            ((RectTransform)notice.Find("Accent")).anchoredPosition = new Vector2(-307,0);
            notice.SetSiblingIndex(content.Find("ForfeitConfirmation").GetSiblingIndex());
            PrefabUtility.SaveAsPrefabAsset(root,path);
        }
        finally { PrefabUtility.UnloadPrefabContents(root); }
        const string piecePath = "Assets/Prefabs/Game/Piece.prefab";
        var piece = PrefabUtility.LoadPrefabContents(piecePath);
        try
        {
            var icon = Icon(piece.transform.Find("Effect"), Vector2.zero, new Vector2(30,27)); icon.SetActive(false);
            var so = new SerializedObject(piece.GetComponent<PieceView>()); so.FindProperty("bonusRollIcon").objectReferenceValue = icon; so.ApplyModifiedPropertiesWithoutUndo();
            PrefabUtility.SaveAsPrefabAsset(piece,piecePath);
        }
        finally { PrefabUtility.UnloadPrefabContents(piece); }
        return "Saved dice-plus symbols, compact tips and prominent board notification.";
    }
    public static string SyncGameScene()
    {
        if(EditorApplication.isPlaying) throw new Exception("Run outside Play Mode.");
        var scene=UnityEngine.SceneManagement.SceneManager.GetSceneByPath("Assets/Scenes/GameScene.unity");
        if(!scene.IsValid() || !scene.isLoaded) throw new Exception("Open GameScene before syncing.");
        if(scene.isDirty) throw new Exception("Preserve unsaved scene edits before syncing.");
        var root=scene.GetRootGameObjects().Single(o=>o.GetComponent<Ludo.Controllers.GameController>()!=null);
        Undo.RegisterFullObjectHierarchyUndo(root,"Sync special-cell UI");
        var content=root.transform.Find("Content"); FormatTip(content);
        var legend=content.Find("BoardCard/Legend"); legend.GetComponent<TMP_Text>().text=""; legend.gameObject.SetActive(false);
        var source=AssetDatabase.LoadAssetAtPath<GameObject>("Assets/Prefabs/Game/GameScreen.prefab");
        var board=root.GetComponentInChildren<BoardView>(true);
        var sourceBoard=source.GetComponentInChildren<BoardView>(true);
        var so=new SerializedObject(board);
        var cells=so.FindProperty("cells"); var icons=so.FindProperty("bonusRollIcons"); icons.arraySize=cells.arraySize;
        for(int i=0;i<cells.arraySize;i++)
        {
            var label=(TMP_Text)cells.GetArrayElementAtIndex(i).objectReferenceValue;
            var icon=Icon(label.transform,Vector2.zero,new Vector2(36,32)); icon.SetActive(false);
            icons.GetArrayElementAtIndex(i).objectReferenceValue=icon;
        }
        var notice=content.Find("BoardNotice");
        if(notice==null) {notice=UnityEngine.Object.Instantiate(source.transform.Find("Content/BoardNotice").gameObject,content,false).transform; notice.name="BoardNotice";}
        notice.SetSiblingIndex(content.Find("ForfeitConfirmation").GetSiblingIndex());
        so.FindProperty("notice").objectReferenceValue=notice.GetComponent<BoardNotice>();
        var counts=so.FindProperty("finishCounts"); counts.arraySize=4;
        for(int i=0;i<4;i++)
        {
            string name="FinishedPodium"+i; var podium=board.transform.Find(name);
            if(podium==null) {podium=UnityEngine.Object.Instantiate(sourceBoard.transform.Find(name).gameObject,board.transform,false).transform; podium.name=name;}
            podium.SetSiblingIndex(board.transform.Find("Pieces").GetSiblingIndex());
            counts.GetArrayElementAtIndex(i).objectReferenceValue=podium.Find("Count").GetComponent<TMP_Text>();
        }
        so.ApplyModifiedPropertiesWithoutUndo();
        UnityEditor.SceneManagement.EditorSceneManager.MarkSceneDirty(scene);
        if(!UnityEditor.SceneManagement.EditorSceneManager.SaveScene(scene)) throw new Exception("Unable to save GameScene.");
        return "Saved actual GameScene: aligned tips, 48 symbol bindings, notice and four finish trays. Existing scene objects preserved.";
    }
}
