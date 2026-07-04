package com.simpleclicker.mobile

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.text.TextUtils
import android.view.Gravity
import android.view.ViewGroup
import android.view.accessibility.AccessibilityManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var accessibilityStatus: TextView
    private lateinit var overlayStatus: TextView
    private lateinit var intervalInput: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun buildUi() {
        window.statusBarColor = COLOR_BACKGROUND
        window.navigationBarColor = COLOR_BACKGROUND
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(24))
            setBackgroundColor(COLOR_BACKGROUND)
        }

        content.addView(TextView(this).apply {
            text = "连点助手"
            textSize = 28f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(COLOR_TEXT)
        }, fullWidthWrap())

        content.addView(TextView(this).apply {
            text = "MOBILE COMMAND HUD"
            textSize = 13f
            letterSpacing = 0.08f
            setTextColor(COLOR_CYAN)
        }, fullWidthWrap(dp(4)))

        content.addView(statusCard(
            "无障碍点击",
            "允许应用执行屏幕点击动作",
            onClickText = "打开无障碍设置",
            onClick = {
                stopFloatingControls()
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            },
            bindStatus = { accessibilityStatus = it }
        ), fullWidthWrap(dp(18)))

        content.addView(statusCard(
            "悬浮控制窗",
            "显示可拖拽准星和开始/停止按钮",
            onClickText = "打开悬浮窗权限",
            onClick = {
                stopFloatingControls()
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
            },
            bindStatus = { overlayStatus = it }
        ), fullWidthWrap(dp(14)))

        content.addView(card().apply {
            orientation = LinearLayout.VERTICAL
            addView(sectionTitle("点击间隔"), fullWidthWrap())
            addView(TextView(this@MainActivity).apply {
                text = "单位：毫秒，最低 1 毫秒"
                textSize = 13f
                setTextColor(COLOR_MUTED)
            }, fullWidthWrap(dp(10)))
            intervalInput = EditText(this@MainActivity).apply {
                inputType = InputType.TYPE_CLASS_NUMBER
                setText(ClickerSettings.intervalMs(this@MainActivity).toString())
                hint = "100"
                textSize = 22f
                setSingleLine(true)
                setTextColor(COLOR_TEXT)
                setHintTextColor(COLOR_MUTED)
                background = rounded(COLOR_FIELD, dp(14), COLOR_STROKE, dp(1))
                setPadding(dp(16), 0, dp(16), 0)
            }
            addView(intervalInput, fullWidth(dp(58), dp(6)))
        }, fullWidthWrap(dp(14)))

        content.addView(card().apply {
            orientation = LinearLayout.VERTICAL
            addView(sectionTitle("悬浮作战台"), fullWidthWrap())
            addView(TextView(this@MainActivity).apply {
                text = "启动后拖动准星到目标位置，再在悬浮窗中开始或停止。"
                textSize = 14f
                setTextColor(COLOR_MUTED)
            }, fullWidthWrap(dp(14)))
            addView(primaryButton("启动悬浮窗").apply {
                setOnClickListener {
                    saveInterval()
                    if (!Settings.canDrawOverlays(this@MainActivity)) {
                        Toast.makeText(this@MainActivity, "请先开启悬浮窗权限", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    startFloatingControls()
                    Toast.makeText(this@MainActivity, "悬浮窗已启动", Toast.LENGTH_SHORT).show()
                }
            }, fullWidth(dp(54), dp(10)))
            addView(secondaryButton("停止悬浮窗").apply {
                setOnClickListener {
                    stopService(Intent(this@MainActivity, FloatingControlService::class.java))
                    ClickEngine.stop()
                    Toast.makeText(this@MainActivity, "悬浮窗已停止", Toast.LENGTH_SHORT).show()
                }
            }, fullWidth(dp(50), 0))
        }, fullWidthWrap())

        val scrollView = ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(COLOR_BACKGROUND)
            addView(content, ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ))
        }

        setContentView(scrollView)
        refreshStatus()
    }

    private fun statusCard(
        title: String,
        subtitle: String,
        onClickText: String,
        onClick: () -> Unit,
        bindStatus: (TextView) -> Unit
    ): LinearLayout {
        return card().apply {
            orientation = LinearLayout.VERTICAL
            val header = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            header.addView(LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                addView(sectionTitle(title), fullWidthWrap())
                addView(TextView(this@MainActivity).apply {
                    text = subtitle
                    textSize = 13f
                    setTextColor(COLOR_MUTED)
                }, fullWidthWrap(dp(2)))
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            val status = TextView(this@MainActivity).apply {
                textSize = 12f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setPadding(dp(12), dp(6), dp(12), dp(6))
            }
            bindStatus(status)
            header.addView(status, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ))
            addView(header, fullWidthWrap(dp(14)))
            addView(secondaryButton(onClickText).apply {
                setOnClickListener { onClick() }
            }, fullWidth(dp(48), 0))
        }
    }

    private fun refreshStatus() {
        val accessibilityReady = isAccessibilityEnabled()
        styleStatus(accessibilityStatus, accessibilityReady)
        accessibilityStatus.text = if (accessibilityReady) "已开启" else "未开启"

        val overlayReady = Settings.canDrawOverlays(this)
        styleStatus(overlayStatus, overlayReady)
        overlayStatus.text = if (overlayReady) "已开启" else "未开启"
    }

    private fun saveInterval() {
        val interval = intervalInput.text.toString().trim().toLongOrNull() ?: 100L
        ClickerSettings.setIntervalMs(this, interval.coerceAtLeast(1L))
    }

    private fun isAccessibilityEnabled(): Boolean {
        val accessibilityEnabled = Settings.Secure.getInt(
            contentResolver,
            Settings.Secure.ACCESSIBILITY_ENABLED,
            0
        ) == 1
        if (!accessibilityEnabled) {
            return false
        }

        val expectedService = ComponentName(this, ClickAccessibilityService::class.java)
        val manager = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val managerServices = manager.getEnabledAccessibilityServiceList(
            android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK
        )
        if (managerServices.any {
                it.resolveInfo.serviceInfo.packageName == packageName &&
                    it.resolveInfo.serviceInfo.name == ClickAccessibilityService::class.java.name
            }) {
            return true
        }

        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabledServices)
        for (enabledService in splitter) {
            val enabledComponent = ComponentName.unflattenFromString(enabledService)
            if (expectedService == enabledComponent) {
                return true
            }
        }
        return false
    }

    private fun stopFloatingControls() {
        ClickEngine.stop()
        stopService(Intent(this, FloatingControlService::class.java))
    }

    private fun startFloatingControls() {
        val intent = Intent(this, FloatingControlService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun card(): LinearLayout {
        return LinearLayout(this).apply {
            setPadding(dp(18), dp(16), dp(18), dp(16))
            background = rounded(COLOR_CARD, dp(20), COLOR_STROKE, dp(1))
        }
    }

    private fun sectionTitle(textValue: String): TextView {
        return TextView(this).apply {
            text = textValue
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(COLOR_TEXT)
        }
    }

    private fun primaryButton(textValue: String): Button {
        return styledButton(textValue, COLOR_BLUE, Color.WHITE)
    }

    private fun secondaryButton(textValue: String): Button {
        return styledButton(textValue, COLOR_PANEL, COLOR_TEXT)
    }

    private fun styledButton(textValue: String, backgroundColor: Int, textColor: Int): Button {
        return Button(this).apply {
            text = textValue
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            isAllCaps = false
            setTextColor(textColor)
            background = rounded(backgroundColor, dp(14), COLOR_STROKE, dp(1))
            minHeight = 0
            minWidth = 0
            stateListAnimator = null
        }
    }

    private fun styleStatus(view: TextView, ready: Boolean) {
        view.setTextColor(if (ready) COLOR_READY_TEXT else COLOR_WARN_TEXT)
        view.background = rounded(
            if (ready) COLOR_READY_BG else COLOR_WARN_BG,
            dp(999),
            Color.TRANSPARENT,
            0
        )
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

    private fun fullWidth(height: Int, bottomMargin: Int): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height).apply {
            this.bottomMargin = bottomMargin
        }
    }

    private fun fullWidthWrap(bottomMargin: Int = 0): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            this.bottomMargin = bottomMargin
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    companion object {
        private const val COLOR_BACKGROUND = 0xFF08111F.toInt()
        private const val COLOR_CARD = 0xFF101B2D.toInt()
        private const val COLOR_PANEL = 0xFF18263B.toInt()
        private const val COLOR_FIELD = 0xFF0B1424.toInt()
        private const val COLOR_STROKE = 0xFF27415F.toInt()
        private const val COLOR_TEXT = 0xFFEAF4FF.toInt()
        private const val COLOR_MUTED = 0xFF91A7C2.toInt()
        private const val COLOR_CYAN = 0xFF22D3EE.toInt()
        private const val COLOR_BLUE = 0xFF2563EB.toInt()
        private const val COLOR_READY_BG = 0xFF123A2D.toInt()
        private const val COLOR_READY_TEXT = 0xFF7CFFB2.toInt()
        private const val COLOR_WARN_BG = 0xFF3D2C12.toInt()
        private const val COLOR_WARN_TEXT = 0xFFFFD166.toInt()
    }
}
