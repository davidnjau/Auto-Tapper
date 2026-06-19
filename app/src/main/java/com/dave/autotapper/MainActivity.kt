package com.dave.autotapper

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var dotAccessibility: TextView
    private lateinit var tvStatusAccessibility: TextView
    private lateinit var dotOverlay: TextView
    private lateinit var tvStatusOverlay: TextView

    private lateinit var tvTapSpeed: TextView
    private lateinit var seekBarSpeed: SeekBar
    private lateinit var rgMultiplier: RadioGroup
    private lateinit var btnEnableService: Button
    private lateinit var btnOpenSettings: Button

    private lateinit var tvSpecRefreshRate: TextView
    private lateinit var tvSpecRam: TextView
    private lateinit var tvSpecCores: TextView
    private lateinit var tvRecSpeed: TextView
    private lateinit var tvRecMultiplier: TextView
    private lateinit var btnApplyRecommended: Button

    private lateinit var cardWarning: LinearLayout
    private lateinit var tvWarningIcon: TextView
    private lateinit var tvWarningTitle: TextView
    private lateinit var tvWarningMessage: TextView

    private lateinit var btnViewHistory: TextView

    private enum class WarnLevel { NONE, CAUTION, DANGER }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        setupListeners()
        setupRecommendation()
        updateServiceStatus()
        requestNotificationPermission()
    }

    override fun onResume() {
        super.onResume()
        updateServiceStatus()
    }

    private fun initViews() {
        dotAccessibility = findViewById(R.id.dotAccessibility)
        tvStatusAccessibility = findViewById(R.id.tvStatusAccessibility)
        dotOverlay = findViewById(R.id.dotOverlay)
        tvStatusOverlay = findViewById(R.id.tvStatusOverlay)

        tvTapSpeed = findViewById(R.id.tvTapSpeed)
        seekBarSpeed = findViewById(R.id.seekBarSpeed)
        rgMultiplier = findViewById(R.id.rgMultiplier)
        btnEnableService = findViewById(R.id.btnEnableService)
        btnOpenSettings = findViewById(R.id.btnOpenSettings)

        tvSpecRefreshRate = findViewById(R.id.tvSpecRefreshRate)
        tvSpecRam = findViewById(R.id.tvSpecRam)
        tvSpecCores = findViewById(R.id.tvSpecCores)
        tvRecSpeed = findViewById(R.id.tvRecSpeed)
        tvRecMultiplier = findViewById(R.id.tvRecMultiplier)
        btnApplyRecommended = findViewById(R.id.btnApplyRecommended)

        cardWarning = findViewById(R.id.cardWarning)
        tvWarningIcon = findViewById(R.id.tvWarningIcon)
        tvWarningTitle = findViewById(R.id.tvWarningTitle)
        tvWarningMessage = findViewById(R.id.tvWarningMessage)

        btnViewHistory = findViewById(R.id.btnViewHistory)

        val prefs = getSharedPreferences("AutoTapperPrefs", Context.MODE_PRIVATE)

        val savedSpeed = prefs.getInt("tap_speed", 5)
        seekBarSpeed.progress = savedSpeed - 1
        tvTapSpeed.text = "$savedSpeed"

        val savedMultiplier = prefs.getInt("tap_multiplier", 1)
        rgMultiplier.check(when (savedMultiplier) {
            2 -> R.id.rb2x
            3 -> R.id.rb3x
            else -> R.id.rb1x
        })

        checkConfigurationWarning()
    }

    private fun setupListeners() {
        seekBarSpeed.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val speed = progress + 1
                tvTapSpeed.text = "$speed"

                val prefs = getSharedPreferences("AutoTapperPrefs", Context.MODE_PRIVATE)
                prefs.edit().putInt("tap_speed", speed).apply()

                AutoTapperService.instance?.updateTapSpeed(speed)
                checkConfigurationWarning()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        rgMultiplier.setOnCheckedChangeListener { _, checkedId ->
            val multiplier = when (checkedId) {
                R.id.rb2x -> 2
                R.id.rb3x -> 3
                else -> 1
            }
            val prefs = getSharedPreferences("AutoTapperPrefs", Context.MODE_PRIVATE)
            prefs.edit().putInt("tap_multiplier", multiplier).apply()
            AutoTapperService.instance?.updateTapMultiplier(multiplier)
            checkConfigurationWarning()
        }

        btnEnableService.setOnClickListener {
            if (!isAccessibilityServiceEnabled()) {
                openAccessibilitySettings()
            } else {
                Toast.makeText(this, "Service already enabled", Toast.LENGTH_SHORT).show()
            }
        }

        btnViewHistory.setOnClickListener {
            SessionHistoryActivity.start(this)
        }

        btnOpenSettings.setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
            } else {
                Toast.makeText(this, "Overlay permission already granted", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun checkConfigurationWarning() {
        val speed = seekBarSpeed.progress + 1
        val multiplier = when (rgMultiplier.checkedRadioButtonId) {
            R.id.rb2x -> 2
            R.id.rb3x -> 3
            else -> 1
        }

        val configuredDelayMs = 1000L / speed
        val capDelayMs = multiplier * 60L
        val capEngages = capDelayMs > configuredDelayMs

        val (level, message) = when {
            speed >= 8 && multiplier >= 2 ->
                WarnLevel.DANGER to
                    "Excessive load — ${speed} taps/sec + ${multiplier}× will cause high gesture cancellations. " +
                    "Max safe: 7 taps/sec at 2×, or 5 taps/sec at 3×."

            speed == 9 && multiplier == 1 ->
                WarnLevel.CAUTION to
                    "Max speed — cancellation rate will increase. Recommended: 7 taps/sec."

            capEngages ->
                WarnLevel.CAUTION to
                    "Speed cap active — ${multiplier}× dispatch limit is ${1000 / capDelayMs} dispatches/sec. " +
                    "Configured speed ($speed) exceeds this; actual rate is capped. " +
                    "Reduce to ${1000 / capDelayMs} taps/sec to match ${multiplier}×."

            else -> WarnLevel.NONE to ""
        }

        when (level) {
            WarnLevel.NONE -> {
                cardWarning.visibility = View.GONE
            }
            WarnLevel.CAUTION -> {
                val color = ContextCompat.getColor(this, R.color.warning)
                cardWarning.setBackgroundResource(R.drawable.bg_card_warning_hud)
                cardWarning.visibility = View.VISIBLE
                tvWarningIcon.setTextColor(color)
                tvWarningTitle.text = "CONFIGURATION WARNING"
                tvWarningTitle.setTextColor(color)
                tvWarningMessage.text = message
            }
            WarnLevel.DANGER -> {
                val color = ContextCompat.getColor(this, R.color.danger)
                cardWarning.setBackgroundResource(R.drawable.bg_card_danger_hud)
                cardWarning.visibility = View.VISIBLE
                tvWarningIcon.setTextColor(color)
                tvWarningTitle.text = "EXCESSIVE CONFIGURATION"
                tvWarningTitle.setTextColor(color)
                tvWarningMessage.text = message
            }
        }
    }

    private fun setupRecommendation() {
        val refreshRate = getRefreshRate()
        val ramGb = getRamGb()
        val cores = Runtime.getRuntime().availableProcessors()
        val (recSpeed, recMultiplier) = computeRecommendation(refreshRate, ramGb)

        tvSpecRefreshRate.text = "${refreshRate.toInt()}Hz"
        tvSpecRam.text = "${ramGb}GB RAM"
        tvSpecCores.text = "$cores cores"
        tvRecSpeed.text = "$recSpeed taps/sec"
        tvRecMultiplier.text = "${recMultiplier}×"

        btnApplyRecommended.setOnClickListener {
            seekBarSpeed.progress = recSpeed - 1
            rgMultiplier.check(when (recMultiplier) {
                2 -> R.id.rb2x
                3 -> R.id.rb3x
                else -> R.id.rb1x
            })

            val prefs = getSharedPreferences("AutoTapperPrefs", Context.MODE_PRIVATE)
            prefs.edit()
                .putInt("tap_speed", recSpeed)
                .putInt("tap_multiplier", recMultiplier)
                .apply()

            AutoTapperService.instance?.updateTapSpeed(recSpeed)
            AutoTapperService.instance?.updateTapMultiplier(recMultiplier)

            Toast.makeText(this, "Configuration applied", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getRefreshRate(): Float {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            display?.refreshRate ?: 60f
        } else {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.refreshRate
        }
    }

    private fun getRamGb(): Int {
        val activityManager = getSystemService(ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        return (memInfo.totalMem / (1024.0 * 1024.0 * 1024.0)).toInt()
    }

    private fun computeRecommendation(refreshRate: Float, ramGb: Int): Pair<Int, Int> {
        val speed = when {
            refreshRate >= 120f -> 8
            refreshRate >= 90f  -> 7
            else                -> 6
        }
        val multiplier = if (ramGb >= 8) 2 else 1
        return Pair(speed, multiplier)
    }

    private fun updateServiceStatus() {
        val isEnabled = isAccessibilityServiceEnabled()
        val hasOverlay = Settings.canDrawOverlays(this)

        val activeColor = ContextCompat.getColor(this, R.color.success)
        val inactiveColor = ContextCompat.getColor(this, R.color.danger)

        dotAccessibility.setTextColor(if (isEnabled) activeColor else inactiveColor)
        tvStatusAccessibility.text = if (isEnabled) "ENABLED" else "DISABLED"
        tvStatusAccessibility.setTextColor(if (isEnabled) activeColor else inactiveColor)

        dotOverlay.setTextColor(if (hasOverlay) activeColor else inactiveColor)
        tvStatusOverlay.text = if (hasOverlay) "GRANTED" else "DENIED"
        tvStatusOverlay.setTextColor(if (hasOverlay) activeColor else inactiveColor)

        btnEnableService.isEnabled = !isEnabled
        btnOpenSettings.isEnabled = !hasOverlay
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val service = "${packageName}/${AutoTapperService::class.java.name}"
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )
        return enabledServices?.contains(service) == true
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(
                    arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                    0
                )
            }
        }
    }

    private fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        startActivity(intent)
        Toast.makeText(this, "Please enable AutoTapper service", Toast.LENGTH_LONG).show()
    }
}
