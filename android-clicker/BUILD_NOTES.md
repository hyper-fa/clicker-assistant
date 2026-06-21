# Android Build Notes

Use JDK 17, Android SDK 34, and Gradle. This project can be built from Android Studio or from the command line.

Debug build:

```powershell
cd android-clicker
gradle assembleDebug --stacktrace --console=plain
```

Expected debug APK:

```text
android-clicker\app\build\outputs\apk\debug\app-debug.apk
```

For a signed release build, run `gradle assembleRelease`.
