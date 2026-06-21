package com.clicker.assistant

import android.content.Context

object ClickerSettings {
    private const val PREFS = "clicker_settings"
    private const val KEY_INTERVAL = "interval_ms"
    private const val KEY_X = "target_x"
    private const val KEY_Y = "target_y"

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
}
