package app.oakkar.vpn

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.util.Base64

/** A key after parsing. [p] holds normalized parameters (id, password, method, net, security, sni, host, path, ...). */
data class ParsedKey(
    val raw: String,
    val proto: String,
    val name: String,
    val host: String,
    val port: Int,
    val p: Map<String, String>,
)

class KeyException(message: String) : Exception(message)

object KeyParser {
    private val SCHEME = Regex("^([a-zA-Z0-9]+)://")
    private val KEY_REGEX = Regex("""\b(?:vmess|vless|trojan|ssconf|ss|hysteria2|hy2|tuic)://[^\s"'<>`]+""", RegexOption.IGNORE_CASE)

    // ---------- helpers ----------

    private fun b64dec(s: String): String {
        val t = s.trim().replace(Regex("\\s+"), "").replace('-', '+').replace('_', '/')
        return String(Base64.getDecoder().decode(t), Charsets.UTF_8)
    }

    private fun b64enc(s: String): String = Base64.getEncoder().encodeToString(s.toByteArray(Charsets.UTF_8))

    private fun sd(s: String): String =
        try { URLDecoder.decode(s.replace("+", "%2B"), "UTF-8") } catch (e: Exception) { s }

    private fun parseQuery(q: String): Map<String, String> =
        q.split('&').filter { it.contains('=') }.associate { it.substringBefore('=') to sd(it.substringAfter('=')) }

    private fun splitHostPort(hp: String): Pair<String, Int> {
        val t = hp.trimEnd('/')
        if (t.startsWith("[")) {
            val i = t.indexOf(']')
            if (i < 0) throw KeyException("IPv6 address မမှန်ပါ")
            return t.substring(1, i) to (t.substring(i + 1).removePrefix(":").toIntOrNull() ?: 0)
        }
        val i = t.lastIndexOf(':')
        return if (i < 0) t to 0 else t.substring(0, i) to (t.substring(i + 1).toIntOrNull() ?: 0)
    }

    private class Url(
        val userInfo: String, val host: String, val port: Int,
        val path: String, val query: Map<String, String>, val fragment: String,
    )

    private fun splitUrl(raw: String): Url {
        val rest = raw.substringAfter("://")
        val fragment = sd(rest.substringAfter('#', ""))
        val noFrag = rest.substringBefore('#')
        val queryStr = noFrag.substringAfter('?', "")
        val beforeQuery = noFrag.substringBefore('?')
        val authority = beforeQuery.substringBefore('/')
        val path = if (beforeQuery.contains('/')) "/" + beforeQuery.substringAfter('/') else ""
        val userInfo = if (authority.contains('@')) authority.substringBeforeLast('@') else ""
        val (host, port) = splitHostPort(authority.substringAfterLast('@'))
        return Url(userInfo, host, port, path, parseQuery(queryStr), fragment)
    }

    // ---------- public API ----------

    /** Finds every key in pasted text. Also accepts a base64 subscription body and Outline JSON. */
    fun extract(text: String): List<String> {
        var found = KEY_REGEX.findAll(text).map { it.value }.toList()
        if (found.isEmpty()) {
            val t = text.trim()
            if (t.startsWith("{")) {
                try {
                    val j = JSONObject(t)
                    val port = j.optInt("server_port", j.optInt("port", 0))
                    val server = j.optString("server")
                    val password = j.optString("password")
                    if (server.isNotEmpty() && port > 0 && password.isNotEmpty()) {
                        val method = j.optString("method", "chacha20-ietf-poly1305")
                        found = listOf("ss://" + b64enc("$method:$password") + "@$server:$port/?outline=1")
                    }
                } catch (e: Exception) { /* not JSON */ }
            } else if (!t.startsWith("http", ignoreCase = true) && t.length >= 16) {
                try { found = KEY_REGEX.findAll(b64dec(t)).map { it.value }.toList() } catch (e: Exception) { /* not base64 */ }
            }
        }
        return found.distinct()
    }

    fun parse(input: String): ParsedKey {
        val raw = input.trim()
        val m = SCHEME.find(raw) ?: throw KeyException("Key ပုံစံ မမှန်ပါ")
        var proto = m.groupValues[1].lowercase()
        if (proto == "hy2") proto = "hysteria2"
        val key = when (proto) {
            "vmess" -> vmess(raw)
            "ss" -> shadowsocks(raw)
            "ssconf" -> ssconf(raw)
            "vless", "trojan", "hysteria2", "tuic" -> generic(raw, proto)
            else -> throw KeyException("မသိသော protocol: $proto")
        }
        if (key.host.isBlank()) throw KeyException("Server (host) မပါပါ")
        if (proto != "ssconf" && key.port !in 1..65535) throw KeyException("Port မမှန်ပါ")
        return key
    }

