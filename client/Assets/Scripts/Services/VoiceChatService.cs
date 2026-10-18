using System;
using System.Collections;
using System.Collections.Concurrent;
using System.Collections.Generic;
using System.Net;
using System.Net.Sockets;
using System.Threading;
using System.Threading.Tasks;
using Newtonsoft.Json.Linq;
using UnityEngine;

namespace Ludo.Services
{
    public enum VoiceConnectionState { Stopped, Connecting, Connected, Failed }

    /// <summary>Thu/phát PCM16 mono và trao đổi các khung 20 ms qua UDP.</summary>
    public sealed class VoiceChatService : MonoBehaviour
    {
        private const int Magic = 0x4C564331; // LVC1
        private const byte ClientRegister = 1;
        private const byte ClientAudio = 2;
        private const byte ServerAudio = 3;
        private const byte ServerRegistered = 4;
        private const int TokenBytes = 32;
        private const int ClientHeaderBytes = 41;
        private const int ServerHeaderBytes = 17;
        private const int MaxQueuedSamples = 32000;
        private const double SpeakingMeanSquareThreshold = 0.000144; // RMS 0.012, khoảng -38 dBFS.
        private static readonly long SpeakingHoldTicks = System.Diagnostics.Stopwatch.Frequency / 4;

        public static VoiceChatService Instance { get; private set; }
        public VoiceConnectionState State { get; private set; } = VoiceConnectionState.Stopped;
        public bool MicEnabled => micEnabled;
        public bool SpeakerEnabled => speakerEnabled;
        public string StatusMessage { get; private set; } = "Thoại chưa kết nối";
        public event Action Changed;

        private readonly ConcurrentQueue<Action> mainThread = new ConcurrentQueue<Action>();
        private readonly ConcurrentDictionary<long, SpeakerBuffer> speakers = new ConcurrentDictionary<long, SpeakerBuffer>();
        private readonly ConcurrentDictionary<long, long> speakingUntil = new ConcurrentDictionary<long, long>();
        private long localPlayerId;
        private readonly object socketLock = new object();
        private Socket socket;
        private Thread receiveThread;
        private byte[] token;
        private int generation;
        private int sequence;
        private int sampleRate = 16000;
        private int frameSamples = 320;
        private float nextKeepAlive;
        private volatile bool receiving;
        private volatile bool micEnabled;
        private volatile bool speakerEnabled = true;

        private AudioSource playbackSource;
        private AudioClip microphoneClip;
        private string microphoneDevice;
        private int microphonePosition;
        private float[] captureFrame;
        private int captureFrameCount;
        private bool permissionRequestPending;

        public static VoiceChatService EnsureInstance()
        {
            if (Instance != null) return Instance;
            var go = new GameObject("VoiceChatService");
            return go.AddComponent<VoiceChatService>();
        }

        private void Awake()
        {
            if (Instance != null && Instance != this) { Destroy(gameObject); return; }
            Instance = this;
            DontDestroyOnLoad(gameObject);
            playbackSource = gameObject.AddComponent<AudioSource>();
            playbackSource.playOnAwake = false;
            playbackSource.loop = true;
            playbackSource.spatialBlend = 0f;
            playbackSource.volume = 1f;
            var playbackClip = AudioClip.Create("UDP Voice Playback", 32000, 1, 16000, true, FillPlayback);
            playbackSource.clip = playbackClip;
            playbackSource.Play();
        }

