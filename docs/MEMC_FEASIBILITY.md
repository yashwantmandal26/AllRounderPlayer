# MEMC / real-time frame interpolation feasibility

Research date: 2026-09-17

## Decision

Real MEMC is possible in AllRounderPlayer, but it is not a normal ExoPlayer option and it cannot be guaranteed for every video or every Android device.

The recommended implementation is an optional custom Media3 `VideoSink` backed by a native Vulkan inference engine. Keep Media3 for demuxing, decoding, audio, subtitles, seeking, and playback state. The custom sink buffers decoded frames, generates intermediate frames, and presents both original and generated frames at display-vsync timestamps.

Do not label frame duplication, display refresh-rate switching, or mpv temporal blending as MEMC. None of those estimates motion and constructs a motion-compensated intermediate frame.

## Why a regular Media3 effect is insufficient

The current app creates one shared ExoPlayer in `VideoPlaybackManager.getOrCreatePlayer()` and attaches it to full-screen and mini-player `PlayerView` instances. This is a good base for a custom sink.

Media3 playback effects apply work to each existing decoded frame. The documented ExoPlayer effects path does not support effects that change frame timestamps, so it cannot turn one input frame into multiple scheduled output frames. `Surface.setFrameRate()` can encourage a matching display refresh rate but explicitly does not create or throttle frames.

A proper pipeline needs look-ahead: to create a frame between A and B, both A and B must already be decoded. The video path therefore needs buffering and explicit presentation scheduling. Seek, speed change, track change, surface replacement, pause/resume, end-of-stream, and decoder flush must all clear or rebuild that buffer.

## Recommended architecture

```text
Media3 demux + MediaCodec decode
              |
              v
Custom MediaCodecVideoRenderer / VideoSink
              |
              v
GPU texture ring: previous + current frame
              |
              +--> scene-cut detector --> original frames only
              |
              +--> Vulkan RIFE inference at one or more time steps
              |
              v
Vsync scheduler --> PlayerView output Surface
```

Suggested modules:

- `player/memc/MemcMode.kt`: Off, Auto, 2x, DisplayRate, plus quality tier.
- `player/memc/MemcCapabilities.kt`: Vulkan, ABI, memory, refresh-rate, HDR, secure-content, and thermal checks.
- `player/memc/MemcVideoSink.kt`: Media3 `VideoSink` contract, queueing, timestamps, flush, and fallback.
- `player/memc/MemcRendererFactory.kt`: supplies a `MediaCodecVideoRenderer` configured with the custom sink.
- `player/memc/MemcScheduler.kt`: maps variable-rate input timestamps to 48/60/90/120 Hz output times.
- `app/src/main/cpp/memc/`: JNI bridge, Vulkan texture interop, ncnn, model loading, and inference.
- `player/memc/MemcTelemetry.kt`: inference time, queue depth, dropped/generated frames, thermal fallback reason.

The open-source starting point with the most practical Android path is RIFE through `rife-ncnn-vulkan`: ncnn supports Vulkan on Android API 24+, matching this app's minimum SDK. Its code is MIT-licensed, while ncnn is BSD-3-Clause. Bundle only model weights whose redistribution terms have also been verified and record all notices.

FILM is useful as an offline quality reference, not as the first mobile real-time engine. Its official implementation targets TensorFlow/CUDA-style offline processing and the repository was archived in 2025.

## Output-rate rules

- Preserve source presentation timestamps; never assume constant-frame-rate input.
- Choose a supported display mode first, then target the lowest useful rate: 24 -> 48/60, 25 -> 50, 30 -> 60, 50 -> 100 when available, and 60 -> 120 when available.
- For 24 -> 60, generate timestamps at actual 60 Hz intervals; do not use a fixed 2x multiplier.
- Do not interpolate when source FPS is already at or above the selected display rate.
- Detect cuts, flashes, large discontinuities, and duplicate frames. Across a cut, repeat or present the nearest original frame instead of synthesizing one.

## Capability and fallback policy

`Auto` should enable interpolation only when all required checks pass:

