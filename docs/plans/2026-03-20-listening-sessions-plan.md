# Listening Sessions Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Track per-book listening sessions (start/stop timestamps + positions) and surface them in a "History" button on all three app players, with tap-to-seek.

**Architecture:** New `listening_sessions` table created via migration #033. Server creates/closes sessions automatically when the existing progress endpoint receives play/stop state changes. New GET endpoint returns sessions per book. All three clients add a History button to their player's secondary controls row.

**Tech Stack:** SQLite + Express (server), React (PWA), Jetpack Compose + Retrofit (Android), SwiftUI + URLSession (iOS)

---

### Task 1: Server Migration — `listening_sessions` Table

**Files:**
- Create: `server/migrations/033_add_listening_sessions.js`

**Step 1: Write the migration file**

```javascript
function runSql(db, sql, params = []) {
  return new Promise((resolve, reject) => {
    db.run(sql, params, function(err) {
      if (err) reject(err);
      else resolve(this);
    });
  });
}

async function up(db) {
  await runSql(db, `
    CREATE TABLE IF NOT EXISTS listening_sessions (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      user_id INTEGER NOT NULL,
      audiobook_id INTEGER NOT NULL,
      started_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
      stopped_at DATETIME,
      start_position INTEGER NOT NULL DEFAULT 0,
      end_position INTEGER,
      device_name TEXT,
      FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
      FOREIGN KEY (audiobook_id) REFERENCES audiobooks(id) ON DELETE CASCADE
    )
  `);

  await runSql(db, `
    CREATE INDEX IF NOT EXISTS idx_listening_sessions_user_book
    ON listening_sessions(user_id, audiobook_id)
  `);

  await runSql(db, `
    CREATE INDEX IF NOT EXISTS idx_listening_sessions_started
    ON listening_sessions(started_at)
  `);

  console.log('Added listening_sessions table');
}

async function down(db) {
  await runSql(db, 'DROP TABLE IF EXISTS listening_sessions');
  console.log('Removed listening_sessions table');
}

module.exports = { up, down };
```

**Step 2: Verify migration runs**

Run: `cd /Users/mondo/Documents/git/sappho && node -e "const m = require('./server/migrations/033_add_listening_sessions.js'); console.log(typeof m.up, typeof m.down)"`
Expected: `function function`

**Step 3: Commit**

```bash
git add server/migrations/033_add_listening_sessions.js
git commit -m "feat: add listening_sessions migration (#033)"
```

---

### Task 2: Server API — Sessions Route Module

**Files:**
- Create: `server/routes/audiobooks/sessions.js`
- Modify: `server/routes/audiobooks/index.js` (register new module)

**Step 1: Check if `dbAll` exists in db helpers**

Run: `grep -n 'dbAll' server/utils/db.js`

If `dbAll` doesn't exist, add it to the `createDbHelpers` return object:

```javascript
function dbAll(sql, params = []) {
  return new Promise((resolve, reject) => {
    db.all(sql, params, (err, rows) => {
      if (err) reject(err);
      else resolve(rows || []);
    });
  });
}
```

**Step 2: Write the sessions route module**

Create `server/routes/audiobooks/sessions.js` following the exact same pattern as `server/routes/audiobooks/progress.js`:

