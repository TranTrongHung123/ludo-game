using System;
using Ludo.Services;
using TMPro;
using UnityEngine;
using UnityEngine.EventSystems;
using UnityEngine.InputSystem;
using UnityEngine.SceneManagement;
using UnityEngine.UI;

namespace Ludo.Views
{
    public sealed class AudioControlView : MonoBehaviour
    {
        private Canvas canvas;
        private CanvasScaler scaler;
        private GraphicRaycaster raycaster;

        private RectTransform buttonRect;
        private Image buttonBackground;
        private Image buttonIcon;
        private TMP_Text buttonText;
        private Button toggleButton;
        private Outline buttonOutline;

        private GameObject panelRoot;
        private RectTransform panelRect;
        private TMP_Text masterValueText;
        private TMP_Text musicValueText;
        private TMP_Text sfxValueText;
        private TMP_Text muteStatusText;
        private Image muteButtonImage;
        private Image muteStatusIcon;
        private Slider masterSlider;
        private Slider musicSlider;
        private Slider sfxSlider;

        private TMP_FontAsset vietnameseFont;
        private Sprite roundedSprite;
        private Sprite circleSprite;
        private Sprite flatSprite;
        private Sprite speakerOnSprite;
        private Sprite speakerMutedSprite;
        private Sprite closeIconSprite;

        private bool panelOpen;
        private AudioManager audioManager;
        private bool isInitialized;
        private bool eventsHooked;

        public void InitIfNeeded()
        {
            if (!isInitialized)
            {
                isInitialized = true;
                audioManager = AudioManager.EnsureInstance();
                CreateSprites();
                LoadFont();
                BuildUi();
                UpdatePositionForScene(SceneManager.GetActiveScene().name);
                RefreshDisplay();
            }

            if (!eventsHooked)
            {
                eventsHooked = true;
                SceneManager.sceneLoaded += OnSceneLoaded;
                if (audioManager != null)
                {
                    audioManager.MuteChanged += OnMuteChanged;
                    audioManager.VolumeChanged += OnVolumeChanged;
                }
            }
        }

        private void Awake()
        {
            InitIfNeeded();
        }

        private void Start()
        {
            InitIfNeeded();
        }

        private void OnDestroy()
        {
            if (eventsHooked)
            {
                eventsHooked = false;
                SceneManager.sceneLoaded -= OnSceneLoaded;
                if (audioManager != null)
                {
                    audioManager.MuteChanged -= OnMuteChanged;
                    audioManager.VolumeChanged -= OnVolumeChanged;
                }
            }
        }

        private void Update()
        {
            // Keyboard shortcut 'M' to toggle mute
            if (Keyboard.current != null && Keyboard.current.mKey.wasPressedThisFrame)
            {
                // Only if not currently typing in a TMP_InputField
                var selected = EventSystem.current?.currentSelectedGameObject;
                if (selected == null || selected.GetComponent<TMP_InputField>() == null)
                {
                    audioManager?.ToggleMute();
                }
            }

            // Close panel on Escape
            if (panelOpen && Keyboard.current != null && Keyboard.current.escapeKey.wasPressedThisFrame)
            {
                SetPanelOpen(false);
            }
        }

        private void OnSceneLoaded(Scene scene, LoadSceneMode mode)
        {
            UpdatePositionForScene(scene.name);
            SetPanelOpen(false);
            RefreshDisplay();
        }

        private void CreateSprites()
        {
            // 1. 9-sliced rounded rectangle sprite (64x64, radius 16)
            int size = 64;
            int r = 16;
            var texRounded = new Texture2D(size, size, TextureFormat.RGBA32, false);
            texRounded.wrapMode = TextureWrapMode.Clamp;
            texRounded.filterMode = FilterMode.Bilinear;

            for (int y = 0; y < size; y++)
            {
                for (int x = 0; x < size; x++)
                {
                    int dx = x < r ? r - x : (x >= size - r ? x - (size - r - 1) : 0);
                    int dy = y < r ? r - y : (y >= size - r ? y - (size - r - 1) : 0);
                    float distSq = dx * dx + dy * dy;
                    if (distSq <= r * r)
                    {
                        float alpha = Mathf.Clamp01(r - Mathf.Sqrt(distSq) + 0.5f);
                        texRounded.SetPixel(x, y, new Color(1f, 1f, 1f, alpha));
                    }
                    else
                    {
                        texRounded.SetPixel(x, y, Color.clear);
                    }
                }
            }
            texRounded.Apply();
            roundedSprite = Sprite.Create(texRounded, new Rect(0, 0, size, size), new Vector2(0.5f, 0.5f), 100f, 0, SpriteMeshType.FullRect, new Vector4(r, r, r, r));

            // 2. Flat white sprite
            var texFlat = new Texture2D(4, 4, TextureFormat.RGBA32, false);
            for (int y = 0; y < 4; y++) for (int x = 0; x < 4; x++) texFlat.SetPixel(x, y, Color.white);
            texFlat.Apply();
            flatSprite = Sprite.Create(texFlat, new Rect(0, 0, 4, 4), new Vector2(0.5f, 0.5f));

            // 3. Circle sprite (48x48)
            int cSize = 48;
            float cRadius = cSize / 2f;
            var texCircle = new Texture2D(cSize, cSize, TextureFormat.RGBA32, false);
            texCircle.wrapMode = TextureWrapMode.Clamp;
            texCircle.filterMode = FilterMode.Bilinear;
            for (int y = 0; y < cSize; y++)
            {
                for (int x = 0; x < cSize; x++)
                {
                    float dist = Vector2.Distance(new Vector2(x + 0.5f, y + 0.5f), new Vector2(cRadius, cRadius));
                    float alpha = Mathf.Clamp01(cRadius - dist + 0.5f);
                    texCircle.SetPixel(x, y, new Color(1f, 1f, 1f, alpha));
                }
            }
            texCircle.Apply();
            circleSprite = Sprite.Create(texCircle, new Rect(0, 0, cSize, cSize), new Vector2(0.5f, 0.5f));

            // 4. Speaker On Sprite (32x32)
            speakerOnSprite = CreateSpeakerTexture(false);

            // 5. Speaker Muted Sprite (32x32)
            speakerMutedSprite = CreateSpeakerTexture(true);

            // 6. Close X Icon Sprite (32x32)
            closeIconSprite = CreateCloseTexture();
        }

