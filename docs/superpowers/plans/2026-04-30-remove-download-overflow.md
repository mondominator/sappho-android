# Remove Download Overflow Action — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Surface a "Remove Download" item in the audiobook detail overflow menu on Android and iOS, visible only when the book is currently downloaded; on confirm, delete the local file using existing `DownloadManager` APIs.

**Architecture:** UI-only wire-up on two platforms. No new domain or service code. Both apps already have a working delete API (Android `DownloadManager.deleteDownload(id)` exposed via `AudiobookDetailViewModel.deleteDownload()`; iOS `DownloadManager.removeDownload(audiobookId:)`). The plan adds a gated menu entry and a confirmation dialog mirroring the existing Android Downloads-screen pattern (`MainScreen.kt:780+`).

**Tech Stack:** Kotlin / Jetpack Compose / Hilt (Android); Swift / SwiftUI (iOS); MockK + Truth + JUnit (Android tests).

**Spec:** `docs/superpowers/specs/2026-04-30-remove-download-overflow-design.md`

**Cross-repo note:** Android work lives in `/Users/mondo/Documents/git/sapphoapp` (this repo). iOS work lives in `/Users/mondo/Documents/git/sapphoios`. Each repo gets its own branch and PR.

---

## File Map

**Android (`/Users/mondo/Documents/git/sapphoapp`)**
- Modify: `app/src/main/java/com/sappho/audiobooks/presentation/detail/AudiobookDetailScreen.kt`
  - Add `showRemoveDownloadConfirm` state, a new `DropdownMenuItem`, and an `AlertDialog`.
- Modify: `app/build.gradle.kts`
  - Bump `versionCode` and `versionName` (required by CLAUDE.md before merging to `main`).
- No changes to `AudiobookDetailViewModel.kt` (`deleteDownload()` already exists at line 555).
- No changes to `DownloadManager.kt` (`deleteDownload(id)` already exists at line 269).
- Existing VM-level test at `app/src/test/java/com/sappho/audiobooks/presentation/detail/AudiobookDetailViewModelTest.kt:192` already verifies `viewModel.deleteDownload()` calls `downloadManager.deleteDownload(123)`. No new unit test needed; the only new code is Compose UI wiring exercised by manual smoke per CLAUDE.md.

**iOS (`/Users/mondo/Documents/git/sapphoios`)**
- Modify: `Sappho/Presentation/Detail/AudiobookDetailView.swift`
  - Add `showRemoveDownloadConfirm` state, a new conditional `moreMenuItem`, an `.alert` modifier, and update the sheet's `presentationDetents` calculation.
- No changes to `Sappho/Service/DownloadManager.swift` (`removeDownload(audiobookId:)` already exists at line 220).
- No new tests; `SapphoTests/DownloadManagerTests.swift` already covers `removeDownload`.

---

## Task 1: Android — create feature branch

**Files:** none (git only).

- [ ] **Step 1: Confirm clean working tree**

```bash
cd /Users/mondo/Documents/git/sapphoapp && git status
```

Expected: clean. If not, stop and surface to the user.

- [ ] **Step 2: Create branch from main**

```bash
cd /Users/mondo/Documents/git/sapphoapp && git checkout -b feat/remove-download-overflow
```

Expected: `Switched to a new branch 'feat/remove-download-overflow'`.

---

## Task 2: Android — wire the menu item, state, and confirmation dialog

**Files:**
- Modify: `/Users/mondo/Documents/git/sapphoapp/app/src/main/java/com/sappho/audiobooks/presentation/detail/AudiobookDetailScreen.kt`

This task adds three things in one logical edit pass:

1. A `showRemoveDownloadConfirm` boolean state.
2. A `DropdownMenuItem` inside the existing `DropdownMenu`, gated on `isDownloaded && !isDownloading`, placed immediately after the "Clear Progress" item.
3. An `AlertDialog` that mirrors the dialog at `MainScreen.kt:780+` verbatim, calling `viewModel.deleteDownload()` on confirm.