        public async Task StartForMatchAsync(NetworkSession session, string roomId)
        {
            if (session == null || string.IsNullOrWhiteSpace(roomId)) return;
            int attempt = ++generation;
            StopMicrophone();
            StopTransport();
            long.TryParse((string)session.Profile?["playerId"], out localPlayerId);
            SetState(VoiceConnectionState.Connecting, "Đang kết nối thoại...");
            try
            {
                JObject config = await session.RegisterVoiceAsync(roomId);
                if (attempt != generation || this == null) return;
                int port = (int?)config["udpPort"] ?? 0;
                sampleRate = (int?)config["sampleRate"] ?? 16000;
                frameSamples = (int?)config["frameSamples"] ?? 320;
                token = DecodeToken((string)config["token"]);
                if (port < 1 || token.Length != TokenBytes || sampleRate != 16000 || frameSamples <= 0)
                    throw new InvalidOperationException("Cấu hình thoại không hợp lệ.");

                IPAddress address = await ResolveAddressAsync(session.ServerHost);
                if (attempt != generation || this == null) return;
                var activeSocket = new Socket(address.AddressFamily, SocketType.Dgram, ProtocolType.Udp);
                activeSocket.Connect(new IPEndPoint(address, port));
                lock (socketLock) socket = activeSocket;
                receiving = true;
                receiveThread = new Thread(() => ReceiveLoop(activeSocket, attempt))
                { IsBackground = true, Name = "udp-voice-client" };
                receiveThread.Start();
                SendRegistration(activeSocket);
                nextKeepAlive = Time.unscaledTime + 3f;
                StatusMessage = "Đang xác nhận kênh UDP...";
                Changed?.Invoke();
            }
            catch (Exception)
            {
                if (attempt != generation || this == null) return;
                StopTransport();
                SetState(VoiceConnectionState.Failed, "Không thể kết nối thoại UDP");
            }
        }

        public void StopMatch()
        {
            generation++;
            micEnabled = false;
            StopMicrophone();
            StopTransport();
            localPlayerId = 0;
            SetState(VoiceConnectionState.Stopped, "Thoại chưa kết nối");
        }

        // Dùng đồng hồ đơn điệu để cả luồng nhận UDP và UI đọc trạng thái an toàn.
        public bool IsSpeaking(string playerId)
        {
            if (State != VoiceConnectionState.Connected || !long.TryParse(playerId, out long id)) return false;
            if (id == localPlayerId && !micEnabled) return false;
            return speakingUntil.TryGetValue(id, out long until)
                && System.Diagnostics.Stopwatch.GetTimestamp() < until;
        }

        private void RecordVoiceActivity(long playerId, double meanSquare)
        {
            if (playerId <= 0 || meanSquare < SpeakingMeanSquareThreshold) return;
            speakingUntil[playerId] = System.Diagnostics.Stopwatch.GetTimestamp() + SpeakingHoldTicks;
        }

        public void ToggleMic()
        {
            micEnabled = !micEnabled;
            if (micEnabled && State == VoiceConnectionState.Connected) StartMicrophone();
            else if (!micEnabled) StopMicrophone();
            Changed?.Invoke();
        }

        public void ToggleSpeaker()
        {
            speakerEnabled = !speakerEnabled;
            if (!speakerEnabled) ClearSpeakerBuffers();
            Changed?.Invoke();
        }

        private void Update()
        {
            while (mainThread.TryDequeue(out Action action)) action();
            if ((State == VoiceConnectionState.Connecting || State == VoiceConnectionState.Connected)
                && Time.unscaledTime >= nextKeepAlive)
            {
                Socket active;
                lock (socketLock) active = socket;
                if (active != null) TrySend(() => SendRegistration(active));
                nextKeepAlive = Time.unscaledTime + 3f;
            }
            if (State != VoiceConnectionState.Connected) return;
            if (micEnabled)
            {
                if (microphoneClip == null) StartMicrophone();
                CaptureMicrophoneFrames();
            }
        }

        private void StartMicrophone()
        {
            if (microphoneClip != null || permissionRequestPending || State != VoiceConnectionState.Connected) return;
            if (!Application.HasUserAuthorization(UserAuthorization.Microphone))
            {
                permissionRequestPending = true;
                StartCoroutine(RequestMicrophoneAccess());
                return;
            }
            StartMicrophoneAfterPermission();
        }

        private IEnumerator RequestMicrophoneAccess()
        {
            yield return Application.RequestUserAuthorization(UserAuthorization.Microphone);
            permissionRequestPending = false;
            if (micEnabled && Application.HasUserAuthorization(UserAuthorization.Microphone))
                StartMicrophoneAfterPermission();
            else if (micEnabled)
            {
                micEnabled = false;
                StatusMessage = "Ứng dụng chưa được cấp quyền dùng mic";
                Changed?.Invoke();
            }
        }

        private void StartMicrophoneAfterPermission()
        {
            if (microphoneClip != null || !micEnabled) return;
            microphoneDevice = Microphone.devices.Length > 0 ? Microphone.devices[0] : null;
            microphoneClip = Microphone.Start(microphoneDevice, true, 1, sampleRate);
            if (microphoneClip == null)
            {
                micEnabled = false;
                StatusMessage = "Không tìm thấy microphone";
                Changed?.Invoke();
                return;
            }
            microphonePosition = 0;
            captureFrame = new float[frameSamples];
            captureFrameCount = 0;
        }

