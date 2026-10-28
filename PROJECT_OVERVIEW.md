# AllRounder Media Player

AllRounder Media Player is an Android app for browsing and playing local video and music. It also includes **My Cinema**, which provides movie and TV metadata, torrent source discovery, downloads, and streaming.

## Main features

- Video playback with subtitles, track selection, gestures, picture-in-picture, and playback resume.
- Music playback with background controls, playlists, lyrics, visualizers, and audio effects.
- Local media library, folders, favorites, and file operations.
- My Cinema discovery using TMDb, with torrent download and streaming support.

## Technology

The app is written in Kotlin and uses Jetpack Compose for its interface. Media playback is based on AndroidX Media3, with LibVLC and FFmpeg decoder support for additional formats. Coroutines, OkHttp, Coil, and libtorrent4j support background work, networking, artwork, and torrent features.

## Source layout

- `app/src/main/java/.../ui/` — Compose screens and player interface.
- `app/src/main/java/.../data/` — media repositories, file operations, and preferences.
- `app/src/main/java/.../player/` and `service/` — playback engines and background services.
- `app/src/main/java/.../mycinema/` — TMDb, torrent, download, and cinema UI code.
- `app/src/test/` and `app/src/androidTest/` — JVM and device tests.

The project is a single Gradle app module (`:app`). Current build settings use **JDK 17**, **Android SDK 37**, and **minSdk 24**. Native torrent binaries are configured for **arm64-v8a**.

## Build

Install Android SDK 37 and JDK 17. Gradle configuration requires a local `keystore.properties` file even for a debug build; create it from `keystore.properties.example` and provide valid signing values. Then run:

```bash
./gradlew assembleDebug
```

Run JVM unit tests with `./gradlew testDebugUnitTest`. Device tests require a connected Android device or emulator: `./gradlew connectedDebugAndroidTest`.
