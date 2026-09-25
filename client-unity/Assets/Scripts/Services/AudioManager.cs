using System;
using System.Collections;
using System.Collections.Generic;
using Ludo.Views;
using UnityEngine;
using UnityEngine.SceneManagement;

namespace Ludo.Services
{
    public enum MusicTrack
    {
        None,
        Lobby,
        Room,
        Game
    }

    public enum SfxClip
    {
        // UI
        ButtonClick,
        ButtonHover,
        Confirm,
        CountdownTick,
        ErrorSoft,
        Notification,

        // Horse
        HorseStep,
        HorseSpawn,
        HorseNeigh,
        HorseGallop,

        // Game
        DiceRoll,
        Capture,
        SpecialPlus3,
        SpecialMinus1,
        BonusRoll,
        Trap,
        TurnStart,
        PieceFinished,
        GameStart,
        Victory,
        Forfeit,

        // Network
        ChatSend,
        ChatReceive,
        PlayerJoin,
        PlayerLeave,
        Ready,
        RoomInvite,
        ServerConnected,
        ServerDisconnected
    }

    public sealed class AudioManager : MonoBehaviour
    {
        public static AudioManager Instance { get; private set; }

        private const string PrefMasterVolume = "Ludo_Audio_Master";
        private const string PrefMusicVolume = "Ludo_Audio_Music";
        private const string PrefSfxVolume = "Ludo_Audio_Sfx";
        private const string PrefMuted = "Ludo_Audio_Muted";

        private const float DefaultMasterVolume = 0.8f;
        private const float DefaultMusicVolume = 0.35f;
        private const float DefaultSfxVolume = 0.8f;

        private AudioSource musicSource;
        private AudioSource sfxSource;

        private readonly Dictionary<string, AudioClip> clipCache = new Dictionary<string, AudioClip>(StringComparer.OrdinalIgnoreCase);
        private Coroutine musicFadeRoutine;
        private MusicTrack currentTrack = MusicTrack.None;

        public float MasterVolume { get; private set; } = DefaultMasterVolume;
        public float MusicVolume { get; private set; } = DefaultMusicVolume;
        public float SfxVolume { get; private set; } = DefaultSfxVolume;
        public bool IsMuted { get; private set; }

        public event Action<bool> MuteChanged;
        public event Action VolumeChanged;

        public MusicTrack CurrentTrack => currentTrack;

        [RuntimeInitializeOnLoadMethod(RuntimeInitializeLoadType.AfterSceneLoad)]
        public static void Initialize()
        {
            var audio = EnsureInstance();
            audio.HandleSceneMusic(SceneManager.GetActiveScene().name);
        }

        public static AudioManager EnsureInstance()
        {
            if (Instance != null)
            {
                Instance.InitIfNeeded();
                return Instance;
            }

            var existing = FindAnyObjectByType<AudioManager>();
            if (existing != null)
            {
                Instance = existing;
                Instance.InitIfNeeded();
                return existing;
            }

            var go = new GameObject("[AudioManager]");
            if (Application.isPlaying) DontDestroyOnLoad(go);
            Instance = go.AddComponent<AudioManager>();
            Instance.InitIfNeeded();
            return Instance;
        }

        public void InitIfNeeded()
        {
            // Ensure AudioListener exists so sound can be heard even in scenes without Camera
            var existingListener = FindAnyObjectByType<AudioListener>();
            if (existingListener == null)
            {
                gameObject.AddComponent<AudioListener>();
            }
            AudioListener.pause = false;
            AudioListener.volume = 1f;

            if (musicSource == null)
            {
                musicSource = gameObject.GetComponent<AudioSource>();
                if (musicSource == null) musicSource = gameObject.AddComponent<AudioSource>();
                musicSource.loop = true;
                musicSource.playOnAwake = false;
                musicSource.spatialBlend = 0f; // 2D Sound
                musicSource.priority = 0;
                musicSource.bypassEffects = true;
                musicSource.bypassListenerEffects = true;
                musicSource.bypassReverbZones = true;
            }

            if (sfxSource == null)
            {
                var sources = gameObject.GetComponents<AudioSource>();
                if (sources.Length > 1) sfxSource = sources[1];
                else sfxSource = gameObject.AddComponent<AudioSource>();
                sfxSource.loop = false;
                sfxSource.playOnAwake = false;
                sfxSource.spatialBlend = 0f; // 2D Sound
                sfxSource.priority = 10;
                sfxSource.bypassEffects = true;
                sfxSource.bypassListenerEffects = true;
                sfxSource.bypassReverbZones = true;
            }

            LoadPreferences();
            ApplyVolumes();
            EnsureOverlay();
        }

        private void Awake()
        {
            if (Instance != null && Instance != this)
            {
                Destroy(gameObject);
                return;
            }

            Instance = this;
            if (Application.isPlaying) DontDestroyOnLoad(gameObject);
            InitIfNeeded();
            SceneManager.sceneLoaded += OnSceneLoaded;
        }

