# Android Media Video Player — Full Feature Spec + Antigravity Prompt

---

## WHAT A GREAT ANDROID VIDEO PLAYER MUST HAVE

### 1. Core Playback Engine
- Hardware-accelerated decoding (H.264, H.265/HEVC, AV1, VP9, VP8)
- Software fallback for unsupported codecs
- Gapless playback between playlist items
- Accurate seek with keyframe snapping
- Buffering with adaptive bitrate for network streams
- Background audio playback (with foreground service)

### 2. Format & Protocol Support
- Local: MP4, MKV, AVI, MOV, FLV, WebM, 3GP, TS, M4V, RMVB
- Network: HTTP/HTTPS direct links, HLS (.m3u8), DASH (.mpd), RTSP, RTMP, SMB/NFS shares
- Audio containers: MP3, AAC, FLAC, OGG, WAV, OPUS

### 3. Gesture Controls
- Double-tap left = rewind 10s / Double-tap right = fast forward 10s
- Single tap = toggle controls visibility
- Swipe up/down on LEFT half = brightness
- Swipe up/down on RIGHT half = volume
- Swipe left/right = seek scrub
- Pinch to zoom / rotate
- Long press = 2× speed playback (release to return)

### 4. Subtitle & Audio Track System
- SRT, ASS/SSA, WebVTT, PGS subtitle support
- External subtitle file loading
- Subtitle styling (size, color, background, position, outline)
- Multiple audio track selection (dubbed movies)
- Delay sync for audio/subtitle (±5000ms offset)

### 5. Advanced Playback Controls
- Playback speed: 0.25×, 0.5×, 0.75×, 1×, 1.25×, 1.5×, 1.75×, 2×, 3×
- Video scaling: Fit, Fill Screen, Crop, Stretch, 16:9, 4:3, Custom Zoom
- Pinch-to-zoom with pan
- Loop: none, loop one, loop all
- Shuffle mode for playlists
- Sleep timer (15m, 30m, 45m, 60m, custom, end of video)
- Resume from last watched position (per-file)

### 6. Player Controls UI
- Auto-hiding overlay (3-second timeout, smooth fade)
- Seekbar with buffered progress indicator + thumbnail preview on seek
- Live timestamp (current / duration)
- Remaining time toggle
- Skip intro / skip outro button (configurable offsets)
- Next/Previous track buttons
- Fullscreen toggle
- Lock screen button (prevent accidental taps)
- Cast button (Chromecast)
- Picture-in-Picture (PiP) button
- Rotate lock button
- Screenshot button

### 7. Picture-in-Picture
- Android 8+ PiP with proper aspect ratio
- PiP controls: play/pause, close, full screen
- Auto-enter PiP on home button press (optional)

### 8. Media Library / File Browser
- Folder-based browsing of internal + SD card storage
- Recently played list (title, thumbnail, progress)
- Favorites / bookmarks
- Search by filename
- Sort: name, date, size, duration
- Thumbnail grid or list view
- Last position badge on thumbnails
- Network/SMB/FTP stream opener

### 9. Lock Screen & Notification
- Media session for lock screen art + controls
- Notification: title, thumbnail, play/pause, prev/next, close
- Persistent notification during background playback

### 10. Equalizer & Audio
- 10-band graphic equalizer with presets
- Bass boost and virtualizer
- Loudness enhancer
- Volume normalization (per track)

### 11. Casting & Sharing
- Chromecast / Google Cast support
- Share current file via intent
- Open in other apps

### 12. Settings & Preferences
- Default subtitle language preference
- Subtitle font, size, color, background
- Default audio track language
- Hardware vs software decoder toggle
- Auto-rotate behavior
- Swipe sensitivity (brightness/volume/seek)
- Thumbnail scrubber toggle
- Resume position threshold (e.g., resume if > 10s played)
- Skip silence
- Video output: surface / texture view
- Preferred decoder order

---

## ANTIGRAVITY IMPLEMENTATION PROMPT

Copy the full prompt below and paste it directly into Antigravity.

---

