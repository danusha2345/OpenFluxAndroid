package io.github.p1neapplexpress.openflux.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import io.github.p1neapplexpress.openflux.R
import io.github.p1neapplexpress.openflux.ui.MainActivity

class VpnNotificationManager(private val service: Service) {

    companion object {
        const val CHANNEL_ID = "io.github.p1neapplexpress.libp1npplydtransport.so.vpn"
        const val NOTIFICATION_ID = 1
    }

    fun startForeground() {
        createChannel()
        service.startForeground(
            NOTIFICATION_ID,
            buildNotification(service.getString(R.string.notify_connecting)),
        )
    }

    fun startSpeedUpdates() {
        val manager = service.getSystemService(NotificationManager::class.java) ?: return
        manager.notify(NOTIFICATION_ID, buildNotification(service.getString(R.string.notify_msg)))
    }

    fun stopSpeedUpdates() = Unit

    private fun buildNotification(text: String): Notification {
        val contentIntent = PendingIntent.getActivity(
            service,
            0,
            Intent(service, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stopIntent = PendingIntent.getService(
            service,
            1,
            Intent(service, SocksVpnService::class.java).setAction(SocksVpnService.ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(service, CHANNEL_ID)
            .setContentTitle(service.getString(R.string.notify_title))
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(contentIntent)
            .addAction(R.drawable.ic_power, service.getString(R.string.notification_stop), stopIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val mgr = service.getSystemService(NotificationManager::class.java) ?: return
        if (mgr.getNotificationChannel(CHANNEL_ID) != null) return
        val ch = NotificationChannel(
            CHANNEL_ID,
            service.getString(R.string.channel_name),
            NotificationManager.IMPORTANCE_LOW,
        )
        mgr.createNotificationChannel(ch)
    }
}
