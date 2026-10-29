package cn.hautpocket.app.domain

import java.net.URI

object OfficialServices {
    const val TEACHING_HOST = "jwglxt.haut.edu.cn"
    const val TEACHING = "https://jwglxt.haut.edu.cn/jwglxt/xtgl/login_slogin.html"
    const val PORTAL = "https://mapp.haut.edu.cn/"
    const val LIBRARY = "https://lib.haut.edu.cn/"
    const val HELP = "https://xxb.haut.edu.cn/info/1032/16509.htm"
    const val CARD_HELP = "https://xxb.haut.edu.cn/info/1221/21459.htm"
    const val WANXIAO = "https://www.17wanxiao.com/"

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