        private void CaptureMicrophoneFrames()
        {
            if (microphoneClip == null) return;
            int current = Microphone.GetPosition(microphoneDevice);
            if (current < 0 || current == microphonePosition) return;
            int available = current > microphonePosition
                ? current - microphonePosition
                : microphoneClip.samples - microphonePosition + current;
            if (available > microphoneClip.samples) available = microphoneClip.samples;

            while (available > 0)
            {
                int count = Math.Min(available, microphoneClip.samples - microphonePosition);
                var samples = new float[count];
                microphoneClip.GetData(samples, microphonePosition);
                for (int i = 0; i < samples.Length; i++)
                {
                    captureFrame[captureFrameCount++] = samples[i];
                    if (captureFrameCount == frameSamples)
                    {
                        SendAudioFrame(captureFrame);
                        captureFrameCount = 0;
                    }
                }
                microphonePosition = (microphonePosition + count) % microphoneClip.samples;
                available -= count;
            }
        }

        private void SendAudioFrame(float[] samples)
        {
            Socket active;
            lock (socketLock) active = socket;
            if (active == null || token == null || !micEnabled) return;
            byte[] packet = new byte[ClientHeaderBytes + samples.Length * 2];
            WriteInt32(packet, 0, Magic);
            packet[4] = ClientAudio;
            Buffer.BlockCopy(token, 0, packet, 5, TokenBytes);
            WriteInt32(packet, 37, sequence++);
            int offset = ClientHeaderBytes;
            double energy = 0;
            for (int i = 0; i < samples.Length; i++)
            {
                energy += samples[i] * samples[i];
                short pcm = (short)Mathf.RoundToInt(Mathf.Clamp(samples[i], -1f, 1f) * 32767f);
                packet[offset++] = (byte)(pcm >> 8);
                packet[offset++] = (byte)pcm;
            }
            if (samples.Length > 0) RecordVoiceActivity(localPlayerId, energy / samples.Length);
            TrySend(() => active.Send(packet));
        }

        private void SendRegistration(Socket active)
        {
            if (token == null) return;
            byte[] packet = new byte[5 + TokenBytes];
            WriteInt32(packet, 0, Magic);
            packet[4] = ClientRegister;
            Buffer.BlockCopy(token, 0, packet, 5, TokenBytes);
            active.Send(packet);
        }

        private void ReceiveLoop(Socket active, int attempt)
        {
            var buffer = new byte[1200];
            try
            {
                while (receiving && attempt == generation)
                {
                    int length = active.Receive(buffer);
                    if (attempt != generation) return;
                    if (length < 5 || ReadInt32(buffer, 0) != Magic) continue;
                    if (buffer[4] == ServerRegistered && length == 5)
                    {
                        mainThread.Enqueue(() =>
                        {
                            if (attempt != generation) return;
                            SetState(VoiceConnectionState.Connected, "Thoại UDP đã sẵn sàng");
                            if (micEnabled) StartMicrophone();
                        });
                        continue;
                    }
                    if (buffer[4] != ServerAudio || length <= ServerHeaderBytes || ((length - ServerHeaderBytes) & 1) != 0)
                        continue;
                    ProcessAudioPacket(buffer, length);
                }
            }
            catch (SocketException) { }
            catch (ObjectDisposedException) { }
        }

        private void ProcessAudioPacket(byte[] buffer, int length)
        {
            long senderId = ReadInt64(buffer, 5);
            // Vẫn chỉ báo người nói khi tắt loa, nhưng không giữ âm thanh để phát lại.
            SpeakerBuffer speaker = speakerEnabled
                ? speakers.GetOrAdd(senderId, _ => new SpeakerBuffer(frameSamples)) : null;
            double energy = 0;
            int count = 0;
            for (int offset = ServerHeaderBytes; offset + 1 < length; offset += 2)
            {
                short pcm = (short)((buffer[offset] << 8) | buffer[offset + 1]);
                float sample = pcm / 32768f;
                energy += sample * sample;
                count++;
                speaker?.Enqueue(sample);
            }
            if (count > 0) RecordVoiceActivity(senderId, energy / count);
        }

