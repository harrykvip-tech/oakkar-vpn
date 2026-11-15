package app.oakkar.vpn

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

private val Teal = Color(0xFF0E7C86)
private val Mint = Color(0xFFEFF9F8)
private val Navy = Color(0xFF14204A)
private val Line = Color(0xFFCFE6E3)
private val Muted = Color(0xFF5F6F7A)
private val Bad = Color(0xFFB3261E)

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = Teal, background = Mint)) { App(vm) }
        }
    }
}

@Composable
private fun App(vm: MainViewModel) {
    var tab by remember { mutableIntStateOf(0) }
    val context = LocalContext.current
    val status by VpnState.status.collectAsState()

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) vm.connect()
    }
    val toggle = {
        if (status == Status.Disconnected) {
            val ask = VpnService.prepare(context)
            if (ask != null) permissionLauncher.launch(ask) else vm.connect()
        } else {
            vm.disconnect()
        }
    }

    val tabs = listOf(
        Triple("ပင်မ", Icons.Default.Home, 0),
        Triple("Servers", Icons.Default.Menu, 1),
        Triple("Key ထည့်ရန်", Icons.Default.Add, 2),
        Triple("ဆက်တင်", Icons.Default.Settings, 3),
    )

    var showSplash by rememberSaveable { mutableStateOf(true) }
    GlassBackground(Modifier.fillMaxSize()) {
    Scaffold(
        containerColor = Color.Transparent,
        bottomBar = { GlassNav(tabs, tab) { tab = it } },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (tab) {
                0 -> Home(vm, onToggle = { toggle() }, openServers = { tab = 1 })
                1 -> Servers(vm)
                2 -> AddKey(vm)
                else -> SettingsTab(vm)
            }
        }
    }
    if (showSplash) Splash { showSplash = false }
    }
}

internal fun fmtBytes(b: Long): String = when {
    b >= 1L shl 30 -> "%.2f GB".format(b / (1L shl 30).toDouble())
    b >= 1L shl 20 -> "%.1f MB".format(b / (1L shl 20).toDouble())
    b >= 1L shl 10 -> "%d KB".format(b shr 10)
    else -> "$b B"
}

private fun fmtClock(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%02d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60)
}

@Composable
private fun Home(vm: MainViewModel, onToggle: () -> Unit, openServers: () -> Unit) {
    val status by VpnState.status.collectAsState()
    val connectedAt by VpnState.connectedAt.collectAsState()
    val error by VpnState.error.collectAsState()
    val keys by vm.keys.collectAsState()
    val selected by vm.selected.collectAsState()
    val speed by vm.speed.collectAsState()
    val usage by vm.usage.collectAsState()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(status) { while (true) { now = System.currentTimeMillis(); delay(1000) } }

    val key = keys.firstOrNull { it.raw == selected } ?: keys.firstOrNull()
    val connected = status == Status.Connected

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Oakkar VPN", color = Teal, fontSize = 24.sp, modifier = Modifier.align(Alignment.Start))
        Spacer(Modifier.height(16.dp))
        OutlinedButton(
            onClick = openServers,
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, Line),
            colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White.copy(alpha = 0.6f), contentColor = Navy),
        ) { Text((key?.name ?: "Key မရှိသေးပါ") + "  ▾", fontSize = 18.sp) }

        Spacer(Modifier.height(8.dp))
        ConnectButton(status, onClick = onToggle)

        Spacer(Modifier.height(24.dp))
        Text(
            when (status) { Status.Disconnected -> "Disconnected"; Status.Connecting -> "Connecting…"; Status.Connected -> "Connected" },
            fontSize = 30.sp, fontWeight = FontWeight.Medium, color = Navy,
        )
        Text(if (connected) fmtClock(now - connectedAt) else "00:00:00", fontSize = 20.sp, color = Muted)
        Text(
            if (connected) "ပုံကိုနှိပ်ပြီး ဖြတ်ပါ" else "ပုံကိုနှိပ်ပြီး ချိတ်ပါ",
            color = Muted, modifier = Modifier.padding(top = 4.dp),
        )
        error?.let { Text(it, color = Bad, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp)) }

        Spacer(Modifier.height(20.dp))
        SpeedCard(speed.first, speed.second)

        Spacer(Modifier.height(14.dp))
        Box(Modifier.fillMaxWidth().glass(RoundedCornerShape(24.dp))) {
            Column(Modifier.padding(20.dp)) {
                Text("Data သုံးစွဲမှု", fontSize = 18.sp, fontWeight = FontWeight.Medium, color = Navy)
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Usage("ဒီနေ့", usage.first)
                    Usage("ဒီလ", usage.second)
                    Usage("စုစုပေါင်း", usage.third)
                }
            }
        }
    }
}

