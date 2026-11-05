# Kee Reads — original 43 checked requirements

**Status:** `Implemented in source` means code paths are present, *not verified on a device*. `Partial` means deliberately limited. `Planned` means absent. This table makes no claim that the entire specification is finished.

| # | Selected requirement | Status | Notes |
|---|---|---|---|
| 1 | Multiple viewing modes | Implemented in source | Continuous / single / two-page |
| 2 | Zoom controls | Implemented in source | Pinch / buttons / fit reset |
| 3 | Dark mode | Implemented in source | Inverted PDF pixels |
| 4 | Reading themes | Implemented in source | White, sepia, dark |
| 5 | Page thumbnails | Implemented in source | Thumbnail picker |
| 6 | Bookmarks | Implemented in source | Bookmark current page |
| 7 | Table of contents | Planned | No PDF outline parser |
| 8 | Search | Partial | Android 15+ text PDFs only |
| 9 | Reading progress | Implemented in source | Last PDF and last page |
| 10 | Multiple highlight colors | Implemented in source | Presets and hex |
| 11 | Adjustable opacity | Implemented in source | Marker opacity slider |
| 12 | Precise text selection | Partial | Android 15+ PdfRenderer text selection |
| 13 | Cross-page highlighting | Planned | Single page selection only |
| 14 | Freehand highlighting | Implemented in source | Finger/stylus marker |
| 15 | Underline | Implemented in source | Freehand straight / exact text |
| 16 | Squiggly underline | Implemented in source | Freehand / exact text |
| 17 | One-tap highlighting | Partial | Tool stays selected, drag required |
| 18 | Edit existing highlights | Partial | Recolor / move / remove; boundary handles not done |
| 19 | Remove individual highlights | Implemented in source | Eraser / select + delete |
| 20 | Pen tool | Implemented in source | Finger/stylus strokes |
| 21 | Highlighter pen | Implemented in source | Translucent brush |
| 22 | Eraser | Implemented in source | Erases whole annotations near touch |
| 23 | Lasso selection | Partial | Select + move supported; no actual polygon lasso |
| 24 | Adjustable pen width | Implemented in source | Brush thickness slider |
| 25 | Undo and redo | Implemented in source | Up to 50 state snapshots |
| 26 | Text boxes | Implemented in source | Tap, type, edit |
| 27 | Annotation sidebar | Partial | Dialog list rather than persistent sidebar |
| 28 | Tap-to-jump | Implemented in source | Notes dialog |
| 29 | Sort annotations | Partial | Fixed document-order sort only |
| 30 | Standard PDF compatibility | Partial | Original PDF opens; flatten-only export |
| 31 | Local storage | Implemented in source | Sidecar JSON |
| 32 | Android file picker | Implemented in source | Storage Access Framework |
| 33 | Dictionary lookup | Partial | Web-search definition intent; network/browser needed |
| 34 | OCR recognition | Planned | Scanned PDFs require a future OCR engine |
| 35 | Flashcard creation | Partial | Android 15+ selected-text cards, basic review only |
| 36 | Study mode | Partial | Notes/cards lists, not dedicated full study mode |
| 37 | Distraction-free reading | Planned | Toolbars remain visible |
| 38 | Fast PDF rendering | Partial | Worker-rendered page bitmap cache, not stress tested |
| 39 | Smooth zooming | Partial | Gesture zoom with pre-rendered bitmaps; high zoom loses sharpness |
| 40 | Floating annotation toolbar | Planned | Fixed bottom horizontal toolbar |
| 41 | Customizable toolbar | Planned | Tools are scrollable but not rearrangeable |
| 42 | Stylus button shortcuts | Partial | Primary stylus button maps to eraser during input |
| 43 | Accessibility support | Partial | Most buttons labeled; comprehensive accessibility work pending |