        private void Start()
        {
            EnsureOverlay();
            HandleSceneMusic(SceneManager.GetActiveScene().name);
        }

        private void OnDestroy()
        {
            if (Instance == this)
            {
                SceneManager.sceneLoaded -= OnSceneLoaded;
                Instance = null;
            }
        }

        private void LoadPreferences()
        {
            MasterVolume = PlayerPrefs.GetFloat(PrefMasterVolume, DefaultMasterVolume);
            MusicVolume = PlayerPrefs.GetFloat(PrefMusicVolume, DefaultMusicVolume);
            SfxVolume = PlayerPrefs.GetFloat(PrefSfxVolume, DefaultSfxVolume);
            IsMuted = PlayerPrefs.GetInt(PrefMuted, 0) == 1;
        }

        private void SavePreferences()
        {
            PlayerPrefs.SetFloat(PrefMasterVolume, MasterVolume);
            PlayerPrefs.SetFloat(PrefMusicVolume, MusicVolume);
            PlayerPrefs.SetFloat(PrefSfxVolume, SfxVolume);
            PlayerPrefs.SetInt(PrefMuted, IsMuted ? 1 : 0);
            PlayerPrefs.Save();
        }

        private void ApplyVolumes()
        {
            float effectiveMusic = IsMuted ? 0f : (MasterVolume * MusicVolume);
            if (musicSource != null)
            {
                musicSource.volume = effectiveMusic;
                musicSource.mute = IsMuted;
            }

            if (sfxSource != null)
            {
                sfxSource.mute = IsMuted;
            }
        }

        public void SetMuted(bool muted)
        {
            if (IsMuted == muted) return;
            IsMuted = muted;
            ApplyVolumes();
            SavePreferences();
            MuteChanged?.Invoke(IsMuted);
            VolumeChanged?.Invoke();
        }

        public void ToggleMute()
        {
            SetMuted(!IsMuted);
        }

        public void SetMasterVolume(float volume)
        {
            volume = Mathf.Clamp01(volume);
            if (Mathf.Approximately(MasterVolume, volume)) return;
            MasterVolume = volume;
            ApplyVolumes();
            SavePreferences();
            VolumeChanged?.Invoke();
        }

        public void SetMusicVolume(float volume)
        {
            volume = Mathf.Clamp01(volume);
            if (Mathf.Approximately(MusicVolume, volume)) return;
            MusicVolume = volume;
            ApplyVolumes();
            SavePreferences();
            VolumeChanged?.Invoke();
        }

        public void SetSfxVolume(float volume)
        {
            volume = Mathf.Clamp01(volume);
            if (Mathf.Approximately(SfxVolume, volume)) return;
            SfxVolume = volume;
            ApplyVolumes();
            SavePreferences();
            VolumeChanged?.Invoke();
        }

        public void PlayMusic(MusicTrack track, bool restartIfSame = false)
        {
            if (!restartIfSame && track == currentTrack && musicSource != null && musicSource.isPlaying) return;

            currentTrack = track;
            string path = GetMusicPath(track);

            if (string.IsNullOrEmpty(path))
            {
                StopMusic();
                return;
            }

            var clip = LoadClip(path);
            if (clip == null)
            {
                StopMusic();
                return;
            }

            if (musicFadeRoutine != null) StopCoroutine(musicFadeRoutine);

            if (isActiveAndEnabled)
            {
                musicFadeRoutine = StartCoroutine(CrossfadeMusic(clip));
            }
            else
            {
                musicSource.clip = clip;
                musicSource.volume = IsMuted ? 0f : (MasterVolume * MusicVolume);
                musicSource.Play();
            }
        }

        public void StopMusic()
        {
            currentTrack = MusicTrack.None;
            if (musicFadeRoutine != null) StopCoroutine(musicFadeRoutine);
            if (musicSource != null && musicSource.isPlaying)
            {
                musicSource.Stop();
                musicSource.clip = null;
            }
        }

        private IEnumerator CrossfadeMusic(AudioClip newClip)
        {
            float targetVolume = IsMuted ? 0f : (MasterVolume * MusicVolume);
            float startVolume = musicSource.volume;
            float duration = 0.35f;

            if (musicSource.isPlaying && startVolume > 0.01f)
            {
                for (float t = 0; t < duration; t += Time.unscaledDeltaTime)
                {
                    musicSource.volume = Mathf.Lerp(startVolume, 0f, t / duration);
                    yield return null;
                }
            }

            musicSource.clip = newClip;
            musicSource.volume = targetVolume;
            musicSource.Play();

            if (duration > 0.05f && targetVolume > 0.01f)
            {
                musicSource.volume = 0f;
                for (float t = 0; t < duration; t += Time.unscaledDeltaTime)
                {
                    musicSource.volume = Mathf.Lerp(0f, targetVolume, t / duration);
                    yield return null;
                }
                musicSource.volume = targetVolume;
            }

            musicFadeRoutine = null;
        }

