using System.Collections.Generic;
using Ludo.Services;
using TMPro;
using UnityEngine;
using UnityEngine.UI;

namespace Ludo.Views
{
    /// <summary>Cụm nút tròn điều khiển loa và microphone trong trận đấu.</summary>
    public sealed class VoiceControlView : MonoBehaviour
    {
        private static readonly Color32 EnabledBackground = new Color32(243, 238, 251, 255);
        private static readonly Color32 DisabledBackground = new Color32(253, 234, 240, 255);
        private static readonly Color32 UnavailableBackground = new Color32(237, 233, 242, 255);
        private static readonly Color32 EnabledIcon = new Color32(94, 53, 177, 255);
        private static readonly Color32 DisabledIcon = new Color32(161, 64, 94, 255);
        private static readonly Color32 EnabledAccent = new Color32(53, 167, 134, 255);
        private static readonly Color32 DisabledAccent = new Color32(206, 88, 116, 255);

        private readonly List<Object> generatedAssets = new List<Object>();

        private VoiceChatService voice;
        private Button micButton;
        private Button speakerButton;
        private Image micBackground;
        private Image speakerBackground;
        private Image micIcon;
        private Image speakerIcon;
        private Image micStateDot;
        private Image speakerStateDot;
        private Outline micOutline;
        private Outline speakerOutline;
        private TMP_Text statusLabel;

        private Sprite circleSprite;
        private Sprite microphoneOnSprite;
        private Sprite microphoneOffSprite;
        private Sprite speakerOnSprite;
        private Sprite speakerOffSprite;

        public static VoiceControlView Create(Transform parent)
        {
            if (parent == null) return null;

            var root = new GameObject("VoiceControls", typeof(RectTransform));
            root.transform.SetParent(parent, false);
            root.transform.SetAsLastSibling();
            var view = root.AddComponent<VoiceControlView>();
            view.Build();
            return view;
        }

        public void Bind(VoiceChatService service)
        {
            if (voice != null) voice.Changed -= Refresh;
            voice = service;
            if (voice != null) voice.Changed += Refresh;
            Refresh();
        }

        private void Build()
        {
            CreateSprites();

            var rect = GetComponent<RectTransform>();
            rect.anchorMin = new Vector2(0f, 1f);
            rect.anchorMax = new Vector2(0f, 1f);
            rect.pivot = new Vector2(0f, 1f);
            rect.anchoredPosition = new Vector2(1436f, -44f);
            rect.sizeDelta = new Vector2(120f, 54f);

            speakerButton = CreateRoundButton(
                "SpeakerButton",
                Vector2.zero,
                speakerOnSprite,
                out speakerBackground,
                out speakerIcon,
                out speakerStateDot,
                out speakerOutline);

            micButton = CreateRoundButton(
                "MicrophoneButton",
                new Vector2(66f, 0f),
                microphoneOffSprite,
                out micBackground,
                out micIcon,
                out micStateDot,
                out micOutline);

            speakerButton.onClick.AddListener(() => voice?.ToggleSpeaker());
            micButton.onClick.AddListener(() => voice?.ToggleMic());

            var statusObject = new GameObject(
                "VoiceStatus",
                typeof(RectTransform),
                typeof(CanvasRenderer),
                typeof(TextMeshProUGUI));
            statusObject.transform.SetParent(transform, false);
            var statusRect = statusObject.GetComponent<RectTransform>();
            statusRect.anchorMin = new Vector2(1f, 1f);
            statusRect.anchorMax = new Vector2(1f, 1f);
            statusRect.pivot = new Vector2(1f, 1f);
            statusRect.anchoredPosition = new Vector2(0f, -60f);
            statusRect.sizeDelta = new Vector2(180f, 20f);

            statusLabel = statusObject.GetComponent<TextMeshProUGUI>();
            TMP_FontAsset font = Resources.Load<TMP_FontAsset>("Fonts/Ludo Vietnamese SDF");
            if (font != null) statusLabel.font = font;
            statusLabel.fontSize = 10.5f;
            statusLabel.fontStyle = FontStyles.Bold;
            statusLabel.color = new Color32(117, 102, 143, 255);
            statusLabel.alignment = TextAlignmentOptions.Right;
            statusLabel.textWrappingMode = TextWrappingModes.NoWrap;
            statusLabel.overflowMode = TextOverflowModes.Ellipsis;
            statusLabel.raycastTarget = false;
        }

