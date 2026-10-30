# 0.9.0 界面升级

本轮参考用户提供的五份资源，延续清爽、易读的校园助手定位，使用既有Kotlin Compose界面实现。不是将五个网页项目整体移植到Android。

## 设计落地

| 参考 | 本项目采用方式 |
|---|---|
| [Apple Design / HIG](https://developer.apple.com/design/) | 明确标题层级、分组设置、稳定文字对比、较大的可点击控件、简短可关闭的动效 |
| [Lucian UX/UI Prompt](https://github.com/Seokwoooo/lucian-uxui-prompt) | 统一卡片、图标底板、状态与主操作层级；参考布局思路，未复制源码 |
| [morphicons](https://github.com/guillermolg00/morphicons) | 导航选中以实心图标、胶囊底板、短促弹性缩放反馈；并非接入其通用SVG变形引擎 |
| [Rare UI](https://github.com/swamimalode07/rare-ui) | 轻量淡入与细节反馈，避免持续动画影响课程和读数阅读；未复制组件 |
| [Macintosh Skeletal Filled](https://github.com/npwitk/Macintosh-Skeletal-Filled) | 借鉴欢迎语的人情味，以系统中文字体实现“你好，工大”；未打包Apple衍生字体 |
| [prettymaps](https://github.com/marceloprates/prettymaps) | 借鉴克制的底色与强调色搭配。本轮没有增加地图，未使用其代码、地图数据或生成图片 |

苹果[动效指南](https://developer.apple.com/design/human-interface-guidelines/motion)要求动效服务于状态和反馈，避免无意义运动，并允许用户选择；[可访问性指南](https://developer.apple.com/design/human-interface-guidelines/accessibility)强调可感知、可适应的内容。Android版本采用平台控件与已有字号/文字颜色设置，不能据此宣称完成全部Apple无障碍标准认证。

## 页面与行为

- 首页：欢迎标题与日期，突出今日课程，统一快捷服务卡片与图标。
- 底栏：圆角浮动导航，保留今天、课表、宿舍、图书馆、校园五项；选中状态有图形、底板和文字粗细反馈。点击顶栏校区可进入设置。
- 课表：统一日列表和周表字体、圆角与对比，保留连续同课程合并与冲突展示。
- 宿舍：优先展示用电趋势，图表增加淡色面积与选中光圈；真实缺失处仍断线。采样帮助默认收起，手工记录保留原入口。
- 图书馆：阅读主题入口；独立图书查询同步新主题与卡片，保留搜索、真实馆藏、封面预览与缺图说明。
- 设置：校区、字体、主题与文字、交互、课表管理、采样分组。新增“减少动效”，兼容旧设置数据。

配色、字号和圆角由共享Theme维护，无新增UI库或资源依赖；学校网页登录、课表解析、水电查询与后台采样业务不作本轮改造。

## 0.9.1 调色与专注提醒

主题和文字调色盘包含可拖动HSV色板、色相条、常用色、HEX输入、精细滑块和对比提示。对应用采用临时配色预览，点应用才写入本机，关闭或取消恢复；即使选中低对比颜色，调色盘操作控件保持可读。自定义主题的按钮文字按亮度使用黑/白字，主题容器按当前明暗表面生成。

专注使用系统AlarmManager与NotificationManager，不以WorkManager周期任务模拟准点提醒，不增加常驻前台服务。Android13+本人选择通知权限；Android12+未允许准时提醒时使用非精确闹钟并在界面说明可能延迟。非导出Receiver校验会话结束时间，前台和后台共用幂等完成逻辑；取消撤销闹钟，重启/应用更新后恢复。超过15分钟的过期会话只恢复完成记录，不补发过时提醒。“10秒测试提醒”不修改专注完成记录。

参考：[Android系统闹钟](https://developer.android.com/develop/background-work/services/alarms)、[通知运行时权限](https://developer.android.com/develop/ui/compose/notifications/notification-permission)。强行停止或厂商省电策略仍可能影响送达，不能保证任何系统条件下准点。

## 0.9.2 提醒状态与后台设置

到点广播增加FLAG_RECEIVER_FOREGROUND，重建PendingIntent以应用新标志；这是广播调度优先级，不是常驻前台服务。新增本机计划、广播接收、提交通知的时间及接收时是否后台，仅保存本应用元数据。“查看测试状态与提醒设置”提供精确通知渠道及应用信息入口，区分通知栏送达与悬浮横幅显示。

用户手机实测：仅通知/精确闹钟授权时，桌面仍收不到。本人允许后台自启动并将省电策略设为无限制后，测试广播在后台于计划后8ms接收、再过4ms提交通知；系统通知记录存在，用户明确确认桌面收到。两项后台设置一起调整，未分别隔离其影响。应用内增加小米/澎湃OS说明，不代改系统设置。
