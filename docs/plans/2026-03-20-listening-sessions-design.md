# Listening Sessions Feature — Design

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add per-book listening session history to all three apps (PWA, Android, iOS), accessible from a "History" button in the max player.

**Architecture:** New server-side `listening_sessions` table logs each play/stop event. New API endpoint returns sessions per book. All three clients add a History button + sheet to the player.

**Tech Stack:** SQLite (server), Express routes (PWA), Jetpack Compose (Android), SwiftUI (iOS)

---

## Server

### Database: `listening_sessions` table

```sql
CREATE TABLE IF NOT EXISTS listening_sessions (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  user_id INTEGER NOT NULL,
  audiobook_id INTEGER NOT NULL,
  started_at DATETIME NOT NULL,
  stopped_at DATETIME,
  start_position INTEGER NOT NULL DEFAULT 0,
  end_position INTEGER,
  device_name TEXT,
  FOREIGN KEY (user_id) REFERENCES users(id),
  FOREIGN KEY (audiobook_id) REFERENCES audiobooks(id)
);
CREATE INDEX idx_listening_sessions_user_book ON listening_sessions(user_id, audiobook_id);
CREATE INDEX idx_listening_sessions_started ON listening_sessions(started_at);
```

### API Endpoints

**GET `/api/audiobooks/:id/sessions`**
- Auth required
- Query params: `limit` (default 50), `offset` (default 0)
- Returns: `{ sessions: [{ id, startedAt, stoppedAt, startPosition, endPosition, deviceName }] }`
- Ordered by `started_at DESC`

**POST `/api/audiobooks/:id/sessions`**
- Auth required
- Body: `{ action: "start" | "stop", position: number, deviceName?: string }`
- On "start": creates new session row
- On "stop": updates most recent open session with end data

## PWA Client

- New "History" button in player controls bar (bottom row, after Sleep Timer)
- Opens a modal with vertical timeline list
- Each entry: date/time, start->end position, duration
- Clicking an entry seeks to that position

## Android Client

- New "History" button in secondary controls row (after Sleep Timer)
- Icon: `Icons.Default.History`
- Opens bottom sheet with timeline
- Tapping an entry seeks to startPosition

## iOS Client

- New "History" button in secondary controls HStack (after Sleep Timer)
- SF Symbol: `clock.arrow.circlepath`
- Opens sheet with timeline list
- Tapping an entry seeks to startPosition

## Timeline Entry UI (all platforms)

```
Mar 20, 9:15 AM
1:23:45 -> 2:01:12  (37 min)
```

- Most recent session at top
- Entries with no stopped_at show as "In progress"
- Tapping seeks to startPosition
