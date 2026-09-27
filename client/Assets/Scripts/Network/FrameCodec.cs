using System;
using System.IO;
using System.Text;
using System.Threading;
using System.Threading.Tasks;

namespace Ludo.Network
{
    public static class FrameCodec
    {
        public const int MaxFrameLength = 65536;
        private static readonly Encoding Utf8 = new UTF8Encoding(false, true);

        // Mã hóa JSON UTF-8 và thêm prefix độ dài big-endian bốn byte.
        public static byte[] Encode(string json)
        {
            byte[] payload = Utf8.GetBytes(json);
            ValidateLength(payload.Length);
            byte[] frame = new byte[payload.Length + 4];
            int size = payload.Length;
            frame[0] = (byte)(size >> 24);
            frame[1] = (byte)(size >> 16);
            frame[2] = (byte)(size >> 8);
            frame[3] = (byte)size;
            Buffer.BlockCopy(payload, 0, frame, 4, size);
            return frame;
        }

        // Đọc đủ prefix và payload, từ chối độ dài không hợp lệ trước khi cấp phát.
        public static async Task<string> ReadAsync(Stream stream, CancellationToken token)
        {
            byte[] header = new byte[4];
            await ReadExactlyAsync(stream, header, token).ConfigureAwait(false);
            int size = (header[0] << 24) | (header[1] << 16) | (header[2] << 8) | header[3];
            ValidateLength(size); // Kiểm tra độ dài từ mạng trước khi cấp phát bộ nhớ.
            byte[] payload = new byte[size];
            await ReadExactlyAsync(stream, payload, token).ConfigureAwait(false);
            return Utf8.GetString(payload);
        }

        // Chặn frame rỗng hoặc lớn hơn giới hạn 64 KB.
        private static void ValidateLength(int size)
        {
            if (size <= 0 || size > MaxFrameLength)
                throw new InvalidDataException("Invalid frame length.");
        }

        // Lặp cho đủ số byte vì một lần đọc TCP có thể chỉ nhận được một phần frame.
        private static async Task ReadExactlyAsync(Stream stream, byte[] buffer, CancellationToken token)
        {
            int offset = 0;
            while (offset < buffer.Length)
            {
                int count = await stream.ReadAsync(buffer, offset, buffer.Length - offset, token).ConfigureAwait(false);
                if (count == 0) throw new EndOfStreamException();
                offset += count;
            }
        }
    }
}
