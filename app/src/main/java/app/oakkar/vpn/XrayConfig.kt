package app.oakkar.vpn

import org.json.JSONArray
import org.json.JSONObject

/** Builds an Xray JSON config (tun inbound -> chosen outbound) from a parsed key. */
object XrayConfig {

    fun build(k: ParsedKey): String {
        val outbound = when (k.proto) {
            "vmess" -> vmess(k)
            "vless" -> vless(k)
            "trojan" -> trojan(k)
            "ss" -> shadowsocks(k)
            else -> throw KeyException("${k.proto} ကို ဤ app က မထောက်ပံ့သေးပါ")
        }.put("tag", "proxy")

        val tunInbound = JSONObject()
            .put("tag", "tun")
            .put("protocol", "tun")
            .put("settings", JSONObject().put("name", "xray0").put("MTU", 1500).put("userLevel", 8))
            .put(
                "sniffing",
                JSONObject().put("enabled", true)
                    .put("destOverride", JSONArray().put("http").put("tls").put("quic")),
            )

        return JSONObject()
            .put("log", JSONObject().put("loglevel", "warning"))
            .put("dns", JSONObject().put("servers", JSONArray().put("1.1.1.1").put("8.8.8.8")))
            .put("inbounds", JSONArray().put(tunInbound))
            .put(
                "outbounds",
                JSONArray().put(outbound).put(JSONObject().put("protocol", "freedom").put("tag", "direct")),
            )
            .toString()
    }

    private fun vmess(k: ParsedKey): JSONObject {
        val user = JSONObject()
            .put("id", k.p["id"].orEmpty())
            .put("alterId", k.p["aid"]?.toIntOrNull() ?: 0)
            .put("security", k.p["scy"] ?: "auto")
        val vnext = JSONObject().put("address", k.host).put("port", k.port).put("users", JSONArray().put(user))
        return JSONObject().put("protocol", "vmess")
            .put("settings", JSONObject().put("vnext", JSONArray().put(vnext)))
            .put("streamSettings", stream(k))
    }

    private fun vless(k: ParsedKey): JSONObject {
        val user = JSONObject()
            .put("id", k.p["id"].orEmpty())
            .put("encryption", k.p["encryption"] ?: "none")
        k.p["flow"]?.takeIf { it.isNotBlank() }?.let { user.put("flow", it) }
        val vnext = JSONObject().put("address", k.host).put("port", k.port).put("users", JSONArray().put(user))
        return JSONObject().put("protocol", "vless")
            .put("settings", JSONObject().put("vnext", JSONArray().put(vnext)))
            .put("streamSettings", stream(k))
    }

    private fun trojan(k: ParsedKey): JSONObject {
        val server = JSONObject().put("address", k.host).put("port", k.port).put("password", k.p["password"].orEmpty())
        return JSONObject().put("protocol", "trojan")
            .put("settings", JSONObject().put("servers", JSONArray().put(server)))
            .put("streamSettings", stream(k))
    }

    private fun shadowsocks(k: ParsedKey): JSONObject {
        if (!k.p["plugin"].isNullOrBlank()) throw KeyException("Shadowsocks plugin ကို မထောက်ပံ့သေးပါ")
        val server = JSONObject().put("address", k.host).put("port", k.port)
            .put("method", k.p["method"].orEmpty()).put("password", k.p["password"].orEmpty())
        return JSONObject().put("protocol", "shadowsocks")
            .put("settings", JSONObject().put("servers", JSONArray().put(server)))
    }

    private fun stream(k: ParsedKey): JSONObject {
        val p = k.p
        val net = p["net"] ?: "tcp"
        val sec = p["security"] ?: "none"
        val sni = p["sni"].orEmpty().ifBlank { p["host"].orEmpty() }.ifBlank { k.host }
        val s = JSONObject().put("network", net).put("security", sec)

        when (sec) {
            "tls" -> {
                val tls = JSONObject().put("serverName", sni).put("allowInsecure", false)
                p["fp"]?.takeIf { it.isNotBlank() }?.let { tls.put("fingerprint", it) }
                p["alpn"]?.takeIf { it.isNotBlank() }?.let { tls.put("alpn", JSONArray(it.split(','))) }
                s.put("tlsSettings", tls)
            }
            "reality" -> s.put(
                "realitySettings",
                JSONObject().put("serverName", sni)
                    .put("fingerprint", p["fp"].orEmpty().ifBlank { "chrome" })
                    .put("publicKey", p["pbk"].orEmpty())
                    .put("shortId", p["sid"].orEmpty())
                    .put("spiderX", p["spx"].orEmpty()),
            )
        }

        when (net) {
            "ws" -> {
                val ws = JSONObject().put("path", p["path"].orEmpty().ifBlank { "/" })
                p["host"]?.takeIf { it.isNotBlank() }?.let { ws.put("headers", JSONObject().put("Host", it)) }
                s.put("wsSettings", ws)
            }
            "grpc" -> s.put("grpcSettings", JSONObject().put("serviceName", p["serviceName"].orEmpty()))
            "httpupgrade" -> s.put(
                "httpupgradeSettings",
                JSONObject().put("path", p["path"].orEmpty().ifBlank { "/" }).put("host", p["host"].orEmpty()),
            )
        }
        return s
    }
}