```javascript
/**
 * Listening sessions route handlers
 * GET/POST endpoints for per-book listening session history.
 */
const { createDbHelpers } = require('../../utils/db');

function register(router, { db, authenticateToken }) {
  const { dbGet, dbRun, dbAll } = createDbHelpers(db);

  // Get listening sessions for an audiobook
  router.get('/:id/sessions', authenticateToken, async (req, res) => {
    const audiobookId = req.params.id;
    const userId = req.user.id;
    const limit = parseInt(req.query.limit) || 50;
    const offset = parseInt(req.query.offset) || 0;

    try {
      const sessions = await dbAll(
        `SELECT id, started_at, stopped_at, start_position, end_position, device_name
         FROM listening_sessions
         WHERE user_id = ? AND audiobook_id = ?
         ORDER BY started_at DESC
         LIMIT ? OFFSET ?`,
        [userId, audiobookId, limit, offset]
      );

      res.json({ sessions });
    } catch (_err) {
      res.status(500).json({ error: 'Internal server error' });
    }
  });

  // Start or stop a listening session
  router.post('/:id/sessions', authenticateToken, async (req, res) => {
    const audiobookId = req.params.id;
    const userId = req.user.id;
    const { action, position, deviceName } = req.body;

    if (!action || !['start', 'stop'].includes(action)) {
      return res.status(400).json({ error: 'action must be "start" or "stop"' });
    }
    if (position == null || typeof position !== 'number') {
      return res.status(400).json({ error: 'position is required and must be a number' });
    }

    try {
      if (action === 'start') {
        // Close any open session for this user+book first
        await dbRun(
          `UPDATE listening_sessions
           SET stopped_at = CURRENT_TIMESTAMP, end_position = ?
           WHERE user_id = ? AND audiobook_id = ? AND stopped_at IS NULL`,
          [Math.floor(position), userId, audiobookId]
        );

        // Create new session
        const result = await dbRun(
          `INSERT INTO listening_sessions (user_id, audiobook_id, start_position, device_name)
           VALUES (?, ?, ?, ?)`,
          [userId, audiobookId, Math.floor(position), deviceName || null]
        );

        res.json({ id: result.lastID, message: 'Session started' });
      } else {
        // Stop: update most recent open session
        const updated = await dbRun(
          `UPDATE listening_sessions
           SET stopped_at = CURRENT_TIMESTAMP, end_position = ?
           WHERE user_id = ? AND audiobook_id = ? AND stopped_at IS NULL`,
          [Math.floor(position), userId, audiobookId]
        );

        res.json({ message: 'Session stopped', updated: updated.changes > 0 });
      }
    } catch (_err) {
      res.status(500).json({ error: 'Internal server error' });
    }
  });
}

module.exports = { register };
```

**Step 3: Register sessions module in index.js**

In `server/routes/audiobooks/index.js`:

1. Add import at top with other modules (after line 7): `const sessions = require('./sessions');`
2. Add registration call after progress (after line 79): `sessions.register(router, sharedDeps);  // /:id/sessions`

**Step 4: Commit**

```bash
git add server/routes/audiobooks/sessions.js server/routes/audiobooks/index.js
# Also add server/utils/db.js if modified
git commit -m "feat: add listening sessions API endpoints (GET/POST)"
```

---

### Task 3: Server Integration — Auto-Create Sessions on Progress Updates

**Files:**
- Modify: `server/routes/audiobooks/progress.js` (lines 75-164, the POST handler)

**Step 1: Add session tracking to the POST progress handler**

In `server/routes/audiobooks/progress.js`, inside the `router.post('/:id/progress', ...)` handler, after the progress DB update (around line 98) and before the session manager code (line 106), add:

```javascript
      // Track listening session (fire-and-forget)
      try {
        if (state === 'playing') {
          // Check if there's already an open session
          const openSession = await dbGet(
            `SELECT id FROM listening_sessions WHERE user_id = ? AND audiobook_id = ? AND stopped_at IS NULL`,
            [userId, audiobookId]
          );
          if (!openSession) {
            await dbRun(
              `INSERT INTO listening_sessions (user_id, audiobook_id, start_position, device_name)
               VALUES (?, ?, ?, ?)`,
              [userId, audiobookId, Math.floor(position), clientInfo.name || 'Unknown']
            );
          }
        } else if (state === 'stopped' || state === 'paused' || completed) {
          await dbRun(
            `UPDATE listening_sessions
             SET stopped_at = CURRENT_TIMESTAMP, end_position = ?
             WHERE user_id = ? AND audiobook_id = ? AND stopped_at IS NULL`,
            [Math.floor(position), userId, audiobookId]
          );
        }
      } catch (_sessionErr) {
        // Don't fail progress updates if session tracking errors
        // Table may not exist if migration hasn't run yet
      }
```

