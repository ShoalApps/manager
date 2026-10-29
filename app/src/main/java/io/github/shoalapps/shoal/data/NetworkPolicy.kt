package io.github.shoalapps.shoal.data

import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import io.github.shoalapps.shoal.Graph

object NetworkPolicy {
    private val cm get() = Graph.app.getSystemService(ConnectivityManager::class.java)
    fun online() = cm?.getNetworkCapabilities(cm?.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
    fun allowed() = online() && !(cm?.isActiveNetworkMetered != false &&
        cm?.restrictBackgroundStatus == ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED)
    fun downloadsAllowed() = allowed() && (!Graph.store.settings.value.wifiOnly || cm?.isActiveNetworkMetered == false)
}
