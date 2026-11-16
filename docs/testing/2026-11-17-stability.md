# Functionality and stability review — 17 November 2026

Build: 2.8.3 (43), Android API 24+, arm64-v8a. Existing workspace changes were preserved.

## Changes in this review

- Music retains the latest playback request while its service starts, including after the mini-player is dismissed. Artwork loading belongs to the service lifecycle and checks the active player and media URI before updating a queue entry. Stopping playback clears its sleep timer; cancelling a timer restores the VLC volume factor. Compatibility-engine Next/Previous now prepare the selected track in the main engine. UI pause/toggle callbacks no longer call back into the same service action, and fallback automatic advance respects the end of a Repeat Off queue.
- Small music visualizers sample their spectrum in the frame callback instead of subscribing library rows to every audio-analysis update. Playback integration tests observe the player on its application thread while audio is animated. Their cleanup also runs independently of Compose idleness.
- Video navigation uses cached dimensions. Uncached metadata is read on the existing background metadata path, with guaranteed retriever cleanup and decoded file-URI paths. Notification artwork jobs are cancelled with the video service. Forward seeking no longer jumps to zero when duration is unknown.
- Rename and information actions preserve the supplied content URI, including audio and opaque document URIs, instead of converting its final segment into an ID in the video collection. Duplicate delete targets are handled once. Renaming a missing file reports failure, and existing destinations remain protected. App-owned media and items with existing write access are trashed directly through the provider; items requiring permission still use system consent. This fixes app-owned trash completion on the connected phone. See [Android's shared-media access guidance](https://developer.android.com/training/data-storage/shared/media).
- Torrent state updates use atomic flow updates. Native progress polling cannot overwrite newer pause/completion updates or reinsert cancelled entries. Download-path containment requires an actual directory boundary.
- Torrent data-source routing checks the exact selected local movie path. External subtitles, network requests and unrelated local files cannot use the movie's piece offsets or file preallocation. Reads respect the physical file end, propagate interruption, and report unexpected disk EOF as an I/O failure. Transfer listeners also receive torrent transfers.
- Sequential downloads remain eligible for progress notifications. Pausing the last download releases its foreground-service budget and wake lock while leaving a Resume notification. Both the notification and downloads UI restart the service when resuming. Empty/finished queues stop the service. Android foreground-service timeouts pause transfers and stop the service, following [Android's timeout contract](https://developer.android.com/develop/background-work/services/fgs/timeout).
- Network-state permission is declared. Wi-Fi-only settings block starting/resuming normal downloads without a suitable network; the service also pauses active downloads when that network is lost. The app reports a failed service start without crashing. Stream playback retains its separate network policy. Resuming after a network-policy pause is manual.
- Fixed the unused Compose constraints container that blocked lint, the minimum-integer album-art hash crash, and an Android 8 API guard.
- Deployment uses the freshly built release APK, discovers ADB from PATH and preserves the installed app and data on failure. It no longer retries by uninstalling or forces a downgrade.

## Validation

The expanded JVM suite has 202 tests: 201 passed and one existing, optional local-PCM calibration test skipped. This includes sixteen new regression tests covering file targets and rename safety, concurrent torrent updates, directory containment, download state selection, data-source routing/ranges/EOF, and the minimum-integer artwork seed.

Five device regressions were added for playback after service dismissal, immediate timer cancellation, compatibility-engine Next/Previous, and the compatibility pause callback. A phone connected during the review: **I2012, Android 14 / API 34**.

**59 distinct device regressions passed across the broad suite and focused reruns.** Coverage includes actual generated MP3/video playback, VLC transitions, queue ordering/repeat/autoplay, sleep timers and screen remounts, video seeking/gestures, file rename/share/trash and cache refresh, consent callback round trips, footer touch isolation, library materials, PCM audio pipelines, update errors, version/history dialogs and large-text layouts.

The first broad device run exposed music animation/idleness problems, app-owned trash completion, and stale update-test assumptions about the installed version and stacked header layout. These were fixed and rerun. A subsequent queue test required the same asynchronous polling correction, including reliable teardown. The final nine music integration tests all passed. Temporary generated media and its remaining test-only history references were cleaned up, and the phone's original stay-awake setting was restored. Real user media was not modified.

Validation logs:

- `build/stability-device-tests.log`: first broad run, including the original failures.
- `build/stability-device-rerun.log`: 37 affected checks; one queue case had a test/teardown idleness failure, with the remaining cases passing.
- `build/stability-music-final.log`: all nine final music UI/service integration cases passed.
- `build/stability-release-check.log`: JVM tests, lint and optimized release assembly passed.
- `build/stability-final-lint.log`: final test-source lint passed; zero errors, 293 warnings and 12 hints remain.
- `build/stability-signature-verification.log`: APK signature verification passed.

The signed optimized **2.8.3 (43)** APK is `app-stable-v2.8.3.apk` at the project root. It was installed over the existing regular app and cold-launched successfully; the home screen was visually inspected. The observed cold launch took 900 ms on this phone; this is a single smoke check, not a performance benchmark. Screenshot: `build/stability-release-home.png`.

SHA-256: `37D8E26860848FA017A671E89395B3F563D69AF6E1BD5197DA759E3AB2A9E37D`.

Reproduction command with JDK 21 running Gradle and the existing JDK 17 Kotlin toolchain:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest :app:assembleRelease --console=plain
```

## Release limits

This is a stabilization candidate tested on one phone, not a claim that every feature has been certified across devices. Further verification remains necessary for the broader codec matrix, audio focus/headset changes, PiP/background transitions, unowned-media system consent and older Android storage behavior, Android 14 selected-media access, haptics, live torrent peers/network changes, Android 15+ service timeout callbacks, and actual update installation. Most automated device checks used the debug variant; the optimized release received a launch/UI smoke check. Pending torrent state is still process-local; process-death download restoration is outside this revision.
