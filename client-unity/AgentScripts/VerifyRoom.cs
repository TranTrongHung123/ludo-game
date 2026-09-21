using System;
using System.Net;
using System.Net.Sockets;
using System.Reflection;
using System.Threading;
using System.Threading.Tasks;
using Ludo.Controllers;
using Ludo.Services;
using Ludo.Network;
using Newtonsoft.Json.Linq;
using TMPro;
using UnityEngine;
using UnityEngine.SceneManagement;
public static class VerifyRoom
{
    static int checks;
    static void Check(bool b,string n){if(!b)throw new Exception(n);checks++;}
    static async Task Until(Func<bool> f){for(int i=0;i<500;i++){if(f())return;await Task.Delay(20);}throw new Exception("Timed out");}
    public static async Task<string> Main()
    {
        checks=0;var s=NetworkSession.Instance;await Until(()=>s.State!=ConnectionState.Connecting);if(s.State!=ConnectionState.Disconnected)throw new Exception("Requires offline Login Play Mode");
        var players=new JArray{new JObject{["playerId"]="p0",["displayName"]="Chủ phòng",["slotIndex"]=0,["color"]="RED",["ready"]=false,["presenceState"]="IN_ROOM"},new JObject{["playerId"]="p1",["displayName"]="Bạn bè",["slotIndex"]=1,["color"]="BLUE",["ready"]=true,["presenceState"]="IN_ROOM"}};
        var room=new JObject{["roomId"]="room-fixture",["hostPlayerId"]="p0",["state"]="WAITING",["players"]=players};
        Check(!RoomController.CanStart(room,"p0"),"Host must ready");players[0]["ready"]=true;Check(RoomController.CanStart(room,"p0"),"All ready host can start");Check(!RoomController.CanStart(room,"p1"),"Nonhost cannot start");players[1]["presenceState"]="DISCONNECTED";Check(!RoomController.CanStart(room,"p0"),"Disconnected blocks start");players[1]["presenceState"]="IN_ROOM";players[0]["ready"]=false;
        var listener=new TcpListener(IPAddress.Loopback,0);listener.Start();var port=typeof(NetworkSession).GetField("serverPort",BindingFlags.NonPublic|BindingFlags.Instance);var host=typeof(NetworkSession).GetField("serverHost",BindingFlags.NonPublic|BindingFlags.Instance);var oldPort=port.GetValue(s);var oldHost=host.GetValue(s);var done=new TaskCompletionSource<bool>();int ready=0,unready=0,invites=0,starts=0,leaves=0;bool wire=true;
        Task server=Task.Run(async()=>{using(var peer=await listener.AcceptTcpClientAsync()){var stream=peer.GetStream();while(true){var read=FrameCodec.ReadAsync(stream,CancellationToken.None);if(await Task.WhenAny(read,done.Task)==done.Task)break;var request=JObject.Parse(await read);string type=(string)request["type"];var response=new JObject{["type"]=type,["requestId"]=request["requestId"],["success"]=true,["data"]=new JObject()};if(type!="LOGIN")wire&=(string)request["sessionId"]=="token";switch(type){
        case "LOGIN":response["data"]=new JObject{["sessionId"]="token",["profile"]=new JObject{["playerId"]="p0",["displayName"]="Chủ phòng",["totalScore"]=10,["firstPlaceCount"]=1}};break;
        case "CREATE_ROOM":response["data"]=new JObject{["room"]=room.DeepClone()};break;
        case "GET_ONLINE_PLAYERS":response["type"]="ONLINE_PLAYERS_UPDATED";response["data"]=new JObject{["players"]=new JArray{new JObject{["playerId"]="p2",["displayName"]="Người được mời",["totalScore"]=3,["presenceState"]="IDLE"},new JObject{["playerId"]="p3",["displayName"]="Đang chơi",["totalScore"]=2,["presenceState"]="PLAYING"}}};break;
        case "READY":ready++;wire&=(bool?)request["data"]?["ready"]==true;players[0]["ready"]=true;await Task.Delay(150);response["data"]=new JObject{["room"]=room.DeepClone()};break;
        case "UNREADY":unready++;wire&=(bool?)request["data"]?["ready"]==false;players[0]["ready"]=false;response["data"]=new JObject{["room"]=room.DeepClone()};break;
        case "INVITE_PLAYER":invites++;wire&=(string)request["data"]?["playerId"]=="p2"&&(string)request["data"]?["roomId"]=="room-fixture";break;
        case "START_GAME":starts++;response["data"]=new JObject{["roomId"]="room-fixture",["state"]="PLAYING"};break;
        case "LEAVE_ROOM":leaves++;break;
        }byte[] bytes=FrameCodec.Encode(response.ToString());await stream.WriteAsync(bytes,0,bytes.Length);if(type=="READY"){bytes=FrameCodec.Encode(new JObject{["type"]="ROOM_UPDATED",["data"]=new JObject{["room"]=room.DeepClone()}}.ToString());await stream.WriteAsync(bytes,0,bytes.Length);}}}});
        try
        {
            port.SetValue(s,((IPEndPoint)listener.LocalEndpoint).Port);host.SetValue(s,"127.0.0.1");await s.ConnectAsync();await s.LoginAsync("fixture","fixture");await s.EnterRoomAsync("CREATE_ROOM");SceneManager.LoadScene("RoomScene");await Until(()=>UnityEngine.Object.FindFirstObjectByType<RoomController>()!=null&&s.OnlinePlayers.Count==2);var c=UnityEngine.Object.FindFirstObjectByType<RoomController>();var root=c.transform.Find("Content");await Until(()=>root.Find("InvitePanel/Scroll/Viewport/Rows").childCount==1);
            Check(root.Find("Slot2/PlayerName").GetComponent<TMP_Text>().text=="Chưa có người chơi","Empty slot");Check(root.Find("Slot0/HostBadge").gameObject.activeSelf,"Host badge");Check(!root.Find("StartButton").GetComponent<UnityEngine.UI.Button>().interactable,"Initial start disabled");Check(root.Find("InvitePanel/Scroll/Viewport/Rows").childCount==1,"Only idle invited");
            c.Invite("p2");await Until(()=>invites==1&&root.Find("Feedback").GetComponent<TMP_Text>().text.StartsWith("Đã gửi"));Check(true,"Invite request");c.ToggleReady();c.ToggleReady();await Until(()=>root.Find("StartButton").GetComponent<UnityEngine.UI.Button>().interactable);Check(ready==1,"Ready response and duplicate guard");c.ToggleReady();await Until(()=>unready==1&&root.Find("ReadyButton/Label").GetComponent<TMP_Text>().text=="Sẵn sàng");Check(true,"Unready authoritative");
            c.Leave();await Until(()=>SceneManager.GetActiveScene().name=="LobbyScene");Check(leaves==1&&s.Room==null,"Leave acknowledged before Lobby");
            await s.EnterRoomAsync("CREATE_ROOM");await Until(()=>SceneManager.GetActiveScene().name=="RoomScene");await Task.Delay(100);c=UnityEngine.Object.FindFirstObjectByType<RoomController>();c.ToggleReady();await Until(()=>RoomController.CanStart(s.Room,"p0"));await Task.Delay(50);c.StartMatch();await Until(()=>s.GameState!=null);Check(starts==1,"Start game request");await Task.Delay(50);Check(!c.transform.Find("Content/ReadyButton").GetComponent<UnityEngine.UI.Button>().interactable,"No waiting actions after start");Check(wire,"Canonical authenticated payloads");return checks+" Room checks passed";
        }
        finally{done.TrySetResult(true);listener.Stop();port.SetValue(s,oldPort);host.SetValue(s,oldHost);if(server.IsCompleted)await server;}
    }
}