The `isDownloaded`, `isDownloading`, and `viewModel` references already exist in this composable's scope (see lines 161-162 of the current file). The `book` reference (an `Audiobook?`) is also already in scope, used by the existing menu items — interpolate `book.title` into the dialog body the same way other dialogs in the file do.

- [ ] **Step 1: Add state declaration**

Find the block of `var show…Dialog by remember { mutableStateOf(false) }` declarations near the top of the composable (search for `showOverflowMenu`). Add a sibling line:

```kotlin
var showRemoveDownloadConfirm by remember { mutableStateOf(false) }
```

- [ ] **Step 2: Add the menu item**

In the existing `DropdownMenu` block (currently at line ~851), find the "Clear Progress" `DropdownMenuItem` (search for `"Clear Progress"`). Immediately after the closing `}` of its `if (hasProgress) { … }` wrapper, insert:

```kotlin
// Remove Download (only if downloaded and not currently downloading)
if (isDownloaded && !isDownloading) {
    DropdownMenuItem(
        text = { Text("Remove Download", color = SapphoText) },
        onClick = {
            showOverflowMenu = false
            showRemoveDownloadConfirm = true
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = null,
                tint = SapphoError
            )
        }
    )
}
```

`Icons.Filled.Delete` and `SapphoError` are already imported (the file uses `Icons.Default.Download` and other Sappho colors). Confirm by searching: `grep -n "Icons.Filled.Delete\|SapphoError" app/src/main/java/com/sappho/audiobooks/presentation/detail/AudiobookDetailScreen.kt`. If `Icons.Filled.Delete` is missing, add `import androidx.compose.material.icons.filled.Delete` to the imports.

- [ ] **Step 3: Add the confirmation dialog**

Find an existing `AlertDialog` in this file (e.g. by searching for `AlertDialog(`) — there are several already. Add a new one alongside them, ideally near where other dialog state flags are read. Use the exact body from `MainScreen.kt:780+` so the wording stays consistent:

```kotlin
if (showRemoveDownloadConfirm) {
    AlertDialog(
        onDismissRequest = { showRemoveDownloadConfirm = false },
        title = { Text("Remove Download", color = Color.White) },
        text = {
            Text(
                "Remove \"${book?.title ?: "this book"}\" from downloads? This will only delete the local file - your listening progress on the server will not be affected.",
                color = SapphoTextLight
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    viewModel.deleteDownload()
                    showRemoveDownloadConfirm = false
                }
            ) {
                Text("Remove", color = SapphoError)
            }
        },
        dismissButton = {
            TextButton(onClick = { showRemoveDownloadConfirm = false }) {
                Text("Cancel", color = SapphoInfo)
            }
        },
        containerColor = SapphoSurfaceLight
    )
}
```

If any of `SapphoTextLight`, `SapphoInfo`, `SapphoSurfaceLight`, or `Color.White` are not already imported in this file, add the imports — they're all used in `MainScreen.kt:780+` so the same imports apply.

- [ ] **Step 4: Build the project**

```bash
cd /Users/mondo/Documents/git/sapphoapp && export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" && ./gradlew assembleDebug
```

Expected: `BUILD SUCCESSFUL`. If unresolved-reference errors appear for any color/icon, fix the imports and rerun.

- [ ] **Step 5: Run the existing test suite**

```bash
cd /Users/mondo/Documents/git/sapphoapp && export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" && ./gradlew test
```

Expected: all tests pass. The existing `AudiobookDetailViewModelTest` test at line 192 (which already verifies `deleteDownload()` calls the manager) covers the VM contract.

- [ ] **Step 6: Commit**

