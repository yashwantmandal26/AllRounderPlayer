# Playback audit — 2026-10-14

Scope: read-only source review of video playback, navigation, enhancement/audio preferences, subtitles, PiP, seeking and compatibility playback in the current v1.7.1 working tree. No production or test files were changed. No device commands or Gradle commands were run by this auditor. No applicable AGENTS.md was present in the repository or its ancestors.

These findings are static findings, not device reproductions. “High confidence” means the trigger and resulting state are directly established by the current code; it does not imply that a phone test was run.

## QA-P01 — Embedded captions remain disabled after switching from imported subtitles

- Severity: P2 (broken subtitle selection).
- Confidence: High; source-confirmed state overwrite. Not device-reproduced.
- Primary location: `C:/Users/Yashwant/Desktop/AllRounder Media Player/app/src/main/java/com/example/ymediaplayer/ui/VideoSubtitleTools.kt:83` and `:88`–`:89`.
- Related locations: `C:/Users/Yashwant/Desktop/AllRounder Media Player/app/src/main/java/com/example/ymediaplayer/ui/VideoPlayerScreen.kt:4541`, `:6308`–`:6313`, `:6372`–`:6377`.

Reproduction:

1. Open a video that contains an embedded subtitle track.
2. Select **Subtitles Off** so the text track type is disabled.
3. Use subtitle tools to import an SRT or VTT file and wait until its cues are visible.
4. Open the subtitle track picker and select an embedded track.

Expected: The selected embedded captions render. Actual inferred from source: text tracks end up disabled, so the selected captions do not render.

Evidence and false-positive check: The external-subtitle DisposableEffect captures `wasDisabled=true` at line 83 before disabling embedded tracks. The embedded-track click explicitly enables text tracks at VideoPlayerScreen line 6372, then invokes `onSelectEmbedded` at line 6376. That callback calls `subtitleState.useEmbedded()` at line 4541, clearing the URI and cues. Changing `externalActive` from true to false disposes the old effect, whose cleanup restores its captured `wasDisabled=true` at VideoSubtitleTools lines 88–89. The new effect does not re-enable tracks when externalActive is false. This is not the intended “Use embedded” restoration path alone: the explicit user selection is overwritten after it has enabled the track.

Suggested correction: Apply the selected track after external-subtitle cleanup, or avoid restoring a stale disabled flag when the user has just made an explicit selection.

## QA-P02 — A–B loop boundaries remain active on a different video

- Severity: P2 (unexpected seeking / incomplete playback).
- Confidence: High; source-confirmed state lifetime. Not device-reproduced.
- Primary location: `C:/Users/Yashwant/Desktop/AllRounder Media Player/app/src/main/java/com/example/ymediaplayer/ui/VideoPlayerScreen.kt:879`–`:880`.
- Related locations in the same file: playlist selection `:739`–`:748`; Next `:1354`–`:1376`; Previous `:1414`–`:1436`; active loop check `:2034`–`:2046`; setting and clearing boundaries `:3518`–`:3526`, `:4760`–`:4768`.

Reproduction:

1. Open a video longer than 15 seconds.
2. Set an A–B loop from 5 seconds to 10 seconds.
3. Press Next, Previous or choose another video inside the current player's playlist, using another video longer than 15 seconds.
4. Let the second video pass 10 seconds.

Expected: The loop belongs to the first video and is cleared on media change. Actual inferred from source: the second video seeks to 5 seconds and loops the old segment.

Evidence and false-positive check: Both boundaries use unkeyed `remember`. All three internal navigation paths mutate `currentUrl` and replace the media item on the same remembered ExoPlayer; none recreate VideoPlayerScreen or clear loop state. Searching every loopStartMs/loopEndMs assignment found only the A/B buttons and their explicit third-click clear. The progress coroutine checks the surviving boundaries and seeks whenever the new video's position reaches B. VideoPlayerActivity's key depends on its initial activity URL/requestSequence; internal playlist navigation does not change those values. Opening a new player activity from the library may reset the loop and is not the reproduction path asserted here.

Suggested correction: Key the loop state to currentUrl or clear both boundaries in the shared media-switch operation.

## QA-P03 — Next/Previous ignore “Start from beginning”

- Severity: P2 (incorrect resume behavior / content skipped).
- Confidence: High; source-confirmed policy inconsistency. Not device-reproduced.
- Primary location: `C:/Users/Yashwant/Desktop/AllRounder Media Player/app/src/main/java/com/example/ymediaplayer/ui/VideoPlayerScreen.kt:1358`, `:1370`–`:1373`.
- Related locations in the same file: Previous `:1418`, `:1430`–`:1433`; correct initial-open policy `:701`–`:702`; correct playlist-selection policy `:741`; autoplay uses Next at `:1586`.

