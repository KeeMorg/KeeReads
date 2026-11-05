# Kee Reads v0.2.0 — redesigned Android PDF reader

**What changed:** see `UI_CHANGELOG.md`. New compact reader navigation, bottom annotation dock, contextual color strip, and tools/options sheets.

**Build status:** Java syntax/project structure validated locally; build with GitHub Actions to generate and test the APK.

# Kee Reads

**A native, offline-first Android PDF reader and annotation prototype.**

Package: `com.keereads.app` · Version `0.1.1` · Minimum Android 7.0 (API 24) · Android Studio / Java 17 · Target Android 15 (API 35).

> **Important:** This is the actual Android Studio **source project**, not an APK and not a finished implementation of the entire 43-feature specification. It has not been built or tested on an Android device in the generation environment because Android SDK and Gradle were not installed, and network access there was unavailable. Treat it as an unverified prototype requiring a real build and functional QA.

## Build in Android Studio (Windows)

1. Extract the `KeeReads` folder from the ZIP.
2. Install Android Studio and Android SDK Platform 35 (Settings → Android SDK). Use its Java 17/JDK 17.
3. Select **File → Open**, then choose the folder containing `settings.gradle`.
4. Let Android Studio set up Gradle and sync the project. The project specifies Android Gradle Plugin 8.9.2 and Gradle 8.11.1. If Studio asks for Gradle, select or install 8.11.1.
5. **Build → Build APK(s)**, or in a terminal with Gradle 8.11.1 installed run `gradle :app:assembleDebug`.
6. The expected APK output location is `app/build/outputs/apk/debug/app-debug.apk`.
7. Install on a device that allows installation from the source used to open that APK.

**Alternative:** Put the folder in a GitHub repository and run the bundled **Build Kee Reads Android APK** GitHub Actions workflow; it installs the SDK and Gradle and uploads a `KeeReads-debug-apk` artifact. This is an optional workflow only; no APK was produced here.

## Main interactions

- **OPEN**: Android file picker. Opens a `content://` PDF; the app requests lasting read access where the provider supports it.
- **◀ / ▶**: Previous/next page. Tap page indicator to jump to a specific page.
- **Pages**: Thumbnail grid. **★** adds/removes a bookmark. **Marks** lists saved bookmarks.
- **Layout** cycles continuous / single page / two-page spread. **Theme** cycles white / sepia / dark inversion.
- **Pan** scrolls. Pinch to zoom, or use ± and Fit.
- **Pen**, **Marker**, **Line**, **Squiggle**, **Erase**, and **Text** create or edit document overlays.
- **Text select** (Android 15+, selectable PDFs): Drag through text to see Highlight / Underline / Squiggle / Copy / Define / Flashcard options. PDFs without a text layer won't yield selectable content.
- **Select** taps an existing mark for recoloring, text-box editing, or deletion. Drag an existing mark to move it. This is not a polygon lasso.
- **Color**, **Brush**: Change pen/marker color, thickness, or marker opacity.
- **↶ Undo / ↷ Redo**: Undo/redo saved notes; automatic on-device storage.
- **Notes**: Shows annotations in document order, tap to navigate. **Cards**: Lists/reviews flashcards made from text selections.
- **Find**: Uses Android 15+ system PDF text search; unavailable on older devices. It will not search image-only scans without OCR.
- **EXPORT**: Asks for a location and writes a new annotated, **flattened/rasterized PDF**. Original content becomes page images in the exported file; the text layer is not preserved.

## Data and privacy

No registration, advertising, internet access or dangerous storage permissions are required for reading and drawing. Annotations, bookmarks, progress, and flashcards are stored as **app-private sidecar JSON** associated with the document URI and automatically saved after edits. The original PDF stays untouched. File picker access and local source files may be revoked by the operating system or their document providers; sidecars are not portable into another PDF app until exported as a flattened document. The PDF export cannot be reopened as editable vector marks in an external reader.

## Known limitations / planned work

The 43-feature checklist is inventoried in `FEATURES.md` with status. Not yet implemented: PDF outline/table-of-contents parsing, cross-page text selections, actual OCR, polygon lasso, adjustable/highlighting boundaries, annotation filters/sorting UI, a true reading-only distraction-free mode, a movable floating/customizable annotation bar, native editable PDF annotation export, and proper multi-resolution/tiled rendering for very large/zoomed PDFs. Android 14 and earlier lack the platform API used by this prototype for exact selectable text and find.

## Safety and reliability

- Avoid annotating the only copy of an irreplaceable PDF without making backups of exported copies; sidecars live in app storage and may be deleted on uninstall depending on backup settings.
- Export creates a **new** file; it does not overwrite the original, but the export can be large and slower for lengthy documents.
- Performance, memory use, compatibility with encrypted PDFs, crash resilience, accessibility, and annotation correctness have **not been device-tested**.
- This is not a production app and shouldn't be distributed as a trusted finished reader without compiling, running on actual devices, testing with real PDFs, and resolving issues identified there.

## Latest source audit (v0.1.1)

This package includes fixes for the prior GitHub compilation errors and additional resource, save, render-thread, and export improvements. See `AUDIT.md` for checks performed, exact limitations, and a post-build smoke-test plan. Use `BUILD_ON_GITHUB.md` to replace the entire repository in a single commit. Android compilation and device QA are still outstanding.

**Debug build upgrades:** GitHub-hosted debug APKs may be signed with different generated debug certificates on different runs. Android may refuse to install a newer APK over an old one. Avoid uninstalling a build containing valuable annotations without backing up the annotated exports; app-private sidecar annotations can be lost on uninstall.
