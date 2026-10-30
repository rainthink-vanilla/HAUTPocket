package cn.hautpocket.app

import android.content.Context
import android.content.res.Configuration
import cn.hautpocket.app.data.PocketStore
import cn.hautpocket.app.domain.TextColors
import org.json.JSONObject

/** Presentation-only adaptation. The official page owns authentication and reservation actions. */
object LibraryPageDesign {
    fun script(context: Context, enabled: Boolean, catalog: Boolean = false): String {
        val state = PocketStore(context).load()
        val dark = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        val palette = when (state.themeColor) {
            "blue" -> if (dark) listOf("#9ccbff", "#1b4268") else listOf("#285f99", "#d8e9ff")
            "purple" -> if (dark) listOf("#d4bbff", "#4d3970") else listOf("#6b5198", "#ede0ff")
            "orange" -> if (dark) listOf("#ffbe8b", "#71391b") else listOf("#975022", "#ffe2cd")
            else -> if (TextColors.validCustom(state.themeColor)) {
                val primary = android.graphics.Color.parseColor(state.themeColor)
                val surface = android.graphics.Color.parseColor(if (dark) "#1C2025" else "#FFFFFF")
                val soft = androidx.core.graphics.ColorUtils.blendARGB(surface, primary, if (dark) .28f else .12f)
                listOf(state.themeColor, "#%06X".format(java.util.Locale.ROOT, soft and 0xFFFFFF))
            } else if (dark) listOf("#95d7bf", "#164c40") else listOf("#176b5b", "#e0f2eb")
        }
        val colors = JSONObject().put("primary", palette[0]).put("soft", palette[1])
            .put("on-primary", if (androidx.core.graphics.ColorUtils.calculateLuminance(android.graphics.Color.parseColor(palette[0])) > .179) "#000000" else "#ffffff")
            .put("bg", if (dark) "#111a19" else "#f5f7f8")
            .put("surface", if (dark) "#1a2724" else "#ffffff")
            .put("text", if (dark) "#e0ebe7" else "#192c2b")
            .put("muted", if (dark) "#b6c8c0" else "#61716f")
            .put("border", if (dark) "#34443e" else "#dde5e2")
        val text = TextColors.palette(state.textColor, dark)
        colors.put("text", text.first).put("muted", text.second)
        val asset = if (catalog) "catalog-design" else "library-design"
        val css = context.assets.open("$asset.css").bufferedReader().use { it.readText() }
        val js = context.assets.open("$asset.js").bufferedReader().use { it.readText() }
        val config = JSONObject().put("enabled", enabled).put("colors", colors)
            .put("fontScale", state.fontScale / 100.0).put("css", css)
        return "(() => { const config = $config;\n$js\n})();"
    }
}
