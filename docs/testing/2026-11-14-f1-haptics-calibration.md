# F1 trailer haptics calibration — 14 November 2026

Reference: the user's local F1 exclusive final trailer, 130.66 seconds, H.264 video
at 1920×804 and stereo AAC at 44.1 kHz. Analysis decodes to stereo PCM16/48 kHz.
The source video and decoded audio are not checked into the repository.

The user's primary listening route is Bluetooth earphones; speakers are also used.
No guessed Bluetooth delay was added. Perceived timing still requires listening
with the actual earphones while holding the phone.

## Finding and change

The original detector emitted 129 candidate events: 106 identical THUMP effects,
21 ticks, and 2 taps. Its heavy-bass threshold was 0.65 in mean-square window
power, while the largest detected bass window in the reference was only 0.041.
This left most bass impacts at the same tactile strength.

Bass intensity now uses five-millisecond window RMS power bands:

- Below 0.008 power (approximately 0.089 RMS): soft thump, scale 0.35, 24 ms budget.
- From 0.008 to below 0.025: medium thump, scale 0.65, 32 ms budget.
- At least 0.025 (approximately 0.158 RMS): heavy thump, scale 0.9, 40 ms budget.

The same reference produces 39 soft, 58 medium, and 9 heavy bass thumps, plus the
same 21 ticks and 2 taps. All 129 fit the existing timeline limits in the offline
probe. Total nominal effect duration falls from 3684 to 3444 ms across the clip.
These counts demonstrate varied intensity, not semantic recognition accuracy or
human-perceived quality. Events are inferred from audio, not authored at F1-specific
timestamps. Detection frequency, onset timing, PCM audio bytes, cooldown, and rolling
output limits are unchanged by this calibration.

## Reproduction

Decode a local reference to PCM16 stereo/48 kHz WAV. Set `HAPTIC_CALIBRATION_WAV`
to its absolute path, then run `:app:testDebugUnitTest --tests
com.example.ymediaplayer.player.HapticCalibrationTest`. The optional probe writes
`events.csv` next to the WAV and skips when the environment variable is unset.
Keep the WAV in a generated output directory, not beside user originals.

Generated analysis, baseline and calibrated event CSVs, waveform, and build logs
are under `app/build/reports/f1-haptics/`.

The standalone JVM probe plus detector suite passed 13 tests, including explicit
soft/medium/heavy response and multi-rate/multichannel bass inputs. Generalization
coverage also includes silence, steady tones/noise, opposite-phase stereo, sharp
transients, quiet bass, bass mixed with midrange audio, buffer boundaries, cooldown,
and invalid PCM samples.

Android's [haptics design principles](https://developer.android.com/develop/ui/views/haptics/haptics-principles)
inform the preference for distinct short effects and supported native primitives.
Hands-on validation is needed for sensory quality and earphone synchronization.

## Delivery verification

- Final isolated build: `testDebugUnitTest` and `assembleRelease` passed; 151 tests,
  zero failures/errors. Isolation avoided shared-output collisions with another
  build in this workspace. The final production-source snapshot matched the
  workspace, including the concurrent FileProvider authority correction.
- Phone: 12 instrumented checks passed together: two haptics clock/lifecycle checks,
  four audio-pipeline checks, and six playback UI/gesture/timer checks.
- Production-signed, non-debuggable release installed successfully on I2012 and
  cold-launched successfully. Package/version remain `com.example.ymediaplayer`,
  2.8.3 / 43; no app-data reset was performed.
- APK: `app/build/reports/f1-haptics/app-release-f1-calibrated.apk`.
  SHA-256: `1572FE1ED833F16F282D120CD38DCCD89FB8B71416B27CB39B7C3B99849723C5`.
- Trailer copied to `/sdcard/Movies/F1_Haptics_Calibration_20261114.mp4` and submitted
  for media scanning. Stronger-event test points include 00:30.91, 00:34.78, and
  00:42.755. These are observed detector outputs, not authored triggers.
- Bluetooth was not connected during the route inspection, so no hands-on
  earphone-latency or tactile-quality validation is claimed.
