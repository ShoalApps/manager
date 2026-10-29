package io.github.shoalapps.shoal.data

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest

/** Finding, downloading and checking Shoal releases on GitHub. */
object Releases {
    data class Release(val app: ShoalApp, val build: Int, val apkUrl: String, val pageUrl: String)

    private val saved get() = io.github.shoalapps.shoal.Graph.app.getSharedPreferences("offline-releases", Context.MODE_PRIVATE)
    fun cached(app: ShoalApp): Release? = runCatching {
        val o = org.json.JSONObject(saved.getString(app.packageName, null) ?: return null)
        Release(app, o.getInt("build"), o.getString("apk"), o.getString("page"))
    }.getOrNull()
    private fun remember(release: Release) {
        saved.edit().putString(release.app.packageName, org.json.JSONObject().put("build", release.build)
            .put("apk", release.apkUrl).put("page", release.pageUrl).toString()).apply()
    }

    /** The build number in a release tag like "build-23", or null for anything else. */
    fun buildOf(tagOrUrl: String): Int? = Regex("""build-(\d+)""").find(tagOrUrl)?.groupValues?.get(1)?.toIntOrNull()

    /**
     * The latest release, from the redirect github.com/…/releases/latest sends, which isn't subject
     * to the API's rate limits.
     */
    suspend fun latest(http: OkHttpClient, app: ShoalApp): Release? = withContext(Dispatchers.IO) {
        if (!NetworkPolicy.allowed()) return@withContext cached(app)
        val client = http.newBuilder().followRedirects(false).followSslRedirects(false).build()
        val req = Request.Builder().url("https://github.com/${app.repo}/releases/latest").head().build()
        client.newCall(req).execute().use { resp ->
            val page = resp.header("Location") ?: return@withContext null
            val tag = page.substringAfterLast("/tag/", "").ifEmpty { return@withContext null }
            val build = buildOf(tag) ?: return@withContext null
            Release(app, build, "https://github.com/${app.repo}/releases/download/$tag/${app.asset}", page).also(::remember)
        }
    }

    /** The installed build (version code), or null if the app isn't installed. */
    fun installedBuild(context: Context, app: ShoalApp): Int? = runCatching {
        PackageInfoCompat.getLongVersionCode(context.packageManager.getPackageInfo(app.packageName, 0)).toInt()
    }.getOrNull()

    /** Downloads a release's APK into the cache, reporting progress from 0 to 1. */
    suspend fun download(http: OkHttpClient, release: Release, dir: File,
        allowDownload: () -> Boolean = NetworkPolicy::downloadsAllowed,
        verified: (File) -> Boolean = { verify(io.github.shoalapps.shoal.Graph.app, it, release.app) == null },
        onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val out = File(dir, "${release.app.packageName}-${release.build}.apk")
        if (out.isFile && verified(out)) return@withContext out
        check(allowDownload()) { "Download paused. Connect to an allowed network; Data Saver takes precedence." }
        val partial = File.createTempFile("${release.app.packageName}-${release.build}-", ".part", dir)
        try {
            http.newCall(Request.Builder().url(release.apkUrl).build()).execute().use { resp ->
                check(resp.isSuccessful) { "Download failed (${resp.code})" }
                val body = resp.body ?: error("Empty download")
                val total = body.contentLength().takeIf { it > 0 }
                partial.outputStream().use { sink ->
                    body.byteStream().use { src ->
                        val buf = ByteArray(64 * 1024)
                        var done = 0L
                        while (true) {
                            check(allowDownload()) { "Download paused by network settings" }
                            val n = src.read(buf)
                            if (n < 0) break
                            sink.write(buf, 0, n)
                            done += n
                            if (total != null) onProgress(done.toFloat() / total)
                        }
                    }
                }
            }
            check(partial.renameTo(out)) { "Couldn't save the download" }
            out
        } catch (e: Exception) {
            // Failed downloads cannot leave a partial APK or damage an existing one.
            partial.delete()
            throw e
        }
    }

    /**
     * Checks a downloaded APK is the app it should be and is signed with the Shoal release key.
     * Returns why not, or null when it's fine.
     */
    fun verify(context: Context, apk: File, app: ShoalApp): String? {
        val pm = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else @Suppress("DEPRECATION") PackageManager.GET_SIGNATURES
        val info = pm.getPackageArchiveInfo(apk.path, flags) ?: return "The download isn't a valid app"
        if (info.packageName != app.packageName) return "The download is a different app (${info.packageName})"
        val certs = if (Build.VERSION.SDK_INT >= 28) {
            val s = info.signingInfo ?: return "The download isn't signed"
            if (s.hasMultipleSigners()) s.apkContentsSigners else s.signingCertificateHistory
        } else {
            @Suppress("DEPRECATION") info.signatures
        } ?: return "The download isn't signed"
        val ok = certs.any { sha256(it.toByteArray()) == Catalog.RELEASE_CERT_SHA256 }
        return if (ok) null else "The download isn't signed by Shoal, so it wasn't installed"
    }

    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02X".format(it) }
}
