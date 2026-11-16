# Home section and footer fixes

- The floating footer blur consumes touch sequences in its empty space. Its
  foreground navigation/mini-player controls remain separate interactive siblings.
  The blur has no click semantics, action, ripple or haptic feedback.
- Home video/folder context sheets use an opaque themed surface. Folder cards and
  the navigation footer no longer bleed through the menu. The action lists scroll
  when their content exceeds the available height.
- Recently Added and Continue Watching offer **Remove from this list** in their
  home-card context menus and their See all screens. This stores section-specific
  hidden URI sets; it never deletes/trashes a file or clears resume/history data.
  Hidden entries stay available in normal folders, All Videos and favorites.
- The Continue Watching card's close button uses the same non-destructive action.
  Undo restores the entry. Home snackbar actions are positioned above the footer
  so the touch barrier cannot cover Undo.
- Hiding survives app restarts, playback progress updates and settings reset.
  Removing from one shelf does not remove the same item from the other shelf.

Validation: 186 JVM tests, 185 passed, one skipped, no failures. New tests cover
section independence, persistence, resume/favorite/history preservation and Undo.
The footer instrumentation regression covers the reported normalized touch point,
long presses, swipes, foreground controls and content above the footer. Device
execution is pending because no ADB device was connected during this revision.

Build log: `build/home-section-final-build.log`.
Release APK: `app/build/outputs/apk/release/app-release.apk`.
Debug, instrumentation APK compilation and signed optimized release builds passed.
Release SHA-256: `DB5F2506DF2979CD09FBCA8BCA3A822889DBCA5B531F8653DD6D9F9EA89873EF`.
