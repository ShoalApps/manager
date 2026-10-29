package io.github.shoalapps.shoal

import io.github.shoalapps.shoal.data.Catalog
import io.github.shoalapps.shoal.data.Releases
import java.io.IOException
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.Source
import okio.Timeout
import okio.buffer
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DownloadTest {
    @get:Rule val dir = TemporaryFolder()
    private val release = Releases.Release(Catalog.video, 5, "https://example.invalid/file.apk", "https://example.invalid/")

    private fun client(fail: Boolean): OkHttpClient = OkHttpClient.Builder().addInterceptor { chain ->
        val stream = object : Source {
            var delivered = false
            override fun read(sink: Buffer, byteCount: Long): Long {
                if (delivered) {
                    if (fail) throw IOException("connection lost")
                    return -1
                }
                delivered = true
                sink.writeUtf8("new apk")
                return 7
            }
            override fun timeout(): Timeout = Timeout.NONE
            override fun close() = Unit
        }.buffer()
        val body = object : ResponseBody() {
            override fun contentType(): MediaType? = null
            override fun contentLength(): Long = 7
            override fun source(): BufferedSource = stream
        }
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK").body(body).build()
    }.build()

    @Test fun failedTransferPreservesPreviousFileAndRemovesPartial() = runBlocking {
        val existing = dir.root.resolve("${Catalog.video.packageName}-5.apk").apply { writeText("existing apk") }
        try {
            Releases.download(client(true), release, dir.root, allowDownload = { true }, verified = { false }) { }
            fail("Expected a failed transfer")
        } catch (_: IOException) { }
        assertEquals("existing apk", existing.readText())
        assertEquals(listOf(existing.name), dir.root.list()!!.toList())
    }

    @Test fun completeTransferReplacesPreviousFile() = runBlocking {
        val existing = dir.root.resolve("${Catalog.video.packageName}-5.apk").apply { writeText("existing apk") }
        val result = Releases.download(client(false), release, dir.root, allowDownload = { true }, verified = { false }) { }
        assertEquals(existing, result)
        assertEquals("new apk", result.readText())
        assertEquals(listOf(existing.name), dir.root.list()!!.toList())
    }
    @Test fun verifiedCachedInstallerWorksWithNetworkDisallowed() = runBlocking {
        val existing = dir.root.resolve(Catalog.video.packageName + "-5.apk").apply { writeText("verified apk") }
        val noNetwork = OkHttpClient.Builder().addInterceptor { error("Offline installation accessed the network") }.build()
        assertEquals(existing, Releases.download(noNetwork, release, dir.root, allowDownload = { false }, verified = { true }) {})
        assertEquals("verified apk", existing.readText())
    }

    @Test fun aNetworkRestrictionStopsTheTransferAndKeepsThePreviousFile() = runBlocking {
        val existing = dir.root.resolve(Catalog.video.packageName + "-5.apk").apply { writeText("old apk") }
        var checks = 0
        try {
            Releases.download(client(false), release, dir.root, allowDownload = { ++checks <= 2 }, verified = { false }) {}
            fail("Expected the transfer to pause")
        } catch (_: IllegalStateException) { }
        assertEquals("old apk", existing.readText())
        assertEquals(listOf(existing.name), dir.root.list()!!.toList())
    }

}
