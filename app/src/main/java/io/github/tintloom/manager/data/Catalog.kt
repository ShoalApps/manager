package io.github.tintloom.manager.data

/** A Tintloom app the Manager looks after. Every push to its repo publishes a `build-<n>` release. */
data class TintloomApp(
    val name: String,
    val packageName: String,
    val repo: String,
    val asset: String,
    val description: String,
)

object Catalog {
    val video = TintloomApp("Tintloom Video", "com.aaronateataco.materialtube", "Tintloom/video", "TintloomVideo.apk", "Watch YouTube videos with SponsorBlock, DeArrow and a TV mode")
    val social = TintloomApp("Tintloom Social", "io.github.tintloom.social", "Tintloom/social", "TintloomSocial.apk", "Bluesky with code blocks and rich formatting")
    val clips = TintloomApp("Tintloom Clips", "io.github.tintloom.clips", "Tintloom/clips", "TintloomClips.apk", "Shorts, TikTok and Reels in one place")
    val manager = TintloomApp("Tintloom Manager", "io.github.tintloom.manager", "Tintloom/manager", "TintloomManager.apk", "Installs and updates the Tintloom apps")

    val apps = listOf(video, social, clips, manager)

    fun of(packageName: String) = apps.firstOrNull { it.packageName == packageName }

    /**
     * SHA-256 of the certificate every Tintloom release is signed with. The Manager installs
     * nothing else, so a tampered download can't be installed even with root.
     */
    const val RELEASE_CERT_SHA256 = "2BA3700D9C8733489CF865E962E0DAD438C00C62DA9B4A89B44ACB4E6325ACF4"
}