**Important:** Wrap in try/catch so session tracking failures never break progress saving.

**Step 2: Run tests**

Run: `cd /Users/mondo/Documents/git/sappho && npm test`
Expected: All existing tests pass, new session tracking doesn't break anything.

**Step 3: Commit**

```bash
git add server/routes/audiobooks/progress.js
git commit -m "feat: auto-create listening sessions on progress updates"
```

---

### Task 4: Server Tests — Sessions API

**Files:**
- Create: `tests/unit/listeningSessions.test.js`

**Step 1: Write tests for the sessions endpoints**

Tests should cover:
1. GET /:id/sessions returns empty array when no sessions
2. POST /:id/sessions with action:"start" creates a session
3. POST /:id/sessions with action:"stop" closes the open session
4. GET /:id/sessions returns sessions ordered by started_at DESC
5. Starting a new session auto-closes any open session
6. POST with invalid action returns 400
7. POST without position returns 400
8. Sessions are scoped to user (user A can't see user B's sessions)
9. Pagination works (limit and offset)

Use the same test patterns as existing route tests. Check `tests/unit/` for test helpers and database setup patterns.

**Step 2: Run tests**

Run: `cd /Users/mondo/Documents/git/sappho && npx jest tests/unit/listeningSessions.test.js --verbose`
Expected: All tests pass.

**Step 3: Commit**

```bash
git add tests/unit/listeningSessions.test.js
git commit -m "test: add listening sessions API tests"
```

---

### Task 5: PWA Client — History Button + Modal

**Files:**
- Modify: `client/src/components/player/FullscreenPlayer.jsx` (add History button to bottom bar, line 392)
- Create: `client/src/components/player/ListeningHistoryModal.jsx`
- Modify: `client/src/api.js` (add sessions API call)

**Step 1: Add API helper**

Check `client/src/api.js` for the pattern used by other API calls. Add:

```javascript
export async function getListeningSessions(audiobookId, limit = 50) {
  const response = await apiFetch(`/api/audiobooks/${audiobookId}/sessions?limit=${limit}`);
  return response.sessions;
}
```

**Step 2: Create ListeningHistoryModal component**

Create `client/src/components/player/ListeningHistoryModal.jsx`:

```jsx
import { useState, useEffect } from 'react';
import { getListeningSessions } from '../../api';

function formatPosition(seconds) {
  const h = Math.floor(seconds / 3600);
  const m = Math.floor((seconds % 3600) / 60);
  const s = Math.floor(seconds % 60);
  if (h > 0) return `${h}:${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}`;
  return `${m}:${s.toString().padStart(2, '0')}`;
}

function formatDate(dateStr) {
  const d = new Date(dateStr);
  return d.toLocaleDateString(undefined, { month: 'short', day: 'numeric' }) +
    ', ' + d.toLocaleTimeString(undefined, { hour: 'numeric', minute: '2-digit' });
}

export default function ListeningHistoryModal({ audiobookId, onSeek, onClose }) {
  const [sessions, setSessions] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    async function load() {
      try {
        const data = await getListeningSessions(audiobookId);
        if (!cancelled) setSessions(data);
      } catch (_err) {
        // ignore
      } finally {
        if (!cancelled) setLoading(false);
      }
    }
    load();
    return () => { cancelled = true; };
  }, [audiobookId]);

  return (
    <div className="listening-history-modal" onClick={onClose}>
      <div className="listening-history-content" onClick={e => e.stopPropagation()}>
        <div className="listening-history-header">
          <h3>Listening History</h3>
          <button className="listening-history-close" onClick={onClose} aria-label="Close">&times;</button>
        </div>
        <div className="listening-history-list">
          {loading && <p className="listening-history-empty">Loading...</p>}
          {!loading && sessions.length === 0 && (
            <p className="listening-history-empty">No listening history yet</p>
          )}
          {sessions.map(session => {
            const duration = session.stopped_at && session.end_position != null
              ? session.end_position - session.start_position
              : null;
            return (
              <button
                key={session.id}
                className="listening-history-entry"
                onClick={() => { onSeek(session.start_position); onClose(); }}
              >
                <div className="listening-history-date">{formatDate(session.started_at)}</div>
                <div className="listening-history-positions">
                  {formatPosition(session.start_position)}
                  {session.end_position != null ? ` → ${formatPosition(session.end_position)}` : ' → In progress'}
                  {duration != null && ` (${Math.round(duration / 60)} min)`}
                </div>
                {session.device_name && (
                  <div className="listening-history-device">{session.device_name}</div>
                )}
              </button>
            );
          })}
        </div>
      </div>
    </div>
  );
}
```

