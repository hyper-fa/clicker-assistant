# 连点助手

连点助手是一个多平台自动点击工具仓库，包含 Windows、Android 和 HarmonyOS NEXT 三个版本。仓库只保存源码和构建说明；可安装的 EXE、APK、HAP 应通过 GitHub Releases 发布。

## 版本

| 平台 | 目录 | 状态 | 主要能力 |
| --- | --- | --- | --- |
| Windows | 根目录 `src/` | 可构建 | 连点器、固定位置、后台点击、脚本录制/回放 |
| Android | `android-clicker/` | 可构建 | 无障碍点击、悬浮窗控制、拖动准星设置位置 |
| HarmonyOS NEXT | `harmony-clicker/` | 实验版 | 无障碍扩展手势点击、倒计时/手动坐标设置 |

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

需要 JDK 17、Android SDK 34 和 Gradle。也可以使用本机 D 盘便携环境。

```powershell
$env:JAVA_HOME='D:\Dev\Android\jdk'
$env:ANDROID_HOME='D:\Dev\Android\sdk'
$env:ANDROID_SDK_ROOT='D:\Dev\Android\sdk'
$env:GRADLE_USER_HOME='D:\Dev\Android\gradle-cache'
$env:Path="D:\Dev\Android\jdk\bin;D:\Dev\Android\gradle\bin;D:\Dev\Android\sdk\cmdline-tools\latest\bin;D:\Dev\Android\sdk\platform-tools;$env:Path"

cd android-clicker
gradle assembleDebug
```

发布签名 APK 的方法见 [docs/RELEASE.md](docs/RELEASE.md)。

## HarmonyOS NEXT 构建

需要 DevEco Studio / HarmonyOS SDK。默认公开配置不包含签名信息，只构建 unsigned HAP。

```powershell
cd harmony-clicker
$env:DEVECO_STUDIO_HOME='D:\Huawei\DevEco Studio'
npm run build
```

HarmonyOS 真机安装通常需要 DevEco/Huawei 账号生成的调试或发布签名。签名文件不要提交到 Git。

## 权限说明

Android 和 HarmonyOS 版本依赖系统无障碍能力执行点击。用户必须手动开启对应无障碍服务。部分系统版本、应用市场策略或厂商权限限制可能会隐藏或限制第三方无障碍服务。

后台点击、脚本回放和移动端自动点击都只面向普通软件辅助操作，不用于绕过游戏、反作弊、支付确认或系统安全限制。

## 发布

源码提交到 GitHub；安装包放 GitHub Releases：

- `clicker-assistant.exe`
- `连点助手.apk`
- `clicker-assistant-*.hap`

发布前先阅读 [docs/SECURITY_NOTES.md](docs/SECURITY_NOTES.md)，确认没有提交签名密钥、密码、本机路径或设备 UDID。