```bash
cd /Users/mondo/Documents/git/sapphoapp && git add app/src/main/java/com/sappho/audiobooks/presentation/detail/AudiobookDetailScreen.kt && git commit -m "$(cat <<'EOF'
feat: add Remove Download to detail overflow menu

Adds a "Remove Download" item to the audiobook detail overflow menu,
visible only when the book is currently downloaded. Tapping shows a
confirmation dialog that mirrors the existing Downloads-screen copy;
on confirm, delegates to the existing AudiobookDetailViewModel.deleteDownload().

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 3: Android — bump version

**Files:**
- Modify: `/Users/mondo/Documents/git/sapphoapp/app/build.gradle.kts`

CLAUDE.md mandates a version bump on every PR before merging — the Play Store deploy job rejects duplicate version codes.

- [ ] **Step 1: Read current version**

```bash
cd /Users/mondo/Documents/git/sapphoapp && grep -E "versionCode|versionName" app/build.gradle.kts
```

Expected: prints the current `versionCode` and `versionName` lines (most recent on `main`: `versionCode = 98`, `versionName = "0.9.80"`).

- [ ] **Step 2: Increment both**

In `app/build.gradle.kts`, bump `versionCode` by 1 (e.g. `98 → 99`) and the patch component of `versionName` by 1 (e.g. `"0.9.80" → "0.9.81"`). Per CLAUDE.md "Versioning Guidelines", patch is the correct increment for a single feature.

- [ ] **Step 3: Verify the diff**

```bash
cd /Users/mondo/Documents/git/sapphoapp && git diff app/build.gradle.kts
```

Expected: only the two version lines changed.

- [ ] **Step 4: Commit**

```bash
cd /Users/mondo/Documents/git/sapphoapp && git add app/build.gradle.kts && git commit -m "$(cat <<'EOF'
chore: bump version for remove-download feature

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 4: Android — install and manual smoke

**Files:** none (manual verification on device).

- [ ] **Step 1: Install on connected device**

```bash
cd /Users/mondo/Documents/git/sapphoapp && export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" && ./gradlew installDebug
```

Expected: `INSTALL SUCCEEDED`. CLAUDE.md requires `installDebug` after code changes.

- [ ] **Step 2: Hand off to user for manual smoke**

Surface this checklist to the user — do NOT take screenshots (CLAUDE.md "Screenshots" rule prohibits it) and do NOT auto-verify via adb. The user tests manually:

1. Download a book; open its detail screen; open the overflow → "Remove Download" appears.
2. Tap "Remove Download" → dialog appears with title "Remove Download" and the configured copy, including the book title.
3. Tap **Remove** → file is removed, download icon on the action row reverts to the un-downloaded state, and reopening the overflow no longer shows "Remove Download."
4. Repeat the flow and tap **Cancel** → nothing changes.
5. Verify the menu item does NOT appear during an active download.
6. Verify the menu item does NOT appear for a not-downloaded book.

Wait for the user's confirmation before proceeding to PR.

---

## Task 5: Android — push branch and open PR

**Files:** none (git/gh).

- [ ] **Step 1: Push branch**

```bash
cd /Users/mondo/Documents/git/sapphoapp && git push -u origin feat/remove-download-overflow
```

Expected: branch pushed; remote tracking set.

- [ ] **Step 2: Open PR via gh**

