# Adaptive video haptics

The Haptics control sits immediately left of Enhanced mode. The preference defaults
to off and survives player recreation. Playback requires a vibrator, active video
audio, and decoded PCM16 or float PCM. Compressed passthrough/offload and the VLC
fallback keep their existing playback behavior and produce no adaptive effects.

The current dynamic-texture implementation and verification are described in
[the dynamic haptics report](testing/2026-11-14-dynamic-haptics.md). Long-press Haptics
to tune overall strength and the synchronization offset for the current audio route.

## Implementation

- `HapticAudioSink` wraps the existing Media3 sink, preserving the dynamics
  processor and every audio byte. It analyzes each incoming buffer once, including
  when the delegate retries a partially consumed buffer. PCM buffers are frame
  aligned under the AudioSink contract. No decoder buffer reference is retained.
- `HapticDetector` aggregates channel powers independently (opposite phase cannot
  cancel), removes DC/subsonic bias from its analysis, and measures bass and changing
  midrange energy in approximately 5 ms windows. It emits confirmed impacts and
  sustained audio envelopes. Impact timestamps retain the original onset window.
  Sustained textures use short rolling windows with their original audio timestamps.
  These remain acoustic heuristics, not a semantic sound classifier.
- Input buffer presentation timestamps and the sink's reported media playback
  position use the same renderer time domain. The latter is sampled on the audio
  thread and published with a monotonic timestamp and speed. A player-owned worker
  extrapolates at most 100 ms and dispatches due events. There is no assumed fixed
  audio-buffer lead time or additional delay added to playback.
- A 32-event priority mailbox rejects old generations and orders both onsets and
  texture packets by audio timestamp. Late impacts are dropped; late texture samples
  are trimmed. Known adjacent textures batch into waveforms of up to 240 ms without
  predicting future audio. Impact deadlines can interrupt textures. The 180 ms
  cooldown applies to impacts, not texture updates.
- `HapticSignal` carries continuously varying amplitudes and distinct click, punch,
  grain, rumble, and swell envelopes. Timing scales with playback speed. Amplitude-
  capable devices play the shaped waveform; basic hardware retains short effects.
  Capabilities and media attributes are cached. This does not synthesize unsupported
  physical motor frequencies.
- The rolling limit is now 400 full-amplitude-equivalent milliseconds per five
  seconds, measured as duration × squared normalized amplitude. It attenuates
  shaped effects when needed and survives seeks/toggles. This is an exposure metric,
  not electrical power; it replaces the older 400 ms raw on-time restriction so
  gentle sustained texture is possible.
- Pause/buffering, seeking, speed changes, item/session changes, route changes,
  disable, sink flush/reset, and player release invalidate queued events. Main
  playback transitions cancel active vibration. A single player-owned scheduler
  prevents duplicated workers on recomposition or rotation.

## Responsiveness improvements (14 November 2026)

- The worker starts only when haptics are active. It sleeps toward the next event
  (1–40 ms), wakes when an empty mailbox receives an event, and never puts an
  8 ms polling task or vibrator playback on the UI thread. PCM producers do not
  wait for vibrator IPC. Release cancels output and shuts down the worker.
  It stops scheduling when its mailbox is empty. Route-policy checks run off the
  UI thread at most once per second while it is processing queued audio.
- Sample timestamp divisions occur once per analysis window rather than once
  per PCM frame; channel power normalization occurs once per window too.
- Player timestamps have their own composable and only recompose when the
  displayed text changes. The seekbar still receives its original 16 ms updates.
- Torrent piece-range scans run on IO every 500 ms, independently of the frame
  loop. Downloading, stream priorities, seeking, and downloaded-range display
  remain available. The unused buffered-position state/poll was removed.
- Regression checks found the sleep timer dialog had no reachable player control
  in the existing layout. Its More-menu button was restored, including the active
  remaining-minutes label.

On the connected I2012 / Android 14, the same 30-second local fixture was measured
for 7 seconds with controls visible, retaining the user's picture/audio settings.
The final debug comparison (one 7-second sample per configuration) was:

| Haptics | UI CPU before / after | Frame p50 before / after | Frame p95 before / after |
| --- | --- | --- | --- |
| Off | 4599 / 3190 ms | 18.77 / 15.15 ms | 28.89 / 18.96 ms |
| On | 4429 / 3407 ms | 19.02 / 16.30 ms | 28.92 / 24.90 ms |

This is 23–31% less UI-thread CPU in this sample, not an app-wide speed guarantee.
An earlier improved-build run showed a 19–32% reduction; timing varies between runs.
FrameMetrics measures app-window frame work, not video decoder frame drops.
The fixture does not establish performance on large torrents or Bluetooth routes.
`VideoPlaybackPerformanceTest` records repeatable CPU and frame-time measurements
without changing picture quality, audio processing, or the progress-bar cadence.

