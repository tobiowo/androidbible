# Markers Module (Bookmarks, Notes, Highlights)

## Overview

"Markers" is the unified system for bookmarks, notes, and highlights. All three share the same `Marker` database table, distinguished by a `kind` column. Markers can be organized with labels (categories) and synced across devices.

## Key Files

- `Alkitab/src/main/java/yuku/alkitab/base/ac/MarkerListActivity.kt` — Filtered list of markers with search and sorting
- `Alkitab/src/main/java/yuku/alkitab/base/ac/MarkersActivity.java` — Label management (create, rename, delete, reorder via `ItemTouchHelper` drag)
- `Alkitab/src/main/java/yuku/alkitab/base/ac/NoteActivity.java` — Note editor with verse link detection
- `Alkitab/src/main/java/yuku/alkitab/base/util/Highlights.kt` — JSON-based highlight encoding
- `Alkitab/src/main/java/yuku/alkitab/base/widget/AttributeView.java` — Icon drawing for bookmark/note/highlight indicators
- `Alkitab/src/main/java/yuku/alkitab/base/verses/VersesAttributes.kt` — Per-verse attribute maps
- `Alkitab/src/main/java/yuku/alkitab/base/verses/VerseAttributeLoader.kt` — Loads attributes from DB and content providers
- `Alkitab/src/main/java/yuku/alkitab/base/storage/MarkerDao.kt` / `LabelDao.kt` / `Marker_LabelDao.kt` — Per-table DAOs issuing raw SQL against the legacy `AlkitabDb` (`InternalDbHelper`)

## Marker Model

```
Marker {
  _id: long           — local database ID
  gid: String         — globally unique ID for sync
  ari: int            — verse reference (ARI encoding)
  kind: int           — 0=bookmark, 1=note, 2=highlight
  caption: String     — display text (bookmark title / note content / highlight color info)
  verseCount: int     — number of verses covered
  createTime: Date
  modifyTime: Date
}
```

Storage: the `Marker`, `Label`, and `Marker_Label` tables live in the legacy `AlkitabDb` SQLite file managed by `InternalDbHelper`. The `MarkerDao` / `LabelDao` / `Marker_LabelDao` classes expose a `Marker` / `Label` / `Marker_Label` model surface for callers and issue raw SQL internally.

## Labels

Labels are user-created categories for organizing bookmarks. Each label has a `title`, `ordering`, and `backgroundColor`. The `marker_label` junction table creates many-to-many relationships. Labels also have GIDs for sync.

## Highlights

Highlights use a JSON encoding in the `caption` field supporting:
- Full-verse highlights (solid color)
- Partial highlights (character range within a verse)
- Hash-based verification to detect when verse text has changed

The `Highlights` utility handles encoding/decoding and color management. A highlight stores the RGB the user picked, but is not drawn in that color. `Highlights.bandColor()` keeps only its hue and takes lightness and chroma from the reading background: soft pastels on a light page, mid-tone bands on a dark one. `TextColorUtil.getForHighlight()` then keeps the reading color on the band when it stays at 4.5:1 contrast, and otherwise switches to black or white, which in practice means black text on night-mode bands. Because the stored color is unchanged, other installs that sync the same highlight can draw it their own way.

`TypeHighlightDialog` offers six presets (yellow, orange, pink, purple, teal, green) chosen to stay distinct once drawn as bands. Only hue survives into the band, and pastel blue and violet look alike, so there is no separate blue. Highlights stored in any other color, from older presets or the custom picker, still draw through `bandColor()`.

Color selection in `TypeHighlightDialog` and the label color editor uses the iOS-style Compose color picker at `yuku.alkitab.base.compose.colorpicker.IosColorPicker` / `ColorPickerDialog`, hosted inside a `ModalBottomSheet`.

## Attribute Display

`VerseAttributeLoader` aggregates bookmark count, note count, highlight color, progress mark presence, and map availability for each verse in a chapter. These are displayed as small icons in `AttributeView` next to each verse.

## Sorting Options

MarkerListActivity supports sorting by:
- `createTime` — when the marker was first created
- `modifyTime` — when last edited
- `ari` — Bible order (by verse reference)
- `caption` — alphabetical by content

## Verse Link Detection

`NoteActivity` uses `DesktopVerseFinder` in the app’s `yuku.alkitabconverter.util` package to detect verse references in note text and convert them to clickable links that navigate to the referenced verse.