        private Button CreateRoundButton(
            string name,
            Vector2 position,
            Sprite initialIcon,
            out Image background,
            out Image icon,
            out Image stateDot,
            out Outline outline)
        {
            var buttonObject = new GameObject(
                name,
                typeof(RectTransform),
                typeof(CanvasRenderer),
                typeof(Image),
                typeof(Shadow),
                typeof(Outline),
                typeof(Button));
            buttonObject.transform.SetParent(transform, false);

            var rect = buttonObject.GetComponent<RectTransform>();
            rect.anchorMin = new Vector2(0f, 1f);
            rect.anchorMax = new Vector2(0f, 1f);
            rect.pivot = new Vector2(0f, 1f);
            rect.anchoredPosition = position;
            rect.sizeDelta = new Vector2(54f, 54f);

            background = buttonObject.GetComponent<Image>();
            background.sprite = circleSprite;
            background.color = DisabledBackground;
            background.type = Image.Type.Simple;

            var shadow = buttonObject.GetComponent<Shadow>();
            shadow.effectColor = new Color32(92, 65, 136, 24);
            shadow.effectDistance = new Vector2(0f, -2f);
            shadow.useGraphicAlpha = true;

            outline = buttonObject.GetComponent<Outline>();
            outline.effectColor = new Color32(218, 207, 238, 200);
            outline.effectDistance = new Vector2(1f, 1f);
            outline.useGraphicAlpha = true;

            var button = buttonObject.GetComponent<Button>();
            button.targetGraphic = background;
            button.navigation = new Navigation { mode = Navigation.Mode.None };
            button.transition = Selectable.Transition.ColorTint;
            button.colors = new ColorBlock
            {
                normalColor = Color.white,
                highlightedColor = new Color(1.08f, 1.08f, 1.08f, 1f),
                pressedColor = new Color(0.76f, 0.79f, 0.8f, 1f),
                selectedColor = Color.white,
                disabledColor = Color.white,
                colorMultiplier = 1f,
                fadeDuration = 0.08f
            };

            var iconObject = new GameObject("Icon", typeof(RectTransform), typeof(CanvasRenderer), typeof(Image));
            iconObject.transform.SetParent(buttonObject.transform, false);
            var iconRect = iconObject.GetComponent<RectTransform>();
            iconRect.anchorMin = new Vector2(0.5f, 0.5f);
            iconRect.anchorMax = new Vector2(0.5f, 0.5f);
            iconRect.pivot = new Vector2(0.5f, 0.5f);
            iconRect.anchoredPosition = Vector2.zero;
            iconRect.sizeDelta = new Vector2(30f, 30f);
            icon = iconObject.GetComponent<Image>();
            icon.sprite = initialIcon;
            icon.color = DisabledIcon;
            icon.preserveAspect = true;
            icon.raycastTarget = false;

            var dotObject = new GameObject("StateDot", typeof(RectTransform), typeof(CanvasRenderer), typeof(Image));
            dotObject.transform.SetParent(buttonObject.transform, false);
            var dotRect = dotObject.GetComponent<RectTransform>();
            dotRect.anchorMin = new Vector2(1f, 1f);
            dotRect.anchorMax = new Vector2(1f, 1f);
            dotRect.pivot = new Vector2(0.5f, 0.5f);
            dotRect.anchoredPosition = new Vector2(-7f, -7f);
            dotRect.sizeDelta = new Vector2(11f, 11f);
            stateDot = dotObject.GetComponent<Image>();
            stateDot.sprite = circleSprite;
            stateDot.color = DisabledAccent;
            stateDot.raycastTarget = false;

            return button;
        }

