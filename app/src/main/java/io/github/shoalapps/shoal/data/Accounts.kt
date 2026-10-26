package io.github.shoalapps.shoal.data

import android.content.Context
import android.webkit.CookieManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.security.MessageDigest

/**
 * One place to sign in to everything. The Shoal apps read these through [AccountsProvider]
 * (signature-protected, so only apps signed with the same key can).
 *
 * - YouTube, TikTok, Instagram and X are website sign-ins, kept as their cookies.
 * - Bluesky keeps your handle and app password: its sessions can't be shared between apps
 *   (refreshing one invalidates the others), so each app signs in for itself with them.
 */
object Accounts {
    enum class Service(val key: String, val title: String, val loginUrl: String, val cookieSite: String, val signedInCookie: String) {
        YOUTUBE("youtube", "YouTube", "https://accounts.google.com/ServiceLogin?service=youtube&continue=https%3A%2F%2Fwww.youtube.com%2F", "https://www.youtube.com", "SAPISID"),
        TIKTOK("tiktok", "TikTok", "https://www.tiktok.com/login", "https://www.tiktok.com", "sessionid"),
        INSTAGRAM("instagram", "Instagram", "https://www.instagram.com/accounts/login/", "https://www.instagram.com", "sessionid"),
        X("x", "X", "https://x.com/i/flow/login", "https://x.com", "auth_token");

        companion object { fun of(key: String) = entries.firstOrNull { it.key == key } }
    }

    @Serializable
    data class Saved(
        val cookies: Map<String, String> = emptyMap(),
        /** The YouTube channel to act as (a brand channel's page id), or null for the main one. */
        val youtubePageId: String? = null,
        val youtubeChannelName: String? = null,
        val blueskyHandle: String? = null,
        val blueskyAppPassword: String? = null,
        /** Bumped on every change, so apps know when to pick it up again. */
        val version: Long = 0,
    )

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private lateinit var prefs: android.content.SharedPreferences
    private val _saved = MutableStateFlow(Saved())
    val saved: StateFlow<Saved> = _saved

    fun init(context: Context) {
        prefs = context.getSharedPreferences("accounts", Context.MODE_PRIVATE)
        _saved.value = prefs.getString("saved", null)?.let { runCatching { json.decodeFromString<Saved>(it) }.getOrNull() } ?: Saved()
    }

    private fun save(change: (Saved) -> Saved) {
        val next = change(_saved.value).let { it.copy(version = System.currentTimeMillis()) }
        _saved.value = next
        prefs.edit().putString("saved", json.encodeToString(Saved.serializer(), next)).apply()
    }

    fun signedIn(s: Service) = _saved.value.cookies[s.key]?.contains(s.signedInCookie + "=") == true

    /** After a sign-in page: keeps the site's cookies if they show a signed-in session. */
    fun captureCookies(s: Service): Boolean {
        val cookie = CookieManager.getInstance().getCookie(s.cookieSite) ?: return false
        if (!cookie.split(";").any { it.trim().startsWith(s.signedInCookie + "=") }) return false
        save { it.copy(cookies = it.cookies + (s.key to cookie), youtubePageId = if (s == Service.YOUTUBE) null else it.youtubePageId) }
        return true
    }

    fun signOut(s: Service) {
        save {
            it.copy(
                cookies = it.cookies - s.key,
                youtubePageId = if (s == Service.YOUTUBE) null else it.youtubePageId,
                youtubeChannelName = if (s == Service.YOUTUBE) null else it.youtubeChannelName,
            )
        }
        val cm = CookieManager.getInstance()
        val domain = android.net.Uri.parse(s.cookieSite).host!!.removePrefix("www.")
        cm.getCookie(s.cookieSite)?.split(";")?.map { it.substringBefore("=").trim() }?.forEach { cm.setCookie(s.cookieSite, "$it=; Max-Age=0; Path=/; Domain=.$domain") }
        cm.flush()
    }

    fun setBluesky(handle: String?, appPassword: String?) = save { it.copy(blueskyHandle = handle, blueskyAppPassword = appPassword) }

    fun setYouTubeChannel(c: Channel?) = save { it.copy(youtubePageId = c?.pageId, youtubeChannelName = c?.name) }

    // ---- Bluesky: check the app password works before keeping it ----

    suspend fun checkBluesky(http: OkHttpClient, handle: String, password: String): String = withContext(Dispatchers.IO) {
        val body = buildJsonObject { put("identifier", handle.trim().removePrefix("@")); put("password", password.trim()) }
        val req = Request.Builder().url("https://bsky.social/xrpc/com.atproto.server.createSession")
            .post(body.toString().toRequestBody("application/json".toMediaType())).build()
        http.newCall(req).execute().use { resp ->
            val o = runCatching { json.parseToJsonElement(resp.body!!.string()) as JsonObject }.getOrDefault(JsonObject(emptyMap()))
            if (!resp.isSuccessful) error((o["message"] as? JsonPrimitive)?.content ?: "Couldn't sign in")
            (o["handle"] as? JsonPrimitive)?.content ?: handle
        }
    }

