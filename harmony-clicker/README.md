# 奔奔助手 HarmonyOS NEXT

HarmonyOS NEXT 原生版自动点击器。v1 使用无障碍扩展手势注入执行屏幕点击，不使用 Android APK、Android AccessibilityService 或全局悬浮窗。

## 功能

- 设置点击间隔，最小 1ms，默认 100ms。
- 通过 3 秒倒计时记录屏幕中心点，也支持手动输入 X/Y 坐标。
- 启动后按目标坐标循环点击，直到停止。
- 使用 Accessibility Extension Ability 注入手势。

## 环境

需要 DevEco Studio / HarmonyOS SDK。若 DevEco Studio 不在常见安装目录，请先设置：

```powershell
$env:DEVECO_STUDIO_HOME='D:\Huawei\DevEco Studio'
```

脚本会自动设置：

```powershell
DEVECO_SDK_HOME=D:\Huawei\DevEco Studio\sdk
OHOS_BASE_SDK_HOME=D:\Huawei\DevEco Studio\sdk\default\openharmony
NODE_HOME=D:\Huawei\DevEco Studio\tools\node
JAVA_HOME=D:\Huawei\DevEco Studio\jbr
```

## 构建

```powershell
cd harmony-clicker
npm run build
```

也可以直接指定 DevEco 目录运行：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\hvigor.ps1 -DevEcoHome 'D:\Huawei\DevEco Studio' --mode module -p product=default assembleHap --no-daemon
```

## 签名

公开仓库里的 `build-profile.json5` 不包含签名配置，只用于 unsigned HAP 构建。真机安装通常需要 DevEco Studio/Huawei 账号生成的调试或发布签名。

可参考 `build-profile.signing.example.json5` 准备本地签名配置，但不要提交真实 `.p12`、`.p7b`、`.cer`、签名密码或设备 UDID。

## 产物

未签名 HAP 通常输出到：

```text
entry\build\default\outputs\default\entry-default-unsigned.hap
```

签名 HAP 建议作为 GitHub Release 附件发布，不要提交到仓库。
