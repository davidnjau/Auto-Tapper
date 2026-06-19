package com.dave.autotapper

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*

@SuppressLint("AccessibilityPolicy")
class AutoTapperService : AccessibilityService() {

    private val TAG = "TapperService"

    companion object {
        var instance: AutoTapperService? = null
        private val LIKE_COUNT_PATTERN = Regex("""^\d+(\.\d+)?[KkMmBb]?$""")
        // Extracts a like-count token from within a longer string (e.g. "♥ 263.4K", "263.4K likes")
        private val LIKE_COUNT_IN_TEXT = Regex("""\d+(?:\.\d+)?[KkMmBb]""")
        private const val CHANNEL_ID = "session_summary"
        private const val NOTIFICATION_ID = 1001
    }

    private var overlayView: View? = null
    private var windowManager: WindowManager? = null
    private var btnStartStop: TextView? = null
    private var frameLayout: FrameLayout? = null
    private var tvCounter: TextView? = null
    private var tvCounterLikes: TextView? = null

    private enum class TapState { IDLE, STARTED, STOPPED }

    private var tapState = TapState.IDLE
    private var isTapping = false
    private var tapCount = 0
    private var tapSpeed = 5 // taps per second
    private var tapMultiplier = 1 // strokes per dispatch: 1, 2, or 3
    private var tapJob: Job? = null

