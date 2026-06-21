package com.simpleclicker.mobile

import android.os.Handler
import android.os.Looper
import java.util.concurrent.atomic.AtomicBoolean

object ClickEngine {
    private val handler = Handler(Looper.getMainLooper())
    private val running = AtomicBoolean(false)
    private var intervalMs: Long = 100L
    private var targetX: Int = 0
    private var targetY: Int = 0

    val isRunning: Boolean
        get() = running.get()

    fun configure(x: Int, y: Int, interval: Long) {
        targetX = x
        targetY = y
        intervalMs = interval.coerceAtLeast(1L)
    }

    fun start(): Boolean {
        if (ClickAccessibilityService.instance == null) {
            return false
        }
        if (!running.compareAndSet(false, true)) {
            return true
        }
        handler.post(clickRunnable)
        return true
    }

    fun stop() {
        running.set(false)
        handler.removeCallbacks(clickRunnable)
    }

    fun toggle(): Boolean {
        return if (isRunning) {
            stop()
            true
        } else {
            start()
        }
    }

    private val clickRunnable = object : Runnable {
        override fun run() {
            if (!running.get()) {
                return
            }

            ClickAccessibilityService.instance?.tap(targetX, targetY)
            if (running.get()) {
                handler.postDelayed(this, intervalMs)
            }
        }
    }
}