        public void PlaySfx(SfxClip clip, float volumeScale = 1f)
        {
            string path = GetSfxPath(clip);
            PlaySfx(path, volumeScale);
        }

        public void PlaySfx(string resourcePath, float volumeScale = 1f)
        {
            if (IsMuted || string.IsNullOrEmpty(resourcePath)) return;

            float effectiveVolume = MasterVolume * SfxVolume * volumeScale;
            if (effectiveVolume <= 0.001f) return;

            var audioClip = LoadClip(resourcePath);
            if (audioClip != null && sfxSource != null)
            {
                sfxSource.PlayOneShot(audioClip, Mathf.Clamp01(effectiveVolume));
            }
        }

        public AudioClip LoadClip(string resourcePath)
        {
            if (string.IsNullOrEmpty(resourcePath)) return null;

            if (clipCache.TryGetValue(resourcePath, out var cached) && cached != null)
            {
                return cached;
            }

            var loaded = Resources.Load<AudioClip>(resourcePath);
            if (loaded != null)
            {
                if (loaded.loadState == AudioDataLoadState.Unloaded)
                {
                    loaded.LoadAudioData();
                }
                clipCache[resourcePath] = loaded;
            }
            return loaded;
        }

        private void OnSceneLoaded(Scene scene, LoadSceneMode mode)
        {
            EnsureOverlay();
            HandleSceneMusic(scene.name);
        }

        public void HandleSceneMusic(string sceneName)
        {
            switch (sceneName)
            {
                case "RoomScene":
                    PlayMusic(MusicTrack.Room);
                    break;
                case "GameScene":
                case "ResultScene":
                    PlayMusic(MusicTrack.Game);
                    break;
                case "LoginScene":
                case "RegisterScene":
                case "LobbyScene":
                case "RankingScene":
                case "HistoryScene":
                default:
                    PlayMusic(MusicTrack.Lobby);
                    break;
            }
        }

        private void EnsureOverlay()
        {
            if (FindAnyObjectByType<AudioControlView>() != null) return;

            var overlayGo = new GameObject("AudioOverlay");
            if (Application.isPlaying) DontDestroyOnLoad(overlayGo);
            overlayGo.AddComponent<AudioControlView>();
        }

        public static string GetMusicPath(MusicTrack track) => track switch
        {
            MusicTrack.Lobby => "Audio/Music/lobby_friendly",
            MusicTrack.Room => "Audio/Music/room_friendly",
            MusicTrack.Game => "Audio/Music/game_friendly",
            _ => null
        };

        public static string GetSfxPath(SfxClip clip) => clip switch
        {
            // UI
            SfxClip.ButtonClick => "Audio/SFX/UI/button_click",
            SfxClip.ButtonHover => "Audio/SFX/UI/button_hover",
            SfxClip.Confirm => "Audio/SFX/UI/confirm",
            SfxClip.CountdownTick => "Audio/SFX/UI/countdown_tick",
            SfxClip.ErrorSoft => "Audio/SFX/UI/error_soft",
            SfxClip.Notification => "Audio/SFX/UI/notification",

            // Horse
            SfxClip.HorseStep => "Audio/SFX/Horse/horse_step",
            SfxClip.HorseSpawn => "Audio/SFX/Horse/horse_spawn",
            SfxClip.HorseNeigh => "Audio/SFX/Horse/horse_neigh_friendly",
            SfxClip.HorseGallop => "Audio/SFX/Horse/horse_gallop_short",

            // Game
            SfxClip.DiceRoll => "Audio/SFX/Game/dice_roll",
            SfxClip.Capture => "Audio/SFX/Game/capture",
            SfxClip.SpecialPlus3 => "Audio/SFX/Game/special_plus3",
            SfxClip.SpecialMinus1 => "Audio/SFX/Game/special_minus1",
            SfxClip.BonusRoll => "Audio/SFX/Game/bonus_roll",
            SfxClip.Trap => "Audio/SFX/Game/trap",
            SfxClip.TurnStart => "Audio/SFX/Game/turn_start",
            SfxClip.PieceFinished => "Audio/SFX/Game/piece_finished",
            SfxClip.GameStart => "Audio/SFX/Game/game_start",
            SfxClip.Victory => "Audio/SFX/Game/victory",
            SfxClip.Forfeit => "Audio/SFX/Game/forfeit",

            // Network
            SfxClip.ChatSend => "Audio/SFX/Network/chat_send",
            SfxClip.ChatReceive => "Audio/SFX/Network/chat_receive",
            SfxClip.PlayerJoin => "Audio/SFX/Network/player_join",
            SfxClip.PlayerLeave => "Audio/SFX/Network/player_leave",
            SfxClip.Ready => "Audio/SFX/Network/ready",
            SfxClip.RoomInvite => "Audio/SFX/Network/room_invite",
            SfxClip.ServerConnected => "Audio/SFX/Network/server_connected",
            SfxClip.ServerDisconnected => "Audio/SFX/Network/server_disconnected",

            _ => null
        };
    }
}
