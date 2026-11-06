package com.orra.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class OrraCallEntry(
    val name: String,
    val number: String,
    val timestamp: Long,
    val direction: String = "Outgoing"
)

private const val CALL_PREFS = "orra_call_history"
private const val CALLS_KEY = "entries"
private const val MAX_CALLS = 50

fun recordOrraCall(context: Context, name: String, number: String, direction: String = "Outgoing") {
    if (number.isBlank()) return
    val prefs = context.getSharedPreferences(CALL_PREFS, Context.MODE_PRIVATE)
    val current = runCatching { JSONArray(prefs.getString(CALLS_KEY, "[]")) }.getOrDefault(JSONArray())
    val updated = JSONArray()
    updated.put(JSONObject().apply {
        put("name", name.ifBlank { number })
        put("number", number)
        put("timestamp", System.currentTimeMillis())
        put("direction", direction)
    })
    for (i in 0 until minOf(current.length(), MAX_CALLS - 1)) {
        updated.put(current.getJSONObject(i))
    }
    prefs.edit().putString(CALLS_KEY, updated.toString()).apply()
}

fun readOrraCallHistory(context: Context): List<OrraCallEntry> {
    val raw = context.getSharedPreferences(CALL_PREFS, Context.MODE_PRIVATE)
        .getString(CALLS_KEY, "[]")
    val array = runCatching { JSONArray(raw) }.getOrDefault(JSONArray())
    return buildList {
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val number = item.optString("number")
            if (number.isNotBlank()) {
                add(
                    OrraCallEntry(
                        name = item.optString("name", number),
                        number = number,
                        timestamp = item.optLong("timestamp"),
                        direction = item.optString("direction", "Outgoing")
                    )
                )
            }
        }
    }
}

fun clearOrraCallHistory(context: Context) {
    context.getSharedPreferences(CALL_PREFS, Context.MODE_PRIVATE)
        .edit().remove(CALLS_KEY).apply()
}
