# Notifications & Reviews Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add an in-app notification system (global announcements for new books, public collections) and surface review text alongside star ratings on all platforms.

**Architecture:** Server-side notifications are auto-generated when library scans find new books or when public collections are created/updated. Clients poll for unread count every 2-3 minutes and display a bell icon with badge in the top bar. Reviews piggyback on the existing `user_ratings` table which already has a `review` TEXT column — the work is adding UI for writing and displaying reviews.

**Tech Stack:** Node.js/Express + SQLite (server), Jetpack Compose + Retrofit (Android), SwiftUI + URLSession (iOS), React (PWA)

---

### Task 1: Server — Notifications Database Migration

**Files:**
- Create: `/Users/mondo/Documents/git/sappho/server/migrations/031_add_notifications.js`

**Step 1: Write the migration file**

Create a migration with two tables:

`notifications` table:
- id INTEGER PRIMARY KEY AUTOINCREMENT
- type TEXT NOT NULL (values: new_audiobook, new_public_collection, collection_item_added)
- title TEXT NOT NULL
- message TEXT NOT NULL
- metadata TEXT (JSON string with audiobook_id, collection_id, etc.)
- created_at DATETIME DEFAULT CURRENT_TIMESTAMP

`user_notification_reads` table:
- user_id INTEGER NOT NULL (FK to users)
- notification_id INTEGER NOT NULL (FK to notifications with CASCADE delete)
- read_at DATETIME DEFAULT CURRENT_TIMESTAMP
- PRIMARY KEY (user_id, notification_id)

Add indexes on `notifications(created_at DESC)` and `notifications(type)`.

Follow the pattern of existing migrations (e.g., `030_add_oidc_support.js`) — export an `up(db)` async function.

**Step 2: Verify migration runs**

Start the server and check logs for "Running migration: 031_add_notifications".

**Step 3: Commit**

```bash
git add server/migrations/031_add_notifications.js
git commit -m "feat: add notifications database tables"
```

---

### Task 2: Server — Notification API Routes

**Files:**
- Create: `/Users/mondo/Documents/git/sappho/server/routes/notifications.js`
- Modify: `/Users/mondo/Documents/git/sappho/server/index.js` (add route mount, around line 137)

**Step 1: Create the notifications route file**

Endpoints to implement:

`GET /api/notifications` — List notifications with read/unread status for the current user.
- Query params: `limit` (default 50, max 100), `offset` (default 0)
- SELECT from `notifications` LEFT JOIN `user_notification_reads` on notification_id and user_id
- Return `is_read` as 1 or 0
- ORDER BY created_at DESC

`GET /api/notifications/unread-count` — Return `{ count: N }` of unread notifications.
- COUNT notifications WHERE NOT EXISTS a read record for this user

`POST /api/notifications/:id/read` — Mark one notification as read.
- INSERT OR IGNORE into user_notification_reads

`POST /api/notifications/read-all` — Mark all as read for current user.
- INSERT OR IGNORE for all unread notification IDs

All endpoints require `authenticateToken` middleware. Use rate limiting (60 req/min).

Follow patterns from existing routes like `server/routes/ratings.js`:
- Import `authenticateToken` from `../auth`
- Import `dbAll`, `dbRun`, `dbGet` from `../utils/db`
- Use express-rate-limit

**Step 2: Mount the route in index.js**

Add after the ratings route mount (around line 137):
```javascript
app.use('/api/notifications', require('./routes/notifications'));
```

**Step 3: Test endpoints manually with curl**

**Step 4: Commit**

```bash
git add server/routes/notifications.js server/index.js
git commit -m "feat: add notification API endpoints"
```

---

### Task 3: Server — Notification Generation Hooks

**Files:**
- Create: `/Users/mondo/Documents/git/sappho/server/services/notificationService.js`
- Modify: `/Users/mondo/Documents/git/sappho/server/services/libraryScanner.js` (around line 185, after emailService.notifyNewAudiobook)
- Modify: `/Users/mondo/Documents/git/sappho/server/routes/collections.js` (POST handler at line 104, POST items handler at line 265)

**Step 1: Create the notification service**

A simple module with these functions:

`createNotification(type, title, message, metadata)` — INSERT into notifications table. Wrap in try/catch and log errors but don't throw (notifications are non-critical).

`notifyNewAudiobook(audiobook)` — Creates a notification with type `new_audiobook`, title "New audiobook added", message like `"Book Title" by Author was added to the library`, metadata `{ audiobook_id }`.

`notifyNewPublicCollection(collection, username)` — Type `new_public_collection`, message like `Username created a public collection: "Collection Name"`, metadata `{ collection_id }`.

`notifyCollectionItemAdded(collection, audiobook, username)` — Type `collection_item_added`, message like `Username added "Book Title" to "Collection Name"`, metadata `{ collection_id, audiobook_id }`.

