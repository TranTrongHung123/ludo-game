using System;
using System.Linq;
using UnityEngine;
using UnityEditor;
using UnityEditor.SceneManagement;
using TMPro;
using Ludo.Controllers;
using Ludo.Views;

// Editor-only authoring; the saved scene and prefabs contain the complete UI.
public static class CreateResultUi
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

    public static string Main()
    {
        if(EditorApplication.isPlaying)throw new Exception("Exit Play Mode first.");
        if(AssetDatabase.LoadAssetAtPath<SceneAsset>("Assets/Scenes/ResultScene.unity")!=null)throw new Exception("ResultScene already exists.");
        var current=UnityEngine.SceneManagement.SceneManager.GetActiveScene();
        if(current.isDirty)throw new Exception("Current scene has unsaved changes.");
        var originals=current.GetRootGameObjects();var source=originals.First(o=>o.GetComponent<Canvas>()!=null);
        font=AssetDatabase.LoadAssetAtPath<TMP_FontAsset>("Assets/Fonts/Ludo Vietnamese SDF.asset");
        if(!AssetDatabase.IsValidFolder("Assets/Prefabs/Result"))AssetDatabase.CreateFolder("Assets/Prefabs","Result");
        var scene=EditorSceneManager.NewScene(NewSceneSetup.EmptyScene,NewSceneMode.Additive);UnityEngine.SceneManagement.SceneManager.SetActiveScene(scene);
        foreach(var o in originals.Where(o=>o.name=="Main Camera"||o.name=="EventSystem"||o.name=="NetworkSession"))UnityEngine.Object.Instantiate(o).name=o.name;
        var canvas=new GameObject("ResultCanvas",typeof(RectTransform),typeof(Canvas),typeof(UnityEngine.UI.CanvasScaler),typeof(UnityEngine.UI.GraphicRaycaster));
        canvas.GetComponent<Canvas>().renderMode=RenderMode.ScreenSpaceOverlay;
        var scaler=canvas.GetComponent<UnityEngine.UI.CanvasScaler>();scaler.uiScaleMode=UnityEngine.UI.CanvasScaler.ScaleMode.ScaleWithScreenSize;scaler.referenceResolution=new Vector2(1920,1080);scaler.matchWidthOrHeight=.5f;
        foreach(string name in new[]{"Background","Halo"}){var t=source.transform.Find(name);if(t!=null)UnityEngine.Object.Instantiate(t.gameObject,canvas.transform,false).name=name;}
        var content=Rect("Content",canvas.transform,0,0,1920,1080);content.anchorMin=content.anchorMax=content.pivot=new Vector2(.5f,.5f);content.anchoredPosition=Vector2.zero;
        var controller=canvas.AddComponent<ResultController>();
        Box("Logo",content,72,46,40,36,Purple,"crown");Text("Brand",content,"LUDO GAME",128,39,260,46,28,null,true);
        Text("Section",content,"KẾT QUẢ TRẬN ĐẤU",72,96,340,30,16,Muted,true);
        var badge=Box("SyncBadge",content,1410,45,438,50,new Color32(233,244,240,255));
        Set(controller,"connectionDot",Box("Dot",badge.transform,18,20,10,10,Muted,"circle"));
        Set(controller,"connection",Text("Connection",badge.transform,"Chưa kết nối Game Server",42,0,380,50,19,Muted));
        Box("CrownHalo",content,902,110,116,116,new Color32(235,224,254,255),"circle");
        Box("Crown",content,930,141,60,52,Purple,"crown");
        for(int i=0;i<18;i++)
        {
            float x=i<9?610+(i%9)*29:1074+(i%9)*29;float y=136+(i%4)*27;
            var c=Box("Confetti"+i,content,x,y,i%3==0?8:13,i%3==0?8:5,Color.Lerp(BoardGeometry.Tints[i%4],Color.white,.25f),i%3==0?"circle":"rounded");
            c.transform.localRotation=Quaternion.Euler(0,0,i*31%100-50);
        }
        Set(controller,"headline",Center("Headline",content,"Kết quả trận đấu",300,236,1320,68,52,null,true));
        Center("Subtitle",content,"Mỗi ván cờ là một niềm vui. Cảm ơn bạn đã cùng chơi!",360,311,1200,38,23,Muted);
        Set(controller,"matchId",Center("MatchId",content,"Đang chờ mã trận từ Server",360,355,1200,32,18,Muted));
        var card=Box("ResultsCard",content,240,416,1440,476,Color.white);
        var shadow=card.gameObject.AddComponent<UnityEngine.UI.Shadow>();shadow.effectColor=new Color(.25f,.17f,.45f,.08f);shadow.effectDistance=new Vector2(0,-8);
        Text("Title",card.transform,"Kết quả chính thức",32,20,700,48,28,null,true);
        Set(controller,"count",Text("Count",card.transform,"Chờ kết quả",1170,26,236,36,19,Muted));
        var header=Box("TableHeader",card.transform,24,84,1392,44,new Color32(245,242,251,255));
        string[] headings={"HẠNG","NGƯỜI CHƠI","TRẠNG THÁI","ĐIỂM NHẬN","TỔNG ĐIỂM"};
        float[] xs={20,126,672,986,1190};float[] widths={90,510,286,180,178};
        for(int i=0;i<headings.Length;i++)Text("Column"+i,header.transform,headings[i],xs[i],0,widths[i],44,16,Muted,true);
        var row=Box("ResultRow",card.transform,24,140,1392,72,new Color32(249,248,253,255));var view=row.gameObject.AddComponent<ResultRowView>();Set(view,"background",row);
        Set(view,"accent",Box("Accent",row.transform,0,15,4,42,Purple));
        var medal=Box("Medal",row.transform,20,12,52,48,new Color32(235,229,245,255));Set(view,"medal",medal);
        Set(view,"rank",Center("Rank",medal.transform,"—",0,0,52,48,24,null,true));
        Set(view,"playerName",Text("PlayerName",row.transform,"",126,6,436,34,23,null,true));
        Set(view,"colorName",Text("Color",row.transform,"",126,42,440,24,16,Muted));
        var self=Box("SelfBadge",row.transform,575,22,62,28,new Color32(227,214,252,255));Center("Label",self.transform,"Bạn",0,0,62,28,16,Purple,true);Set(view,"selfBadge",self.gameObject);self.gameObject.SetActive(false);
        Set(view,"status",Text("Status",row.transform,"",672,0,286,72,21));
        Set(view,"earned",Text("Earned",row.transform,"",986,0,180,72,26,Purple,true));
        Set(view,"total",Text("Total",row.transform,"",1190,0,178,72,26,null,true));
        var rowAsset=PrefabUtility.SaveAsPrefabAsset(row.gameObject,"Assets/Prefabs/Result/ResultRow.prefab");UnityEngine.Object.DestroyImmediate(row.gameObject);
        var serialized=new SerializedObject(controller);var rows=serialized.FindProperty("rows");rows.arraySize=4;
        for(int i=0;i<4;i++){var go=(GameObject)PrefabUtility.InstantiatePrefab(rowAsset,card.transform);go.name="ResultRow"+i;((RectTransform)go.transform).anchoredPosition=new Vector2(24,-140-i*80);rows.GetArrayElementAtIndex(i).objectReferenceValue=go.GetComponent<ResultRowView>();go.SetActive(false);}serialized.ApplyModifiedPropertiesWithoutUndo();
        var empty=Center("EmptyState",card.transform,"Kết quả sẽ xuất hiện khi trận đấu kết thúc.",80,215,1280,90,25,Muted);Set(controller,"emptyState",empty.gameObject);
        var button=Box("LobbyButton",content,780,924,360,66,Color.white,"primary-gradient");button.raycastTarget=true;
        var action=button.gameObject.AddComponent<UnityEngine.UI.Button>();action.targetGraphic=button;Set(controller,"lobbyButton",action);
        var colors=action.colors;colors.highlightedColor=new Color(.94f,.89f,1);colors.pressedColor=new Color(.81f,.72f,.95f);action.colors=colors;
        Set(controller,"lobbyLabel",Center("Label",button.transform,"Về sảnh",0,0,360,66,25,Color.white,true));
        Set(controller,"feedback",Center("Feedback",content,"",280,1002,1360,40,19,Muted));
        var prefab=PrefabUtility.SaveAsPrefabAsset(canvas,"Assets/Prefabs/Result/ResultScreen.prefab");
        UnityEngine.Object.DestroyImmediate(canvas);PrefabUtility.InstantiatePrefab(prefab,scene);
        EditorSceneManager.SaveScene(scene,"Assets/Scenes/ResultScene.unity");
        EditorBuildSettings.scenes=EditorBuildSettings.scenes.Concat(new[]{new EditorBuildSettingsScene("Assets/Scenes/ResultScene.unity",true)}).ToArray();
        AssetDatabase.SaveAssets();EditorSceneManager.OpenScene("Assets/Scenes/ResultScene.unity",OpenSceneMode.Single);
        return "ResultScene, ResultScreen and ResultRow saved; added to Build Settings.";
    }
}
