# AllRounder Player — Full app QA report

**Date:** 14 October 2026  
**Build:** v1.7.1, versionCode 16  
**Source:** `main`, commit `4a65cdd9e617c1a04831e46c995981852db102e7`, plus the existing local update/header UI changes.  
**Device:** Samsung SM-S921E, Android 17 / API 37, 1080 × 2340, density 480.

**Result: release hold recommended.** Ordinary video playback with audio failed on the phone. The existing automated suite passes, but it misses several integration failures. This audit found **17 actionable findings: 8 reproduced or confirmed by focused probes, and 9 additional source-confirmed findings awaiting their platform/flow-specific runtime tests**. One further memory risk is listed separately.

This is a broad audit of the current build on one phone, not a guarantee that every device, codec, or interaction is bug-free. No production code was fixed, committed, or pushed during this audit. The prior local UI changes were preserved. Device tests used the separate `.debug` application and generated QA media; production app preferences and real media were not reset, renamed, or deleted.

## Validation results

| Check | Result | Meaning |
|---|---|---|
| Existing JVM tests | **64 passed** | Current baseline tests pass |
| Existing Android instrumentation tests | **6 passed** | Permission UI and update/header UI tests pass on the connected phone |
| Debug lint | **0 errors, 159 warnings, 10 hints** | No blocking lint errors; warnings still need triage |
| Release assembly | **Passed** | Release build compiles; this does not prove runtime correctness |
| New focused QA probes | **13 run: 2 passed, 11 failed** | Failures deliberately assert expected behavior and expose 7 distinct bugs |
| Manual UI inspection | **Additional issue reproduced** | Corrupted separator text in the player mode dialog |

The two passing new controls process twelve nonempty PCM chunks in Normal and volume-leveling modes. The failing audio probes exercise the actual Media3 pipeline startup, which the controls and older tests do not cover. Temporary probes were archived under [probes](2026-10-14/probes/) and removed from active app test sources so intentionally failing tests do not remain in the normal suite.

## Coverage

| Area | Executed / reviewed | Result / limit |
|---|---|---|
| Startup and library | Launch, full media permissions, folder discovery, recent media scanning | Generated videos and songs appeared in the library |
| Update UI | Existing layout/large-text/short-window/progress tests; real Settings composition with an injected test release | Component tests pass; Settings dismissal fails |
| Release-note formatting | Parser/unit and UI tests, bold/italic/list rendering paths | Existing checks pass |
| Video with audio | Fresh launch of valid H.264 Baseline, YUV420P, AAC-LC MP4 | **Fails in custom audio processing**, retries and falls back |
| Video without audio | Same video stream with audio removed | Playback advances; pause and seek work, seek reached 00:17 / 00:30 |
| Player modes | Open preset dialog, choose Vivid, reopen player | Selection persists; separator text is visibly corrupted. No image-quality measurement claimed |
| Ambient light | Visible on silent video | Glow renders; frame-by-frame latency and battery performance were not measured |
| PiP / tasks | Explicit PiP entry; ActivityManager task inspection | PiP entered `mode=pinned`; Main and video shared a task before PiP. Earlier duplicate-recents symptom not reproduced in this run |
| Music | Actual generated MP3 UI playback, search, Recent, Repeat One, timer detach/remount | Playback/search work; three state/integration bugs reproduced |
| Preferences | Reset and global-vs-route audio probes | Five failing assertions across two defects |
| File operations | Source audit of trash/rename/permission callbacks | Older-platform problems found; no destructive operation performed on real files |
| Subtitles / queue / resume | Connected source-path review | Findings below; full runtime regression still required |
| Updater integrity | URL/redirect, size, package identity and signature checks reviewed | Protections exist; no actual APK installation or downgrade attempted |

## Reproduced findings

### QA-01 — P1: Video audio processor breaks ordinary playback

**Verification:** Phone playback failure plus two real Media3 pipeline probe failures.

Open the generated `QA_Landscape_60s.mp4` from a fresh debug process. The player throws `IllegalArgumentException: The source buffer is this buffer`, reports `ERROR_CODE_FAILED_RUNTIME_CHECK`, retries, and invokes compatibility playback. A black/fallback screen appears instead of normal playback. Removing only the audio stream makes the same video play.