        private Sprite CreateSpeakerTexture(bool muted)
        {
            int s = 32;
            var tex = new Texture2D(s, s, TextureFormat.RGBA32, false);
            tex.wrapMode = TextureWrapMode.Clamp;
            tex.filterMode = FilterMode.Bilinear;

            for (int y = 0; y < s; y++)
            {
                for (int x = 0; x < s; x++)
                {
                    float alpha = 0f;

                    // Thân chữ nhật của loa: x in [5, 11], y in [11, 20]
                    if (x >= 5 && x <= 11 && y >= 11 && y <= 20)
                    {
                        alpha = 1f;
                    }
                    // Vành loa mở rộng (hình nón): từ x=11 đến x=18, y từ [11 - (x-11)*1.3, 20 + (x-11)*1.3]
                    else if (x >= 11 && x <= 18)
                    {
                        float spread = (x - 11) * 1.35f;
                        if (y >= 11 - spread && y <= 20 + spread)
                        {
                            alpha = 1f;
                        }
                    }

                    if (!muted)
                    {
                        // Sóng âm thanh 1: arc nhỏ quanh tâm (13, 15.5)
                        float d1 = Vector2.Distance(new Vector2(x, y), new Vector2(13, 15.5f));
                        if (d1 >= 7.5f && d1 <= 9.5f && x > 18 && y >= 10 && y <= 21)
                        {
                            alpha = Mathf.Max(alpha, 1f - Mathf.Abs(d1 - 8.5f));
                        }
                        // Sóng âm thanh 2: arc lớn hơn
                        float d2 = Vector2.Distance(new Vector2(x, y), new Vector2(13, 15.5f));
                        if (d2 >= 12f && d2 <= 14f && x > 20 && y >= 7 && y <= 24)
                        {
                            alpha = Mathf.Max(alpha, 1f - Mathf.Abs(d2 - 13f) * 0.9f);
                        }
                    }
                    else
                    {
                        // Đường gạch chéo cho muted: từ góc trên trái (6, 26) đến góc dưới phải (26, 6)
                        float lineDist = Mathf.Abs((x - 6) - (26 - y)) / 1.414f;
                        if (lineDist <= 1.5f && x >= 5 && x <= 27 && y >= 5 && y <= 27)
                        {
                            alpha = Mathf.Max(alpha, Mathf.Clamp01(1.5f - lineDist));
                        }
                    }

                    tex.SetPixel(x, y, new Color(1f, 1f, 1f, Mathf.Clamp01(alpha)));
                }
            }
            tex.Apply();
            return Sprite.Create(tex, new Rect(0, 0, s, s), new Vector2(0.5f, 0.5f));
        }

        private Sprite CreateCloseTexture()
        {
            int s = 32;
            var tex = new Texture2D(s, s, TextureFormat.RGBA32, false);
            tex.wrapMode = TextureWrapMode.Clamp;
            tex.filterMode = FilterMode.Bilinear;

            for (int y = 0; y < s; y++)
            {
                for (int x = 0; x < s; x++)
                {
                    float d1 = Mathf.Abs(x - y);
                    float d2 = Mathf.Abs(x - (31 - y));
                    float minD = Mathf.Min(d1, d2);
                    float alpha = 0f;
                    if (minD <= 1.6f && x >= 9 && x <= 22 && y >= 9 && y <= 22)
                    {
                        alpha = Mathf.Clamp01(1.6f - minD);
                    }
                    tex.SetPixel(x, y, new Color(1f, 1f, 1f, alpha));
                }
            }
            tex.Apply();
            return Sprite.Create(tex, new Rect(0, 0, s, s), new Vector2(0.5f, 0.5f));
        }

        private void LoadFont()
        {
            vietnameseFont = Resources.Load<TMP_FontAsset>("Fonts/Ludo Vietnamese SDF");
            if (vietnameseFont == null)
            {
                vietnameseFont = TMP_Settings.defaultFontAsset;
            }
        }

