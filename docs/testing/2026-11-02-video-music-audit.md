# Video and music device audit — 2 November 2026

Tested current working-tree source, v2.8 / versionCode 40, using the isolated `com.example.ymediaplayer.debug` app on the connected I2012, Android 14. Existing local changes were preserved. No production source fixes, commits, or releases were made.

**Result: seven device-confirmed defects, including one reproducible app crash.** The first six below concern playback/state; the seventh concerns button visibility. Each has a proposed solution, not an implemented fix.

## Findings and solutions

### 1. P1 — “Play Next” on the current song crashes Queue

**Reproduce:** Play a song; pause it; return to the music list; open that song’s three-dot menu; select **Play Next**; reopen the full player and tap **Queue**. This was reproduced through physical ADB touch input with the existing `AllRounder_QA_A` test track.

**Observed:** The debug process crashes with `IllegalArgumentException: Key "queue_1000038578" was already used`. A separate service probe also produced four queue rows with only three unique keys.

**Cause:** [MusicService.kt](../../app/src/main/java/com/example/ymediaplayer/service/MusicService.kt), line 268, preserves the currently playing song and inserts it again. [MusicScreen.kt](../../app/src/main/java/com/example/ymediaplayer/ui/MusicScreen.kt), line 6313, keys queue rows by song ID, which is no longer unique.

**Solution:** Represent each queue occurrence with a stable, unique queue-entry ID and use that ID for row keys, moves, and current-row selection. If duplicate occurrences are intentionally unsupported, explicitly handle enqueuing the current song instead of inserting a duplicate. Merely changing the key to a mutable list index would mask the crash while leaving occurrence selection/reordering ambiguous.

**Evidence:** [actual crash stack](2026-11-02/evidence/duplicate-queue-crash.txt), [current-song menu](2026-11-02/evidence/current-track-menu.png), [Play Next confirmation](2026-11-02/evidence/after-play-next.png).

**Retest:** Current-song Play Next, repeated Play Next, two occurrences of one track, queue scrolling, selecting/moving each occurrence, notification Next.

### 2. P2 — Moving music queue rows does not change playback order

**Reproduce:** Queue A, B, C; play A; move C above B; press Next.

**Observed on device:** The displayed queue changes to A, C, B, but Next plays B. The focused probe expected C and received B.

**Cause:** `MusicService.reorderQueue()` at line 227 changes `currentPlaylist` only. The ExoPlayer timeline remains A, B, C. The real queue arrows call this method at `MusicScreen.kt:6361` and `6378`.

**Solution:** Perform `player.moveMediaItem(fromIndex, toIndex)` and update observable queue/index state together on the main thread. Preserve the current queue occurrence, position, playing state, and shuffle behavior. Prefer one authoritative queue model instead of independently mutable lists.

**Evidence:** `queueReorderingMustChangeTheActualNextTrack` in [initial device probes](2026-11-02/evidence/hidden-probes.txt).

**Retest:** Move before/after the current item; Next/Previous in the app and notification; leave and reopen Music; shuffle enabled.

### 3. P2 — Cancelling or extending a fading music timer leaves volume reduced

**Reproduce:** With sleep-timer fade enabled, reach its final 20 seconds. Cancel it or extend it to 15 minutes.

**Observed on device:** Cancel left player volume around **0.749**, and extension left it around **0.736**, instead of restoring **1.0**. Probes used a 15-second deadline to enter the same real service fade branch immediately.

**Cause:** `MusicService.startSleepTimer()` at line 285 cancels the previous job but restores volume only when a timer naturally expires. Cancelling returns early; extending bypasses the fade branch until the new deadline approaches.

**Solution:** Track the pre-fade volume and restore it whenever an active fade is cancelled or replaced. Reset fade state on stop/destruction. Calculate future fade values relative to the original volume; do not overwrite an intentional user volume change with an unconditional full-volume reset.

**Evidence:** `cancellingFadingSleepTimerMustRestoreVolume` and `extendingFadingSleepTimerMustRestoreVolume` in [device probes](2026-11-02/evidence/hidden-probes.txt).

**Retest:** Cancel, extend, shorten, disable fade, expire normally, and change volume during fade.

### 4. P2 — Repeat Off still loops the music queue

**Reproduce:** Enable Auto Play, disable Repeat and Shuffle, play the last queue item, and let it finish.

**Observed on device:** Playback restarts at A after the final item C. Repeat remains Off. The probe used the real end-of-track transition by seeking to 250 ms before the end.

**Cause:** The `STATE_ENDED` handler in `MusicService.kt:618` invokes `playNext()` when Auto Play is enabled. `playNext()` at line 490 explicitly wraps to index zero when there is no next item.

**Solution:** Separate automatic track completion from an explicit user Next command. With Repeat Off, stop at the end of the queue; wrap automatically only for Repeat All. Let the Media3 timeline perform ordinary transitions, with explicit handling only for policies the engine does not supply.

**Evidence:** `repeatOffMustNotWrapAtEndOfQueue` in [device probes](2026-11-02/evidence/hidden-probes.txt).

**Retest:** One-item/multiple-item queues with Repeat Off, One, All, shuffle, and Auto Play off.

### 5. P2 — Seeking a paused video starts playback

**Reproduce:** Open a video, pause, then tap/drag its seek slider.

**Observed on device:** The seek succeeds, but `playWhenReady` becomes true. The probe exercised the actual slider with a generated 30-second H.264/AAC video.

