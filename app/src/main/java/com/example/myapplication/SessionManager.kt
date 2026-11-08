package com.example.myapplication

import android.content.Context

object SessionManager {
    private const val PREF_SECURITY = "security_prefs"
    private const val KEY_LAST_BACKGROUND_TIME = "last_background_time"

    fun registrarSalida(context: Context) {
        val prefs = context.getSharedPreferences(PREF_SECURITY, Context.MODE_PRIVATE)
        prefs.edit().putLong(KEY_LAST_BACKGROUND_TIME, System.currentTimeMillis()).apply()
    }

    fun haExpiradoInactividad(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREF_SECURITY, Context.MODE_PRIVATE)
        val lastTime = prefs.getLong(KEY_LAST_BACKGROUND_TIME, 0L)

        if (lastTime == 0L) return false

        val autoLockSetting = prefs.getString("auto_lock", "5 minutos") ?: "5 minutos"
        val timeoutLimitMs = convertirTextoAMilisegundos(autoLockSetting)

        // Si se seleccionó "Desactivar", no expira nunca (-1L)
        if (timeoutLimitMs == -1L) return false

        val tiempoTranscurrido = System.currentTimeMillis() - lastTime
        return tiempoTranscurrido > timeoutLimitMs
    }

    fun limpiarSesion(context: Context) {
        val prefs = context.getSharedPreferences(PREF_SECURITY, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_LAST_BACKGROUND_TIME).apply()
    }

    private fun convertirTextoAMilisegundos(texto: String): Long {
        return when (texto) {
            "15 segundos" -> 15 * 1000L
            "30 segundos" -> 30 * 1000L
            "1 minuto"    -> 1 * 60 * 1000L
            "3 minutos"   -> 3 * 60 * 1000L
            "5 minutos"   -> 5 * 60 * 1000L
            "10 minutos"  -> 10 * 60 * 1000L
            "Desactivar", "Nunca" -> -1L
            else          -> 5 * 60 * 1000L
        }
    }
}