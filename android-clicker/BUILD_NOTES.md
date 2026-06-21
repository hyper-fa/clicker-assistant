# Android Build Notes

Use JDK 17, Android SDK 34, and Gradle. This project can be built from Android Studio or from the portable command-line environment on `D:\Dev\Android`.

```powershell
$env:JAVA_HOME='D:\Dev\Android\jdk'
$env:ANDROID_HOME='D:\Dev\Android\sdk'
$env:ANDROID_SDK_ROOT='D:\Dev\Android\sdk'
$env:GRADLE_USER_HOME='D:\Dev\Android\gradle-cache'
$env:Path="D:\Dev\Android\jdk\bin;D:\Dev\Android\gradle\bin;D:\Dev\Android\sdk\cmdline-tools\latest\bin;D:\Dev\Android\sdk\platform-tools;$env:Path"
```

Debug build:

```powershell
cd android-clicker
gradle assembleDebug --stacktrace --console=plain
```

Expected debug APK:

```text
android-clicker\app\build\outputs\apk\debug\app-debug.apk
```

Release signing uses local files that must not be committed:

```text
android-clicker\keystore.properties
android-clicker\keystore\*.jks
```
