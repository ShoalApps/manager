package io.github.tintloom.manager

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.github.tintloom.manager.ui.HomeScreen
import io.github.tintloom.manager.ui.ManagerTheme
import io.github.tintloom.manager.ui.SettingsScreen

class MainActivity : ComponentActivity() {
    /** The app whose "Check for updates" opened the Manager, if any. */
    private val highlight = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SwordfishNotice.show(this)
        enableEdgeToEdge()
        highlight.value = callerOf(intent)
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        setContent {
            val settings by Graph.store.settings.collectAsState()
            ManagerTheme(settings) {
                var inSettings by remember { mutableStateOf(false) }
                BackHandler(enabled = inSettings) { inSettings = false }
                if (inSettings) SettingsScreen(onBack = { inSettings = false })
                else HomeScreen(highlight.value, onSettings = { inSettings = true })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        highlight.value = callerOf(intent)
    }

    override fun onPause() {
        Graph.paused()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        Graph.resumed()
    }

    private fun callerOf(intent: Intent?): String? =
        if (intent?.action == ACTION_CHECK_UPDATES) intent.getStringExtra(EXTRA_PACKAGE)?.removeSuffix(".debug") else null

    companion object {
        const val ACTION_CHECK_UPDATES = "io.github.tintloom.manager.CHECK_UPDATES"
        const val EXTRA_PACKAGE = "package"
    }
}
