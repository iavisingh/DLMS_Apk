package com.example.dlmsconfigurator.core.session

import android.content.Context

interface FrameCounterStore {
    fun nextOutgoingCounter(meterId: String): Long
    fun lastIncomingCounter(meterId: String): Long
    fun acceptIncomingCounter(meterId: String, counter: Long): Boolean
}

class SharedPrefsFrameCounterStore(context: Context) : FrameCounterStore {
    private val prefs = context.getSharedPreferences("dlms_frame_counters", Context.MODE_PRIVATE)

    override fun nextOutgoingCounter(meterId: String): Long {
        val key = "out_$meterId"
        val next = prefs.getLong(key, 0L) + 1L
        prefs.edit().putLong(key, next).apply()
        return next
    }

    override fun lastIncomingCounter(meterId: String): Long {
        return prefs.getLong("in_$meterId", -1L)
    }

    override fun acceptIncomingCounter(meterId: String, counter: Long): Boolean {
        val key = "in_$meterId"
        val previous = prefs.getLong(key, -1L)
        if (counter <= previous) return false
        prefs.edit().putLong(key, counter).apply()
        return true
    }
}