    private var completedGestures = 0
    private var cancelledGestures = 0
    private var foregroundLossEvents = 0
    private var screenBaseline: Long? = null  // like count read from screen at session start

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
        createNotificationChannel()
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
            } else {
                Log.d(TAG, "Target app not in foreground: $packageName")
                serviceScope.launch { stopTapping() }
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
        tapMultiplier = prefs.getInt("tap_multiplier", 1)
        Log.d(TAG, "Loaded tap speed: $tapSpeed, multiplier: ${tapMultiplier}x")
    }

    fun updateTapSpeed(speed: Int) {
        tapSpeed = speed
        if (isTapping) {
            val savedCount = tapCount
            stopTapping()
            tapCount = savedCount
            startTapping()
        }
    }

    fun updateTapMultiplier(multiplier: Int) {
        tapMultiplier = multiplier
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

        val density = resources.displayMetrics.density
        params.gravity = Gravity.TOP or Gravity.END
        params.x = (20 * density).toInt()
        params.y = (150 * density).toInt()

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
        tapState = TapState.STARTED
        tapCount = 0
        completedGestures = 0
        cancelledGestures = 0
        foregroundLossEvents = 0
        screenBaseline = readLikeCountFromScreen().also {
            if (it != null) Log.i(TAG, "Baseline like count: $it")
            else Log.w(TAG, "Could not read baseline like count from screen")
        }
        updateButtonUI()

        tapJob = serviceScope.launch {
            // Ensure the delay is long enough for the full gesture to complete before the next dispatch.
            // Each extra stroke adds 60ms (50ms duration + 10ms gap); min safe window = multiplier * 60ms.
            val gestureDurationMs = (tapMultiplier * 60).toLong()
            val baseDelay = maxOf(1000L / tapSpeed, gestureDurationMs)

            while (isTapping) {
                if (!isTikTokForeground()) {
                    foregroundLossEvents++
                    stopTapping()
                    break
                }
                performTap()
                tapCount += tapMultiplier
                updateCounter()
                delay(baseDelay + (-30L..30L).random())
            }
        }
    }

    private fun stopTapping() {
        if (tapCount > 0) {
            val successRate = completedGestures * 100 / tapCount
            val estimatedLikes = LikesCalculator.calculateExpectedLikesFromAppTaps(tapCount)
            val endCount = readLikeCountFromScreen()
            val baseline = screenBaseline

            Log.i(TAG, "Session summary — attempts: $tapCount | completed: $completedGestures | " +
                    "cancelled: $cancelledGestures | foreground losses: $foregroundLossEvents | success rate: $successRate%")

            if (baseline != null && endCount != null) {
                val actualDelta = endCount - baseline
                val observedRate = actualDelta.toDouble() / tapCount
                val configuredRate = LikesCalculator.REGISTRATION_RATE * 100
                Log.i(TAG, "Calibration — baseline: $baseline | end: $endCount | actual delta: $actualDelta | " +
                        "observed rate: ${"%.1f".format(observedRate * 100)}% | configured rate: ${"%.1f".format(configuredRate)}%")
            } else {
                Log.w(TAG, "Calibration skipped — could not read end like count from screen")
            }

            showSessionSummaryNotification(
                taps = tapCount,
                successRate = successRate,
                estimatedLikes = estimatedLikes,
                baseline = baseline,
                endCount = endCount
            )

            val actualDelta = if (baseline != null && endCount != null) endCount - baseline else null
            val observedRatePct = if (baseline != null && endCount != null && tapCount > 0)
                (endCount - baseline).toDouble() / tapCount * 100.0 else null

            SessionStore.save(this, SessionData(
                timestamp = System.currentTimeMillis(),
                tapSpeed = tapSpeed,
                tapMultiplier = tapMultiplier,
                totalTaps = tapCount,
                completedGestures = completedGestures,
                cancelledGestures = cancelledGestures,
                successRate = successRate,
                estimatedLikes = estimatedLikes,
                baselineLikes = baseline,
                endLikes = endCount,
                actualDelta = actualDelta,
                observedRatePct = observedRatePct
            ))
        }
        Log.d(TAG, "Stopping tapping")
        isTapping = false
        tapState = TapState.STOPPED
        tapJob?.cancel()
        tapJob = null
        updateButtonUI()
    }

    private fun isTikTokForeground(): Boolean {
        return rootInActiveWindow?.packageName in targetPackages
    }

    private fun getRandomTapPoint(): Pair<Float, Float> {
        val metrics = resources.displayMetrics
        val centerX = metrics.widthPixels * 0.5f
        val centerY = metrics.heightPixels * 0.38f
        return Pair(
            centerX + (-20..20).random(),
            centerY + (-20..20).random()
        )
    }

    private fun performTap() {
        val gestureBuilder = GestureDescription.Builder()
        repeat(tapMultiplier) { i ->
            val (x, y) = getRandomTapPoint()
            val path = Path().apply { moveTo(x, y) }
            // Each stroke offset by 60ms so strokes don't overlap (50ms duration + 10ms gap).
            gestureBuilder.addStroke(GestureDescription.StrokeDescription(path, (i * 60).toLong(), 50))
        }
        val gesture = gestureBuilder.build()

        Log.d(TAG, "Dispatching ${tapMultiplier}x tap gesture")

        dispatchGesture(
            gesture,
            object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    completedGestures++
                    Log.d(TAG, "Gesture completed (${tapMultiplier}x)")
                }
                override fun onCancelled(gestureDescription: GestureDescription?) {
                    cancelledGestures++
                    Log.w(TAG, "Gesture cancelled (${tapMultiplier}x)")
                }
            },
            null
        )
    }

    private fun updateButtonUI() {

        if (isTapping) {
            btnStartStop?.text = "■" // Stop
        }else{
            btnStartStop?.text = "▶" // Start
        }


        frameLayout?.apply {
            when (tapState) {
                TapState.STARTED -> setBackgroundResource(R.drawable.bg_fab_start)
                TapState.STOPPED -> setBackgroundResource(R.drawable.bg_fab_end)
                TapState.IDLE    -> setBackgroundResource(R.drawable.bg_fab_normal)
            }
        }
    }

    private fun updateCounter() {
        tvCounter?.text = "Taps: $tapCount"

        val estimatedFromTaps = LikesCalculator.calculateExpectedLikesFromAppTaps(tapCount)
        val baseline = screenBaseline
        if (baseline != null) {
            val total = baseline + estimatedFromTaps
            tvCounterLikes?.text = "~${formatLikeCount(total)}"
        } else {
            tvCounterLikes?.text = "Est: $estimatedFromTaps"
        }
    }

    // ── Notifications ─────────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Session Summary",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Shown after each Auto Tapper session with tap and like statistics"
            }
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
    }

    private fun showSessionSummaryNotification(
        taps: Int,
        successRate: Int,
        estimatedLikes: Int,
        baseline: Long?,
        endCount: Long?
    ) {
        val body = buildString {
            appendLine("Taps: ${"%,d".format(taps)}  ·  Success rate: $successRate%")
            appendLine()
            if (baseline != null && endCount != null) {
                val delta = endCount - baseline
                appendLine("Likes before:     ${"%,d".format(baseline)}")
                appendLine("Likes after:      ${"%,d".format(endCount)}")
                appendLine("Actual gained:    +${"%,d".format(delta)}")
                append(    "Estimated gained: +${"%,d".format(estimatedLikes)}")
            } else {
                appendLine("Estimated gained: +${"%,d".format(estimatedLikes)}")
                append("(Live like count unavailable)")
            }
        }

        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Auto Tapper — Session Complete")
            .setContentText("Taps: ${"%,d".format(taps)}  ·  Success: $successRate%")
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .notify(NOTIFICATION_ID, notification)
    }

    // ── Screen reading ─────────────────────────────────────────────────────────

    private fun readLikeCountFromScreen(): Long? {
        val root = rootInActiveWindow ?: return null
        val node = findLikeNode(root) ?: return null
        val text = node.text?.toString() ?: node.contentDescription?.toString() ?: return null
        return parseLikeCount(text)
    }

    /**
     * Traverses the accessibility tree looking for a node whose text or contentDescription
     * matches a like-count format (e.g. "1.2K", "45.6K", "1.2M").
     *
     * Logs every candidate with its screen position under tag TapperService so the
     * position filter can be tuned if needed.
     */
    private fun findLikeNode(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val rawText = (node.text?.toString() ?: node.contentDescription?.toString())?.trim() ?: ""
        if (rawText.isNotEmpty()) {
            // Accept exact match OR extract a count token from a longer string (e.g. "♥ 263.4K", "263.4K likes")
            val matchedToken = when {
                LIKE_COUNT_PATTERN.matches(rawText) -> rawText
                else -> LIKE_COUNT_IN_TEXT.find(rawText)?.value
            }
            if (matchedToken != null) {
                val bounds = Rect()
                node.getBoundsInScreen(bounds)
                val metrics = resources.displayMetrics
                val relX = bounds.centerX().toFloat() / metrics.widthPixels
                val relY = bounds.centerY().toFloat() / metrics.heightPixels
                Log.d(TAG, "Like count candidate: '$rawText' (token='$matchedToken')  relX=${"%.2f".format(relX)}  relY=${"%.2f".format(relY)}")
                // Left 60%, top 65% — covers the top-left like counter across TikTok layouts
                if (relX < 0.60f && relY in 0.05f..0.65f) {
                    Log.i(TAG, "Like count matched: '$matchedToken'  relX=${"%.2f".format(relX)}  relY=${"%.2f".format(relY)}")
                    return node
                }
            }
        }
        for (i in 0 until node.childCount) {
            val found = findLikeNode(node.getChild(i) ?: continue)
            if (found != null) return found
        }
        return null
    }

    private fun parseLikeCount(raw: String): Long? {
        // Extract the numeric+suffix token in case the raw string has extra chars (e.g. "♥ 263.4K", "263.4K likes")
        val token = if (LIKE_COUNT_PATTERN.matches(raw.trim())) raw.trim()
                    else LIKE_COUNT_IN_TEXT.find(raw)?.value ?: raw.trim()
        val cleaned = token.uppercase().replace(",", "")
        return when {
            cleaned.endsWith("B") -> cleaned.dropLast(1).toDoubleOrNull()?.times(1_000_000_000)?.toLong()
            cleaned.endsWith("M") -> cleaned.dropLast(1).toDoubleOrNull()?.times(1_000_000)?.toLong()
            cleaned.endsWith("K") -> cleaned.dropLast(1).toDoubleOrNull()?.times(1_000)?.toLong()
            else -> cleaned.filter { it.isDigit() }.toLongOrNull()
        }
    }

    private fun formatLikeCount(count: Long): String = when {
        count >= 1_000_000 -> "${"%.1f".format(count / 1_000_000.0)}M"
        count >= 1_000     -> "${"%.1f".format(count / 1_000.0)}K"
        else               -> count.toString()
    }

}