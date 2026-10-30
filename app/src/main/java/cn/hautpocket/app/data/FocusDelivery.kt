package cn.hautpocket.app.data

import android.content.Context
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Local timing metadata; no notification contents or account data. */
object FocusDelivery {
    @Volatile var foreground: Boolean = false
    private fun prefs(context: Context) = context.getSharedPreferences("focus-delivery",Context.MODE_PRIVATE)
    fun planned(context: Context, kind: String, at: Long, exact: Boolean) {
        prefs(context).edit().putLong("$kind.planned",at).putBoolean("$kind.exact",exact)
            .remove("$kind.received").remove("$kind.submitted").remove("$kind.error").remove("$kind.background").apply()
    }
    fun received(context: Context, kind: String) {
        prefs(context).edit().putLong("$kind.received",System.currentTimeMillis()).putBoolean("$kind.background",!foreground).apply()
    }
    fun submitted(context: Context, kind: String, error: String? = null) {
        prefs(context).edit().putLong("$kind.submitted",if (error == null) System.currentTimeMillis() else 0L).putString("$kind.error",error).apply()
    }
    fun testSummary(context: Context): String {
        val p = prefs(context)
        if (p.getLong("test.planned",0) == 0L) return "尚未进行测试提醒"
        fun time(key: String): String = p.getLong("test.$key",0).takeIf { it > 0 }?.let {
            Instant.ofEpochMilli(it).atZone(ZoneId.of("Asia/Shanghai")).format(DateTimeFormatter.ofPattern("HH:mm:ss"))
        } ?: "尚未记录"
        val mode = if (p.getBoolean("test.exact",false)) "准时提醒" else "普通提醒（可能延迟）"
        val source = if (p.contains("test.background")) if (p.getBoolean("test.background",false)) "后台" else "应用前台" else "未接收"
        return "计划：${time("planned")} · $mode\n广播接收：${time("received")}（$source）\n提交系统通知：${time("submitted")}\n${p.getString("test.error",null) ?: "提交系统不代表已显示横幅，可下拉通知栏核对。"}"
    }
}
