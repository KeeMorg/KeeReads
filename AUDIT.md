# Kee Reads — consolidated build and source audit

Version: v0.1.1-audit · November 5, 2026

## Why the previous GitHub builds failed

1. Earlier workflow used the Android SDK legacy `tools` package. This project now uses `android-actions/setup-android@v4` and explicit Android 35 platform/build-tools installation. Your later logs showed that SDK installation succeeded.
2. The original `MainActivity.java` placed `PdfDocument` inside try-with-resources even though it does not implement `AutoCloseable`. The document is now manually closed in `finally`.
3. The original `Annotations.java` used `JSONArray.put(double)` six times without handling `JSONException`. Each call is inside guarded `try/catch` blocks.

The user-provided latest log reached Java compilation and reported six errors in `Annotations.java` only. That is evidence the earlier failure was fixed, but does **not** independently prove this revised project compiles.

## Additional full-source improvements

- Avoid expensive page bitmap rendering inside `View.onDraw`. Render visible page bitmaps on a dedicated worker and update the UI after completion.
- Synchronize PDF renderer closing against background page reads/search; discard stale render results on document changes and close resources on activity destruction.
- Limit export page bitmap dimensions to around 3 megapixels to reduce memory spikes, and ensure bitmap recycling and PDF document closure in `finally`.
- Close autosave temporary file before renaming it to the sidecar file, and perform file sync.
- Guard against missing/corrupted annotation array elements during add, update, and removal.
- Save bookmarks in consistent page order, validate PDF display name, and close pending descriptors after open failures.
- Handle Android 15+ edge-to-edge system bar insets so toolbar controls are not placed underneath the status/navigation bars.

## Checks completed locally

- Parsed all three Java source files with the JDK compiler's Java 17 syntax parser (no grammar errors).
- Parsed every Android XML file and checked manifest component configuration.
- Checked Gradle plugin configuration, required SDK versions, and GitHub Actions workflow/artifact paths.
- Checked Bash build script syntax.
- Checked the 43-feature tracking inventory.
- Verified API 35 `PdfRenderer.Page.searchText`, `selectContent`, `PageSelection.getSelectedTextContents` and text bounds signatures against Android's reference documentation.

Run the local structural test with `python3 qa/check_project.py` after compiling `qa/SyntaxCheck.java` using JDK 17+.

## What has **not** been verified

This environment cannot download the Android SDK or Gradle and has no existing Android SDK, so an actual APK compilation and runtime smoke test were **not** possible here. GitHub Actions remains the required real build check. Newly added UI threading/inset changes have not been tested on hardware. A successful CI APK build will validate Android Java compilation, Android resource linking, packaging and debug signing.

## Remaining 43-feature scope

This is **not** a completed 43-feature app. OCR, native editable PDF annotations, PDF table of contents, cross-page selection, actual lasso, image quality at extreme zoom, and several UI/organization features are still missing or partial. Consult `FEATURES.md` for item-by-item status. Exports are flattened and rasterized: searchability of the original text is lost in the exported PDF.

## Suggested smoke tests after a green APK build

1. Install and launch on Android 7+; open a small local text-based PDF through **OPEN**.
2. Test navigation, bookmarks, relaunch/restored last page, scrolling, pinch zoom, dark and sepia views.
3. Make two different pen/marker strokes, undo, redo, recolor, move, erase, relaunch, and ensure annotations survive.
4. Export a copy; open that copy in another PDF reader and check all marks appear.
5. On Android 15+, test text selection, highlight, search and flashcards. On earlier Android, confirm these unavailable functions explain their limitation.
6. Test a scanned PDF and a large (100+ page) PDF to assess performance and memory; OCR is not implemented.
