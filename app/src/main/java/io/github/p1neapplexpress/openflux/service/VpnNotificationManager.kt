package io.github.p1neapplexpress.openflux.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.net.TrafficStats
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import io.github.p1neapplexpress.openflux.R
import io.github.p1neapplexpress.openflux.event.AppEvent
import io.github.p1neapplexpress.openflux.event.EventBus
import io.github.p1neapplexpress.openflux.ui.MainActivity

class VpnNotificationManager(private val service: Service) {

    companion object {
        const val CHANNEL_ID = "io.github.p1neapplexpress.libp1npplydtransport.so.vpn"
        const val NOTIFICATION_ID = 1
        private const val UPDATE_INTERVAL_MS = 1000L
    }

    private val handler = Handler(Looper.getMainLooper())
    private val uid = Process.myUid()
    private var lastRxBytes = 0L
    private var lastTxBytes = 0L
    private var lastSampleAt = 0L
    private var sessionRxStart = 0L
    private var sessionTxStart = 0L

    // speedUpdater publishes session traffic to the app once a second.
    // The foreground notification itself remains static: repeatedly calling
    // notify() makes some Android/OEM status bars reorder this icon against
    // neighbouring notification icons every second.
    // TrafficStats is used instead of tapping the packet path directly:
    // the data plane here is a native tun2socks process, opaque to this
    // Kotlin code, but per-UID counters keep working regardless of which
    // process is actually moving bytes through the TUN interface.
    private val speedUpdater = object : Runnable {
        override fun run() {
            val now = SystemClock.elapsedRealtime()
            val elapsedMs = (now - lastSampleAt).coerceAtLeast(1)
            val rxBytes = TrafficStats.getUidRxBytes(uid).coerceAtLeast(0)
            val txBytes = TrafficStats.getUidTxBytes(uid).coerceAtLeast(0)
            val rxPerSec = (rxBytes - lastRxBytes) * 1000 / elapsedMs
            val txPerSec = (txBytes - lastTxBytes) * 1000 / elapsedMs
            lastRxBytes = rxBytes
            lastTxBytes = txBytes
            lastSampleAt = now
            EventBus.dispatch(
                AppEvent.TrafficSnapshot(
                    txBytes = (txBytes - sessionTxStart).coerceAtLeast(0),
                    rxBytes = (rxBytes - sessionRxStart).coerceAtLeast(0),
                    txBytesPerSecond = txPerSec.coerceAtLeast(0),
                    rxBytesPerSecond = rxPerSec.coerceAtLeast(0),
                )
            )
            handler.postDelayed(this, UPDATE_INTERVAL_MS)
        }
    }

    fun startForeground() {
        createChannel()
        service.startForeground(NOTIFICATION_ID, buildNotification(service.getString(R.string.notify_msg)))
    }

    // startSpeedUpdates begins the live upload/download indicator; call once
    // the tunnel is actually passing traffic (tun2socks reported running).
    fun startSpeedUpdates() {
        lastRxBytes = TrafficStats.getUidRxBytes(uid).coerceAtLeast(0)
        lastTxBytes = TrafficStats.getUidTxBytes(uid).coerceAtLeast(0)
        sessionRxStart = lastRxBytes
        sessionTxStart = lastTxBytes
        lastSampleAt = SystemClock.elapsedRealtime()
        handler.removeCallbacks(speedUpdater)
        handler.post(speedUpdater)
    }

    // stopSpeedUpdates cancels the periodic refresh; call when the tunnel
    // stops so a stale speed reading isn't left on screen.
    fun stopSpeedUpdates() {
        handler.removeCallbacks(speedUpdater)
        EventBus.dispatch(AppEvent.TrafficSnapshot(0, 0, 0, 0))
    }

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
