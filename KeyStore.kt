package app.oakkar.vpn

import android.content.Context
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Keeps the key list, the selected key and data-usage counters in SharedPreferences. */
class KeyStore(context: Context) {
    private val sp = context.getSharedPreferences("oakkar", Context.MODE_PRIVATE)

    fun load(): List<ParsedKey> {
        val arr = try { JSONArray(sp.getString("keys", "[]")) } catch (e: Exception) { JSONArray() }
        val out = ArrayList<ParsedKey>()
        for (i in 0 until arr.length()) {
            try { out.add(KeyParser.parse(arr.getString(i))) } catch (e: Exception) { /* skip broken entry */ }
        }
        return out
    }

    fun save(raws: List<String>) {
        sp.edit().putString("keys", JSONArray(raws).toString()).apply()
    }

    var selected: String?
        get() = sp.getString("selected", null)
        set(v) { sp.edit().putString("selected", v).apply() }

    private fun dayKey() = "d_" + SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
    private fun monthKey() = "m_" + SimpleDateFormat("yyyyMM", Locale.US).format(Date())

    fun addUsage(bytes: Long) {
        if (bytes <= 0) return
        sp.edit()
            .putLong(dayKey(), sp.getLong(dayKey(), 0) + bytes)
            .putLong(monthKey(), sp.getLong(monthKey(), 0) + bytes)
            .putLong("total", sp.getLong("total", 0) + bytes)
            .apply()
    }

    /** today, this month, all time (bytes) */
    fun usage(): Triple<Long, Long, Long> =
        Triple(sp.getLong(dayKey(), 0), sp.getLong(monthKey(), 0), sp.getLong("total", 0))
}
