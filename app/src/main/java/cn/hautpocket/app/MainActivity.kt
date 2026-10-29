package cn.hautpocket.app

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cn.hautpocket.app.data.PocketStore
import cn.hautpocket.app.domain.PocketState
import cn.hautpocket.app.ui.PocketApp
import cn.hautpocket.app.ui.PocketTheme

class MainActivity : ComponentActivity() {
    private lateinit var store: PocketStore
    private var state by mutableStateOf(PocketState())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        store = PocketStore(this)
        state = store.load()
        setContent {
            PocketTheme {
                PocketApp(state, { updated ->
                    runCatching { store.save(updated) }.onSuccess { state = updated }.onFailure {
                        Toast.makeText(this, it.message ?: "保存失败", Toast.LENGTH_LONG).show()
                    }
                }, store.loadWarning)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::store.isInitialized) state = store.load()
    }
}
