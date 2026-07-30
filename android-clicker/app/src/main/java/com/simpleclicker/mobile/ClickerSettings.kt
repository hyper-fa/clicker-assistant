package com.simpleclicker.mobile

import android.content.Context

object ClickerSettings {
    private const val PREFS = "clicker_settings"
    private const val KEY_INTERVAL = "interval_ms"
    private const val KEY_X = "target_x"
    private const val KEY_Y = "target_y"
    private const val KEY_SCRIPT_LOOP_ENABLED = "script_loop_enabled"
    private const val KEY_SCRIPT_LOOP_INTERVAL = "script_loop_interval_ms"

    fun intervalMs(context: Context): Long {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong(KEY_INTERVAL, 100L)
            .coerceAtLeast(1L)
    }

    fun setIntervalMs(context: Context, value: Long) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_INTERVAL, value.coerceAtLeast(1L))
            .apply()
    }

    fun target(context: Context): Pair<Int, Int> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return Pair(prefs.getInt(KEY_X, 300), prefs.getInt(KEY_Y, 600))
    }

    fun setTarget(context: Context, x: Int, y: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_X, x)
            .putInt(KEY_Y, y)
            .apply()
    }

    fun scriptLoopEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_SCRIPT_LOOP_ENABLED, false)
    }

    fun setScriptLoopEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_SCRIPT_LOOP_ENABLED, enabled)
            .apply()
    }

    fun scriptLoopIntervalMs(context: Context): Long {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong(KEY_SCRIPT_LOOP_INTERVAL, 1_000L)
            .coerceIn(MIN_SCRIPT_LOOP_INTERVAL_MS, MAX_SCRIPT_LOOP_INTERVAL_MS)
    }

    fun setScriptLoopIntervalMs(context: Context, value: Long) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong(
                KEY_SCRIPT_LOOP_INTERVAL,
                value.coerceIn(MIN_SCRIPT_LOOP_INTERVAL_MS, MAX_SCRIPT_LOOP_INTERVAL_MS)
            )
            .apply()
    }

    private const val MIN_SCRIPT_LOOP_INTERVAL_MS = 50L
    private const val MAX_SCRIPT_LOOP_INTERVAL_MS = 86_400_000L
}
