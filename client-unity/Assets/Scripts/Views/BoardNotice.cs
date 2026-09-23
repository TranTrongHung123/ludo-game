using System.Collections.Generic;
using TMPro;
using UnityEngine;

namespace Ludo.Views
{
    // Non-modal, uses unscaled time and never intercepts board input.
    public sealed class BoardNotice : MonoBehaviour
    {
        [SerializeField] private CanvasGroup group;
        [SerializeField] private TMP_Text label;
        [SerializeField] private UnityEngine.UI.Image accent;
        private readonly Queue<(string text, Color color)> pending = new Queue<(string, Color)>();
        private float elapsed;
        private bool showing;
        public bool Visible => showing || pending.Count > 0;
        private void Awake() => Clear();
        public void Show(string text, Color color)
        {
            if (string.IsNullOrEmpty(text)) return;
            if (pending.Count >= 4) pending.Dequeue();
            pending.Enqueue((text, color));
            if (!showing) Next();
        }
        private void Next()
        {
            var item = pending.Dequeue();
            label.richText = false; label.text = item.text; accent.color = item.color;
            elapsed = 0; showing = true;
            group.blocksRaycasts = false; group.interactable = false; group.alpha = 0;
        }
        private void Update()
        {
            if (!showing) return;
            elapsed += Time.unscaledDeltaTime;
            group.alpha = Mathf.Min(Mathf.Clamp01(elapsed / .18f), Mathf.Clamp01((2.6f - elapsed) / .3f));
            if (elapsed < 2.6f) return;
            showing = false; group.alpha = 0;
            if (pending.Count > 0) Next();
        }
        public void Clear()
        {
            pending.Clear(); showing = false;
            if (group != null) { group.alpha = 0; group.blocksRaycasts = false; group.interactable = false; }
        }
        private void OnDisable() => Clear();
    }
}
