package com.dave.autotapper

data class SessionData(
    val timestamp: Long,
    val tapSpeed: Int,
    val tapMultiplier: Int,
    val totalTaps: Int,
    val completedGestures: Int,
    val cancelledGestures: Int,
    val successRate: Int,
    val estimatedLikes: Int,
    val baselineLikes: Long?,
    val endLikes: Long?,
    val actualDelta: Long?,
    val observedRatePct: Double?
)
