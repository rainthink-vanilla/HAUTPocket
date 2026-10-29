# 口袋工大 · HAUTPocket

河南工业大学非官方 Android 校园助手，是参考 **[ZZUPocket](https://github.com/3beuml/ZZUPocket)** 的河南工业大学版**复刻 / 适配实现**。默认莲花街校区，同时支持嵩山路校区。

**特别感谢 [3beuml](https://github.com/3beuml) 开源 ZZUPocket。** 原项目把课表、宿舍用电和校园服务集中到一个校园助手中的设计，为本项目的功能规划、离线体验与技术选型提供了重要参考。

本项目作为独立仓库维护。业务代码针对河南工业大学重新实现，复用了部分构建配置与 Gradle Wrapper；功能和界面并非与 ZZUPocket 完全相同，也不代表原作者参与或认可本项目。

[下载 0.2.0 测试版 APK](https://github.com/rainthink-vanilla/HAUTPocket/releases/tag/v0.2.0) · [原项目 ZZUPocket](https://github.com/3beuml/ZZUPocket) · [课表方案对比](docs/TIMETABLE-COMPARISON.md)

**当前为 0.2.0 双方案版。课表支持官方网页导入，以及兼容本地“大学课表”的文件导入和反向 JSON 导出。教务网页和智能水电仍未使用河南工业大学真实账户完成端到端联调，不应把“编译成功”理解为接口已验证。**

## 功能与状态

| 功能 | 当前实现 | 验证边界 |
|---|---|---|
| 双校区 | 莲花街 / 嵩山路切换；手工房间与读数按校区隔离；在线房间由用户确认归属 | 本地存储与计算可单元测试，界面待设备检查 |
| 课表 | 官方教务网页导入，或大学课表 .md/.json 文件导入；支持校历、作息、备注、替换/更新合并/追加、反向 JSON 导出 | 文件互通可自动验证，真实学校登录流程待验证；显示仍为按周/日选取的列表 |
| 智能水电 | 输入本人校园卡账号，查询已绑定房间，再读取各房间的水电表计 | 使用社区记录的接口和河南工业大学历史代码 43，当前租户有效性待真实账号验证 |
| 在线读数 | 显示服务端设备名称、余额、今日用量、累计购入、状态和查询时间 | 不推测单位、不将人民币余额当成度数；缓存不代表实时值 |
| 手工记录 | 房间标签、可选服务端 ID、独立表计、剩余量、期间充值量和区间使用量 | 充值量未知时不计算消费；不和在线不同单位的读数混算 |
| 校园卡 | 集中入口，可唤起已安装的完美校园；否则显示校方说明 | 本版没有原生校园卡余额/流水接口 |
| 校园服务 | 云上工大官方网页、教务、图书馆及学校帮助页 | 学校门户外网可能返回默认页或需要校园网；保留外部浏览器按钮 |
| 专注 | 15/25/45 分钟计时、进程重开恢复、完成记录 | 暂无后台到点通知 |

不提供自动充值、校园卡转账、自动抢座。座位预约请使用云上工大内图书馆预约入口，本版图书馆入口打开官网。

## 首次使用

1. 安装测试 APK 后，顶部默认选择“莲花街”，可切换“嵩山路”。
2. “课表 → 导入课表 → 从教务网页导入”，在学校页面登录，进入“学生课表查询”，选好学年/学期，再点底部“导入当前学期课表”。确认预览后才替换原课表。
3. 右上角设置中填写第一教学周的**周一**日期、总周数和学校当前节次时间。没有预填未经核实的校历或作息。
4. “宿舍 → 查询智能水电”，填写本人完美校园所绑定的校园卡账号（通常为学号）。若成功返回房间，逐一选择房间所属校区。需要先在完美校园“智能水电”里绑定自己的房间。
5. 如果智能水电提示失败，继续在官方 App 使用；可在“我的手工记录”保存官方读数。只添加确实独立存在的电表/空调表/水表。
6. “校园 → 登录会话”可清除本应用内学校网页登录状态；“宿舍 → 账号 → 移除账号及在线缓存”可移除保存的水电账号。

## 导入 JSON

也可以选择“从大学课表文件导入（MD / JSON）”，读取大学课表导出或外部 agent 按 `univ-timetable/v1` 模板生成的文件。机器可读 JSON 优先，其次读取 Markdown 课程明细表。导入预览中可选更新合并、整体替换、追加，以及是否应用文件内的校历和作息。无有效课程时不修改旧课表。

课表页“导出”会生成大学课表可读取的标准 JSON，包含课程、备注、校历与作息，不包含水电数据或账号。具体差异和两种方案边界见 [课表实现对比](docs/TIMETABLE-COMPARISON.md)。内置AI文字/截图识别、手动课程编辑、日/周格/学期多视图和背景主题尚未迁移。

直接支持正方课表查询返回的 `{"kbList":[...]}`、仅包含课表条目的数组，以及以下独立格式：

```json
{
  "courses": [
    {
      "name": "格式示例课程",
      "teacher": "",
      "location": "格式示例教室",
      "day": 1,
      "weeks": [1, 3, 5],
      "sections": [1, 2],
      "campus": "LIANHUA"
    }
  ]
}
```

这是格式说明，不是学校课表。`day` 为周一=1 至周日=7，`campus` 可为 `LIANHUA`、`SONGSHAN`。解析支持单双周、分段周次及不连续节次。发现坏条目时预览会显示数量，用户可以取消；完全无有效课程时不替换旧课表。

## 构建与检查

Kotlin 2.0.21、Compose Material 3、AGP 8.7.3、Gradle 8.9、JDK 17、compile/target SDK 35；支持 Android 8.0 / API 26 及以上。

Windows 项目自带便携工具链配置脚本（下载文件按 SHA-256 验证；工具写入本项目 `.toolchain`）：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\setup-android.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\build.ps1
```

检查包括 `testDebugUnitTest`、`assembleDebug`、`lintDebug`。若已有 JDK17/Android SDK，可直接配置 `JAVA_HOME` 和 `local.properties` 后使用 `gradlew.bat`。

构建产物：`app/build/outputs/apk/debug/app-debug.apk`。这是测试包，供安装联调，尚未配置正式发行签名。

交付文件位于 `deliverables/`，新版本为 `HAUTPocket-0.2.0-debug.apk`，另有源码压缩包与 SHA-256 清单。保留0.1.0旧包作为回滚点；完整验证范围见 [验证记录](docs/VERIFICATION.md)。

## 隐私与数据

- 无自建后端、广告或统计 SDK。数据仅保存在本机和学校/完美校园官方服务之间。
- 教务登录密码只填写在学校页面，不被原生代码读取。WebView 保存的登录会话可清除。
- 原生水电查询只使用本人校园卡账号；账号以 Android Keystore AES-GCM 加密保存。房间查询凭据 `roomverify` 只存在本次查询内存中，不写入课表、日志或本地快照。
- 在线房间缓存只保存凭据的 SHA-256 索引、房间名、表计显示值与更新时间。课表、房间标签、手工记录保存在应用私有存储。
- 禁用 Android 自动备份，避免系统备份带走网页会话和个人记录。卸载会清除本机数据；本版可导入/导出课表，但没有全应用数据备份/恢复界面。
- 不忽略 TLS 证书错误，不使用任意网页可调用的 JavaScript 原生桥。教务导入脚本仅在 `https://jwglxt.haut.edu.cn/jwglxt/` 上按用户操作执行。

协议与证据见 [接口与联调](docs/INTEGRATIONS.md)，构建与贡献说明见 [开发说明](docs/DEVELOPMENT.md)。

## 许可与致谢

### 感谢 ZZUPocket 与原作者

- 原项目：**[3beuml/ZZUPocket](https://github.com/3beuml/ZZUPocket)**。
- 原作者：**[3beuml](https://github.com/3beuml)**。
- 参考版本：[`5d2b2e6`](https://github.com/3beuml/ZZUPocket/tree/5d2b2e6e2e0871922f424436a1785fade4cdae4d)。
- 参考与复用范围：校园助手的功能组织、离线课表与宿舍用量思路，以及部分构建配置和 Gradle Wrapper；学校特有的业务接口由本项目另行适配。

感谢原作者将 ZZUPocket 以 MIT 许可开放，使其他学校的同学也能参考其思路制作适合自己校园的工具。欢迎关注与支持原项目。

### 其他参考与许可

新增源代码使用 [MIT 许可](LICENSE)。ZZUPocket 的原作者版权与 MIT 声明完整保留在 [第三方声明](THIRD_PARTY_NOTICES.md#zzupocket) 中。Gradle Wrapper 自身的 Apache 许可与声明保存在 [licenses](licenses/) 目录。

课表文件格式与互通验证参考 [大学课表 · Univ Timetable](https://github.com/rainthink-vanilla/univ-timetable)，其 MIT 声明见 [licenses/UnivTimetable-MIT.txt](licenses/UnivTimetable-MIT.txt)。

参考社区项目时仅核对接口路径、字段和学校目录事实，未复制未授权或 GPL/AGPL 项目源码。具体事实来源和有效性限制见接口文档。
