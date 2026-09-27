using System;
using System.Collections.Concurrent;
using System.IO;
using System.Net.Sockets;
using System.Threading;
using System.Threading.Tasks;
using Newtonsoft.Json;
using Newtonsoft.Json.Linq;

namespace Ludo.Network
{
    public sealed class NetworkClient : IDisposable
    {
        private readonly TcpClient socket = new TcpClient();
        private readonly CancellationTokenSource lifetime = new CancellationTokenSource();
        private readonly SemaphoreSlim sendLock = new SemaphoreSlim(1, 1);
        private readonly ConcurrentDictionary<string, TaskCompletionSource<JObject>> pending =
            new ConcurrentDictionary<string, TaskCompletionSource<JObject>>();
        private int closed;
        public bool IsConnected { get; private set; }
        // Sự kiện phát từ luồng đọc mạng; bên nhận phải chuyển thao tác Unity về luồng chính.
        public event Action Disconnected;
        public event Action<JObject> EventReceived;

        // Mở TCP với thời hạn kết nối rồi chạy vòng đọc nền, không chặn luồng Unity.
        public async Task ConnectAsync(string host, int port)
        {
            try
            {
                Task connect = socket.ConnectAsync(host, port);
                Task timeout = Task.Delay(5000, lifetime.Token);
                if (await Task.WhenAny(connect, timeout).ConfigureAwait(false) != connect)
                {
                    Dispose();
                    // Nhận lỗi từ tác vụ kết nối đã bị đóng để không bỏ sót lỗi bất đồng bộ.
                    try { await connect.ConfigureAwait(false); } catch { }
                    throw new TimeoutException();
                }
                await connect.ConfigureAwait(false);
                lifetime.Token.ThrowIfCancellationRequested();
                socket.NoDelay = true;
                IsConnected = true;
                _ = ReadLoopAsync();
            }
            catch { Dispose(); throw; }
        }

        // Gắn requestId, chờ đúng phản hồi trong thời hạn và luôn dọn yêu cầu đang chờ.
        public async Task<JObject> RequestAsync(string type, JObject data, string sessionId = null, string responseType = null)
        {
            if (!IsConnected) throw new IOException("Not connected.");
            string id = Guid.NewGuid().ToString();
            var completion = new TaskCompletionSource<JObject>(TaskCreationOptions.RunContinuationsAsynchronously);
            pending[id] = completion;
            try
            {
                var message = new JObject { ["type"] = type, ["requestId"] = id, ["data"] = data };
                if (sessionId != null) message["sessionId"] = sessionId;
                await SendAsync(message).ConfigureAwait(false);
                if (await Task.WhenAny(completion.Task, Task.Delay(10000, lifetime.Token)).ConfigureAwait(false) != completion.Task)
                {
                    // Đóng kết nối khi chờ quá hạn để phản hồi đăng nhập đến muộn không tạo phiên ngoài ý muốn.
                    Dispose();
                    throw new TimeoutException();
                }
                JObject response = await completion.Task.ConfigureAwait(false);
                if ((string)response["type"] != (responseType ?? type) && (string)response["type"] != "ERROR")
                    throw new InvalidDataException("Unexpected response type.");
                return response;
            }
            finally { pending.TryRemove(id, out _); }
        }

        // Khóa từng lần ghi để nhiều yêu cầu không trộn byte trong cùng TCP stream.
        private async Task SendAsync(JObject message)
        {
            byte[] bytes = FrameCodec.Encode(message.ToString(Formatting.None));
            await sendLock.WaitAsync(lifetime.Token).ConfigureAwait(false);
            try { await socket.GetStream().WriteAsync(bytes, 0, bytes.Length, lifetime.Token).ConfigureAwait(false); }
            catch { Dispose(); throw; }
            finally { sendLock.Release(); }
        }

        // Đọc message, trả heartbeat và tách phản hồi đang chờ khỏi sự kiện Server.
        private async Task ReadLoopAsync()
        {
            try
            {
                while (!lifetime.IsCancellationRequested)
                {
                    string json = await FrameCodec.ReadAsync(socket.GetStream(), lifetime.Token).ConfigureAwait(false);
                    var message = JObject.Parse(json);
                    if (message["type"]?.Type != JTokenType.String || string.IsNullOrEmpty((string)message["type"]))
                        throw new InvalidDataException("Missing message type.");
                    if ((string)message["type"] == "PING")
                        await SendAsync(new JObject { ["type"] = "PONG", ["requestId"] = message["requestId"], ["data"] = message["data"] }).ConfigureAwait(false);
                    if (message["requestId"] != null && message["requestId"].Type != JTokenType.Null &&
                        message["requestId"].Type != JTokenType.String)
                        throw new InvalidDataException("Invalid request id.");
                    string id = (string)message["requestId"];
                    if (id != null && pending.TryRemove(id, out var completion)) completion.TrySetResult(message);
                    else if (message["success"] == null && (string)message["type"] != "PING") EventReceived?.Invoke(message);
                }
            }
            catch (Exception exception) when (exception is IOException || exception is SocketException ||
                exception is JsonException || exception is OperationCanceledException ||
                exception is ObjectDisposedException || exception is ArgumentException)
            {
                // Không ghi nguyên message vào log vì có thể chứa mật khẩu hoặc mã phiên.
            }
            finally { Dispose(); }
        }

        // Đóng kết nối đúng một lần và báo lỗi cho mọi yêu cầu chưa nhận phản hồi.
        public void Dispose()
        {
            if (Interlocked.Exchange(ref closed, 1) != 0) return;
            IsConnected = false;
            lifetime.Cancel();
            socket.Close();
            foreach (var request in pending.Values) request.TrySetException(new IOException("Connection closed."));
            pending.Clear();
            Disconnected?.Invoke();
        }
    }
}