- 64-bit ARM device with Vulkan support and a validated GPU/driver.
- Enough free graphics memory for the chosen resolution and model.
- Measured inference time remains comfortably below the frame budget.
- Clear, non-secure video; Media3's effects path does not support DRM, and app-readable GPU processing must not be promised for protected content.
- Supported color pipeline. The first release should be SDR only. HDR10, HLG, and Dolby Vision should bypass MEMC until a verified linear-light, wide-gamut path exists.
- Thermal status below the configured limit and battery saver disabled, unless the user explicitly overrides it.

On failure or overload, drop generated frames first, reduce interpolation resolution next, then disable MEMC and continue normal playback. Playback must never fail because MEMC failed.

Android TV has a separate system Media Quality framework on Android 16+. Newer APIs define MEMC picture-profile parameters, but profile creation can require an allowlist and support is OEM-dependent. Treat this as an optional hardware fast path detected at runtime, not the cross-device implementation.

## Product controls

Add a playback setting and an in-player quick control:

- Motion smoothing: Off / Auto / 2x / Match display
- Quality: Performance / Balanced / Quality
- Maximum processing resolution: 720p / 1080p / Native
- Status line: active output FPS, quality tier, and fallback reason

Default to Off during the experimental phase and Auto after enough device telemetry exists. Warn that battery use can resemble a demanding game; SVP itself recommends roughly Snapdragon 865-class hardware for 1080p and documents that some Android TV hardware must reduce processing to 1080p or 720p.

## Supported-content promise

Use this wording rather than “works for any video”:

> Works with supported local and network videos when the device has sufficient Vulkan performance. Automatically falls back to normal playback for DRM, unsupported HDR paths, thermal throttling, driver failures, or content already at the display frame rate.

Container and codec coverage still comes from Media3/MediaCodec. MEMC operates on decoded frames, so it does not add codec support.

## Delivery plan

1. Build an offline Android benchmark screen that decodes two frames and measures the chosen RIFE model at 720p and 1080p on representative Adreno, Mali, and PowerVR devices.
2. Implement an SDR-only custom sink with 2x interpolation, scene-cut bypass, seeking/flush, pause/resume, and automatic fallback.
3. Add variable-frame-rate and 24 -> 60 scheduling, playback-speed handling, mini-player/PiP surface replacement, and instrumentation.
4. Gate release through a device compatibility table and a 30-minute thermal/battery test.
5. Investigate HDR and Android TV system-MEMC profiles separately; do not block the SDR release on them.

## Acceptance tests

- 23.976, 24, 25, 29.97, 30, 50, and 59.94 fps clips.
- H.264, HEVC, VP9, AV1, 8-bit and 10-bit decode paths, with unsupported combinations falling back cleanly.
- Variable-frame-rate phone recordings and files with broken/missing FPS metadata.
- Rapid cuts, flashes, animation, panning shots, occlusions, subtitles, and letterboxing.
- Repeated seeking, orientation changes, full-screen to mini-player, PiP, speed changes, track changes, and end-of-file looping.
- 30-minute runs at 720p and 1080p with generated-frame drop rate, A/V offset, thermal state, memory use, and battery drain recorded.
- No regression to ordinary playback when MEMC is Off or when native initialization fails.

## Primary references

- Android Media3 ExoPlayer video effects limitations: https://developer.android.com/reference/androidx/media3/exoplayer/ExoPlayer#setVideoEffects(java.util.List%3Candroidx.media3.common.Effect%3E)
- Media3 `VideoSink`: https://developer.android.com/reference/androidx/media3/exoplayer/video/VideoSink
- Android `Surface.setFrameRate()`: https://developer.android.com/reference/android/view/Surface#setFrameRate(float,int,int)
- Android TV Media Quality framework: https://developer.android.com/training/tv/mqf
- SVPlayer architecture and requirements: https://www.svp-team.com/wiki/SVPlayer and https://www.svp-team.com/wiki/FAQ_(Android)
- RIFE ncnn Vulkan: https://github.com/nihui/rife-ncnn-vulkan
- ncnn Vulkan on Android: https://github.com/Tencent/ncnn/wiki/FAQ-ncnn-vulkan
- Official FILM implementation: https://github.com/google-research/frame-interpolation
- mpv interpolation semantics: https://mpv.io/manual/stable/#options-interpolation