**Step 2: Hook into libraryScanner.js**

Import the notification service at the top. After the existing `emailService.notifyNewAudiobook(audiobook)` call (around line 185), call `notificationService.notifyNewAudiobook(audiobook)`. Do the same for multi-file audiobooks (around line 381).

**Step 3: Hook into collections.js**

Import the notification service. In the POST `/` handler (create collection), after the successful INSERT, if `is_public` is true, fetch the user's display name and call `notifyNewPublicCollection()`.

In the POST `/:id/items` handler (add item), after the successful INSERT, check if the collection is public. If so, fetch the audiobook title and user display name, then call `notifyCollectionItemAdded()`.

**Step 4: Test by triggering events and checking GET /api/notifications**

**Step 5: Commit**

```bash
git add server/services/notificationService.js server/services/libraryScanner.js server/routes/collections.js
git commit -m "feat: generate notifications for new books and public collections"
```

---

### Task 4: Android — Reviews UI on Detail Page

**Files:**
- Modify: `/Users/mondo/Documents/git/sapphoapp/app/src/main/java/com/sappho/audiobooks/presentation/detail/AudiobookDetailScreen.kt` (rating section at lines 466-593)
- Modify: `/Users/mondo/Documents/git/sapphoapp/app/src/main/java/com/sappho/audiobooks/presentation/detail/AudiobookDetailViewModel.kt` (add review state and methods)
- Modify: `/Users/mondo/Documents/git/sapphoapp/app/src/main/java/com/sappho/audiobooks/data/remote/SapphoApi.kt` (add getAllRatings endpoint)

**What to build:**

1. **API**: Add `getAllRatings(audiobookId)` endpoint to `SapphoApi.kt` calling `GET /api/ratings/audiobook/{id}/all`. Create a `ReviewItem` data class with fields: id, userId, audiobookId, rating, review, username, displayName, createdAt, updatedAt. Use `@SerializedName` for snake_case fields.

2. **ViewModel**: Add `_reviews: MutableStateFlow<List<ReviewItem>>`, `_userReviewText: MutableStateFlow<String>`, and a `loadReviews()` method. Modify `setRating()` to also send review text via the existing `RatingRequest` (which already has a `review` field). Call `loadReviews()` after loading the audiobook.

3. **UI — Review text input**: Below the star picker (after line 591), when a rating is selected, show a `TextField` for writing a review (placeholder: "Write a review (optional)") and a "Submit" button. Pre-populate if user already has a review.

4. **UI — Reviews list**: Below the rating section, show a "Reviews" header with count, then cards for each review. Each card: display name or username (bold), small filled stars, review text, relative date. Only show entries with non-empty review text. Use `MaterialTheme.sapphoColors` for colors.

5. **Relative date helper**: Format ISO date strings to relative time ("just now", "2 hours ago", "3 days ago").

**Step 1: Implement all changes following existing patterns**

**Step 2: Build and verify**

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
cd /Users/mondo/Documents/git/sapphoapp && ./gradlew assembleDebug
```

**Step 3: Commit**

```bash
git add app/src/main/java/com/sappho/audiobooks/
git commit -m "feat: add reviews UI to audiobook detail page (Android)"
```

---

### Task 5: Android — Bell Icon + Notification Panel

**Files:**
- Modify: `/Users/mondo/Documents/git/sapphoapp/app/src/main/java/com/sappho/audiobooks/presentation/main/MainScreen.kt` (TopBar area, lines 1143-1186 where avatar is)
- Modify: `/Users/mondo/Documents/git/sapphoapp/app/src/main/java/com/sappho/audiobooks/data/remote/SapphoApi.kt` (add notification endpoints)
- Create: `/Users/mondo/Documents/git/sapphoapp/app/src/main/java/com/sappho/audiobooks/presentation/notifications/NotificationPanel.kt`

**What to build:**

1. **API**: Add notification endpoints to `SapphoApi.kt`:
   - `GET api/notifications` → `Response<List<NotificationItem>>`
   - `GET api/notifications/unread-count` → `Response<UnreadCount>`
   - `POST api/notifications/{id}/read` → `Response<Unit>`
   - `POST api/notifications/read-all` → `Response<Unit>`

   Data models: `NotificationItem` (id, type, title, message, metadata, createdAt, isRead) and `UnreadCount` (count). Use `@SerializedName` for snake_case fields.

2. **Notification state in MainScreen**: Add state for `unreadCount`, `showNotificationPanel`, and `notifications` list. Poll `getUnreadNotificationCount()` every 2 minutes using `LaunchedEffect` with `delay()` loop. Fetch full list when panel opens.

3. **Bell icon**: In the TopBar, add a bell icon (`Icons.Outlined.Notifications`) to the LEFT of the avatar box (before line 1143). Use Material 3 `BadgedBox` with a red badge showing unread count (hidden when 0). Clickable to toggle notification panel.

4. **NotificationPanel composable**: Dropdown panel positioned similarly to the user menu dropdown (follow pattern at lines 1191-1256):
   - Header: "Notifications" title + "Mark all read" text button
   - List of notification items with: type icon (book for new_audiobook, folder for collections), message, relative timestamp, unread highlight (subtle accent background)
   - Tapping a notification: mark as read, navigate to audiobook or collection via `navController`
   - Parse `metadata` JSON string to extract `audiobook_id` or `collection_id` for navigation
   - Empty state: "No notifications yet"
   - Use `MaterialTheme.sapphoColors` for all colors

**Step 1: Implement all changes following existing patterns**

**Step 2: Build and verify**

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
cd /Users/mondo/Documents/git/sapphoapp && ./gradlew assembleDebug
```