        private void BuildUi()
        {
            // Canvas setup
            canvas = gameObject.AddComponent<Canvas>();
            canvas.renderMode = RenderMode.ScreenSpaceOverlay;
            canvas.sortingOrder = 950; // Above scene UI, below critical dialogs

            scaler = gameObject.AddComponent<CanvasScaler>();
            scaler.uiScaleMode = CanvasScaler.ScaleMode.ScaleWithScreenSize;
            scaler.referenceResolution = new Vector2(1920, 1080);
            scaler.screenMatchMode = CanvasScaler.ScreenMatchMode.MatchWidthOrHeight;
            scaler.matchWidthOrHeight = 0.5f;

            raycaster = gameObject.AddComponent<GraphicRaycaster>();

            // Root container
            var rootGo = new GameObject("Root", typeof(RectTransform));
            rootGo.transform.SetParent(transform, false);
            var rootRect = rootGo.GetComponent<RectTransform>();
            rootRect.anchorMin = Vector2.zero;
            rootRect.anchorMax = Vector2.one;
            rootRect.sizeDelta = Vector2.zero;
            rootRect.anchoredPosition = Vector2.zero;

            // Audio Toggle / Settings Button (Góc trên màn hình)
            var btnGo = new GameObject("AudioButton", typeof(RectTransform), typeof(CanvasRenderer), typeof(Image), typeof(Button));
            btnGo.transform.SetParent(rootRect, false);
            buttonRect = btnGo.GetComponent<RectTransform>();
            buttonRect.anchorMin = new Vector2(1, 1);
            buttonRect.anchorMax = new Vector2(1, 1);
            buttonRect.pivot = new Vector2(1, 1);
            buttonRect.sizeDelta = new Vector2(132, 36);
            buttonRect.anchoredPosition = new Vector2(-40, -16);

            buttonBackground = btnGo.GetComponent<Image>();
            buttonBackground.sprite = roundedSprite;
            buttonBackground.type = Image.Type.Sliced;
            buttonBackground.color = new Color32(243, 238, 251, 245); // Soft lavender

            buttonOutline = btnGo.AddComponent<Outline>();
            buttonOutline.effectColor = new Color32(218, 207, 238, 200);
            buttonOutline.effectDistance = new Vector2(1f, -1f);

            toggleButton = btnGo.GetComponent<Button>();
            toggleButton.targetGraphic = buttonBackground;
            var colors = toggleButton.colors;
            colors.normalColor = Color.white;
            colors.highlightedColor = new Color32(230, 220, 248, 255);
            colors.pressedColor = new Color32(214, 202, 238, 255);
            toggleButton.colors = colors;
            toggleButton.onClick.AddListener(() => SetPanelOpen(!panelOpen));

            // Icon Loa bên trái của nút AudioButton (không dùng emoji font)
            var iconGo = new GameObject("Icon", typeof(RectTransform), typeof(CanvasRenderer), typeof(Image));
            iconGo.transform.SetParent(btnGo.transform, false);
            var iconRect = iconGo.GetComponent<RectTransform>();
            iconRect.anchorMin = new Vector2(0, 0.5f);
            iconRect.anchorMax = new Vector2(0, 0.5f);
            iconRect.pivot = new Vector2(0, 0.5f);
            iconRect.anchoredPosition = new Vector2(12, 0);
            iconRect.sizeDelta = new Vector2(20, 20);

            buttonIcon = iconGo.GetComponent<Image>();
            buttonIcon.sprite = speakerOnSprite;
            buttonIcon.color = new Color32(94, 53, 177, 255);
            buttonIcon.preserveAspect = true;
            buttonIcon.raycastTarget = false;

            // Nhãn text của nút AudioButton
            var textGo = new GameObject("Text", typeof(RectTransform), typeof(CanvasRenderer), typeof(TextMeshProUGUI));
            textGo.transform.SetParent(btnGo.transform, false);
            var textRect = textGo.GetComponent<RectTransform>();
            textRect.anchorMin = new Vector2(0, 0.5f);
            textRect.anchorMax = new Vector2(1, 0.5f);
            textRect.pivot = new Vector2(0, 0.5f);
            textRect.anchoredPosition = new Vector2(38, 0);
            textRect.sizeDelta = new Vector2(-46, 28);

            buttonText = textGo.GetComponent<TextMeshProUGUI>();
            if (vietnameseFont != null) buttonText.font = vietnameseFont;
            buttonText.fontSize = 15;
            buttonText.fontStyle = FontStyles.Bold;
            buttonText.alignment = TextAlignmentOptions.MidlineLeft;
            buttonText.color = new Color32(94, 53, 177, 255); // Deep purple
            buttonText.text = "Âm thanh";
            buttonText.raycastTarget = false;

            // -------------------------------------------------------------
            // Backdrop Container (Giữ tên "Backdrop" để backward compatible với test script)
            // CỰC KỲ QUAN TRỌNG: Container này KHÔNG chứa component Button, để tránh bubble click từ Panel!
            // -------------------------------------------------------------
            var backdropContainer = new GameObject("Backdrop", typeof(RectTransform));
            backdropContainer.transform.SetParent(rootRect, false);
            var bdcRect = backdropContainer.GetComponent<RectTransform>();
            bdcRect.anchorMin = Vector2.zero;
            bdcRect.anchorMax = Vector2.one;
            bdcRect.sizeDelta = Vector2.zero;
            bdcRect.anchoredPosition = Vector2.zero;
            panelRoot = backdropContainer;
            panelRoot.SetActive(false);

            // ClickCatcher (Con thứ 1 - Sibling nằm dưới Panel): Click ngoài Panel để đóng
            var clickCatcherGo = new GameObject("ClickCatcher", typeof(RectTransform), typeof(CanvasRenderer), typeof(Image), typeof(Button));
            clickCatcherGo.transform.SetParent(backdropContainer.transform, false);
            var ccRect = clickCatcherGo.GetComponent<RectTransform>();
            ccRect.anchorMin = Vector2.zero;
            ccRect.anchorMax = Vector2.one;
            ccRect.sizeDelta = Vector2.zero;

            var ccImg = clickCatcherGo.GetComponent<Image>();
            ccImg.color = new Color(0, 0, 0, 0.35f); // Làm mờ cảnh nền phía sau
            ccImg.raycastTarget = true;

            var ccBtn = clickCatcherGo.GetComponent<Button>();
            ccBtn.transition = Selectable.Transition.None;
            ccBtn.onClick.AddListener(() => SetPanelOpen(false));

            // -------------------------------------------------------------
            // Audio Settings Modal Panel (Con thứ 2 - Sibling nằm đè lên ClickCatcher)
            // Vì Panel là anh em với ClickCatcher, click trong Panel KHÔNG BAO GIỜ bị đóng modal!
            // -------------------------------------------------------------
            var pnlGo = new GameObject("Panel", typeof(RectTransform), typeof(CanvasRenderer), typeof(Image));
            pnlGo.transform.SetParent(backdropContainer.transform, false);
            panelRect = pnlGo.GetComponent<RectTransform>();
            panelRect.anchorMin = new Vector2(1, 1);
            panelRect.anchorMax = new Vector2(1, 1);
            panelRect.pivot = new Vector2(1, 1);
            panelRect.sizeDelta = new Vector2(390, 440);
            panelRect.anchoredPosition = new Vector2(-40, -92);

            var pnlImage = pnlGo.GetComponent<Image>();
            pnlImage.sprite = roundedSprite;
            pnlImage.type = Image.Type.Sliced;
            pnlImage.color = Color.white;
            pnlImage.raycastTarget = true; // Chặn raycast không xuyên xuống ClickCatcher

            // Viền nhẹ cho Panel để thêm phần cao cấp
            var outline = pnlGo.AddComponent<Outline>();
            outline.effectColor = new Color32(226, 217, 243, 255);
            outline.effectDistance = new Vector2(1.5f, -1.5f);

            // Tiêu đề Panel
            CreateText(pnlGo.transform, "Title", "CÀI ĐẶT ÂM THANH", 20, FontStyles.Bold, new Color32(44, 38, 64, 255),
                new Vector2(24, -20), new Vector2(270, 36), TextAlignmentOptions.Left);

            // Nút Đóng hình tròn (X) ở góc phải
            var closeBtnGo = new GameObject("CloseButton", typeof(RectTransform), typeof(CanvasRenderer), typeof(Image), typeof(Button));
            closeBtnGo.transform.SetParent(pnlGo.transform, false);
            var closeRect = closeBtnGo.GetComponent<RectTransform>();
            closeRect.anchorMin = new Vector2(1, 1);
            closeRect.anchorMax = new Vector2(1, 1);
            closeRect.pivot = new Vector2(1, 1);
            closeRect.sizeDelta = new Vector2(34, 34);
            closeRect.anchoredPosition = new Vector2(-20, -20);

            var closeBg = closeBtnGo.GetComponent<Image>();
            closeBg.sprite = circleSprite;
            closeBg.color = new Color32(242, 239, 248, 255);

            var closeBtn = closeBtnGo.GetComponent<Button>();
            closeBtn.targetGraphic = closeBg;
            var closeColors = closeBtn.colors;
            closeColors.highlightedColor = new Color32(228, 222, 240, 255);
            closeColors.pressedColor = new Color32(212, 204, 230, 255);
            closeBtn.colors = closeColors;
            closeBtn.onClick.AddListener(() => SetPanelOpen(false));

            var closeIconGo = new GameObject("Icon", typeof(RectTransform), typeof(CanvasRenderer), typeof(Image));
            closeIconGo.transform.SetParent(closeBtnGo.transform, false);
            var ciRect = closeIconGo.GetComponent<RectTransform>();
            ciRect.anchorMin = new Vector2(0.5f, 0.5f);
            ciRect.anchorMax = new Vector2(0.5f, 0.5f);
            ciRect.pivot = new Vector2(0.5f, 0.5f);
            ciRect.sizeDelta = new Vector2(16, 16);
            ciRect.anchoredPosition = Vector2.zero;

            var ciImg = closeIconGo.GetComponent<Image>();
            ciImg.sprite = closeIconSprite;
            ciImg.color = new Color32(117, 106, 138, 255);
            ciImg.preserveAspect = true;
            ciImg.raycastTarget = false;

            // Đường phân cách Divider
            CreateImage(pnlGo.transform, "Divider", new Color32(234, 229, 245, 255),
                new Vector2(24, -62), new Vector2(342, 1.5f), new Vector2(0, 1), new Vector2(0, 1), new Vector2(0, 1));

            // Section 1: Mute / Unmute Toggle Button
            CreateText(pnlGo.transform, "MuteLabel", "Trạng thái:", 17, FontStyles.Normal, new Color32(80, 72, 100, 255),
                new Vector2(24, -82), new Vector2(120, 36), TextAlignmentOptions.MidlineLeft);

            var muteBtnGo = new GameObject("MuteToggleButton", typeof(RectTransform), typeof(CanvasRenderer), typeof(Image), typeof(Button));
            muteBtnGo.transform.SetParent(pnlGo.transform, false);
            var mbRect = muteBtnGo.GetComponent<RectTransform>();
            mbRect.anchorMin = new Vector2(0, 1);
            mbRect.anchorMax = new Vector2(0, 1);
            mbRect.pivot = new Vector2(0, 1);
            mbRect.anchoredPosition = new Vector2(150, -80);
            mbRect.sizeDelta = new Vector2(216, 40);

            muteButtonImage = muteBtnGo.GetComponent<Image>();
            muteButtonImage.sprite = roundedSprite;
            muteButtonImage.type = Image.Type.Sliced;
            muteButtonImage.color = new Color32(39, 174, 96, 255); // Green unmuted

            var muteBtn = muteBtnGo.GetComponent<Button>();
            muteBtn.targetGraphic = muteButtonImage;
            muteBtn.onClick.AddListener(() =>
            {
                audioManager?.ToggleMute();
                RefreshDisplay();
            });

            // Icon bên trong nút MuteToggleButton
            var miGo = new GameObject("MuteIcon", typeof(RectTransform), typeof(CanvasRenderer), typeof(Image));
            miGo.transform.SetParent(muteBtnGo.transform, false);
            var miRect = miGo.GetComponent<RectTransform>();
            miRect.anchorMin = new Vector2(0, 0.5f);
            miRect.anchorMax = new Vector2(0, 0.5f);
            miRect.pivot = new Vector2(0, 0.5f);
            miRect.anchoredPosition = new Vector2(18, 0);
            miRect.sizeDelta = new Vector2(20, 20);

            muteStatusIcon = miGo.GetComponent<Image>();
            muteStatusIcon.sprite = speakerOnSprite;
            muteStatusIcon.color = Color.white;
            muteStatusIcon.preserveAspect = true;
            muteStatusIcon.raycastTarget = false;

            // Text bên trong nút MuteToggleButton
            var mtGo = new GameObject("Text", typeof(RectTransform), typeof(CanvasRenderer), typeof(TextMeshProUGUI));
            mtGo.transform.SetParent(muteBtnGo.transform, false);
            var mtRect = mtGo.GetComponent<RectTransform>();
            mtRect.anchorMin = new Vector2(0, 0);
            mtRect.anchorMax = new Vector2(1, 1);
            mtRect.pivot = new Vector2(0.5f, 0.5f);
            mtRect.anchoredPosition = new Vector2(12, 0);
            mtRect.sizeDelta = Vector2.zero;

            muteStatusText = mtGo.GetComponent<TextMeshProUGUI>();
            if (vietnameseFont != null) muteStatusText.font = vietnameseFont;
            muteStatusText.fontSize = 16;
            muteStatusText.fontStyle = FontStyles.Bold;
            muteStatusText.color = Color.white;
            muteStatusText.alignment = TextAlignmentOptions.Center;
            muteStatusText.text = "ĐANG BẬT";
            muteStatusText.raycastTarget = false;

            // Section 2: Master Volume
            CreateText(pnlGo.transform, "MasterLabel", "Âm lượng tổng:", 16, FontStyles.Normal, new Color32(80, 72, 100, 255),
                new Vector2(24, -135), new Vector2(180, 26), TextAlignmentOptions.MidlineLeft);
            masterValueText = CreateText(pnlGo.transform, "MasterValue", "80%", 16, FontStyles.Bold, new Color32(113, 73, 209, 255),
                new Vector2(-24, -135), new Vector2(80, 26), TextAlignmentOptions.MidlineRight, new Vector2(1, 1), new Vector2(1, 1), new Vector2(1, 1));
            masterSlider = CreateSlider(pnlGo.transform, "MasterSlider", new Vector2(24, -165), new Vector2(342, 36));
            masterSlider.onValueChanged.AddListener(val =>
            {
                audioManager?.SetMasterVolume(val);
                masterValueText.text = Mathf.RoundToInt(val * 100f) + "%";
            });

            // Section 3: Music Volume (BGM)
            CreateText(pnlGo.transform, "MusicLabel", "Nhạc nền (BGM):", 16, FontStyles.Normal, new Color32(80, 72, 100, 255),
                new Vector2(24, -212), new Vector2(180, 26), TextAlignmentOptions.MidlineLeft);
            musicValueText = CreateText(pnlGo.transform, "MusicValue", "35%", 16, FontStyles.Bold, new Color32(113, 73, 209, 255),
                new Vector2(-24, -212), new Vector2(80, 26), TextAlignmentOptions.MidlineRight, new Vector2(1, 1), new Vector2(1, 1), new Vector2(1, 1));
            musicSlider = CreateSlider(pnlGo.transform, "MusicSlider", new Vector2(24, -242), new Vector2(342, 36));
            musicSlider.onValueChanged.AddListener(val =>
            {
                audioManager?.SetMusicVolume(val);
                musicValueText.text = Mathf.RoundToInt(val * 100f) + "%";
            });

            // Section 4: SFX Volume
            CreateText(pnlGo.transform, "SfxLabel", "Hiệu ứng (SFX):", 16, FontStyles.Normal, new Color32(80, 72, 100, 255),
                new Vector2(24, -289), new Vector2(180, 26), TextAlignmentOptions.MidlineLeft);
            sfxValueText = CreateText(pnlGo.transform, "SfxValue", "80%", 16, FontStyles.Bold, new Color32(113, 73, 209, 255),
                new Vector2(-24, -289), new Vector2(80, 26), TextAlignmentOptions.MidlineRight, new Vector2(1, 1), new Vector2(1, 1), new Vector2(1, 1));
            sfxSlider = CreateSlider(pnlGo.transform, "SfxSlider", new Vector2(24, -319), new Vector2(342, 36));
            sfxSlider.onValueChanged.AddListener(val =>
            {
                audioManager?.SetSfxVolume(val);
                sfxValueText.text = Mathf.RoundToInt(val * 100f) + "%";
            });

            // Footer note
            CreateText(pnlGo.transform, "Footer", "Mẹo: Nhấn phím 'M' để tắt/bật nhanh âm thanh", 14, FontStyles.Italic, new Color32(150, 142, 170, 255),
                new Vector2(24, -380), new Vector2(342, 26), TextAlignmentOptions.Center);
        }

