using System;
using System.IO;
using System.Linq;
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

public static class VerifyChat
{
    static int checks, chatRequests;
    static bool wire = true;
    static TcpListener listener;
    static TcpClient peer;
    static NetworkStream stream;
    static readonly SemaphoreSlim writes = new SemaphoreSlim(1, 1);
    static JObject state, room;
    static CancellationTokenSource stop;
    static NetworkSession session;
    static object originalPort, originalHost;
    static Task server;

    static FieldInfo Field(string name) => typeof(NetworkSession).GetField(name, BindingFlags.Instance | BindingFlags.NonPublic);
    static void Check(bool value, string name) { if (!value) throw new Exception("Check failed: " + name); checks++; }
    static async Task Until(Func<bool> value, string name)
    {
        for (int i = 0; i < 500; i++) { if (value()) return; await Task.Delay(20); }
        throw new Exception("Timed out: " + name);
    }

    static GameController Controller => UnityEngine.Object.FindFirstObjectByType<GameController>();
    static long Now => DateTimeOffset.UtcNow.ToUnixTimeMilliseconds();

    static JObject Snapshot()
    {
        var members = new JArray();
        for (int s = 0; s < 4; s++)
        {
            var pieces = new JArray();
            for (int i = 0; i < 4; i++)
                pieces.Add(new JObject { ["pieceId"] = "p" + s + "-" + i, ["ownerPlayerId"] = "p" + s, ["color"] = "RED", ["state"] = "IN_YARD", ["stepCount"] = -1, ["slowed"] = false, ["shielded"] = false });
            members.Add(new JObject { ["playerId"] = "p" + s, ["displayName"] = "Người chơi " + s, ["slotIndex"] = s, ["color"] = "RED", ["presenceState"] = "PLAYING", ["matchStatus"] = "ACTIVE", ["rank"] = null, ["scoreEarned"] = 0, ["pieces"] = pieces });
        }
        return new JObject
        {
            ["roomId"] = "fixture-room",
            ["matchId"] = "fixture-match",
            ["roomState"] = "PLAYING",
            ["currentPlayerId"] = "p0",
            ["currentSlot"] = 0,
            ["turnState"] = "WAITING_FOR_ROLL",
            ["diceValue"] = null,
            ["validPieceIds"] = new JArray(),
            ["phaseDurationMillis"] = 8000,
            ["serverDeadlineEpochMillis"] = Now + 8000,
            ["participants"] = members,
            ["specialCells"] = new JArray(),
            ["stateVersion"] = 1
        };
    }

    static async Task Send(JObject message)
    {
        byte[] bytes = FrameCodec.Encode(message.ToString());
        await writes.WaitAsync();
        try { await stream.WriteAsync(bytes, 0, bytes.Length); }
        finally { writes.Release(); }
    }

    static Task Event(string type, JObject data) => Send(new JObject { ["type"] = type, ["data"] = data.DeepClone() });

    static async Task Serve()
    {
        while (!stop.IsCancellationRequested)
        {
            try
            {
                peer = await listener.AcceptTcpClientAsync();
                stream = peer.GetStream();
                while (!stop.IsCancellationRequested)
                {
                    var request = JObject.Parse(await FrameCodec.ReadAsync(stream, stop.Token));
                    string type = (string)request["type"];
                    var response = new JObject { ["type"] = type, ["requestId"] = request["requestId"], ["success"] = true, ["data"] = new JObject() };
                    switch (type)
                    {
                        case "LOGIN":
                            response["data"] = new JObject { ["sessionId"] = "fixture-token", ["profile"] = new JObject { ["playerId"] = "p0", ["displayName"] = "Người kiểm thử", ["totalScore"] = 0, ["firstPlaceCount"] = 0 } };
                            break;
                        case "CREATE_ROOM":
                            response["data"] = new JObject { ["room"] = room.DeepClone() };
                            break;
                        case "START_GAME":
                            response["data"] = state.DeepClone();
                            break;
                        case "CHAT_MESSAGE":
                            chatRequests++;
                            string msg = (string)request["data"]?["message"];
                            wire &= (string)request["data"]?["roomId"] == "fixture-room" && !string.IsNullOrEmpty(msg);
                            response["data"] = new JObject
                            {
                                ["messageId"] = "msg-" + chatRequests,
                                ["roomId"] = "fixture-room",
                                ["senderId"] = "p0",
                                ["senderDisplayName"] = "Người kiểm thử",
                                ["senderSlotIndex"] = 0,
                                ["senderColor"] = "RED",
                                ["message"] = msg,
                                ["timestampEpochMillis"] = Now
                            };
                            break;
                        case "RECONNECT":
                            response["type"] = "RECONNECT_RESULT";
                            response["data"] = new JObject { ["restored"] = true, ["room"] = room.DeepClone(), ["gameState"] = state.DeepClone() };
                            break;
                        default:
                            break;
                    }
                    await Send(response);
                    if (type == "CHAT_MESSAGE")
                    {
                        // Simulate server broadcasting the chat message to room members
                        await Event("CHAT_MESSAGE", (JObject)response["data"]);
                    }
                }
            }
            catch (Exception e) when (e is IOException || e is SocketException || e is ObjectDisposedException || e is OperationCanceledException)
            {
                if (stop.IsCancellationRequested) return;
            }
        }
    }

