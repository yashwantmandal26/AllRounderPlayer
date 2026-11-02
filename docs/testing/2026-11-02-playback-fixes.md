# Video and music fixes — 2 November 2026

Implemented all seven findings from the earlier audit and the reported three-finger screenshot freeze. Existing working-tree edits were preserved; nothing was committed or pushed.

## Installed app and device

The user confirmed the regular app was the intended app and removed the separate debug player. Subsequent verification targeted **`com.example.ymediaplayer`**, on the connected **I2012 / Android 14**, using an explicitly selected, production-signed QA variant with the same application ID. This updated the regular app rather than installing a second player.

After tests, the normal, minified, production-signed **release APK** was installed over that same app with `adb install -r`. Version remains **2.8 / versionCode 40**. Installation succeeded, normal startup was visually checked, and `pm list packages ymediaplayer` showed only `com.example.ymediaplayer` after removing instrumentation helpers. App preferences and real media were not cleared. The temporary always-awake setting was restored.

Evidence: [installed package metadata](2026-11-02/fixes/installed-package.txt), [release home](2026-11-02/fixes/release-home.png).

## Changes

| Issue | Implemented fix |
|---|---|
| Three-finger screenshot leaves video controls unresponsive | End ignored gestures when every pointer is released/cancelled. Restart pointer handling on window focus loss/regain. Clean up gesture state and temporary speed/overlays. Avoid treating consumed button input as a background tap. |
| Current song → Play Next → Queue crashes | Keep the existing unique-song queue model: selecting the current song does not insert another occurrence and reports “Already playing.” Queue assignments also suppress duplicate URIs. |
| Queue reorder changes only the UI | Move the corresponding Media3 timeline item and synchronize the current index, preserving track position and pause state. |
| Cancelling/extending a fading music timer leaves quiet playback | Restore the pre-fade volume when replacing/cancelling the timer. Scale the fade relative to that volume and preserve an intervening user volume change. |
| Repeat Off still loops the music queue | Remove the automatic `playNext()` call at `STATE_ENDED`; Media3 owns automatic transitions/repetition. Explicit user Next behavior remains available. |
| Seeking a paused video resumes it | Ordinary seeks preserve playback intent. Torrent seek buffering records whether playback should resume and respects a pause or track change while waiting. |
| Video timer lost on Activity recreation | Store a monotonic deadline and expiry job in `VideoPlaybackManager`, render the live remaining time, preserve it during internal player rebuilding, and cancel it on explicit release. |
| White-accent music buttons have invisible content | Use the theme’s `onAccent` foreground on accent-filled music buttons. |

The screenshot freeze had a concrete control-flow cause: the `IGNORED_DRAG` branch continued before checking for pointer release, leaving the current gesture alive and swallowing future taps.

Also added the missing Media3 unstable-API opt-in to `TorrentStreamDataSource.kt`; all 40 blocking lint errors were instances of that missing annotation.

## Validation

| Check | Result |
|---|---|
| JVM tests, rerun against changed source | **99 passed; zero failures/errors/skips** |
| Regular-app device regression run | **24 passed** |
| Additional original crash-path UI regression | **1 passed** |
| Final music UI run, with the production-style host background and added crash test | **8 passed**, included within the **25 distinct device tests** above |
| Debug lint | **0 errors**, 261 warnings, 10 hints |
| Normal release assembly | **Passed** |
| Normal release update and startup on intended package | **Passed** |

The five video interaction tests cover paused seek, three-finger release, three-finger cancellation followed by working controls/buttons, timer retention after Activity recreation, and timer expiry without restarting its deadline. The eight music service tests cover queue ordering/current-item movement, duplicate prevention, repeat boundaries, auto-advance, and timer-volume restoration. The eight music UI tests cover history, repeat, timer detach/remount, queue preservation, autoplay-off boundaries, timer expiry off-screen, actual button contrast, and the original current-song Play Next/Queue crash path. Four audio-pipeline tests remain passing.

The gesture tests inject three-pointer input through Android and verify the next taps and player buttons. They exercise release and cancellation recovery; they do not separately certify every OEM screenshot overlay or screenshot-saving implementation.

Evidence:

- [24-test regular-app run](2026-11-02/fixes/regular-app-final-regressions.txt)
- [Original crash-path UI test](2026-11-02/fixes/current-song-queue-ui.txt)
- [Final eight music UI tests](2026-11-02/fixes/regular-app-music-ui-final.txt)
- [JVM test summary](2026-11-02/fixes/unit-test-summary.txt)
- [Build, JVM, lint, and release log](2026-11-02/fixes/build-unit-lint-release.txt)
- [Video timer after recreation](2026-11-02/fixes/video-timer-after-fixed.png)
- [Visible White-accent Play button](2026-11-02/fixes/music-white-accent-fixed.png)

Earlier failed/interrupted runs remain in the evidence folder for transparency. The final runs above supersede them. Music fixture setup now gives generated MP3s unique embedded titles so media scanning cannot reintroduce old-title deduplication collisions, compares equivalent volume URIs by media ID, and restores preferences after tests. The timer UI regression checks whether the options menu is already open instead of inadvertently closing it while testing timer retention.

## Reproduce device verification

Default debug builds still use their original suffix. To test the regular app package deliberately:

```powershell
.\gradlew.bat -PqaTestBuildType=qa :app:assembleQa :app:assembleQaAndroidTest
adb install -r app/build/outputs/apk/qa/app-qa.apk
adb install -r app/build/outputs/apk/androidTest/qa/app-qa-androidTest.apk
adb shell am instrument -w -e allowReleaseQa true -e class com.example.ymediaplayer.ui.VideoInteractionRegressionTest,com.example.ymediaplayer.ui.MusicQueueAndTimerRegressionTest,com.example.ymediaplayer.ui.MusicPlaybackIntegrationTest,com.example.ymediaplayer.player.VideoAudioPipelineTest com.example.ymediaplayer.test/androidx.test.runner.AndroidJUnitRunner
```

The maintained suite now contains 25 device tests across those four classes. Tests against the regular application require the explicit `allowReleaseQa` argument. Finish with the normal release APK rather than leaving the debuggable QA variant installed.

Release artifact: `app/build/outputs/apk/release/app-release.apk`  
SHA-256: `0DD779FD48C0917FD0AB7E8A53A4B2ABB0530FB2DDA33AB3177E80127DA5A3A3`

Video sleep timers survive screen/Activity recreation while the playback process lives. Process-death persistence and broad codec/Bluetooth/OEM coverage remain separate work; no claim of universal device coverage is made.
