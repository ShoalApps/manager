package io.github.shoalapps.shoal

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.shoalapps.shoal.data.Catalog
import io.github.shoalapps.shoal.data.Installer
import io.github.shoalapps.shoal.data.Releases
import io.github.shoalapps.shoal.data.Store
import io.github.shoalapps.shoal.data.ShoalApp
import io.github.shoalapps.shoal.data.UpdateWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class ManagerApp : Application() {
    override fun attachBaseContext(base: android.content.Context) { super.attachBaseContext(AppLanguage.wrap(base)) }
    override fun onCreate() {
        super.onCreate()
        AppLanguage.init(this)
        Telemetry.init(this)
        DataMover.run(this)
        Graph.app = this
        io.github.shoalapps.shoal.data.Accounts.init(this)
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(UpdateWorker.CHANNEL, io.github.shoalapps.shoal.AppLanguage.translate("Updates"), NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "New versions of Shoal apps"
            },
        )
        UpdateWorker.schedule(this)
        Graph.scope.launch { Installer.results.collect { (pkg, outcome) -> Graph.finished(pkg, outcome) } }
        // Shizuku starting, stopping or granting permission changes how installs happen.
        rikka.shizuku.Shizuku.addBinderReceivedListenerSticky { Graph.methodChanged() }
        rikka.shizuku.Shizuku.addBinderDeadListener { Graph.methodChanged() }
        rikka.shizuku.Shizuku.addRequestPermissionResultListener { _, _ -> Graph.methodChanged() }
    }
}

/** What the Manager knows about one app right now. */
data class AppState(
    val installed: Int? = null,
    val latest: Releases.Release? = null,
    /** Download progress 0..1 while downloading; -1 while installing. */
    val progress: Float? = null,
    val message: String? = null,
) {
    val updateAvailable get() = installed != null && latest != null && latest.build > installed
}

/** One instance of each piece for the whole app. */
object Graph {
    lateinit var app: Application
    val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .addInterceptor { chain -> chain.proceed(chain.request().newBuilder().header("User-Agent", "ShoalManager (Android)").build()) }
            .build()
    }
    val store by lazy { Store(app) }
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    val states = mutableStateMapOf<String, AppState>()
    var checking by mutableStateOf(false)
        private set

    /** Bumped whenever Shizuku or root availability may have changed. */
    var methodVersion by mutableStateOf(0)
        private set

    fun methodChanged() { scope.launch { methodVersion++ } }

    private fun state(app: ShoalApp) = states[app.packageName] ?: AppState()
    private fun set(app: ShoalApp, change: (AppState) -> AppState) { states[app.packageName] = change(state(app)) }

    /** Re-reads what's installed (cheap; after installs and on resume). */
    fun refreshInstalled() {
        for (a in Catalog.apps) set(a) { it.copy(installed = Releases.installedBuild(this.app, a), latest = it.latest ?: Releases.cached(a)) }
    }

    /** Asks GitHub for every app's latest release. */
    suspend fun check() {
        checking = true
        refreshInstalled()
        if (!io.github.shoalapps.shoal.data.NetworkPolicy.allowed()) {
            Catalog.apps.forEach { a -> set(a) { it.copy(message = "Offline or Data Saver: showing the last checked release") } }
            checking = false
            return
        }
        coroutineScope {
            Catalog.apps.map { a ->
                async(Dispatchers.IO) {
                    val latest = runCatching { Releases.latest(http, a) }
                    a to latest
                }
            }.awaitAll()
        }.forEach { (a, r) ->
            set(a) { s -> r.fold(
                { release -> if (release == null) s.copy(message = if (s.latest == null) "Couldn't find a release" else "Couldn't find a release; showing the last checked version") else s.copy(latest = release, message = null) },
                { s.copy(message = if (s.latest == null) "Couldn't reach GitHub" else "Couldn't reach GitHub; showing the last checked version") },
            ) }
        }
        store.lastCheck = System.currentTimeMillis()
        checking = false
        io.github.shoalapps.shoal.widget.recordUpdates(app, Catalog.apps.count { state(it).updateAvailable })
    }

    /** Downloads, checks and installs an app's latest release. */
    suspend fun install(app: ShoalApp) = kotlinx.coroutines.withContext(Dispatchers.IO) { installNow(app) }

    private suspend fun installNow(app: ShoalApp) {
        val release = state(app).latest ?: runCatching { Releases.latest(http, app) }.getOrNull()
        if (release == null) { set(app) { it.copy(message = "Couldn't find a release") }; return }
        set(app) { it.copy(latest = release, progress = 0f, message = null) }
        val outcome = runCatching {
            val apk = Releases.download(http, release, this.app.cacheDir) { p -> set(app) { it.copy(progress = p) } }
            Releases.verify(this.app, apk, app)?.let { why -> apk.delete(); return@runCatching Installer.Outcome.Failed(why) }
            set(app) { it.copy(progress = -1f) }
            val method = Installer.pick(this.app, store.settings.value.installMethod)
            if (method == Installer.Method.ROOT) store.rootGranted = true
            Installer.install(this.app, apk, app, method)
        }.getOrElse { Installer.Outcome.Failed(it.message ?: "Install failed") }
        finished(app.packageName, outcome)
    }

    internal fun finished(pkg: String, outcome: Installer.Outcome) {
        val app = Catalog.of(pkg) ?: return
        when (outcome) {
            is Installer.Outcome.WaitingForYou -> {
                waiting[pkg] = outcome.session
                set(app) { it.copy(progress = -1f) }
            }
            Installer.Outcome.Installed -> {
                waiting.remove(pkg)
                set(app) { it.copy(progress = null, message = null, installed = Releases.installedBuild(this.app, app)) }
                this.app.cacheDir.listFiles { f -> f.name.startsWith("$pkg-") }?.forEach { it.delete() }
            }
            is Installer.Outcome.Failed -> {
                waiting.remove(pkg)
                set(app) { it.copy(progress = null, message = outcome.message) }
            }
        }
    }

    private var visits = 0
    private var visibleSince = -1

    fun paused() { visits++; visibleSince = -1 }

    /** Installs waiting for Android's confirmation, by package. */
    private val waiting = java.util.concurrent.ConcurrentHashMap<String, Int>()

    /**
     * Back in the Manager after Android's install screens. Android never reports a confirmation
     * you cancelled (or the "allow installs from this source" screen you backed out of), so an
     * install still waiting shortly after you return is abandoned and can be tried again.
     */
    fun resumed() {
        visibleSince = ++visits
        methodChanged()
        refreshInstalled()
        if (waiting.isEmpty()) return
        val visit = visits
        scope.launch {
            kotlinx.coroutines.delay(2_000)
            // Another install screen (Play Protect, a second confirmation) came up meanwhile: keep waiting.
            if (visibleSince != visit || visits != visit) return@launch
            for ((pkg, session) in waiting.toMap()) {
                val app = Catalog.of(pkg) ?: continue
                runCatching { this@Graph.app.packageManager.packageInstaller.abandonSession(session) }
                waiting.remove(pkg)
                if (state(app).progress != null) set(app) { it.copy(progress = null, message = "The install didn't finish") }
            }
        }
    }

    /** Updates every installed app that has a newer release, the Manager itself last. */
    suspend fun updateAll() {
        for (a in Catalog.apps.sortedBy { it == Catalog.manager }) if (state(a).updateAvailable) install(a)
    }
}