```bash
cd /Users/mondo/Documents/git/sapphoapp && gh pr create --title "feat: add Remove Download to detail overflow menu" --body "$(cat <<'EOF'
## Summary
- Adds a "Remove Download" item to the audiobook detail overflow menu, visible only when the book is currently downloaded.
- Confirmation dialog mirrors the existing Downloads-screen pattern; server-side listening progress is unaffected.
- Bumps versionCode/versionName per release checklist.

## Test plan
- [x] Build succeeds (`./gradlew assembleDebug`)
- [x] Unit tests pass (`./gradlew test`)
- [ ] Menu item appears on a downloaded book; hidden on not-downloaded and during active download
- [ ] Confirmation dialog shows and the **Remove** path deletes the file
- [ ] **Cancel** preserves the download

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

Expected: prints the PR URL. Do not enable automerge (CLAUDE.md). Wait for both `Build and Release APK` and `Deploy to Play Store` workflows to pass before marking the PR complete.

---

## Task 6: iOS — create feature branch

**Files:** none (git only).

- [ ] **Step 1: Confirm clean working tree**

```bash
cd /Users/mondo/Documents/git/sapphoios && git status
```

Expected: clean. If not, stop and surface to the user.

- [ ] **Step 2: Create branch from main**

```bash
cd /Users/mondo/Documents/git/sapphoios && git checkout main && git pull && git checkout -b feat/remove-download-overflow
```

Expected: branch created from up-to-date `main`.

---

## Task 7: iOS — wire the menu item, state, alert, and detent adjustment

**Files:**
- Modify: `/Users/mondo/Documents/git/sapphoios/Sappho/Presentation/Detail/AudiobookDetailView.swift`

This task adds:

1. A `@State private var showRemoveDownloadConfirm = false` declaration.
2. A `moreMenuItem` rendered after the "Clear Progress" `if hasProgress` block and before the admin divider, gated on `if case .downloaded = downloadState`.
3. An `.alert("Remove Download", …)` modifier next to the existing `showDeleteConfirm` alert (line 158).
4. An updated `presentationDetents` expression so the sheet doesn't clip the new row.

Text references `displayBook.title`, which is already in scope (computed property at line 67-69 of the current file).

- [ ] **Step 1: Add the state declaration**

Find the line `@State private var showDeleteConfirm = false` (around line 50). Add a sibling:

```swift
@State private var showRemoveDownloadConfirm = false
```

- [ ] **Step 2: Add the menu item**

In the menu sheet (around line 250 in the current file), find the `if hasProgress { moreMenuItem(…"Clear Progress"…) }` block. Immediately after the closing `}` of that `if hasProgress { … }` block, insert:

```swift
if case .downloaded = downloadState {
    moreMenuItem(
        icon: "trash",
        title: "Remove Download",
        subtitle: "Delete the local file",
        color: .sapphoError
    ) {
        showMoreMenu = false
        showRemoveDownloadConfirm = true
    }
}
```

The `moreMenuItem` helper, `downloadState` computed property, and `.sapphoError` color are already in scope.

- [ ] **Step 3: Add the alert**

Find the existing `.alert("Delete Audiobook", isPresented: $showDeleteConfirm) { … }` block (line 158). Immediately after its closing brace (the `} message: { … }` block that closes around line 168), append:

```swift
.alert("Remove Download", isPresented: $showRemoveDownloadConfirm) {
    Button("Cancel", role: .cancel) {}
    Button("Remove", role: .destructive) {
        downloadManager.removeDownload(audiobookId: displayBook.id)
    }
} message: {
    Text("Remove \"\(displayBook.title)\" from downloads? This will only delete the local file — your listening progress on the server will not be affected.")
}
```

`downloadManager` and `displayBook` are already in scope.

- [ ] **Step 4: Adjust the sheet's presentationDetents**

Find the line:

```swift
.presentationDetents([.fraction(authRepository.isAdmin ? 0.75 : (chapters.isEmpty ? 0.38 : 0.45))])
```

Replace with:

```swift
.presentationDetents([.fraction({
    let base = authRepository.isAdmin ? 0.75 : (chapters.isEmpty ? 0.38 : 0.45)
    let isDownloaded: Bool = { if case .downloaded = downloadState { return true } else { return false } }()
    return base + (isDownloaded ? 0.05 : 0.0)
}())])
```

Rationale: when the new row is rendered the sheet needs ~5% more vertical space; otherwise the detent is unchanged.

- [ ] **Step 5: Build**

```bash
cd /Users/mondo/Documents/git/sapphoios && xcodebuild -project Sappho.xcodeproj -scheme Sappho -destination 'id=00008101-000955A61A8B001E' -derivedDataPath build build 2>&1 | tail -30
```

Expected: `** BUILD SUCCEEDED **`. SourceKit diagnostic warnings may appear and are IDE noise per the project's auto-memory; only `xcodebuild` errors matter.

- [ ] **Step 6: Run unit tests**

```bash
cd /Users/mondo/Documents/git/sapphoios && xcodebuild test -project Sappho.xcodeproj -scheme Sappho -destination 'platform=iOS Simulator,name=iPhone 15' 2>&1 | tail -20
```

Expected: tests pass (only need to verify the existing `DownloadManagerTests` still passes; we did not modify the manager).

- [ ] **Step 7: Commit**

```bash
cd /Users/mondo/Documents/git/sapphoios && git add Sappho/Presentation/Detail/AudiobookDetailView.swift && git commit -m "$(cat <<'EOF'
feat: add Remove Download to detail overflow menu

