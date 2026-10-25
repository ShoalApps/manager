package io.github.swordfishapps.manager

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.swordfishapps.manager.data.Catalog
import io.github.swordfishapps.manager.data.Releases
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** The Manager must refuse anything that isn't the right app signed with the Swordfish key. */
@RunWith(AndroidJUnit4::class)
class VerifyTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    // This test build is signed with a development key, not Swordfish's.
    private val debugApk = File(context.packageCodePath)

    @Test fun aDifferentAppIsRefused() {
        val why = Releases.verify(context, debugApk, Catalog.video)
        assertTrue("got: $why", why != null && why.contains("different app"))
    }

    @Test fun theRightAppWithTheWrongKeyIsRefused() {
        val sameName = Catalog.manager.copy(packageName = context.packageName)
        assertEquals("The download isn't signed by Swordfish, so it wasn't installed", Releases.verify(context, debugApk, sameName))
    }

    @Test fun somethingThatIsntAnAppIsRefused() {
        val junk = File(context.cacheDir, "junk.apk").apply { writeText("not an apk") }
        assertEquals("The download isn't a valid app", Releases.verify(context, junk, Catalog.video))
    }
}
