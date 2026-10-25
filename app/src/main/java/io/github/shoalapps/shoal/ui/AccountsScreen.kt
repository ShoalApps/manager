@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package io.github.shoalapps.shoal.ui

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.github.shoalapps.shoal.Graph
import io.github.shoalapps.shoal.data.Accounts
import kotlinx.coroutines.launch

/** Sign in to every service once; the Shoal apps use these accounts. */
@Composable
fun AccountsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val saved by Accounts.saved.collectAsState()
    var signingIn by remember { mutableStateOf<Accounts.Service?>(null) }
    var bluesky by remember { mutableStateOf(false) }
    var pickingChannel by remember { mutableStateOf(false) }

    signingIn?.let { s ->
        WebSignIn(s, onDone = { ok ->
            signingIn = null
            if (ok && s == Accounts.Service.YOUTUBE) pickingChannel = true
        })
        return
    }
    if (pickingChannel) {
        ChannelPicker(onDone = { pickingChannel = false })
        return
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Accounts") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } }) }) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(top = pad.calculateTopPadding()).wrapContentWidth().widthIn(max = 720.dp),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    "Sign in here once and Anglerfish, Social and Clips use these accounts. They stay on this phone and are only shared with apps signed by Shoal.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                ServiceCard(
                    "YouTube",
                    if (Accounts.signedIn(Accounts.Service.YOUTUBE)) "Signed in" + (saved.youtubeChannelName?.let { " · as $it" } ?: "") else "Video and Clips: your feed, subscriptions and likes",
                    Accounts.signedIn(Accounts.Service.YOUTUBE),
                    onSignIn = { signingIn = Accounts.Service.YOUTUBE },
                    onSignOut = { Accounts.signOut(Accounts.Service.YOUTUBE) },
                    extra = if (Accounts.signedIn(Accounts.Service.YOUTUBE)) ({ TextButton(onClick = { pickingChannel = true }) { Text("Choose channel") } }) else null,
                )
            }
            item {
                ServiceCard(
                    "Bluesky",
                    saved.blueskyHandle?.let { "Signed in as @$it" } ?: "Social: post, reply, like and your timeline",
                    saved.blueskyHandle != null,
                    onSignIn = { bluesky = true },
                    onSignOut = { Accounts.setBluesky(null, null) },
                )
            }
            items(listOf(Accounts.Service.TIKTOK, Accounts.Service.INSTAGRAM, Accounts.Service.X)) { s ->
                ServiceCard(
                    s.title,
                    if (Accounts.signedIn(s)) "Signed in" else when (s) { Accounts.Service.X -> "Social's X tab"; else -> "Clips' ${s.title} tab" },
                    Accounts.signedIn(s),
                    onSignIn = { signingIn = s },
                    onSignOut = { Accounts.signOut(s) },
                )
            }
        }
    }

    if (bluesky) {
        var handle by remember { mutableStateOf("") }
        var password by remember { mutableStateOf("") }
        var busy by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { bluesky = false },
            title = { Text("Sign in to Bluesky") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Use an app password (Bluesky → Settings → Privacy and security → App passwords), not your main password.", style = MaterialTheme.typography.bodyMedium)
                    OutlinedTextField(handle, { handle = it }, label = { Text("Handle") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(password, { password = it }, label = { Text("App password") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                }
            },
            confirmButton = {
                TextButton(enabled = !busy && handle.isNotBlank() && password.isNotBlank(), onClick = {
                    busy = true; error = null
                    scope.launch {
                        runCatching { Accounts.checkBluesky(Graph.http, handle, password) }
                            .onSuccess { real -> Accounts.setBluesky(real, password.trim()); bluesky = false }
                            .onFailure { error = it.message }
                        busy = false
                    }
                }) { Text(if (busy) "Checking…" else "Sign in") }
            },
            dismissButton = { TextButton(onClick = { bluesky = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ServiceCard(title: String, body: String, signedIn: Boolean, onSignIn: () -> Unit, onSignOut: () -> Unit, extra: (@Composable () -> Unit)? = null) {
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (signedIn) Icon(Icons.Rounded.CheckCircle, null, Modifier.padding(start = 6.dp).size(18.dp), tint = MaterialTheme.colorScheme.primary)
                }
                Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                extra?.invoke()
            }
            if (signedIn) OutlinedButton(onClick = onSignOut) { Text("Sign out") } else Button(onClick = onSignIn) { Text("Sign in") }
        }
    }
}

/** The service's own sign-in page; done when its session cookie appears. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun WebSignIn(s: Accounts.Service, onDone: (Boolean) -> Unit) {
    var loading by remember { mutableStateOf(true) }
    BackHandler { onDone(false) }
    Scaffold(topBar = { TopAppBar(title = { Text("Sign in to ${s.title}") }, navigationIcon = { IconButton(onClick = { onDone(false) }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } }) }) { pad ->
        Box(Modifier.fillMaxSize().padding(top = pad.calculateTopPadding())) {
            AndroidView(
                factory = { ctx ->
                    CookieManager.getInstance().setAcceptCookie(true)
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        // Sign-in pages work best in their mobile Chrome form.
                        settings.userAgentString = settings.userAgentString.replace("; wv)", ")").replace(Regex(" Version/\\d+\\.\\d+"), "")
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                        var finished = false
                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) { loading = true }
                            override fun onPageFinished(view: WebView, url: String?) {
                                loading = false
                                CookieManager.getInstance().flush()
                                if (!finished && Accounts.captureCookies(s)) { finished = true; onDone(true) }
                            }
                        }
                        loadUrl(s.loginUrl)
                    }
                },
                onRelease = { it.stopLoading(); it.destroy() },
                modifier = Modifier.fillMaxSize(),
            )
            if (loading) LinearWavyProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
        }
    }
}

@Composable
private fun ChannelPicker(onDone: () -> Unit) {
    var channels by remember { mutableStateOf<List<Accounts.Channel>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { runCatching { Accounts.youtubeChannels(Graph.http) }.onSuccess { channels = it }.onFailure { error = it.message } }
    BackHandler { onDone() }
    Scaffold(topBar = { TopAppBar(title = { Text("Choose a channel") }, navigationIcon = { IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } }) }) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(top = pad.calculateTopPadding()).wrapContentWidth().widthIn(max = 720.dp), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val list = channels
            when {
                error != null -> item { Text(error!!, color = MaterialTheme.colorScheme.error) }
                list == null -> item { LinearWavyProgressIndicator(Modifier.fillMaxWidth()) }
                list.isEmpty() -> item { Text("No channels found; the main one will be used.") }
                else -> items(list) { c ->
                    Surface(
                        shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).clickable { Accounts.setYouTubeChannel(c); onDone() },
                    ) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            coil3.compose.AsyncImage(c.avatar, null, Modifier.size(44.dp).clip(CircleShape))
                            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                                Text(c.name, style = MaterialTheme.typography.titleMedium)
                                c.handle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            }
                            if (c.selected) Icon(Icons.Rounded.CheckCircle, "Current", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}