**Step 3: Add History button to FullscreenPlayer.jsx bottom bar**

In `client/src/components/player/FullscreenPlayer.jsx`:

1. Add import: `import ListeningHistoryModal from './ListeningHistoryModal';`
2. Add state: `const [showHistory, setShowHistory] = useState(false);` (inside component)
3. Add the History button AFTER the Sleep Timer button (line 392, before the closing `</div>` of `fullscreen-bottom-bar`):

```jsx
        <button
          className="fullscreen-bottom-btn"
          onClick={() => setShowHistory(true)}
          aria-label="Listening history"
        >
          <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
            <path d="M3 12a9 9 0 1 0 9-9 9.75 9.75 0 0 0-6.74 2.74L3 8"/>
            <path d="M3 3v5h5"/>
            <path d="M12 7v5l4 2"/>
          </svg>
          <span>History</span>
        </button>
```

4. Add the modal render (before the closing `</div>` of `fullscreen-player`):

```jsx
      {showHistory && (
        <ListeningHistoryModal
          audiobookId={audiobook.id}
          onSeek={onSeek}
          onClose={() => setShowHistory(false)}
        />
      )}
```

The `onSeek` prop is already available in FullscreenPlayer.

**Step 4: Add CSS for the modal**

Find the CSS file for the player (likely `client/src/styles/` or same-directory CSS). Add styles for `.listening-history-modal` following the pattern of existing modals (chapter modal). Key styles:
- Modal overlay: fixed position, dark backdrop
- Content: scrollable list, max-height 60vh
- Entries: clickable, hover state, padding

**Step 5: Commit**

```bash
git add client/src/components/player/ListeningHistoryModal.jsx \
        client/src/components/player/FullscreenPlayer.jsx \
        client/src/api.js
# Also add CSS file if modified
git commit -m "feat(pwa): add listening history button and modal to player"
```

---

### Task 6: Android Client — History Button + Dialog

**Files:**
- Create: `app/src/main/java/com/sappho/audiobooks/domain/model/ListeningSession.kt`
- Modify: `app/src/main/java/com/sappho/audiobooks/data/remote/SapphoApi.kt`
- Modify: `app/src/main/java/com/sappho/audiobooks/presentation/player/PlayerActivity.kt`

**Step 1: Add data class**

Create `app/src/main/java/com/sappho/audiobooks/domain/model/ListeningSession.kt`:

```kotlin
package com.sappho.audiobooks.domain.model

import com.google.gson.annotations.SerializedName

data class ListeningSession(
    val id: Int,
    @SerializedName("started_at") val startedAt: String,
    @SerializedName("stopped_at") val stoppedAt: String?,
    @SerializedName("start_position") val startPosition: Int,
    @SerializedName("end_position") val endPosition: Int?,
    @SerializedName("device_name") val deviceName: String?
)

data class ListeningSessionsResponse(
    val sessions: List<ListeningSession>
)
```

**Step 2: Add API endpoint to SapphoApi.kt**

After the `getProgress` endpoint (around line 88), add:

```kotlin
    @GET("api/audiobooks/{id}/sessions")
    suspend fun getListeningSessions(
        @Path("id") audiobookId: Int,
        @Query("limit") limit: Int = 50
    ): Response<ListeningSessionsResponse>
```

**Step 3: Add History button to PlayerActivity.kt secondary controls Row**

