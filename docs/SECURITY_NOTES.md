# 安全与公开仓库检查

发布到 GitHub 前必须确认没有提交以下内容：

- Android keystore：`*.jks`、`*.keystore`
- Android `keystore.properties`
- HarmonyOS 签名文件：`*.p12`、`*.p7b`、`*.cer`、`*.csr`
- HarmonyOS `signing/` 目录和 DevEco 本地签名 Profile
- 签名密码、token、私钥、本机绝对路径、设备 UDID
- 构建产物：`target/`、`build/`、`dist/`、`*.apk`、`*.hap`、`*.exe`

建议检查命令：

```powershell
git status --short --ignored
rg -n -i "password|storePassword|keyPassword|secret|token|p12|jks|keystore|udid|private"
rg -n "0000001B|\\.ohos|ChangeThisPassword"
```

敏感词搜索会命中文档和示例配置中的占位字段，这是正常的；重点确认没有真实密码、真实证书路径、真实 UDID 或密钥文件进入 `git status --short`。
