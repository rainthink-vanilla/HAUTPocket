package cn.hautpocket.app

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.webkit.*
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import cn.hautpocket.app.data.PocketStore
import cn.hautpocket.app.domain.*
import cn.hautpocket.app.ui.PocketTheme
import cn.hautpocket.app.ui.ScheduleImportDialog
import org.json.JSONObject
import org.json.JSONTokener

class OfficialWebActivity : ComponentActivity() {
    private var web: WebView? = null
    private val handler = Handler(Looper.getMainLooper())
    private var busy by mutableStateOf(false)
    private var loading by mutableStateOf(true)
    private var error by mutableStateOf<String?>(null)
    private var host by mutableStateOf("")
    private var preview by mutableStateOf<ScheduleParser.Result?>(null)
    private var currentUrl: String = ""
    private var importGeneration = 0
    private var libraryFlow = false
    private var catalogFlow = false
    private var libraryAuthPending = false
    private var libraryCanContinue by mutableStateOf(false)
    private var libraryRedesigned by mutableStateOf(true)
    private var designStatus by mutableStateOf("正在加载页面")
    private var designGeneration = 0

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val url = intent.getStringExtra("url") ?: OfficialServices.TEACHING
        if (!OfficialServices.canBrowse(url)) { finish(); return }
        currentUrl = url
        libraryFlow = OfficialServices.isLibraryH5(url)
        catalogFlow = OfficialServices.isLibraryCatalog(url)
        val designPreference = if (catalogFlow) "catalog-redesigned" else "redesigned"
        libraryRedesigned = getSharedPreferences("library-ui", MODE_PRIVATE).getBoolean(designPreference, true)
        val title = intent.getStringExtra("title") ?: "官方服务"
        val importEnabled = intent.getBooleanExtra("import", false)
        val appearance = PocketStore(this).load()
        setContent { PocketTheme(appearance.themeColor, appearance.textColor) {
            Scaffold(
                topBar = { Column { TopAppBar(title = { Column { Text(title); Text(host, style = MaterialTheme.typography.labelSmall) } },
                    navigationIcon = { IconButton(onClick = { finish() }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回") } },
                    actions = {
                        IconButton(onClick = { error = null; web?.reload() }) { Icon(Icons.Outlined.Refresh, "重新加载") }
                        IconButton(onClick = { external(currentUrl) }) { Icon(Icons.AutoMirrored.Outlined.OpenInNew, "用浏览器打开") }
                    })
                    if (libraryFlow || catalogFlow) Surface(tonalElevation = 2.dp) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("新版界面", style = MaterialTheme.typography.labelLarge)
                                Text(if (libraryRedesigned) designStatus else "已关闭 · 使用原版", style = MaterialTheme.typography.labelSmall)
                            }
                            Switch(checked = libraryRedesigned, onCheckedChange = { enabled ->
                                libraryRedesigned = enabled
                                getSharedPreferences("library-ui", MODE_PRIVATE).edit().putBoolean(designPreference, enabled).apply()
                                applyLibraryDesign()
                            })
                        }
                    }
                } },
                bottomBar = { if (importEnabled) Surface(tonalElevation = 3.dp) {
                    Column(Modifier.navigationBarsPadding().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("在学校页面登录，进入“学生课表查询”并选好学期。", style = MaterialTheme.typography.bodySmall)
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Text("登录状态会自动保存；过期后需重新认证。", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall)
                            TextButton(onClick = { web?.loadUrl(OfficialServices.STUDENT_PORTAL) }) { Text("统一认证入口") }
                        }
                        Button(onClick = { importSchedule() }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                            Text(if (busy) "正在读取课表…" else "导入当前学期课表")
                        }
                    }
                } else if (libraryFlow) Surface(tonalElevation = 3.dp) {
                    Row(Modifier.navigationBarsPadding().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Text(if (libraryCanContinue) "座位状态以当前查询时段为准" else "请在学校页面完成登录", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        TextButton(onClick = { web?.evaluateJavascript(LIBRARY_CONTINUE_SCRIPT, null) }, enabled = libraryCanContinue) { Text("区域列表") }
                    }
                } },
            ) { padding ->
                Column(Modifier.padding(padding)) {
                    if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                    error?.let { message -> Surface(color = MaterialTheme.colorScheme.errorContainer) {
                        Text(message, Modifier.fillMaxWidth().padding(12.dp), style = MaterialTheme.typography.bodySmall)
                    } }
                    AndroidView(factory = { context -> createWebView(context, url) }, modifier = Modifier.fillMaxSize())
                }
            }
            BackHandler { if (web?.canGoBack() == true) web?.goBack() else finish() }
            preview?.let { result -> ScheduleImportDialog(result, PocketStore(this).load().courses.isNotEmpty(), { preview = null }) { mode, calendar ->
                    runCatching {
                        val store = PocketStore(this)
                        val old = store.load()
                        store.save(ScheduleImport.apply(old, result, mode, calendar, System.currentTimeMillis()))
                    }.onSuccess { Toast.makeText(this, "已导入课表，可离线查看", Toast.LENGTH_SHORT).show(); finish() }
                        .onFailure { error = it.message ?: "本地保存失败"; preview = null }
            } }
        } }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(context: Context, url: String): WebView = WebView(context).apply {
        web = this
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        settings.setSupportMultipleWindows(false)
        OfficialWebSession.configure(this)
        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val destination = request.url.toString()
                if (OfficialServices.canNavigate(destination, request.isForMainFrame)) return false
                (if (request.isForMainFrame) OfficialServices.secureTeachingLink(destination, request.method) else null)?.let { secure ->
                    view.post { view.loadUrl(secure) }
                    return true
                }
                if (request.isForMainFrame) error = "已阻止未核实的页面跳转（${OfficialServices.navigationOrigin(destination)}）。请核对学校认证入口后重试。"
                return true
            }
            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                designGeneration++
                designStatus = "正在加载页面"
                currentUrl = url
                host = runCatching { Uri.parse(url).host.orEmpty() }.getOrDefault("")
                loading = true
                error = null
                libraryCanContinue = libraryFlow && OfficialServices.isLibraryH5(url)
                if (libraryFlow && OfficialServices.isLibraryLogin(url)) libraryAuthPending = true
                if (busy) { importGeneration++; busy = false }
            }
            override fun onPageFinished(view: WebView, url: String) {
                loading = false
                OfficialWebSession.persist()
                applyTeachingLayout()
                applyLibraryDesign()
                if (libraryFlow && libraryAuthPending && OfficialServices.isLibraryH5(url)) {
                    libraryAuthPending = false
                    // Allow the official H5 app to consume its own authentication callback.
                    handler.postDelayed({ web?.evaluateJavascript(LIBRARY_RETURN_SCRIPT, null) }, 1200)
                }
            }
            override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
                currentUrl = url
                host = Uri.parse(url).host.orEmpty()
                handler.post { applyTeachingLayout() }
                // History API navigation does not necessarily emit onPageFinished.
                if (libraryFlow || catalogFlow) handler.post { applyLibraryDesign() }
            }
            override fun onReceivedSslError(view: WebView, sslHandler: SslErrorHandler, sslError: SslError) {
                sslHandler.cancel()
                error = "学校网页证书校验失败，已停止连接。请稍后重试。"
                loading = false
            }
            override fun onReceivedError(view: WebView, request: WebResourceRequest, failure: WebResourceError) {
                if (request.isForMainFrame) { error = "页面暂时无法打开，请检查校园网或使用学校 WebVPN。"; loading = false }
            }
            override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                if (request.isForMainFrame) { error = "服务返回 HTTP ${response.statusCode}，可稍后重试或使用官方 App。"; loading = false }
            }
        }
        loadUrl(url)
    }

    private fun applyTeachingLayout() {
        if (OfficialServices.isTeachingSchedule(web?.url.orEmpty())) {
            val script = assets.open("teaching-layout.js").bufferedReader().use { it.readText() }
            web?.evaluateJavascript(script, null)
        }
    }

    private fun applyLibraryDesign() {
        if (!libraryFlow && !catalogFlow) return
        val current = web?.url.orEmpty()
        if (!((libraryFlow && OfficialServices.isLibraryH5(current)) || (catalogFlow && OfficialServices.isLibraryCatalog(current)))) {
            designStatus = "此页面保留官方界面"
            return
        }
        val generation = ++designGeneration
        designStatus = if (libraryRedesigned) "正在应用新版界面" else "已关闭 · 使用原版"
        web?.evaluateJavascript(LibraryPageDesign.script(this, libraryRedesigned, catalogFlow)) { raw ->
            if (generation != designGeneration || isDestroyed) return@evaluateJavascript
            val result = runCatching { JSONTokener(raw).nextValue() as? String }.getOrNull()
            designStatus = when (result) {
                "applied" -> "新版样式已应用"
                "disabled" -> "已关闭 · 使用原版"
                "unsupported" -> "此页面保留官方界面"
                "waiting" -> "页面准备中，可稍后刷新"
                else -> "样式未加载，请刷新重试"
            }
        }
    }

    private fun importSchedule() {
        if (!OfficialServices.canImportSchedule(web?.url.orEmpty())) {
            error = "请先在河南工业大学教务系统登录并进入学生课表查询。"
            return
        }
        busy = true; error = null
        val generation = ++importGeneration
        web?.evaluateJavascript(IMPORT_SCRIPT, null)
        pollResult(generation, 0)
    }

    private fun pollResult(generation: Int, attempt: Int) {
        handler.postDelayed({
            if (generation != importGeneration || isFinishing || isDestroyed) return@postDelayed
            if (!OfficialServices.canImportSchedule(web?.url.orEmpty())) { busy = false; error = "页面已切换，请重新进入课表查询。"; return@postDelayed }
            web?.evaluateJavascript("JSON.stringify(window.__hautPocketImport || {state:'waiting'})") { raw ->
                if (generation != importGeneration) return@evaluateJavascript
                runCatching {
                    val decoded = JSONTokener(raw).nextValue() as? String ?: throw IllegalArgumentException("无法读取网页")
                    val message = JSONObject(decoded)
                    when (message.optString("state")) {
                        "ready" -> {
                            val state = PocketStore(this).load()
                            preview = ScheduleParser.parse(message.getString("payload"), state.campus)
                            busy = false
                            web?.evaluateJavascript("delete window.__hautPocketImport", null)
                        }
                        "error" -> { busy = false; error = "读取失败。请确认已登录，并在学生课表查询中选好学年和学期。" }
                        else -> if (attempt < 40) pollResult(generation, attempt + 1) else { busy = false; error = "读取超时，请稍后重试。" }
                    }
                }.onFailure { busy = false; error = it.message ?: "课表解析失败，原课表已保留。" }
            }
        }, 500)
    }

    private fun external(url: String) {
        if (!OfficialServices.canBrowse(url)) return
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            .onFailure { Toast.makeText(this, "未找到可用浏览器", Toast.LENGTH_SHORT).show() }
    }

    override fun onStop() {
        OfficialWebSession.persist()
        super.onStop()
    }

    override fun onDestroy() {
        importGeneration++
        handler.removeCallbacksAndMessages(null)
        web?.stopLoading(); web?.destroy(); web = null
        super.onDestroy()
    }

    companion object {
        // Navigate inside the official document. Do not reconstruct or read token-bearing URLs.
        private const val LIBRARY_CONTINUE_SCRIPT = """
            (() => {
              if (location.origin !== 'https://wslib.haut.edu.cn' || location.pathname !== '/h5/index.html') return;
              location.hash = '/seatPage/SelectRoom/SelectRoom';
            })();
        """
        private const val LIBRARY_RETURN_SCRIPT = """
            (() => {
              if (location.origin !== 'https://wslib.haut.edu.cn' || location.pathname !== '/h5/index.html') return;
              if (location.hash && location.hash !== '#' && location.hash !== '#/') return;
              location.hash = '/seatPage/SelectRoom/SelectRoom';
            })();
        """
        fun open(context: Context, title: String, url: String, allowImport: Boolean = false) {
            context.startActivity(Intent(context, OfficialWebActivity::class.java).putExtra("title", title).putExtra("url", url).putExtra("import", allowImport))
        }
        // Only reads the student's selected semester; never reads password, cookies, or token fields.
        private val IMPORT_SCRIPT = """
            (() => {
              if (location.protocol !== 'https:' || location.hostname !== 'jwglxt.haut.edu.cn') return;
              window.__hautPocketImport = {state:'waiting'};
              (async () => {
                const controller = new AbortController();
                const timer = setTimeout(() => controller.abort(), 15000);
                try {
                  let doc = document;
                  const value = (d, name) => d.querySelector('[name="'+name+'"],#'+name)?.value;
                  if (!value(doc, 'xnm') || !value(doc, 'xqm')) {
                    const page = await fetch('/jwglxt/kbcx/xskbcx_cxXskbcxIndex.html?gnmkdm=N2151', {credentials:'same-origin',signal:controller.signal});
                    doc = new DOMParser().parseFromString(await page.text(),'text/html');
                  }
                  const year = value(doc,'xnm'); const term = value(doc,'xqm');
                  if (!/^\d{4}$/.test(year||'') || !/^\d{1,2}$/.test(term||'')) throw new Error('semester');
                  const response = await fetch('/jwglxt/kbcx/xskbcx_cxXsKb.html?gnmkdm=N2151', {
                    method:'POST',credentials:'same-origin',signal:controller.signal,
                    headers:{'Content-Type':'application/x-www-form-urlencoded;charset=UTF-8','X-Requested-With':'XMLHttpRequest'},
                    body:new URLSearchParams({xnm:year,xqm:term}).toString()
                  });
                  if(!response.ok) throw new Error('http');
                  const data = await response.json();
                  if(!Array.isArray(data.kbList)) throw new Error('format');
                  window.__hautPocketImport = {state:'ready',payload:JSON.stringify({kbList:data.kbList})};
                } catch (_) { window.__hautPocketImport = {state:'error'}; }
                finally { clearTimeout(timer); }
              })();
            })();
        """.trimIndent()
    }
}
