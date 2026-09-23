using System.Collections;
using TMPro;
using Newtonsoft.Json.Linq;
using UnityEngine;

namespace Ludo.Views
{
    public sealed class PieceView : MonoBehaviour
    {
        [SerializeField] private UnityEngine.UI.Button button;
        [SerializeField] private UnityEngine.UI.Image body, halo;
        [SerializeField] private TMP_Text number, effect;
        [SerializeField] private HorseGraphic horse;
        [SerializeField] private GameObject finishBadge;
        private Coroutine motion;
        private int previousStep = -2;
        private int boundSlot, boundIndex;
        private bool legalNow, selectedNow;
        public bool IsAnimating => motion != null;
        private RectTransform Rect => (RectTransform)transform;
        public void Bind(string id, int slot, int index, int step, bool legal, bool selected, System.Action<string> select, bool animate,
            JObject move = null, System.Action<string> notify = null)
        {
            boundSlot = slot; boundIndex = index; legalNow = legal; selectedNow = selected;
            body.color = Color.clear;
            horse.color = BoardGeometry.Tints[slot];
            number.gameObject.SetActive(false);
            button.onClick.RemoveAllListeners(); button.onClick.AddListener(() => select(id));
            if (step == previousStep && move == null) { if (!IsAnimating) Rest(); return; }
            int from = previousStep;
            Snap();
            previousStep = step;
            bool validMove = move != null && (string)move["pieceId"] == id &&
                (int?)move["fromStep"] == from && (int?)move["toStep"] == step &&
                (int?)move["landedStep"] >= 0 && (int?)move["landedStep"] <= 53;
            if (animate && validMove && isActiveAndEnabled)
            {
                effect.gameObject.SetActive(false);
                if (finishBadge != null) finishBadge.SetActive(false);
                Rect.localScale = Vector3.one; button.interactable = false;
                motion = StartCoroutine(Move(slot, index, from, (int)move["landedStep"], step, (string)move["triggeredEffect"], notify));
            }
            else { Rect.anchoredPosition = BoardGeometry.Position(slot, step, index); Rest(); }
        }
        private void Rest()
        {
            bool finished = previousStep == 53;
            Rect.localScale = Vector3.one * (finished ? .76f : 1f);
            effect.gameObject.SetActive(false);
            if (finishBadge != null) finishBadge.SetActive(finished);
            halo.gameObject.SetActive(finished || legalNow || selectedNow);
            halo.color = finished ? new Color32(239,201,91,255) : selectedNow ? new Color32(113,73,209,255) : new Color32(237,197,87,255);
            button.interactable = legalNow && !finished;
        }
        public void Snap()
        {
            if (motion != null) StopCoroutine(motion);
            motion = null;
            if (previousStep >= -1) Rect.anchoredPosition = BoardGeometry.Position(boundSlot, previousStep, boundIndex);
            Rest();
        }
        private void OnDisable() => Snap();
        public static string EffectMessage(string effect, bool blocked) => effect switch
        {
            "SPEED" => blocked ? "+2 bị chặn • Giữ nguyên ô" : "Tiến thêm 2 bước!",
            "SLOW" => blocked ? "−2 bị chặn • Giữ nguyên ô" : "Lùi lại 2 bước!",
            "LUCKY" => "May mắn • Tung thêm 1 lần!",
            "TRAP" => "Trúng bẫy • Về chuồng!",
            _ => null
        };
        private IEnumerator Move(int slot, int index, int from, int landed, int to, string triggered, System.Action<string> notify)
        {
            transform.SetAsLastSibling();
            if (from < 0) yield return Hop(BoardGeometry.PathPosition(slot, landed, index), .25f);
            else for (int step = from + 1; step <= landed; step++)
                yield return Hop(BoardGeometry.PathPosition(slot, step, index), .13f);
            string message = EffectMessage(triggered, landed == to);
            if (message != null)
            {
                notify?.Invoke(message);
                effect.text = triggered == "SPEED" ? "+2" : triggered == "SLOW" ? "−2" : triggered == "LUCKY" ? "+1" : "!";
                effect.gameObject.SetActive(true);
                halo.gameObject.SetActive(true); halo.color = triggered == "SLOW" || triggered == "TRAP" ? new Color32(244,132,143,255) : new Color32(239,201,91,255);
                yield return Pulse(.38f);
                if (to < 0) yield return Hop(BoardGeometry.Yard(slot, index), .4f);
                else if (to != landed)
                {
                    int direction = to > landed ? 1 : -1;
                    for (int step = landed + direction; step != to + direction; step += direction)
                        yield return Hop(BoardGeometry.PathPosition(slot, step, index), .19f);
                }
            }
            if (to == 53)
            {
                effect.gameObject.SetActive(false);
                halo.gameObject.SetActive(true); halo.color = new Color32(239,201,91,255);
                notify?.Invoke("Ngựa " + (index + 1) + " đã về đích!");
                yield return Pulse(.42f);
                yield return Hop(BoardGeometry.Finished(slot, index), .45f);
            }
            Rect.anchoredPosition = BoardGeometry.Position(slot, to, index);
            motion = null; Rest();
        }
        private IEnumerator Pulse(float seconds)
        {
            for (float time = 0; time < seconds; time += Time.unscaledDeltaTime)
            { Rect.localScale = Vector3.one * (1f + .2f * Mathf.Sin(time / seconds * Mathf.PI)); yield return null; }
            Rect.localScale = Vector3.one;
        }
        private IEnumerator Hop(Vector2 target, float seconds)
        {
            var origin = Rect.anchoredPosition;
            for (float time = 0; time < seconds; time += Time.unscaledDeltaTime)
            {
                float t = time / seconds;
                Rect.anchoredPosition = Vector2.Lerp(origin, target, Mathf.SmoothStep(0,1,t)) + Vector2.up * Mathf.Sin(t * Mathf.PI) * 7;
                yield return null;
            }
            Rect.anchoredPosition = target;
        }
    }
}
