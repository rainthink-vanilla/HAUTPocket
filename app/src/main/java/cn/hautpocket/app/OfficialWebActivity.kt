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

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val url = intent.getStringExtra("url") ?: OfficialServices.TEACHING
        if (!OfficialServices.canBrowse(url)) { finish(); return }
        currentUrl = url
        val title = intent.getStringExtra("title") ?: "官方服务"
        val importEnabled = intent.getBooleanExtra("import", false)
        setContent { PocketTheme {
            Scaffold(
                topBar = { TopAppBar(title = { Column { Text(title); Text(host, style = MaterialTheme.typography.labelSmall) } },
                    navigationIcon = { IconButton(onClick = { finish() }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回") } },
                    actions = {
                        IconButton(onClick = { error = null; web?.reload() }) { Icon(Icons.Outlined.Refresh, "重新加载") }
                        IconButton(onClick = { external(currentUrl) }) { Icon(Icons.AutoMirrored.Outlined.OpenInNew, "用浏览器打开") }
                    }) },
                bottomBar = { if (importEnabled) Surface(tonalElevation = 3.dp) {
                    Column(Modifier.navigationBarsPadding().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("在学校页面登录，进入“学生课表查询”并选好学期。", style = MaterialTheme.typography.bodySmall)
                        Button(onClick = { importSchedule() }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                            Text(if (busy) "正在读取课表…" else "导入当前学期课表")
                        }
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
        CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val destination = request.url.toString()
                if (OfficialServices.canBrowse(destination)) return false
                error = "该链接超出已核实的服务域名。如学校使用外部认证，请用右上角浏览器继续。"
                return true
            }
            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                currentUrl = url
                host = runCatching { Uri.parse(url).host.orEmpty() }.getOrDefault("")
                loading = true
                if (busy) { importGeneration++; busy = false }
            }
            override fun onPageFinished(view: WebView, url: String) { loading = false }
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

    override fun onDestroy() {
        importGeneration++
        handler.removeCallbacksAndMessages(null)
        web?.stopLoading(); web?.destroy(); web = null
        super.onDestroy()
    }

    companion object {
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