        private void UpdatePositionForScene(string sceneName)
        {
            if (buttonRect == null || panelRect == null) return;

            buttonRect.anchorMin = new Vector2(1, 1);
            buttonRect.anchorMax = new Vector2(1, 1);
            buttonRect.pivot = new Vector2(1, 1);

            panelRect.anchorMin = new Vector2(1, 1);
            panelRect.anchorMax = new Vector2(1, 1);
            panelRect.pivot = new Vector2(1, 1);

            switch (sceneName)
            {
                case "GameScene":
                    // Trong GameScene, LeaveButton nằm ở X: 1710..1872, Y: -44..-98, StatusFrame ở Y: -44..-89.
                    // Đặt AudioButton ở dải trên (Y: -6..-38) tại X: -215 để vừa < -200f (tránh LeaveButton),
                    // vừa hoàn toàn nằm phía trên StatusFrame và LeaveButton mà không đè lên bất kỳ phần tử nào.
                    buttonRect.anchoredPosition = new Vector2(-215, -6);
                    buttonRect.sizeDelta = new Vector2(130, 32);
                    panelRect.anchoredPosition = new Vector2(-215, -44);
                    break;

                case "RoomScene":
                    // Trong RoomScene, Rời phòng (LeaveButton) nằm ở Y: -86..-150, X: 1600..1810.
                    // Đặt AudioButton ở góc trên bên phải (X: -40, Y: -18), chiều cao 36px (đáy -54).
                    // Cách đỉnh của nút Rời phòng tới 32px khoảng trống, hoàn toàn không bị che hay xung đột.
                    buttonRect.anchoredPosition = new Vector2(-40, -18);
                    buttonRect.sizeDelta = new Vector2(132, 36);
                    panelRect.anchoredPosition = new Vector2(-40, -60);
                    break;

                case "LobbyScene":
                    // Trong LobbyScene, Header card màu trắng nằm ở Y: -66..-194.
                    // Đặt AudioButton ở dải trên màn hình (X: -40, Y: -14), đáy ở -50.
                    // Cách mép trên của Header card 16px, không chạm vào card và không đè nút Đăng xuất.
                    buttonRect.anchoredPosition = new Vector2(-40, -14);
                    buttonRect.sizeDelta = new Vector2(132, 36);
                    panelRect.anchoredPosition = new Vector2(-40, -56);
                    break;

                case "RankingScene":
                case "HistoryScene":
                    // Trong RankingScene và HistoryScene, ConnectionBadge nằm ở Y: -48..-100.
                    // Đặt AudioButton ở Y: -8 (đáy -42), nằm phía trên ConnectionBadge.
                    buttonRect.anchoredPosition = new Vector2(-40, -8);
                    buttonRect.sizeDelta = new Vector2(132, 34);
                    panelRect.anchoredPosition = new Vector2(-40, -48);
                    break;

                case "ResultScene":
                    // Trong ResultScene, SyncBadge nằm ở Y: -45..-95.
                    // Đặt AudioButton ở Y: -6 (đáy -40), nằm phía trên SyncBadge.
                    buttonRect.anchoredPosition = new Vector2(-40, -6);
                    buttonRect.sizeDelta = new Vector2(132, 34);
                    panelRect.anchoredPosition = new Vector2(-40, -46);
                    break;

                default:
                    // LoginScene, RegisterScene hoặc các màn hình khác (góc trên phải hoàn toàn tự do)
                    buttonRect.anchoredPosition = new Vector2(-40, -18);
                    buttonRect.sizeDelta = new Vector2(132, 36);
                    panelRect.anchoredPosition = new Vector2(-40, -60);
                    break;
            }
        }

