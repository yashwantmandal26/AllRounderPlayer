# IndiPlayer

A premium, feature-rich Android media player built with modern Android development practices, Jetpack Compose, and ExoPlayer (Media3).

## Features

### 🎬 Video Player
* **Dynamic Gestures**: Control volume, brightness, and playback seek with intuitive swipe gestures.
* **Smart UI**: Auto-hiding controls, screen lock mechanism, and Picture-in-Picture (PiP) support.
* **Resume Playback**: Automatically remembers where you left off with a "Continue Watching" banner.
* **Quick Tools**: Built-in screenshot capture, aspect ratio cycling, and playback speed control.
* **Thumbnail Previews**: Real-time frame previews while scrubbing the timeline.

### 🎵 Music Player
* **Interactive Visuals**: Includes a premium, responsive multi-colored beat visualizer that reacts to playback state.
* **Mini & Fullscreen Player**: Seamlessly transition between a floating mini-player and a stunning fullscreen immersive UI.
* **Blurred Album Art**: Dynamic background generation based on the currently playing track.
* **Continuous Playback**: Music keeps playing smoothly as you navigate through the app.

### 📁 Media Library
* **Smart Organization**: Automatically categorizes videos into folders and a "Recently Added" section.
* **Custom Views**: Toggle between Grid and List views for your folders.
* **Sorting Options**: Sort your media by Name, Date, or Size.
* **Favorites**: Mark videos as favorites for quick access.
* **Dark Mode**: Fully adaptive dynamic dark mode/light mode switching.

## Tech Stack

* **UI Framework**: [Jetpack Compose](https://developer.android.com/jetpack/compose) for a fully declarative UI.
* **Media Playback**: [Media3 (ExoPlayer)](https://developer.android.com/media/media3) for robust audio and video playback.
* **Image Loading**: [Coil 3](https://coil-kt.github.io/coil/) for fast asynchronous image and video frame decoding.
* **Language**: [Kotlin](https://kotlinlang.org/)

## Permissions

The app requests the following permissions to function correctly:
* `READ_EXTERNAL_STORAGE` (Android 12 and below)
* `READ_MEDIA_VIDEO` & `READ_MEDIA_AUDIO` (Android 13+)
* `INTERNET` (For future streaming capabilities)

## Architecture

* **MVVM / Compose State**: Relies on Compose's powerful state management to maintain synchronous UI updates.
* **Local Storage**: Uses `SharedPreferences` to persistently store user preferences, sort orders, favorites, and playback histories.
* **Edge-to-Edge**: Full screen rendering for an immersive, modern look.

## Building and Running

1. Clone the repository.
2. Open the project in Android Studio (Giraffe or newer recommended).
3. Let Gradle sync and resolve dependencies.
4. Run the app on an emulator or a physical device.

---
*Built with ❤️ for a seamless media experience.*
