# Static audit appendix: updates, permissions, and settings

Scope: Android application source for version 1.7.1 (`versionCode = 16`). This appendix records source-based evidence only. No finding below is claimed as a device or instrumentation reproduction. Regression-probe and runtime results belong in the main QA report.

## U1 — P1: Update dialog cannot be dismissed from Settings

- **Source:** `app/src/main/java/com/example/ymediaplayer/ui/SettingsScreen.kt:2354-2359`; `app/src/main/java/com/example/ymediaplayer/update/UpdateManager.kt:342-351`; `app/src/main/java/com/example/ymediaplayer/MainActivity.kt:402-408`.
- **Steps to reproduce:** Make a newer release available, then enter Settings. Tap Close, Not now, or Skip version. Also open the global update notice while Settings is the current screen.
- **Expected:** The selected dismissal removes the update modal; Skip suppresses that version's automatic prompt. A single dialog handles the update.
- **Actual code path:** Settings mounts the dialog whenever `availableUpdate != null`, without checking `showUpdateDialog`. Dismissal clears `showUpdateDialog` but retains `availableUpdate`, so the Settings condition stays true. MainActivity separately mounts a dialog when both values indicate an open update, permitting two simultaneous dialogs.
- **Impact:** A known update can trap users behind the Settings modal. Closing or skipping does not dismiss that modal. Duplicate windows can confuse download ownership and back navigation.
- **Confidence:** High. Direct contradiction between the rendering predicate and dismissal state mutation. Existing UpdateUiTest covers isolated content callbacks, not Settings integration.

## U2 — P1: Android 14+ selected-video access is treated as denied