        public void SetPanelOpen(bool open)
        {
            panelOpen = open;
            if (panelRoot != null) panelRoot.SetActive(open);
            if (open)
            {
                RefreshDisplay();
                audioManager?.PlaySfx(SfxClip.ButtonClick);
            }
        }

        private void OnMuteChanged(bool muted) => RefreshDisplay();
        private void OnVolumeChanged() => RefreshDisplay();

        private void RefreshDisplay()
        {
            if (audioManager == null) return;

            bool muted = audioManager.IsMuted;

            // Nút AudioButton ở góc màn hình
            if (buttonText != null)
            {
                buttonText.text = muted ? "Đã tắt" : "Âm thanh";
                buttonText.color = muted ? new Color32(211, 47, 47, 255) : new Color32(94, 53, 177, 255);
            }

            if (buttonIcon != null)
            {
                buttonIcon.sprite = muted ? speakerMutedSprite : speakerOnSprite;
                buttonIcon.color = muted ? new Color32(211, 47, 47, 255) : new Color32(94, 53, 177, 255);
            }

            if (buttonBackground != null)
            {
                buttonBackground.color = muted ? new Color32(255, 235, 238, 245) : new Color32(243, 238, 251, 245);
            }

            if (buttonOutline != null)
            {
                buttonOutline.effectColor = muted ? new Color32(255, 205, 210, 200) : new Color32(218, 207, 238, 200);
            }

            // Nút MuteToggleButton trong Panel
            if (muteStatusText != null)
            {
                muteStatusText.text = muted ? "ĐÃ TẮT" : "ĐANG BẬT";
            }

            if (muteStatusIcon != null)
            {
                muteStatusIcon.sprite = muted ? speakerMutedSprite : speakerOnSprite;
            }

            if (muteButtonImage != null)
            {
                muteButtonImage.color = muted ? new Color32(229, 57, 53, 255) : new Color32(39, 174, 96, 255);
            }

            // Cập nhật giá trị các thanh trượt
            if (masterSlider != null) masterSlider.SetValueWithoutNotify(audioManager.MasterVolume);
            if (masterValueText != null) masterValueText.text = Mathf.RoundToInt(audioManager.MasterVolume * 100f) + "%";

            if (musicSlider != null) musicSlider.SetValueWithoutNotify(audioManager.MusicVolume);
            if (musicValueText != null) musicValueText.text = Mathf.RoundToInt(audioManager.MusicVolume * 100f) + "%";

            if (sfxSlider != null) sfxSlider.SetValueWithoutNotify(audioManager.SfxVolume);
            if (sfxValueText != null) sfxValueText.text = Mathf.RoundToInt(audioManager.SfxVolume * 100f) + "%";
        }

