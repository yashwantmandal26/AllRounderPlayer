# Dynamic audio haptics — 14 November 2026

The previous implementation changed the strength of short impacts. The user reported
that everything still felt like “thak-thak” and requested varied textures, dynamic
intensity, and precise synchronization.

## Resulting behavior

- Bass impacts have a shaped attack/body/release. Broadband attacks can be crisp
  clicks or irregular grain. Sustained bass produces rumble, rising energy produces
  swells, and changing midrange energy can produce grainy textures.
- Both the envelope samples and impact strength derive continuously from decoded
  PCM energy. The category names are diagnostic labels, not fixed output strengths.
- Analysis rejects inaudible DC bias, preserves channel independence, and leaves
  all audio bytes, video processing, and audio/video timing unchanged.
- All queues are bounded. Known adjacent texture packets are combined into native
  waveforms up to 240 ms to reduce repeated motor restarts; no extra audio buffering
  or fixed Bluetooth delay is introduced. Upcoming impacts retain their deadlines.
- Playback speed retimes the envelope. Late texture samples are trimmed, while
  stale/too-late impacts are discarded. Pause, seek, buffering, disable, route changes,
  release, and generation changes retain cancellation behavior.
- Long-press **Haptics** for overall strength (25–150%) and synchronization offset
  (−250 to +250 ms). Negative means earlier vibration; positive means later. Settings
  are saved independently for speakers, wired headphones, and Bluetooth. Very early
  offsets are constrained by how much audio has already been decoded.
- On Android 13+, route selection queries the output policy for media attributes
  instead of assuming every connected headset is the active output. Older Android
  versions use connected-device information. Routing checks stay off the UI thread.

## Hardware and precision

The connected I2012 / Android 14 reports amplitude control, no composition primitives,
and no usable frequency profile. The implementation therefore uses amplitude-shaped
waveforms on this phone. It cannot recreate every physical vibration frequency.
Basic devices without amplitude control keep short effects and omit sustained textures.

Native dumps confirm changing-amplitude, multi-segment media waveforms. Short-packet
traces also showed extra leading zero segments added outside the requested waveform;
this motivated batching. Batching reduces restarts but cannot guarantee zero driver
latency or perfect sensory synchronization. The sync control compensates a consistent
per-route timing bias; human feedback with the actual Bluetooth earphones is still needed.

Android references: [amplitude/envelope capabilities](https://developer.android.com/develop/ui/views/haptics/haptics-apis),
[custom effects](https://developer.android.com/develop/ui/views/haptics/custom-haptic-effects),
and [media routing policy](https://developer.android.com/reference/android/media/AudioManager#getAudioDevicesForAttributes(android.media.AudioAttributes)).

## Verification

- Debug/release builds and 161 unit tests passed. Coverage includes distinct wave
  shapes, continuously varying strength, sustained bass and silence release, DC
  rejection, retiming, late trimming, timestamp ordering, texture batching, impact
  interruption, exposure accounting, buffer preservation, and generation invalidation.
- The phone UI suite passed all seven checks, including long-press tuning, strength
  and offset persistence, reset, and existing seek/gesture/timer behavior. Four native
  audio-pipeline checks also passed. The tuning dialog screenshot was inspected.
- Before batching, the same F1 section was measured for seven seconds per configuration:
  168 video frames rendered, zero video drops, and zero audio underruns with haptics
  both disabled and enabled. UI CPU was 3340 / 3627 ms and window frame p95 was
  18.56 / 23.74 ms (off/on), so this sample does not imply zero UI overhead or jank.
- Final batching clock/performance checks and release installation are recorded in
  `app/build/reports/dynamic-haptics/`. Performance probes wake the phone and hold the
  screen awake, preserving/restoring the user's settings afterward.
- Full debug lint reports one existing error in unchanged `MyCinemaDetailModal.kt:2253`
  (`UnusedBoxWithConstraintsScope`), with no errors in the changed haptics/tuning files.

Final batching verification passed all three clock/lifecycle checks and the F1
performance probe. Together with the earlier unchanged UI/audio checks, 15 distinct
phone checks passed across these final runs. The batched-packet test verifies an
earlier/later offset, continuous amplitude changes within one native submission,
and the original audio end deadline while allowing up to 25 ms software dispatch
lateness. This bound is a test condition, not a physical motor/Bluetooth guarantee.

The final seven-second F1 samples rendered 168 video frames each, with zero video
drops and zero audio underruns. Window-frame p50/p95 were 13.68/16.72 ms with haptics
off and 14.48/21.14 ms with haptics on; UI CPU was 3075/3440 ms. Device temperature
and scheduling vary, so no improvement percentage is inferred from the earlier run.

The final production-signed, non-debuggable release installed successfully over
`com.example.ymediaplayer` (2.8.3 / 43), and a cold launch succeeded. User data was
preserved. Artifact: `app/build/reports/dynamic-haptics/app-release-dynamic-haptics.apk`.
SHA-256: `EA70828D6033CAC431D4E0DE02D897602CC7082367791ED9B14DDF2357212C0F`.

The optional local reference probe uses a bounded 150 ms decoder lead and outputs
`events.csv` and `played.csv` beside the generated WAV. It is deterministic mechanical
validation, not a measure of semantic recognition or perceived tactile quality.
No copyrighted video/audio is committed to the repository.
