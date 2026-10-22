# Library and music audit

Audit date: 2026-10-14 (Asia/Calcutta).

Scope: library, history, playlists, file operations, music player/service, and notification control paths in the current workspace. This is a **read-only static audit**. No production code or user media was changed. No Gradle or device commands were run by this audit agent. Runtime confirmation, where obtained, belongs in the main QA report.

## Findings

### LIB-01 — P1: “Move to Bin” permanently deletes media on Android 7–10

- **Status/confidence:** Static, high confidence.
- **Sources:** `C:/Users/Yashwant/Desktop/AllRounder Media Player/app/src/main/java/com/example/ymediaplayer/data/FileManager.kt:40-48`; `C:/Users/Yashwant/Desktop/AllRounder Media Player/app/src/main/java/com/example/ymediaplayer/ui/FolderDetailScreen.kt:779-786`; `C:/Users/Yashwant/Desktop/AllRounder Media Player/app/src/main/java/com/example/ymediaplayer/ui/FolderListScreen.kt:1475-1476`; supported minimum API is 24 in `C:/Users/Yashwant/Desktop/AllRounder Media Player/app/build.gradle.kts:22`.
- **Reproduce:** On Android 9 or earlier, use a disposable test video, choose Move to Bin, and confirm. Android 10 has the same permanent-delete implementation when write authorization already exists.
- **Expected:** A recoverable trash operation, as the confirmation explicitly promises restoration within 30 days.
- **Observed in code:** Only API 30+ uses `MediaStore.createTrashRequest()`. API 24–29 instead calls `contentResolver.delete()`.
- **Impact:** Irrecoverable user media loss after agreeing to an operation described as reversible. Do not reproduce against real user files.
- **Suggested correction:** Show permanent-delete wording on unsupported Android versions, or implement an app-managed recoverable bin.

### MUS-01 — P2: Sleep timer is cancelled and forgotten when leaving Music

- **Status/confidence:** Static, high confidence.
- **Sources:** `C:/Users/Yashwant/Desktop/AllRounder Media Player/app/src/main/java/com/example/ymediaplayer/ui/MusicScreen.kt:323-324,403-413,2434-2447`; `C:/Users/Yashwant/Desktop/AllRounder Media Player/app/src/main/java/com/example/ymediaplayer/ui/FolderListScreen.kt:627-635`.
- **Reproduce:** Start music, set a sleep timer, collapse the full player, switch to Video, then return to Music. A screen recreation also discards the timer.
- **Expected:** The timer remains active while music continues through MusicService and eventually pauses it.
- **Observed in code:** Timer values use local `remember` state, and the countdown is a screen-scoped `LaunchedEffect`. The tab switch removes MusicScreen, cancels its effect, and recreates its state as zero on return.
- **Impact:** Music can continue beyond the user's requested stop time.
- **Suggested correction:** Store an absolute deadline and own the timer in the playback service; expose that state to the screen.

### MUS-02 — P2: Playing a playlist does not establish a playlist queue

- **Status/confidence:** Static, high confidence.
- **Sources:** `C:/Users/Yashwant/Desktop/AllRounder Media Player/app/src/main/java/com/example/ymediaplayer/ui/MusicScreen.kt:415-448,459-484,617-618,2230-2232,2250-2253,2278-2280,2424-2429`.
- **Reproduce:** Create a playlist containing two tracks that are not adjacent in the full library. Play its first track and press Next, or let the track end with auto-next enabled. Inspect Play Queue.
- **Expected:** Next follows the selected playlist, and the queue contains its tracks in playlist order. Playlist Shuffle should continue within that playlist.
- **Observed in code:** Playlist clicks only pass a single song to `playSongForce()`. Next/Previous use `allSongs`, service synchronization assigns `allSongs` to `currentPlaylist`, and the queue sheet also receives `allSongs`. Playlist Shuffle chooses only the starting song at random.
- **Impact:** Playback leaves the playlist and can play tracks the user did not select.
- **Suggested correction:** Own an active queue separately from the library and pass the selected playlist/order into playback.

### MUS-03 — P2: Playing tracks from the music UI does not update Recent

