using System;
using System.Linq;
using UnityEngine;
using UnityEditor;
using UnityEditor.SceneManagement;
using TMPro;
using Ludo.Controllers;
using Ludo.Views;

// Editor-only authoring; the saved scene and prefabs contain the complete UI.
public static class CreateRankingUi
{
    static TMP_FontAsset font;
    static Color Navy = new Color32(43,42,72,255), Muted = new Color32(115,110,141,255), Purple = new Color32(124,88,210,255);
    static RectTransform Rect(string name, Transform parent, float x, float y, float w, float h)
    {
        var r = new GameObject(name, typeof(RectTransform)).GetComponent<RectTransform>(); r.SetParent(parent,false);
        r.anchorMin = r.anchorMax = r.pivot = new Vector2(0,1); r.anchoredPosition = new Vector2(x,-y); r.sizeDelta = new Vector2(w,h); return r;
    }
    static UnityEngine.UI.Image Box(string name, Transform p, float x,float y,float w,float h,Color color,string sprite="rounded")
    {
        var im=Rect(name,p,x,y,w,h).gameObject.AddComponent<UnityEngine.UI.Image>();
        im.sprite=AssetDatabase.LoadAssetAtPath<Sprite>("Assets/Art/Login/"+sprite+".png");
        im.type=UnityEngine.UI.Image.Type.Sliced;im.color=color;im.raycastTarget=false;return im;
    }
    static TMP_Text Text(string name,Transform p,string value,float x,float y,float w,float h,float size,Color? color=null,bool bold=false)
    {
        var t=Rect(name,p,x,y,w,h).gameObject.AddComponent<TextMeshProUGUI>();t.font=font;t.text=value;t.fontSize=size;t.color=color??Navy;
        t.fontStyle=bold?FontStyles.Bold:FontStyles.Normal;t.raycastTarget=false;t.richText=false;t.overflowMode=TextOverflowModes.Ellipsis;
        t.textWrappingMode=TextWrappingModes.NoWrap;t.verticalAlignment=VerticalAlignmentOptions.Middle;return t;
    }
    static void Set(UnityEngine.Object obj,string name,UnityEngine.Object value)
    {var s=new SerializedObject(obj);s.FindProperty(name).objectReferenceValue=value;s.ApplyModifiedPropertiesWithoutUndo();}
    static TMP_Text Center(string name,Transform p,string value,float x,float y,float w,float h,float size,Color? color=null,bool bold=false)
    {var t=Text(name,p,value,x,y,w,h,size,color,bold);t.alignment=TextAlignmentOptions.Center;return t;}

