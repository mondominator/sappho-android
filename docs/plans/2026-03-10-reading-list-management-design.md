# Enhanced Reading List Management

**Issue:** #156
**Date:** 2026-03-10

## Summary

Transform the reading list from a simple grid of covers into a numbered, reorderable list with drag-and-drop, swipe-to-remove, and sort options. The server already supports ordering, priority, and reorder APIs — this is a client-side implementation.

## Design

### Screen Layout

Numbered vertical list replacing the current grid. Each row:

```
[#1]  [Cover Thumb]  Title                    [≡ drag handle]
                     Author · 12h 30m
```

- Header: "Reading List (N)" with sort dropdown
- Numbers reflect current position in the list
- Progress bar on cover thumbnail (same as existing)
- Tap row → navigate to audiobook detail

### Interactions

- **Drag-and-drop reorder**: Grab the ≡ handle to reorder. On drop, sync new order to server via `PUT /api/audiobooks/favorites/reorder`
- **Swipe-to-remove**: Swipe left to reveal remove action. Calls `DELETE /api/audiobooks/:id/favorite` or toggle endpoint
- **Sort options**: Custom Order (default), Title, Author, Date Added — maps to server `sort` query param (`custom`, `title`, `date`)

### API Changes (SapphoApi.kt)

- `getFavorites()` → add `@Query("sort") sort: String` parameter
- Add `reorderFavorites(@Body request: ReorderFavoritesRequest): Response<Unit>` for `PUT /api/audiobooks/favorites/reorder`
- Add `removeFavorite(id: Int)` for `DELETE /api/audiobooks/:id/favorite`

### Dependencies

- `sh.calvin.reorderable` library for Compose LazyList drag-and-drop

### What stays the same

- Library home card for Reading List (navigates to screen)
- Bookmark toggle on audiobook detail screen
- Batch add/remove from All Books view
- Reading list ribbon on book covers throughout the app

### Not in scope

- Priority tiers (High/Medium/Low) — position = priority
- Bulk select/edit mode — swipe-to-remove is sufficient
- Categories/smart suggestions (Phase 3 of issue)