```
Build a complete Android video player application in Kotlin using the following full feature specification.
This should be a production-quality, polished app — NOT a proof of concept.

---

## TECH STACK

- Language: Kotlin
- UI: Jetpack Compose (Material3 base) — all screens in Compose
- Player Engine: Androidx Media3 (ExoPlayer 1.x) — use `media3-exoplayer`, `media3-ui`, `media3-session`, `media3-exoplayer-hls`, `media3-exoplayer-dash`, `media3-exoplayer-rtsp`, `media3-datasource-okhttp`
- DI: Hilt
- Navigation: Compose NavHost
- Database: Room (watch history, favorites, subtitle settings per file)
- Preferences: DataStore (app settings)
- Networking: OkHttp + Retrofit (for stream URL resolution)
- Image loading: Coil (for thumbnails, poster art)
- Subtitles: Built-in Media3 + libass binding for SSA/ASS rendering
- Media scanning: MediaStore API + custom file indexer
- Build: Gradle Kotlin DSL, minSdk 24, targetSdk 35

---

## DESIGN SYSTEM — iOS GLASSMORPHISM DARK THEME

This is the core visual identity. Every screen must follow this system exactly.

### Color Tokens
```
Background:       #0A0A0F  (near-black, very deep cool navy)
Surface:          rgba(255,255,255,0.07)  (frosted glass card bg)
SurfaceHigher:    rgba(255,255,255,0.12)  (elevated card/sheet)
Outline:          rgba(255,255,255,0.14)  (glass border)
Primary:          #6C63FF  (electric indigo-violet — accent)
PrimaryGlow:      rgba(108,99,255,0.35)  (glow shadow for accent elements)
OnPrimary:        #FFFFFF
Secondary:        rgba(255,255,255,0.55)  (secondary text)
Tertiary:         rgba(255,255,255,0.30)  (disabled/hint text)
OnSurface:        #F0F0FF  (primary text, cool white)
SeekbarFilled:    #6C63FF  
SeekbarBuffer:    rgba(255,255,255,0.20)
SeekbarTrack:     rgba(255,255,255,0.12)
ProgressGradient: linear #6C63FF → #A78BFA
```

### Typography
```
Display:    Inter ExtraBold 700, size 28sp, tracking -0.5sp
Title:      Inter SemiBold 600, size 18sp
Body:       Inter Regular 400, size 14sp
Caption:    Inter Regular 400, size 11sp, opacity 0.55
Mono:       JetBrains Mono 400, size 13sp (timestamps, speed values)
```
Import Inter and JetBrains Mono via downloadable fonts (Google Fonts XML).

### Glass Card Style (reuse everywhere)
```
background: Surface color
border: 1dp Outline color (rounded to 20dp)
blur: use RenderEffect BlurMaskFilter (API 31+) or a manual dimmed background fallback for API 24–30
shadow: elevation 0dp (NO hard Material shadow), instead use box-shadow effect via Canvas (4dp spread, PrimaryGlow color, 0.18 opacity)
padding: 16dp internal
```

### Motion
- All overlays: 220ms fade (FastOutLinearIn for hide, LinearOutSlowIn for show)
- Sheet open: spring (stiffness=300, damping=0.75) slide up from bottom
- Seekbar thumb: spring pop on touch
- Gesture feedback: ripple replaced by subtle glow pulse on the action icon

---

## SCREENS & COMPONENTS

### 1. SPLASH / HOME SCREEN (`HomeScreen.kt`)
- Background: #0A0A0F full bleed
- Top bar: App logo (vector — a play triangle inside a frosted rounded square, Primary color), title "Vela" in Display style
- Tabs: "Library", "Recents", "Network" — pill-style tab switcher, glass surface bg, primary color for active pill, no underline indicator
- FAB: circular glass button bottom-right, "+" icon, Primary color, glow shadow — opens file picker

**Library tab:**
- Grid of video cards (2 columns phone, 3 tablet)
- Each card: video thumbnail (Coil), frosted glass overlay at bottom, title + duration badge (pill shape, #0A0A0F at 80% opacity), progress bar at card bottom if partially watched (indigo color), subtle rounded corner 16dp
- Long press: context menu (glass bottom sheet) → Play, Add to Favorites, Delete from library, Share, Info

**Recents tab:**
- Vertical list
- Each row: thumbnail left (70×50dp, radius 10dp), title + folder path (secondary color) + progress % right, timestamp right-aligned
- "Clear all" action top-right

**Network tab:**
- "Open URL" glass input field, paste button, "Play" button (Primary)
- Saved streams list (Room DB) with delete swipe action

---

### 2. FILE BROWSER SCREEN (`FileBrowserScreen.kt`)
- List of folders/files on device storage + SD card
- Each folder row: folder icon (Primary tinted), folder name, file count badge
- Each file row: thumbnail, name, resolution badge, codec badge, file size, duration
- Breadcrumb path display at top (horizontal scroll)
- Floating search bar at bottom with glass style, expands on tap
- Sort/filter sheet: Sort by Name/Date/Size/Duration, toggle show/hide subtitles files

---

### 3. PLAYER SCREEN (`PlayerScreen.kt`) — THE MAIN SCREEN

This is the most important screen. Must be pixel-perfect and buttery smooth.

#### Player Surface
- Full-screen SurfaceView or TextureView (configurable) for video rendering
- Black background underneath
- Tapping surface toggles controls visibility

#### CONTROLS OVERLAY (all frosted glass, auto-hide after 3s)

**Top bar (fade in/out):**
- Back arrow (left) — white icon, 44dp touch target
- Video title (truncated, OnSurface color, Inter SemiBold)
- Cast icon + PiP icon + Overflow menu icon (right side)
- Overflow menu items: Sleep Timer, Equalizer, Video Info, Open subtitles file, Settings

**Center overlay (gesture feedback):**
- Left zone: brightness icon + percentage in a frosted pill — appears on swipe, auto-hides
- Right zone: volume icon + percentage in a frosted pill — appears on swipe, auto-hides
- Center: Play/Pause animation using AnimatedVector (circle ripple + morphing icon)
- Double-tap left: "-10s" feedback (rewind animation) — frosted pill with icon + label, fades in/out
- Double-tap right: "+10s" feedback (fast forward) — same pattern
- Long press: "2×" speed pill at top center

**Bottom controls bar (frosted glass panel, fully rounded 24dp top corners):**
```
Row 1 — Seekbar:
  [timestamp current]  [----seekbar-with-thumb----]  [duration]
  Seekbar must show:
  - Filled portion: Primary gradient
  - Buffered portion: SeekbarBuffer color
  - Track: SeekbarTrack color
  - Thumb: 14dp circle, Primary color, spring animation on press (grows to 18dp)
  - On drag: thumbnail preview popup ABOVE seekbar (120×67dp rounded glass card)
  
