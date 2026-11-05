# Replace the repository in one update

This ZIP is the complete Kee Reads v0.1.1-audit source at repository-root level (no enclosing KeeReads folder).

To avoid changing one Java file at a time, use GitHub Desktop:

1. Clone `KeeMorg/KeeReads` with GitHub Desktop.
2. Extract this ZIP and copy **all its contents**, including hidden `.github`, into the local cloned repository, replacing existing files.
3. In GitHub Desktop, review the changed files, enter commit message `Kee Reads consolidated audit fixes`, click **Commit to main**, then **Push origin**.
4. On GitHub, open **Actions → Build Kee Reads Android APK**, and inspect the run triggered by the commit. On success, download **KeeReads-debug-apk** from Artifacts and extract `app-debug.apk`.

If you prefer the web interface, upload the updated directories/files to the same repository paths, taking care to replace existing files and avoid a nested project folder. GitHub does not compile an uploaded ZIP by itself.

The updated files contain both earlier compiler fixes and additional source improvements. Do **not** mix older individual `MainActivity.java` / `Annotations.java` downloads into this project.

This package is audited statically but has not been successfully compiled into an APK in this environment, so any build result from GitHub remains the definitive compilation check.