**Step 3: Commit**

```bash
git add app/src/main/java/com/sappho/audiobooks/
git commit -m "feat: add notification bell icon and panel (Android)"
```

---

### Task 6: iOS — Reviews UI on Detail Page

**Files:**
- Modify: `/Users/mondo/Documents/git/sapphoios/Sappho/Presentation/Detail/AudiobookDetailView.swift` (rating section at lines 287-335)
- Modify: `/Users/mondo/Documents/git/sapphoios/Sappho/Services/SapphoAPI.swift` (add getAllRatings, around line 415)
- Modify: `/Users/mondo/Documents/git/sapphoios/Sappho/Models/Models.swift` (add ReviewItem model)

**What to build:**

1. **API**: Add `getAllRatings(audiobookId:)` to `SapphoAPI.swift` calling `api/ratings/audiobook/{id}/all`, returning `[ReviewItem]`.

2. **Model**: Add `ReviewItem` struct to `Models.swift` (Codable, Identifiable) with: id, userId, audiobookId, rating, review, username, displayName, createdAt, updatedAt.

3. **State**: Add `@State` properties: `reviews: [ReviewItem]`, `userReviewText: String`, `showReviewField: Bool`.

4. **UI — Review input**: After the star picker, show a `TextField` for review text and a "Submit" button when a rating is selected. Pre-populate from existing review.

5. **UI — Reviews list**: Below rating section, show reviews with: display name or username (bold), small star indicators, review text, relative date. Only show entries with non-empty reviews.

6. **Relative date**: Use `RelativeDateTimeFormatter` or a simple helper.

**Step 1: Implement following existing SwiftUI patterns**

Use `@Environment(\.sapphoAPI)` for API access, `@State` for local state, `.task { }` for loading.

**Step 2: Build and verify**

```bash
cd /Users/mondo/Documents/git/sapphoios
xcodebuild -project Sappho.xcodeproj -scheme Sappho -destination 'id=00008110-000C48680C9A201E' -derivedDataPath build build 2>&1 | tail -5
```

**Step 3: Commit**

```bash
git add Sappho/
git commit -m "feat: add reviews UI to audiobook detail page (iOS)"
```

---

### Task 7: iOS — Bell Icon + Notification Panel

**Files:**
- Modify: `/Users/mondo/Documents/git/sapphoios/Sappho/Presentation/Home/MainView.swift` (topBar area, around line 155)
- Modify: `/Users/mondo/Documents/git/sapphoios/Sappho/Services/SapphoAPI.swift` (add notification endpoints)
- Modify: `/Users/mondo/Documents/git/sapphoios/Sappho/Models/Models.swift` (add notification models)
- Create: `/Users/mondo/Documents/git/sapphoios/Sappho/Presentation/Notifications/NotificationPanel.swift`

**What to build:**

1. **API**: Add to `SapphoAPI.swift`:
   - `getNotifications(limit:)` → `[NotificationItem]`
   - `getUnreadNotificationCount()` → `UnreadCount`
   - `markNotificationRead(id:)` → POST
   - `markAllNotificationsRead()` → POST

2. **Models**: `NotificationItem` (id, type, title, message, metadata, createdAt, isRead) with a computed `metadataDict` property that parses the JSON string. `UnreadCount` (count).

3. **Bell icon**: In the topBar HStack (line 155), add a bell button (`Image(systemName: "bell")`) before `avatarMenu`. Overlay a red badge circle with unread count (hidden when 0).

4. **Polling**: Use `.task { }` with a loop and `try await Task.sleep(for: .seconds(120))` to poll unread count. Also refresh on `scenePhase` becoming `.active`.

5. **NotificationPanel**: A `.sheet` or overlay view:
   - Header with title + "Mark all read" button
   - List of notifications with type icon (SF Symbols: `book` for new_audiobook, `folder` for collections), message, relative time, unread highlight
   - Tap to mark read and navigate
   - Empty state

