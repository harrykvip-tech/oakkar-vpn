package app.oakkar.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.flow.MutableStateFlow

enum class Status { Disconnected, Connecting, Connected }

/** Process-wide connection state shared between the service and the UI. */
object VpnState {
    val status = MutableStateFlow(Status.Disconnected)
    val connectedAt = MutableStateFlow(0L)
    val error = MutableStateFlow<String?>(null)
}

class OakVpnService : VpnService() {
    private var tun: ParcelFileDescriptor? = null
    private var core: CoreBridge? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val config = intent.getStringExtra(EXTRA_CONFIG)
                if (config == null) {
                    fail("Config မရှိပါ")
                } else {
                    goForeground(intent.getStringExtra(EXTRA_NAME) ?: "Oakkar VPN")
                    connect(config)
                }
            }
            ACTION_STOP -> disconnect()
        }
        return START_NOT_STICKY
    }

    private fun connect(config: String) {
        Thread {
            try {
                val builder = Builder()
                    .setSession("Oakkar VPN")
                    .setMtu(1500)
                    .addAddress("172.19.0.1", 30)
                    .addRoute("0.0.0.0", 0)
                    .addDnsServer("1.1.1.1")
                    .addDnsServer("8.8.8.8")
                    // The core runs inside this app; keep its own sockets out of the tunnel.
                    .addDisallowedApplication(packageName)
                val fd = builder.establish()
                    ?: throw IllegalStateException("VPN ဖွင့်၍မရပါ (ခွင့်ပြုချက် စစ်ပါ)")
                tun = fd
                core = CoreBridge(applicationContext).also { it.start(config, fd.fd) }
                VpnState.connectedAt.value = System.currentTimeMillis()
                VpnState.status.value = Status.Connected
            } catch (e: Throwable) {
                fail(e.message ?: "ချိတ်ဆက်၍မရပါ")
            }
        }.start()
    }

    private fun fail(message: String) {
        VpnState.error.value = message
        disconnect()
    }

    private fun disconnect() {
        cleanup()
        VpnState.status.value = Status.Disconnected
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun cleanup() {
        try { core?.stop() } catch (e: Exception) { /* ignore */ }
        core = null
        try { tun?.close() } catch (e: Exception) { /* ignore */ }
        tun = null
    }

    private fun goForeground(name: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "VPN", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val n = Notification.Builder(this, CHANNEL)
            .setContentTitle("Oakkar VPN")
            .setContentText(name)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(open)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED)
        } else {
            startForeground(1, n)
        }
    }

    override fun onRevoke() = disconnect()

    override fun onDestroy() {
        cleanup()
        if (VpnState.status.value != Status.Disconnected) VpnState.status.value = Status.Disconnected
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "app.oakkar.vpn.START"
        const val ACTION_STOP = "app.oakkar.vpn.STOP"
        const val EXTRA_CONFIG = "config"
        const val EXTRA_NAME = "name"
        private const val CHANNEL = "vpn"
    }
}
