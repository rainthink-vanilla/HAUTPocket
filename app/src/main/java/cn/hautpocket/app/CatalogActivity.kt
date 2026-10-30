package cn.hautpocket.app

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.http.SslError
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.webkit.*
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Density
import androidx.compose.ui.viewinterop.AndroidView
import cn.hautpocket.app.data.PocketStore
import cn.hautpocket.app.domain.*
import cn.hautpocket.app.ui.CatalogScreen
import cn.hautpocket.app.ui.PocketTheme
import org.json.JSONObject
import org.json.JSONTokener

/** Native search and reading UI; the same official session remains available for advanced actions. */
class CatalogActivity : ComponentActivity() {
    private var engine: WebView? = null
    private val handler = Handler(Looper.getMainLooper())
    private var snapshot by mutableStateOf(CatalogSnapshot())
    private var query by mutableStateOf("")
    private var busy by mutableStateOf(false)
    private var error by mutableStateOf<String?>(null)
    private var official by mutableStateOf(false)
    private var pendingSearch: String? = null
    private var executing = false
    private var generation = 0
    private var busySince = 0L
    private var expectedBookCount = -1
    private var pendingDetail = false
    private var selectedBook: CatalogBook? = null
    private var active = false
    private val snapshotScript by lazy { assets.open("catalog-snapshot.js").bufferedReader().use { it.readText() } }
    private val poll = object : Runnable {
        override fun run() { if (active) { readPage(); handler.postDelayed(this, 800) } }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appearance = PocketStore(this).load()
        setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, density.fontScale * appearance.fontScale / 100f)) {
                PocketTheme(appearance.themeColor, appearance.textColor, appearance.reduceMotion) {
                    Box(Modifier.fillMaxSize()) {
                        // Keep the official document at its normal viewport for its own pagination logic.
                        // Touches are consumed while the native UI covers it; no invisible actions can fire.
                        AndroidView(factory = { createEngine() }, update = { it.isFocusable = official; it.isFocusableInTouchMode = official }, modifier = Modifier.fillMaxSize())
                        if (official) Column(Modifier.fillMaxWidth().onSizeChanged { engine?.setPadding(0, it.height, 0, 0) }) { TopAppBar(title = { Text("完整检索") }, navigationIcon = {
                            IconButton(onClick = { official = false }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回应用界面") }
                        }, actions = {
                            IconButton(onClick = { engine?.reload() }) { Icon(Icons.Outlined.Refresh, "刷新") }
                            TextButton(onClick = { official = false; readPage() }) { Text("返回新版") }
                        }) }
                        else Surface(Modifier.fillMaxSize()) {
                            SideEffect { engine?.setPadding(0, 0, 0, 0); engine?.clearFocus() }
                            CatalogScreen(snapshot, query, { query = it.take(200) }, busy, error, ::search, ::openBook, ::more,
                                ::back, { official = true })
                        }
                    }
                }
            }
            BackHandler { back() }
        }
    }

    @SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
    private fun createEngine(): WebView = WebView(this).apply {
        engine = this
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        isFocusable = false; isFocusableInTouchMode = false
        setOnTouchListener { _, _ -> !official }
        OfficialWebSession.configure(this)
        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                if (OfficialServices.canNavigate(request.url.toString(), request.isForMainFrame)) return false
                if (request.isForMainFrame) error = "已阻止未核实的页面跳转（${OfficialServices.navigationOrigin(request.url.toString())}）。请使用学校官方入口。"
                return true
            }
            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) { generation++; executing = false; error = null }
            override fun onPageFinished(view: WebView, url: String) { OfficialWebSession.persist(); readPage() }
            override fun onReceivedSslError(view: WebView, sslHandler: SslErrorHandler, sslError: SslError) { sslHandler.cancel(); error = "学校网页证书校验失败"; busy = false }
            override fun onReceivedError(view: WebView, request: WebResourceRequest, failure: WebResourceError) {
                if (request.isForMainFrame) { error = "无法连接馆藏系统，请检查网络后重试。"; busy = false }
            }
            override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                if (request.isForMainFrame) { error = "馆藏系统返回 HTTP ${response.statusCode}"; busy = false }
            }
        }
        loadUrl(OfficialServices.LIBRARY_CATALOG)
    }

    private fun begin() { busy = true; busySince = System.currentTimeMillis(); error = null }
    private fun search(value: String) {
        val keyword = value.trim().take(200)
        if (keyword.isEmpty() || busy) return
        query = keyword; pendingSearch = keyword; pendingDetail = false; expectedBookCount = -1; selectedBook = null
        snapshot = CatalogSnapshot(); begin()
        engine?.loadUrl("https://libspace.haut.edu.cn/mspace/search")
    }
    private fun openBook(book: CatalogBook) {
        if (busy) return
        begin(); pendingDetail = true; selectedBook = book
        val script = """
            (()=>{if(location.origin!=='https://libspace.haut.edu.cn'||location.pathname!=='/mspace/searchList')return false;
            const rows=[...document.querySelectorAll('.search-list-wrapper .book-list .van-list > .van-cell')].filter(x=>x.querySelector('.book-info'));
            const row=rows[${book.index}];const title=row?.querySelector('.book-info .title span')||row?.querySelector('.book-info .title');
            if(!title||title.textContent.replace(/\s+/g,' ').trim()!==${JSONObject.quote(book.title)})return false;
            title.click();return true;})()
        """.trimIndent()
        engine?.evaluateJavascript(script) { result -> if (result != "true") { busy = false; pendingDetail = false; error = "书目列表已更新，请重新选择。" } }
    }
    private fun more() {
        if (busy) return
        begin(); expectedBookCount = snapshot.books.size
        engine?.evaluateJavascript("if(location.origin==='https://libspace.haut.edu.cn'&&location.pathname==='/mspace/searchList'){window.scrollTo(0,document.documentElement.scrollHeight);window.dispatchEvent(new Event('scroll'));}", null)
    }
    private fun back() {
        if (official) { official = false; readPage(); return }
        pendingSearch = null; pendingDetail = false; expectedBookCount = -1; busy = false; error = null
        if (snapshot.kind == "detail" && engine?.canGoBack() == true) engine?.goBack() else finish()
    }
    private fun readPage() {
        val web = engine ?: return
        if (busy && System.currentTimeMillis() - busySince > 20_000) {
            busy = false; pendingSearch = null; pendingDetail = false; expectedBookCount = -1
            error = "馆藏系统暂未返回新数据，可重试或进入完整检索。"
        }
        if (executing) return
        if (!OfficialServices.isLibraryCatalog(web.url.orEmpty())) {
            if (!web.url.isNullOrBlank()) { snapshot = CatalogSnapshot(kind = "official"); busy = false }
            return
        }
        val currentGeneration = generation
        executing = true
        val keyword = pendingSearch
        if (keyword != null && android.net.Uri.parse(web.url).path == "/mspace/search") {
            web.evaluateJavascript("""
                (()=>{if(location.origin!=='https://libspace.haut.edu.cn'||location.pathname!=='/mspace/search')return false;
                const vm=document.querySelector('#header')?.__vue__;
                if(vm?.${'$'}options.name!=='AlphaCommonHeader'||typeof vm.onSearch!=='function')return false;
                vm.searchWord=${JSONObject.quote(keyword)};vm.onSearch();return true;})()
            """.trimIndent()) { result ->
                if (pendingSearch == keyword && result == "true") pendingSearch = null
                executing = false
            }
            return
        }
        web.evaluateJavascript(snapshotScript) { raw ->
            executing = false
            if (currentGeneration != generation || isDestroyed) return@evaluateJavascript
            runCatching {
                val decoded = JSONTokener(raw).nextValue() as? String ?: return@runCatching
                val next = CatalogSnapshot.parse(decoded)
                if (pendingSearch != null) return@runCatching
                if (busy && pendingDetail && next.kind != "detail" && next.kind != "official") return@runCatching
                snapshot = if (next.kind == "detail" && next.coverUrl.isEmpty())
                    next.copy(coverUrl = selectedBook?.takeIf { it.title == next.title }?.coverUrl.orEmpty()) else next
                if (next.kind == "detail") { busy = false; pendingDetail = false; error = null }
                else if (next.kind == "results" && !next.loading && (next.books.isNotEmpty() || next.empty || next.finished)) {
                    if (expectedBookCount < 0 || next.books.size > expectedBookCount || next.finished) { busy = false; expectedBookCount = -1; error = null }
                } else if (next.kind == "official") busy = false
            }.onFailure { error = "书目数据结构变化，暂时无法读取。可打开完整检索继续。"; busy = false }
        }
    }
    override fun onStart() { super.onStart(); active = true; handler.post(poll) }
    override fun onStop() { active = false; handler.removeCallbacks(poll); OfficialWebSession.persist(); super.onStop() }
    override fun onDestroy() { generation++; handler.removeCallbacksAndMessages(null); engine?.destroy(); engine = null; super.onDestroy() }
}
