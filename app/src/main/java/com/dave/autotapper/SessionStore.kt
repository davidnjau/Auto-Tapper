package com.dave.autotapper

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object SessionStore {

    private const val PREFS_NAME = "AutoTapperSessions"
    private const val KEY_SESSIONS = "sessions"
    private const val MAX_SESSIONS = 50

    fun save(context: Context, session: SessionData) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existing = load(context).toMutableList()
        existing.add(0, session)
        if (existing.size > MAX_SESSIONS) existing.subList(MAX_SESSIONS, existing.size).clear()

        val arr = JSONArray()
        existing.forEach { arr.put(sessionToJson(it)) }
        prefs.edit().putString(KEY_SESSIONS, arr.toString()).apply()
    }

    fun load(context: Context): List<SessionData> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_SESSIONS, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { jsonToSession(arr.getJSONObject(it)) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun sessionToJson(s: SessionData): JSONObject = JSONObject().apply {
        put("timestamp", s.timestamp)
        put("tapSpeed", s.tapSpeed)
        put("tapMultiplier", s.tapMultiplier)
        put("totalTaps", s.totalTaps)
        put("completedGestures", s.completedGestures)
        put("cancelledGestures", s.cancelledGestures)
        put("successRate", s.successRate)
        put("estimatedLikes", s.estimatedLikes)
        s.baselineLikes?.let { put("baselineLikes", it) }
        s.endLikes?.let { put("endLikes", it) }
        s.actualDelta?.let { put("actualDelta", it) }
        s.observedRatePct?.let { put("observedRatePct", it) }
    }

    private fun jsonToSession(o: JSONObject) = SessionData(
        timestamp = o.getLong("timestamp"),
        tapSpeed = o.getInt("tapSpeed"),
        tapMultiplier = o.getInt("tapMultiplier"),
        totalTaps = o.getInt("totalTaps"),
        completedGestures = o.getInt("completedGestures"),
        cancelledGestures = o.getInt("cancelledGestures"),
        successRate = o.getInt("successRate"),
        estimatedLikes = o.getInt("estimatedLikes"),
        baselineLikes = if (o.has("baselineLikes")) o.getLong("baselineLikes") else null,
        endLikes = if (o.has("endLikes")) o.getLong("endLikes") else null,
        actualDelta = if (o.has("actualDelta")) o.getLong("actualDelta") else null,
        observedRatePct = if (o.has("observedRatePct")) o.getDouble("observedRatePct") else null
    )
}
