package com.simpleclicker.mobile

import android.content.Context
import java.util.Calendar

enum class ScriptExecutionMode(val storedValue: String) {
    ONCE("once"),
    LOOP("loop"),
    SCHEDULED("scheduled");

    companion object {
        fun fromStoredValue(value: String?): ScriptExecutionMode? {
            return entries.firstOrNull { it.storedValue == value }
        }
    }
}

object ClickerSettings {
    private const val PREFS = "clicker_settings"
    private const val KEY_INTERVAL = "interval_ms"
    private const val KEY_X = "target_x"
    private const val KEY_Y = "target_y"
    private const val KEY_SCRIPT_EXECUTION_MODE = "script_execution_mode"
    private const val KEY_SCRIPT_LOOP_ENABLED = "script_loop_enabled"
    private const val KEY_SCRIPT_LOOP_INTERVAL = "script_loop_interval_ms"
    private const val KEY_SCRIPT_SCHEDULED_HOUR = "script_scheduled_hour"
    private const val KEY_SCRIPT_SCHEDULED_MINUTE = "script_scheduled_minute"
    private const val KEY_SCRIPT_LOCK_AFTER_SCHEDULED = "script_lock_after_scheduled_execution"

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

    fun scriptExecutionMode(context: Context): ScriptExecutionMode {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return ScriptExecutionMode.fromStoredValue(
            prefs.getString(KEY_SCRIPT_EXECUTION_MODE, null)
        ) ?: if (prefs.getBoolean(KEY_SCRIPT_LOOP_ENABLED, false)) {
            ScriptExecutionMode.LOOP
        } else {
            ScriptExecutionMode.ONCE
        }
    }

    fun setScriptExecutionMode(context: Context, mode: ScriptExecutionMode) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SCRIPT_EXECUTION_MODE, mode.storedValue)
            .putBoolean(KEY_SCRIPT_LOOP_ENABLED, mode == ScriptExecutionMode.LOOP)
            .apply()
    }

    fun scriptLoopEnabled(context: Context): Boolean {
        return scriptExecutionMode(context) == ScriptExecutionMode.LOOP
    }

    fun setScriptLoopEnabled(context: Context, enabled: Boolean) {
        setScriptExecutionMode(
            context,
            if (enabled) ScriptExecutionMode.LOOP else ScriptExecutionMode.ONCE
        )
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

    fun scriptLoopIntervalSeconds(context: Context): Long {
        val intervalMs = scriptLoopIntervalMs(context)
        return ((intervalMs + 999L) / 1_000L)
            .coerceIn(MIN_SCRIPT_LOOP_INTERVAL_SECONDS, MAX_SCRIPT_LOOP_INTERVAL_SECONDS)
    }

    fun setScriptLoopIntervalSeconds(context: Context, value: Long) {
        val seconds = value.coerceIn(
            MIN_SCRIPT_LOOP_INTERVAL_SECONDS,
            MAX_SCRIPT_LOOP_INTERVAL_SECONDS
        )
        setScriptLoopIntervalMs(context, seconds * 1_000L)
    }

    fun scriptScheduledTime(context: Context): Pair<Int, Int> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.contains(KEY_SCRIPT_SCHEDULED_HOUR) &&
            prefs.contains(KEY_SCRIPT_SCHEDULED_MINUTE)
        ) {
            return Pair(
                prefs.getInt(KEY_SCRIPT_SCHEDULED_HOUR, 0).coerceIn(0, 23),
                prefs.getInt(KEY_SCRIPT_SCHEDULED_MINUTE, 0).coerceIn(0, 59)
            )
        }

        val defaultTime = Calendar.getInstance().apply {
            add(Calendar.MINUTE, 1)
        }
        return Pair(
            defaultTime.get(Calendar.HOUR_OF_DAY),
            defaultTime.get(Calendar.MINUTE)
        )
    }

    fun setScriptScheduledTime(context: Context, hour: Int, minute: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_SCRIPT_SCHEDULED_HOUR, hour.coerceIn(0, 23))
            .putInt(KEY_SCRIPT_SCHEDULED_MINUTE, minute.coerceIn(0, 59))
            .apply()
    }

    fun lockAfterScheduledExecution(context: Context): Boolean {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_SCRIPT_LOCK_AFTER_SCHEDULED, false)
    }

    fun setLockAfterScheduledExecution(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_SCRIPT_LOCK_AFTER_SCHEDULED, enabled)
            .apply()
    }

    private const val MIN_SCRIPT_LOOP_INTERVAL_MS = 50L
    private const val MAX_SCRIPT_LOOP_INTERVAL_MS = 86_400_000L
    private const val MIN_SCRIPT_LOOP_INTERVAL_SECONDS = 1L
    private const val MAX_SCRIPT_LOOP_INTERVAL_SECONDS = 86_400L
}
