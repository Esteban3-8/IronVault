package com.example.myapplication

internal object VaultTimeoutPolicy {
    fun delay(setting: String?): Long = when (setting) {
        "15 segundos" -> 15000L
        "30 segundos" -> 30000L
        "1 minuto" -> 60000L
        "3 minutos" -> 180000L
        "10 minutos" -> 600000L
        "Desactivar", "Nunca" -> -1L
        else -> 300000L
    }
    fun expired(deadline: Long, elapsedNow: Long) = deadline > 0 && elapsedNow >= deadline
}