**Cause:** On its first output poll, Media3 queues `AudioProcessor.EMPTY_BUFFER`. `replaceOutputBuffer(0)` returns that same shared buffer; copying it into itself throws. This occurs in both Normal and leveling paths at [VideoDynamicsProcessor.kt](../../app/src/main/java/com/example/ymediaplayer/player/VideoDynamicsProcessor.kt), lines **67 and 82**. This is an app audio-pipeline bug, not evidence that the valid fixture is corrupt. The fallback message misleadingly describes the file as unrecoverable.

**Impact:** Core video playback with decoded PCM16 audio can fail even with the default Normal sound mode. **Fix first**, then rerun ordinary audio/video playback and enhancement tests.

Evidence: [phone playback error](2026-10-14/evidence/phone-aac-playback-error.txt), [pipeline test output](2026-10-14/evidence/audio-music-probes.log), [root cause](2026-10-14/appendices/audio-pipeline-root-cause.md), [failure screenshot](2026-10-14/evidence/qa-video-fresh.png), [silent-video control](2026-10-14/evidence/qa-silent-video.png).

### QA-02 — P1: Settings update popup remains after “Not now”

**Verification:** Android integration test uses the real Settings screen and a synthetic available release; no download is initiated. Tap **Not now**. The update dialog is still mounted and visible; `assertDoesNotExist` fails.

[SettingsScreen.kt](../../app/src/main/java/com/example/ymediaplayer/ui/SettingsScreen.kt), **2354–2359**, mounts the dialog whenever `availableUpdate` is non-null. [UpdateManager.kt](../../app/src/main/java/com/example/ymediaplayer/update/UpdateManager.kt), **342–351**, dismisses only its visibility flag and leaves that release value intact. MainActivity has a second dialog host, so duplicate mounting is also possible; that secondary symptom is source-based, not separately reproduced.

**Impact:** Users cannot reliably dismiss the update interruption from Settings. Gate rendering on a single visibility state and use one dialog owner.

Evidence: [test failure](2026-10-14/evidence/settings-integration-probe.log), [popup after dismissal](2026-10-14/evidence/qa-settings-dismiss-bug.png).

### QA-05 — P2: “Reset All Settings” does not restore several defaults

**Verification:** Four independent JVM probes failed. After setting nondefault values and invoking reset, **HDR stays off, subtitle background stays off, play-during-calls stays on, and artwork stays CARD instead of VINYL**.

[DataRepository.kt](../../app/src/main/java/com/example/ymediaplayer/data/DataRepository.kt), **687–743**, omits these keys. Settings additionally changes its local artwork display to VINYL without persisting that reset. Other omitted setting groups are documented in the appendix; only the four named values were directly probed here.

**Impact:** Reset results are incomplete and can disagree with the immediate UI. Evidence: [five preference probe failures](2026-10-14/evidence/regression-probes-build.log), [JUnit XML](2026-10-14/evidence/TEST-com.example.ymediaplayer.data.FullQaRegressionProbeTest.xml).

### QA-06 — P2: Settings audio preset does not reach an existing output profile

**Verification:** A preference-path probe saves speakers=CINEMA, then performs the same global setter as Settings to select CLEAR_VOICE. The playback getter still returns CINEMA.

[SettingsScreen.kt](../../app/src/main/java/com/example/ymediaplayer/ui/SettingsScreen.kt), **1136–1139**, writes `video_sound_mode`; the player reads the route override `video_sound_<route>` through [DataRepository.kt](../../app/src/main/java/com/example/ymediaplayer/data/DataRepository.kt), **319–325**.

**Impact:** Settings appears to apply a sound choice while playback retains a previous one. Either apply the selection to the intended route or clearly expose the default/per-route distinction. This probe verifies preference integration, not a listening comparison. Evidence: [probe failure](2026-10-14/evidence/regression-probes-build.log).

### QA-07 — P2: Playing music through the UI does not populate Recent

**Verification:** Search for generated `AllRounder_QA_A`, play it through the real music UI, verify service playback, then inspect Recent. Stored recent history remains `[]`.

[MusicScreen.kt](../../app/src/main/java/com/example/ymediaplayer/ui/MusicScreen.kt), **415–451**, increments play count but bypasses `recordMusicPlayed()`. Recent reads a separate history list at **680–687**.

**Impact:** Recent stays empty or stale despite successful playback. Record history centrally on successful playback transitions. Evidence: [test output](2026-10-14/evidence/audio-music-probes.log), [Recent screenshot](2026-10-14/evidence/fullqa-music-recent.png).

### QA-08 — P2: Repeat One is displayed but the playback service remains Repeat Off