        private TMP_Text CreateText(Transform parent, string name, string text, float fontSize, FontStyles style,
            Color color, Vector2 pos, Vector2 size, TextAlignmentOptions align,
            Vector2? anchorMin = null, Vector2? anchorMax = null, Vector2? pivot = null)
        {
            var go = new GameObject(name, typeof(RectTransform), typeof(CanvasRenderer), typeof(TextMeshProUGUI));
            go.transform.SetParent(parent, false);
            var rect = go.GetComponent<RectTransform>();
            rect.anchorMin = anchorMin ?? new Vector2(0, 1);
            rect.anchorMax = anchorMax ?? new Vector2(0, 1);
            rect.pivot = pivot ?? new Vector2(0, 1);
            rect.anchoredPosition = pos;
            rect.sizeDelta = size;

            var tmp = go.GetComponent<TextMeshProUGUI>();
            if (vietnameseFont != null) tmp.font = vietnameseFont;
            tmp.text = text;
            tmp.fontSize = fontSize;
            tmp.fontStyle = style;
            tmp.color = color;
            tmp.alignment = align;
            tmp.raycastTarget = false;
            return tmp;
        }

        private GameObject CreateImage(Transform parent, string name, Color color,
            Vector2 pos, Vector2 size, Vector2 anchorMin, Vector2 anchorMax, Vector2 pivot)
        {
            var go = new GameObject(name, typeof(RectTransform), typeof(CanvasRenderer), typeof(Image));
            go.transform.SetParent(parent, false);
            var rect = go.GetComponent<RectTransform>();
            rect.anchorMin = anchorMin;
            rect.anchorMax = anchorMax;
            rect.pivot = pivot;
            rect.anchoredPosition = pos;
            rect.sizeDelta = size;

            var img = go.GetComponent<Image>();
            img.sprite = flatSprite;
            img.color = color;
            img.raycastTarget = false;
            return go;
        }

