# Notifications & Reviews Design

## Goal

Add an in-app notification system (global announcements) and surface the existing review/text capability alongside star ratings across all platforms (Android, iOS, PWA).

## Architecture

### Reviews

The server already has a `user_ratings` table with `rating` (1-5) and `review` (text) columns, plus full CRUD API endpoints. No schema changes needed. The work is entirely client-side: exposing the review text field when rating, and displaying all reviews on the detail page.

**Writing a review:**
- On the detail page, after selecting a star rating, a text field appears for the user to write a review
- Submitted via existing `POST /api/ratings/audiobook/:id` with `{ rating, review }` body
- Users can edit their own review by re-submitting, or delete via `DELETE /api/ratings/audiobook/:id`

**Reading reviews:**
- Below the rating section on the detail page, display all reviews for the book
- Each review shows: username, star rating, review text, relative date
- Fetched via existing `GET /api/ratings/audiobook/:id/all`
- Only your own review is editable/deletable

### Notifications

**Server-side (new):**

Two new database tables:

`notifications`:
- id INTEGER PRIMARY KEY
- type TEXT NOT NULL (new_audiobook, new_public_collection, collection_item_added)
- title TEXT NOT NULL
- message TEXT NOT NULL
- metadata TEXT (JSON — audiobook_id, collection_id, etc.)
- created_at DATETIME DEFAULT CURRENT_TIMESTAMP

`user_notification_reads`:
- user_id INTEGER NOT NULL
- notification_id INTEGER NOT NULL
- read_at DATETIME DEFAULT CURRENT_TIMESTAMP
- PRIMARY KEY (user_id, notification_id)

**API endpoints (new):**
- `GET /api/notifications` — list notifications with read/unread status for current user, paginated
- `GET /api/notifications/unread-count` — returns `{ count: N }` for badge display
- `POST /api/notifications/:id/read` — mark single notification as read
- `POST /api/notifications/read-all` — mark all as read for current user

**Notification generation triggers:**
- Library scan finds new audiobook(s) → one notification per new book
- User creates a public collection → one notification
- User adds a book to a public collection → one notification

**Client-side (all platforms):**

Bell icon in top bar, next to avatar:
- Red badge with unread count (hidden when 0)
- Tapping opens a notification panel (bottom sheet on mobile, dropdown on PWA)
- Each notification: icon (type-based), message, relative timestamp
- Unread notifications have a highlight/accent bar
- "Mark all as read" button at top of panel
- Tapping a notification marks it read and navigates to the relevant book or collection

**Polling:**
- Check unread count on app open
- Poll every 2-3 minutes while app is active
- Full notification list fetched when panel is opened

## Tech Stack

- Server: Node.js/Express, SQLite (new migration for tables)
- Android: Jetpack Compose, Retrofit
- iOS: SwiftUI, URLSession
- PWA: React, existing fetch utilities

## Scope

1. Server: migration + API routes + notification generation hooks
2. Android: reviews UI on detail page + bell icon + notification panel
3. iOS: reviews UI on detail page + bell icon + notification panel
4. PWA: reviews UI on detail page + bell icon + notification panel

## Separate task (not part of this design)

iOS detail view fixes:
- Remove title from detail view
- Move About section to top
- Add catch up / recap button
