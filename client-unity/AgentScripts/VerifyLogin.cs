// Run with Unity MCP run_script, entry VerifyLogin.Main. Uses loopback fixtures, no database or real accounts.
using System;
using System.IO;
using System.Net;
using System.Net.Sockets;
using System.Text;
using System.Threading;
using System.Threading.Tasks;
using Ludo.Network;
using Ludo.Controllers;
using Newtonsoft.Json.Linq;

public static class VerifyLogin
{
    static int checks;
    static void Check(bool condition, string message)
    { if (!condition) throw new Exception("FAIL: " + message); checks++; }

    public static async Task<string> Main()
    {
        checks = 0;
        Check(LoginController.Validate("", "x") != null, "Blank username rejected");
        Check(LoginController.Validate("name ", "x") != null, "Username whitespace rejected");
        Check(LoginController.Validate("player", new string('ệ', 25)) != null, "72-byte UTF8 limit enforced");
        Check(LoginController.Validate("player", new string('ệ', 24)) == null, "72-byte UTF8 boundary accepted");
        string payload = "{\"message\":\"Xin chào Việt Nam\"}";
        byte[] frame = FrameCodec.Encode(payload);
        Check(frame[0] == 0 && frame[1] == 0 && frame[3] == Encoding.UTF8.GetByteCount(payload), "Big endian byte length");
        Check(await FrameCodec.ReadAsync(new FragmentedStream(frame), CancellationToken.None) == payload, "Partial TCP reads");
        byte[] combined = new byte[frame.Length * 2];
        Buffer.BlockCopy(frame, 0, combined, 0, frame.Length); Buffer.BlockCopy(frame, 0, combined, frame.Length, frame.Length);
        using (var stream = new MemoryStream(combined))
            Check(await FrameCodec.ReadAsync(stream, CancellationToken.None) == payload && await FrameCodec.ReadAsync(stream, CancellationToken.None) == payload, "Coalesced frames");
        foreach (byte[] header in new[] { new byte[4], new byte[] {0,1,0,1}, new byte[] {127,255,255,255}, new byte[] {255,255,255,255} })
        {
            bool rejected = false;
            try { await FrameCodec.ReadAsync(new MemoryStream(header), CancellationToken.None); } catch (InvalidDataException) { rejected = true; }
            Check(rejected, "Invalid length rejected before allocation");
        }
        bool partial = false;
        try { await FrameCodec.ReadAsync(new MemoryStream(new byte[] {0,0,0,5,65}), CancellationToken.None); } catch (EndOfStreamException) { partial = true; }
        Check(partial, "Truncated frame rejected");
        bool utf8 = false;
        try { await FrameCodec.ReadAsync(new MemoryStream(new byte[] {0,0,0,1,255}), CancellationToken.None); } catch (DecoderFallbackException) { utf8 = true; }
        Check(utf8, "Invalid UTF8 rejected");
        using (var listener = new ListenerScope())
        using (var client = new NetworkClient())
        {
            Task server = Task.Run(async () =>
            {
                using (var peer = await listener.Listener.AcceptTcpClientAsync())
                {
                    var stream = peer.GetStream();
                    var request = JObject.Parse(await FrameCodec.ReadAsync(stream, CancellationToken.None));
                    Check((string)request["type"] == "LOGIN" && (string)request["data"]?["username"] == "fixture", "Canonical LOGIN payload");
                    byte[] ping = FrameCodec.Encode("{\"type\":\"PING\",\"requestId\":\"heartbeat\",\"data\":{\"timestamp\":123}}");
                    await stream.WriteAsync(ping, 0, ping.Length);
                    var pong = JObject.Parse(await FrameCodec.ReadAsync(stream, CancellationToken.None));
                    Check((string)pong["type"] == "PONG" && (long)pong["data"]["timestamp"] == 123 && (string)pong["requestId"] == "heartbeat", "Heartbeat echoes request id and data");
                    var response = new JObject { ["type"] = "ERROR", ["requestId"] = request["requestId"], ["success"] = false, ["error"] = new JObject { ["code"] = "INVALID_CREDENTIALS" } };
                    byte[] bytes = FrameCodec.Encode(response.ToString());
                    for (int i=0;i<bytes.Length;i+=3) await stream.WriteAsync(bytes,i,Math.Min(3,bytes.Length-i));
                    var next = JObject.Parse(await FrameCodec.ReadAsync(stream,CancellationToken.None));
                    var success = new JObject { ["type"]="LOGIN", ["requestId"]=next["requestId"], ["success"]=true,
                        ["data"]=new JObject { ["sessionId"]="fixture-session", ["profile"]=new JObject { ["playerId"]="fixture-player" } } };
                    bytes=FrameCodec.Encode(success.ToString()); await stream.WriteAsync(bytes,0,bytes.Length);
                    await Task.Delay(100);
                }
            });
            await client.ConnectAsync("127.0.0.1", listener.Port);
            var login = await client.RequestAsync("LOGIN",new JObject { ["username"]="fixture", ["password"]="fixture-only" });
            Check((string)login["error"]?["code"]=="INVALID_CREDENTIALS", "Error response correlated");
            var result = await client.RequestAsync("LOGIN",new JObject { ["username"]="fixture", ["password"]="fixture-only" });
            Check((string)result["data"]?["sessionId"]=="fixture-session", "LOGIN success correlated");
            await server;
            for(int i=0;i<20 && client.IsConnected;i++) await Task.Delay(10);
            Check(!client.IsConnected,"Remote disconnect detected");
        }
        return checks + " checks passed: framing, Unicode, input validation, heartbeat, LOGIN/ERROR correlation and disconnect.";
    }
    sealed class FragmentedStream : MemoryStream
    {
        public FragmentedStream(byte[] bytes) : base(bytes) { }
        public override Task<int> ReadAsync(byte[] buffer,int offset,int count,CancellationToken token) => base.ReadAsync(buffer,offset,Math.Min(2,count),token);
    }
    sealed class ListenerScope : IDisposable
    {
        public readonly TcpListener Listener = new TcpListener(IPAddress.Loopback,0);
        public int Port => ((IPEndPoint)Listener.LocalEndpoint).Port;
        public ListenerScope() { Listener.Start(); }
        public void Dispose() { Listener.Stop(); }
    }
}
