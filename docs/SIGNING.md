# 发行签名与更新

1.0.0采用非调试Release构建和独立RSA3072发行密钥。公开证书在`signing/release-certificate.pem`，旧签名到新签名的证明在`signing/lineage.bin`。

发行证书SHA-256：`434f230a28f2cadee78555201ef21e5c02b67260e452043353885b0ccf398048`。

旧测试证书SHA-256：`c070412699f65af8b5d75d823210f3a2dc166e1a8edae435c51a40e6536806ab`。0.2.0与0.9.2测试包使用同一证书。

Android9及以上通过v3签名和轮换证明使用新发行证书；Android8使用旧证书的v2签名兼容既有安装。无论哪种平台，1.0.0 APK均关闭debuggable。覆盖安装的实际验证见VERIFICATION，不要求卸载或清除数据。

## 维护者构建

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\build.ps1 -Release
powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\sign-release.ps1
python tools/audit-source.py
powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\package.ps1 -Release
```

`setup-release-signing.ps1`只供首次初始化或核对已有材料；一旦公开证明已存在，缺少私钥会停止，不能随意生成替代密钥。一般贡献者可构建Debug或未签名Release，不具备官方发行签名能力。

## 私有材料保管

本机项目根目录的`.private-signing/`包含发行密钥、密码文件与用于兼容Android8的旧签名密钥，已被Git忽略，并限制本机文件权限。请将整个目录安全备份到可信离线位置，保留公开`signing/`目录与此文档。丢失密钥会影响后续更新签名。不要上传该目录、密钥或密码到仓库、Release、Issue或聊天。

认证使用本机Git Credential Manager，仅在进程内调用GitHub官方API，不写入发布附件。发布辅助脚本不输出凭据匹配值。

依据：[Android签名](https://developer.android.com/studio/publish/app-signing)、[apksigner轮换](https://developer.android.com/tools/apksigner)。本次发行渠道为GitHub Release，不代表已提交应用商店。
