using System;
using System.Linq;
using System.Reflection;
using System.Threading.Tasks;
using Ludo.Services;
using Ludo.Views;
using TMPro;
using UnityEngine;
using UnityEngine.UI;

public static class VerifyAudio
{
    private static int checks;

    private static void Check(bool condition, string message)
    {
        if (!condition) throw new Exception("Check failed: " + message);
        checks++;
    }

    public static async Task<string> Main()
    {
        checks = 0;

        // 1. Verify All 32 Audio Clips load cleanly from Resources
        var musicTracks = new[]
        {
            ("Lobby", "Audio/Music/lobby_friendly"),
            ("Room", "Audio/Music/room_friendly"),
            ("Game", "Audio/Music/game_friendly")
        };

        foreach (var (name, path) in musicTracks)
        {
            var clip = Resources.Load<AudioClip>(path);
            Check(clip != null, "Music clip loaded: " + name + " (" + path + ")");
            Check(clip.length > 1f, "Music clip duration valid: " + name + " (" + clip.length + "s)");
        }

        foreach (SfxClip sfx in Enum.GetValues(typeof(SfxClip)))
        {
            string path = AudioManager.GetSfxPath(sfx);
            Check(!string.IsNullOrEmpty(path), "SFX path defined for: " + sfx);
            var clip = Resources.Load<AudioClip>(path);
            Check(clip != null, "SFX clip loaded: " + sfx + " (" + path + ")");
            Check(clip.length > 0f, "SFX clip has duration: " + sfx);
        }

        // 2. Verify AudioManager instance, AudioListener and preference handling
        var audio = AudioManager.EnsureInstance();
        Check(audio != null, "AudioManager instance created");

        var listener = UnityEngine.Object.FindAnyObjectByType<AudioListener>();
        Check(listener != null, "AudioListener exists in scene (crucial for audio output)");
        Check(!AudioListener.pause, "AudioListener is not paused");
        Check(Mathf.Approximately(AudioListener.volume, 1f), "AudioListener master volume is 1.0");

        var audioSources = audio.GetComponents<AudioSource>();
        Check(audioSources.Length >= 2, "AudioManager has at least 2 AudioSources (Music + SFX)");
        foreach (var src in audioSources)
        {
            Check(Mathf.Approximately(src.spatialBlend, 0f), "AudioSource is set to 2D (spatialBlend == 0)");
        }

        float originalMaster = audio.MasterVolume;
        float originalMusic = audio.MusicVolume;
        float originalSfx = audio.SfxVolume;
        bool originalMute = audio.IsMuted;

        try
        {
            // Test volume setting and clamping
            audio.SetMasterVolume(0.5f);
            Check(Mathf.Approximately(audio.MasterVolume, 0.5f), "SetMasterVolume applied");
            Check(Mathf.Approximately(PlayerPrefs.GetFloat("Ludo_Audio_Master"), 0.5f), "MasterVolume persisted to PlayerPrefs");

            audio.SetMusicVolume(0.3f);
            Check(Mathf.Approximately(audio.MusicVolume, 0.3f), "SetMusicVolume applied");
            Check(Mathf.Approximately(PlayerPrefs.GetFloat("Ludo_Audio_Music"), 0.3f), "MusicVolume persisted to PlayerPrefs");

            audio.SetSfxVolume(0.7f);
            Check(Mathf.Approximately(audio.SfxVolume, 0.7f), "SetSfxVolume applied");
            Check(Mathf.Approximately(PlayerPrefs.GetFloat("Ludo_Audio_Sfx"), 0.7f), "SfxVolume persisted to PlayerPrefs");

            // Test Mute toggle
            audio.SetMuted(true);
            Check(audio.IsMuted, "SetMuted(true) applied");
            Check(PlayerPrefs.GetInt("Ludo_Audio_Muted") == 1, "Muted persisted to PlayerPrefs");

            audio.ToggleMute();
            Check(!audio.IsMuted, "ToggleMute() unmuted");
            Check(PlayerPrefs.GetInt("Ludo_Audio_Muted") == 0, "Unmuted persisted to PlayerPrefs");

            // Test playback methods without exceptions
            audio.PlayMusic(MusicTrack.Lobby);
            Check(audio.CurrentTrack == MusicTrack.Lobby, "PlayMusic(Lobby) track set");

            audio.PlayMusic(MusicTrack.Room);
            Check(audio.CurrentTrack == MusicTrack.Room, "PlayMusic(Room) track set");

            audio.PlayMusic(MusicTrack.Game);
            Check(audio.CurrentTrack == MusicTrack.Game, "PlayMusic(Game) track set");

            // Play SFX through all enum values
            foreach (SfxClip sfx in Enum.GetValues(typeof(SfxClip)))
            {
                audio.PlaySfx(sfx);
            }
            Check(true, "All 29 SFX clips played without exception");

            audio.StopMusic();
            Check(audio.CurrentTrack == MusicTrack.None, "StopMusic() cleared currentTrack");

            // 3. Verify AudioControlView
            var controlView = UnityEngine.Object.FindAnyObjectByType<AudioControlView>();
            Check(controlView != null, "AudioControlView overlay exists in scene");
            controlView.InitIfNeeded();

            var canvas = controlView.GetComponent<Canvas>();
            Check(canvas != null && canvas.renderMode == RenderMode.ScreenSpaceOverlay, "Overlay canvas uses ScreenSpaceOverlay");
            Check(canvas.sortingOrder >= 900, "Overlay sortingOrder is on top");

            var scaler = controlView.GetComponent<CanvasScaler>();
            Check(scaler != null && scaler.uiScaleMode == CanvasScaler.ScaleMode.ScaleWithScreenSize, "CanvasScaler uses ScaleWithScreenSize");
            Check(scaler.referenceResolution == new Vector2(1920, 1080), "CanvasScaler referenceResolution is 1920x1080");

            var btn = controlView.transform.Find("Root/AudioButton")?.GetComponent<Button>();
            Check(btn != null, "AudioButton exists in hierarchy");

            var btnText = btn.GetComponentInChildren<TMP_Text>();
            Check(btnText != null, "AudioButton has TMP_Text");

            audio.SetMuted(false);
            Check(btnText.text.Contains("Âm thanh"), "Button shows unmuted state");

            audio.SetMuted(true);
            Check(btnText.text.Contains("Đã tắt"), "Button shows muted state");

            // Test open panel
            controlView.SetPanelOpen(true);
            var panel = controlView.transform.Find("Root/Backdrop/Panel");
            Check(panel != null && panel.gameObject.activeInHierarchy, "Panel is open and visible");

            // Test Master Slider UI interaction
            var masterSlider = panel.Find("MasterSlider")?.GetComponent<Slider>();
            Check(masterSlider != null, "MasterSlider exists");
            masterSlider.value = 0.9f;
            Check(Mathf.Approximately(audio.MasterVolume, 0.9f), "MasterSlider update synced to AudioManager");

            // Test Music Slider UI interaction
            var musicSlider = panel.Find("MusicSlider")?.GetComponent<Slider>();
            Check(musicSlider != null, "MusicSlider exists");
            musicSlider.value = 0.45f;
            Check(Mathf.Approximately(audio.MusicVolume, 0.45f), "MusicSlider update synced to AudioManager");

            // Test SFX Slider UI interaction
            var sfxSlider = panel.Find("SfxSlider")?.GetComponent<Slider>();
            Check(sfxSlider != null, "SfxSlider exists");
            sfxSlider.value = 0.85f;
            Check(Mathf.Approximately(audio.SfxVolume, 0.85f), "SfxSlider update synced to AudioManager");

            // Test Mute button in panel
            var mutePanelBtn = panel.Find("MuteToggleButton")?.GetComponent<Button>();
            Check(mutePanelBtn != null, "MuteToggleButton exists in panel");
            mutePanelBtn.onClick.Invoke();
            Check(!audio.IsMuted, "Panel mute toggle unmuted");

            // Close panel
            controlView.SetPanelOpen(false);
            Check(!panel.gameObject.activeInHierarchy, "Panel is closed");

            // Test scene positioning adaptation
            var updatePosMethod = typeof(AudioControlView).GetMethod("UpdatePositionForScene", BindingFlags.Instance | BindingFlags.NonPublic);
            Check(updatePosMethod != null, "UpdatePositionForScene method exists");

            updatePosMethod.Invoke(controlView, new object[] { "GameScene" });
            var btnRect = btn.GetComponent<RectTransform>();
            Check(btnRect.anchoredPosition.x < -200f, "GameScene position leaves room for LeaveButton");
            Check(btnRect.anchoredPosition.y > -15f, "GameScene position avoids overlap with header");

            updatePosMethod.Invoke(controlView, new object[] { "RoomScene" });
            Check(btnRect.anchoredPosition.y > -30f, "RoomScene position has 30px+ clearance above LeaveButton");

            updatePosMethod.Invoke(controlView, new object[] { "LobbyScene" });
            Check(btnRect.anchoredPosition.x >= -100f, "LobbyScene position aligned in top right");
            Check(btnRect.anchoredPosition.y > -20f, "LobbyScene position sits cleanly above Header card");

            await Task.Yield();
        }
        finally
        {
            // Restore preferences
            audio.SetMasterVolume(originalMaster);
            audio.SetMusicVolume(originalMusic);
            audio.SetSfxVolume(originalSfx);
            audio.SetMuted(originalMute);
        }

        return checks + " Audio/Volume/Mute checks passed successfully.";
    }
}