    // ---- YouTube channels (the website's account switcher) ----

    data class Channel(val name: String, val handle: String?, val avatar: String?, val pageId: String?, val selected: Boolean)

    suspend fun youtubeChannels(http: OkHttpClient): List<Channel> = withContext(Dispatchers.IO) {
        val cookie = _saved.value.cookies["youtube"] ?: error("Sign in to YouTube first")
        val origin = "https://www.youtube.com"
        val body = buildJsonObject {
            putJsonObject("context") { putJsonObject("client") { put("clientName", "WEB"); put("clientVersion", "2.20260930.01.00"); put("hl", "en") } }
            put("requestType", "ACCOUNTS_LIST_REQUEST_TYPE_CHANNEL_SWITCHER")
            put("callCircumstance", "SWITCHING_USERS_FULL")
        }
        val req = Request.Builder().url("$origin/youtubei/v1/account/accounts_list?prettyPrint=false")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .header("Cookie", cookie).header("Authorization", sapisidHash(cookie, origin) ?: "")
            .header("Origin", origin).header("X-Origin", origin).header("X-Goog-AuthUser", "0")
            .header("X-Youtube-Client-Name", "1").header("X-Youtube-Client-Version", "2.20260930.01.00")
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36")
            .build()
        val root = http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) error("YouTube said ${resp.code}")
            json.parseToJsonElement(resp.body!!.string())
        }
        channelsIn(root)
    }

    /** Accounts in an accounts_list answer; the same reading Swordfish uses. */
    fun channelsIn(root: JsonElement): List<Channel> {
        val out = mutableListOf<Channel>()
        fun walk(el: JsonElement?) {
            when (el) {
                is JsonObject -> for ((k, v) in el) {
                    val o = v as? JsonObject
                    val name = o?.let { text(it["accountName"]) }
                    if (o != null && name != null && (k.contains("accountItem", true) || o.containsKey("accountPhoto"))) {
                        val disabled = (o["isDisabled"] as? JsonPrimitive)?.content == "true"
                        val hasChannel = (o["hasChannel"] as? JsonPrimitive)?.content != "false"
                        if (!disabled && hasChannel) out += Channel(
                            name, text(o["channelHandle"]) ?: text(o["accountByline"]),
                            ((o["accountPhoto"] as? JsonObject)?.get("thumbnails") as? JsonArray)?.lastOrNull()?.let { ((it as? JsonObject)?.get("url") as? JsonPrimitive)?.content },
                            findString(o, "pageId"), (o["isSelected"] as? JsonPrimitive)?.content == "true",
                        )
                    } else walk(v)
                }
                is JsonArray -> el.forEach(::walk)
                else -> Unit
            }
        }
        walk(root)
        return out.distinctBy { it.pageId ?: "main:${it.name}" }
    }

    private fun text(el: JsonElement?): String? {
        val o = el as? JsonObject ?: return (el as? JsonPrimitive)?.takeIf { it.isString }?.content
        (o["simpleText"] as? JsonPrimitive)?.let { return it.content }
        (o["runs"] as? JsonArray)?.let { runs -> return runs.joinToString("") { ((it as? JsonObject)?.get("text") as? JsonPrimitive)?.content.orEmpty() } }
        return null
    }

    private fun findString(el: JsonElement?, key: String): String? {
        when (el) {
            is JsonObject -> { (el[key] as? JsonPrimitive)?.takeIf { it.isString }?.let { return it.content }; for (v in el.values) findString(v, key)?.let { return it } }
            is JsonArray -> for (v in el) findString(v, key)?.let { return it }
            else -> Unit
        }
        return null
    }

    /** SAPISIDHASH, as YouTube's web app (and yt-dlp) builds it. */
    private fun sapisidHash(cookie: String, origin: String): String? {
        val jar = cookie.split(";").map { it.trim() }.associate { it.substringBefore("=") to it.substringAfter("=", "") }
        val sid = jar["SAPISID"]?.takeIf { it.isNotEmpty() } ?: jar["__Secure-3PAPISID"] ?: return null
        val ts = (System.currentTimeMillis() / 1000).toString()
        val hash = MessageDigest.getInstance("SHA-1").digest("$ts $sid $origin".toByteArray()).joinToString("") { "%02x".format(it) }
        return "SAPISIDHASH ${ts}_$hash"
    }
}
