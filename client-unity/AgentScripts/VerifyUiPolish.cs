using System;
using System.Linq;
using System.Net;
using System.Net.Sockets;
using System.Reflection;
using System.Threading.Tasks;
using Ludo.Controllers;
using Ludo.Services;
using Ludo.Views;
using Newtonsoft.Json.Linq;
using TMPro;
using UnityEngine;
using UnityEditor;

public static class VerifyUiPolish
{
    static int checks;
    static void Check(bool pass,string message){if(!pass)throw new Exception(message);checks++;}
    static T Field<T>(object o,string name)=>(T)o.GetType().GetField(name,BindingFlags.Instance|BindingFlags.NonPublic).GetValue(o);
    static void Capture(Canvas canvas,string name)
    {
        var camera=Camera.main;var mode=canvas.renderMode;var cam=canvas.worldCamera;float distance=canvas.planeDistance;
        var prior=camera.targetTexture;int mask=camera.cullingMask;var active=RenderTexture.active;var rt=new RenderTexture(1920,1080,24);Texture2D t=null;
        try{camera.cullingMask=-1;camera.targetTexture=rt;canvas.renderMode=RenderMode.ScreenSpaceCamera;canvas.worldCamera=camera;canvas.planeDistance=10;Canvas.ForceUpdateCanvases();camera.Render();RenderTexture.active=rt;t=new Texture2D(1920,1080,TextureFormat.RGB24,false);t.ReadPixels(new Rect(0,0,1920,1080),0,0);t.Apply();System.IO.File.WriteAllBytes("Documentation/"+name+".png",t.EncodeToPNG());}
        finally{canvas.renderMode=mode;canvas.worldCamera=cam;canvas.planeDistance=distance;camera.cullingMask=mask;camera.targetTexture=prior;RenderTexture.active=active;rt.Release();UnityEngine.Object.Destroy(rt);if(t!=null)UnityEngine.Object.Destroy(t);}
    }
    public static async Task<string> Main()
    {
        if(!EditorApplication.isPlaying)throw new Exception("Requires Play Mode.");
        checks=0;var session=NetworkSession.Instance;
        Check(session.SessionId==null,"Requires logged-out session");
        var listener=new TcpListener(IPAddress.Loopback,0);listener.Start();TcpClient peer=null;var host=session.ServerHost;var port=session.ServerPort;
        try
        {
            var accepted=listener.AcceptTcpClientAsync();
            await session.ChangeServerAsync("127.0.0.1",((IPEndPoint)listener.LocalEndpoint).Port);peer=await accepted;
            Check(session.State==ConnectionState.Connected,"Custom port connected");
            bool rejected=false;try{await session.ChangeServerAsync("127.0.0.1",65536);}catch(ArgumentException){rejected=true;}
            Check(rejected&&session.State==ConnectionState.Connected,"Invalid port rejected without dropping valid connection");
        }
        finally{peer?.Dispose();listener.Stop();await session.ChangeServerAsync(host,port);}
        var originals=UnityEngine.Object.FindObjectsByType<Canvas>(FindObjectsSortMode.None).Where(c=>c.transform.parent==null).Select(c=>c.gameObject).ToArray();
        GameObject preview=null;
        try
        {
            foreach(var o in originals)o.SetActive(false);
            preview=UnityEngine.Object.Instantiate(AssetDatabase.LoadAssetAtPath<GameObject>("Assets/Prefabs/Game/GameScreen.prefab"));
            var controller=preview.GetComponent<GameController>();controller.enabled=false;
            var board=preview.GetComponentInChildren<BoardView>();var participants=new JArray();
            for(int s=0;s<4;s++)
            {
                var pieces=new JArray();for(int i=0;i<4;i++)pieces.Add(new JObject{["pieceId"]="p"+s+"-"+i,["ownerPlayerId"]="p"+s,["stepCount"]=-1});
                participants.Add(new JObject{["playerId"]="p"+s,["color"]=BoardGeometry.Colors[s],["matchStatus"]="ACTIVE",["pieces"]=pieces});
            }
            var state=new JObject{["participants"]=participants};board.Bind(state,"p0",null,false,_=>{},false);
            await Task.Delay(100);Canvas.ForceUpdateCanvases();
            var horses=preview.GetComponentsInChildren<HorseGraphic>();
            Check(horses.Length==16,"16 horses");
            Check(horses.All(h=>h.canvasRenderer.GetMesh().vertexCount==18),"All horse meshes render");
            Check(preview.GetComponentsInChildren<PieceView>().All(p=>!Field<TMP_Text>(p,"number").gameObject.activeSelf),"No piece numbers");
            var frames=Field<UnityEngine.UI.Image[]>(board,"ownYardHighlights");Check(frames.Count(f=>f.gameObject.activeSelf)==1&&frames[0].gameObject.activeSelf,"Only own yard highlighted");
            for(int s=0;s<4;s++)
            {
                Check(Field<TMP_Text[]>(board,"cells")[s*12].text==new[]{"→","↓","←","↑"}[s],"Arrow "+s);
                var yard=(RectTransform)board.transform.Find("Yard"+s);
                for(int c=0;c<48;c++)
                {var delta=BoardGeometry.Ring(c)-yard.anchoredPosition;Check(Mathf.Abs(delta.x)>=yard.rect.width/2+26||Mathf.Abs(delta.y)>=yard.rect.height/2+26,"Yard does not overlap ring");}
            }
            var receive=typeof(GameController).GetMethod("OnChatReceived",BindingFlags.Instance|BindingFlags.NonPublic);
            for(int i=0;i<80;i++)receive.Invoke(controller,new object[]{new JObject{["senderDisplayName"]="Bạn chơi",["senderColor"]="BLUE",["message"]="Tin nhắn "+i+": Chúc bạn có một ván cờ thật vui!"}});
            await Task.Delay(80);Canvas.ForceUpdateCanvases();var scroll=Field<UnityEngine.UI.ScrollRect>(controller,"chatScroll");
            Check(scroll.content.rect.height>scroll.viewport.rect.height*5,"Chat content grows beyond viewport");
            Check(scroll.verticalNormalizedPosition<.01f,"Newest chat visible");
            scroll.verticalNormalizedPosition=1;Check(scroll.verticalNormalizedPosition>.99f,"Old messages accessible");scroll.verticalNormalizedPosition=0;
            var input=Field<TMP_InputField>(controller,"chatInput");input.text="Xin chào";input.ActivateInputField();await Task.Delay(80);Canvas.ForceUpdateCanvases();
            Check(input.customCaretColor&&input.caretColor.a==1&&input.caretWidth==3,"Opaque chat caret");
            Check(input.textViewport!=input.transform&&input.textComponent.rectTransform.parent==input.textViewport,"Chat text and caret share viewport");
            var dice=preview.GetComponentInChildren<DiceView>();dice.Animate(6);await Task.Delay(100);Check(dice.transform.localScale.x>1,"Dice animation visible");dice.Show(6);await Task.Delay(900);
            Check(Field<GameObject[]>(dice,"pips").Count(p=>p.activeSelf)==6&&dice.transform.localScale==Vector3.one,"Dice settles on server face");
            Capture(preview.GetComponent<Canvas>(),"ui-polish-game");
        }
        finally{if(preview!=null)UnityEngine.Object.Destroy(preview);foreach(var o in originals)if(o!=null)o.SetActive(true);}
        return checks+" UI/endpoint checks passed.";
    }
}
