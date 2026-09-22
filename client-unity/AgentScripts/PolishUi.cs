using System;
using System.Linq;
using UnityEngine;
using UnityEditor;
using UnityEditor.SceneManagement;
using TMPro;
using Ludo.Views;
using Ludo.Controllers;

public static class PolishUi
{
    static Sprite Rounded => AssetDatabase.LoadAssetAtPath<Sprite>("Assets/Art/Login/rounded.png");
    static void Set(UnityEngine.Object obj,string name,UnityEngine.Object value)
    { var s=new SerializedObject(obj); s.FindProperty(name).objectReferenceValue=value; s.ApplyModifiedPropertiesWithoutUndo(); }
    static void Array(UnityEngine.Object obj,string name,UnityEngine.Object[] values)
    { var s=new SerializedObject(obj);var a=s.FindProperty(name);a.arraySize=values.Length;for(int i=0;i<values.Length;i++)a.GetArrayElementAtIndex(i).objectReferenceValue=values[i];s.ApplyModifiedPropertiesWithoutUndo(); }
    static RectTransform Rect(string name,Transform parent,float x,float y,float w,float h)
    { var r=new GameObject(name,typeof(RectTransform)).GetComponent<RectTransform>();r.SetParent(parent,false);Place(r,x,y,w,h);return r; }
    static void Place(RectTransform r,float x,float y,float w,float h)
    {r.anchorMin=r.anchorMax=r.pivot=new Vector2(0,1);r.anchoredPosition=new Vector2(x,-y);r.sizeDelta=new Vector2(w,h);}
    static void Stretch(RectTransform r,float left=0,float right=0,float top=0,float bottom=0)
    {r.anchorMin=Vector2.zero;r.anchorMax=Vector2.one;r.pivot=new Vector2(.5f,.5f);r.offsetMin=new Vector2(left,bottom);r.offsetMax=new Vector2(-right,-top);}
    static UnityEngine.UI.Image Image(RectTransform r,Color color)
    {var im=r.GetComponent<UnityEngine.UI.Image>()??r.gameObject.AddComponent<UnityEngine.UI.Image>();im.sprite=Rounded;im.type=UnityEngine.UI.Image.Type.Sliced;im.color=color;im.raycastTarget=false;return im;}
    static void Inputs(GameObject root)
    {
        foreach(var input in root.GetComponentsInChildren<TMP_InputField>(true))
        {
            input.customCaretColor=true;input.caretColor=new Color32(45,31,82,255);input.caretWidth=3;input.caretBlinkRate=.85f;
            input.selectionColor=new Color(0.48f,.34f,.82f,.3f);
            var area=input.textViewport;
            if(area==null || area==input.transform)
            {area=Rect("TextArea",input.transform,0,0,100,40);input.textViewport=area;}
            if(area.GetComponent<UnityEngine.UI.RectMask2D>()==null)area.gameObject.AddComponent<UnityEngine.UI.RectMask2D>();
            float right=input.name=="PasswordField" && root.GetComponent<LoginController>()!=null?100:18;
            Stretch(area,18,right,7,7);
            foreach(var t in new[]{input.textComponent,input.placeholder as TMP_Text})
            {
                if(t==null)continue;t.transform.SetParent(area,false);Stretch(t.rectTransform);
                t.alignment=TextAlignmentOptions.MidlineLeft;t.margin=Vector4.zero;t.enableAutoSizing=false;
                t.textWrappingMode=TextWrappingModes.NoWrap;t.raycastTarget=false;
            }
            var fill=input.transform.Find("Fill") as RectTransform;
            if(fill!=null)Stretch(fill,2,2,2,2);
            input.textComponent.color=new Color32(43,42,72,255);
        }
    }
    static void Badges(GameObject root)
    {
        foreach(var status in root.GetComponentsInChildren<ConnectionStatusView>(true))
        {
            var parent=status.transform.parent;
            if(parent!=null && (parent.name=="ConnectionBadge"||parent.name=="SyncBadge"))
            {
                var oldBackground=parent.GetComponent<UnityEngine.UI.Image>();
                if(oldBackground!=null)UnityEngine.Object.DestroyImmediate(oldBackground);
                if(parent.Find("RetryButton")==null)Stretch((RectTransform)status.transform);
            }
        }
        foreach(var dot in root.GetComponentsInChildren<UnityEngine.UI.Image>(true).Where(i=>i.name=="ConnectionDot"))dot.gameObject.SetActive(false);
        foreach(var label in root.GetComponentsInChildren<TMP_Text>(true).Where(t=>t.name=="Connection"||t.name=="ConnectionText"))
        {
            if(label.GetComponentInParent<ConnectionStatusView>()!=null)continue;
            var r=label.rectTransform;
            var box=Rect("StatusFrame",r.parent,0,0,1,1);
            box.anchorMin=r.anchorMin;box.anchorMax=r.anchorMax;box.pivot=r.pivot;box.anchoredPosition=r.anchoredPosition;box.sizeDelta=r.sizeDelta;
            box.SetSiblingIndex(r.GetSiblingIndex());
            var bg=Image(box,new Color32(255,218,225,255));
            label.transform.SetParent(box,false);Stretch(r,10,10,0,0);label.alignment=TextAlignmentOptions.Center;label.fontSize=Mathf.Min(label.fontSize,19);
            var v=box.gameObject.AddComponent<ConnectionStatusView>();Set(v,"background",bg);Set(v,"label",label);
            foreach(var dot in box.parent.GetComponentsInChildren<UnityEngine.UI.Image>(true).Where(i=>i.name=="Dot"||i.name=="StateDot"))dot.gameObject.SetActive(false);
        }
    }
    static void Login(GameObject root)
    {
        var c=root.GetComponent<LoginController>();if(c==null)return;
        var card=root.transform.Find("Content/LoginCard");
        if(card.Find("ServerHostField")!=null)return;
        foreach(var pair in new[]{("UsernameLabel",238f),("UsernameField",276f),("PasswordLabel",363f),("PasswordField",401f),("PasswordVisibility",415f),("Feedback",602f),("LoginButton",656f),("RegisterPrompt",746f),("RegisterButton",741f)})
        {var r=(RectTransform)card.Find(pair.Item1);r.anchoredPosition=new Vector2(r.anchoredPosition.x,-pair.Item2);}
        ((RectTransform)card).sizeDelta=new Vector2(680,816);
        foreach(var item in new[]{("ServerHost",64f,350f,"IP server","127.0.0.1"),("ServerPort",434f,182f,"Port","5555")})
        {
            var source=card.Find("UsernameField");var field=UnityEngine.Object.Instantiate(source.gameObject,card).GetComponent<TMP_InputField>();field.name=item.Item1+"Field";
            Place((RectTransform)field.transform,item.Item2,534,item.Item3,60);field.text=item.Item5;field.characterLimit=item.Item1=="ServerPort"?5:253;
            field.contentType=item.Item1=="ServerPort"?TMP_InputField.ContentType.IntegerNumber:TMP_InputField.ContentType.Standard;
            (field.placeholder as TMP_Text).text=item.Item4;
            var label=UnityEngine.Object.Instantiate(card.Find("UsernameLabel").gameObject,card).GetComponent<TMP_Text>();label.name=item.Item1+"Label";label.text=item.Item4;Place(label.rectTransform,item.Item2,492,item.Item3,32);
            Set(c,item.Item1=="ServerHost"?"serverHostField":"serverPortField",field);
        }
        var retry=root.transform.Find("Content/ConnectionBadge/RetryButton");retry.GetComponentInChildren<TMP_Text>().text="Kết nối";
    }
    static void Room(GameObject root)
    {
        foreach(var slot in root.GetComponentsInChildren<RoomSlotView>(true))
        {
            if(slot.transform.Find("HostFrame")!=null)continue;
            var rect=Rect("HostFrame",slot.transform,0,0,1,1);Stretch(rect);rect.SetAsFirstSibling();
            var frame=Image(rect,new Color32(242,215,150,255));
            var inset=Rect("Inset",rect,0,0,1,1);Stretch(inset,4,4,4,4);Image(inset,new Color32(255,249,229,255));
            Set(slot,"hostFrame",frame);frame.gameObject.SetActive(false);
        }
        if(root.GetComponent<RoomController>()!=null)
        {
            var info=root.transform.Find("Content/RoomInfo");
            Place((RectTransform)info.Find("Host"),30,12,610,36);
            info.Find("Host").GetComponent<TMP_Text>().alignment=TextAlignmentOptions.MidlineLeft;
            Place((RectTransform)info.Find("CodeLabel"),690,63,130,28);
            Place((RectTransform)info.Find("Code"),830,57,660,40);
            info.Find("Code").GetComponent<TMP_Text>().alignment=TextAlignmentOptions.MidlineRight;
            Place((RectTransform)info.Find("CopyButton"),1510,54,160,46);
            Stretch((RectTransform)info.Find("CopyButton/Label"));
            Place((RectTransform)info.Find("State"),30,62,610,29);
            var b=root.transform.Find("Content/LeaveButton").GetComponent<UnityEngine.UI.Button>();
            var im=b.GetComponent<UnityEngine.UI.Image>();im.sprite=Rounded;im.color=new Color32(251,217,225,255);
            b.GetComponentInChildren<TMP_Text>().color=new Color32(154,40,65,255);
        }
    }
    static void Piece(GameObject root)
    {
        foreach(var existing in root.GetComponentsInChildren<HorseGraphic>(true))
            if(existing.GetComponent<CanvasRenderer>()==null)existing.gameObject.AddComponent<CanvasRenderer>();
        var p=root.GetComponent<PieceView>();if(p==null||root.transform.Find("Horse")!=null)return;
        root.transform.Find("Inner").gameObject.SetActive(false);root.transform.Find("Number").gameObject.SetActive(false);
        root.GetComponent<UnityEngine.UI.Image>().color=Color.clear;
        var r=Rect("Horse",root.transform,0,0,44,44);Stretch(r,-2,-2,-2,-2);var horse=r.gameObject.AddComponent<HorseGraphic>();horse.color=BoardGeometry.Tints[0];horse.raycastTarget=false;
        r.SetSiblingIndex(root.transform.Find("Effect").GetSiblingIndex());Set(p,"horse",horse);
    }
    static void Game(GameObject root)
    {
        var controller=root.GetComponent<GameController>();if(controller==null)return;
        Place((RectTransform)root.transform.Find("Content/TurnPanel/Timer"),372,46,98,54);
        var board=root.GetComponentInChildren<BoardView>();var highlights=new UnityEngine.UI.Image[4];var names=new TMP_Text[4];
        for(int i=0;i<4;i++)
        {
            var yard=(RectTransform)board.transform.Find("Yard"+i);yard.sizeDelta=new Vector2(222,222);
            var name=yard.Find("Name").GetComponent<TMP_Text>();Place(name.rectTransform,4,8,214,26);names[i]=name;
            var rect=yard.Find("OwnHighlight") as RectTransform;
            if(rect==null){rect=Rect("OwnHighlight",yard,0,0,1,1);Stretch(rect);rect.SetAsFirstSibling();var im=Image(rect,BoardGeometry.Tints[i]);var inner=Rect("Inset",rect,0,0,1,1);Stretch(inner,4,4,4,4);Image(inner,Color.Lerp(BoardGeometry.Tints[i],Color.white,.87f));}
            highlights[i]=rect.GetComponent<UnityEngine.UI.Image>();rect.gameObject.SetActive(false);
            highlights[i].color=Color.Lerp(BoardGeometry.Tints[i],Color.black,.16f);
            var inset=(RectTransform)rect.Find("Inset");Stretch(inset,8,8,8,8);
            inset.GetComponent<UnityEngine.UI.Image>().color=Color.Lerp(BoardGeometry.Tints[i],Color.white,.78f);
            board.transform.Find("Ring"+(i*12)+"/Symbol").GetComponent<TMP_Text>().text=new[]{"→","↓","←","↑"}[i];
        }
        Array(board,"ownYardHighlights",highlights);Array(board,"yardNames",names);
        var chat=root.transform.Find("Content/ChatPanel");
        if(chat.Find("ChatScroll")==null)
        {
            var log=chat.Find("ChatLog").GetComponent<TMP_Text>();
            var sr=Rect("ChatScroll",chat,24,64,448,112);var scroll=sr.gameObject.AddComponent<UnityEngine.UI.ScrollRect>();
            var viewport=Rect("Viewport",sr,0,0,448,112);Stretch(viewport);var hit=Image(viewport,Color.clear);hit.raycastTarget=true;viewport.gameObject.AddComponent<UnityEngine.UI.RectMask2D>();
            log.transform.SetParent(viewport,false);var r=log.rectTransform;r.anchorMin=new Vector2(0,1);r.anchorMax=Vector2.one;r.pivot=new Vector2(.5f,1);r.anchoredPosition=Vector2.zero;r.sizeDelta=Vector2.zero;
            log.alignment=TextAlignmentOptions.TopLeft;log.overflowMode=TextOverflowModes.Overflow;log.textWrappingMode=TextWrappingModes.Normal;log.raycastTarget=false;log.margin=new Vector4(2,2,2,2);
            var fitter=log.gameObject.GetComponent<UnityEngine.UI.ContentSizeFitter>()??log.gameObject.AddComponent<UnityEngine.UI.ContentSizeFitter>();fitter.horizontalFit=UnityEngine.UI.ContentSizeFitter.FitMode.Unconstrained;fitter.verticalFit=UnityEngine.UI.ContentSizeFitter.FitMode.PreferredSize;
            scroll.viewport=viewport;scroll.content=r;scroll.horizontal=false;scroll.vertical=true;scroll.scrollSensitivity=28;scroll.movementType=UnityEngine.UI.ScrollRect.MovementType.Clamped;Set(controller,"chatScroll",scroll);
        }
        var dice=root.GetComponentInChildren<DiceView>();var dr=(RectTransform)dice.transform;
        if(dr.pivot!=new Vector2(.5f,.5f)){var delta=new Vector2((.5f-dr.pivot.x)*dr.sizeDelta.x,(.5f-dr.pivot.y)*dr.sizeDelta.y);dr.pivot=new Vector2(.5f,.5f);dr.anchoredPosition+=delta;}
    }
    static void Apply(GameObject root)
    {
        Login(root);Inputs(root);Badges(root);Room(root);Piece(root);Game(root);
        if(root.GetComponent<RankingController>()!=null)((TMP_Text)new SerializedObject(root.GetComponent<RankingController>()).FindProperty("count").objectReferenceValue).gameObject.SetActive(false);
        if(root.GetComponent<HistoryController>()!=null)root.transform.Find("Content/HistoryCard/TimeZone").gameObject.SetActive(false);
    }
    public static string Main()
    {
        if(EditorApplication.isPlaying)throw new Exception("Exit Play Mode first.");
        var setup=EditorSceneManager.GetSceneManagerSetup();
        foreach(var s in setup) { var scene=UnityEngine.SceneManagement.SceneManager.GetSceneByPath(s.path);if(scene.isDirty)throw new Exception("Unsaved scene: "+s.path); }
        var paths=AssetDatabase.FindAssets("t:Prefab",new[]{"Assets/Prefabs"}).Select(AssetDatabase.GUIDToAssetPath).ToArray();
        foreach(var path in paths)
        {var root=PrefabUtility.LoadPrefabContents(path);try{Apply(root);PrefabUtility.SaveAsPrefabAsset(root,path);}finally{PrefabUtility.UnloadPrefabContents(root);}}
        foreach(var scene in EditorBuildSettings.scenes)
        {var s=EditorSceneManager.OpenScene(scene.path,OpenSceneMode.Single);foreach(var root in s.GetRootGameObjects())Apply(root);EditorSceneManager.SaveScene(s);}
        EditorSceneManager.RestoreSceneManagerSetup(setup);AssetDatabase.SaveAssets();
        return "Updated UI prefabs and all eight scenes.";
    }
    public static string Followup()
    {
        if(EditorApplication.isPlaying)throw new Exception("Exit Play Mode first.");
        var setup=EditorSceneManager.GetSceneManagerSetup();
        foreach(var s in setup)if(UnityEngine.SceneManagement.SceneManager.GetSceneByPath(s.path).isDirty)throw new Exception("Unsaved scene: "+s.path);
        foreach(var path in AssetDatabase.FindAssets("t:Prefab",new[]{"Assets/Prefabs"}).Select(AssetDatabase.GUIDToAssetPath).Where(p=>p.EndsWith("Screen.prefab")))
        {var root=PrefabUtility.LoadPrefabContents(path);try{Badges(root);Room(root);Game(root);PrefabUtility.SaveAsPrefabAsset(root,path);}finally{PrefabUtility.UnloadPrefabContents(root);}}
        foreach(var item in EditorBuildSettings.scenes)
        {var scene=EditorSceneManager.OpenScene(item.path,OpenSceneMode.Single);foreach(var root in scene.GetRootGameObjects()){Badges(root);Room(root);Game(root);}EditorSceneManager.SaveScene(scene);}
        EditorSceneManager.RestoreSceneManagerSetup(setup);AssetDatabase.SaveAssets();return "Removed legacy backgrounds, aligned room information right, strengthened own-yard border.";
    }
}