Row 2 — Main controls:
  [Rewind 10s] [Prev Track] [Play/Pause 48dp] [Next Track] [FF 10s]
  All icons: white, 36dp touch targets, subtle ripple replaced by 200ms scale pulse (0.92 → 1.0)
  
Row 3 — Secondary controls:
  [Speed pill: 1×]  [Subtitle icon]  [Audio track icon]  [Aspect ratio icon]  [Fullscreen toggle]  [Lock screen icon]
  These are smaller (28dp icons), secondary text color
```

**Speed bottom sheet (glass, half-screen):**
- Title "Playback Speed"
- Horizontal chip group: 0.25× 0.5× 0.75× 1× 1.25× 1.5× 1.75× 2× 3×
- Active chip: Primary filled, others: glass outline
- Custom speed: slider below (0.1× to 5.0×, JetBrains Mono label)

**Subtitle bottom sheet (glass, 60% screen height):**
- "Off" option at top
- List of embedded tracks (radio selection, Primary color active)
- "Open file" button (browse .srt/.ass/.vtt)
- Subtitle style section:
  - Font size slider
  - Text color picker (9 preset swatches + custom)
  - Background opacity slider
  - Position: Top / Middle / Bottom (segmented control)
  - Delay: -/+ buttons with ms label (JetBrains Mono)

**Audio Track sheet:**
- Same structure as subtitle sheet
- Embedded audio tracks (radio)
- Audio delay ±5000ms with slider

**Aspect Ratio sheet (horizontal icon chips):**
- Fit to screen, Fill screen, Crop, Stretch, 16:9, 4:3, Custom Zoom

**Sleep Timer sheet:**
- Chips: Off, 15m, 30m, 45m, 60m, End of video
- Custom: time picker (HH:mm)
- Shows countdown pill in player when active (top-left corner of screen)

#### Lock Screen Overlay
- When locked: dim overlay, padlock icon center, tap gesture shows "Swipe up to unlock" toast
- All other gestures disabled

---

### 4. GESTURE SYSTEM (GestureDetector + PointerInputScope in Compose)

Implement `PlayerGestureHandler.kt`:

```
- Divide screen into zones:
    LEFT third: brightness swipe zone
    CENTER third: double-tap seek zone (+ single tap = toggle controls)
    RIGHT third: volume swipe zone

