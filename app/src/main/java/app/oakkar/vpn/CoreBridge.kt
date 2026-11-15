package app.oakkar.vpn

import android.content.Context
import libv2ray.CoreCallbackHandler
import libv2ray.CoreController
import libv2ray.Libv2ray

/**
 * The only file that touches the Xray core (libv2ray.aar from 2dust/AndroidLibXrayLite).
 * Written against the CoreController API (newCoreController / startLoop(config, tunFd)).
 * If a different libv2ray version is used and this file fails to compile, adapt it here only.
 */
class CoreBridge(private val context: Context) {
    private var controller: CoreController? = null

    fun start(config: String, tunFd: Int) {
        Libv2ray.initCoreEnv(context.filesDir.absolutePath, "oakkar-vpn")
        val handler = object : CoreCallbackHandler {
            override fun startup(): Long = 0
            override fun shutdown(): Long = 0
            override fun onEmitStatus(code: Long, message: String?): Long = 0
        }
        val c = Libv2ray.newCoreController(handler)
        c.startLoop(config, tunFd)
        controller = c
    }

    fun stop() {
        try { controller?.stopLoop() } catch (e: Exception) { /* already stopped */ }
        controller = null
    }
}
