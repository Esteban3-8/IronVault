package com.example.myapplication

import org.junit.Assert.*
import org.junit.Test

class AccountAccessEventTest {
    @Test fun installationIdentityIsIsolatedPerAccount() {
        val first = AccountAccessEvent.deviceId("account-a", "installation-a")
        assertEquals(first, AccountAccessEvent.deviceId("account-a", "installation-a"))
        assertNotEquals(first, AccountAccessEvent.deviceId("account-b", "installation-a"))
        assertNotEquals(first, AccountAccessEvent.deviceId("account-a", "installation-b"))
    }
    @Test fun sameAuthenticationIsDeduplicatedButNewLoginHasNewId() {
        val device = AccountAccessEvent.deviceId("account-a", "installation-a")
        assertEquals(AccountAccessEvent.documentId(device, 100), AccountAccessEvent.documentId(device, 100))
        assertNotEquals(AccountAccessEvent.documentId(device, 100), AccountAccessEvent.documentId(device, 101))
    }
    @Test fun deviceNamesCannotInjectLinesOrDirectionControls() {
        assertEquals("PhoneFake", AccountAccessEvent.label("Phone\n\u202eFake", 80))
        assertEquals("Desconocido", AccountAccessEvent.label("\n", 80))
        assertEquals(80, AccountAccessEvent.label("x".repeat(200), 80).length)
    }
    @Test(expected = IllegalArgumentException::class)
    fun malformedDeviceIdIsRejected() { AccountAccessEvent.documentId("../other-account", 100) }
    @Test(expected = IllegalArgumentException::class)
    fun invalidAuthenticationDateIsRejected() { AccountAccessEvent.documentId("a".repeat(64), 0) }
    @Test(expected = IllegalArgumentException::class)
    fun unknownSchemaIsRejected() { AccountAccessEvent.parse(mapOf("version" to 2L)) }
}
