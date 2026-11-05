# Kee Reads 0.2.0 — UI redesign

## Replaced the first UI

- Removed the two horizontally-scrolling rows of tiny toolbar buttons.
- Fixed document header with readable file title, Open and document options.
- Fixed page navigation with previous, numbered page indicator, next, Find, Pages and Bookmark.
- Full-screen-first PDF canvas with unobtrusive floating zoom controls.
- Persistent five-destination bottom bar: Read, Highlight, Pen, Text and More.
- Context-sensitive color palette and brush settings appear only in annotation modes.
- More opens a bottom tool sheet with underline, squiggle, eraser, selection, text selection, colors, brush, notes, undo/redo, flashcards and bookmarks.
- Reader options opens a separate bottom sheet with Open, Export, Pages, Search, Theme, Layout, Fit and Bookmarks.
- New document-empty state with a prominent Open PDF button.
- Restyled colors, consistent vector icons (rather than emoji), typography and tap targets.
- Improved color-selection feedback and bookmark indicator.

## Functional limitations retained

This is a UI and usability revision of the v0.1.1 source, not a complete implementation of all 43 requested features. OCR, true native editable PDF export, and an actual geometric lasso remain incomplete. PDF text search/selection require Android 15+ in this implementation. PDF export remains flattened and may lose text selectability. These facts should not be obscured by the new UI.

## Build status

Static Java-17 parser, Android XML, Gradle/workflow structural checks pass. The Android SDK isn't available locally; a GitHub Actions compilation and Android-device smoke test are still required. This archive does *not* contain an APK.

## Manual device test

1. Launch without a document; verify home state button opens file picker.
2. Open a selectable text PDF; verify title, page navigation, zoom, bookmarks and notes.
3. Switch Read, Highlight, Pen, Text; verify bottom bar selected state and color strip visibility.
4. Mark up a PDF; verify undo/redo in More sheet and persistence after reopening.
5. Check all 12 More actions and all 8 Reader options.
6. Run in portrait on small phone and landscape on tablet; verify no clipping.
7. Export an annotated copy to a new PDF; verify annotations appear on exported pages.
8. On Android 15+, verify Select text and Search; on older Android, verify graceful messaging.
