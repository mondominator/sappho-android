# Remove Download from Detail Overflow Menu — Design

**Date:** 2026-04-30
**Scope:** Android (`sapphoapp`), iOS (`sapphoios`)
**Out of scope:** Web/PWA (`sappho/client`) — the web app does not store audiobook files; its "download" button triggers a normal browser file download to the user's filesystem, which the app cannot manage. Audio streams are explicitly passthrough in `client/public/sw.js:219-223`.

## Summary

Add a "Remove Download" action to the audiobook detail screen's overflow menu in both mobile apps, visible only when the book is currently downloaded. Tapping the action shows a confirmation dialog; confirming deletes the local file via the existing `DownloadManager` API. Server-side listening progress is unaffected.

## Motivation

Today, removing a downloaded audiobook is only possible from the Android Downloads screen. Users on the detail screen who want to free up space have no in-context way to remove the local file. iOS has no removal UI at all from the detail screen. This adds the action where users naturally look for it.

## UX

### Visibility

The menu item appears only when the book is in the `downloaded` state:

- **Android:** `viewModel.downloadManager.isDownloaded(audiobookId) && !isDownloading`
- **iOS:** `if case .downloaded = downloadState`

It is hidden during `downloading` and when not downloaded. This avoids ambiguous states (e.g. a half-finished download) and keeps the menu compact for users who never download.

### Position in menu

Just after "Clear Progress." This groups it with the existing user-level destructive action and keeps it above the admin-only section on iOS.

### Visual treatment

- **Android:** `Icons.Filled.Delete` tinted `SapphoError`, label "Remove Download" in `SapphoText`.
- **iOS:** `trash` SF Symbol, color `.sapphoError`, title "Remove Download", subtitle "Delete the local file".

The destructive tint matches the existing "Delete Audiobook" admin item, signaling the destructive nature consistently.

### Confirmation dialog

Reuses the copy from the existing Android Downloads-screen dialog (`MainScreen.kt:780+`) verbatim so the voice stays consistent across surfaces:

- **Title:** Remove Download
- **Body:** Remove "<book title>" from downloads? This will only delete the local file — your listening progress on the server will not be affected.
- **Buttons:** Cancel (info color) / **Remove** (error color)

### Post-confirm behavior

On confirm, call the existing manager method. The `isDownloaded` state flips to false, which causes:

- The download button on the action row reverts to its "Download" affordance automatically (existing reactive code).
- The "Remove Download" menu item disappears the next time the menu opens (gated on `isDownloaded`).

No success toast/snackbar — silent success matches the existing Downloads-screen behavior. Local-file delete failures are also silent (matches existing behavior); the worst case is a redundant retry.

## Android implementation

**File edited:** `app/src/main/java/com/sappho/audiobooks/presentation/detail/AudiobookDetailScreen.kt`

1. Add a `var showRemoveDownloadConfirm by remember { mutableStateOf(false) }` near the other dialog flags.
2. Inside the existing `DropdownMenu` (around line 851), add a new `DropdownMenuItem` after the "Clear Progress" item, gated on `isDownloaded && !isDownloading`. `onClick` closes the menu and sets `showRemoveDownloadConfirm = true`.
3. Add a new `AlertDialog` block alongside the other dialogs, mirroring the one at `MainScreen.kt:780+`:
   - Same `SapphoSurfaceLight` container.
   - Same `SapphoError` / `SapphoInfo` button tints.
   - Confirm button calls `viewModel.deleteDownload()` and clears the flag.
   - Dismiss button just clears the flag.

**No ViewModel or DownloadManager changes.** `AudiobookDetailViewModel.deleteDownload()` already exists at line 555 and already invokes `downloadManager.deleteDownload(book.id)`. `DownloadManager.deleteDownload(audiobookId)` at line 269 of `download/DownloadManager.kt` is the existing entry point used by the Downloads screen.

## iOS implementation

**File edited:** `Sappho/Presentation/Detail/AudiobookDetailView.swift`

1. Add `@State private var showRemoveDownloadConfirm = false` next to the other dialog flags (near `showMoreMenu`, `showDeleteConfirm`).
2. Inside the existing menu sheet content (around line 250, after the "Clear Progress" `if hasProgress` block, before the admin divider), add a conditional `moreMenuItem`:
   - `icon: "trash"`, `title: "Remove Download"`, `subtitle: "Delete the local file"`, `color: .sapphoError`.
   - Action sets `showMoreMenu = false` and `showRemoveDownloadConfirm = true`.
3. Attach an `.alert("Remove Download", isPresented: $showRemoveDownloadConfirm)` modifier to the view chain:
   - Message uses the verbatim copy above with the book title interpolated.
   - "Remove" button (role `.destructive`) calls `downloadManager.removeDownload(audiobookId: displayBook.id)`.
   - "Cancel" button is `.cancel`.
4. Update the sheet's `presentationDetents` calculation to add space for the new item when `downloadState == .downloaded`. The current expression — `.fraction(authRepository.isAdmin ? 0.75 : (chapters.isEmpty ? 0.38 : 0.45))` — gets a `+ 0.05` term applied when the downloaded item is rendered, so the sheet doesn't clip it.

**No DownloadManager changes.** `DownloadManager.removeDownload(audiobookId:)` at line 220 of `Service/DownloadManager.swift` is the existing entry point.

## Testing

### Android

- `AudiobookDetailViewModelTest` — add (or extend) a case verifying that `viewModel.deleteDownload()` invokes `downloadManager.deleteDownload(book.id)`. The screen wiring is exercised by manual smoke.
- **Manual smoke** on a connected device (per `CLAUDE.md`: `./gradlew installDebug` is required after code changes):
  1. Download a book; open its detail screen; open overflow → "Remove Download" appears.
  2. Tap it → dialog appears with correct title and body.
  3. Confirm → file is removed, download icon reverts to the un-downloaded state, menu item disappears on next open.
  4. Repeat and Cancel → nothing changes.
  5. Verify the menu item does NOT appear during an active download.
  6. Verify the menu item does NOT appear for a not-downloaded book.

### iOS

- No new unit test needed: `DownloadManagerTests.swift` already covers `removeDownload(audiobookId:)`. (Confirm during implementation.)
- **Manual smoke** on MonPad iPad (`00008101-000955A61A8B001E`):
  - Same six checks as Android, adapted for iOS interaction (sheet menu, native alert).
  - Additionally verify the sheet detent comfortably fits the new item without clipping in both admin and non-admin views.

## Risks and edge cases

- **Mid-download removal:** Hidden by design — the action is gated on `isDownloaded && !isDownloading`. If a user wants to cancel an in-flight download they use the existing download-button cancel affordance.
- **Stale state:** The state flows in both apps already drive the action row reactively; the menu re-reads `isDownloaded` on each render, so no manual invalidation is needed.
- **Local delete failure:** Both managers handle this silently today (Android returns Boolean, iOS uses `try?`). We do not change this behavior. The feature does not introduce new failure modes.