        private void FillPlayback(float[] data)
        {
            Array.Clear(data, 0, data.Length);
            if (!speakerEnabled) return;
            for (int i = 0; i < data.Length; i++)
            {
                float mixed = 0f;
                int count = 0;
                foreach (SpeakerBuffer speaker in speakers.Values)
                {
                    if (speaker.TryDequeue(out float sample)) { mixed += sample; count++; }
                }
                if (count > 0) data[i] = Mathf.Clamp(mixed / Mathf.Sqrt(count), -1f, 1f);
            }
        }

        private void StopMicrophone()
        {
            speakingUntil.TryRemove(localPlayerId, out _);
            if (microphoneClip != null && Microphone.IsRecording(microphoneDevice))
                Microphone.End(microphoneDevice);
            microphoneClip = null;
            captureFrame = null;
            captureFrameCount = 0;
        }

        private void StopTransport()
        {
            receiving = false;
            Socket active;
            lock (socketLock) { active = socket; socket = null; }
            try { active?.Close(); } catch (SocketException) { }
            if (receiveThread != null && receiveThread != Thread.CurrentThread && receiveThread.IsAlive)
                receiveThread.Join(250);
            receiveThread = null;
            token = null;
            ClearSpeakerBuffers();
            speakingUntil.Clear();
        }

        private void ClearSpeakerBuffers()
        {
            speakers.Clear();
        }

        private void SetState(VoiceConnectionState state, string message)
        {
            State = state;
            StatusMessage = message;
            Changed?.Invoke();
        }

        private void TrySend(Action send)
        {
            try { send(); }
            catch (SocketException) { }
            catch (ObjectDisposedException) { }
        }

        private static async Task<IPAddress> ResolveAddressAsync(string host)
        {
            IPAddress[] addresses = await Dns.GetHostAddressesAsync(host);
            foreach (IPAddress address in addresses)
                if (address.AddressFamily == AddressFamily.InterNetwork) return address;
            if (addresses.Length == 0) throw new SocketException((int)SocketError.HostNotFound);
            return addresses[0];
        }

        private static byte[] DecodeToken(string encoded)
        {
            if (string.IsNullOrEmpty(encoded)) return Array.Empty<byte>();
            string value = encoded.Replace('-', '+').Replace('_', '/');
            value = value.PadRight(value.Length + ((4 - value.Length % 4) % 4), '=');
            return Convert.FromBase64String(value);
        }

        private static void WriteInt32(byte[] bytes, int offset, int value)
        {
            bytes[offset] = (byte)(value >> 24);
            bytes[offset + 1] = (byte)(value >> 16);
            bytes[offset + 2] = (byte)(value >> 8);
            bytes[offset + 3] = (byte)value;
        }

        private static int ReadInt32(byte[] bytes, int offset) =>
            (bytes[offset] << 24) | (bytes[offset + 1] << 16) | (bytes[offset + 2] << 8) | bytes[offset + 3];

        private static long ReadInt64(byte[] bytes, int offset)
        {
            long value = 0;
            for (int i = 0; i < 8; i++) value = (value << 8) | bytes[offset + i];
            return value;
        }

        private void OnDestroy()
        {
            if (Instance != this) return;
            generation++;
            StopMicrophone();
            StopTransport();
            Instance = null;
        }

        private sealed class SpeakerBuffer
        {
            private readonly ConcurrentQueue<float> samples = new ConcurrentQueue<float>();
            private readonly int startThreshold;
            private int count;
            private int ready;

            public SpeakerBuffer(int frameSize) { startThreshold = Math.Max(frameSize * 2, 1); }

            public void Enqueue(float sample)
            {
                samples.Enqueue(sample);
                int queued = Interlocked.Increment(ref count);
                if (queued >= startThreshold) Interlocked.Exchange(ref ready, 1);
                while (queued > MaxQueuedSamples && samples.TryDequeue(out _))
                    queued = Interlocked.Decrement(ref count);
            }

            public bool TryDequeue(out float sample)
            {
                sample = 0f;
                if (Volatile.Read(ref ready) == 0) return false;
                if (!samples.TryDequeue(out sample))
                {
                    Interlocked.Exchange(ref ready, 0);
                    return false;
                }
                if (Interlocked.Decrement(ref count) == 0) Interlocked.Exchange(ref ready, 0);
                return true;
            }
        }
    }
}
