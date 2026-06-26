package com.simpleclicker.mobile

import android.content.Context
import android.content.Intent
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
import android.widget.TextView
import android.widget.Toast
import android.app.Activity
import android.content.ComponentName

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
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(28), dp(24), dp(24))
            gravity = Gravity.CENTER_HORIZONTAL
        }

        val title = TextView(this).apply {
            text = getString(R.string.app_name)
            textSize = 24f
            gravity = Gravity.CENTER
        }
        root.addView(title, fullWidth(dp(48)))

        accessibilityStatus = TextView(this).apply {
            textSize = 16f
        }
        root.addView(accessibilityStatus, fullWidth(dp(36)))

        root.addView(Button(this).apply {
            text = "开启无障碍权限"
            setOnClickListener {
                stopFloatingControls()
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }, fullWidth(dp(48)))

        overlayStatus = TextView(this).apply {
            textSize = 16f
        }
        root.addView(overlayStatus, fullWidth(dp(36)))

        root.addView(Button(this).apply {
            text = "开启悬浮窗权限"
            setOnClickListener {
                stopFloatingControls()
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
            }
        }, fullWidth(dp(48)))

        root.addView(TextView(this).apply {
            text = "点击间隔（毫秒）"
            textSize = 16f
        }, fullWidth(dp(34)))

        intervalInput = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(ClickerSettings.intervalMs(this@MainActivity).toString())
            hint = "100"
        }
        root.addView(intervalInput, fullWidth(dp(56)))

        root.addView(Button(this).apply {
            text = "启动悬浮窗"
            setOnClickListener {
                saveInterval()
                if (!Settings.canDrawOverlays(this@MainActivity)) {
                    Toast.makeText(this@MainActivity, "请先开启悬浮窗权限", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                startFloatingControls()
                Toast.makeText(this@MainActivity, "已启动悬浮窗", Toast.LENGTH_SHORT).show()
            }
        }, fullWidth(dp(52)))

        root.addView(Button(this).apply {
            text = "停止悬浮窗"
            setOnClickListener {
                stopService(Intent(this@MainActivity, FloatingControlService::class.java))
                ClickEngine.stop()
            }
        }, fullWidth(dp(52)))

        root.addView(TextView(this).apply {
            text = "使用方法：拖动准星到目标位置，在悬浮窗上点开始/停止。"
            textSize = 14f
        }, fullWidth(dp(70)))

        setContentView(root)
        refreshStatus()
    }

    private fun refreshStatus() {
        accessibilityStatus.text = if (isAccessibilityEnabled()) {
            "无障碍权限：已开启"
        } else {
            "无障碍权限：未开启"
        }
        overlayStatus.text = if (Settings.canDrawOverlays(this)) {
            "悬浮窗权限：已开启"
        } else {
            "悬浮窗权限：未开启"
        }
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

    private fun fullWidth(height: Int): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height).apply {
            bottomMargin = dp(10)
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }
}
