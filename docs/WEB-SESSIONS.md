# 学校网页登录会话

教务、图书查询及座位预约共用应用私有的 Android WebView profile。启用第一方 Cookie 与 DOM storage，页面加载结束及退出到后台时调用 `CookieManager.flush()` 保存会话；写盘在串行后台队列执行。应用不复制 Cookie、登录密码或认证令牌到自建文件，不自动填写或重放密码。

教务从 `https://jwglxt.haut.edu.cn/` 进入，由学校决定进入已有会话还是登录页；导入页提供学校公布的“一站式学生服务”入口 `https://mapp.haut.edu.cn/haut/`。如果教务独立登录提示账号密码不正确，可通过该入口按学校正常流程进入教务。应用不修改学校登录表单的加密算法，也不代替学校检查密码。

HTTPS 学校域名的导航继续交给 WebView；内部空白子框架不再产生整页“外部认证”错误。学校公布的旧 HTTP 教务 GET 链接转换为同站 HTTPS，只限默认端口及教务路径；不重放 POST、不全局放行 HTTP、不忽略证书错误。未核实的顶层跳转会显示脱敏的协议、域名和端口，隐藏路径、账号及认证参数，便于进一步核查。

真机观察到 N9 课表查询路径 `/jwglxt/kbcx/xskbcxZccx_cxXskbcxIndex.html` 已返回内容，但 `html/body` 的 `height:100%; overflow:hidden` 在该 WebView 环境计算为 0 高，造成整页白屏。`teaching-layout.js` 仅在该 HTTPS 来源及路径恢复自动高度、视口最小高度和滚动；登录页及其他教务页面不注入，不改课程数据或请求。

在“校园 → 登录会话”中可以主动清除。操作等待系统异步删除 Cookie 完成，清除网页存储并写盘后再提示成功。已导入课表及宿舍本地记录不受影响。

服务器主动注销、修改密码、会话到期、学校风控、第三方认证策略以及清除应用数据仍可能要求重新认证。应用尽量复用有效会话，不能保证永久免登录；外部浏览器、云上工大与本应用的会话也不自动共享。

参考：[Android CookieManager](https://developer.android.com/reference/android/webkit/CookieManager#flush())、[WebViewClient](https://developer.android.com/reference/android/webkit/WebViewClient#shouldOverrideUrlLoading(android.webkit.WebView,%20android.webkit.WebResourceRequest))、[Chromium WebView Cookie 实现](https://chromium.googlesource.com/chromium/src/+/refs/tags/133.0.6943.137/android_webview/browser/cookie_manager.cc)和[学校一站式入口说明](https://xxb.haut.edu.cn/info/1032/16559.htm)。
