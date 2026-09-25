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
        public void Show(int value)
        {
            result = value;
            if (bounceRoutine != null) return;
            Display(value);
        }
        private void Display(int value)
        {
            empty.gameObject.SetActive(value < 1 || value > 6);
            // TL, TR, ML, C, MR, BL, BR.
            bool[] visible = { value >= 4, value >= 2, value == 6, value % 2 == 1, value == 6, value >= 2, value >= 4 };
            for (int i = 0; i < pips.Length; i++) pips[i].SetActive(value >= 1 && value <= 6 && visible[i]);
        }
        public void Animate(int value)
        {
            result = value;
            Ludo.Services.AudioManager.Instance?.PlaySfx(Ludo.Services.SfxClip.DiceRoll);
            if (bounceRoutine != null) StopCoroutine(bounceRoutine);
            bounceRoutine = StartCoroutine(Bounce());
        }
        private IEnumerator Bounce()
        {
            for (float t = 0; t < 1; t += Time.unscaledDeltaTime / .8f)
            {
                // Decorative faces only; the final result always comes from the server.
                Display(t < .75f ? 1 + (int)(t * 24) % 6 : result);
                transform.localRotation = Quaternion.Euler(0,0,Mathf.Sin(t * Mathf.PI * 6) * (1-t) * 24);
                transform.localScale = Vector3.one * (1 + Mathf.Sin(t*Mathf.PI)*.18f);
                yield return null;
            }
            transform.localRotation = Quaternion.identity; transform.localScale = Vector3.one; bounceRoutine = null; Display(result);
        }
        private void OnDisable()
        {
            if (bounceRoutine != null) StopCoroutine(bounceRoutine);
            bounceRoutine = null; transform.localRotation = Quaternion.identity; transform.localScale = Vector3.one;
        }
    }
}
