package com.example.myapplication

/** Local throttling; does not replace cryptographic protection against offline guesses. */
internal object AttemptLockPolicy {
    fun duration(limit: Int, failed: Int): Long {
        if (limit !in setOf(3, 5, 7) || failed <= 0 || failed % limit != 0) return 0
        val base = when (limit) { 5 -> 60000L; 7 -> 120000L; else -> 30000L }
        return (base * (1L shl (failed / limit - 1).coerceIn(0, 7))).coerceAtMost(3600000L)
    }
    fun remaining(until: Long, now: Long, duration: Long): Long =
        (until - now).coerceIn(0, duration.coerceIn(0, 3600000L))
}
