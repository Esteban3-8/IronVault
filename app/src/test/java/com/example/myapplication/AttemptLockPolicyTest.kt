package com.example.myapplication

import org.junit.Assert.assertEquals
import org.junit.Test

class AttemptLockPolicyTest {
    @Test fun configuredThresholdsAndFirstDelays() {
        for ((limit, delay) in listOf(3 to 30000L, 5 to 60000L, 7 to 120000L)) {
            for (failed in 0 until limit) assertEquals(0L, AttemptLockPolicy.duration(limit, failed))
            assertEquals(delay, AttemptLockPolicy.duration(limit, limit))
            assertEquals(delay * 2, AttemptLockPolicy.duration(limit, limit * 2))
        }
    }
    @Test fun disabledAndOverflowCannotCreateNegativeWaits() {
        assertEquals(0L, AttemptLockPolicy.duration(-1, 30))
        assertEquals(3600000L, AttemptLockPolicy.duration(3, 300))
        assertEquals(3600000L, AttemptLockPolicy.duration(3, Int.MAX_VALUE - 1))
    }
    @Test fun expirationAllowsImmediateRetryAndClockRollbackIsBounded() {
        assertEquals(0L, AttemptLockPolicy.remaining(1000, 1000, 30000))
        assertEquals(0L, AttemptLockPolicy.remaining(1000, 2000, 30000))
        assertEquals(500L, AttemptLockPolicy.remaining(1000, 500, 30000))
        assertEquals(30000L, AttemptLockPolicy.remaining(100000, 0, 30000))
    }
}