6. **Xcode project**: Add `NotificationPanel.swift` to the project file following existing file patterns.

**Step 1: Implement following existing SwiftUI patterns**

**Step 2: Build and verify**

```bash
cd /Users/mondo/Documents/git/sapphoios
xcodebuild -project Sappho.xcodeproj -scheme Sappho -destination 'id=00008110-000C48680C9A201E' -derivedDataPath build build 2>&1 | tail -5
```

**Step 3: Commit**

```bash
git add Sappho/
git commit -m "feat: add notification bell icon and panel (iOS)"
```

---

### Task 8: PWA — Reviews UI on Detail Page

**Files:**
- Modify: `/Users/mondo/Documents/git/sappho/client/src/components/RatingSection.jsx` (add review input and reviews list)
- Modify: `/Users/mondo/Documents/git/sappho/client/src/api.js` (add getAllRatings, around line 362)

**What to build:**

1. **API**: Add `getAllRatings(audiobookId)` to `api.js` calling `GET /ratings/audiobook/${audiobookId}/all`.

2. **Review text input**: In `RatingSection.jsx`, after the star picker, add a `<textarea>` for review text and a "Submit" button. Pre-populate from user's existing review. Call existing `setRating(audiobookId, rating, review)`.

3. **Reviews list**: Below the rating section, fetch and display all reviews with: username/display_name, small star indicators, review text, relative date. Only show entries with non-empty reviews.

4. **Relative date**: Use a helper function.

5. **Styling**: Match existing dark theme CSS conventions.

**Step 1: Implement following existing React patterns**

Use `useState`/`useEffect`, existing CSS class naming conventions, dark theme colors.

**Step 2: Verify build**

```bash
cd /Users/mondo/Documents/git/sappho/client && npm run build
```

**Step 3: Commit**

```bash
git add client/src/
git commit -m "feat: add reviews UI to audiobook detail page (PWA)"
```

---

### Task 9: PWA — Bell Icon + Notification Panel

**Files:**
- Modify: `/Users/mondo/Documents/git/sappho/client/src/components/Navigation.jsx` (desktop avatar at line 269, mobile at line 232)
- Modify: `/Users/mondo/Documents/git/sappho/client/src/api.js` (add notification endpoints)
- Create: `/Users/mondo/Documents/git/sappho/client/src/components/NotificationPanel.jsx`
- Create: `/Users/mondo/Documents/git/sappho/client/src/components/NotificationPanel.css`

**What to build:**

1. **API**: Add to `api.js`:
   - `getNotifications(limit)`
   - `getUnreadNotificationCount()`
   - `markNotificationRead(id)`
   - `markAllNotificationsRead()`

2. **Bell icon**: In `Navigation.jsx`, add a bell icon button BEFORE the user-avatar-button (both desktop line 269 and mobile line 232). Red badge with unread count (hidden when 0). Click toggles notification panel.

3. **Polling**: Use `setInterval` (2 minutes) and `visibilitychange` event listener to poll unread count. Clean up on unmount.

4. **NotificationPanel component**: Dropdown panel styled like the user menu:
   - Header: "Notifications" + "Mark all read" link
   - Notification list: type icon, message, relative time, unread highlight
   - Click to mark read and navigate (`useNavigate` to `/audiobook/:id` or `/collection/:id`)
   - Click outside to close (follow user menu pattern)
   - Empty state
   - Dark theme CSS matching existing styles

**Step 1: Implement following existing React/CSS patterns**

**Step 2: Verify build**

```bash
cd /Users/mondo/Documents/git/sappho/client && npm run build
```

**Step 3: Commit**

```bash
git add client/src/
git commit -m "feat: add notification bell icon and panel (PWA)"
```

---

### Task 10: Version Bumps + Ship

**Files:**
- Modify: `/Users/mondo/Documents/git/sapphoapp/app/build.gradle.kts` (versionCode/versionName)
- Modify: `/Users/mondo/Documents/git/sapphoios/Sappho/Resources/Info.plist` (CFBundleVersion)

**Steps:**

1. Bump Android: `versionCode = 73`, `versionName = "0.9.55"`
2. Bump iOS: `CFBundleVersion` to `11`
3. Create branches, commit, push, open PRs for all three repos
4. Merge PRs after CI passes
5. Verify Android Play Store deploy succeeds
6. iOS: archive and upload to TestFlight (may need Xcode UI)

---

### Execution Order

Tasks 1-3 (server) must be done first — they create the API that clients depend on.

Tasks 4-9 (clients) can be done in any order after server tasks. Within each platform, the reviews task should be done before the notifications task (smaller scope first).

Task 10 (ship) is last.

Recommended parallel execution after server tasks:
- Android: Task 4 → Task 5
- iOS: Task 6 → Task 7
- PWA: Task 8 → Task 9
