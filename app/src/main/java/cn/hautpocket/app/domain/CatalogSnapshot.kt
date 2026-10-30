package cn.hautpocket.app.domain

import org.json.JSONObject
import java.net.URI

data class CatalogBook(val index: Int, val title: String, val bibliography: String, val summary: String, val resources: String, val coverUrl: String = "")
data class CatalogHolding(val location: String, val lines: List<String>, val status: String, val shelf: String)
data class CatalogSnapshot(
    val kind: String = "loading", val books: List<CatalogBook> = emptyList(), val recommendations: List<String> = emptyList(),
    val title: String = "", val bibliography: String = "", val description: String = "", val holdings: List<CatalogHolding> = emptyList(),
    val finished: Boolean = false, val empty: Boolean = false, val loading: Boolean = false,
    val coverUrl: String = "",
) {
    companion object {
        fun parse(text: String): CatalogSnapshot {
            require(text.length <= 1_000_000) { "图书数据过大" }
            val root = JSONObject(text)
            val kind = root.getString("kind")
            require(kind in listOf("home", "results", "detail", "loading", "official")) { "无法识别图书页面" }
            fun strings(name: String): List<String> = root.optJSONArray(name)?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList()
            val books = root.optJSONArray("books")?.let { a -> (0 until minOf(a.length(),200)).map { i ->
                val b = a.getJSONObject(i)
                require(b.getInt("index") >= 0 && b.getString("title").isNotBlank()) { "图书条目缺少标题" }
                CatalogBook(b.getInt("index"), b.getString("title"), b.optString("bibliography"), b.optString("summary"), b.optString("resources"), cover(b.optString("coverUrl")))
            } } ?: emptyList()
            val holdings = root.optJSONArray("holdings")?.let { a -> (0 until minOf(a.length(),150)).map { i ->
                val h = a.getJSONObject(i); val lines = h.optJSONArray("lines")
                CatalogHolding(h.optString("location"), lines?.let { l -> (0 until l.length()).map { l.getString(it) } } ?: emptyList(), h.optString("status"), h.optString("shelf"))
            } } ?: emptyList()
            return CatalogSnapshot(kind, books, strings("recommendations"), root.optString("title"), root.optString("bibliography"), root.optString("description"), holdings,
                root.optBoolean("finished"), root.optBoolean("empty"), root.optBoolean("loading"), cover(root.optString("coverUrl")))
        }

        // Only public HTTPS images observed in the official catalog. Never forward WebView credentials.
        fun cover(value: String): String = runCatching {
            val uri = URI(value)
            val host = uri.host?.lowercase().orEmpty()
            require(value.length <= 2048 && uri.scheme == "https" && uri.userInfo == null && uri.rawQuery == null && uri.fragment == null && (uri.port == -1 || uri.port == 443))
            require(host == "libspace.haut.edu.cn" || host == "img.hwlsp.cn" || host == "ddimg.cn" || host.endsWith(".ddimg.cn"))
            value
        }.getOrDefault("")
    }
}
