// Play Mode integration check with an isolated loopback TCP fixture, never a production account.
using System;
using System.Net;
using System.Net.Sockets;
using System.Reflection;
using System.Threading;
using System.Threading.Tasks;
using Ludo.Controllers;
using Ludo.Network;
using Ludo.Services;
using Newtonsoft.Json.Linq;
using TMPro;
using UnityEngine;

public static class VerifyLoginUi
{
    static void Check(bool value, string name) { if (!value) throw new Exception("FAIL: " + name); }
    public static async Task<string> Main()
    {
        if (!Application.isPlaying) throw new Exception("Enter Play Mode first.");
        var session = NetworkSession.Instance;
        for (int i=0;i<300 && session.State==ConnectionState.Connecting;i++) await Task.Delay(20);
        if (session.State != ConnectionState.Disconnected) throw new Exception("Requires disconnected session.");
        var controller = UnityEngine.Object.FindFirstObjectByType<LoginController>();
        var card = controller.transform.Find("Content/LoginCard");
        var username = card.Find("UsernameField").GetComponent<TMP_InputField>();
        var password = card.Find("PasswordField").GetComponent<TMP_InputField>();
        var submit = card.Find("LoginButton").GetComponent<UnityEngine.UI.Button>();
        var feedback = card.Find("Feedback").GetComponent<TMP_Text>();
        var listener = new TcpListener(IPAddress.Loopback,0); listener.Start();
        var portField = typeof(NetworkSession).GetField("serverPort",BindingFlags.NonPublic|BindingFlags.Instance);
        var hostField = typeof(NetworkSession).GetField("serverHost",BindingFlags.NonPublic|BindingFlags.Instance);
        var oldPort=portField.GetValue(session); var oldHost=hostField.GetValue(session);
        var finish=new TaskCompletionSource<bool>(TaskCreationOptions.RunContinuationsAsynchronously);
        int requests=0;
        Task fixture=Task.Run(async () =>
        {
            using(var peer=await listener.AcceptTcpClientAsync())
            {
                var stream=peer.GetStream();
                for(int i=0;i<2;i++)
                {
                    var request=JObject.Parse(await FrameCodec.ReadAsync(stream,CancellationToken.None)); requests++;
                    await Task.Delay(250);
                    var response=new JObject { ["type"]=i==0?"ERROR":"LOGIN",["requestId"]=request["requestId"],["success"]=i!=0 };
                    if(i==0) response["error"]=new JObject { ["code"]="INVALID_CREDENTIALS" };
                    else response["data"]=new JObject { ["sessionId"]="isolated-fixture",["profile"]=new JObject { ["playerId"]="isolated-fixture",["displayName"]="Fixture" } };
                    byte[] bytes=FrameCodec.Encode(response.ToString());await stream.WriteAsync(bytes,0,bytes.Length);
                }
                await finish.Task;
            }
        });
        try
        {
            portField.SetValue(session,((IPEndPoint)listener.LocalEndpoint).Port);hostField.SetValue(session,"127.0.0.1");
            await session.ConnectAsync();Check(submit.interactable,"Connected enables login");
            username.text="fixture";password.text="wrong-fixture";controller.Submit();controller.Submit();
            Check(!submit.interactable && !username.interactable,"Pending disables controls");
            for(int i=0;i<100&&!submit.interactable;i++)await Task.Delay(20);
            Check(requests==1,"Double submit sends only one LOGIN");
            Check(feedback.text=="Tên đăng nhập hoặc mật khẩu không đúng." && password.text=="","Localized server error and cleared password");
            password.text="correct-fixture";controller.Submit();
            for(int i=0;i<100&&session.SessionId==null;i++)await Task.Delay(20);
            Check(session.SessionId=="isolated-fixture" && session.Profile!=null,"Successful response persists session/profile");
            Check(feedback.text.StartsWith("Đăng nhập thành công.")&&!submit.interactable&&password.text=="","Missing lobby is handled without a fake scene or resubmission");
            return "6 Play Mode checks passed: real socket status, pending, duplicate suppression, auth errors, stored session and deferred navigation.";
        }
        finally
        {
            finish.TrySetResult(true);listener.Stop();portField.SetValue(session,oldPort);hostField.SetValue(session,oldHost);
            username.text="";password.text="";
        }
    }
}
