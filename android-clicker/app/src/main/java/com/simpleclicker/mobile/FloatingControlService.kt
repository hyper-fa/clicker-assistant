package com.simpleclicker.mobile

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
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
import kotlin.math.min
import kotlin.math.roundToInt

class FloatingControlService : Service() {
    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val NOTIFICATION_CHANNEL_ID = "floating_controls"
    }

    private lateinit var windowManager: WindowManager
    private var rootView: FrameLayout? = null
    private var crosshairView: View? = null
    private var actionButton: Button? = null
    private var statusView: TextView? = null
    private var crosshairParams: WindowManager.LayoutParams? = null
    private var panelParams: WindowManager.LayoutParams? = null

    override fun onCreate() {
        super.onCreate()
        startAsForegroundService()
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
        val view = CrosshairView(this)

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
                    screenCenterOf(view)?.let { center ->
                        dragState.startCenterX = center.first
                        dragState.startCenterY = center.second
                    }
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = dragState.startX + (event.rawX - dragState.startRawX).roundToInt()
                    params.y = dragState.startY + (event.rawY - dragState.startRawY).roundToInt()
                    windowManager.updateViewLayout(view, params)
                    saveTargetFromDrag(dragState, event)
                    updateStatus()
                    true
                }
                MotionEvent.ACTION_UP -> {
                    saveTargetFromDrag(dragState, event)
                    view.post {
                        saveTargetFromView(view)
                        updateStatus()
                    }
                    true
                }
                else -> false
            }
        }

        crosshairView = view
        crosshairParams = params
        windowManager.addView(view, params)
        view.post {
            alignCrosshairToSavedTarget(view, params)
        }
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

    private fun alignCrosshairToSavedTarget(view: View, params: WindowManager.LayoutParams) {
        val target = ClickerSettings.target(this)
        val center = screenCenterOf(view) ?: return
        val deltaX = target.first - center.first
        val deltaY = target.second - center.second

        if (deltaX != 0 || deltaY != 0) {
            params.x += deltaX
            params.y += deltaY
            windowManager.updateViewLayout(view, params)
        }

        view.post {
            saveTargetFromView(view)
            updateStatus()
        }
    }

    private fun saveTargetFromDrag(dragState: DragState, event: MotionEvent) {
        val startCenterX = dragState.startCenterX ?: return
        val startCenterY = dragState.startCenterY ?: return
        val deltaX = (event.rawX - dragState.startRawX).roundToInt()
        val deltaY = (event.rawY - dragState.startRawY).roundToInt()
        ClickerSettings.setTarget(this, startCenterX + deltaX, startCenterY + deltaY)
    }

    private fun saveTargetFromView(view: View) {
        val center = screenCenterOf(view) ?: return
        ClickerSettings.setTarget(this, center.first, center.second)
    }

    private fun screenCenterOf(view: View): Pair<Int, Int>? {
        if (!view.isAttachedToWindow || view.width == 0 || view.height == 0) {
            return null
        }
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        return Pair(
            location[0] + (view.width / 2f).roundToInt(),
            location[1] + (view.height / 2f).roundToInt()
        )
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

    private fun startAsForegroundService() {
        createNotificationChannel()
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "悬浮控制",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "奔奔助手悬浮控制运行中"
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, flags)
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        return builder
            .setContentTitle(getString(R.string.app_name))
            .setContentText("悬浮控制运行中")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).roundToInt()
    }

    private class DragState {
        var startRawX = 0f
        var startRawY = 0f
        var startX = 0
        var startY = 0
        var startCenterX: Int? = null
        var startCenterY: Int? = null
    }

    private class CrosshairView(context: Context) : View(context) {
        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xCC2563EB.toInt()
            style = Paint.Style.FILL
        }
        private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = dp(2f)
        }
        private val crossPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeWidth = dp(3f)
        }
        private val centerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFD166.toInt()
            style = Paint.Style.FILL
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val centerX = width / 2f
            val centerY = height / 2f
            val radius = min(width, height) / 2f - dp(2f)
            val gap = dp(5f)
            val armLength = radius - dp(8f)

            canvas.drawCircle(centerX, centerY, radius, fillPaint)
            canvas.drawCircle(centerX, centerY, radius - ringPaint.strokeWidth / 2f, ringPaint)
            canvas.drawLine(centerX - armLength, centerY, centerX - gap, centerY, crossPaint)
            canvas.drawLine(centerX + gap, centerY, centerX + armLength, centerY, crossPaint)
            canvas.drawLine(centerX, centerY - armLength, centerX, centerY - gap, crossPaint)
            canvas.drawLine(centerX, centerY + gap, centerX, centerY + armLength, crossPaint)
            canvas.drawCircle(centerX, centerY, dp(3f), centerPaint)
        }

        private fun dp(value: Float): Float {
            return value * resources.displayMetrics.density
        }
    }
}