**Verification:** After actual UI playback, tap Repeat twice to select One. Service player `repeatMode` is **0 / OFF**, expected **1 / ONE**.

[MusicScreen.kt](../../app/src/main/java/com/example/ymediaplayer/ui/MusicScreen.kt), **266–268 and 2345–2355**, updates local UI state; the service never receives the selected repeat mode. Shuffle has the same local-state ownership problem by source inspection. Its background random ordering was not separately measured.

**Impact:** Repeat/shuffle behavior can change when leaving Music; the service can advance sequentially. This test proves engine-state mismatch, not a measured full-track completion sequence. Evidence: [test output](2026-10-14/evidence/audio-music-probes.log), [Repeat One screenshot](2026-10-14/evidence/fullqa-music-repeat-one.png).

### QA-09 — P2: Music sleep timer disappears when the screen is recreated

**Verification:** Set a 15-minute timer, confirm its badge, detach/remount the real Music screen as tab navigation does, reopen the player. The timer badge disappears and the remembered countdown is lost.

[MusicScreen.kt](../../app/src/main/java/com/example/ymediaplayer/ui/MusicScreen.kt), **323–324 and 404–412**, keeps the countdown in local composition state. [FolderListScreen.kt](../../app/src/main/java/com/example/ymediaplayer/ui/FolderListScreen.kt), **628**, removes that composition when leaving Music.

**Impact:** Music may continue past the intended stop time. Own the timer in the playback service using a deadline. The probe verifies lost timer state; it does not wait fifteen minutes. Evidence: [before](2026-10-14/evidence/fullqa-music-timer-before-detach.png), [after](2026-10-14/evidence/fullqa-music-timer-after-remount.png), [test output](2026-10-14/evidence/audio-music-probes.log).

### QA-17 — P3: Corrupted text in player mode dialogs

**Verification:** Visible on the phone and present in the UI hierarchy: `Choose a preset Â· applied immediately`.

[PlayerModeControls.kt](../../app/src/main/java/com/example/ymediaplayer/ui/PlayerModeControls.kt), **79**, contains the extra `Â` in its literal. Use the correct middle dot / proper UTF-8 text. Evidence: [screenshot](2026-10-14/evidence/qa-mode.png), [hierarchy](2026-10-14/evidence/qa-mode-menu.xml).

## Additional source-confirmed findings

These have connected code-path evidence and concrete reproduction steps. They are **not claimed as device-reproduced**. Relevant older Android versions, installation permission flows, and multi-track/subtitle sequences need follow-up runtime coverage.

| ID / priority | Finding and trigger | Source / consequence |
|---|---|---|
| **QA-03 / P1** | **“Move to Bin” permanently deletes on Android 7–10.** Confirm trash on a disposable file on API 24–29; Android 10 requires existing write authorization. | [FileManager.kt](../../app/src/main/java/com/example/ymediaplayer/data/FileManager.kt):40–48 uses `delete()` below API 30, while library UI promises 30-day restoration. Potential irreversible data loss. Never test on valuable media. |
| **QA-04 / P1** | **Selected-video-only permission is not recognized.** On Android 14+, retain selected-video access while full video and audio permissions are denied; revisit after compatibility session access expires. | [MainActivity.kt](../../app/src/main/java/com/example/ymediaplayer/MainActivity.kt):79–103,181 checks only full video/audio, so permitted selected media can be blocked. With audio granted, the main app can still open. |
| **QA-10 / P2** | **Playlist playback escapes into the full library.** Play a playlist containing two nonadjacent library tracks, then Next / inspect Queue. | [MusicScreen.kt](../../app/src/main/java/com/example/ymediaplayer/ui/MusicScreen.kt):459–484,617–618,2278–2280,2424–2429 uses `allSongs` instead of a selected playlist queue. Unselected songs can play. |
| **QA-11 / P2** | **Update installation does not resume after permission approval.** Download with Install unknown apps disabled, enable permission, return. | [UpdateManager.kt](../../app/src/main/java/com/example/ymediaplayer/update/UpdateManager.kt):206–208,288,309–316 has no pending-install continuation; another button press deletes/re-downloads the ready APK. |
| **QA-12 / P2** | **Manual update-check failures can be invisible.** Fresh session, no known release, offline / failed GitHub request, tap Check for Updates. | [SettingsScreen.kt](../../app/src/main/java/com/example/ymediaplayer/ui/SettingsScreen.kt):482–486,575–579,1827–1831 does not display failure; the only error reader is the dialog, which is absent without a release. |
| **QA-13 / P2** | **Rename/delete does not retry after recoverable storage approval.** On a platform/file requiring consent, approve the requested rename; Android 10 deletion shares the problem. | [FileManager.kt](../../app/src/main/java/com/example/ymediaplayer/data/FileManager.kt):66–82 returns an IntentSender; result callbacks in FolderDetailScreen:110–119 and FolderListScreen:180–189 refresh but retain/retry no pending operation. |
| **QA-14 / P2** | **Embedded captions can remain disabled after imported captions.** Subtitles Off → import SRT/VTT → explicitly select embedded track. | [VideoSubtitleTools.kt](../../app/src/main/java/com/example/ymediaplayer/ui/VideoSubtitleTools.kt):83–89 restores captured disabled=true after the selection at VideoPlayerScreen:6372–6376 enables text. Captions stay hidden. |
| **QA-15 / P2** | **A–B loop carries into another video.** Set 5–10-second loop, then switch using in-player Next / Previous / playlist. | [VideoPlayerScreen.kt](../../app/src/main/java/com/example/ymediaplayer/ui/VideoPlayerScreen.kt):879–880,1354,1414,2043–2046 keeps unkeyed boundaries and applies them to the new media. |
| **QA-16 / P2** | **“Start from beginning” is ignored by Next/Previous/autoplay.** Save >3 seconds progress in B, select START, open A then navigate to B. | [VideoPlayerScreen.kt](../../app/src/main/java/com/example/ymediaplayer/ui/VideoPlayerScreen.kt):1358–1373,1418–1433 restores progress unconditionally in these paths despite respecting START on direct opening. |