In the secondary controls Row (lines 794-894), after the Sleep Timer Box (line 893), before the closing `}` of the Row, add a 4th button following the exact same Box/Column/Icon/Text pattern:

```kotlin
                        // History button
                        val historyInteractionSource = remember { MutableInteractionSource() }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clickable(
                                    interactionSource = historyInteractionSource,
                                    indication = null
                                ) { showHistory = !showHistory }
                                .padding(vertical = 12.dp, horizontal = 12.dp)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.History,
                                    contentDescription = "Listening history",
                                    modifier = Modifier.size(24.dp),
                                    tint = SapphoTextSecondary
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "History",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.White
                                )
                            }
                        }
```

**Step 4: Add state variables and dialog**

Near other `remember` states, add:

```kotlin
var showHistory by remember { mutableStateOf(false) }
var listeningSessions by remember { mutableStateOf<List<ListeningSession>>(emptyList()) }
var historyLoading by remember { mutableStateOf(false) }
```

Add a LaunchedEffect to load sessions when dialog opens:

```kotlin
LaunchedEffect(showHistory) {
    if (showHistory) {
        historyLoading = true
        try {
            val response = api.getListeningSessions(audiobook.id)
            if (response.isSuccessful) {
                listeningSessions = response.body()?.sessions ?: emptyList()
            }
        } catch (_: Exception) { }
        historyLoading = false
    }
}
```

Add the dialog (after existing dialogs like chapters dialog):

```kotlin
if (showHistory) {
    AlertDialog(
        onDismissRequest = { showHistory = false },
        title = { Text("Listening History", color = Color.White) },
        containerColor = SapphoSurface,
        text = {
            if (historyLoading) {
                Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = SapphoPrimary)
                }
            } else if (listeningSessions.isEmpty()) {
                Text("No listening history yet", color = SapphoTextSecondary)
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                    items(listeningSessions) { session ->
                        ListeningSessionItem(session) { position ->
                            viewModel.seekTo(position * 1000L)
                            showHistory = false
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { showHistory = false }) {
                Text("Close", color = SapphoPrimary)
            }
        }
    )
}
```

Add a private composable for session items:

```kotlin
@Composable
private fun ListeningSessionItem(
    session: ListeningSession,
    onSeek: (Int) -> Unit
) {
    val dateFormat = remember { java.text.SimpleDateFormat("MMM d, h:mm a", java.util.Locale.getDefault()) }
    val parseFormat = remember { java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.getDefault()) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSeek(session.startPosition) }
            .padding(vertical = 8.dp)
    ) {
        Text(
            text = try { dateFormat.format(parseFormat.parse(session.startedAt)!!) } catch (_: Exception) { session.startedAt },
            style = MaterialTheme.typography.labelMedium,
            color = SapphoTextSecondary
        )
        val startFormatted = formatSessionSeconds(session.startPosition)
        val endFormatted = session.endPosition?.let { formatSessionSeconds(it) } ?: "In progress"
        val duration = session.endPosition?.let { (it - session.startPosition) / 60 }
        Text(
            text = "$startFormatted → $endFormatted" + (duration?.let { " ($it min)" } ?: ""),
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White
        )
        session.deviceName?.let {
            Text(text = it, style = MaterialTheme.typography.labelSmall, color = SapphoTextSecondary)
        }
        Divider(color = SapphoSurface.copy(alpha = 0.5f), modifier = Modifier.padding(top = 8.dp))
    }
}

private fun formatSessionSeconds(totalSeconds: Int): String {
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) String.format("%d:%02d:%02d", h, m, s)
    else String.format("%d:%02d", m, s)
}
```

**Step 5: Add import for Icons.Default.History**

