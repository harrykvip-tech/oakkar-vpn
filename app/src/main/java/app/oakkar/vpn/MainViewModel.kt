package app.oakkar.vpn

import android.app.Application
import android.content.Intent
import android.net.TrafficStats
import android.os.Process
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val store = KeyStore(app)

    val keys = MutableStateFlow(store.load())
    val selected = MutableStateFlow(store.selected)
    val message = MutableStateFlow<String?>(null)
    val speed = MutableStateFlow(0L to 0L) // download, upload bytes per second
    val usage = MutableStateFlow(store.usage())

    init { startMeter() }

    fun currentKey(): ParsedKey? = keys.value.firstOrNull { it.raw == selected.value } ?: keys.value.firstOrNull()

    fun select(raw: String) {
        selected.value = raw
        store.selected = raw
    }

    fun addKeys(text: String) {
        val found = KeyParser.extract(text)
        if (found.isEmpty()) {
            message.value = if (text.trim().startsWith("http", ignoreCase = true)) {
                "Subscription link ကို တိုက်ရိုက် မဖတ်နိုင်ပါ။ link ထဲမှ key စာသားကို paste လုပ်ပါ။"
            } else {
                "Key မတွေ့ပါ။ vmess:// ကဲ့သို့ စတင်သော key ကို paste လုပ်ပါ။"
            }
            return
        }
        val list = keys.value.toMutableList()
        var added = 0; var dup = 0; var bad = 0
        for (raw in found) {
            if (list.any { it.raw == raw }) { dup++; continue }
            try { list.add(KeyParser.parse(raw)); added++ } catch (e: Exception) { bad++ }
        }
        keys.value = list
        store.save(list.map { it.raw })
        if (selected.value == null && list.isNotEmpty()) select(list.first().raw)
        message.value = "$added ခု ထည့်ပြီးပါပြီ" +
            (if (dup > 0) " · $dup ခု ထပ်နေသည်" else "") +
            (if (bad > 0) " · $bad ခု မှားနေသည်" else "")
    }

    fun remove(raw: String) {
        val list = keys.value.filter { it.raw != raw }
        keys.value = list
        store.save(list.map { it.raw })
        if (selected.value == raw) {
            selected.value = list.firstOrNull()?.raw
            store.selected = selected.value
        }
    }

    fun clearAll() {
        keys.value = emptyList()
        store.save(emptyList())
        selected.value = null
        store.selected = null
    }

    fun connect() {
        val key = currentKey()
        if (key == null) {
            VpnState.error.value = "Key အရင်ထည့်ပါ"
            return
        }
        VpnState.error.value = null
        VpnState.status.value = Status.Connecting
        viewModelScope.launch {
            try {
                val resolved = withContext(Dispatchers.IO) {
                    if (key.proto == "ssconf") KeyParser.resolveSsconf(key) else key
                }
                val config = XrayConfig.build(resolved)
                val app = getApplication<Application>()
                val i = Intent(app, OakVpnService::class.java)
                    .setAction(OakVpnService.ACTION_START)
                    .putExtra(OakVpnService.EXTRA_CONFIG, config)
                    .putExtra(OakVpnService.EXTRA_NAME, key.name)
                app.startForegroundService(i)
            } catch (e: Exception) {
                VpnState.error.value = e.message ?: "ချိတ်ဆက်၍မရပါ"
                VpnState.status.value = Status.Disconnected
            }
        }
    }

    fun disconnect() {
        val app = getApplication<Application>()
        app.startService(Intent(app, OakVpnService::class.java).setAction(OakVpnService.ACTION_STOP))
        VpnState.status.value = Status.Disconnected
    }

    /** Samples this app's own traffic once a second. The core runs in-process, so this equals tunnel traffic. */
    private fun startMeter() {
        viewModelScope.launch {
            val uid = Process.myUid()
            var lastRx = TrafficStats.getUidRxBytes(uid)
            var lastTx = TrafficStats.getUidTxBytes(uid)
            while (true) {
                delay(1000)
                val rx = TrafficStats.getUidRxBytes(uid)
                val tx = TrafficStats.getUidTxBytes(uid)
                if (VpnState.status.value == Status.Connected && rx >= lastRx && tx >= lastTx && lastRx >= 0 && lastTx >= 0) {
                    val dr = rx - lastRx
                    val dt = tx - lastTx
                    speed.value = dr to dt
                    if (dr + dt > 0) { store.addUsage(dr + dt); usage.value = store.usage() }
                } else {
                    speed.value = 0L to 0L
                }
                lastRx = rx
                lastTx = tx
            }
        }
    }
}
