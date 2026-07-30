package com.simpleclicker.mobile

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent

class ClickAccessibilityService : AccessibilityService() {
    companion object {
        @Volatile
        var instance: ClickAccessibilityService? = null
            private set

        val isEnabled: Boolean
            get() = instance != null
    }

    fun tap(
        x: Int,
        y: Int,
        durationMs: Long = 50L,
        onFinished: ((Boolean) -> Unit)? = null
    ): Boolean {
        val path = Path().apply {
            moveTo(x.toFloat(), y.toFloat())
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs.coerceAtLeast(1L))
        val gesture = GestureDescription.Builder()
            .addStroke(stroke)
            .build()
        val callback = if (onFinished == null) {
            null
        } else {
            object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    onFinished(true)
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    onFinished(false)
                }
            }
        }
        val accepted = dispatchGesture(
            gesture,
            callback,
            if (callback == null) null else Handler(Looper.getMainLooper())
        )
        if (!accepted) {
            onFinished?.invoke(false)
        }
        return accepted
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onDestroy() {
        if (instance === this) {
            instance = null
        }
        super.onDestroy()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        if (instance === this) {
            instance = null
        }
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit
}
