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
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import java.lang.ref.WeakReference
import kotlin.math.min
import kotlin.math.roundToInt

class FloatingControlService : Service() {
    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val NOTIFICATION_CHANNEL_ID = "floating_controls"

        private var instanceReference = WeakReference<FloatingControlService>(null)

        val instance: FloatingControlService?
            get() = instanceReference.get()
    }

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var windowManager: WindowManager
    private var rootView: FrameLayout? = null
    private var crosshairView: View? = null
    private var recorderSurface: View? = null
    private var clickModeButton: Button? = null
    private var scriptModeButton: Button? = null
    private var clickActionButton: Button? = null
    private var recordButton: Button? = null
    private var playButton: Button? = null
    private var clearButton: Button? = null
    private var statusView: TextView? = null
    private var crosshairParams: WindowManager.LayoutParams? = null
    private var crosshairWindowOffsetX: Int? = null
    private var crosshairWindowOffsetY: Int? = null
    private var recorderParams: WindowManager.LayoutParams? = null
    private var recorderSurfaceAttached = false
    private var panelParams: WindowManager.LayoutParams? = null
    private var scriptMode = false
    private var recording = false
    private var forwardingRecordedTap = false
    private var recordingStartedAt = 0L
    private val recordedEvents = mutableListOf<ScriptTap>()
    private var forwardGeneration = 0
    private var scriptPlaying = false
    private var playbackStartedAt = 0L
    private var playbackIndex = 0
    private var playbackEvents: List<ScriptTap> = emptyList()
    private var playbackLoopEnabled = false
    private var playbackLoopIntervalMs = 1_000L
    private var playbackWaitingForLoop = false

    override fun onCreate() {
        super.onCreate()
        instanceReference = WeakReference(this)
        startAsForegroundService()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        showCrosshair()
        showRecorderSurface()
        showPanel()
        updateStatus()
    }

    override fun onDestroy() {
        if (recording && recordedEvents.isNotEmpty()) {
            ClickScriptStore.save(this, recordedEvents)
        }
        recording = false
        stopScriptPlayback()
        ClickEngine.stop()
        handler.removeCallbacksAndMessages(null)
        rootView?.let { windowManager.removeView(it) }
        crosshairView?.let { windowManager.removeView(it) }
        if (recorderSurfaceAttached) {
            recorderSurface?.let { windowManager.removeView(it) }
        }
        rootView = null
        crosshairView = null
        recorderSurface = null
        if (instanceReference.get() === this) {
            instanceReference.clear()
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    fun clearSavedScript() {
        stopRecording(save = false)
        stopScriptPlayback()
        ClickScriptStore.clear(this)
        updateStatus()
    }

    private fun showCrosshair() {
        val size = dp(64)
        val storedTarget = ClickerSettings.target(this)
        val savedTarget = clampTarget(storedTarget.first, storedTarget.second, size / 2)
        if (savedTarget != storedTarget) {
            ClickerSettings.setTarget(this, savedTarget.first, savedTarget.second)
        }
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
        view.setOnTouchListener { touchView, event ->
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
                    val maxX = (resources.displayMetrics.widthPixels - view.width).coerceAtLeast(0)
                    val maxY = (resources.displayMetrics.heightPixels - view.height).coerceAtLeast(0)
                    params.x = (dragState.startX +
                        (event.rawX - dragState.startRawX).roundToInt()).coerceIn(0, maxX)
                    params.y = (dragState.startY +
                        (event.rawY - dragState.startRawY).roundToInt()).coerceIn(0, maxY)
                    windowManager.updateViewLayout(view, params)
                    saveTargetFromDrag(dragState, event)
                    updateStatus()
                    true
                }
                MotionEvent.ACTION_UP -> {
                    saveTargetFromDrag(dragState, event)
                    touchView.performClick()
                    view.post {
                        saveTargetFromView(view)
                        updateCrosshairWindowOffset(view, params)
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

    private fun showRecorderSurface() {
        val view = View(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            setOnTouchListener { touchView, event ->
                if (!recording || forwardingRecordedTap) {
                    return@setOnTouchListener false
                }
                val x = event.rawX.roundToInt()
                val y = event.rawY.roundToInt()
                when (event.action) {
                    MotionEvent.ACTION_DOWN,
                    MotionEvent.ACTION_MOVE -> moveCrosshairTo(x, y, persist = false)
                    MotionEvent.ACTION_UP -> {
                        moveCrosshairTo(x, y, persist = true)
                        touchView.performClick()
                        captureAndForwardTap(x, y)
                    }
                }
                true
            }
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
            alpha = 1f
        }

        recorderSurface = view
        recorderParams = params
    }

    private fun showPanel() {
        val root = DragFrameLayout(this).apply {
            background = rounded(0xA6111829.toInt(), dp(14), 0x8022D3EE.toInt(), dp(1))
            setPadding(dp(10), dp(8), dp(10), dp(8))
            elevation = dp(8).toFloat()
        }

        val panelWidth = (resources.displayMetrics.widthPixels - dp(16))
            .coerceAtMost(dp(340))
        val innerWidth = panelWidth - dp(20)

        val title = TextView(this).apply {
            text = "CLICK HUD"
            textSize = 10f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(0xFF22D3EE.toInt())
        }
        val status = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 12f
            typeface = Typeface.MONOSPACE
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
        }

        val clickMode = hudButton("连点").apply {
            setOnClickListener { switchMode(useScriptMode = false) }
        }
        val scriptModeControl = hudButton("脚本").apply {
            setOnClickListener { switchMode(useScriptMode = true) }
        }
        val clickAction = hudButton("开始连续点击").apply {
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
        val recordAction = hudButton("录制").apply {
            setOnClickListener {
                if (recording) {
                    stopRecording(save = true)
                } else {
                    startRecording()
                }
            }
        }
        val playAction = hudButton("播放").apply {
            setOnClickListener {
                if (scriptPlaying) {
                    stopScriptPlayback()
                    updateStatus()
                } else {
                    startScriptPlayback()
                }
            }
        }
        val clearAction = hudButton("清空").apply {
            setOnClickListener {
                clearSavedScript()
                Toast.makeText(
                    this@FloatingControlService,
                    "脚本已清空",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        root.addView(title, FrameLayout.LayoutParams(dp(92), dp(18)).apply {
            gravity = Gravity.TOP or Gravity.START
        })
        root.addView(status, FrameLayout.LayoutParams(innerWidth - dp(94), dp(20)).apply {
            leftMargin = dp(94)
            topMargin = 0
        })
        root.addView(clickMode, FrameLayout.LayoutParams(dp(76), dp(38)).apply {
            leftMargin = 0
            topMargin = dp(26)
        })
        root.addView(scriptModeControl, FrameLayout.LayoutParams(dp(76), dp(38)).apply {
            leftMargin = dp(82)
            topMargin = dp(26)
        })
        root.addView(clickAction, FrameLayout.LayoutParams(innerWidth, dp(42)).apply {
            leftMargin = 0
            topMargin = dp(70)
        })

        val actionGap = dp(6)
        val actionWidth = (innerWidth - actionGap * 2) / 3
        root.addView(recordAction, FrameLayout.LayoutParams(actionWidth, dp(42)).apply {
            leftMargin = 0
            topMargin = dp(70)
        })
        root.addView(playAction, FrameLayout.LayoutParams(actionWidth, dp(42)).apply {
            leftMargin = actionWidth + actionGap
            topMargin = dp(70)
        })
        root.addView(clearAction, FrameLayout.LayoutParams(actionWidth, dp(42)).apply {
            leftMargin = (actionWidth + actionGap) * 2
            topMargin = dp(70)
        })

        val params = WindowManager.LayoutParams(
            panelWidth,
            dp(128),
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(16)
            y = dp(100)
        }

        val dragState = DragState()
        root.setOnTouchListener { touchView, event ->
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
                MotionEvent.ACTION_UP -> {
                    touchView.performClick()
                    false
                }
                else -> false
            }
        }

        rootView = root
        statusView = status
        clickModeButton = clickMode
        scriptModeButton = scriptModeControl
        clickActionButton = clickAction
        recordButton = recordAction
        playButton = playAction
        clearButton = clearAction
        panelParams = params
        windowManager.addView(root, params)
    }

    private fun updateStatus() {
        val target = ClickerSettings.target(this)
        val clickRunning = ClickEngine.isRunning
        val storedScript = ClickScriptStore.load(this)

        clickModeButton?.let { styleHudButton(it, selected = !scriptMode) }
        scriptModeButton?.let { styleHudButton(it, selected = scriptMode) }
        clickActionButton?.visibility = if (scriptMode) View.GONE else View.VISIBLE
        recordButton?.visibility = if (scriptMode) View.VISIBLE else View.GONE
        playButton?.visibility = if (scriptMode) View.VISIBLE else View.GONE
        clearButton?.visibility = if (scriptMode) View.VISIBLE else View.GONE

        if (scriptMode) {
            statusView?.text = when {
                recording -> "录制中 · ${recordedEvents.size} 点"
                scriptPlaying && playbackWaitingForLoop ->
                    "循环等待 · ${formatDuration(playbackLoopIntervalMs)}"
                scriptPlaying && playbackLoopEnabled ->
                    "循环 ${playbackIndex.coerceAtMost(playbackEvents.size)}/${playbackEvents.size}"
                scriptPlaying ->
                    "播放 ${playbackIndex.coerceAtMost(playbackEvents.size)}/${playbackEvents.size}"
                storedScript == null -> "暂无脚本"
                ClickerSettings.scriptLoopEnabled(this) ->
                    "${storedScript.events.size} 点 · 循环"
                else ->
                    "${storedScript.events.size} 点 · ${formatDuration(storedScript.durationMs)}"
            }
        } else {
            statusView?.text = "坐标 ${target.first},${target.second}"
        }

        clickActionButton?.apply {
            text = if (clickRunning) "停止连续点击" else "开始连续点击"
            styleActionButton(this, running = clickRunning)
        }
        recordButton?.apply {
            text = if (recording) "停止录制" else "录制"
            isEnabled = !scriptPlaying
            alpha = if (isEnabled) 1f else 0.45f
            styleActionButton(this, running = recording)
        }
        playButton?.apply {
            text = if (scriptPlaying) "停止播放" else "播放"
            isEnabled = !recording && (storedScript != null || scriptPlaying)
            alpha = if (isEnabled) 1f else 0.45f
            styleActionButton(this, running = scriptPlaying)
        }
        clearButton?.apply {
            isEnabled = !recording && !scriptPlaying && storedScript != null
            alpha = if (isEnabled) 1f else 0.45f
            background = rounded(0xCC18263B.toInt(), dp(10), 0xC027415F.toInt(), dp(1))
        }

        val controlsLocked = clickRunning || recording || scriptPlaying
        clickModeButton?.isEnabled = !controlsLocked
        scriptModeButton?.isEnabled = !controlsLocked
        setCrosshairTouchable(!controlsLocked)
        crosshairView?.alpha = if (recording || !controlsLocked) 1f else 0.45f
        setRecorderCaptureEnabled(recording && !forwardingRecordedTap)
    }

    private fun switchMode(useScriptMode: Boolean) {
        if (scriptMode == useScriptMode) {
            return
        }
        ClickEngine.stop()
        scriptMode = useScriptMode
        updateStatus()
    }

    private fun startRecording() {
        if (ClickAccessibilityService.instance == null) {
            Toast.makeText(this, "请先开启无障碍服务", Toast.LENGTH_SHORT).show()
            return
        }

        ClickEngine.stop()
        stopScriptPlayback()
        scriptMode = true
        recordedEvents.clear()
        forwardingRecordedTap = false
        recordingStartedAt = SystemClock.elapsedRealtime()
        recording = true
        updateStatus()
        Toast.makeText(this, "脚本录制已开始", Toast.LENGTH_SHORT).show()
    }

    private fun stopRecording(save: Boolean) {
        if (!recording) {
            return
        }

        recording = false
        forwardGeneration += 1
        forwardingRecordedTap = false
        setRecorderCaptureEnabled(false)

        if (save && recordedEvents.isNotEmpty()) {
            ClickScriptStore.save(this, recordedEvents)
            Toast.makeText(
                this,
                "已保存 ${recordedEvents.size} 个点击",
                Toast.LENGTH_SHORT
            ).show()
        } else if (save) {
            Toast.makeText(this, "没有录到点击，原脚本已保留", Toast.LENGTH_SHORT).show()
        }

        recordedEvents.clear()
        updateStatus()
    }

    private fun captureAndForwardTap(x: Int, y: Int) {
        val accessibility = ClickAccessibilityService.instance
        if (accessibility == null) {
            Toast.makeText(this, "无障碍服务已断开", Toast.LENGTH_SHORT).show()
            stopRecording(save = true)
            return
        }

        val event = ScriptTap(
            atMs = SystemClock.elapsedRealtime() - recordingStartedAt,
            x = x,
            y = y
        )
        recordedEvents.add(event)
        forwardingRecordedTap = true
        val generation = ++forwardGeneration
        statusView?.text = "录制中 · ${recordedEvents.size} 点"
        handler.post {
            setRecorderCaptureEnabled(false)
            handler.postDelayed(
                { dispatchRecordedTap(generation, event, accessibility) },
                96L
            )
        }
    }

    private fun dispatchRecordedTap(
        generation: Int,
        event: ScriptTap,
        accessibility: ClickAccessibilityService
    ) {
        if (generation != forwardGeneration || !forwardingRecordedTap) {
            return
        }

        val accepted = accessibility.tap(event.x, event.y) { success ->
            handler.post {
                finishForwardedTap(generation, success)
            }
        }
        if (!accepted) {
            finishForwardedTap(generation, success = false)
            return
        }

        handler.postDelayed(
            { finishForwardedTap(generation, success = true) },
            500L
        )
    }

    private fun finishForwardedTap(generation: Int, success: Boolean) {
        if (generation != forwardGeneration || !forwardingRecordedTap) {
            return
        }
        if (!success) {
            Toast.makeText(this, "点击已记录，但未能转发到底层应用", Toast.LENGTH_SHORT).show()
        }
        forwardingRecordedTap = false
        updateStatus()
    }

    private fun startScriptPlayback() {
        val script = ClickScriptStore.load(this)
        if (script == null) {
            Toast.makeText(this, "请先录制脚本", Toast.LENGTH_SHORT).show()
            updateStatus()
            return
        }
        if (ClickAccessibilityService.instance == null) {
            Toast.makeText(this, "请先开启无障碍服务", Toast.LENGTH_SHORT).show()
            return
        }

        ClickEngine.stop()
        playbackEvents = script.events
        playbackIndex = 0
        playbackLoopEnabled = ClickerSettings.scriptLoopEnabled(this)
        playbackLoopIntervalMs = ClickerSettings.scriptLoopIntervalMs(this)
        playbackWaitingForLoop = false
        playbackStartedAt = SystemClock.uptimeMillis()
        scriptPlaying = true
        updateStatus()
        scheduleNextPlaybackEvent()
    }

    private fun scheduleNextPlaybackEvent() {
        if (!scriptPlaying || playbackIndex >= playbackEvents.size) {
            finishScriptPlayback()
            return
        }
        val event = playbackEvents[playbackIndex]
        handler.postAtTime(playbackRunnable, playbackStartedAt + event.atMs)
    }

    private val playbackRunnable = object : Runnable {
        override fun run() {
            if (!scriptPlaying || playbackIndex >= playbackEvents.size) {
                finishScriptPlayback()
                return
            }

            playbackWaitingForLoop = false
            val event = playbackEvents[playbackIndex]
            moveCrosshairTo(event.x, event.y, persist = false)
            val accepted = ClickAccessibilityService.instance?.tap(event.x, event.y) == true
            if (!accepted) {
                Toast.makeText(
                    this@FloatingControlService,
                    "脚本点击失败，请检查无障碍服务",
                    Toast.LENGTH_SHORT
                ).show()
                stopScriptPlayback()
                updateStatus()
                return
            }

            playbackIndex += 1
            updateStatus()
            if (playbackIndex < playbackEvents.size) {
                scheduleNextPlaybackEvent()
            } else if (playbackLoopEnabled) {
                playbackIndex = 0
                playbackWaitingForLoop = true
                playbackStartedAt = SystemClock.uptimeMillis() + playbackLoopIntervalMs
                updateStatus()
                scheduleNextPlaybackEvent()
            } else {
                handler.postDelayed({ finishScriptPlayback() }, 80L)
            }
        }
    }

    private fun finishScriptPlayback() {
        if (!scriptPlaying) {
            return
        }
        scriptPlaying = false
        playbackIndex = 0
        playbackEvents = emptyList()
        playbackLoopEnabled = false
        playbackWaitingForLoop = false
        updateStatus()
    }

    private fun stopScriptPlayback() {
        handler.removeCallbacks(playbackRunnable)
        scriptPlaying = false
        playbackIndex = 0
        playbackEvents = emptyList()
        playbackLoopEnabled = false
        playbackWaitingForLoop = false
    }

    private fun setRecorderCaptureEnabled(enabled: Boolean) {
        val view = recorderSurface ?: return
        val params = recorderParams ?: return
        if (enabled && !recorderSurfaceAttached) {
            params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
            view.visibility = View.VISIBLE
            windowManager.addView(view, params)
            recorderSurfaceAttached = true
            handler.post {
                if (recorderSurfaceAttached) {
                    bringCrosshairAndPanelToFront()
                }
            }
        } else if (!enabled && recorderSurfaceAttached) {
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            windowManager.removeViewImmediate(view)
            recorderSurfaceAttached = false
        }
    }

    private fun bringCrosshairAndPanelToFront() {
        val crosshair = crosshairView
        val crosshairLayout = crosshairParams
        if (crosshair != null && crosshairLayout != null && crosshair.isAttachedToWindow) {
            windowManager.removeViewImmediate(crosshair)
            windowManager.addView(crosshair, crosshairLayout)
            crosshair.post {
                updateCrosshairWindowOffset(crosshair, crosshairLayout)
            }
        }

        val panel = rootView
        val panelLayout = panelParams
        if (panel != null && panelLayout != null && panel.isAttachedToWindow) {
            windowManager.removeViewImmediate(panel)
            windowManager.addView(panel, panelLayout)
        }
    }

    private fun hudButton(textValue: String): Button {
        return Button(this).apply {
            text = textValue
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            isAllCaps = false
            minHeight = 0
            minWidth = 0
            stateListAnimator = null
            setTextColor(Color.WHITE)
            setPadding(dp(4), 0, dp(4), 0)
            background = rounded(0xCC18263B.toInt(), dp(10), 0xC027415F.toInt(), dp(1))
        }
    }

    private fun styleHudButton(button: Button, selected: Boolean) {
        button.background = rounded(
            if (selected) 0xCC0E7490.toInt() else 0xCC18263B.toInt(),
            dp(10),
            if (selected) 0xFF22D3EE.toInt() else 0xFF27415F.toInt(),
            dp(1)
        )
    }

    private fun styleActionButton(button: Button, running: Boolean) {
        button.background = rounded(
            if (running) 0xCCDC2626.toInt() else 0xCC2563EB.toInt(),
            dp(10),
            if (running) 0xFFFCA5A5.toInt() else 0xFF60A5FA.toInt(),
            dp(1)
        )
    }

    private fun formatDuration(durationMs: Long): String {
        if (durationMs < 1_000L) {
            return "${durationMs}ms"
        }
        val seconds = durationMs / 1_000L
        val tenths = (durationMs % 1_000L) / 100L
        return "$seconds.${tenths}秒"
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
            updateCrosshairWindowOffset(view, params)
            updateStatus()
        }
    }

    private fun saveTargetFromDrag(dragState: DragState, event: MotionEvent) {
        val startCenterX = dragState.startCenterX ?: return
        val startCenterY = dragState.startCenterY ?: return
        val deltaX = (event.rawX - dragState.startRawX).roundToInt()
        val deltaY = (event.rawY - dragState.startRawY).roundToInt()
        val target = clampTarget(startCenterX + deltaX, startCenterY + deltaY)
        ClickerSettings.setTarget(this, target.first, target.second)
    }

    private fun saveTargetFromView(view: View) {
        val center = screenCenterOf(view) ?: return
        val target = clampTarget(center.first, center.second)
        ClickerSettings.setTarget(this, target.first, target.second)
    }

    private fun moveCrosshairTo(x: Int, y: Int, persist: Boolean) {
        val view = crosshairView ?: return
        val params = crosshairParams ?: return
        val target = clampTarget(x, y)
        val halfWidth = if (view.width > 0) view.width / 2 else dp(32)
        val halfHeight = if (view.height > 0) view.height / 2 else dp(32)
        val center = screenCenterOf(view)
        val offsetX = crosshairWindowOffsetX
            ?: center?.let { it.first - (params.x + halfWidth) }
            ?: 0
        val offsetY = crosshairWindowOffsetY
            ?: center?.let { it.second - (params.y + halfHeight) }
            ?: 0
        crosshairWindowOffsetX = offsetX
        crosshairWindowOffsetY = offsetY
        params.x = target.first - halfWidth - offsetX
        params.y = target.second - halfHeight - offsetY
        windowManager.updateViewLayout(view, params)
        if (persist) {
            ClickerSettings.setTarget(this, target.first, target.second)
        }
    }

    private fun clampTarget(x: Int, y: Int, margin: Int = dp(32)): Pair<Int, Int> {
        val maxX = (resources.displayMetrics.widthPixels - margin).coerceAtLeast(margin)
        val maxY = (resources.displayMetrics.heightPixels - margin).coerceAtLeast(margin)
        return Pair(
            x.coerceIn(margin, maxX),
            y.coerceIn(margin, maxY)
        )
    }

    private fun updateCrosshairWindowOffset(
        view: View,
        params: WindowManager.LayoutParams
    ) {
        val center = screenCenterOf(view) ?: return
        crosshairWindowOffsetX = center.first - (params.x + view.width / 2)
        crosshairWindowOffsetY = center.second - (params.y + view.height / 2)
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
            view.alpha = 0.45f
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
            description = "连点助手悬浮控制正在运行"
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, flags)
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        return builder
            .setContentTitle(getString(R.string.app_name))
            .setContentText("悬浮控制正在运行")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun rounded(color: Int, radius: Int, strokeColor: Int, strokeWidth: Int): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = radius.toFloat()
            if (strokeWidth > 0) {
                setStroke(strokeWidth, strokeColor)
            }
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
        var startCenterX: Int? = null
        var startCenterY: Int? = null
    }

    private class CrosshairView(context: Context) : View(context) {
        private val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x3322D3EE
            style = Paint.Style.FILL
        }
        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xD9141B2D.toInt()
            style = Paint.Style.FILL
        }
        private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF22D3EE.toInt()
            style = Paint.Style.STROKE
            strokeWidth = dp(2.5f)
        }
        private val innerRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = dp(1.4f)
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
            val radius = min(width, height) / 2f - dp(5f)
            val gap = dp(6f)
            val armLength = radius - dp(7f)

            canvas.drawCircle(centerX, centerY, radius + dp(4f), haloPaint)
            canvas.drawCircle(centerX, centerY, radius, fillPaint)
            canvas.drawCircle(centerX, centerY, radius - ringPaint.strokeWidth / 2f, ringPaint)
            canvas.drawCircle(centerX, centerY, radius * 0.58f, innerRingPaint)
            canvas.drawLine(centerX - armLength, centerY, centerX - gap, centerY, crossPaint)
            canvas.drawLine(centerX + gap, centerY, centerX + armLength, centerY, crossPaint)
            canvas.drawLine(centerX, centerY - armLength, centerX, centerY - gap, crossPaint)
            canvas.drawLine(centerX, centerY + gap, centerX, centerY + armLength, crossPaint)
            canvas.drawCircle(centerX, centerY, dp(4f), centerPaint)
        }

        override fun performClick(): Boolean {
            super.performClick()
            return true
        }

        private fun dp(value: Float): Float {
            return value * resources.displayMetrics.density
        }
    }

    private class DragFrameLayout(context: Context) : FrameLayout(context) {
        override fun performClick(): Boolean {
            super.performClick()
            return true
        }
    }
}
