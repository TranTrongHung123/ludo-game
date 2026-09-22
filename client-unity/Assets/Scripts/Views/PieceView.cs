using System.Collections;
using System.Collections.Generic;
using TMPro;
using UnityEngine;

namespace Ludo.Views
{
    public sealed class PieceView : MonoBehaviour
    {
        [SerializeField] private UnityEngine.UI.Button button;
        [SerializeField] private UnityEngine.UI.Image body, halo;
        [SerializeField] private TMP_Text number, effect;
        [SerializeField] private HorseGraphic horse;
        private Coroutine motion;
        private int previousStep = -2;
        private RectTransform Rect => (RectTransform)transform;
        public void Bind(string id, int slot, int index, int step, bool shield, bool slow, bool legal, bool selected, System.Action<string> select, bool animate)
        {
            body.color = Color.clear;
            horse.color = BoardGeometry.Tints[slot];
            number.gameObject.SetActive(false);
            effect.text = shield ? "K" : slow ? "−2" : step == 53 ? "V" : "";
            effect.gameObject.SetActive(shield || slow || step == 53);
            halo.gameObject.SetActive(legal || selected);
            halo.color = selected ? new Color32(113,73,209,255) : new Color32(237,197,87,255);
            button.interactable = legal;
            button.onClick.RemoveAllListeners(); button.onClick.AddListener(() => select(id));
            if (step == previousStep) return;
            if (motion != null) StopCoroutine(motion);
            var destination = BoardGeometry.Position(slot, step, index);
            if (!animate || previousStep < 0 || step < 0 || Mathf.Abs(step - previousStep) > 8)
                Rect.anchoredPosition = destination;
            else
            {
                var points = new List<Vector2>();
                int direction = step > previousStep ? 1 : -1;
                for (int s = previousStep + direction; s != step + direction; s += direction)
                    points.Add(BoardGeometry.Position(slot, s, index));
                motion = StartCoroutine(Move(points));
            }
            previousStep = step;
        }
        private IEnumerator Move(List<Vector2> points)
        {
            foreach (var target in points)
            {
                var origin = Rect.anchoredPosition;
                for (float t = 0; t < 1; t += Time.unscaledDeltaTime / .085f)
                { Rect.anchoredPosition = Vector2.Lerp(origin, target, Mathf.SmoothStep(0,1,t)) + Vector2.up * Mathf.Sin(t * Mathf.PI) * 5; yield return null; }
                Rect.anchoredPosition = target;
            }
            motion = null;
        }
    }
}