- Brightness (left zone, vertical swipe):
    UP = increase brightness
    DOWN = decrease brightness
    Use WindowManager.LayoutParams.screenBrightness
    Show frosted pill overlay: ☀ 75%

- Volume (right zone, vertical swipe):
    UP = increase volume (AudioManager.STREAM_MUSIC)
    DOWN = decrease volume
    Show frosted pill overlay: 🔊 60%

- Seek (horizontal swipe, any zone):
    Swipe right = forward (speed proportional to swipe velocity)
    Swipe left = backward
    Show frosted seek pill: ► +30s  →  01:24:15

- Double tap seek:
    Left third, 2 taps = -10s with ripple animation
    Right third, 2 taps = +10s with ripple animation
    Center = Play/Pause

- Pinch:
    Zoom in/out video (scale 0.8x to 3.0x, with pan when zoomed)

- Long press:
    Hold = 2× speed
    Release = 1× speed
    Show "2×" pill at top center

- Swipe sensitivity: configurable in settings (1×, 1.5×, 2×)
```

---

### 5. MEDIA SESSION & BACKGROUND SERVICE (`PlaybackService.kt`)

Use `MediaSessionService` from media3:
- Bind to player
- Lock screen notification: poster art, title, artist (folder name), play/pause, prev, next, close
- Audio focus handling: pause on call, duck for navigation
- Headphone unplug: pause playback
- Bluetooth device connect: resume playback (optional, settings toggle)
- Foreground service notification with custom layout

---

### 6. PICTURE-IN-PICTURE (`PiPHandler.kt`)

- Enter PiP on home press (if setting enabled)
- PiP aspect ratio from video dimensions
- Custom PiP actions: Play/Pause, Close, Expand
- Android 12+ seamless PiP transition animation

---

### 7. EQUALIZER SCREEN (`EqualizerScreen.kt`)

Full-screen glass sheet (slide up):
- 10 vertical sliders (60Hz, 170Hz, 310Hz, 600Hz, 1kHz, 3kHz, 6kHz, 12kHz, 14kHz, 16kHz)
- Each slider: glass track, Primary thumb, -15dB to +15dB range, 0dB center line dotted
- Presets: Normal, Classical, Dance, Flat, Folk, Heavy Metal, Hip Hop, Jazz, Pop, Rock — horizontal scroll chips
- Bass Boost slider (0–1000 millibels)
- Virtualizer strength slider (0–1000)
- Toggle switch for EQ on/off

---

### 8. VIDEO INFO BOTTOM SHEET (`VideoInfoSheet.kt`)

Glass half-sheet, monospace font for values:
- File name, full path
- Resolution (e.g., 1920×1080)
- Frame rate (e.g., 23.976 fps)
- Video codec (e.g., H.265 HEVC)
- Audio codec (e.g., AC3 Dolby 5.1)
- Bitrate
- File size
- Duration
- Subtitles tracks count
- Audio tracks count
- Container format

---

### 9. SETTINGS SCREEN (`SettingsScreen.kt`)

Glass card sections with dividers:

**Playback:**
- Default speed (1×)
- Resume position (ask / always / never)
- Background playback toggle
- Skip silence toggle
- Auto-enter PiP on home press
- Default aspect ratio

**Subtitles:**
- Preferred subtitle language
- Default font size
- Default text color
- Background opacity
- Position

**Gestures:**
- Swipe sensitivity
- Double-tap seek duration (5s / 10s / 15s / 30s)
- Long-press speed (1.5× / 2× / 3×)

**Decoder:**
- Hardware acceleration toggle
- Preferred decoder (auto / HW / SW)
- Surface type (SurfaceView / TextureView)

**Interface:**
- Theme (default dark only for this glass system)
- Show thumbnail on seek (toggle)
- Controls timeout (1s / 2s / 3s / 5s)

**About:**
- App version, open source licenses

---

## DATA LAYER

### Room Entities:
```kotlin
@Entity data class WatchHistory(
    @PrimaryKey val filePath: String,
    val title: String,
    val thumbnailPath: String?,
    val durationMs: Long,
    val lastPositionMs: Long,
    val lastWatchedAt: Long, // epoch ms
    val watchCount: Int
)

