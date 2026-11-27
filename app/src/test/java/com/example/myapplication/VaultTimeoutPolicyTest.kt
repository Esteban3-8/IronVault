package com.example.myapplication

import org.junit.Assert.*
import org.junit.Test

class VaultTimeoutPolicyTest {
    @Test fun backgroundDeadlineOnlyExpiresAtSelectedTime() {
        val deadline = 1000L + VaultTimeoutPolicy.delay("15 segundos")
        assertFalse(VaultTimeoutPolicy.expired(deadline, 15999))
        assertTrue(VaultTimeoutPolicy.expired(deadline, 16000))
        assertTrue(VaultTimeoutPolicy.expired(deadline, 60000))
        assertFalse(VaultTimeoutPolicy.expired(0, 60000))
    }
    @Test fun allExistingSelectionsRemainSupported() {
        assertEquals(listOf(15000L, 30000L, 60000L, 180000L, 300000L, 600000L, -1L, -1L),
            listOf("15 segundos", "30 segundos", "1 minuto", "3 minutos", "5 minutos", "10 minutos",
                "Desactivar", "Nunca").map(VaultTimeoutPolicy::delay))
    }
}
