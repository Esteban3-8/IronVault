package com.example.myapplication

import java.security.MessageDigest

/** Account metadata, not proof that a vault was unlocked or that a session is still active. */
internal data class AccountAccessEvent(
    val deviceId: String,
    val deviceName: String,
    val androidVersion: String,
    val authTime: Long
) {
    companion object {
        fun deviceId(uid: String, installation: String): String = MessageDigest.getInstance("SHA-256")
            .digest("$uid:$installation".toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

        fun documentId(deviceId: String, authTime: Long): String {
            require(deviceId.matches(Regex("[a-f0-9]{64}")) && authTime > 0)
            return "${deviceId}_$authTime"
        }

        fun label(value: String, maximum: Int): String = value
            .filterNot { Character.isISOControl(it) || Character.getType(it) == Character.FORMAT.toInt() }
            .trim().take(maximum).ifEmpty { "Desconocido" }

        fun parse(data: Map<String, Any>): AccountAccessEvent {
            require(data["version"] == 1L)
            val event = AccountAccessEvent(
                data["deviceId"] as String, data["deviceName"] as String,
                data["androidVersion"] as String, data["authTime"] as Long)
            documentId(event.deviceId, event.authTime)
            require(event.deviceName.length in 1..80 && event.androidVersion.length in 1..32)
            require(event.authTime <= 253402300799L)
            return event.copy(deviceName = label(event.deviceName, 80), androidVersion = label(event.androidVersion, 32))
        }
    }
}