Reproduction:

1. Save an unfinished position greater than 3 seconds in video B.
2. Set Resume Mode to **START / Start from beginning**.
3. Open video A in B's folder, then press Next to B (or allow auto-advance). Test Previous similarly.

Expected: B starts at 0. Actual inferred from source: B starts at saved progress if it is not marked completed.

Evidence: Initial opening and direct playlist selection check getResumeMode() before loading saved progress. Next and Previous read saved progress unconditionally and only check whether progress exceeds 3 seconds and the file is not completed. Both call setMediaItem with that saved position. Auto-advance invokes the same Next callback.

Suggested correction: Share one resume-position policy across initial opening, playlist selection, Next, Previous and automatic advancement.

## QA-P04 — Settings sound presets no longer affect a route after a player preset was saved

- Severity: P2 (setting appears applied but playback retains another preset).
- Confidence: High; source-confirmed preference-key mismatch. Not device-reproduced.
- Primary location: `C:/Users/Yashwant/Desktop/AllRounder Media Player/app/src/main/java/com/example/ymediaplayer/ui/SettingsScreen.kt:1137`–`:1138`.
- Related locations: `C:/Users/Yashwant/Desktop/AllRounder Media Player/app/src/main/java/com/example/ymediaplayer/data/DataRepository.kt:319`–`:325`; `C:/Users/Yashwant/Desktop/AllRounder Media Player/app/src/main/java/com/example/ymediaplayer/ui/VideoPlayerScreen.kt:1116`, `:1305`, `:4013`.

Reproduction:

1. While using the phone speakers, choose Cinema in the player's Audio mode picker.
2. In Settings, choose Voice as the video sound preset.
3. Return to playback or close and reopen the video on speakers.

Expected: The Settings choice controls playback, or the UI clearly indicates that it is a default overridden by separate output settings. Actual inferred from source: Settings shows Voice while the route continues using Cinema.

Evidence: Settings updates only the global video_sound_mode key. The player writes video_sound_speakers/video_sound_headphones, reads via getVideoRouteSoundMode, and reloads the same route value after its settings overlay closes. The route getter always prioritizes an existing route key over the global key. Thus, after the first player preset selection for an output, later Settings preset choices have no effect on that output even on a fresh player session.

Suggested correction: Have Settings edit the selected output's preset, or explicitly present the global setting as a default and expose the existing output overrides.

## QA-P05 — Seek preview can retain hundreds of MiB on Android API 24–26

- Severity: P2 (conditional memory/performance reliability risk; crash not verified).
- Confidence: High for unbounded-by-bytes full-resolution caching; medium for actual OOM on a particular device. Not device-reproduced.
- Primary location: `C:/Users/Yashwant/Desktop/AllRounder Media Player/app/src/main/java/com/example/ymediaplayer/ui/VideoSeekPreview.kt:35`, `:42`–`:45`.
- Related locations: error catch `:51`; app supports API 24+ in `C:/Users/Yashwant/Desktop/AllRounder Media Player/app/build.gradle.kts:22`.

Reproduction candidate:

1. Use a supported API 24, 25 or 26 device with limited app heap.
2. Play a high-resolution video, preferably 3840×2160.
3. Hold a seek gesture and move over enough positions to extract 12 distinct preview frames.
4. Measure heap use and check whether playback becomes unstable or the process throws OutOfMemoryError.

Expected: Small thumbnails with a bounded memory budget. Actual source behavior: API 27+ requests 240×136 scaled thumbnails; API 24–26 calls getFrameAtTime without resizing and retains up to 12 bitmap entries. LruCache's default unit is entries, not bytes. At ARGB_8888, twelve 3840×2160 frames require 398,131,200 bytes (about 380 MiB), before other player/app allocations. The catch handles Exception, which does not catch OutOfMemoryError. Actual frame configuration and device heap determine whether an OOM occurs; do not label this an observed crash.

Suggested correction: Downscale frames before caching on older APIs and bound cache memory by allocation bytes.

## Coverage limits / discarded candidates

- Existing subtitle tests exercise parsing and timing; they do not cover the DisposableEffect cleanup or user track-switch sequence.
- Existing seek/resume helper tests do not cover the actual Next/Previous callbacks or cross-video A–B state.
- No assertion is made that all audio modes fail; the Settings issue specifically requires a previously saved route preset.
- A possible Background Audio lifecycle mismatch was considered but excluded from the findings because the apparent in-player BG control belongs to an unused composable and the active path is a PiP action; its runtime behavior needs device verification.
- No unsupported claim of a device crash or an instrumented playback test is made in this report.