@Entity data class Favorite(
    @PrimaryKey val filePath: String,
    val addedAt: Long
)

@Entity data class SubtitlePreference(
    @PrimaryKey val filePath: String,
    val subtitleFilePath: String?,
    val trackIndex: Int,
    val delayMs: Int,
    val fontSize: Float,
    val textColor: Int,
    val bgOpacity: Float
)

@Entity data class SavedStream(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val url: String,
    val savedAt: Long
)
```

### DataStore Settings keys (all typed via `Preferences.Key`):
Resume behavior, default speed, HW acceleration, PiP auto enter, EQ on/off, EQ bands array (JSON), bass boost, virtualizer, subtitle defaults, gesture sensitivity, double-tap seek duration, long press speed, controls timeout, surface type.

---

## ANIMATIONS — MUST IMPLEMENT THESE

1. **Player controls fade**: `AnimatedVisibility` with `fadeIn(tween(220)) + slideInVertically` for bottom bar, `fadeIn + slideInVertically(initialOffset = -40)` for top bar
2. **Seekbar thumb spring**: `Animatable` with spring spec on touch down/up
3. **Thumbnail preview popup**: scale from 0.8 → 1.0 as it appears, spring
4. **Gesture pill popups**: `AnimatedVisibility` fade + scale
5. **Double-tap ripple**: expanding circle from tap point, primary color at 0.3 opacity, fades out 400ms
6. **Play/Pause morph**: `AnimatedVectorDrawable` or `CrossfadeAnimatable` between play triangle and pause bars
7. **Speed chips**: selected chip expands slightly on select with spring
8. **Lock screen icon**: wiggle animation on tap when locked

---

## PERFORMANCE REQUIREMENTS

- No frame drops during gesture handling — all gesture processing must be off the main thread where possible
- Thumbnail generation: background coroutine with `MediaMetadataRetriever`, store in cache dir as JPEG
- File scan: use `ContentResolver` + `MediaStore.Video.Media` for fast initial scan, then supplement with manual `File` walk for SD card / unsupported paths
- Coil: use `VideoFrameDecoder` for video thumbnails
- Memory: release decoder resources on `onPause` if PiP not active, re-acquire on `onResume`
- ExoPlayer: use `DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER` for hardware

---

## PROJECT STRUCTURE

```
app/
├── di/                    (Hilt modules)
├── data/
│   ├── db/               (Room: entities, DAOs, database)
│   ├── datastore/        (Settings DataStore)
│   ├── repository/       (MediaRepository, HistoryRepository)
│   └── model/            (data classes: VideoFile, Stream)
├── domain/
│   └── usecase/          (GetRecentVideos, SaveWatchPosition, etc.)
├── player/
│   ├── PlaybackService.kt
│   ├── PlayerController.kt   (wraps ExoPlayer, exposes StateFlow)
│   ├── GestureHandler.kt
│   ├── PiPHandler.kt
│   ├── SubtitleManager.kt
│   └── EqualizerManager.kt
├── ui/
│   ├── theme/            (Color.kt, Type.kt, Theme.kt — glass design system)
│   ├── components/       (GlassCard, GlassPill, GlassBottomSheet, SeekBar, SpeedChips...)
│   ├── home/             (HomeScreen, HomeViewModel)
│   ├── browser/          (FileBrowserScreen, FileBrowserViewModel)
│   ├── player/           (PlayerScreen, PlayerViewModel, controls composables)
│   ├── equalizer/        (EqualizerScreen, EqualizerViewModel)
│   └── settings/         (SettingsScreen, SettingsViewModel)
└── util/
    ├── ThumbnailCache.kt
    ├── FormatUtils.kt    (ms → 01:24:15, bytes → "1.2 GB")
    ├── MediaScanner.kt
    └── Extensions.kt
