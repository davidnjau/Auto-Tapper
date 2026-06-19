package com.dave.autotapper

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.min

class SessionHistoryActivity : AppCompatActivity() {

    private val dateFmt = SimpleDateFormat("dd MMM  HH:mm", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_session_history)

        val sessions = SessionStore.load(this)

        renderAnalysis(sessions)
        renderRecommendation(sessions)
        renderSessionLog(sessions)
    }

    private fun renderAnalysis(sessions: List<SessionData>) {
        val tvTotal = findViewById<TextView>(R.id.tvAnalysisTotal)
        val tvAvgSuccess = findViewById<TextView>(R.id.tvAnalysisAvgSuccess)
        val tvAvgObserved = findViewById<TextView>(R.id.tvAnalysisAvgObserved)
        val tvTotalLikes = findViewById<TextView>(R.id.tvAnalysisTotalLikes)

        tvTotal.text = sessions.size.toString()

        if (sessions.isEmpty()) {
            tvAvgSuccess.text = "—"
            tvAvgObserved.text = "—"
            tvTotalLikes.text = "—"
            return
        }

        val avgSuccess = sessions.map { it.successRate }.average()
        tvAvgSuccess.text = "${"%.1f".format(avgSuccess)}%"

        val withObserved = sessions.filter { it.observedRatePct != null }
        if (withObserved.isNotEmpty()) {
            val avgObserved = withObserved.mapNotNull { it.observedRatePct }.average()
            tvAvgObserved.text = "${"%.1f".format(avgObserved)}%"
        } else {
            tvAvgObserved.text = "—"
        }

        val totalLikes = sessions.sumOf { it.actualDelta ?: it.estimatedLikes.toLong() }
        tvTotalLikes.text = formatLikes(totalLikes)
    }

    private fun renderRecommendation(sessions: List<SessionData>) {
        val tvRecConfig = findViewById<TextView>(R.id.tvRecConfig)
        val tvRecReason = findViewById<TextView>(R.id.tvRecReason)
        val btnApply = findViewById<TextView>(R.id.btnApplyRec)

        if (sessions.isEmpty()) {
            tvRecConfig.text = "No data yet"
            tvRecReason.text = "Run at least one session to get a recommendation."
            btnApply.visibility = View.GONE
            return
        }

        // Group by (speed, multiplier) and score each config
        data class Config(val speed: Int, val multiplier: Int)
        val grouped = sessions.groupBy { Config(it.tapSpeed, it.tapMultiplier) }

        val scored = grouped.map { (config, list) ->
            val avgSuccess = list.map { it.successRate }.average()
            val effectiveTps = min(config.speed.toDouble(), 1000.0 / (config.multiplier * 60.0)) * config.multiplier
            val score = (avgSuccess / 100.0) * effectiveTps
            Triple(config, score, avgSuccess)
        }.sortedByDescending { it.second }

        val best = scored.first()
        val bestConfig = best.first

        tvRecConfig.text = "${bestConfig.speed} taps/sec  ·  ${bestConfig.multiplier}×"
        tvRecReason.text = "Score ${"%.2f".format(best.second)} effective taps/sec  ·  avg success ${"%.1f".format(best.third)}%  ·  ${grouped[bestConfig]!!.size} sessions"

        btnApply.visibility = View.VISIBLE
        btnApply.setOnClickListener {
            val prefs = getSharedPreferences("AutoTapperPrefs", Context.MODE_PRIVATE)
            prefs.edit()
                .putInt("tap_speed", bestConfig.speed)
                .putInt("tap_multiplier", bestConfig.multiplier)
                .apply()
            AutoTapperService.instance?.updateTapSpeed(bestConfig.speed)
            AutoTapperService.instance?.updateTapMultiplier(bestConfig.multiplier)
            Toast.makeText(this, "Configuration applied", Toast.LENGTH_SHORT).show()
        }
    }

    private fun renderSessionLog(sessions: List<SessionData>) {
        val container = findViewById<LinearLayout>(R.id.sessionLogContainer)
        val tvEmpty = findViewById<TextView>(R.id.tvEmptySessions)

        if (sessions.isEmpty()) {
            tvEmpty.visibility = View.VISIBLE
            container.visibility = View.GONE
            return
        }

        tvEmpty.visibility = View.GONE
        container.visibility = View.VISIBLE

        sessions.forEach { session ->
            val row = buildSessionRow(session)
            container.addView(row)
        }
    }

    private fun buildSessionRow(s: SessionData): View {
        val ctx = this
        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_card_hud_subtle)
            val pad = (12 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (8 * resources.displayMetrics.density).toInt() }
            layoutParams = lp
        }

        // Row 1: date + config
        val row1 = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (6 * resources.displayMetrics.density).toInt() }
            layoutParams = lp
        }

        val tvDate = TextView(ctx).apply {
            text = dateFmt.format(Date(s.timestamp))
            textSize = 11f
            setTextColor(ContextCompat.getColor(ctx, R.color.cyan))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val tvConfig = TextView(ctx).apply {
            text = "${s.tapSpeed}tps · ${s.tapMultiplier}×"
            textSize = 11f
            setTextColor(ContextCompat.getColor(ctx, R.color.text_muted))
        }

        row1.addView(tvDate)
        row1.addView(tvConfig)
        card.addView(row1)

        // Row 2: taps + success rate
        val row2 = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (4 * resources.displayMetrics.density).toInt() }
            layoutParams = lp
        }

        val tvTaps = TextView(ctx).apply {
            text = "%,d taps".format(s.totalTaps)
            textSize = 13f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(ContextCompat.getColor(ctx, R.color.text_primary))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val successColor = when {
            s.successRate >= 90 -> R.color.success
            s.successRate >= 70 -> R.color.warning
            else -> R.color.danger
        }
        val tvSuccess = TextView(ctx).apply {
            text = "${s.successRate}% success"
            textSize = 12f
            setTextColor(ContextCompat.getColor(ctx, successColor))
        }

        row2.addView(tvTaps)
        row2.addView(tvSuccess)
        card.addView(row2)

        // Row 3: likes
        val likesText = if (s.actualDelta != null) {
            "Actual: +${"%,d".format(s.actualDelta)}  ·  Est: +${"%,d".format(s.estimatedLikes)}"
        } else {
            "Est likes: +${"%,d".format(s.estimatedLikes)}"
        }

        val tvLikes = TextView(ctx).apply {
            text = likesText
            textSize = 11f
            setTextColor(ContextCompat.getColor(ctx, R.color.text_secondary))
        }
        card.addView(tvLikes)

        return card
    }

    private fun formatLikes(count: Long): String = when {
        count >= 1_000_000 -> "${"%.1f".format(count / 1_000_000.0)}M"
        count >= 1_000 -> "${"%.1f".format(count / 1_000.0)}K"
        else -> count.toString()
    }

    companion object {
        fun start(context: Context) {
            context.startActivity(Intent(context, SessionHistoryActivity::class.java))
        }
    }
}
