# 连点助手

连点助手是一个多平台自动点击工具仓库，包含 Windows、Android 和 HarmonyOS NEXT 三个版本。Windows 和 Android 安装包会通过 GitHub Releases 发布。

## 版本

| 平台 | 目录 | 状态 | 主要能力 |
| --- | --- | --- | --- |
| Windows | 根目录 `src/` | 可构建 | 连点器、固定位置、后台点击、脚本录制/回放 |
| Android | `android-clicker/` | 可构建 | 无障碍点击、悬浮窗控制、拖动准星设置位置 |
| HarmonyOS NEXT | `harmony-clicker/` | 暂不可用 | HarmonyOS 6.0+ 无障碍能力限制下暂无法正常使用 |

## Windows 构建

需要 Rust 1.75+ 和 Windows MSVC 工具链。

```powershell
cargo fmt -- --check
cargo check
cargo build --release
```

产物：

```text
target\release\clicker-assistant.exe
```

## Android 构建

需要 JDK 17、Android SDK 34 和 Gradle。

```powershell
cd android-clicker
gradle assembleDebug
```

发布签名 APK 的方法见 [docs/RELEASE.md](docs/RELEASE.md)。

## HarmonyOS NEXT

HarmonyOS NEXT 源码保留在 `harmony-clicker/` 作为实验实现。由于 HarmonyOS 6.0 及以上系统对第三方无障碍服务和手势注入存在限制，当前版本暂时无法正常使用，因此 v0.1.0 不发布 HarmonyOS 安装包。

## 权限说明

Android 版本依赖系统无障碍能力执行点击。用户必须手动开启对应无障碍服务。部分系统版本、应用市场策略或厂商权限限制可能会隐藏或限制第三方无障碍服务。

后台点击、脚本回放和移动端自动点击都只面向普通软件辅助操作，不用于绕过游戏、反作弊、支付确认或系统安全限制。

## 发布

v0.1.0 发布附件：

- `clicker-assistant.exe`
- `clicker-assistant-android-v0.1.0.apk`
