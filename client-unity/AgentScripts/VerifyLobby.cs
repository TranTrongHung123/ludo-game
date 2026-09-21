using System;
using System.Net;
using System.Net.Sockets;
using System.Reflection;
using System.Threading;
using System.Threading.Tasks;
using Ludo.Services;
using Ludo.Network;
using Ludo.Controllers;
using TMPro;
using Newtonsoft.Json.Linq;
using UnityEngine;
using UnityEngine.SceneManagement;
public static class VerifyLobby
{
    static int checks;
    static void Check(bool b,string n){if(!b)throw new Exception(n);checks++;}
    static async Task Until(Func<bool> p){for(int i=0;i<500;i++){if(p())return;await Task.Delay(20);}throw new Exception("Wait timed out");}
    public static async Task<string> Main()
    {
        checks=0;var session=NetworkSession.Instance;await Until(()=>session.State!=ConnectionState.Connecting);
        if(session.State!=ConnectionState.Disconnected)throw new Exception("Requires offline Login in Play Mode");
        var listener=new TcpListener(IPAddress.Loopback,0);listener.Start();
        var port=typeof(NetworkSession).GetField("serverPort",BindingFlags.NonPublic|BindingFlags.Instance);var host=typeof(NetworkSession).GetField("serverHost",BindingFlags.NonPublic|BindingFlags.Instance);
        var oldPort=port.GetValue(session);var oldHost=host.GetValue(session);
        var finish=new TaskCompletionSource<bool>();var drop=new TaskCompletionSource<bool>();
        int joins=0,creates=0,rejects=0,reconnects=0;bool tokenValid=true;
        var players=new JArray();for(int i=0;i<20;i++)players.Add(new JObject{["playerId"]="p"+i,["displayName"]="Người chơi "+i,["totalScore"]=i+0.5,["firstPlaceCount"]=i,["presenceState"]=i%2==0?"IDLE":"PLAYING"});
        Func<string,JObject> invitation=id=>new JObject{["type"]="INVITE_PLAYER",["data"]=new JObject{["invitationId"]=id,["roomId"]="room-fixture",["inviterDisplayName"]="Bạn bè",["expiresAtEpochMillis"]=DateTimeOffset.UtcNow.ToUnixTimeMilliseconds()+60000}};
        Task server=Task.Run(async()=>
        {
            for(int connection=0;connection<2;connection++)
            using(var peer=await listener.AcceptTcpClientAsync())
            {
                var stream=peer.GetStream();
                while(true)
                {
                    var read=FrameCodec.ReadAsync(stream,CancellationToken.None);
                    if(connection==0 && await Task.WhenAny(read,drop.Task)==drop.Task)break;
                    if(connection==1 && await Task.WhenAny(read,finish.Task)==finish.Task)break;
                    var request=JObject.Parse(await read);string type=(string)request["type"];
                    if(type!="LOGIN"&&type!="RECONNECT")tokenValid&=(string)request["sessionId"]=="fixture-token";
                    var response=new JObject{["type"]=type,["requestId"]=request["requestId"],["success"]=true,["data"]=new JObject()};
                    switch(type)
                    {
                        case "LOGIN":response["data"]=new JObject{["sessionId"]="fixture-token",["profile"]=new JObject{["playerId"]="p0",["displayName"]="Người chơi 0",["username"]="fixture",["totalScore"]=0.5,["firstPlaceCount"]=0}};break;
                        case "GET_ONLINE_PLAYERS":response["type"]="ONLINE_PLAYERS_UPDATED";response["data"]=new JObject{["players"]=players.DeepClone()};break;
                        case "JOIN_ROOM":Interlocked.Increment(ref joins);await Task.Delay(200);response["type"]="ERROR";response["success"]=false;response["error"]=new JObject{["code"]="ROOM_FULL"};break;
                        case "CREATE_ROOM":Interlocked.Increment(ref creates);await Task.Delay(200);response["data"]=new JObject{["room"]=new JObject{["roomId"]="room-fixture",["state"]="WAITING",["hostPlayerId"]="p0",["players"]=new JArray()}};break;
                        case "REJECT_INVITE":Interlocked.Increment(ref rejects);break;
                        case "RECONNECT":Interlocked.Increment(ref reconnects);tokenValid&=(string)request["data"]?["sessionId"]=="fixture-token";response["type"]="RECONNECT_RESULT";response["data"]=new JObject{["restored"]=true,["presenceState"]="IDLE"};break;
                    }
                    byte[] bytes=FrameCodec.Encode(response.ToString());await stream.WriteAsync(bytes,0,bytes.Length);
                    if(type=="GET_ONLINE_PLAYERS"&&connection==0){bytes=FrameCodec.Encode(invitation("invite-first").ToString());await stream.WriteAsync(bytes,0,bytes.Length);}
                    if(type=="REJECT_INVITE"){bytes=FrameCodec.Encode(invitation("invite-new").ToString());await stream.WriteAsync(bytes,0,bytes.Length);}
                }
            }
        });
        try
        {
            port.SetValue(session,((IPEndPoint)listener.LocalEndpoint).Port);host.SetValue(session,"127.0.0.1");await session.ConnectAsync();await session.LoginAsync("fixture","fixture");
            SceneManager.LoadScene("LobbyScene");await Until(()=>UnityEngine.Object.FindFirstObjectByType<LobbyController>()!=null&&session.OnlinePlayers.Count==20);
            var lobby=UnityEngine.Object.FindFirstObjectByType<LobbyController>();var root=lobby.transform.Find("Content");await Until(()=>root.Find("OnlineCard/PlayerScroll/Viewport/Rows").childCount==20);
            Check(root.Find("Header/Welcome").GetComponent<TMP_Text>().text.Contains("Người chơi 0"),"Profile rendering");
            Check(root.Find("OnlineCard/PlayerScroll/Viewport/Rows").GetChild(0).Find("PlayerName").GetComponent<TMP_Text>().text.Contains("(Bạn)"),"Current player marker");
            Canvas.ForceUpdateCanvases();Check(((RectTransform)root.Find("OnlineCard/PlayerScroll/Viewport/Rows")).rect.height>363,"Scrollable long list");
            await Until(()=>root.Find("InvitationCard").gameObject.activeSelf);lobby.RejectInvitation();await Until(()=>rejects==1&&(string)session.Invitation?["invitationId"]=="invite-new");Check(true,"Replacement invitation preserved");
            lobby.JoinRoom();Check(joins==0,"Blank room does not send");root.Find("RoomCard/RoomCodeField").GetComponent<TMP_InputField>().text="room-fixture";lobby.JoinRoom();lobby.JoinRoom();await Until(()=>root.Find("Feedback").GetComponent<TMP_Text>().text=="Phòng đã đủ 4 người.");Check(joins==1,"Join error and duplicate suppression");
            lobby.OpenRanking();Check(root.Find("Feedback").GetComponent<TMP_Text>().text.Contains("Bảng xếp hạng"),"Deferred Ranking");lobby.OpenHistory();Check(root.Find("Feedback").GetComponent<TMP_Text>().text.Contains("Lịch sử"),"Deferred History");
            drop.TrySetResult(true);await Until(()=>reconnects==1&&session.State==ConnectionState.Connected);Check(session.SessionId=="fixture-token","Reconnect retains authenticated session");
            lobby.CreateRoom();lobby.CreateRoom();await Until(()=>session.Room!=null);Check(creates==1&&!root.Find("RoomCard/CreateButton").GetComponent<UnityEngine.UI.Button>().interactable,"Create stores authoritative room and locks repeated room actions");
            await Task.Delay(100);lobby.Logout();await Until(()=>SceneManager.GetActiveScene().name=="LoginScene");Check(session.SessionId==null&&session.Profile==null,"Logout cleared state and returned Login");Check(tokenValid,"Authenticated requests carry correct session token");
            return checks+" Lobby checks passed";
        }
        finally{finish.TrySetResult(true);drop.TrySetResult(true);listener.Stop();port.SetValue(session,oldPort);host.SetValue(session,oldHost);if(server.IsCompleted)await server;}
    }
}
