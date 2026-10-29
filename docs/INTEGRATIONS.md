# 接口证据与联调清单

## 校方公开资料

- 云上工大官方上线通知：<https://xxb.haut.edu.cn/info/1032/16509.htm>。
- 鸿蒙与多端服务介绍：<https://xxb.haut.edu.cn/info/1031/20959.htm>。确认统一身份认证、课表、校园卡充值、场馆预约等服务；没有公开授权给本应用的原生 API。
- 一卡通说明：<https://xxb.haut.edu.cn/info/1221/21459.htm>。确认完美校园和校园卡的关联。这是历史页面，具体入口以在用 App 为准。
- 官方主页链接服务门户 `portal.haut.edu.cn`、学生入口 `mapp.haut.edu.cn`。2026-10-27 公开网页检索中前者根路径返回 nginx 默认页，后者根路径返回 404；不能据此认定真实用户登录一定失败，也不能认定它们已可正常登录。本版网页错误有提示和浏览器继续入口。
- 学校 VPN 说明：<https://xxb.haut.edu.cn/info/1221/20649.htm>，提到统一认证和 `mapp.haut.edu.cn`。
- 教务登录页：<https://jwglxt.haut.edu.cn/jwglxt/xtgl/login_slogin.html>，实际页面标识正方 N5 / V9。
- 图书馆 <https://lib.haut.edu.cn/>；校方读者手册说明座位预约从云上工大进入，不能将官网入口当成已集成预约。

## 教务课表

参考事实来源：<https://github.com/yusixian/AISchedule_myparser>。仓库 README 写明河南工业大学适配和 2025-03-13 更新。未发现许可文件，所以只参考协议事实，不复制解析器代码。

- 课表页：`/jwglxt/kbcx/xskbcx_cxXskbcxIndex.html?gnmkdm=N2151`。
- 查询：POST `/jwglxt/kbcx/xskbcx_cxXsKb.html?gnmkdm=N2151`。
- 学年、学期参数从官方页面 `xnm`、`xqm` 字段读取，避免猜测正方学期编码。
- 课程字段：`kbList/kcmc/xqj/zcd/jc/jcs/cdmc/xm/xqmc`。
- 请求同源携带网页会话，原生层不取 Cookie、密码或令牌；不读取登录表单。
- 本版采用白名单域、无 `addJavascriptInterface`；异步同源请求加超时，结果需用户确认。

待验证：实际账号登录跳转、课表权限菜单码、当前学期字段、两个校区的地点命名及节次时间。网页外部浏览器登录与本应用 WebView 会话互不共享，不宣称外部登录后可直接回传课表。

## 完美校园 · 智能水电

用户确认：莲花街入口为“智能水电”。嵩山路入口及业务规则未实测。

协议事实线索：<https://github.com/DragonYH/astrbot_plugin_wanxiao_electricity> 的 `wanxiao_client.py` 与学校目录；<https://github.com/JustaSage/ZCST_FEE_Bot> 也记录了同域名的水电接口家族。这些项目不等于河南工大官方接口文档。本应用独立实现字段序列化、HTTP 调用和响应解析，不复制其源码。

- 地址：`https://xqh5.17wanxiao.com/smartWaterAndElectricityService/SWAEServlet`。
- 河南工业大学历史目录 `customercode=43`。来源链：历史官方 `open.17wanxiao.com/kdword_fl02.html` 已失效；社区固定镜像 <https://github.com/zuwei522/perfect-campus_electricity-alert/blob/f258c6438040f03dbcfb909c7b705c970d976ea3/school-list.md>；新项目将其名称-代码事实结构化。**当前是否仍为这个租户必须实测。**
- POST 查询参数：`customercode`、`method`、`param`（JSON 字符串）。
- `getbindroom`：参数 `cmd`、本人校园卡 `account`。`existflag=0/1/2` 对应未绑定/单房间/多房间；多房间列表 `roomlist`。
- `h5_getstuindexpage`：参数 `cmd`、`account`、服务端返回的 `roomverify`、UTC+8 时间戳。字段 `modlist` 包含表计。
- 原样展示 `devicename/odd/todayuse/sumbuy/status`；业务类型字段可能拼为 `bussnesstype` 或 `businesstype`。只识别公开记录的电/水类型，不推算照明与空调映射，也不补造热水类型。
- 不枚举其他学号，不按编号猜房间，不实现绑定/解绑/支付写接口。只能读取用户自己的已绑定房间。
- 房间先显示“未分配校区”，由用户确认莲花街或嵩山路；不能因为学校代码相同就假定房间属于当前校区。
- 账号切换先清除旧账号的在线结果与校区归属，避免把不同账号数据混在一起。账号加密保存；临时 `roomverify` 不持久化。
- 缓存每个房间成功读取的时间；失败房间单独显示，其他房间可继续返回；总体失败不将缓存伪装成新数据。

待真实账户验收：

1. 使用本人账号先在官方“智能水电”成功查询，记录预期房间名称和读数。
2. 本应用查询返回同样的已绑房间；不要发密码、登录链接 params、Cookie、roomverify 到公开聊天或仓库。
3. 对照设备名称、余额及单位、今日用量、刷新时间；无单位的数据保持原文展示。
4. 莲花街、嵩山路各取一个本人或得到授权的账户核对，确认是否共用学校租户。
5. 若返回结构不同，只提供脱敏字段结构用于适配，不提交真实账号或房间凭据。

## 校园卡

当前仅提供完美校园 App 入口，未编写原生校园卡余额/流水客户端。不能用“智能水电余额”替代校园卡余额，也不能把郑大 `ecard.v.zzu.edu.cn` 的任何接口改域名后当作河南工大接口。