        private void Refresh()
        {
            if (voice == null || micButton == null) return;

            bool connected = voice.State == VoiceConnectionState.Connected;
            micButton.interactable = connected;
            speakerButton.interactable = connected;

            ApplyState(
                micBackground,
                micIcon,
                micStateDot,
                micOutline,
                voice.MicEnabled,
                connected,
                microphoneOnSprite,
                microphoneOffSprite);
            ApplyState(
                speakerBackground,
                speakerIcon,
                speakerStateDot,
                speakerOutline,
                voice.SpeakerEnabled,
                connected,
                speakerOnSprite,
                speakerOffSprite);

            switch (voice.State)
            {
                case VoiceConnectionState.Connecting:
                    statusLabel.text = "ĐANG KẾT NỐI THOẠI...";
                    statusLabel.color = new Color32(145, 98, 21, 255);
                    break;
                case VoiceConnectionState.Failed:
                    statusLabel.text = "LỖI KẾT NỐI THOẠI";
                    statusLabel.color = DisabledAccent;
                    break;
                case VoiceConnectionState.Stopped:
                    statusLabel.text = "THOẠI CHƯA KẾT NỐI";
                    statusLabel.color = new Color32(117, 102, 143, 255);
                    break;
                default:
                    statusLabel.text = string.Empty;
                    break;
            }
        }

        private static void ApplyState(
            Image background,
            Image icon,
            Image stateDot,
            Outline outline,
            bool enabled,
            bool connected,
            Sprite onSprite,
            Sprite offSprite)
        {
            icon.sprite = enabled ? onSprite : offSprite;

            if (!connected)
            {
                background.color = UnavailableBackground;
                icon.color = new Color32(139, 128, 154, 255);
                stateDot.color = new Color32(159, 149, 171, 255);
                outline.effectColor = new Color32(218, 210, 229, 200);
                return;
            }

            background.color = enabled ? EnabledBackground : DisabledBackground;
            icon.color = enabled ? EnabledIcon : DisabledIcon;
            stateDot.color = enabled ? EnabledAccent : DisabledAccent;
            outline.effectColor = enabled
                ? new Color32(218, 207, 238, 200)
                : new Color32(240, 202, 214, 220);
        }

        private void CreateSprites()
        {
            circleSprite = CreateCircleSprite(96);
            microphoneOnSprite = CreateMicrophoneSprite(false);
            microphoneOffSprite = CreateMicrophoneSprite(true);
            speakerOnSprite = CreateSpeakerSprite(false);
            speakerOffSprite = CreateSpeakerSprite(true);
        }

        private Sprite CreateCircleSprite(int size)
        {
            var texture = NewTexture(size, size, "VoiceCircleTexture");
            float radius = size * 0.5f;
            var center = new Vector2(radius, radius);
            for (int y = 0; y < size; y++)
            {
                for (int x = 0; x < size; x++)
                {
                    float distance = Vector2.Distance(new Vector2(x + 0.5f, y + 0.5f), center);
                    float alpha = Mathf.Clamp01(radius - distance + 0.5f);
                    texture.SetPixel(x, y, new Color(1f, 1f, 1f, alpha));
                }
            }
            return FinishSprite(texture, "VoiceCircle");
        }

        private Sprite CreateMicrophoneSprite(bool muted)
        {
            const int size = 64;
            var texture = NewTexture(size, size, muted ? "MicrophoneOffTexture" : "MicrophoneOnTexture");
            for (int y = 0; y < size; y++)
            {
                for (int x = 0; x < size; x++)
                {
                    var point = new Vector2(x + 0.5f, y + 0.5f);
                    float alpha = 0f;

                    if (DistanceToVerticalCapsule(point, new Vector2(32f, 25f), new Vector2(32f, 44f), 7f) <= 0.6f)
                        alpha = 1f;

                    float bowlDistance = Mathf.Abs(Vector2.Distance(point, new Vector2(32f, 31f)) - 15.5f);
                    if (point.y <= 31f && point.y >= 14f && bowlDistance <= 1.8f)
                        alpha = Mathf.Max(alpha, 1f - Mathf.Max(0f, bowlDistance - 0.8f));
                    if ((Mathf.Abs(point.x - 16.5f) <= 1.6f || Mathf.Abs(point.x - 47.5f) <= 1.6f)
                        && point.y >= 30f && point.y <= 35f)
                        alpha = 1f;

                    if (DistanceToSegment(point, new Vector2(32f, 14f), new Vector2(32f, 8f)) <= 1.8f)
                        alpha = 1f;
                    if (DistanceToSegment(point, new Vector2(23f, 8f), new Vector2(41f, 8f)) <= 1.8f)
                        alpha = 1f;

                    if (muted && DistanceToSegment(point, new Vector2(12f, 52f), new Vector2(52f, 12f)) <= 2.5f)
                        alpha = 1f;

                    texture.SetPixel(x, y, new Color(1f, 1f, 1f, Mathf.Clamp01(alpha)));
                }
            }
            return FinishSprite(texture, muted ? "MicrophoneOff" : "MicrophoneOn");
        }

