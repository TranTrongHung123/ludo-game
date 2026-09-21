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

public static class VerifyGame
{
    static int checks, rolls, moves, leaves, reconnects;
    static bool wire=true, rejectMove=true, rejectLeave=false;
    static TcpListener listener;
    static TcpClient peer;
    static NetworkStream stream;
    static readonly SemaphoreSlim writes=new SemaphoreSlim(1,1);
    static JObject state, room;
    static CancellationTokenSource stop;
    static NetworkSession session;
    static object originalPort,originalHost;
    static Task server;
    static FieldInfo Field(string name)=>typeof(NetworkSession).GetField(name,BindingFlags.Instance|BindingFlags.NonPublic);
    static void Check(bool value,string name){if(!value)throw new Exception(name);checks++;}
    static async Task Until(Func<bool> value,string name){for(int i=0;i<500;i++){if(value())return;await Task.Delay(20);}throw new Exception("Timed out: "+name);}
    static GameController Controller=>UnityEngine.Object.FindFirstObjectByType<GameController>();
    static UnityEngine.UI.Button Button(string path)=>Controller.transform.Find("Content/"+path).GetComponent<UnityEngine.UI.Button>();
    static TMP_Text Text(string path)=>Controller.transform.Find("Content/"+path).GetComponent<TMP_Text>();
    static ResultController Result=>UnityEngine.Object.FindFirstObjectByType<ResultController>();
    static TMP_Text ResultText(string path)=>Result.transform.Find("Content/"+path).GetComponent<TMP_Text>();
    static UnityEngine.UI.Button ResultButton=>Result.transform.Find("Content/LobbyButton").GetComponent<UnityEngine.UI.Button>();
    static void CaptureResult(int width,int height)
    {
        var canvas=Result.GetComponent<Canvas>();var camera=Camera.main;
        var mode=canvas.renderMode;var priorCamera=canvas.worldCamera;float distance=canvas.planeDistance;
        var previous=camera.targetTexture;var active=RenderTexture.active;int mask=camera.cullingMask;
        var rt=new RenderTexture(width,height,24);Texture2D texture=null;
        try
        {
            camera.targetTexture=rt;camera.cullingMask=-1;canvas.renderMode=RenderMode.ScreenSpaceCamera;canvas.worldCamera=camera;canvas.planeDistance=10;
            Canvas.ForceUpdateCanvases();camera.Render();RenderTexture.active=rt;
            texture=new Texture2D(width,height,TextureFormat.RGB24,false);texture.ReadPixels(new Rect(0,0,width,height),0,0);texture.Apply();
            System.IO.File.WriteAllBytes("Documentation/result-"+width+"x"+height+"-fixture.png",texture.EncodeToPNG());
            var corners=new Vector3[4];var content=Result.transform.Find("Content");
            foreach(var rect in content.GetComponentsInChildren<RectTransform>())
            {
                rect.GetWorldCorners(corners);
                Check(corners.All(c=>{var point=RectTransformUtility.WorldToScreenPoint(camera,c);return point.x>=-1&&point.x<=width+1&&point.y>=-1&&point.y<=height+1;}),"Result bounds "+rect.name+" at "+width);
            }
        }
        finally
        {
            canvas.renderMode=mode;canvas.worldCamera=priorCamera;canvas.planeDistance=distance;camera.targetTexture=previous;camera.cullingMask=mask;RenderTexture.active=active;
            rt.Release();UnityEngine.Object.DestroyImmediate(rt);if(texture!=null)UnityEngine.Object.DestroyImmediate(texture);Canvas.ForceUpdateCanvases();
        }
    }
    static long Now=>DateTimeOffset.UtcNow.ToUnixTimeMilliseconds();
    static JObject Snapshot()
    {
        var members=new JArray();
        for(int s=0;s<4;s++)
        {
            var pieces=new JArray();for(int i=0;i<4;i++)pieces.Add(new JObject{["pieceId"]="p"+s+"-"+i,["ownerPlayerId"]="p"+s,["color"]=BoardGeometry.Colors[s],["state"]="IN_YARD",["stepCount"]=-1,["slowed"]=false,["shielded"]=false});
            members.Add(new JObject{["playerId"]="p"+s,["displayName"]=new[]{"Người kiểm thử","Bạn xanh dương","Bạn vàng","Bạn xanh lá"}[s],["slotIndex"]=s,["color"]=BoardGeometry.Colors[s],["presenceState"]="PLAYING",["matchStatus"]="ACTIVE",["rank"]=null,["scoreEarned"]=0,["pieces"]=pieces});
        }
        var specials=new JArray();for(int s=0;s<4;s++)for(int i=0;i<5;i++)specials.Add(new JObject{["globalIndex"]=s*12+2+i*2,["type"]=new[]{"SPEED","SLOW","LUCKY","TRAP","SHIELD"}[i]});
        return new JObject{["roomId"]="fixture-room",["matchId"]="fixture-match",["roomState"]="PLAYING",["currentPlayerId"]="p0",["currentSlot"]=0,["turnState"]="WAITING_FOR_ROLL",["diceValue"]=null,["validPieceIds"]=new JArray(),["phaseDurationMillis"]=8000,["serverDeadlineEpochMillis"]=Now+8000,["participants"]=members,["specialCells"]=specials,["stateVersion"]=1};
    }
    static async Task Send(JObject message)
    {var bytes=FrameCodec.Encode(message.ToString());await writes.WaitAsync();try{await stream.WriteAsync(bytes,0,bytes.Length);}finally{writes.Release();}}
    static Task Event(string type,JObject data)=>Send(new JObject{["type"]=type,["data"]=data.DeepClone()});
    static async Task Serve()
    {
        while(!stop.IsCancellationRequested)
        {
            try
            {
                peer=await listener.AcceptTcpClientAsync();stream=peer.GetStream();
                while(!stop.IsCancellationRequested)
                {
                    var request=JObject.Parse(await FrameCodec.ReadAsync(stream,stop.Token));string type=(string)request["type"];
                    var response=new JObject{["type"]=type,["requestId"]=request["requestId"],["success"]=true,["data"]=new JObject()};
                    if(type=="RECONNECT") wire&=(string)request["data"]?["sessionId"]=="fixture-token";
                    else if(type!="LOGIN")wire&=(string)request["sessionId"]=="fixture-token";
                    switch(type)
                    {
                        case "LOGIN":response["data"]=new JObject{["sessionId"]="fixture-token",["profile"]=new JObject{["playerId"]="p0",["displayName"]="Người kiểm thử",["totalScore"]=0,["firstPlaceCount"]=0}};break;
                        case "CREATE_ROOM":response["data"]=new JObject{["room"]=room.DeepClone()};break;
                        case "GET_ONLINE_PLAYERS":response["type"]="ONLINE_PLAYERS_UPDATED";response["data"]=new JObject{["players"]=new JArray()};break;
                        case "START_GAME":state["serverDeadlineEpochMillis"]=Now+8000;response["data"]=state.DeepClone();break;
                        case "ROLL_DICE":
                            rolls++;wire&=request["data"] is JObject roll&&roll.Count==1&&(string)roll["roomId"]=="fixture-room";
                            await Task.Delay(250);state["stateVersion"]=(long)state["stateVersion"]+1;state["turnState"]="WAITING_FOR_MOVE";state["diceValue"]=6;state["validPieceIds"]=new JArray("p0-0","p0-1","p0-2","p0-3");state["phaseDurationMillis"]=12000;state["serverDeadlineEpochMillis"]=Now+12000;
                            response["type"]="DICE_RESULT";response["data"]=new JObject{["roomId"]="fixture-room",["playerId"]="p0",["diceValue"]=6,["validPieceIds"]=state["validPieceIds"].DeepClone()};break;
                        case "MOVE_PIECE":
                            moves++;wire&=request["data"] is JObject move&&move.Count==2&&(string)move["roomId"]=="fixture-room"&&(string)move["pieceId"]=="p0-0";
                            await Task.Delay(250);response["type"]="MOVE_PIECE_RESULT";
                            if(rejectMove){response["type"]="ERROR";response["success"]=false;response["error"]=new JObject{["code"]="INVALID_MOVE"};break;}
                            state["stateVersion"]=(long)state["stateVersion"]+1;state["participants"][0]["pieces"][0]["stepCount"]=0;state["participants"][0]["pieces"][0]["state"]="ON_TRACK";state["turnState"]="WAITING_FOR_ROLL";state["validPieceIds"]=new JArray();state["phaseDurationMillis"]=8000;state["serverDeadlineEpochMillis"]=Now+8000;
                            response["data"]=new JObject{["piece"]=state["participants"][0]["pieces"][0].DeepClone(),["gameState"]=state.DeepClone(),["bonusRoll"]=true,["shieldConsumed"]=false};break;
                        case "RECONNECT":reconnects++;response["type"]="RECONNECT_RESULT";response["data"]=new JObject{["restored"]=true,["room"]=room.DeepClone(),["gameState"]=state.DeepClone()};break;
                        case "LEAVE_ROOM":
                            leaves++;wire&=request["data"] is JObject leave&&leave.Count==1&&(string)leave["roomId"]=="fixture-room";
                            await Task.Delay(250);
                            if(rejectLeave){response["type"]="ERROR";response["success"]=false;response["error"]=new JObject{["code"]="INTERNAL_SERVER_ERROR"};}
                            break;
                        case "LOGOUT":break;
                        default:wire=false;break;
                    }
                    await Send(response);
                    if(type=="ROLL_DICE"){await Task.Delay(250);await Event("DICE_RESULT",(JObject)response["data"]);await Event("GAME_STATE_UPDATED",state);}
                    if(type=="MOVE_PIECE"&&!rejectMove)await Event("GAME_STATE_UPDATED",state);
                }
            }
            catch(Exception e) when(e is System.IO.IOException||e is SocketException||e is ObjectDisposedException||e is OperationCanceledException){if(stop.IsCancellationRequested)return;}
        }
    }
    public static async Task<string> Main()
    {
        if(!UnityEditor.EditorApplication.isPlaying)throw new Exception("Requires Play Mode with offline Login scene.");
        checks=rolls=moves=leaves=reconnects=0;wire=true;rejectMove=true;rejectLeave=false;
        session=NetworkSession.Instance;await Until(()=>session!=null&&session.State!=ConnectionState.Connecting,"offline session");
        if(session.State!=ConnectionState.Disconnected||session.SessionId!=null)throw new Exception("Requires offline unauthenticated session.");
        Check(Enumerable.Range(0,48).Select(BoardGeometry.Ring).Distinct().Count()==48,"48 distinct ring cells");
        for(int s=0;s<4;s++)
        {Check(BoardGeometry.Position(s,0,0)==BoardGeometry.Ring(s*12),"Spawn mapping "+s);Check(BoardGeometry.Position(s,47,0)==BoardGeometry.Ring((s*12+47)%48),"Entry mapping "+s);}
        Check(BoardGeometry.Position(0,50,0)==new Vector2(-174,0),"Carry-over maps to finish slot 3");
        state=Snapshot();room=new JObject{["roomId"]="fixture-room",["hostPlayerId"]="p0",["state"]="PLAYING",["players"]=new JArray(state["participants"].Select(p=>new JObject{["playerId"]=p["playerId"],["displayName"]=p["displayName"],["slotIndex"]=p["slotIndex"],["color"]=p["color"],["ready"]=true,["presenceState"]="PLAYING"}))};
        Check(!GameController.MayAct(state,"p1","WAITING_FOR_ROLL",Now),"Wrong player blocked");Check(!GameController.MayAct(state,"p0","WAITING_FOR_MOVE",Now),"Wrong phase blocked");Check(!GameController.MayAct(state,"p0","WAITING_FOR_ROLL",Now+9000),"Expired deadline blocked");
        listener=new TcpListener(IPAddress.Loopback,0);listener.Start();stop=new CancellationTokenSource();originalPort=Field("serverPort").GetValue(session);originalHost=Field("serverHost").GetValue(session);Field("serverPort").SetValue(session,((IPEndPoint)listener.LocalEndpoint).Port);Field("serverHost").SetValue(session,"127.0.0.1");server=Task.Run(Serve);
        try
        {
            await session.ConnectAsync();await session.LoginAsync("fixture","fixture");await session.EnterRoomAsync("CREATE_ROOM");await session.StartGameAsync();SceneManager.LoadScene("RoomScene");
            await Until(()=>Controller!=null,"Room to Game transition");await Task.Delay(100);
            Check(SceneManager.GetActiveScene().name=="GameScene","Room navigates to GameScene");
            Check(UnityEngine.Object.FindObjectsByType<PieceView>(FindObjectsSortMode.None).Length==16,"16 server pieces rendered");
            Check(Text("TurnPanel/Timer").text!="—","Server deadline displayed");
            var first=UnityEngine.Object.FindObjectsByType<PieceView>(FindObjectsSortMode.None).First();Check(((RectTransform)first.transform).anchorMin==new Vector2(.5f,.5f),"Piece layer uses centered anchors");
            Controller.Roll();Controller.Roll();Check(!Button("TurnPanel/RollButton").interactable,"Roll pending disables button");Check(session.GameState["diceValue"].Type==JTokenType.Null,"No optimistic dice");
            await Until(()=>Text("Feedback").text=="Đã cập nhật.","roll acknowledgement");Check(!Button("TurnPanel/RollButton").interactable,"Roll remains locked until updated snapshot");Controller.Roll();
            await Until(()=>(string)session.GameState["turnState"]=="WAITING_FOR_MOVE"&&Text("Feedback").text=="Đã cập nhật.","roll result");Check(rolls==1,"Duplicate roll suppressed");
            Controller.SelectPiece("p1-0");Check(!Button("TurnPanel/MoveButton").interactable,"Opponent selection blocked");Controller.SelectPiece("p0-0");Check(Button("TurnPanel/MoveButton").interactable,"Valid piece selected");
            Controller.Move();Controller.Move();Check((int)session.GameState["participants"][0]["pieces"][0]["stepCount"]==-1,"No optimistic move");
            await Until(()=>Text("Feedback").text.StartsWith("Nước đi"),"server rejection");Check(moves==1&&Button("TurnPanel/MoveButton").interactable,"Rejected move unlocks retry");
            rejectMove=false;Controller.Move();await Until(()=>(int)session.GameState["participants"][0]["pieces"][0]["stepCount"]==0&&Button("TurnPanel/RollButton").interactable,"move response");Check(moves==2,"Move response applies authoritative snapshot");
            var stale=Snapshot();stale["stateVersion"]=1;await Event("GAME_STATE_UPDATED",stale);await Task.Delay(120);Check((int)session.GameState["participants"][0]["pieces"][0]["stepCount"]==0,"Stale state rejected");
            var wrong=(JObject)state.DeepClone();wrong["roomId"]="another-room";wrong["stateVersion"]=99;await Event("GAME_STATE_UPDATED",wrong);await Task.Delay(120);Check((string)session.GameState["roomId"]=="fixture-room","Foreign room rejected");
            state["serverDeadlineEpochMillis"]=Now-1;state["stateVersion"]=(long)state["stateVersion"]+1;await Event("GAME_STATE_UPDATED",state);await Until(()=>!Button("TurnPanel/RollButton").interactable,"timer expiration");Check((string)session.GameState["turnState"]=="WAITING_FOR_ROLL","Countdown does not change server phase");
            peer.Close();await Until(()=>session.State!=ConnectionState.Connected,"disconnect");Check(!Button("TurnPanel/RollButton").interactable,"Offline actions disabled");state["participants"][1]["presenceState"]="DISCONNECTED";state["serverDeadlineEpochMillis"]=Now+8000;state["stateVersion"]=(long)state["stateVersion"]+1;
            await Until(()=>reconnects==1&&session.State==ConnectionState.Connected,"reconnect");Check(Text("Player1/Status").text.StartsWith("Mất kết nối"),"Reconnect snapshot presence rendered");
            state["participants"][1]["matchStatus"]="FORFEITED";state["stateVersion"]=(long)state["stateVersion"]+1;await Event("GAME_STATE_UPDATED",state);await Until(()=>UnityEngine.Object.FindObjectsByType<PieceView>(FindObjectsSortMode.None).Length==12,"forfeit pieces removed");Check(true,"Forfeited player has no pieces on board");
            state["roomState"]="FINISHED";state["turnState"]="FINISHED";state["currentPlayerId"]=null;state["currentSlot"]=null;state["serverDeadlineEpochMillis"]=null;state["participants"][0]["rank"]=1;state["participants"][0]["scoreEarned"]=10;state["stateVersion"]=(long)state["stateVersion"]+1;await Event("GAME_STATE_UPDATED",state);
            var standings=new JArray();
            for(int i=3;i>=0;i--)standings.Add(new JObject{["playerId"]="p"+i,["displayName"]=i==0?"Người kiểm thử":i==1?"<b>Tên dài không dùng rich text</b>":"Người chơi "+i,["rank"]=i+1,["color"]=BoardGeometry.Colors[i],["matchStatus"]=i==3?"FORFEITED":"COMPLETED",["scoreEarned"]=new decimal[]{3,1.5m,.5m,0}[i],["totalScore"]=new decimal[]{13,21.5m,7.5m,0}[i]});
            var result=new JObject{["roomId"]="fixture-room",["matchId"]="fixture-match",["standings"]=standings};
            var foreign=(JObject)result.DeepClone();foreign["matchId"]="wrong-match";await Event("GAME_OVER",foreign);await Task.Delay(150);
            Check(SceneManager.GetActiveScene().name=="GameScene"&&session.GameOver==null,"Foreign match result ignored");
            await Event("GAME_OVER",result);await Until(()=>Result!=null,"Game to Result transition");await Task.Delay(150);
            Check(ResultText("Headline").text=="Bạn về hạng 1!","Authoritative placement headline");
            Check(ResultText("ResultsCard/ResultRow0/Total").text=="13","Server total is displayed directly");
            Check(ResultText("ResultsCard/ResultRow1/Earned").text=="+1.5","Fractional score preserved");
            Check(ResultText("ResultsCard/ResultRow3/Status").text=="Đã bỏ cuộc","Forfeit distinguished from completion");
            Check(Result.transform.Find("Content/ResultsCard/ResultRow0/SelfBadge").gameObject.activeSelf,"Current player badge");
            Check(!ResultText("ResultsCard/ResultRow1/PlayerName").richText,"Names cannot inject TMP markup");
            Check(UnityEngine.Object.FindObjectsByType<ResultRowView>(FindObjectsSortMode.None).Length==4,"Four standings rendered in rank order");
            CaptureResult(1920,1080);CaptureResult(2560,1440);CaptureResult(3840,2160);
            peer.Close();await Until(()=>session.State!=ConnectionState.Connected,"result disconnect");
            Check(!ResultButton.interactable,"Result leave disabled offline");
            Check(ResultText("ResultsCard/ResultRow0/Total").text=="13","Result retained offline");
            await Until(()=>session.State==ConnectionState.Connected&&reconnects==2,"result reconnect");
            Check(ResultButton.interactable&&session.GameOver!=null,"Reconnect preserves official result");
            var smaller=(JObject)result.DeepClone();smaller["standings"]=new JArray(standings[2].DeepClone(),standings[3].DeepClone());
            smaller["standings"][1]["matchStatus"]="FORFEITED";smaller["standings"][1]["scoreEarned"]=0;
            await Event("GAME_OVER",smaller);await Until(()=>ResultText("Headline").text.StartsWith("Bạn đã bỏ cuộc"),"forfeit headline");
            Check(UnityEngine.Object.FindObjectsByType<ResultRowView>(FindObjectsSortMode.None).Length==2,"Two-player result hides unused rows");
            Check(ResultText("ResultsCard/ResultRow0/Earned").text=="+0","Forfeit score comes from server");
            await Event("GAME_OVER",result);await Until(()=>ResultText("Headline").text=="Bạn về hạng 1!","duplicate result update");
            rejectLeave=true;int priorLeaves=leaves;ResultButton.onClick.Invoke();ResultButton.onClick.Invoke();
            Check(!ResultButton.interactable&&session.Room!=null,"Leave pending locks button and keeps room");
            await Until(()=>ResultText("Feedback").text.StartsWith("Chưa thể rời phòng"),"leave rejection");
            Check(leaves==priorLeaves+1&&ResultButton.interactable,"Leave rejection permits retry; duplicate suppressed");
            Check(session.GameOver!=null&&SceneManager.GetActiveScene().name=="ResultScene","Rejected leave retains results");
            rejectLeave=false;ResultButton.onClick.Invoke();Check(SceneManager.GetActiveScene().name=="ResultScene","Result waits for leave acknowledgement");
            await Until(()=>SceneManager.GetActiveScene().name=="LobbyScene","leave completed game");Check(session.GameState==null&&session.GameOver==null,"Leaving clears game cache");
            state=Snapshot();state["matchId"]="fixture-second-match";await session.EnterRoomAsync("CREATE_ROOM");await session.StartGameAsync();await Until(()=>Controller!=null,"second game");await Task.Delay(100);
            int count=leaves;Controller.Leave();Check(Controller.transform.Find("Content/ForfeitConfirmation").gameObject.activeSelf&&leaves==count,"Forfeit opens confirmation only");Button("ForfeitConfirmation/Dialog/CancelButton").onClick.Invoke();Check(leaves==count,"Cancel sends no leave");Controller.Leave();Controller.ConfirmLeave();Controller.ConfirmLeave();Check(SceneManager.GetActiveScene().name=="GameScene","Waits for leave acknowledgement");await Until(()=>SceneManager.GetActiveScene().name=="LobbyScene","forfeit leave");Check(leaves==count+1,"Confirmed forfeit sent once");Check(wire,"Canonical request types, session and intent-only payloads");
            await session.LogoutAsync();return checks+" Game/Result checks passed (TCP localhost fixture).";
        }
        finally
        {
            Field("serverPort").SetValue(session,originalPort);Field("serverHost").SetValue(session,originalHost);stop.Cancel();peer?.Close();listener.Stop();
            if(server.IsCompleted)await server;
        }
    }
}
