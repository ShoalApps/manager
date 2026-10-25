package io.github.swordfishapps.manager.data

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import rikka.shizuku.Shizuku
import java.io.File
import io.github.swordfishapps.manager.Graph
import java.util.concurrent.TimeUnit

/**
 * Installs APKs. Without help, Android's installer asks you to confirm (except updates to apps the
 * Manager installed, on Android 12+). With Shizuku running or root, installs are silent.
 */
object Installer {
    enum class Method { STANDARD, SHIZUKU, ROOT }

    enum class ShizukuState { NOT_INSTALLED, NOT_RUNNING, NEEDS_PERMISSION, READY }

    sealed interface Outcome {
        data object Installed : Outcome
        /** Android is showing its install confirmation; the result arrives in [results]. */
        data class WaitingForYou(val session: Int) : Outcome
        data class Failed(val message: String) : Outcome
    }

    /** Results of standard installs, which finish after Android's confirmation. */
    private val _results = MutableSharedFlow<Pair<String, Outcome>>(extraBufferCapacity = 8)
    val results: SharedFlow<Pair<String, Outcome>> = _results
    internal fun report(pkg: String, outcome: Outcome) { _results.tryEmit(pkg to outcome) }

    const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
    const val SHIZUKU_REQUEST = 7110

    fun shizukuState(context: Context): ShizukuState {
        val installed = runCatching { context.packageManager.getPackageInfo(SHIZUKU_PACKAGE, 0) }.isSuccess
        val running = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
        return when {
            running && !Shizuku.isPreV11() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED -> ShizukuState.READY
            running -> ShizukuState.NEEDS_PERMISSION
            installed -> ShizukuState.NOT_RUNNING
            else -> ShizukuState.NOT_INSTALLED
        }
    }

    fun requestShizuku() {
        runCatching { if (Shizuku.pingBinder() && !Shizuku.isPreV11()) Shizuku.requestPermission(SHIZUKU_REQUEST) }
    }

    /** Whether `su` works. The root manager may ask the first time. */
    suspend fun hasRoot(): Boolean = withContext(Dispatchers.IO) {
        withTimeoutOrNull(15_000) {
            runCatching {
                val p = ProcessBuilder("su", "-c", "id").redirectErrorStream(true).start()
                val out = p.inputStream.bufferedReader().readText()
                p.waitFor(10, TimeUnit.SECONDS) && out.contains("uid=0")
            }.getOrDefault(false)
        } ?: false
    }

    /** The method to use: the chosen one if it works, otherwise the best one that does. */
    suspend fun pick(context: Context, preferred: String): Method {
        val shizuku = shizukuState(context) == ShizukuState.READY
        return when (preferred) {
            "standard" -> Method.STANDARD
            "shizuku" -> if (shizuku) Method.SHIZUKU else Method.STANDARD
            "root" -> if (hasRoot()) Method.ROOT else Method.STANDARD
            else -> when {
                shizuku -> Method.SHIZUKU
                Graph.store.rootGranted && hasRoot() -> Method.ROOT
                else -> Method.STANDARD
            }
        }
    }

    suspend fun install(context: Context, apk: File, app: SwordfishApp, method: Method): Outcome = when (method) {
        Method.STANDARD -> standard(context, apk, app)
        Method.SHIZUKU -> withContext(Dispatchers.IO) { viaPm(shizukuProcess(pmInstall(context, apk)), apk) }
        Method.ROOT -> withContext(Dispatchers.IO) { viaPm(ProcessBuilder("su", "-c", pmInstall(context, apk).joinToString(" ")).start(), apk) }
    }

    /** `pm install`, reading the APK from stdin, recording the Manager as the installer. */
    private fun pmInstall(context: Context, apk: File) = arrayOf("pm", "install", "-r", "-i", context.packageName, "-S", apk.length().toString())

    private fun viaPm(process: Process, apk: File): Outcome {
        process.outputStream.use { sink -> apk.inputStream().use { it.copyTo(sink) } }
        val out = process.inputStream.bufferedReader().readText() + process.errorStream.bufferedReader().readText()
        process.waitFor(120, TimeUnit.SECONDS)
        return if (out.contains("Success")) Outcome.Installed else Outcome.Failed(out.lines().firstOrNull { it.isNotBlank() }?.trim() ?: "Install failed")
    }

    /** Runs a command as Shizuku's shell user. Shizuku 13 keeps this API but no longer exposes it. */
    private fun shizukuProcess(cmd: Array<String>): Process {
        val m = Shizuku::class.java.getDeclaredMethod("newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java)
        m.isAccessible = true
        return m.invoke(null, cmd, null, null) as Process
    }

    private fun standard(context: Context, apk: File, app: SwordfishApp): Outcome {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(app.packageName)
            if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            session.openWrite("base.apk", 0, apk.length()).use { sink -> apk.inputStream().use { it.copyTo(sink) } }
            val callback = PendingIntent.getBroadcast(
                context, id,
                Intent(context, InstallResultReceiver::class.java).putExtra("pkg", app.packageName),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
            session.commit(callback.intentSender)
        }
        return Outcome.WaitingForYou(id)
    }
}

/** Shows Android's install confirmation when asked, and reports how each install ended. */
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pkg = intent.getStringExtra("pkg") ?: intent.getStringExtra(PackageInstaller.EXTRA_PACKAGE_NAME).orEmpty()
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return
                context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            PackageInstaller.STATUS_SUCCESS -> Installer.report(pkg, Installer.Outcome.Installed)
            PackageInstaller.STATUS_FAILURE_ABORTED -> Installer.report(pkg, Installer.Outcome.Failed("Cancelled"))
            else -> Installer.report(pkg, Installer.Outcome.Failed(intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "Install failed"))
        }
    }
}