        private Slider CreateSlider(Transform parent, string name, Vector2 pos, Vector2 size)
        {
            // sliderGo có kích thước rộng rãi (chiều cao 36px) để click cực nhạy
            var sliderGo = new GameObject(name, typeof(RectTransform), typeof(CanvasRenderer), typeof(Image), typeof(Slider));
            sliderGo.transform.SetParent(parent, false);
            var sliderRect = sliderGo.GetComponent<RectTransform>();
            sliderRect.anchorMin = new Vector2(0, 1);
            sliderRect.anchorMax = new Vector2(0, 1);
            sliderRect.pivot = new Vector2(0, 1);
            sliderRect.anchoredPosition = pos;
            sliderRect.sizeDelta = size;

            // Vùng chạm vô hình bao phủ toàn bộ slider: click ở bất kỳ đâu trên thanh đều nhận!
            var hitImg = sliderGo.GetComponent<Image>();
            hitImg.color = Color.clear;
            hitImg.raycastTarget = true;

            var slider = sliderGo.GetComponent<Slider>();
            slider.direction = Slider.Direction.LeftToRight;
            slider.minValue = 0f;
            slider.maxValue = 1f;

            // Background track (Thanh ray xám tím nằm ở giữa, dày 10px, bo góc)
            var bgGo = new GameObject("Background", typeof(RectTransform), typeof(CanvasRenderer), typeof(Image));
            bgGo.transform.SetParent(sliderGo.transform, false);
            var bgRect = bgGo.GetComponent<RectTransform>();
            bgRect.anchorMin = new Vector2(0, 0.5f);
            bgRect.anchorMax = new Vector2(1, 0.5f);
            bgRect.pivot = new Vector2(0.5f, 0.5f);
            bgRect.anchoredPosition = Vector2.zero;
            bgRect.sizeDelta = new Vector2(0, 10);

            var bgImg = bgGo.GetComponent<Image>();
            bgImg.sprite = roundedSprite;
            bgImg.type = Image.Type.Sliced;
            bgImg.color = new Color32(234, 229, 245, 255);
            bgImg.raycastTarget = false;

            // Fill Area
            var fillArea = new GameObject("Fill Area", typeof(RectTransform));
            fillArea.transform.SetParent(sliderGo.transform, false);
            var fillAreaRect = fillArea.GetComponent<RectTransform>();
            fillAreaRect.anchorMin = new Vector2(0, 0.5f);
            fillAreaRect.anchorMax = new Vector2(1, 0.5f);
            fillAreaRect.pivot = new Vector2(0.5f, 0.5f);
            fillAreaRect.anchoredPosition = Vector2.zero;
            fillAreaRect.sizeDelta = new Vector2(-26, 10);

            // Fill (Thanh màu tím đã kéo)
            var fillGo = new GameObject("Fill", typeof(RectTransform), typeof(CanvasRenderer), typeof(Image));
            fillGo.transform.SetParent(fillArea.transform, false);
            var fillRect = fillGo.GetComponent<RectTransform>();
            fillRect.anchorMin = Vector2.zero;
            fillRect.anchorMax = new Vector2(0, 1);
            fillRect.pivot = new Vector2(0, 0.5f);
            fillRect.sizeDelta = Vector2.zero;

            var fillImg = fillGo.GetComponent<Image>();
            fillImg.sprite = roundedSprite;
            fillImg.type = Image.Type.Sliced;
            fillImg.color = new Color32(124, 77, 255, 255); // Vibrant purple
            fillImg.raycastTarget = false;

            // Handle Slide Area
            var handleArea = new GameObject("Handle Slide Area", typeof(RectTransform));
            handleArea.transform.SetParent(sliderGo.transform, false);
            var handleAreaRect = handleArea.GetComponent<RectTransform>();
            handleAreaRect.anchorMin = Vector2.zero;
            handleAreaRect.anchorMax = Vector2.one;
            handleAreaRect.pivot = new Vector2(0.5f, 0.5f);
            handleAreaRect.anchoredPosition = Vector2.zero;
            handleAreaRect.sizeDelta = new Vector2(-26, 0);

            // Handle (Nút tròn 26x26 xoe 100%, không bao giờ bị méo elip)
            var handleGo = new GameObject("Handle", typeof(RectTransform), typeof(CanvasRenderer), typeof(Image));
            handleGo.transform.SetParent(handleArea.transform, false);
            var handleRect = handleGo.GetComponent<RectTransform>();
            handleRect.anchorMin = new Vector2(0, 0.5f);
            handleRect.anchorMax = new Vector2(0, 0.5f);
            handleRect.pivot = new Vector2(0.5f, 0.5f);
            handleRect.anchoredPosition = Vector2.zero;
            handleRect.sizeDelta = new Vector2(26, 26);

            var handleImg = handleGo.GetComponent<Image>();
            handleImg.sprite = circleSprite;
            handleImg.color = new Color32(124, 77, 255, 255);
            handleImg.preserveAspect = true;
            handleImg.raycastTarget = true;

            // Chấm tròn trắng ở tâm Handle tạo hiệu ứng 3D nổi bật
            var dotGo = new GameObject("Dot", typeof(RectTransform), typeof(CanvasRenderer), typeof(Image));
            dotGo.transform.SetParent(handleGo.transform, false);
            var dotRect = dotGo.GetComponent<RectTransform>();
            dotRect.anchorMin = new Vector2(0.5f, 0.5f);
            dotRect.anchorMax = new Vector2(0.5f, 0.5f);
            dotRect.pivot = new Vector2(0.5f, 0.5f);
            dotRect.anchoredPosition = Vector2.zero;
            dotRect.sizeDelta = new Vector2(10, 10);

            var dotImg = dotGo.GetComponent<Image>();
            dotImg.sprite = circleSprite;
            dotImg.color = Color.white;
            dotImg.preserveAspect = true;
            dotImg.raycastTarget = false;

            slider.fillRect = fillRect;
            slider.handleRect = handleRect;
            slider.targetGraphic = handleImg;

            return slider;
        }
    }
}
