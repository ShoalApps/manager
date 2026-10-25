package io.github.shoalapps.shoal

import io.github.shoalapps.shoal.data.Catalog
import io.github.shoalapps.shoal.data.Releases
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReleasesTest {
    @Test fun buildNumbersComeFromReleaseTags() {
        assertEquals(42, Releases.buildOf("https://github.com/ShoalApps/anglerfish/releases/tag/build-42"))
        assertEquals(7, Releases.buildOf("build-7"))
        assertNull(Releases.buildOf("v1.0"))
    }

    @Test fun fingerprintsAreUppercaseHexLikeTheCatalogs() {
        assertEquals("BA7816BF8F01CFEA414140DE5DAE2223B00361A396177A9CB410FF61F20015AD", Releases.sha256("abc".toByteArray()))
        assertEquals(64, Catalog.RELEASE_CERT_SHA256.length)
    }

    @Test fun everyAppHasItsOwnRepoAndApk() {
        assertEquals(Catalog.apps.size, Catalog.apps.map { it.packageName }.toSet().size)
        Catalog.apps.forEach { assert(it.asset.endsWith(".apk") && it.repo.startsWith("ShoalApps/")) }
    }
}
