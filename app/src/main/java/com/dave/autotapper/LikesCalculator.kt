package com.dave.autotapper

import kotlin.math.roundToInt

/**
 * Calculator for expected TikTok likes based on tapping parameters
 * Based on real performance data showing 96.75% app efficiency and 97.8% registration rate
 */
object LikesCalculator {

    // Constants based on your actual performance data
    private const val APP_EFFICIENCY = 0.9678      // 96.75% - How many taps app actually performs
    private const val REGISTRATION_RATE = 0.9780    // 97.8% - How many app taps become likes
    private const val COMBINED_EFFICIENCY = 0.9465  // APP_EFFICIENCY × REGISTRATION_RATE

    /**
     * Calculate expected likes given time and tap speed
     *
     * @param timeMinutes Duration in minutes
     * @param tapSpeed Taps per second (1-20)
     * @return Expected number of likes
     */
    fun calculateExpectedLikes(timeMinutes: Int, tapSpeed: Int): Int {
        val theoreticalTaps = timeMinutes * 60 * tapSpeed
        val expectedLikes = (theoreticalTaps * COMBINED_EFFICIENCY).roundToInt()
        return expectedLikes
    }

    /**
     * Calculate expected likes given actual app taps
     *
     * @param appTaps Number shown on app counter
     * @return Expected number of likes
     */
    fun calculateExpectedLikesFromAppTaps(appTaps: Int): Int {
        return (appTaps * REGISTRATION_RATE).roundToInt()
    }

    /**
     * Calculate time required to reach target likes
     *
     * @param targetLikes Desired number of likes
     * @param tapSpeed Taps per second (1-20)
     * @return Time needed in minutes (rounded up)
     */
    fun calculateTimeForLikes(targetLikes: Int, tapSpeed: Int): Int {
        val likesPerMinute = tapSpeed * 60 * COMBINED_EFFICIENCY
        val minutesNeeded = (targetLikes / likesPerMinute)
        return kotlin.math.ceil(minutesNeeded).toInt()
    }

    /**
     * Get detailed breakdown of calculations
     *
     * @param timeMinutes Duration in minutes
     * @param tapSpeed Taps per second
     * @param actualAppTaps Optional: actual app tap count (if available)
     * @return Detailed calculation report
     */
    fun getDetailedBreakdown(
        timeMinutes: Int,
        tapSpeed: Int,
        actualAppTaps: Int? = null
    ): CalculationBreakdown {
        val theoreticalTaps = timeMinutes * 60 * tapSpeed
        val estimatedAppTaps = (theoreticalTaps * APP_EFFICIENCY).roundToInt()
        val appTaps = actualAppTaps ?: estimatedAppTaps
        val expectedLikes = (appTaps * REGISTRATION_RATE).roundToInt()

        val appEfficiency = if (actualAppTaps != null) {
            (actualAppTaps.toDouble() / theoreticalTaps * 100)
        } else {
            APP_EFFICIENCY * 100
        }

        return CalculationBreakdown(
            timeMinutes = timeMinutes,
            tapSpeed = tapSpeed,
            theoreticalTaps = theoreticalTaps,
            appTaps = appTaps,
            appEfficiencyPercent = appEfficiency,
            registrationRatePercent = REGISTRATION_RATE * 100,
            expectedLikes = expectedLikes
        )
    }

    /**
     * Compare different tap speeds
     *
     * @param timeMinutes Duration in minutes
     * @param speeds List of tap speeds to compare
     * @return List of comparisons
     */
    fun compareSpeed(timeMinutes: Int, speeds: List<Int>): List<SpeedComparison> {
        return speeds.map { speed ->
            val likes = calculateExpectedLikes(timeMinutes, speed)
            val tapsPerMinute = speed * 60
            val likesPerMinute = likes.toDouble() / timeMinutes

            SpeedComparison(
                tapSpeed = speed,
                expectedLikes = likes,
                tapsPerMinute = tapsPerMinute,
                likesPerMinute = likesPerMinute.roundToInt()
            )
        }
    }

    /**
     * Quick reference table for common durations
     */
    fun getQuickReferenceTable(tapSpeed: Int): List<QuickReference> {
        val durations = listOf(1, 3, 5, 7, 10, 15, 20, 30)
        return durations.map { minutes ->
            QuickReference(
                minutes = minutes,
                expectedLikes = calculateExpectedLikes(minutes, tapSpeed)
            )
        }
    }
}

/**
 * Data class for detailed calculation breakdown
 */
data class CalculationBreakdown(
    val timeMinutes: Int,
    val tapSpeed: Int,
    val theoreticalTaps: Int,
    val appTaps: Int,
    val appEfficiencyPercent: Double,
    val registrationRatePercent: Double,
    val expectedLikes: Int
) {
    override fun toString(): String {
        return """
            |╔════════════════════════════════════════╗
            |║      LIKES CALCULATION BREAKDOWN       ║
            |╠════════════════════════════════════════╣
            |║ Duration: $timeMinutes minutes
            |║ Tap Speed: $tapSpeed taps/sec
            |║────────────────────────────────────────
            |║ Theoretical Taps: $theoreticalTaps
            |║ App Taps: $appTaps (${String.format("%.1f", appEfficiencyPercent)}%)
            |║ Registration Rate: ${String.format("%.1f", registrationRatePercent)}%
            |║────────────────────────────────────────
            |║ ⭐ EXPECTED LIKES: $expectedLikes
            |╚════════════════════════════════════════╝
        """.trimMargin()
    }
}

/**
 * Data class for speed comparison
 */
data class SpeedComparison(
    val tapSpeed: Int,
    val expectedLikes: Int,
    val tapsPerMinute: Int,
    val likesPerMinute: Int
) {
    override fun toString(): String {
        return "$tapSpeed taps/sec → $expectedLikes likes ($likesPerMinute likes/min)"
    }
}

/**
 * Data class for quick reference
 */
data class QuickReference(
    val minutes: Int,
    val expectedLikes: Int
) {
    override fun toString(): String {
        return "${minutes} min → $expectedLikes likes"
    }
}