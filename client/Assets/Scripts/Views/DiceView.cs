using System.Collections;
using TMPro;
using UnityEngine;

namespace Ludo.Views
{
    public sealed class DiceView : MonoBehaviour
    {
        [SerializeField] private GameObject[] pips;
        [SerializeField] private TMP_Text empty;
        private Coroutine bounceRoutine;
        private int result;
        // Hiển thị trực tiếp giá trị xúc xắc đã được Server xác nhận.
        public void Show(int value)
        {
            result = value;
            if (bounceRoutine != null) return;
            Display(value);
        }
        // Bật các chấm tương ứng với giá trị mặt xúc xắc.
        private void Display(int value)
        {
            empty.gameObject.SetActive(value < 1 || value > 6);
            // Thứ tự chấm là trên trái, trên phải, giữa trái, tâm, giữa phải, dưới trái, dưới phải.
            bool[] visible = { value >= 4, value >= 2, value == 6, value % 2 == 1, value == 6, value >= 2, value >= 4 };
            for (int i = 0; i < pips.Length; i++) pips[i].SetActive(value >= 1 && value <= 6 && visible[i]);
        }
        // Bắt đầu hiệu ứng tung với kết quả cuối đã có từ Server.
        public void Animate(int value)
        {
            result = value;
            Ludo.Services.AudioManager.Instance?.PlaySfx(Ludo.Services.SfxClip.DiceRoll);
            if (bounceRoutine != null) StopCoroutine(bounceRoutine);
            bounceRoutine = StartCoroutine(Bounce());
        }
        // Đổi mặt để minh họa trong lúc nảy rồi dừng ở kết quả chính thức.
        private IEnumerator Bounce()
        {
            for (float t = 0; t < 1; t += Time.unscaledDeltaTime / .8f)
            {
                // Các mặt thay đổi chỉ là hiệu ứng; kết quả cuối cùng luôn do Server gửi.
                Display(t < .75f ? 1 + (int)(t * 24) % 6 : result);
                transform.localRotation = Quaternion.Euler(0,0,Mathf.Sin(t * Mathf.PI * 6) * (1-t) * 24);
                transform.localScale = Vector3.one * (1 + Mathf.Sin(t*Mathf.PI)*.18f);
                yield return null;
            }
            transform.localRotation = Quaternion.identity; transform.localScale = Vector3.one; bounceRoutine = null; Display(result);
        }
        // Dừng hiệu ứng khi xúc xắc bị ẩn để không còn coroutine cập nhật UI.
        private void OnDisable()
        {
            if (bounceRoutine != null) StopCoroutine(bounceRoutine);
            bounceRoutine = null; transform.localRotation = Quaternion.identity; transform.localScale = Vector3.one;
        }
    }
}
