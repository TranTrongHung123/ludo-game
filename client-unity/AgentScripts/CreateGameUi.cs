using System;
using System.Linq;
using UnityEngine;
using UnityEditor;
using UnityEditor.SceneManagement;
using TMPro;
using Ludo.Views;
using Ludo.Controllers;

// One-time Editor authoring command. All visuals and references are saved in prefabs,
// not constructed by runtime code. Re-running refuses to overwrite authored assets.
public static class CreateGameUi
{
    static TMP_FontAsset font;
    static Color Navy = new Color32(43,42,72,255), Muted = new Color32(125,121,151,255), Purple = new Color32(124,88,210,255), Pale = new Color32(244,241,252,255);
    static Sprite Sprite(string name) => AssetDatabase.LoadAssetAtPath<Sprite>("Assets/Art/Login/"+name+".png");
    static RectTransform Rect(string name, Transform parent, float x, float y, float w, float h)
    {
        var r = new GameObject(name, typeof(RectTransform)).GetComponent<RectTransform>(); r.SetParent(parent,false);
        r.anchorMin = r.anchorMax = new Vector2(0,1); r.pivot = new Vector2(0,1); r.anchoredPosition = new Vector2(x,-y); r.sizeDelta = new Vector2(w,h); return r;
    }
    static UnityEngine.UI.Image Box(string name, Transform p, float x,float y,float w,float h,Color color,string sprite="rounded")
    { var r=Rect(name,p,x,y,w,h);var im=r.gameObject.AddComponent<UnityEngine.UI.Image>();im.sprite=Sprite(sprite);im.type=UnityEngine.UI.Image.Type.Sliced;im.color=color;im.raycastTarget=false;if(name.StartsWith("Ring")||name.StartsWith("Finish"))im.pixelsPerUnitMultiplier=3;return im; }
    static TMP_Text Text(string name,Transform p,string value,float x,float y,float w,float h,float size,Color? color=null,bool bold=false)
    {var t=Rect(name,p,x,y,w,h).gameObject.AddComponent<TextMeshProUGUI>();t.font=font;t.text=value;t.fontSize=size;t.color=color??Navy;t.fontStyle=bold?FontStyles.Bold:FontStyles.Normal;t.raycastTarget=false;t.richText=false;t.overflowMode=TextOverflowModes.Truncate;t.textWrappingMode=TextWrappingModes.NoWrap;t.verticalAlignment=VerticalAlignmentOptions.Middle;return t;}
    static UnityEngine.UI.Button Button(string name,Transform p,string value,float x,float y,float w,float h,bool primary=true)
    {var im=Box(name,p,x,y,w,h,primary?Color.white:new Color32(238,231,250,255),primary?"primary-gradient":"rounded");im.raycastTarget=true;var b=im.gameObject.AddComponent<UnityEngine.UI.Button>();b.targetGraphic=im;var t=Text("Label",im.transform,value,8,0,w-16,h,22,primary?Color.white:Purple,true);t.alignment=TextAlignmentOptions.Center;return b;}
    static void Set(UnityEngine.Object obj,string name,UnityEngine.Object value)
    {var s=new SerializedObject(obj);s.FindProperty(name).objectReferenceValue=value;s.ApplyModifiedPropertiesWithoutUndo();}
    static void Array(UnityEngine.Object obj,string name,UnityEngine.Object[] values)
    {var s=new SerializedObject(obj);var a=s.FindProperty(name);a.arraySize=values.Length;for(int i=0;i<values.Length;i++)a.GetArrayElementAtIndex(i).objectReferenceValue=values[i];s.ApplyModifiedPropertiesWithoutUndo();}
    static void Center(RectTransform r,Vector2 pos)
    {r.anchorMin=r.anchorMax=r.pivot=new Vector2(.5f,.5f);r.anchoredPosition=pos;}
    static GameObject Save(GameObject root,string name)
    {return PrefabUtility.SaveAsPrefabAsset(root,"Assets/Prefabs/Game/"+name+".prefab");}

