using System;
using System.Collections.Concurrent;
using System.Threading.Tasks;
using Ludo.Network;
using Newtonsoft.Json.Linq;
using UnityEngine;

namespace Ludo.Services
{
    public enum ConnectionState { Disconnected, Connecting, Connected }

    public sealed partial class NetworkSession : MonoBehaviour
    {
        public static NetworkSession Instance { get; private set; }
        [SerializeField] private string serverHost = "127.0.0.1";
        [SerializeField] private int serverPort = 5555;
        private readonly ConcurrentQueue<Action> mainThread = new ConcurrentQueue<Action>();
        private NetworkClient client;
        private bool disposed;
        public ConnectionState State { get; private set; }
        public string SessionId { get; private set; }
        public JObject Profile { get; private set; }
        public string AuthUsername { get; set; } = "";
        public string AuthNotice { get; set; } = "";
        public event Action<ConnectionState> StateChanged;

        private void Awake()
        {
            if (Instance != null && Instance != this) { Destroy(gameObject); return; }
            Instance = this;
            Application.runInBackground = true;
            DontDestroyOnLoad(gameObject);
            string host = Environment.GetEnvironmentVariable("SERVER_HOST");
            if (!string.IsNullOrWhiteSpace(host)) serverHost = host;
            if (int.TryParse(Environment.GetEnvironmentVariable("SERVER_PORT"), out int port) && port > 0 && port <= 65535)
                serverPort = port;
        }

        private void Update()
        {
            while (mainThread.TryDequeue(out var action)) action();
        }

        public async Task ConnectAsync()
        {
            if (disposed || State == ConnectionState.Connecting || State == ConnectionState.Connected) return;
            SetState(ConnectionState.Connecting);
            client?.Dispose();
            var attempt = new NetworkClient();
            client = attempt;
            attempt.Disconnected += () => mainThread.Enqueue(() =>
            {
                if (client == attempt && !disposed)
                {
                    SetState(ConnectionState.Disconnected);
                    if (SessionId != null) _ = ReconnectAsync();
                }
            });
            attempt.EventReceived += message => mainThread.Enqueue(() =>
            {
                if (client == attempt && !disposed) ApplyLobbyEvent(message);
            });
            try
            {
                await attempt.ConnectAsync(serverHost, serverPort);
                if (SessionId != null && !disposed && client == attempt)
                {
                    var response = await attempt.RequestAsync("RECONNECT", new JObject { ["sessionId"] = SessionId }, null, "RECONNECT_RESULT");
                    if ((bool?)response["success"] != true || (bool?)response["data"]?["restored"] != true)
                    { ClearAuthentication(); attempt.Dispose(); AuthNotice = "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại."; SetState(ConnectionState.Disconnected); return; }
                    Room = response["data"]?["room"] as JObject;
                    if ((string)GameState?["matchId"] != (string)response["data"]?["gameState"]?["matchId"]) GameOver = null;
                    LastDice = null;
                    GameState = response["data"]?["gameState"] as JObject;
                    Invitation = null;
                }
                if (!disposed && client == attempt)
                    SetState(attempt.IsConnected ? ConnectionState.Connected : ConnectionState.Disconnected);
            }
            catch (Exception) { if (!disposed && client == attempt) SetState(ConnectionState.Disconnected); }
        }

        public async Task<JObject> LoginAsync(string username, string password)
        {
            if (State != ConnectionState.Connected) throw new System.IO.IOException();
            var activeClient = client;
            JObject response = await activeClient.RequestAsync("LOGIN", new JObject { ["username"] = username, ["password"] = password });
            if (disposed || activeClient != client || !activeClient.IsConnected) throw new System.IO.IOException();
            if (response["success"]?.Type == JTokenType.Boolean && (bool)response["success"])
            {
                string token = (string)response["data"]?["sessionId"];
                var profile = response["data"]?["profile"] as JObject;
                if ((string)response["type"] != "LOGIN" || string.IsNullOrEmpty(token) || string.IsNullOrEmpty((string)profile?["playerId"]))
                { activeClient.Dispose(); throw new System.IO.InvalidDataException(); }
                SessionId = token;
                Profile = (JObject)profile.DeepClone();
            }
            return response;
        }

        public async Task<JObject> RegisterAsync(string username, string displayName, string password)
        {
            if (State != ConnectionState.Connected || SessionId != null) throw new System.IO.IOException();
            var activeClient = client;
            JObject response = await activeClient.RequestAsync("REGISTER", new JObject
            {
                ["username"] = username, ["password"] = password, ["displayName"] = displayName
            });
            if (disposed || activeClient != client || !activeClient.IsConnected) throw new System.IO.IOException();
            if (response["success"]?.Type != JTokenType.Boolean)
                throw new System.IO.InvalidDataException("Invalid registration response.");
            if ((bool)response["success"] && ((string)response["type"] != "REGISTER" ||
                response["data"]?["profile"] is not JObject profile ||
                string.IsNullOrEmpty((string)profile["playerId"]) || (string)profile["username"] != username))
                throw new System.IO.InvalidDataException("Invalid registration profile.");
            return response; // Registration does not authenticate or create a local session.
        }

        private void SetState(ConnectionState state) { State = state; StateChanged?.Invoke(state); }
        private void OnDestroy()
        {
            if (Instance != this) return;
            disposed = true;
            client?.Dispose();
            Instance = null;
        }
    }
}
