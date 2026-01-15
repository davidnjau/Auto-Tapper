package com.dave.autotapper

import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var tvTapSpeed: TextView
    private lateinit var seekBarSpeed: SeekBar
    private lateinit var btnEnableService: Button
    private lateinit var btnOpenSettings: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        setupListeners()
        updateServiceStatus()
    }

    override fun onResume() {
        super.onResume()
        updateServiceStatus()
    }

    private fun initViews() {
        tvStatus = findViewById(R.id.tvStatus)
        tvTapSpeed = findViewById(R.id.tvTapSpeed)
        seekBarSpeed = findViewById(R.id.seekBarSpeed)
        btnEnableService = findViewById(R.id.btnEnableService)
        btnOpenSettings = findViewById(R.id.btnOpenSettings)

        // Load saved tap speed
        val prefs = getSharedPreferences("AutoTapperPrefs", Context.MODE_PRIVATE)
        val savedSpeed = prefs.getInt("tap_speed", 5)
        seekBarSpeed.progress = savedSpeed - 1
        tvTapSpeed.text = "$savedSpeed"
    }

    private fun setupListeners() {
        seekBarSpeed.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val speed = progress + 1
                tvTapSpeed.text = "$speed"

                // Save tap speed
                val prefs = getSharedPreferences("AutoTapperPrefs", Context.MODE_PRIVATE)
                prefs.edit().putInt("tap_speed", speed).apply()

                // Update service if running
                AutoTapperService.instance?.updateTapSpeed(speed)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        btnEnableService.setOnClickListener {
            if (!isAccessibilityServiceEnabled()) {
                openAccessibilitySettings()
            } else {
                Toast.makeText(this, "Service is already enabled!", Toast.LENGTH_SHORT).show()
            }
        }

        btnOpenSettings.setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
            } else {
                Toast.makeText(this, "Overlay permission already granted!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateServiceStatus() {
        val isEnabled = isAccessibilityServiceEnabled()
        val hasOverlay = Settings.canDrawOverlays(this)

        tvStatus.text = buildString {
            append("Accessibility Service: ${if (isEnabled) "✓ Enabled" else "✗ Disabled"}\n")
            append("Overlay Permission: ${if (hasOverlay) "✓ Granted" else "✗ Not Granted"}")
        }

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

    private fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        startActivity(intent)
        Toast.makeText(
            this,
            "Please enable AutoTapper service",
            Toast.LENGTH_LONG
        ).show()
    }


}