    static UnityEngine.UI.Button Button(string name,Transform p,string label,float x,float y,float w,bool primary)
    {
        var im=Box(name,p,x,y,w,58,primary?Color.white:new Color32(237,229,252,255),primary?"primary-gradient":"rounded");im.raycastTarget=true;
        var b=im.gameObject.AddComponent<UnityEngine.UI.Button>();b.targetGraphic=im;
        Center("Label",im.transform,label,0,0,w,58,22,primary?Color.white:Purple,true);return b;
    }
    public static string Main()
    {
        if(EditorApplication.isPlaying)throw new Exception("Exit Play Mode first.");
        if(AssetDatabase.LoadAssetAtPath<SceneAsset>("Assets/Scenes/RankingScene.unity")!=null)throw new Exception("RankingScene already exists.");
        var current=UnityEngine.SceneManagement.SceneManager.GetActiveScene();
        if(current.isDirty)throw new Exception("Current scene has unsaved changes.");
        var originals=current.GetRootGameObjects();var source=originals.First(o=>o.GetComponent<Canvas>()!=null);
        font=AssetDatabase.LoadAssetAtPath<TMP_FontAsset>("Assets/Fonts/Ludo Vietnamese SDF.asset");
        if(!AssetDatabase.IsValidFolder("Assets/Prefabs/Ranking"))AssetDatabase.CreateFolder("Assets/Prefabs","Ranking");
        var scene=EditorSceneManager.NewScene(NewSceneSetup.EmptyScene,NewSceneMode.Additive);UnityEngine.SceneManagement.SceneManager.SetActiveScene(scene);
        foreach(var o in originals.Where(o=>o.name=="Main Camera"||o.name=="EventSystem"||o.name=="NetworkSession"))UnityEngine.Object.Instantiate(o).name=o.name;
        var canvas=new GameObject("RankingCanvas",typeof(RectTransform),typeof(Canvas),typeof(UnityEngine.UI.CanvasScaler),typeof(UnityEngine.UI.GraphicRaycaster));
        canvas.GetComponent<Canvas>().renderMode=RenderMode.ScreenSpaceOverlay;
        var scaler=canvas.GetComponent<UnityEngine.UI.CanvasScaler>();scaler.uiScaleMode=UnityEngine.UI.CanvasScaler.ScaleMode.ScaleWithScreenSize;scaler.referenceResolution=new Vector2(1920,1080);scaler.matchWidthOrHeight=.5f;
        foreach(string name in new[]{"Background","Halo"}){var t=source.transform.Find(name);if(t!=null)UnityEngine.Object.Instantiate(t.gameObject,canvas.transform,false).name=name;}
        var content=Rect("Content",canvas.transform,0,0,1920,1080);content.anchorMin=content.anchorMax=content.pivot=new Vector2(.5f,.5f);content.anchoredPosition=Vector2.zero;
        var controller=canvas.AddComponent<RankingController>();
        Set(controller,"backButton",Button("BackButton",content,"Về sảnh",72,46,184,false));
        Box("Logo",content,296,57,38,32,Purple,"crown");Text("Brand",content,"LUDO GAME",350,46,270,54,27,null,true);
        var badge=Box("ConnectionBadge",content,1434,48,414,52,new Color32(233,244,240,255));
        Set(controller,"connectionDot",Box("Dot",badge.transform,20,21,10,10,Muted,"circle"));
        Set(controller,"connection",Text("Connection",badge.transform,"Chưa kết nối Game Server",46,0,350,52,19,Muted));
        Box("CrownHalo",content,72,153,116,116,new Color32(235,224,253,255),"circle");Box("Crown",content,101,186,58,49,Purple,"crown");
        Text("Eyebrow",content,"NHỮNG NGƯỜI CHƠI XUẤT SẮC",220,148,960,30,17,Purple,true);
        Text("Title",content,"Bảng xếp hạng",220,181,1020,65,48,null,true);
        Text("Subtitle",content,"Cùng chinh phục những vị trí dẫn đầu!",222,255,1050,36,23,Muted);
        var refresh=Button("RefreshButton",content,"Làm mới",1632,202,216,true);Set(controller,"refreshButton",refresh);Set(controller,"refreshLabel",refresh.GetComponentInChildren<TMP_Text>());
        var card=Box("RankingCard",content,72,330,1776,648,Color.white);
        var shadow=card.gameObject.AddComponent<UnityEngine.UI.Shadow>();shadow.effectColor=new Color(.25f,.17f,.45f,.08f);shadow.effectDistance=new Vector2(0,-8);
        Text("Title",card.transform,"Đường đua vinh quang",32,20,1100,46,27,null,true);
        Set(controller,"count",Text("Count",card.transform,"Chờ dữ liệu",1454,23,280,40,20,Muted));
        var header=Box("TableHeader",card.transform,24,84,1728,46,new Color32(245,242,251,255));
        string[] names={"HẠNG","NGƯỜI CHƠI","TỔNG ĐIỂM","SỐ LẦN HẠNG NHẤT"};float[] xs={24,148,1072,1380};float[] ws={100,872,260,320};
        for(int i=0;i<4;i++)Text("Column"+i,header.transform,names[i],xs[i],0,ws[i],46,17,Muted,true);
        var scrollRoot=Rect("ScrollView",card.transform,24,144,1728,476);var scroll=scrollRoot.gameObject.AddComponent<UnityEngine.UI.ScrollRect>();
        var viewport=Box("Viewport",scrollRoot,0,0,1728,476,Color.white);viewport.raycastTarget=true;viewport.gameObject.AddComponent<UnityEngine.UI.Mask>().showMaskGraphic=false;
        var rows=Rect("Rows",viewport.transform,0,0,1728,0);rows.anchorMin=new Vector2(0,1);rows.anchorMax=new Vector2(1,1);rows.sizeDelta=Vector2.zero;
        var layout=rows.gameObject.AddComponent<UnityEngine.UI.VerticalLayoutGroup>();layout.spacing=8;layout.childControlWidth=true;layout.childControlHeight=true;layout.childForceExpandWidth=true;layout.childForceExpandHeight=false;
        var fitter=rows.gameObject.AddComponent<UnityEngine.UI.ContentSizeFitter>();fitter.verticalFit=UnityEngine.UI.ContentSizeFitter.FitMode.PreferredSize;
        scroll.viewport=(RectTransform)viewport.transform;scroll.content=rows;scroll.horizontal=false;scroll.vertical=true;scroll.movementType=UnityEngine.UI.ScrollRect.MovementType.Clamped;scroll.scrollSensitivity=38;
        Set(controller,"scroll",scroll);Set(controller,"rows",rows);
        var row=Box("RankingRow",rows,0,0,1728,72,new Color32(249,248,253,255));var view=row.gameObject.AddComponent<RankingRowView>();Set(view,"background",row);
        var sizing=row.gameObject.AddComponent<UnityEngine.UI.LayoutElement>();sizing.minHeight=72;sizing.preferredHeight=72;
        Set(view,"accent",Box("Accent",row.transform,0,16,4,40,Purple));
        var medal=Box("Medal",row.transform,24,12,78,48,new Color32(235,229,245,255));Set(view,"medal",medal);
        Set(view,"rank",Center("Rank",medal.transform,"—",0,0,78,48,23,null,true));
        var avatar=Box("Avatar",row.transform,148,12,48,48,new Color32(236,228,250,255),"circle");Set(view,"initial",Center("Initial",avatar.transform,"",0,0,48,48,21,Purple,true));
        Set(view,"playerName",Text("PlayerName",row.transform,"",214,0,676,72,23,null,true));
        var self=Box("SelfBadge",row.transform,916,23,62,26,new Color32(228,215,250,255));Center("Label",self.transform,"Bạn",0,0,62,26,16,Purple,true);Set(view,"selfBadge",self.gameObject);self.gameObject.SetActive(false);
        Set(view,"crown",Box("Crown",row.transform,998,25,26,23,Purple,"crown").gameObject);
        Set(view,"score",Text("Score",row.transform,"",1072,0,260,72,26,Purple,true));
        Set(view,"wins",Text("Wins",row.transform,"",1380,0,320,72,24,null,true));
        var rowAsset=PrefabUtility.SaveAsPrefabAsset(row.gameObject,"Assets/Prefabs/Ranking/RankingRow.prefab");UnityEngine.Object.DestroyImmediate(row.gameObject);Set(controller,"rowPrefab",rowAsset.GetComponent<RankingRowView>());
        Set(controller,"empty",Center("Empty",card.transform,"Bảng xếp hạng sẽ xuất hiện khi kết nối với Server.",100,300,1576,100,25,Muted));
        Set(controller,"feedback",Text("Feedback",content,"",80,999,1750,38,19,Muted));
        var prefab=PrefabUtility.SaveAsPrefabAsset(canvas,"Assets/Prefabs/Ranking/RankingScreen.prefab");UnityEngine.Object.DestroyImmediate(canvas);PrefabUtility.InstantiatePrefab(prefab,scene);
        EditorSceneManager.SaveScene(scene,"Assets/Scenes/RankingScene.unity");
        EditorBuildSettings.scenes=EditorBuildSettings.scenes.Concat(new[]{new EditorBuildSettingsScene("Assets/Scenes/RankingScene.unity",true)}).ToArray();
        AssetDatabase.SaveAssets();EditorSceneManager.OpenScene("Assets/Scenes/RankingScene.unity",OpenSceneMode.Single);
        return "RankingScene and prefabs saved; Build Settings updated.";
    }
}

