# Song sound modes and beat haptics

The full music player has a compact **Beat haptics** icon beside Favorite. Tap to
toggle; long press to adjust overall strength and synchronization for the current
speaker, wired or Bluetooth route. The
sound-mode button in the lower controls opens a menu with four choices:

| Mode | Processing |
| --- | --- |
| Original | No app EQ, bass enhancement or virtualizer; the sound processor copies PCM bytes unchanged. |
| Bass Punch | +7 dB around 220 Hz, a low-mid cut, a small presence lift, and upper harmonics of low bass to make kicks perceptible on small speakers. |
| Vocal Clarity | Low-mid reduction and a broad +6 dB presence lift around 1.8 kHz, with additional articulation around 4 kHz. |
| Live Stage | Warmth around 220 Hz, reduced boxiness around 900 Hz and brighter percussion/instruments around 3.2 kHz. |

Enhanced modes run in `MusicSoundProcessor` before the existing music visualizer.
They do not depend on a device exposing working Android Equalizer, BassBoost or
Virtualizer effects. Named modes disable those hardware effects to avoid double
processing. Manual EQ retains the previous AudioFX path. Original switches those
effects off. Legacy Balanced and 3D Spatial selections map to Original and Live
Stage respectively. The selection persists through service recreation.

Live changes crossfade for 20 ms without seeking or rebuilding the player. The
enhanced modes reserve headroom and use a channel-linked peak limiter. Processing
does not boost silence, add reverb latency, or change frame count. The sink uses
Media3's PCM16 processor path, including conversion of higher-depth source audio;
Original bypass refers to app sound effects, not a bit-perfect digital output
claim. The compatibility/VLC path does not run these software effects.

## Beat haptics

`MusicHapticDetector` uses independent channel powers, a 35–105 Hz bass band,
deep-bass support and a separate voice band. It measures bass novelty against its
own adaptive floor instead of requiring bass to dominate total song energy.
Two 10 ms windows confirm an onset. Deep-band support, relative voice novelty,
rearming and ringing rejection suppress vocal/tonal false triggers. Song events
have a 110 ms detector interval and 100 ms output cooldown, allowing fast beat
runs without changing the movie cooldown.

Confirmed beats produce a 28 ms pulse with an immediate attack and tapered
release. Its peak varies continuously from 48–218/255 with acoustic bass energy,
before the user's strength setting and rolling exposure limit. Quiet beats remain
tactile and stronger beats create a stronger pulse. Sustained tones do not become
continuous buzz. The previous weaker 29 ms envelope and 75% gain are replaced.

Onsets are backdated by 10 ms for filter/window delay. The default song profile
dispatches another 20 ms earlier as a motor-start allowance. This is a conservative
heuristic, not a hardware latency measurement. Long-press calibration supports
±250 ms and 25–150% strength; values persist separately for each route and from
movie profiles. There is no assumed universal Bluetooth delay.

The existing audio-clock scheduler dispatches these pulses off the UI thread.
Haptic analysis observes the original decoded PCM before sound enhancement, so
changing a sound mode does not turn enhanced vocal energy into beat triggers.
Pause, buffering, seek, track/session/route changes, speed changes, disabling the
switch and service release invalidate pending pulses. Explicit player mute and
compatibility playback suspend song haptics. Background audio retains haptics
because ownership belongs to MusicService, not the composable. The switch defaults
off, persists separately from movie/touch haptics and is included in settings reset.

This is frequency/onset filtering, not vocal stem separation. Very low sung notes
can overlap bass, and bass masked by loud vocals may be intentionally missed.
Phone speakers cannot produce physical sub-bass; the harmonic treatment changes
perceived punch. Actual sound and tactile feel need listening and hands-on checks.
Motor amplitude/primitive support varies by device; see the
[Android haptics API guidance](https://developer.android.com/develop/ui/views/haptics/haptics-apis).

## Automated coverage

- Music detector: bass onsets at multiple sample rates and channel counts,
  opposite-phase stereo, vocal-range bursts, voiced harmonics, silence/DC,
  sustained bass, kicks under louder vocals, varying intensity, 125 ms beat runs,
  bass pitches from 45–105 Hz, reset and non-finite input.
- Sound processor: Original byte preservation, distinct speaker-band responses,
  bass harmonics, silence, peak bounds, live switches and return to bypass.
- Sink: original PCM bytes/order preserved, retry deduplication and music events.
- Device probes: real AudioTrack clock and lifecycle cancellation; full player
  haptics visibility, four-mode selection and saved toggle independence.

Build and device logs are under `build/music-*.log` and `build/song-haptics-*.log`.

### Verification on 16 November 2026

- JVM suite: 177 tests, 176 passed, one skipped, no failures.
- Debug, instrumentation and production-signed optimized release APKs built.
- I2012 / Android 14: both music UI tests passed (full-player controls, all four
  choices, persistence and movie preference independence). Real AudioTrack music
  haptics and UI-independent dispatch/release probes passed (two further tests).
- The broad existing music playback UI suite timed out on Compose idling in two
  tests. Its generated MediaStore fixtures were removed. These results do not
  establish a full music-library UI regression pass.
- An existing cinematic texture test missed its narrow audio-deadline assertion
  during a broader run. Its impact dispatch/release probe passed separately;
  the new song-mode clock test passed. Movie texture scheduling was not changed.
- Lint completed with the existing `UnusedBoxWithConstraintsScope` error in
  `MyCinemaDetailModal.kt:2253`, 288 warnings and 12 hints. The combined command
  therefore exits nonzero despite successful APK and unit-test tasks.
- The full-player screenshot was visually checked at
  `build/reports/music-audio/music-player.png`.

Release: `app/build/outputs/apk/release/app-release.apk`.

### Compact dynamic haptics revision

- 183 JVM tests: 182 passed, one skipped, zero failures.
- Both UI tests passed on I2012 / Android 14, including alignment next to the
  Favorite icon, toggle persistence and long-press song tuning.
- The real AudioTrack probe passed with four progressively stronger bass beats.
  Each dispatch was between 55 ms before and 10 ms after the PCM beat onset;
  this verifies dispatch timing, not the physical motor's perceptual onset.
- With actual hardware output enabled, Android recorded all four pulses as
  `finished`, with system-scaled peak amplitudes approximately 0.30, 0.39, 0.52
  and 0.61. This verifies dynamic output reaches the vibrator instead of only
  testing a mock output. System vibration settings still affect final strength.
- Debug, instrumentation and optimized release builds passed. The new screenshot
  is `build/reports/music-haptics-v2/music-player.png`; device logs are in
  `build/music-haptics-v2-*.log`.

Current release SHA-256:
`7E3494BF69CA0BA02318CAD8A663385DCB7E7DCC0C4D7B42A75EB2EC41A4872B`.
