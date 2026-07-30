package com.simpleclicker.mobile

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class ScriptTap(
    val atMs: Long,
    val x: Int,
    val y: Int
)

data class ClickScript(
    val createdAt: Long,
    val events: List<ScriptTap>
) {
    val durationMs: Long
        get() = events.lastOrNull()?.atMs ?: 0L
}

object ClickScriptStore {
    private const val PREFS = "click_script_store"
    private const val KEY_LATEST_SCRIPT = "latest_script"
    private const val SCRIPT_VERSION = 1

    fun save(context: Context, events: List<ScriptTap>) {
        if (events.isEmpty()) {
            return
        }

        val firstEventAtMs = events.first().atMs
        val jsonEvents = JSONArray()
        events.forEach { event ->
            jsonEvents.put(
                JSONObject()
                    .put("at_ms", (event.atMs - firstEventAtMs).coerceAtLeast(0L))
                    .put("x", event.x)
                    .put("y", event.y)
            )
        }

        val script = JSONObject()
            .put("version", SCRIPT_VERSION)
            .put("created_at", System.currentTimeMillis())
            .put("events", jsonEvents)

        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LATEST_SCRIPT, script.toString())
            .apply()
    }

    fun load(context: Context): ClickScript? {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_LATEST_SCRIPT, null)
            ?: return null

        return runCatching {
            val script = JSONObject(raw)
            if (script.getInt("version") != SCRIPT_VERSION) {
                return null
            }

            val jsonEvents = script.getJSONArray("events")
            val events = buildList {
                for (index in 0 until jsonEvents.length()) {
                    val event = jsonEvents.getJSONObject(index)
                    add(
                        ScriptTap(
                            atMs = event.getLong("at_ms").coerceAtLeast(0L),
                            x = event.getInt("x"),
                            y = event.getInt("y")
                        )
                    )
                }
            }
            if (events.isEmpty()) {
                null
            } else {
                ClickScript(
                    createdAt = script.optLong("created_at", 0L),
                    events = events
                )
            }
        }.getOrNull()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_LATEST_SCRIPT)
            .apply()
    }
}