- **Source:** `app/src/main/java/com/example/ymediaplayer/MainActivity.kt:79-85`, `:96-103`, `:181-196`; `app/src/main/AndroidManifest.xml:11`.
- **Steps to reproduce:** On Android 14 or later, grant access only to selected videos and deny audio access. After the compatibility session grant expires (background/relaunch), leave `READ_MEDIA_VISUAL_USER_SELECTED` granted while `READ_MEDIA_VIDEO` and `READ_MEDIA_AUDIO` are denied. Launch the app.
- **Expected:** The app opens and lists the selected videos, with an affordance to manage the selection.
- **Actual code path:** The manifest declares selected-video permission, but the runtime request omits it and `updatePermissionState()` recognizes only full video permission. The main app gate checks only full-video or audio permission; selected-only access falls into PermissionFallbackScreen.
- **Impact:** Users who intentionally grant partial video access can be blocked despite having usable video access. If audio permission is granted, the main app can still open, so this finding does not claim a complete block in that state.
- **Confidence:** High for the permission-state decision; Android-device reproduction pending. Platform behavior was verified against [Android's partial-photo/video-access documentation](https://developer.android.com/about/versions/14/changes/partial-photo-video-access), which documents the selected-access branch and compatibility session-grant expiry.

## U3 — P2: First-time update installation does not resume after permission approval

- **Source:** `app/src/main/java/com/example/ymediaplayer/update/UpdateManager.kt:206-208`, `:284-288`, `:309-316`; `app/src/main/java/com/example/ymediaplayer/ui/UpdateDialog.kt:72`, `:172-178`; `app/src/main/java/com/example/ymediaplayer/MainActivity.kt:126-135`.
- **Steps to reproduce:** Disable Install unknown apps for AllRounder Player. Download an available update inside the app. Enable the permission in the Android Settings screen that opens, then return to the app.
- **Expected:** Installation resumes using the already downloaded, validated APK, or the app offers Install without another download.
- **Actual code path:** `triggerInstall()` launches the permission Settings screen and returns. The sole call site invokes it at download completion; onResume refreshes media permission and brightness but does not retry installation. The update button still invokes `downloadAndInstall()`, which deletes the ready APK before starting again.
- **Impact:** The first update requires another complete APK download after permission approval. A user who approves permission and then goes offline cannot use the downloaded update through the update UI.
- **Confidence:** High. No pending-install state or permission-result continuation exists in the audited source. Device reproduction pending.

## U4 — P2: Reset All Settings is incomplete and can display a false artwork value

- **Source:** `app/src/main/java/com/example/ymediaplayer/data/DataRepository.kt:687-743`; omitted-key definitions at `:204-208`, `:291-317`, `:573-574`, `:588-599`, `:658-661`, `:755-767`; `app/src/main/java/com/example/ymediaplayer/ui/SettingsScreen.kt:2294-2340`, especially `:2337`.
- **Steps to reproduce:** Choose a non-VINYL music artwork style; change subtitle background, HDR, Play During Phone Calls, or UI corner style. Run Reset All Settings. Leave and reopen Settings and the affected player screens.
- **Expected:** Preferences covered by Reset All Settings return to defaults and remain at defaults after reentry/relaunch.
- **Actual code path:** The reset's removal list omits `music_artwork_style`, `play_during_calls`, `hdr_playback_enabled`, `sub_bg_enabled`, `ui_corner_style`, music sound/EQ settings, and updater preference keys. Settings nevertheless assigns local `musicArtworkStyle = "VINYL"` without persisting it. Reopening Settings reads the old saved artwork style.
- **Impact:** Several user changes survive an operation labelled Reset All Settings. The immediate artwork UI can claim a reset that did not happen persistently.
- **Confidence:** High. Setter keys and reset removals were compared directly. Unit/probe and UI reproduction pending at appendix creation.

## U5 — P2: Manual update failures are silent when no release is known

- **Source:** `app/src/main/java/com/example/ymediaplayer/ui/SettingsScreen.kt:482-486`, `:575-579`, `:1827-1831`, `:2354-2359`; `app/src/main/java/com/example/ymediaplayer/update/UpdateManager.kt:97-103`, `:179-184`; `app/src/main/java/com/example/ymediaplayer/ui/UpdateDialog.kt:71`, `:168-170`.
- **Steps to reproduce:** Start a fresh app session with no update already loaded. Disconnect the network, then tap Check for Updates. The same path applies to a non-success GitHub response such as a rate-limit response.
- **Expected:** Visible failure feedback explains that checking failed and offers a retry.
- **Actual code path:** Settings callers handle only success with no release. UpdateManager sets `errorMessage`, but its only UI reader is the update dialog, mounted only when an update is already known. The checking spinner stops without presenting the error.
- **Impact:** Users cannot distinguish failed checks from a check that did nothing; connection/API problems are hidden.
- **Confidence:** High. Error propagation and every UI read of errorMessage were inspected. Offline device reproduction pending.

## U6 — P2: Global video sound selection is ignored after an in-player route preset is saved

- **Source:** `app/src/main/java/com/example/ymediaplayer/ui/SettingsScreen.kt:115`, `:1136-1139`; `app/src/main/java/com/example/ymediaplayer/data/DataRepository.kt:319-325`; `app/src/main/java/com/example/ymediaplayer/ui/VideoPlayerScreen.kt:1116`, `:1303-1305`, `:4013`.
- **Steps to reproduce:** Using speakers, choose Cinema in the video player's audio picker. Exit to Settings and choose Voice under Video Sound Profiles. Reopen a video using speakers. Repeat with headphones if needed.
- **Expected:** The active preset follows the new selection, or Settings explicitly identifies its scope as a default used only for routes without a saved preset.
- **Actual code path:** Settings writes legacy `video_sound_mode`, while the player writes and reads `video_sound_<route>`. A saved route value takes precedence over the legacy fallback; Settings does not update or clear it.
- **Impact:** Settings visibly confirms a new preset while actual playback continues using the previously saved route preset.
- **Confidence:** High. Directly traceable preference-key mismatch. Audio/device reproduction pending.

## Reviewed paths without a new concrete finding

- Update download limits file size and redirects, restricts URLs to approved HTTPS hosts, verifies APK package identity, and compares APK signing certificates to the installed application.
- The release-notes renderer handles formatted text natively and does not execute HTML, load images, or activate remote links.
- Manifest/FileProvider review did not establish a concrete exploitable issue within this audit's scope; no speculative security finding is included.
