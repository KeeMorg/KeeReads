@echo off
setlocal
where gradle >nul 2>nul
if errorlevel 1 (
    echo Gradle was not found on PATH.
    echo Open KeeReads in Android Studio and choose Build ^> Build APK(s),
    echo or install Gradle 8.11.1 and Android SDK 35 before running this script.
    exit /b 1
)
call gradle :app:assembleDebug
if errorlevel 1 exit /b 1
echo APK: app\build\outputs\apk\debug\app-debug.apk
