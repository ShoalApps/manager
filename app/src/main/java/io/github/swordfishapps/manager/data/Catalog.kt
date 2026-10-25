package io.github.swordfishapps.manager.data

/** A Swordfish app the Manager looks after. Every push to its repo publishes a `build-<n>` release. */
data class SwordfishApp(
    val name: String,
    val packageName: String,
    val repo: String,
    val asset: String,
    val description: String,
)

object Catalog {
    val video = SwordfishApp("Swordfish Video", "io.github.swordfishapps.video", "SwordfishApps/video", "SwordfishVideo.apk", "Watch YouTube videos with SponsorBlock, DeArrow and a TV mode")
    val social = SwordfishApp("Swordfish Social", "io.github.swordfishapps.social", "SwordfishApps/social", "SwordfishSocial.apk", "Bluesky with code blocks and rich formatting")
    val clips = SwordfishApp("Swordfish Clips", "io.github.swordfishapps.clips", "SwordfishApps/clips", "SwordfishClips.apk", "Shorts, TikTok and Reels in one place")
    val manager = SwordfishApp("Swordfish Manager", "io.github.swordfishapps.manager", "SwordfishApps/manager", "SwordfishManager.apk", "Installs and updates the Swordfish apps")

    val apps = listOf(video, social, clips, manager)

    fun of(packageName: String) = apps.firstOrNull { it.packageName == packageName }

    /**
     * SHA-256 of the certificate every Swordfish release is signed with. The Manager installs
     * nothing else, so a tampered download can't be installed even with root.
     */
    const val RELEASE_CERT_SHA256 = "2BA3700D9C8733489CF865E962E0DAD438C00C62DA9B4A89B44ACB4E6325ACF4"
}
