using System;
using System.Linq;
using System.Net;
using System.Net.Sockets;
using System.Reflection;
using System.Threading;
using System.Threading.Tasks;
using Ludo.Controllers;
using Ludo.Services;
using Ludo.Views;
using Ludo.Network;
using Newtonsoft.Json.Linq;
using TMPro;
using UnityEngine;
using UnityEngine.SceneManagement;

public static class VerifyHistory
{
    static int checks, requests, reconnects;
    static bool wire, reject, malformed;
    static TcpListener listener;
    static TcpClient peer;
    static CancellationTokenSource stop;
    static JArray entries;
    static NetworkSession session;
    static HistoryController Controller=>UnityEngine.Object.FindFirstObjectByType<HistoryController>();
    static TMP_Text Text(string path)=>Controller.transform.Find("Content/"+path).GetComponent<TMP_Text>();
    static UnityEngine.UI.Button RefreshButton=>Controller.transform.Find("Content/RefreshButton").GetComponent<UnityEngine.UI.Button>();
    static UnityEngine.UI.ScrollRect Scroll=>Controller.transform.Find("Content/HistoryCard/ScrollView").GetComponent<UnityEngine.UI.ScrollRect>();
    static FieldInfo Field(string name)=>typeof(NetworkSession).GetField(name,BindingFlags.Instance|BindingFlags.NonPublic);
    static void Check(bool value,string name){if(!value)throw new Exception(name);checks++;}
    static async Task Until(Func<bool> condition,string name){for(int i=0;i<500;i++){if(condition())return;await Task.Delay(20);}throw new Exception("Timed out: "+name);}
    static JArray Entries(int n)
    {
        var array=new JArray();for(int i=0;i<n;i++)array.Add(new JObject{["matchId"]="fixture-match-"+(i+1),["startedAtEpochMillis"]=1789952400000L-i*86400000L,["endedAtEpochMillis"]=1789953000000L-i*86400000L,["playerCount"]=4,["color"]=BoardGeometry.Colors[i%4],["rank"]=i%4+1,["scoreEarned"]=new decimal[]{3,1.5m,.5m,0}[i%4],["forfeited"]=i%4==3});return array;
    }
    static async Task Serve()
    {
        while(!stop.IsCancellationRequested)
        {
            try
            {
                peer=await listener.AcceptTcpClientAsync();var stream=peer.GetStream();
                while(!stop.IsCancellationRequested)
                {
                    var req=JObject.Parse(await FrameCodec.ReadAsync(stream,stop.Token));string type=(string)req["type"];
                    var response=new JObject{["type"]=type,["requestId"]=req["requestId"],["success"]=true,["data"]=new JObject()};
                    if(type!="LOGIN"&&type!="RECONNECT")wire&=(string)req["sessionId"]=="history-fixture-token";
                    switch(type)
                    {
                        case "LOGIN":response["data"]=new JObject{["sessionId"]="history-fixture-token",["profile"]=new JObject{["playerId"]="p1",["displayName"]="Trần Hoàng Nam",["totalScore"]=149,["firstPlaceCount"]=29}};break;
                        case "GET_ONLINE_PLAYERS":response["type"]="ONLINE_PLAYERS_UPDATED";response["data"]=new JObject{["players"]=new JArray()};break;
                        case "GET_MATCH_HISTORY":
                            requests++;wire&=req["data"] is JObject data&&data.Count==0;
                            response["type"]="MATCH_HISTORY_RESULT";response["data"]=new JObject{["matches"]=entries.DeepClone()};
                            if(malformed)response["data"]=new JObject{["matches"]=new JArray(new JObject{["rank"]=1})};
                            if(reject){response["type"]="ERROR";response["success"]=false;response["error"]=new JObject{["code"]="INTERNAL_SERVER_ERROR"};}
                            await Task.Delay(300);break;
                        case "RECONNECT":reconnects++;wire&=(string)req["data"]?["sessionId"]=="history-fixture-token";response["type"]="RECONNECT_RESULT";response["data"]=new JObject{["restored"]=true};break;
                        case "LOGOUT":break;
                        default:wire=false;break;
                    }
                    var bytes=FrameCodec.Encode(response.ToString());await stream.WriteAsync(bytes,0,bytes.Length);
                }
            }
            catch(Exception e) when(e is System.IO.IOException||e is SocketException||e is ObjectDisposedException||e is OperationCanceledException){if(stop.IsCancellationRequested)return;}
        }
    }
    static void Capture(int width,int height)
    {
        var canvas=Controller.GetComponent<Canvas>();var camera=Camera.main;var mode=canvas.renderMode;var prior=canvas.worldCamera;float distance=canvas.planeDistance;
        var target=camera.targetTexture;var active=RenderTexture.active;int mask=camera.cullingMask;var rt=new RenderTexture(width,height,24);Texture2D texture=null;
        try
        {
            canvas.renderMode=RenderMode.ScreenSpaceCamera;canvas.worldCamera=camera;canvas.planeDistance=10;camera.targetTexture=rt;camera.cullingMask=-1;
            Canvas.ForceUpdateCanvases();camera.Render();RenderTexture.active=rt;texture=new Texture2D(width,height,TextureFormat.RGB24,false);texture.ReadPixels(new Rect(0,0,width,height),0,0);texture.Apply();
            System.IO.File.WriteAllBytes("Documentation/history-"+width+"x"+height+"-fixture.png",texture.EncodeToPNG());
            foreach(string path in new[]{"BackButton","RefreshButton","HistoryCard","HistoryCard/ScrollView","Feedback"})
            {
                var corners=new Vector3[4];((RectTransform)Controller.transform.Find("Content/"+path)).GetWorldCorners(corners);
                Check(corners.All(c=>{var p=RectTransformUtility.WorldToScreenPoint(camera,c);return p.x>=0&&p.x<=width&&p.y>=0&&p.y<=height;}),"Visible bounds "+path+" at "+width);
            }
        }
        finally
        {
            canvas.renderMode=mode;canvas.worldCamera=prior;canvas.planeDistance=distance;camera.targetTexture=target;camera.cullingMask=mask;RenderTexture.active=active;rt.Release();UnityEngine.Object.DestroyImmediate(rt);if(texture!=null)UnityEngine.Object.DestroyImmediate(texture);Canvas.ForceUpdateCanvases();
        }
    }
    public static async Task<string> Main()
    {
        if(!UnityEditor.EditorApplication.isPlaying)throw new Exception("Requires Play Mode, offline Login.");
        session=NetworkSession.Instance;await Until(()=>session!=null&&session.State!=ConnectionState.Connecting,"offline session");
        if(session.SessionId!=null||session.State!=ConnectionState.Disconnected)throw new Exception("Requires unauthenticated offline session.");
        checks=requests=reconnects=0;wire=true;reject=malformed=false;entries=Entries(20);
        var port=Field("serverPort").GetValue(session);var host=Field("serverHost").GetValue(session);
        listener=new TcpListener(IPAddress.Loopback,0);listener.Start();stop=new CancellationTokenSource();
        Field("serverPort").SetValue(session,((IPEndPoint)listener.LocalEndpoint).Port);Field("serverHost").SetValue(session,"127.0.0.1");var server=Task.Run(Serve);
        try
        {
            await session.ConnectAsync();await session.LoginAsync("fixture","fixture");SceneManager.LoadScene("LobbyScene");
            await Until(()=>UnityEngine.Object.FindFirstObjectByType<LobbyController>()!=null,"lobby");await Task.Delay(100);
            UnityEngine.Object.FindFirstObjectByType<LobbyController>().OpenHistory();await Until(()=>Controller!=null,"History navigation");await Task.Delay(50);
            Check(!RefreshButton.interactable,"Initial loading disables refresh");Controller.Refresh();Controller.Refresh();
            await Until(()=>RefreshButton.interactable,"initial load");Check(requests==1,"Initial request and duplicate suppression");
            Check(Scroll.content.childCount==20,"All server entries rendered");Check(Scroll.content.rect.height>Scroll.viewport.rect.height,"Long list scrolls");
            var first=Scroll.content.GetChild(0);
            Check(first.Find("Medal/Rank").GetComponent<TMP_Text>().text=="Hạng 1/4","Rank and player count from server");
            var expectedTime=DateTimeOffset.FromUnixTimeMilliseconds((long)entries[0]["endedAtEpochMillis"]).ToLocalTime().ToString("dd/MM/yyyy • HH:mm",System.Globalization.CultureInfo.InvariantCulture);
            Check(first.Find("Time").GetComponent<TMP_Text>().text==expectedTime,"Server end time formatted in local timezone");
            Check(first.Find("Metadata").GetComponent<TMP_Text>().text=="Quân đỏ • fixture-match-1","Color and match identifier rendered");
            Check(Scroll.content.GetChild(1).Find("Score").GetComponent<TMP_Text>().text=="+1.5","Fractional score preserved");
            Check(Scroll.content.GetChild(3).Find("Score").GetComponent<TMP_Text>().text=="+0"&&Scroll.content.GetChild(3).Find("Status").GetComponent<TMP_Text>().text=="Bỏ cuộc","Forfeit distinguished with zero score");
            Check(first.Find("Crown").gameObject.activeSelf&&!Scroll.content.GetChild(1).Find("Crown").gameObject.activeSelf,"Crown only on first place");
            Check(Text("Count").text=="20 trận gần nhất • 5 lần hạng nhất","Summary limited to returned matches");
            Check(!first.Find("Metadata").GetComponent<TMP_Text>().richText,"Match identifiers are plain text");
            Capture(1920,1080);Capture(2560,1440);Capture(3840,2160);
            Scroll.verticalNormalizedPosition=0;await Task.Delay(100);Check(Scroll.content.anchoredPosition.y>0,"Scroll reaches lower rows");
            reject=true;Controller.Refresh();await Until(()=>RefreshButton.interactable,"rejected load");
            Check(Text("Feedback").text.StartsWith("Chưa tải được"),"Server error displayed");Check(Scroll.content.GetChild(0).gameObject.activeSelf,"Error preserves prior list");
            reject=false;malformed=true;Controller.Refresh();await Until(()=>RefreshButton.interactable,"invalid response");Check(Text("Feedback").text.StartsWith("Chưa tải được"),"Malformed response rejected safely");
            malformed=false;entries=new JArray();Controller.Refresh();await Until(()=>RefreshButton.interactable,"empty response");
            Check(Text("HistoryCard/Empty").gameObject.activeSelf,"Empty state visible");Check(Scroll.content.Cast<Transform>().All(t=>!t.gameObject.activeSelf),"Old rows hidden on empty result");
            entries=Entries(3);entries[0]["rank"]=2;Controller.Refresh();await Until(()=>RefreshButton.interactable,"three rows");
            Check(Scroll.content.GetChild(0).Find("Medal/Rank").GetComponent<TMP_Text>().text=="Hạng 2/4","Client preserves server rank");
            Check(Scroll.content.childCount==20&&Scroll.content.Cast<Transform>().Count(t=>t.gameObject.activeSelf)==3,"Rows reused without duplicates");
            Check(Math.Abs(Scroll.content.anchoredPosition.y)<1,"Short list remains aligned to top");
            entries=Entries(20);Controller.Refresh();await Until(()=>RefreshButton.interactable,"long list restored");
            Scroll.verticalNormalizedPosition=0;Controller.Refresh();await Until(()=>RefreshButton.interactable,"scrolled refresh");
            Check(Scroll.verticalNormalizedPosition>=.99f,"Refresh resets long list to top");
            peer.Close();await Until(()=>session.State!=ConnectionState.Connected,"disconnect");Check(!RefreshButton.interactable,"Offline refresh disabled");
            await Until(()=>session.State==ConnectionState.Connected&&RefreshButton.interactable&&reconnects==1,"reconnect refresh");
            int before=requests;Controller.Refresh();Controller.Back();await Until(()=>SceneManager.GetActiveScene().name=="LobbyScene","back while pending");await Task.Delay(500);
            Check(SceneManager.GetActiveScene().name=="LobbyScene"&&requests==before+1,"Late response does not reopen ranking");Check(NetworkSession.Instance==session,"Navigation preserves session instance");
            Check(wire,"GET_MATCH_HISTORY empty payload, response type and session over TCP");await session.LogoutAsync();
            SceneManager.LoadScene("HistoryScene");await Until(()=>SceneManager.GetActiveScene().name=="LoginScene","unauthenticated guard");Check(true,"Direct unauthenticated entry returns Login");
            return checks+" History checks passed (TCP localhost fixture).";
        }
        finally
        {
            Field("serverPort").SetValue(session,port);Field("serverHost").SetValue(session,host);stop.Cancel();peer?.Close();listener.Stop();if(server.IsCompleted)await server;
        }
    }
}
