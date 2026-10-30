package cn.hautpocket.app

import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import java.util.concurrent.Executors

/** All school pages use WebView's private, shared profile. No credential copies. */
object OfficialWebSession {
    private val writes = Executors.newSingleThreadExecutor { task ->
        Thread(task, "school-session-storage").apply { isDaemon = true }
    }
    private val main = Handler(Looper.getMainLooper())

    fun configure(view: WebView) {
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(view, false)
        }
        view.settings.domStorageEnabled = true
    }

    // flush may perform disk I/O; do not block animations or the login form.
    fun persist() { writes.execute { CookieManager.getInstance().flush() } }

    fun clear(onComplete: () -> Unit) {
        CookieManager.getInstance().removeAllCookies {
            WebStorage.getInstance().deleteAllData()
            writes.execute {
                CookieManager.getInstance().flush()
                main.post(onComplete)
            }
        }
    }
}
