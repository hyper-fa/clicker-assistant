# 发布与签名

Windows EXE 和 Android APK 通过 GitHub Releases 发布。HarmonyOS 版因 HarmonyOS 6.0+ 无障碍功能限制，v0.1.0 不发布安装包。

## Windows

```powershell
cargo fmt -- --check
cargo check
cargo build --release
```

发布文件：

```text
target\release\clicker-assistant.exe
```

## Android

需要 JDK 17、Android SDK 34 和 Gradle。

Debug APK：

```powershell
cd android-clicker
gradle assembleDebug --stacktrace --console=plain
```

构建 release：

```powershell
cd android-clicker
gradle assembleRelease --stacktrace --console=plain
```

验证签名：

```powershell
& "$env:ANDROID_HOME\build-tools\34.0.0\apksigner.bat" verify --print-certs .\app\build\outputs\apk\release\app-release.apk
```

## GitHub Release 附件

v0.1.0 上传：

- `clicker-assistant.exe`
- `clicker-assistant-android-v0.1.0.apk`
