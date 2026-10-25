package io.github.shoalapps.shoal

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
import io.github.shoalapps.shoal.ui.HomeScreen
import io.github.shoalapps.shoal.ui.ManagerTheme
import io.github.shoalapps.shoal.ui.SettingsScreen

class MainActivity : ComponentActivity() {
    /** The app whose "Check for updates" opened the Manager, if any. */
    private val highlight = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DataMover.offerUninstall(this)
        enableEdgeToEdge()
        highlight.value = callerOf(intent)
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        setContent {
            val settings by Graph.store.settings.collectAsState()
            ManagerTheme(settings) {
                var screen by remember { mutableStateOf("home") }
                BackHandler(enabled = screen != "home") { screen = "home" }
                when (screen) {
                    "settings" -> SettingsScreen(onBack = { screen = "home" })
                    "accounts" -> io.github.shoalapps.shoal.ui.AccountsScreen(onBack = { screen = "home" })
                    else -> HomeScreen(highlight.value, onSettings = { screen = "settings" }, onAccounts = { screen = "accounts" })
                }
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
        const val ACTION_CHECK_UPDATES = "io.github.shoalapps.shoal.CHECK_UPDATES"
        const val EXTRA_PACKAGE = "package"
    }
}