    public static string Main()
    {
        if(EditorApplication.isPlaying)throw new Exception("Exit Play Mode first.");
        if(AssetDatabase.LoadAssetAtPath<SceneAsset>("Assets/Scenes/GameScene.unity")!=null)throw new Exception("GameScene already exists.");
        if(UnityEngine.SceneManagement.SceneManager.GetActiveScene().isDirty)throw new Exception("Current scene has unsaved changes.");
        font=AssetDatabase.LoadAssetAtPath<TMP_FontAsset>("Assets/Fonts/Ludo Vietnamese SDF.asset");
        if(!AssetDatabase.IsValidFolder("Assets/Prefabs/Game"))AssetDatabase.CreateFolder("Assets/Prefabs","Game");
        var originals=UnityEngine.SceneManagement.SceneManager.GetActiveScene().GetRootGameObjects();
        var sourceCanvas=originals.First(o=>o.GetComponent<Canvas>()!=null);
        var scene=EditorSceneManager.NewScene(NewSceneSetup.EmptyScene,NewSceneMode.Additive);
        UnityEngine.SceneManagement.SceneManager.SetActiveScene(scene);
        foreach(var o in originals.Where(o=>o.name=="Main Camera"||o.name=="EventSystem"||o.name=="NetworkSession"))UnityEngine.Object.Instantiate(o).name=o.name;
        var canvas=new GameObject("GameCanvas",typeof(RectTransform),typeof(Canvas),typeof(UnityEngine.UI.CanvasScaler),typeof(UnityEngine.UI.GraphicRaycaster));
        canvas.GetComponent<Canvas>().renderMode=RenderMode.ScreenSpaceOverlay;
        var scaler=canvas.GetComponent<UnityEngine.UI.CanvasScaler>();scaler.uiScaleMode=UnityEngine.UI.CanvasScaler.ScaleMode.ScaleWithScreenSize;scaler.referenceResolution=new Vector2(1920,1080);scaler.matchWidthOrHeight=.5f;
        foreach(string n in new[]{"Background","Halo"}) {var t=sourceCanvas.transform.Find(n);if(t!=null)UnityEngine.Object.Instantiate(t.gameObject,canvas.transform,false).name=n;}
        var content=Rect("Content",canvas.transform,0,0,1920,1080);content.anchorMin=content.anchorMax=content.pivot=new Vector2(.5f,.5f);content.anchoredPosition=Vector2.zero;
        var controller=canvas.AddComponent<GameController>();
        Box("Logo",content,48,42,46,46,Purple,"crown");Text("Brand",content,"LUDO GAME",108,36,245,38,30,null,true);Text("Subtitle",content,"Cùng nhau về đích",109,78,260,26,18,Muted);
        Set(controller,"roomLabel",Text("Room",content,"PHÒNG  —",456,38,840,34,24,null,true));
        Set(controller,"matchLabel",Text("Match",content,"Đang chờ dữ liệu trận đấu",456,78,840,24,17,Muted));
        Set(controller,"connection",Text("Connection",content,"Chưa kết nối Game Server",1376,44,320,45,19,Muted));
        var leave=Button("LeaveButton",content,"Bỏ cuộc",1710,44,162,54,false);leave.GetComponent<UnityEngine.UI.Image>().color=new Color32(253,234,240,255);leave.GetComponentInChildren<TMP_Text>().color=new Color32(190,62,87,255);Set(controller,"leaveButton",leave);Set(controller,"leaveLabel",leave.GetComponentInChildren<TMP_Text>());
        Text("PlayersTitle",content,"NGƯỜI CHƠI",48,141,368,30,18,Muted,true);

        var card=Box("PlayerCard",content,48,192,368,168,Color.white);var pv=card.gameObject.AddComponent<PlayerCardView>();
        var highlight=Box("TurnHighlight",card.transform,0,0,368,168,new Color32(228,216,253,255));highlight.transform.SetAsFirstSibling();Set(pv,"highlight",highlight);
        Set(pv,"accent",Box("Accent",card.transform,0,22,5,124,Purple));
        Set(pv,"colorName",Text("Color",card.transform,"ĐỎ",24,16,310,26,16,Purple,true));
        Set(pv,"playerName",Text("PlayerName",card.transform,"Không có người chơi",24,48,318,36,25,null,true));
        Set(pv,"status",Text("Status",card.transform,"Vị trí trống",24,92,320,26,19,Muted));
        Set(pv,"progress",Text("Progress",card.transform,"—",24,130,320,24,17,Muted));
        var cardAsset=Save(card.gameObject,"PlayerCard");UnityEngine.Object.DestroyImmediate(card.gameObject);
        var cards=new PlayerCardView[4];for(int i=0;i<4;i++){var go=(GameObject)PrefabUtility.InstantiatePrefab(cardAsset,content);go.name="Player"+i;var r=(RectTransform)go.transform;r.anchoredPosition=new Vector2(48,-192-i*184);cards[i]=go.GetComponent<PlayerCardView>();cards[i].Bind(null,i,null,null);}Array(controller,"players",cards);
        var tip=Box("Tip",content,48,948,368,84,new Color32(235,228,249,255));Text("TipTitle",tip.transform,"MẸO NHỎ",20,9,330,25,16,Purple,true);Text("TipText",tip.transform,"Đổ được 6 để đưa quân ra sân.",20,38,330,28,18,Muted);

        var boardCard=Box("BoardCard",content,456,144,888,888,Color.white);
        var shadow=boardCard.gameObject.AddComponent<UnityEngine.UI.Shadow>();shadow.effectColor=new Color(0.25f,.17f,.45f,.09f);shadow.effectDistance=new Vector2(0,-7);
        Text("BoardTitle",boardCard.transform,"BÀN CỜ",28,17,180,30,17,Muted,true);
        Text("BoardNote",boardCard.transform,"4 màu • Một đường đua",568,17,290,30,18,Muted).alignment=TextAlignmentOptions.Right;
        var board=Rect("Board",boardCard.transform,44,52,800,800);var view=board.gameObject.AddComponent<BoardView>();Set(controller,"board",view);
        for(int slot=0;slot<4;slot++)
        {
            var center=BoardGeometry.Rotate(new Vector2(-3.55f,3.55f),slot)*BoardGeometry.Cell;
            var yard=Box("Yard"+slot,board,0,0,260,260,Color.Lerp(BoardGeometry.Tints[slot],Color.white,.87f));Center((RectTransform)yard.transform,center);
            Text("Name",yard.transform,BoardGeometry.Names[slot].ToUpperInvariant(),18,12,224,26,17,BoardGeometry.Tints[slot],true).alignment=TextAlignmentOptions.Center;
            for(int n=0;n<4;n++){var socket=Box("Socket"+n,board,0,0,64,64,Color.white,"circle");Center((RectTransform)socket.transform,BoardGeometry.Yard(slot,n));}
            for(int n=1;n<=6;n++)
            {var pos=BoardGeometry.Rotate(new Vector2(n-6,0),slot)*BoardGeometry.Cell;var cell=Box("Finish"+slot+"_"+n,board,0,0,52,52,Color.Lerp(BoardGeometry.Tints[slot],Color.white,.6f));Center((RectTransform)cell.transform,pos);Text("Slot",cell.transform,n.ToString(),0,0,52,52,18,BoardGeometry.Tints[slot],true).alignment=TextAlignmentOptions.Center;}
        }
        var labels=new TMP_Text[48];for(int i=0;i<48;i++)
        {var cell=Box("Ring"+i,board,0,0,52,52,i%12==0?Color.Lerp(BoardGeometry.Tints[i/12],Color.white,.48f):new Color32(239,236,247,255));Center((RectTransform)cell.transform,BoardGeometry.Ring(i));labels[i]=Text("Symbol",cell.transform,i%12==0?"→":"",0,0,52,52,23,Purple,true);labels[i].alignment=TextAlignmentOptions.Center;}
        Array(view,"cells",labels);
        var home=Box("Home",board,0,0,70,70,Purple,"circle");Center((RectTransform)home.transform,Vector2.zero);Box("Crown",home.transform,18,19,34,30,Color.white,"crown");
        var layer=Rect("Pieces",board,0,0,800,800);Set(view,"pieceLayer",layer);
        var piece=Box("Piece",layer,0,0,44,44,Color.white,"circle");piece.raycastTarget=true;var pieceButton=piece.gameObject.AddComponent<UnityEngine.UI.Button>();pieceButton.targetGraphic=piece;var pieceView=piece.gameObject.AddComponent<PieceView>();Set(pieceView,"button",pieceButton);Set(pieceView,"body",piece);
        var halo=Box("LegalHalo",piece.transform,-5,-5,54,54,new Color32(239,201,91,255),"circle");halo.transform.SetAsFirstSibling();Set(pieceView,"halo",halo);
        Box("Inner",piece.transform,3,3,38,38,Color.white,"circle");var number=Text("Number",piece.transform,"",0,0,44,44,23,Purple,true);number.alignment=TextAlignmentOptions.Center;Set(pieceView,"number",number);
        var effect=Text("Effect",piece.transform,"",28,-10,25,25,17,Purple,true);effect.alignment=TextAlignmentOptions.Center;Set(pieceView,"effect",effect);
        Center((RectTransform)piece.transform,Vector2.zero);var pieceAsset=Save(piece.gameObject,"Piece");UnityEngine.Object.DestroyImmediate(piece.gameObject);Set(view,"piecePrefab",pieceAsset.GetComponent<PieceView>());
        Text("Legend",boardCard.transform,"+2  Tăng tốc     −2  Chậm     +1  May mắn     !  Bẫy     K  Khiên",20,846,848,28,17,Muted).alignment=TextAlignmentOptions.Center;

        var turn=Box("TurnPanel",content,1376,144,496,508,Color.white);
        Text("Title",turn.transform,"LƯỢT HIỆN TẠI",28,20,320,30,17,Purple,true);
        Set(controller,"turnName",Text("Name",turn.transform,"Đang chờ",28,54,340,44,30,null,true));
        Set(controller,"timer",Text("Timer",turn.transform,"—",390,46,80,54,37,Purple,true));
        Set(controller,"phase",Text("Phase",turn.transform,"Chờ dữ liệu trận đấu",28,106,440,30,19,Muted));
        Box("TimerTrack",turn.transform,28,148,440,6,Pale);var fill=Box("TimerFill",turn.transform,28,148,440,6,Purple);fill.type=UnityEngine.UI.Image.Type.Filled;fill.fillMethod=UnityEngine.UI.Image.FillMethod.Horizontal;fill.fillAmount=0;Set(controller,"timerFill",fill);
        var die=Box("Dice",turn.transform,200,178,96,96,new Color32(245,239,255,255));var dv=die.gameObject.AddComponent<DiceView>();
        var points=new[]{new Vector2(19,19),new Vector2(63,19),new Vector2(19,41),new Vector2(41,41),new Vector2(63,41),new Vector2(19,63),new Vector2(63,63)};
        var pips=points.Select((p,i)=>Box("Pip"+i,die.transform,p.x,p.y,14,14,Purple,"circle").gameObject).ToArray();Array(dv,"pips",pips);var empty=Text("Empty",die.transform,"?",0,0,96,96,42,Purple,true);empty.alignment=TextAlignmentOptions.Center;Set(dv,"empty",empty);dv.Show(0);
        var diceAsset=Save(die.gameObject,"Dice");UnityEngine.Object.DestroyImmediate(die.gameObject);var diceGo=(GameObject)PrefabUtility.InstantiatePrefab(diceAsset,turn.transform);Set(controller,"dice",diceGo.GetComponent<DiceView>());
        var diceText=Text("DiceLabel",turn.transform,"Chưa đổ xúc xắc",28,286,440,30,19,Muted);diceText.alignment=TextAlignmentOptions.Center;Set(controller,"diceLabel",diceText);
        Set(controller,"rollButton",Button("RollButton",turn.transform,"Đổ xúc xắc",28,328,440,58));
        Set(controller,"moveButton",Button("MoveButton",turn.transform,"Di chuyển quân",28,400,440,54,false));
        var selection=Text("Selection",turn.transform,"Chọn quân được đánh dấu trên bàn cờ",20,468,456,26,17,Muted);selection.alignment=TextAlignmentOptions.Center;Set(controller,"selection",selection);
        var chat=Box("ChatPanel",content,1376,676,496,270,Color.white);Text("Title",chat.transform,"Trò chuyện",28,20,440,40,26,null,true);
        Text("Unavailable",chat.transform,"Trò chuyện chưa khả dụng",28,88,440,30,21,Muted).alignment=TextAlignmentOptions.Center;
        Text("Detail",chat.transform,"Chức năng sẽ mở khi máy chủ hỗ trợ.",28,124,440,30,18,Muted).alignment=TextAlignmentOptions.Center;
        var input=Box("ChatInput",chat.transform,24,190,344,54,Pale);Text("Placeholder",input.transform,"Nhập tin nhắn...",16,0,312,54,18,Muted);Button("SendButton",chat.transform,"Gửi",380,190,92,54,false).interactable=false;
        var feedback=Text("Feedback",content,"Đăng nhập và bắt đầu trận từ phòng chờ.",1384,964,476,64,18,Muted);feedback.textWrappingMode=TextWrappingModes.Normal;Set(controller,"feedback",feedback);
        var modal=Box("ForfeitConfirmation",content,0,0,1920,1080,new Color(0.13f,.1f,.24f,.5f));modal.raycastTarget=true;
        var dialog=Box("Dialog",modal.transform,620,366,680,348,Color.white);Text("Title",dialog.transform,"Rời trận đấu đang diễn ra?",40,36,600,54,30,null,true);
        var description=Text("Description",dialog.transform,"Bỏ cuộc sẽ kết thúc phần chơi của bạn ngay lập tức. Bạn nhận 0 điểm và không thể quay lại trận này.",40,112,600,84,23,Muted);description.textWrappingMode=TextWrappingModes.Normal;
        Set(controller,"cancelButton",Button("CancelButton",dialog.transform,"Ở lại",40,242,282,62,false));Set(controller,"confirmButton",Button("ConfirmButton",dialog.transform,"Bỏ cuộc",342,242,298,62));Set(controller,"confirmation",modal.gameObject);modal.gameObject.SetActive(false);
        Save(canvas,"GameScreen");
        EditorSceneManager.SaveScene(scene,"Assets/Scenes/GameScene.unity");
        EditorBuildSettings.scenes=EditorBuildSettings.scenes.Concat(new[]{new EditorBuildSettingsScene("Assets/Scenes/GameScene.unity",true)}).ToArray();
        AssetDatabase.SaveAssets();EditorSceneManager.OpenScene("Assets/Scenes/GameScene.unity",OpenSceneMode.Single);
        return "GameScene and GameScreen/PlayerCard/Piece/Dice prefabs saved.";
    }
}
