package cn.hautpocket.app.domain

import java.net.URI

object OfficialServices {
    const val TEACHING_HOST = "jwglxt.haut.edu.cn"
    // Enter through the service root so the school can reuse an existing session.
    const val TEACHING = "https://jwglxt.haut.edu.cn/"
    const val STUDENT_PORTAL = "https://mapp.haut.edu.cn/haut/"
    const val PORTAL = "https://mapp.haut.edu.cn/"
    const val LIBRARY_CATALOG = "https://libspace.haut.edu.cn/space/"
    // Observed on the user's device; authentication parameters must never be embedded.
    const val LIBRARY_SEATS = "https://wslib.haut.edu.cn/h5/index.html#/seatPage/SelectRoom/SelectRoom"
    const val LIBRARY_SEAT_HELP = "https://lib.haut.edu.cn/info/1162/12015.htm"
    const val LIBRARY = "https://lib.haut.edu.cn/"
    const val HELP = "https://xxb.haut.edu.cn/info/1032/16509.htm"
    const val CARD_HELP = "https://xxb.haut.edu.cn/info/1221/21459.htm"
    const val WANXIAO = "https://www.17wanxiao.com/"
    const val TEACHING_SCHEDULE_PATH = "/jwglxt/kbcx/xskbcxZccx_cxXskbcxIndex.html"

    fun isTeachingSchedule(url: String): Boolean = runCatching {
        canImportSchedule(url) && URI(url).path == TEACHING_SCHEDULE_PATH
    }.getOrDefault(false)

    /** Internal blank frames are normal in web login flows, not external sign-in pages. */
    fun canNavigate(url: String, mainFrame: Boolean): Boolean =
        canBrowse(url) || (!mainFrame && url in setOf("about:blank", "about:srcdoc"))

    /** Upgrade only the school's documented legacy teaching GET links; never replay a POST. */
    fun secureTeachingLink(url: String, method: String): String? = runCatching {
        val uri = URI(url)
        if (method != "GET" || uri.scheme != "http" || !uri.host.equals(TEACHING_HOST, true) ||
            uri.userInfo != null || (uri.port != -1 && uri.port != 80) ||
            !(uri.path.isNullOrEmpty() || uri.path == "/" || uri.path.startsWith("/jwglxt/"))) null
        else "https://$TEACHING_HOST" + uri.rawPath.orEmpty() +
            (uri.rawQuery?.let { "?$it" } ?: "") + (uri.rawFragment?.let { "#$it" } ?: "")
    }.getOrNull()

    /** Diagnostics must not expose a student's ID, credentials, tickets or callback parameters. */
    fun navigationOrigin(url: String): String = runCatching {
        val uri = URI(url)
        val scheme = uri.scheme?.lowercase()
        if (scheme !in setOf("http", "https")) "非网页链接"
        else "$scheme://${uri.host ?: return@runCatching "未知来源"}" +
            (if (uri.port == -1) "" else ":${uri.port}")
    }.getOrDefault("未知来源")

    fun isLibraryH5(url: String): Boolean = runCatching {
        val uri = URI(url)
        canBrowse(url) && uri.host.equals("wslib.haut.edu.cn", true) && uri.path == "/h5/index.html"
    }.getOrDefault(false)

    fun isLibraryLogin(url: String): Boolean = runCatching {
        val uri = URI(url)
        canBrowse(url) && uri.host.equals("mapp.haut.edu.cn", true) &&
            (uri.path.startsWith("/authentication/login/") || uri.path == "/cas/login")
    }.getOrDefault(false)

    fun isLibraryCatalog(url: String): Boolean = runCatching {
        val uri = URI(url)
        canBrowse(url) && uri.host.equals("libspace.haut.edu.cn", true) &&
            (uri.path == "/space/" || uri.path == "/mspace/index" || uri.path == "/mspace/search" || uri.path == "/mspace/searchList" || uri.path == "/mspace/searchAdvanced" ||
                Regex("^/mspace/searchDetailLocal/[A-Za-z0-9_-]+$").matches(uri.path))
    }.getOrDefault(false)

    fun canBrowse(url: String): Boolean = runCatching {
        val uri = URI(url)
        val host = uri.host?.lowercase().orEmpty()
        uri.scheme == "https" && uri.userInfo == null && (uri.port == -1 || uri.port == 443) &&
            (host == "haut.edu.cn" || host.endsWith(".haut.edu.cn") || host == "17wanxiao.com" || host.endsWith(".17wanxiao.com"))
    }.getOrDefault(false)

    fun canImportSchedule(url: String): Boolean = runCatching {
        val uri = URI(url)
        uri.scheme == "https" && uri.host.equals(TEACHING_HOST, ignoreCase = true) &&
            uri.userInfo == null && (uri.port == -1 || uri.port == 443) && uri.path.startsWith("/jwglxt/")
    }.getOrDefault(false)
}
