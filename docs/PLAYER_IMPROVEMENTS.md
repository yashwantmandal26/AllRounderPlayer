# Player improvements — 1.7.0

## Controls and feedback

- Video, sharpness, and audio pickers apply presets immediately and stay open for adjustment.
- Each picker has an intensity slider and Reset. Resetting picture or sharpness also disables Smart clarity so the reset is visible.
- Hold **Original** to compare the original picture and sound; release to restore enhancements. The accessibility action toggles comparison. Leaving the activity clears comparison.
- “Super AI” is now **Smart clarity**, describing detail enhancement, shadow lift, and color processing.
- Mode, zoom, speed, and long-press feedback uses text with a text shadow, without background cards or icons.
- Player icon buttons have click semantics, long-press descriptions, and minimum touch targets. The bottom controls and More panel scroll when space is limited.

## Playback and audio

- Playback is paused at Stop rather than Pause, allowing PiP transitions to finish.
- PiP restores the prior subtitle-disabled state rather than always enabling subtitles.
- The current media URL and progress are saved with activity state. Configuration changes retain the shared player, including its speed and selected tracks.
- Reopening a video from the library compares against the actual playing URL, including after playlist navigation, and saves outgoing progress before switching.
- Audio mode, strength, and volume leveling are saved separately for connected headphones and speakers. Virtualization is limited to headphones.
- Volume leveling uses bounded gain changes on decoded PCM audio. Night mode adds linked-channel compression and a peak ceiling. Original mode preserves the PCM samples unchanged.
- Hardware EQ bands follow their actual center frequencies. Presets requiring an unavailable EQ are marked unavailable; Night mode and volume leveling use the decoder's audio processor.

## Seeking and ambient lighting

- Scrubbing shows a small thumbnail and timestamp. Thumbnail extraction uses one decoder, a bounded cache, and the most recent target, with no queued seek requests.
- During horizontal video gestures, moving upward by 80 dp changes the seeking rate to one tenth. Releasing seeks once to the selected position.
- More → **Ambient lighting** controls enablement, intensity, spread, and battery saver.
- Ambient captures stop for a stationary paused frame and refresh after a paused seek. Capture is suspended when the activity is not resumed or is in PiP.
- Published ambient bitmaps are never overwritten or recycled while the renderer might still use them.

## Subtitles and library

- Subtitles → **Timing, position & subtitle search** imports SRT or WebVTT through Android's document picker.
- Imported subtitle timing is saved per video, supports positive and negative offsets, and supports search-to-seek. Search and timing apply to imported text files, not embedded subtitle streams.
- Text size and bottom padding are adjustable. Embedded cues with explicit placement can retain their own position.
- **Use embedded** restores embedded subtitle rendering. Subtitle attachments are remembered per video when document permission persists.
- Removing an item from Continue Watching offers a text-only Undo action.
- Existing library search, favorites, list/grid views, and batch actions are retained.

## Compatibility and verification

- Picture effects require Android 12; sharpness requires Android 13. HDR and compatibility playback preserve the original video output and explain why manual picture effects are unavailable.
- Unit tests cover audio bypass, stereo balance, loud-peak reduction, normalization gain limits, route preference isolation, settings reset, subtitle parsing, overlapping cues, and positive/negative subtitle delay.
- Build/check commands: `:app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:assembleRelease`.
- Device checks still needed: SDR/HDR playback, rotation, PiP entry/return with subtitles on and off, headphone connect/disconnect, scrubbing, original comparison release/cancellation, subtitle import/search, and large system text. No device or emulator was available for these checks during this change.