        private Sprite CreateSpeakerSprite(bool muted)
        {
            const int size = 64;
            var texture = NewTexture(size, size, muted ? "SpeakerOffTexture" : "SpeakerOnTexture");
            for (int y = 0; y < size; y++)
            {
                for (int x = 0; x < size; x++)
                {
                    var point = new Vector2(x + 0.5f, y + 0.5f);
                    float alpha = 0f;

                    if (x >= 10 && x <= 20 && y >= 25 && y <= 39)
                        alpha = 1f;

                    if (x >= 20 && x <= 34)
                    {
                        float halfHeight = 8f + (x - 20) * 0.72f;
                        if (Mathf.Abs(point.y - 32f) <= halfHeight)
                            alpha = 1f;
                    }

                    if (!muted)
                    {
                        float distance = Vector2.Distance(point, new Vector2(31f, 32f));
                        if (x >= 37 && Mathf.Abs(distance - 11f) <= 1.5f)
                            alpha = Mathf.Max(alpha, 1f - Mathf.Max(0f, Mathf.Abs(distance - 11f) - 0.5f));
                        if (x >= 40 && Mathf.Abs(distance - 18f) <= 1.5f)
                            alpha = Mathf.Max(alpha, 1f - Mathf.Max(0f, Mathf.Abs(distance - 18f) - 0.5f));
                    }
                    else if (DistanceToSegment(point, new Vector2(12f, 52f), new Vector2(52f, 12f)) <= 2.5f)
                    {
                        alpha = 1f;
                    }

                    texture.SetPixel(x, y, new Color(1f, 1f, 1f, Mathf.Clamp01(alpha)));
                }
            }
            return FinishSprite(texture, muted ? "SpeakerOff" : "SpeakerOn");
        }

        private Texture2D NewTexture(int width, int height, string assetName)
        {
            var texture = new Texture2D(width, height, TextureFormat.RGBA32, false)
            {
                name = assetName,
                wrapMode = TextureWrapMode.Clamp,
                filterMode = FilterMode.Bilinear,
                hideFlags = HideFlags.HideAndDontSave
            };
            generatedAssets.Add(texture);
            return texture;
        }

        private Sprite FinishSprite(Texture2D texture, string assetName)
        {
            texture.Apply(false, true);
            var sprite = Sprite.Create(
                texture,
                new Rect(0f, 0f, texture.width, texture.height),
                new Vector2(0.5f, 0.5f),
                100f,
                0,
                SpriteMeshType.FullRect);
            sprite.name = assetName;
            sprite.hideFlags = HideFlags.HideAndDontSave;
            generatedAssets.Add(sprite);
            return sprite;
        }

        private static float DistanceToSegment(Vector2 point, Vector2 start, Vector2 end)
        {
            Vector2 segment = end - start;
            float lengthSquared = segment.sqrMagnitude;
            if (lengthSquared <= Mathf.Epsilon) return Vector2.Distance(point, start);
            float t = Mathf.Clamp01(Vector2.Dot(point - start, segment) / lengthSquared);
            return Vector2.Distance(point, start + segment * t);
        }

        private static float DistanceToVerticalCapsule(Vector2 point, Vector2 start, Vector2 end, float radius)
        {
            return DistanceToSegment(point, start, end) - radius;
        }

        private void OnDestroy()
        {
            if (voice != null) voice.Changed -= Refresh;
            foreach (Object asset in generatedAssets)
            {
                if (asset != null) Destroy(asset);
            }
            generatedAssets.Clear();
        }
    }
}
