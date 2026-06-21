# 连点助手 HarmonyOS NEXT

HarmonyOS NEXT 原生版自动点击器实验实现。由于 HarmonyOS 6.0 及以上系统对第三方无障碍服务和手势注入存在限制，当前版本暂时无法正常使用，v0.1.0 不发布 HAP 安装包。

## 功能

- 设置点击间隔，最小 1ms，默认 100ms。
- 通过 3 秒倒计时记录屏幕中心点，也支持手动输入 X/Y 坐标。
- 启动后按目标坐标循环点击，直到停止。
- 使用 Accessibility Extension Ability 注入手势。

## 状态

源码保留用于后续验证 HarmonyOS 无障碍能力变化。当前公开版本仅发布 Windows 和 Android 安装包。