    /** Outline dynamic access key (ssconf://). Fetches the JSON config; call off the main thread. */
    fun resolveSsconf(k: ParsedKey): ParsedKey {
        val url = k.p["url"] ?: throw KeyException("ssconf URL မရှိပါ")
        val body = try {
            val c = URL(url).openConnection() as HttpURLConnection
            c.connectTimeout = 10_000
            c.readTimeout = 10_000
            c.inputStream.bufferedReader().use { it.readText() }.trim()
        } catch (e: Exception) {
            throw KeyException("Outline dynamic key ကို ရယူ၍မရပါ")
        }
        if (body.startsWith("ss://")) return parse(body)
        val j = try { JSONObject(body) } catch (e: Exception) { throw KeyException("Dynamic key ၏ အဖြေ ပုံစံ မမှန်ပါ") }
        val port = j.optInt("server_port", 0)
        val server = j.optString("server")
        if (server.isEmpty() || port == 0) throw KeyException("Dynamic key တွင် server မပါပါ")
        return ParsedKey(
            k.raw, "ss", k.name, server, port,
            mapOf("method" to j.optString("method", "chacha20-ietf-poly1305"), "password" to j.optString("password")),
        )
    }

    // ---------- per protocol ----------

    private fun vmess(raw: String): ParsedKey {
        val j = try { JSONObject(b64dec(raw.substring(8))) } catch (e: Exception) { throw KeyException("vmess ကို ဖတ်၍မရပါ") }
        val host = j.optString("add")
        val port = j.optString("port").toIntOrNull() ?: 0
        val p = mapOf(
            "id" to j.optString("id"),
            "aid" to j.optString("aid", "0").ifBlank { "0" },
            "scy" to j.optString("scy", "auto").ifBlank { "auto" },
            "net" to j.optString("net", "tcp").ifBlank { "tcp" },
            "security" to if (j.optString("tls") == "tls") "tls" else "none",
            "host" to j.optString("host"),
            "path" to j.optString("path"),
            "sni" to j.optString("sni"),
            "alpn" to j.optString("alpn"),
            "fp" to j.optString("fp"),
        )
        return ParsedKey(raw, "vmess", j.optString("ps").ifBlank { "$host:$port" }, host, port, p)
    }

    private fun shadowsocks(raw: String): ParsedKey {
        var body = raw.substring(5)
        var name = ""
        body.indexOf('#').let { if (it >= 0) { name = sd(body.substring(it + 1)); body = body.substring(0, it) } }
        var query = ""
        body.indexOf('?').let { if (it >= 0) { query = body.substring(it + 1); body = body.substring(0, it) } }
        body = body.trimEnd('/')

        val userInfo: String
        val hp: String
        if (body.contains('@')) {
            val at = body.lastIndexOf('@')
            val ui = body.substring(0, at)
            hp = body.substring(at + 1)
            userInfo = if (ui.contains(':')) sd(ui) else try { b64dec(sd(ui)) } catch (e: Exception) {
                throw KeyException("ss key ၏ user info ကို ဖတ်၍မရပါ")
            }
        } else {
            val dec = try { b64dec(body) } catch (e: Exception) { throw KeyException("ss key ကို ဖတ်၍မရပါ") }
            val at = dec.lastIndexOf('@')
            if (at < 0) throw KeyException("ss key တွင် server မပါပါ")
            userInfo = dec.substring(0, at)
            hp = dec.substring(at + 1)
        }
        val ci = userInfo.indexOf(':')
        if (ci < 0) throw KeyException("ss key တွင် cipher/password မပါပါ")
        val (host, port) = splitHostPort(hp)
        val p = mapOf("method" to userInfo.substring(0, ci), "password" to userInfo.substring(ci + 1)) +
            parseQuery(query).filterKeys { it == "plugin" || it == "outline" }
        return ParsedKey(raw, "ss", name.ifBlank { "$host:$port" }, host, port, p)
    }

    private fun ssconf(raw: String): ParsedKey {
        val u = splitUrl(raw.replaceFirst(Regex("^ssconf", RegexOption.IGNORE_CASE), "https"))
        val port = if (u.port == 0) 443 else u.port
        val url = "https://${u.host}:$port${u.path}"
        return ParsedKey(raw, "ssconf", u.fragment.ifBlank { u.host }, u.host, port, mapOf("url" to url))
    }

    private fun generic(raw: String, proto: String): ParsedKey {
        val u = splitUrl(raw)
        val q = u.query
        val p = HashMap<String, String>()
        when (proto) {
            "vless" -> { p["id"] = sd(u.userInfo); p["encryption"] = q["encryption"] ?: "none" }
            "trojan", "hysteria2" -> p["password"] = sd(u.userInfo)
            "tuic" -> { p["id"] = sd(u.userInfo.substringBefore(':')); p["password"] = sd(u.userInfo.substringAfter(':', "")) }
        }
        p["net"] = q["type"] ?: "tcp"
        p["security"] = q["security"] ?: if (proto == "trojan") "tls" else "none"
        for (k in listOf("sni", "host", "path", "flow", "fp", "pbk", "sid", "spx", "alpn", "serviceName", "headerType")) {
            q[k]?.let { p[k] = it }
        }
        return ParsedKey(raw, proto, u.fragment.ifBlank { "${u.host}:${u.port}" }, u.host, u.port, p)
    }
}
