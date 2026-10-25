package io.github.shoalapps.shoal.data

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.shoalapps.shoal.Graph
import io.github.shoalapps.shoal.MainActivity
import io.github.shoalapps.shoal.R
import java.util.concurrent.TimeUnit

/**
 * Once a day: checks every installed Shoal app for a newer release. With Shizuku or root it
 * installs them silently; otherwise it posts a notification to install with a tap.
 */
class UpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val s = Graph.store.settings.value
        if (!s.autoUpdate) return Result.success()
        Graph.check()
        val pending = Catalog.apps.filter { Graph.states[it.packageName]?.updateAvailable == true }
        if (pending.isEmpty()) return Result.success()
        val method = Installer.pick(applicationContext, s.installMethod)
        if (method != Installer.Method.STANDARD) {
            Graph.updateAll()
            val failed = pending.filter { Graph.states[it.packageName]?.updateAvailable == true }
            notify(if (failed.isEmpty()) "Updated ${pending.joinToString { it.name }}" else "Couldn't update ${failed.joinToString { it.name }}", if (failed.isEmpty()) null else "Tap to try again")
        } else {
            notify(
                if (pending.size == 1) "${pending[0].name} has an update" else "${pending.size} Shoal updates",
                "Tap to install. With Shizuku or root, the Manager can do this for you.",
            )
        }
        return Result.success()
    }

    private fun notify(title: String, text: String?) {
        val ctx = applicationContext
        if (android.os.Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val open = PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_mono)
            .setContentTitle(title)
            .apply { if (text != null) setContentText(text) }
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(ctx).notify(1, n)
    }

    companion object {
        const val CHANNEL = "updates"

        fun schedule(context: Context) {
            val wifi = Graph.store.settings.value.wifiOnly
            val request = PeriodicWorkRequestBuilder<UpdateWorker>(24, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(if (wifi) NetworkType.UNMETERED else NetworkType.CONNECTED).setRequiresBatteryNotLow(true).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("updates", ExistingPeriodicWorkPolicy.UPDATE, request)
        }
    }
}
