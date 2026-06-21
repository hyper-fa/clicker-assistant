package com.clicker.assistant

import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import kotlin.math.roundToInt

class FloatingControlService : Service() {
    private lateinit var windowManager: WindowManager
    private var rootView: FrameLayout? = null
    private var crosshairView: TextView? = null
    private var actionButton: Button? = null
    private var statusView: TextView? = null
    private var crosshairParams: WindowManager.LayoutParams? = null
    private var panelParams: WindowManager.LayoutParams? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        showCrosshair()
        showPanel()
        updateStatus()
    }

    override fun onDestroy() {
        ClickEngine.stop()
        rootView?.let { windowManager.removeView(it) }
        crosshairView?.let { windowManager.removeView(it) }
        rootView = null
        crosshairView = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun showCrosshair() {
        val savedTarget = ClickerSettings.target(this)
        val size = dp(56)
        val view = TextView(this).apply {
            text = "+"
            textSize = 34f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setBackgroundColor(0xCC2563EB.toInt())
        }

        val params = WindowManager.LayoutParams(
            size,
            size,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = savedTarget.first - size / 2
            y = savedTarget.second - size / 2
        }

        val dragState = DragState()
        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    dragState.startRawX = event.rawX
                    dragState.startRawY = event.rawY
                    dragState.startX = params.x
                    dragState.startY = params.y
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = dragState.startX + (event.rawX - dragState.startRawX).roundToInt()
                    params.y = dragState.startY + (event.rawY - dragState.startRawY).roundToInt()
                    windowManager.updateViewLayout(view, params)
                    saveTargetFromParams(params, size)
                    updateStatus()
                    true
                }
                MotionEvent.ACTION_UP -> {
                    saveTargetFromParams(params, size)
                    updateStatus()
                    true
                }
                else -> false
            }
        }

        crosshairView = view
        crosshairParams = params
        windowManager.addView(view, params)
        saveTargetFromParams(params, size)
    }

    private fun showPanel() {
        val root = FrameLayout(this).apply {
            setBackgroundColor(0xDD111827.toInt())
            setPadding(dp(8), dp(6), dp(8), dp(6))
        }
        val status = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 12f
        }
        val button = Button(this).apply {
            text = "开始"
            textSize = 14f
            setOnClickListener {
                val target = ClickerSettings.target(this@FloatingControlService)
                val interval = ClickerSettings.intervalMs(this@FloatingControlService)
                ClickEngine.configure(target.first, target.second, interval)
                val ok = ClickEngine.toggle()
                if (!ok) {
                    Toast.makeText(
                        this@FloatingControlService,
                        "请先开启无障碍服务",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                updateStatus()
            }
        }

        root.addView(status, FrameLayout.LayoutParams(dp(160), dp(28)).apply {
            gravity = Gravity.TOP or Gravity.START
        })
        root.addView(button, FrameLayout.LayoutParams(dp(86), dp(44)).apply {
            gravity = Gravity.BOTTOM or Gravity.END
        })

        val params = WindowManager.LayoutParams(
            dp(260),
            dp(62),
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(16)
            y = dp(100)
        }

        val dragState = DragState()
        root.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    dragState.startRawX = event.rawX
                    dragState.startRawY = event.rawY
                    dragState.startX = params.x
                    dragState.startY = params.y
                    false
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = dragState.startX + (event.rawX - dragState.startRawX).roundToInt()
                    params.y = dragState.startY + (event.rawY - dragState.startRawY).roundToInt()
                    windowManager.updateViewLayout(root, params)
                    true
                }
                else -> false
            }
        }

        rootView = root
        statusView = status
        actionButton = button
        panelParams = params
        windowManager.addView(root, params)
    }

    private fun updateStatus() {
        val target = ClickerSettings.target(this)
        statusView?.text = "坐标 ${target.first},${target.second}"
        actionButton?.text = if (ClickEngine.isRunning) "停止" else "开始"
        setCrosshairTouchable(!ClickEngine.isRunning)
    }

    private fun saveTargetFromParams(params: WindowManager.LayoutParams, size: Int) {
        ClickerSettings.setTarget(this, params.x + size / 2, params.y + size / 2)
    }

    private fun setCrosshairTouchable(touchable: Boolean) {
        val view = crosshairView ?: return
        val params = crosshairParams ?: return
        val shouldNotTouch = params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE != 0
        if (touchable && shouldNotTouch) {
            params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
            view.alpha = 1.0f
            windowManager.updateViewLayout(view, params)
        } else if (!touchable && !shouldNotTouch) {
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            view.alpha = 0.35f
            windowManager.updateViewLayout(view, params)
        }
    }

    private fun overlayType(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).roundToInt()
    }

    private class DragState {
        var startRawX = 0f
        var startRawY = 0f
        var startX = 0
        var startY = 0
    }
}