Adds a "Remove Download" item to the audiobook detail overflow sheet,
visible only when the book is currently downloaded. Tapping shows a
native confirmation alert; on confirm, calls the existing
DownloadManager.removeDownload(audiobookId:). Adjusts sheet detents
so the new row is not clipped.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 8: iOS — install and manual smoke

**Files:** none (manual verification on iPad).

- [ ] **Step 1: Install on MonPad**

```bash
xcrun devicectl device install app --device 00008101-000955A61A8B001E /Users/mondo/Documents/git/sapphoios/build/Build/Products/Debug-iphoneos/Sappho.app
```

Expected: install succeeds. Device id is from the project auto-memory.

- [ ] **Step 2: Hand off to user for manual smoke**

Surface the same six checks as Task 4 to the user (adapted for iOS UI: bottom sheet, native alert). Additionally verify the sheet detent comfortably fits the new item without clipping — both as a non-admin user and (if available) admin. Wait for the user's confirmation before proceeding.

---

## Task 9: iOS — push branch and open PR

**Files:** none (git/gh).

- [ ] **Step 1: Push branch**

```bash
cd /Users/mondo/Documents/git/sapphoios && git push -u origin feat/remove-download-overflow
```

- [ ] **Step 2: Open PR via gh**

```bash
cd /Users/mondo/Documents/git/sapphoios && gh pr create --title "feat: add Remove Download to detail overflow menu" --body "$(cat <<'EOF'
## Summary
- Adds a "Remove Download" item to the audiobook detail overflow sheet, visible only when the book is currently downloaded.
- Native iOS alert mirrors the Android Downloads-screen confirmation copy; server-side listening progress is unaffected.
- Adjusts the sheet's `presentationDetents` so the new row is not clipped.

## Test plan
- [x] `xcodebuild` succeeds
- [ ] Menu item appears on a downloaded book; hidden on not-downloaded and during active download
- [ ] Confirmation alert shows and **Remove** deletes the file
- [ ] **Cancel** preserves the download
- [ ] Sheet detent fits the new row in admin and non-admin contexts

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

Expected: prints the PR URL. Wait for CI before marking complete; do not enable automerge.

---

## Self-review checklist (already run by author)

- **Spec coverage:** Every spec section maps to a task. UX → Tasks 2 & 7. Android implementation → Tasks 1–5. iOS implementation → Tasks 6–9. Testing → Tasks 4 & 8 (manual smoke; existing unit tests cover the deletion contract). Risks → handled via the gated visibility in Task 2/7.
- **Placeholders:** None. Every code step contains the literal code; every command step contains the exact command and expected output.
- **Type consistency:** `viewModel.deleteDownload()`, `downloadManager.removeDownload(audiobookId:)`, `DownloadState.downloaded`, `Audiobook.title`, `displayBook.title`, `Icons.Filled.Delete`, `SapphoError`, `SapphoTextLight`, `SapphoInfo`, `SapphoSurfaceLight` — all match identifiers verified in the current source files.