## Earlier impact-only verification

- 148 unit tests passed, including 20 haptics tests. New cases cover quiet bass,
  bass over a soundtrack, light transient strength, original onset timestamps,
  deadline-aware mailbox wakes, and preserving PCM byte order across retries.
- Debug and optimized release APK tasks completed. The combined build/lint command
  exits nonzero solely because of the existing lint error described below.
- 13 distinct device checks passed across the final runs on I2012 / Android 14:
  two haptics tests (real AudioTrack clock and output while the UI is blocked),
  four audio-pipeline tests, one performance probe, and all six interaction tests.
  The interaction suite passed together after restoring timer access and updating
  its selector to click the icon button rather than the non-clickable caption.
- Lint still reports the existing `UnusedBoxWithConstraintsScope` error in unchanged
  `MyCinemaDetailModal.kt:2253`; no errors are reported in the changed haptics/UI files.
- The production-signed, non-debuggable release APK was installed over the regular
  app (`com.example.ymediaplayer`, 2.8.3 / 43). A cold-launch playback smoke check
  rendered the test video at 1.792 seconds with no AndroidRuntime crash. Temporary
  device fixtures were removed. Existing preferences were restored by device tests.
- Scoped whitespace checks passed. A whole-tree check also finds six existing
  trailing-whitespace lines in the separately edited `FolderListScreen.kt`.

Logs and the release-playback screenshot: `app/build/reports/haptics-v2/`.
Release APK: `app/build/outputs/apk/release/app-release.apk`.
SHA-256: `6F48E1BCA8DF2655324A85098B387E56290D2DC33A2FC203CA5FE770B0689EC1`.

This estimates impacts; it cannot identify gunshots or reject every shouted word
or musical attack. Initial warm-up and invalidation can omit impacts in audio
already queued for playback. Timestamp polling, Android scheduling, motor startup,
and route latency introduce uncertainty. Bluetooth latency is device dependent.
The feature makes no frame-perfect synchronization claim.

## Verification

Unit coverage includes silence, noise, steady tones, sharp/bass bursts, sample rates,
opposite-phase multichannel input, buffer boundaries, float/PCM16 preservation,
delegate retries, cooldown, rolling limits, speed mapping, late/stale events, and
unavailable vibration output. Synthetic tests establish mechanics, not recognition
accuracy on films.

`CinemaHapticsPlaybackTest` exercises a real device AudioTrack/sink clock with
recorded output instead of physical vibration. The video interaction regression
test checks control placement, preference changes, seek, and pause behavior.

Manual validation before release:

- Compare dialogue, orchestral music, action, and silence; check false triggers.
- Listen and feel using speakers, wired headphones where available, and Bluetooth.
- Compare Enhanced mode on/off; confirm audio remains unchanged by Haptics.
- Pause/resume, seek both directions, buffer, and change playback speed.
- Rotate, switch videos, leave/return to the player, and toggle repeatedly.
- Check unsupported hardware, compressed audio routes, and VLC fallback.

Actual tactile quality and perceived audio alignment require hands-on validation.

## Original implementation verification results

- `:app:testDebugUnitTest`: 143 tests passed, including 15 haptics unit tests.
- `:app:assembleDebug`: passed; APK at `app/build/outputs/apk/debug/app-debug.apk`.
- `:app:connectedDebugAndroidTest` with runner class
  `com.example.ymediaplayer.player.CinemaHapticsPlaybackTest`: passed on I2012,
  Android 14, using real audio output and recorded vibration events.
- The same task with runner class
  `com.example.ymediaplayer.ui.VideoInteractionRegressionTest#hapticsTogglePersistsAndPlaybackSurvivesSeekAndPause`:
  passed on that phone (placement, persistence, seek/pause, and toggling).
- `:app:lintDebug`: completed analysis with one existing error in unchanged
  `mycinema/ui/MyCinemaDetailModal.kt:2253` (`UnusedBoxWithConstraintsScope`),
  plus 286 warnings and 12 hints. No haptics-file lint errors were reported.
- `git diff --check`: passed.

Build logs are in `app/build/reports/haptics/`; the lint report is
`app/build/reports/lint-results-debug.html`.

## Settings and live preview

Settings → Haptics now provides separate speaker, wired and Bluetooth profiles,
Subtle/Balanced/Cinematic presets, overall/impact/rumble intensity, texture detail,
dynamic range, sensitivity, family switches and ±250 ms synchronization.
Touch feedback has its own global intensity. Profile edits are saved automatically.

Live preview offers finite impact, rumble, swell, grain, mixed and touch samples,
a waveform cursor, Stop and profile reset. Slider previews are coalesced on a
worker thread. Optional sound cues follow the actual Media3 AudioSink position.
Previews temporarily suppress movie effects and stop when settings leaves the
foreground. Explicit previews work while the movie master switch is off.
