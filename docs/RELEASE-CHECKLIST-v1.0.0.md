# 1.0.0 发布前检查

日期：2026-10-30。对象：版本代码18、包名`cn.hautpocket.app`、Android8.0+的正式Release APK。

| 项目 | 结果 |
|---|---|
| Release构建 | R8优化构建成功，保留既有WorkManager任务类名；不是重命名Debug包 |
| 单元测试 | Debug 50项、Release 50项，失败0、错误0 |
| Lint | Release无Error/Fatal；5项依赖版本更新Warning，发布检查不进行无关大版本升级 |
| JavaScript | 三份脚本语法检查通过；五份打包网页资源与源码匹配 |
| 发行签名 | RSA3072发行证书、签名轮换证明通过apksigner；Android8 v2与Android9+ v3验证通过 |
| 旧版兼容 | 0.2.0、0.9.2测试签名一致；手机从旧版保留数据覆盖到1.0.0成功 |
| 关闭调试 | 系统flags无DEBUGGABLE，run-as明确拒绝“package not debuggable” |
| 核心真机验收 | 用户确认旧课表/配色保留，Release书目搜索与馆藏详情正常；首页排版正常 |
| 后台提醒 | 先前0.9.2同业务逻辑已在桌面受控验证；需保留本人设置的后台自启动和无限制省电 |
| 源码范围 | 自动扫描无匹配凭据/私钥，工具链、手机调试内容、内部记录、私钥均排除 |
| 许可与来源 | 保留ZZUPocket/3beuml致谢、MIT与第三方许可；独立非官方项目声明清楚 |
| 源码附件 | 使用已提交Git树生成ZIP，和Release标签指向同一提交 |
| 发布附件 | APK、源码ZIP和SHA256SUMS，上传后由维护脚本回下载核对哈希 |

## 实际边界

教务真实账号导入与智能水电当前租户端到端联调仍未完成，详见[发布说明](RELEASE-v1.0.0.md)和[验证记录](VERIFICATION.md)。没有代提交座位预约、充值或转账。未穷举所有Android设备、主题字号、Doze、重启与三种专注完整时长。发布渠道是GitHub，不代表应用商店审核。

公开证书和签名维护方式见[SIGNING](SIGNING.md)。本地签名材料需要由维护者安全备份，不能提交到公开仓库。