- **Status/confidence:** Static, high confidence.
- **Sources:** `C:/Users/Yashwant/Desktop/AllRounder Media Player/app/src/main/java/com/example/ymediaplayer/ui/MusicScreen.kt:415-451,680-687,1776-1816`; `C:/Users/Yashwant/Desktop/AllRounder Media Player/app/src/main/java/com/example/ymediaplayer/data/DataRepository.kt:212-224,434-441`; `C:/Users/Yashwant/Desktop/AllRounder Media Player/app/src/main/java/com/example/ymediaplayer/service/MusicService.kt:324`.
- **Reproduce:** Play a previously unplayed song from Tracks, then open Recent. Compare with a track started through the service's background Next path.
- **Expected:** Each newly played track appears at the start of Recently Played.
- **Observed in code:** UI playback calls `incrementPlayCount()`, which updates play counts and `played_songs_uris`. Recent reads a separate `music_played_uris` list, written by `recordMusicPlayed()`. That call exists in `MusicService.playSong()`, but the UI directly submits a media item to its controller and bypasses it.
- **Impact:** Recent remains empty/stale and history differs depending on whether playback started through the UI or the background service.
- **Suggested correction:** Record history centrally on a successful media item transition/start rather than in only one playback entry point.

### LIB-02 — P2: Scoped-storage authorization does not retry the failed rename/delete

- **Status/confidence:** Static, high confidence; applies when MediaStore throws `RecoverableSecurityException` (particularly Android 10).
- **Sources:** `C:/Users/Yashwant/Desktop/AllRounder Media Player/app/src/main/java/com/example/ymediaplayer/data/FileManager.kt:50-52,66-82`; `C:/Users/Yashwant/Desktop/AllRounder Media Player/app/src/main/java/com/example/ymediaplayer/ui/FolderDetailScreen.kt:110-119,742-752,794-803`; `C:/Users/Yashwant/Desktop/AllRounder Media Player/app/src/main/java/com/example/ymediaplayer/ui/FolderListScreen.kt:180-189,1454-1459`.
- **Reproduce:** Rename another app's video when Android requests write consent, then grant the request. On Android 10, the same authorization/retry problem affects deletion of other-owned media.
- **Expected:** Approval resumes the pending rename/delete automatically and the UI reflects the completed operation.
- **Observed in code:** The failed operation returns only an `IntentSender`. Both screens launch it through their delete-result launcher. On approval, the callback merely clears deletion/selection state and increments refresh; it retains no pending operation or filename and never retries `update()`/`delete()`.
- **Impact:** Approval succeeds but the requested operation does not. Rename requires another explicit attempt; Android 10 deletion can appear finished while leaving the file intact.
- **Suggested correction:** Track the pending operation and retry after recoverable permission approval. Keep the API 30+ trash result path distinct because that system request actually performs the trash operation.

### MUS-04 — P2: Repeat One and Shuffle are not maintained by the playback service

- **Status/confidence:** Static, high confidence for leaving Music / background fallback. Foreground completion ordering remains a runtime test item.
- **Sources:** `C:/Users/Yashwant/Desktop/AllRounder Media Player/app/src/main/java/com/example/ymediaplayer/ui/MusicScreen.kt:266-268,459-474,553-561,592-599,620-621,2345-2355`; `C:/Users/Yashwant/Desktop/AllRounder Media Player/app/src/main/java/com/example/ymediaplayer/service/MusicService.kt:394-405,467,520-523`; default auto-next is enabled in `C:/Users/Yashwant/Desktop/AllRounder Media Player/app/src/main/java/com/example/ymediaplayer/data/DataRepository.kt:521`.
- **Reproduce:** Play music; enable Repeat One or Shuffle; collapse the player and switch to Video; use notification Next or allow the track to finish. Return to Music and inspect the controls.
- **Expected:** Repeat One continues repeating the same song, Shuffle continues choosing from the active queue, and their displayed states stay consistent.
- **Observed in code:** Both controls update local `remember` state only. There are no assignments to music player/controller `repeatMode` or `shuffleModeEnabled`. The service checks its actual player's repeat mode on completion, and otherwise advances sequentially modulo `currentPlaylist`. Leaving Music disposes its screen callbacks and the local settings; returning recreates Repeat Off and Shuffle Off.
- **Impact:** Playback behavior changes when navigating between tabs; Repeat One can advance and Shuffle becomes sequential in the service fallback path.
- **Suggested correction:** Give the service ownership of repeat/shuffle state and queue transitions; observe that state in the UI.
- **Independent wiring validation:** An exhaustive source search for `repeatMode =`, `shuffleModeEnabled =`, `setRepeatMode`, and `setShuffleModeEnabled` in MusicScreen/MusicService found only the local UI repeat assignment, and no engine assignments. The screen and service also each implement `STATE_ENDED` handling. Potential foreground duplicate-advance/next-track restart behavior from those two handlers is not claimed as reproduced and should be checked at runtime.

## Validation limits

- These six findings are inferred from complete, connected call paths, not from test failures.
- Existing FileManager unit tests cover result types/date/resolution helpers; they do not exercise scoped-storage permission retries or deletion semantics.
- Android versions, permissions, file ownership, media availability, and callback scheduling should be stated beside any runtime results in the main report.
- No assertion is made here that every app feature has been fully tested or that all bugs have been found.
