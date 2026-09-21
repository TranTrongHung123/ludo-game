// Isolated Play Mode socket checks. Open RegisterScene with no server connected first.
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
using UnityEngine.SceneManagement;

public static class VerifyRegister
{
    static int checks;
    static void Check(bool condition,string name) { if(!condition)throw new Exception("FAIL: "+name); checks++; }
    static async Task Until(Func<bool> condition)
    { for(int i=0;i<300;i++){if(condition())return;await Task.Delay(20);}throw new Exception("Timed out waiting for UI."); }
    public static async Task<string> Main()
    {
        checks=0;
        Check(RegisterController.Validate("player","Tên Việt","secret","different")!=null,"Confirmation mismatch");
        Check(RegisterController.Validate("player"," ","secret","secret")!=null,"Blank display name");
        Check(RegisterController.Validate("player",new string('a',101),"secret","secret")!=null,"Display name limit");
        Check(RegisterController.Validate("player"," Name","secret","secret")!=null,"Display name whitespace");
        Check(RegisterController.Validate("player","Tên Việt",new string('ệ',25),new string('ệ',25))!=null,"Password UTF8 byte limit");
        Check(RegisterController.Validate("player",new string('a',100),new string('ệ',24),new string('ệ',24))==null,"Accepted boundaries");
        if(!Application.isPlaying)throw new Exception("Requires Play Mode.");
        var session=NetworkSession.Instance;
        await Until(()=>session.State!=ConnectionState.Connecting);
        if(session.State!=ConnectionState.Disconnected)throw new Exception("Requires disconnected session.");
        var controller=UnityEngine.Object.FindFirstObjectByType<RegisterController>();
        var card=controller.transform.Find("Content/RegisterCard");
        var username=card.Find("UsernameField").GetComponent<TMP_InputField>();
        var display=card.Find("DisplayNameField").GetComponent<TMP_InputField>();
        var password=card.Find("PasswordField").GetComponent<TMP_InputField>();
        var confirm=card.Find("ConfirmPasswordField").GetComponent<TMP_InputField>();
        var submit=card.Find("RegisterButton").GetComponent<UnityEngine.UI.Button>();
        var back=card.Find("LoginButton").GetComponent<UnityEngine.UI.Button>();
        var feedback=card.Find("Feedback").GetComponent<TMP_Text>();
        Check(!submit.interactable,"Offline disables registration");
        controller.TogglePassword();Check(password.contentType==TMP_InputField.ContentType.Standard && confirm.contentType==TMP_InputField.ContentType.Standard,"Reveal both passwords");
        controller.TogglePassword();Check(password.contentType==TMP_InputField.ContentType.Password && confirm.contentType==TMP_InputField.ContentType.Password,"Mask both passwords");
        var listener=new TcpListener(IPAddress.Loopback,0);listener.Start();
        var portField=typeof(NetworkSession).GetField("serverPort",BindingFlags.NonPublic|BindingFlags.Instance);
        var hostField=typeof(NetworkSession).GetField("serverHost",BindingFlags.NonPublic|BindingFlags.Instance);
        var oldPort=portField.GetValue(session);var oldHost=hostField.GetValue(session);
        var finish=new TaskCompletionSource<bool>(TaskCreationOptions.RunContinuationsAsynchronously);
        int requests=0;bool wireValid=true;
        Task fixture=Task.Run(async()=>
        {
            using(var peer=await listener.AcceptTcpClientAsync())
            {
                var stream=peer.GetStream();
                for(int i=0;i<3;i++)
                {
                    var request=JObject.Parse(await FrameCodec.ReadAsync(stream,CancellationToken.None));Interlocked.Increment(ref requests);
                    var data=(JObject)request["data"];
                    wireValid &= (string)request["type"]=="REGISTER" && data.Count==3 && (string)data["username"]=="fixture-register" && (string)data["displayName"]=="Tên Việt" && (string)data["password"]=="fixture-password";
                    await Task.Delay(200);
                    var response=new JObject{["type"]=i==0?"ERROR":"REGISTER",["requestId"]=request["requestId"],["success"]=i!=0};
                    if(i==0)response["error"]=new JObject{["code"]="USERNAME_ALREADY_EXISTS"};
                    else if(i==2)response["data"]=new JObject{["profile"]=new JObject{["playerId"]="fixture-id",["username"]="fixture-register",["displayName"]="Tên Việt"}};
                    byte[] bytes=FrameCodec.Encode(response.ToString());await stream.WriteAsync(bytes,0,bytes.Length);
                }
                await finish.Task;
            }
        });
        try
        {
            portField.SetValue(session,((IPEndPoint)listener.LocalEndpoint).Port);hostField.SetValue(session,"127.0.0.1");
            await session.ConnectAsync();Check(submit.interactable,"Connected enables registration");
            username.text="fixture-register";display.text="Tên Việt";password.text="fixture-password";confirm.text="mismatch";controller.Submit();
            Check(requests==0 && feedback.text=="Mật khẩu xác nhận không khớp.","Mismatch sends no network request");
            confirm.text=password.text;controller.Submit();controller.Submit();
            Check(!submit.interactable&&!back.interactable&&!display.interactable,"Pending locks inputs and navigation");
            await Until(()=>submit.interactable);
            Check(requests==1 && feedback.text.StartsWith("Tên đăng nhập đã được sử dụng."),"Duplicate suppression and server error");
            Check(password.text==""&&confirm.text=="","Clear both passwords after response");
            password.text=confirm.text="fixture-password";controller.Submit();await Until(()=>submit.interactable);
            Check(SceneManager.GetActiveScene().name=="RegisterScene"&&session.SessionId==null,"Malformed success cannot navigate/authenticate");
            password.text=confirm.text="fixture-password";controller.Submit();
            await Until(()=>SceneManager.GetActiveScene().name=="LoginScene" && UnityEngine.Object.FindFirstObjectByType<LoginController>()!=null);
            await Until(()=>UnityEngine.Object.FindFirstObjectByType<LoginController>().transform.Find("Content/LoginCard/UsernameField").GetComponent<TMP_InputField>().text=="fixture-register");
            var login=UnityEngine.Object.FindFirstObjectByType<LoginController>();var loginCard=login.transform.Find("Content/LoginCard");
            Check(loginCard.Find("UsernameField").GetComponent<TMP_InputField>().text=="fixture-register"&&loginCard.Find("Feedback").GetComponent<TMP_Text>().text.StartsWith("Đăng ký thành công."),"Success returns to Login with username and notice");
            Check(NetworkSession.Instance==session && session.State==ConnectionState.Connected && session.SessionId==null && session.Profile==null,"Same socket, registration does not log in");
            Check(wireValid&&requests==3,"Canonical REGISTER payload only");
            loginCard.Find("RegisterButton").GetComponent<UnityEngine.UI.Button>().onClick.Invoke();
            await Until(()=>SceneManager.GetActiveScene().name=="RegisterScene" && UnityEngine.Object.FindFirstObjectByType<RegisterController>()!=null);
            await Until(()=>UnityEngine.Object.FindFirstObjectByType<RegisterController>().transform.Find("Content/RegisterCard/UsernameField").GetComponent<TMP_InputField>().text=="fixture-register");
            var returned=UnityEngine.Object.FindFirstObjectByType<RegisterController>();
            Check(returned.transform.Find("Content/RegisterCard/UsernameField").GetComponent<TMP_InputField>().text=="fixture-register","Login to Register preserves username");
            returned.OpenLogin();await Until(()=>SceneManager.GetActiveScene().name=="LoginScene");
            Check(NetworkSession.Instance==session,"Back to Login reuses session object");
            return checks+" checks passed: validation, visibility, TCP REGISTER, pending, duplicate name, malformed response and scene round trips.";
        }
        finally
        {
            finish.TrySetResult(true);listener.Stop();portField.SetValue(session,oldPort);hostField.SetValue(session,oldHost);
            session.AuthUsername="";session.AuthNotice="";
            if(fixture.IsCompleted)await fixture;
        }
    }
}