```

---

## MANIFEST PERMISSIONS & FEATURES

```xml
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
<uses-permission android:name="android.permission.READ_MEDIA_VIDEO" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" />
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.MODIFY_AUDIO_SETTINGS" />
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
<uses-feature android:name="android.software.leanback" android:required="false" />
<uses-feature android:name="android.hardware.touchscreen" android:required="false" />
```

PlayerScreen activity in manifest:
```xml
android:configChanges="orientation|screenSize|screenLayout|smallestScreenSize|keyboard|keyboardHidden|navigation"
android:resizeableActivity="true"
android:supportsPictureInPicture="true"
android:launchMode="singleTop"
```

---

## IMPORTANT IMPLEMENTATION NOTES

1. Use `media3-exoplayer` NOT the old `exoplayer2` — they have different package names
2. Use `PlayerView` from media3 initially, then replace with fully custom Compose controls overlaid on `AndroidView { SurfaceView }`
3. Subtitle rendering: use `SubtitleView` from media3 placed above the SurfaceView, below controls overlay
4. For glass blur on API < 31: use a dark semi-transparent overlay (#000000 at 55% opacity) + a subtle white border (1dp) as a fallback — still looks great
5. Seekbar thumbnail preview: use `MediaMetadataRetriever.getFrameAtTime()` in a coroutine every 5% of duration, cache frames as Bitmap in WeakReference map
6. Orientation: when user enters fullscreen, set `requestedOrientation = SCREEN_ORIENTATION_SENSOR_LANDSCAPE`, when exits set back to `SCREEN_ORIENTATION_UNSPECIFIED`
7. Status bar & nav bar: in player screen, use `WindowInsetsController` to hide system bars (`BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE`), restore on back press
8. Edge-to-edge: enable via `WindowCompat.setDecorFitsSystemWindows(window, false)` in PlayerActivity
9. Animate seekbar with a `Animatable<Float>` + `LaunchedEffect` polling `player.currentPosition` every 200ms
10. All `BottomSheet`s: use `ModalBottomSheet` from Material3, override `containerColor` with glass surface color, set `shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)`

---

## DELIVERABLES EXPECTED FROM ANTIGRAVITY

1. All Kotlin source files as listed in project structure
2. `build.gradle.kts` (app + root) with all dependencies and version catalog
3. All `res/` files: colors.xml (fallback), themes.xml, font XMLs, vector drawables for icons
4. `AndroidManifest.xml` complete
5. Room migrations (if any)
6. All ViewModels with proper coroutine scoping and StateFlow
7. Hilt modules for all injectable dependencies
8. Compose previews for major UI components

Start with: Theme system → Components → PlayerScreen (most critical) → HomeScreen → FileBrowser → Settings → Equalizer → Service layer.
```

---

## SUMMARY OF MUST-HAVE FEATURES (Quick Reference)

| Category | Features |
|---|---|
| **Playback** | HW decode, all codecs, network streams, gapless |
| **Controls** | Auto-hide, seekbar preview, speed control, loop, shuffle |
| **Gestures** | Swipe brightness/volume, double-tap seek, pinch zoom, long press 2× |
| **Subtitles** | SRT/ASS/VTT, external file, styling, sync delay |
| **Audio** | Multi-track, audio delay, EQ, bass boost, background play |
| **PiP** | Full PiP with custom actions, auto-enter on home |
| **Library** | Folder browser, recents, favorites, search, thumbnails |
| **Design** | iOS glassmorphism, Inter font, #6C63FF accent, spring animations |
| **System** | Lock screen controls, notification, audio focus, headphone events |
| **Settings** | Decoder, gestures, subtitle defaults, PiP behavior, resume |