    public static async Task<string> Main()
    {
        if (!UnityEditor.EditorApplication.isPlaying) throw new Exception("Requires Play Mode with offline Login scene.");
        checks = chatRequests = 0;
        wire = true;
        session = NetworkSession.Instance;
        await Until(() => session != null && session.State != ConnectionState.Connecting, "offline session");

        state = Snapshot();
        room = new JObject { ["roomId"] = "fixture-room", ["hostPlayerId"] = "p0", ["state"] = "PLAYING", ["players"] = new JArray() };

        listener = new TcpListener(IPAddress.Loopback, 0);
        listener.Start();
        stop = new CancellationTokenSource();
        originalPort = Field("serverPort").GetValue(session);
        originalHost = Field("serverHost").GetValue(session);
        Field("serverPort").SetValue(session, ((IPEndPoint)listener.LocalEndpoint).Port);
        Field("serverHost").SetValue(session, "127.0.0.1");
        server = Task.Run(Serve);

        try
        {
            await session.ConnectAsync();
            await session.LoginAsync("fixture", "fixture");
            await session.EnterRoomAsync("CREATE_ROOM");
            await session.StartGameAsync();
            SceneManager.LoadScene("GameScene");

            await Until(() => Controller != null, "GameScene loaded");
            await Task.Delay(150);

            var chatPanel = Controller.transform.Find("Content/ChatPanel");
            Check(chatPanel != null, "ChatPanel exists");

            var chatInput = chatPanel.Find("ChatInput")?.GetComponent<TMP_InputField>();
            Check(chatInput != null, "TMP_InputField on ChatInput exists");
            Check(chatInput.interactable, "ChatInput is interactable when connected");
            Check(!chatInput.richText, "ChatInput richText is disabled to avoid IME underline tags");

            var sendButton = chatPanel.Find("SendButton")?.GetComponent<UnityEngine.UI.Button>();
            Check(sendButton != null, "SendButton exists");
            Check(sendButton.interactable, "SendButton is interactable when connected");

            var chatLog = chatPanel.Find("ChatLog")?.GetComponent<TMP_Text>();
            Check(chatLog != null, "ChatLog TMP_Text exists");

            // Test 1: Send empty/blank message -> should be suppressed
            chatInput.text = "   ";
            Controller.SendChat();
            await Task.Delay(100);
            Check(chatRequests == 0, "Blank message is suppressed");

            // Test 2: Send valid message
            chatInput.text = "Xin chào cả phòng!";
            Controller.SendChat();
            await Until(() => chatRequests == 1, "Chat request sent to server");
            Check(wire, "Chat request wire payload verified");
            Check(chatInput.text == "", "ChatInput cleared after sending");

            // Test 2b: Send message with accidental IME <u> tags -> tags stripped
            chatInput.text = "ok đục <u>rồi</u>";
            Controller.SendChat();
            await Until(() => chatRequests == 2, "IME sanitized chat request sent");
            Check(wire, "IME sanitized chat request wire payload verified");
            Check(chatInput.text == "", "ChatInput cleared after sending IME text");

            // Test 3: Verify received chat in ChatLog
            await Until(() => chatLog.text.Contains("Xin chào cả phòng!"), "ChatLog rendered sent message");
            Check(chatLog.text.Contains("Người kiểm thử"), "ChatLog displays sender name");
            Check(chatLog.text.Contains("ok đục rồi"), "ChatLog displays sanitized message");
            Check(!chatLog.text.Contains("<u>"), "ChatLog does not contain raw <u> tags");

            // Test 4: Incoming chat message from opponent
            await Event("CHAT_MESSAGE", new JObject
            {
                ["messageId"] = "msg-opp-1",
                ["roomId"] = "fixture-room",
                ["senderId"] = "p1",
                ["senderDisplayName"] = "Đối thủ xanh",
                ["senderSlotIndex"] = 1,
                ["senderColor"] = "BLUE",
                ["message"] = "Chúc chơi vui vẻ!",
                ["timestampEpochMillis"] = Now
            });
            await Until(() => chatLog.text.Contains("Chúc chơi vui vẻ!"), "Opponent message rendered in ChatLog");
            Check(chatLog.text.Contains("Đối thủ xanh"), "Opponent name rendered in ChatLog");

            // Test 5: Foreign room chat message should be ignored
            await Event("CHAT_MESSAGE", new JObject
            {
                ["messageId"] = "msg-foreign",
                ["roomId"] = "other-room",
                ["senderId"] = "p9",
                ["senderDisplayName"] = "Người lạ",
                ["senderSlotIndex"] = 2,
                ["senderColor"] = "YELLOW",
                ["message"] = "Tin nhắn phòng khác",
                ["timestampEpochMillis"] = Now
            });
            await Task.Delay(100);
            Check(!chatLog.text.Contains("Tin nhắn phòng khác"), "Foreign room chat message ignored");

            // Test 6: Offline disables chat interactability
            peer.Close();
            await Until(() => session.State != ConnectionState.Connected, "disconnected");
            Check(!sendButton.interactable, "SendButton disabled when disconnected");
            Check(!chatInput.interactable, "ChatInput disabled when disconnected");

            return "VerifyChat passed: " + checks + " checks passed successfully.";
        }
        finally
        {
            stop.Cancel();
            listener.Stop();
            Field("serverPort").SetValue(session, originalPort);
            Field("serverHost").SetValue(session, originalHost);
        }
    }
}