Android's selected-access behavior and compatibility session expiry are described in the [official Android documentation](https://developer.android.com/about/versions/14/changes/partial-photo-video-access). The permission branch was reviewed and a permission-state simulation attempted; native selected-item access and expiry were not fully reproduced, so QA-04 remains source-confirmed only.

Detailed reproduction steps and source reasoning: [updates/settings appendix](2026-10-14/appendices/static-findings-update.md), [library/music appendix](2026-10-14/appendices/static-findings-library.md), [playback/subtitles appendix](2026-10-14/appendices/static-findings-playback.md). These appendices were drafted before the focused probes; the verification statuses in this main report take precedence.

## Separate risk and remaining tests

**Unreproduced memory risk:** [VideoSeekPreview.kt](../../app/src/main/java/com/example/ymediaplayer/ui/VideoSeekPreview.kt), **35–51**, caches twelve frames by entry count. API 24–26 retrieves full-resolution frames rather than scaled previews. Twelve decoded 3840 × 2160 ARGB frames alone are about **380 MiB**, before player and UI allocations. No out-of-memory crash was reproduced; test this on a low-memory older device with a 4K fixture before treating it as a confirmed crash.

Still required for broader confidence: older Android storage/permissions matrix, a native selected-video-only flow, actual updater download/install/resume, Bluetooth/headphone switching, incoming calls/audio focus, long-session battery/frame timing, HDR/HEVC/AV1/Dolby and network/corrupt media, subtitle import/export end to end, accessibility/TalkBack, tablet/foldable layouts, and playlist/background completion sequences. The video audio failure limits meaningful end-to-end validation of several player features until QA-01 is fixed.

**Suggested fix order:** QA-01 audio startup → QA-02 dialog ownership/dismissal → QA-03 destructive trash semantics and QA-04 permissions → music timer/repeat/queue/history → reset/audio settings consistency → subtitle/resume/loop and updater recovery → text polish. Rerun the archived regression probes and the normal suite after each relevant fix.

## Evidence and workspace state

- [Initial baseline run](2026-10-14/evidence/full-app-qa-baseline.log) and [final baseline run](2026-10-14/evidence/final-baseline.log).
- [Fresh execution of all 64 JVM tests](2026-10-14/evidence/fresh-unit-baseline.log), [six passing device tests](2026-10-14/evidence/baseline-device-results.xml), and [machine-readable QA counts](2026-10-14/evidence/qa-metrics.json).
- Archived probes contain exact assertions for reproducing the newly discovered failures. They are not active app sources.
- Generated test files and the temporary debug app were removed from the phone after testing. Real media and the production installation were preserved.
- This report and its evidence are the only lasting additions from this audit; no production behavior was changed.
