package com.dave.autotapper

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Path
import android.graphics.PixelFormat
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import kotlinx.coroutines.*

@SuppressLint("AccessibilityPolicy")
class AutoTapperService : AccessibilityService() {

    private val TAG = "TapperService"

    companion object {
        var instance: AutoTapperService? = null
    }

    private var overlayView: View? = null
    private var windowManager: WindowManager? = null
    private var btnStartStop: TextView? = null
    private var frameLayout: FrameLayout? = null
    private var tvCounter: TextView? = null
    private var tvCounterLikes: TextView? = null

    private var isTapStarted = 0
    private var isTapping = false
    private var tapCount = 0
    private var tapSpeed = 5 // taps per second
    private var tapJob: Job? = null

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // Target packages to monitor
    private val targetPackages = setOf(
        "com.zhiliaoapp.musically",      // TikTok (global – Kenya)
        "com.zhiliaoapp.musically.go"    // TikTok Lite (some devices)
    )

    override fun onCreate() {
        super.onCreate()
        instance = this
        loadTapSpeed()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        createFloatingButton()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Monitor for target apps
        event?.let {
            val packageName = it.packageName?.toString() ?: return

            if (targetPackages.contains(packageName)) {
                Log.d(TAG, "Target app in foreground: $packageName")
                // Target app is in foreground
                // You can add specific behavior here if needed
                // For example: auto-show floating button, log activity, etc.
            }else {
                Log.d(TAG, "Target app not in foreground: $packageName")
                // Target app is not in foreground
                // You can add specific behavior here if needed
                // For example: hide floating button, log activity, etc.
                stopTapping()
            }
        }
    }

    override fun onInterrupt() {
        stopTapping()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopTapping()
        removeFloatingButton()
        serviceScope.cancel()
        instance = null
    }

    private fun loadTapSpeed() {
        val prefs = getSharedPreferences("AutoTapperPrefs", Context.MODE_PRIVATE)
        tapSpeed = prefs.getInt("tap_speed", 5)
        Log.d(TAG, "Loaded tap speed: $tapSpeed")
    }

    fun updateTapSpeed(speed: Int) {
        tapSpeed = speed
        if (isTapping) {
            // Restart tapping with new speed
            stopTapping()
            startTapping()
        }
    }

    private fun createFloatingButton() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val inflater = getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater
        overlayView = inflater.inflate(R.layout.floating_button, null)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )

        params.gravity = Gravity.TOP or Gravity.END
        params.x = 20
        params.y = 150

        frameLayout = overlayView?.findViewById(R.id.frameLayout)
        btnStartStop = overlayView?.findViewById(R.id.btnStartStop)
        tvCounter = overlayView?.findViewById(R.id.tvCounter)
        tvCounterLikes = overlayView?.findViewById(R.id.tvCounterLikes)

        btnStartStop?.setOnClickListener {
            if (isTapping) {
                stopTapping()
            } else {
                startTapping()
            }
        }

        windowManager?.addView(overlayView, params)
        updateButtonUI()
    }

    private fun removeFloatingButton() {
        overlayView?.let {
            windowManager?.removeView(it)
            overlayView = null
        }
    }

    private fun startTapping() {
        Log.d(TAG, "Starting tapping at speed: $tapSpeed")
        isTapping = true
        isTapStarted = 1 //Started
        tapCount = 0
        updateButtonUI()

        tapJob = serviceScope.launch {
            val delayMs = 1000L / tapSpeed

            while (isTapping) {
                performTap()
                tapCount++
                updateCounter()
                delay(delayMs)
            }
        }
    }

    private fun stopTapping() {
        Log.d(TAG, "Stopping tapping")
        isTapping = false
        isTapStarted = 2
        tapJob?.cancel()
        tapJob = null
        updateButtonUI()
    }

    private fun performTap() {
        // Perform accessibility tap at center of screen
        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels

        // Tap at center of screen
        val x = screenWidth / 2f
        val y = screenHeight / 2f

        Log.d(TAG, "Performing tap at ($x, $y)")

        val path = Path()
        path.moveTo(x, y)

        val gestureBuilder = GestureDescription.Builder()
        val gesture = gestureBuilder
            .addStroke(GestureDescription.StrokeDescription(path, 0, 10))
            .build()

        dispatchGesture(gesture, null, null)
    }

    private fun updateButtonUI() {

        if (isTapping) {
            btnStartStop?.text = "■" // Stop
        }else{
            btnStartStop?.text = "▶" // Start
        }


        frameLayout?.apply {
            when (isTapStarted) {
                1 -> setBackgroundResource(R.drawable.bg_fab_start)
                2 -> setBackgroundResource(R.drawable.bg_fab_end)
                else -> setBackgroundResource(R.drawable.bg_fab_normal)
            }
        }
    }

    private fun updateCounter() {
        tvCounter?.text = "Taps: $tapCount"

        val likesFromTaps = LikesCalculator.calculateExpectedLikesFromAppTaps(tapCount)
        tvCounterLikes?.text = "Est Likes: $likesFromTaps"

    }

}