**Cause:** `safeSeek()` in [VideoPlayerScreen.kt](../../app/src/main/java/com/example/ymediaplayer/ui/VideoPlayerScreen.kt), line 1641, calls `manualPlay()` whenever the player is not playing after a seek. The torrent seek branch resumes unconditionally too, although that branch was not exercised here.

**Solution:** Capture playback intent before seeking and preserve it. Ordinary scrubbing should not call Play. For buffered/torrent seeking, resume only if playback was requested before buffering and the user has not paused or changed tracks while waiting.

**Evidence:** `pausedSeekMustRemainPaused` in [device probes](2026-11-02/evidence/hidden-probes.txt).

**Retest:** Paused/playing slider seeks, double-tap seeks, bookmarks, subtitles’ seek actions, and a delayed torrent seek followed by Pause.

### 6. P2 — Video sleep timer is lost when the Activity is recreated

**Reproduce:** Open video → More → Sleep Timer → 15 Minutes. Confirm `15m` is active. Recreate the Activity and reopen More.

**Observed on device:** The timer returns to the inactive **Sleep Timer** state. The final probe successfully asserted `15m` before recreation, then failed that assertion after recreation. It used semantics actions to select the real UI controls, avoiding earlier touch/visibility synchronization problems.

**Cause:** `sleepTimerMinutes` at `VideoPlayerScreen.kt:1379` is held only in `remember`. Its countdown is a `LaunchedEffect` at line 2599, cancelled when the screen leaves composition. The actual remaining deadline is not retained elsewhere.

**Solution:** Own a monotonic deadline and expiry job in the video playback service/manager. Render remaining time from that owner and restore it when the screen reattaches. A `rememberSaveable` minutes value alone would restart the whole duration instead of preserving elapsed time. Process-death behavior should be specified separately.

**Evidence:** [final isolated timer probe](2026-11-02/evidence/video-timer-final.txt), [before/after semantics](2026-11-02/evidence/video-timer-final-logcat.txt).

**Retest:** Activity recreation, settings navigation, background playback, PiP, timer cancellation and actual expiry. Only Activity recreation was directly reproduced here.

### 7. P2 — Music search Play button becomes visually blank with a white accent

**Reproduce:** Use the White accent in dark mode, open Music, and search for an existing track.

**Observed:** Search Results shows a white rounded Play button with an invisible icon and label. Touching it still starts playback; this is a contrast failure. A matching blank-button symptom was visible on the video-permission screen, but that secondary code path was not audited as part of this finding.

**Cause:** `MusicScreen.kt:1347` sets button background to `c.accentBlue` but hard-codes its foreground to `Color.White`. `theme/Theme.kt:65` already supplies `c.onAccent`, including a dark foreground for White accent in dark mode.

**Solution:** Use `c.onAccent` for text/icons on accent-filled buttons and audit the other music buttons with the same hard-coded foreground. Ensure the Material color scheme’s `onPrimary` agrees where components rely on it.

**Evidence:** [blank Play button](2026-11-02/evidence/manual-search.png), [successful playback after tapping it](2026-11-02/evidence/manual-playing.png).

**Retest:** White, Yellow, and default accents in light and dark mode, including disabled buttons.

## Validation and limitations

- Debug app and instrumentation APK build successfully. Current JVM results contain **99 tests, zero failures/errors**; Gradle reused up-to-date results during this audit.
- All **four video audio-pipeline tests passed on this phone**: [output](2026-11-02/evidence/video-audio-tests.txt). The older ordinary-audio pipeline failure was therefore not carried forward as a current finding.
- The initial focused six-probe run has five valid playback assertion failures exposing four distinct defects, plus one timer UI-navigation failure. The corrected, isolated video-timer probe later reproduced defect 6. The duplicate queue defect was first exposed by a queue invariant probe and then confirmed as an actual crash through manual UI input.
- The existing six-test music integration suite failed during fixture lookup before playback assertions: [output](2026-11-02/evidence/music-baseline-awake.txt). Newly inserted MediaStore fixtures were not returned by the repository on this device. Those six setup failures are **not six app defects** and do not establish a playback regression. Their indexing/deduplication interaction needs separate investigation.
- A later combined probe rerun stalled after test-state interference and was interrupted; its partial output is retained for transparency, not presented as a clean suite result. The report relies on the specific successful reproductions linked above.
- Manual checks also covered Music navigation/search, real UI playback/pause, opening the full player, song menu actions, and queue opening. Video probes rendered and played generated H.264/AAC media. Codec breadth, Bluetooth/headset behavior, calls, long-duration battery use, torrent networking, subtitle-format coverage, and every OEM lifecycle case remain outside this run.
- Production app data and real media files were not reset, renamed, or deleted. The temporary always-awake setting was restored. Generated private probe files were cleaned up. Deliberately failing probes are archived under [probes](2026-11-02/probes/) instead of remaining in active test sources. To replay video probes, copy the generated [fixture](2026-11-02/evidence/QA_Video_30s.mp4) to `app/src/androidTest/assets/QA_Hidden_30s.mp4` temporarily.

Recommended fix order: duplicate-queue crash; queue/engine synchronization; pause-preserving seek; timer ownership and volume restoration; Repeat Off boundary; button contrast. Convert the focused probes into maintained regression tests as each corresponding fix is implemented.
