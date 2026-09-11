# AllRounderPlayer 🎬🎵

[![Platform](https://img.shields.io/badge/Platform-Android-green.svg)](https://www.android.com/)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0+-blue.svg)](https://kotlinlang.org/)
[![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4.svg)](https://developer.android.com/jetpack/compose)
[![Media3 ExoPlayer](https://img.shields.io/badge/Player-Media3%20ExoPlayer-orange.svg)](https://developer.android.com/media/media3)

**AllRounderPlayer** is a modern, high-performance, and beautifully crafted Android media player app built with 100% Jetpack Compose and Media3 (ExoPlayer). It provides an all-in-one cinematic video playback and rich audio streaming experience with fluid animations, intuitive gestures, and glassmorphic UI aesthetics.

---

## ✨ Features

### 🎬 Advanced Video Player
- **Intelligent Aspect Ratio & Auto-Orientation**:
  - Automatically detects video aspect ratio (e.g. 9:16 vertical vs. 16:9 landscape).
  - Vertical videos open fullscreen in portrait; widescreen videos open in landscape.
  - Dynamically responds to device orientation sensor changes with smooth transitions.
- **Fast Seek Preview**:
  - Instant scrubbing preview thumbnail with LRU frame caching.
  - Zero-lag seeking along the video timeline.
- **Gesture Controls**:
  - Double-tap anywhere to play/pause or double-tap left/right to skip backward/forward 10s.
  - Vertical swipe gestures for screen brightness (left side) and media volume (right side).
  - Dedicated lock mode to prevent accidental touches.
- **Symmetrical Floating Control Bar**:
  - Clean, balanced floating control bar containing: **Audio Track Selector**, **Subtitles**, **Speed Control**, **Sleep Timer**, **Loop Mode**, **Night Filter**, **Background Play**, and **Video Details**.
- **Detailed Metadata & Copy Options**:
  - Inspect video resolution, bitrate, size, path, codec, and duration.
  - One-tap quick copy buttons for copying all video details or the full file path to the clipboard.
- **Background & PiP Playback**:
  - Seamlessly switch to Picture-in-Picture (PiP) or listen to video audio in the background.

---

### 🎵 High-Fidelity Music Player & Background Service
- **Always-On Background Playback**:
  - Powered by Android `MediaSessionService` (`MusicService`) with lock screen controls and notification media session.
- **Real-Time Animated Beat Visualizer**:
  - Interactive multi-bar audio frequency visualizer with reflections that react dynamically to play/pause state.
- **Now Playing Indicator in Video Mode**:
  - When music is playing in the background, an animated floating pill with real-time equalizer bars appears on the Video screen.
  - One-tap instant return to the music player.
- **Fallback Album Art**:
  - Graceful fallback to styled pulsating music note icons for songs without embedded thumbnail art.
- **Interactive Fullscreen Player with Artwork Themes**:
  - 5 customizable visualizer themes: **Vinyl Record** (spinning animation), **Retro Cassette Tape**, **Neon Glow**, **Waveform Stage**, and **3D Frosted Glass Card**.
- **Built-in Equalizer & Presets**:
  - 5-band equalizer with presets (Flat, Bass Boost, Rock, Pop, Jazz, Electronic, Vocal, Acoustic, Classical, Hip Hop).

---

### 📁 Smart Media Library & File Explorer
- **Folder & List Management**:
  - Video folders categorized with thumbnails, video counts, and total folder sizes.
  - Pinned folders float to the top for immediate access.
  - Quick single-tap cycle button for layouts (Card, Grid, List views).
- **Multi-Select & Batch Actions**:
  - Batch select videos to share, delete, or inspect.
  - Swipe-to-dismiss gesture on video items for quick deletion or sharing.
- **Continue Watching & Favorites**:
  - Remembers exact playback position and resumes seamlessly.
  - Favorite tracks and videos for instant one-click access.

---

## 🛠️ Tech Stack & Architecture

- **UI**: [Jetpack Compose](https://developer.android.com/jetpack/compose) (Material 3, animations, gestures, custom canvas drawings)
- **Media Engine**: [AndroidX Media3 (ExoPlayer)](https://developer.android.com/media/media3)
- **Image & Thumbnail Loading**: [Coil 3](https://coil-kt.github.io/coil/) with `VideoFrameDecoder`
- **Audio Effects**: Android AudioFX API (`Equalizer`, `BassBoost`, `Virtualizer`)
- **Language**: Kotlin 2.0+ with Coroutines & StateFlow
- **Architecture**: Single-Activity Compose MVVM architecture with Repository pattern

---

## 🚀 Getting Started

### Prerequisites
- Android Studio Ladybug / Koala or newer
- Android SDK 34+
- Physical device or emulator running Android 8.0 (API 26) or above

### Clone and Run
```bash
git clone https://github.com/yashwantmandal26/AllRounderPlayer.git
cd AllRounderPlayer
```
Build the debug APK:
```bash
./gradlew assembleDebug
```
Install directly onto a connected device via ADB:
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## 📜 Permissions

- `READ_MEDIA_VIDEO` & `READ_MEDIA_AUDIO` (Android 13+)
- `READ_EXTERNAL_STORAGE` (Android 12 and below)
- `POST_NOTIFICATIONS` (Foreground playback notification)
- `WAKE_LOCK` (Continuous smooth playback)

---

## 👨‍💻 Author

**Yashwant Mandal**  
- GitHub: [@yashwantmandal26](https://github.com/yashwantmandal26)

---

## 📄 License
This project is open-source and available under the [MIT License](LICENSE).

