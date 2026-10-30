package cn.hautpocket.app

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import cn.hautpocket.app.data.PocketStore
import cn.hautpocket.app.data.FocusReminders
import kotlinx.coroutines.delay
import cn.hautpocket.app.domain.PocketState
import cn.hautpocket.app.ui.PocketApp
import cn.hautpocket.app.ui.PocketTheme

class MainActivity : ComponentActivity() {
    private lateinit var store: PocketStore
    private var state by mutableStateOf(PocketState())
    private var previewText by mutableStateOf<String?>(null)
    private var previewTheme by mutableStateOf<String?>(null)
    private val stateListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "state" && ::store.isInitialized) state = store.load()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        store = PocketStore(this)
        getSharedPreferences("pocket", MODE_PRIVATE).registerOnSharedPreferenceChangeListener(stateListener)
        runCatching { FocusReminders.restore(this) }
        state = store.load()
        setContent {
            LaunchedEffect(state.focusEndsAt) {
                val expectedEnd = state.focusEndsAt
                if (expectedEnd > 0) {
                    delay((expectedEnd - System.currentTimeMillis()).coerceAtLeast(0))
                    runCatching { FocusReminders.finish(this@MainActivity, expectedEnd) }
                }
            }
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, density.fontScale * state.fontScale / 100f)) { PocketTheme(previewTheme ?: state.themeColor, previewText ?: state.textColor, state.reduceMotion) {
                PocketApp(state, { updated ->
                    runCatching { store.update { latest -> updated.copy(focusEndsAt = latest.focusEndsAt, focusMinutes = latest.focusMinutes, focusSessions = latest.focusSessions) } }.onSuccess { state = it }.onFailure {
                        Toast.makeText(this, it.message ?: "保存失败", Toast.LENGTH_LONG).show()
                    }
                }, store.loadWarning,
                    startFocus = { minutes -> runCatching { FocusReminders.start(this, minutes) }.onSuccess { state = it }.onFailure { Toast.makeText(this, "专注提醒设置失败，请重试", Toast.LENGTH_LONG).show() } },
                    stopFocus = { runCatching { FocusReminders.stop(this) }.onSuccess { state = it }.onFailure { Toast.makeText(this,"结束专注失败，请重试",Toast.LENGTH_LONG).show() } },
                    previewText = { previewText = it }, previewTheme = { previewTheme = it })
            } }
        }
    }

    override fun onResume() {
        super.onResume()
        cn.hautpocket.app.data.FocusDelivery.foreground = true
        if (::store.isInitialized) {
            runCatching { FocusReminders.restore(this) }
            state = store.load()
            cn.hautpocket.app.data.DailySampling.schedule(this,
                state.autoSampling && !cn.hautpocket.app.data.AccountVault(this).read().isNullOrBlank())
        }
    }

    override fun onDestroy() {
        getSharedPreferences("pocket", MODE_PRIVATE).unregisterOnSharedPreferenceChangeListener(stateListener)
        super.onDestroy()
    }

    override fun onPause() {
        cn.hautpocket.app.data.FocusDelivery.foreground = false
        super.onPause()
    }
}