@Composable
private fun Usage(label: String, bytes: Long) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(fmtBytes(bytes), color = Teal, fontSize = 18.sp, fontWeight = FontWeight.Medium)
        Text(label, color = Muted, fontSize = 13.sp)
    }
}

@Composable
private fun Servers(vm: MainViewModel) {
    val keys by vm.keys.collectAsState()
    val selected by vm.selected.collectAsState()
    val current = keys.firstOrNull { it.raw == selected } ?: keys.firstOrNull()

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp)) {
        Text("Servers", fontSize = 24.sp, color = Teal)
        Spacer(Modifier.height(12.dp))
        if (keys.isEmpty()) {
            Text("Key မရှိသေးပါ။ \"Key ထည့်ရန်\" tab တွင် paste လုပ်ပါ။", color = Muted)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(keys, key = { it.raw }) { k ->
                    Box(
                        modifier = Modifier.fillMaxWidth()
                            .glass(RoundedCornerShape(16.dp))
                            .then(if (k == current) Modifier.border(1.5.dp, Teal, RoundedCornerShape(16.dp)) else Modifier)
                            .clickable { vm.select(k.raw) },
                    ) {
                        Row(Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = k == current, onClick = { vm.select(k.raw) })
                            Column(Modifier.weight(1f)) {
                                Text(k.name, fontWeight = FontWeight.Medium, color = Navy)
                                Text("${k.proto.uppercase()} · ${k.host}:${k.port}", color = Muted, fontSize = 13.sp)
                            }
                            IconButton(onClick = { vm.remove(k.raw) }) { Icon(Icons.Default.Delete, "ဖျက်မည်", tint = Muted) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AddKey(vm: MainViewModel) {
    var text by remember { mutableStateOf("") }
    val message by vm.message.collectAsState()
    val clipboard = LocalClipboardManager.current

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Key ထည့်ရန်", fontSize = 24.sp, color = Teal)
        Text(
            "vmess, vless, trojan, ss (Outline ပါ), ssconf key များကို paste လုပ်ပါ။ တစ်ကြောင်းလျှင် တစ်ခု၊ သို့မဟုတ် base64 စာသား ရပါသည်။",
            color = Muted,
        )
        OutlinedTextField(
            value = text, onValueChange = { text = it },
            modifier = Modifier.fillMaxWidth(), minLines = 6, placeholder = { Text("vless://…") },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = { clipboard.getText()?.text?.let { text = it } }) { Text("Paste") }
            Button(
                onClick = { vm.addKeys(text); text = "" },
                colors = ButtonDefaults.buttonColors(containerColor = Teal),
                enabled = text.isNotBlank(),
            ) { Text("ထည့်မည်") }
        }
        message?.let { Text(it, color = Navy) }
    }
}

@Composable
private fun SettingsTab(vm: MainViewModel) {
    var confirm by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("ဆက်တင်", fontSize = 24.sp, color = Teal)
        Text("Oakkar VPN 1.0", color = Navy)
        Text(
            "ထောက်ပံ့သော key: vmess, vless, trojan, ss (Outline), ssconf (Outline dynamic)။ hysteria2 နှင့် tuic ကို parse လုပ်ပေးနိုင်သော်လည်း မချိတ်နိုင်သေးပါ။",
            color = Muted,
        )
        OutlinedButton(onClick = { if (confirm) { vm.clearAll(); confirm = false } else confirm = true }) {
            Text(if (confirm) "ထပ်နှိပ်၍ အတည်ပြုပါ" else "Key အားလုံး ဖျက်မည်", color = Bad)
        }
    }
}
