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


## 2026-10-30 图书馆入口核实

- 官网 https://lib.haut.edu.cn/index.htm 的“馆藏书目检索”实际指向 https://libspace.haut.edu.cn/space/ ，本版通过已有安全WebView加载；公网探测超时，需校园网和设备验证。
- 较新的2025-04-01通知 https://lib.haut.edu.cn/info/1162/12015.htm 要求预约统一从云上工大首页“图书馆空间预约”进入；因此不沿用旧通知的微信小程序，也不猜测未公开的App内部深链。新“图书馆”页的座位预约按钮唤起官方App，尚不能直达原生选座页面。
- UI只聚合两个功能入口；书目结果及预约流程仍由官方系统提供。不会自动携带教务凭据到预约系统。
- Android周期任务并非精确闹钟：https://developer.android.com/reference/androidx/work/PeriodicWorkRequest 。0.5.0通过提高检查频率、保留白天最近样本降低漏整天的概率，并不承诺手机后台准点。


## 2026-10-30 0.5.1 手机跳转日志验证（取代上节预约仅唤起App的限制）

连接用户安卓手机，读取云上工大1.3.8当前进程的近期跳转日志，脱敏后发现实际H5地址：

- 预约首页：`https://wslib.haut.edu.cn/h5/index.html#/pages/SeatBooking/SeatBooking`
- 区域列表：`https://wslib.haut.edu.cn/h5/index.html#/seatPage/SelectRoom/SelectRoom`
- 原应用跳转含token参数；应用只采用不带参数的官方地址，不复制或硬编码令牌。

用户手机Edge浏览器加载不带token的预约首页成功；用户明确确认点“座位预约”可进入选座列表。0.5.1内嵌区域列表页面，保留云上工大备用入口。PC公网请求TLS连接失败，因此不能据此判断手机端不可达。尚未提交预约，也未验证独立WebView首次认证；不声称已全程预约联调。

本次是应用跳转日志读取，未配置HTTPS中间人代理，未安装证书，未修改手机网络代理。没有将原始日志、令牌、手机截图或官方APK纳入交付源码。


## 0.5.3 预约认证边界

用户进一步测试发现公开列表不代表已登录：确定预约时进入学校mapp认证。本人完成认证后返回官方H5根首页，URL由校方附加会话令牌。原生应用不读取令牌，只在确认从学校登录返回官方H5时尝试一次hash导航到区域列表；可通过“继续选座”手动导航。学校网页的cookie/localStorage继续由原有WebView管理。完整实际提交预约未执行。

复用Android WebView.evaluateJavascript与已有官方域名白名单；新增判定限制在精确的图书馆H5文档与已观察到的学校CAS登录路径。外部域名、HTTP、非标准端口与其他文档不触发回跳。


## 0.6.0 展示层适配

`assets/library-design.js`与CSS在精确的官方H5文档和SelectRoom/SelectSeat两个路由内运行，通过原始类名增强布局，未调用非官方预约API或创建JavaScript原生桥。只读区域/座位标签、公开余量及官方状态图例；自有输入仅用于过滤已加载项。data URL图标与图例图像按相同src匹配，未知状态保留官方图标。原版切换移除新增控件并取消样式作用域，原始节点与事件始终保留。

校方DOM变更需重新核查；右上角可回到原版。图书馆网页会在某些导航或刷新时重载，因此每次onPageFinished重新配置展示增强；同文档路由变动使用hashchange/MutationObserver适配。脚本不读取账号、密码、Cookie或令牌，不自动查询座位API、不提交预约。


## 0.7.0 图书查询与文字颜色

实际移动馆藏入口为https://libspace.haut.edu.cn/mspace/search，结果为/mspace/searchList，详情为/mspace/searchDetailLocal/书目ID，原入口/space/保留作重定向入口。新增catalog-design资源只覆盖已核实检索路由，不覆盖登录与个人页面；使用现有Vant搜索、筛选、排序、分页和纸本馆藏弹层。新增统计只表示已加载项，不能替代校方全量查询。

TextColors负责自有界面与WebView的正文/辅助文字配色。预设针对明暗背景分别设置；自定义HEX经过规范化后写入CSS变量，不允许CSS片段。原版网页不受自定义颜色覆盖。用户明确确认的智能水电今日用量为kWh，历史raw不迁移，图表统一显示kWh；余额和其他设备类型仍不擅自推断单位。

## 0.8.0 独立图书查询

底栏图书馆的查书入口改为CatalogActivity，使用Compose Material3展示搜索框、书目列表和馆藏详情。底层保留同一官方WebView会话，用户可通过“完整检索”完成高级筛选、查看地图或学校认证，再返回应用界面。

catalog-snapshot.js只在已核实的libspace.haut.edu.cn检索路由提取公开DOM字段，不读取登录表单、Cookie、存储或令牌。搜索由已核实的官方AlphaCommonHeader.onSearch处理；书目选择校验原始行号和标题后触发原节点，更多结果沿用官方滚动加载。无自建馆藏API、无伪造数据、无借阅提交操作。学校DOM或组件变化会导致适配失败，此时显示错误并提供完整检索入口。官网前置认证仍可能阻止搜索，需要本人在官方页面继续，不能绕过。

封面使用官网当前结果中的img来源，允许libspace.haut.edu.cn、ddimg.cn子域与img.hwlsp.cn的无查询参数HTTPS图片，不把WebView凭据传给图片请求。缺图或失败保留图书图标；官网自身提供占位图时沿用其内容。点击封面放大预览；详情无图片节点时，仅在书名一致时沿用用户选中结果的封面。复用[Coil](https://github.com/coil-kt/coil/tree/2.7.0)的AsyncImage加载、缩放与缓存，2.7.0使用Kotlin2.0与Compose1.6，兼容本项目既有工具链，未为图片加载升级应用框架。
