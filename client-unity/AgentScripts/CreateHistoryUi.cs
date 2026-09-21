using System;
using System.Linq;
using UnityEngine;
using UnityEditor;
using UnityEditor.SceneManagement;
using TMPro;
using Ludo.Controllers;
using Ludo.Views;

// Editor-only authoring; the saved scene and prefabs contain the complete UI.
public static class CreateHistoryUi
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
        if(AssetDatabase.LoadAssetAtPath<SceneAsset>("Assets/Scenes/HistoryScene.unity")!=null)throw new Exception("HistoryScene already exists.");
        var current=UnityEngine.SceneManagement.SceneManager.GetActiveScene();
        if(current.isDirty)throw new Exception("Current scene has unsaved changes.");
        var originals=current.GetRootGameObjects();var source=originals.First(o=>o.GetComponent<Canvas>()!=null);
        font=AssetDatabase.LoadAssetAtPath<TMP_FontAsset>("Assets/Fonts/Ludo Vietnamese SDF.asset");
        if(!AssetDatabase.IsValidFolder("Assets/Prefabs/History"))AssetDatabase.CreateFolder("Assets/Prefabs","History");
        var scene=EditorSceneManager.NewScene(NewSceneSetup.EmptyScene,NewSceneMode.Additive);UnityEngine.SceneManagement.SceneManager.SetActiveScene(scene);
        foreach(var o in originals.Where(o=>o.name=="Main Camera"||o.name=="EventSystem"||o.name=="NetworkSession"))UnityEngine.Object.Instantiate(o).name=o.name;
        var canvas=new GameObject("HistoryCanvas",typeof(RectTransform),typeof(Canvas),typeof(UnityEngine.UI.CanvasScaler),typeof(UnityEngine.UI.GraphicRaycaster));
        canvas.GetComponent<Canvas>().renderMode=RenderMode.ScreenSpaceOverlay;
        var scaler=canvas.GetComponent<UnityEngine.UI.CanvasScaler>();scaler.uiScaleMode=UnityEngine.UI.CanvasScaler.ScaleMode.ScaleWithScreenSize;scaler.referenceResolution=new Vector2(1920,1080);scaler.matchWidthOrHeight=.5f;
        foreach(string name in new[]{"Background","Halo"}){var t=source.transform.Find(name);if(t!=null)UnityEngine.Object.Instantiate(t.gameObject,canvas.transform,false).name=name;}
        var content=Rect("Content",canvas.transform,0,0,1920,1080);content.anchorMin=content.anchorMax=content.pivot=new Vector2(.5f,.5f);content.anchoredPosition=Vector2.zero;
        var controller=canvas.AddComponent<HistoryController>();
        Set(controller,"backButton",Button("BackButton",content,"Về sảnh",72,46,184,false));
        Box("Logo",content,296,57,38,32,Purple,"crown");Text("Brand",content,"LUDO GAME",350,46,270,54,27,null,true);
        var badge=Box("ConnectionBadge",content,1434,48,414,52,new Color32(233,244,240,255));
        Set(controller,"connectionDot",Box("Dot",badge.transform,20,21,10,10,Muted,"circle"));
        Set(controller,"connection",Text("Connection",badge.transform,"Chưa kết nối Game Server",46,0,350,52,19,Muted));
        var clock=Box("ClockHalo",content,72,153,116,116,new Color32(235,224,253,255),"circle");
        Box("ClockFace",clock.transform,25,25,66,66,Purple,"circle");Box("ClockInner",clock.transform,30,30,56,56,new Color32(245,239,255,255),"circle");
        Box("HourHand",clock.transform,55,39,5,22,Purple);Box("MinuteHand",clock.transform,55,56,23,5,Purple);
        Text("Eyebrow",content,"HÀNH TRÌNH CỦA BẠN",220,148,960,30,17,Purple,true);
        Text("Title",content,"Lịch sử trận đấu",220,181,1020,65,48,null,true);
        Set(controller,"count",Text("Count",content,"Đang chờ lịch sử từ Server",222,255,1270,36,23,Muted));
        var refresh=Button("RefreshButton",content,"Làm mới",1632,202,216,true);Set(controller,"refreshButton",refresh);Set(controller,"refreshLabel",refresh.GetComponentInChildren<TMP_Text>());
        var card=Box("HistoryCard",content,72,330,1776,648,Color.white);
        var shadow=card.gameObject.AddComponent<UnityEngine.UI.Shadow>();shadow.effectColor=new Color(.25f,.17f,.45f,.08f);shadow.effectDistance=new Vector2(0,-8);
        Text("Title",card.transform,"Những ván cờ đã chơi",32,20,1100,46,27,null,true);
        Text("TimeZone",card.transform,"Giờ địa phương",1430,23,310,40,19,Muted);
        var header=Box("TableHeader",card.transform,24,84,1728,46,new Color32(245,242,251,255));
        string[] names={"KẾT QUẢ","THỜI GIAN VÀ TRẬN ĐẤU","ĐIỂM NHẬN","TRẠNG THÁI"};float[] xs={24,258,1140,1400};float[] ws={204,850,228,300};
        for(int i=0;i<4;i++)Text("Column"+i,header.transform,names[i],xs[i],0,ws[i],46,17,Muted,true);
        var scrollRoot=Rect("ScrollView",card.transform,24,144,1728,476);var scroll=scrollRoot.gameObject.AddComponent<UnityEngine.UI.ScrollRect>();
        var viewport=Box("Viewport",scrollRoot,0,0,1728,476,Color.white);viewport.raycastTarget=true;viewport.gameObject.AddComponent<UnityEngine.UI.Mask>().showMaskGraphic=false;
        var rows=Rect("Rows",viewport.transform,0,0,1728,0);rows.anchorMin=new Vector2(0,1);rows.anchorMax=new Vector2(1,1);rows.sizeDelta=Vector2.zero;
        var layout=rows.gameObject.AddComponent<UnityEngine.UI.VerticalLayoutGroup>();layout.spacing=8;layout.childControlWidth=true;layout.childControlHeight=true;layout.childForceExpandWidth=true;layout.childForceExpandHeight=false;
        var fitter=rows.gameObject.AddComponent<UnityEngine.UI.ContentSizeFitter>();fitter.verticalFit=UnityEngine.UI.ContentSizeFitter.FitMode.PreferredSize;
        scroll.viewport=(RectTransform)viewport.transform;scroll.content=rows;scroll.horizontal=false;scroll.vertical=true;scroll.movementType=UnityEngine.UI.ScrollRect.MovementType.Clamped;scroll.scrollSensitivity=38;
        Set(controller,"scroll",scroll);Set(controller,"rows",rows);
        var row=Box("HistoryRow",rows,0,0,1728,88,new Color32(249,248,253,255));var view=row.gameObject.AddComponent<HistoryRowView>();Set(view,"background",row);
        var sizing=row.gameObject.AddComponent<UnityEngine.UI.LayoutElement>();sizing.minHeight=88;sizing.preferredHeight=88;
        Set(view,"accent",Box("Accent",row.transform,0,18,4,52,Purple));
        var medal=Box("Medal",row.transform,24,20,148,48,new Color32(235,229,245,255));Set(view,"medal",medal);
        Set(view,"rank",Center("Rank",medal.transform,"—",0,0,148,48,22,null,true));
        Set(view,"crown",Box("Crown",row.transform,194,33,27,23,Purple,"crown").gameObject);
        Set(view,"time",Text("Time",row.transform,"",258,9,848,34,24,null,true));
        Set(view,"metadata",Text("Metadata",row.transform,"",258,48,848,30,18,Muted));
        Set(view,"score",Text("Score",row.transform,"",1140,0,228,88,27,Purple,true));
        Set(view,"status",Text("Status",row.transform,"",1400,0,300,88,22));
        var rowAsset=PrefabUtility.SaveAsPrefabAsset(row.gameObject,"Assets/Prefabs/History/HistoryRow.prefab");UnityEngine.Object.DestroyImmediate(row.gameObject);Set(controller,"rowPrefab",rowAsset.GetComponent<HistoryRowView>());
        Set(controller,"empty",Center("Empty",card.transform,"Lịch sử sẽ xuất hiện khi kết nối với Server.",100,300,1576,100,25,Muted));
        Set(controller,"feedback",Text("Feedback",content,"",80,999,1750,38,19,Muted));
        var prefab=PrefabUtility.SaveAsPrefabAsset(canvas,"Assets/Prefabs/History/HistoryScreen.prefab");UnityEngine.Object.DestroyImmediate(canvas);PrefabUtility.InstantiatePrefab(prefab,scene);
        EditorSceneManager.SaveScene(scene,"Assets/Scenes/HistoryScene.unity");
        EditorBuildSettings.scenes=EditorBuildSettings.scenes.Concat(new[]{new EditorBuildSettingsScene("Assets/Scenes/HistoryScene.unity",true)}).ToArray();
        AssetDatabase.SaveAssets();EditorSceneManager.OpenScene("Assets/Scenes/HistoryScene.unity",OpenSceneMode.Single);
        return "HistoryScene and prefabs saved; Build Settings updated.";
    }
}

