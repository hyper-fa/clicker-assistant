# 发布与签名

本仓库只提交源码。Windows EXE、Android APK、HarmonyOS HAP 作为 GitHub Releases 附件发布。

## Windows

```powershell
cargo fmt -- --check
cargo check
cargo build --release
```

发布文件：

```text
target\release\simple-clicker.exe
```

## Android

准备构建环境：

```powershell
$env:JAVA_HOME='D:\Dev\Android\jdk'
$env:ANDROID_HOME='D:\Dev\Android\sdk'
$env:ANDROID_SDK_ROOT='D:\Dev\Android\sdk'
$env:GRADLE_USER_HOME='D:\Dev\Android\gradle-cache'
$env:Path="D:\Dev\Android\jdk\bin;D:\Dev\Android\gradle\bin;D:\Dev\Android\sdk\cmdline-tools\latest\bin;D:\Dev\Android\sdk\platform-tools;$env:Path"
```

Debug APK：

```powershell
cd android-clicker
gradle assembleDebug --stacktrace --console=plain
```

发布签名 APK 需要本地 `android-clicker\keystore.properties` 和 `android-clicker\keystore\*.jks`。这两个路径已被 `.gitignore` 排除。

`keystore.properties` 示例：

```properties
storeFile=keystore/benben-release.jks
storePassword=ChangeThisPassword
keyAlias=benben
keyPassword=ChangeThisPassword
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

## HarmonyOS NEXT

公开仓库中的 `harmony-clicker\build-profile.json5` 不包含签名配置。未签名构建：

```powershell
cd harmony-clicker
$env:DEVECO_STUDIO_HOME='D:\Huawei\DevEco Studio'
npm run build
```

签名 HAP 建议在 DevEco Studio 中配置 HarmonyOS 调试或发布签名，或复制 `harmony-clicker\build-profile.signing.example.json5` 为本地签名配置后填入本机证书、Profile 和密码。真实 `.p12`、`.p7b`、`.cer`、签名密码和设备 UDID 不要提交。

发布文件建议命名：

```text
benben-assistant-harmonyos-next-signed.hap
```

## GitHub Release 附件

推荐每次发布上传：

- `simple-clicker.exe`
- `奔奔助手.apk`
- `benben-assistant-harmonyos-next-signed.hap`
- 简短变更说明和目标系统版本
