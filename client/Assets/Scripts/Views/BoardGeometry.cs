using System;
using UnityEngine;

namespace Ludo.Views
{
    // Chỉ tính tọa độ hiển thị; tiến độ và nước đi hợp lệ phải lấy từ Server.
    public static class BoardGeometry
    {
        public const float Cell = 58f;
        public static readonly string[] Colors = { "RED", "BLUE", "YELLOW", "GREEN" };
        public static readonly string[] Names = { "Đỏ", "Xanh dương", "Vàng", "Xanh lá" };
        public static readonly Color[] Tints = { new Color32(231,88,116,255), new Color32(83,137,223,255), new Color32(224,169,48,255), new Color32(53,167,134,255) };
        public static int Slot(string color) => Array.IndexOf(Colors, color);
        // Xoay tọa độ mẫu theo slot để bốn màu dùng chung một bố cục.
        public static Vector2 Rotate(Vector2 point, int slot)
        { for (int i = 0; i < slot; i++) point = new Vector2(point.y, -point.x); return point; }
        // Ánh xạ chỉ số vòng chung thành tọa độ màn hình qua bốn phần tư bàn cờ.
        public static Vector2 Ring(int index)
        {
            if (index < 0 || index > 47) throw new ArgumentOutOfRangeException(nameof(index));
            int local = index % 12;
            var point = local < 6 ? new Vector2(-6 + local, 1) : local < 11 ? new Vector2(-1, local - 4) : new Vector2(0, 6);
            return Rotate(point, index / 12) * Cell;
        }
        public static Vector2 Yard(int slot, int index) => Rotate(new Vector2(-4.3f + (index % 2) * 1.5f, 4.3f - (index / 2) * 1.5f), slot) * Cell;
        public static Vector2 Podium(int slot) => new Vector2(slot == 0 || slot == 3 ? -206 : 206, slot < 2 ? 350 : -350);
        public static Vector2 Finished(int slot, int index) => Podium(slot) + new Vector2(-72 + index * 48, -9);
        // Nấc đích thứ sáu nằm ở tâm bàn trước khi quân hoàn thành chuyển lên khay.
        public static Vector2 PathPosition(int slot, int step, int index) => step == 53 ? Vector2.zero : Position(slot, step, index);
        // Chọn vị trí bãi, vòng chung, đường đích hoặc khay theo stepCount từ Server.
        public static Vector2 Position(int slot, int step, int index)
        {
            if (slot < 0 || slot > 3 || step < -1 || step > 53) throw new ArgumentOutOfRangeException();
            if (step == -1) return Yard(slot, index);
            if (step < 48) return Ring((slot * 12 + step) % 48);
            if (step == 53) return Finished(slot, index);
            return Rotate(new Vector2(step - 53, 0), slot) * Cell;
        }
    }
}
