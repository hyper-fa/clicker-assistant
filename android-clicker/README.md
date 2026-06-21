# 连点助手 Android

Android 简化自动点击器。它使用无障碍服务执行点击，并通过悬浮窗按钮和可拖动准星设置目标位置。

## 功能

- 设置点击间隔，默认 100ms。
- 通过悬浮窗准星选择屏幕点击位置。
- 在其他 App 上层用悬浮按钮开始/停止。
- 使用 `AccessibilityService.dispatchGesture` 执行点击。

## 构建

需要 JDK 17、Android SDK 34 和 Gradle。

```powershell
cd android-clicker
gradle assembleDebug
```

生成 APK：

```text
app\build\outputs\apk\debug\app-debug.apk
```

签名 release APK 的方法见仓库根目录 `docs/RELEASE.md`。

## 使用

1. 打开 App。
2. 点击“开启无障碍权限”，在系统设置中启用“连点助手”。
3. 点击“开启悬浮窗权限”，允许显示在其他应用上层。
4. 设置点击间隔。
5. 点击“启动悬浮窗”。
6. 拖动准星到目标位置，点悬浮按钮开始/停止。

## 限制

本版本只做前台屏幕点击，不支持后台点击、脚本录制、右键或中键概念。不同 Android 厂商系统可能会限制无障碍服务或悬浮窗权限。