Ensure `import androidx.compose.material.icons.filled.History` is present (it's part of `material-icons-extended`). Check if it's already a dependency in `build.gradle.kts`.

**Step 6: Build**

Run: `./gradlew assembleDebug`
Expected: Build succeeds.

**Step 7: Commit**

```bash
git add app/src/main/java/com/sappho/audiobooks/domain/model/ListeningSession.kt \
        app/src/main/java/com/sappho/audiobooks/data/remote/SapphoApi.kt \
        app/src/main/java/com/sappho/audiobooks/presentation/player/PlayerActivity.kt
git commit -m "feat(android): add listening history button and dialog to player"
```

---

### Task 7: iOS Client — History Button + Sheet

**Files:**
- Create: `Sappho/Domain/Model/ListeningSession.swift`
- Create: `Sappho/Presentation/Player/ListeningHistorySheet.swift`
- Modify: `Sappho/Data/Remote/SapphoAPI.swift`
- Modify: `Sappho/Presentation/Player/PlayerView.swift`

**Step 1: Add model**

Create `Sappho/Domain/Model/ListeningSession.swift`:

```swift
import Foundation

struct ListeningSession: Codable, Identifiable {
    let id: Int
    let startedAt: String
    let stoppedAt: String?
    let startPosition: Int
    let endPosition: Int?
    let deviceName: String?

    enum CodingKeys: String, CodingKey {
        case id
        case startedAt = "started_at"
        case stoppedAt = "stopped_at"
        case startPosition = "start_position"
        case endPosition = "end_position"
        case deviceName = "device_name"
    }
}

struct ListeningSessionsResponse: Codable {
    let sessions: [ListeningSession]
}
```

**Step 2: Add API method to SapphoAPI.swift**

After the existing audiobook methods, add:

```swift
func getListeningSessions(audiobookId: Int, limit: Int = 50) async throws -> [ListeningSession] {
    let response: ListeningSessionsResponse = try await request(
        path: "/api/audiobooks/\(audiobookId)/sessions",
        queryItems: [URLQueryItem(name: "limit", value: String(limit))]
    )
    return response.sessions
}
```

**Step 3: Create ListeningHistorySheet**

Create `Sappho/Presentation/Player/ListeningHistorySheet.swift`:

```swift
import SwiftUI

struct ListeningHistorySheet: View {
    let audiobookId: Int
    let onSeek: (Int) -> Void
    @Environment(\.dismiss) private var dismiss
    @Environment(\.sapphoAPI) private var api
    @State private var sessions: [ListeningSession] = []
    @State private var isLoading = true

    var body: some View {
        NavigationStack {
            Group {
                if isLoading {
                    ProgressView()
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                } else if sessions.isEmpty {
                    Text("No listening history yet")
                        .foregroundColor(.sapphoTextSecondary)
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                } else {
                    List(sessions) { session in
                        Button {
                            onSeek(session.startPosition)
                            dismiss()
                        } label: {
                            VStack(alignment: .leading, spacing: 4) {
                                Text(formatSessionDate(session.startedAt))
                                    .font(.sapphoSmall)
                                    .foregroundColor(.sapphoTextSecondary)
                                HStack(spacing: 4) {
                                    Text(formatPosition(session.startPosition))
                                    Text("→")
                                        .foregroundColor(.sapphoTextSecondary)
                                    if let end = session.endPosition {
                                        Text(formatPosition(end))
                                        let duration = (end - session.startPosition) / 60
                                        Text("(\(duration) min)")
                                            .foregroundColor(.sapphoTextSecondary)
                                    } else {
                                        Text("In progress")
                                            .foregroundColor(.sapphoWarning)
                                    }
                                }
                                .font(.sapphoBody)
                                .foregroundColor(.sapphoTextHigh)

                                if let device = session.deviceName {
                                    Text(device)
                                        .font(.sapphoSmall)
                                        .foregroundColor(.sapphoTextSecondary)
                                }
                            }
                            .padding(.vertical, 4)
                        }
                        .listRowBackground(Color.sapphoSurface)
                    }
                    .listStyle(.plain)
                }
            }
            .navigationTitle("Listening History")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Done") { dismiss() }
                }
            }
            .background(Color.sapphoBackground)
        }
        .task {
            do {
                sessions = try await api.getListeningSessions(audiobookId: audiobookId)
            } catch { }
            isLoading = false
        }
    }

    private func formatPosition(_ totalSeconds: Int) -> String {
        let h = totalSeconds / 3600
        let m = (totalSeconds % 3600) / 60
        let s = totalSeconds % 60
        if h > 0 { return String(format: "%d:%02d:%02d", h, m, s) }
        return String(format: "%d:%02d", m, s)
    }

    private func formatSessionDate(_ dateStr: String) -> String {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        guard let date = formatter.date(from: dateStr) ?? ISO8601DateFormatter().date(from: dateStr) else {
            return dateStr
        }
        let display = DateFormatter()
        display.dateFormat = "MMM d, h:mm a"
        return display.string(from: date)
    }
}
```

**Step 4: Add History button to PlayerView.swift**

In the secondary controls HStack (lines 286-366), after the Sleep Timer button (line 365) and before the closing `}` of the HStack, add:

```swift
                            // History
                            Button {
                                showHistory = true
                            } label: {
                                VStack(spacing: 6) {
                                    Image(systemName: "clock.arrow.circlepath")
                                        .font(.sapphoIconSmall)
                                        .foregroundColor(.sapphoTextSecondary)
                                    Text("History")
                                        .font(.sapphoSmall)
                                        .foregroundColor(.sapphoTextHigh)
                                }
                                .frame(maxWidth: .infinity, alignment: .center)
                                .contentShape(Rectangle())
                                .padding(.vertical, 12)
                            }
                            .buttonStyle(.plain)
                            .accessibilityLabel("Listening history")
                            .accessibilityHint("Double tap to view listening history")
```

Add state variable near other `@State` properties:

```swift
@State private var showHistory = false
```

Add sheet presentation (near other `.sheet` modifiers):

```swift
.sheet(isPresented: $showHistory) {
    ListeningHistorySheet(audiobookId: audiobook.id) { position in
        Task {
            await audioPlayer.seek(to: Double(position))
        }
    }
    .presentationDetents([.medium, .large])
}
```

**Step 5: Add new files to Xcode project**

Since this is a manual Xcode project (no SPM), new Swift files need to be added to `Sappho.xcodeproj/project.pbxproj`. Use a script or Xcode to add:
- `Sappho/Domain/Model/ListeningSession.swift`
- `Sappho/Presentation/Player/ListeningHistorySheet.swift`

**Step 6: Build**

Run: `cd /Users/mondo/Documents/git/sapphoios && xcodebuild -project Sappho.xcodeproj -scheme Sappho -destination 'platform=iOS Simulator,name=iPhone 17 Pro' build`
Expected: Build succeeds.

**Step 7: Commit**

```bash
git add Sappho/Domain/Model/ListeningSession.swift \
        Sappho/Data/Remote/SapphoAPI.swift \
        Sappho/Presentation/Player/PlayerView.swift \
        Sappho/Presentation/Player/ListeningHistorySheet.swift \
        Sappho.xcodeproj/project.pbxproj
git commit -m "feat(ios): add listening history button and sheet to player"
```

---

### Task 8: Deploy and Test

**Step 1: Deploy server**

Push server changes. Verify migration ran:

```bash
ssh root@192.168.86.151 "docker logs sappho --tail 50 | grep -i listening"
```

**Step 2: Install Android app**

```bash
cd /Users/mondo/Documents/git/sapphoapp && ./gradlew installDebug
```

**Step 3: Install iOS app on device**

```bash
cd /Users/mondo/Documents/git/sapphoios
xcodebuild -project Sappho.xcodeproj -scheme Sappho -destination 'id=00008101-000955A61A8B001E' -derivedDataPath build -allowProvisioningUpdates
xcrun devicectl device install app --device 00008101-000955A61A8B001E build/Build/Products/Debug-iphoneos/Sappho.app
```

**Step 4: User testing on all platforms**

Verify:
- Play a book for 30+ seconds, pause/stop
- Open History in max player
- See session entry with correct timestamps and positions
- Tap entry — player seeks to start position
- Multiple sessions appear correctly
- Cross-device sessions appear (play on PWA, see in Android/iOS)
